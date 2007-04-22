package hudson.plugins.jabber.im;

import hudson.Launcher;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.plugins.jabber.NotificationStrategy;
import hudson.plugins.jabber.tools.Assert;
import hudson.tasks.Publisher;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

/**
 * The actual Publisher that sends notification-Messages out to the clients.
 * @author Uwe Schaefer
 *
 */
public abstract class IMPublisher extends Publisher
{

    private static final IMMessageTargetConverter CONVERTER = new DefaultIMMessageTargetConverter();
    private final List<IMMessageTarget> targets = new LinkedList<IMMessageTarget>();

    protected IMPublisher(final String targetsAsString) throws IMMessageTargetConversionException
    {
        Assert.isNotNull(targetsAsString, "Parameter 'targetsAsString' must not be null.");
        final String[] split = targetsAsString.split("\\s");
        final IMMessageTargetConverter conv = getIMMessageTargetConverter();
        for (final String fragment : split)
        {
            IMMessageTarget createIMMessageTarget;
            createIMMessageTarget = conv.fromString(fragment);
            if (createIMMessageTarget != null)
            {
                this.targets.add(createIMMessageTarget);
            }
        }
    }

    protected abstract IMConnection getIMConnection() throws IMException;

    protected IMMessageTargetConverter getIMMessageTargetConverter()
    {
        return IMPublisher.CONVERTER;
    }

    protected NotificationStrategy getNotificationStrategy() // TODO make this configurable
    {
        return NotificationStrategy.STATECHANGE_ONLY;
        // return NotificationStrategy.ALL;
    }

    private List<IMMessageTarget> getNotificationTargets()
    {
        return this.targets;
    }

    public final String getTargets()
    {
        final StringBuffer sb = new StringBuffer();
        for (final IMMessageTarget t : this.targets)
        {
            sb.append(t.toString());
            sb.append(" ");
        }
        return sb.toString().trim();
    }

    public boolean perform(final Build build, final Launcher arg1, final BuildListener arg2)
            throws InterruptedException, IOException
    {
        Assert.isNotNull(build, "Parameter 'build' must not be null.");
        Assert.isNotNull(arg2, "Parameter 'arg2' must not be null.");
        if (getNotificationStrategy().notificationWanted(build))
        {
            final String msg = "Project " + build.getProject().getName() + " build (#" + build.getNumber() + "): "
                    + build.getResult(); // TODO add URL here.

            for (final IMMessageTarget target : getNotificationTargets())
            {
                try
                {
                    arg2.getLogger().append("Sending notification to: " + target.toString() + "\n");
                    getIMConnection().send(target, msg);
                }
                catch (final Throwable e)
                {
                    arg2.getLogger().append("There was an Error sending notification to: " + target.toString() + "\n");
                }
            }
        }
        return false;
    }
}
