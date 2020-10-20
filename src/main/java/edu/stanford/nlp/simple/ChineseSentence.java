package edu.stanford.nlp.simple;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.pipeline.CoreNLPProtos;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;

/**
 * A {@link Sentence}, but in Chinese.
 *
 * @author <a href="mailto:angeli@cs.stanford.edu">Gabor Angeli</a>
 */
public class ChineseSentence extends Sentence {

  /** A properties object for creating a document from a single sentence. Used in the constructor {@link Sentence#Sentence(String)} */
  static Properties SINGLE_SENTENCE_DOCUMENT = new Properties() {{
    try (InputStream is = IOUtils.getInputStreamFromURLOrClasspathOrFileSystem("edu/stanford/nlp/pipeline/StanfordCoreNLP-chinese.properties")){
      load(is);
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
    setProperty("language", "chinese");
    setProperty("annotators", "");
    setProperty("ssplit.isOneSentence", "true");
    setProperty("tokenize.class", "PTBTokenizer");
    setProperty("tokenize.language", "zh");
  }};

  /** A properties object for creating a document from a single tokenized sentence. */
  private static Properties SINGLE_SENTENCE_TOKENIZED_DOCUMENT = new Properties() {{
    try (InputStream is = IOUtils.getInputStreamFromURLOrClasspathOrFileSystem("edu/stanford/nlp/pipeline/StanfordCoreNLP-chinese.properties")){
      load(is);
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
    setProperty("language", "chinese");
    setProperty("annotators", "");
    setProperty("ssplit.isOneSentence", "true");
    setProperty("tokenize.class", "WhitespaceTokenizer");
    setProperty("tokenize.language", "zh");
    setProperty("tokenize.whitespace", "true");  // redundant?
  }};

  public ChineseSentence(String text) {
    super(new ChineseDocument(text), SINGLE_SENTENCE_DOCUMENT);
  }

  public ChineseSentence(List<String> tokens) {
    super(ChineseDocument::new, tokens, SINGLE_SENTENCE_TOKENIZED_DOCUMENT);
  }

  public ChineseSentence(CoreNLPProtos.Sentence proto) {
    super(ChineseDocument::new, proto, SINGLE_SENTENCE_DOCUMENT);
  }

}
