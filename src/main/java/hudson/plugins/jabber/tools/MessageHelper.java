package hudson.plugins.jabber.tools;

import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.Hudson;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;

/**
 * Utility class to help message creation
 * 
 * @author vsellier
 */
public class MessageHelper {
	private final static Pattern SPACE_PATTERN = Pattern.compile("\\s");
	private final static String QUOTE = "\"";
	
	public static String getBuildURL(AbstractBuild<?, ?> lastBuild) {
		// The hudson's base url
		StringBuilder builder = new StringBuilder(
				String.valueOf(Hudson.getInstance().getRootUrl()));

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
				parameters.add(commandLine.substring(firstQuote + 1, endQuoted));

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
	
	/**
	 * Unfortunately in Java 5 there is no Arrays.copyOfRange.
	 * So we have to implement it ourself.
	 */
	@SuppressWarnings("unchecked")
    public static <T,U> T[] copyOfRange(U[] original, int from, int to, Class<? extends T[]> newType) {
        int newLength = to - from;
        if (newLength < 0)
            throw new IllegalArgumentException(from + " > " + to);
        T[] copy = ((Object)newType == (Object)Object[].class)
            ? (T[]) new Object[newLength]
            : (T[]) Array.newInstance(newType.getComponentType(), newLength);
        System.arraycopy(original, from, copy, 0,
                         Math.min(original.length - from, newLength));
        return copy;
    }

	/**
	 * Joins together all strings in the array - starting at startIndex - by
	 * using a single space as separator.
	 */
	public static String join(String[] array, int startIndex) {
		String joined = StringUtils.join(copyOfRange(array, startIndex, array.length, String[].class), " ");
	    joined = joined.replaceAll("\"", "");
	    return joined;
	}
}
