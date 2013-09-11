package edu.stanford.nlp.ling;

import edu.stanford.nlp.objectbank.TokenizerFactory;
import edu.stanford.nlp.process.PTBTokenizer;

import java.io.*;
import java.net.URL;

/**
 * Basic mechanism for reading in Documents from various input sources.
 * This default implementation can read from strings, files, URLs, and
 * InputStreams and can use a given Tokenizer to turn the text into words.
 * When working with a new data format, make a new DocumentReader to parse it
 * and then use it with the existing Document APIs (rather than having to make
 * new Document classes). Use the protected class variables (in, tokenizer,
 * keepOriginalText) to read text and create docs appropriately. Subclasses should
 * ideally provide similar constructors to this class, though only the constructor
 * that takes a Reader is required.
 *
 * @author Joseph Smarr (jsmarr@stanford.edu)
 * @author Sarah Spikes (sdspikes@cs.stanford.edu) - templatized
 *
 * @param <L> label type
 */
public class DocumentReader<L> {

  /**
   * Reader used to read in document text. In default implementation, this is
   * guaranteed to be a BufferedReader (so cast down) but it's typed as
   * Reader in case subclasses don't want it buffered for some reason.
   */
  protected BufferedReader in;

  /**
   * Tokenizer used to chop up document text into words.
   */
  protected TokenizerFactory<? extends HasWord> tokenizerFactory;

  /**
   * Whether to keep source text in document along with tokenized words.
   */
  protected boolean keepOriginalText;

  /**
   * Constructs a new DocumentReader without an initial input source.
   * Must call {@link #setReader} before trying to read any documents.
   * Uses a PTBTokenizer and keeps original text.
   */
  public DocumentReader() {
    this(null);
  }

  /**
   * Constructs a new DocumentReader using a PTBTokenizerFactory and keeps the original text.
   *
   * @param in The Reader
   */
  public DocumentReader(Reader in) {
    this(in, PTBTokenizer.PTBTokenizerFactory.newTokenizerFactory(), true);
  }

  /**
   * Constructs a new DocumentReader that will read text from the given
   * Reader and tokenize it into words using the given Tokenizer. The default
   * implementation will internally buffer the reader if it is not already
   * buffered, so there is no need to pre-wrap the reader with a BufferedReader.
   * This class provides many <tt>getReader</tt> methods for conviniently
   * reading from many input sources.
   */
  public DocumentReader(Reader in, TokenizerFactory<? extends HasWord> tokenizerFactory, boolean keepOriginalText) {
    if (in != null) {
      setReader(in);
    }
    setTokenizerFactory(tokenizerFactory);
    this.keepOriginalText = keepOriginalText;
  }

  /**
   * Returns the reader for the text input source of this DocumentReader.
   */
  public Reader getReader() {
    return in;
  }

  /**
   * Sets the reader from which to read and create documents.
   * Default implementation automatically buffers the Reader if it's not
   * already buffered. Subclasses that don't want buffering may want to override
   * this method to simply set the global <tt>in</tt> directly.
   */
  public void setReader(Reader in) {
    this.in = getBufferedReader(in);
  }

  /**
   * Returns the tokenizer used to chop up text into words for the documents.
   */
  public TokenizerFactory<? extends HasWord> getTokenizerFactory() {
    return (tokenizerFactory);
  }

  /**
   * Sets the tokenizer used to chop up text into words for the documents.
   */
  public void setTokenizerFactory(TokenizerFactory<? extends HasWord> tokenizerFactory) {
    this.tokenizerFactory = tokenizerFactory;
  }

  /**
   * Returns whether created documents will store their source text along with tokenized words.
   */
  public boolean getKeepOriginalText() {
    return (keepOriginalText);
  }

  /**
   * Sets whether created documents should store their source text along with tokenized words.
   */
  public void setKeepOriginalText(boolean keepOriginalText) {
    this.keepOriginalText = keepOriginalText;
  }

  /**
   * Reads the next document's worth of text from the reader and turns it into
   * a Document. Default implementation calls {@link #readNextDocumentText}
   * and passes it to {@link #parseDocumentText} to create the document.
   * Subclasses may wish to override either or both of those methods to handle
   * custom formats of document collections and individual documents
   * respectively. This method can also be overridden in its entirety to
   * provide custom reading and construction of documents from input text.
   */
  public BasicDocument<L> readDocument() throws IOException {
    String text = readNextDocumentText();
    if (text == null) {
      return (null);
    }
    return parseDocumentText(text);
  }

  /**
   * Reads the next document's worth of text from the reader. Default
   * implementation reads all the text. Subclasses wishing to read multiple
   * documents from a single input source should read until the next document
   * delimiter and return the text so far. Returns null if there is no more
   * text to be read.
   */
  protected String readNextDocumentText() throws IOException {
    return readText(in);
  }

  /**
   * Creates a new Document for the given text. Default implementation tokenizes
   * the text using the tokenizer provided during construction and sticks the words
   * in a new BasicDocument. The text is also stored as the original text in
   * the BasicDocument if keepOriginalText was set in the constructor. Subclasses
   * may wish to extract additional information from the text and/or return another
   * document subclass with additional meta-data.
   */
  protected BasicDocument<L> parseDocumentText(String text) {
    new BasicDocument<L>();
    return BasicDocument.init(text, keepOriginalText);
  }

  /**
   * Wraps the given Reader in a BufferedReader or returns it directly if it
   * is already a BufferedReader. Subclasses should use this method before
   * reading from <tt>in</tt> for efficiency and/or to read entire lines at
   * a time. Note that this should only be done once per reader because when
   * you read from a buffered reader, it reads more than necessary and stores
   * the rest, so if you then throw that buffered reader out and get a new one
   * for the original reader, text will be missing. In the default DocumentReader
   * text, the Reader passed in at construction is wrapped in a buffered reader
   * so you can just cast <tt>in</tt> down to a BufferedReader without calling
   * this method.
   */
  public static BufferedReader getBufferedReader(Reader in) {
    if (in == null) {
      return (null);
    }
    if (!(in instanceof BufferedReader)) {
      in = new BufferedReader(in);
    }
    return (BufferedReader) in;
  }

  /**
   * Returns everything that can be read from the given Reader as a String.
   * Returns null if the given Reader is null.
   */
  public static String readText(Reader in) throws IOException {
    // returns null if the reader is null
    if (in == null) {
      return (null);
    }

    // ensures the reader is buffered
    BufferedReader br = getBufferedReader(in);

    // reads all the chars into a buffer
    StringBuilder sb = new StringBuilder(16000);  // make biggish
    int c;
    while ((c = br.read()) >= 0) {
      sb.append((char) c);
    }

    return sb.toString();
  }

  /**
   * Returns a Reader that reads in the given text.
   */
  public static Reader getReader(String text) {
    return (new StringReader(text));
  }

  /**
   * Returns a Reader that reads in the given file.
   */
  public static Reader getReader(File file) throws FileNotFoundException {
    return (new FileReader(file));
  }

  /**
   * Returns a Reader that reads in the given URL.
   */
  public static Reader getReader(URL url) throws IOException {
    return (getReader(url.openStream()));
  }

  /**
   * Returns a Reader that reads in the given InputStream.
   */
  public static Reader getReader(InputStream in) {
    return (new InputStreamReader(in));
  }
}
