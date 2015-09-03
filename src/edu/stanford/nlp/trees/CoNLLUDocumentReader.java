package edu.stanford.nlp.trees;

import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;

import edu.stanford.nlp.international.Language;
import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.objectbank.DelimitRegExIterator;
import edu.stanford.nlp.objectbank.IteratorFromReaderFactory;
import edu.stanford.nlp.objectbank.ObjectBank;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.trees.TypedDependency;
import edu.stanford.nlp.util.Pair;

/**
 * Reader for ConLL-U formatted dependency treebanks.
 *
 * @author Sebastian Schuster
 */
public class CoNLLUDocumentReader implements
    IteratorFromReaderFactory<SemanticGraph> {


  private IteratorFromReaderFactory<SemanticGraph> ifrf;

  public CoNLLUDocumentReader() {
    this.ifrf = DelimitRegExIterator.getFactory("\n(\\s*\n)+", new SentenceProcessor());
  }


  @Override
  public Iterator<SemanticGraph> getIterator(Reader r) {
    return ifrf.getIterator(r);
  }


  private static final Comparator<IndexedWord> byIndex = (i1, i2) -> i1.compareTo(i2);

  /* Comparator for putting multiword tokens before regular tokens.  */
  private static final Comparator<IndexedWord> byType = (i1, i2) ->
          i1.containsKey(CoreAnnotations.CoNLLUTokenSpanAnnotation.class) ? -1 :
                  i2.containsKey(CoreAnnotations.CoNLLUTokenSpanAnnotation.class) ? 1 : 0;

  private static class SentenceProcessor implements Function<String,SemanticGraph> {

    private int lineNumberCounter = 0;

    public SemanticGraph apply(String line) {
      if (line == null) return null;

      Function<String,IndexedWord> func = new WordProcessor();
      ObjectBank<IndexedWord> words = ObjectBank.getLineIterator(new StringReader(line), func);

      List<IndexedWord> wordList = new ArrayList<>(words);

      List<IndexedWord> sorted = new ArrayList<>(wordList.size());
      wordList.stream().filter(w -> w != IndexedWord.NO_WORD)
              .sorted(byIndex.thenComparing(byType))
              .forEach(w -> sorted.add(w));

      List<IndexedWord> sortedTokens = new ArrayList<>(wordList.size());
      sorted.stream()
              .filter(w -> !w.containsKey(CoreAnnotations.CoNLLUTokenSpanAnnotation.class))
              .forEach(w -> sortedTokens.add(w));

      /* Construct a semantic graph. */
      List<TypedDependency> deps = new ArrayList<>(sorted.size());

      Pair<Integer,Integer> tokenSpan = null;
      String originalToken = null;
      for (IndexedWord word : sorted) {
        lineNumberCounter++;

        if (word.containsKey(CoreAnnotations.CoNLLUTokenSpanAnnotation.class)) {
          tokenSpan = word.get(CoreAnnotations.CoNLLUTokenSpanAnnotation.class);
          originalToken = word.word();
        } else {
          /* Deal with multiword tokens. */
          if (tokenSpan != null && tokenSpan.second >= word.index()) {
            word.setOriginalText(originalToken);
            word.set(CoreAnnotations.CoNLLUTokenSpanAnnotation.class, tokenSpan);
          } else {
            tokenSpan = null;
            originalToken = null;
          }
          GrammaticalRelation reln = GrammaticalRelation.valueOf(Language.UniversalEnglish,
                  word.get(CoreAnnotations.CoNLLDepTypeAnnotation.class));
          int govIdx = word.get(CoreAnnotations.CoNLLDepParentIndexAnnotation.class);
          IndexedWord gov;
          if (govIdx == 0) {
            gov = new IndexedWord(word.docID(), word.sentIndex(), 0);
            gov.setValue("ROOT");
            if (word.get(CoreAnnotations.CoNLLDepTypeAnnotation.class).equals("root")) {
              reln = GrammaticalRelation.ROOT;
            }
          } else {
            gov = sortedTokens.get(govIdx - 1);
          }
          TypedDependency dep = new TypedDependency(reln, gov, word);
          word.set(CoreAnnotations.LineNumberAnnotation.class, lineNumberCounter);
          deps.add(dep);

          HashMap<Integer,String> extraDeps = word.get(CoreAnnotations.CoNLLUSecondaryDepsAnnotation.class);
          for (Integer extraGovIdx : extraDeps.keySet()) {
            GrammaticalRelation extraReln = GrammaticalRelation.valueOf(Language.UniversalEnglish, extraDeps.get(extraGovIdx));
            IndexedWord extraGov =  sortedTokens.get(extraGovIdx - 1);
            TypedDependency extraDep = new TypedDependency(extraReln, extraGov, word);
            extraDep.setExtra();
            deps.add(extraDep);
          }
        }
      }
      lineNumberCounter++;

      return new SemanticGraph(deps);
    }
  }

  private static class WordProcessor implements Function<String,IndexedWord> {
    public IndexedWord apply(String line) {

      /* Comments.
       * TODO[sebschu]: Save them somewhere such that they can be output again.
       */
      if (line.startsWith("#")) {
        return IndexedWord.NO_WORD;
      }

      String[] bits = line.split("\\s+");

      IndexedWord word = new IndexedWord();
      word.set(CoreAnnotations.TextAnnotation.class, bits[1]);

      /* Check if it is a multiword token. */
      if (bits[0].contains("-")) {
        String[] span = bits[0].split("-");
        Integer start = Integer.parseInt(span[0]);
        Integer end = Integer.parseInt(span[1]);
        word.set(CoreAnnotations.CoNLLUTokenSpanAnnotation.class, new Pair<>(start, end));
        word.set(CoreAnnotations.IndexAnnotation.class, start);
      } else {
        word.set(CoreAnnotations.IndexAnnotation.class, Integer.parseInt(bits[0]));
        word.set(CoreAnnotations.LemmaAnnotation.class, bits[2]);
        word.set(CoreAnnotations.CoarseTagAnnotation.class, bits[3]);
        word.set(CoreAnnotations.PartOfSpeechAnnotation.class, bits[4]);

        word.set(CoreAnnotations.CoNLLDepParentIndexAnnotation.class, Integer.parseInt(bits[6]));
        word.set(CoreAnnotations.CoNLLDepTypeAnnotation.class, bits[7]);
        word.set(CoreAnnotations.CoNLLUMisc.class, bits[9]);

        word.setIndex(Integer.parseInt(bits[0]));
        word.setValue(bits[1]);

        /* Parse features. */
        HashMap<String, String> features = parseFeatures(bits[5]);
        word.set(CoreAnnotations.CoNLLUFeats.class, features);

        /* Parse extra dependencies. */
        HashMap<Integer,String> extraDeps = parseExtraDeps(bits[8]);
        word.set(CoreAnnotations.CoNLLUSecondaryDepsAnnotation.class, extraDeps);
      }

    return word;
    }
  }


  /**
   * Parses the value of the feature column in a CoNLL-U file
   * and returns them in a HashMap with the feature names as keys
   * and the feature values as values.
   *
   * @param featureString
   * @return A HashMap<String,String> with the feature values.
   */
  public static HashMap<String,String> parseFeatures(String featureString) {
    HashMap<String, String> features = new HashMap<String, String>();
    if (! featureString.equals("_")) {
      String[] featValPairs = featureString.split("\\|");
      for (String p : featValPairs) {
        String[] featValPair = p.split("=");
        features.put(featValPair[0], featValPair[1]);
      }
    }
    return features;
  }

  /**
   * Converts a feature HashMap to a feature string to be used
   * in a CoNLL-U file.
   *
   * @return The feature string.
   */
  public static String toFeatureString(HashMap<String,String> features) {
    StringBuffer sb = new StringBuffer();
    boolean first = true;
    List<String> sortedKeys = new ArrayList<String>(features.keySet());
    Collections.sort(sortedKeys, new FeatureNameComparator());
    for (String key : sortedKeys) {
      if ( ! first) {
        sb.append("|");
      } else {
        first = false;
      }

      sb.append(key)
        .append("=")
        .append(features.get(key));

    }

    /* Empty feature list. */
    if (first) {
      sb.append("_");
    }

    return sb.toString();
  }

  /**
   * Parses the value of the extra dependencies column in a CoNLL-U file
   * and returns them in a HashMap with the governor indices as keys
   * and the relation names as values.
   *
   * @param extraDepsString
   * @return A HashMap<Integer,String> with the additional dependencies.
   */
  public static HashMap<Integer,String> parseExtraDeps(String extraDepsString) {
    HashMap<Integer,String> extraDeps = new HashMap<>();
    if ( ! extraDepsString.equals("_")) {
      String[] extraDepParts = extraDepsString.split("\\|");
      for (String extraDepString : extraDepParts) {
        int sepPos = extraDepString.lastIndexOf(":");
        String reln = extraDepString.substring(sepPos + 1);
        Integer gov = Integer.parseInt(extraDepString.substring(0, sepPos));
        extraDeps.put(gov, reln);
      }
    }
    return extraDeps;
  }

  /**
   * Converts an extra dependencies hash map to a string to be used
   * in a CoNLL-U file.
   *
   * @param extraDeps
   * @return The extra dependencies string.
   */
  public static String toExtraDepsString(HashMap<Integer,String> extraDeps) {
    StringBuffer sb = new StringBuffer();
    boolean first = true;
    List<Integer> sortedKeys = new ArrayList<>(extraDeps.keySet());
    Collections.sort(sortedKeys);
    for (Integer key : sortedKeys) {
      if ( ! first) {
        sb.append("|");
      } else {
        first = false;
      }

      sb.append(key)
              .append(":")
              .append(extraDeps.get(key));
    }

    /* Empty feature list. */
    if (first) {
      sb.append("_");
    }
    return sb.toString();
  }


  public static class FeatureNameComparator implements Comparator<String> {

    @Override
    public int compare(String featureName1, String featureName2) {
      return featureName1.toLowerCase().compareTo(featureName2.toLowerCase());
    }
  }
}
