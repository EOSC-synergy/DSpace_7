/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.repository;

import java.sql.SQLException;
import java.util.UUID;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;

import org.dspace.app.nbevent.service.NBEventService;
import org.dspace.app.rest.model.ItemRest;
import org.dspace.app.rest.model.NBEventRest;
import org.dspace.app.rest.projection.Projection;
import org.dspace.content.Item;
import org.dspace.content.NBEvent;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

/**
 * Link repository for "related" subresource of a nb event.
 *
 * @author Andrea Bollini (andrea.bollini at 4science.it)
 *
 */
@Component(NBEventRest.CATEGORY + "." + NBEventRest.NAME + "." + NBEventRest.RELATED)
public class NBEventRelatedLinkRepository extends AbstractDSpaceRestRepository implements LinkRestRepository {

    @Autowired
    private NBEventService nbEventService;

    @Autowired
    private ItemService itemService;

    /**
     * Returns the item related to the nb event with the given id. This is another
     * item that should be linked to the target item as part of the correction
     *
     * @param request    the http servlet request
     * @param id         the nb event id
     * @param pageable   the optional pageable
     * @param projection the projection object
     * @return the item rest representation of the secondary item related to nb event
     */
    @PreAuthorize("hasAuthority('ADMIN')")
    public ItemRest getRelated(@Nullable HttpServletRequest request, String id, @Nullable Pageable pageable,
            Projection projection) {
        Context context = obtainContext();
        NBEvent nbEvent = nbEventService.findEventByEventId(context, id);
        if (nbEvent == null) {
            throw new ResourceNotFoundException("No nb event with ID: " + id);
        }
        if (nbEvent.getRelated() == null) {
            return null;
        }
        UUID itemUuid = UUID.fromString(nbEvent.getRelated());
        Item item;
        try {
            item = itemService.find(context, itemUuid);
            if (item == null) {
                throw new ResourceNotFoundException("No related item found with id : " + id);
            }
            return converter.toRest(item, projection);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
