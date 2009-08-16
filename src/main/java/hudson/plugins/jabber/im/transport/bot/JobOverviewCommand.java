package hudson.plugins.jabber.im.transport.bot;

import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.model.TopLevelItem;
import hudson.model.View;
import hudson.plugins.jabber.tools.MessageHelper;

import java.util.ArrayList;
import java.util.Collection;

import org.apache.commons.lang.StringUtils;

/**
 * Abstract command which gives an overview about several jobs.
 *
 * @author kutzi
 */
public abstract class JobOverviewCommand extends AbstractTextSendingCommand {
	
	static final String UNKNOWN_JOB_STR = "unknown job";
	static final String UNKNOWN_VIEW_STR = "unknown view";

    protected abstract CharSequence getMessageForJob(AbstractProject<?, ?> job);

    protected abstract String getCommandShortName();
    
    private enum Mode {
    	SINGLE, VIEW, ALL;
    }

    @Override
	protected String getReply(String sender, String[] args) {
        Collection<AbstractProject<?, ?>> projects = new ArrayList<AbstractProject<?, ?>>();

        final Mode mode;
        String view = null;
        try {
            if (args.length >= 2) {
                if ("-v".equals(args[1])) {
                	mode = Mode.VIEW;
                	view = join(args, 2);
                    getProjectsForView(projects, view);
                } else {
                	mode = Mode.SINGLE;
                    String jobName = join(args, 1);
                    jobName = jobName.replaceAll("\"", "");

                    AbstractProject<?, ?> project = Hudson.getInstance().getItemByFullName(jobName, AbstractProject.class);
                    if (project != null) {
                        projects.add(project);
                    } else {
                    	return sender + ": " + UNKNOWN_JOB_STR + " " + jobName;
                    }
                }
            } else if (args.length == 1) {
            	mode = Mode.ALL;
                for (AbstractProject<?, ?> project : Hudson.getInstance().getAllItems(AbstractProject.class)) {
                    // add only top level project
                    // sub project are accessible by their name but are not shown for visibility
                    if (Hudson.getInstance().equals(project.getParent())) {
                        projects.add(project);
                    }
                }
            } else {
            	throw new IllegalArgumentException("'args' must not be empty!");
            }
        } catch (IllegalArgumentException e) {
            return sender + ": error: " + e.getMessage();
        }

        if (!projects.isEmpty()) {
            StringBuilder msg = new StringBuilder();
                
            switch(mode) {
            	case SINGLE : break;
            	case ALL:
            		msg.append(getCommandShortName())
            			.append(" of all projects:\n");
            		break;
            	case VIEW:
            		msg.append(getCommandShortName())
        				.append(" of projects in view " + view + ":\n");
            		break;
            }

            boolean first = true;
            for (AbstractProject<?, ?> project : projects) {
                if (!first) {
                    msg.append("\n");
                } else {
                    first = false;
                }

                msg.append(getMessageForJob(project));
            }
            return msg.toString();
        } else {
            return sender + ": no job found";
        }
	}

	public String getHelp() {
        return " [<job>|-v <view>] - show the "
                + getCommandShortName()
                + " of a specific job, jobs in a view or all jobs";
    }

    private void getProjectsForView(Collection<AbstractProject<?, ?>> toAddTo, String viewName) {
        View view = Hudson.getInstance().getView(viewName);

        if (view != null) {
            Collection<TopLevelItem> items = view.getItems();
            for (TopLevelItem item : items) {
                if (item instanceof AbstractProject<?, ?>) {
                    toAddTo.add((AbstractProject<?, ?>) item);
                }
            }
        } else {
            throw new IllegalArgumentException(UNKNOWN_VIEW_STR + ": " + viewName);
        }
    }
    
    private String join(String[] array, int startIndex) {
    	String joined = StringUtils.join(MessageHelper.copyOfRange(array, startIndex, array.length, String[].class), " ");
        joined = joined.replaceAll("\"", "");
        return joined;
    }
}
