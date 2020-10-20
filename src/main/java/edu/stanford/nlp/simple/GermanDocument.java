package edu.stanford.nlp.simple;

import edu.stanford.nlp.coref.data.CorefChain;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.CoreNLPProtos;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;

/**
 * A sentence running with the German models.
 *
 * @author <a href="mailto:angeli@cs.stanford.edu">Gabor Angeli</a>
 */
public class GermanDocument extends Document {

  /**
   * The empty {@link Properties} object, for use with creating default annotators.
   */
  static final Properties EMPTY_PROPS = new Properties() {{
    try (InputStream is = IOUtils.getInputStreamFromURLOrClasspathOrFileSystem("edu/stanford/nlp/pipeline/StanfordCoreNLP-german.properties")){
      load(is);
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
    setProperty("language", "german");
    setProperty("annotators", "");
  }};

  /**
   * Create a new document from the passed in text.
   * @param text The text of the document.
   */
  public GermanDocument(String text) {
    super(GermanDocument.EMPTY_PROPS, text);
  }

  /**
   * Convert a CoreNLP Annotation object to a Document.
   * @param ann The CoreNLP Annotation object.
   */
  @SuppressWarnings("Convert2streamapi")
  public GermanDocument(Annotation ann) {
    super(GermanDocument.EMPTY_PROPS, ann);
  }


  /**
   * Create a Document object from a read Protocol Buffer.
   * @see Document#serialize()
   * @param proto The protocol buffer representing this document.
   */
  public GermanDocument(CoreNLPProtos.Document proto) {
    super(GermanDocument.EMPTY_PROPS, proto);
  }

  /**
   * Create a new German document from the passed in text and the given properties.
   * @param text The text of the document.
   */
  protected GermanDocument(Properties props, String text) {
    super(props, text);
  }


  /**
   * No lemma annotator for German -- set the lemma to be the word.
   *
   * @see Document#runLemma(Properties)
   */
  @Override
  protected Document runLemma(Properties props) {
    return mockLemma(props);
  }


  @Override
  protected Document runSentiment(Properties props) {
    throw new IllegalArgumentException("Sentiment analysis is not implemented for German");
  }


  @Override
  public Map<Integer, CorefChain> coref(Properties props) {
    throw new IllegalArgumentException("Coreference is not implemented for German");
  }

}
