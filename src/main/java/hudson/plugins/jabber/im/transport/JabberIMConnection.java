/**
 * Copyright (c) 2007-2018 the original author or authors
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
 * CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE
 */
package hudson.plugins.jabber.im.transport;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
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
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.security.sasl.SaslException;

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
import hudson.util.DaemonThreadFactory;
import hudson.util.NamingThreadFactory;
import org.apache.commons.io.IOUtils;
import org.jivesoftware.smack.AbstractConnectionListener;
import org.jivesoftware.smack.ConnectionListener;
import org.jivesoftware.smack.ReconnectionManager;
import org.jivesoftware.smack.SmackConfiguration;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.StanzaListener;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.chat.Chat;
import org.jivesoftware.smack.chat.ChatManager;
import org.jivesoftware.smack.filter.MessageTypeFilter;
import org.jivesoftware.smack.filter.StanzaFilter;
import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.parsing.ExceptionLoggingCallback;
import org.jivesoftware.smack.proxy.ProxyInfo;
import org.jivesoftware.smack.proxy.ProxyInfo.ProxyType;
import org.jivesoftware.smack.roster.Roster;
import org.jivesoftware.smack.roster.Roster.SubscriptionMode;
import org.jivesoftware.smack.roster.RosterEntry;
import org.jivesoftware.smack.roster.packet.RosterPacket.ItemType;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.jivesoftware.smack.util.TLSUtils;
import org.jivesoftware.smackx.delay.packet.DelayInformation;
import org.jivesoftware.smackx.muc.MultiUserChat;
import org.jivesoftware.smackx.muc.MultiUserChatException.MucNotJoinedException;
import org.jivesoftware.smackx.muc.MultiUserChatManager;
import org.jivesoftware.smackx.nick.packet.Nick;
import org.jivesoftware.smackx.ping.packet.Ping;
import org.jivesoftware.smackx.vcardtemp.VCardManager;
import org.jivesoftware.smackx.vcardtemp.packet.VCard;
import org.jxmpp.jid.BareJid;
import org.jxmpp.jid.EntityBareJid;
import org.jxmpp.jid.EntityJid;
import org.jxmpp.jid.Jid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.jid.parts.Resourcepart;
import org.jxmpp.stringprep.XmppStringprepException;
import org.springframework.util.Assert;

/**
 * Smack-specific implementation of {@link IMConnection}.
 * 
 * @author kutzi
 * @author Uwe Schaefer (original author)
 * @author jenky-hm
 */
class JabberIMConnection extends AbstractIMConnection {

	private static final Logger LOGGER = Logger.getLogger(JabberIMConnection.class.getName());

	private volatile XMPPTCPConnection connection;

	private final Map<String, WeakReference<MultiUserChat>> groupChatCache = new HashMap<String, WeakReference<MultiUserChat>>();
	private final Map<EntityJid, WeakReference<Chat>> chatCache = new HashMap<>();
	private final Set<Bot> bots = new HashSet<Bot>();
	private final String passwd;
	private final String botCommandPrefix;

	/**
	 * Jabber 'nick'. This is just the username-part of the Jabber-ID. I.e. for 'john.doe@gmail.com' it is 'john.doe'.
	 */
	private final String nick;

	/**
	 * The 'resource' part of the Jabber ID. I.e. for 'john.doe@gmail.com/Jenkins' it is 'Jenkins' or for
	 * 'john.doe@gmail.com' it is null.
	 */
	@Nullable
	private final Resourcepart resource;

	/**
	 * The nick name of the Jenkins bot to use in group chats. May be null in which case the nick is used.
	 */
	@Nullable
	private final Resourcepart groupChatNick;

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

	private MultiUserChatManager mucManager;

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
		SmackConfiguration.setDefaultReplyTimeout(20000);

		SmackConfiguration.setDefaultParsingExceptionCallback(new ExceptionLoggingCallback());

