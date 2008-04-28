package hudson.plugins.jabber.im.transport;

import hudson.plugins.jabber.im.IMConnection;
import hudson.plugins.jabber.im.IMException;
import hudson.plugins.jabber.im.IMPresence;
import hudson.plugins.jabber.tools.Assert;

/**
 * @author Uwe Schaefer
 *
 */
final class JabberIMConnectionProvider
{
    private static final JabberIMConnectionProvider INSTANCE = new JabberIMConnectionProvider();

    static final JabberIMConnectionProvider getInstance()
    {
        return JabberIMConnectionProvider.INSTANCE;
    }

    private IMConnection imConnection;
    private JabberPublisherDescriptor descriptor;

    private JabberIMConnectionProvider()
    {
    }

    synchronized IMConnection createConnection(final JabberPublisherDescriptor desc) throws IMException
    {
        Assert.isNotNull(desc, "Parameter 'desc' must not be null.");
        this.descriptor = desc;

        releaseConnection();

        this.imConnection = new JabberIMConnection(desc);
        this.imConnection.setPresence(desc.isExposePresence() ? IMPresence.AVAILABLE : IMPresence.UNAVAILABLE);
        return this.imConnection;
    }

    /**
     * @throws IMException on any underlying communication Exception
     */
    synchronized IMConnection currentConnection() throws IMException
    {
        return this.imConnection != null ? this.imConnection : createConnection(this.descriptor);
    }

    /**
     * releases (and thus closes) the current connection
     */
    synchronized void releaseConnection()
    {
        if (this.imConnection != null)
        {
            this.imConnection.close();
            this.imConnection = null;
        }
    }
}
