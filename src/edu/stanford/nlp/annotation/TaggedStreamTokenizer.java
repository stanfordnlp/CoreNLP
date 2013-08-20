package edu.stanford.nlp.annotation;

import java.io.*;
import java.util.Hashtable;
import java.util.StringTokenizer;
import java.util.Vector;

/**
 * <code>TaggedStreamTokenizer</code> is similar to
 * <code>java.io.StreamTokenizer</code>,
 * except that it is better suited to deal with documents containing html-style
 * tags.  Note that <code>TaggedStreamTokenizer</code> is not a subclass of
 * <code>StreamTokenizer</code>.<br><br>
 * <code>TaggedStreamTokenizer</code> allows the distinction to be made between
 * "target" text and "background" text through the use of target tags.  For
 * instance, consider the following snippet:
 * <br><br>
 * <pre>
 * &lt;html&gt;
 * Cows eat &lt;color&gt;green&lt;/color&gt; grass.  Aliens eat &lt;color&gt;green&lt;/color&gt; slime.  I am an alien cow.
 * &lt;/pre&gt;
 * &lt;/html&gt;
 * </pre>
 * <br><br>
 * By specifying that &lt;color&gt; is a target tag, the tokenizer would return
 * both instances of "green" as type TT_TARGET_WORD, while the other words outside
 * the scope of the color tags would be of type TT_BACKGROUND_WORD.
 * <br><br>
 * <p/>
 * As in <code>StreamTokenizer</code>, a call to <code>nextToken()</code> after
 * initializing the tokenizer returns a TT code, which is also stored in the
 * public <code>ttype</code> variable; the token text will be stored in
 * <code>sval</code>, and if the token is within the scope of some target tag,
 * <code>attr</code> will contain the name of that tag.  TT_EOF is returned
 * in the case that the end of the stream is reached.
 * <br><br>
 * It is probably wise to run some sort of HTML validator or scrubber on input
 * files before passing them into the tokenizer.  Although the tokenizer is
 * somewhat forgiving when dealing with broken html, things such as the spacing
 * inside of tags or whether quotes are used for attribute names are ignored
 * by the tokenizer; depending on your task, you may want to standardize these.
 * <br><br>
 * <code>TaggedStreamTokenizer</code> is built around a lexer coded using JavaCC,
 * in the file <code>HtmlLexer.jj</code>.  The following characteristics of the
 * tokenizer are mainly built into the lexer, so modifications to these will
 * probably require modifications to both the tokenizer and lexer files.
 * <ul>
 * <li>HTML tags must be well-formed, starting with a '&lt;' and ending with a
 * '&gt;'.  A tag is automatically closed off with a '&gt' if a '&lt;' is encountered
 * before a closing
 * '&gt;' -- this is new for this version.  Tokenization should properly handle
 * quoted literals inside of tags that include '&lt;' or '&gt;'
 * characters.
 * <li> There is no maximum length imposed for a tag.
 * <li>HTML escape sequences must be well-formed to be recognized as html.
 * Examples of well-formed escape sequences include <code>&#38;#143;</code>
 * and <code>&#38;nbsp;</code>.  Mal-formed escape sequences include
 * <code>&#38;123;</code> and <code>&#38;lt</code>.
 * <li>HTML comments must start with "&lt;!--" and end with "--&gt;"
 * <li>Words can be single alphanumeric characters, sequences of alphanumeric
 * characters (with exceptions, below), or single non-alphanumeric characters
 * (which by default are discarded).  The rules defining legal words are based
 * on common patterns in American English language and CS jargon.  They include:
 * <ul>
 * <li>A word can include any number of '-' or '_' characters, with no
 * restrictions placed on where these appear in the word, so long as no
 * two such characters appear in sequence, or at the beginning or end of
 * a word.
 * <li>A word can include at most one apostrophe, so long as it is flanked
 * by alphanumeric characters.
 * <li>A word can include one or more periods, each of which must be flanked
 * by at least one alphanumeric characters.  A period may also occur at the
 * beginning of a word.  Thus, ellipses are not included in words, but
 * IP addresses, domain names, are considered one word.
 * <li>A word can end in a period if it is a single capital-letter abbreviation,
 * or if the word is an abbreviation with more than one period (eg, B.S.),
 * or if the word is a recognized abbreviation (eg, St., Mr., Calif.)
 * <li> A word can be an email address
 * <li>A sequence of digits only can include commas, but in this case they
 * must constitute a well-formed number (like 34,456 but not 23,67)
 * <li>A sequence of digits can optionally have one or both of  '-' and '%'
 * at the beginning or end, respectively
 * <li>A number can include a '$' at the beginning, as long as neither '-'
 * or '%' appear
 * <li>A number can be in time format (hh:mm:ss or hh:mm or h:mm, etc), though
 * no attempt is made at verifying that it represents a real time.
 * </ul>
 * <li>There is no support for multilingual character sets; accented letters, etc,
 * are treated as delimiters.
 * </ul>
 * <br>
 * <br><b>Updates 5/12/02:</b> Removed the tag length restriction, '&lt;' and '&gt;'
 * symbols are allowed to appear in quoted literals within an html tag, runaway tags are
 * explicitly terminated when a '&lt;' is encountered before a '&gt;'
 * <br>
 * <br><b>Updates 5/28/02:</b> Added tokenization of email addresses, abbreviations
 * are tokenized to include the trailing period.
 * <br>
 *
 * @author Miler Lee, Stanford University
 * @version 1.2 5/12/02
 */

