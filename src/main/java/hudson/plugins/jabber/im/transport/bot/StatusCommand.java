/*
 * Created on Apr 22, 2007
 */
package hudson.plugins.jabber.im.transport.bot;

import hudson.model.Build;
import hudson.model.Hudson;
import hudson.model.Project;
import hudson.Util;

import java.util.ArrayList;
import java.util.Collection;

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
		Collection<Project> projects = new ArrayList<Project>(0);
		
		if (args.length >= 2) {
			String jobName = args[1];
			
            Project project = Hudson.getInstance().getItemByFullName(jobName, Project.class);
			if (project != null) {
				projects.add(project);
            } else {
            	groupChat.sendMessage(new StringBuffer(sender).append(": unknown job ")
            			.append(jobName).toString());
            }
		} else if (args.length == 1) {
			for (Project project : Hudson.getInstance().getProjects()) {
				projects.add(project);
			}
		}
		
		if (! projects.isEmpty()) {
			StringBuffer msg = new StringBuffer();
			if (projects.size() > 1) {
				msg.append("Status of all projects:\n");
			}
			boolean first = true;
			for (Project<?,?> project : projects) {
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
				
				Build<?,?> lastBuild = project.getLastBuild();
				while ((lastBuild != null) && lastBuild.isBuilding()) {
					lastBuild = lastBuild.getPreviousBuild();
				}
				if (lastBuild != null) {
                    // Escape spaces and non-ASCII characters in build URL.
                    String lastBuildUrl = Util.encode(lastBuild.getUrl());

                    msg.append("last build: ").append(lastBuild.getNumber())
					.append(": ").append(lastBuild.getResult()).append(": ")
					.append(Hudson.getInstance().getRootUrl()).append(lastBuildUrl);
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
