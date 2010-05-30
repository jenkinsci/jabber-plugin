/**
 * 
 */
package hudson.plugins.jabber.im.transport;

import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.plugins.im.IMException;
import hudson.plugins.im.IMMessageTarget;
import hudson.plugins.im.IMMessageTargetConversionException;
import hudson.plugins.im.IMMessageTargetConverter;
import hudson.plugins.im.IMPublisherDescriptor;
import hudson.plugins.im.NotificationStrategy;
import hudson.plugins.im.tools.Assert;
import hudson.plugins.im.tools.ExceptionHelper;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;

import net.sf.json.JSONObject;

import org.acegisecurity.Authentication;
import org.acegisecurity.AuthenticationException;
import org.acegisecurity.providers.UsernamePasswordAuthenticationToken;
import org.apache.commons.lang.StringUtils;
import org.jivesoftware.smack.Roster.SubscriptionMode;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

public class JabberPublisherDescriptor extends BuildStepDescriptor<Publisher> implements IMPublisherDescriptor {
    private static final Logger LOGGER = Logger.getLogger(JabberPublisherDescriptor.class.getName());
    
    private static final String PREFIX = "jabberPlugin.";
    public static final String PARAMETERNAME_ENABLED = JabberPublisherDescriptor.PREFIX + "enabled";
    public static final String PARAMETERNAME_PORT = JabberPublisherDescriptor.PREFIX + "port";
    public static final String PARAMETERNAME_HOSTNAME = JabberPublisherDescriptor.PREFIX + "hostname";
    public static final String PARAMETERNAME_SSL = JabberPublisherDescriptor.PREFIX + "ssl";
    public static final String PARAMETERNAME_SASL = JabberPublisherDescriptor.PREFIX + "enableSASL";
    public static final String PARAMETERNAME_PRESENCE = JabberPublisherDescriptor.PREFIX + "exposePresence";
    public static final String PARAMETERNAME_PASSWORD = JabberPublisherDescriptor.PREFIX + "password";
    public static final String PARAMETERNAME_JABBERID = JabberPublisherDescriptor.PREFIX + "jabberId";
    public static final String PARAMETERNAME_GROUP_NICKNAME = JabberPublisherDescriptor.PREFIX + "groupNick";
    public static final String PARAMETERNAME_TARGETS = JabberPublisherDescriptor.PREFIX + "targets";
    public static final String PARAMETERNAME_STRATEGY = JabberPublisherDescriptor.PREFIX + "strategy";
    public static final String PARAMETERNAME_NOTIFY_START = JabberPublisherDescriptor.PREFIX + "notifyStart";
    public static final String PARAMETERNAME_NOTIFY_SUSPECTS = JabberPublisherDescriptor.PREFIX + "notifySuspects";
    public static final String PARAMETERNAME_NOTIFY_CULPRITS = JabberPublisherDescriptor.PREFIX + "notifyCulprits";
    public static final String PARAMETERNAME_NOTIFY_FIXERS = JabberPublisherDescriptor.PREFIX + "notifyFixers";
    public static final String PARAMETERNAME_NOTIFY_UPSTREAM_COMMITTERS = JabberPublisherDescriptor.PREFIX + "notifyUpstreamCommitters";
    public static final String PARAMETERNAME_INITIAL_GROUPCHATS = JabberPublisherDescriptor.PREFIX + "initialGroupChats";
    public static final String PARAMETERNAME_COMMAND_PREFIX = JabberPublisherDescriptor.PREFIX + "commandPrefix";
    public static final String PARAMETERNAME_DEFAULT_ID_SUFFIX = JabberPublisherDescriptor.PREFIX + "defaultIdSuffix";
    public static final String PARAMETERNAME_HUDSON_LOGIN = JabberPublisherDescriptor.PREFIX + "hudsonLogin";
    public static final String PARAMETERNAME_HUDSON_PASSWORD = JabberPublisherDescriptor.PREFIX + "hudsonPassword";
    public static final String PARAMETERNAME_SUBSCRIPTION_MODE = JabberPublisherDescriptor.PREFIX + "subscriptionMode";
    public static final String[] PARAMETERVALUE_SUBSCRIPTION_MODE;
    static {
    	SubscriptionMode[] modes = SubscriptionMode.values();
    	PARAMETERVALUE_SUBSCRIPTION_MODE = new String[modes.length];
    	for (int i=0; i < modes.length; i++) {
    		PARAMETERVALUE_SUBSCRIPTION_MODE[i] = modes[i].name();
    	}
    }
    
    public static final String[] PARAMETERVALUE_STRATEGY_VALUES = NotificationStrategy.getDisplayNames();
    public static final String PARAMETERVALUE_STRATEGY_DEFAULT = NotificationStrategy.STATECHANGE_ONLY.getDisplayName();
    public static final String DEFAULT_COMMAND_PREFIX = "!";
    
