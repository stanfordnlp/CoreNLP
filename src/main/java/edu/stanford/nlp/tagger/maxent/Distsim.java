package edu.stanford.nlp.tagger.maxent;

import edu.stanford.nlp.objectbank.ObjectBank;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Interner;
import edu.stanford.nlp.util.Timing;
import edu.stanford.nlp.util.logging.Redwood;

import java.io.File;
import java.io.Serializable;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Keeps track of a distributional similarity mapping, i.e., a map from
 * word to class.  Returns strings to save time, since that is how the
 * results are used in the tagger.
 * <p>
 * <i>Implementation note: This class largely overlaps DistSimClassifier. Unify?</i>
 */
public class Distsim implements Serializable {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(Distsim.class);

  // Avoid loading the same lexicon twice but allow different lexicons
  // TODO: when loading a distsim, should we populate this map?
  private static final Map<String,Distsim> lexiconMap = Generics.newHashMap();

  private final Map<String,String> lexicon;

  private final String unk;

  private boolean mapdigits; // = false
  private boolean casedDistSim; // = false;

  private static final Pattern digits = Pattern.compile("[0-9]");

  /**
   * The Extractor argument extraction keeps ; together, so we use
   * that to delimit options.  Actually, the only option supported is
   * mapdigits, which tells the Distsim to try mapping [0-9] to 0 and
   * requery for an unknown word with digits.
   */
  public Distsim(String path) {
    String[] pieces = path.split(";");
    String filename = pieces[0];
    for (int arg = 1; arg < pieces.length; ++arg) {
      if (pieces[arg].equalsIgnoreCase("mapdigits")) {
        mapdigits = true;
      } else if (pieces[arg].equalsIgnoreCase("casedDistSim")) {
        casedDistSim = true;
      } else {
        throw new IllegalArgumentException("Unknown argument " + pieces[arg]);
      }
    }

    // should work better than String.intern()
    // interning the strings like this means they should be serialized
    // in an interned manner, saving disk space and also memory when
    // loading them back in
    Interner<String> interner = new Interner<>();
    lexicon = Generics.newHashMap();
    // todo [cdm 2016]: Note that this loads file with default file encoding rather than specifying it
    for (String word : ObjectBank.getLineIterator(new File(filename))) {
      String[] bits = word.split("\\s+");
      String w = bits[0];
      if ( ! casedDistSim) {
        w = w.toLowerCase();
      }
      lexicon.put(w, interner.intern(bits[1]));
    }

    unk = lexicon.getOrDefault("<unk>", "null");
  }

  public static Distsim initLexicon(String path) {
    synchronized (lexiconMap) {
      Distsim lex = lexiconMap.get(path);
      if (lex == null) {
        Timing timer = new Timing();
        lex = new Distsim(path);
        lexiconMap.put(path, lex);
        timer.done(log, "Loading distsim lexicon from " + path);
      }
      return lex;
    }
  }

  /**
   * Returns the cluster for the given word as a string.  If the word
   * is not found, but the Distsim contains default numbers and the
   * word contains the digits 0-9, the default number is returned if
   * found.  If the word is still unknown, the unknown word is
   * returned ("null" if no other unknown word was specified).
   */
  public String getMapping(String word) {
    if ( ! casedDistSim) {
      word = word.toLowerCase();
    }
    String distSim = lexicon.get(word);

    if (distSim == null && mapdigits) {
      Matcher matcher = digits.matcher(word);
      if (matcher.find()) {
        distSim = lexicon.get(matcher.replaceAll("9"));
      }
    }

    if (distSim == null) {
      distSim = unk;
    }
    return distSim;
  }


  private static final long serialVersionUID = 2L;

}
