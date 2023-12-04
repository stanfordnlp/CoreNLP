package edu.stanford.nlp.semgraph.semgrex.ssurgeon;

import java.util.Objects;

import edu.stanford.nlp.international.Language;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.process.Morphology;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.semgrex.SemgrexMatcher;

/**
 * Add the output of the English lemmatizer to the word in question.
 * Currently this only supports English!  You can add a known lemma
 * for a different language by using EditNode and setting the lemma
 * attribute
 *
 * @author John Bauer
 */
public class Lemmatize extends SsurgeonEdit {
  public static final String LABEL = "lemmatize";

  final String nodeName;
  final Morphology morphology;
  final Language language;

  public Lemmatize(String nodeName, Language language) {
    if (nodeName == null) {
      throw new SsurgeonParseException("Cannot make a Lemmatize with no nodeName");
    }
    this.nodeName = nodeName;

    if (language == Language.UniversalEnglish || language == Language.English) {
      this.language = Language.English;
    } else if (language == Language.Unknown) {
      // log something here?
      this.language = Language.English;
    } else {
      throw new SsurgeonParseException("Lemmatizing " + language + " is not supported");
    }

    this.morphology = new Morphology();
  }

  @Override
  public String toEditString() {
    StringBuilder buf = new StringBuilder();
    buf.append(LABEL);  buf.append("\t");
    buf.append(Ssurgeon.NODENAME_ARG);buf.append(" ");
    buf.append(nodeName);
    return buf.toString();
  }

  public boolean evaluate(SemanticGraph sg, SemgrexMatcher sm) {
    IndexedWord word = sm.getNode(nodeName);
    if (word == null)
      return false;

    String oldLemma = word.lemma();
    morphology.stem(word.backingLabel(), CoreAnnotations.LemmaAnnotation.class);
    String newLemma = word.lemma();
    boolean changed = !Objects.equals(oldLemma, newLemma);
    return changed;
  }
}
