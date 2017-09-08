package old.edu.stanford.nlp.process;

// Stanford English Tokenizer -- a deterministic, fast high-quality tokenizer
// Copyright (c) 2002-2009 The Board of Trustees of
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
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
//
// For more information, bug reports, fixes, contact:
//    Christopher Manning
//    Dept of Computer Science, Gates 1A
//    Stanford CA 94305-9010
//    USA
//    java-nlp-support@lists.stanford.edu
//    http://nlp.stanford.edu/software/


import java.io.*;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;
import java.util.regex.Pattern;

import old.edu.stanford.nlp.ling.CoreLabel;
import old.edu.stanford.nlp.ling.Word;
import old.edu.stanford.nlp.ling.HasWord;
import old.edu.stanford.nlp.objectbank.TokenizerFactory;
import old.edu.stanford.nlp.util.StringUtils;
import old.edu.stanford.nlp.util.Timing;
import old.edu.stanford.nlp.io.IOUtils;


/**
 * Tokenizer implementation that conforms to the Penn Treebank tokenization
 * conventions.
 * This tokenizer is a Java implementation of Professor Chris Manning's Flex
 * tokenizer, pgtt-treebank.l.  It reads raw text and outputs
 * tokens as edu.stanford.nlp.trees.Words in the Penn treebank format. It can
 * optionally return carriage returns as tokens.
 *
 * @author Tim Grow
 * @author Teg Grenager (grenager@stanford.edu)
 * @author Christopher Manning
 * @author Jenny Finkel (integrating in invertible PTB tokenizer)
 */
public class PTBTokenizer<T extends HasWord> extends AbstractTokenizer<T> {

  // the underlying lexer
  private PTBLexer lexer;


  /**
   * Constructs a new PTBTokenizer that returns Word tokens and which treats
   * carriage returns as normal whitespace.
   *
   * @param r The Reader whose contents will be tokenized
   * @return A PTBTokenizer that tokenizes a stream to objects of type
   *          {@link Word}
   */
  public static PTBTokenizer<Word> newPTBTokenizer(Reader r) {
    return newPTBTokenizer(r, false);
  }

  /**
   * Constructs a new PTBTokenizer that optionally returns newlines
   * as their own token. NLs come back as Words whose text is
   * the value of <code>PTBLexer.NEWLINE_TOKEN</code>.
   *
   * @param r The Reader to read tokens from
   * @param tokenizeNLs Whether to return newlines as separate tokens
   *         (otherwise they normally disappear as whitespace)
   * @return A PTBTokenizer which returns Word tokens
   */
  public static PTBTokenizer<Word> newPTBTokenizer(Reader r, boolean tokenizeNLs) {
    return new PTBTokenizer<Word>(r, tokenizeNLs, false, false, new WordTokenFactory());
  }


  /**
   * Constructs a new PTBTokenizer that makes CoreLabel tokens.
   * It optionally returns carriage returns
   * as their own token. CRs come back as Words whose text is
   * the value of <code>PTBLexer.NEWLINE_TOKEN</code>.
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
    return new PTBTokenizer<CoreLabel>(r, tokenizeNLs, invertible, false, new CoreLabelTokenFactory());
  }


  /**
   * Constructs a new PTBTokenizer that optionally returns carriage returns
   * as their own token, and has a custom LexedTokenFactory.
   * If asked for, CRs come back as Words whose text is
   * the value of <code>PTBLexer.cr</code>.  This constructor translates
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
   * the options String. This is the new recommended constructor!
   *
   * @param r The Reader to read tokens from
   * @param tokenFactory The LexedTokenFactory to use to create
   *         tokens from the text.
   * @param options Options to the lexer.  See the extensive documentation
   *         in PTBLexer.  The String may be null or empty, which means that
   *         all traditional PTB normalizations are done.  You can pass in
   *         "ptb3Escaping=false" and have no normalizations done (that is,
   *         the behavior of the old suppressEscaping=true option).
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
  @Override
  @SuppressWarnings("unchecked")
  protected T getNext() {
    // if (lexer == null) {
    //   return null;
    // }
    T token = null;
    try {
      token = (T) lexer.next();
      // cdm 2007: this shouldn't be necessary: PTBLexer decides for itself whether to return CRs based on the same flag!
      // get rid of CRs if necessary
      // while (!tokenizeNLs && PTBLexer.cr.equals(((HasWord) token).word())) {
      //   token = (T)lexer.next();
      // }
    } catch (Exception e) {
      nextToken = null;
      // do nothing, return null
    }
    return token;
  }


  /**
   * Returns a presentable version of the given PTB-tokenized text.
   * PTB tokenization splits up punctuation and does various other things
   * that makes simply joining the tokens with spaces look bad. So join
   * the tokens with space and run it through this method to produce nice
   * looking text. It's not perfect, but it works pretty well.
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
      e.printStackTrace();
    }
    return (sb.toString());
  }

  /**
   * Returns a presentable version of a given PTB token. For instance,
   * it transforms -PTB- into (.
   */
  public static String ptbToken2Text(String ptbText) {
    return ptb2Text(" "+ptbText+" ").trim();
  }