public class TaggedStreamTokenizer {


  /**
   * A constant indicating that the end of the stream has been read
   */
  public static final int TT_EOF = -1;

  /**
   * A constant indicating that an active start target tag has been read
   */
  public static final int TT_TARGET_TAG = 1;

  /**
   * A constant indicating that an active end target tag has been read
   */
  public static final int TT_TARGET_TAG_END = 2;

  /**
   * A constant indicating that a word token has been read that is within
   * the scope of an active tag
   */
  public static final int TT_TARGET_WORD = 3;

  /**
   * A constant indicating that a non-target-tag html token has been read
   * that is within the scope of an active tag
   */
  public static final int TT_TARGET_HTML = 4;

  /**
   * A constant indicating that an inactive target tag has been read that is
   * within the scope of an active tag
   */
  public static final int TT_TARGET_INACTIVE_TAG = 5;

  /**
   * A constant indicating that a word token has been read that is outside
   * the scope of any active tags
   */
  public static final int TT_BACKGROUND_WORD = 6;

  /**
   * A constant indicating that a non-target-tag html token has been read
   * that is outside the scope of any active tags
   */
  public static final int TT_BACKGROUND_HTML = 7;

  /**
   * A constant indicating that an inactive target tag has been read that is
   * outside the scope of any active target tags
   */
  public static final int TT_BACKGROUND_INACTIVE_TAG = 8;


  /**
   * This field contains the name of the active target tag that is
   * currently in scope, or null if no tag is in scope.
   */
  public String attr = null;
  /**
   * This field contains a string giving the characters of the word token
   * just read.  The initial value of this field is null.
   */
  public String sval = null;
  /**
   * After a call to the nextToken method, this field contains the type of
   * the token just read.  The initial value of this field is -10.
   */
  public int ttype = -10;


  private HtmlLexer parser;
  private String keeperList = "";

  private Hashtable<String, String> targets = new Hashtable<String, String>();
  private Hashtable<String, String> activeTargets = new Hashtable<String, String>();
  private Hashtable<String, String> keeperTags = new Hashtable<String, String>();
  private Vector<Token> cache = new Vector<Token>();
  private boolean tagInScope = false;
  private String currentTag;
  private boolean eofReached = false;

  private boolean discardScript = true;
  private boolean discardComments = true;
  private boolean discardInactive = true;
  private boolean discardHtml = true;
  private boolean discardTags = true;
  private boolean discardCommentTags = true;


  /**
   * Create a tokenizer that parses the given character stream.  The
   * tokenizer is initialized to the following default state:
   * <ul><li>All tags (active or inactive) are discarded
   * <li>All html is discarded
   * <li>All non-word characters are discarded
   * </ul>
   *
   * @param i an InputStream object
   */

  public TaggedStreamTokenizer(InputStream i) {
    parser = new HtmlLexer(i);
  }

  /**
   * Create a tokenizer that parses the given character stream.  The
   * tokenizer is initialized to the following default state:
   * <ul><li>All tags (active or inactive) are discarded
   * <li>All html is discarded
   * <li>All non-word characters are discarded
   * </ul>
   *
   * @param r a Reader object
   */

  public TaggedStreamTokenizer(Reader r) {
    parser = new HtmlLexer(r);
  }

