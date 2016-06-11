package edu.stanford.nlp.simple;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.pipeline.CoreNLPProtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Properties;

/**
 * A {@link Sentence}, but in Chinese.
 *
 * @author <a href="mailto:gabor@eloquent.ai">Gabor Angeli</a>
 */
public class ChineseSentence extends Sentence {
  /**
   * An SLF4J Logger for this class.
   */
  private static final Logger log = LoggerFactory.getLogger(ChineseSentence.class);

  /** A properties object for creating a document from a single sentence. Used in the constructor {@link Sentence#Sentence(String)} */
  static Properties SINGLE_SENTENCE_DOCUMENT = new Properties() {{
    try {
      load(IOUtils.getInputStreamFromURLOrClasspathOrFileSystem("edu/stanford/nlp/pipeline/StanfordCoreNLP-chinese.properties"));
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
    setProperty("language", "chinese");
    setProperty("annotators", "");
    setProperty("ssplit.isOneSentence", "true");
    setProperty("tokenize.class", "PTBTokenizer");
    setProperty("tokenize.language", "en");
  }};

  /** A properties object for creating a document from a single tokenized sentence. */
  private static Properties SINGLE_SENTENCE_TOKENIZED_DOCUMENT = new Properties() {{
    try {
      load(IOUtils.getInputStreamFromURLOrClasspathOrFileSystem("edu/stanford/nlp/pipeline/StanfordCoreNLP-chinese.properties"));
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
    setProperty("language", "chinese");
    setProperty("annotators", "");
    setProperty("ssplit.isOneSentence", "true");
    setProperty("tokenize.class", "WhitespaceTokenizer");
    setProperty("tokenize.language", "en");
    setProperty("tokenize.whitespace", "true");  // redundant?
  }};

  public ChineseSentence(String text) {
    super(text, SINGLE_SENTENCE_DOCUMENT);
  }

  public ChineseSentence(List<String> tokens) {
    super(tokens, SINGLE_SENTENCE_TOKENIZED_DOCUMENT);
  }

  public ChineseSentence(CoreNLPProtos.Sentence proto) {
    super(proto, SINGLE_SENTENCE_DOCUMENT);
  }
}
