/*
 * Created on 06.03.2007
 */
package hudson.plugins.jabber.im.transport;

import hudson.plugins.jabber.im.GroupChatIMMessageTarget;
import hudson.plugins.jabber.im.IMConnection;
import hudson.plugins.jabber.im.IMException;
import hudson.plugins.jabber.im.IMMessageTarget;
import hudson.plugins.jabber.im.IMPresence;
import hudson.plugins.jabber.im.transport.bot.Bot;
import hudson.plugins.jabber.tools.Assert;
import hudson.util.TimeUnit2;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.jivesoftware.smack.Chat;
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
class JabberIMConnection implements IMConnection {
	
	private static final Logger LOGGER = Logger.getLogger(JabberIMConnection.class.getName());
	
	static private class GroupChatCacheEntry {
		private final GroupChat groupChat;

		public GroupChatCacheEntry(final GroupChat groupChat) {
			this.groupChat = groupChat;
		}

		public GroupChat getGroupChat() {
			return this.groupChat;
		}
	}

	private volatile XMPPConnection connection;
	// Synchronize on this to avoid concurrent access to connection.
	private final Object connectionLock = new Object();

	private final Map<String, GroupChatCacheEntry> groupChatCache = new HashMap<String, GroupChatCacheEntry>();
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

	private final Connector connector;
	private final Thread connectorThread;
	
	JabberIMConnection(final JabberPublisherDescriptor desc) throws IMException {
		Assert.isNotNull(desc, "Parameter 'desc' must not be null.");
		this.hostname = desc.getHostname();
		this.port = desc.getPort();
		this.legacySSL = desc.isLegacySSL();
		this.nick = desc.getHudsonNickname();
		this.passwd = desc.getHudsonPassword();
		this.groupChatNick = desc.getGroupChatNickname() != null ? desc
				.getGroupChatNickname() : this.nick;
		this.botCommandPrefix = desc.getCommandPrefix();
		if (desc.getInitialGroupChats() != null) {
			this.groupChats = desc.getInitialGroupChats().trim().split("\\s");
		} else {
			this.groupChats = new String[0];
		}

		connectAll();
		
		connector = new Connector();
		connectorThread = new Thread(connector, "Jabber-ConnectorThread");
		connectorThread.start();
	}

