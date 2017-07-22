/**
 * Copyright (c) 2007-2017 the original author or authors
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
 * CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE
 */
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
