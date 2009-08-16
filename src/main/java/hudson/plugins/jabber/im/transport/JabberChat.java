package hudson.plugins.jabber.im.transport;

import org.jivesoftware.smack.Chat;
import org.jivesoftware.smack.GroupChat;
import org.jivesoftware.smack.XMPPException;

/**
 * Interface to unify handling of Smack {@link Chat} and {@link GroupChat}
 * which have for some weird reason no super interface of themselves.
 * 
 * @author kutzi
 */
public interface JabberChat {
	public void sendMessage(String msg) throws XMPPException;
	
	public String getNickName(String sender);
	
	public static class MultiUserChat implements JabberChat {
		 private GroupChat chat;

		public MultiUserChat (GroupChat chat) {
			 this.chat = chat;
		 }

		public void sendMessage(String msg) throws XMPPException {
			this.chat.sendMessage(msg);
		}

		public String getNickName(String sender) {
			int slashIndex = sender.indexOf('/');
			if (slashIndex != -1) {
				sender = sender.substring(slashIndex + 1);
			}
			return sender;
		}
	}
	
	public static class SingleChat implements JabberChat {
		 private Chat chat;

		public SingleChat (Chat chat) {
			 this.chat = chat;
		 }

		public void sendMessage(String msg) throws XMPPException {
			this.chat.sendMessage(msg);
		}

		public String getNickName(String sender) {
			String s = sender;
			int index = s.indexOf('/');
			if (index != -1) {
				s = s.substring(0, index);
			}
			
			index = s.indexOf('@');
			if (index != -1) {
				s = s.substring(0, index);
			}
			return s;
		}
	}
}
