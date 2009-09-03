package hudson.plugins.jabber.im.transport;

import junit.framework.Assert;

import org.junit.Test;


public class JabberUtilTest {

	@Test
	public void testGetUserPart() {
		String jabberId = "abc";
		Assert.assertEquals("abc", JabberUtil.getUserPart(jabberId));
		
		jabberId = "abc@xyz";
		Assert.assertEquals("abc", JabberUtil.getUserPart(jabberId));
		
		jabberId = "abc@xyz/resource";
		Assert.assertEquals("abc", JabberUtil.getUserPart(jabberId));
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testMissingUserPart() {
		String jabberId = " @domain";
		JabberUtil.getUserPart(jabberId);
	}
	
	@Test
	public void testGetDomainPart() {
		String jabberId = "abc";
		Assert.assertNull(JabberUtil.getDomainPart(jabberId));
		
		jabberId = "abc@xyz";
		Assert.assertEquals("xyz", JabberUtil.getDomainPart(jabberId));
		
		jabberId = "abc@xyz/resource";
		Assert.assertEquals("xyz", JabberUtil.getDomainPart(jabberId));
		
		jabberId = "abc@";
		Assert.assertNull(JabberUtil.getDomainPart(jabberId));
	}
	
	@Test
	public void testGetResourcePart() {
		String jabberId = "abc";
		Assert.assertNull(JabberUtil.getResourcePart(jabberId));
		
		jabberId = "abc@xyz";
		Assert.assertNull(JabberUtil.getResourcePart(jabberId));
		
		jabberId = "abc@xyz/resource";
		Assert.assertEquals("resource", JabberUtil.getResourcePart(jabberId));
		
		jabberId = "abc@xyz/";
		Assert.assertNull(JabberUtil.getResourcePart(jabberId));
	}
}
