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
