package hudson.plugins.jabber.tools;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.jvnet.hudson.test.Bug;

public class MessageHelperTest {

	@Test
	public void testExtractCommandLine() {
		assertEquals(1, MessageHelper.extractCommandLine("test").length);
		assertEquals(1, MessageHelper.extractCommandLine("test  ").length);
		assertEquals(3,
				MessageHelper.extractCommandLine("test param1 param2").length);

		assertEquals(3, MessageHelper.extractCommandLine("param1 \"same param\" param3").length);
		assertEquals("same param", MessageHelper.extractCommandLine("param1 \"same param\" param3")[1]);
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
	
	@Test
	@Bug(3215)
	public void testSingleQuote() {
	    String cmdLine = "\"";
	    assertEquals(1, MessageHelper.extractCommandLine(cmdLine).length);
	    assertEquals("\"", MessageHelper.extractCommandLine(cmdLine)[0]);
	    
	    cmdLine = "\"a b";
	    assertEquals(2, MessageHelper.extractCommandLine(cmdLine).length);
	    assertEquals("\"a", MessageHelper.extractCommandLine(cmdLine)[0]);
	    
	    cmdLine = "a b\"";
        assertEquals(2, MessageHelper.extractCommandLine(cmdLine).length);
        assertEquals("b\"", MessageHelper.extractCommandLine(cmdLine)[1]);
	}
}
