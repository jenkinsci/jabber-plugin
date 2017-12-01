/**
 * Copyright (c) 2007-2017 the original author or authors
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
 * CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE
 */
package hudson.plugins.jabber.im.steps;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;

import hudson.plugins.jabber.im.transport.JabberIMMessageTargetConverter;
import hudson.plugins.jabber.im.transport.JabberPublisher;

import com.google.common.collect.ImmutableSet;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.im.MatrixJobMultiplier;
import hudson.plugins.im.NotificationStrategy;
import hudson.plugins.im.build_notify.BuildToChatNotifier;
import hudson.plugins.im.build_notify.DefaultBuildToChatNotifier;
import hudson.util.ListBoxModel;

import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * Pipeline step.
 */
public class JabberNotifyStep extends Step {

	private final static char TARGET_SEPARATOR_CHAR = ' ';
	private final static JabberIMMessageTargetConverter CONVERTER = new JabberIMMessageTargetConverter();

	private final String targets;
	private boolean notifySuspects;
	private boolean notifyCulprits;
	private boolean notifyFixers;
	private boolean notifyUpstreamCommitters;
	private String notificationStrategy = NotificationStrategy.ALL.getDisplayName();
	private BuildToChatNotifier buildToChatNotifier = new DefaultBuildToChatNotifier();
	private MatrixJobMultiplier matrixNotifier = MatrixJobMultiplier.ONLY_PARENT;
	private String extraMessage = "";


	@Override
	public StepExecution start(StepContext context) throws Exception {
		return new JabberNotifyStepExecution(this, context);
	}

	@DataBoundConstructor
	public JabberNotifyStep(String targets) {
		this.targets = targets;
	}

	public String getTargets() {
		return targets;
	}

	public boolean isNotifySuspects() {
		return notifySuspects;
	}

	@DataBoundSetter
	public void setNotifySuspects(boolean notifySuspects) {
		this.notifySuspects = notifySuspects;
	}

	public boolean isNotifyCulprits() {
		return notifyCulprits;
	}

	@DataBoundSetter
	public void setNotifyCulprits(boolean notifyCulprits) {
		this.notifyCulprits = notifyCulprits;
	}

	public boolean isNotifyFixers() {
		return notifyFixers;
	}

	@DataBoundSetter
	public void setNotifyFixers(boolean notifyFixers) {
		this.notifyFixers = notifyFixers;
	}

	public boolean isNotifyUpstreamCommitters() {
		return notifyUpstreamCommitters;
	}

	@DataBoundSetter
	public void setNotifyUpstreamCommitters(boolean notifyUpstreamCommitters) {
		this.notifyUpstreamCommitters = notifyUpstreamCommitters;
	}

	public BuildToChatNotifier getBuildToChatNotifier() {
		return buildToChatNotifier;
	}

	@DataBoundSetter
	public void setBuildToChatNotifier(BuildToChatNotifier buildToChatNotifier) {
		this.buildToChatNotifier = buildToChatNotifier;
	}

	public MatrixJobMultiplier getMatrixNotifier() {
		return matrixNotifier;
	}

	@DataBoundSetter
	public void setMatrixNotifier(MatrixJobMultiplier matrixNotifier) {
		this.matrixNotifier = matrixNotifier;
	}

	public String getNotificationStrategy() {
		return notificationStrategy;
	}

	@DataBoundSetter
	public void setNotificationStrategy(String notificationStrategy) {
		this.notificationStrategy = notificationStrategy;
	}

	public String getExtraMessage() {
		return extraMessage;
	}

	@DataBoundSetter
	public void setExtraMessage(String extraMessage) {
		this.extraMessage = extraMessage;
	}

	private static class JabberNotifyStepExecution extends SynchronousNonBlockingStepExecution<Void> {
		private transient final JabberNotifyStep step;

		public JabberNotifyStepExecution(@Nonnull JabberNotifyStep step, @Nonnull StepContext context) {
			super(context);
			this.step = step;
		}

		@Override
		protected Void run() throws Exception {
			List<String> targets = Arrays.asList(StringUtils.split(step.targets, TARGET_SEPARATOR_CHAR));
			JabberPublisher publisher = new JabberPublisher(
					CONVERTER.allFromString(targets),
					step.notificationStrategy,
					false,
					step.notifySuspects,
					step.notifyCulprits,
					step.notifyFixers,
					step.notifyUpstreamCommitters,
					step.buildToChatNotifier,
					step.matrixNotifier
			);
			publisher.setExtraMessage(step.extraMessage);
			publisher.perform(
					getContext().get(Run.class),
					getContext().get(FilePath.class),
					getContext().get(Launcher.class),
					getContext().get(TaskListener.class));

			return null;
		}
	}


	@Extension(optional = true)
	public static final class DescriptorImpl extends StepDescriptor {

		@Override
		public Set<? extends Class<?>> getRequiredContext() {
			return ImmutableSet.of(FilePath.class, Run.class, Launcher.class, TaskListener.class);
		}

		@Override
		public String getFunctionName() {
			return "jabberNotify";
		}

		@Override
		public String getDisplayName() {
			return "Jabber Notification";
		}

		public ListBoxModel doFillNotificationStrategyItems() {
			ListBoxModel items = new ListBoxModel();
			for (NotificationStrategy strategy : NotificationStrategy.values()) {
				items.add(strategy.getDisplayName(), strategy.getDisplayName());
			}
			return items;
		}
	}
}
