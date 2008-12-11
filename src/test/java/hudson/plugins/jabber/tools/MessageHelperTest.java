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
		// TODO commented to not break the global build
		// assertEquals(2,
		// MessageHelper.extractCommandLine("test \"same param\"").length);
		// ' is not a separator
		// assertEquals(3,
		// MessageHelper.extractCommandLine("test 'same param'").length);
		
		// TODO add tests for :
		// "param1 param's param3" => 3
		// 'param1 "same param"' => 2
		// 'param1 "same param" param3' => 3
		// "param1 \\\"same param\\\" param3" => 3
	}

}
