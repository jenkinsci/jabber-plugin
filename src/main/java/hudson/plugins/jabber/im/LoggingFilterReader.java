package hudson.plugins.jabber.im;

import java.io.FilterReader;
import java.io.IOException;
import java.io.Reader;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A {@link FilterReader} which logs all received XMPP messages on the given log level.
 *
 * @author kutzi
 */
public class LoggingFilterReader extends FilterReader {
    
    private final Reader wrapped;
    private final Logger logger;
    private final Level level;

    public LoggingFilterReader(Reader wrapped, Logger logger, Level level) {
        super(wrapped);
        this.wrapped = wrapped;
        this.logger = logger;
        this.level = level;
    }
    
    @Override
    public int read(char[] cbuf, int off, int len) throws IOException {
        int count = this.wrapped.read(cbuf, off, len);
        if (count > 0 && this.logger.isLoggable(this.level)) {
            String str = new String(cbuf, off, count);
            log(str);
        }
        return count;
    }

    private void log(String str) {
        this.logger.log(this.level, "RECV: " + str);
    }
}
