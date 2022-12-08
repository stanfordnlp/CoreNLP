package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.coref.CorefCoreAnnotations;
import edu.stanford.nlp.coref.data.CorefChain;
import edu.stanford.nlp.ling.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Wrapper around an annotation representing a document.  Adds some helpful methods.
 *
 */
public class CoreDocument {

  protected Annotation annotationDocument;
  private List<CoreEntityMention> entityMentions;
  private List<CoreQuote> quotes;
  private List<CoreSentence> sentences;

  public CoreDocument(String documentText) {
    this.annotationDocument = new Annotation(documentText);
  }

  public CoreDocument(Annotation annotation) {
    this.annotationDocument = annotation;
    wrapAnnotations();
  }

  /** complete the wrapping process post annotation by a pipeline **/
  public void wrapAnnotations() {
    // wrap all of the sentences
    if (this.annotationDocument.get(CoreAnnotations.SentencesAnnotation.class) != null) {
      wrapSentences();
      // if there are entity mentions, build a document wide list
      if (!sentences.isEmpty() && sentences.get(0).entityMentions() != null) {
        buildDocumentEntityMentionsList();
      } else if (annotationDocument.get(CoreAnnotations.MentionsAnnotation.class) != null) {
        entityMentions = Collections.emptyList();
      }
      // if there are quotes, build a document wide list
      if (QuoteAnnotator.gatherQuotes(this.annotationDocument) != null)
        buildDocumentQuotesList();
    }
  }

  /** create list of CoreSentence's based on the Annotation's sentences **/
  private void wrapSentences() {
    sentences = this.annotationDocument.get(CoreAnnotations.SentencesAnnotation.class).
        stream().map(coreMapSentence -> new CoreSentence(this, coreMapSentence)).collect(Collectors.toList());
    sentences.forEach(CoreSentence::wrapEntityMentions);
  }

  /** build a list of all entity mentions in the document from the sentences **/
  private void buildDocumentEntityMentionsList() {
    entityMentions = sentences.stream().flatMap(sentence -> sentence.entityMentions().stream()).
        collect(Collectors.toList());
  }

  private void buildDocumentQuotesList() {
      this.quotes =
          QuoteAnnotator.gatherQuotes(this.annotationDocument).stream().
              map(coreMapQuote -> new CoreQuote(this, coreMapQuote)).collect(Collectors.toList());
  }

  /** provide access to the underlying annotation if needed **/
  public Annotation annotation() {
    return this.annotationDocument;
  }

  /** return the doc id of this doc **/
  public String docID() {
    return this.annotationDocument.get(CoreAnnotations.DocIDAnnotation.class);
  }

  /** return the doc date of this doc **/
  public String docDate() {
    return this.annotationDocument.get(CoreAnnotations.DocDateAnnotation.class);
  }

  /** return the full text of the doc **/
  public String text() { return this.annotationDocument.get(CoreAnnotations.TextAnnotation.class); }

  /** return the full token list for this doc **/
  public List<CoreLabel> tokens() {
    return this.annotationDocument.get(CoreAnnotations.TokensAnnotation.class);
  }

  /** the list of sentences in this document **/
  public List<CoreSentence> sentences() {
    return this.sentences;
  }

  /** the list of entity mentions in this document **/
  public List<CoreEntityMention> entityMentions() { return this.entityMentions; }

  /** coref info **/
  public Map<Integer, CorefChain> corefChains() {
    return this.annotationDocument.get(CorefCoreAnnotations.CorefChainAnnotation.class);
  }

  /** quotes **/
  public List<CoreQuote> quotes() {
    return this.quotes;
  }

  @Override
  public String toString() {
    return annotation().toString();
  }

}
