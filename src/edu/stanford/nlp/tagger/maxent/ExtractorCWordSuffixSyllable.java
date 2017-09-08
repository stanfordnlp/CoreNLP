package edu.stanford.nlp.tagger.maxent;

class ExtractorCWordSuffixSyllable extends RareExtractor {
    private static final long serialVersionUID = -103L;

    ExtractorCWordSuffixSyllable() {
    }

    String extract(History h, PairsHolder pH) {
        String word = TestSentence.toNice(pH.getWord(h, 0));
        String[] syllables = word.split("_");
        return syllables.length > 1 ? syllables[syllables.length - 1] : word;
    }
}

