package edu.stanford.nlp.tagger.maxent;

class ExtractorCWordCapitalizedSyllable extends RareExtractor {
    private static final long serialVersionUID = -109L;

    ExtractorCWordCapitalizedSyllable() {
    }

    String extract(History h, PairsHolder pH) {
        String word = TestSentence.toNice(pH.getWord(h, 0));
        String[] syllables = word.split("_");
        String[] arr$ = syllables;
        int len$ = syllables.length;

        for(int i$ = 0; i$ < len$; ++i$) {
            String syllable = arr$[i$];
            if (Character.isLowerCase(syllable.charAt(0))) {
                return "0";
            }
        }

        return "1";
    }
}