  /**
   * Writes a presentable version of the given PTB-tokenized text.
   * PTB tokenization splits up punctuation and does various other things
   * that makes simply joining the tokens with spaces look bad. So join
   * the tokens with space and run it through this method to produce nice
   * looking text. It's not perfect, but it works pretty well.
   */
  public static int ptb2Text(Reader ptbText, Writer w) throws IOException {
    int numTokens = 0;
    PTB2TextLexer lexer = new PTB2TextLexer(ptbText);
    for (String token; (token = lexer.next()) != null; ) {
      numTokens++;
      w.write(token);
    }
    return numTokens;
  }

  private static void untok(List<String> inputFileList, List<String> outputFileList, String charset) throws IOException {
    Timing t = new Timing();
    int numTokens = 0;
    int sz = inputFileList.size();
    if (sz == 0) {
      Reader r = new InputStreamReader(System.in, charset);
      PrintWriter out = new PrintWriter(System.out, true);
      numTokens = ptb2Text(r, out);
    } else {
      for (int j = 0; j < sz; j++) {
        Reader r = IOUtils.readReaderFromString(inputFileList.get(j), charset);
        PrintWriter out;
        if (outputFileList == null) {
          out = new PrintWriter(System.out, true);
        } else {
          out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFileList.get(j)), charset)), true);
        }
        numTokens += ptb2Text(r, out);
        out.close();
      }
    }
    long millis = t.stop();
    double wordspersec = numTokens / (((double) millis) / 1000);
    NumberFormat nf = new DecimalFormat("0.00"); // easier way!
    System.err.println("PTBTokenizer untokenized " + numTokens + " tokens at " +
                       nf.format(wordspersec) + " tokens per second.");
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
   * join the words with spaces and call {@link #ptb2Text(String)} on the
   * output. This method will take the word() values to prevent additional
   * text from creeping in (e.g., POS tags).
   *
   * @param ptbWords A list of HasWord objects
   * @return A presentable version of the given PTB-tokenized words
   */
  public static String labelList2Text(List<? extends HasWord> ptbWords) {
    List<String> words = new ArrayList<String>();
    for (HasWord hw : ptbWords) {
      words.add(hw.word());
    }

    return ptb2Text(words);
  }


  private static void tok(List<String> inputFileList, List<String> outputFileList, String charset, Pattern parseInsideBegin, Pattern parseInsideEnd, String options, boolean preserveLines, boolean dump) throws IOException {
    Timing t = new Timing();
    int numTokens = 0;
    int sz = inputFileList.size();
    if (sz == 0) {
      Reader r = new InputStreamReader(System.in, charset);
      PrintWriter out = new PrintWriter(System.out, true);
      numTokens += tokReader(r, out, parseInsideBegin, parseInsideEnd, options, preserveLines, dump);
    } else {
      for (int j = 0; j < sz; j++) {
        Reader r = IOUtils.readReaderFromString(inputFileList.get(j), charset);
        PrintWriter out;
        if (outputFileList == null) {
          out = new PrintWriter(System.out, true);
        } else {
          out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFileList.get(j)), charset)), true);
        }

        numTokens += tokReader(r, out, parseInsideBegin, parseInsideEnd, options, preserveLines, dump);
        r.close();
        if (outputFileList != null) out.close();
      } // end for j going through inputFileList
    }
    long millis = t.stop();
    double wordspersec = numTokens / (((double) millis) / 1000);
    NumberFormat nf = new DecimalFormat("0.00"); // easier way!
    System.err.println("PTBTokenizer tokenized " + numTokens + " tokens at " +
                       nf.format(wordspersec) + " tokens per second.");
  }

  private static int tokReader(Reader r, PrintWriter out, Pattern parseInsideBegin, Pattern parseInsideEnd, String options, boolean preserveLines, boolean dump) {
    int numTokens = 0;
    PTBTokenizer<CoreLabel> tokenizer = new PTBTokenizer<CoreLabel>(r, new CoreLabelTokenFactory(), options);
    boolean printing = parseInsideBegin == null; // start off printing, unless you're looking for a start entity
    boolean beginLine = true;
    while (tokenizer.hasNext()) {
      CoreLabel obj = tokenizer.next();
      String str = obj.word();

      if (parseInsideBegin != null && parseInsideBegin.matcher(str).matches()) {
        printing = true;
      } else if (parseInsideEnd != null && parseInsideEnd.matcher(str).matches()) {
        printing = false;
      } else if (printing) {
        if (dump) {
          // after having checked for tags, change str to be exhaustive
          str = obj.toString();
        }
        if (preserveLines) {
          if (PTBLexer.NEWLINE_TOKEN.equals(str)) {
            beginLine = true;
            out.println();
          } else {
            if ( ! beginLine) {
              out.print(" ");
            } else {
              beginLine = false;
            }
            out.print(str);
          }
        } else {
          out.println(str);
        }
      }
      numTokens++;
    }
    return numTokens;
  }


  public static TokenizerFactory<Word> factory() {
    return PTBTokenizerFactory.newTokenizerFactory();
  }


  public static <T extends HasWord> TokenizerFactory<T> factory(boolean tokenizeNLs, LexedTokenFactory<T> factory) {
    return new PTBTokenizerFactory<T>(tokenizeNLs, false, false, factory);
  }

  public static TokenizerFactory<CoreLabel> factory(boolean tokenizeNLs, boolean invertible) {
    return PTBTokenizerFactory.newPTBTokenizerFactory(tokenizeNLs, invertible);
  }

  public static <T extends HasWord> TokenizerFactory<T> factory(LexedTokenFactory<T> factory, String options) {
    return new PTBTokenizerFactory<T>(factory, options);

  }


  public static class PTBTokenizerFactory<T extends HasWord> implements TokenizerFactory<T> {

    protected LexedTokenFactory<T> factory;
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
      return newPTBTokenizerFactory(new WordTokenFactory(), "");
    }

    /**
     * Constructs a new PTBTokenizer that optionally returns carriage returns
     * as their own token.
     *
     * @param tokenizeNLs If true, newlines come back as Words whose text is
     *    the value of <code>PTBLexer.NEWLINE_TOKEN</code>.
     * @return A TokenizerFactory that returns Word objects
     */
    public static PTBTokenizerFactory<Word> newPTBTokenizerFactory(boolean tokenizeNLs) {
      return new PTBTokenizerFactory<Word>(tokenizeNLs, false, false, new WordTokenFactory());
    }

    /**
     * Constructs a new PTBTokenizer that returns Word objects and
     * uses the options passed in.
     *
     * @param options A String of options
     * @return A TokenizerFactory that returns Word objects
     */
    public static PTBTokenizerFactory<Word> newWordTokenizerFactory(String options) {
      return new PTBTokenizerFactory<Word>(new WordTokenFactory(), options);
    }

    /**
     * Constructs a new PTBTokenizer that returns CoreLabel objects and
     * uses the options passed in.
     *
     * @param options A String of options
     * @return A TokenizerFactory that returns CoreLabel objects o
     */
    public static PTBTokenizerFactory<CoreLabel> newCoreLabelTokenizerFactory(String options) {
      return new PTBTokenizerFactory<CoreLabel>(new CoreLabelTokenFactory(), options);
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
      return new PTBTokenizerFactory<T>(tokenFactory, options);
    }

    public static PTBTokenizerFactory<CoreLabel> newPTBTokenizerFactory(boolean tokenizeNLs, boolean invertible) {
      return new PTBTokenizerFactory<CoreLabel>(tokenizeNLs, invertible, false, new CoreLabelTokenFactory());
    }


    // Constructors

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


    private PTBTokenizerFactory(LexedTokenFactory<T> tokenFactory, String options) {
      this.factory = tokenFactory;
      this.options = options;
    }


    public Iterator<T> getIterator(Reader r) {
      return getTokenizer(r);
    }

    public Tokenizer<T> getTokenizer(Reader r) {
      return new PTBTokenizer<T>(r, factory, options);
    }

  } // end static class PTBTokenizerFactory


  /**
   * Reads files named as arguments and print their tokens, by default as
   * one per line.  This is useful either for testing or to run
   * standalone to turn a corpus into a one-token-per-line file of tokens.
   * This main method assumes that the input file is in utf-8 encoding,
   * unless it is specified.
   * <p/>
   * Usage: <code>
   * java edu.stanford.nlp.process.PTBTokenizer [options] filename+
   * </code>
   * <p/>
   * Options:
   * <ul>
   * <li> -options options Set various tokenization options
   *       (see the documentation in PTBLexer)
   * <li> -preserveLines Produce space-separated tokens, except
   *       when the original had a line break, not one-token-per-line
   * <li> -charset charset Specifies a character encoding
   * <li> -parseInside regex Names an XML-style tag or a regular expression
   *      over such elements.  The tokenizer will only tokenize inside element
   *      that match this name.  (This is done by regex matching, not an XML
   *      parser, but works well for simply XML documents, or other SGML-style
   *      documents, such as Linguistic Data Consortium releases.)
   * <li> -ioFileList file* The remaining command-line arguments are treated as
   *      filenames that themselves contain lists of pairs of input-output
   *      filenames (2 column, whitespace separated).
   * <li> -dump Print the whole of each CoreLabel, not just the value (word)
   * <li> -untok Heuristically untokenize tokenized text
   * <li>-h Print usage info
   * </ul>
   *
   * @param args Command line arguments
   * @throws IOException If any file I/O problem
   */
  public static void main(String[] args) throws IOException {
    int i = 0;
    String charset = "utf-8";
    Pattern parseInsideBegin = null;
    Pattern parseInsideEnd = null;
    StringBuilder optionsSB = new StringBuilder();
    boolean preserveLines = false;
    boolean inputOutputFileList = false;
    boolean dump = false;
    boolean untok = false;

    while (i < args.length && args[i].charAt(0) == '-') {
      if ("-options".equals(args[i])) {
        i++;
        optionsSB.append(',');
        optionsSB.append(args[i]);
      } else if ("-preserveLines".equals(args[i])) {
        optionsSB.append(",tokenizeNL");
        preserveLines = true;
      } else if ("-dump".equals(args[i])) {
        dump = true;
      } else if ("-ioFileList".equals(args[i])) {
        inputOutputFileList = true;
      } else if ("-charset".equals(args[i]) && i < args.length - 1) {
        i++;
        charset = args[i];
      } else if ("-parseInside".equals(args[i]) && i < args.length - 1) {
        i++;
        try {
          parseInsideBegin = Pattern.compile("<(?:" + args[i] + ")[^>]*?>");
          parseInsideEnd = Pattern.compile("</(?:" + args[i] + ")[^>]*?>");
        } catch (Exception e) {
          parseInsideBegin = null;
          parseInsideEnd = null;
        }
      } else if ("-untok".equals(args[i])) {
        untok = true;
      } else if ("-h".equals(args[i]) || "-help".equals(args[i]) || "--help".equals(args[i])) {
        System.err.println("usage: java edu.stanford.nlp.process.PTBTokenizer [options]* filename*");
        System.err.println("  options: -preserveLines|-dump|-ioFileList|-charset|-parseInside elementRegex|-options options|-h");
        return;  // exit if they asked for help in options
      } else {
        System.err.println("Unknown option: " + args[i]);
      }
      i++;
    }

    ArrayList<String> inputFileList = new ArrayList<String>();
    ArrayList<String> outputFileList = null;

    if (inputOutputFileList) {
      outputFileList = new ArrayList<String>();
      for (int j = i; j < args.length; j++) {
        BufferedReader r = new BufferedReader(
          new InputStreamReader(new FileInputStream(args[j]), charset));
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
    } else {
      inputFileList.addAll(Arrays.asList(args).subList(i, args.length));
    }

    if (untok) {
      untok(inputFileList, outputFileList, charset);
    } else {
      tok(inputFileList, outputFileList, charset, parseInsideBegin, parseInsideEnd, optionsSB.toString(), preserveLines, dump);
    }
  } // end main

} // end PTBTokenizer
