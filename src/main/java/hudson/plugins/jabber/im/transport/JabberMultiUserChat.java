package hudson.plugins.jabber.im.transport;

import hudson.plugins.im.IMChat;
import hudson.plugins.im.IMException;
import hudson.plugins.im.IMMessageListener;

import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smackx.muc.MultiUserChat;
import org.jivesoftware.smackx.muc.Occupant;

/**
 * Handle for a multi-user chat (aka. conference room) in XMPP/Jabber.
 * 
 * @author kutzi
 */
public class JabberMultiUserChat implements IMChat {
    
    private final MultiUserChat chat;
	private final JabberIMConnection connection;
    private final boolean commandsAccepted;

    public JabberMultiUserChat (MultiUserChat chat, JabberIMConnection connection, boolean commandsAccepted) {
         this.chat = chat;
         this.connection = connection;
         this.commandsAccepted = commandsAccepted;
     }

    public void sendMessage(String msg) throws IMException {
        try {
            this.chat.sendMessage(msg);
        } catch (XMPPException e) {
            throw new IMException(e);
        }
    }

    /**
     * Returns the 'resource' part of the sender id which is the nickname
     * of the sender in this room.
     */
    @Override
    public String getNickName(String sender) {
    	// Jabber has the chosen MUC nickname in the resource part of the sender id
    	String resource = JabberUtil.getResourcePart(sender);
        if (resource != null) {
            return resource;
        }
        return sender;
    }
    
    @Override
    public String getIMId(String senderId) {
        Occupant occ = this.chat.getOccupant(senderId);
        if (occ != null) {
            return occ.getJid();
        }
        return null;
    }

    public void addMessageListener(IMMessageListener listener) {
        this.chat.addMessageListener(
        		new JabberMUCMessageListenerAdapter(listener, this.connection, this.chat));
    }

    public void removeMessageListener(IMMessageListener listener) {
    	// doesn't work out-of the box with Smack
    	// We would need to access the underlying connection to remove the packetListener
	}

	public boolean isMultiUserChat() {
        return true;
    }

    public boolean isCommandsAccepted() {
        return this.commandsAccepted;
    }
}
