package edu.stanford.nlp.process;


import static edu.stanford.nlp.trees.international.pennchinese.ChineseUtils.WHITEPLUS;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Serializable;
import java.io.StringReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.objectbank.ObjectBank;
import edu.stanford.nlp.trees.international.pennchinese.ChineseUtils;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.StringUtils;

/**
 * Convert a Chinese Document into a List of sentence Strings.
 *
 * @author Pi-Chuan Chang
 */
public class ChineseDocumentToSentenceProcessor implements Serializable {

  // todo: This class is a mess. We should try to get it out of core

  private static final long serialVersionUID = 4054964767812217460L;

  private static final Set<Character> fullStopsSet = Generics.newHashSet(Arrays.asList(new Character[]{'\u3002', '\uff01', '\uff1f', '!', '?'}));
  // not \uff0e . (too often separates English first/last name, etc.)

  private static final Set<Character> rightMarkSet = Generics.newHashSet(Arrays.asList(new Character[]{'\u201d', '\u2019', '\u300b', '\u300f', '\u3009', '\u300d', '\uff1e', '\uff07', '\uff09', '\'', '"', ')', ']', '>'}));

  // private final String normalizationTableFile;

  private final String encoding = "UTF-8";
  private final List<Pair<String,String>> normalizationTable;


  public ChineseDocumentToSentenceProcessor() {
    this(null);
  }

  static final Pattern PAIR_PATTERN = Pattern.compile("([^\\s]+)\\s+([^\\s]+)");

  /** @param normalizationTableFile A file listing character pairs for
   *     normalization.  Currently the normalization table must be in UTF-8.
   *     If this parameter is <code>null</code>, the default normalization
   *     of the zero-argument constructor is used.
   */
  public ChineseDocumentToSentenceProcessor(String normalizationTableFile) {
    // this.normalizationTableFile = normalizationTableFile;
    if (normalizationTableFile != null) {
      normalizationTable = new ArrayList<Pair<String,String>>();
      for (String line : ObjectBank.getLineIterator(new File(normalizationTableFile), encoding)) {
        Matcher pairMatcher = PAIR_PATTERN.matcher(line);
        if (pairMatcher.find()) {
          normalizationTable.add(new Pair<String,String>(pairMatcher.group(1),pairMatcher.group(2)));
        } else {
          System.err.println("Didn't match: "+line);
        }
      }
    } else {
      normalizationTable = null;
    }
  }
  /*
  public ChineseDocumentToSentenceProcessor(String normalizationTableFile, String encoding) {
    System.err.println("WARNING: ChineseDocumentToSentenceProcessor ignores normalizationTableFile argument!");
    System.err.println("WARNING: ChineseDocumentToSentenceProcessor ignores encoding argument!");
    // encoding is never read locally
    this.encoding = encoding;
  }
  */


  /** This should now become disused, and other people should call
   *  ChineseUtils directly!  CDM June 2006.
   */
  public String normalization(String in) {
    //System.err.println("BEFOR NORM: "+in);
    String norm = ChineseUtils.normalize(in);
    String out = normalize(norm);
    //System.err.println("AFTER NORM: "+out);
    return out;
  }

  private static final Pattern WHITEPLUS_PATTERN = Pattern.compile(WHITEPLUS);
  private static final Pattern START_WHITEPLUS_PATTERN = Pattern.compile("^" + WHITEPLUS);
  private static final Pattern END_WHITEPLUS_PATTERN = Pattern.compile(WHITEPLUS + "$");

  private String normalize(String inputString) {
    if (normalizationTable == null) {
      return inputString;
    }

    Pattern replacePattern = WHITEPLUS_PATTERN;
    Matcher replaceMatcher = replacePattern.matcher(inputString);
    inputString = replaceMatcher.replaceAll(" ");

    for (Pair<String,String> p : normalizationTable) {
      replacePattern = Pattern.compile(p.first(), Pattern.LITERAL);
      replaceMatcher = replacePattern.matcher(inputString);
      String escape = p.second();
      if (escape.equals("$")) {escape="\\$";}
      inputString = replaceMatcher.replaceAll(escape);
    }
    return inputString;
  }


