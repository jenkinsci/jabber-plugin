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
package hudson.plugins.jabber.im;

/**
 * DefaultIMMessageTarget basically is a String, that represents an Im-Account to send messages to.
 * 
 * @author Uwe Schaefer
 */
@Deprecated
public class DefaultIMMessageTarget extends hudson.plugins.im.DefaultIMMessageTarget {
	private static final long serialVersionUID = 1L;

	public DefaultIMMessageTarget(final String value) {
		super(value);
	}

	@Override
	public boolean equals(final Object o) {
		if (o == null) {
			return false;
		}
		if (o == this) {
			return true;
		}
		if (o instanceof DefaultIMMessageTarget) {
			final DefaultIMMessageTarget other = (DefaultIMMessageTarget) o;
			return this.value.equals(other.value);
		} else {
			return false;
		}

	}
}
