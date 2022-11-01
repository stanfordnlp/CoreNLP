package edu.stanford.nlp.simple;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.pipeline.*;
import edu.stanford.nlp.util.Lazy;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;

/**
 * A sentence running with the Chinese models.
 *
 * @author <a href="mailto:angeli@cs.stanford.edu">Gabor Angeli</a>
 */
public class ChineseDocument extends Document {

  /**
   * The default {@link ChineseSegmenterAnnotator} implementation
   */
  private static final Lazy<Annotator> chineseSegmenter = Lazy.of(() -> new ChineseSegmenterAnnotator("segment", new Properties() {{
    setProperty("segment.model", "edu/stanford/nlp/models/segmenter/chinese/ctb.gz");
    setProperty("segment.sighanCorporaDict", "edu/stanford/nlp/models/segmenter/chinese");
    setProperty("segment.serDictionary", "edu/stanford/nlp/models/segmenter/chinese/dict-chris6.ser.gz");
    setProperty("segment.sighanPostProcessing", "true");
  }}));

  /**
   * The empty {@link java.util.Properties} object, for use with creating default annotators.
   */
  static final Properties EMPTY_PROPS = new Properties() {{
    try (InputStream is = IOUtils.getInputStreamFromURLOrClasspathOrFileSystem("edu/stanford/nlp/pipeline/StanfordCoreNLP-chinese.properties")){
      load(is);
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
    setProperty("language", "chinese");
    setProperty("annotators", "");
    setProperty("parse.binaryTrees", "true");
  }};

  /**
   * Create a new document from the passed in text.
   * @param text The text of the document.
   */
  public ChineseDocument(String text) {
    super(ChineseDocument.EMPTY_PROPS, text);
  }

  /**
   * Convert a CoreNLP Annotation object to a Document.
   * @param ann The CoreNLP Annotation object.
   */
  @SuppressWarnings("Convert2streamapi")
  public ChineseDocument(Annotation ann) {
    super(ChineseDocument.EMPTY_PROPS, ann);
  }


  /**
   * Create a Document object from a read Protocol Buffer.
   * @see edu.stanford.nlp.simple.Document#serialize()
   * @param proto The protocol buffer representing this document.
   */
  public ChineseDocument(CoreNLPProtos.Document proto) {
    super(ChineseDocument.EMPTY_PROPS, proto);
  }

  /**
   * Create a new chinese document from the passed in text and the given properties.
   * @param text The text of the document.
   */
  protected ChineseDocument(Properties props, String text) {
    super(props, text);
  }


  /** {@inheritDoc} */
  @Override
  public List<Sentence> sentences(Properties props) {
    return this.sentences(props, chineseSegmenter.get());
  }


  /**
   * No lemma annotator for Chinese -- set the lemma to be the word.
   *
   * @see Document#runLemma(Properties)
   */
  @Override
  protected Document runLemma(Properties props) {
    return mockLemma(props);
  }


  /**
   * No sentiment analysis implemented for Chinese.
   *
   * @see Document#runSentiment(Properties)
   */
  @Override
  protected Document runSentiment(Properties props) {
    throw new IllegalArgumentException("Sentiment analysis is not implemented for Chinese");
  }

  /**
   * The Neural Dependency Parser doesn't support Chinese yet, so back off to running the
   * constituency parser instead.
   */
  @Override  // TODO(danqi; from Gabor): remove this method when we have a trained NNDep model
  Document runDepparse(Properties props) {
    return runParse(props);
  }
}