  /** usage: java ChineseDocumentToSentenceProcessor [-segmentIBM]
   *  -file filename [-encoding encoding]
   *  <p>
   *  The -segmentIBM option is for IBM GALE-specific splitting of an
   *  XML element into sentences.
   */
  public static void main(String[] args) throws IOException {
    //String encoding = "GB18030";
    Properties props = StringUtils.argsToProperties(args);
    // System.err.println("Here are the properties:");
    // props.list(System.err);
    boolean alwaysAddS = props.containsKey("alwaysAddS");
    ChineseDocumentToSentenceProcessor cp;
    if (! props.containsKey("file")) {
      System.err.println("usage: java ChineseDocumentToSentenceProcessor [-segmentIBM] -file filename [-encoding encoding]");
      return;
    }
    cp = new ChineseDocumentToSentenceProcessor();
    if (props.containsKey("encoding")) {
      System.err.println("WARNING: for now the default encoding is "+cp.encoding+". It's not changeable for now");
    }
    String input = IOUtils.slurpFileNoExceptions(props.getProperty("file"),
                                                     cp.encoding);
    // String input = StringUtils.slurpGBURLNoExceptions(new URL(props.getProperty("file")));

    if (props.containsKey("segmentIBM")) {
      Tokenizer<Word> tok = WhitespaceTokenizer.
        newWordWhitespaceTokenizer(new StringReader(input), true);
      String parseInside = props.getProperty("parseInside");
      if (parseInside == null) parseInside = "";

      Pattern p1, p2, p3, p4;
      PrintWriter pw = new PrintWriter(new OutputStreamWriter(System.out, cp.encoding), true);
      StringBuilder buff = new StringBuilder();
      StringBuilder sgmlbuff = new StringBuilder();
      String lastSgml = "";
      try {
        p1 = Pattern.compile("<.*>");
        p2 = Pattern.compile("\uFEFF?<[\\p{Alpha}]+");
        p3 = Pattern.compile("[A-Za-z0-9=\"]+>");
        p4 = Pattern.compile("<(?:" + parseInside + ")[ >]");
      } catch (Exception e) {
        e.printStackTrace();
        return;
      }
      boolean inSGML = false;
      int splitItems = 0;
      int numAdded = 0;
      while (tok.hasNext()) {
        String s = tok.next().word();
        // pw.println("The token is |" + s + "|");
        if (p2.matcher(s).matches()) {
          inSGML = true;
          sgmlbuff.append(s).append(" ");
        } else if (p1.matcher(s).matches() || inSGML && p3.matcher(s).matches() || "\n".equals(s)) {
          inSGML = false;
          if (buff.toString().trim().length() > 0) {
            // pw.println("Dumping sentences");
            // pw.println("Buff is " + buff);
            boolean processIt = false;
            if (parseInside.equals("")) {
              processIt = true;
            } else if (p4.matcher(lastSgml).find()) {
              processIt = true;
            }
            if (processIt) {
              List<String> sents = ChineseDocumentToSentenceProcessor.fromPlainText(buff.toString(), true);
              // pw.println("Sents is " + sents);
              // pw.println();
              if (alwaysAddS || sents.size() > 1) {
                int i = 1;
                for (String str : sents) {
                  pw.print("<s id=\"" + i + "\">");
                  pw.print(str);
                  pw.println("</s>");
                  i++;
                }
                if (sents.size() > 1) {
                  splitItems++;
                  numAdded += sents.size() - 1;
                }
              } else if (sents.size() == 1) {
                pw.print(sents.get(0));
              }
            } else {
              pw.print(buff);
            }
            buff = new StringBuilder();
          }
          sgmlbuff.append(s);
          // pw.println("sgmlbuff is " + sgmlbuff);
          pw.print(sgmlbuff);
          lastSgml = sgmlbuff.toString();
          sgmlbuff = new StringBuilder();
        } else {
          if (inSGML) {
            sgmlbuff.append(s).append(" ");
          } else {
            buff.append(s).append(" ");
          }
          // pw.println("Buff is now |" + buff + "|");
        }
      } // end while (tok.hasNext()) {
      // empty remaining buffers
      pw.flush();
      pw.close();
      System.err.println("Split " + splitItems + " segments, adding " +
                         numAdded + " sentences.");
    } else {
      List<String> sent = cp.fromHTML(input);
      PrintWriter pw = new PrintWriter(new OutputStreamWriter(System.err, cp.encoding), true);

      for (String a : sent) {
        pw.println(a);
      }
    }
  }


