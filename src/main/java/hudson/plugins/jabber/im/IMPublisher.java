package hudson.plugins.jabber.im;

import hudson.Launcher;
import hudson.Util;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.Hudson;
import hudson.model.Result;
import hudson.plugins.jabber.NotificationStrategy;
import hudson.plugins.jabber.im.transport.JabberPublisherDescriptor;
import hudson.plugins.jabber.tools.Assert;
import hudson.plugins.jabber.user.JabberUserProperty;
import hudson.scm.ChangeLogSet;
import hudson.scm.ChangeLogSet.Entry;
import hudson.tasks.Publisher;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * The actual Publisher that sends notification-Messages out to the clients.
 * @author Uwe Schaefer
 *
 */
public abstract class IMPublisher extends Publisher
{
    private static final IMMessageTargetConverter CONVERTER = new DefaultIMMessageTargetConverter();
    private final List<IMMessageTarget> targets = new LinkedList<IMMessageTarget>();
    private final NotificationStrategy notificationStrategy;
    private final boolean notifyOnBuildStart;
    private final boolean notifySuspects;
    private final boolean notifyFixers;

    protected IMPublisher(final String targetsAsString, final String notificationStrategyString,
    		final boolean notifyGroupChatsOnBuildStart,
    		final boolean notifySuspects,
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
        if (notificationStrategyString == null) {
        	this.notificationStrategy = NotificationStrategy.STATECHANGE_ONLY;
        } else if (notificationStrategyString.equals(JabberPublisherDescriptor.PARAMETERVALUE_STRATEGY_ALL)) {
        	this.notificationStrategy = NotificationStrategy.ALL;
        } else if (notificationStrategyString.equals(JabberPublisherDescriptor.PARAMETERVALUE_STRATEGY_FAILURE)) {
        	this.notificationStrategy = NotificationStrategy.ANY_FAILURE;
        } else {
        	this.notificationStrategy = NotificationStrategy.STATECHANGE_ONLY;
        }
        
        this.notifyOnBuildStart = notifyGroupChatsOnBuildStart;
        this.notifySuspects = notifySuspects;
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
        final StringBuffer sb = new StringBuffer();
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
    	if (getNotificationStrategy() == NotificationStrategy.ALL) {
    		return JabberPublisherDescriptor.PARAMETERVALUE_STRATEGY_ALL;
    	}
    	if (getNotificationStrategy() == NotificationStrategy.ANY_FAILURE) {
    		return JabberPublisherDescriptor.PARAMETERVALUE_STRATEGY_FAILURE;
    	}
    	return JabberPublisherDescriptor.PARAMETERVALUE_STRATEGY_STATE_CHANGE;
    }
    
    public final boolean getNotifyOnStart() {
    	return notifyOnBuildStart;
    }
    
    public final boolean getNotifySuspects() {
    	return notifySuspects;
    }
    
    public final boolean getNotifyFixers() {
    	return notifyFixers;
    }

    public boolean perform(final Build<?,?> build, final Launcher launcher, final BuildListener buildListener)
            throws InterruptedException, IOException
    {
        Assert.isNotNull(build, "Parameter 'build' must not be null.");
        Assert.isNotNull(buildListener, "Parameter 'arg2' must not be null.");
        if (getNotificationStrategy().notificationWanted(build))
        {
            // Escape any spaces and non-ASCII characters in the build URL.
            String buildUrl = Util.encode(build.getUrl());

            final StringBuffer sb = new StringBuffer();
        	sb.append("Project ").append(build.getProject().getName())
        	.append(" build (").append(build.getNumber()).append("): ")
        	.append(build.getResult()).append(" in ")
        	.append(build.getDurationString())
        	.append(": ")
        	.append(Hudson.getInstance().getRootUrl()).append(buildUrl);
        	
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

            for (final IMMessageTarget target : getNotificationTargets())
            {
                try
                {
                    buildListener.getLogger().append("Sending notification to: " + target.toString() + "\n");
                    getIMConnection().send(target, msg);
                }
                catch (final Throwable e)
                {
                    buildListener.getLogger().append("There was an Error sending notification to: " + target.toString() + "\n");
                }
            }
        }
        
        if (this.notifySuspects && build.getResult().isWorseThan(Result.SUCCESS)) {
        	final String message = new StringBuffer("You're suspected of having broken ")
        	.append(build.getProject().getName()).append(": ")
        	.append(Hudson.getInstance().getRootUrl()).append(build.getUrl())
        	.toString();
        	for (final IMMessageTarget target : calculateSuspectsTargets(build.getChangeSet())) {
        		try {
        			getIMConnection().send(target, message);
        		} catch (final Throwable e) {
        			buildListener.getLogger().append("There was an Error sending suspect notification to: " + target.toString() + "\n");
        		}
        	}
        }
        
        if (this.notifyFixers && (build.getResult() == Result.SUCCESS) &&
        		(build.getPreviousBuild() != null) && build.getPreviousBuild().getResult().isWorseThan(Result.SUCCESS)) {
        	final String message = new StringBuffer("Seems you've fixed  ")
        	.append(build.getProject().getName()).append(": ")
        	.append(Hudson.getInstance().getRootUrl()).append(build.getUrl())
        	.toString();
        	for (final IMMessageTarget target : calculateSuspectsTargets(build.getChangeSet())) {
        		try {
        			getIMConnection().send(target, message);
        		} catch (final Throwable e) {
        			buildListener.getLogger().append("There was an Error sending fixer notification to: " + target.toString() + "\n");
        		}
        	}
        }
        
        return true;
    }

	/* (non-Javadoc)
	 * @see hudson.tasks.Publisher#prebuild(hudson.model.Build, hudson.model.BuildListener)
	 */
	@Override
	public boolean prebuild(Build build, BuildListener buildListener) {
		try {
			if (notifyOnBuildStart) {
				final StringBuffer sb = new StringBuffer("Starting build ").append(build.getNumber())
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
		                    buildListener.getLogger().append("There was an Error sending notification to: " + target.toString() + "\n");
		                }
					}
	            }
			}
		} catch (Throwable t) {
			// ignore: never, ever cancel a build because a notification fails
		}
		return true;
	}
	
	private Collection<IMMessageTarget> calculateSuspectsTargets(ChangeLogSet<? extends Entry> changeLogSet) {
		Set<IMMessageTarget> suspects = new HashSet<IMMessageTarget>();
		
		if (changeLogSet != null && (! changeLogSet.isEmptySet())) {
			for (Entry e : changeLogSet) {
				JabberUserProperty jabberUserProperty = (JabberUserProperty) e.getAuthor().getProperties().get(JabberUserProperty.DESCRIPTOR);
				if ((jabberUserProperty != null) && (jabberUserProperty.getJid() != null)) {
					try {
						suspects.add(CONVERTER.fromString(jabberUserProperty.getJid()));
					} catch (final IMMessageTargetConversionException dontCare) {
					}
				}
			}
		}
		return suspects;
	}

}
