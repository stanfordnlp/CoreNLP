package edu.stanford.nlp.trees.ud;

import java.util.*;

/**
 * Utility functions for reading and writing CoNLL-U files.
 *
 * @author Sebastian Schuster
 */
public class CoNLLUUtils {

    /**
     * Parses the value of the extra dependencies column in a CoNLL-U file
     * and returns them in a HashMap with the governor indices as keys
     * and the relation names as values.
     *
     * @param extraDepsString
     * @return A {@code HashMap<Integer,String>} with the additional dependencies.
     */
    public static HashMap<String,String> parseExtraDeps(String extraDepsString) {
        HashMap<String,String> extraDeps = new HashMap<>();
        if ( ! extraDepsString.equals("_")) {
            String[] extraDepParts = extraDepsString.split("\\|");
            for (String extraDepString : extraDepParts) {
                int sepPos = extraDepString.indexOf(":");
                String reln = extraDepString.substring(sepPos + 1);
                String gov = extraDepString.substring(0, sepPos);
                extraDeps.put(gov, reln);
            }
        }
        return extraDeps;
    }

    /**
     * Converts an extra dependencies hash map to a string to be used
     * in a CoNLL-U file.
     *
     * @param extraDeps
     * @return The extra dependencies string.
     */
    public static String toExtraDepsString(HashMap<String,String> extraDeps) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        if (extraDeps != null) {
            List<String> sortedKeys = new ArrayList<>(extraDeps.keySet());
            Collections.sort(sortedKeys,  new DepIndexComparator());
            for (String key : sortedKeys) {
                if (!first) {
                    sb.append("|");
                } else {
                    first = false;
                }

                sb.append(key)
                        .append(":")
                        .append(extraDeps.get(key));
            }
        }
        /* Empty feature list. */
        if (first) {
            sb.append("_");
        }
        return sb.toString();
    }


    public static class DepIndexComparator implements Comparator<String> {
      // TODO FIXME: technically this doesn't work in the case of 10 or more extra nodes
        @Override
        public int compare(String depIndex1, String depIndex2) {
            return Float.valueOf(depIndex1).compareTo(Float.valueOf(depIndex2));
        }
    }
}
