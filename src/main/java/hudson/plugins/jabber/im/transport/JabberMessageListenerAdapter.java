package hudson.plugins.jabber.im.transport;

import hudson.plugins.im.IMMessageListener;

import org.jivesoftware.smack.chat.Chat;
import org.jivesoftware.smack.chat.ChatMessageListener;
import org.jivesoftware.smack.packet.Message;

/**
 * Wraps an {@link IMMessageListener} in a Smack {@link PacketListener}.
 * 
 * @author kutzi
 */
class JabberMessageListenerAdapter extends AbstractJabberMessageListenerAdapter implements ChatMessageListener {

    public JabberMessageListenerAdapter(IMMessageListener listener,
    		JabberIMConnection connection, Chat chat) {
        super(listener, connection);
    }

    @Override
    public void processMessage(Chat chat, Message msg) {
        processMessage(msg);
    }
}
