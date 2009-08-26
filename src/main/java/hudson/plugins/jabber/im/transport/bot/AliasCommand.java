package hudson.plugins.jabber.im.transport.bot;

import hudson.plugins.jabber.tools.MessageHelper;

public class AliasCommand extends AbstractTextSendingCommand {

	@Override
	protected String getReply(String sender, String[] args) {
		if (args.length < 1) {
			throw new IllegalArgumentException();
		} else if (args.length == 1) {
			// TODO
			return "Defined aliases: none";
		} else if (args.length < 3) {
			// TODO remove existing alias
			return "deleted alias " + args[1];
		} else {
			String alias = args[1];
			String command = MessageHelper.getJoinedName(args, 2);
			// TODO: create alias
			return "created alias: " + alias + "='" + command + "'";
		}
	}

	public String getHelp() {
		return " [<alias> <command>] - defines a new alias or lists the existing ones";
	}

}