  /**
   * Specifies which tags should be considered target tags.  A pair of
   * start and end target tags mark "target" text, i.e., words that will
   * be of type TT_TARGET_WORD, so long as they are "active."  The tags
   * themselves are of type TT_TARGET_TAG and TT_TARGET_TAG_END
   * respectively; these are returned if setDiscardTargetTags is passed
   * a value of true. Non-active
   * tags can be specified to distinguish them from the naturally-occuring
   * html tags in the document, for filtering purposes.  These tags are of
   * type TT_TARGET_INACTIVE_TAG or TT_BACKGROUND_INACTIVE_TAG, depending
   * on whether a target tag is in scope or not, and are returned if true
   * was passed to setDiscardInactiveTags.
   *
   * @param start  a string representing the start tag, e.g., "&lt;start&gt;'"
   * @param end    a string representing the corresponding end tag, e.g.,
   *               "&lt;end&gt;" or "&lt;/start&gt;"
   * @param active true indicates that the tag is active, and will be
   *               interpreted as marking target text.
   */

  public void addTarget(String start, String end, boolean active) {
    if (active) {
      activeTargets.put(start, end);
    } else {
      targets.put(start, end);
      targets.put(end, start);
    }
  }

  /**
   * Adds an active target tag
   *
   * @param start a string representing the start tag
   * @param end   a string representing the corresponding end tag
   */

  public void addTarget(String start, String end) {
    activeTargets.put(start, end);
  }


  /**
   * Specifies that a tag should be returned as a token, overriding
   * the DiscardHtml setting.  The tag should be specified by name, <b>without
   * angled brackets</b>.  All instances of the tag, starting or closing, with
   * or without attributes, case-insensitive, are returned as type TT_BACKGROUND_HTML or
   * TT_TARGET_HTML.  Comment tags may also be specified; pass in "!--" to
   * allow both "&lt;!--" and "--&gt;" tags to be returned as tokens.  Note
   * that whether the comment body is tokenized needs to be set in
   * <code>setDiscardComments</code>.
   * <br>
   * In the case of script, applet, server, and style tags, note that the
   * DiscardScript setting can be true, while still returning these tags.
   *
   * @param tag a string representing the tag name to be returned
   */

  public void addKeeperTag(String tag) {
    if (tag == null || tag.equals("")) {
      return;
    } else if (tag.equals("!--")) {
      discardCommentTags = false;
      return;
    }

    String temp = tag.toLowerCase();
    keeperTags.put(temp, temp);
  }


  /**
   * Determines which (non-html) delimiters to return as tokens. All non-word
   * characters are treated
   * as delimiters, including the ones in the <code>keeper</code> set.
   * However, specifying a keeper sets causes these delimiters to be returned
   * as one-character tokens.  Special cases: delimiting white space
   * characters are treated as one token by the lexer for efficiency;
   * therefore, no white space characters can be specified in the keeper
   * set.  To change this, the grammar file needs to be modified.  Ellipses
   * (...) are returned as a single token, as are any string of two or more
   * periods; these are returned by specifying a single period in the
   * keeper string.
   *
   * @param keepers a String containing the non-word characters to return
   */

  public void setKeeperCharacters(String keepers) {
    keeperList = keepers;
  }


  /**
   * Returns the keeper string.
   *
   * @return a String containing the non-word characters that are legal tokens
   */

  public String getKeeperCharacters() {
    return keeperList;
  }


  /**
   * Determines whether or not html-style comments are discarded.
   * Passing in a value of false causes the start and end comment
   * sequences to be considered html (and returned based on what was passed
   * into setDiscardHtml); then the comment body is parsed.  Because the
   * lexer is customized to recognize entire comment spans, this process
   * is not optimized; making it more efficient would require rewriting
   * portions of the grammar.
   * <br>Note that the leading "&lt;!--" and trailing "--&gt;" tags are
   * considered html, separate from the comment body; therefore, passing
   * true into setDiscardComments does not prevent those tags
   * from being returned as tokens.
   *
   * @param val true indicates that comments are discarded.
   */
  public void setDiscardComments(boolean val) {
    discardComments = val;
  }


  /**
   * Returns the DiscardComments setting.
   *
   * @return true if comments are discarded
   */

  public boolean getDiscardComments() {
    return discardComments;
  }


