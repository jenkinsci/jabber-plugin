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
import hudson.util.IOUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import javax.net.ssl.SSLSocketFactory;

import org.jivesoftware.smack.Chat;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.ConnectionListener;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.Roster;
import org.jivesoftware.smack.Roster.SubscriptionMode;
import org.jivesoftware.smack.RosterEntry;
import org.jivesoftware.smack.SASLAuthentication;
import org.jivesoftware.smack.SmackConfiguration;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.filter.AndFilter;
import org.jivesoftware.smack.filter.MessageTypeFilter;
import org.jivesoftware.smack.filter.PacketFilter;
import org.jivesoftware.smack.filter.ToContainsFilter;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.PacketExtension;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.RosterPacket.ItemType;
import org.jivesoftware.smack.packet.XMPPError.Condition;
import org.jivesoftware.smack.proxy.ProxyInfo;
import org.jivesoftware.smack.proxy.ProxyInfo.ProxyType;
import org.jivesoftware.smack.util.StringUtils;
import org.jivesoftware.smackx.muc.MultiUserChat;
import org.jivesoftware.smackx.packet.DelayInformation;
import org.jivesoftware.smackx.packet.VCard;
import org.springframework.util.Assert;

import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Smack-specific implementation of {@link IMConnection}.
 * 
 * @author kutzi
 * @author Uwe Schaefer (original author)
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
    private boolean enableSASL;

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

	static {
		SmackConfiguration.setPacketReplyTimeout(20000);
		
		System.setProperty("smack.debuggerClass", JabberConnectionDebugger.class.getName());
	}
	
	JabberIMConnection(JabberPublisherDescriptor desc, AuthenticationHolder authentication) throws IMException {
	    super(desc);
		Assert.notNull(desc, "Parameter 'desc' must not be null.");
		this.desc = desc;
		this.authentication = authentication;
		this.hostnameOverride = desc.getHostname();
		this.port = desc.getPort();
		this.nick = JabberUtil.getUserPart(desc.getJabberId());
		this.passwd = desc.getPassword();
        this.enableSASL = desc.isEnableSASL();
		this.proxytype = desc.getProxyType();
		this.proxyhost = desc.getProxyHost();
		this.proxyport = desc.getProxyPort();
		this.proxyuser = desc.getProxyUser();
		this.proxypass = desc.getProxyPass();
		this.groupChatNick = desc.getGroupChatNickname() != null ?
				desc.getGroupChatNickname() : this.nick;
		this.botCommandPrefix = desc.getCommandPrefix();
		this.groupChats = desc.getDefaultTargets();
		this.impresence = desc.isExposePresence() ? IMPresence.AVAILABLE : IMPresence.UNAVAILABLE;
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
								+ (this.connection.isUsingTLS() ? " using TLS" : "")
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

	private boolean createConnection() throws XMPPException {
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

		// try workaround for SASL error in Smack 3.1.0
		// See: JENKINS-6032
		// http://www.igniterealtime.org/community/message/198558
		// and also http://www.igniterealtime.org/community/message/201908#201908
		SASLAuthentication.unregisterSASLMechanism("DIGEST-MD5");
		
		//SASLAuthentication.unregisterSASLMechanism("GSSAPI");

        cfg.setSASLAuthenticationEnabled(this.enableSASL);
        
        LOGGER.info("Trying to connect to XMPP on "
                + cfg.getHost() + ":" + cfg.getPort()
                + "/" + cfg.getServiceName()
                + (cfg.isSASLAuthenticationEnabled() ? " with SASL" : "")
                + (cfg.isCompressionEnabled() ? " using compression" : "")
                + (pi.getProxyType() != ProxyInfo.ProxyType.NONE ? " via proxy " + pi.getProxyType() + " "
                        + pi.getProxyAddress() + ":" + pi.getProxyPort() : "")
                );

        boolean retryWithLegacySSL = false;
        Exception originalException = null;
		try {
			this.connection = new XMPPConnection(cfg);
			this.connection.connect();
			if (!this.connection.isConnected()) {
				retryWithLegacySSL = true;
			}
		} catch (XMPPException e) {
			retryWithLegacySSL = true;
			originalException = e;
		}
		
		if (retryWithLegacySSL) {
			retryConnectionWithLegacySSL(cfg, originalException);
		}

		if (this.connection.isConnected()) {
			this.connection.login(this.desc.getUserName(), this.passwd, "Jenkins");
			
			setupSubscriptionMode();
			createVCardIfNeeded();
			listenForPrivateChats();
		}
		
		return this.connection.isAuthenticated();
	}

	/**
	 * Transparently retries the connection attempt with legacy SSL if original attempt fails.
	 * @param originalException the exception of the original attempt (may be null)
	 * 
	 * See JENKINS-6863
	 */
	private void retryConnectionWithLegacySSL(
			final ConnectionConfiguration cfg, Exception originalException)
			throws XMPPException {
		try {
			LOGGER.info("Retrying connection with legacy SSL");
			cfg.setSocketFactory(SSLSocketFactory.getDefault());
			this.connection = new XMPPConnection(cfg);
			this.connection.connect();
		} catch (XMPPException e) {
			if (originalException != null) {
				// use the original connection exception as legacy SSL should only
				// be a fallback
			    LOGGER.warning("Retrying with legacy SSL failed: " + e.getMessage());
				throw new XMPPException("Exception of original (without legacy SSL) connection attempt", originalException);
			} else {
				throw new XMPPException(e);
			}
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
            // See http://xmpp.org/extensions/xep-0054.html#sect-id304495
            if (e.getXMPPError() != null && Condition.item_not_found.toString().equals(e.getXMPPError().getCondition())) {
                return false;
            }
            
            // there was probably a 'real' problem
            throw new XMPPException(e);
        }
	}

	/**
	 * Constructs a vCard for Mr Jenkins.
	 */
	private void createVCard() throws XMPPException {

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
		PacketFilter filter = new AndFilter(new MessageTypeFilter(Message.Type.chat), 
				new ToContainsFilter(this.desc.getUserName()));
		// Actually, this should be the full user name (including '@server')
		// but since via this connection only message to me should be delivered (right?)
		// this doesn't matter anyway.
		
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
		
		final Chat chat = this.connection.getChatManager().createChat(chatPartner, null);
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
            	this.connection.sendPacket(presence);
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
			ConnectionListener l = new ConnectionListener() {
				@Override
				public void connectionClosedOnError(Exception e) {
					listener.connectionBroken(e);
				}
				
				@Override
				public void connectionClosed() {
				}
				
				@Override
				public void reconnectingIn(int paramInt) {
				}
				@Override
				public void reconnectionFailed(Exception paramException) {
				}
				@Override
				public void reconnectionSuccessful() {
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
