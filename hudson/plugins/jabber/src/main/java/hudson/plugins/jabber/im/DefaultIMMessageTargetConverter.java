package hudson.plugins.jabber.im;

import hudson.plugins.jabber.tools.Assert;

/**
 * {@inheritDoc}
 * @author doc
 *
 */
public class DefaultIMMessageTargetConverter implements IMMessageTargetConverter
{

    /**
     * {@inheritDoc}
     */
    public IMMessageTarget fromString(final String targetAsString) throws IMMessageTargetConversionException
    {
        if (targetAsString != null)
        {
            final String f = targetAsString.trim();
            if (f.length() > 0)
            {
                return new DefaultIMMessageTarget(f);
            }
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    public String toString(final IMMessageTarget target)
    {
        Assert.isNotNull(target, "Parameter 'target' must not be null.");
        return target.toString();
    }
}