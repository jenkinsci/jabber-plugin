package hudson.plugins.jabber.im.transport;

import hudson.plugins.im.IMConnection;
import hudson.plugins.im.IMConnectionProvider;
import hudson.plugins.im.IMException;
import hudson.plugins.im.IMPublisherDescriptor;

/**
 * Jabber implementation of an {@link IMConnectionProvider}.
 * 
 * @author Uwe Schaefer
 * @author kutzi
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
    }

    private JabberIMConnectionProvider() {
    	super();
    	init();
    }

    @Override
    public synchronized IMConnection createConnection() throws IMException {
        releaseConnection();

        if (getDescriptor() == null) {
        	throw new IMException("Descriptor not set");
        }
        
        IMConnection imConnection = new JabberIMConnection((JabberPublisherDescriptor)getDescriptor(),
        		getAuthenticationHolder());
        if (imConnection.connect()) {
        	return imConnection;
        }
        throw new IMException("Connection failed");
    }
}
