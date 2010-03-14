package hudson.plugins.jabber.im.transport;

import hudson.plugins.im.IMMessage;
import hudson.plugins.im.IMMessageListener;

import org.jivesoftware.smack.Chat;
import org.jivesoftware.smack.MessageListener;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.PacketExtension;
import org.jivesoftware.smackx.packet.DelayInformation;

/**
 * Wraps an {@link IMMessageListener} in a Smack {@link PacketListener}.
 * 
 * @author kutzi
 */
class JabberMessageListenerAdapter implements MessageListener {

    private final IMMessageListener listener;
	private final JabberIMConnection connection;

    public JabberMessageListenerAdapter(IMMessageListener listener,
    		JabberIMConnection connection, Chat chat) {
        this.listener = listener;
        this.connection = connection;
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
        		msg.getTo(), msg.getBody(),
        		this.connection.isAuthorized(msg.getFrom()));
        
        listener.onMessage(imMessage);
	}
}
