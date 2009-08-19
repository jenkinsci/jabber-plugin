package hudson.plugins.jabber.im;


/**
 * Abstraction of a chat.
 *
 * @author kutzi
 */
public interface IMChat {
    
    /**
     * Sends a message to the chat.
     *
     * @throws IMException If the message couldn't be delivered for any reason.
     */
    public void sendMessage(String message) throws IMException;
    
    /**
     * Translates the sender into a nickname which can be used to address the sender.
     */
    public String getNickName(String senderId);
    
    public boolean isMultiUserChat();
    
    
    public void addMessageListener(IMMessageListener listener);
}