  /**
   * Strip off HTML tags before processing.
   * Only the simplest tag stripping is implemented.
   *
   * @param inputString Chinese document text which contains HTML tags
   * @return a List of sentence strings
   */
  public static List<String> fromHTML(String inputString) throws IOException {
    //HTMLParser parser = new HTMLParser();
    //return fromPlainText(parser.parse(inputString));
    List<String> ans = new ArrayList<String>();
    MyHTMLParser parser = new MyHTMLParser();
    List<String> sents = parser.parse(inputString);
    for (String s : sents) {
      ans.addAll(fromPlainText(s));
    }
    return ans;
  }


  /**
   * @param contentString Chinese document text
   * @return a List of sentence strings
   * @throws IOException
   */
  public static List<String> fromPlainText(String contentString) throws IOException {
    return fromPlainText(contentString, false);
  }

  public static List<String> fromPlainText(String contentString, boolean segmented) throws IOException {
    if (segmented) {
      contentString = ChineseUtils.normalize(contentString,
                                             ChineseUtils.LEAVE,
                                             ChineseUtils.ASCII);
    } else {
      contentString = ChineseUtils.normalize(contentString,
                                             ChineseUtils.FULLWIDTH,
                                             ChineseUtils.ASCII);
    }

    String sentenceString = "";

    char[] content = contentString.toCharArray();
    boolean sentenceEnd = false;
    List<String> sentenceList = new ArrayList<String>();

    int lastCh = -1;
    for (Character c : content) {
      // EncodingPrintWriter.out.println("Char is |" + c + "|", "UTF-8");
      String newChar = c.toString();

      if (sentenceEnd == false) {
        if (segmented && fullStopsSet.contains(c) &&
            (lastCh == -1 || Character.isSpaceChar(lastCh))) {
          // require it to be a standalone punctuation mark -- cf. URLs
          sentenceString += newChar;
          sentenceEnd = true;
        } else if ( ! segmented && fullStopsSet.contains(c)) {
          // EncodingPrintWriter.out.println("  End of sent char", "UTF-8");
          sentenceString += newChar;
          sentenceEnd = true;
        } else {
          sentenceString += newChar;
        }
      } else { // sentenceEnd == true
        if (rightMarkSet.contains(c)) {
          sentenceString += newChar;
          // EncodingPrintWriter.out.println("  Right mark char", "UTF-8");
        } else if (newChar.matches("\\s")) {
          sentenceString += newChar;
        } else if (fullStopsSet.contains(c)) {
          // EncodingPrintWriter.out.println("  End of sent char (2+)", "UTF-8");
          sentenceString += newChar;
        } else { // otherwise
          if (sentenceString.length() > 0) {
            sentenceEnd = false;
          }
          sentenceString = removeWhitespace(sentenceString, segmented);
          if (sentenceString.length() > 0) {
            //System.err.println("<<< "+sentenceString+" >>>");
            sentenceList.add(sentenceString);
          }
          sentenceString = "";
          sentenceString += newChar;
        }
      }
      lastCh = c.charValue();
    } // end for (Character c : content)

    sentenceString = removeWhitespace(sentenceString, segmented);
    if (sentenceString.length() > 0) {
      //System.err.println("<<< "+sentenceString+" >>>");
      sentenceList.add(sentenceString);
    }
    return sentenceList;
  }

