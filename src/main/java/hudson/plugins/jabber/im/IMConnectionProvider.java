package hudson.plugins.jabber.im;

/**
 * Abstract implementation of a provider of {@link IMConnection}s.
 * 
 * @author kutzi
 */
public abstract class IMConnectionProvider {

	protected IMPublisherDescriptor descriptor;
	private IMConnection imConnection;
	
	protected IMConnectionProvider() {
	}
	
	public abstract IMConnection createConnection() throws IMException;

    /**
     * @throws IMException on any underlying communication Exception
     */
    public synchronized IMConnection currentConnection() throws IMException
    {
    	if (this.imConnection == null) {
    		this.imConnection = createConnection();
    	}
    	
        return this.imConnection;
    }

    /**
     * Releases (and thus closes) the current connection.
     */
    public synchronized void releaseConnection() {
        if (this.imConnection != null) {
        	this.imConnection.shutdown();
            this.imConnection = null;
        }
    }

	protected IMPublisherDescriptor getDescriptor() {
		return this.descriptor;
	}

	public void setDescriptor(IMPublisherDescriptor desc) {
		this.descriptor = desc;
	}
}
