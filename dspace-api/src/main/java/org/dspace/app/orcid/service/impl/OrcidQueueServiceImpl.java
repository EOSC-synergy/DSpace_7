/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.orcid.service.impl;

import static org.apache.commons.lang3.ArrayUtils.contains;
import static org.dspace.app.profile.OrcidEntitySyncPreference.DISABLED;
import static org.springframework.util.StringUtils.capitalize;

import java.sql.SQLException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.dspace.app.orcid.OrcidOperation;
import org.dspace.app.orcid.OrcidQueue;
import org.dspace.app.orcid.dao.OrcidQueueDAO;
import org.dspace.app.orcid.model.OrcidEntityType;
import org.dspace.app.orcid.service.OrcidHistoryService;
import org.dspace.app.orcid.service.OrcidQueueService;
import org.dspace.app.profile.OrcidEntitySyncPreference;
import org.dspace.content.Item;
import org.dspace.content.MetadataFieldName;
import org.dspace.content.authority.service.ChoiceAuthorityService;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.dspace.discovery.DiscoverQuery;
import org.dspace.discovery.DiscoverResultItemIterator;
import org.dspace.discovery.indexobject.IndexableItem;
import org.dspace.services.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Implementation of {@link OrcidQueueService}.
 *
 * @author Luca Giamminonni (luca.giamminonni at 4science.it)
 *
 */
public class OrcidQueueServiceImpl implements OrcidQueueService {

    @Autowired
    private OrcidQueueDAO orcidQueueDAO;

    @Autowired
    private OrcidHistoryService orcidHistoryService;

    @Autowired
    private ItemService itemService;

    @Autowired
    private ChoiceAuthorityService choiceAuthorityService;

    @Autowired
    private ConfigurationService configurationService;

    @Override
    public List<OrcidQueue> findByOwnerId(Context context, UUID ownerId) throws SQLException {
        return orcidQueueDAO.findByOwnerId(context, ownerId, -1, 0);
    }

    @Override
    public List<OrcidQueue> findByOwnerId(Context context, UUID ownerId, Integer limit, Integer offset)
        throws SQLException {
        return orcidQueueDAO.findByOwnerId(context, ownerId, limit, offset);
    }

    @Override
    public List<OrcidQueue> findByOwnerAndEntity(Context context, Item owner, Item entity) throws SQLException {
        return orcidQueueDAO.findByOwnerAndEntity(context, owner, entity);
    }

    @Override
    public List<OrcidQueue> findByOwnerOrEntity(Context context, Item item) throws SQLException {
        return orcidQueueDAO.findByOwnerOrEntity(context, item);
    }

    @Override
    public long countByOwnerId(Context context, UUID ownerId) throws SQLException {
        return orcidQueueDAO.countByOwnerId(context, ownerId);
    }

    @Override
    public List<OrcidQueue> findAll(Context context) throws SQLException {
        return orcidQueueDAO.findAll(context, OrcidQueue.class);
    }

    @Override
    public OrcidQueue create(Context context, Item owner, Item entity) throws SQLException {
        Optional<String> putCode = orcidHistoryService.findLastPutCode(context, owner, entity);
        if (putCode.isPresent()) {
            return createEntityUpdateRecord(context, owner, entity, putCode.get());
        } else {
            return createEntityInsertionRecord(context, owner, entity);
        }
    }

    @Override
    public OrcidQueue createEntityInsertionRecord(Context context, Item owner, Item entity) throws SQLException {
        OrcidQueue orcidQueue = new OrcidQueue();
        orcidQueue.setEntity(entity);
        orcidQueue.setRecordType(itemService.getEntityType(entity));
        orcidQueue.setOwner(owner);
        orcidQueue.setDescription(getMetadataValue(entity, "dc.title"));
        orcidQueue.setOperation(OrcidOperation.INSERT);
        return orcidQueueDAO.create(context, orcidQueue);
    }

    @Override
    public OrcidQueue createEntityUpdateRecord(Context context, Item owner, Item entity, String putCode)
        throws SQLException {
        OrcidQueue orcidQueue = new OrcidQueue();
        orcidQueue.setOwner(owner);
        orcidQueue.setEntity(entity);
        orcidQueue.setPutCode(putCode);
        orcidQueue.setRecordType(itemService.getEntityType(entity));
        orcidQueue.setDescription(getMetadataValue(entity, "dc.title"));
        orcidQueue.setOperation(OrcidOperation.UPDATE);
        return orcidQueueDAO.create(context, orcidQueue);
    }

    @Override
    public OrcidQueue createEntityDeletionRecord(Context context, Item owner, String description, String type,
        String putCode)
        throws SQLException {
        OrcidQueue orcidQueue = new OrcidQueue();
        orcidQueue.setRecordType(type);
        orcidQueue.setOwner(owner);
        orcidQueue.setPutCode(putCode);
        orcidQueue.setDescription(description);
        orcidQueue.setOperation(OrcidOperation.DELETE);
        return orcidQueueDAO.create(context, orcidQueue);
    }

