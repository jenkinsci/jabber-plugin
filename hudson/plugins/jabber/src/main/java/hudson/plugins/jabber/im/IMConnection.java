package hudson.plugins.jabber.im;


/**
 * Represents a connection to an IM-Server.
 * @author Uwe Schaefer
 */
public interface IMConnection
{
    /**
     * Closes the connection (includes logout) and releases resources. 
     */
    void close();

    /**
     * Sends a Message-Text to an IMMessageTarget (aka a User ;).
     * @param target the target to send to 
     * @param text the text to be sent
     * @throws IMException
     */
    void send(IMMessageTarget target, String text) throws IMException;

    /**
     * Sets the current connections� presence to a protocol specific adaption of the given presence parameter.
     * @param presence the presence to set
     * @throws IMException encapsulated exception of underlying protocol/client 
     */
    void setPresence(IMPresence presence) throws IMException;

}
