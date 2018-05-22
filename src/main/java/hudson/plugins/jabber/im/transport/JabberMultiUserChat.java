/**
 * Copyright (c) 2007-2018 the original author or authors
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
import org.jivesoftware.smackx.muc.MultiUserChat;
import org.jivesoftware.smackx.muc.Occupant;
import org.jxmpp.jid.EntityFullJid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.util.XmppStringUtils;

/**
 * Handle for a multi-user chat (aka. conference room) in XMPP/Jabber.
 * 
 * @author kutzi
 */
public class JabberMultiUserChat implements IMChat {

	private final MultiUserChat chat;
	private final JabberIMConnection connection;
	private final boolean commandsAccepted;

	public JabberMultiUserChat(MultiUserChat chat, JabberIMConnection connection, boolean commandsAccepted) {
		this.chat = chat;
		this.connection = connection;
		this.commandsAccepted = commandsAccepted;
	}

	public void sendMessage(String msg) throws IMException {
		try {
			this.chat.sendMessage(msg);
		} catch (SmackException.NotConnectedException | InterruptedException e) {
			throw new IMException(e);
		}
	}

	/**
	 * Returns the 'resource' part of the sender id which is the nickname of the sender in this room.
	 */
	@Override
	public String getNickName(String sender) {
		// Jabber has the chosen MUC nickname in the resource part of the sender id
		String resource = XmppStringUtils.parseResource(sender);
		if (resource != null) {
			return resource;
		}
		return sender;
	}

	@Override
	public String getIMId(String senderId) {
		EntityFullJid senderJid = JidCreate.entityFullFromOrThrowUnchecked(senderId);
		Occupant occ = this.chat.getOccupant(senderJid);
		if (occ != null) {
			return occ.getJid().toString();
		}
		return null;
	}

	public void addMessageListener(IMMessageListener listener) {
		this.chat.addMessageListener(new JabberMUCMessageListenerAdapter(listener, this.connection, this.chat));
	}

	public void removeMessageListener(IMMessageListener listener) {
		// doesn't work out-of the box with Smack
		// We would need to access the underlying connection to remove the packetListener
	}

	public boolean isMultiUserChat() {
		return true;
	}

	public boolean isCommandsAccepted() {
		return this.commandsAccepted;
	}
}
