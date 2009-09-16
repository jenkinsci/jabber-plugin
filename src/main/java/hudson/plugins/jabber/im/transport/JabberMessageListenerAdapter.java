package hudson.plugins.jabber.im.transport;

import hudson.plugins.im.IMMessage;
import hudson.plugins.im.IMMessageListener;

import java.util.Iterator;

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
class JabberMessageListenerAdapter implements PacketListener {

    private final IMMessageListener listener;

    public JabberMessageListenerAdapter(IMMessageListener listener) {
        this.listener = listener;
    }
    
    @SuppressWarnings("unchecked")
    public void processPacket(Packet p) {
        if (p instanceof Message) {
            // don't react to old messages
            for (Iterator iter = p.getExtensions(); iter.hasNext();) {
                PacketExtension pe = (PacketExtension) iter.next();
                if (pe instanceof DelayInformation) {
                    return; // simply bail out here, it's an old message
                }
            }

            final Message msg = (Message) p;
            IMMessage imMessage = new IMMessage(msg.getFrom(), msg.getTo(), msg.getBody());
            
            listener.onMessage(imMessage);
        }
    }
    
}
