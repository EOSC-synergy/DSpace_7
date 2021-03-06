/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */

package org.dspace.subscriptions;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.app.metrics.CrisMetrics;
import org.dspace.app.metrics.service.CrisMetricsService;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.core.Context;
import org.dspace.discovery.IndexableObject;
import org.dspace.eperson.Subscription;
import org.dspace.eperson.service.SubscribeService;
import org.dspace.scripts.DSpaceRunnable;
import org.dspace.scripts.handler.DSpaceRunnableHandler;
import org.dspace.subscriptions.service.DSpaceObjectUpdates;
import org.dspace.subscriptions.service.SubscriptionGenerator;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.proxy.LazyInitializer;


/**
 * Implementation of {@link DSpaceRunnable} to find subscribed objects and send notification mails about them
 *
 * @author alba aliu
 */
public class SubscriptionEmailNotificationService {
    private static final Logger log = LogManager.getLogger(SubscriptionEmailNotification.class);
    public static final List<String> FREQUENCIES = Arrays.asList("D", "W", "M");
    private final CrisMetricsService crisMetricsService;
    private final SubscribeService subscribeService;
    private Map<String, DSpaceObjectUpdates> contentUpdates = new HashMap<>();
    private Map<String, SubscriptionGenerator> generators = new HashMap<>();
    private List<IndexableObject> communities = new ArrayList<>();
    private List<IndexableObject> collections = new ArrayList<>();
    private List<IndexableObject> items = new ArrayList<>();

    public void perform(Context context, DSpaceRunnableHandler handler, String type, String frequency) {
        try {
            context.turnOffAuthorisationSystem();
            List<Subscription> subscriptionList = findAllSubscriptionsByTypeAndFrequency(context, type, frequency);
            // if content subscription
            // Here is verified if type is "content" Or "statistics" as them are configured
            if (type.equals(generators.keySet().toArray()[0])) {
                // the list of the person who has subscribed
                int iterator = 0;
                for (Subscription subscription : subscriptionList) {
                    DSpaceObject dSpaceObject = getdSpaceObject(subscription);
                    if (dSpaceObject instanceof Community) {
                        communities.addAll(contentUpdates.get(Community.class.getSimpleName().toLowerCase(Locale.ROOT))
                                .findUpdates(context, dSpaceObject, frequency));
                    } else if (dSpaceObject instanceof Collection) {
                        collections.addAll(contentUpdates.get(Collection.class.getSimpleName().toLowerCase(Locale.ROOT))
                                .findUpdates(context, dSpaceObject, frequency));
                    } else if (dSpaceObject instanceof Item) {
                        items.addAll(contentUpdates.get(Item.class.getSimpleName().toLowerCase(Locale.ROOT))
                                .findUpdates(context, dSpaceObject, frequency));
                    }
                    if (iterator < subscriptionList.size() - 1) {
                        if (subscription.getePerson().equals(subscriptionList.get(iterator + 1).getePerson())) {
                            iterator++;
                            continue;
                        } else {
                            generators.get(type).notifyForSubscriptions(context, subscription.getePerson(),
                                    communities, collections, items);
                            communities.clear();
                            collections.clear();
                            items.clear();
                        }
                    } else {
                        //in the end of the iteration
                        generators.get(type).notifyForSubscriptions(context, subscription.getePerson(),
                                communities, collections, items);
                    }
                    iterator++;
                }
            } else {
                if (!type.equals(generators.keySet().toArray()[1])) {
                    throw new IllegalArgumentException("Options type t and frequency f must be set correctly, " +
                        "type must be one of: "
                    + String.join(",", generators.keySet()) + " frequency one of: "
                    + String.join(", ", FREQUENCIES));
                }
                int iterator = 0;
                List<CrisMetrics> crisMetricsList = new ArrayList<>();
                for (Subscription subscription : subscriptionList) {
                    try {
                        crisMetricsList.addAll(crisMetricsService.findAllByDSO(context,
                                subscription.getdSpaceObject()));
                    } catch (Exception e) {
                        log.error(e.getMessage());
                    }
                    if (iterator < subscriptionList.size() - 1) {
                        if (subscription.getePerson().equals(subscriptionList.get(iterator + 1).getePerson())) {
                            iterator++;
                            continue;
                        } else {
                            generators.get(type).notifyForSubscriptions(context, subscription.getePerson(),
                                    crisMetricsList, null, null);
                        }
                    } else {
                        //in the end of the iteration
                        generators.get(type).notifyForSubscriptions(context, subscription.getePerson(),
                                crisMetricsList, null, null);
                    }
                    iterator++;
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            handler.handleException(e);
            context.abort();
        } finally {
            context.restoreAuthSystemState();
        }
    }

    private DSpaceObject getdSpaceObject(Subscription subscription) {
        DSpaceObject dSpaceObject = subscription.getdSpaceObject();
        if (subscription.getdSpaceObject() instanceof HibernateProxy) {
            HibernateProxy hibernateProxy = (HibernateProxy) subscription.getdSpaceObject();
            LazyInitializer initializer = hibernateProxy.getHibernateLazyInitializer();
            dSpaceObject = (DSpaceObject) initializer.getImplementation();
        }
        return dSpaceObject;
    }

    private List<Subscription> findAllSubscriptionsByTypeAndFrequency(Context context, String type, String frequency) {
        try {
            return this.subscribeService.findAllSubscriptionsByTypeAndFrequency(context, type, frequency)
                .stream()
                .sorted(Comparator.comparing(s -> s.getePerson().getID()))
                .collect(Collectors.toList());
        } catch (SQLException sqlException) {
            log.error(sqlException.getMessage());
        }
        return null;
    }

    public SubscriptionEmailNotificationService(CrisMetricsService crisMetricsService,
                                                SubscribeService subscribeService,
                                                Map<String, SubscriptionGenerator> generators,
                                                Map<String, DSpaceObjectUpdates> contentUpdates) {
        this.crisMetricsService = crisMetricsService;
        this.subscribeService = subscribeService;
        this.generators = generators;
        this.contentUpdates = contentUpdates;
    }

}
