package hudson.plugins.jabber.im;

/**
 * DefaultIMMessageTarget basically is a String, that represents an Im-Account to send messages to.
 * @author Pascal Bleser
 */
@Deprecated
public class GroupChatIMMessageTarget extends hudson.plugins.im.GroupChatIMMessageTarget
{
    private static final long serialVersionUID = 1L;

    public GroupChatIMMessageTarget(final String value)
    {
    	super(value);
    }

    @Override
    public boolean equals(final Object arg0)
    {
        if (arg0 == null)
        {
            return false;
        }
        if (arg0 == this)
        {
            return true;
        }
        if (arg0 instanceof GroupChatIMMessageTarget)
        {
            final GroupChatIMMessageTarget other = (GroupChatIMMessageTarget) arg0;
            boolean retval = true;

            retval &= this.value.equals(other.value);

            return retval;
        }
        else
        {
            return false;
        }

    }
}
