/**
 * 
 */
package hudson.plugins.jabber.im.transport;

import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.plugins.im.GroupChatIMMessageTarget;
import hudson.plugins.im.IMException;
import hudson.plugins.im.IMMessageTarget;
import hudson.plugins.im.IMMessageTargetConversionException;
import hudson.plugins.im.IMMessageTargetConverter;
import hudson.plugins.im.IMPublisherDescriptor;
import hudson.plugins.im.MatrixJobMultiplier;
import hudson.plugins.im.NotificationStrategy;
import hudson.plugins.im.build_notify.BuildToChatNotifier;
import hudson.plugins.im.tools.ExceptionHelper;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import hudson.util.Scrambler;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;

import net.sf.json.JSONObject;

import org.apache.commons.lang.StringUtils;
import org.jivesoftware.smack.Roster.SubscriptionMode;
import org.jivesoftware.smack.proxy.ProxyInfo.ProxyType;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.springframework.util.Assert;

public class JabberPublisherDescriptor extends BuildStepDescriptor<Publisher> implements IMPublisherDescriptor {

    private static final Logger LOGGER = Logger.getLogger(JabberPublisherDescriptor.class.getName());
    
    private static final String PREFIX = "jabberPlugin.";
	private static final int DEFAULT_PROXYPORT = 3128;

