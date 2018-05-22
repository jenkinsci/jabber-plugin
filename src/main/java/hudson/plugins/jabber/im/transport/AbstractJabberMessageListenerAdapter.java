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

import hudson.plugins.im.IMMessage;
import hudson.plugins.im.IMMessageListener;
import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smackx.delay.packet.DelayInformation;

class AbstractJabberMessageListenerAdapter {

	protected final IMMessageListener listener;
	protected final JabberIMConnection connection;

	public AbstractJabberMessageListenerAdapter(IMMessageListener listener, JabberIMConnection connection) {
		this.listener = listener;
		this.connection = connection;
	}

	protected void processMessage(Message msg) {
		// Don't react to old messages.
		// Especially useful for chat rooms where all old messages are replayed, when you connect to them
		for (ExtensionElement pe : msg.getExtensions()) {
			if (pe instanceof DelayInformation) {
				return; // simply bail out here, it's an old message
			}
		}

		IMMessage imMessage = new IMMessage(msg.getFrom().toString(), msg.getTo().toString(), msg.getBody(),
				this.connection.isAuthorized(msg.getFrom().asBareJid()));

		listener.onMessage(imMessage);
	}
}
