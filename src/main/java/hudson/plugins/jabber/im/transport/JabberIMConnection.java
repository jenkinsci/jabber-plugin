/*
 * Created on 06.03.2007
 */
package hudson.plugins.jabber.im.transport;

import hudson.plugins.im.AbstractIMConnection;
import hudson.plugins.im.GroupChatIMMessageTarget;
import hudson.plugins.im.IMConnectionListener;
import hudson.plugins.im.IMException;
import hudson.plugins.im.IMMessageTarget;
import hudson.plugins.im.IMPresence;
import hudson.plugins.im.bot.Bot;
import hudson.plugins.im.tools.Assert;
import hudson.plugins.im.tools.ExceptionHelper;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.acegisecurity.Authentication;
import org.jivesoftware.smack.Chat;
import org.jivesoftware.smack.ConnectionListener;
import org.jivesoftware.smack.GroupChat;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.SSLXMPPConnection;
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
import org.jivesoftware.smackx.packet.DelayInformation;
import org.jivesoftware.smackx.packet.MessageEvent;
import org.jivesoftware.smackx.packet.XHTMLExtension;

/**
 * Smack-specific implementation of IMConnection.
 * 
 * @author Uwe Schaefer
 * 
 */
class JabberIMConnection extends AbstractIMConnection {
	
	private static final Logger LOGGER = Logger.getLogger(JabberIMConnection.class.getName());
	
	private volatile XMPPConnection connection;

	private final Map<String, WeakReference<GroupChat>> groupChatCache = new HashMap<String, WeakReference<GroupChat>>();
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
	 * The nick name of the Hudson bot to use in group chats.
	 * May be null in which case the nick is used.
	 */
	private final String groupChatNick;
	/**
	 * Server name of the Jabber server.
	 */
	private final String hostname;
	private final int port;
	private final boolean legacySSL;

	private final String[] groupChats;
	
	private IMPresence impresence;

    private String imStatusMessage;

    private final JabberPublisherDescriptor desc;
    private final Authentication authentication;
	
	JabberIMConnection(JabberPublisherDescriptor desc, Authentication authentication) throws IMException {
	    super(desc);
		Assert.isNotNull(desc, "Parameter 'desc' must not be null.");
		this.desc = desc;
		this.authentication = authentication;
		this.hostname = desc.getHost();
		this.port = desc.getPort();
		this.legacySSL = desc.isLegacySSL();
		this.nick = JabberUtil.getUserPart(desc.getJabberId());
		this.passwd = desc.getPassword();
		this.groupChatNick = desc.getGroupChatNickname() != null ?
				desc.getGroupChatNickname() : this.nick;
		this.botCommandPrefix = desc.getCommandPrefix();
		if (desc.getInitialGroupChats() != null) {
			this.groupChats = desc.getInitialGroupChats().trim().split("\\s");
		} else {
			this.groupChats = new String[0];
		}
		this.impresence = desc.isExposePresence() ? IMPresence.AVAILABLE : IMPresence.UNAVAILABLE;
	}

