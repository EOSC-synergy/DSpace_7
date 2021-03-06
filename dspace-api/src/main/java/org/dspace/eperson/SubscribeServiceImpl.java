/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.eperson;

import java.sql.SQLException;
import java.util.List;

import org.apache.logging.log4j.Logger;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.content.Collection;
import org.dspace.content.DSpaceObject;
import org.dspace.content.service.CollectionService;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.core.LogHelper;
import org.dspace.eperson.dao.SubscriptionDAO;
import org.dspace.eperson.service.SubscribeService;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Class defining methods for sending new item e-mail alerts to users
 *
 * @author Robert Tansley
 * @version $Revision$
 */
public class SubscribeServiceImpl implements SubscribeService {
    /**
     * log4j logger
     */
    private Logger log = org.apache.logging.log4j.LogManager.getLogger(SubscribeServiceImpl.class);

    @Autowired(required = true)
    protected SubscriptionDAO subscriptionDAO;
    @Autowired(required = true)
    protected AuthorizeService authorizeService;
    @Autowired(required = true)
    protected CollectionService collectionService;

    protected SubscribeServiceImpl() {

    }

    @Override
    public List<Subscription> findAll(Context context, String resourceType,
                                      Integer limit, Integer offset) throws Exception {
        if (resourceType == null) {
            return subscriptionDAO.findAllOrderedByDSO(context, limit, offset);
        } else {
            if (resourceType.equals("Item") || resourceType.equals("Collection") || resourceType.equals("Community")) {
                return subscriptionDAO.findAllOrderedByIDAndResourceType(context, resourceType, limit, offset);
            } else {
                log.error("Resource type must be Item, Collection or Community");
                throw new Exception("Resource type must be Item, Collection or Community");
            }
        }
    }

    @Override
    public Subscription subscribe(Context context, EPerson eperson,
                                  DSpaceObject dSpaceObject,
                                  List<SubscriptionParameter> subscriptionParameterList,
                                  String type) throws SQLException, AuthorizeException {
        // Check authorisation. Must be administrator, or the eperson.
        if (authorizeService.isAdmin(context)
                || ((context.getCurrentUser() != null) && (context
                .getCurrentUser().getID().equals(eperson.getID())))) {
            Subscription newSubscription = subscriptionDAO.create(context, new Subscription());
            subscriptionParameterList.forEach(subscriptionParameter ->
                    newSubscription.addParameter(subscriptionParameter));
            newSubscription.setePerson(eperson);
            newSubscription.setdSpaceObject(dSpaceObject);
            newSubscription.setType(type);
            return newSubscription;
        } else {
            throw new AuthorizeException(
                    "Only admin or e-person themselves can subscribe");
        }
    }

    @Override
    public void unsubscribe(Context context, EPerson eperson,
                            DSpaceObject dSpaceObject) throws SQLException, AuthorizeException {
        // Check authorisation. Must be administrator, or the eperson.
        if (authorizeService.isAdmin(context)
                || ((context.getCurrentUser() != null) && (context
                .getCurrentUser().getID().equals(eperson.getID())))) {
            if (dSpaceObject == null) {
                // Unsubscribe from all
                subscriptionDAO.deleteByEPerson(context, eperson);
            } else {
                subscriptionDAO.deleteByDSOAndEPerson(context, dSpaceObject, eperson);

                log.info(LogHelper.getHeader(context, "unsubscribe",
                                              "eperson_id=" + eperson.getID() + ",collection_id="
                                                  + dSpaceObject.getID()));
            }
        } else {
            throw new AuthorizeException(
                    "Only admin or e-person themselves can unsubscribe");
        }
    }

    @Override
    public List<Subscription> getSubscriptionsByEPerson(Context context, EPerson eperson, Integer limit, Integer offset)
            throws SQLException {
        return subscriptionDAO.findByEPerson(context, eperson, limit, offset);
    }

    @Override
    public List<Subscription> getSubscriptionsByEPersonAndDso(Context context,
                                                              EPerson eperson, DSpaceObject dSpaceObject,
                                                              Integer limit, Integer offset)
            throws SQLException {
        return subscriptionDAO.findByEPersonAndDso(context, eperson, dSpaceObject, limit, offset);
    }

    @Override
    public List<Collection> getAvailableSubscriptions(Context context)
            throws SQLException {
        return getAvailableSubscriptions(context, null);
    }

    @Override
    public List<Collection> getAvailableSubscriptions(Context context, EPerson eperson)
            throws SQLException {
        List<Collection> collections;
        if (eperson != null) {
            context.setCurrentUser(eperson);
        }
        collections = collectionService.findAuthorized(context, null, Constants.ADD);
        return collections;
    }

    @Override
    public boolean isSubscribed(Context context, EPerson eperson,
                                DSpaceObject dSpaceObject) throws SQLException {
        return subscriptionDAO.findByEPersonAndDso(context, eperson, dSpaceObject, -1, -1) != null;
    }

