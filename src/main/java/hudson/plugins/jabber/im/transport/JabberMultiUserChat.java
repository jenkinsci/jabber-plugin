package hudson.plugins.jabber.im.transport;

import hudson.plugins.jabber.im.IMChat;
import hudson.plugins.jabber.im.IMException;
import hudson.plugins.jabber.im.IMMessageListener;

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
        int slashIndex = sender.indexOf('/');
        if (slashIndex != -1) {
            sender = sender.substring(slashIndex + 1);
        }
        return sender;
    }
    
    public void addMessageListener(IMMessageListener listener) {
        this.chat.addMessageListener(new JabberMessageListenerAdapter(listener));
    }

    public boolean isMultiUserChat() {
        return true;
    }
}
