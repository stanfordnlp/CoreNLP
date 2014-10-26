

package edu.stanford.nlp.depparser.util;

import java.util.*;

/**
 * Utilities for managing command line flags and arguments.
 * 
 * @author Dan Klein
 */

public class CommandLineUtils 
{

	/**
	 * Simple method which turns an array of command line arguments into a map,
	 * where each token starting with a '-' is a key and the following non '-'
	 * initial token, if there is one, is the value. For example, '-size 5
	 * -verbose' will produce a map with entries (-size, 5) and (-verbose,
	 * null).
	 */
	public static Map<String, String> simpleCommandLineParser(String[] args) 
	{
		Map<String, String> map = new HashMap<String, String>();
		for (int i = 0; i <= args.length; i++) {
			String key = (i > 0 ? args[i - 1] : null);
			String value = (i < args.length ? args[i] : null);

			if (key == null || key.startsWith("-")) {
				if (value != null && value.startsWith("-"))
					value = null;
				if (key != null || value != null)
					map.put(key.substring(1, key.length()), value);
			}
		}
		return map;
	}
}