    @Override
    public OrcidQueue createProfileInsertionRecord(Context context, Item profile, String description, String recordType,
        String metadata) throws SQLException {
        OrcidQueue orcidQueue = new OrcidQueue();
        orcidQueue.setEntity(profile);
        orcidQueue.setRecordType(recordType);
        orcidQueue.setOwner(profile);
        orcidQueue.setDescription(description);
        orcidQueue.setMetadata(metadata);
        orcidQueue.setOperation(OrcidOperation.INSERT);
        return orcidQueueDAO.create(context, orcidQueue);
    }

    @Override
    public OrcidQueue createProfileDeletionRecord(Context context, Item profile, String description, String recordType,
        String metadata, String putCode) throws SQLException {
        OrcidQueue orcidQueue = new OrcidQueue();
        orcidQueue.setEntity(profile);
        orcidQueue.setRecordType(recordType);
        orcidQueue.setOwner(profile);
        orcidQueue.setDescription(description);
        orcidQueue.setPutCode(putCode);
        orcidQueue.setMetadata(metadata);
        orcidQueue.setOperation(OrcidOperation.DELETE);
        return orcidQueueDAO.create(context, orcidQueue);
    }

    @Override
    public void deleteById(Context context, Integer id) throws SQLException {
        OrcidQueue orcidQueue = orcidQueueDAO.findByID(context, OrcidQueue.class, id);
        if (orcidQueue != null) {
            delete(context, orcidQueue);
        }
    }

    @Override
    public List<OrcidQueue> findByAttemptsLessThan(Context context, int attempts) throws SQLException {
        return orcidQueueDAO.findByAttemptsLessThan(context, attempts);
    }

    @Override
    public void delete(Context context, OrcidQueue orcidQueue) throws SQLException {
        orcidQueueDAO.delete(context, orcidQueue);
    }

    @Override
    public void deleteByEntityAndRecordType(Context context, Item entity, String recordType) throws SQLException {
        List<OrcidQueue> records = orcidQueueDAO.findByEntityAndRecordType(context, entity, recordType);
        for (OrcidQueue record : records) {
            orcidQueueDAO.delete(context, record);
        }
    }

    @Override
    public void deleteByOwnerAndRecordType(Context context, Item owner, String recordType) throws SQLException {
        List<OrcidQueue> records = orcidQueueDAO.findByOwnerAndRecordType(context, owner, recordType);
        for (OrcidQueue record : records) {
            orcidQueueDAO.delete(context, record);
        }
    }

    @Override
    public OrcidQueue find(Context context, int id) throws SQLException {
        return orcidQueueDAO.findByID(context, OrcidQueue.class, id);
    }

    @Override
    public void update(Context context, OrcidQueue orcidQueue) throws SQLException {
        orcidQueueDAO.save(context, orcidQueue);
    }

    @Override
    public void recalculateOrcidQueue(Context context, Item owner, OrcidEntityType orcidEntityType,
        OrcidEntitySyncPreference preference) throws SQLException {

        String entityType = capitalize(orcidEntityType.name().toLowerCase());
        if (preference == DISABLED) {
            deleteByOwnerAndRecordType(context, owner, entityType);
        } else {
            Iterator<Item> entities = findAllEntitiesLinkableWith(context, owner, entityType);
            while (entities.hasNext()) {
                create(context, owner, entities.next());
            }
        }

    }

    private Iterator<Item> findAllEntitiesLinkableWith(Context context, Item owner, String entityType) {

        String ownerType = itemService.getMetadataFirstValue(owner, "dspace", "entity", "type", Item.ANY);

        String query = choiceAuthorityService.getAuthorityControlledFieldsByEntityType(ownerType).stream()
            .map(metadataField -> metadataField.replaceAll("_", "."))
            .filter(metadataField -> shouldNotBeIgnoredForOrcid(metadataField))
            .map(metadataField -> metadataField + "_allauthority: \"" + owner.getID().toString() + "\"")
            .collect(Collectors.joining(" OR "));

        if (StringUtils.isEmpty(query)) {
            return Collections.emptyIterator();
        }

        DiscoverQuery discoverQuery = new DiscoverQuery();
        discoverQuery.addDSpaceObjectFilter(IndexableItem.TYPE);
        discoverQuery.addFilterQueries(query);
        discoverQuery.addFilterQueries("search.entitytype:" + entityType);

        return new DiscoverResultItemIterator(context, discoverQuery);

    }

    private boolean shouldNotBeIgnoredForOrcid(String metadataField) {
        return !contains(configurationService.getArrayProperty("orcid.linkable-metadata-fields.ignore"), metadataField);
    }

    private String getMetadataValue(Item item, String metadatafield) {
        return itemService.getMetadataFirstValue(item, new MetadataFieldName(metadatafield), Item.ANY);
    }
}