  /**
   * Determines whether or not script, server, applet, and style (i.e.,
   * cascading style sheets) code is discarded.
   *
   * @param val true indicates that script is discarded.
   */
  public void setDiscardScript(boolean val) {
    discardScript = val;
  }


  /**
   * Returns the DiscardScript setting.
   *
   * @return true if script is discarded
   */
  public boolean getDiscardScript() {
    return discardScript;
  }


  /**
   * Determines whether or not inactive tags are discarded.
   *
   * @param val true indicates that inactive tags are discarded.
   */
  public void setDiscardInactiveTags(boolean val) {
    discardInactive = val;
  }


  /**
   * Returns the DiscardInactiveTags setting.
   *
   * @return true if inactive tags are discarded
   */
  public boolean getDiscardInactiveTags() {
    return discardInactive;
  }


  /**
   * Determines whether or not html is discarded -- both html tags and
   * escape characters such as <code>&#38;nbsp;</code>.  If true is
   * passed in, it overrides whatever value was passed in by
   * DiscardScript has, ie--if all html is discarded, then all
   * javascript and applet code is discarded also.  The same is not true
   * for comments.<br>
   * This setting is overridden for any tag name passed into setKeeperTag --
   * i.e., tags of those names are always returned as tokens.
   *
   * @param val true indicates that html is discarded.
   */
  public void setDiscardHtml(boolean val) {
    discardHtml = val;
  }


  /**
   * Returns the DiscardHtml setting.
   *
   * @return true if html is discarded
   */

  public boolean getDiscardHtml() {
    return discardHtml;
  }


  /**
   * Determines whether or not active target tags are discarded.
   *
   * @param val true indicates that active target tags are discarded.
   */
  public void setDiscardTargetTags(boolean val) {
    discardTags = val;
  }


  /**
   * Returns the DiscardTargetTags setting.
   *
   * @return true if active target tags are discarded
   */

  public boolean getDiscardTargetTags() {
    return discardTags;
  }


  /**
   * Causes the currently in-scope tag to be removed from scope.  Thus, if
   * the next token is a plain word, it will be of type
   * <code>TT_BACKGROUND_WORD</code>
   */

  public void reset() {
    tagInScope = false;
    currentTag = null;
  }


  /**
   * Generates the next token from the input stream of this tokenizer.  The
   * type of the token is returned in the <code>ttype</code> field.  The string
   * representation of the token is in the <code>sval</code> field, and an
   * optional attribute tag is in the <code>attr</code> field.<br><br>
   *
   * @return the value of the <code>ttype</code> field
   */
  public int nextToken() {
    return generateToken();
  }


  /**
   * **************PRIVATE METHODS*****************
   */


  private int generateToken() {
    Token t;

    if (eofReached) {
      return getEOF();
    }

    if (cache.isEmpty()) {
      t = parser.getNextToken();
    } else {
      t = cache.elementAt(0);
      cache.removeElementAt(0);    //java 1.1 compliance!
    }


    switch (t.kind) {

      case (HtmlLexerConstants.EOF):
        return getEOF();

      case (HtmlLexerConstants.COMMENT_START):
        return getComment();

      case (HtmlLexerConstants.ESCAPE_CHARACTER):
        return getEscapeCharacter(t.toString());

      case (HtmlLexerConstants.CODE_TAG):
        return getCodeTag(t.toString());

      case (HtmlLexerConstants.HTML_TAG):
        return getTag(t.toString());

      case (HtmlLexerConstants.RUNAWAY_TAG):
        //runaway tags need a bit of post-processing to remove trailing white space.
        return getTag(t.toString().trim() + ">");   //runaway tags are closed off

      case (HtmlLexerConstants.WORD):
      case (HtmlLexerConstants.APOST_WORD):
      case (HtmlLexerConstants.DOT_WORD):
      case (HtmlLexerConstants.EMAIL_WORD):
      case (HtmlLexerConstants.DEC_COMMA_NUM):
      case (HtmlLexerConstants.DEC_NUM):
      case (HtmlLexerConstants.QUALIFIED_NUM):
      case (HtmlLexerConstants.MONEY_NUM):
      case (HtmlLexerConstants.ALPHANUM):
      case (HtmlLexerConstants.TIME):
        return getWord(t.toString());

      default:
        return getOther(t.toString(), t.kind);
    }
  }


  private int getEOF() {
    attr = null;
    sval = null;
    ttype = TT_EOF;
    return TT_EOF;
  }


