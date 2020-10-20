package edu.stanford.nlp.ling;

import edu.stanford.nlp.process.TokenizerFactory;
import edu.stanford.nlp.process.PTBTokenizer;
import edu.stanford.nlp.process.Tokenizer;
import edu.stanford.nlp.util.ErasureUtils;
import edu.stanford.nlp.util.logging.Redwood;

import java.io.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;


/**
 * Basic implementation of Document that should be suitable for most needs.
 * BasicDocument is an ArrayList for storing words and performs tokenization
 * during construction. Override {@link #parse(String)} to provide support
 * for custom
 * document formats or to do a custom job of tokenization. BasicDocument should
 * only be used for documents that are small enough to store in memory.
 * <br>
 * The easiest way to use BasicDocuments is to construct them and call an init
 * method in the same line (we use init methods instead of constructors because
 * they're inherited and allow subclasses to have other more specific constructors).
 * <br>
 * For example, to read in a file {@code file} and tokenize it, you can call
 * {@code Document doc = new BasicDocument().init(file); }.
 *
 * @author Joseph Smarr (jsmarr@stanford.edu)
 * @author Sarah Spikes (sdspikes@cs.stanford.edu) (Templatization)
 *
 * @param <L> The type of the labels
 */
public class BasicDocument<L> extends ArrayList<Word> implements Document<L, Word, Word>  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(BasicDocument.class);

  /**
   * title of this document (never null).
   */
  protected String title = "";

  /**
   * original text of this document (may be null).
   */
  protected String originalText;

  /**
   * Label(s) for this document.
   */
  protected final List<L> labels = new ArrayList<>();

  /**
   * TokenizerFactory used to convert the text into words inside
   * {@link #parse(String)}.
   */
  protected TokenizerFactory<Word> tokenizerFactory;

  /**
   * Constructs a new (empty) BasicDocument using a {@link PTBTokenizer}.
   * Call one of the {@code init} methods to populate the document from a desired source.
   */
  public BasicDocument() {
    this(PTBTokenizer.factory());
  }

  /**
   * Constructs a new (empty) BasicDocument using the given tokenizer.
   * Call one of the {@code init} methods to populate the document from a desired source.
   */
  public BasicDocument(TokenizerFactory<Word> tokenizerFactory) {
    setTokenizerFactory(tokenizerFactory);
  }

  public BasicDocument(Document<L, Word, Word> d) {
    this((Collection<Word>) d);
  }

  public BasicDocument(Collection<Word> d) {
    this();
    addAll(d);
  }

  /**
   * Inits a new BasicDocument with the given text contents and title.
   * The text is tokenized using {@link #parse(String)} to populate the list of words
   * ("" is used if text is null). If specified, a reference to the
   * original text is also maintained so that the text() method returns the
   * text given to this constructor. Returns a reference to this
   * BasicDocument
   * for convenience (so it's more like a constructor, but inherited).
   */
  public static <L> BasicDocument<L> init(String text, String title, boolean keepOriginalText) {
    BasicDocument<L> basicDocument = new BasicDocument<>();
    // initializes the List of labels and sets the title
    basicDocument.setTitle(title);

    // stores the original text as specified
    if (keepOriginalText) {
      basicDocument.originalText = text;
    } else {
      basicDocument.originalText = null;
    }

    // populates the words by parsing the text
    basicDocument.parse(text == null ? "" : text);

    return basicDocument;
  }

  /**
   * Calls init(text,title,true)
   */
  public static <L> BasicDocument<L> init(String text, String title) {
    return init(text, title, true);
  }

  /**
   * Calls init(text,null,keepOriginalText)
   */
  public static <L> BasicDocument<L> init(String text, boolean keepOriginalText) {
    return init(text, null, keepOriginalText);
  }

  /**
   * Calls init(text,null,true)
   */
  public static <L> BasicDocument<L> init(String text) {
    return init(text, null, true);
  }

  /**
   * Calls init((String)null,null,true)
   */
  public static <L> BasicDocument<L> init() {
    return init((String) null, null, true);
  }

  /**
   * Inits a new BasicDocument by reading in the text from the given Reader.
   *
   * @see #init(String,String,boolean)
   */
  public static <L> BasicDocument<L> init(Reader textReader, String title, boolean keepOriginalText) throws IOException {
    return init(DocumentReader.readText(textReader), title, keepOriginalText);
  }

  /**
   * Calls init(textReader,title,true)
   */
  public BasicDocument<L> init(Reader textReader, String title) throws IOException {
    return init(textReader, title, true);
  }

  /**
   * Calls init(textReader,null,keepOriginalText)
   */
  public BasicDocument<L> init(Reader textReader, boolean keepOriginalText) throws IOException {
    return init(textReader, null, keepOriginalText);
  }

  /**
   * Calls init(textReader,null,true)
   */
  public BasicDocument<L> init(Reader textReader) throws IOException {
    return init(textReader, null, true);
  }

  /**
   * Inits a new BasicDocument by reading in the text from the given File.
   *
   * @see #init(String,String,boolean)
   */
  public BasicDocument<L> init(File textFile, String title, boolean keepOriginalText) throws IOException {
    Reader in = DocumentReader.getReader(textFile);
    BasicDocument<L> bd = init(in, title, keepOriginalText);
    in.close();
    return bd;
  }

  /**
   * Calls init(textFile,title,true)
   */
  public BasicDocument<L> init(File textFile, String title) throws IOException {
    return init(textFile, title, true);
  }

  /**
   * Calls init(textFile,textFile.getCanonicalPath(),keepOriginalText)
   */
  public BasicDocument<L> init(File textFile, boolean keepOriginalText) throws IOException {
    return init(textFile, textFile.getCanonicalPath(), keepOriginalText);
  }

  /**
   * Calls init(textFile,textFile.getCanonicalPath(),true)
   */
  public BasicDocument<L> init(File textFile) throws IOException {
    return init(textFile, textFile.getCanonicalPath(), true);
  }

  /**
   * Constructs a new BasicDocument by reading in the text from the given URL.
   *
   * @see #init(String,String,boolean)
   */
  public BasicDocument<L> init(URL textURL, String title, boolean keepOriginalText) throws IOException {
    return init(DocumentReader.getReader(textURL), title, keepOriginalText);
  }

  /**
   * Calls init(textURL,title,true)
   */
  public BasicDocument<L> init(URL textURL, String title) throws IOException {
    return init(textURL, title, true);
  }

  /**
   * Calls init(textURL,textFile.toExternalForm(),keepOriginalText)
   */
  public BasicDocument<L> init(URL textURL, boolean keepOriginalText) throws IOException {
    return init(textURL, textURL.toExternalForm(), keepOriginalText);
  }

  /**
   * Calls init(textURL,textURL.toExternalForm(),true)
   */
  public BasicDocument<L> init(URL textURL) throws IOException {
    return init(textURL, textURL.toExternalForm(), true);
  }

  /**
   * Initializes a new BasicDocument with the given list of words and title.
   */
  public BasicDocument<L> init(List<? extends Word> words, String title) {
    // initializes the List of labels and sets the title
    setTitle(title);
    // no original text
    originalText = null;
    // adds all of the given words to the list maintained by this document
    addAll(words);
    return (this);
  }

  /**
   * Calls init(words,null)
   */
  public BasicDocument<L> init(List<? extends Word> words) {
    return init(words, null);
  }

  /**
   * Tokenizes the given text to populate the list of words this Document
   * represents. The default implementation uses the current tokenizer and tokenizes
   * the entirety of the text into words. Subclasses should override this method
   * to parse documents in non-standard formats, and/or to pull the title of the
   * document from the text. The given text may be empty ("") but will never
   * be null. Subclasses may want to do additional processing and then just
   * call super.parse.
   *
   * @see #setTokenizerFactory
   */
  protected void parse(String text) {
    Tokenizer<Word> toke = tokenizerFactory.getTokenizer(new StringReader(text));
    addAll(toke.tokenize());
  }

  /**
   * Returns {@code this} (the features are the list of words).
   */
  @Override
  public Collection<Word> asFeatures() {
    return this;
  }

  /**
   * Returns the first label for this Document, or null if none have been
   * set.
   */
  @Override
  public L label() {
    return (labels.size() > 0) ? labels.get(0) : null;
  }

  /**
   * Returns the complete List of labels for this Document.
   * This is an empty collection if none have been set.
   */
  @Override
  public Collection<L> labels() {
    return labels;
  }

  /**
   * Removes all currently assigned labels for this Document then adds
   * the given label.
   * Calling {@code setLabel(null)} effectively clears all labels.
   */
  public void setLabel(L label) {
    labels.clear();
    addLabel(label);
  }

  /**
   * Removes all currently assigned labels for this Document then adds all
   * of the given labels.
   */
  public void setLabels(Collection<L> labels) {
    this.labels.clear();
    if (labels != null) {
      this.labels.addAll(labels);
    }
  }

  /**
   * Adds the given label to the List of labels for this Document if it is not null.
   */
  public void addLabel(L label) {
    if (label != null) {
      labels.add(label);
    }
  }

  /**
   * Returns the title of this document. The title may be empty ("") but will
   * never be null.
   */
  @Override
  public String title() {
    return (title);
  }

  /**
   * Sets the title of this Document to the given title. If the given title
   * is null, sets the title to "".
   */
  public void setTitle(String title) {
    if (title == null) {
      this.title = "";
    } else {
      this.title = title;
    }
  }

  /**
   * Returns the current TokenizerFactory used by {@link #parse(String)}.
   */
  public TokenizerFactory<Word> tokenizerFactory() {
    return (tokenizerFactory);
  }


  /**
   * Sets the tokenizerFactory to be used by {@link #parse(String)}.
   * Set this tokenizer before calling one of the {@code init} methods
   * because
   * it will probably call parse. Note that the tokenizer can equivalently be
   * passed in to the constructor.
   *
   * @see #BasicDocument(TokenizerFactory)
   */
  public void setTokenizerFactory(TokenizerFactory<Word> tokenizerFactory) {
    this.tokenizerFactory = tokenizerFactory;
  }

  /**
   * Returns a new empty BasicDocument with the same title, labels, and
   * tokenizer as this Document. This is useful when you want to make a
   * new Document that's like the old document but
   * can be filled with new text (e.g. if you're transforming
   * the contents non-destructively).
   *
   * Subclasses that want to preserve extra state should
   * override this method and add the extra state to the new document before
   * returning it. The new BasicDocument is created by calling
   * {@code getClass().newInstance()} so it should be of the correct subclass,
   * and thus you should be able to cast it down and add extra meta data directly.
   * Note however that in the event an Exception is thrown on instantiation
   * (e.g. if your subclass doesn't have a public empty constructor--it should btw!)
   * then a new {@code BasicDocument} is used instead. Thus if you want to be paranoid
   * (or some would say "correct") you should check that your instance is of
   * the correct sub-type as follows (this example assumes the subclass is called
   * {@code NumberedDocument} and it has the additional {@code number} property):
   * <pre>
   * Document blankDocument=super.blankDocument();
   * if(blankDocument instanceof NumberedDocument) {
   *     ((NumberedDocument)blankDocument).setNumber(getNumber());
   * </pre>
   */
  @Override
  public <OUT> Document<L, Word, OUT> blankDocument() {
    BasicDocument<L> bd;

    // tries to instantiate by reflection, settles for direct instantiation
    try {
      bd = ErasureUtils.<BasicDocument<L>>uncheckedCast(getClass().getDeclaredConstructor().newInstance());
    } catch (Exception e) {
      bd = new BasicDocument<>();
    }

    // copies over basic meta-data
    bd.setTitle(title());
    bd.setLabels(labels());
    bd.setTokenizerFactory(tokenizerFactory);

    // cast to the new output type
    return ErasureUtils.<Document<L, Word, OUT>>uncheckedCast(bd);
  }

  /**
   * Returns the text originally used to construct this document, or null if
   * there was no original text.
   */
  public String originalText() {
    return (originalText);
  }

  /**
   * Returns a "pretty" version of the words in this Document suitable for
   * display. The default implementation returns each of the words in
   * this Document separated
   * by spaces. Specifically, each element that implements {@link HasWord}
   * has its
   * {@link HasWord#word} printed, and other elements are skipped.
   *
   * Subclasses that maintain additional information may which to
   * override this method.
   */
  public String presentableText() {
    StringBuilder sb = new StringBuilder();
    for (Word cur : this) {
      if (sb.length() > 0) {
        sb.append(' ');
      }
      sb.append(cur.word());
    }
    return (sb.toString());
  }

  /**
   * For internal debugging purposes only. Creates and tests various instances
   * of BasicDocument.
   */
  public static void main(String[] args) {
    try {
      printState(BasicDocument.init("this is the text", "this is the title [String]", true));
      printState(BasicDocument.init(new StringReader("this is the text"), "this is the title [Reader]", true));

      File f = File.createTempFile("BasicDocumentTestFile", null);
      f.deleteOnExit();
      PrintWriter out = new PrintWriter(new FileWriter(f));
      out.print("this is the text");
      out.flush();
      out.close();
      printState(new BasicDocument<String>().init(f, "this is the title [File]", true));
      printState(new BasicDocument<String>().init(new URL("http://www.stanford.edu/~jsmarr/BasicDocumentTestFile.txt"), "this is the title [URL]", true));
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * For internal debugging purposes only.
   * Prints the state of the given BasicDocument to stderr.
   */
  private static <L> void printState(BasicDocument<L> bd) {
    log.info("BasicDocument:");
    log.info("\tTitle: " + bd.title());
    log.info("\tLabels: " + bd.labels());
    log.info("\tOriginalText: " + bd.originalText());
    log.info("\tWords: " + bd);
    log.info();
  }

  private static final long serialVersionUID = -24171720584352262L;

}

