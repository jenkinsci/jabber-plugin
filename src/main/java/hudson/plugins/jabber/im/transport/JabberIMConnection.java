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
import org.jivesoftware.smack.Chat;
import org.jivesoftware.smack.GroupChat;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.Presence;

import java.util.HashMap;
import java.util.Map;

/**
 * Smack-specific implementation of IMConnection.
 * @author Uwe Schaefer
 *
 */
class JabberIMConnection implements IMConnection
{
    static private class GroupChatCacheEntry {
		private final GroupChat groupChat;
		private final Bot bot;
		
		public GroupChatCacheEntry(final GroupChat groupChat, final Bot bot) {
			this.groupChat = groupChat;
			this.bot = bot;
		}
		
		public GroupChat getGroupChat() {
			return this.groupChat;
		}
		
		public Bot getBot() {
			return this.bot;
		}
	}
	
	
    private static final String DND_MESSAGE = "I'm busy building your software...";

    private XMPPConnection connection;
    // Synchronize on this to avoid concurrent access to connection.
    private final Object connectionLock = new Object();

    private Map<String, GroupChatCacheEntry> groupChatCache = new HashMap<String, GroupChatCacheEntry>(0);
    private final String passwd;
    private final String botCommandPrefix;
    /**
     * Jabber ID. It can be either 'john.doe@gmail.com' (username+service name) or just
     * 'john.doe' (service name.)
     */
    private final String nick;
    /**
     * Optional server name. If the {@link #nick} is username+service name, this field
     * can be omitted, in which case reverse DNS lookup and other defaulting mechanism is used
     * to determine the server name. 
     */
    private final String hostname;
    private final int port;

    JabberIMConnection(final JabberPublisherDescriptor desc) throws IMException
    {
        Assert.isNotNull(desc, "Parameter 'desc' must not be null.");
        this.hostname = desc.getHostname();
        this.port = desc.getPort();
        this.nick = desc.getHudsonNickname();
        this.passwd = desc.getHudsonPassword();
        this.botCommandPrefix = desc.getCommandPrefix();
        try
        {
            createConnection();
            
            if (desc.getInitialGroupChats() != null) {
            	for (String groupChatName : desc.getInitialGroupChats().trim().split("\\s")) {
            		createGroupChatConnection(groupChatName.trim());
            	}
            }
        }
        catch (final XMPPException dontCare)
        {
            // Server might be temporarily not available.
            dontCare.printStackTrace();
        }
    }

    /**
     * Returns 'gmail.com' portion of the nick name 'john.doe@gmail.com',
     * or null if not found.
     */
    public String getServiceName() {
        int idx = nick.indexOf('@');
        if(idx<0)   return null;
        else        return nick.substring(idx+1);
    }

    /**
     * Returns 'john.doe' portion of the nick name 'john.doe@gmail.com'.
     */
    public String getUserName() {
        int idx = nick.indexOf('@');
        if(idx<0)   return nick;
        else        return nick.substring(0,idx);
    }

    /**
     * 
     */
    public void close()
    {
        synchronized (connectionLock)
        {
            try
            {
                if ((this.connection != null) && this.connection.isConnected())
                {
                    for (GroupChatCacheEntry entry : groupChatCache.values()) {
                        if (entry.getGroupChat().isJoined()) {
                            entry.getGroupChat().leave();
                        }
                    }
                    this.connection.close();
                }
            }
            finally
            {
                this.connection = null;
            }
        }
    }

    private void createConnection() throws XMPPException
    {
        synchronized (connectionLock)
        {
            if ((this.connection == null) || !this.connection.isConnected())
            {
                String serviceName = getServiceName();
                if(serviceName==null)
                {
                    this.connection = new XMPPConnection(this.hostname, this.port);
                }
                else if(hostname==null)
                {
                    this.connection = new XMPPConnection(serviceName);
                }
                else
                {
                    this.connection = new XMPPConnection(this.hostname, this.port, serviceName);
                }
                
                this.connection.login(getUserName(), this.passwd);
            }
        }
    }
    
    private GroupChat createGroupChatConnection(String groupChatName) throws XMPPException {
        synchronized (connectionLock)
        {
            createConnection();
            GroupChatCacheEntry cacheEntry = groupChatCache.get(groupChatName);
            if (cacheEntry == null) {
                GroupChat groupChat = this.connection.createGroupChat(groupChatName);
                groupChat.join(this.nick);

                // get rid of old messages:
                while (groupChat.pollMessage() != null) {
                }

                Bot bot = new Bot(groupChat, this.nick, this.botCommandPrefix);

                cacheEntry = new GroupChatCacheEntry(groupChat, bot);
                groupChatCache.put(groupChatName, cacheEntry);
                groupChat.addMessageListener(bot);
            }
            return cacheEntry.getGroupChat();
        }
    }
    
    public void send(final IMMessageTarget target, final String text) throws IMException
    {
        Assert.isNotNull(target, "Parameter 'target' must not be null.");
        Assert.isNotNull(text, "Parameter 'text' must not be null.");
        try
        {
            synchronized (connectionLock)
            {
                createConnection();
                if (target instanceof GroupChatIMMessageTarget) {
                    createGroupChatConnection(target.toString()).sendMessage(text);
                } else {
                    final Chat chat = this.connection.createChat(target.toString());
                    chat.sendMessage(text);
                }
            }
        }
        catch (final XMPPException dontCare)
        {
            // server unavailable ? Target-host unknown ? Well. Just skip this one.
            dontCare.printStackTrace();
        }
    }
    
    public synchronized void setPresence(final IMPresence impresence) throws IMException
    {
        Assert.isNotNull(impresence, "Parameter 'impresence' must not be null.");
        try
        {
            createConnection();
            Presence presence;
            switch (impresence)
            {
                case AVAILABLE:
                    presence = new Presence(Presence.Type.AVAILABLE, JabberIMConnection.DND_MESSAGE, 1,
                            Presence.Mode.DO_NOT_DISTURB);
                    break;

                case UNAVAILABLE:
                    presence = new Presence(Presence.Type.UNAVAILABLE);
                    break;

                default:
                    throw new IllegalStateException("Don't know how to handle " + impresence);
            }
            synchronized(connectionLock)
            {
                this.connection.sendPacket(presence);
            }
        }
        catch (final XMPPException e)
        {
            throw new IMException(e);
        }
    }

}
