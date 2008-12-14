package hudson.plugins.jabber.tools;

import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.Hudson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Utility class to help message creation
 * 
 * @author vsellier
 */
public class MessageHelper {
	public final static Logger LOGGER = Logger.getLogger(MessageHelper.class
			.toString());

	private final static Pattern SPACE_PATTERN = Pattern.compile("\\s");
	private final static String QUOTE = "\"";
	
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
		return extractParameters(message).toArray(new String[] {});
	}

	private static List<String> extractParameters(String commandLine) {
		List<String> parameters = new ArrayList<String>();

		// space protection
		commandLine = commandLine.trim();
		
		if (commandLine.contains(QUOTE)) {
			int firstQuote = commandLine.indexOf(QUOTE);
			if (firstQuote == 0) {
				// the first character is the quote
				int end = commandLine.indexOf(QUOTE, 1);
				parameters.add(commandLine.substring(1, end - 1));
			} else {
				// adding every thing before the first quote
				parameters.addAll(extractParameters(commandLine.substring(0,
						firstQuote)));
				// adding the parameter between quotes
				int endQuoted = commandLine.indexOf(QUOTE, firstQuote + 1);
				parameters
						.add(commandLine.substring(firstQuote + 1, endQuoted));

				// adding everything after the quoted parameter into the
				// parameters list
				if (endQuoted < commandLine.length() - 1) {
					parameters.addAll(extractParameters(commandLine
							.substring(endQuoted + 1)));
				}
			}
		} else {
			// no quotes, just splitting on spaces
			Collections.addAll(parameters, SPACE_PATTERN.split(commandLine));
		}
		return parameters;
	}
}
