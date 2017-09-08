package edu.stanford.nlp.tagger.maxent;

class ExtractorCWordNumberOfSyllable extends RareExtractor {
    private static final long serialVersionUID = -111L;

    ExtractorCWordNumberOfSyllable() {
    }

    String extract(History h, PairsHolder pH) {
        String word = TestSentence.toNice(pH.getWord(h, 0));
        String[] syllables = word.split("_");
        return "" + syllables.length;
    }
}

