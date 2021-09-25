package edu.stanford.nlp.process;

// Stanford English Tokenizer -- a deterministic, fast high-quality tokenizer
// Copyright (c) 2002-2019 The Board of Trustees of
// The Leland Stanford Junior University. All Rights Reserved.
//
// This program is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License
// as published by the Free Software Foundation; either version 2
// of the License, or (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see http://www.gnu.org/licenses/ .
//
// For more information, bug reports, fixes, contact:
//    Christopher Manning
//    Dept of Computer Science, Gates 2A
//    Stanford CA 94305-9020
//    USA
//    java-nlp-support@lists.stanford.edu
//    http://nlp.stanford.edu/software/


import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.logging.Redwood;


/**
 * A fast, rule-based tokenizer implementation, which produces Penn Treebank
 * style tokenization of English text. It was initially written to conform
 * to Penn Treebank tokenization conventions over ASCII text, but now provides
 * a range of tokenization options over a broader space of Unicode text.
 * It reads raw text and outputs
 * tokens of classes that implement edu.stanford.nlp.trees.HasWord
 * (typically a Word or a CoreLabel). It can
 * optionally return end-of-line as a token.
 * <p>
 * New code is encouraged to use the {@link #PTBTokenizer(Reader,LexedTokenFactory,String)}
 * constructor. The other constructors are historical.
 * You specify the type of result tokens with a
 * LexedTokenFactory, and can specify the treatment of tokens by mainly boolean
 * options given in a comma separated String options
 * (e.g., "invertible,normalizeParentheses=true").
 * If the String is {@code null} or empty, you get the traditional
 * PTB3 normalization behaviour (i.e., you get ptb3Escaping=true).  If you
 * want no normalization, then you should pass in the String
 * "ptb3Escaping=false".  The known option names are:
 * <ol>
 * <li>invertible: Store enough information about the original form of the
 *     token and the whitespace around it that a list of tokens can be
 *     faithfully converted back to the original String.  Valid only if the
 *     LexedTokenFactory is an instance of CoreLabelTokenFactory.  The
 *     keys used in it are: TextAnnotation for the tokenized form,
 *     OriginalTextAnnotation for the original string, BeforeAnnotation and
 *     AfterAnnotation for the whitespace before and after a token, and
 *     perhaps CharacterOffsetBeginAnnotation and CharacterOffsetEndAnnotation to record
 *     token begin/after end character offsets, if they were specified to be recorded
 *     in TokenFactory construction.  (Like the String class, begin and end
 *     are done so end - begin gives the token length.) Default is false. </li>
 * <li>tokenizeNLs: Whether end-of-lines should become tokens (or just
 *     be treated as part of whitespace). Default is false. </li>
 * <li>tokenizePerLine: Run the tokenizer separately on each line of a file.
 *     This has the following consequences: (i) A token (currently only SGML tokens)
 *     cannot span multiple lines of the original input, and (ii) The tokenizer will not
 *     examine/wait for input from the next line before deciding tokenization decisions on
 *     this line. The latter property affects treating periods by acronyms as end-of-sentence
 *     markers. Having this true is necessary to stop the tokenizer blocking and waiting
 *     for input after a newline is seen when the previous line ends with an abbreviation. </li>
 * <li>ptb3Escaping: Enable all traditional PTB3 token transforms
 *     (like parentheses becoming -LRB-, -RRB-).  This is a macro flag that
 *     sets or clears all the options below. Note that because properties are set in a Map,
 *     if you specify both this flag and flags it sets, the resulting behaviour is non-deterministic (sorry!).
 *     (Default setting of the various properties below that this flag controls is equivalent to it being set
 *     to true.) </li>
 * <li>ud: [From CoreNLP 4.0] Enable options that make tokenization like what is used in UD v2. This is a
 *     macro flag that sets various of the options below. It ignores a value for this key.
 *     Note that because properties are set in a Map, if you specify both this flag and flags it sets,
 *     the resulting behaviour is non-deterministic (sorry!). </li>
 * <li>americanize: Whether to rewrite common British English spellings
 *     as American English spellings. (This is useful if your training
 *     material uses American English spelling, such as the Penn Treebank.)
 *     Default is true. </li>
 * <li>normalizeSpace: Whether any spaces in tokens (phone numbers, fractions
 *     get turned into U+00A0 (non-breaking space).  It's dangerous to turn
 *     this off for most of our Stanford NLP software, which assumes no
 *     spaces in tokens. Default is true. </li>
 * <li>normalizeAmpersandEntity: Whether to map the XML {@code &amp;} to an
 *      ampersand. Default is true. </li>
 * <li>normalizeFractions: Whether to map certain common composed
 *     fraction characters to spelled out letter forms like "1/2".
 *     Default is true. </li>
 * <li>normalizeParentheses: Whether to map round parentheses to -LRB-,
 *     -RRB-, as in the Penn Treebank. Default is true. </li>
 * <li>normalizeOtherBrackets: Whether to map other common bracket characters
 *     to -LCB-, -LRB-, -RCB-, -RRB-, roughly as in the Penn Treebank.
 *     Default is true. </li>
 * <li>quotes: [From CoreNLP 4.0] Select a style of mapping quotes. An enum with possible values (case insensitive):
 *     latex, unicode, ascii, not_cp1252, original. "ascii" maps all quote characters to the traditional ' and ".
 *     "latex" maps quotes to ``, `, ', '', as in Latex and the PTB3 WSJ (though this is now heavily frowned on in Unicode).
 *     "unicode" maps quotes to the range U+2018 to U+201D, the preferred unicode encoding of single and double quotes.
 *     "original" leaves all quotes as they were. "not_cp1252" only remaps invalid cp1252 quotes to Unicode.
 *     The default is "not_cp1252". </li>
 * <li>ellipses: [From CoreNLP 4.0] Select a style for mapping ellipses (3 dots).  An enum with possible values
 *     (case insensitive): unicode, ptb3, not_cp1252, original. "ptb3" maps ellipses to three dots (...), the
 *     old PTB3 WSJ coding of an ellipsis. "unicode" maps three dot and space three dot sequences to
 *     U+2026, the Unicode ellipsis character. "not_cp1252" only remaps invalid cp1252 ellipses to unicode.
 *     "original" leaves all ellipses as they were. The default is "not_cp1252". </li>
 * <li>dashes: [From CoreNLP 4.0] Select a style for mapping dashes. An enum with possible values
 *     (case insensitive): unicode, ptb3, not_cp1252, original. "ptb3" maps dashes to "--", the
 *     most prevalent old PTB3 WSJ coding of a dash (though some are just "-" HYPHEN-MINUS).
 *     "unicode" maps "-", "--", and "---" HYPHEN-MINUS sequences and CP1252 dashes to Unicode en and em dashes.
 *     "not_cp1252" only remaps invalid cp1252 dashes to unicode.
 *     "original" leaves all dashes as they were. The default is "not_cp1252". </li>
 * <li>splitAssimilations: true to tokenize "gonna", false to tokenize "gon na".  Default is true. </li>
 * <li>escapeForwardSlashAsterisk: Whether to put a backslash escape in front
 *     of / and * as the old PTB3 WSJ does for some reason (something to do
 *     with Lisp readers??). Default is false. This flag is no longer set
 *     by ptb3Escaping. </li>
 * <li>normalizeCurrency: Whether to do some awful lossy currency mappings
 *     to turn common currency characters into $, #, or "cents", reflecting
 *     the fact that nothing else appears in the old PTB3 WSJ.  (No Euro!)
 *     Default is false. (Note: The default was true through CoreNLP v3.8.0, but we're
 *     gradually inching our way towards the modern world!) This flag is no longer set
 *     by ptb3Escaping. </li>
 * <li>untokenizable: What to do with untokenizable characters (ones not
 *     known to the tokenizer).  Six options combining whether to log a
 *     warning for none, the first, or all, and whether to delete them or
 *     to include them as single character tokens in the output: noneDelete,
 *     firstDelete, allDelete, noneKeep, firstKeep, allKeep.
 *     The default is "firstDelete". </li>
 * <li>strictTreebank3: PTBTokenizer deliberately deviates from strict PTB3
 *      WSJ tokenization in two cases.  Setting this improves compatibility
 *      for those cases.  They are: (i) When an acronym is followed by a
 *      sentence end, such as "U.K." at the end of a sentence, the PTB3
 *      has tokens of "Corp" and ".", while by default PTBTokenizer duplicates
 *      the period returning tokens of "Corp." and ".", and (ii) PTBTokenizer
 *      will return numbers with a whole number and a fractional part like
 *      "5 7/8" as a single token, with a non-breaking space in the middle,
 *      while the PTB3 separates them into two tokens "5" and "7/8".
 *      (Exception: for only "U.S." the treebank does have the two tokens
 *      "U.S." and "." like our default; strictTreebank3 now does that too.)
 *      The default is false. </li>
 * <li>strictAcronym: control only the acronym portion of strictTreebank3. </li>
 * <li>strictFraction: control only the fraction portion of strictTreebank3. </li>
 * <li>splitHyphenated: whether or not to tokenize segments of hyphenated words
 *      separately ("school" "-" "aged", "frog" "-" "lipped"), keeping the exceptions
 *      in Supplementary Guidelines for ETTB 2.0 by Justin Mott, Colin Warner, Ann Bies,
 *      Ann Taylor and CLEAR guidelines (Bracketing Biomedical Text) by Colin Warner et al. (2012).
 *      Default is false, which maintains old treebank tokenizer behavior. </li>
 * <li>splitForwardSlash: [From CoreNLP 4.0] Whether to tokenize segments of slashed tokens separately
 *      ("Asian" "/" "Indian", "and" "/" "or"). Default is false. </li>
 * </ol>
 * <p>
 * A single instance of a PTBTokenizer is not thread safe, as it uses
 * a non-threadsafe JFlex object to do the processing.  Multiple
 * instances can be created safely, though.  A single instance of a
 * PTBTokenizerFactory is also not thread safe, as it keeps its
 * options in a local variable.
 *
 * @author Tim Grow (his tokenizer is a Java implementation of Professor
 *     Chris Manning's Flex tokenizer, pgtt-treebank.l)
 * @author Teg Grenager (grenager@stanford.edu)
 * @author Jenny Finkel (integrating in invertible PTB tokenizer)
 * @author Christopher Manning (redid API, added many options, maintenance)
 */
