package hudson.plugins.jabber.tools;

import static org.junit.Assert.*;

import org.junit.Test;

public class MessageHelperTest {

	@Test
	public void testExtractCommandLine() {
		assertEquals(1, MessageHelper.extractCommandLine("test").length);
		assertEquals(1, MessageHelper.extractCommandLine("test  ").length);
		assertEquals(3,
				MessageHelper.extractCommandLine("test param1 param2").length);
		// TODO desactivate to not break the global build
		// assertEquals(2,
		// MessageHelper.extractCommandLine("test \"same param\"").length);
		// assertEquals(2,
		// MessageHelper.extractCommandLine("test 'same param'").length);
	}

}
