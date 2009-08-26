/*
 * Created on 06.03.2007
 */
package hudson.plugins.jabber.im.transport;

import hudson.plugins.jabber.im.AbstractIMConnection;
import hudson.plugins.jabber.im.GroupChatIMMessageTarget;
import hudson.plugins.jabber.im.IMException;
import hudson.plugins.jabber.im.IMMessageTarget;
import hudson.plugins.jabber.im.IMPresence;
import hudson.plugins.jabber.im.transport.bot.Bot;
import hudson.plugins.jabber.tools.Assert;
import hudson.plugins.jabber.tools.ExceptionHelper;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

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
	private final String passwd;
	private final String botCommandPrefix;
	/**
	 * Jabber ID. It can be either 'john.doe@gmail.com' (username+service name)
	 * or just 'john.doe' (service name.)
	 */
	private final String nick;
	private final String groupChatNick;
	/**
	 * Optional server name. If the {@link #nick} is username+service name, this
	 * field can be omitted, in which case reverse DNS lookup and other
	 * defaulting mechanism is used to determine the server name.
	 */
	private final String hostname;
	private final int port;
	private final boolean legacySSL;

	private final String[] groupChats;
	
	private IMPresence impresence;

	private final String defaultIdSuffix;

    private String imStatusMessage;

    private JabberPublisherDescriptor desc;
	
	JabberIMConnection(final JabberPublisherDescriptor desc) throws IMException {
	    super(desc);
		Assert.isNotNull(desc, "Parameter 'desc' must not be null.");
		this.desc = desc;
		this.hostname = desc.getHostname();
		this.port = desc.getPort();
		this.legacySSL = desc.isLegacySSL();
		this.nick = desc.getJabberId();
		this.passwd = desc.getPassword();
		this.groupChatNick = desc.getGroupChatNickname() != null ? desc
				.getGroupChatNickname() : this.nick;
		this.botCommandPrefix = desc.getCommandPrefix();
		if (desc.getInitialGroupChats() != null) {
			this.groupChats = desc.getInitialGroupChats().trim().split("\\s");
		} else {
			this.groupChats = new String[0];
		}
		this.impresence = desc.isExposePresence() ? IMPresence.AVAILABLE : IMPresence.UNAVAILABLE;
		this.defaultIdSuffix = desc.getDefaultIdSuffix();
	}

	@Override
	protected boolean connect0() {
	    lock();
	    try {
			try {
				if (!isConnected()) {
					if (createConnection()) {
						LOGGER.info("Connected to XMPP on " + this.hostname + ":" + this.port);
			
						updateIMStatus();
						
						for (String groupChatName : this.groupChats) {
							try {
								groupChatName = groupChatName.trim();
								createGroupChatConnection(groupChatName, true);
								LOGGER.info("Joined groupchat " + groupChatName);
							} catch (XMPPException e) {
								// if we got here, the XMPP connection could be established, but probably the groupchat name
								// is invalid
								LOGGER.warning("Unable to connect to groupchat '" + groupChatName + "'. Did you append @conference or so to the name?\n"
										+ "Message: " + e.toString());
							}
						}
					} else {
						return false;
					}
				}
				return true;
			} catch (final Exception dontCare) {
				// Server might be temporarily not available.
				LOGGER.warning(ExceptionHelper.dump(dontCare));
				tryReconnect();
				return false;
			}
		} finally {
		    unlock();
		}
	}

	@Override
    protected void close0() {
	    lock();
	    try {
			try {
				if (isConnected()) {
					for (WeakReference<GroupChat> entry : groupChatCache.values()) {
						GroupChat chat = entry.get();
						if (chat != null && chat.isJoined()) {
							chat.leave();
						}
					}
					this.groupChatCache.clear();
					this.chatCache.clear();
					this.connection.close();
				}
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
					this.hostname, this.port) : new XMPPConnection(
					this.hostname, this.port);
		} else if (hostname == null) {
			this.connection = this.legacySSL ? new SSLXMPPConnection(
					serviceName) : new XMPPConnection(serviceName);
		} else {
			this.connection = this.legacySSL ? new SSLXMPPConnection(
					this.hostname, this.port, serviceName)
					: new XMPPConnection(this.hostname, this.port,
							serviceName);
		}

		if (this.connection.isConnected()) {
			this.connection.login(this.desc.getUserName(), this.passwd, "Hudson");
			
			String fullUser = this.desc.getUserName() + "@" + this.hostname;
			
			PacketFilter filter = new AndFilter(new MessageTypeFilter(Message.Type.CHAT), 
					new ToContainsFilter(fullUser));
			
			PacketListener listener = new IMListener();
			this.connection.addPacketListener(listener, filter);
			this.connection.addConnectionListener(new ConnectionListener() {
				public void connectionClosedOnError(Exception paramException) {
					tryReconnect();
				}
				
				public void connectionClosed() {
					tryReconnect();
				}
			});
		}
		
		return this.connection.isAuthenticated();
	}

	private GroupChat createGroupChatConnection(String groupChatName, boolean forceReconnect)
			throws XMPPException {
		WeakReference<GroupChat> ref = groupChatCache.get(groupChatName);
		GroupChat groupChat = null;
		if (ref != null) {
			groupChat = ref.get();
		}
		boolean create = (groupChat == null) || forceReconnect;
		
		if (forceReconnect && groupChat != null) {
			try {
				groupChatCache.remove(groupChatName);
				groupChat.leave();
			} catch (Exception e) {
				// ignore
			}
		}
		
		if (create) {
			groupChat = this.connection
					.createGroupChat(groupChatName);
			groupChat.join(this.groupChatNick);

			// get rid of old messages:
			while (groupChat.pollMessage() != null) {
			}

			new Bot(new JabberMultiUserChat(groupChat),
					this.groupChatNick, this.hostname,
					this.botCommandPrefix);

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
				this.hostname, this.botCommandPrefix);
		
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
            			createGroupChatConnection(target.toString(), false).sendMessage(
            					text);
            		} else {
            			final Chat chat = getChat(target.toString(), null);
            			chat.sendMessage(text);
            		}
            } catch (final XMPPException dontCare) {
            	// server unavailable ? Target-host unknown ? Well. Just skip this
            	// one.
                LOGGER.warning(ExceptionHelper.dump(dontCare));
            	tryReconnect();
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
    protected boolean isConnected() {
	    lock();
		try {
			return this.connection != null && this.connection.isAuthenticated();
		} finally {
		    unlock();
		}
	}
	
	public String getDefaultIdSuffix() {
		return this.defaultIdSuffix;
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
