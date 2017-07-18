package hudson.plugins.jabber.im.transport;

import hudson.plugins.im.IMChat;
import hudson.plugins.im.IMException;
import hudson.plugins.im.IMMessageListener;

import org.jivesoftware.smack.chat.Chat;
import org.jivesoftware.smack.SmackException;
import org.jxmpp.util.XmppStringUtils;

/**
 * 1-on-1 Jabber chat.
 * 
 * @author kutzi
 */
public class JabberChat implements IMChat {
    private final Chat chat;
    private final JabberIMConnection connection;

    public JabberChat(Chat chat, JabberIMConnection connection) {
        this.chat = chat;
        this.connection = connection;
    }

    public void sendMessage(String msg) throws IMException {
        try {
            this.chat.sendMessage(msg);
        } catch (SmackException.NotConnectedException e) {
            throw new IMException(e);
        }
    }

    @Override
    public String getNickName(String sender) {
    	return XmppStringUtils.parseLocalpart(sender);
    }
    
    @Override
    public String getIMId(String senderId) {
        return XmppStringUtils.parseLocalpart(senderId) + '@' + XmppStringUtils.parseDomain(senderId);
    }

    public void addMessageListener(IMMessageListener listener) {
        this.chat.addMessageListener(
        		new JabberMessageListenerAdapter(listener, this.connection, this.chat));
    }

    public void removeMessageListener(IMMessageListener listener) {
		// doesn't work out-of the box with Smack
    	// We would need to access the underlying connection to remove the packetListener
	}

	public boolean isMultiUserChat() {
        return false;
    }

    @Override
    public boolean isCommandsAccepted() {
        return true;
    }
}