    private static final int DEFAULT_PORT = 5222;
    
    // big Boolean to support backwards compatibility
    private Boolean enabled;
    private int port = DEFAULT_PORT;
    private String hostname;
    
    /**
     * Only left here for deserialization compatibility with old instances.
     * @deprecated not supported anymore. Any half decent jabber server doesn't need this.
     */
    @SuppressWarnings("unused")
	@Deprecated
	private transient boolean legacySSL;
    
    // the following 2 are actually the Jabber nick and password. For backward compatibility I cannot rename them
    private String hudsonNickname;
    private String hudsonPassword;
    private String groupChatNickname;
    private boolean exposePresence = true;
    private boolean enableSASL = true;
    private String initialGroupChats;
    private String commandPrefix = DEFAULT_COMMAND_PREFIX;
    private String defaultIdSuffix;
    private String hudsonCiLogin;
    private String hudsonCiPassword;
    private String subscriptionMode = SubscriptionMode.accept_all.name();

    public JabberPublisherDescriptor()
    {
        super(JabberPublisher.class);
        load();
        
        if (isEnabled()) {
            try {
            	JabberIMConnectionProvider.setDesc(this);
            } catch (final Exception e) {
                // Server temporarily unavailable or misconfigured?
                LOGGER.warning(ExceptionHelper.dump(e));
            }
        } else {
            try {
				JabberIMConnectionProvider.setDesc(null);
			} catch (IMException e) {
				// ignore
				LOGGER.info(ExceptionHelper.dump(e));
			}
        }
    }
    
    @Override
	public void load() {
		super.load();
    	if (this.enabled == null) {
        	// migrate from plugin < v1.0
        	if (Util.fixEmptyAndTrim(this.hudsonNickname) != null) {
        		this.enabled = Boolean.TRUE;
        	} else {
        		this.enabled = Boolean.FALSE;
        	}
        }
    	
    	if (this.subscriptionMode == null) {
    		this.subscriptionMode = SubscriptionMode.accept_all.name();
    	}
	}

