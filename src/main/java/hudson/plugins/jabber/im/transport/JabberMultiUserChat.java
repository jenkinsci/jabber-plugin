package hudson.plugins.jabber.im.transport;

import hudson.plugins.im.IMChat;
import hudson.plugins.im.IMException;
import hudson.plugins.im.IMMessageListener;

import org.jivesoftware.smack.GroupChat;
import org.jivesoftware.smack.XMPPException;

public class JabberMultiUserChat implements IMChat {
    
    private GroupChat chat;

    public JabberMultiUserChat (GroupChat chat) {
         this.chat = chat;
     }

    public void sendMessage(String msg) throws IMException {
        try {
            this.chat.sendMessage(msg);
        } catch (XMPPException e) {
            throw new IMException(e);
        }
    }

    public String getNickName(String sender) {
    	// Jabber has the chosen MUC nickname in the resource part of the sender id
    	String resource = JabberUtil.getResourcePart(sender);
        if (resource != null) {
            return resource;
        }
        return sender;
    }
    
    public void addMessageListener(IMMessageListener listener) {
        this.chat.addMessageListener(new JabberMessageListenerAdapter(listener));
    }

    public void removeMessageListener(IMMessageListener listener) {
    	// doesn't work out-of the box with Smack
    	// We would need to access the underlying connection to remove the packetListener
	}

	public boolean isMultiUserChat() {
        return true;
    }
}
