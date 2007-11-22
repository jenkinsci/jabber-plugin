/*
 * Created on Apr 22, 2007
 */
package hudson.plugins.jabber.im.transport.bot;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.jivesoftware.smack.GroupChat;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.PacketExtension;
import org.jivesoftware.smackx.packet.DelayInformation;

/**
 * Jabber bot.
 * @author Pascal Bleser
 */
public class Bot implements PacketListener {

	private static final BotCommand BUILD_COMMAND = new BuildCommand("build");
	private static final BotCommand STATUS_COMMAND = new StatusCommand();
	private static final BotCommand QUEUE_COMMAND = new QueueCommand();
	private static final BotCommand HELP_COMMAND = new BotCommand() {

		public void executeCommand(GroupChat groupChat, Message message, String sender, String[] args) throws XMPPException {
			if (HELP_CACHE == null) {
				final StringBuffer msg = new StringBuffer();
				msg.append("Available commands:");
				for (final Entry<String, BotCommand> item : COMMAND_MAP.entrySet()) {
					// skip myself
					if ((item.getValue() != this) && (item.getValue().getHelp() != null)) {
						msg.append("\n");
						msg.append(item.getKey());
						msg.append(item.getValue().getHelp());
					}
				}
				HELP_CACHE = msg.toString();
			}
			groupChat.sendMessage(HELP_CACHE);
		}

		public String getHelp() {
			return null;
		}
		
	};
	
	private static String HELP_CACHE = null;
	private static final Map<String, BotCommand> COMMAND_MAP;
	
	static {
		COMMAND_MAP = new HashMap<String, BotCommand>();
		COMMAND_MAP.put("help", HELP_COMMAND);
		COMMAND_MAP.put("build", BUILD_COMMAND);
		COMMAND_MAP.put("schedule", BUILD_COMMAND);
		COMMAND_MAP.put("status", STATUS_COMMAND);
		COMMAND_MAP.put("s", STATUS_COMMAND);
		COMMAND_MAP.put("jobs", STATUS_COMMAND);
		COMMAND_MAP.put("queue", QUEUE_COMMAND);
		COMMAND_MAP.put("q", QUEUE_COMMAND);
	}
	
	private final GroupChat groupChat;
	private final String nick;
	private final String commandPrefix;
	
	public Bot(final GroupChat groupChat, final String nick, final String commandPrefix) {
		this.groupChat = groupChat;
		this.nick = nick;
		this.commandPrefix = commandPrefix;
	}
	
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
			// is it a command for me ? (returns null if not, the payload if so)
			String payload = retrieveMessagePayLoad(msg.getBody());
			if (payload != null) {
				// split words
				String[] args = payload.split("\\s");
				if (args.length > 0) {
					// first word is the command name
					String cmd = args[0];
					if (COMMAND_MAP.containsKey(cmd)) {
						String sender = msg.getFrom();
						if (sender != null) {
							int slashIndex = sender.indexOf('/');
							if (slashIndex != -1) {
								sender = sender.substring(slashIndex + 1);
							}
						}
						BotCommand command = COMMAND_MAP.get(cmd);
						try {
							command.executeCommand(this.groupChat, msg, sender, args);
						} catch (XMPPException e) {
							// ignore
							e.printStackTrace();
						}
					}
				}
			}
		}
	}
	
	private static boolean isNickSeparator(final String candidate) {
		return ":".equals(candidate) || ",".equals(candidate);
	}
	
	private String retrieveMessagePayLoad(final String body) {
		if (body == null) {
			return null;
		}
		
		if (body.startsWith(this.commandPrefix)) {
			return body.substring(this.commandPrefix.length()).trim();
		}
		
		if (body.startsWith(this.nick) && isNickSeparator(body.substring(this.nick.length(), this.nick.length() + 1))) {
			return body.substring(this.nick.length() + 1).trim();
		}
		
		return null;
	}

}
