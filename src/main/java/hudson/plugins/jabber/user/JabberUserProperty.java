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

import hudson.Extension;
import hudson.model.UserProperty;
import hudson.model.UserPropertyDescriptor;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

/**
 * Jabber user property.
 * 
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
		if ((jid != null) && (!"".equals(jid)) && (!validateJID(jid))) {
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
