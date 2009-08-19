package hudson.plugins.jabber.im.transport.bot;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.plugins.jabber.im.IMChat;
import hudson.plugins.jabber.im.IMException;
import hudson.plugins.jabber.im.IMMessage;
import hudson.tasks.junit.CaseResult;
import hudson.tasks.test.AbstractTestResultAction;

import java.util.List;

/**
 * Print out the latest test results for a build
 * @author R. Tyler Ballance <tyler@slide.com>
 */
public class TestResultCommand implements BotCommand {
	
	private static final String HELP = " <job> - specify which job's test results you want to see";

	public void executeCommand(IMChat chat, IMMessage message, String sender, String[] args) throws IMException {
		if (args.length >= 2) {
			String jobName = args[1];
			jobName = jobName.replaceAll("\"", "");

			AbstractProject<?, ?> project = Hudson.getInstance().getItemByFullName(jobName, AbstractProject.class);
			if (project == null) {
				// Invalid job name
				chat.sendMessage(new StringBuffer(sender).append(": that doesn't look like a valid job").toString());
				return;
			}
			AbstractBuild<?, ?> build = project.getLastBuild();
			if (build == null) {
				// No builds? lolwut?
				chat.sendMessage(new StringBuffer(sender).append(": it appears this job has never been built").toString());
				return;
			}	
			AbstractTestResultAction<?> tests = build.getTestResultAction();
			if (tests == null) {
				// no test results associated with this job
				chat.sendMessage(new StringBuffer(sender).append(": sorry, looks like the latest build doesn't contain test results").toString());
				return;
			}
			StringBuffer listing = new StringBuffer();
			listing.append(String.format("%s: %s build #%s had %s of %s tests fail\n", sender, jobName, build.getNumber(), tests.getFailCount(), tests.getTotalCount()));
			List<CaseResult> rc = tests.getFailedTests();
			listing.append("\n");
			if (rc.size() > 0) {
				for (int i = 0; i < rc.size(); ++i) 
				listing.append(String.format("%s failed in %ss\n", rc.get(i).getFullName(), rc.get(i).getDuration()));
			}
			chat.sendMessage(listing.toString());
		} 
		else {
			// No job name specified
			chat.sendMessage(new StringBuffer(sender).append(": you need to specify a job name").toString());
			return; 
		}
	}

	public String getHelp() {
		return HELP;
	}

}
