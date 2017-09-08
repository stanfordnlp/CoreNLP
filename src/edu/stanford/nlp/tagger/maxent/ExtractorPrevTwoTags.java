package edu.stanford.nlp.tagger.maxent;

import old.edu.stanford.nlp.util.StringBuildMemoizer;

class ExtractorPrevTwoTags extends Extractor {
    private static final long serialVersionUID = 5124896556547424355L;

    public ExtractorPrevTwoTags() {
    }

    public int leftContext() {
        return 2;
    }

    String extract(History h, PairsHolder pH) {
        return StringBuildMemoizer.toString(new String[]{pH.getTag(h, -1), "!", pH.getTag(h, -2)});
    }

    public boolean isLocal() {
        return false;
    }

    public boolean isDynamic() {
        return true;
    }
}
