package hudson.plugins.jabber.im;

import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.User;
import hudson.plugins.jabber.NotificationStrategy;
import hudson.plugins.jabber.tools.Assert;
import hudson.plugins.jabber.tools.BuildHelper;
import hudson.plugins.jabber.tools.MessageHelper;
import hudson.plugins.jabber.user.JabberUserProperty;
import hudson.scm.ChangeLogSet;
import hudson.scm.ChangeLogSet.Entry;
import hudson.tasks.BuildStep;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import org.apache.commons.lang.StringUtils;

/**
 * The actual Publisher that sends notification-Messages out to the clients.
 * @author Uwe Schaefer
 *
 */
public abstract class IMPublisher extends Notifier implements BuildStep
{
	private static final Logger LOGGER = Logger.getLogger(IMPublisher.class.getName());
	
    private static final IMMessageTargetConverter CONVERTER = new DefaultIMMessageTargetConverter();
    private final List<IMMessageTarget> targets = new LinkedList<IMMessageTarget>();
    private final NotificationStrategy notificationStrategy;
    private final boolean notifyOnBuildStart;
    private final boolean notifySuspects;
    private final boolean notifyCulprits;
    private final boolean notifyFixers;

    protected IMPublisher(final String targetsAsString, final String notificationStrategyString,
    		final boolean notifyGroupChatsOnBuildStart,
    		final boolean notifySuspects,
    		final boolean notifyCulprits,
    		final boolean notifyFixers) throws IMMessageTargetConversionException
    {
        Assert.isNotNull(targetsAsString, "Parameter 'targetsAsString' must not be null.");
        final String[] split = targetsAsString.split("\\s");
        final IMMessageTargetConverter conv = getIMMessageTargetConverter();
        for (final String fragment : split)
        {
            IMMessageTarget createIMMessageTarget;
            createIMMessageTarget = conv.fromString(fragment);
            if (createIMMessageTarget != null)
            {
                this.targets.add(createIMMessageTarget);
            }
        }

        NotificationStrategy strategy = NotificationStrategy.forDisplayName(notificationStrategyString);
        if (strategy == null) {
        	strategy = NotificationStrategy.STATECHANGE_ONLY;
        }
        this.notificationStrategy = strategy;
        
        this.notifyOnBuildStart = notifyGroupChatsOnBuildStart;
        this.notifySuspects = notifySuspects;
        this.notifyCulprits = notifyCulprits;
        this.notifyFixers = notifyFixers;
    }
    
    protected abstract IMConnection getIMConnection() throws IMException;

    protected IMMessageTargetConverter getIMMessageTargetConverter()
    {
        return IMPublisher.CONVERTER;
    }

    protected NotificationStrategy getNotificationStrategy()
    {
        return notificationStrategy;
    }

    private List<IMMessageTarget> getNotificationTargets()
    {
        return this.targets;
    }

    public final String getTargets()
    {
        final StringBuilder sb = new StringBuilder();
        for (final IMMessageTarget t : this.targets)
        {
        	if ((t instanceof GroupChatIMMessageTarget) && (! t.toString().contains("@conference."))) {
        		sb.append("*");
        	}
            sb.append(t.toString());
            sb.append(" ");
        }
        return sb.toString().trim();
    }
    
    public final String getStrategy() {
        return getNotificationStrategy().getDisplayName();
    }
    
    public final boolean getNotifyOnStart() {
    	return notifyOnBuildStart;
    }
    
    public final boolean getNotifySuspects() {
    	return notifySuspects;
    }
    
    public final boolean getNotifyCulprits() {
    	return notifyCulprits;
    }

    public final boolean getNotifyFixers() {
    	return notifyFixers;
    }

    @Override
    public boolean perform(final AbstractBuild<?,?> build, final Launcher launcher, final BuildListener buildListener)
            throws InterruptedException, IOException
    {
        Assert.isNotNull(build, "Parameter 'build' must not be null.");
        Assert.isNotNull(buildListener, "Parameter 'buildListener' must not be null.");
        if (getNotificationStrategy().notificationWanted(build)) {
            notifyChats(build, buildListener);
        }

        if (this.notifySuspects && BuildHelper.isFailureOrUnstable(build)) {
        	LOGGER.info("Notifying suspects");
        	final String message = "Oh no! You're suspected of having broken " + build.getProject().getName() + ": " + MessageHelper.getBuildURL(build);
        	
        	for (IMMessageTarget target : calculateIMTargets(getCommitters(build), buildListener.getLogger())) {
        		try {
        			buildListener.getLogger().append("Sending notification to suspect: " + target.toString() + "\n");
        			getIMConnection().send(target, message);
        		} catch (final Throwable e) {
        			buildListener.getLogger().append("There was an error sending suspect notification to: " + target.toString() + "\n");
        		}
        	}
        }
        
        if (this.notifyCulprits && BuildHelper.isFailureOrUnstable(build)) {
        	LOGGER.info("Notifying culprits");
        	final String message = "You're still being suspected of having broken " + build.getProject().getName() + ": " + MessageHelper.getBuildURL(build);
        	
        	for (IMMessageTarget target : calculateIMTargets(getCulpritsOnly(build), buildListener.getLogger())) {
        		try {
        			buildListener.getLogger().append("Sending notification to culprit: " + target.toString() + "\n");
        			getIMConnection().send(target, message);
        		} catch (final Throwable e) {
        			buildListener.getLogger().append("There was an error sending suspect notification to: " + target.toString() + "\n");
        		}
        	}
        }
        
        if (this.notifyFixers && BuildHelper.isFix(build)) {
        	LOGGER.info("Notifying fixers");
        	final String message = "Yippie! Seems you've fixed " + build.getProject().getName() + ": " + MessageHelper.getBuildURL(build);
        	
        	for (IMMessageTarget target : calculateIMTargets(getCommitters(build), buildListener.getLogger())) {
        		try {
        			buildListener.getLogger().append("Sending notification to fixer: " + target.toString() + "\n");
        			getIMConnection().send(target, message);
        		} catch (final Throwable e) {
        			buildListener.getLogger().append("There was an error sending fixer notification to: " + target.toString() + "\n");
        		}
        	}
        }
        
        return true;
    }

