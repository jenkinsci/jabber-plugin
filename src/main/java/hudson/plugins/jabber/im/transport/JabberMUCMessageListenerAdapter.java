package hudson.plugins.jabber.im.transport;

import hudson.plugins.im.IMMessageListener;

import org.jivesoftware.smack.MessageListener;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smackx.muc.MultiUserChat;

/**
 * Wraps an {@link IMMessageListener} in a Smack {@link PacketListener}.
 * 
 * @author kutzi
 */
class JabberMUCMessageListenerAdapter extends AbstractJabberMessageListenerAdapter implements MessageListener {

    // We may want to use information from the MUC instance in future
    @SuppressWarnings("unused")
    private final MultiUserChat muc;

    public JabberMUCMessageListenerAdapter(IMMessageListener listener,
    		JabberIMConnection connection, MultiUserChat muc) {
        super(listener, connection);
        this.muc = muc;
    }
    
    @Override
    public void processMessage(Message msg) {
    	super.processMessage(msg);
    }
}
