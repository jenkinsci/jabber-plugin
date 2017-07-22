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
package hudson.plugins.jabber.im.transport;

import java.util.logging.Level;
import java.util.logging.Logger;

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
final class JabberIMConnectionProvider extends IMConnectionProvider {
	private static final IMConnectionProvider INSTANCE = new JabberIMConnectionProvider();

	private static final Logger LOGGER = Logger.getLogger(JabberIMConnectionProvider.class.getName());

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

		LOGGER.info("Creating XMPP JabberIMConnection");
		IMConnection imConnection;
		try {
			imConnection = new JabberIMConnection((JabberPublisherDescriptor) getDescriptor(),
					getAuthenticationHolder());
		} catch (RuntimeException e) {
			LOGGER.log(Level.WARNING, "exception", e);
			throw e;
		}
		if (imConnection.connect()) {
			return imConnection;
		}
		throw new IMException("Connection failed");
	}
}
