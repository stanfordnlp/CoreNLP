package edu.stanford.nlp.scenegraph;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import edu.stanford.nlp.dcoref.CorefChain;
import edu.stanford.nlp.dcoref.CorefChain.CorefMention;
import edu.stanford.nlp.dcoref.CorefCoreAnnotations;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.util.CoreMap;

/**
 * Pronoun resolver which uses the CoreNLP deterministic
 * coreference resolution system.
 *
 * @author Sebastian Schuster
 */

public class DcorefPronounResolver extends AbstractPronounResolver {

  private StanfordCoreNLP pipeline;

  public DcorefPronounResolver() {
    Properties props  = new Properties();
    props.setProperty("annotators", "parse,ner,lemma,dcoref");
    props.setProperty("enforceRequirements", "false");
    pipeline = new StanfordCoreNLP(props);
  }

  @Override
  protected HashMap<Integer, Integer> resolvePronouns(SemanticGraph sg) {
    throw new RuntimeException("Method not implemented!");
  }


  @Override
  protected HashMap<Integer, Integer> resolvePronouns(List<CoreLabel> tokens) {

    HashMap<Integer, Integer> pronPairs = new HashMap<Integer,Integer>(1);

    CoreMap sentence = new CoreLabel();
    sentence.set(CoreAnnotations.TokensAnnotation.class, tokens);
    sentence.set(CoreAnnotations.SentenceIndexAnnotation.class, 1);

    List<CoreMap> sentences = new ArrayList<CoreMap>(1);
    sentences.add(sentence);

    Annotation annotation = new Annotation(sentences);

    pipeline.annotate(annotation);

    Map<Integer, CorefChain> corefChains =  annotation.get(CorefCoreAnnotations.CorefChainAnnotation.class);
    for (CorefChain chain : corefChains.values()) {
      CoreLabel firstRef = null;
      for (CorefMention m : chain.getMentionsInTextualOrder()) {
        CoreLabel lbl = tokens.get(m.headIndex - 1);
        if (lbl.tag().startsWith("PRP") && firstRef != null) {
          pronPairs.put(lbl.index(), firstRef.index());
        } else if ( ! lbl.tag().startsWith("PRP") && firstRef == null) {
          firstRef = lbl;
        }
      }
    }
    return pronPairs;
  }

  public static void main(String[] args) throws IOException {
    DcorefPronounResolver resolver = new DcorefPronounResolver();
    resolver.run(args);
  }

}
