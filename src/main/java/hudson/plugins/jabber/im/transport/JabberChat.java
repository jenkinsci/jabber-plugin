package hudson.plugins.jabber.im.transport;

import hudson.plugins.im.IMChat;
import hudson.plugins.im.IMException;
import hudson.plugins.im.IMMessageListener;

import org.jivesoftware.smack.Chat;
import org.jivesoftware.smack.XMPPException;

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
        } catch (XMPPException e) {
            throw new IMException(e);
        }
    }

    @Override
    public String getNickName(String sender) {
    	return JabberUtil.getUserPart(sender);
    }
    
    @Override
    public String getIMId(String senderId) {
        return JabberUtil.getUserPart(senderId) + '@' + JabberUtil.getDomainPart(senderId);
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
