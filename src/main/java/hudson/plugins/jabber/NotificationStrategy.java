package hudson.plugins.jabber;

import hudson.model.Build;
import hudson.model.Result;
import hudson.plugins.jabber.tools.Assert;

/**
 * Represents the notification strategy.
 * @author Uwe Schaefer
 */
public enum NotificationStrategy {

    /**
     * Notifications should be sent only if there was a change in the build state, or this was the first build. 
     */
    STATECHANGE_ONLY {

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean notificationWanted(final Build<?,?> build)
        {
            Assert.isNotNull(build, "Parameter 'build' must not be null.");
            final Build<?,?> previousBuild = build.getPreviousBuild();
            return (previousBuild == null) || (build.getResult() != previousBuild.getResult());
        }
    },

    /**
     * Not matter what, notifications sould always be sent.
     */
    ALL {
        /**
         * {@inheritDoc}
         */
        @Override
        public boolean notificationWanted(final Build build)
        {
            return true;
        }
    },

    /**
     * Whenever there is a failure, a Notification should be sent. 
     */
    ANY_FAILURE {
        /**
         * {@inheritDoc}
         */
        @Override
        public boolean notificationWanted(final Build<?,?> build)
        {
            Assert.isNotNull(build, "Parameter 'build' must not be null.");
            return build.getResult() != Result.SUCCESS;

        }
    };

    /**
     * Signals if the given build qualifies to send a notification according to the current strategy.
     * @param build The build for which it should be decided, if notification is wanted or not. 
     * @return true if, according to the given strategy, a notification should be sent.
     */
    public abstract boolean notificationWanted(Build<?,?> build);

}
