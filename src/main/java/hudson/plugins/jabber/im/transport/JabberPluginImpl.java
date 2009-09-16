package hudson.plugins.jabber.im.transport;

import hudson.Plugin;
import hudson.plugins.im.IMPlugin;

/**
 * Plugin entry point used to start/stop the plugin.
 *
 * @author Uwe Schaefer
 * @author kutzi
 */
public class JabberPluginImpl extends Plugin {

	private transient final IMPlugin imPlugin;

	public JabberPluginImpl() {
		this.imPlugin = new IMPlugin(JabberIMConnectionProvider.getInstance());
	}
	
    /**
     * {@inheritDoc}
     */
    @Override
    public void start() throws Exception {
        super.start();
        this.imPlugin.start();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stop() throws Exception {
    	this.imPlugin.stop();
        super.stop();
    }
}