	@Override
	public boolean connect() {
	    lock();
	    try {
			try {
				if (!isConnected()) {
					if (createConnection()) {
						LOGGER.info("Connected to XMPP on " + this.desc.getHost() + ":" + this.port);
			
						// I've read somewhere that status must be set, before one can do anything other
						// Don't know if it's true, but can't hurt, either.
						sendPresence();
						
						groupChatCache.clear();
						for (String groupChatName : this.groupChats) {
							try {
								groupChatName = groupChatName.trim();
								getGroupChat(groupChatName);
								LOGGER.info("Joined groupchat " + groupChatName);
							} catch (IMException e) {
								// if we got here, the XMPP connection could be established, but probably the groupchat name
								// is invalid
								LOGGER.warning("Unable to connect to groupchat '" + groupChatName + "'. Did you append @conference or so to the name?\n"
										+ "Exception: " + ExceptionHelper.dump(e));
							}
						}
					} else {
						// clean-up if needed
						if (this.connection != null) {
							try {
								this.connection.close();
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
				for (WeakReference<GroupChat> entry : groupChatCache.values()) {
					GroupChat chat = entry.get();
					if (chat != null && chat.isJoined()) {
						chat.leave();
					}
				}
				// there seems to be no way to leave a 1-on-1 chat with Smack
				
				this.groupChatCache.clear();
				this.chatCache.clear();
				if (this.connection.isConnected()) {
					this.connection.close();
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
				this.connection.close();
			} catch (Exception ignore) {
				// ignore
			}
		}
		String serviceName = desc.getServiceName();
		if (serviceName == null) {
			this.connection = this.legacySSL ? new SSLXMPPConnection(
					desc.getHostname(), this.port) : new XMPPConnection(
					desc.getHostname(), this.port);
		} else if (desc.getHostname() == null) {
			this.connection = this.legacySSL ? new SSLXMPPConnection(
					serviceName) : new XMPPConnection(serviceName);
		} else {
			this.connection = this.legacySSL ? new SSLXMPPConnection(
					desc.getHostname(), this.port, serviceName)
					: new XMPPConnection(this.hostname, this.port,
							serviceName);
		}

		if (this.connection.isConnected()) {
			this.connection.login(this.desc.getUserName(), this.passwd, "Hudson");
			
			PacketFilter filter = new AndFilter(new MessageTypeFilter(Message.Type.CHAT), 
					new ToContainsFilter(this.desc.getUserName()));
			// Actually, this should be the full user name (including '@server')
			// but since via this connection only message to me should be delivered (right?)
			// this doesn't matter anyway.
			
			PacketListener listener = new IMListener();
			this.connection.addPacketListener(listener, filter);
		}
		
		return this.connection.isAuthenticated();
	}

	private GroupChat getGroupChat(String groupChatName) throws IMException {
		WeakReference<GroupChat> ref = groupChatCache.get(groupChatName);
		GroupChat groupChat = null;
		if (ref != null) {
			groupChat = ref.get();
		}
		boolean create = (groupChat == null);
		
		if (create) {
			groupChat = this.connection.createGroupChat(groupChatName);
			try {
				groupChat.join(this.groupChatNick);
			} catch (XMPPException e) {
				LOGGER.warning("Cannot join group chat '" + groupChatName + "'. Exception:\n" + ExceptionHelper.dump(e));
				throw new IMException(e);
			}

			// get rid of old messages:
			while (groupChat.pollMessage() != null) {
			}

			this.bots.add(new Bot(new JabberMultiUserChat(groupChat),
					this.groupChatNick, this.desc.getHost(),
					this.botCommandPrefix, this.authentication));

			groupChatCache.put(groupChatName, new WeakReference<GroupChat>(groupChat));
		}
		return groupChat;
	}
	
	private Chat getChat(String chatPartner, Message msg) {
		// use possibly existing chat
		WeakReference<Chat> wr = chatCache.get(chatPartner);
		if (wr != null) {
			Chat c = wr.get();
			if (c != null) {
				return c;
			}
		}
		
		final Chat chat = this.connection.createChat(chatPartner);
		Bot bot = new Bot(new JabberChat(chat), this.groupChatNick,
					this.desc.getHost(), this.botCommandPrefix, this.authentication);
		this.bots.add(bot);
		
		if (msg != null) {
			// replay original message:
			bot.onMessage(new JabberMessage(msg));
		}
		chatCache.put(chatPartner, new WeakReference<Chat>(chat));
		return chat;
	}

	public void send(final IMMessageTarget target, final String text)
			throws IMException {
		Assert.isNotNull(target, "Parameter 'target' must not be null.");
		Assert.isNotNull(text, "Parameter 'text' must not be null.");
		try {
		    // prevent long waits for lock
            if (!tryLock(5, TimeUnit.SECONDS)) {
                return;
            }
            try {
            		if (target instanceof GroupChatIMMessageTarget) {
            			getGroupChat(target.toString()).sendMessage(
            					text);
            		} else {
            			final Chat chat = getChat(target.toString(), null);
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

	public void setPresence(final IMPresence impresence, String statusMessage)
			throws IMException {
		Assert.isNotNull(impresence, "Parameter 'impresence' must not be null.");
		if (this.desc.isExposePresence()) {
		    this.impresence = impresence;
		    this.imStatusMessage = statusMessage;
		} else {
		    // ignore set presence
		    this.impresence = IMPresence.UNAVAILABLE;
		    this.imStatusMessage = "";
		}
		sendPresence();
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
            		presence = new Presence(Presence.Type.AVAILABLE,
            				this.imStatusMessage, 1, Presence.Mode.AVAILABLE);
            		break;
            	
            	case OCCUPIED:
            	    presence = new Presence(Presence.Type.AVAILABLE,
            	            this.imStatusMessage, 1, Presence.Mode.AWAY);
            	    break;
            	    
            	case DND:
            	    presence = new Presence(Presence.Type.AVAILABLE,
                            this.imStatusMessage, 1, Presence.Mode.DO_NOT_DISTURB);
                    break;

            	case UNAVAILABLE:
            		presence = new Presence(Presence.Type.UNAVAILABLE);
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

	private final class IMListener implements PacketListener {

		public void processPacket(Packet packet) {
			if (packet instanceof Message) {
				Message m = (Message)packet;

				boolean composing = false;
				boolean xhtmlMessage = false;
				@SuppressWarnings("unchecked")
				Iterator<PacketExtension> extensions = m.getExtensions();
				while (extensions.hasNext()) {
					PacketExtension ext = extensions.next();
					if (ext instanceof DelayInformation) {
						// ignore delayed messages
						return;
					}
					if (ext instanceof MessageEvent) {
						MessageEvent me = (MessageEvent)ext;
						if (me.isComposing()) {
							// ignore messages which are still being composed
							composing = true;
						}
					}
					if (ext instanceof XHTMLExtension) {
						xhtmlMessage = true;
					}
				}
				
				if (composing && !xhtmlMessage) {
					// pretty strange: if composing extension BUT also a XHTMLExtension this seems
					// to mean that the message was delivered
					return;
				}
				
				if (m.getBody() != null) {
					LOGGER.info("Message from " + m.getFrom() + " : " + m.getBody());
					
					final String chatPartner = m.getFrom();
					getChat(chatPartner, m);
				}
			}
		}
	};
}
