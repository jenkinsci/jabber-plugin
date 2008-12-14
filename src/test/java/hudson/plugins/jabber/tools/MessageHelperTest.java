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

		assertEquals(3, MessageHelper.extractCommandLine("param1 \"same param\" param3").length);
		assertEquals(2,
				MessageHelper.extractCommandLine("test \"same param\"").length);
		// ' is not a separator
		assertEquals(2, MessageHelper
				.extractCommandLine("param1 \"test 'same param'\"").length);

		// several quoted arguments
		assertEquals(3, MessageHelper
				.extractCommandLine("param1 \"second param\" \"third param\"").length);

		assertEquals(3, MessageHelper
				.extractCommandLine("param1 param's param3").length);

		assertEquals(1, MessageHelper.extractCommandLine("\"param1 param2\"").length);
	}
}
