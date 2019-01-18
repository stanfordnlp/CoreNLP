package edu.stanford.nlp.simple;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.pipeline.CoreNLPProtos;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;

/**
 * A {@link Sentence}, but in Arabic.
 *
 * @author <a href="mailto:angeli@cs.stanford.edu">Gabor Angeli</a>
 */
public class ArabicSentence extends Sentence {

  /** A properties object for creating a document from a single sentence. Used in the constructor {@link Sentence#Sentence(String)} */
  static Properties SINGLE_SENTENCE_DOCUMENT = new Properties() {{
    try (InputStream is = IOUtils.getInputStreamFromURLOrClasspathOrFileSystem("edu/stanford/nlp/pipeline/StanfordCoreNLP-arabic.properties")){
      load(is);
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
    setProperty("language", "arabic");
    setProperty("annotators", "");
    setProperty("ssplit.isOneSentence", "true");
    setProperty("tokenize.class", "PTBTokenizer");
    setProperty("tokenize.language", "ar");
  }};

  /** A properties object for creating a document from a single tokenized sentence. */
  private static Properties SINGLE_SENTENCE_TOKENIZED_DOCUMENT = new Properties() {{
    try (InputStream is = IOUtils.getInputStreamFromURLOrClasspathOrFileSystem("edu/stanford/nlp/pipeline/StanfordCoreNLP-arabic.properties")){
      load(is);
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
    setProperty("language", "arabic");
    setProperty("annotators", "");
    setProperty("ssplit.isOneSentence", "true");
    setProperty("tokenize.class", "WhitespaceTokenizer");
    setProperty("tokenize.language", "ar");
    setProperty("tokenize.whitespace", "true");  // redundant?
  }};

  public ArabicSentence(String text) {
    super(new ArabicDocument(text), SINGLE_SENTENCE_DOCUMENT);
  }

  public ArabicSentence(List<String> tokens) {
    super(ArabicDocument::new, tokens, SINGLE_SENTENCE_TOKENIZED_DOCUMENT);
  }

  public ArabicSentence(CoreNLPProtos.Sentence proto) {
    super(ArabicDocument::new, proto, SINGLE_SENTENCE_DOCUMENT);
  }

}
