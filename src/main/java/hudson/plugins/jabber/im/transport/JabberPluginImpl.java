package hudson.plugins.jabber.im.transport;

import hudson.Plugin;

/**
 * Plugin Entrypoint used to start/stop the plugin.
 * @author Uwe Schaefer
 * @plugin
 */
public class JabberPluginImpl extends Plugin
{

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() throws Exception
    {
        super.start();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stop() throws Exception
    {
        JabberPublisher.DESCRIPTOR.shutdown();
        super.stop();
    }
}
