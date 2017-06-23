package edu.stanford.nlp.tagger.maxent;

import edu.stanford.nlp.util.logging.Redwood;

import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.util.Pair;

/** Maintains a set of feature extractors for a maxent POS tagger and applies them.
 *
 *  @author Kristina Toutanova
 *  @version 1.0
 */
public class Extractors implements Serializable  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(Extractors.class);

  private final Extractor[] v;

  private static final boolean DEBUG = false;

  transient List<Pair<Integer,Extractor>>
    local, // extractors only looking at current word
    localContext, // extractors only looking at words, except those in "local"
    dynamic; // extractors depending on class labels


  /**
   * Set the extractors from an array.
   *
   * @param extrs The array of extractors.  It is copied in this init.
   */
  public Extractors(Extractor[] extrs) {
    v = new Extractor[extrs.length];
    System.arraycopy(extrs, 0, v, 0, extrs.length);
    initTypes();
  }


  /**
   * Determine type of each feature extractor.
   */
  void initTypes() {

    local = new ArrayList<>();
    localContext = new ArrayList<>();
    dynamic = new ArrayList<>();

    for(int i=0; i<v.length; ++i) {
      Extractor e = v[i];
      if(e.isLocal() && e.isDynamic())
        throw new RuntimeException("Extractors can't both be local and dynamic!");
      if(e.isLocal()) {
        local.add(Pair.makePair(i,e));
        //localContext.put(i,e);
      } else if(e.isDynamic()) {
        dynamic.add(Pair.makePair(i,e));
      } else {
        localContext.add(Pair.makePair(i,e));
      }
    }
    if(DEBUG) {
      log.info("Extractors: " + this);
      log.info("Local: " + local.size() + " extractors");
      log.info("Local context: " + localContext.size() + " extractors");
      log.info("Dynamic: " + dynamic.size() + " extractors");
    }
  }

  /**
   * Extract using the i'th extractor.
   * @param i The extractor to use
   * @param h The history to extract from
   * @return String The feature value
   */

  String extract(int i, History h) {
    return v[i].extract(h);
  }

  boolean equals(History h, History h1) {
    for (Extractor extractor : v) {
      if ( ! (extractor.extract(h).equals(extractor.extract(h1)))) {
        return false;
      }
    }
    return true;
  }


  /** Find maximum left context of extractors. Used in TagInference to decide windows for dynamic programming.
   * @return The maximum of the left contexts used by all extractors.
   */
  int leftContext() {
    int max = 0;

    for (Extractor extractor : v) {
      int lf = extractor.leftContext();
      if (lf > max) {
        max = lf;
      }
    }

    return max;
  }


  /** Find maximum right context of extractors. Used in TagInference to decide windows for dynamic programming.
   * @return The maximum of the right contexts used by all extractors.
   */
  int rightContext() {
    int max = 0;

    for (Extractor extractor : v) {
      int lf = extractor.rightContext();
      if (lf > max) {
        max = lf;
      }
    }

    return max;
  }


  public int size() {
    return v.length;
  }

  protected void setGlobalHolder(MaxentTagger tagger) {
    for (Extractor extractor : v) {
      extractor.setGlobalHolder(tagger);
    }
  }

  /*
  public void save(String filename) {
    try {
      DataOutputStream rf = IOUtils.getDataOutputStream(filename);
      rf.writeInt(v.length);
      for (Extractor extr : v) {
        rf.writeBytes(extr.toString());
      }
      rf.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public void read(String filename) {
    try {
      InDataStreamFile rf = new InDataStreamFile(filename);
      int len = rf.readInt();
      v = new Extractor[len];
      //GlobalHolder.init();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
  */

  Extractor get(int index) {
    return v[index];
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("Extractors[");
    for (int i = 0; i < v.length; i++) {
      sb.append(v[i]);
      if (i < v.length - 1) {
        sb.append(", ");
      }
    }
    sb.append(']');
    return sb.toString();
  }


  /**
   * Prints out the pair of {@code Extractors} objects found in the
   * file that is the first and only argument.
   * @param args Filename of extractors file (standardly written with
   *       {@code .ex} extension)
   */
  public static void main(String[] args) {
    try {
      ObjectInputStream in = new ObjectInputStream(new FileInputStream(args[0]));
      Extractors extrs = (Extractors) in.readObject();
      Extractors extrsRare = (Extractors) in.readObject();
      in.close();
      System.out.println("All words:  " + extrs);
      System.out.println("Rare words: " + extrsRare);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static final long serialVersionUID = -4777107742414749890L;

}
