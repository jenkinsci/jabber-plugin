package hudson.plugins.jabber.im.transport;

import hudson.plugins.im.DefaultIMMessageTarget;
import hudson.plugins.im.GroupChatIMMessageTarget;
import hudson.plugins.im.IMMessageTarget;
import hudson.plugins.im.IMMessageTargetConversionException;
import hudson.plugins.im.IMMessageTargetConverter;

import org.springframework.util.Assert;

/**
 * Converts Jabber IDs from/to strings.
 * 
 * @author kutzi
 */
class JabberIMMessageTargetConverter implements IMMessageTargetConverter {
    
    private void checkValidity(final String target) throws IMMessageTargetConversionException {
    	// See: http://xmpp.org/rfcs/rfc3920.html#addressing
    	// obviously, there is no easy regexp to validate this.
    	// Additionally, we require the part before the @.
        // So, just some very simple validation:
        final int i = target.indexOf('@');
        if (i == -1) {
        	throw new IMMessageTargetConversionException("Invalid input for target: '" + target + "'." +
        			"\nDoesn't contain a @.");
        } else if (target.indexOf('@', i + 1) != -1)
        {
            throw new IMMessageTargetConversionException("Invalid input for target: '" + target + "'." +
            		"\nContains more than on @.");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IMMessageTarget fromString(final String targetAsString) throws IMMessageTargetConversionException {
        String f = targetAsString.trim();
        if (f.length() > 0)
        {
        	IMMessageTarget target;
        	if (f.startsWith("*")) {
        		f = f.substring(1);
        		// group chat
        		if (! f.contains("@")) {
        			f += "@conference." + JabberPublisher.DESCRIPTOR.getHost();
        		}
        		target = new GroupChatIMMessageTarget(f);
        	} else if (f.contains("@conference.")) {
        		target = new GroupChatIMMessageTarget(f);
        	} else {
                if (!f.contains("@")) {
                    f += "@" + JabberPublisher.DESCRIPTOR.getHost();
                }
                target = new DefaultIMMessageTarget(f);
        	}
            checkValidity(f);
            return target;
        }
        else
        {
            return null;
        }
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
	public String toString(final IMMessageTarget target) {
        Assert.notNull(target, "Parameter 'target' must not be null.");
        return target.toString();
    }
}