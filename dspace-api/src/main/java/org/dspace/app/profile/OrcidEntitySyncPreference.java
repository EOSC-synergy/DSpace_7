/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.profile;

/**
 * Enum that model the allowed values to configure the ORCID synchronization
 * preferences.
 *
 * @author Luca Giamminonni (luca.giamminonni at 4science.it)
 *
 */
public enum OrcidEntitySyncPreference {

    DISABLED,
    ALL,
    MY_SELECTED,
    MINE;
}
