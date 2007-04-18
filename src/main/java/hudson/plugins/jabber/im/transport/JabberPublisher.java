package hudson.plugins.jabber.im.transport;

import hudson.model.Descriptor;
import hudson.plugins.jabber.im.DefaultIMMessageTarget;
import hudson.plugins.jabber.im.DefaultIMMessageTargetConverter;
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
                if (!f.contains("@"))
                {
                    f += "@" + JabberPublisher.DESCRIPTOR.getHostname();
                }
                checkValidity(f);
                return new DefaultIMMessageTarget(f);
            }
            else
            {
                return null;
            }
        }
    }
    static final JabberPublisherDescriptor DESCRIPTOR = new JabberPublisherDescriptor();

    private static final IMMessageTargetConverter CONVERTER = new JabberIMMessageTargetConverter();

    public JabberPublisher(final String targetsAsString) throws IMMessageTargetConversionException
    {
        super(targetsAsString);
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
