package edu.stanford.nlp.util;

import edu.stanford.nlp.international.Language;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.trees.GrammaticalRelation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * A set of utilities for parsing TSV files into CoreMaps
 *
 * @author Gabor Angeli
 */
public class TSVUtils {

  /**
   * Parse an SQL array.
   * @param array The array to parse.
   * @return The parsed array, as a list.
   */
  public static List<String> parseArray(String array) {
    char[] input = array.substring(1, array.length() - 1).replace("\\\\", "\\").toCharArray();
    List<String> output = new ArrayList<>();
    StringBuilder elem = new StringBuilder();
    boolean inQuotes = false;
    boolean escaped = false;
    for (char c : input) {
      if (escaped) {
        elem.append(c);
        escaped = false;
      } else if (c == '"') {
        inQuotes = !inQuotes;
        escaped = false;
      } else if (c == '\\') {
        escaped = true;
      } else {
        if (inQuotes) {
          elem.append(c);
        } else if (c == ',') {
          output.add(elem.toString());
          elem.setLength(0);  // This is basically .clear()
        } else {
          elem.append(c);
        }
        escaped = false;
      }
    }
    if (elem.length() > 0) {
      output.add(elem.toString());
    }
    return output;
  }


  private static final Pattern newline = Pattern.compile("\\\\n");
  private static final Pattern tab = Pattern.compile("\\\\t");

  /**
   * Parse a CoNLL formatted tree into a SemanticGraph.
   * @param conll The CoNLL tree to parse.
   * @param tokens The tokens of the sentence, to form the backing labels of the tree.
   * @return A semantic graph of the sentence, according to the given tree.
   */
  public static SemanticGraph parseTree(String conll, List<CoreLabel> tokens) {
    SemanticGraph tree = new SemanticGraph();
    if (conll == null || conll.isEmpty()) {
      return tree;
    }
    String[] treeLines = newline.split(conll);
    IndexedWord[] vertices = new IndexedWord[tokens.size() + 2];
    // Add edges
    for (String line : treeLines) {
      // Parse row
      String[] fields = tab.split(line);
      int dependentIndex = Integer.parseInt(fields[0]);
      if (vertices[dependentIndex] == null) {
        if (dependentIndex > tokens.size()) {
          // Bizarre mismatch in sizes; the malt parser seems to do this often
          return new SemanticGraph();
        }
        vertices[dependentIndex] = new IndexedWord(tokens.get(dependentIndex - 1));
      }
      IndexedWord dependent = vertices[dependentIndex];
      int governorIndex = Integer.parseInt(fields[1]);
      if (governorIndex > tokens.size()) {
        // Bizarre mismatch in sizes; the malt parser seems to do this often
        return new SemanticGraph();
      }
      if (vertices[governorIndex] == null && governorIndex > 0) {
        vertices[governorIndex] = new IndexedWord(tokens.get(governorIndex - 1));
      }
      IndexedWord governor = vertices[governorIndex];
      String relation = fields[2];

      // Process row
      if (governorIndex == 0) {
        tree.addRoot(dependent);
      } else {
        tree.addVertex(dependent);
        if (!tree.containsVertex(governor)) {
          tree.addVertex(governor);
        }
        if (!"ref".equals(relation)) {
          tree.addEdge(governor, dependent, GrammaticalRelation.valueOf(Language.English, relation), Double.NEGATIVE_INFINITY, false);
        }
      }
    }
    return tree;
  }

