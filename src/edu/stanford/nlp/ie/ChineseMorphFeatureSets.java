package edu.stanford.nlp.ie;

import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.HashIndex;

import java.io.*;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A class for holding Chinese morphological features used for word segmentation and POS tagging.
 *
 * @author Galen Andrew
 */
public class ChineseMorphFeatureSets implements Serializable {

  private static final long serialVersionUID = -1055526945031459198L;

  private Index<String> featIndex = new HashIndex<>();
  private Map<String, Set<Character>> singletonFeatures = Generics.newHashMap();
  private Map<String, Pair<Set<Character>, Set<Character>>> affixFeatures = Generics.newHashMap();

  public Map<String, Set<Character>> getSingletonFeatures() {
    return singletonFeatures;
  }

  public Map<String, Pair<Set<Character>, Set<Character>>> getAffixFeatures() {
    return affixFeatures;
  }

  public ChineseMorphFeatureSets(String featureDir) {
    try {
      File dir = new File(featureDir);
      File[] files = dir.listFiles((dir1, name) -> name.endsWith(".gb"));
      for (File file : files) {
        getFeatures(file);
      }
    } catch (IOException e) {
      throw new RuntimeException("Error creating ChineseMaxentLexicon" + e);
    }
  }

  private enum FeatType {
    PREFIX, SUFFIX, SINGLETON
  }

  private void getFeatures(File file) throws IOException {
    BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(file), "GB18030"));
    String filename = file.getName();
    String singleFeatName = filename;
    if (singleFeatName.indexOf('.') >= 0) {
      singleFeatName = singleFeatName.substring(0, filename.lastIndexOf('.'));
    }

    FeatType featType = null;
    for (FeatType ft : FeatType.values()) {
      if (filename.contains(ft.toString().toLowerCase())) {
        featType = ft;
        singleFeatName = singleFeatName.substring(0, filename.indexOf(ft.toString().toLowerCase()));
        if (singleFeatName.endsWith("_")) {
          singleFeatName = singleFeatName.substring(0, singleFeatName.lastIndexOf('_'));
        }
        break;
      }
    }

    featIndex.add(singleFeatName);
    String singleFeatIndexString = Integer.toString(featIndex.indexOf(singleFeatName));

    Set<Character> featureSet = Generics.newHashSet();
    String line;
    Pattern typedDoubleFeatPattern = Pattern.compile("([A-Za-z]+)\\s+(.)\\s+(.)\\s*");
    Pattern typedSingleFeatPattern = Pattern.compile("([A-Za-z]+)\\s+(.)\\s*");
    Pattern singleFeatPattern = Pattern.compile("(.)(?:\\s+[0-9]+)?\\s*");
    while ((line = in.readLine()) != null) {
      if (line.length() == 0) {
        continue;
      }

      if (featType == null) {
        Matcher typedDoubleFeatMatcher = typedDoubleFeatPattern.matcher(line);
        if (typedDoubleFeatMatcher.matches()) {
          String featName = typedDoubleFeatMatcher.group(1);
          featIndex.add(featName);
          String featIndexString = Integer.toString(featIndex.indexOf(featName));
          String prefixChar = typedDoubleFeatMatcher.group(2);
          addTypedFeature(featIndexString, prefixChar.charAt(0), true);
          String suffixChar = typedDoubleFeatMatcher.group(3);
          addTypedFeature(featIndexString, suffixChar.charAt(0), false);
          continue;
        }
      }

      Matcher typedSingleFeatMatcher = typedSingleFeatPattern.matcher(line);
      if (typedSingleFeatMatcher.matches()) {
        String featName = typedSingleFeatMatcher.group(1);
        featIndex.add(featName);
        String featIndexString = Integer.toString(featIndex.indexOf(featName));
        String charString = typedSingleFeatMatcher.group(2);
        switch (featType) {
          case PREFIX:
            addTypedFeature(featIndexString, charString.charAt(0), true);
            break;

          case SUFFIX:
            addTypedFeature(featIndexString, charString.charAt(0), false);
            break;

          case SINGLETON:
            throw new RuntimeException("ERROR: typed SINGLETON feature.");
        }
        continue;
      }

      Matcher singleFeatMatcher = singleFeatPattern.matcher(line);
      if (singleFeatMatcher.matches()) {
        String charString = singleFeatMatcher.group();
        featureSet.add(charString.charAt(0));
        continue;
      }

      if (line.startsWith("prefix") || line.startsWith("suffix")) {
        if (featureSet.size() > 0) {
          Pair<Set<Character>, Set<Character>> p = affixFeatures.get(singleFeatIndexString);
          if (p == null) {
            affixFeatures.put(singleFeatIndexString, p = new Pair<>());
          }
          if (featType == FeatType.PREFIX) {
            p.setFirst(featureSet);
          } else {
            p.setSecond(featureSet);
          }
          featureSet = Generics.newHashSet();
        }
        featType = FeatType.PREFIX;
        if (line.startsWith("prefix")) {
          featType = FeatType.PREFIX;
        } else if (line.startsWith("suffix")) {
          featType = FeatType.SUFFIX;
        }
      }
    }

    if (featureSet.size() > 0) {
      if (featType == FeatType.SINGLETON) {
        singletonFeatures.put(singleFeatIndexString, featureSet);
      } else {
        Pair<Set<Character>, Set<Character>> p = affixFeatures.get(singleFeatIndexString);
        if (p == null) {
          affixFeatures.put(singleFeatIndexString, p = new Pair<>());
        }
        if (featType == FeatType.PREFIX) {
          p.setFirst(featureSet);
        } else {
          p.setSecond(featureSet);
        }
      }
    }
  }

  private void addTypedFeature(String featName, char featChar, boolean isPrefix) {
    Pair<Set<Character>, Set<Character>> p = affixFeatures.get(featName);
    if (p == null) {
      affixFeatures.put(featName, p = new Pair<>());
    }
    Set<Character> feature;
    if (isPrefix) {
      feature = p.first();
      if (feature == null) {
        p.setFirst(feature = Generics.newHashSet());
      }
    } else {
      feature = p.second();
      if (feature == null) {
        p.setSecond(feature = Generics.newHashSet());
      }
    }
    feature.add(featChar);
  }

}
