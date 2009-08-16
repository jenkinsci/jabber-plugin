package hudson.plugins.jabber.im.transport.bot;

import hudson.plugins.jabber.im.transport.JabberChat;

import java.util.logging.Logger;

import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.Message;

/**
 * Abstract command for sending a reply back to the sender.
 * 
 * @author kutzi
 */
public abstract class AbstractTextSendingCommand implements BotCommand {
	
	private static final Logger LOGGER = Logger.getLogger(AbstractTextSendingCommand.class.getName());

	/**
	 * {@inheritDoc}
	 */
	public final void executeCommand(JabberChat chat, Message message,
			String sender, String[] args) throws XMPPException {
		String reply;
		try {
			reply = getReply(sender, args);
		} catch (RuntimeException e) {
			LOGGER.warning(e.toString());
			reply = sender + ": Error " + e.toString();
		}
		chat.sendMessage(reply);
	}

	/**
	 * Gets the text reply
	 * 
	 * @param sender the room nickname of the command sender
	 * @param args arguments passed to the command, where <code>args[0]</code> is the command name itself
	 */
	protected abstract String getReply(String sender, String args[]);

}
