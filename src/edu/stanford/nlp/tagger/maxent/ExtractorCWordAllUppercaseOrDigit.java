package edu.stanford.nlp.tagger.maxent;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

class ExtractorCWordAllUppercaseOrDigit extends RareExtractor {
    private static final long serialVersionUID = -115L;
    private static final Pattern pattern = Pattern.compile("[A-Z]+(_)?(\\d)*$");

    ExtractorCWordAllUppercaseOrDigit() {
    }

    String extract(History h, PairsHolder pH) {
        String word = TestSentence.toNice(pH.getWord(h, 0));
        Matcher matcher = pattern.matcher(word);
        return matcher.lookingAt() ? "1" : "0";
    }
}