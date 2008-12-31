/*
 * Created on Apr 22, 2007
 */
package hudson.plugins.jabber.im.transport.bot;

import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.plugins.jabber.tools.MessageHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import org.apache.commons.lang.StringUtils;

import org.jivesoftware.smack.GroupChat;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.Message;

/**
 * Job/project status command for the jabber bot
 * @author Pascal Bleser
 */
public class StatusCommand implements BotCommand {
	
	private static final String HELP = " [<job>] - show the status of a specific or all jobs";

	public void executeCommand(GroupChat groupChat, Message message,
			String sender, String[] args) throws XMPPException {
		Collection<AbstractProject<?, ?>> projects = new ArrayList<AbstractProject<?, ?>>(0);
		
		if (args.length >= 2) {
			String jobName = StringUtils.join(Arrays.copyOfRange(args, 1, args.length), " ");
			jobName = jobName.replaceAll("\"", "");
			
			AbstractProject<?, ?> project = Hudson.getInstance()
					.getItemByFullName(jobName, AbstractProject.class);
			if (project != null) {
				projects.add(project);
            } else {
            	groupChat.sendMessage(new StringBuffer(sender).append(": unknown job ")
            			.append(jobName).toString());
            }
		} else if (args.length == 1) {
			for (AbstractProject<?, ?> project : Hudson.getInstance().getAllItems(AbstractProject.class)) {
				// add only top level project
				// sub project are accessible by there name but not shown for visibility
				if (Hudson.getInstance().equals(project.getParent())) {
					projects.add(project);
				}
			}
		}
		
		if (! projects.isEmpty()) {
			StringBuffer msg = new StringBuffer();
			if (projects.size() > 1) {
				msg.append("Status of all projects:\n");
			}
			boolean first = true;
			for (AbstractProject<?,?> project : projects) {
				if (! first) {
					msg.append("\n");
				} else {
					first = false;
				}
				
				msg.append(project.getName());
				if (project.isDisabled()) {
					msg.append("(disabled) ");
				} else if (project.isInQueue()) {
					msg.append("(in queue) ");
				} else if (project.isBuilding()) {
					msg.append("(BUILDING) ");
				}
				msg.append(": ");
				
				AbstractBuild<?,?> lastBuild = project.getLastBuild();
				while ((lastBuild != null) && lastBuild.isBuilding()) {
					lastBuild = lastBuild.getPreviousBuild();
				}
				if (lastBuild != null) {
					msg.append("last build: ").append(lastBuild.getNumber())
						.append(": ").append(lastBuild.getResult()).append(": ")
						.append(MessageHelper.getBuildURL(lastBuild));
				} else {
					msg.append("no finished build yet");
				}
	
			}
			groupChat.sendMessage(msg.toString());
		}
	}

	public String getHelp() {
		return HELP;
	}

}
