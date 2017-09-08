package old.edu.stanford.nlp.process;

import old.edu.stanford.nlp.io.IOUtils;
import old.edu.stanford.nlp.ling.HasWord;
import old.edu.stanford.nlp.ling.Word;
import old.edu.stanford.nlp.objectbank.TokenizerFactory;
import old.edu.stanford.nlp.objectbank.XMLBeginEndIterator;
import old.edu.stanford.nlp.util.Function;
import old.edu.stanford.nlp.web.HTMLParser;

import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fully customizable preprocessor for XML, HTML, and PLAIN text documents.
 * Can take any of a number of input formats and return a {@link List} of tokenized strings.
 *
 * @author Chris Cox
 * @author Jenny Finkel
 */

public class DocumentPreprocessor {


  private static final boolean DEBUG = false;

  private TokenizerFactory<? extends HasWord> tokenizerFactory;
  private String encoding;
  private String[] sentenceFinalPuncWords;

  /*
   *  SET TOKENIZER METHODS
   */
  public DocumentPreprocessor(TokenizerFactory<? extends HasWord> tokenizerFactory) {
    this.tokenizerFactory = tokenizerFactory;
  }

  /**
   * Constructs a preprocessor using the default tokenzier: {@link PTBTokenizer}.
   */
  public DocumentPreprocessor() {
    this.tokenizerFactory = PTBTokenizer.factory();
  }

  /**
   * Constructs a preprocessor using the default tokenizer: {@link PTBTokenizer}.
   * and sets the supressEscaping flag.
   */
  public DocumentPreprocessor(boolean suppressEscaping) {
    String options = "ptb3Escaping=" + (! suppressEscaping);
    this.tokenizerFactory = PTBTokenizer.factory(new WordTokenFactory(),
            options);
  }

  /**
   * Set the character encoding.
   *
   * @param encoding The character encoding used by Readers
   */
  public void setEncoding(String encoding) {
    this.encoding = encoding;
  }


  public void setSentenceFinalPuncWords(String[] sentenceFinalPuncWords) {
    this.sentenceFinalPuncWords = sentenceFinalPuncWords;
  }

  /**
   * Sets the factory from which to produce a {@link Tokenizer}.  The default is
   * {@link PTBTokenizer}.
   *
   */
  public void setTokenizerFactory(TokenizerFactory<? extends HasWord> newTokenizerFactory) {
    tokenizerFactory = newTokenizerFactory;
  }

  public void usePTBTokenizer() {
    tokenizerFactory = PTBTokenizer.factory();
  }

  /**
   * Use tokenizers which tokenize on whitespace.
   */
  public void useWhitespaceTokenizer() {
    tokenizerFactory = WhitespaceTokenizer.factory();
  }

  //
  // METHODS TO PROCESS PLAIN TEXT
  //

  /**
   * Reads the file into a single list of words.
   *
   * @param fileOrURL the path of a text file or URL
   * @return a list of objects of type Word representing words
   * @throws IOException
   */
  public List<Word> getWordsFromText(String fileOrURL) throws IOException {
    return getWordsFromText(fileOrURLToReader(fileOrURL));
  }

  /**
   * @param input a Reader of text
   * @return a List of objects of type Word representing words
   */
  public List<Word> getWordsFromText(Reader input) {
    Tokenizer tokenizer = tokenizerFactory.getTokenizer(new BufferedReader(input));
    return tokenizer.tokenize();
  }

  /**
   * Reads a file or URL and outputs a list of sentences.
   *
   * @param fileOrURL the path of a text file or URL
   * @return a list of objects of type <code>List<List<? extends HasWord>></code>
   * @throws IOException
   */
  public List<List<? extends HasWord>> getSentencesFromText(String fileOrURL) throws IOException {
    return getSentencesFromText(fileOrURLToReader(fileOrURL));
  }

  public List<List<? extends HasWord>> getSentencesFromText(String fileOrURL, boolean doPTBEscaping, String sentenceDelimiter, int tagDelimiter) throws IOException {
    return getSentencesFromText(fileOrURLToReader(fileOrURL), doPTBEscaping, sentenceDelimiter, tagDelimiter);
  }

