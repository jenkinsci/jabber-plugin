package hudson.plugins.jabber.im.transport;

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
class JabberMUCMessageListenerAdapter extends AbstractJabberMessageListenerAdapter implements PacketListener {

    // We may want to use information from the MUC instance in future
    @SuppressWarnings("unused")
    private final MultiUserChat muc;

    public JabberMUCMessageListenerAdapter(IMMessageListener listener,
    		JabberIMConnection connection, MultiUserChat muc) {
        super(listener, connection);
        this.muc = muc;
    }
    
    @Override
    public void processPacket(Packet p) {
        final Message msg = (Message) p;
        processMessage(msg);
    }
}
