package hudson.plugins.jabber.im.transport;

import hudson.plugins.im.IMMessage;
import hudson.plugins.im.IMMessageListener;

import org.jivesoftware.smack.packet.Message;

class AbstractJabberMessageListenerAdapter {

    protected final IMMessageListener listener;
    protected final JabberIMConnection connection;

    public AbstractJabberMessageListenerAdapter(IMMessageListener listener,
    		JabberIMConnection connection) {
        this.listener = listener;
        this.connection = connection;
    }

    protected void processMessage(Message msg) {
        // don't react to old messages
        if (msg.getExtension("x", "jabber:x:delay") != null) {
            return; // simply bail out here, it's an old message
        }

        IMMessage imMessage = new IMMessage(msg.getFrom(), msg.getTo(),
                msg.getBody(), this.connection.isAuthorized(msg.getFrom()));

        listener.onMessage(imMessage);
    }
}
