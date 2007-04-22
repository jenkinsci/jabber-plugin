/**
 * 
 */
package hudson.plugins.jabber.im.transport;

import hudson.model.Descriptor;
import hudson.plugins.jabber.im.IMException;
import hudson.plugins.jabber.im.IMMessageTargetConversionException;
import hudson.plugins.jabber.tools.Assert;
import hudson.tasks.Publisher;

import java.net.InetAddress;
import java.net.UnknownHostException;

import javax.servlet.http.HttpServletRequest;

import org.kohsuke.stapler.StaplerRequest;

public final class JabberPublisherDescriptor extends Descriptor<Publisher>
{
    private static final String PREFIX = "jabberPlugin.";
    public static final String PARAMETERNAME_PORT = JabberPublisherDescriptor.PREFIX + "port";
    public static final String PARAMETERNAME_HOSTNAME = JabberPublisherDescriptor.PREFIX + "hostname";
    public static final String PARAMETERNAME_PRESENCE = JabberPublisherDescriptor.PREFIX + "exposePresence";
    public static final String PARAMETERNAME_PASSWORD = JabberPublisherDescriptor.PREFIX + "password";
    public static final String PARAMETERNAME_NICKNAME = JabberPublisherDescriptor.PREFIX + "nick";
    public static final String PARAMETERNAME_TARGETS = JabberPublisherDescriptor.PREFIX + "targets";

    private int port = 5222;
    private String hostname = null;
    private String hudsonNickname = "hudson";
    private String hudsonPassword = "secret";
    private boolean exposePresence = true;

    public JabberPublisherDescriptor()
    {
        super(JabberPublisher.class);
        load();
        try
        {
            JabberIMConnectionProvider.getInstance().createConnection(this);
        }
        catch (final IMException dontCare)
        {
            // Server temporarily unavailable ?
            dontCare.printStackTrace();
        }
    }

    private void applyHostname(final HttpServletRequest req) throws FormException
    {
        final String s = req.getParameter(JabberPublisherDescriptor.PARAMETERNAME_HOSTNAME);
        if ((s != null) && (s.trim().length() > 0))
        {
            try
            {
                InetAddress.getByName(s); // try to resolve
                this.hostname = s;
            }
            catch (final UnknownHostException e)
            {
                throw new FormException("Cannot find Host '" + s + "'.",
                        JabberPublisherDescriptor.PARAMETERNAME_HOSTNAME);
            }
        }
        else
        {
            this.hostname = null;
        }
    }

    private void applyNickname(final HttpServletRequest req) throws FormException
    {
        this.hudsonNickname = req.getParameter(JabberPublisherDescriptor.PARAMETERNAME_NICKNAME);
        if ((this.hostname != null) && ((this.hudsonNickname == null) || (this.hudsonNickname.trim().length() == 0)))
        {
            throw new FormException("Account/Nickname cannot be empty.",
                    JabberPublisherDescriptor.PARAMETERNAME_NICKNAME);
        }
    }

    private void applyPassword(final HttpServletRequest req) throws FormException
    {
        this.hudsonPassword = req.getParameter(JabberPublisherDescriptor.PARAMETERNAME_PASSWORD);
        if (((this.hostname != null) && (this.hudsonPassword == null)) || (this.hudsonPassword.trim().length() == 0))
        {
            throw new FormException("Password cannot be empty.", JabberPublisherDescriptor.PARAMETERNAME_PASSWORD);
        }
    }

    private void applyPort(final HttpServletRequest req) throws FormException
    {
        final String p = req.getParameter(JabberPublisherDescriptor.PARAMETERNAME_PORT);
        if (p != null)
        {
            try
            {
                final int i = Integer.parseInt(p);
                if ((i < 0) || (i > 65535))
                {
                    throw new FormException("Port out of range.", JabberPublisherDescriptor.PARAMETERNAME_PORT);
                }
                this.port = i;
            }
            catch (final NumberFormatException e)
            {
                throw new FormException("Port cannot be parsed.", JabberPublisherDescriptor.PARAMETERNAME_PORT);
            }
        }
    }

    private void applyPresence(final HttpServletRequest req)
    {
        this.exposePresence = req.getParameter(JabberPublisherDescriptor.PARAMETERNAME_PRESENCE) != null;
    }

    /** 
     * {@inheritDoc}
     */
    @Override
    public boolean configure(final HttpServletRequest req) throws FormException
    {
        Assert.isNotNull(req, "Parameter 'req' must not be null.");

        applyPresence(req);
        applyHostname(req);
        applyPort(req);
        applyNickname(req);
        applyPassword(req);

        try
        {
            JabberIMConnectionProvider.getInstance().createConnection(this);
        }
        catch (final Exception e)
        {
            throw new FormException("Unable to create Client: " + e, null);
        }
        save();
        return super.configure(req);
    }

    /**
     * This human readable name is used in the configuration screen.
     */
    @Override
    public String getDisplayName()
    {
        return "Jabber Notification";
    }

    public String getHostname()
    {
        return this.hostname;
    }

    public String getHudsonNickname()
    {
        return this.hudsonNickname;
    }

    public String getHudsonPassword()
    {
        return this.hudsonPassword;
    }

    public int getPort()
    {
        return this.port;
    }

    public boolean isExposePresence()
    {
        return this.exposePresence;
    }

    /**
     * Creates a new instance of {@link JabberPublisher} from a submitted form.
     */
    @Override
    public JabberPublisher newInstance(final StaplerRequest req) throws FormException
    {
        Assert.isNotNull(req, "Parameter 'req' must not be null.");
        final String t = req.getParameter(JabberPublisherDescriptor.PARAMETERNAME_TARGETS);
        try
        {
            return new JabberPublisher(t);
        }
        catch (final IMMessageTargetConversionException e)
        {
            throw new FormException(e, JabberPublisherDescriptor.PARAMETERNAME_TARGETS);
        }
    }

    public void shutdown()
    {
        final JabberIMConnectionProvider factory = JabberIMConnectionProvider.getInstance();
        factory.releaseConnection();
    }

}