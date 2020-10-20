package edu.stanford.nlp.parser.lexparser; 
import edu.stanford.nlp.util.logging.Redwood;

import edu.stanford.nlp.ie.ChineseMorphFeatureSets;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.international.pennchinese.RadicalMap;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.stats.IntCounter;

import java.util.*;

/**
 * @author Galen Andrew
 */
public class ChineseWordFeatureExtractor implements WordFeatureExtractor  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(ChineseWordFeatureExtractor.class);
  /**
   * 
   */
  private static final long serialVersionUID = -4327267414095852504L;
  boolean morpho;
  boolean chars;
  boolean rads;
  boolean useLength;
  boolean useFreq;
  boolean bigrams;
  boolean conjunctions;
  boolean mildConjunctions;

  public boolean turnOffWordFeatures = false;
  private IntCounter wordCounter;
  private ChineseMorphFeatureSets cmfs = null;
  private static final String featureDir = "gbfeatures";

  public void setFeatureLevel(int level) {
    morpho = false;
    chars = false;
    rads = false;
    useLength = false;
    useFreq = false;
    bigrams = false;
    conjunctions = false;
    mildConjunctions = false;

    switch (level) {
      case 3:
        bigrams = true;
        conjunctions = true;

      case 2:
        chars = true;

      case 1:
        morpho = true;
        mildConjunctions = true;
        loadFeatures();

      case 0:
        rads = true;

      case -1:
        useLength = true;
        useFreq = true;
        break;

      default:
        log.info("Feature level " + level + " is not supported in ChineseWordFeatureExtractor.");
        log.info("Using level 0");
        setFeatureLevel(0);
    }
  }

  /*
  public ChineseWordFeatureExtractor() {
    this(trees, 2);
  }
  */

  public ChineseWordFeatureExtractor(int featureLevel) {
    wordCounter = new IntCounter();
    setFeatureLevel(featureLevel);
  }

  public void train(Collection<Tree> trees) {
    train(trees, 1.0);
  }

  public void train(Collection<Tree> trees, double weight) {
    for (Tree tree : trees) {
      train(tree, weight);
    }
  }

  public void train(Tree tree, double weight) {
    train(tree.taggedYield(), weight);
  }

  public void train(List<TaggedWord> sentence, double weight) {
    for (TaggedWord word : sentence) {
      String wordString = word.word();
      wordCounter.incrementCount(wordString, weight);
    }
  }

  private void loadFeatures() {
    if (cmfs != null) return;
    cmfs = new ChineseMorphFeatureSets(featureDir);
    log.info("Total affix features: " + cmfs.getAffixFeatures().size());
  }

  private Collection<String> threshedFeatures;

  public void applyFeatureCountThreshold(Collection<String> data, int thresh) {
    IntCounter c = new IntCounter();
    for (String datum : data) {
      for (String feat : makeFeatures(datum)) {
        c.incrementCount(feat);
      }
    }
    threshedFeatures = c.keysAbove(thresh);
    log.info((c.size() - threshedFeatures.size()) + " word features removed due to thresholding.");
  }

  public Collection<String> makeFeatures(String word) {
    List<String> features = new ArrayList<>();
    if (morpho) {
      for (Map.Entry<String, Set<Character>> e : cmfs.getSingletonFeatures().entrySet()) {
        if (e.getValue().contains(word.charAt(0))) {
          features.add(e.getKey() + "-1");
        }
      }

      // Hooray for generics!!! :-)
      for (Map.Entry<String, Pair<Set<Character>, Set<Character>>> e : cmfs.getAffixFeatures().entrySet()) {
        boolean both = false;
        if (e.getValue().first().contains(word.charAt(0))) {
          features.add(e.getKey() + "-P");
          both = true;
        }
        if (e.getValue().second().contains(word.charAt(word.length() - 1))) {
          features.add(e.getKey() + "-S");
        } else {
          both = false;
        }
        if (both && mildConjunctions && !conjunctions) {
          features.add(e.getKey() + "-PS");
        }
      }

      if (conjunctions) {
        int max = features.size();
        for (int i=1; i<max; i++) {
          String s1 = features.get(i);
          for (int j=0; j<i; j++) {
            String s2 = features.get(j);
            features.add(s1 + "&&" + s2);
          }
        }
      }
    }

    if (!turnOffWordFeatures) {
      features.add(word + "-W");
    }

    if (rads) {
      features.add(RadicalMap.getRadical(word.charAt(0)) + "-FR");
      features.add(RadicalMap.getRadical(word.charAt(word.length()-1)) + "-LR");

      for (int i=0; i<word.length(); i++) {
        features.add(RadicalMap.getRadical(word.charAt(i)) + "-CR");
      }
    }

    if (chars) {
      // first and last chars
      features.add(word.charAt(0) + "-FC");
      features.add(word.charAt(word.length()-1) + "-LC");

      for (int i=0; i<word.length(); i++) {
        features.add(word.charAt(i) + "-CC");
      }

      if (bigrams && word.length() > 1) {
        features.add(word.substring(0,2) + "-FB");
        features.add(word.substring(word.length()-2) + "-LB");
        for (int i=2; i<=word.length(); i++) {
          features.add(word.substring(i-2,i) + "-CB");
        }
      }
    }

    if (useLength) {
      int lengthBin = word.length();
      if (lengthBin >= 5) {
        if (lengthBin >= 8) {
          lengthBin = 8;
        } else {
          lengthBin = 5;
        }
      }
      features.add(word.length() + "-L");
    }

    if (useFreq && !turnOffWordFeatures) {
      int freq = wordCounter.getIntCount(word);
      int freqBin;
      if (freq <= 1) freqBin = 0;
      else if (freq <= 3) freqBin = 1;
      else if (freq <= 6) freqBin = 2;
      else if (freq <= 15) freqBin = 3;
      else if (freq <= 50) freqBin = 4;
      else freqBin = 5;
      features.add(freqBin + "-FQ");
    }

    features.add("PR");

    if (threshedFeatures != null) {
      for (Iterator<String> iter = features.iterator(); iter.hasNext();) {
        String s = iter.next();
        if (!threshedFeatures.contains(s)) {
          iter.remove();
        }
      }
    }

    return features;
  }
}
