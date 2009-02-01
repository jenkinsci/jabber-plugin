package hudson.plugins.jabber.im.transport.bot;

import org.jivesoftware.smack.GroupChat;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.Message;

import hudson.model.*;

/**
 * Abort a running job
 * @author R. Tyler Ballance <tyler@slide.com>
 */
public class AbortCommand implements BotCommand {
	
	private static final String HELP = " <job> - specify which job to abort";

	public void executeCommand(GroupChat groupChat, Message message, String sender, String[] args) throws XMPPException {
		if (args.length >= 2) {
			String jobName = args[1];
			jobName = jobName.replaceAll("\"", "");

			AbstractProject<?, ?> project = Hudson.getInstance().getItemByFullName(jobName, AbstractProject.class);
			if (project == null) {
				// Invalid job name
				groupChat.sendMessage(new StringBuffer(sender).append(": that doesn't look like a valid job").toString());
				return;
			}
			if ( (project.isInQueue() == false) && (project.isBuilding() == false) ) {
				groupChat.sendMessage(String.format("%s: how do you intend a build that isn't building?", sender));
				return;
			}
			AbstractBuild build = project.getLastBuild();
			if (build == null) {
				// No builds? lolwut?
				groupChat.sendMessage(new StringBuffer(sender).append(": it appears this job has never been built").toString());
				return;
			}	

			Executor ex = build.getExecutor();
			if (ex == null)
				return; // how the hell does this happen o_O

			ex.interrupt();

			groupChat.sendMessage(String.format("%s aborted, I hope you're happy!", jobName));
		} 
		else {
			// No job name specified
			groupChat.sendMessage(new StringBuffer(sender).append(": you need to specify a job name").toString());
			return; 
		}
	}

	public String getHelp() {
		return HELP;
	}

}
