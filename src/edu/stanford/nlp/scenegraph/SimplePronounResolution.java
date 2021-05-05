package edu.stanford.nlp.scenegraph;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import edu.stanford.nlp.dcoref.Dictionaries;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.util.CoreMap;


/**
 * Performs a simple, rule-based intrasential pronoun resolution,
 * roughly following the first three rules of the Hobbs algorithm.
 *
 * It assumes that one sentence is the entire document, e.g. in
 * search queries or image captions. Only resolves anaphora.
 *
 * @author Sebastian Schuster
 *
 */


public class SimplePronounResolution extends AbstractPronounResolver {

  private StanfordCoreNLP pipeline;
  private Dictionaries dict;


  public SimplePronounResolution() {
    dict = new Dictionaries();
  }

  private void loadPipeline() {
    Properties props  = new Properties();
    props.setProperty("annotators", "lemma,parse");
    props.setProperty("parse.model", SceneGraphImagePCFGParser.PCFG_MODEL);
    props.setProperty("enforceRequirements", "false");
    pipeline = new StanfordCoreNLP(props);
  }


  public static void main(String args[]) throws IOException {
    SimplePronounResolution resolver = new SimplePronounResolution();
    resolver.run(args);
  }

  /**
   * Performs a left-to-right breadth-first search
   * in a SemanticGraph starting at start. Returns
   * the first noun it finds. In case singular is set to
   * true it returns only singular nouns.
   *
   * @param sg A SemanticGraph.
   * @param start Start node.
   * @param singular Whether only singular noun phrases should be matched.
   * @return
   */
  private IndexedWord bfsNPSearch(SemanticGraph sg, IndexedWord start, boolean singular) {
    List<IndexedWord> queue = new LinkedList<IndexedWord>();
    queue.add(start);
    while( ! queue.isEmpty()) {
      IndexedWord current = queue.remove(0);
      if (current.tag().matches("^NNP?$") || ( ! singular && current.tag().matches("^NNP?S?$"))) {
        return current;
      }
      for (IndexedWord child : sg.getChildList(current)) {
        queue.add(child);
      }
    }

    IndexedWord gov = sg.getParent(start);

    return gov == null ? null : bfsNPSearch(sg, gov, singular);

  }


  @Override
  protected HashMap<Integer, Integer> resolvePronouns(SemanticGraph sg) {

    HashMap<Integer, Integer> pronPairs = new HashMap<Integer, Integer>(1);

    for (IndexedWord token : sg.vertexListSorted()) {
      if (token.index() > 1 && token.tag().startsWith("PRP")) {

        boolean isSingular = dict.singularPronouns.contains(token.word());

        IndexedWord iw = sg.getNodeByIndexSafe(token.index());
        if (iw == null) {
          continue;
        }

        IndexedWord gov = sg.getParent(iw);

        if (gov == null) {
          continue;
        }

        IndexedWord antecedent = this.bfsNPSearch(sg, sg.getFirstRoot(), isSingular);
        if (antecedent != null && antecedent.index() < iw.index()) {
          pronPairs.put(token.index(), antecedent.index());
        }

      }
    }
    return pronPairs;
  }

  @Override
  protected HashMap<Integer, Integer> resolvePronouns(List<CoreLabel> tokens) {

    if (pipeline == null) {
      loadPipeline();
    }

    CoreMap sentence = new CoreLabel();
    sentence.set(CoreAnnotations.TokensAnnotation.class, tokens);
    sentence.set(CoreAnnotations.SentenceIndexAnnotation.class, 1);

    List<CoreMap> sentences = new ArrayList<CoreMap>(1);
    sentences.add(sentence);

    Annotation annotation = new Annotation(sentences);

    pipeline.annotate(annotation);

    CoreMap annotatedSentence = annotation.get(CoreAnnotations.SentencesAnnotation.class).get(0);

    SemanticGraph sg = annotatedSentence.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);

    return this.resolvePronouns(sg);
  }
}
