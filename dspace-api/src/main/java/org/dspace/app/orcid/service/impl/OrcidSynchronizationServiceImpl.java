/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.orcid.service.impl;

import static java.time.LocalDateTime.now;
import static java.time.format.DateTimeFormatter.ISO_DATE_TIME;
import static java.util.List.of;
import static org.apache.commons.collections.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.EnumUtils.isValidEnum;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.dspace.app.profile.OrcidEntitySyncPreference.DISABLED;
import static org.dspace.content.Item.ANY;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.codec.binary.StringUtils;
import org.dspace.app.orcid.OrcidQueue;
import org.dspace.app.orcid.model.OrcidEntityType;
import org.dspace.app.orcid.model.OrcidTokenResponseDTO;
import org.dspace.app.orcid.service.OrcidQueueService;
import org.dspace.app.orcid.service.OrcidSynchronizationService;
import org.dspace.app.orcid.service.OrcidWebhookService;
import org.dspace.app.profile.OrcidEntitySyncPreference;
import org.dspace.app.profile.OrcidProfileDisconnectionMode;
import org.dspace.app.profile.OrcidProfileSyncPreference;
import org.dspace.app.profile.OrcidSynchronizationMode;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.dspace.discovery.DiscoverQuery;
import org.dspace.discovery.DiscoverResultItemIterator;
import org.dspace.discovery.indexobject.IndexableItem;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.service.EPersonService;
import org.dspace.services.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Implementation of {@link OrcidSynchronizationService}.
 *
 * @author Luca Giamminonni (luca.giamminonni at 4science.it)
 *
 */
public class OrcidSynchronizationServiceImpl implements OrcidSynchronizationService {

    @Autowired
    private ItemService itemService;

    @Autowired
    private OrcidQueueService orcidQueueService;

    @Autowired
    private ConfigurationService configurationService;

    @Autowired
    private OrcidWebhookService orcidWebhookService;

    @Autowired
    private EPersonService ePersonService;

    @Override
    public void linkProfile(Context context, Item profile, OrcidTokenResponseDTO token) throws SQLException {

        String orcid = token.getOrcid();
        String accessToken = token.getAccessToken();
        String refreshToken = token.getRefreshToken();
        String[] scopes = token.getScopeAsArray();

        itemService.setMetadataSingleValue(context, profile, "person", "identifier", "orcid", null, orcid);
        itemService.setMetadataSingleValue(context, profile, "cris", "orcid", "access-token", null, accessToken);
        itemService.setMetadataSingleValue(context, profile, "cris", "orcid", "refresh-token", null, refreshToken);
        itemService.clearMetadata(context, profile, "cris", "orcid", "scope", Item.ANY);
        for (String scope : scopes) {
            itemService.addMetadata(context, profile, "cris", "orcid", "scope", null, scope);
        }

        if (isBlank(itemService.getMetadataFirstValue(profile, "cris", "orcid", "authenticated", Item.ANY))) {
            String currentDate = ISO_DATE_TIME.format(now());
            itemService.setMetadataSingleValue(context, profile, "cris", "orcid", "authenticated", null, currentDate);
        }

        EPerson ePerson = ePersonService.findByProfileItem(context, profile);
        EPerson ePersonByOrcid = ePersonService.findByNetid(context, orcid);
        if (ePerson != null && ePersonByOrcid == null && isBlank(ePerson.getNetid())) {
            ePerson.setNetid(orcid);
            updateEPerson(context, ePerson);
        }

        updateItem(context, profile);

    }

    @Override
    public void unlinkProfile(Context context, Item profile) throws SQLException {

        if (orcidWebhookService.isProfileRegistered(profile)) {
            orcidWebhookService.unregister(context, profile);
        }

        itemService.clearMetadata(context, profile, "person", "identifier", "orcid", Item.ANY);
        itemService.clearMetadata(context, profile, "cris", "orcid", "access-token", Item.ANY);
        itemService.clearMetadata(context, profile, "cris", "orcid", "refresh-token", Item.ANY);
        itemService.clearMetadata(context, profile, "cris", "orcid", "scope", Item.ANY);
        itemService.clearMetadata(context, profile, "cris", "orcid", "authenticated", Item.ANY);
        updateItem(context, profile);

        List<OrcidQueue> queueRecords = orcidQueueService.findByOwnerId(context, profile.getID());
        for (OrcidQueue queueRecord : queueRecords) {
            orcidQueueService.delete(context, queueRecord);
        }

    }

    @Override
    public boolean setEntityPreference(Context context, Item profile, OrcidEntityType type,
        OrcidEntitySyncPreference value) throws SQLException {
        String metadataQualifier = "sync-" + type.name().toLowerCase() + "s";
        return updatePreferenceForSynchronizingWithOrcid(context, profile, metadataQualifier, of(value.name()));
    }

    @Override
    public boolean setProfilePreference(Context context, Item profile, List<OrcidProfileSyncPreference> values)
        throws SQLException {

        List<String> valuesAsString = values.stream()
            .map(OrcidProfileSyncPreference::name)
            .collect(Collectors.toList());

        return updatePreferenceForSynchronizingWithOrcid(context, profile, "sync-profile", valuesAsString);

    }

    @Override
    public boolean setSynchronizationMode(Context context, Item profile, OrcidSynchronizationMode value)
        throws SQLException {

        if (!isLinkedToOrcid(profile)) {
            throw new IllegalArgumentException("The given profile cannot be configured for the ORCID "
                + "synchronization because it is not linked to any ORCID account: " + profile.getID());
        }

        String newValue = value.name();
        String oldValue = itemService.getMetadataFirstValue(profile, "cris", "orcid", "sync-mode", Item.ANY);

        if (StringUtils.equals(oldValue, newValue)) {
            return false;
        } else {
            itemService.setMetadataSingleValue(context, profile, "cris", "orcid", "sync-mode", null, value.name());
            return true;
        }

    }

