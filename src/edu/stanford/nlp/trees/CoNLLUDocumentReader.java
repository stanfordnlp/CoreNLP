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
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.objectbank.DelimitRegExIterator;
import edu.stanford.nlp.objectbank.IteratorFromReaderFactory;
import edu.stanford.nlp.objectbank.ObjectBank;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.trees.TypedDependency;

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

  private static class SentenceProcessor implements Function<String,SemanticGraph> {
    public SemanticGraph apply(String line) {
      if (line == null) return null;
      Function<String,IndexedWord> func = new WordProcessor();
      ObjectBank<IndexedWord> words = ObjectBank.getLineIterator(new StringReader(line), func);
      List<IndexedWord> sorted = new ArrayList<IndexedWord>(words);
      Collections.sort(sorted);


      /* Construct a semantic graph. */
      List<TypedDependency> deps = new ArrayList<TypedDependency>(sorted.size());
      for (IndexedWord word : sorted) {
        GrammaticalRelation reln = GrammaticalRelation.valueOf(Language.UniversalEnglish, word.get(CoreAnnotations.CoNLLDepTypeAnnotation.class));
        int govIdx = word.get(CoreAnnotations.CoNLLDepParentIndexAnnotation.class);
        IndexedWord gov;
        if (govIdx == 0) {
          gov = new IndexedWord(word.docID(), word.sentIndex(), 0);
          gov.setValue("ROOT");
          if (word.get(CoreAnnotations.CoNLLDepTypeAnnotation.class).equals("root")) {
            reln = GrammaticalRelation.ROOT;
          }
        } else {
          gov = sorted.get(govIdx - 1);
        }
        TypedDependency dep = new TypedDependency(reln, gov, word);
        deps.add(dep);
      }

      return new SemanticGraph(deps);
    }
  }

  private static class WordProcessor implements Function<String,IndexedWord> {
    public IndexedWord apply(String line) {
      String[] bits = line.split("\\s+");
      IndexedWord word = new IndexedWord();
      word.set(CoreAnnotations.IndexAnnotation.class, Integer.parseInt(bits[0]));
      word.set(CoreAnnotations.TextAnnotation.class, bits[1]);
      word.set(CoreAnnotations.LemmaAnnotation.class, bits[2]);
      word.set(CoreAnnotations.CoarseTagAnnotation.class, bits[3]);
      word.set(CoreAnnotations.PartOfSpeechAnnotation.class, bits[4]);

      word.set(CoreAnnotations.CoNLLDepParentIndexAnnotation.class, Integer.parseInt(bits[6]));
      word.set(CoreAnnotations.CoNLLDepTypeAnnotation.class, bits[7]);
      word.set(CoreAnnotations.CoNLLUSecondaryDepsAnnotation.class, bits[8]);
      word.set(CoreAnnotations.CoNLLUMisc.class, bits[9]);

      word.setIndex(Integer.parseInt(bits[0]));
      word.setValue(bits[1]);

      /* Parse features. */
      HashMap<String, String> features = parseFeatures(bits[5]);

      word.set(CoreAnnotations.CoNLLUFeats.class, features);


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

  public static class FeatureNameComparator implements Comparator<String> {

    @Override
    public int compare(String featureName1, String featureName2) {
      return featureName1.toLowerCase().compareTo(featureName2.toLowerCase());
    }
  }
}
