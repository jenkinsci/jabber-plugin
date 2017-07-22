/**
 * Copyright (c) 2007-2017 the original author or authors
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
 * CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE
 */
package hudson.plugins.jabber.user;

import hudson.model.User;
import hudson.model.UserProperty;
import hudson.model.UserPropertyDescriptor;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Descriptor for Jabber user property.
 * 
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
