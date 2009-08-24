/*
 * Created on Apr 22, 2007
 */
package hudson.plugins.jabber.im.transport.bot;

import hudson.plugins.jabber.im.IMChat;
import hudson.plugins.jabber.im.IMException;
import hudson.plugins.jabber.im.IMMessage;
import hudson.plugins.jabber.im.IMMessageListener;
import hudson.plugins.jabber.tools.ExceptionHelper;
import hudson.plugins.jabber.tools.MessageHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;

/**
 * Instant messaging bot.
 * 
 * @author Pascal Bleser
 */
public class Bot implements IMMessageListener {

	private static final Logger LOGGER = Logger.getLogger(Bot.class.getName());

	private final BotCommand BUILD_COMMAND;
	private static final BotCommand STATUS_COMMAND = new StatusCommand();
    private static final BotCommand HEALTH_COMMAND = new HealthCommand();
	private static final BotCommand QUEUE_COMMAND = new QueueCommand();
	private static final BotCommand SNACK_COMMAND = new SnackCommand();
	private static final BotCommand TESTRESULTS_COMMAND =  new TestResultCommand();
	private static final BotCommand ABORT_COMMAND = new AbortCommand();
	private static final BotCommand HELP_COMMAND = new BotCommand() {

		public void executeCommand(IMChat groupChat, IMMessage message,
				String sender, String[] args) throws IMException {
			if (HELP_CACHE == null) {
				final StringBuffer msg = new StringBuffer();
				msg.append("Available commands:");
				for (final Entry<String, BotCommand> item : COMMAND_MAP
						.entrySet()) {
					// skip myself
					if ((item.getValue() != this)
							&& (item.getValue().getHelp() != null)) {
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
		COMMAND_MAP.put("status", STATUS_COMMAND);
		COMMAND_MAP.put("s", STATUS_COMMAND);
        COMMAND_MAP.put("health", HEALTH_COMMAND);
        COMMAND_MAP.put("h", HEALTH_COMMAND);
		COMMAND_MAP.put("jobs", STATUS_COMMAND);
		COMMAND_MAP.put("queue", QUEUE_COMMAND);
		COMMAND_MAP.put("q", QUEUE_COMMAND);
		COMMAND_MAP.put("testresult", TESTRESULTS_COMMAND);
		COMMAND_MAP.put("abort", ABORT_COMMAND);
		COMMAND_MAP.put("botsnack", SNACK_COMMAND);
	}

	private final IMChat chat;
	private final String nick;
	private final String jabberServer;
	private final String commandPrefix;

	public Bot(final IMChat chat, final String nick, final String jabberServer,
			final String commandPrefix) {
		this.chat = chat;
		this.nick = nick;
		this.jabberServer = jabberServer;
		this.commandPrefix = commandPrefix;
		this.BUILD_COMMAND  = new BuildCommand(this.nick + "@" + this.jabberServer);
		COMMAND_MAP.put("build", BUILD_COMMAND);
		COMMAND_MAP.put("schedule", BUILD_COMMAND);
		
		chat.addMessageListener(this);
		
		// TODO: add one shutdown hook for all bots
		if (chat.isMultiUserChat()) {
			addShutdownHook();
		}
	}

	private void addShutdownHook() {
		Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
			public void run() {
				try {
					chat.sendMessage("Oops, seems like Hudson is going down now. See ya!");
				} catch (Exception e) {
					// ignore
				}
			}
		}));
	}

	public void onMessage(IMMessage msg) {
        // is it a command for me ? (returns null if not, the payload if so)
        String payload = retrieveMessagePayLoad(msg.getBody());
        if (payload != null) {
            // split words
            String[] args = MessageHelper.extractCommandLine(payload);
            if (args.length > 0) {
                // first word is the command name
                String cmd = args[0];
                String sender = msg.getFrom();
                if (sender != null) {
                    sender = this.chat.getNickName(sender);
                }
                try {
                    if (COMMAND_MAP.containsKey(cmd)) {
                        BotCommand command = COMMAND_MAP.get(cmd);
                        command.executeCommand(
                                this.chat, msg, sender,
                                args);
                        
                    } else {
                        this.chat.sendMessage(sender + " did you mean me? Unknown command '" + cmd
                                + "'\nUse " + this.commandPrefix + "help to get help!");
                    }
                } catch (IMException e) {
                    LOGGER.warning(ExceptionHelper.dump(e));
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

		if (body.startsWith(this.nick)
				&& isNickSeparator(body.substring(this.nick.length(), this.nick
						.length() + 1))) {
			return body.substring(this.nick.length() + 1).trim();
		}

		return null;
	}

}