  /** In non-segmented mode, all whitespace is removed,
   *  in segmented mode only leading and trailing whitespace goes away.
   *
   */
  private static String removeWhitespace(String str, boolean segmented) {
    if (str.length() > 0) {
      //System.out.println("Add: "+sentenceString);
      Pattern replacePattern = START_WHITEPLUS_PATTERN;
      Matcher replaceMatcher = replacePattern.matcher(str);
      str = replaceMatcher.replaceAll("");
      replacePattern = END_WHITEPLUS_PATTERN;
      replaceMatcher = replacePattern.matcher(str);
      str = replaceMatcher.replaceAll("");

      if ( ! segmented) {
        replacePattern = WHITEPLUS_PATTERN;
        replaceMatcher = replacePattern.matcher(str);
        str = replaceMatcher.replaceAll("");
      }
    }
    return str;
  }



  static class MyHTMLParser extends HTMLEditorKit.ParserCallback {

    protected StringBuffer textBuffer;
    protected List<String> sentences;
    protected String title;
    protected boolean isTitle;
    protected boolean isBody;
    protected boolean isScript;
    protected boolean isBreak;

    public MyHTMLParser() {
      super();
      title = "";
      isTitle = false;
      isBody = false;
      isScript = false;
      isBreak = false;
    }


    @Override
    public void handleText(char[] data, int pos) {
      if (data.length == 0) return;

      if (isTitle) {
        title = new String(data);
      } else if (isBody && !isScript) {
        //textBuffer.append(data).append(" ");
      }
      //if (isBreak) {
      if (true) {
        textBuffer.append(data);
        String text = textBuffer.toString();
        text = text.replaceAll("\u00a0","");
        text = text.trim();
        if (text.length()==0) return;


        sentences.add(text);
        textBuffer = new StringBuffer(500);
      }
    }

    /**
     * Sets a flag if the start tag is the "TITLE" element start tag.
     */
    @Override
    public void handleStartTag(HTML.Tag tag, MutableAttributeSet attrSet, int pos) {
      if (tag == HTML.Tag.TITLE) {
        isTitle = true;
      } else if (tag == HTML.Tag.BODY) {
        isBody = true;
      } else if (tag == HTML.Tag.SCRIPT) {
        isScript = true;
      }

      isBreak = tag.breaksFlow();
    }

    /**
     * Sets a flag if the end tag is the "TITLE" element end tag
     */
    @Override
    public void handleEndTag(HTML.Tag tag, int pos) {
      if (tag == HTML.Tag.TITLE) {
        isTitle = false;
      } else if (tag == HTML.Tag.BODY) {
        isBody = false;
      } else if (tag == HTML.Tag.SCRIPT) {
        isScript = false;
      }
    }

    public List<String> parse(URL url) throws IOException {
      return (parse(IOUtils.slurpURL(url)));
    }

    public List<String> parse(Reader r) throws IOException {
      return parse(IOUtils.slurpReader(r));
    }

    /**
     * The parse method that actually does the work.
     * Now it first gets rid of singleton tags before running.
     * @throws IOException
     */
    public List<String> parse(String text) throws IOException {
      text = text.replaceAll("/>", ">");
      text = text.replaceAll("<\\?","<");
      StringReader r = new StringReader(text);
      textBuffer = new StringBuffer(200);
      sentences = new ArrayList<String>();
      new ParserDelegator().parse(r, this, true);
      return sentences;
    }

    public String title() {
      return title;
    }

    /*
    public static void main(String[] args) throws IOException {
      MyHTMLParser parser = new MyHTMLParser();
      String input = StringUtils.slurpGBURLNoExceptions(new URL(args[0]));
      List<String> result = parser.parse(input);
      PrintWriter orig = new PrintWriter("file.orig");
      PrintWriter parsed = new PrintWriter("file.parsed");
      System.err.println("output to file.orig");
      orig.println(input);
      for (String s : result) {
        System.err.println("output to file.parsed");
        parsed.println(s);
        parsed.println("-----------------------------------------");
      }
      orig.close();
      parsed.close();
    }
    */

  }


} // end class ChineseDocumentToSentenceProcessor
