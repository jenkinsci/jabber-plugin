package hudson.plugins.jabber;

import hudson.model.AbstractBuild;
import hudson.model.Result;
import hudson.plugins.jabber.tools.Assert;

/**
 * Represents the notification strategy.
 * 
 * @author Uwe Schaefer
 */
public enum NotificationStrategy {

    // Note that the order of the constants also specifies the display order!

	/**
	 * Not matter what, notifications should always be sent.
	 */
	ALL("all") {
		/**
		 * {@inheritDoc}
		 */
		@Override
		public boolean notificationWanted(final AbstractBuild<?, ?> build) {
			return true;
		}
	},

	/**
	 * Whenever there is a failure, a Notification should be sent.
	 */
	ANY_FAILURE("failure") {
		/**
		 * {@inheritDoc}
		 */
		@Override
		public boolean notificationWanted(final AbstractBuild<?, ?> build) {
			Assert.isNotNull(build, "Parameter 'build' must not be null.");
			return build.getResult() != Result.SUCCESS;

		}
	},

	/**
	 * Whenever there is a failure or a failure was fixed, a Notification should be sent.
	 */
	FAILURE_AND_FIXED("failure and fixed") {
		/**
		 * {@inheritDoc}
		 */
		@Override
		public boolean notificationWanted(final AbstractBuild<?, ?> build) {
			Assert.isNotNull(build, "Parameter 'build' must not be null.");
                        if (build.getResult() != Result.SUCCESS) {
                            return true;
                        }
                        final AbstractBuild<?, ?> previousBuild = build.getPreviousBuild();
                        if (previousBuild == null) {
                            return false;
                        } else {
                            return previousBuild.getResult() != Result.SUCCESS;
                        }
		}
	},

        /**
	 * Notifications should be sent only if there was a change in the build
	 * state, or this was the first build.
	 */
	STATECHANGE_ONLY("change") {

		/**
		 * {@inheritDoc}
		 */
		@Override
		public boolean notificationWanted(final AbstractBuild<?, ?> build) {
			Assert.isNotNull(build, "Parameter 'build' must not be null.");
			final AbstractBuild<?, ?> previousBuild = build.getPreviousBuild();
			return (previousBuild == null)
					|| (build.getResult() != previousBuild.getResult());
		}
	};

        private final String displayName;

        private NotificationStrategy(String displayName) {
            this.displayName = displayName;
        }
	/**
	 * Signals if the given build qualifies to send a notification according to
	 * the current strategy.
	 * 
	 * @param build
	 *            The build for which it should be decided, if notification is
	 *            wanted or not.
	 * @return true if, according to the given strategy, a notification should
	 *         be sent.
	 */
	public abstract boolean notificationWanted(AbstractBuild<?, ?> build);

        /**
         * Returns the name of the strategy to display in dialogs etc.
         *
         * @return the display name
         */
        public String getDisplayName() {
            return this.displayName;
        }

        /**
         * Returns the notififaction strategy with the given display name.
         *
         * @param displayName the display name
         * @return the notification strategy or null
         */
        public static NotificationStrategy forDisplayName(String displayName) {
            for (NotificationStrategy strategy : values()) {
                if (strategy.getDisplayName().equals(displayName)) {
                    return strategy;
                }
            }
            return null;
        }
}
