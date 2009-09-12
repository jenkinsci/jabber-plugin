package hudson.plugins.jabber.im.transport;

import hudson.plugins.jabber.im.DummyConnection;
import hudson.plugins.jabber.im.IMConnection;
import hudson.plugins.jabber.im.IMConnectionProvider;
import hudson.plugins.jabber.im.IMException;
import hudson.plugins.jabber.im.IMPublisherDescriptor;

/**
 * @author Uwe Schaefer
 *
 */
final class JabberIMConnectionProvider extends IMConnectionProvider
{
    private static final IMConnectionProvider INSTANCE = new JabberIMConnectionProvider();
    
    static final synchronized IMConnectionProvider getInstance() {
        return INSTANCE;
    }
    
    static final synchronized void setDesc(IMPublisherDescriptor desc) {
    	INSTANCE.setDescriptor(desc);
    }

    private JabberIMConnectionProvider() {
    	super();
    }

    public synchronized IMConnection createConnection() throws IMException {
        releaseConnection();

        // close vulnerability hole during initialization:
        if (getDescriptor() == null) {
        	return new DummyConnection();
        }
        
        IMConnection imConnection = new JabberIMConnection((JabberPublisherDescriptor)getDescriptor());
        imConnection.init();
        return imConnection;
    }
}
