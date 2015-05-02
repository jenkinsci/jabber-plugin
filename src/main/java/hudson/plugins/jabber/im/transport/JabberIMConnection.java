/*
 * Created on 06.03.2007
 */
package hudson.plugins.jabber.im.transport;

import hudson.Util;
import hudson.plugins.im.AbstractIMConnection;
import hudson.plugins.im.AuthenticationHolder;
import hudson.plugins.im.GroupChatIMMessageTarget;
import hudson.plugins.im.IMConnection;
import hudson.plugins.im.IMConnectionListener;
import hudson.plugins.im.IMException;
import hudson.plugins.im.IMMessageTarget;
import hudson.plugins.im.IMPresence;
import hudson.plugins.im.bot.Bot;
import hudson.plugins.im.tools.ExceptionHelper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.security.auth.kerberos.KerberosPrincipal;
import javax.security.sasl.SaslException;

import org.apache.commons.io.IOUtils;
import org.jivesoftware.smack.AbstractConnectionListener;
import org.jivesoftware.smack.Chat;
import org.jivesoftware.smack.ChatManager;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.ConnectionListener;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.Roster;
import org.jivesoftware.smack.Roster.SubscriptionMode;
import org.jivesoftware.smack.RosterEntry;
import org.jivesoftware.smack.SmackConfiguration;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.filter.MessageTypeFilter;
import org.jivesoftware.smack.filter.PacketFilter;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.PacketExtension;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.RosterPacket.ItemType;
import org.jivesoftware.smack.packet.XMPPError.Condition;
import org.jivesoftware.smack.proxy.ProxyInfo;
import org.jivesoftware.smack.proxy.ProxyInfo.ProxyType;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.util.StringUtils;
import org.jivesoftware.smackx.delay.packet.DelayInformation;
import org.jivesoftware.smackx.muc.MultiUserChat;
import org.jivesoftware.smackx.nick.packet.Nick;
import org.jivesoftware.smackx.ping.packet.Ping;
import org.jivesoftware.smackx.vcardtemp.packet.VCard;
import org.springframework.util.Assert;

import sun.security.util.HostnameChecker;

/**
 * Smack-specific implementation of {@link IMConnection}.
 * 
 * @author kutzi
 * @author Uwe Schaefer (original author)
 * @author jenky-hm
 */
class JabberIMConnection extends AbstractIMConnection {
	
	private static final Logger LOGGER = Logger.getLogger(JabberIMConnection.class.getName());
	
	private volatile XMPPConnection connection;

	private final Map<String, WeakReference<MultiUserChat>> groupChatCache = new HashMap<String, WeakReference<MultiUserChat>>();
	private final Map<String, WeakReference<Chat>> chatCache = new HashMap<String, WeakReference<Chat>>();
	private final Set<Bot> bots = new HashSet<Bot>();
	private final String passwd;
	private final String botCommandPrefix;
	/**
	 * Jabber 'nick'. This is just the username-part of the Jabber-ID.
	 * I.e. for 'john.doe@gmail.com' it is 'john.doe'.
	 */
	private final String nick;
    /**
     * The 'resource' part of the Jabber ID.
     * I.e. for 'john.doe@gmail.com/Jenkins' it is 'Jenkins' or
     * for 'john.doe@gmail.com' it is null.
     */
	@Nullable
	private final String resource;
	/**
	 * The nick name of the Jenkins bot to use in group chats.
	 * May be null in which case the nick is used.
	 */
	@Nullable
	private final String groupChatNick;
	/**
	 * Server name of the Jabber server.
	 */
	private final String hostnameOverride;
	private final int port;

	private final List<IMMessageTarget> groupChats;
	
	private IMPresence impresence;

    private String imStatusMessage;

    private final JabberPublisherDescriptor desc;
    private final AuthenticationHolder authentication;

	private Roster roster;
	
	/**
	 * Proxy parameters
	 */
	private final ProxyType proxytype;
	private final String proxyhost;
	private final String proxyuser;
	private final String proxypass;
	private final int proxyport;

