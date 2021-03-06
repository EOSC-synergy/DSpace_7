/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.profile;

import static java.time.LocalDateTime.now;
import static java.time.format.DateTimeFormatter.ISO_DATE_TIME;
import static org.apache.commons.collections.CollectionUtils.isNotEmpty;
import static org.dspace.content.Item.ANY;

import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.collections.CollectionUtils;
import org.dspace.app.profile.service.AfterResearcherProfileCreationAction;
import org.dspace.content.Item;
import org.dspace.content.MetadataFieldName;
import org.dspace.content.MetadataValue;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.service.EPersonService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * Implementation of {@link AfterResearcherProfileCreationAction} that copy the
 * ORCID metadata, if any, from the owner to the researcher profile item.
 *
 * @author Luca Giamminonni (luca.giamminonni at 4science.it)
 *
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class OrcidMetadataCopyingAction implements AfterResearcherProfileCreationAction {

    @Autowired
    private ItemService itemService;

    @Autowired
    private EPersonService ePersonService;

    @Override
    public void perform(Context context, ResearcherProfile researcherProfile, EPerson owner) throws SQLException {

        Item item = researcherProfile.getItem();

        copyMetadataValues(context, owner, "eperson.orcid", item, "person.identifier.orcid");
        copyMetadataValues(context, owner, "eperson.orcid.access-token", item, "cris.orcid.access-token");
        copyMetadataValues(context, owner, "eperson.orcid.refresh-token", item, "cris.orcid.refresh-token");
        copyMetadataValues(context, owner, "eperson.orcid.scope", item, "cris.orcid.scope");

        if (isLinkedToOrcid(owner)) {
            String currentDate = ISO_DATE_TIME.format(now());
            itemService.setMetadataSingleValue(context, item, "cris", "orcid", "authenticated", null, currentDate);
        }

    }

    private void copyMetadataValues(Context context, EPerson ePerson, String ePersonMetadataField, Item item,
        String itemMetadataField) throws SQLException {

        List<String> values = getMetadataValues(ePerson, ePersonMetadataField);
        if (CollectionUtils.isEmpty(values)) {
            return;
        }

        MetadataFieldName metadata = new MetadataFieldName(itemMetadataField);
        itemService.clearMetadata(context, item, metadata.schema, metadata.element, metadata.qualifier, ANY);
        itemService.addMetadata(context, item, metadata.schema, metadata.element, metadata.qualifier, null, values);

    }

    private boolean isLinkedToOrcid(EPerson ePerson) {
        return isNotEmpty(getMetadataValues(ePerson, "eperson.orcid"))
            && isNotEmpty(getMetadataValues(ePerson, "eperson.orcid.access-token"));
    }

    private List<String> getMetadataValues(EPerson ePerson, String metadataField) {
        return ePersonService.getMetadataByMetadataString(ePerson, metadataField).stream()
            .map(MetadataValue::getValue)
            .collect(Collectors.toList());
    }

}