  /** Create an Annotation object (with a single sentence) from the given specification */
  private static Annotation parseSentence(Optional<String> docid, Optional<Integer> sentenceIndex, String gloss,
                                          Function<List<CoreLabel>,SemanticGraph> tree,
                                          Function<List<CoreLabel>,SemanticGraph> maltTree,
                                          List<String> words, List<String> lemmas, List<String> pos, List<String> ner,
                                          Optional<String> sentenceid) {
    // Error checks
    if (lemmas.size() != words.size()) {
      throw new IllegalArgumentException("Array lengths don't match: " + words.size() + " vs " + lemmas.size() + " (sentence " + sentenceid.orElse("???") +")");
    }
    if (pos.size() != words.size()) {
      throw new IllegalArgumentException("Array lengths don't match: " + words.size() + " vs " + pos.size() + " (sentence " + sentenceid.orElse("???") +")");
    }
    if (ner.size() != words.size()) {
      throw new IllegalArgumentException("Array lengths don't match: " + words.size() + " vs " + ner.size() + " (sentence " + sentenceid.orElse("???") +")");
    }

    // Create structure
    List<CoreLabel> tokens = new ArrayList<>(words.size());
    int beginChar = 0;
    for (int i = 0; i < words.size(); ++i) {
      CoreLabel token = new CoreLabel(12);
      token.setWord(words.get(i));
      token.setValue(words.get(i));
      token.setBeginPosition(beginChar);
      token.setEndPosition(beginChar + words.get(i).length());
      beginChar += words.get(i).length() + 1;
      token.setLemma(lemmas.get(i));
      token.setTag(pos.get(i));
      token.setNER(ner.get(i));
      token.set(CoreAnnotations.DocIDAnnotation.class, docid.orElse("???"));
      token.set(CoreAnnotations.SentenceIndexAnnotation.class, sentenceIndex.orElse(-1));
      token.set(CoreAnnotations.IndexAnnotation.class, i + 1);
      token.set(CoreAnnotations.TokenBeginAnnotation.class, i);
      token.set(CoreAnnotations.TokenEndAnnotation.class, i + 1);
      tokens.add(token);
    }
    gloss = gloss.replace("\\n", "\n").replace("\\t", "\t");
    CoreMap sentence = new ArrayCoreMap(16);
    sentence.set(CoreAnnotations.TokensAnnotation.class, tokens);
    SemanticGraph graph = tree.apply(tokens);
    sentence.set(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class, graph);
    sentence.set(SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class, graph);
    sentence.set(SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation.class, graph);
    SemanticGraph maltGraph = maltTree.apply(tokens);
    sentence.set(SemanticGraphCoreAnnotations.AlternativeDependenciesAnnotation.class, maltGraph);
    sentence.set(CoreAnnotations.DocIDAnnotation.class, docid.orElse("???"));
    sentence.set(CoreAnnotations.SentenceIndexAnnotation.class, sentenceIndex.orElse(-1));
    sentence.set(CoreAnnotations.TextAnnotation.class, gloss);
    sentence.set(CoreAnnotations.TokenBeginAnnotation.class, 0);
    sentence.set(CoreAnnotations.TokenEndAnnotation.class, tokens.size());
    Annotation doc = new Annotation(gloss);
    doc.set(CoreAnnotations.TokensAnnotation.class, tokens);
    doc.set(CoreAnnotations.SentencesAnnotation.class, Collections.singletonList(sentence));
    doc.set(CoreAnnotations.DocIDAnnotation.class, docid.orElse("???"));
    doc.set(CoreAnnotations.SentenceIndexAnnotation.class, sentenceIndex.orElse(-1));
    return doc;
  }

  /** Create an Annotation object (with a single sentence) from the given specification, as Postgres would output them */
  public static Annotation parseSentence(Optional<String> docid, Optional<String> sentenceIndex,
                                         String gloss, String dependencies, String maltDependencies,
                                         String words, String lemmas, String posTags, String nerTags,
                                         Optional<String> sentenceid) {
    return parseSentence(docid, sentenceIndex.map(Integer::parseInt), gloss,
        tokens -> parseTree(dependencies, tokens),
        tokens -> parseTree(maltDependencies, tokens),
        parseArray(words), parseArray(lemmas), parseArray(posTags), parseArray(nerTags), sentenceid);
  }
}