  /**
   * @param input a Reader of text
   * @return a List of sentences
   */
  public List<List<? extends HasWord>> getSentencesFromText(Reader input) {
    return getSentencesFromText(input, false, null, -1);
  }

  /**
   * Produce a list of sentences from text.
   *
   * @param input             the path to the filename or URL
   * @param escaper           a {@link Function} that takes a List of HasWords and returns an escaped version of those words.
   *                          Passing in <code>null</code> here means that no escaping is done.
   * @param sentenceDelimiter If null, means that sentences are not segmented already, and should be using default sentence delimiters
   *                          if non-null, means that sentences have already been segmented, and are delimited with this token.
   * @return a List of sentences
   */
  public List<List<? extends HasWord>> getSentencesFromText(String input, Function<List<HasWord>,List<HasWord>> escaper, String sentenceDelimiter, int tagDelimiter) throws IOException {
    return getSentencesFromText(fileOrURLToReader(input), escaper, sentenceDelimiter, tagDelimiter);
  }

  public List<List<? extends HasWord>> getSentencesFromText(Reader input, String sentenceDelimiter) {
    return getSentencesFromText(input, null, sentenceDelimiter, -1);
  }

  /**
   * Produce a list of sentences from a Reader.
   *
   * @param input             The input Reader
   * @param escaper           A {@link Function} that takes a List of HasWords
   *                          and returns an escaped version of those words.
   *                          Passing in <code>null</code> here means that no
   *                          escaping is done.
   * @param sentenceDelimiter If null, means that sentences are not segmented
   *                          already, and should be using default sentence
   *                          delimiters; if non-null, means that sentences
   *                          have already been segmented, and are delimited
   *                          with this token.
   * @param tagDelimiter A character, the rightmost instance of which in a
   *                          token is taken to separate the word from a
   *                          POS tag.  A negative number if there are no
   *                          POS tags to separate off.
   * @return A list of sentences
   */
  public List<List<? extends HasWord>> getSentencesFromText(Reader input, Function<List<HasWord>,List<HasWord>> escaper, String sentenceDelimiter, int tagDelimiter) {
    if (escaper == null) {
      escaper = new NullEscaper();
    }
    ListEscaper listEscaper = new ListEscaper(escaper);

    if (tokenizerFactory instanceof WhitespaceTokenizer.WhitespaceTokenizerFactory) {
      // tokenized
      if (sentenceDelimiter == null) {
        // tokenized but sentences are not delimited
        Tokenizer tokenizer = new WhitespaceTokenizer(input, false);
        List<HasWord> words = tokenizer.tokenize();
        if (tagDelimiter >= 0) {
          // split off tags first
          WordToTaggedWordProcessor wttwp = new WordToTaggedWordProcessor((char) tagDelimiter);
          words = wttwp.process(words);
        }
        words = escaper.apply(words);

        WordToSentenceProcessor sp;
        if (sentenceFinalPuncWords != null) {
          sp = new WordToSentenceProcessor(new HashSet<String>(Arrays.asList(sentenceFinalPuncWords)));
        } else {
          sp = new WordToSentenceProcessor();
        }

        return sp.process(words);
      } else {
        // tokenized, and sentences are delimited
        Tokenizer tokenizer = new WhitespaceTokenizer(input, sentenceDelimiter.equals("\n"));
        List<HasWord> words = tokenizer.tokenize();
        List sentences = splitListsOnToken(words, sentenceDelimiter);
        if (tagDelimiter >= 0) {
          sentences = tagSplitSentences(sentences, tagDelimiter);
        }
       sentences = listEscaper.apply(sentences);
       return sentences;
      }
    } else {
      // not pre-tokenized
      if (tagDelimiter >= 0) {
        throw new RuntimeException("Can't read tags from untokenized document.");
      }
      // no tags
      if (sentenceDelimiter == null) {
        // the simple case: not tokenized, not sentence delimited, not tagged
        if (DEBUG) {
          System.out.println("doing plain case: not tokenized, not sentence delimited, not tagged");
        }
        Tokenizer tokenizer = tokenizerFactory.getTokenizer(new BufferedReader(input));
        List words = tokenizer.tokenize();
        words = escaper.apply(words);

        WordToSentenceProcessor sp;
        if (sentenceFinalPuncWords != null) {
          sp = new WordToSentenceProcessor(new HashSet<String>(Arrays.asList(sentenceFinalPuncWords)));
        } else {
          sp = new WordToSentenceProcessor();
        }

        return sp.process(words);
      } else {
        // not tokenized, but sentence delimited, so we must tokenize twice
        // first tokenization to look for the delimiter
        Tokenizer tokenizer = new WhitespaceTokenizer(input, true);
        List tokens = tokenizer.tokenize();
        tokens = escaper.apply(tokens);
        List<String> sentences = glueSentences(splitListsOnToken(tokens, sentenceDelimiter));
        // second tokenization done per sentence
        return tokenizeSentences(sentences);
      }
    }
  }