  private void addToCache(int kind, String image) {
    Token t = Token.newToken(0);
    t.kind = kind;
    t.image = image;
    cache.addElement(t);
  }

  private int getComment() {
    return getSpan("<!--", HtmlLexerConstants.COMMENT_END, discardComments);
  }


  //these are the script, applet, etc tags, most of the time
  //we just discard them

  private int getCodeTag(String s) {
    return getSpan(s, HtmlLexerConstants.CODE_END, discardScript || discardHtml);
  }


  private int getSpan(String startTag, int endKind, boolean discardBody) {

    if (!discardHtml || checkKeepers(startTag.toLowerCase())) {
      addToCache(HtmlLexerConstants.HTML_TAG, startTag);
    }

    //consume the body of the comment, store it in the tokens vector

    Vector<String> tokens = new Vector<String>();
    boolean gotEOF = false;
    Token t;

    while (true) {
      t = parser.getNextToken();
      if (t.kind == HtmlLexerConstants.EOF) {
        gotEOF = true;
        if (discardBody) {
          return getEOF();
        } else {
          break;
        }
      }
      if (t.kind == endKind) {
        break;
      }
      tokens.addElement(t.toString());
    }

    //store the end tag string
    String endTagText = t.image;

    
    //apply the lexer to each string in the tokens vector
    if (!discardBody) {
      for (int i = 0; i < tokens.size(); i++) {
        String s = tokens.elementAt(i);
        if (s.equals("")) {
          continue;
        }
        HtmlLexer l = new HtmlLexer(new StringReader(s));

        while (true) {
          t = l.getNextToken();
          if (t.kind == HtmlLexerConstants.EOF) {
            break;
          }
          addToCache(t.kind, t.image);
        }
      }
    }

    if (gotEOF) {
      addToCache(HtmlLexerConstants.EOF, "");
    } else if (!discardHtml || checkKeepers(endTagText.toLowerCase())) {
      addToCache(HtmlLexerConstants.HTML_TAG, endTagText);
    }

    return generateToken();
  }


  private int getEscapeCharacter(String s) {
    if (discardHtml) {
      return generateToken();
    } else {
      if (tagInScope) {
        attr = currentTag;
        sval = s;
        ttype = TT_TARGET_HTML;
        return TT_TARGET_HTML;
      } else {
        attr = null;
        sval = s;
        ttype = TT_BACKGROUND_HTML;
        return TT_BACKGROUND_HTML;
      }
    }
  }


  private int getTag(String s) {

    //if it is a start tag and there isn't already a tag in scope, set
    //the tag scope to be true

    String value = activeTargets.get(s);
    if (value != null && !tagInScope) {
      tagInScope = true;
      currentTag = s;

      if (discardTags) {
        return generateToken();
      } else {
        attr = s;
        sval = s;
        ttype = TT_TARGET_TAG;
        return ttype;
      }
    }

    //if it is an end tag that matches with the start tag in scope, set
    //the tag scope to be false

    if (tagInScope) {
      value = activeTargets.get(currentTag);
      if (value.equals(s)) {
        tagInScope = false;
        if (discardTags) {
          currentTag = null;
          return generateToken();
        } else {
          attr = currentTag;
          sval = s;
          ttype = TT_TARGET_TAG_END;
          currentTag = null;
          return ttype;
        }
      }
    }


    //otherwise, it defaults to whatever settings are on right now

    //check if it's a tag, but inactive; if discard, generate a new token
    
    String value2 = targets.get(s);
    if (value2 != null) {
      if (discardInactive) {
        return generateToken();
      } else {
        if (tagInScope) {
          attr = currentTag;
          sval = s;
          ttype = TT_TARGET_INACTIVE_TAG;
          return TT_TARGET_INACTIVE_TAG;
        } else {
          attr = null;
          sval = s;
          ttype = TT_BACKGROUND_INACTIVE_TAG;
          return TT_BACKGROUND_INACTIVE_TAG;
        }
      }
    }


    //must be plain HTML now

    //check that the tag is not in the keeper set
    //also ignore annotation tags that are not targets
    if ((!discardHtml && s.indexOf("tag name") == -1) || checkKeepers(s)) {
      if (tagInScope) {
        attr = currentTag;
        sval = s;
        ttype = TT_TARGET_HTML;
        return TT_TARGET_HTML;
      } else {
        attr = null;
        sval = s;
        ttype = TT_BACKGROUND_HTML;
        return TT_BACKGROUND_HTML;
      }
    } else {
      return generateToken();
    }


  }

