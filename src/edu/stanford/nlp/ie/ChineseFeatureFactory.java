package edu.stanford.nlp.ie;

import edu.stanford.nlp.trees.international.pennchinese.RadicalMap;
import edu.stanford.nlp.util.PaddedList;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations.ChineseCharAnnotation;
import edu.stanford.nlp.sequences.SeqClassifierFlags;
import edu.stanford.nlp.sequences.FeatureFactory;
import edu.stanford.nlp.sequences.Clique;

import java.util.*;

/**
 * A Feature Factory for Chinese words
 * @author Galen Andrew
 */
public class ChineseFeatureFactory<IN extends CoreLabel> extends FeatureFactory<IN> { // implements Serializable

  private static final long serialVersionUID = 4559182480629798157L;

  @Override
  public void init(SeqClassifierFlags flags) {
    super.init(flags);
    if (flags.morphFeatureFile != null) {
      cmfs = new ChineseMorphFeatureSets(flags.morphFeatureFile);
    }
  }


  /**
   * Extracts all the features from the input data at a certain index.
   *
   * @param cInfo The complete data set as a List of WordInfo
   * @param loc  The index at which to extract features.
   */
  @Override
  public Collection<String> getCliqueFeatures(PaddedList<IN> cInfo, int loc, Clique clique) {
    Collection<String> features = new HashSet<String>();

    if (clique == cliqueC) {
      addAllInterningAndSuffixing(features, featuresC(cInfo, loc), "C");
    } else if (clique == cliqueCpC) {
      addAllInterningAndSuffixing(features, featuresCpC(cInfo, loc), "CpC");
    } else if (clique == cliqueCpCp2C) {
      addAllInterningAndSuffixing(features, featuresCpCp2C(cInfo, loc), "CpCp2C");
    } else if (clique == cliqueCpCp2Cp3C) {
      addAllInterningAndSuffixing(features, featuresCpCp2Cp3C(cInfo, loc), "CpCp2Cp3C");
    } else if (clique == cliqueCpCp2Cp3Cp4C) {
      addAllInterningAndSuffixing(features, featuresCpCp2Cp3Cp4C(cInfo, loc), "CpCp2Cp3Cp4C");
    }

    return features;
  }


  private static char getCharFromWordInfo(CoreLabel w) {
    String charString = w.get(ChineseCharAnnotation.class);
    if (charString == null || charString.length() == 0) {
      return 0;
    } else {
      //      if (charString.length() != 1) {
      //        pw.println("long char: " + charString);
      //      }
      return charString.charAt(0);
    }
  }

  private Collection<String> getCharFeatures(PaddedList<IN> cInfo, int loc, boolean bigram) {
    Collection<String> features = new ArrayList<String>();

    char[] c = new char[flags.charHalfWindow+1];
    char[] pc = new char[flags.charHalfWindow+1];
    char[] r = new char[flags.charHalfWindow+1];
    char[] pr = new char[flags.charHalfWindow+1];
    for (int i = 1; i <= flags.charHalfWindow; i++) {
      CoreLabel wi = cInfo.get(loc + i - 1);
      CoreLabel wmi = cInfo.get(loc - i);
      c[i] = getCharFromWordInfo(wi);
      pc[i] = getCharFromWordInfo(wmi);
      r[i] = RadicalMap.getRadical(c[i]);
      pr[i] = RadicalMap.getRadical(pc[i]);
    }

    for (boolean rad = false;;) {
      for (int pos = -flags.charHalfWindow; pos <= flags.charHalfWindow; pos++) {
        if (pos == 0) {
          continue;
        }
        char ch, ra;
        if (pos < 0) {
          ch = pc[-pos];
          ra = pr[-pos];
        } else {
          ch = c[pos];
          ra = r[pos];
        }
        StringBuilder featBuffer = new StringBuilder();
        if (rad) {
          featBuffer.append(ra);
          featBuffer.append('R');
        } else {
          featBuffer.append(ch);
          featBuffer.append('C');
        }
        featBuffer.append(pos);
        features.add(featBuffer.toString());
      }
      if (flags.useRadical && !rad) {
        rad = true;
      } else {
        break;
      }
    }

    if (bigram) {
      short radBits = 0;
      do {
        for (int pos1 = -flags.charHalfWindow; pos1 < flags.charHalfWindow; pos1++) {
          if (pos1 == 0) {
            continue;
          }
          int pos2 = (pos1 == -1) ? 1 : pos1 + 1;
          StringBuilder featBuffer = new StringBuilder();

          if ((radBits & 1) > 0) {
              if (pos1 < 0) {
                featBuffer.append(pr[-pos1]);
              } else {
                featBuffer.append(r[pos1]);
              }
              featBuffer.append('R');
            } else {
              if (pos1 < 0) {
                featBuffer.append(pc[-pos1]);
              } else {
                featBuffer.append(c[pos1]);
              }
              featBuffer.append('C');
          }
          featBuffer.append(pos1);

          if ((radBits & 2) > 0) {
              if (pos2 < 0) {
                featBuffer.append(pr[-pos2]);
              } else {
                featBuffer.append(r[pos2]);
              }
              featBuffer.append('R');
            } else {
              if (pos2 < 0) {
                featBuffer.append(pc[-pos2]);
              } else {
                featBuffer.append(c[pos2]);
              }
              featBuffer.append('C');
            }
          featBuffer.append(pos2);

          features.add(featBuffer.toString());
        }
        radBits++;
      } while (flags.useRadical && radBits < 4);
    }

    features.add("PRIOR");

    return features;
  }

