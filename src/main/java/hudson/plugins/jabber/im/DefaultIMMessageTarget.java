package hudson.plugins.jabber.im;


/**
 * DefaultIMMessageTarget basically is a String, that represents an Im-Account to send messages to.
 * @author Uwe Schaefer
 */
@Deprecated
public class DefaultIMMessageTarget extends hudson.plugins.im.DefaultIMMessageTarget
{
    private static final long serialVersionUID = 1L;

    public DefaultIMMessageTarget(final String value)
    {
    	super(value);
    }

    @Override
    public boolean equals(final Object o)
    {
        if (o == null)
        {
            return false;
        }
        if (o == this)
        {
            return true;
        }
        if (o instanceof DefaultIMMessageTarget)
        {
            final DefaultIMMessageTarget other = (DefaultIMMessageTarget) o;
            return this.value.equals(other.value);
        }
        else
        {
            return false;
        }

    }
}