    /**
     * Notify all registered chats about the build result.
     */
	private void notifyChats(final AbstractBuild<?, ?> build, final BuildListener buildListener) {
		final StringBuilder sb;
		if (BuildHelper.isFix(build)) {
			sb = new StringBuilder("Yippie, build fixed!\n");
		} else {
			sb = new StringBuilder();
		}
		sb.append("Project ").append(build.getProject().getName())
			.append(" build (").append(build.getNumber()).append("): ")
			.append(BuildHelper.getResultDescription(build)).append(" in ")
			.append(build.getTimestampString())
			.append(": ")
			.append(MessageHelper.getBuildURL(build));
		
		if ((build.getChangeSet() != null) && (! build.getChangeSet().isEmptySet())) {
			boolean hasManyChangeSets = build.getChangeSet().getItems().length > 1;
			for (Entry entry : build.getChangeSet()) {
				sb.append("\n");
				if (hasManyChangeSets) {
					sb.append("* ");
				}
				sb.append(entry.getAuthor()).append(": ").append(entry.getMsg());
			}
		}
		final String msg = sb.toString();

		for (IMMessageTarget target : getNotificationTargets())
		{
		    try
		    {
		        buildListener.getLogger().append("Sending notification to: " + target.toString() + "\n");
		        getIMConnection().send(target, msg);
		    }
		    catch (final Throwable e)
		    {
		        buildListener.getLogger().append("There was an error sending notification to: " + target.toString() + "\n");
		    }
		}
	}

	/* (non-Javadoc)
	 * @see hudson.tasks.Publisher#prebuild(hudson.model.Build, hudson.model.BuildListener)
	 */
	@Override
	public boolean prebuild(AbstractBuild<?, ?> build, BuildListener buildListener) {
		try {
			if (notifyOnBuildStart) {
				final StringBuilder sb = new StringBuilder("Starting build ").append(build.getNumber())
					.append(" for job ").append(build.getProject().getName());
				if (build.getPreviousBuild() != null) {
					sb.append(" (previous build: ").append(build.getPreviousBuild().getResult().toString().toLowerCase());
					if (build.getPreviousBuild().getResult().isWorseThan(Result.SUCCESS)) {
						sb.append(" -- last ").append(build.getPreviousNotFailedBuild().getResult().toString().toLowerCase())
						.append(" #").append(build.getPreviousNotFailedBuild().getNumber())
						.append(" ").append(build.getPreviousNotFailedBuild().getTimestampString()).append(" ago");
					}
					sb.append(")");
				}
				final String msg = sb.toString();
				for (final IMMessageTarget target : getNotificationTargets()) {
					// only notify group chats
					if (target instanceof GroupChatIMMessageTarget) {
		                try {
		                    getIMConnection().send(target, msg);
		                } catch (final Throwable e) {
		                    buildListener.getLogger().append("There was an error sending notification to: " + target.toString() + "\n");
		                }
					}
	            }
			}
		} catch (Throwable t) {
			// ignore: never, ever cancel a build because a notification fails
            buildListener.getLogger().append("There was an error in the Jabber plugin: " + t.toString() + "\n");
		}
		return true;
	}
	
	private static Set<User> getCommitters(AbstractBuild<?, ?> build) {
		Set<User> committers = new HashSet<User>();
		ChangeLogSet<? extends Entry> changeSet = build.getChangeSet();
		for (Entry entry : changeSet) {
			committers.add(entry.getAuthor());
		}
		return committers;
	}
	
	/**
	 * Returns the culprits WITHOUT the committers to the current build.
	 */
	private static Set<User> getCulpritsOnly(AbstractBuild<?, ?> build) {
		Set<User> culprits = new HashSet<User>(build.getCulprits());
		culprits.removeAll(getCommitters(build));
		return culprits;
	}
	
	private Collection<IMMessageTarget> calculateIMTargets(Set<User> targets, PrintStream logger) {
		Set<IMMessageTarget> suspects = new HashSet<IMMessageTarget>();
		
		String defaultSuffix = null;
		try {
			defaultSuffix = getIMConnection().getDefaultIdSuffix();
			if (StringUtils.isBlank(defaultSuffix)) {
				defaultSuffix = null;
			}
		} catch (IMException e) {
			// ignore
		}
		LOGGER.fine("Default Suffix: " + defaultSuffix);
		
		for (User target : targets) {
			LOGGER.fine("Possible target: " + target.getId());
            String jabberId = null;
			JabberUserProperty jabberUserProperty = (JabberUserProperty) target.getProperties().get(JabberUserProperty.DESCRIPTOR);
			if ((jabberUserProperty != null) && (jabberUserProperty.getJid() != null)) {
                jabberId = jabberUserProperty.getJid();
			} else if (defaultSuffix != null) {
                jabberId = target.getId() + defaultSuffix;
            }

            if (jabberId != null) {
                try {
                    suspects.add(CONVERTER.fromString(jabberId));
                } catch (final IMMessageTargetConversionException e) {
                    logger.append("Invalid Jabber ID: " + jabberId + "\n");
                }
            } else {
            	logger.append("No Jabber ID found for: " + target.getId() + "\n");
            }
		}
		return suspects;
	}

    @Override
    public abstract BuildStepDescriptor<Publisher> getDescriptor();
	
}
