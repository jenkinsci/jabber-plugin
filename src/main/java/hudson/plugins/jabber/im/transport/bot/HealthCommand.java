package hudson.plugins.jabber.im.transport.bot;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.HealthReport;
import hudson.plugins.jabber.tools.MessageHelper;

/**
 * Displays the weather resp. health for one or several jobs.
 *
 * @author kutzi
 */
public class HealthCommand extends JobOverviewCommand {

    @Override
    protected CharSequence getMessageForJob(AbstractProject<?, ?> project) {
        StringBuilder msg = new StringBuilder(32);
        msg.append(project.getName());
        if (project.isDisabled()) {
            msg.append("(disabled) ");
        } else if (project.isInQueue()) {
            msg.append("(in queue) ");
        } else if (project.isBuilding()) {
            msg.append("(BUILDING) ");
        }
        msg.append(": ");

        AbstractBuild<?, ?> lastBuild = project.getLastBuild();
        while ((lastBuild != null) && lastBuild.isBuilding()) {
            lastBuild = lastBuild.getPreviousBuild();
        }
        if (lastBuild != null) {
            HealthReport health = project.getBuildHealth();
            msg.append(health.getDescription())
                    .append(" (").append(health.getScore())
                    .append("%): ").append(MessageHelper.getBuildURL(lastBuild));
        } else {
            msg.append("no finished build yet");
        }
        return msg;
    }

    @Override
    protected String getCommandShortName() {
        return "health";
    }
}