    @Override
    public boolean isSynchronizationEnabled(Item profile, Item item) {

        String entityType = itemService.getEntityType(item);
        if (entityType == null) {
            return false;
        }

        if (OrcidEntityType.isValid(entityType)) {
            return getEntityPreference(profile, OrcidEntityType.fromString(entityType))
                .filter(pref -> pref != DISABLED)
                .isPresent();
        }

        if (entityType.equals(getProfileType())) {
            return profile.equals(item) && !isEmpty(getProfilePreferences(profile));
        }

        return false;

    }

    @Override
    public Optional<OrcidSynchronizationMode> getSynchronizationMode(Item item) {
        return getMetadataValue(item, "cris.orcid.sync-mode")
            .map(metadataValue -> metadataValue.getValue())
            .filter(value -> isValidEnum(OrcidSynchronizationMode.class, value))
            .map(value -> OrcidSynchronizationMode.valueOf(value));
    }

    @Override
    public Optional<OrcidEntitySyncPreference> getEntityPreference(Item item, OrcidEntityType entityType) {
        return getMetadataValue(item, "cris.orcid.sync-" + entityType.name().toLowerCase() + "s")
            .map(metadataValue -> metadataValue.getValue())
            .filter(value -> isValidEnum(OrcidEntitySyncPreference.class, value))
            .map(value -> OrcidEntitySyncPreference.valueOf(value));
    }

    @Override
    public List<OrcidProfileSyncPreference> getProfilePreferences(Item item) {
        return getMetadataValues(item, "cris.orcid.sync-profile")
            .map(MetadataValue::getValue)
            .filter(value -> isValidEnum(OrcidProfileSyncPreference.class, value))
            .map(value -> OrcidProfileSyncPreference.valueOf(value))
            .collect(Collectors.toList());
    }

    @Override
    public boolean isLinkedToOrcid(Item item) {
        return getOrcidAccessToken(item).isPresent() && getOrcid(item).isPresent();
    }

    @Override
    public OrcidProfileDisconnectionMode getDisconnectionMode() {
        String value = configurationService.getProperty("orcid.disconnection.allowed-users");
        if (!OrcidProfileDisconnectionMode.isValid(value)) {
            return OrcidProfileDisconnectionMode.DISABLED;
        }
        return OrcidProfileDisconnectionMode.fromString(value);
    }

    @Override
    public Iterator<Item> findProfilesByOrcid(Context context, String orcid) {
        DiscoverQuery discoverQuery = new DiscoverQuery();
        discoverQuery.setDSpaceObjectFilter(IndexableItem.TYPE);
        discoverQuery.addFilterQueries("search.entitytype:" + getProfileType());
        discoverQuery.addFilterQueries("person.identifier.orcid:" + orcid);
        return new DiscoverResultItemIterator(context, discoverQuery);
    }

    @Override
    public Iterator<Item> findProfilesWithOrcid(Context context) {
        return findProfilesByOrcid(context, "*");
    }

    private boolean updatePreferenceForSynchronizingWithOrcid(Context context, Item profile,
        String metadataQualifier, List<String> values) throws SQLException {

        if (!isLinkedToOrcid(profile)) {
            throw new IllegalArgumentException("The given profile cannot be configured for the ORCID "
                + "synchronization because it is not linked to any ORCID account: " + profile.getID());
        }

        List<String> oldValues = itemService.getMetadata(profile, "cris", "orcid", metadataQualifier, ANY).stream()
            .map(metadataValue -> metadataValue.getValue())
            .collect(Collectors.toList());

        if (containsSameValues(oldValues, values)) {
            return false;
        }

        itemService.clearMetadata(context, profile, "cris", "orcid", metadataQualifier, ANY);
        for (String value : values) {
            itemService.addMetadata(context, profile, "cris", "orcid", metadataQualifier, null, value);
        }

        return true;

    }

    private boolean containsSameValues(List<String> firstList, List<String> secondList) {
        return new HashSet<>(firstList).equals(new HashSet<>(secondList));
    }

    private Optional<String> getOrcidAccessToken(Item item) {
        return getMetadataValue(item, "cris.orcid.access-token")
            .map(metadataValue -> metadataValue.getValue());
    }

    public Optional<String> getOrcid(Item item) {
        return getMetadataValue(item, "person.identifier.orcid")
            .map(metadataValue -> metadataValue.getValue());
    }

    private Optional<MetadataValue> getMetadataValue(Item item, String metadataField) {
        return getMetadataValues(item, metadataField).findFirst();
    }

    private Stream<MetadataValue> getMetadataValues(Item item, String metadataField) {
        return item.getMetadata().stream()
            .filter(metadata -> metadataField.equals(metadata.getMetadataField().toString('.')));
    }

    private String getProfileType() {
        return configurationService.getProperty("researcher-profile.type", "Person");
    }

    private void updateItem(Context context, Item item) throws SQLException {
        try {
            context.turnOffAuthorisationSystem();
            itemService.update(context, item);
        } catch (AuthorizeException e) {
            throw new RuntimeException(e);
        } finally {
            context.restoreAuthSystemState();
        }
    }

    private void updateEPerson(Context context, EPerson ePerson) throws SQLException {
        try {
            context.turnOffAuthorisationSystem();
            ePersonService.update(context, ePerson);
        } catch (AuthorizeException e) {
            throw new RuntimeException(e);
        } finally {
            context.restoreAuthSystemState();
        }
    }

}
