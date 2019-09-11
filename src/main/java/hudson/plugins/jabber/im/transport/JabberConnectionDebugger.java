/**
 * Copyright (c) 2007-2019 the original author or authors
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

import java.io.Reader;
import java.io.Writer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jivesoftware.smack.ConnectionListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.debugger.SmackDebugger;
import org.jivesoftware.smack.packet.TopLevelStreamElement;
import org.jxmpp.jid.EntityFullJid;

/**
 * Logs detailed info to the log in level FINE or FINEST.
 *
 * @author kutzi
 */
public class JabberConnectionDebugger extends SmackDebugger {

	private static final Logger LOGGER = Logger.getLogger(JabberConnectionDebugger.class.getName());
	private static final Level MIN_LOG_LEVEL = Level.FINE;

	private ConnectionListener connListener;

	public JabberConnectionDebugger(XMPPConnection connection) {
		super(connection);
		this.connListener = new ConnectionListener() {
			@Override
			public void connected(XMPPConnection connection) {
				if (LOGGER.isLoggable(MIN_LOG_LEVEL)) {
					LOGGER.fine("Connection " + connection + " established");
				}
			}

			@Override
			public void authenticated(XMPPConnection connection, boolean resumed) {
				if (LOGGER.isLoggable(MIN_LOG_LEVEL)) {
					LOGGER.fine("Connection " + connection + " authenticated");
				}
			}

			@Override
			public void connectionClosed() {
				if (LOGGER.isLoggable(MIN_LOG_LEVEL)) {
					LOGGER.fine("Connection closed");
				}
			}

			@Override
			public void connectionClosedOnError(Exception e) {
				if (LOGGER.isLoggable(MIN_LOG_LEVEL)) {
					LOGGER.log(MIN_LOG_LEVEL, "Connection closed due to an exception", e);
				}
			}
		};
		connection.addConnectionListener(this.connListener);
	}

	// N.B. we do not use the newConnection(Reader|Writer) hooks, since they are pretty low level. That is, they are
	// invoked for every chunk that is send or received, which usually creates a lot of noise, since an XMPP stream
	// element may consists of multiple chunks. Instead we use the high level on(Incoming|Outgoing)StreamElement hooks.

	@Override
	public Reader newConnectionReader(Reader reader) {
		return reader;
	}

	@Override
	public Writer newConnectionWriter(Writer writer) {
		return writer;
	}

	@Override
	public void userHasLogged(EntityFullJid user) {
		if (LOGGER.isLoggable(MIN_LOG_LEVEL)) {
			boolean isAnonymous = "".equals(user.getLocalpart());
			String title = "User logged in (" + this.connection.hashCode() + "): "
					+ ((isAnonymous) ? "" : user.asBareJid()) + "@" + this.connection.getXMPPServiceDomain()
					+ ":" + this.connection.getPort();

			title = title + "/" + user.getResourcepart();
			LOGGER.fine(title);
		}
	}

	@Override
	public void onIncomingStreamElement(TopLevelStreamElement streamElement) {
		if (LOGGER.isLoggable(MIN_LOG_LEVEL)) {
			LOGGER.log(MIN_LOG_LEVEL, "RECV: " + streamElement.toXML(null));
		}
	}

	@Override
	public void onOutgoingStreamElement(TopLevelStreamElement streamElement) {
		if (LOGGER.isLoggable(MIN_LOG_LEVEL)) {
			LOGGER.log(MIN_LOG_LEVEL, "SENT: " + streamElement.toXML(null));
		}
	}
}
