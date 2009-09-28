/*
 * Created on Apr 22, 2007
 */
package hudson.plugins.jabber.user;

import hudson.model.User;
import hudson.model.UserProperty;
import hudson.model.UserPropertyDescriptor;

import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Descriptor for Jabber user property.
 * @author Pascal Bleser
 */
public class JabberUserPropertyDescriptor extends UserPropertyDescriptor {
	
	public static final String PARAMETERNAME_JID = "jabber.user.jid";
	
	public JabberUserPropertyDescriptor() {
		super(JabberUserProperty.class);
	}

	@Override
	public UserProperty newInstance(User user) {
		return new JabberUserProperty(null);
	}

	@Override
	public UserProperty newInstance(StaplerRequest req, JSONObject formData) throws FormException {
		try {
			return new JabberUserProperty(req.getParameter(PARAMETERNAME_JID));
		} catch (IllegalArgumentException e) {
			throw new FormException("invalid Jabber ID", PARAMETERNAME_JID);
		}
	}

	@Override
	public String getDisplayName() {
		return "Jabber ID";
	}


}
