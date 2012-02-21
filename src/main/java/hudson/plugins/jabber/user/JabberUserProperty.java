/*
 * Created on Apr 22, 2007
 */
package hudson.plugins.jabber.user;

import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import hudson.Extension;
import hudson.model.UserProperty;
import hudson.model.UserPropertyDescriptor;

/**
 * Jabber user property.
 * @author Pascal Bleser
 */
@ExportedBean(defaultVisibility = 999)
public class JabberUserProperty extends UserProperty {

	@Extension
	public static final JabberUserPropertyDescriptor DESCRIPTOR = new JabberUserPropertyDescriptor();

	private String jid;
	
	public JabberUserProperty() {
	    // public constructor needed for @Extension parsing
	}
	
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
	
	@Exported
	public String getJid() {
		return this.jid;
	}
	
	@Override
    public UserPropertyDescriptor getDescriptor() {
		return DESCRIPTOR;
	}
	
	private static final boolean validateJID(final String jid) {
		return (jid.trim().length() > 0) && jid.contains("@"); 
	}

}
