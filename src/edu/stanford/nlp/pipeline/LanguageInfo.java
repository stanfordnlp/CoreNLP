package edu.stanford.nlp.pipeline;

import java.lang.reflect.Field;

/**
 * This contains mappings from strings to language properties files
 */


public class LanguageInfo {

    /** list of properties files for each language **/
    public static final String CHINESE_PROPERTIES = "StanfordCoreNLP-chinese.properties";
    public static final String ENGLISH_PROPERTIES = "StanfordCoreNLP.properties";
    public static final String FRENCH_PROPERTIES = "StanfordCoreNLP-french.properties";
    public static final String GERMAN_PROPERTIES = "StanfordCoreNLP-german.properties";
    public static final String SPANISH_PROPERTIES = "StanfordCoreNLP-spanish.properties";

    private LanguageInfo() {
    }

    /**
     * Go through all of the paths via reflection, and print them out in a TSV format.
     * This is useful for command line scripts.
     *
     * @param args Ignored.
     */
    public static void main(String[] args) throws IllegalAccessException {
        for (Field field : LanguageInfo.class.getFields()) {
            System.out.println(field.getName() + "\t" + field.get(null));
        }
    }

    /** return the properties file name for a specific language **/
    public static String getLanguagePropertiesFile(String languageName) {
        String languageNameLower = languageName.toLowerCase();
        if (languageNameLower.equals("english") || languageNameLower.equals("en"))
            return ENGLISH_PROPERTIES;
        if (languageNameLower.equals("chinese") || languageNameLower.equals("zh"))
            return CHINESE_PROPERTIES;
        else if (languageNameLower.equals("french") || languageNameLower.equals("fr"))
            return FRENCH_PROPERTIES;
        else if (languageNameLower.equals("german") || languageNameLower.equals("de"))
            return GERMAN_PROPERTIES;
        else if (languageNameLower.equals("spanish") || languageNameLower.equals("es"))
            return SPANISH_PROPERTIES;
        else
            return null;
    }
}