    public static final String PARAMETERNAME_ENABLED = PREFIX + "enabled";
    public static final String PARAMETERNAME_PORT = PREFIX + "port";
    public static final String PARAMETERNAME_HOSTNAME = PREFIX + "hostname";
    public static final String PARAMETERNAME_USEPROXY = PREFIX + "useProxy";
    public static final String PARAMETERNAME_PROXYTYPE = PREFIX + "proxyType";
    public static final String PARAMETERNAME_PROXYHOST = PREFIX + "proxyHost";
    public static final String PARAMETERNAME_PROXYPORT = PREFIX + "proxyPort";
    public static final String PARAMETERNAME_PROXYUSER = PREFIX + "proxyUser";
    public static final String PARAMETERNAME_PROXYPASS = PREFIX + "proxyPass";
    public static final String PARAMETERNAME_SSL = PREFIX + "ssl";
    public static final String PARAMETERNAME_SASL = PREFIX + "enableSASL";
    public static final String PARAMETERNAME_PRESENCE = PREFIX + "exposePresence";
    public static final String PARAMETERNAME_PASSWORD = PREFIX + "password";
    public static final String PARAMETERNAME_JABBERID = PREFIX + "jabberId";
    public static final String PARAMETERNAME_GROUP_NICKNAME = PREFIX + "groupNick";
    public static final String PARAMETERNAME_TARGETS = PREFIX + "targets";
    public static final String PARAMETERNAME_INITIAL_GROUPCHATS = PREFIX + "initialGroupChats";
    public static final String PARAMETERNAME_COMMAND_PREFIX = PREFIX + "commandPrefix";
    public static final String PARAMETERNAME_DEFAULT_ID_SUFFIX = PREFIX + "defaultIdSuffix";
    public static final String PARAMETERNAME_SUBSCRIPTION_MODE = PREFIX + "subscriptionMode";
    public static final String PARAMETERNAME_EMAIL_ADDRESS_AS_JABBERID = PREFIX + "emailAsJabberId";
    public static final String[] PARAMETERVALUE_SUBSCRIPTION_MODE;
    public static final String[] PARAMETERVALUE_PROXYTYPES;
    static {
    	SubscriptionMode[] modes = SubscriptionMode.values();
    	PARAMETERVALUE_SUBSCRIPTION_MODE = new String[modes.length];
    	for (int i=0; i < modes.length; i++) {
    		PARAMETERVALUE_SUBSCRIPTION_MODE[i] = modes[i].name();
    	}
    	ProxyType[] ptypes = ProxyType.values();
    	PARAMETERVALUE_PROXYTYPES = new String[ptypes.length];
    	for (int i=0; i < ptypes.length; i++) {
    		PARAMETERVALUE_PROXYTYPES[i] = ptypes[i].name();
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

    /**
     * Marks if passwords are scrambled as they are since 1.23.
     * Needed to migrate old, unscrambled passwords.
     */
    private boolean scrambledPasswords = false;
    
    /**
     * @deprecated replaced by {@link #defaultTargets}
     * Still needed to deserialize old descriptors
     */
    @Deprecated
    private String initialGroupChats;
    private List<IMMessageTarget> defaultTargets;
    
    private String commandPrefix = DEFAULT_COMMAND_PREFIX;
    private String defaultIdSuffix;
    private String hudsonCiLogin;
    private String subscriptionMode = SubscriptionMode.accept_all.name();
    private boolean emailAddressAsJabberId;

    // Proxy parameters
    private ProxyType proxyType = ProxyType.NONE;
    private int proxyPort = DEFAULT_PROXYPORT;
    private String proxyHost = null;
    private String proxyUser = null;
    private String proxyPass = null;

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
        final String s = req.getParameter(PARAMETERNAME_HOSTNAME);
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
                        PARAMETERNAME_HOSTNAME);
            }
        }
        else
        {
            this.hostname = null;
        }
    }

    private void applyNickname(final HttpServletRequest req, boolean check) throws FormException
    {
        this.hudsonNickname = req.getParameter(PARAMETERNAME_JABBERID);
        if (check) {
	        if ((this.hostname != null) && ((this.hudsonNickname == null) || (this.hudsonNickname.trim().length() == 0)))
	        {
	            throw new FormException("Account/Nickname cannot be empty.",
	                    PARAMETERNAME_JABBERID);
	        }
        }
    }

    private void applyPassword(final HttpServletRequest req, boolean check) throws FormException
    {
        this.scrambledPasswords = true;
        String password = req.getParameter(PARAMETERNAME_PASSWORD);
        if (check) {
	        if ((this.hostname != null)
	              && ((password == null) || (password.trim().length() == 0))) {
	            throw new FormException("Password cannot be empty.", PARAMETERNAME_PASSWORD);
	        }
        }
        this.hudsonPassword = Scrambler.scramble(password);
    }

    private void applyGroupChatNickname(final HttpServletRequest req) throws FormException
    {
        this.groupChatNickname = req.getParameter(PARAMETERNAME_GROUP_NICKNAME);
        if (this.groupChatNickname != null && this.groupChatNickname.trim().length() == 0)
        {
            this.groupChatNickname = null;
        }
    }

    private void applyPort(final HttpServletRequest req, boolean check) throws FormException
    {
        final String p = Util.fixEmptyAndTrim(req.getParameter(PARAMETERNAME_PORT));
        if (p != null)
        {
            try
            {
                final int i = Integer.parseInt(p);
                if (check && ((i < 0) || (i > 65535))) {
                    throw new FormException("Port out of range.", PARAMETERNAME_PORT);
                }
                this.port = i;
            }
            catch (final NumberFormatException e)
            {
                throw new FormException("Port cannot be parsed.", PARAMETERNAME_PORT);
            }
        } else {
            this.port = DEFAULT_PORT;
        }
    }

    private void applyInitialGroupChats(final HttpServletRequest req) {
        String[] chatNames = req.getParameterValues("jabberPlugin.chat.name");
        String[] chatPasswords = req.getParameterValues("jabberPlugin.chat.password");
        String[] notifyOnlys = req.getParameterValues("jabberPlugin.chat.notificationOnly");
        this.defaultTargets = new ArrayList<IMMessageTarget>();
        
        if (chatNames != null) {
            for (int i = 0; i < chatNames.length; i++) {
                String chatName = chatNames[i];
                String chatPassword = Util.fixEmptyAndTrim(chatPasswords[i]);
                boolean notifyOnly = notifyOnlys != null ? "on".equalsIgnoreCase(notifyOnlys[i]) : false; 
                this.defaultTargets.add(new GroupChatIMMessageTarget(chatName, chatPassword, notifyOnly));
            }
        }
    }
    
    private void applyCommandPrefix(final HttpServletRequest req) {
    	String prefix = req.getParameter(PARAMETERNAME_COMMAND_PREFIX);
    	if ((prefix != null) && (prefix.trim().length() > 0)) {
    		this.commandPrefix = prefix;
    	} else {
    		this.commandPrefix = DEFAULT_COMMAND_PREFIX;
    	}
    }

     private void applyDefaultIdSuffix(final HttpServletRequest req) {
    	String suffix = req.getParameter(PARAMETERNAME_DEFAULT_ID_SUFFIX);
    	if ((suffix != null) && (suffix.trim().length() > 0)) {
    		this.defaultIdSuffix = suffix.trim();
    	} else {
    		this.defaultIdSuffix = "";
    	}
    }
     
     private void applyHudsonLoginPassword(HttpServletRequest req) throws FormException {
    	 this.hudsonCiLogin = Util.fixEmptyAndTrim(req.getParameter(PARAMETERNAME_HUDSON_LOGIN));
    	 
    	 // TODO: add validation of login name, again?
//    	 if(this.hudsonCiLogin != null) {
//    		 Authentication auth = new UsernamePasswordAuthenticationToken(this.hudsonCiLogin, this.hudsonCiPassword);
//    		 try {
//				Hudson.getInstance().getSecurityRealm().getSecurityComponents().manager.authenticate(auth);
//			} catch (AuthenticationException e) {
//				throw new FormException(e, "Bad Jenkins credentials");
//			}
//    	 }
     }
    
    private void applyProxy(final HttpServletRequest req) throws FormException {
        
        boolean enabled = "on".equals(req.getParameter(PARAMETERNAME_USEPROXY));
        
        if (!enabled) {
            this.proxyType = ProxyType.NONE;
            return;
        }

        this.proxyType = ProxyType.NONE;
        String s = req.getParameter(PARAMETERNAME_PROXYTYPE);
        if (s != null) {
            try {
                this.proxyType = ProxyType.valueOf(s);
            } catch (RuntimeException e) {
                throw new FormException("Proxy type cannot be parsed.",
                        PARAMETERNAME_PROXYTYPE);
            }
        }

        if (ProxyType.NONE == this.proxyType)
            return;

        s = req.getParameter(PARAMETERNAME_PROXYHOST);
        if ((s != null) && (s.trim().length() > 0)) {
            try {
                InetAddress.getByName(s); // try to resolve
                this.proxyHost = s;
            } catch (final UnknownHostException e) {
                this.proxyType = ProxyType.NONE;
                throw new FormException("Cannot find Proxy host '" + s + "'.",
                        PARAMETERNAME_PROXYHOST);
            }
        } else
            this.proxyType = ProxyType.NONE;
       if (ProxyType.NONE == this.proxyType)
            return;

        s = Util.fixEmptyAndTrim(req.getParameter(PARAMETERNAME_PROXYPORT));
        if (s != null) {
            try {
                final int i = Integer.parseInt(s);
                if ((i < 0) || (i > 65535)) {
                    throw new FormException("Proxy port out of range.",
                            PARAMETERNAME_PROXYPORT);
                }
                this.proxyPort = i;
            }
            catch (final NumberFormatException e) {
                this.proxyType = ProxyType.NONE;
                throw new FormException("Proxy port cannot be parsed.",
                        PARAMETERNAME_PROXYPORT);
            }
        } else
            this.proxyPort = DEFAULT_PROXYPORT;

        this.proxyUser = req.getParameter(PARAMETERNAME_PROXYUSER);
        this.proxyPass = req.getParameter(PARAMETERNAME_PROXYPASS);
        if ((null != this.proxyUser) && (this.proxyUser.length() > 0) && (null == this.proxyPass))
            this.proxyPass = "";
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

    /**
     * Returns the overridden hostname in case e.g. DNS lookup by service name doesn't work. 
     */
    // Note: unlike the same method in the interface this one is NOT deprecated
    // as we need it for hostname overriding
    @Override
    public String getHostname() {
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
    public String getPassword() {
        return Scrambler.descramble(this.hudsonPassword);
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

    public boolean isEmailAddressAsJabberId() {
        return emailAddressAsJabberId;
    }

    @Override
    public String getDefaultIdSuffix() {
        return this.defaultIdSuffix;
    }

    @Override
    public String getCommandPrefix() {
    	return this.commandPrefix;
    }

    public String getProxyHost() {
        return this.proxyHost;
    }

    public String getProxyUser() {
        return this.proxyUser;
    }

    public String getProxyPass() {
        return this.proxyPass;
    }

    public ProxyType getProxyType() {
        return this.proxyType;
    }

    public int getProxyPort() {
        return this.proxyPort;
    }

    /**
     * Returns the text to be put into the form field.
     */
    public String getProxyPortString() {
        return String.valueOf(proxyPort);
    }

    /**
     * Returns the text to be put into the form field.
     */
    public String getProxyTypeString() {
        return this.proxyType.name();
    }

    /**
     * Creates a new instance of {@link JabberPublisher} from a submitted form.
     */
    @Override
    public JabberPublisher newInstance(final StaplerRequest req, JSONObject formData) throws FormException
    {
        Assert.notNull(req, "Parameter 'req' must not be null.");
        final String t = req.getParameter(PARAMETERNAME_TARGETS);
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
			throw new FormException("Invalid Jabber address", e, PARAMETERNAME_TARGETS);
		}
        
        String n = req.getParameter(PARAMETERNAME_STRATEGY);
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
        
        MatrixJobMultiplier matrixJobMultiplier = MatrixJobMultiplier.ONLY_CONFIGURATIONS;
        if (formData.has("matrixNotifier")) {
            String o = formData.getString("matrixNotifier");
            matrixJobMultiplier = MatrixJobMultiplier.valueOf(o);
        }
        
        try {
            return new JabberPublisher(targets, n, notifyStart, notifySuspects, notifyCulprits,
            		notifyFixers, notifyUpstream,
            		req.bindJSON(BuildToChatNotifier.class,formData.getJSONObject("buildToChatNotifier")),
            		matrixJobMultiplier);
        } catch (final IMMessageTargetConversionException e) {
            throw new FormException(e, PARAMETERNAME_TARGETS);
        }
    }

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean configure(StaplerRequest req, JSONObject json) throws hudson.model.Descriptor.FormException {
		String en = req.getParameter(PARAMETERNAME_ENABLED);
		this.enabled = Boolean.valueOf(en != null);
		this.exposePresence = req.getParameter(PARAMETERNAME_PRESENCE) != null;
        this.enableSASL = req.getParameter(PARAMETERNAME_SASL) != null;
		this.subscriptionMode = Util.fixEmptyAndTrim(req.getParameter(PARAMETERNAME_SUBSCRIPTION_MODE));
		this.emailAddressAsJabberId = req.getParameter(PARAMETERNAME_EMAIL_ADDRESS_AS_JABBERID) != null;
        applyHostname(req, this.enabled);
        applyPort(req, this.enabled);
        applyNickname(req, this.enabled);
        applyPassword(req, this.enabled);
        applyGroupChatNickname(req);
        applyInitialGroupChats(req);
        applyCommandPrefix(req);
        applyDefaultIdSuffix(req);
        applyHudsonLoginPassword(req);
        applyProxy(req);

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
			@QueryParameter final String hostname, @QueryParameter final String port, @QueryParameter final String proxyType) {
	    if(!Hudson.getInstance().hasPermission(Hudson.ADMINISTER)) {
            return FormValidation.ok();
        }
	    
	    if (jabberId == null || jabberId.trim().length() == 0) {
	        return FormValidation.error("Jabber ID must not be empty!");
	    } else if (Util.fixEmptyAndTrim(hostname) != null) {
	    	// validation has already been done for the hostname field
	    	return FormValidation.ok();
	    } else if (JabberUtil.getDomainPart(jabberId) != null) {
			String pts = Util.fixEmptyAndTrim(proxyType);
	        String host = JabberUtil.getDomainPart(jabberId);
			ProxyType pt = ProxyType.NONE;
	        try {
				if (pts != null) {
					pt = ProxyType.valueOf(pts);
				}
			} catch (IllegalArgumentException e) {
				return FormValidation.error("Invalid proxy type " + proxyType);
			}
	        try {
                checkHostAccessibility(host, port, pt);
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
	
	public FormValidation doProxyCheck(@QueryParameter final String proxyType,
			@QueryParameter final String proxyHost, @QueryParameter final String proxyPort) {
		if(!Hudson.getInstance().hasPermission(Hudson.ADMINISTER)) {
			return FormValidation.ok();
		}
		String host = Util.fixEmptyAndTrim(proxyHost);
		String p = Util.fixEmptyAndTrim(proxyPort);
		String pts = Util.fixEmptyAndTrim(proxyType);
		if (host == null) {
			return FormValidation.ok();
		} else {
			ProxyType pt = ProxyType.NONE;
			try {
				if (pts != null) {
					pt = ProxyType.valueOf(pts);
				}
			} catch (IllegalArgumentException e) {
				return FormValidation.error("Invalid proxy type " + proxyType);
			}
			if (pt != ProxyType.NONE) {
				try {
					checkHostAccessibility(host, p, ProxyType.NONE);
				} catch (UnknownHostException e) {
					return FormValidation.error("Unknown proxy host " + proxyHost);
				} catch (NumberFormatException e) {
					return FormValidation.error("Invalid proxy port " + proxyPort);
				} catch (IOException e) {
					return FormValidation.error("Unable to connect to "+host+":"+p+" : "+e.getMessage());
				}
			}
			return FormValidation.ok();
		}
	}

    /**
     * Validates the connection information.
     */
    public FormValidation doServerCheck(@QueryParameter final String hostname,
			@QueryParameter final String port, @QueryParameter final String proxyType) {
        if(!Hudson.getInstance().hasPermission(Hudson.ADMINISTER)) {
            return FormValidation.ok();
        }
        String host = Util.fixEmptyAndTrim(hostname);
        String p = Util.fixEmptyAndTrim(port);
		String pts = Util.fixEmptyAndTrim(proxyType);
        if (host == null) {
            return FormValidation.ok();
        } else {
			ProxyType pt = ProxyType.NONE;
            try {
				if (pts != null) {
					pt = ProxyType.valueOf(pts);
				}
			} catch (IllegalArgumentException e) {
				return FormValidation.error("Invalid proxy type " + proxyType);
			}
			try {
				checkHostAccessibility(host, port, pt);
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
    
	private static void checkHostAccessibility(String hostname, String port, ProxyType pt)
        throws UnknownHostException, IOException, NumberFormatException {
        hostname = Util.fixEmptyAndTrim(hostname);
        port = Util.fixEmptyAndTrim(port);
        int iPort = DEFAULT_PORT;
        InetAddress address = InetAddress.getByName(hostname);
        
        if (port != null) {
            iPort = Integer.parseInt(port);
        }
		if (pt == ProxyType.NONE) {
			// Only try connect if not using a proxy
		    Socket s = new Socket(address, iPort);
		    s.close();
		} else {
		    // TODO:
		    // Socket s = new Socket(Proxy)
		    // s.connect(host, port)
		    // s.close();
		}
	}

    /**
     * {@inheritDoc}
     */
	@Override
	@SuppressWarnings("rawtypes")
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
	public String getHudsonUserName() {
		return this.hudsonCiLogin;
	}
	
	@Override
	public IMMessageTargetConverter getIMMessageTargetConverter() {
		return JabberPublisher.CONVERTER;
	}

	@Override
	public List<IMMessageTarget> getDefaultTargets() {
		return this.defaultTargets;
	}
	
	/**
     * Deserialize old descriptors.
     */
    private Object readResolve() {
        
        if (this.defaultTargets == null) {
            this.defaultTargets = new ArrayList<IMMessageTarget>();
        }
        
        if (this.initialGroupChats != null) {
            String[] split = this.initialGroupChats.trim().split("\\s");
            for (String chatName : split) {
                this.defaultTargets.add(new GroupChatIMMessageTarget(chatName, null, false));
            }
            this.initialGroupChats = null;
            save();
        }
        
        if (!this.scrambledPasswords) {
            this.hudsonPassword = Scrambler.scramble(this.hudsonPassword);
            this.scrambledPasswords = true;
            save();
        }
        
        return this;
    }
	
}