	private final boolean acceptAllCerts;

	private Runnable keepAliveCommand;
	
	private ScheduledExecutorService scheduler;

	static {
		SmackConfiguration.setDefaultPacketReplyTimeout(20000);
		
		System.setProperty("smack.debuggerClass", JabberConnectionDebugger.class.getName());
	}
	
	JabberIMConnection(JabberPublisherDescriptor desc, AuthenticationHolder authentication) throws IMException {
	    super(desc);
		Assert.notNull(desc, "Parameter 'desc' must not be null.");
		this.desc = desc;
		this.authentication = authentication;
		this.hostnameOverride = desc.getHostname();
		this.port = desc.getPort();
		this.nick = desc.getNickname();
		this.resource = StringUtils.parseResource(desc.getJabberId());
		this.passwd = desc.getPassword();
		this.proxytype = desc.getProxyType();
		this.proxyhost = desc.getProxyHost();
		this.proxyport = desc.getProxyPort();
		this.proxyuser = desc.getProxyUser();
		this.proxypass = desc.getProxyPass();
		this.groupChatNick = desc.getNickname();
		this.botCommandPrefix = desc.getCommandPrefix();
		this.groupChats = desc.getDefaultTargets();
		this.impresence = desc.isExposePresence() ? IMPresence.AVAILABLE : IMPresence.UNAVAILABLE;
		this.acceptAllCerts = desc.isAcceptAllCerts();
	}

	@Override
	public boolean connect() {
	    lock();
	    try {
			try {
				if (!isConnected()) {
					if (createConnection()) {
						LOGGER.info("Connected to XMPP on "
								+ this.connection.getHost() + ":" + this.connection.getPort()
								+ "/" + this.connection.getServiceName()
								+ (this.connection.isSecureConnection() ? " using secure connection" : "")
								+ (this.connection.isUsingCompression() ? " using compression" : ""));
			
						// kutzi: I've read somewhere that status must be set, before one can do anything other
						// Don't know if it's true, but can't hurt, either.
						sendPresence();
						
						groupChatCache.clear();
						for (IMMessageTarget chat : this.groupChats) {
						    GroupChatIMMessageTarget groupChat = (GroupChatIMMessageTarget) chat;
							try {
								getOrCreateGroupChat(groupChat);
								LOGGER.info("Joined groupchat " + groupChat.getName());
							} catch (IMException e) {
								// if we got here, the XMPP connection could be established, but probably the groupchat name
								// is invalid
								LOGGER.warning("Unable to connect to groupchat '" + groupChat.getName() + "'. Did you append @conference or so to the name?\n"
										+ "Exception: " + ExceptionHelper.dump(e));
							}
						}
					} else {
						// clean-up if needed
						if (this.connection != null) {
							try {
								this.connection.disconnect();
							} catch (Exception e) {
								// ignore
							}
						}
						return false;
					}
				}
				return true;
			} catch (final Exception e) {
				LOGGER.warning(ExceptionHelper.dump(e));
				return false;
			}
		} finally {
		    unlock();
		}
	}

	@Override
    public void close() {
	    lock();
	    try {
			try {
				for (WeakReference<MultiUserChat> entry : groupChatCache.values()) {
					MultiUserChat chat = entry.get();
					if (chat != null && chat.isJoined()) {
						chat.leave();
					}
				}
				// there seems to be no way to leave a 1-on-1 chat with Smack
				
				this.groupChatCache.clear();
				this.chatCache.clear();
				
				if (this.scheduler != null) {
					this.scheduler.shutdownNow();
					this.scheduler = null;
				}
				
				if (this.connection.isConnected()) {
					this.connection.disconnect();
				}
			} catch (Exception e) {
				// ignore
				LOGGER.fine(e.toString());
			} finally {
				this.connection = null;
			}
		} finally {
		    unlock();
		}
	}

