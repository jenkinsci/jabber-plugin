package hudson.plugins.jabber.im.transport;

import hudson.plugins.im.IMMessage;
import hudson.plugins.im.IMMessageListener;

import org.jivesoftware.smack.Chat;
import org.jivesoftware.smack.MessageListener;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.PacketExtension;
import org.jivesoftware.smackx.packet.DelayInformation;

/**
 * Wraps an {@link IMMessageListener} in a Smack {@link PacketListener}.
 * 
 * @author kutzi
 */
class JabberMessageListenerAdapter implements MessageListener, PacketListener {

    private final IMMessageListener listener;

    public JabberMessageListenerAdapter(IMMessageListener listener) {
        this.listener = listener;
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
            IMMessage imMessage = new IMMessage(msg.getFrom(), msg.getTo(), msg.getBody());
            
            listener.onMessage(imMessage);
        }
    }

	@Override
	public void processMessage(Chat chat, Message msg) {
		// don't react to old messages
        for (PacketExtension pe : msg.getExtensions()) {
            if (pe instanceof DelayInformation) {
                return; // simply bail out here, it's an old message
            }
        }

        IMMessage imMessage = new IMMessage(msg.getFrom(),
        		msg.getTo(), msg.getBody());
        
        listener.onMessage(imMessage);
	}
    
}
