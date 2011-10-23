package hudson.plugins.jabber.im.transport;

import hudson.Extension;
import hudson.Util;
import hudson.model.User;
import hudson.plugins.im.GroupChatIMMessageTarget;
import hudson.plugins.im.IMConnection;
import hudson.plugins.im.IMException;
import hudson.plugins.im.IMMessageTarget;
import hudson.plugins.im.IMMessageTargetConversionException;
import hudson.plugins.im.IMMessageTargetConverter;
import hudson.plugins.im.IMPublisher;
import hudson.plugins.im.MatrixJobMultiplier;
import hudson.plugins.im.build_notify.BuildToChatNotifier;
import hudson.plugins.jabber.user.JabberUserProperty;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Mailer;

import java.util.List;


/**
 * Jabber-specific implementation of the {@link IMPublisher}.
 *
 * @author Christoph Kutzinski
 * @author Uwe Schaefer (original implementation)
 */
public class JabberPublisher extends IMPublisher
{
    @Extension
    public static final JabberPublisherDescriptor DESCRIPTOR = new JabberPublisherDescriptor();

    static final IMMessageTargetConverter CONVERTER = new JabberIMMessageTargetConverter();

    public JabberPublisher(List<IMMessageTarget> targets, String notificationStrategy,
    		boolean notifyGroupChatsOnBuildStart,
    		boolean notifySuspects,
    		boolean notifyCulprits,
    		boolean notifyFixers,
    		boolean notifyUpstreamCommitters,
    		BuildToChatNotifier buildToChatNotifier,
    		MatrixJobMultiplier matrixJobMultiplier) throws IMMessageTargetConversionException
    {
        super(targets, notificationStrategy, notifyGroupChatsOnBuildStart,
        		notifySuspects, notifyCulprits, notifyFixers, notifyUpstreamCommitters,
        		buildToChatNotifier, matrixJobMultiplier);
    }

    @Override
    public JabberPublisherDescriptor getDescriptor() {
        return JabberPublisher.DESCRIPTOR;
    }

    @Override
    protected IMConnection getIMConnection() throws IMException {
        return JabberIMConnectionProvider.getInstance().currentConnection();
    }

	@Override
	protected String getPluginName() {
		return "Jabber notifier plugin";
	}

	@Override
	protected String getConfiguredIMId(User user) {
	    // if set, user property override all other settings:
        JabberUserProperty jabberUserProperty = (JabberUserProperty) user.getProperties().get(JabberUserProperty.DESCRIPTOR);
        if (jabberUserProperty != null && jabberUserProperty.getJid() != null) {
            return jabberUserProperty.getJid();
        }
	    
		if (getDescriptor().isEmailAddressAsJabberId()) {
			Mailer.UserProperty mailProperty = user.getProperty(Mailer.UserProperty.class);
			if (mailProperty != null) {
				String emailAddress = mailProperty.getAddress();
				if (Util.fixEmpty(emailAddress) != null) {
					return emailAddress;
				}
			}
		}
		return null;
	}
    
	@Override
	public String getTargets() {
		List<IMMessageTarget> notificationTargets = getNotificationTargets();
		
		StringBuilder sb = new StringBuilder();
		for (IMMessageTarget target : notificationTargets) {
			if ((target instanceof GroupChatIMMessageTarget) && (! target.toString().contains("@conference."))) {
        		sb.append("*");
        	}
            sb.append(getIMDescriptor().getIMMessageTargetConverter().toString(target));
            sb.append(" ");
        }
        return sb.toString().trim();
	}
    
	@Override
	public BuildStepMonitor getRequiredMonitorService() {
		return BuildStepMonitor.BUILD;
	}
	
    @Override
    protected Object readResolve() {
        // make sure we don't forget to call readResolve on the super class
        super.readResolve();
        return this;
    }
}
