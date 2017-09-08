package edu.stanford.nlp.tagger.maxent;

public class ExtractorCWordPrefixSyllable extends RareExtractor {
    private static final long serialVersionUID = -107L;

    ExtractorCWordPrefixSyllable() {
    }

    String extract(History h, PairsHolder pH) {
        String word = TestSentence.toNice(pH.getWord(h, 0));
        String[] syllables = word.split("_");
        return syllables.length > 1 ? syllables[0] : word;
    }
}
