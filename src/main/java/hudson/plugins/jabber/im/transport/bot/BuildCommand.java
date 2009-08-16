/*
 * Created on Apr 22, 2007
 */
package hudson.plugins.jabber.im.transport.bot;

import hudson.model.AbstractProject;
import hudson.model.Cause;
import hudson.model.Hudson;
import hudson.model.Queue;
import hudson.plugins.jabber.im.transport.JabberChat;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.Message;

/**
 * Build command for the Jabber bot.
 * @author Pascal Bleser
 */
public class BuildCommand implements BotCommand {
	
	private static final Pattern NUMERIC_EXTRACTION_REGEX = Pattern.compile("^(\\d+)");
	private static final String SYNTAX = " <job> [now|<delay[s|m|h]>]";
	private static final String HELP = SYNTAX + " - schedule a job build, with standard, custom or no quiet period";
	
	private final String jabberId;
	
	public BuildCommand(final String jabberId) {
		this.jabberId = jabberId;
	}

	private boolean scheduleBuild(AbstractProject<?, ?> project, int delaySeconds, String sender) {
		Cause cause = new Cause.RemoteCause(this.jabberId, "on request of '" + sender + "'");
        return project.scheduleBuild(delaySeconds, cause);
	}
	
	public void executeCommand(final JabberChat groupChat, final Message message, String sender,
			final String[] args) throws XMPPException {
		if (args.length >= 2) {
			String jobName = args[1];
			jobName = jobName.replaceAll("\"", "");
			
    		AbstractProject<?, ?> project = Hudson.getInstance().getItemByFullName(jobName, AbstractProject.class);
			if (project != null) {
    				if (project.isInQueue()) {
    					Queue.Item queueItem = project.getQueueItem();
						groupChat.sendMessage(sender + ": job " + jobName + " is already in the build queue (" + queueItem.getWhy() + ")");
        			} else if (project.isDisabled()) {
            					groupChat.sendMessage(sender + ": job " + jobName + " is disabled");
    				} else {
        					//project.scheduleBuild();
        					if ((args.length == 2) || (args.length == 3 && "now".equalsIgnoreCase(args[2]))) {
        						if (scheduleBuild(project, 1, sender)) {
                					groupChat.sendMessage(sender + ": job " + jobName + " build scheduled now");
  	     						} else {
	            					groupChat.sendMessage(sender + ": job " + jobName + " scheduling failed or already in build queue");
        						}
        					} else if (args.length >= 3) {
	            				final String delay = args[2].trim();
	            				int factor = 1;
	            				if (delay.endsWith("m") || delay.endsWith("min")) {
	            					factor = 60;
	            				} else if (delay.endsWith("h")) {
	            					factor = 3600;
	            				} else {
	            					char c = delay.charAt(delay.length() - 1);
	            					if (! (c == 's' || Character.isDigit(c))) {
	            						giveSyntax(groupChat, sender, args[0]);
	            						return;
	            					}
	            				}
	            				Matcher matcher = NUMERIC_EXTRACTION_REGEX.matcher(delay);
	            				if (matcher.find()) {
	            					int value = Integer.parseInt(matcher.group());
	                				if (scheduleBuild(project, value * factor, sender)) {
	    	                			groupChat.sendMessage(sender + ": job " + jobName + " build scheduled with a quiet period of " +
	    	                					(value * factor) + " seconds");
	                				} else {
	                					groupChat.sendMessage(sender + ": job " + jobName + " already scheduled in build queue");
	                				}
	            				}
        				
        					} else {
	            				if (scheduleBuild(project, project.getQuietPeriod(), sender)) {
		                			groupChat.sendMessage(sender + ": job " + jobName + " build scheduled (quiet period: " +
		                					project.getQuietPeriod() + " seconds)");
	            				} else {
	            					groupChat.sendMessage(sender + ": job " + jobName + " already scheduled in build queue");
	            				}
        					}
        				}
            		} else {
            			giveSyntax(groupChat, sender, args[0]);
            		}
		} else {
			groupChat.sendMessage(sender + ": Error, syntax is: '" + args[0] +  SYNTAX + "'");
		}
	}
	
	private void giveSyntax(JabberChat chat, String sender, String cmd) throws XMPPException {
		chat.sendMessage(sender + ": syntax is: '" + cmd +  SYNTAX + "'");
	}

	public String getHelp() {
		return HELP;
	}

}
