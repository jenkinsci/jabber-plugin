package hudson.plugins.jabber.im.transport.bot;

import org.jivesoftware.smack.GroupChat;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.Message;

/**
 * Give the bot a snack!
 * (this is really more to familiarize myself with working with Hudson/jabber
 * @author R. Tyler Ballance <tyler@slide.com>
 */
public class SnackCommand implements BotCommand {
	
	private static final String HELP = " - om nom nmo";

	public void executeCommand(GroupChat groupChat, Message message, String sender, String[] args) throws XMPPException {
		groupChat.sendMessage(new StringBuffer(sender).append(": thanks a lot! nom nom nom ").toString());
	}

	public String getHelp() {
		return HELP;
	}

}
