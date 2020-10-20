package edu.stanford.nlp.parser.eval; 
import edu.stanford.nlp.util.logging.Redwood;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.stanford.nlp.international.Language;
import edu.stanford.nlp.international.arabic.ArabicMorphoFeatureSpecification;
import edu.stanford.nlp.international.french.FrenchMorphoFeatureSpecification;
import edu.stanford.nlp.international.morph.MorphoFeatureSpecification;
import edu.stanford.nlp.international.morph.MorphoFeatureSpecification.MorphoFeatureType;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.parser.lexparser.TreebankLangParserParams;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.TwoDimensionalIntCounter;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.Treebank;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Pair;

/**
 * Computes gross statistics for morphological annotations in a treebank.
 *
 * @author Spence Green
 *
 */
public class TreebankFactoredLexiconStats  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(TreebankFactoredLexiconStats.class);


//  private static String stripTag(String tag) {
//    if (tag.startsWith("DT")) {
//      String newTag = tag.substring(2, tag.length());
//      return newTag.length() > 0 ? newTag : tag;
//    }
//    return tag;
//  }

  /**
   * @param args
   */
  public static void main(String[] args) {
    if (args.length != 3) {
      System.err.printf("Usage: java %s language filename features%n", TreebankFactoredLexiconStats.class.getName());
      System.exit(-1);
    }

    Language language = Language.valueOf(args[0]);
    TreebankLangParserParams tlpp = language.params;
    if (language.equals(Language.Arabic)) {
      String[] options = {"-arabicFactored"};
      tlpp.setOptionFlag(options, 0);
    } else {
      String[] options = {"-frenchFactored"};
      tlpp.setOptionFlag(options, 0);
    }
    Treebank tb = tlpp.diskTreebank();
    tb.loadPath(args[1]);

    MorphoFeatureSpecification morphoSpec = language.equals(Language.Arabic) ?
        new ArabicMorphoFeatureSpecification() : new FrenchMorphoFeatureSpecification();

    String[] features = args[2].trim().split(",");
    for (String feature : features) {
      morphoSpec.activate(MorphoFeatureType.valueOf(feature));
    }

    // Counters
    Counter<String> wordTagCounter = new ClassicCounter<>(30000);
    Counter<String> morphTagCounter = new ClassicCounter<>(500);
//    Counter<String> signatureTagCounter = new ClassicCounter<String>();
    Counter<String> morphCounter = new ClassicCounter<>(500);
    Counter<String> wordCounter = new ClassicCounter<>(30000);
    Counter<String> tagCounter = new ClassicCounter<>(300);

    Counter<String> lemmaCounter = new ClassicCounter<>(25000);
    Counter<String> lemmaTagCounter = new ClassicCounter<>(25000);

    Counter<String> richTagCounter = new ClassicCounter<>(1000);

    Counter<String> reducedTagCounter = new ClassicCounter<>(500);

    Counter<String> reducedTagLemmaCounter = new ClassicCounter<>(500);

    Map<String,Set<String>> wordLemmaMap = Generics.newHashMap();

    TwoDimensionalIntCounter<String,String> lemmaReducedTagCounter = new TwoDimensionalIntCounter<>(30000);
    TwoDimensionalIntCounter<String,String> reducedTagTagCounter = new TwoDimensionalIntCounter<>(500);
    TwoDimensionalIntCounter<String,String> tagReducedTagCounter = new TwoDimensionalIntCounter<>(300);

    int numTrees = 0;
    for (Tree tree : tb) {
      for (Tree subTree : tree) {
        if (!subTree.isLeaf()) {
          tlpp.transformTree(subTree, tree);
        }
      }
      List<Label> pretermList = tree.preTerminalYield();
      List<Label> yield = tree.yield();
      assert yield.size() == pretermList.size();

      int yieldLen = yield.size();
      for (int i = 0; i < yieldLen; ++i) {
        String tag = pretermList.get(i).value();

        String word = yield.get(i).value();
        String morph = ((CoreLabel) yield.get(i)).originalText();

        // Note: if there is no lemma, then we use the surface form.
        Pair<String,String> lemmaTag = MorphoFeatureSpecification.splitMorphString(word, morph);
        String lemma = lemmaTag.first();
        String richTag = lemmaTag.second();

        // WSGDEBUG
        if (tag.contains("MW")) lemma += "-MWE";

        lemmaCounter.incrementCount(lemma);
        lemmaTagCounter.incrementCount(lemma + tag);

        richTagCounter.incrementCount(richTag);

        String reducedTag = morphoSpec.strToFeatures(richTag).toString();
        reducedTagCounter.incrementCount(reducedTag);

        reducedTagLemmaCounter.incrementCount(reducedTag + lemma);

        wordTagCounter.incrementCount(word + tag);
        morphTagCounter.incrementCount(morph + tag);
        morphCounter.incrementCount(morph);
        wordCounter.incrementCount(word);
        tagCounter.incrementCount(tag);

        reducedTag = reducedTag.equals("") ? "NONE" : reducedTag;
        if (wordLemmaMap.containsKey(word)) {
          wordLemmaMap.get(word).add(lemma);
        } else {
          Set<String> lemmas = Generics.newHashSet(1);
          wordLemmaMap.put(word, lemmas);
        }
        lemmaReducedTagCounter.incrementCount(lemma, reducedTag);
        reducedTagTagCounter.incrementCount(lemma+reducedTag, tag);
        tagReducedTagCounter.incrementCount(tag, reducedTag);
      }
      ++numTrees;
    }

    // Barf...
    System.out.println("Language: " + language.toString());
    System.out.printf("#trees:\t%d%n", numTrees);
    System.out.printf("#tokens:\t%d%n", (int) wordCounter.totalCount());
    System.out.printf("#words:\t%d%n", wordCounter.keySet().size());
    System.out.printf("#tags:\t%d%n", tagCounter.keySet().size());
    System.out.printf("#wordTagPairs:\t%d%n", wordTagCounter.keySet().size());
    System.out.printf("#lemmas:\t%d%n", lemmaCounter.keySet().size());
    System.out.printf("#lemmaTagPairs:\t%d%n", lemmaTagCounter.keySet().size());
    System.out.printf("#feattags:\t%d%n", reducedTagCounter.keySet().size());
    System.out.printf("#feattag+lemmas:\t%d%n", reducedTagLemmaCounter.keySet().size());
    System.out.printf("#richtags:\t%d%n", richTagCounter.keySet().size());
    System.out.printf("#richtag+lemma:\t%d%n", morphCounter.keySet().size());
    System.out.printf("#richtag+lemmaTagPairs:\t%d%n", morphTagCounter.keySet().size());

    // Extra
    System.out.println("==================");
    StringBuilder sbNoLemma = new StringBuilder();
    StringBuilder sbMultLemmas = new StringBuilder();
    for (Map.Entry<String, Set<String>> wordLemmas : wordLemmaMap.entrySet()) {
      String word = wordLemmas.getKey();
      Set<String> lemmas = wordLemmas.getValue();
      if (lemmas.size() == 0) {
        sbNoLemma.append("NO LEMMAS FOR WORD: " + word + "\n");
        continue;
      }
      if (lemmas.size() > 1) {
        sbMultLemmas.append("MULTIPLE LEMMAS: " + word + " " + setToString(lemmas) + "\n");
        continue;
      }
      String lemma = lemmas.iterator().next();
      Set<String> reducedTags = lemmaReducedTagCounter.getCounter(lemma).keySet();
      if (reducedTags.size() > 1) {
        System.out.printf("%s --> %s%n", word, lemma);
        for (String reducedTag : reducedTags) {
          int count = lemmaReducedTagCounter.getCount(lemma, reducedTag);
          String posTags = setToString(reducedTagTagCounter.getCounter(lemma+reducedTag).keySet());
          System.out.printf("\t%s\t%d\t%s%n", reducedTag, count, posTags);
        }
        System.out.println();
      }
    }
    System.out.println("==================");
    System.out.println(sbNoLemma.toString());
    System.out.println(sbMultLemmas.toString());
    System.out.println("==================");
    List<String> tags = new ArrayList<>(tagReducedTagCounter.firstKeySet());
    Collections.sort(tags);
    for (String tag : tags) {
      System.out.println(tag);
      Set<String> reducedTags = tagReducedTagCounter.getCounter(tag).keySet();
      for (String reducedTag : reducedTags) {
        int count = tagReducedTagCounter.getCount(tag, reducedTag);
//        reducedTag = reducedTag.equals("") ? "NONE" : reducedTag;
        System.out.printf("\t%s\t%d%n", reducedTag, count);
      }
      System.out.println();
    }
    System.out.println("==================");
  }

  private static String setToString(Set<String> set) {
    StringBuilder sb = new StringBuilder();
    sb.append("[");
    for (String string : set) {
      sb.append(string).append(" ");
    }
    sb.append("]");
    return sb.toString();
  }
}
