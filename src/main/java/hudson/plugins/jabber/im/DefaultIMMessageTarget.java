package hudson.plugins.jabber.im;

import hudson.plugins.jabber.tools.Assert;

/**
 * DefaultIMMessageTarget basically is a String, that represents an Im-Account to send messages to.
 * @author Uwe Schaefer
 */
public class DefaultIMMessageTarget implements IMMessageTarget
{
    private static final long serialVersionUID = 1L;
    private String value;

    public DefaultIMMessageTarget(final String value)
    {
        Assert.isNotNull(value, "Parameter 'value' must not be null.");
        this.value = value;
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
        if (arg0 instanceof DefaultIMMessageTarget)
        {
            final DefaultIMMessageTarget other = (DefaultIMMessageTarget) arg0;
            boolean retval = true;

            retval &= this.value.equals(other.value);

            return retval;
        }
        else
        {
            return false;
        }

    }

    @Override
    public int hashCode()
    {
        return this.value.hashCode();
    }

    @Override
    public String toString()
    {
        return this.value;
    }
}