		System.setProperty("smack.debuggerClass", JabberConnectionDebugger.class.getName());

		// Currently, we handle reconnects ourself.
		ReconnectionManager.setEnabledPerDefault(false);
	}

	JabberIMConnection(JabberPublisherDescriptor desc, AuthenticationHolder authentication) throws IMException {
		super(desc);
		Assert.notNull(desc, "Parameter 'desc' must not be null.");

		EntityJid myJid;
		try {
			myJid = JidCreate.entityFromUnescaped(desc.getJabberId());
		} catch (XmppStringprepException e) {
			throw new IMException(e);
		}
		this.desc = desc;
		this.authentication = authentication;
		this.hostnameOverride = desc.getHostname();
		this.port = desc.getPort();
		this.nick = desc.getNickname();
		this.resource = myJid.getResourceOrNull();
		this.passwd = desc.getPassword();
		this.proxytype = desc.getProxyType();
		this.proxyhost = desc.getProxyHost();
		this.proxyport = desc.getProxyPort();
		this.proxyuser = desc.getProxyUser();
		this.proxypass = desc.getProxyPass();
		try {
			this.groupChatNick = Resourcepart.from(desc.getNickname());
		} catch (XmppStringprepException e) {
			throw new IMException(e);
		}
		this.botCommandPrefix = desc.getCommandPrefix();
		this.groupChats = desc.getDefaultTargets();
		this.impresence = desc.isExposePresence() ? IMPresence.AVAILABLE : IMPresence.UNAVAILABLE;
		this.acceptAllCerts = desc.isAcceptAllCerts();
	}

	@Override
	public boolean connect() {
		lock();
		try {
			LOGGER.info("Trying to connect XMPP connection");
			if (this.connection != null && this.connection.isConnected()) {
				LOGGER.fine("XMPP connection already established");
				return true;
			}
			LOGGER.fine("creating new XMPP connection");
			boolean connectingSucceeded = createConnection();
			if (connectingSucceeded) {
				initNewConnection();
			} else {
				disconnect();
			}
			return connectingSucceeded;
		} catch (final Exception e) {
			LOGGER.warning(ExceptionHelper.dump(e));
			return false;
		} finally {
			unlock();
		}
	}

	private void disconnect() {
		// clean-up if needed
		if (this.connection != null && this.connection.isConnected()) {
			try {
				this.connection.disconnect();
			} catch (Exception e) {
				LOGGER.info("Exception while disconnecting: " + e.getMessage());
			}
		}
	}

	private void initNewConnection() {
		LOGGER.info("Connected to XMPP on " + this.connection.getHost() + ":" + this.connection.getPort() + "/"
				+ this.connection.getXMPPServiceDomain()
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
				LOGGER.warning("Unable to connect to groupchat '" + groupChat.getName()
						+ "'. Did you append @conference or so to the name?\n" + "Exception: "
						+ ExceptionHelper.dump(e));
			}
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

	private boolean createConnection() throws XMPPException, SaslException, SmackException, IOException,
			NoSuchAlgorithmException, KeyManagementException, InterruptedException {
		if (this.connection != null) {
			try {
				this.connection.disconnect();
			} catch (Exception ignore) {
				LOGGER.info("Caught an exception while disconnecting before reconnect: " + ignore.getMessage());
				// ignore
			}
		}

		ProxyInfo pi = null;
		if (this.proxytype != null) {
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
				throw new AssertionError();
			}
		}

		String serviceName = desc.getServiceName();
		final XMPPTCPConnectionConfiguration.Builder cfg = XMPPTCPConnectionConfiguration.builder();

		if (pi != null) {
			cfg.setProxyInfo(pi);
		}

		if (serviceName == null) {
			cfg.setHost(hostnameOverride).setPort(port);
		} else if (this.hostnameOverride == null) {
			// uses DNS lookup, to get the actual hostname for this service:
			cfg.setXmppDomain(serviceName);
		} else {
			cfg.setHost(hostnameOverride).setPort(port).setXmppDomain(serviceName);
		}

		cfg.enableDefaultDebugger();

		if (acceptAllCerts) {
			TLSUtils.acceptAllCertificates(cfg);
			cfg.setHostnameVerifier(new HostnameVerifier() {
				@Override
				public boolean verify(String arg0, SSLSession arg1) {
					return true;
				}
			});
		}

		XMPPTCPConnectionConfiguration conf = cfg.build();

		final XMPPTCPConnection connection = new XMPPTCPConnection(conf);
		this.mucManager = MultiUserChatManager.getInstanceFor(connection);

		this.connection = connection;
		LOGGER.info("Trying to connect to XMPP on " + "/" + connection.getXMPPServiceDomain()
				+ (conf.isCompressionEnabled() ? " using compression" : "")
				+ (pi != null
						? " via proxy " + pi.getProxyType() + " " + pi.getProxyAddress() + ":" + pi.getProxyPort()
						: ""));

		boolean retryWithLegacySSL = false;
		Exception originalException = null;
		try {
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
					this.resource);

			setupSubscriptionMode();
			createVCardIfNeeded();

			installServerTypeHacks();

			listenForPrivateChats();
		}

		return this.connection.isAuthenticated();
	}

	private void installServerTypeHacks() {
		if (this.connection.getXMPPServiceDomain().toString().contains("hipchat")) {
			// JENKINS-25222: HipChat connections time out after 150 seconds
			// (http://help.hipchat.com/knowledgebase/articles/64377-xmpp-jabber-support-details)
			addConnectionKeepAlivePings(60);
		} else {
			// JENKINS-25676: other servers also seem to require pings, but 5 minute intervals should be enough in this
			// case
			addConnectionKeepAlivePings(5 * 60);
		}
	}

	private void addConnectionKeepAlivePings(int keepAlivePeriodInSeconds) {
		if (this.scheduler == null) {
			this.scheduler = Executors.newSingleThreadScheduledExecutor(
					new NamingThreadFactory(new DaemonThreadFactory(), JabberIMConnection.class.getSimpleName()));
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
						connection.sendStanza(new Ping());
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
		scheduler.scheduleAtFixedRate(keepAliveCommand, keepAlivePeriodInSeconds, keepAlivePeriodInSeconds,
				TimeUnit.SECONDS);
	}

	/**
	 * Transparently retries the connection attempt with legacy SSL if original attempt fails.
	 * 
	 * @param originalException the exception of the original attempt (may be null)
	 * 
	 *            See JENKINS-6863
	 * @throws InterruptedException 
	 */
	private void retryConnectionWithLegacySSL(final XMPPTCPConnectionConfiguration.Builder cfg,
			@Nullable Exception originalException) throws XMPPException, SmackException, InterruptedException {
		try {
			LOGGER.info("Retrying connection with legacy SSL");
			cfg.setSocketFactory(SSLSocketFactory.getDefault());
			this.connection = new XMPPTCPConnection(cfg.build());
			this.connection.connect();
		} catch (XMPPException e) {
			if (originalException != null) {
				// use the original connection exception as legacy SSL should only
				// be a fallback
				LOGGER.warning("Retrying with legacy SSL failed: " + e.getMessage());
				throw new SmackException("Exception of original (without legacy SSL) connection attempt",
						originalException);
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
		this.roster = Roster.getInstanceFor(connection);
		SubscriptionMode mode = SubscriptionMode.valueOf(this.desc.getSubscriptionMode());
		switch (mode) {
		case accept_all:
			LOGGER.info("Accepting all subscription requests");
			break;
		case reject_all:
			LOGGER.info("Rejecting all subscription requests");
			break;
		case manual:
			LOGGER.info("Subscription requests must be handled manually");
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
		} catch (InterruptedException | XMPPException e) {
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
			VCard vcard = VCardManager.getInstanceFor(connection).loadVCard();

			// Best effort check to see if the vcard already exists.
			if (Util.fixEmpty(vcard.getNickName()) != null) {
				return true;
			}
			return false;
		} catch (XMPPException e) {
			if (e instanceof XMPPException.XMPPErrorException) {
				XMPPException.XMPPErrorException ex = (XMPPException.XMPPErrorException) e;
				// See http://xmpp.org/extensions/xep-0054.html#sect-id304495
				if (ex.getXMPPError().getCondition().equals(org.jivesoftware.smack.packet.StanzaError.Condition.item_not_found)) {
					return false;
				}
			}

			// there was probably a 'real' problem
			throw e;
		} catch (SmackException.NotConnectedException | SmackException.NoResponseException e) {
			LOGGER.warning(ExceptionHelper.dump(e));
			return false;
		} catch (InterruptedException e) {
			LOGGER.fine(ExceptionHelper.dump(e));
			return false;
		} catch (ClassCastException e) {
			// This seems to be a VCard parsing exception in Smack 4.0.x
			// See e.g.
			// http://stackoverflow.com/questions/26752285/android-asmack-vcard-classcastexception-while-calling-vcard-loadconn
			LOGGER.warning(ExceptionHelper.dump(e));
			// Assume vcard exists, just couldn't be parsed by smack
			return true;
		}
	}

	/**
	 * Constructs a vCard for Mr Jenkins.
	 * @throws InterruptedException 
	 */
	private void createVCard()
			throws XMPPException, SmackException.NotConnectedException, SmackException.NoResponseException, InterruptedException {

		VCard vCard = new VCard();
		vCard.setFirstName("Mr.");
		vCard.setLastName("Jenkins");
		vCard.setNickName(this.nick.toString());
		setAvatarImage(vCard);
		VCardManager.getInstanceFor(connection).saveVCard(vCard);
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
		// PacketFilter filter = new AndFilter(new MessageTypeFilter(Message.Type.chat),
		// new ToContainsFilter(this.desc.getUserName()));
		// Actually, this should be the full user name (including '@server')
		// but since via this connection only message to me should be delivered (right?)
		// this doesn't matter anyway.

		// TODO: ToContainsFilter which was in Smack 4.0.0!?
		StanzaFilter filter = MessageTypeFilter.CHAT;

		StanzaListener listener = new PrivateChatListener();
		this.connection.addSyncStanzaListener(listener, filter);
	}

	private MultiUserChat getOrCreateGroupChat(GroupChatIMMessageTarget chat) throws IMException {
		EntityBareJid mucJid = JidCreate.entityBareFromUnescapedOrThrowUnchecked(chat.getName());

		WeakReference<MultiUserChat> ref = groupChatCache.get(chat.getName());
		MultiUserChat groupChat = null;
		if (ref != null) {
			groupChat = ref.get();
		}

		if (groupChat == null) {
			groupChat = MultiUserChatManager.getInstanceFor(connection).getMultiUserChat(mucJid);
			try {
				groupChat.join(this.groupChatNick, chat.getPassword());
			} catch (InterruptedException | SmackException | XMPPException e) {
				LOGGER.warning("Cannot join group chat '" + chat + "'. Exception:\n" + ExceptionHelper.dump(e));
				throw new IMException(e);
			}

			// TODO This could be avoided by simply requesting *no* history from the MUC on join.
			// get rid of old messages:
			try {
				while (groupChat.pollMessage() != null) {
				}
			} catch (MucNotJoinedException e) {
				throw new IMException(e);
			}

			this.bots.add(new Bot(new JabberMultiUserChat(groupChat, this, !chat.isNotificationOnly()),
					this.groupChatNick.toString(), this.desc.getHost(), this.botCommandPrefix, this.authentication));

			groupChatCache.put(chat.getName(), new WeakReference<MultiUserChat>(groupChat));
		}
		return groupChat;
	}

	private Chat getOrCreatePrivateChat(Jid chatPartnerJid, Message msg) {
		EntityJid chatPartner = chatPartnerJid.asEntityJidOrThrow();
		// use possibly existing chat
		WeakReference<Chat> wr = chatCache.get(chatPartner);
		if (wr != null) {
			Chat c = wr.get();
			if (c != null) {
				return c;
			}
		}

		final Chat chat = ChatManager.getInstanceFor(this.connection).createChat(chatPartner, null);
		Bot bot = new Bot(new JabberChat(chat, this), this.groupChatNick.toString(), this.desc.getHost(), this.botCommandPrefix,
				this.authentication);
		this.bots.add(bot);

		if (msg != null) {
			// replay original message:
			bot.onMessage(new JabberMessage(msg, isAuthorized(msg.getFrom().asBareJid())));
		}
		chatCache.put(chatPartner, new WeakReference<Chat>(chat));
		return chat;
	}

	@Override
	public void send(final IMMessageTarget target, final String text) throws IMException {
		Assert.notNull(target, "Parameter 'target' must not be null.");
		Assert.notNull(text, "Parameter 'text' must not be null.");
		try {
			// prevent long waits for lock
			if (!tryLock(5, TimeUnit.SECONDS)) {
				return;
			}
			try {
				if (target instanceof GroupChatIMMessageTarget) {
					getOrCreateGroupChat((GroupChatIMMessageTarget) target).sendMessage(text);
				} else {
					Jid targetJid = JidCreate.fromOrThrowUnchecked(target.toString());
					final Chat chat = getOrCreatePrivateChat(targetJid, null);
					chat.sendMessage(text);
				}
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
	 * This implementation ignores the new presence if {@link JabberPublisherDescriptor#isExposePresence()} is false.
	 */
	@Override
	public void setPresence(final IMPresence impresence, String statusMessage) throws IMException {
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
				if (!isConnected()) {
					return;
				}
				Presence presence;
				switch (this.impresence) {
				case AVAILABLE:
					presence = new Presence(Presence.Type.available, this.imStatusMessage, 1, Presence.Mode.available);
					break;

				case OCCUPIED:
					presence = new Presence(Presence.Type.available, this.imStatusMessage, 1, Presence.Mode.away);
					break;

				case DND:
					presence = new Presence(Presence.Type.available, this.imStatusMessage, 1, Presence.Mode.dnd);
					break;

				case UNAVAILABLE:
					presence = new Presence(Presence.Type.unavailable);
					break;

				default:
					throw new IllegalStateException("Don't know how to handle " + impresence);
				}

				presence.addExtension(new Nick(this.nick));
				this.connection.sendStanza(presence);
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

	public boolean isAuthorized(BareJid bareAddress) {
		// is this a (private) message send from a user in a chat I'm part of?
		boolean authorized = this.groupChatCache.containsKey(bareAddress);

		if (!authorized) {
			RosterEntry entry = this.roster.getEntry(bareAddress);
			authorized = entry != null && (entry.getType() == ItemType.both || entry.getType() == ItemType.from);
		}

		return authorized;
	}

	private final Map<IMConnectionListener, ConnectionListener> listeners = new ConcurrentHashMap<IMConnectionListener, ConnectionListener>();

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
	private final class PrivateChatListener implements StanzaListener {

		@Override
		public void processStanza(Stanza packet) {
			if (packet instanceof Message) {
				Message m = (Message) packet;

				for (ExtensionElement ext : m.getExtensions()) {
					if (ext instanceof DelayInformation) {
						// ignore delayed messages
						return;
					}
				}

				if (m.getBody() != null) {
					LOGGER.fine("Message from " + m.getFrom() + " : " + m.getBody());

					final Jid chatPartner = m.getFrom();
					getOrCreatePrivateChat(chatPartner, m);
				}
			}
		}
	};
}
