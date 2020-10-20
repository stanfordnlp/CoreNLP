package edu.stanford.nlp.process;

import java.io.Serializable;
import java.util.Map;

import edu.stanford.nlp.objectbank.ObjectBank;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Interner;
import edu.stanford.nlp.util.Timing;


/** Maps a String to its distributional similarity class.
 *
 *  @author Christopher Manning
 */
public class DistSimClassifier implements Serializable {

  private static final long serialVersionUID = 3L;

  private final Map<String,String> lexicon;
  private final boolean cased;
  private final boolean numberEquivalence;
  private final String unknownWordClass;


  public DistSimClassifier(String filename, boolean cased, boolean numberEquivalence) {
    this(filename, "alexClark", "utf-8", -1, cased, numberEquivalence, "NULL");
  }

  public DistSimClassifier(String filename, boolean cased,
                           boolean numberEquivalence, String unknownWordClass) {
    this(filename, "alexClark", "utf-8", -1, cased, numberEquivalence, unknownWordClass);
  }

  public DistSimClassifier(String filename, String format, String encoding,
                           int distSimMaxBits,
                           boolean cased, boolean numberEquivalence,
                           String unknownWordClass) {
    this.cased = cased;
    this.numberEquivalence = numberEquivalence;
    this.unknownWordClass = unknownWordClass;
    Timing.startDoing("Loading distsim lexicon from " + filename);
    // should work better than String.intern()
    // interning the strings like this means they should be serialized
    // in an interned manner, saving disk space and also memory when
    // loading them back in
    Interner<String> interner = new Interner<>();
    lexicon = Generics.newHashMap(1 << 15);  // make a reasonable starting size
    boolean terryKoo = "terryKoo".equals(format);
    for (String line : ObjectBank.getLineIterator(filename, encoding)) {
      String word;
      String wordClass;
      if (terryKoo) {
        String[] bits = line.split("\\t");
        word = bits[1];
        wordClass = bits[0];
        if (distSimMaxBits > 0 && wordClass.length() > distSimMaxBits) {
          wordClass = wordClass.substring(0, distSimMaxBits);
        }
      } else {
        // "alexClark"
        String[] bits = line.split("\\s+");
        word = bits[0];
        wordClass = bits[1];
      }
      if ( ! cased) {
        word = word.toLowerCase();
      }
      if (numberEquivalence) {
        word = WordShapeClassifier.wordShape(word, WordShapeClassifier.WORDSHAPEDIGITS);
      }
      lexicon.put(word, interner.intern(wordClass));
    }
    Timing.endDoing();
  }


  public String distSimClass(String word) {
    if ( ! cased) {
      word = word.toLowerCase();
    }
    if (numberEquivalence) {
      word = WordShapeClassifier.wordShape(word, WordShapeClassifier.WORDSHAPEDIGITS);
    }
    String distSim = lexicon.get(word);
    if (distSim == null) {
      distSim = unknownWordClass;
    }
    return distSim;
  }

}
