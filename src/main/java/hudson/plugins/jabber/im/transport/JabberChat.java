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

import hudson.plugins.im.IMChat;
import hudson.plugins.im.IMException;
import hudson.plugins.im.IMMessageListener;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.chat.Chat;
import org.jxmpp.util.XmppStringUtils;

/**
 * 1-on-1 Jabber chat.
 * 
 * @author kutzi
 */
public class JabberChat implements IMChat {
	private final Chat chat;
	private final JabberIMConnection connection;

	public JabberChat(Chat chat, JabberIMConnection connection) {
		this.chat = chat;
		this.connection = connection;
	}

	public void sendMessage(String msg) throws IMException {
		try {
			this.chat.sendMessage(msg);
		} catch (SmackException.NotConnectedException | InterruptedException e) {
			throw new IMException(e);
		}
	}

	@Override
	public String getNickName(String sender) {
		return XmppStringUtils.parseLocalpart(sender);
	}

	@Override
	public String getIMId(String senderId) {
		return XmppStringUtils.parseLocalpart(senderId) + '@' + XmppStringUtils.parseDomain(senderId);
	}

	public void addMessageListener(IMMessageListener listener) {
		this.chat.addMessageListener(new JabberMessageListenerAdapter(listener, this.connection, this.chat));
	}

	public void removeMessageListener(IMMessageListener listener) {
		// doesn't work out-of the box with Smack
		// We would need to access the underlying connection to remove the packetListener
	}

	public boolean isMultiUserChat() {
		return false;
	}

	@Override
	public boolean isCommandsAccepted() {
		return true;
	}
}