  /**
   * Gets a list of words from a string.
   *
   * @param input string
   * @return a List of objects of type Word representing words
   */
  public List<Word> getWordsFromString(String input) {
    Tokenizer tokenizer = tokenizerFactory.getTokenizer(new BufferedReader(new StringReader(input)));
    return tokenizer.tokenize();
  }


  //
  // METHODS TO PROCESS XML
  //

  /**
   * Returns a list of sentences contained in an XML file or URL, occuring
   * between the begin and end of a selected tag.
   * It escapes sentences using a {@link WordToSentenceProcessor}.
   * By default, it does PTBEscaping as well.
   *
   * @param splitOnTag the tag which denotes text boundaries
   * @return A list of sentences
   * @throws IOException
   */
  public List<List<? extends HasWord>> getSentencesFromXML(String fileOrURL, String splitOnTag) throws IOException {
    return getSentencesFromXML(fileOrURL, splitOnTag, null, true);
  }

  /**
   * Returns a list of sentences contained in an XML file or URL, occuring
   * between the begin and end of a selected tag.
   * It escapes sentences using a {@link WordToSentenceProcessor}.
   * By default, it does PTBEscaping as well.
   *
   * @param splitOnTag the tag which denotes text boundaries
   * @return A list of sentences
   * @throws IOException
   */
  public List<List<? extends HasWord>> getSentencesFromXML(String fileOrURL, String splitOnTag, boolean doPTBEscaping) throws IOException {
    return getSentencesFromXML(fileOrURL, splitOnTag, null, doPTBEscaping);
  }

  /**
   * Returns a list of sentences contained in an XML file, occuring between the begin and end of a selected tag.
   * It escapes sentences using a {@link WordToSentenceProcessor}.
   *
   * @param splitOnTag    the tag which denotes text boundaries
   * @param doPTBEscaping whether to escape PTB tokens using a {@link PTBEscapingProcessor}
   * @return A list of sentences contained in an XML file
   * @throws IOException
   */
  public List<List<? extends HasWord>> getSentencesFromXML(String fileOrURL, String splitOnTag, String sentenceDelimiter, boolean doPTBEscaping) throws IOException {
    return getSentencesFromXML(fileOrURLToReader(fileOrURL), splitOnTag, sentenceDelimiter, doPTBEscaping);
  }

  /**
   * Returns a list of sentences contained in an XML file, occuring between the begin and end of a selected tag.
   *
   * @param splitOnTag    the tag which denotes text boundaries
   * @param sentenceDelimiter The text that separates sentences
   * @param doPTBEscaping whether to escape PTB tokens using a {@link PTBEscapingProcessor}
   * @return A list of sentences contained in an XML file
   */
  public List<List<? extends HasWord>> getSentencesFromXML(Reader input, String splitOnTag, String sentenceDelimiter, boolean doPTBEscaping) {
    Function<List<HasWord>, List<HasWord>> escaper;
    if (doPTBEscaping) {
      escaper = new PTBEscapingProcessor();
    } else {
      escaper = new NullEscaper();
    }
    return getSentencesFromXML(input, escaper, splitOnTag, sentenceDelimiter);
  }