  ChineseMorphFeatureSets cmfs = null;

  private void addSingletonFeatures(Collection<String> features, PaddedList<IN> cInfo, int loc) {
    if (cmfs != null) {
      char c = getCharFromWordInfo(cInfo.get(loc));
      for (Map.Entry<String, Set<Character>> e : cmfs.getSingletonFeatures().entrySet()) {
        if (e.getValue().contains(c)) {
          features.add(e.getKey() + "-Sin");
        }
      }
    }
  }

  private void addAffixFeatures(Collection<String> features, PaddedList<IN> cInfo, int loc1, int loc2) {
    if (cmfs != null) {

      char c1 = getCharFromWordInfo(cInfo.get(loc1));
      char c2 = getCharFromWordInfo(cInfo.get(loc2));
      for (Map.Entry<String, Pair<Set<Character>, Set<Character>>> e : cmfs.getAffixFeatures().entrySet()) {
        boolean both = false;
        if (e.getValue().first().contains(c1)) {
          features.add(e.getKey() + "-Pre");
          both = true;
        }
        if (e.getValue().second().contains(c2)) {
          features.add(e.getKey() + "-Suf");
        } else {
          both = false;
        }
        if (both) {
          features.add(e.getKey() + "-PreAndSuf");
        }
      }
    }
  }

  // adds conjunctions of the form
  // prevChar is in morph-class A && currentChar is in morph-class B
  private void addReverseAffixFeatures(Collection<String> features, PaddedList<IN> cInfo, int loc) {
    if (cmfs != null && flags.useReverseAffix) {
      char cp = getCharFromWordInfo(cInfo.get(loc-1));
      char c = getCharFromWordInfo(cInfo.get(loc));
      List<String> cpFeat = new ArrayList<String>();
      List<String> cFeat = new ArrayList<String>();
      for (Map.Entry<String, Pair<Set<Character>, Set<Character>>> e : cmfs.getAffixFeatures().entrySet()) {
        if (e.getValue().second().contains(cp)) {
          cpFeat.add(e.getKey() + "-Suf");
        }
        if (e.getValue().first().contains(c)) {
          cFeat.add(e.getKey() + "-Pre");
        }
      }
      for (String sCp : cpFeat) {
        for (String sC : cFeat) {
          features.add(sCp + '&' + sC);
        }
      }
    }
  }

  protected Collection<String> featuresC(PaddedList<IN> cInfo, int loc) {
    Collection<String> feat = getCharFeatures(cInfo, loc, true);
    addSingletonFeatures(feat, cInfo, loc);
    addReverseAffixFeatures(feat, cInfo, loc);
    if (flags.useNumberFeature && loc > 0) {
      String c = cInfo.get(loc).get(ChineseCharAnnotation.class);
      String p = cInfo.get(loc-1).get(ChineseCharAnnotation.class);
      if (c != null && p != null && c.length() == 1 && p.length() == 1) {
        char c1 = c.charAt(0);
        char p1 = p.charAt(0);
        if (c1 >= '0' && c1 <= '9' && p1 >= '0' && p1 <= '9') {
          feat.add("P#_C#");
        }
      }
    }
    return feat;
  }


  protected Collection<String> featuresCpC(PaddedList<IN> cInfo, int loc) {
    Collection<String> feat =  getCharFeatures(cInfo, loc, flags.useBigramInTwoClique);
    if (flags.useBigramInTwoClique) {
      addReverseAffixFeatures(feat, cInfo, loc-1);
      addReverseAffixFeatures(feat, cInfo, loc);
    }
    addAffixFeatures(feat, cInfo, loc-1, loc);
    return feat;
  }

  protected Collection<String> featuresCpCp2C(PaddedList<IN> cInfo, int loc) {
    Collection<String> feat =  getCharFeatures(cInfo, loc - 1, false);
    addAffixFeatures(feat, cInfo, loc-1, loc);
    return feat;
  }

  protected Collection<String> featuresCpCp2Cp3C(PaddedList<IN> cInfo, int loc) {
    Collection<String> feat =  getCharFeatures(cInfo, loc - 2, false);
    addAffixFeatures(feat, cInfo, loc-2, loc);
    return feat;
  }

  protected Collection<String> featuresCpCp2Cp3Cp4C(PaddedList<IN> cInfo, int loc) {
    Collection<String> feat =  getCharFeatures(cInfo, loc - 2, false);
    addAffixFeatures(feat, cInfo, loc-2, loc);
    return feat;
  }
}
