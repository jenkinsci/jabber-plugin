/**
 * 
 */
package hudson.plugins.jabber.im.transport;

import hudson.Util;
import hudson.util.FormFieldValidator;
import hudson.model.Descriptor;
import hudson.plugins.jabber.im.IMException;
import hudson.plugins.jabber.im.IMMessageTargetConversionException;
import hudson.plugins.jabber.tools.Assert;
import hudson.tasks.Publisher;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.ServletException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.io.IOException;

public class JabberPublisherDescriptor extends Descriptor<Publisher>
{
    private static final String PREFIX = "jabberPlugin.";
    public static final String PARAMETERNAME_PORT = JabberPublisherDescriptor.PREFIX + "port";
    public static final String PARAMETERNAME_HOSTNAME = JabberPublisherDescriptor.PREFIX + "hostname";
    public static final String PARAMETERNAME_PRESENCE = JabberPublisherDescriptor.PREFIX + "exposePresence";
    public static final String PARAMETERNAME_PASSWORD = JabberPublisherDescriptor.PREFIX + "password";
    public static final String PARAMETERNAME_NICKNAME = JabberPublisherDescriptor.PREFIX + "nick";
    public static final String PARAMETERNAME_TARGETS = JabberPublisherDescriptor.PREFIX + "targets";
    public static final String PARAMETERNAME_STRATEGY = JabberPublisherDescriptor.PREFIX + "strategy";
    public static final String PARAMETERNAME_NOTIFY_START = JabberPublisherDescriptor.PREFIX + "notifyStart";
    public static final String PARAMETERNAME_NOTIFY_SUSPECTS = JabberPublisherDescriptor.PREFIX + "notifySuspects";
    public static final String PARAMETERNAME_NOTIFY_FIXERS = JabberPublisherDescriptor.PREFIX + "notifyFixers";
    public static final String PARAMETERNAME_INITIAL_GROUPCHATS = JabberPublisherDescriptor.PREFIX + "initialGroupChats";
    public static final String PARAMETERNAME_COMMAND_PREFIX = JabberPublisherDescriptor.PREFIX + "commandPrefix";
    public static final String PARAMETERVALUE_STRATEGY_ALL = "all";
    public static final String PARAMETERVALUE_STRATEGY_FAILURE = "failure";
    public static final String PARAMETERVALUE_STRATEGY_STATE_CHANGE = "change";
    public static final String[] PARAMETERVALUE_STRATEGY_VALUES = {
    	PARAMETERVALUE_STRATEGY_ALL,
    	PARAMETERVALUE_STRATEGY_FAILURE,
    	PARAMETERVALUE_STRATEGY_STATE_CHANGE
    };
    public static final String PARAMETERVALUE_STRATEGY_DEFAULT = PARAMETERVALUE_STRATEGY_STATE_CHANGE;
    public static final String DEFAULT_COMMAND_PREFIX = "!";

    private int port = 5222;
    private String hostname = null;
    private String hudsonNickname = "hudson";
    private String hudsonPassword = "secret";
    private boolean exposePresence = true;
    private String initialGroupChats = null;
    private String commandPrefix = DEFAULT_COMMAND_PREFIX;

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
        final String p = Util.fixEmptyAndTrim(req.getParameter(JabberPublisherDescriptor.PARAMETERNAME_PORT));
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
        } else {
            this.port = 5222;
        }
    }

    private void applyPresence(final HttpServletRequest req)
    {
        this.exposePresence = req.getParameter(JabberPublisherDescriptor.PARAMETERNAME_PRESENCE) != null;
    }
    
    private void applyInitialGroupChats(final HttpServletRequest req) {
    	this.initialGroupChats = Util.fixEmptyAndTrim(req.getParameter(JabberPublisherDescriptor.PARAMETERNAME_INITIAL_GROUPCHATS));
    }
    
    private void applyCommandPrefix(final HttpServletRequest req) {
    	String commandPrefix = req.getParameter(JabberPublisherDescriptor.PARAMETERNAME_COMMAND_PREFIX);
    	if ((commandPrefix != null) && (commandPrefix.trim().length() > 0)) {
    		this.commandPrefix = commandPrefix;
    	} else {
    		this.commandPrefix = DEFAULT_COMMAND_PREFIX;
    	}
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

    /**
     * Returns the text to be put into the form field.
     * If the port is default, leave it empty.
     */
    public String getPortString() {
        if(port==5222)  return null;
        else            return String.valueOf(port);
    }

    public boolean isExposePresence()
    {
        return this.exposePresence;
    }

    /**
     * Gets the whitespace separated list of group chats to join,
     * or null if nothing is configured.
     */
    public String getInitialGroupChats() {
    	return Util.fixEmptyAndTrim(this.initialGroupChats);
    }
    
    public String getCommandPrefix() {
    	return this.commandPrefix;
    }

    /**
     * Creates a new instance of {@link JabberPublisher} from a submitted form.
     */
    @Override
    public JabberPublisher newInstance(final StaplerRequest req) throws FormException
    {
        Assert.isNotNull(req, "Parameter 'req' must not be null.");
        final String t = req.getParameter(JabberPublisherDescriptor.PARAMETERNAME_TARGETS);
        String n = req.getParameter(JabberPublisherDescriptor.PARAMETERNAME_STRATEGY);
        if (n == null) {
        	n = PARAMETERVALUE_STRATEGY_DEFAULT;
        } else {
        	boolean foundStrategyValueMatch = false;
        	for (final String strategyValue : PARAMETERVALUE_STRATEGY_VALUES) {
        		if (strategyValue.equals(n)) {
        			foundStrategyValueMatch = true;
        			break;
        		}
        	}
        	if (! foundStrategyValueMatch) {
        		n = PARAMETERVALUE_STRATEGY_DEFAULT;
        	}
        }
        final String s = req.getParameter(JabberPublisherDescriptor.PARAMETERNAME_NOTIFY_START);
        final String ns = req.getParameter(JabberPublisherDescriptor.PARAMETERNAME_NOTIFY_SUSPECTS);
        final String nf = req.getParameter(JabberPublisherDescriptor.PARAMETERNAME_NOTIFY_FIXERS);
        try
        {
            return new JabberPublisher(t, n,
            		(s != null && "on".equals(s)),
            		(ns != null && "on".equals(ns)),
            		(nf != null && "on".equals(nf)));
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

	/* (non-Javadoc)
	 * @see hudson.model.Descriptor#configure(org.kohsuke.stapler.StaplerRequest)
	 */
	@Override
	public boolean configure(StaplerRequest req) throws hudson.model.Descriptor.FormException {
		Assert.isNotNull(req, "Parameter 'req' must not be null.");

        applyPresence(req);
        applyHostname(req);
        applyPort(req);
        applyNickname(req);
        applyPassword(req);
        applyInitialGroupChats(req);
        applyCommandPrefix(req);

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
     * Validates the server name.
     */
    public void doServerCheck(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        new FormFieldValidator(req, rsp, false) {
            protected void check() throws IOException, ServletException {
                String v = Util.fixEmptyAndTrim(request.getParameter("value"));
                if (v == null)
                    ok();
                else {
                    try {
                        InetAddress.getByName(v);
                        ok();
                    } catch (UnknownHostException e) {
                        error("Unknown host "+v);
                    }
                }
            }
        }.process();
    }
}