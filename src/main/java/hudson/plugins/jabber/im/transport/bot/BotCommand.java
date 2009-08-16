/*
 * Created on Apr 22, 2007
 */
package hudson.plugins.jabber.im.transport.bot;

import hudson.plugins.jabber.im.transport.JabberChat;

import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.Message;

/**
 * Command pattern contract for Jabber bot commands.
 * 
 * @author Pascal Bleser
 * @see Bot
 */
public interface BotCommand {
	
	/**
	 * Execute a command.
	 * 
	 * @param chat the {@link JabberChat} object, may be used to send reply messages
	 * @param message the original {@link Message}
	 * @param sender the room nickname of the command sender
	 * @param args arguments passed to the command, where <code>args[0]</code> is the command name itself
	 * @throws XMPPException
	 */
	public void executeCommand(final JabberChat chat, final Message message, String sender, final String[] args) throws XMPPException;
	
	/**
	 * Return the command usage text.
	 * @return the command usage text
	 */
	public String getHelp();
}
