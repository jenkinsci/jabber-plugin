package hudson.plugins.jabber.im.transport;

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
    
    static final synchronized void setDesc(IMPublisherDescriptor desc) throws IMException {
    	INSTANCE.setDescriptor(desc);
    	INSTANCE.releaseConnection();
    	INSTANCE.currentConnection();
    }

    private JabberIMConnectionProvider() {
    	super();
    }

    public synchronized IMConnection createConnection() throws IMException {
        releaseConnection();

        if (getDescriptor() == null) {
        	return null;
        }
        
        IMConnection imConnection = new JabberIMConnection((JabberPublisherDescriptor)getDescriptor());
        if (imConnection.connect()) {
        	return imConnection;
        }
        return null;
    }
}
