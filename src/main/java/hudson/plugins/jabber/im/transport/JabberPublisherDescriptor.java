/**
 * 
 */
package hudson.plugins.jabber.im.transport;

import hudson.Util;
import hudson.plugins.jabber.NotificationStrategy;
import hudson.plugins.jabber.im.IMMessageTargetConversionException;
import hudson.plugins.jabber.tools.Assert;
import hudson.plugins.jabber.tools.ExceptionHelper;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
import hudson.util.FormFieldValidator;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

import net.sf.json.JSONObject;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

public class JabberPublisherDescriptor extends BuildStepDescriptor<Publisher>
{
    private static final Logger LOGGER = Logger.getLogger(JabberPublisherDescriptor.class.getName());
    
    private static final String PREFIX = "jabberPlugin.";
    public static final String PARAMETERNAME_PORT = JabberPublisherDescriptor.PREFIX + "port";
    public static final String PARAMETERNAME_HOSTNAME = JabberPublisherDescriptor.PREFIX + "hostname";
    public static final String PARAMETERNAME_SSL = JabberPublisherDescriptor.PREFIX + "ssl";
    public static final String PARAMETERNAME_PRESENCE = JabberPublisherDescriptor.PREFIX + "exposePresence";
    public static final String PARAMETERNAME_PASSWORD = JabberPublisherDescriptor.PREFIX + "password";
    public static final String PARAMETERNAME_NICKNAME = JabberPublisherDescriptor.PREFIX + "nick";
    public static final String PARAMETERNAME_GROUP_NICKNAME = JabberPublisherDescriptor.PREFIX + "groupNick";
    public static final String PARAMETERNAME_TARGETS = JabberPublisherDescriptor.PREFIX + "targets";
    public static final String PARAMETERNAME_STRATEGY = JabberPublisherDescriptor.PREFIX + "strategy";
    public static final String PARAMETERNAME_NOTIFY_START = JabberPublisherDescriptor.PREFIX + "notifyStart";
    public static final String PARAMETERNAME_NOTIFY_SUSPECTS = JabberPublisherDescriptor.PREFIX + "notifySuspects";
    public static final String PARAMETERNAME_NOTIFY_FIXERS = JabberPublisherDescriptor.PREFIX + "notifyFixers";
    public static final String PARAMETERNAME_INITIAL_GROUPCHATS = JabberPublisherDescriptor.PREFIX + "initialGroupChats";
    public static final String PARAMETERNAME_COMMAND_PREFIX = JabberPublisherDescriptor.PREFIX + "commandPrefix";
    public static final String PARAMETERNAME_DEFAULT_ID_SUFFIX = JabberPublisherDescriptor.PREFIX + "defaultIdSuffix";
    public static final String[] PARAMETERVALUE_STRATEGY_VALUES;
    static {
        PARAMETERVALUE_STRATEGY_VALUES = new String[NotificationStrategy.values().length];
        int i = 0;
        for (NotificationStrategy strategy : NotificationStrategy.values()) {
            PARAMETERVALUE_STRATEGY_VALUES[i++] = strategy.getDisplayName();
        }
    };
    public static final String PARAMETERVALUE_STRATEGY_DEFAULT = NotificationStrategy.STATECHANGE_ONLY.getDisplayName();
    public static final String DEFAULT_COMMAND_PREFIX = "!";

    private int port = 5222;
    private String hostname = null;
    private boolean legacySSL = false;
    private String hudsonNickname = "hudson";
    private String hudsonPassword = "secret";
    private String groupChatNickname = null;
    private boolean exposePresence = true;
    private String initialGroupChats = null;
    private String commandPrefix = DEFAULT_COMMAND_PREFIX;
    private String defaultIdSuffix;

    public JabberPublisherDescriptor()
    {
        super(JabberPublisher.class);
        load();
        
        if (StringUtils.isNotBlank(this.hostname)) {
            try
            {
                JabberIMConnectionProvider.getInstance().createConnection(this);
            }
            catch (final Exception dontCare)
            {
                // Server temporarily unavailable or misconfigured?
                LOGGER.warning(ExceptionHelper.dump(dontCare));
            }
        } else {
            LOGGER.info("No hostname configured.");
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

    private void applyGroupChatNickname(final HttpServletRequest req) throws FormException
    {
        this.groupChatNickname = req.getParameter(JabberPublisherDescriptor.PARAMETERNAME_GROUP_NICKNAME);
        if (this.groupChatNickname != null && this.groupChatNickname.trim().length() == 0)
        {
            this.groupChatNickname = null;
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

    private void applyLegacySSL(final HttpServletRequest req)
    {
        this.legacySSL = req.getParameter(JabberPublisherDescriptor.PARAMETERNAME_SSL) != null;
    }

    private void applyPresence(final HttpServletRequest req)
    {
        this.exposePresence = req.getParameter(JabberPublisherDescriptor.PARAMETERNAME_PRESENCE) != null;
    }
    
    private void applyInitialGroupChats(final HttpServletRequest req) {
    	this.initialGroupChats = Util.fixEmptyAndTrim(req.getParameter(JabberPublisherDescriptor.PARAMETERNAME_INITIAL_GROUPCHATS));
    }
    
    private void applyCommandPrefix(final HttpServletRequest req) {
    	String prefix = req.getParameter(JabberPublisherDescriptor.PARAMETERNAME_COMMAND_PREFIX);
    	if ((prefix != null) && (prefix.trim().length() > 0)) {
    		this.commandPrefix = prefix;
    	} else {
    		this.commandPrefix = DEFAULT_COMMAND_PREFIX;
    	}
    }

     private void applyDefaultIdSuffix(final HttpServletRequest req) {
    	String suffix = req.getParameter(JabberPublisherDescriptor.PARAMETERNAME_DEFAULT_ID_SUFFIX);
    	if ((suffix != null) && (suffix.trim().length() > 0)) {
    		this.defaultIdSuffix = suffix.trim();
    	} else {
    		this.defaultIdSuffix = "";
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

    public String getGroupChatNickname()
    {
        return this.groupChatNickname;
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

    public boolean isLegacySSL()
    {
        return this.legacySSL;
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

    public String getDefaultIdSuffix() {
        return this.defaultIdSuffix;
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

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean configure(StaplerRequest req, JSONObject json) throws hudson.model.Descriptor.FormException {
		Assert.isNotNull(req, "Parameter 'req' must not be null.");

        applyPresence(req);
        applyHostname(req);
        applyPort(req);
        applyLegacySSL(req);
        applyNickname(req);
        applyPassword(req);
        applyGroupChatNickname(req);
        applyInitialGroupChats(req);
        applyCommandPrefix(req);
        applyDefaultIdSuffix(req);

        if (StringUtils.isNotBlank(this.hostname)) {
            try
            {
                JabberIMConnectionProvider.getInstance().createConnection(this);
            }
            catch (final Exception e)
            {
                throw new FormException("Unable to create Client: " + e, null);
            }
        } else {
            LOGGER.info("No hostname specified.");
        }
        save();
        return super.configure(req, json);		
	}

    /**
     * Validates the server name.
     */
    public void doServerCheck(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        new FormFieldValidator(req, rsp, false) {
            @Override
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

    /**
     * {@inheritDoc}
     */
	@Override
	public boolean isApplicable(Class jobType) {
		return true;
	}
}
