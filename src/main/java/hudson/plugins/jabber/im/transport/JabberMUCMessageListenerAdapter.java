package hudson.plugins.jabber.im.transport;

import hudson.plugins.im.IMMessage;
import hudson.plugins.im.IMMessageListener;

import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smackx.muc.MultiUserChat;

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
        final Message msg = (Message) p;

        // don't react to old messages
        if (msg.getExtension("x", "jabber:x:delay") != null) {
            return; // simply bail out here, it's an old message
        }

        // Messages from users in the same MUC are automatically authorized.
        // Getting the JID for a other user in a chatroom doesn't seem to that
        // easy ...
        IMMessage imMessage = new IMMessage(msg.getFrom(), msg.getTo(),
                msg.getBody(), true);

        listener.onMessage(imMessage);
    }
}
