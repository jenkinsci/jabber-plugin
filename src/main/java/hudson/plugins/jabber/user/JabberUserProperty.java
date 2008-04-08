/*
 * Created on Apr 22, 2007
 */
package hudson.plugins.jabber.user;

import hudson.model.UserProperty;
import hudson.model.UserPropertyDescriptor;

/**
 * Jabber user property.
 * @author Pascal Bleser
 */
public class JabberUserProperty extends UserProperty {
	
	public static final JabberUserPropertyDescriptor DESCRIPTOR = new JabberUserPropertyDescriptor();

	private final String jid;
	
	public JabberUserProperty(final String jid) {
		if ((jid != null) && (! "".equals(jid)) && (! validateJID(jid))) {
			throw new IllegalArgumentException("malformed Jabber ID " + jid);
		}
		if ("".equals(jid)) {
			this.jid = null;
		} else {
			this.jid = jid;
		}
	}
	
	public String getJid() {
		return this.jid;
	}
	
	public UserPropertyDescriptor getDescriptor() {
		return DESCRIPTOR;
	}
	
	private static final boolean validateJID(final String jid) {
		return (jid.trim().length() > 0) && jid.contains("@"); 
	}

}
