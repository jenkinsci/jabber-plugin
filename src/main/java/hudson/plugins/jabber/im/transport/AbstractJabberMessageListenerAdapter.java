package hudson.plugins.jabber.im.transport;

import hudson.plugins.im.IMMessage;
import hudson.plugins.im.IMMessageListener;

import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smackx.delay.packet.DelayInformation;

class AbstractJabberMessageListenerAdapter {

    protected final IMMessageListener listener;
    protected final JabberIMConnection connection;

    public AbstractJabberMessageListenerAdapter(IMMessageListener listener,
    		JabberIMConnection connection) {
        this.listener = listener;
        this.connection = connection;
    }

    protected void processMessage(Message msg) {
        // Don't react to old messages.
    	// Especially useful for chat rooms where all old messages are replayed, when you connect to them
    	for (ExtensionElement pe : msg.getExtensions()) {
            if (pe instanceof DelayInformation) {
                return; // simply bail out here, it's an old message
            }
    	}

        IMMessage imMessage = new IMMessage(msg.getFrom(), msg.getTo(),
                msg.getBody(), this.connection.isAuthorized(msg.getFrom()));

        listener.onMessage(imMessage);
    }
}
