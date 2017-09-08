package edu.stanford.nlp.tagger.maxent;

class ExtractorCWordNumberOfCharacter extends RareExtractor {
    private static final long serialVersionUID = -113L;

    ExtractorCWordNumberOfCharacter() {
    }

    String extract(History h, PairsHolder pH) {
        String word = TestSentence.toNice(pH.getWord(h, 0));
        return "" + word.length();
    }
}

