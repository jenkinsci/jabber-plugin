package hudson.plugins.jabber.im.transport;

import java.io.Reader;
import java.io.Writer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jivesoftware.smack.ConnectionListener;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.debugger.SmackDebugger;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.util.ObservableReader;
import org.jivesoftware.smack.util.ObservableWriter;
import org.jivesoftware.smack.util.ReaderListener;
import org.jivesoftware.smack.util.StringUtils;
import org.jivesoftware.smack.util.WriterListener;

/**
 * Logs detailed info to the log in level FINE or FINEST.
 *
 * @author kutzi
 */
public class JabberConnectionDebugger implements SmackDebugger {
    
    private static final Logger LOGGER = Logger.getLogger(JabberConnectionDebugger.class.getName());
    
    private XMPPConnection connection;
    private Writer writer;
    private Reader reader;
    private ReaderListener readerListener;
    private WriterListener writerListener;

    private PacketListener listener;

    private ConnectionListener connListener;

    public JabberConnectionDebugger(XMPPConnection connection, Writer writer, Reader reader) {
        this.connection = connection;
        this.writer = writer;
        this.reader = reader;
        init();
    }
    
    private void init() {
        ObservableReader debugReader = new ObservableReader(this.reader);
        this.readerListener = new ReaderListener() {
            public void read(String str) {
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.fine("RECV: " + str);
                }
            }
        };
        debugReader.addReaderListener(this.readerListener);

        ObservableWriter debugWriter = new ObservableWriter(this.writer);
        this.writerListener = new WriterListener() {
            public void write(String str) {
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.fine("SENT: " + str);
                }
            }
        };
        debugWriter.addWriterListener(this.writerListener);

        this.reader = debugReader;
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
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.fine("Connection closed");
                }
            }

            public void connectionClosedOnError(Exception e) {
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.log(Level.FINE,
                            "Connection closed due to an exception", e);
                }
            }

            public void reconnectionFailed(Exception e) {
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.log(Level.FINE,
                            "Reconnection failed due to an exception", e);
                }
            }

            public void reconnectionSuccessful() {
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.log(Level.FINE, "Reconnection successful");
                }
            }

            public void reconnectingIn(int seconds) {
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.log(Level.FINE, "Reconnecting in " + seconds
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
        ((ObservableReader)this.reader).removeReaderListener(this.readerListener);
        ObservableReader debugReader = new ObservableReader(newReader);
        debugReader.addReaderListener(this.readerListener);
        this.reader = debugReader;
        return this.reader;
    }

    @Override
    public Writer newConnectionWriter(Writer newWriter) {
        ((ObservableWriter)this.writer).removeWriterListener(this.writerListener);
        ObservableWriter debugWriter = new ObservableWriter(newWriter);
        debugWriter.addWriterListener(this.writerListener);
        this.writer = debugWriter;
        return this.writer;
    }

    @Override
    public void userHasLogged(String user) {
        if (LOGGER.isLoggable(Level.FINE)) {
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
