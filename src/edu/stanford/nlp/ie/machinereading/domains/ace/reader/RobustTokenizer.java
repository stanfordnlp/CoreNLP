/*
 * RobustTokenizer.java
 * Performs tokenization of natural language English text, following ACE data
 * Use the method tokenize() for smart tokenization
 * @author Mihai
 */

package edu.stanford.nlp.ie.machinereading.domains.ace.reader; 
import edu.stanford.nlp.util.logging.Redwood;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.FileReader;
import java.io.BufferedReader;

import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.process.AbstractTokenizer;
import edu.stanford.nlp.util.Generics;

public class RobustTokenizer<T extends Word> extends AbstractTokenizer<Word>  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(RobustTokenizer.class);
  
  /** Buffer to tokenize */
  String buffer;

  /** The set of known abbreviations */
  private AbbreviationMap mAbbreviations;
  
  public final static int MAX_MULTI_WORD_SIZE = 20;

  // basic tokens
  public final static String DOT         = block("\\.");
  public final static String DOTDOT      = block("\\:");
  public final static String APOSTROPHE  = block("\\'");
  public final static String SLASH       = block("\\/");
  public final static String UNDERSCORE  = block("\\_");
  public final static String MINUS       = block("\\-");
  public final static String PLUS        = block("\\+");
  public final static String COMMA       = block("\\,");
  public final static String DOTCOMMA    = block("\\;");
  public final static String QUOTES      = block(or("\\\"", "\\'\\'", "\\'", "\\`\\`", "\\`"));
  public final static String DOUBLE_QUOTES = block(or("\\\"" , "\\'\\'")); 
  public final static String LRB         = block("\\(");
  public final static String RRB         = block("\\)");
  public final static String LCB         = block("\\{");
  public final static String RCB         = block("\\}");
  public final static String GREATER     = block("\\>");
  public final static String LOWER       = block("\\<");
  public final static String AMPERSAND   = block("\\&");
  public final static String AT          = block("\\@");
  public final static String HTTP        = block("[hH][tT][tT][pP]\\:\\/\\/");

  // basic sequences
  public final static String WHITE_SPACE = block("\\s");
  public final static String DIGIT       = block("\\d");
  public final static String LETTER      = block("[a-zA-Z]");
  public final static String UPPER       = block("[A-Z]");
  public final static String SIGN        = or(MINUS,PLUS);

  // numbers
  public final static String FULLNUM = 
    block(
        zeroOrOne(SIGN) +
        oneOrMore(DIGIT) +
        zeroOrMore(
            zeroOrOne(or(DOT, COMMA, SLASH)) +
            oneOrMore(DIGIT)));
  public final static String DECNUM       = block(DOT + oneOrMore(DIGIT));
  public final static String NUM          = or(FULLNUM, DECNUM);    

  // date and time
  public final static String DATE = 
    block(oneOrMore(DIGIT) + SLASH +
        oneOrMore(DIGIT) + SLASH +
        oneOrMore(DIGIT));
  public final static String TIME = 
    block(oneOrMore(DIGIT) + 
        oneOrMore(block(
            DOTDOT + 
            oneOrMore(DIGIT))));

  // punctuation marks
  public final static String PUNC = 
    or(QUOTES, 
        block(MINUS + oneOrMore(MINUS)),
        block(DOT + oneOrMore(DOT)));

  // words
  public final static String LETTERS     = oneOrMore(LETTER);
  public final static String BLOCK       = or(NUM, LETTERS); 
  public final static String WORD        = 
    block(zeroOrOne(APOSTROPHE) + 
        BLOCK +
        zeroOrMore(block(
            zeroOrOne(or(UNDERSCORE, 
                MINUS, 
                APOSTROPHE,
                SLASH,
                AMPERSAND)) +
                BLOCK)));

  // acronyms
  public final static String ACRONYM = block(oneOrMore(LETTER + DOT));// + zeroOrOne(LETTER));

  // this matches acronyms AFTER abbreviation merging
  public final static String LOOSE_ACRONYM = 
    block(oneOrMore((oneOrMore(LETTER) + DOT)) + zeroOrMore(LETTER));

  // other possible constructs
  public final static String PAREN      = or(LRB, RRB, LCB, RCB);
  public final static String SGML       = "<[^<>]+>";
  public final static String HTMLCODE   = block(AMPERSAND + UPPER + DOTCOMMA);

  public final static String ANY        = block("\\S");

  // email addresses must start with a letter, contain @, and end with a letter
  public final static String EMAIL      = block(LETTER +
      zeroOrMore(or(LETTER,
          DIGIT,
          DOT,
          MINUS,
          UNDERSCORE)) + 
          AT + 
          zeroOrMore(or(LETTER,
              DIGIT,
              DOT,
              MINUS,
              UNDERSCORE)) +
              LETTER);
  
  // email addresses must start with a letter, contain @, and end with . com 
  public final static String DOMAIN_EMAIL      = block(LETTER +
      zeroOrMore(or(LETTER,
          DIGIT,
          DOT,
          MINUS,
          UNDERSCORE)) + 
          AT + 
          oneOrMore(or(LETTER, DIGIT, DOT, MINUS, UNDERSCORE)) +
          zeroOrMore(WHITE_SPACE)+ DOT + zeroOrMore(WHITE_SPACE) + or("org", "ORG", "com", "COM", "net", "NET", "ru", "us"));

  // URLs must start with http:// or ftp://, followed by at least a letter
  public final static String URL = 
    block(HTTP +
        oneOrMore(or(LETTER,
            DIGIT,
            DOT,
            UNDERSCORE,
            SLASH,
            AMPERSAND,
            MINUS,
            PLUS)));
  
  //URLs without http, but ending in org, com, net
  public final static String SMALL_URL = 
    block(oneOrMore(oneOrMore(LETTER) + DOT) + zeroOrMore(WHITE_SPACE) + or("org", "ORG", "com", "COM", "net", "NET", "ru", "us"));

  // keep sequence of underscores as a single token
  public final static String UNDERSCORESEQ = oneOrMore("_");

  // list bullet, e.g., "(a)"
  public final static String LIST_BULLET = block(LRB + LETTER + zeroOrOne(LETTER) + RRB); 
  // part of a phone number, e.g., "(214)"
  public final static String PHONE_PART = block(LRB + oneOrMore(DIGIT) + RRB);

  // sequence of digits
  public final static String DIGITSEQ = oneOrMore(DIGIT);

  // the complete pattern
  public final static String RECOGNISED_PATTERN 
  = block(block(TIME) + "|" +
      block(DOMAIN_EMAIL) + "|" +
      block(EMAIL) + "|" +
      block(URL) + "|" +
      // block(SMALL_URL) + "|" +
      block(ACRONYM) + "|" + 
      block(DATE) + "|" + 
      block(PHONE_PART) + "|" + // must be before WORD, otherwise it's broken into multiple tokens
      block(WORD) + "|" +
      block(PUNC) + "|" + 
      block(LIST_BULLET) + "|" + 
      block(PAREN) + "|" + 
      block(SGML) + "|" + 
      block(HTMLCODE) + "|" + 
      block(UNDERSCORESEQ) + "|" + 
      block(ANY));

  /** The overall token pattern */
  private final static Pattern wordPattern;

  /** Pattern to recognize SGML tags */
  private final static Pattern sgmlPattern;

  /** Pattern to recognize slash-separated dates */
  private final static Pattern slashDatePattern;

  /** Pattern to recognize acronyms */
  private final static Pattern acronymPattern;

  /** Pattern to recognize URLs */
  private final static Pattern urlPattern;

  /** Pattern to recognize emails */
  private final static Pattern emailPattern;

  /** Recognized sequences of digits */
  private final static Pattern digitSeqPattern;

  static{
    wordPattern          = Pattern.compile(RECOGNISED_PATTERN); 
    sgmlPattern          = Pattern.compile(SGML);
    slashDatePattern     = Pattern.compile(DATE);
    acronymPattern       = Pattern.compile(LOOSE_ACRONYM);
    urlPattern           = Pattern.compile(URL);
    emailPattern         = Pattern.compile(EMAIL);
    digitSeqPattern      = Pattern.compile(DIGITSEQ);
  }
  
  public RobustTokenizer(String buffer) {
    mAbbreviations = new AbbreviationMap(true);
    this.buffer = buffer;
    this.cachedTokens = null;
  }


  public RobustTokenizer(boolean caseInsensitive, String buffer) {
    mAbbreviations = new AbbreviationMap(caseInsensitive);
    this.buffer = buffer;
    this.cachedTokens = null;
  }

  /** any in the set */ 
  public static String range(String s){
    return block("[" + s + "]");
  }


  /** zero or one */
  public static String zeroOrOne(String s){
    return block(block(s) + "?");
  }

  /** zero or more */
  public static String zeroOrMore(String s){
    return block(block(s) + "*");
  }

  /** one or more */
  public static String oneOrMore(String s){
    return block(block(s) + "+");
  }

  /** parens */
  public static String block(String s){
    return "(" + s + ")";
  }

  /** any of the two */
  public static String or(String s1, String s2){
    return block(block(s1) + "|" + block(s2));
  }

  /** any of the three */
  public static String or(String s1, String s2, String s3){
    return block(block(s1) + "|" + block(s2) + "|" + block(s3));
  }

  /** any of the four */
  public static String or(String s1, String s2, String s3, String s4){
    return block(block(s1) + "|" + block(s2) + "|" + block(s3) + "|" + block(s4));
  }

  /** any of the five */
  public static String or(String s1, String s2, String s3, String s4, String s5){
    return block(block(s1) + "|" + block(s2) + "|" + block(s3) + "|" + 
        block(s4) + "|" + block(s5));
  }

  /** any of the six */
  public static String or(String s1, String s2, String s3, 
      String s4, String s5, String s6){
    return block(block(s1) + "|" + block(s2) + "|" + block(s3) + "|" + 
        block(s4) + "|" + block(s5) + "|" + block(s6));
  }

  /** any of the seven */
  public static String or(String s1, String s2, String s3, 
      String s4, String s5, String s6, String s7){
    return block(block(s1) + "|" + block(s2) + "|" + block(s3) + "|" + 
        block(s4) + "|" + block(s5) + "|" + block(s6) + "|" + block(s7));
  }

  /** any of the eight */
  public static String or(String s1, String s2, String s3, String s4, 
      String s5, String s6, String s7, String s8){
    return block(block(s1) + "|" + block(s2) + "|" + block(s3) + "|" + 
        block(s4) + "|" + block(s5) + "|" + block(s6) + "|" + 
        block(s7) + "|" + block(s8));
  }

  /** any of the nine */
  public static String or(String s1, String s2, String s3, String s4, 
      String s5, String s6, String s7, String s8, String s9){
    return block(block(s1) + "|" + block(s2) + "|" + block(s3) + "|" + 
        block(s4) + "|" + block(s5) + "|" + block(s6) + "|" + 
        block(s7) + "|" + block(s8) + "|" + block(s9));
  }

  public static String or(String s1, String s2, String s3, String s4, 
      String s5, String s6, String s7, String s8, 
      String s9, String s10){
    return block(block(s1) + "|" + block(s2) + "|" + block(s3) + "|" + 
        block(s4) + "|" + block(s5) + "|" + block(s6) + "|" + 
        block(s7) + "|" + block(s8) + "|" + block(s9) + "|" +
        block(s10));
  }

  public static String or(String s1, String s2, String s3, String s4, 
      String s5, String s6, String s7, String s8, 
      String s9, String s10, String s11){
    return block(block(s1) + "|" + block(s2) + "|" + block(s3) + "|" + 
        block(s4) + "|" + block(s5) + "|" + block(s6) + "|" + 
        block(s7) + "|" + block(s8) + "|" + block(s9) + "|" +
        block(s10) + "|" + block(s11));
  }

  public static String or(String s1, String s2, String s3, String s4, 
      String s5, String s6, String s7, String s8, 
      String s9, String s10, String s11, String s12){
    return block(block(s1) + "|" + block(s2) + "|" + block(s3) + "|" + 
        block(s4) + "|" + block(s5) + "|" + block(s6) + "|" + 
        block(s7) + "|" + block(s8) + "|" + block(s9) + "|" +
        block(s10) + "|" + block(s11) + "|" + block(s12));
  }

  /** not */
  public static String rangeNot(String s){
    return range(block("^" + s));
  }

  private static int hasApostropheBlock(String s) {
    for(int i = s.length() - 1; i > 0; i --){
      if(s.charAt(i) == '\'' && i < s.length() - 1){
        return i;
      }

      if(! Character.isLetter(s.charAt(i))){
        return -1;
      }
    }

    return -1;
  }

  private static <T extends WordToken> String concatenate(List<T> tokens,
      int start,
      int end) {
    StringBuilder builder = new StringBuilder();

    for(; start < end; start ++){
      builder.append(((WordToken) tokens.get(start)).getWord());
    }
    return builder.toString();
  }

  private static <T extends WordToken> int countNewLines(List<T> tokens,
      int start,
      int end) {
    int count = 0;
    for(int i = start + 1; i < end; i ++){
      count += tokens.get(i).getNewLineCount();
    }
    return count;
  }

  public static boolean isUrl(String s) {
    Matcher match = urlPattern.matcher(s);
    return match.find(0);
  }

  public static boolean isEmail(String s) {
    Matcher match = emailPattern.matcher(s);
    return match.find(0);
  }

  public static boolean isSgml(String s) {
    Matcher match = sgmlPattern.matcher(s);
    return match.find(0);
  }

  public static boolean isSlashDate(String s) {
    Matcher match = slashDatePattern.matcher(s);
    return match.find(0);
  }

  public static boolean isAcronym(String s) {
    Matcher match = acronymPattern.matcher(s);
    return match.find(0);
  }

  public static boolean isDigitSeq(String s) {
    Matcher match = digitSeqPattern.matcher(s);
    return match.find(0);
  }

  public int countNewLines(String s, int start, int end) {
    int count = 0;
    for(int i = start; i < end; i ++) {
      if(s.charAt(i) == '\n') count ++;
    }
    return count;
  }
  
  /**
   * Smart tokenization storing the output in an array of CoreLabel
   * Sets the following fields:
   * - TextAnnotation - the text of the token
   * - TokenBeginAnnotation - the byte offset of the token (start)
   * - TokenEndAnnotation - the byte offset of the token (end)
   */
  public Word [] tokenizeToWords() {
    List<WordToken> toks = tokenizeToWordTokens();
    Word [] labels = new Word[toks.size()];
    for(int i = 0; i < toks.size(); i ++){
      WordToken tok = toks.get(i);
      Word l = new Word(tok.getWord(), tok.getStart(), tok.getEnd());
      labels[i] = l;
    }
    return labels;
  }

  /**
   * Tokenizes a natural language string
   * @return List of WordTokens
   */
  public List<WordToken> tokenizeToWordTokens() {
    List<WordToken> result = new ArrayList<>();

    //
    // replace illegal characters with SPACE
    //
    /*
    StringBuilder buffer = new StringBuilder();
    for(int i = 0; i < originalString.length(); i ++){
    	int c = (int) originalString.charAt(i);
    	//
    	// regular character
    	//
    	if(c > 31 && c < 127) buffer.append((char) c);

    	else{
    		log.info("Control character at position " + i + ": " + c);

    		//
    		// DOS new line counts as two characters
    		// 
    		if(c == 10) buffer.append(" ");

    		//
    		// other control character
    		//
    		else buffer.append(' ');
    	}
    }
     */

    Matcher match = wordPattern.matcher(buffer);
    int previousEndMatch = 0;

    //
    // Straight tokenization, ignoring known abbreviations
    //
    while(match.find()){
      String crtMatch = match.group();
      int endMatch = match.end();
      int startMatch = endMatch - crtMatch.length();
      int i;

      // found word ending in "n't"
      if (crtMatch.endsWith("n't")){
        if (crtMatch.length() > 3){
          WordToken token1 = 
            new WordToken(
                crtMatch.substring(0, crtMatch.length() - 3), 
                startMatch, endMatch - 3,
                countNewLines(buffer, previousEndMatch, startMatch));
          result.add(token1);
        }
        WordToken token2 = 
          new WordToken(crtMatch.substring(crtMatch.length() - 3, 
              crtMatch.length()),
              endMatch - 3, endMatch, 0);
        result.add(token2);
      } 

      // found word containing an appostrophe
      // XXX: is this too relaxed? e.g. "O'Hare"
      else if ((i = hasApostropheBlock(crtMatch)) != -1){
        WordToken token1 = new WordToken(crtMatch.substring(0, i), 
            startMatch, startMatch + i, countNewLines(buffer, previousEndMatch, startMatch));
        WordToken token2 = 
          new WordToken(crtMatch.substring(i, crtMatch.length()),
              startMatch + i, endMatch, 0);
        result.add(token1);
        result.add(token2);
      }

      // just a regular word
      else{
        WordToken token = new WordToken(crtMatch, startMatch, endMatch, 
            countNewLines(buffer, previousEndMatch, startMatch));
        result.add(token);
      }

      previousEndMatch = endMatch;
    }   

    //
    // Merge known abreviations
    //
    List<WordToken> resultWithAbs = new ArrayList<>();
    for(int i = 0; i < result.size(); i ++){
      // where the mw ends
      int end = result.size();
      if(end > i + MAX_MULTI_WORD_SIZE) end = i + MAX_MULTI_WORD_SIZE; 

      boolean found = false;

      // must have at least two tokens per multiword
      for(; end > i + 1; end --){
        WordToken startToken = result.get(i);
        WordToken endToken = result.get(end - 1);
        if(countNewLines(result, i, end) == 0){ // abbreviation tokens cannot appear on different lines
          String conc = concatenate(result, i, end);
          found = false;

          // found a multiword
          if((mAbbreviations.contains(conc) == true)){
            found = true;
            WordToken token = new WordToken(conc, 
                startToken.getStart(), 
                endToken.getEnd(),
                startToken.getNewLineCount());
            resultWithAbs.add(token);
            i = end - 1;
            break;
          }
        }
      }

      // no multiword starting at this position found
      if(! found){
        resultWithAbs.add(result.get(i));
      }
    }
    
    resultWithAbs = postprocess(resultWithAbs);

    return resultWithAbs;
  }
  
  /**
   * Redefine this method to implement additional domain-specific tokenization rules
   * @param tokens
   */
  protected List<WordToken> postprocess(List<WordToken> tokens) { return tokens; };

  /** 
   * Tokenizes and adds blank spaces were needed between each token 
   */
  public String tokenizeText() throws java.io.IOException{
    List<WordToken> tokenList = tokenizeToWordTokens();
    StringBuilder builder = new StringBuilder();
    Iterator<WordToken> iter = tokenList.iterator();
    if (iter.hasNext()){
    	builder.append(iter.next());
    }
    while(iter.hasNext()){
    	builder.append(" ");
    	builder.append(iter.next());
    }
    return builder.toString().replaceAll("\\s\\s+", " ");                
  }

  public static class AbbreviationMap {

    private Set<String> mAbbrevSet;

    private static List<String> normalizeCase(boolean caseInsensitive, List<String> words) {
      if(! caseInsensitive) return words;
      List<String> normWords = new ArrayList<>();
      for(String word: words) normWords.add(word.toLowerCase());
      return normWords;
    }

    /** Creates a new instance of AbreviationMap with some know abbreviations */
    public AbbreviationMap(boolean caseInsensitive)  {
      mAbbrevSet = Generics.newHashSet(normalizeCase(caseInsensitive, Arrays.asList(new String[]{
          "1.",
          "10.",
          "11.",
          "12.",
          "13.",
          "14.",
          "15.",
          "16.",
          "17.",
          "18.",
          "19.",
          "2.",
          "20.",
          "21.",
          "22.",
          "23.",
          "24.",
          "25.",
          "26.",
          "27.",
          "28.",
          "29.",
          "3.",
          "30.",
          "31.",
          "32.",
          "33.",
          "34.",
          "35.",
          "36.",
          "37.",
          "38.",
          "39.",
          "4.",
          "40.",
          "41.",
          "42.",
          "43.",
          "44.",
          "45.",
          "46.",
          "47.",
          "48.",
          "49.",
          "5.",
          "50.",
          "6.",
          "7.",
          "8.",
          "9.",
          "A.",
          "A.C.",
          "A.D.",
          "A.D.L.",
          "A.F.",
          "A.G.",
          "A.H.",
          "A.J.C.",
          "A.L.",
          "A.M",
          "A.M.",
          "A.P.",
          "A.T.B.",
          "AUG.",
          "Act.",
          "Adm.",
          "Ala.",
          "Ariz.",
          "Ark.",
          "Assn.",
          "Ass'n.",
          "Ass'n",
          "Aug.",
          "B.",
          "B.A.T",
          "B.B.",
          "B.F.",
          "B.J.",
          "B.V.",
          "Bancorp.",
          "Bhd.",
          "Blvd.",
          "Br.",
          "Brig.",
          "Bros.",
          "C.",
          "C.B.",
          "C.D.s",
          "C.J.",
          "C.O.",
          "C.R.",
          "C.W.",
          "CEO.",
          "CO.",
          "CORP.",
          "COS.",
          "Cal.",
          "Calif.",
          "Capt.",
          "Cie.",
          "Cir.",
          "Cmdr.",
          "Co.",
          "Col.",
          "Colo.",
          "Comdr.",
          "Conn.",
          "Corp.",
          "Cos.",
          "D.",
          "D.B.",
          "D.C",
          "D.C.",
          "D.H.",
          "D.M.",
          "D.N.",
          "D.S.",
          "D.T",
          "D.T.",
          "D.s",
          "Dec.",
          "Del.",
          "Dept.",
          "Dev.",
          "Dr.",
          "Ds.",
          "E.",
          "E.E.",
          "E.F.",
          "E.I.",
          "E.M.",
          "E.R.",
          "E.W.",
          "Etc.",
          "F.",
          "F.A.",
          "F.A.O.",
          "F.C",
          "F.E.",
          "F.J.",
          "F.S.B.",
          "F.W.",
          "FEB.",
          "FL.",
          "Feb.",
          "Fed.",
          "Fla.",
          "Fran.",
          "French.",
          "Freon.",
          "Ft.",
          "G.",
          "G.D.",
          "G.L.",
          "G.O.",
          "G.S.",
          "G.m.b",
          "G.m.b.H.",
          "GP.",
          "GPO.",
          "Ga.",
          "Gen.",
          "Gov.",
          "H.",
          "H.F.",
          "H.G.",
          "H.H.",
          "H.J.",
          "H.L.",
          "H.R.",
          "Hon.",
          "I.",
          "I.B.M.",
          "I.C.H.",
          "I.E.P.",
          "I.M.",
          "I.V.",
          "I.W.",
          "II.",
          "III.",
          "INC.",
          "Intl.",
          "Int'l",
          "IV.",
          "IX.",
          "Ill.",
          "Inc.",
          "Ind.",
          "J.",
          "J.C.",
          "J.D.",
          "J.E.",
          "J.F.",
          "J.F.K.",
          "J.H.",
          "J.L.",
          "J.M.",
          "JohnQ.Public",
          "J.P.",
          "J.R.",
          "J.V",
          "J.V.",
          "J.X.",
          "Jan.",
          "Jansz.",
          "Je.",
          "Jos.",
          "Jr.",
          "K.",
          "K.C.",
          "Kan.",
          "Ky.",
          "L.",
          "L.A.",
          "L.H.",
          "L.J.",
          "L.L.",
          "L.M.",
          "L.P",
          "L.P.",
          "La.",
          "Lt.",
          "Ltd.",
          "M.",
          "M.A.",
          "M.B.A.",
          "M.D",
          "M.D.",
          "M.D.C.",
          "M.E.",
          "M.J.",
          "M.R.",
          "M.S.",
          "M.W.",
          "M8.7sp",
          "Maj.",
          "Mar.",
          "Mass.",
          "Md.",
          "Med.",
          "Messrs.",
          "Mfg.",
          "Mich.",
          "Minn.",
          "Mir.",
          "Miss.",
          "Mo.",
          "Mr.",
          "Mrs.",
          "Ms.",
          "Mt.",
          "N.",
          "N.A.",
          "N.C",
          "N.C.",
          "N.D",
          "N.D.",
          "N.H",
          "N.H.",
          "N.J",
          "N.J.",
          "N.M",
          "N.M.",
          "N.V",
          "N.V.",
          "N.Y",
          "N.Y.",
          "NOV.",
          "Neb.",
          "Nev.",
          "No.",
          "no.", 
          "Nos.",
          "Nov.",
          "O.",
          "O.P.",
          "OK.",
          "Oct.",
          "Okla.",
          "Ore.",
          "P.",
          "P.J.",
          "P.M",
          "P.M.",
          "P.R.",
          "Pa.",
          "Penn.",
          "Pfc.",
          "Ph.",
          "Ph.D.",
          "pro-U.N.",
          "Prof.",
          "Prop.",
          "Pty.",
          "Q.",
          "R.",
          "R.D.",
          "Ret.",
          "R.H.",
          "R.I",
          "R.I.",
          "R.L.",
          "R.P.",
          "R.R.",
          "R.W.",
          "RLV.",
          "Rd.",
          "Rep.",
          "Reps.",
          "Rev.",
          "S.",
          "S.A",
          "S.A.",
          "S.C",
          "S.C.",
          "S.D.",
          "S.G.",
          "S.I.",
          "S.P.",
          "S.S.",
          "S.p",
          "S.p.A",
          "S.p.A.",
          "SKr1.5",
          "Sen.",
          "Sens.",
          "Sept.",
          "Sgt.",
          "Snr.",
          "Spc.",
          "Sr.",
          "St.",
          "Sys.",
          "T.",
          "T.D.",
          "T.F.",
          "T.T.",
          "T.V.",
          "TEL.",
          "Tech.",
          "Tenn.",
          "Tex.",
          "Tx.",
          "U.",
          "U.Cal-Davis",
          "U.K",
          "U.K.",
          "U.N.",
          "U.S.",
          "U.S.A",
          "U.S.A.",
          "U.S.C.",
          "U.S.C..",
          "U.S.S.R",
          "U.S.S.R.",
          "UK.",
          "US116.7",
          "V.",
          "V.H.",
          "VI.",
          "VII.",
          "VIII.",
          "VS.",
          "Va.",
          "Vs.",
          "Vt.",
          "W.",
          "W.A.",
          "W.G.",
          "W.I.",
          "W.J.",
          "W.R.",
          "W.T.",
          "W.Va",
          "W.Va.",
          "Wash.",
          "Wis.",
          "Wyo.",
          "X.",
          "Y.",
          "Y.J.",
          "Z.",
          "a.",
          "a.d.",
          "a.k.a",
          "a.m",
          "a.m.",
          "al.",
          "b.",
          "c.",
          "c.i.f",
          "cf.",
          "cnsl.",
          "cnsls.",
          "cont'd.",
          "d.",
          "deft.",
          "defts.",
          "e.",
          "et.",
          "etc.",
          "etseq.",
          "f.",
          "f.o.b",
          "ft.",
          "g.",
          "h.",
          "i.",
          "i.e.",
          "j.",
          "k.",
          "l.",
          "m.",
          "mots.",
          "n.",
          "o.",
          "p.",
          "p.m",
          "p.m.",
          "pltf.",
          "pltfs.",
          "prelim.",
          "r.",
          "s.",
          "seq.",
          "supp.",
          "sq.",
          "t.",
          "u.",
          "v.",
          "vs.",
          "x.",
          "y.",
          "z.",
      })));

    }

    public boolean contains(String s){
      return mAbbrevSet.contains(s.toLowerCase());
    }
  }
  
  public static class WordToken {

    /** Start position */
    protected int mStart;

    /** End position */
    protected int mEnd;
    
    /** Counts how many new lines appear between this token and the previous one in the stream */
    protected int mNewLineCount;

    /** The lexem */
    protected String mWord;

    public WordToken(String w, 
        int s,
        int e) {
      mWord = w;
      mStart = s;
      mEnd = e;
      mNewLineCount = 0;
    }
    public WordToken(String w, int s, int e, int nl) {
      mWord = w;
      mStart = s;
      mEnd = e;
      mNewLineCount = nl;
    }

    public String toString() {
      StringBuilder builder = new StringBuilder();
      builder.append("[");
      builder.append(mWord);
      builder.append(", ");
      builder.append(mStart);
      builder.append(", ");
      builder.append(mEnd);
      builder.append("]");
      return builder.toString();
    }

    public int getStart() { return mStart; }
    public void setStart(int i) { mStart = i; }

    public int getEnd() { return mEnd; }
    public void setEnd(int i) { mEnd = i; }
    
    public int getNewLineCount() { return mNewLineCount; }
    public void setNewLineCount(int i) { mNewLineCount = i; } 

    public String getWord() { return mWord; }
    public void setWord(String w) { mWord = w; }

  }
  
  /** Cached tokens for this buffer. Used by getNext */
  Word [] cachedTokens;
  /** Current position in the cachedTokens list. Used by getNext */
  int cachedPosition;
  
  @Override
  protected Word getNext() {
    if(cachedTokens == null){
      cachedTokens = tokenizeToWords();
      cachedPosition = 0;
    }
    
    if(cachedPosition >= cachedTokens.length){
      return null;
    }
    
    Word token = cachedTokens[cachedPosition];
    cachedPosition ++;
    
    return token;
  }

  public static void main(String argv[]) throws Exception {
    if(argv.length != 1){
      log.info("Usage: java edu.stanford.nlp.ie.machinereading.common.RobustTokenizer <file to tokenize>");
      System.exit(1);
    }

    // tokenize this file
    BufferedReader is = 
      new BufferedReader(new FileReader(argv[0])); 

    // read the whole file in a buffer
    // XXX: for sure there are more efficient ways of reading a file...
    int ch;
    StringBuilder builder = new StringBuilder();
    while((ch = is.read()) != -1) builder.append((char) ch);
    
    // create the tokenizer object
    RobustTokenizer<Word> t = new RobustTokenizer<>(builder.toString());

    List<Word> tokens = t.tokenize();
    for (Word token : tokens) {
      System.out.println(token);
    }
  }
}