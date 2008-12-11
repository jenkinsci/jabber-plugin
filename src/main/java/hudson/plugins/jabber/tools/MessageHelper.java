package hudson.plugins.jabber.tools;

import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.Build;
import hudson.model.Hudson;

/**
 * Utility class to help message creation
 * 
 * @author vsellier
 */
public class MessageHelper {
	public static String getBuildURL(AbstractBuild<?, ?> lastBuild) {
		// The hudson's base url
		StringBuilder builder = new StringBuilder(Hudson.getInstance()
				.getRootUrl());

		// The build's url, escaped for project with space or other specials
		// characters
		builder.append(Util.encode(lastBuild.getUrl()));

		return builder.toString();

	}
	
	public static String[] extractCommandLine(String message) {
		return message.split("\\s");		
	}
}
