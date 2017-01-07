package de.unihd.dbs.uima.annotator.heideltime.utilities;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
/**
 * 
 * The Toolbox class contains methods with functionality that you would also
 * find outside the context of HeidelTime's specific skillset; i.e. they do
 * not require the CAS context, but are 'useful code snippets'.
 * @author jannik stroetgen
 *
 */
public class Toolbox {
	/**
	 * Find all the matches of a pattern in a charSequence and return the
	 * results as list.
	 * 
	 * @param pattern Pattern to be matched
	 * @param s String to be matched against
	 * @return Iterable List of MatchResults
	 */
	public static Iterable<MatchResult> findMatches(Pattern pattern, CharSequence s) {
		List<MatchResult> results = new ArrayList<MatchResult>();

		for (Matcher m = pattern.matcher(s); m.find();)
			results.add(m.toMatchResult());

		return results;
	}

	/**
	 * Sorts a given HashMap using a custom function
	 * @param m Map of items to sort
	 * @return sorted List of items
	 */
    public static List<Pattern> sortByValue(final HashMap<Pattern,String> m) {
        List<Pattern> keys = new ArrayList<Pattern>();
        keys.addAll(m.keySet());
        Collections.sort(keys, new Comparator<Object>() {
            @SuppressWarnings({ "unchecked", "rawtypes" })
			public int compare(Object o1, Object o2) {
                Object v1 = m.get(o1);
                Object v2 = m.get(o2);
                if (v1 == null) {
                    return (v2 == null) ? 0 : 1;
                } else if (v1 instanceof Comparable) {
                    return ((Comparable) v1).compareTo(v2);
                } else {
                    return 0;
                }
            }
        });
        return keys;
    }
}