  /**
   * Returns a list of sentences contained in an XML file, occuring between
   * the begin and end of a selected tag.
   *
   * @param escaper  An escaper to use.
   * @param splitOnTag    the tag which denotes text boundaries
   * @return A list of sentences contained in an XML file
   */
  public List<List<? extends HasWord>> getSentencesFromXML(String fileOrURL, Function<List<HasWord>, List<HasWord>> escaper, String splitOnTag) throws IOException {
    return getSentencesFromXML(fileOrURLToReader(fileOrURL), escaper, splitOnTag, null);
  }

  /**
   * Returns a list of sentences contained in an XML file, occuring between
   * the begin and end of a selected tag.
   *
   * @param fileOrURL The filename or URL to get input from
   * @param escaper  An escaper to use on each sentence.
   * @param splitOnTag The XML element which denotes text boundaries to be
   *                   processed. This is a regular expression which
   *                   should match the element name(s) (i.e., specified
   *                   without the angle brackets).
   * @param sentenceDelimiter A String that will split sentences, including
   *                   the special values "newline" or "onePerElement"
   * @return A list of sentences contained in an XML file
   */
  public List<List<? extends HasWord>> getSentencesFromXML(String fileOrURL, Function<List<HasWord>, List<HasWord>> escaper, String splitOnTag, String sentenceDelimiter) throws IOException {
    return getSentencesFromXML(fileOrURLToReader(fileOrURL), escaper, splitOnTag, sentenceDelimiter);
  }

  /**
   * Returns a list of sentences contained in an XML file, occuring between
   * the begin and end of a selected tag.
   *
   * @param input The Reader to get input from
   * @param escaper  An escaper to use on each sentence.
   * @param splitOnTag The XML element which denotes text boundaries to be
   *                   processed. This is a regular expression which
   *                   should match the element name(s) (i.e., specified
   *                   without the angle brackets).
   * @param sentenceDelimiter A String that will split sentences, including
   *                   the special values "newline" or "onePerElement"
   * @return A list of sentences contained in an XML file
   */
  public List<List<? extends HasWord>> getSentencesFromXML(Reader input, Function<List<HasWord>, List<HasWord>> escaper, String splitOnTag, String sentenceDelimiter) {
    List<List<? extends HasWord>> lis = new ArrayList<List<? extends HasWord>>();
    if ("onePerElement".equals(sentenceDelimiter)) {
      sentenceDelimiter = ".$.onePerElement.$.";  // we assume this never matches!
    }

//     try {
//     System.err.println("hello world");
//     BufferedReader br = new BufferedReader(input);
//     String line = br.readLine();
//     while (true) {
//       System.err.println(line);
//       if (line == null) break;
//       line = br.readLine();
//     }
//     }catch (Exception e) {}

//     System.err.println(splitOnTag);

    XMLBeginEndIterator<String> xmlIter = new XMLBeginEndIterator<String>(input, splitOnTag);
    while (xmlIter.hasNext()) {
      // get the next string delimited by splitOnTag
      String s = xmlIter.next();
      // get this string as a list of sentences with appropriate options
      List<List<? extends HasWord>> section = this.getSentencesFromText(new BufferedReader(new StringReader(s)), escaper, sentenceDelimiter, -1);
      //place these sentences on the master list
      for (List<? extends HasWord> individual : section) {
        lis.add(individual);
      }
    }
    return lis;
  }


  //
  // METHODS TO PROCESS HTML
  //

  public List<Word> getWordsFromHTML(String fileOrURL) throws IOException {
    return getWordsFromHTML(fileOrURLToReader(fileOrURL));
  }

  public List<Word> getWordsFromHTML(Reader input) {
    HTMLParser parser = new HTMLParser();
    try {
      String s = parser.parse(input);
      return getWordsFromText(new StringReader(s));
    } catch (IOException e) {
      System.err.println("IOException" + e.getMessage());
    }
    return null;
  }

  public List<List<? extends HasWord>> getSentencesFromHTML(String fileOrURL) throws IOException {
    return getSentencesFromHTML(fileOrURLToReader(fileOrURL));
  }

