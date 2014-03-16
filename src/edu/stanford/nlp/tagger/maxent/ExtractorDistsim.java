package edu.stanford.nlp.tagger.maxent;

/**
 * Extractor for adding distsim information.
 *
 * @author rafferty
 */
public class ExtractorDistsim extends Extractor {

  private static final long serialVersionUID = 2L;

  private final Distsim lexicon;

  @Override
  String extract(History h, PairsHolder pH) {
    String word = super.extract(h, pH);
    return lexicon.getMapping(word);
  }

  ExtractorDistsim(String distSimPath, int position) {
    super(position, false);
    lexicon = Distsim.initLexicon(distSimPath);
  }

  @Override public boolean isLocal() { return position == 0; }
  @Override public boolean isDynamic() { return false; }
}
