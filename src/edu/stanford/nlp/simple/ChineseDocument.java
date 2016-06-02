package edu.stanford.nlp.simple;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.AnnotatorImplementations;
import edu.stanford.nlp.pipeline.CoreNLPProtos;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Properties;

/**
 * A sentence running with the Chinese models.
 *
 * @author <a href="mailto:gabor@eloquent.ai">Gabor Angeli</a>
 */
public class ChineseDocument extends Document {
  /**
   * An SLF4J Logger for this class.
   */
  private static final Logger log = LoggerFactory.getLogger(ChineseDocument.class);

  /**
   * The empty {@link java.util.Properties} object, for use with creating default annotators.
   */
  static final Properties EMPTY_PROPS = new Properties() {{
    try {
      load(IOUtils.getInputStreamFromURLOrClasspathOrFileSystem("edu/stanford/nlp/pipeline/StanfordCoreNLP-chinese.properties"));
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
    setProperty("language", "chinese");
    setProperty("annotators", "");
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

}
