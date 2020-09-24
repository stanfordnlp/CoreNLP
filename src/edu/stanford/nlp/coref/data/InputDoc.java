package edu.stanford.nlp.coref.data;

import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.stanford.nlp.coref.docreader.CoNLLDocumentReader.CoNLLDocument;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.util.Triple;

/**
 * An input document read from a input source (CoNLL, ACE, MUC, or raw text).
 * Stores Annotation, gold info (optional) and additional document information (optional).
 * Used in coreference systems
 *
 * @author heeyoung
 */
public class InputDoc {

  public Annotation annotation;

  /**
   * Additional document information possibly useful for coref.
   * (e.g., is this dialog? the source of article, etc)
   * We can use this as features for coref system.
   * This is optional.
   */
  public Map<String, String> docInfo;

  /**
   * Gold mentions with coreference information for evaluation.
   * This is optional.
   */
  public List<List<Mention>> goldMentions;
  public Set<Triple<Integer,Integer,Integer>> filterMentionSet;

  /** optional for CoNLL document */
  public CoNLLDocument conllDoc;

  public InputDoc(Annotation anno) {
    this(anno, null, null, null);
  }

  public InputDoc(Annotation anno, Map<String, String> docInfo) {
    this(anno, docInfo, null, null);
  }

  public InputDoc(Annotation anno, Map<String, String> docInfo, List<List<Mention>> goldMentions) {
    this(anno, docInfo, goldMentions, null);
  }

  public InputDoc(Annotation anno, Map<String, String> docInfo, List<List<Mention>> goldMentions, CoNLLDocument conllDoc) {
    this.annotation = anno;
    this.docInfo = docInfo;
    this.goldMentions = goldMentions;
    this.conllDoc = conllDoc;
  }

}
