package edu.stanford.nlp.ie;

import edu.stanford.nlp.classify.Classifier;
import edu.stanford.nlp.ling.BasicDocument;
import edu.stanford.nlp.ling.Document;
import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.process.PTBTokenizer;
import edu.stanford.nlp.util.Iterables;

import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

/**
 * FieldExtractor that uses text classification in place of substring extraction.
 * A ClassifierFieldExtractor extracts a field from text by treating the text as
 * a document, classifying the document (predicting a label for it) and using the
 * String value of that label as the extracted text. For example, extracting the
 * department for a class web page could be accomplished by running the page
 * through a department classifier and using the result. This is an alternative
 * to finding the extracted field within the text of the document. For some fields
 * using text classification for IE may be more useful (especially for fields with
 * small, constrained value ranges).
 */
public class ClassifierFieldExtractor extends AbstractFieldExtractor {
  /**
   * 
   */
  private static final long serialVersionUID = 8441980740688320766L;
  private Classifier<String, Word> classifier; // classifier used for extraction

  /**
   * Constructs a new ClassifierFieldExtractor using the given Classifier.
   * The single extractable field will be the same as the name of this
   * Classifier.
   */
  public ClassifierFieldExtractor(Classifier<String, Word> classifier, String name) {
    this.classifier = classifier;
    setName(name);
  }

  /**
   * Classifies the given text document and "extracts" its label.
   * Single field name will be the name of this FieldExtractor and its value
   * will be the String value of the label the Classifier assigns to the text
   * document.
   */
  public Map<String, String> extractFields(String text) {
    Map<String, String> extractedFields = new HashMap<String, String>();

    Document<String, Word, Word> doc = new BasicDocument<String>();
    Iterables.addAll(PTBTokenizer.newPTBTokenizer(new StringReader(text)), doc);
    extractedFields.put(getName(), classifier.classOf(doc).toString());
    return (extractedFields);
  }

  /**
   * Uses the name of this FieldExtractor as the sole field to extract.
   */
  public String[] getExtractableFields() {
    return (new String[]{getName()});
  }
}
