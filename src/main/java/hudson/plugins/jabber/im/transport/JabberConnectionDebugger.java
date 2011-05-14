package hudson.plugins.jabber.im.transport;

import hudson.plugins.jabber.im.LoggingFilterReader;
import hudson.plugins.jabber.im.LoggingFilterWriter;

import java.io.Reader;
import java.io.Writer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jivesoftware.smack.Connection;
import org.jivesoftware.smack.ConnectionListener;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.debugger.SmackDebugger;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.util.StringUtils;

/**
 * Logs detailed info to the log in level FINE or FINEST.
 *
 * @author kutzi
 */
public class JabberConnectionDebugger implements SmackDebugger {
    
    private static final Logger LOGGER = Logger.getLogger(JabberConnectionDebugger.class.getName());
    private static final Level MIN_LOG_LEVEL = Level.FINE;
    
    private final Connection connection;
    private Writer writer;
    private Reader reader;

    private PacketListener listener;

    private ConnectionListener connListener;

    public JabberConnectionDebugger(Connection connection, Writer writer, Reader reader) {
        this.connection = connection;
        this.writer = writer;
        this.reader = reader;
        init();
    }
    
    private void init() {
        
        LoggingFilterReader debugReader = new LoggingFilterReader(this.reader,
                LOGGER, MIN_LOG_LEVEL);
        this.reader = debugReader;

        LoggingFilterWriter debugWriter = new LoggingFilterWriter(this.writer,
                LOGGER, MIN_LOG_LEVEL);
        this.writer = debugWriter;

        this.listener = new PacketListener() {
            public void processPacket(Packet packet) {
                if (LOGGER.isLoggable(Level.FINEST)) {
                    LOGGER.finest("RCV PKT: " + packet.toXML());
                }
            }
        };

        this.connListener = new ConnectionListener() {
            public void connectionClosed() {
                if (LOGGER.isLoggable(MIN_LOG_LEVEL)) {
                    LOGGER.fine("Connection closed");
                }
            }

            public void connectionClosedOnError(Exception e) {
                if (LOGGER.isLoggable(MIN_LOG_LEVEL)) {
                    LOGGER.log(MIN_LOG_LEVEL,
                            "Connection closed due to an exception", e);
                }
            }

            public void reconnectionFailed(Exception e) {
                if (LOGGER.isLoggable(MIN_LOG_LEVEL)) {
                    LOGGER.log(MIN_LOG_LEVEL,
                            "Reconnection failed due to an exception", e);
                }
            }

            public void reconnectionSuccessful() {
                if (LOGGER.isLoggable(MIN_LOG_LEVEL)) {
                    LOGGER.log(MIN_LOG_LEVEL, "Reconnection successful");
                }
            }

            public void reconnectingIn(int seconds) {
                if (LOGGER.isLoggable(MIN_LOG_LEVEL)) {
                    LOGGER.log(MIN_LOG_LEVEL, "Reconnecting in " + seconds
                            + " seconds");
                }
            }
        };
    }

    @Override
    public Reader getReader() {
        return this.reader;
    }

    @Override
    public PacketListener getReaderListener() {
        return this.listener;
    }

    @Override
    public Writer getWriter() {
        return this.writer;
    }

    @Override
    public PacketListener getWriterListener() {
        return null;
    }

    @Override
    public Reader newConnectionReader(Reader newReader) {
        LoggingFilterReader debugReader = new LoggingFilterReader(newReader,
                LOGGER, MIN_LOG_LEVEL);
        this.reader = debugReader;
        return this.reader;
    }

    @Override
    public Writer newConnectionWriter(Writer newWriter) {
        LoggingFilterWriter debugWriter = new LoggingFilterWriter(newWriter,
                LOGGER, MIN_LOG_LEVEL); 
        this.writer = debugWriter;
        return this.writer;
    }

    @Override
    public void userHasLogged(String user) {
        if (LOGGER.isLoggable(MIN_LOG_LEVEL)) {
            boolean isAnonymous = "".equals(StringUtils.parseName(user));
            String title = "User logged in (" + this.connection.hashCode() + "): "
                    + ((isAnonymous) ? "" : StringUtils.parseBareAddress(user))
                    + "@" + this.connection.getServiceName() + ":"
                    + this.connection.getPort();
    
            title = title + "/" + StringUtils.parseResource(user);
            LOGGER.fine(title);
        }

        this.connection.addConnectionListener(this.connListener);
    }
}
