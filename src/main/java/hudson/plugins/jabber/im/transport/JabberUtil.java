package hudson.plugins.jabber.im.transport;

import org.jivesoftware.smack.util.StringUtils;

import hudson.Util;

/**
 * Jabber related utility methods.
 *
 * @author kutzi
 */
public class JabberUtil {

	private JabberUtil() {
		// no instances
	}
	
	/**
	 * Returns the user part (aka. 'node') of a jabber ID in the form:
	 * 
	 * <user>[@<domain>[/<resource]]
	 * 
	 * @return the part before '@' or jabberId unchanged if '@' was not found
	 */
	public static String getUserPart(String jabberId) {
		int idx = jabberId.indexOf('@');
        if (idx == -1)
            return jabberId;
        else {
            String userPart = jabberId.substring(0, idx);
            if (Util.fixEmptyAndTrim(userPart) == null) {
            	throw new IllegalArgumentException("Missing user part in " + jabberId);
            }
            return userPart;
        }
	}
	
	/**
	 * Returns the domain part of a jabber ID in the form:
	 * 
	 * <user>[@<domain>[/<resource]]
	 * 
	 * @return the domain or null if no '@' was found
	 */
	public static String getDomainPart(String jabberId) {
		int atIdx = jabberId.indexOf('@');
        if (atIdx == -1) {
            return null;
        } else {
        	int slashIdx = jabberId.indexOf('/', atIdx);
        	if (slashIdx == -1) {
        		if (atIdx + 1 < jabberId.length()) {
        			return jabberId.substring(atIdx + 1);
        		} else {
        			return null;
        		}
        	} else {
        		// filter out 'resource' part
        		return jabberId.substring(atIdx + 1, slashIdx);
        	}
        }
	}
	
	/**
	 * Returns the resource part of a jabber ID in the form:
	 * 
	 * <user>[@<domain>[/<resource]]
	 * 
	 * @return the domain or null if no '@' and '/' was found
	 */
	public static String getResourcePart(String jabberId) {
		String resource = StringUtils.parseResource(jabberId);
		return resource.length() > 0 ? resource : null;
	}
}