	// TODO: reuse the checkHostAccessibility method for this
    private void applyHostname(final HttpServletRequest req, boolean check) throws FormException
    {
        final String s = req.getParameter(JabberPublisherDescriptor.PARAMETERNAME_HOSTNAME);
        if (check && (s != null) && (s.trim().length() > 0))
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

    private void applyNickname(final HttpServletRequest req, boolean check) throws FormException
    {
        this.hudsonNickname = req.getParameter(JabberPublisherDescriptor.PARAMETERNAME_JABBERID);
        if (check) {
	        if ((this.hostname != null) && ((this.hudsonNickname == null) || (this.hudsonNickname.trim().length() == 0)))
	        {
	            throw new FormException("Account/Nickname cannot be empty.",
	                    JabberPublisherDescriptor.PARAMETERNAME_JABBERID);
	        }
        }
    }

    private void applyPassword(final HttpServletRequest req, boolean check) throws FormException
    {
        this.hudsonPassword = req.getParameter(JabberPublisherDescriptor.PARAMETERNAME_PASSWORD);
        if (check) {
	        if (((this.hostname != null) && (this.hudsonPassword == null))
	    		|| (this.hudsonPassword.trim().length() == 0)) {
	            throw new FormException("Password cannot be empty.", JabberPublisherDescriptor.PARAMETERNAME_PASSWORD);
	        }
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

    private void applyPort(final HttpServletRequest req, boolean check) throws FormException
    {
        final String p = Util.fixEmptyAndTrim(req.getParameter(JabberPublisherDescriptor.PARAMETERNAME_PORT));
        if (p != null)
        {
            try
            {
                final int i = Integer.parseInt(p);
                if (check && ((i < 0) || (i > 65535))) {
                    throw new FormException("Port out of range.", JabberPublisherDescriptor.PARAMETERNAME_PORT);
                }
                this.port = i;
            }
            catch (final NumberFormatException e)
            {
                throw new FormException("Port cannot be parsed.", JabberPublisherDescriptor.PARAMETERNAME_PORT);
            }
        } else {
            this.port = DEFAULT_PORT;
        }
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
     
     private void applyHudsonLoginPassword(HttpServletRequest req) throws FormException {
    	 this.hudsonCiLogin = Util.fixEmptyAndTrim(req.getParameter(PARAMETERNAME_HUDSON_LOGIN));
    	 this.hudsonCiPassword = Util.fixEmptyAndTrim(req.getParameter(PARAMETERNAME_HUDSON_PASSWORD));
    	 if(this.hudsonCiLogin != null) {
    		 Authentication auth = new UsernamePasswordAuthenticationToken(this.hudsonCiLogin, this.hudsonCiPassword);
    		 try {
				Hudson.getInstance().getSecurityRealm().getSecurityComponents().manager.authenticate(auth);
			} catch (AuthenticationException e) {
				throw new FormException(e, "Bad Hudson credentials");
			}
    	 }
     }
    
    /**
     * This human readable name is used in the configuration screen.
     */
    @Override
    public String getDisplayName() {
        return "Jabber Notification";
    }
    
    @Override
    public String getPluginDescription() {
        return "Jabber plugin";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isEnabled() {
    	return Boolean.TRUE.equals(this.enabled);
    }

    @Override
    public String getHostname()
    {
        return this.hostname;
    }
    
    /**
     * Returns the real host to use.
     * I.e. when hostname is set returns hostname.
     * Otherwise returns {@link #getServiceName()}
     */
    @Override
    public String getHost() {
        if (StringUtils.isNotBlank(this.hostname)) {
            return this.hostname;
        } else {
            return getServiceName();
        }
    }

    /**
     * Returns the jabber ID.
     * 
     * The jabber ID may have the syntax <user>[@<domain>[/<resource]]
     */
    public String getJabberId()
    {
        return this.hudsonNickname;
    }

    @Override
    public String getPassword()
    {
        return this.hudsonPassword;
    }

    public String getGroupChatNickname()
    {
        return this.groupChatNickname;
    }

    @Override
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

    public boolean isEnableSASL() {
        return enableSASL;
    }

    public boolean isExposePresence() {
        return this.exposePresence;
    }
    
    public String getSubscriptionMode() {
    	return this.subscriptionMode;
    }

    /**
     * Gets the whitespace separated list of group chats to join,
     * or null if nothing is configured.
     */
    public String getInitialGroupChats() {
    	return Util.fixEmptyAndTrim(this.initialGroupChats);
    }

    @Override
    public String getDefaultIdSuffix() {
        return this.defaultIdSuffix;
    }

    @Override
    public String getCommandPrefix() {
    	return this.commandPrefix;
    }

    /**
     * Creates a new instance of {@link JabberPublisher} from a submitted form.
     */
    @Override
    public JabberPublisher newInstance(final StaplerRequest req, JSONObject formData) throws FormException
    {
        Assert.isNotNull(req, "Parameter 'req' must not be null.");
        final String t = req.getParameter(JabberPublisherDescriptor.PARAMETERNAME_TARGETS);
        final String[] split;
        if (t != null) {
        	split = t.split("\\s");
        } else {
        	split = new String[0];
        }
        
    	List<IMMessageTarget> targets = new ArrayList<IMMessageTarget>(split.length);
    	
        
        try {
			final IMMessageTargetConverter conv = getIMMessageTargetConverter();
			for (String fragment : split) {
			    IMMessageTarget createIMMessageTarget;
			    createIMMessageTarget = conv.fromString(fragment);
			    if (createIMMessageTarget != null)  {
			        targets.add(createIMMessageTarget);
			    }
			}
		} catch (IMMessageTargetConversionException e) {
			throw new FormException("Invalid Jabber address", e, JabberPublisherDescriptor.PARAMETERNAME_TARGETS);
		}
        
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
        boolean notifyStart = "on".equals(req.getParameter(PARAMETERNAME_NOTIFY_START));
        boolean notifySuspects = "on".equals(req.getParameter(PARAMETERNAME_NOTIFY_SUSPECTS));
        boolean notifyCulprits = "on".equals(req.getParameter(PARAMETERNAME_NOTIFY_CULPRITS));
        boolean notifyFixers = "on".equals(req.getParameter(PARAMETERNAME_NOTIFY_FIXERS));
        boolean notifyUpstream = "on".equals(req.getParameter(PARAMETERNAME_NOTIFY_UPSTREAM_COMMITTERS));
        try {
            return new JabberPublisher(targets, n, notifyStart, notifySuspects, notifyCulprits,
            		notifyFixers, notifyUpstream);
        } catch (final IMMessageTargetConversionException e) {
            throw new FormException(e, JabberPublisherDescriptor.PARAMETERNAME_TARGETS);
        }
    }

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean configure(StaplerRequest req, JSONObject json) throws hudson.model.Descriptor.FormException {
		String en = req.getParameter(PARAMETERNAME_ENABLED);
		this.enabled = Boolean.valueOf(en != null);
		this.exposePresence = req.getParameter(JabberPublisherDescriptor.PARAMETERNAME_PRESENCE) != null;
        this.enableSASL = req.getParameter(JabberPublisherDescriptor.PARAMETERNAME_SASL) != null;
		this.subscriptionMode = Util.fixEmptyAndTrim(req.getParameter(JabberPublisherDescriptor.PARAMETERNAME_SUBSCRIPTION_MODE));
        applyHostname(req, this.enabled);
        applyPort(req, this.enabled);
        applyNickname(req, this.enabled);
        applyPassword(req, this.enabled);
        applyGroupChatNickname(req);
        applyInitialGroupChats(req);
        applyCommandPrefix(req);
        applyDefaultIdSuffix(req);
        applyHudsonLoginPassword(req);

        if (isEnabled()) {
            try {
            	JabberIMConnectionProvider.setDesc(this);
                JabberIMConnectionProvider.getInstance().currentConnection();
            } catch (final Exception e) {
                //throw new FormException("Unable to create Client: " + ExceptionHelper.dump(e), null);
            	LOGGER.warning(ExceptionHelper.dump(e));
            }
        } else {
        	JabberIMConnectionProvider.getInstance().releaseConnection();
        	try {
				JabberIMConnectionProvider.setDesc(null);
			} catch (IMException e) {
				// ignore
				LOGGER.info(ExceptionHelper.dump(e));
			}
            LOGGER.info("No hostname specified.");
        }
        save();
        return super.configure(req, json);		
	}

	
	public FormValidation doJabberIdCheck(@QueryParameter String jabberId,
			@QueryParameter final String hostname, @QueryParameter final String port) {
	    if(!Hudson.getInstance().hasPermission(Hudson.ADMINISTER)) {
            return FormValidation.ok();
        }
	    
	    if (jabberId == null || jabberId.trim().length() == 0) {
	        return FormValidation.error("Jabber ID must not be empty!");
	    } else if (Util.fixEmptyAndTrim(hostname) != null) {
	    	// validation has already been done for the hostname field
	    	return FormValidation.ok();
	    } else if (JabberUtil.getDomainPart(jabberId) != null) {
	        String host = JabberUtil.getDomainPart(jabberId);
	        try {
                checkHostAccessibility(host, port);
                return FormValidation.ok();
            } catch (UnknownHostException e) {
                return FormValidation.error("Unknown host " + host);
            } catch (NumberFormatException e) {
                return FormValidation.error("Invalid port " + port);
            } catch (IOException e) {
                return FormValidation.error("Unable to connect to "+hostname+":"+port+" : "+e.getMessage());
            }
	    } else {
	    	return FormValidation.error("No hostname specified - neither via 'Jabber ID' nor via 'Server'!");
	    }
	}
	
    /**
     * Validates the connection information.
     */
    public FormValidation doServerCheck(@QueryParameter final String hostname,
            @QueryParameter final String port) {
        if(!Hudson.getInstance().hasPermission(Hudson.ADMINISTER)) {
            return FormValidation.ok();
        }
        String host = Util.fixEmptyAndTrim(hostname);
        String p = Util.fixEmptyAndTrim(port);
        if (host == null) {
            return FormValidation.ok();
        } else {
            try {
                checkHostAccessibility(host, port);
                return FormValidation.ok();
            } catch (UnknownHostException e) {
                return FormValidation.error("Unknown host " + host);
            } catch (NumberFormatException e) {
                return FormValidation.error("Invalid port " + port);
            } catch (IOException e) {
                return FormValidation.error("Unable to connect to "+hostname+":"+p+" : "+e.getMessage());
            }
        }
    }
    
    private static void checkHostAccessibility(String hostname, String port)
        throws UnknownHostException, IOException, NumberFormatException {
        hostname = Util.fixEmptyAndTrim(hostname);
        port = Util.fixEmptyAndTrim(port);
        int iPort = DEFAULT_PORT;
        InetAddress address = InetAddress.getByName(hostname);
        
        if (port != null) {
            iPort = Integer.parseInt(port);
        }
        
        Socket s = new Socket(address, iPort);
        s.close();
    }

    /**
     * {@inheritDoc}
     */
	@Override
	@SuppressWarnings("unchecked")
	public boolean isApplicable(Class<? extends AbstractProject> jobType) {
		return true;
	}
	
	/**
	 * Returns the 'user' part of the Jabber ID. E.g. returns
	 * 'john.doe' for 'john.doe@gmail.com' or
	 * 'alfred.e.neumann' for 'alfred.e.neumann'.
	 */
	@Override
	public String getUserName() {
		return JabberUtil.getUserPart(getJabberId());
	}
	
	/**
     * Returns 'gmail.com' portion of the nick name 'john.doe@gmail.com', or
     * null if not found.
     */
    String getServiceName() {
        return JabberUtil.getDomainPart(getJabberId());
    }

	@Override
	public String getHudsonPassword() {
		return this.hudsonCiPassword;
	}

	@Override
	public String getHudsonUserName() {
		return this.hudsonCiLogin;
	}
	
	@Override
	public IMMessageTargetConverter getIMMessageTargetConverter() {
		return JabberPublisher.CONVERTER;
	}

	@Override
	public List<IMMessageTarget> getDefaultTargets() {
		// not implemented for Jabber bot
		return Collections.emptyList();
	}
}
