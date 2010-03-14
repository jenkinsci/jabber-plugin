package hudson.plugins.jabber.im.transport;

import hudson.plugins.im.IMMessage;
import hudson.plugins.im.IMMessageListener;

import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.PacketExtension;
import org.jivesoftware.smackx.muc.MultiUserChat;
import org.jivesoftware.smackx.packet.DelayInformation;

/**
 * Wraps an {@link IMMessageListener} in a Smack {@link PacketListener}.
 * 
 * @author kutzi
 */
class JabberMUCMessageListenerAdapter implements PacketListener {

    private final IMMessageListener listener;
	private final JabberIMConnection connection;
	private final MultiUserChat muc;

    public JabberMUCMessageListenerAdapter(IMMessageListener listener,
    		JabberIMConnection connection, MultiUserChat muc) {
        this.listener = listener;
        this.connection = connection;
        this.muc = muc;
    }
    
    @Override
    public void processPacket(Packet p) {
        if (p instanceof Message) {
            // don't react to old messages
            for (PacketExtension pe : p.getExtensions()) {
                if (pe instanceof DelayInformation) {
                    return; // simply bail out here, it's an old message
                }
            }

            final Message msg = (Message) p;

            // Messages from users in the same MUC are automatically authorized.
            // Getting the JID for a other user in a chatroom doesn't seem to that easy ...
            IMMessage imMessage = new IMMessage(msg.getFrom(), msg.getTo(), msg.getBody(),
            		true);
            
            listener.onMessage(imMessage);
        }
    }
}