  public List<List<? extends HasWord>> getSentencesFromHTML(Reader input) {
    HTMLParser parser = new HTMLParser();
    try {
      String s = parser.parse(input);
      return getSentencesFromText(new StringReader(s));
    } catch (IOException e) {
      System.err.println("IOException" + e.getMessage());
    }
    return null;
  }


  //
  // PRIVATE
  //


  private List<List<? extends HasWord>> getSentencesFromText(Reader fileOrURL, boolean doPTBEscaping, String sentenceDelimiter, int tagDelimiter) {
    Function<List<HasWord>, List<HasWord>> escaper;
    if (doPTBEscaping) {
      escaper = new PTBEscapingProcessor();
    } else {
      escaper = new NullEscaper();
    }
    return getSentencesFromText(fileOrURL, escaper, sentenceDelimiter, tagDelimiter);
  }


  private static class NullEscaper implements Function<List<HasWord>, List<HasWord>> {
    public List<HasWord> apply(List<HasWord> hasWords) {
      return hasWords;
    }
  }

  private static class ListEscaper implements Function<List<List<HasWord>>, List<List<HasWord>>> {

    public ListEscaper(Function<List<HasWord>, List<HasWord>> f) {
      this.f = f;
    }

    Function<List<HasWord>, List<HasWord>> f;

    public List<List<HasWord>> apply(List<List<HasWord>> lists) {
      List<List<HasWord>> result = new ArrayList<List<HasWord>>(lists.size());
      for (List<HasWord> l : lists) {
        result.add(f.apply(l));
      }
      return result;
    }
  }


  /**
   * @return a List of Lists of tokens. delimiter tokens are removed.
   */
  private static List<List<HasWord>> splitListsOnToken(List<HasWord> tokens, String sentenceDelimiter) {
    List<List<HasWord>> result = new ArrayList<List<HasWord>>();
    List<HasWord> sentence = new ArrayList<HasWord>();
    for (HasWord word : tokens) {
      if (word.word().equals(sentenceDelimiter)) {
        // don't add this token
        // finish this sentence and add it
        result.add(sentence);
        sentence = new ArrayList<HasWord>();
      } else {
        sentence.add(word);
      }
    }
    if ( ! sentence.isEmpty()) {
      result.add(sentence);
    }
    return result;
  }

  private static List<String> glueSentences(List<List<HasWord>> sentences) {
    List<String> result = new ArrayList<String>();
    for (List<HasWord> sentence : sentences) {
      result.add(glueSentence(sentence));
    }
    return result;
  }

  /** Turn a List of HasWord into a String, by putting a space in between
   *  each word() value.
   *
   * @param sentence The List of HasWord
   * @return The sentence with tokens space separated.
   */
  private static String glueSentence(List<HasWord> sentence) {
    StringBuilder result = new StringBuilder();
    if ( ! sentence.isEmpty()) {
      HasWord word = sentence.get(0);
      String s = word.word();
      result.append(s);
      for (int i = 1, sz = sentence.size(); i < sz; i++) {
        word = sentence.get(i);
        s = word.word();
        result.append(" ").append(s);
      }
    }
    return result.toString();
  }


  private List tokenizeSentences(List<String> sentences) {
    List result = new ArrayList();
    for (String sentence : sentences) {
      Tokenizer tok = tokenizerFactory.getTokenizer(new StringReader(sentence));
      result.add(tok.tokenize());
    }
    return result;
  }

  private static List<List<? extends HasWord>> tagSplitSentences(List<List<HasWord>> sentences, int tagDelimiter) {
    List<List<? extends HasWord>> result = new ArrayList<List<? extends HasWord>>();
    WordToTaggedWordProcessor wttwp = new WordToTaggedWordProcessor((char) tagDelimiter);
    for (List<HasWord> sentence : sentences) {
      sentence = wttwp.process(sentence);
      result.add(sentence);
    }
    return result;
  }

  private static final Pattern urlPattern = Pattern.compile("(?:ht|f)tps?://.*?");