	private boolean createConnection() throws XMPPException, SaslException,
			SmackException, IOException, NoSuchAlgorithmException,
			KeyManagementException {
		if (this.connection != null) {
			try {
				this.connection.disconnect();
			} catch (Exception ignore) {
				// ignore
			}
		}
		
		ProxyInfo pi;
		switch (this.proxytype) {
			case HTTP:
				pi = ProxyInfo.forHttpProxy(this.proxyhost, this.proxyport, this.proxyuser, this.proxypass);
				break;
			case SOCKS4:
				pi = ProxyInfo.forSocks4Proxy(this.proxyhost, this.proxyport, this.proxyuser, this.proxypass);
				break;
			case SOCKS5:
				pi = ProxyInfo.forSocks5Proxy(this.proxyhost, this.proxyport, this.proxyuser, this.proxypass);
				break;
			default:
				pi = ProxyInfo.forNoProxy();
				break;
		}
		
		String serviceName = desc.getServiceName();
		final ConnectionConfiguration cfg;
		if (serviceName == null) {
			cfg = new ConnectionConfiguration(
					this.hostnameOverride, this.port, pi);
		} else if (this.hostnameOverride == null) {
		    // uses DNS lookup, to get the actual hostname for this service: 
			cfg = new ConnectionConfiguration(serviceName, pi);
		} else {
			cfg = new ConnectionConfiguration(
					this.hostnameOverride, this.port,
					serviceName, pi);
		}
		// Currently, we handle reconnects ourself.
		// Maybe we should change it in the future, but currently I'm
		// not sure what Smack's reconnect feature really does.
		cfg.setReconnectionAllowed(false);
		
		cfg.setDebuggerEnabled(true);

		if (acceptAllCerts) {
			// TODO Smack 4.1 provides TLSUtil.acceptAllCertificates, replace
			// the code here with the code provided by Smack
			SSLContext context = SSLContext.getInstance("TLS");
			// Install an "accept all" trust manager
			X509TrustManager tm = new X509TrustManager() {
				@Override
				public void checkClientTrusted(X509Certificate[] arg0,
						String arg1) throws CertificateException {
					// Nothing to do here
				}
				@Override
				public void checkServerTrusted(X509Certificate[] arg0,
						String arg1) throws CertificateException {
					// Nothing to do here
				}
				@Override
				public X509Certificate[] getAcceptedIssuers() {
					return new X509Certificate[0];
				}
			};
			context.init(null, new TrustManager[] { tm }, new SecureRandom());
			cfg.setCustomSSLContext(context);
			cfg.setHostnameVerifier(new HostnameVerifier() {
				@Override
				public boolean verify(String arg0, SSLSession arg1) {
					return true;
				}
			});
		} else {
			// TODO This hostname verifier is the default in Smack 4.1 when
			// smack-java7 is used, remove the code once Smack 4.1 is used
			cfg.setHostnameVerifier(new HostnameVerifier() {
				@Override
				public boolean verify(String hostname, SSLSession session) {
					HostnameChecker checker = HostnameChecker
							.getInstance(HostnameChecker.TYPE_TLS);

					boolean validCertificate = false, validPrincipal = false;
					try {
						Certificate[] peerCertificates = session
								.getPeerCertificates();

						if (peerCertificates.length > 0
								&& peerCertificates[0] instanceof X509Certificate) {
							X509Certificate peerCertificate = (X509Certificate) peerCertificates[0];

							try {
								checker.match(hostname, peerCertificate);
								// Certificate matches hostname
								validCertificate = true;
							} catch (CertificateException ex) {
								// Certificate does not match hostname
							}
						} else {
							// Peer does not have any certificates or they
							// aren't X.509
						}
					} catch (SSLPeerUnverifiedException ex) {
						// Not using certificates for peers, try verifying the
						// principal
						try {
							Principal peerPrincipal = session
									.getPeerPrincipal();
							if (peerPrincipal instanceof KerberosPrincipal) {
								validPrincipal = HostnameChecker.match(
										hostname,
										(KerberosPrincipal) peerPrincipal);
							} else {
								// Can't verify principal, not Kerberos
							}
						} catch (SSLPeerUnverifiedException ex2) {
							// Can't verify principal, no principal
						}
					}

					return validCertificate || validPrincipal;
				}
			});
		}

        LOGGER.info("Trying to connect to XMPP on "
                + "/" + cfg.getServiceName()
                + (cfg.isCompressionEnabled() ? " using compression" : "")
                + (pi.getProxyType() != ProxyInfo.ProxyType.NONE ? " via proxy " + pi.getProxyType() + " "
                        + pi.getProxyAddress() + ":" + pi.getProxyPort() : "")
                );

        boolean retryWithLegacySSL = false;
        Exception originalException = null;
		try {
			this.connection = new XMPPTCPConnection(cfg);
			this.connection.connect();
			if (!this.connection.isConnected()) {
				retryWithLegacySSL = true;
			}
		} catch (XMPPException e) {
			retryWithLegacySSL = true;
			originalException = e;
		} catch (SmackException.NoResponseException e) {
			retryWithLegacySSL = true;
			originalException = e;
		} catch (SmackException e) {
            LOGGER.warning(ExceptionHelper.dump(e));
        } catch (IOException e) {
            LOGGER.warning(ExceptionHelper.dump(e));
        }

        if (retryWithLegacySSL) {
			retryConnectionWithLegacySSL(cfg, originalException);
		}

		if (this.connection.isConnected()) {
			this.connection.login(this.desc.getUserName(), this.passwd,
				this.resource != null ? this.resource : "Jenkins");
			
			setupSubscriptionMode();
			createVCardIfNeeded();
			
			installServerTypeHacks();
			
			listenForPrivateChats();
		}
		
		return this.connection.isAuthenticated();
	}

