package edu.stanford.nlp.tagger.maxent;

import edu.stanford.nlp.objectbank.ObjectBank;
import edu.stanford.nlp.util.Timing;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Extractor for adding distsim information.
 *
 * @author rafferty
 */
public class ExtractorDistsim extends Extractor {

  private static final long serialVersionUID = 1L;

  // avoid loading the same lexicon twice but allow different lexicons
  private static final Map<String,Map<String,String>> lexiconMap = new HashMap<String, Map<String,String>>();

  private final Map<String,String> lexicon;

  private static Map<String,String> initLexicon(String path) {
    synchronized (lexiconMap) {
      Map<String,String> lex = lexiconMap.get(path);
      if (lex != null) {
        return lex;
      } else {
        Timing.startDoing("Loading distsim lexicon from " + path);
        Map<String,String> lexic = new HashMap<String, String>();
        for (String word : ObjectBank.getLineIterator(new File(path))) {
          String[] bits = word.split("\\s+");
          lexic.put(bits[0].toLowerCase(), bits[1]);
        }
        lexiconMap.put(path, lexic);
        Timing.endDoing();
        return lexic;
      }
    }
  }

  @Override
  String extract(History h, PairsHolder pH) {
    String word = super.extract(h, pH);
    String distSim = lexicon.get(word.toLowerCase());
    if (distSim == null) distSim = "null";
    return distSim;
  }

  ExtractorDistsim(String distSimPath, int position) {
    super(position, false);
    lexicon = initLexicon(distSimPath);
  }

  @Override public boolean isLocal() { return position == 0; }
  @Override public boolean isDynamic() { return false; }


  public static class ExtractorDistsimConjunction extends Extractor {

    private static final long serialVersionUID = 1L;

    private final Map<String,String> lexicon;
    private final int left;
    private final int right;
    private String name;

    @Override
    String extract(History h, PairsHolder pH) {
      StringBuilder sb = new StringBuilder();
      for (int j = left; j <= right; j++) {
        String word = pH.getWord(h, j);
        String distSim = lexicon.get(word.toLowerCase());
        if (distSim == null) distSim = "null";
        sb.append(distSim);
        if (j < right) {
          sb.append('|');
        }
      }
      return sb.toString();
    }

    ExtractorDistsimConjunction(String distSimPath, int left, int right) {
      super();
      lexicon = initLexicon(distSimPath);
      this.left = left;
      this.right = right;
      name = "ExtractorDistsimConjunction(" + left + ',' + right + ')';
    }

    @Override
    public String toString() {
      return name;
    }

    @Override public boolean isLocal() { return false; }
    @Override public boolean isDynamic() { return false; }

  } // end static class ExtractorDistsimConjunction

}
