package edu.stanford.nlp.trees.ud;

import java.util.*;

/**
 * Utility functions for reading and writing CoNLL-U files.
 *
 * @author Sebastian Schuster
 */
public class CoNLLUUtils {

    /**
     * Parses the value of the feature column in a CoNLL-U file
     * and returns them in a HashMap with the feature names as keys
     * and the feature values as values.
     *
     * @param featureString
     * @return A HashMap<String,String> with the feature values.
     */
    public static HashMap<String,String> parseFeatures(String featureString) {
        HashMap<String, String> features = new HashMap<>();
        if (! featureString.equals("_")) {
            String[] featValPairs = featureString.split("\\|");
            for (String p : featValPairs) {
                String[] featValPair = p.split("=");
                features.put(featValPair[0], featValPair[1]);
            }
        }
        return features;
    }

    /**
     * Converts a feature HashMap to a feature string to be used
     * in a CoNLL-U file.
     *
     * @return The feature string.
     */
    public static String toFeatureString(HashMap<String,String> features) {
        StringBuffer sb = new StringBuffer();
        boolean first = true;
        if (features != null) {
            List<String> sortedKeys = new ArrayList<>(features.keySet());
            Collections.sort(sortedKeys, new FeatureNameComparator());
            for (String key : sortedKeys) {
                if (!first) {
                    sb.append("|");
                } else {
                    first = false;
                }

                sb.append(key)
                        .append("=")
                        .append(features.get(key));

            }
        }

    /* Empty feature list. */
        if (first) {
            sb.append("_");
        }

        return sb.toString();
    }

    /**
     * Parses the value of the extra dependencies column in a CoNLL-U file
     * and returns them in a HashMap with the governor indices as keys
     * and the relation names as values.
     *
     * @param extraDepsString
     * @return A HashMap<Integer,String> with the additional dependencies.
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
        StringBuffer sb = new StringBuffer();
        boolean first = true;
        if (extraDeps != null) {
            List<String> sortedKeys = new ArrayList<>(extraDeps.keySet());
            Collections.sort(sortedKeys);
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


    public static class FeatureNameComparator implements Comparator<String> {

        @Override
        public int compare(String featureName1, String featureName2) {
            return featureName1.toLowerCase().compareTo(featureName2.toLowerCase());
        }
    }
}
