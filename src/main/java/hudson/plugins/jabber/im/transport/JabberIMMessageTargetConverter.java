/**
 * Copyright (c) 2007-2021 the original author or authors
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
package hudson.plugins.jabber.im.transport;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import hudson.plugins.im.DefaultIMMessageTarget;
import hudson.plugins.im.GroupChatIMMessageTarget;
import hudson.plugins.im.IMMessageTarget;
import hudson.plugins.im.IMMessageTargetConversionException;
import hudson.plugins.im.IMMessageTargetConverter;
import hudson.util.Secret;

import org.springframework.util.Assert;



/**
 * Converts Jabber IDs from/to strings.
 * 
 * @author kutzi
 */
public class JabberIMMessageTargetConverter implements IMMessageTargetConverter {

	private void checkValidity(final String target) throws IMMessageTargetConversionException {
		// See: http://xmpp.org/rfcs/rfc3920.html#addressing
		// obviously, there is no easy regexp to validate this.
		// Additionally, we require the part before the @.
		// So, just some very simple validation:
		final int i = target.indexOf('@');
		if (i == -1) {
			throw new IMMessageTargetConversionException(
					"Invalid input for target: '" + target + "'." + "\nDoesn't contain a @.");
		} else if (target.indexOf('@', i + 1) != -1) {
			throw new IMMessageTargetConversionException(
					"Invalid input for target: '" + target + "'." + "\nContains more than on @.");
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IMMessageTarget fromString(final String targetAsString) throws IMMessageTargetConversionException {
		String f = targetAsString.trim();
		if (f.length() > 0) {
			IMMessageTarget target;
			if (f.startsWith("*")) {
				f = f.substring(1);
				// group chat
				if (!f.contains("@")) {
					f += "@conference." + JabberPublisher.DESCRIPTOR.getHost();
				}
				target = new GroupChatIMMessageTarget(f, (Secret) null, false);
			} else if (f.contains("@conference.")) {
				target = new GroupChatIMMessageTarget(f, (Secret) null, false);
			} else {
				if (!f.contains("@")) {
					f += "@" + JabberPublisher.DESCRIPTOR.getHost();
				}
				target = new DefaultIMMessageTarget(f);
			}
			checkValidity(f);
			return target;
		} else {
			return null;
		}
	}

	public List<IMMessageTarget> allFromString(final Collection<String> targetsAsString) throws IMMessageTargetConversionException {
		List<IMMessageTarget> finalTargets = new LinkedList<>();
		for (String target : targetsAsString) {
			finalTargets.add(fromString(target));
		}
		return finalTargets;
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
