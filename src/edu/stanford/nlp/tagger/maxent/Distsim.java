package edu.stanford.nlp.tagger.maxent;

import edu.stanford.nlp.objectbank.ObjectBank;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Timing;

import java.io.File;
import java.io.Serializable;
import java.util.Map;

/**
 * Keeps track of a distributional similarity mapping, eg a map from
 * word to class.  Returns strings to save time, since that is how the
 * results are used in the tagger.
 */
public class Distsim implements Serializable {
  // avoid loading the same lexicon twice but allow different lexicons
  private static final Map<String,Distsim> lexiconMap = Generics.newHashMap();

  private final Map<String,String> lexicon;

  private final String unk;

  public Distsim(String path) {
    lexicon = Generics.newHashMap();
    for (String word : ObjectBank.getLineIterator(new File(path))) {
      String[] bits = word.split("\\s+");
      lexicon.put(bits[0].toLowerCase(), bits[1]);
    }
    // TODO: check for numbers?
    if (lexicon.containsKey("<unk>")) {
      unk = lexicon.get("<unk>");
    } else {
      unk = "null";
    }
  }

  static Distsim initLexicon(String path) {
    synchronized (lexiconMap) {
      Distsim lex = lexiconMap.get(path);
      if (lex == null) {
        Timing.startDoing("Loading distsim lexicon from " + path);
        lex = new Distsim(path);
        lexiconMap.put(path, lex);
        Timing.endDoing();
      }
      return lex;
    }
  }


  public String getMapping(String word) {
    String distSim = lexicon.get(word.toLowerCase());
    if (distSim == null) {
      distSim = unk;
    }
    return distSim;
  }


  private static final long serialVersionUID = 2L;
}