    @Override
    public void deleteByDspaceObject(Context context, DSpaceObject dSpaceObject) throws SQLException {
        subscriptionDAO.deleteByDspaceObject(context, dSpaceObject);
    }

    @Override
    public void deleteByEPerson(Context context, EPerson ePerson) throws SQLException {
        subscriptionDAO.deleteByEPerson(context, ePerson);
    }

    @Override
    public Subscription findById(Context context, int id) throws SQLException, AuthorizeException {
        Subscription subscription = subscriptionDAO.findByID(context, Subscription.class, id);
        if (context.getCurrentUser().equals(subscription.getePerson()) ||
                authorizeService.isAdmin(context, context.getCurrentUser())) {
            return subscription;
        }
        throw new AuthorizeException("Only admin or e-person themselves can edit the subscription");
    }

    @Override
    public Subscription updateSubscription(Context context, Integer id,
                                           EPerson eperson,
                                           DSpaceObject dSpaceObject,
                                           List<SubscriptionParameter> subscriptionParameterList,
                                           String type) throws SQLException, AuthorizeException {
        // must be admin or the subscriber of the subscription
        if (authorizeService.isAdmin(context, context.getCurrentUser()) || eperson.equals(context.getCurrentUser())) {
            Subscription subscriptionDB = subscriptionDAO.findByID(context, Subscription.class, id);
            subscriptionDB.removeParameterList();
            subscriptionDB.setType(type);
            subscriptionDB.setdSpaceObject(dSpaceObject);
            subscriptionParameterList.forEach(subscriptionParameter ->
                    subscriptionDB.addParameter(subscriptionParameter));
            subscriptionDB.setePerson(eperson);
            subscriptionDAO.save(context, subscriptionDB);
            return subscriptionDB;
        } else {
            throw new AuthorizeException("Only admin or e-person themselves can edit the subscription");
        }
    }

    @Override
    public Subscription addSubscriptionParameter(Context context, Integer id,
                   SubscriptionParameter subscriptionParameter) throws SQLException, AuthorizeException {
        // must be admin or the subscriber of the subscription
        Subscription subscriptionDB = subscriptionDAO.findByID(context, Subscription.class, id);
        if (authorizeService.isAdmin(context, context.getCurrentUser())
                || subscriptionDB.getePerson().equals(context.getCurrentUser())) {
            subscriptionDB.addParameter(subscriptionParameter);
            subscriptionDAO.save(context, subscriptionDB);
            return subscriptionDB;
        } else {
            throw new AuthorizeException("Only admin or e-person themselves can edit the subscription");
        }
    }

    @Override
    public Subscription removeSubscriptionParameter(Context context, Integer id,
                       SubscriptionParameter subscriptionParameter) throws SQLException, AuthorizeException {
        // must be admin or the subscriber of the subscription
        Subscription subscriptionDB = subscriptionDAO.findByID(context, Subscription.class, id);
        if (authorizeService.isAdmin(context, context.getCurrentUser())
                || subscriptionDB.getePerson().equals(context.getCurrentUser())) {
            subscriptionDB.removeParameter(subscriptionParameter);
            subscriptionDAO.save(context, subscriptionDB);
            return subscriptionDB;
        } else {
            throw new AuthorizeException("Only admin or e-person themselves can edit the subscription");
        }
    }

    @Override
    public void deleteSubscription(Context context, Integer id) throws SQLException, AuthorizeException {
        // initially find the eperson associated with the subscription
        Subscription subscription = subscriptionDAO.findByID(context, Subscription.class, id);
        if (subscription != null) {
            // must be admin or the subscriber of the subscription
            if (authorizeService.isAdmin(context, context.getCurrentUser())
                    || subscription.getePerson().equals(context.getCurrentUser())) {
                try {
                    subscriptionDAO.delete(context, subscription);
                } catch (SQLException sqlException) {
                    throw new SQLException(sqlException);
                }

            } else {
                throw new AuthorizeException("Only admin or e-person themselves can delete the subscription");
            }
        } else {
            throw new IllegalArgumentException("Subscription with id " + id + " is not found");
        }

    }

    @Override
    public List<Subscription> findAllSubscriptionsByTypeAndFrequency(Context context,
                                     String type, String frequencyValue) throws SQLException {
        return subscriptionDAO.findAllSubscriptionsByTypeAndFrequency(context, type, frequencyValue);
    }

    @Override
    public Long countAll(Context context) throws SQLException {
        return subscriptionDAO.countAll(context);
    }

    @Override
    public Long countAllByEPerson(Context context, EPerson ePerson) throws SQLException {
        return subscriptionDAO.countAllByEPerson(context, ePerson);
    }

    @Override
    public Long countAllByEPersonAndDSO(Context context,
           EPerson ePerson, DSpaceObject dSpaceObject) throws SQLException {
        return subscriptionDAO.countAllByEPersonAndDso(context, ePerson, dSpaceObject);
    }
}