public class PTBTokenizer<T extends HasWord> extends AbstractTokenizer<T>  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(PTBTokenizer.class);

  /** The underlying lexer */
  private final PTBLexer lexer;


  /**
   * Constructs a new PTBTokenizer that returns Word tokens and which treats
   * carriage returns as normal whitespace.
   *
   * @param r The Reader whose contents will be tokenized
   * @return A PTBTokenizer that tokenizes a stream to objects of type
   *          {@link Word}
   */
  public static PTBTokenizer<Word> newPTBTokenizer(Reader r) {
    return new PTBTokenizer<>(r, new WordTokenFactory(), "invertible=false");
  }


  /**
   * Constructs a new PTBTokenizer that makes CoreLabel tokens.
   * It optionally returns carriage returns
   * as their own token. CRs come back as CoreLabels whose text is
   * the value of {@code AbstractTokenizer.NEWLINE_TOKEN}.
   *
   * @param r The Reader to read tokens from
   * @param tokenizeNLs Whether to return newlines as separate tokens
   *         (otherwise they normally disappear as whitespace)
   * @param invertible if set to true, then will produce CoreLabels which
   *         will have fields for the string before and after, and the
   *         character offsets
   * @return A PTBTokenizer which returns CoreLabel objects
   */
  public static PTBTokenizer<CoreLabel> newPTBTokenizer(Reader r, boolean tokenizeNLs, boolean invertible) {
    return new PTBTokenizer<>(r, tokenizeNLs, invertible, false, new CoreLabelTokenFactory());
  }


  /**
   * Constructs a new PTBTokenizer that optionally returns carriage returns
   * as their own token, and has a custom LexedTokenFactory.
   * If asked for, CRs come back as Words whose text is
   * the value of {@code PTBLexer.cr}.  This constructor translates
   * between the traditional boolean options of PTBTokenizer and the new
   * options String.
   *
   * @param r The Reader to read tokens from
   * @param tokenizeNLs Whether to return newlines as separate tokens
   *         (otherwise they normally disappear as whitespace)
   * @param invertible if set to true, then will produce CoreLabels which
   *         will have fields for the string before and after, and the
   *         character offsets
   * @param suppressEscaping If true, all the traditional Penn Treebank
   *         normalizations are turned off.  Otherwise, they all happen.
   * @param tokenFactory The LexedTokenFactory to use to create
   *         tokens from the text.
   */
  private PTBTokenizer(final Reader r,
                       final boolean tokenizeNLs,
                       final boolean invertible,
                       final boolean suppressEscaping,
                       final LexedTokenFactory<T> tokenFactory) {
    StringBuilder options = new StringBuilder();
    if (suppressEscaping) {
      options.append("ptb3Escaping=false");
    } else {
      options.append("ptb3Escaping=true"); // i.e., turn on all the historical PTB normalizations
    }
    if (tokenizeNLs) {
      options.append(",tokenizeNLs");
    }
    if (invertible) {
      options.append(",invertible");
    }
    lexer = new PTBLexer(r, tokenFactory, options.toString());
  }


  /**
   * Constructs a new PTBTokenizer with a custom LexedTokenFactory.
   * Many options for tokenization and what is returned can be set via
   * the options String. See the class documentation for details on
   * the options String.  This is the new recommended constructor!
   *
   * @param r The Reader to read tokens from.
   * @param tokenFactory The LexedTokenFactory to use to create
   *         tokens from the text.
   * @param options Options to the lexer.  See the extensive documentation
   *         in the class javadoc.  The String may be null or empty,
   *         which means that all traditional PTB normalizations are
   *         done.  You can pass in "ptb3Escaping=false" and have no
   *         normalizations done (that is, the behavior of the old
   *         suppressEscaping=true option).
   */
  public PTBTokenizer(final Reader r,
                      final LexedTokenFactory<T> tokenFactory,
                      final String options) {
    lexer = new PTBLexer(r, tokenFactory, options);
  }


  /**
   * Internally fetches the next token.
   *
   * @return the next token in the token stream, or null if none exists.
   */
  @SuppressWarnings({"unchecked"})
  @Override
  protected T getNext() {
    // if (lexer == null) {
    //   return null;
    // }
    try {
      return (T) lexer.next();
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
    // cdm 2007: this shouldn't be necessary: PTBLexer decides for itself whether to return CRs based on the same flag!
    // get rid of CRs if necessary
    // while (!tokenizeNLs && PTBLexer.cr.equals(((HasWord) token).word())) {
    //   token = (T)lexer.next();
    // }

    // horatio: we used to catch exceptions here, which led to broken
    // behavior and made it very difficult to debug whatever the
    // problem was.
  }

  /**
   * Returns the string literal inserted for newlines when the -tokenizeNLs
   * options is set.
   *
   * @return string literal inserted for "\n".
   */
  public static String getNewlineToken() { return NEWLINE_TOKEN; }

  /**
   * Returns a presentable version of the given PTB-tokenized text.
   * PTB tokenization splits up punctuation and does various other things
   * that makes simply joining the tokens with spaces look bad. So join
   * the tokens with space and run it through this method to produce nice
   * looking text. It's not perfect, but it works pretty well.
   * <p>
   * <b>Note:</b> If your tokens have maintained the OriginalTextAnnotation and
   * the BeforeAnnotation and the AfterAnnotation, then rather than doing
   * this you can actually precisely reconstruct the text they were made
   * from!
   *
   * @param ptbText A String in PTB3-escaped form
   * @return An approximation to the original String
   */
  public static String ptb2Text(String ptbText) {
    StringBuilder sb = new StringBuilder(ptbText.length()); // probably an overestimate
    PTB2TextLexer lexer = new PTB2TextLexer(new StringReader(ptbText));
    try {
      for (String token; (token = lexer.next()) != null; ) {
        sb.append(token);
      }
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
    return sb.toString();
  }

  /**
   * Returns a presentable version of a given PTB token. For instance,
   * it transforms -LRB- into (.
   */
  public static String ptbToken2Text(String ptbText) {
    return ptb2Text(' ' + ptbText + ' ').trim();
  }

  /**
   * Writes a presentable version of the given PTB-tokenized text.
   * PTB tokenization splits up punctuation and does various other things
   * that makes simply joining the tokens with spaces look bad. So join
   * the tokens with space and run it through this method to produce nice
   * looking text. It's not perfect, but it works pretty well.
   */
  public static long ptb2Text(Reader ptbText, Writer w) throws IOException {
    long numTokens = 0;
    PTB2TextLexer lexer = new PTB2TextLexer(ptbText);
    for (String token; (token = lexer.next()) != null; ) {
      numTokens++;
      w.write(token);
    }
    return numTokens;
  }

  private static void untok(List<String> inputFileList, List<String> outputFileList, String charset) throws IOException {
    final long start = System.nanoTime();
    long numTokens = 0;
    int sz = inputFileList.size();
    if (sz == 0) {
      Reader r = new InputStreamReader(System.in, charset);
      BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(System.out, charset));
      numTokens = ptb2Text(r, writer);
      writer.close();
    } else {
      for (int j = 0; j < sz; j++) {
        try (Reader r = IOUtils.readerFromString(inputFileList.get(j), charset)) {
          BufferedWriter writer;
          if (outputFileList == null) {
            writer = new BufferedWriter(new OutputStreamWriter(System.out, charset));
          } else {
            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFileList.get(j)), charset));
          }
          try {
            numTokens += ptb2Text(r, writer);
          } finally {
            writer.close();
          }
        }
      }
    }
    final long duration = System.nanoTime() - start;
    final double wordsPerSec = (double) numTokens / ((double) duration / 1000000000.0);
    System.err.printf("PTBTokenizer untokenized %d tokens at %.2f tokens per second.%n", numTokens, wordsPerSec);
  }

  /**
   * Returns a presentable version of the given PTB-tokenized words.
   * Pass in a List of Strings and this method will
   * join the words with spaces and call {@link #ptb2Text(String)} on the
   * output.
   *
   * @param ptbWords A list of String
   * @return A presentable version of the given PTB-tokenized words
   */
  public static String ptb2Text(List<String> ptbWords) {
    return ptb2Text(StringUtils.join(ptbWords));
  }


  /**
   * Returns a presentable version of the given PTB-tokenized words.
   * Pass in a List of Words or a Document and this method will
   * take the word() values (to prevent additional text from creeping in, e.g., POS tags),
   * and call {@link #ptb2Text(String)} on the output.
   *
   * @param ptbWords A list of HasWord objects
   * @return A presentable version of the given PTB-tokenized words
   */
  public static String labelList2Text(List<? extends HasWord> ptbWords) {
    List<String> words = new ArrayList<>();
    for (HasWord hw : ptbWords) {
      words.add(hw.word());
    }

    return ptb2Text(words);
  }


  private static void tok(List<String> inputFileList, List<String> outputFileList, String charset,
                          Pattern parseInsidePattern, Pattern filterPattern, String options,
                          boolean preserveLines, boolean oneLinePerElement, boolean dump,
                          boolean lowerCase, boolean blankLineAfterFiles) throws IOException {
    final long start = System.nanoTime();
    long numTokens = 0;
    int numFiles = inputFileList.size();
    if (numFiles == 0) {
      Reader stdin = IOUtils.readerFromStdin(charset);
      BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(System.out, charset));
      numTokens += tokReader(stdin, writer, parseInsidePattern, filterPattern, options, preserveLines, oneLinePerElement, dump, lowerCase);
      IOUtils.closeIgnoringExceptions(writer);

    } else {
      BufferedWriter out = null;
      if (outputFileList == null) {
        out = new BufferedWriter(new OutputStreamWriter(System.out, charset));
      }
      for (int j = 0; j < numFiles; j++) {
        try (Reader r = IOUtils.readerFromString(inputFileList.get(j), charset)) {
          if (outputFileList != null) {
            out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFileList.get(j)), charset));
          }
          numTokens += tokReader(r, out, parseInsidePattern, filterPattern, options, preserveLines, oneLinePerElement, dump, lowerCase);
        }
        if (blankLineAfterFiles) {
          out.newLine();
        }
        if (outputFileList != null) {
          IOUtils.closeIgnoringExceptions(out);
        }
      } // end for j going through inputFileList
      if (outputFileList == null) {
        IOUtils.closeIgnoringExceptions(out);
      }
    }

    final long duration = System.nanoTime() - start;
    final double wordsPerSec = (double) numTokens / ((double) duration / 1000000000.0);
    System.err.printf("PTBTokenizer tokenized %d tokens at %.2f tokens per second.%n", numTokens, wordsPerSec);
  }

  private static long tokReader(Reader r, BufferedWriter writer, Pattern parseInsidePattern, Pattern filterPattern, String options,
                               boolean preserveLines, boolean oneLinePerElement, boolean dump, boolean lowerCase) throws IOException {
    long numTokens = 0;
    boolean beginLine = true;
    boolean printing = (parseInsidePattern == null); // start off printing, unless you're looking for a start entity
    Matcher m = null;
    if (parseInsidePattern != null) {
      m = parseInsidePattern.matcher(""); // create once as performance hack
      // System.err.printf("parseInsidePattern is: |%s|%n", parseInsidePattern);
    }
    for (PTBTokenizer<CoreLabel> tokenizer = new PTBTokenizer<>(r, new CoreLabelTokenFactory(), options); tokenizer.hasNext(); ) {
      CoreLabel obj = tokenizer.next();
      // String origStr = obj.get(CoreAnnotations.TextAnnotation.class).replaceFirst("\n+$", ""); // DanC added this to fix a lexer bug, hopefully now corrected
      String origStr = obj.get(CoreAnnotations.TextAnnotation.class);
      String str;
      if (lowerCase) {
        str = origStr.toLowerCase(Locale.ENGLISH);
        obj.set(CoreAnnotations.TextAnnotation.class, str);
      } else {
        str = origStr;
      }
      if (m != null && m.reset(origStr).matches()) {
        printing = m.group(1).isEmpty(); // turn on printing if no end element slash, turn it off it there is
        // System.err.printf("parseInsidePattern matched against: |%s|, printing is %b.%n", origStr, printing);
        if ( ! printing) {
          // true only if matched a stop
          beginLine = true;
          if (oneLinePerElement) {
            writer.newLine();
          }
        }
      } else if (printing) {
        if (dump) {
          // after having checked for tags, change str to be exhaustive
          str = obj.toShorterString();
        }
        if (filterPattern != null && filterPattern.matcher(origStr).matches()) {
          // skip
        } else if (preserveLines) {
          if (NEWLINE_TOKEN.equals(origStr)) {
            beginLine = true;
            writer.newLine();
          } else {
            if (!beginLine) {
              writer.write(' ');
            } else {
              beginLine = false;
            }
            // writer.write(str.replace("\n", ""));
            writer.write(str);
          }
        } else if (oneLinePerElement) {
          if ( ! beginLine) {
            writer.write(' ');
          } else {
            beginLine = false;
          }
          writer.write(str);
        } else {
          writer.write(str);
          writer.newLine();
        }
      }
      numTokens++;
    }
    return numTokens;
  }


  /** This is a historical constructor that returns Word tokens.
   *  Note that Word tokens don't support the extra fields to make an invertible tokenizer.
   *
   *  @return A PTBTokenizerFactory that vends Word tokens.
   */
  public static TokenizerFactory<Word> factory() {
    return PTBTokenizerFactory.newTokenizerFactory();
  }


  /** @return A PTBTokenizerFactory that vends CoreLabel tokens. */
  public static TokenizerFactory<CoreLabel> factory(boolean tokenizeNLs, boolean invertible) {
    return PTBTokenizerFactory.newPTBTokenizerFactory(tokenizeNLs, invertible);
  }

  /** @return A PTBTokenizerFactory that vends CoreLabel tokens with default tokenization. */
  public static TokenizerFactory<CoreLabel> coreLabelFactory() {
    return coreLabelFactory("");
  }

  /** @return A PTBTokenizerFactory that vends CoreLabel tokens with default tokenization. */
  public static TokenizerFactory<CoreLabel> coreLabelFactory(String options) {
    return PTBTokenizerFactory.newPTBTokenizerFactory(new CoreLabelTokenFactory(), options);
  }

  /** Get a TokenizerFactory that does Penn Treebank tokenization.
   *  This is now the recommended factory method to use.
   *
   * @param factory A TokenFactory that determines what form of token is returned by the Tokenizer
   * @param options A String specifying options (see the class javadoc for details)
   * @param <T> The type of the tokens built by the LexedTokenFactory
   * @return A TokenizerFactory that does Penn Treebank tokenization
   */
  public static <T extends HasWord> TokenizerFactory<T> factory(LexedTokenFactory<T> factory, String options) {
    return new PTBTokenizerFactory<>(factory, options);
  }


  /** This class provides a factory which will vend instances of PTBTokenizer
   *  which wrap a provided Reader.  See the documentation for
   *  {@link PTBTokenizer} for details of the parameters and options.
   *
   *  @see PTBTokenizer
   *  @param <T> The class of the returned tokens
   */
  public static class PTBTokenizerFactory<T extends HasWord> implements TokenizerFactory<T> {

    private static final long serialVersionUID = -8859638719818931606L;

    protected final LexedTokenFactory<T> factory;
    protected String options;


    /**
     * Constructs a new TokenizerFactory that returns Word objects and
     * treats carriage returns as normal whitespace.
     * THIS METHOD IS INVOKED BY REFLECTION BY SOME OF THE JAVANLP
     * CODE TO LOAD A TOKENIZER FACTORY.  IT SHOULD BE PRESENT IN A
     * TokenizerFactory.
     *
     * @return A TokenizerFactory that returns Word objects
     */
    public static TokenizerFactory<Word> newTokenizerFactory() {
      return newPTBTokenizerFactory(new WordTokenFactory(), "invertible=false");
    }

    /**
     * Constructs a new PTBTokenizer that returns Word objects and
     * uses the options passed in.
     * THIS METHOD IS INVOKED BY REFLECTION BY SOME OF THE JAVANLP
     * CODE TO LOAD A TOKENIZER FACTORY.  IT SHOULD BE PRESENT IN A
     * TokenizerFactory.
     *
     * @param options A String of options
     * @return A TokenizerFactory that returns Word objects
     */
    public static PTBTokenizerFactory<Word> newWordTokenizerFactory(String options) {
      return new PTBTokenizerFactory<>(new WordTokenFactory(), "invertible=false," + options);
    }

    /**
     * Constructs a new PTBTokenizer that returns CoreLabel objects and
     * uses the options passed in.
     *
     * @param options A String of options. For the default, recommended
     *                options for PTB-style tokenization compatibility, pass
     *                in an empty String.
     * @return A TokenizerFactory that returns CoreLabel objects o
     */
    public static PTBTokenizerFactory<CoreLabel> newCoreLabelTokenizerFactory(String options) {
      return new PTBTokenizerFactory<>(new CoreLabelTokenFactory(), options);
    }

    /**
     * Constructs a new PTBTokenizer that uses the LexedTokenFactory and
     * options passed in.
     *
     * @param tokenFactory The LexedTokenFactory
     * @param options A String of options
     * @return A TokenizerFactory that returns objects of the type of the
     *         LexedTokenFactory
     */
    public static <T extends HasWord> PTBTokenizerFactory<T> newPTBTokenizerFactory(LexedTokenFactory<T> tokenFactory, String options) {
      return new PTBTokenizerFactory<>(tokenFactory, options);
    }

    public static PTBTokenizerFactory<CoreLabel> newPTBTokenizerFactory(boolean tokenizeNLs, boolean invertible) {
      return new PTBTokenizerFactory<>(tokenizeNLs, invertible, false, new CoreLabelTokenFactory());
    }


    // Constructors

    // This one is historical
    private PTBTokenizerFactory(boolean tokenizeNLs, boolean invertible, boolean suppressEscaping, LexedTokenFactory<T> factory) {
      this.factory = factory;
      StringBuilder optionsSB = new StringBuilder();
      if (suppressEscaping) {
        optionsSB.append("ptb3Escaping=false");
      } else {
        optionsSB.append("ptb3Escaping=true"); // i.e., turn on all the historical PTB normalizations
      }
      if (tokenizeNLs) {
        optionsSB.append(",tokenizeNLs");
      }
      if (invertible) {
        optionsSB.append(",invertible");
      }
      this.options = optionsSB.toString();
    }

    /** Make a factory for PTBTokenizers.
     *
     *  @param tokenFactory A factory for the token type that the tokenizer will return
     *  @param options Options to the tokenizer (see the class documentation for details)
     */
    private PTBTokenizerFactory(LexedTokenFactory<T> tokenFactory, String options) {
      this.factory = tokenFactory;
      this.options = options;
    }


    /** Returns a tokenizer wrapping the given Reader. */
    @Override
    public Iterator<T> getIterator(Reader r) {
      return getTokenizer(r);
    }

    /** Returns a tokenizer wrapping the given Reader. */
    @Override
    public Tokenizer<T> getTokenizer(Reader r) {
      return new PTBTokenizer<>(r, factory, options);
    }

    @Override
    public Tokenizer<T> getTokenizer(Reader r, String extraOptions) {
      if (options == null || options.isEmpty()) {
        return new PTBTokenizer<>(r, factory, extraOptions);
      } else {
        return new PTBTokenizer<>(r, factory, options + ',' + extraOptions);
      }
    }

    @Override
    public void setOptions(String options) {
      this.options = options;
    }

  } // end static class PTBTokenizerFactory


  /**
   * Command-line option specification.
   */
  private static Map<String,Integer> optionArgDefs() {
    Map<String,Integer> optionArgDefs = Generics.newHashMap();
    optionArgDefs.put("options", 1);
    optionArgDefs.put("ioFileList", 0);
    optionArgDefs.put("fileList", 0);
    optionArgDefs.put("lowerCase", 0);
    optionArgDefs.put("dump", 0);
    optionArgDefs.put("untok", 0);
    optionArgDefs.put("encoding", 1);
    optionArgDefs.put("parseInside", 1);
    optionArgDefs.put("filter", 1);
    optionArgDefs.put("preserveLines", 0);
    optionArgDefs.put("oneLinePerElement", 0);
    optionArgDefs.put("blankLineAfterFiles", 0);
    return optionArgDefs;
  }

  /**
   * Reads files given as arguments and print their tokens, by default as
   * one per line.  This is useful either for testing or to run
   * standalone to turn a corpus into a one-token-per-line file of tokens.
   * This main method assumes that the input file is in utf-8 encoding,
   * unless an encoding is specified.
   * <p>
   * Usage: {@code java edu.stanford.nlp.process.PTBTokenizer [options] filename+ }
   * <p>
   * Options:
   * <ul>
   * <li> -options options Set various tokenization options
   *       (see the documentation in the class javadoc). </li>
   * <li> -preserveLines Produce space-separated tokens, except
   *       when the original had a line break, not one-token-per-line. </li>
   * <li> -oneLinePerElement Print the tokens of an element space-separated on one line.
   *       An "element" is either a file or one of the elements matched by the
   *       parseInside regex. </li>
   * <li> -blankLineAfterFiles Put a blank line after each input file
   *      (including after just a single input file but not at the end of input from stdin). </li>
   * <li> -filter regex Delete any token that matches() (in its entirety) the given regex. </li>
   * <li> -encoding encoding Specifies a character encoding. If you do not
   *      specify one, the default is utf-8 (not the platform default). </li>
   * <li> -lowerCase Lowercase all tokens (on tokenization). </li>
   * <li> -parseInside regex Names an XML-style element or a regular expression
   *      over such elements.  The tokenizer will only tokenize inside elements
   *      that match this regex.  (This is done by regex matching, not an XML
   *      parser, but works well for simple XML documents, or other SGML-style
   *      documents, such as Linguistic Data Consortium releases, which adopt
   *      the convention that a line of a file is either XML markup or
   *      character data but never both.) </li>
   * <li> -ioFileList file* The remaining command-line arguments are treated as
   *      filenames that themselves contain lists of pairs of input-output
   *      filenames (2 column, whitespace separated). Alternatively, if there is only
   *      one filename per line, the output filename is the input filename with ".tok" appended. </li>
   * <li> -fileList file* The remaining command-line arguments are treated as
   *      filenames that contain filenames, one per line. The output of tokenization is sent to
   *      stdout. </li>
   * <li> -dump Print the whole of each CoreLabel, not just the value (word). </li>
   * <li> -untok Heuristically untokenize tokenized text. </li>
   * <li> -h, -help Print usage info. </li>
   * </ul>
   * <p>
   * A note on {@code -preserveLines}: Basically, if you use this option, your output file should have
   * the same number of lines as your input file. If not, there is a bug. But the truth of this statement
   * depends on how you count lines…. Unicode includes "line separator" and "paragraph separator" characters
   * and Unicode says that you should accept them. See e.g., http://unicode.org/standard/reports/tr13/tr13-5.html
   * <p>
   * However, Unix, Linux utilities, etc. don't recognize them and count only the traditional \n|\r|\r\n.
   * And PTBTokenizer does normalize line separation. Hence, if your input text contains, say U+2028 Line Separator
   * characters, the Unix wc utility will report more lines after tokenization than before,
   * even though line breaks have been preserved, according to Unicode. It may be useful to compare results with the
   * Perl uniwc script from https://raw.githubusercontent.com/briandfoy/Unicode-Tussle/master/script/uniwc
   * <p>
   * If it reports the same number of input and output lines, then this difference is your problem,
   * and in a certain Unicode sense, our tokenizer did indeed preserve the line count.
   * If not, please send us a bug report. At present there is no way to disable this process of Unicode separator
   * characters. If you don't want this anomaly, you'll need to either delete these two characters or to map them
   * to conventional Unix newline characters. Or to some other weirdo character.
   *
   * @param args Command line arguments
   * @throws IOException If any file I/O problem
   */
  public static void main(String[] args) throws IOException {
    Properties options = StringUtils.argsToProperties(args, optionArgDefs());
    boolean showHelp = PropertiesUtils.getBool(options, "help", false);
    showHelp = PropertiesUtils.getBool(options, "h", showHelp);
    if (showHelp) {
      log.info("Usage: java edu.stanford.nlp.process.PTBTokenizer [options]* filename*");
      log.info("  options: -h|-help|-options tokenizerOptions|-encoding encoding|-dump|");
      log.info("           -lowerCase|-preserveLines|-oneLinePerElement|-filter regex|");
      log.info("           -parseInside regex|-fileList|-ioFileList|-untok");
      return;
    }

    StringBuilder optionsSB = new StringBuilder();
    String tokenizerOptions = options.getProperty("options", null);
    if (tokenizerOptions != null) {
      optionsSB.append(tokenizerOptions);
    }
    boolean preserveLines = PropertiesUtils.getBool(options, "preserveLines", false);
    if (preserveLines) {
      optionsSB.append(",tokenizeNLs");
    }
    boolean blankLineAfterFiles = PropertiesUtils.getBool(options, "blankLineAfterFiles", false);
    boolean oneLinePerElement = PropertiesUtils.getBool(options, "oneLinePerElement", false);
    boolean inputOutputFileList = PropertiesUtils.getBool(options, "ioFileList", false);
    boolean fileList = PropertiesUtils.getBool(options, "fileList", false);
    boolean lowerCase = PropertiesUtils.getBool(options, "lowerCase", false);
    boolean dump = PropertiesUtils.getBool(options, "dump", false);
    boolean untok = PropertiesUtils.getBool(options, "untok", false);
    String charset = options.getProperty("encoding", "utf-8");
    String parseInsideValue = options.getProperty("parseInside", null);
    Pattern parseInsidePattern = null;
    if (parseInsideValue != null) {
      try {
        // We still allow space, but PTBTokenizer will change space to &nbsp; so need to also match it
        parseInsidePattern = Pattern.compile("<(/?)(?:" + parseInsideValue + ")(?:(?:\\s|\u00A0)[^>]*?)?>");
      } catch (PatternSyntaxException e) {
        // just go with null parseInsidePattern
      }
    }
    String filterValue = options.getProperty("filter", null);
    Pattern filterPattern = null;
    if (filterValue != null) {
      try {
        filterPattern = Pattern.compile(filterValue);
      } catch (PatternSyntaxException e) {
        // just go with null filterPattern
      }
    }

    // Other arguments are filenames
    String parsedArgStr = options.getProperty("",null);
    String[] parsedArgs = (parsedArgStr == null) ? null : parsedArgStr.split("\\s+");

    ArrayList<String> inputFileList = new ArrayList<>();
    ArrayList<String> outputFileList = null;
    if (parsedArgs != null) {
      if (fileList || inputOutputFileList ) {
        outputFileList = new ArrayList<>();
        for (String fileName : parsedArgs) {
          BufferedReader r = IOUtils.readerFromString(fileName, charset);
          for (String inLine; (inLine = r.readLine()) != null; ) {
            String[] fields = inLine.split("\\s+");
            inputFileList.add(fields[0]);
            if (fields.length > 1) {
              outputFileList.add(fields[1]);
            } else {
              outputFileList.add(fields[0] + ".tok");
            }
          }
          r.close();
        }
        if (fileList) {
          // We're not actually going to use the outputFileList!
          outputFileList = null;
        }
      } else {
        // Concatenate input files into a single output file
        inputFileList.addAll(Arrays.asList(parsedArgs));
      }
    }

    if (untok) {
      untok(inputFileList, outputFileList, charset);
    } else {
      tok(inputFileList, outputFileList, charset, parseInsidePattern, filterPattern, optionsSB.toString(),
              preserveLines, oneLinePerElement, dump, lowerCase, blankLineAfterFiles);
    }
  } // end main

} // end PTBTokenizer
