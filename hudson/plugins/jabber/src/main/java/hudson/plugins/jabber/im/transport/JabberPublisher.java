package hudson.plugins.jabber.im.transport;

import hudson.model.Descriptor;
import hudson.plugins.jabber.im.DefaultIMMessageTarget;
import hudson.plugins.jabber.im.DefaultIMMessageTargetConverter;
import hudson.plugins.jabber.im.GroupChatIMMessageTarget;
import hudson.plugins.jabber.im.IMConnection;
import hudson.plugins.jabber.im.IMException;
import hudson.plugins.jabber.im.IMMessageTarget;
import hudson.plugins.jabber.im.IMMessageTargetConversionException;
import hudson.plugins.jabber.im.IMMessageTargetConverter;
import hudson.plugins.jabber.im.IMPublisher;
import hudson.tasks.Publisher;

/**
 * Jabber-specific implementation of the Publisher.
 * @author Uwe Schaefer
 */
public class JabberPublisher extends IMPublisher
{
    static class JabberIMMessageTargetConverter extends DefaultIMMessageTargetConverter
    {
        private void checkValidity(final String f) throws IMMessageTargetConversionException
        {
            // @TODO just as a demonstration.
            final int i = f.indexOf('@');
            if (f.indexOf('@', i + 1) > -1)
            {
                throw new IMMessageTargetConversionException("Invalid input for target: '" + f + "'");
            }
        }

        @Override
        public IMMessageTarget fromString(final String targetAsString) throws IMMessageTargetConversionException
        {
            String f = targetAsString.trim();
            if (f.length() > 0)
            {
            	IMMessageTarget target;
            	if (f.startsWith("*")) {
            		f = f.substring(1);
            		// group chat
            		if (! f.contains("@")) {
            			f += "@conference." + JabberPublisher.DESCRIPTOR.getHostname();
            		}
            		target = new GroupChatIMMessageTarget(f);
            	} else if (f.contains("@conference.")) {
            		target = new GroupChatIMMessageTarget(f);
            	} else {
	                if (!f.contains("@")) {
	                    f += "@" + JabberPublisher.DESCRIPTOR.getHostname();
	                }
	                target = new DefaultIMMessageTarget(f);
            	}
                checkValidity(f);
                return target;
            }
            else
            {
                return null;
            }
        }
    }
    static final JabberPublisherDescriptor DESCRIPTOR = new JabberPublisherDescriptor();

    private static final IMMessageTargetConverter CONVERTER = new JabberIMMessageTargetConverter();

    public JabberPublisher(final String targetsAsString, final String notificationStrategy,
    		final boolean notifyGroupChatsOnBuildStart,
    		final boolean notifySuspects,
    		final boolean notifyFixers) throws IMMessageTargetConversionException
    {
        super(targetsAsString, notificationStrategy, notifyGroupChatsOnBuildStart,
        		notifySuspects, notifyFixers);
    }

    public Descriptor<Publisher> getDescriptor()
    {
        return JabberPublisher.DESCRIPTOR;
    }

    @Override
    protected IMConnection getIMConnection() throws IMException
    {
        return JabberIMConnectionProvider.getInstance().currentConnection();
    }

    @Override
    protected IMMessageTargetConverter getIMMessageTargetConverter()
    {
        return JabberPublisher.CONVERTER;
    }
}