	private void installServerTypeHacks() {
		if (this.connection.getServiceName().contains("hipchat")) {
			// JENKINS-25222: HipChat connections time out after 150 seconds (http://help.hipchat.com/knowledgebase/articles/64377-xmpp-jabber-support-details)
			addConnectionKeepAlivePings(60);
		} else {
			// JENKINS-25676: other servers also seem to require pings, but 5 minute intervals should be enough in this case
			addConnectionKeepAlivePings(5 * 60);
		}
	}

	private void addConnectionKeepAlivePings(int keepAlivePeriodInSeconds) {
		if (this.scheduler == null)  {
			this.scheduler = Executors.newScheduledThreadPool(1);
		}
		
		if (keepAliveCommand != null) {
			return;
		}
		
		keepAliveCommand = new Runnable() {
			
			@Override
			public void run() {
				// prevent long waits for lock
		        try {
					if (!tryLock(5, TimeUnit.SECONDS)) {
					    return;
					}
					
					try {
						connection.sendPacket(new Ping());
					} catch (NotConnectedException e) {
						// connection died, so lets scheduled task die, too
						throw new RuntimeException(e);
					} finally {
						unlock();
					}
					
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}
		};
		scheduler.scheduleAtFixedRate(keepAliveCommand, keepAlivePeriodInSeconds, keepAlivePeriodInSeconds, TimeUnit.SECONDS);
	}

	/**
	 * Transparently retries the connection attempt with legacy SSL if original attempt fails.
	 * @param originalException the exception of the original attempt (may be null)
	 * 
	 * See JENKINS-6863
	 */
	private void retryConnectionWithLegacySSL(
			final ConnectionConfiguration cfg, @Nullable Exception originalException)
			throws XMPPException, SmackException {
		try {
			LOGGER.info("Retrying connection with legacy SSL");
			cfg.setSocketFactory(SSLSocketFactory.getDefault());
			this.connection = new XMPPTCPConnection(cfg);
			this.connection.connect();
		} catch (XMPPException e) {
			if (originalException != null) {
				// use the original connection exception as legacy SSL should only
				// be a fallback
			    LOGGER.warning("Retrying with legacy SSL failed: " + e.getMessage());
				throw new SmackException("Exception of original (without legacy SSL) connection attempt", originalException);
			} else {
				throw new SmackException(e);
			}
		} catch (SmackException e) {
            LOGGER.warning(ExceptionHelper.dump(e));
        } catch (IOException e) {
            LOGGER.warning(ExceptionHelper.dump(e));
        }
    }

	/**
	 * Sets the chosen subscription mode on our connection.
	 */
	private void setupSubscriptionMode() {
		this.roster = this.connection.getRoster();
		SubscriptionMode mode = SubscriptionMode.valueOf(this.desc.getSubscriptionMode());
		switch (mode) {
			case accept_all : LOGGER.info("Accepting all subscription requests");
				break;
			case reject_all : LOGGER.info("Rejecting all subscription requests");
				break;
			case manual : LOGGER.info("Subscription requests must be handled manually");
				break;
		}
		this.roster.setSubscriptionMode(mode);
	}

	/** Sets the Jenkins vCard avatar for this account, if not done so already. */
	private void createVCardIfNeeded() {
	    try {
    	    if (!vCardExists()) {
    	        createVCard();
    	    }
		} catch (XMPPException e) {
			LOGGER.warning(ExceptionHelper.dump(e));
		} catch (SmackException.NotConnectedException e) {
            LOGGER.warning(ExceptionHelper.dump(e));
        } catch (SmackException.NoResponseException e) {
            LOGGER.warning(ExceptionHelper.dump(e));
        }
    }
	
	// Unfortunately the Smack API doesn't specify what concretely happens, if a vCard doesn't exist, yet.
	// It could be just an empty vCard or an XMPPException thrown.
	private boolean vCardExists() throws XMPPException {
	    try {
            VCard vcard = new VCard();
            vcard.load(this.connection);
            
            // Best effort check to see if the vcard already exists.
            if (Util.fixEmpty(vcard.getNickName()) != null) {
                return true;
            }
            return false;
        } catch (XMPPException e) {
            if (e instanceof  XMPPException.XMPPErrorException){
                XMPPException.XMPPErrorException ex =(XMPPException.XMPPErrorException) e;
                // See http://xmpp.org/extensions/xep-0054.html#sect-id304495
                if (ex.getXMPPError() != null && Condition.item_not_found.toString().equals(ex.getXMPPError().getCondition())) {
                    return false;
                }
            }
            
            // there was probably a 'real' problem
            throw e;
        } catch (SmackException.NotConnectedException | SmackException.NoResponseException e) {
            LOGGER.warning(ExceptionHelper.dump(e));
            return false;
        } catch (ClassCastException e) {
        	// This seems to be a VCard parsing exception in Smack 4.0.x
        	// See e.g. http://stackoverflow.com/questions/26752285/android-asmack-vcard-classcastexception-while-calling-vcard-loadconn
        	LOGGER.warning(ExceptionHelper.dump(e));
        	// Assume vcard exists, just couldn't be parsed by smack
        	return true;
        }
    }

	/**
	 * Constructs a vCard for Mr Jenkins.
	 */
	private void createVCard() throws XMPPException, SmackException.NotConnectedException, SmackException.NoResponseException {

		VCard vCard = new VCard();
		vCard.setFirstName("Mr.");
		vCard.setLastName("Jenkins");
		vCard.setNickName(this.nick);
		setAvatarImage(vCard);
		vCard.save(this.connection);
	}
	
	private void setAvatarImage(VCard vCard) {
        InputStream input = null;
        ByteArrayOutputStream output = null;
        try {
            input = JabberIMConnection.class.getResourceAsStream("headshot.png");
            output = new ByteArrayOutputStream();
            IOUtils.copy(input, output);
            byte[] avatar = output.toByteArray();
            vCard.setAvatar(avatar, "image/png");
        } catch (IOException e) {
            LOGGER.warning(ExceptionHelper.dump(e));
        } finally {
            IOUtils.closeQuietly(input);
            IOUtils.closeQuietly(output);
        }
    }

    /**
	 * Listens on the connection for private chat requests.
	 */
	private void listenForPrivateChats() {
		//PacketFilter filter = new AndFilter(new MessageTypeFilter(Message.Type.chat),
		//		new ToContainsFilter(this.desc.getUserName()));
		// Actually, this should be the full user name (including '@server')
		// but since via this connection only message to me should be delivered (right?)
		// this doesn't matter anyway.


        // TODO: ToContainsFilter which was in Smack 4.0.0!?
        PacketFilter filter = new MessageTypeFilter(Message.Type.chat);

		PacketListener listener = new PrivateChatListener();
		this.connection.addPacketListener(listener, filter);
	}

	private MultiUserChat getOrCreateGroupChat(GroupChatIMMessageTarget chat) throws IMException {
		WeakReference<MultiUserChat> ref = groupChatCache.get(chat.getName());
		MultiUserChat groupChat = null;
		if (ref != null) {
			groupChat = ref.get();
		}
		
		if (groupChat == null) {
			groupChat = new MultiUserChat(this.connection, chat.getName());
			try {
				groupChat.join(this.groupChatNick, chat.getPassword());
			} catch (XMPPException e) {
				LOGGER.warning("Cannot join group chat '" + chat + "'. Exception:\n" + ExceptionHelper.dump(e));
				throw new IMException(e);
			} catch (SmackException e) {
                LOGGER.warning("Cannot join group chat '" + chat + "'. Exception:\n" + ExceptionHelper.dump(e));
                throw new IMException(e);
            }

			// get rid of old messages:
			while (groupChat.pollMessage() != null) {
			}

			this.bots.add(new Bot(new JabberMultiUserChat(groupChat, this, !chat.isNotificationOnly()),
					this.groupChatNick, this.desc.getHost(),
					this.botCommandPrefix, this.authentication));

			groupChatCache.put(chat.getName(), new WeakReference<MultiUserChat>(groupChat));
		}
		return groupChat;
	}
	
	private Chat getOrCreatePrivateChat(String chatPartner, Message msg) {
		// use possibly existing chat
		WeakReference<Chat> wr = chatCache.get(chatPartner);
		if (wr != null) {
			Chat c = wr.get();
			if (c != null) {
				return c;
			}
		}
		
		final Chat chat = ChatManager.getInstanceFor(this.connection).createChat(chatPartner, null);
		Bot bot = new Bot(new JabberChat(chat, this), this.groupChatNick,
					this.desc.getHost(), this.botCommandPrefix, this.authentication);
		this.bots.add(bot);
		
		if (msg != null) {
			// replay original message:
			bot.onMessage(new JabberMessage(msg, isAuthorized(msg.getFrom())));
		}
		chatCache.put(chatPartner, new WeakReference<Chat>(chat));
		return chat;
	}

	public void send(final IMMessageTarget target, final String text)
			throws IMException {
		Assert.notNull(target, "Parameter 'target' must not be null.");
		Assert.notNull(text, "Parameter 'text' must not be null.");
		try {
		    // prevent long waits for lock
            if (!tryLock(5, TimeUnit.SECONDS)) {
                return;
            }
            try {
            		if (target instanceof GroupChatIMMessageTarget) {
            			getOrCreateGroupChat((GroupChatIMMessageTarget) target).sendMessage(
            					text);
            		} else {
            			final Chat chat = getOrCreatePrivateChat(target.toString(), null);
            			chat.sendMessage(text);
            		}
            } catch (final XMPPException e) {
            	// server unavailable ? Target-host unknown ? Well. Just skip this
            	// one.
                LOGGER.warning(ExceptionHelper.dump(e));
            	// TODO ? tryReconnect();
            } catch (SmackException.NotConnectedException e) {
                LOGGER.warning(ExceptionHelper.dump(e));
            } finally {
                unlock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            // ignore
        }
	}

	/**
	 * This implementation ignores the new presence if
	 * {@link JabberPublisherDescriptor#isExposePresence()} is false.
	 */
	@Override
	public void setPresence(final IMPresence impresence, String statusMessage)
			throws IMException {
		Assert.notNull(impresence, "Parameter 'impresence' must not be null.");
		if (this.desc.isExposePresence()) {
		    this.impresence = impresence;
		    this.imStatusMessage = statusMessage;
		    sendPresence();
		} else {
		    // Ignore new presence.
		    
		    // Don't re-send presence, either. It would result in disconnecting from
		    // all joined group chats
		}
	}
	
	private void sendPresence() {
	    
	    try {
	        // prevent long waits for lock
            if (!tryLock(5, TimeUnit.SECONDS)) {
                return;
            }
            try {
            	if( !isConnected() ) {
            		return;
            	}
            	Presence presence;
            	switch (this.impresence) {
            	case AVAILABLE:
            		presence = new Presence(Presence.Type.available,
            				this.imStatusMessage, 1, Presence.Mode.available);
            		break;
            	
            	case OCCUPIED:
            	    presence = new Presence(Presence.Type.available,
            	            this.imStatusMessage, 1, Presence.Mode.away);
            	    break;
            	    
            	case DND:
            	    presence = new Presence(Presence.Type.available,
                            this.imStatusMessage, 1, Presence.Mode.dnd);
                    break;

            	case UNAVAILABLE:
            		presence = new Presence(Presence.Type.unavailable);
            		break;

            	default:
            		throw new IllegalStateException("Don't know how to handle "
            				+ impresence);
            	}
            	
            	presence.addExtension(new Nick(this.nick));
            	this.connection.sendPacket(presence);
            } catch (SmackException.NotConnectedException e) {
                LOGGER.warning(ExceptionHelper.dump(e));
            } finally {
                unlock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            // ignore
        }
	}
	
	@Override
    public boolean isConnected() {
	    lock();
		try {
			return this.connection != null && this.connection.isAuthenticated();
		} finally {
		    unlock();
		}
	}
	
	public boolean isAuthorized(String xmppAddress) {
		String bareAddress = StringUtils.parseBareAddress(xmppAddress);
		
		// is this a (private) message send from a user in a chat I'm part of?
		boolean authorized = this.groupChatCache.containsKey(bareAddress);
		
		if (!authorized) {
    		RosterEntry entry = this.roster.getEntry(bareAddress);
            authorized = entry != null
            	&& (entry.getType() == ItemType.both
            	    || entry.getType() == ItemType.from);
		}
        
        return authorized;
	}
	
	private final Map<IMConnectionListener, ConnectionListener> listeners = 
		new ConcurrentHashMap<IMConnectionListener, ConnectionListener>();
	
	@Override
	public void addConnectionListener(final IMConnectionListener listener) {
		lock();
		try {
			ConnectionListener l = new AbstractConnectionListener() {
				@Override
				public void connectionClosedOnError(Exception e) {
					listener.connectionBroken(e);
				}
			};
			listeners.put(listener, l);
			this.connection.addConnectionListener(l);
		} finally {
			unlock();
		}
	}

	@Override
	public void removeConnectionListener(IMConnectionListener listener) {
		lock();
		try {
			ConnectionListener l = this.listeners.remove(listener);
			if (l != null) {
				this.connection.removeConnectionListener(l);
			} else {
				LOGGER.warning("Connection listener " + listener + " not found.");
			}
		} finally {
			unlock();
		}
	}

	/**
	 * Listens for private chats.
	 */
	private final class PrivateChatListener implements PacketListener {

		public void processPacket(Packet packet) {
			if (packet instanceof Message) {
				Message m = (Message)packet;

				for (PacketExtension ext : m.getExtensions()) {
					if (ext instanceof DelayInformation) {
						// ignore delayed messages
						return;
					}
				}
                
				if (m.getBody() != null) {
					LOGGER.fine("Message from " + m.getFrom() + " : " + m.getBody());
					
					final String chatPartner = m.getFrom();
					getOrCreatePrivateChat(chatPartner, m);
				}
			}
		}
	};
}