  private boolean checkKeepers(String tag) {
    //special casing the comment tags
    if (tag.equals("<!--") || tag.equals("-->")) {
      return !discardCommentTags;
    }

    StringTokenizer st = new StringTokenizer(tag, " \t\r\n\f</>");
    if (st.hasMoreTokens()) {
      String name = st.nextToken();
      if (keeperTags.get(name.toLowerCase()) != null) {
        return true;
      }
    }
    return false;
  }


  private int getWord(String s) {
    if (tagInScope) {
      attr = currentTag;
      sval = s;
      ttype = TT_TARGET_WORD;
      return TT_TARGET_WORD;
    } else {
      attr = null;
      sval = s;
      ttype = TT_BACKGROUND_WORD;
      return TT_BACKGROUND_WORD;
    }
  }


  private int getOther(String s, int tokenCode) {
    //if it's not a word, then it is a single character (or in a couple
    //special cases, a few characters.
    //check if each char is in the keep set, if it is return it, otherwise 
    //generate a new token
    
    if (keeperList == null || keeperList.length() == 0) {
      return generateToken();
    } else if (s.length() > 1) {
      if (tokenCode != HtmlLexerConstants.DOTPLUS) {
        return generateToken();
      } else if (keeperList.indexOf('.') == -1) {
        return generateToken();
      }
    } else {
      if (keeperList.indexOf(s) == -1) {
        return generateToken();
      }
    }

    return getWord(s);
  }


  /**
   * Test the TaggedStreamTokenizer by passing in an html filename argument.
   */
  public static void main(String argv[]) throws Exception {
    System.err.println("TaggedStreamTokenizer test, 5/12/02");

    if (argv.length < 1) {
      System.err.println("Needs the filename of the html file");
      System.exit(-1);
    }

    BufferedReader f = new BufferedReader(new FileReader(argv[0]));

    TaggedStreamTokenizer tsr = new TaggedStreamTokenizer(f);

    tsr.setKeeperCharacters("*:.");

    tsr.addTarget("<post_date>", "</post_date>", true);
    tsr.addTarget("<area>", "</area>", true);
    tsr.addTarget("<title>", "</title>", true);
    tsr.addTarget("<country>", "</country>", false);

    tsr.addKeeperTag("tag");
    tsr.addKeeperTag("html");
    tsr.addKeeperTag("script");
    tsr.addKeeperTag("!--");


    //      tsr.AddTarget("<tag>", "</tag>");


    // tsr.setDiscardComments(false);
    //       tsr.setDiscardScript(false);
    tsr.setDiscardInactiveTags(false);
    tsr.setDiscardHtml(false);

    //      tsr.setDiscardScript(false);
    //	    tsr.nextToken();


    int blah = 0;
    while (blah != -1) {

      blah = tsr.nextToken();

      if (blah == TaggedStreamTokenizer.TT_TARGET_TAG) {
        System.out.println("target tag " + tsr.attr + ": " + tsr.sval);
      }
      if (blah == TaggedStreamTokenizer.TT_TARGET_TAG_END) {
        System.out.println("target tag end " + tsr.attr + ": " + tsr.sval);
      } else if (blah == TaggedStreamTokenizer.TT_TARGET_HTML) {
        System.out.println("target html" + tsr.attr + ": " + tsr.sval);
      } else if (blah == TaggedStreamTokenizer.TT_TARGET_WORD) {
        System.out.println("target word" + tsr.attr + ": " + tsr.sval);
      } else if (blah == TaggedStreamTokenizer.TT_BACKGROUND_WORD) {
        System.out.println("background word: " + tsr.sval);
      } else if (blah == TaggedStreamTokenizer.TT_BACKGROUND_HTML) {
        System.out.println("background html: " + tsr.sval);
      } else if (blah == TaggedStreamTokenizer.TT_BACKGROUND_INACTIVE_TAG) {
        System.out.println("background inactive tag: " + tsr.sval);
      } else if (blah == TaggedStreamTokenizer.TT_TARGET_INACTIVE_TAG) {
        System.out.println("target inac" + tsr.attr + ": " + tsr.sval);
      }
    }

    f.close();

    //  }
    //       (Exception e) {}

  }

}
