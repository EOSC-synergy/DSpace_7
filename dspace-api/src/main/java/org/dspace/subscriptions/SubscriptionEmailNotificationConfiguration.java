/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */

package org.dspace.subscriptions;

import java.sql.SQLException;

import org.apache.commons.cli.Options;
import org.dspace.authorize.AuthorizeServiceImpl;
import org.dspace.core.Context;
import org.dspace.scripts.DSpaceRunnable;
import org.dspace.scripts.configuration.ScriptConfiguration;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Implementation of {@link DSpaceRunnable} to find subscribed objects and send notification mails about them
 *
 * @author alba aliu
 */

public class SubscriptionEmailNotificationConfiguration<T
        extends SubscriptionEmailNotification> extends ScriptConfiguration<T> {

    private Class<T> dspaceRunnableClass;

    @Autowired
    private AuthorizeServiceImpl authorizeService;

    @Override
    public Class<T> getDspaceRunnableClass() {
        return dspaceRunnableClass;
    }

    @Override
    public void setDspaceRunnableClass(Class<T> dspaceRunnableClass) {
        this.dspaceRunnableClass = dspaceRunnableClass;
    }

    @Override
    public boolean isAllowedToExecute(Context context) {
        try {
            return authorizeService.isAdmin(context);
        } catch (SQLException e) {
            throw new RuntimeException("SQLException occurred when checking if the current user is an admin", e);
        }
    }

    @Override
    public Options getOptions() {
        if (options == null) {
            Options options = new Options();
            options.addOption("t", "Type", true,
                    "Subscription type, It can have values content or statistics");
            options.getOption("t").setRequired(true);
            options.addOption("f", "Frequency", true,
                    "Subscription frequency. It can have value D, M or W");
            options.getOption("f").setRequired(true);
            super.options = options;
        }
        return options;
    }
}