	private boolean connectAll() {
		synchronized (connectionLock) {
			try {
				if (!isConnected()) {
					if (createConnection()) {
						LOGGER.info("Connected to XMPP on " + this.hostname + ":" + this.port);
			
						for (String groupChatName : this.groupChats) {
							try {
								groupChatName = groupChatName.trim();
								createGroupChatConnection(groupChatName);
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
			} catch (final XMPPException dontCare) {
				// Server might be temporarily not available.
				LOGGER.warning(dontCare.toString());
				return false;
			}
		}
	}

	/**
	 * Returns 'gmail.com' portion of the nick name 'john.doe@gmail.com', or
	 * null if not found.
	 */
	public String getServiceName() {
		int idx = nick.indexOf('@');
		if (idx < 0)
			return null;
		else
			return nick.substring(idx + 1);
	}

	/**
	 * Returns 'john.doe' portion of the nick name 'john.doe@gmail.com'.
	 */
	public String getUserName() {
		int idx = nick.indexOf('@');
		if (idx < 0)
			return nick;
		else
			return nick.substring(0, idx);
	}

	/**
     * 
     */
	public void close() {
		this.connectorThread.interrupt();
		synchronized (connectionLock) {
			try {
				if ((this.connection != null) && this.connection.isConnected()) {
					for (GroupChatCacheEntry entry : groupChatCache.values()) {
						if (entry.getGroupChat().isJoined()) {
							entry.getGroupChat().leave();
						}
					}
					this.connection.close();
				}
			} finally {
				this.connection = null;
			}
		}
	}

	private boolean createConnection() throws XMPPException {
		synchronized (connectionLock) {
			String serviceName = getServiceName();
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
				this.connection.login(getUserName(), this.passwd, "Hudson");
				
				String fullUser = getUserName() + "@" + this.hostname;
				
				PacketFilter filter = new AndFilter(new MessageTypeFilter(Message.Type.CHAT), 
						new ToContainsFilter(fullUser) );
				
				PacketListener listener = new IMListener();
				this.connection.addPacketListener(listener, filter);
			}
			
			return this.connection.isAuthenticated();
		}
	}

	private GroupChat createGroupChatConnection(String groupChatName)
			throws XMPPException {
		synchronized (connectionLock) {
			GroupChatCacheEntry cacheEntry = groupChatCache.get(groupChatName);
			if (cacheEntry == null) {
				GroupChat groupChat = this.connection
						.createGroupChat(groupChatName);
				groupChat.join(this.groupChatNick);

				// get rid of old messages:
				while (groupChat.pollMessage() != null) {
				}

				Bot bot = new Bot(new JabberChat.MultiUserChat(groupChat),
						this.groupChatNick,
						this.botCommandPrefix);

				cacheEntry = new GroupChatCacheEntry(groupChat);
				groupChatCache.put(groupChatName, cacheEntry);
				groupChat.addMessageListener(bot);
			}
			return cacheEntry.getGroupChat();
		}
	}

	public void send(final IMMessageTarget target, final String text)
			throws IMException {
		Assert.isNotNull(target, "Parameter 'target' must not be null.");
		Assert.isNotNull(text, "Parameter 'text' must not be null.");
		try {
			synchronized (connectionLock) {
				if (target instanceof GroupChatIMMessageTarget) {
					createGroupChatConnection(target.toString()).sendMessage(
							text);
				} else {
					final Chat chat = this.connection.createChat(target.toString());
					chat.sendMessage(text);
				}
			}
		} catch (final XMPPException dontCare) {
			// server unavailable ? Target-host unknown ? Well. Just skip this
			// one.
			dontCare.printStackTrace();
			this.connector.semaphore.release();
		}
	}

	public void setPresence(final IMPresence impresence)
			throws IMException {
		Assert.isNotNull(impresence, "Parameter 'impresence' must not be null.");
		Presence presence;
		switch (impresence) {
		case AVAILABLE:
			presence = new Presence(Presence.Type.AVAILABLE,
					"", 1, Presence.Mode.AVAILABLE);
			break;

		case UNAVAILABLE:
			presence = new Presence(Presence.Type.UNAVAILABLE);
			break;

		default:
			throw new IllegalStateException("Don't know how to handle "
					+ impresence);
		}
		synchronized (connectionLock) {
			this.connection.sendPacket(presence);
		}
	}
	
	private boolean isConnected() {
		synchronized (connectionLock) {
			return this.connection != null && this.connection.isAuthenticated();
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
					// pretty strange if composing extension BUT also a XHTMLExtension this seems
					// to mean that the message was delivered
					return;
				}
				
				if (m.getBody() != null) {
					System.out.println("Message from " + m.getFrom() + " : " + m.getBody());
					
					final String chatPartner = m.getFrom();
					enterChat(chatPartner, m);
				}
			}
		}
	};
	
	private final Map<String, WeakReference<Chat>> chats = new HashMap<String, WeakReference<Chat>>();
	
	private void enterChat(String chatPartner, Message msg) {
		// use possibly existing chat
		WeakReference<Chat> wr = chats.get(chatPartner);
		if (wr != null && wr.get() != null) {
			return;
		}
		
		final Chat chat = this.connection.createChat(chatPartner);
		Bot bot = new Bot(new JabberChat.SingleChat(chat), this.groupChatNick, this.botCommandPrefix);
		// replay original message:
		bot.processPacket(msg);
		chat.addMessageListener(bot);
		chats.put(chatPartner, new WeakReference<Chat>(chat));
	}
	
	private final class Connector implements Runnable {

		private final Semaphore semaphore = new Semaphore(0);
		
		public void run() {
			try {
				while (true) {
					this.semaphore.acquire();
					this.semaphore.drainPermits();
					
					// wait a little bit in case the XMPP server/network has just a 'hickup'
					TimeUnit.SECONDS.sleep(30);
					
					boolean success = false;
					int timeout = 1;
					while (!success) {
						synchronized (connectionLock) {
							if (!isConnected()) {
								success = connectAll();
							} else {
								success = true;
							}
						}
						
						// make sure to release connectionLock before sleeping!
						if(!success) {
							LOGGER.info("Next connection try in " + timeout + " minutes");
							TimeUnit2.MINUTES.sleep(timeout);
							// exponentially increase timeout
							timeout = timeout * 2;
						}
					}
				}
			} catch (InterruptedException e) {
				LOGGER.info("Connect thread interrupted");
				// just bail out
			}
		}
		
	}
}
