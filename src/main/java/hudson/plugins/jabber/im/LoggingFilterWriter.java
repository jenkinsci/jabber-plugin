package hudson.plugins.jabber.im;

import java.io.FilterWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A {@link FilterWriter} which logs all sent XMPP messages on the given log level.
 *
 * @author kutzi
 */
public class LoggingFilterWriter extends FilterWriter {
    private final Writer wrappedWriter;
    private final Logger logger;
    private final Level level;

    public LoggingFilterWriter(Writer wrapped, Logger logger, Level level) {
        super(wrapped);
        this.wrappedWriter = wrapped;
        this.logger = logger;
        this.level = level;
    }
    
    private boolean isEnabled() {
        return this.logger.isLoggable(this.level);
    }
    
    @Override
    public void write(char[] cbuf, int off, int len) throws IOException {
        this.wrappedWriter.write(cbuf, off, len);
        if (isEnabled()) {
            String str = new String(cbuf, off, len);
            log(str);
        }
    }

    @Override
    public void write(char[] cbuf) throws IOException {
        this.wrappedWriter.write(cbuf);
        if (isEnabled()) {
            String str = new String(cbuf);
            log(str);
        }
    }

    @Override
    public void write(String str) throws IOException {
        this.wrappedWriter.write(str);
        log(str);
    }

    @Override
    public void write(String str, int off, int len) throws IOException {
        this.wrappedWriter.write(str, off, len);
        if (isEnabled()) {
            str = str.substring(off, off + len);
            log(str);
        }
    }

    private void log(String str) {
        this.logger.log(this.level, "SENT: " + str);
    }
}