  // todo cdm: This is sorta woeful, and should be replaced by the stuff in the io package, except that we might steal the more general regex for matching web services
  private Reader fileOrURLToReader(String fileOrURL) throws IOException {
    if (DEBUG) {
      System.out.println("fileOrURLToReader(" + fileOrURL + ")");
    }
    System.err.println(fileOrURL);
    Matcher m = urlPattern.matcher(fileOrURL);
    if (m.matches()) {
      URL url = new URL(fileOrURL);
      return new BufferedReader(new StringReader(IOUtils.slurpURL(url)));
    } else {
      if (encoding == null) {
        return new FileReader(fileOrURL);
      } else {
        return new BufferedReader(new InputStreamReader(new FileInputStream(fileOrURL), encoding));
      }
    }
  }


  private static final int PLAIN = 0;
  private static final int XML = 1;
  private static final int HTML = 2;


  /**
   * This provides a simple test method for DocumentPreprocessor. <br/>
   * Usage:
   * java
   * DocumentPreprocessor -file filename [-xml tag|-html] [-noSplitSentence]
   * <p>
   * A filename is required. The code doesn't run as a filter currently.
   * <p>
   * tag is the element name of the XML from which to extract text.  It can
   * be a regular expression which is called on the element with the
   * matches() method, such as 'TITLE|P'.
   * The -noSplitSentence flag suppresses the normal splitting into sentences
   * using PTBTokenizer and WordToSentenceProcessor
   *
   * @param args Command-line arguments
   * @throws IOException If file isn't openable, etc.
   */
  public static void main(String[] args) throws IOException {
    if (args.length == 0) {
      System.err.println("usage: DocumentPreprocessor -file filename [-xml tag|-html] [-noSplitSentence]");
      return;
    }

    boolean splitSentences = true;
    boolean suppressEscaping = false;
    boolean noTokenization = false;
    boolean plainOutput = true;
    String xmlTag = null;
    DocumentPreprocessor docPreprocessor;

    int fileType = PLAIN;

    String file = null;

    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-file")) {
        file = args[++i];
      } else if (args[i].equals("-xml")) {
        fileType = XML;
        xmlTag = args[++i];
      } else if (args[i].equals("-html")) {
        fileType = HTML;
      } else if (args[i].equals("-noSplitSentence")) {
        splitSentences = false;
      } else if (args[i].equals("-suppressEscaping")) {
        suppressEscaping = true;
      } else if (args[i].equals("-noTokenization")) {
        noTokenization = true;
      //} else if (args[i].equals("-plainOutput")) {
      //  plainOutput = true;
      }
    }

    if(noTokenization)
      docPreprocessor = new DocumentPreprocessor(WhitespaceTokenizer.factory(true));
    else
      docPreprocessor = new DocumentPreprocessor(suppressEscaping);
    System.err.println("Tokenizer: "+docPreprocessor.tokenizerFactory.getClass());

    List<? extends HasWord> doc;
    List<List<? extends HasWord>> docs = new ArrayList<List<? extends HasWord>>();

    switch (fileType) {
      case (PLAIN):
        if (splitSentences) {
          docs = docPreprocessor.getSentencesFromText(file);
        } else {
          doc = docPreprocessor.getWordsFromText(file);
          docs.add(doc);
        }
        break;
      case (XML):
        boolean doPTBEscaping = !suppressEscaping;
        docs = docPreprocessor.getSentencesFromXML(file, xmlTag, doPTBEscaping);
        break;
      case (HTML):
        if (splitSentences) {
          docs = docPreprocessor.getSentencesFromHTML(file);
        } else {
          doc = docPreprocessor.getWordsFromHTML(file);
          docs.add(doc);
        }
        break;
    }

    System.err.println("Read in " + docs.size() + " sentences.");
    for (List lis : docs) {
      System.err.println("Length: " + lis.size());
      if(plainOutput) {
        for(int i=0; i<lis.size(); ++i) {
          if(i>0) System.out.print(" ");
          System.out.print(lis.get(i));
        }
        System.out.println();
      } else {
        System.out.println(lis);
      }
    }

  } // end main()

}
