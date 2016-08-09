package edu.stanford.nlp.trees.international.pennchinese;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;

/**
 * A lexer for the Penn Chinese Treebank.  Supports Chinese characters.
 *
 * @author Roger Levy
 * @author Christopher Manning (fully redid to accept most stuff, add CTB 4-9 XML entities)
 */

%%

%class CHTBLexer
// %standalone
%unicode
%int


%{
  private static final boolean DBG = false;

  public static final int IGNORE = 0;
  public static final int ACCEPT = 1;

  public String match() {
    return yytext();
  }

  private static void reportError(String yytext) {
    try {
      PrintWriter p = new PrintWriter(new OutputStreamWriter(System.err,
                                                "utf-8"), true);
      p.println("chtbl.flex tokenization error: \"" + yytext + "\"");
      if (yytext.length() >= 1) {
        p.println("First character is: " + yytext.charAt(0));
        if (yytext.length() >= 2) {
          p.println("Second character is: " + yytext.charAt(1));
        }
      }
    } catch (UnsupportedEncodingException e) {
      System.err.println("chtbl.flex tokenization and encoding present error");
    }
  }

%}


/* OLD WAY: See next comment.
 * There are a few non-CJK characters as terminals; in particular 'II'
 * shows up as part of a terminal, and '-' and \u00B7 are used in nouns.
 * in CTB4 there are some bopomofo and a "geometric shape" character (?) */

// CJKCHAR = \u00D7|\uE37B|\u2236|\u25CB|[\u3000-\u303F]|[\u3400-\u4DBF]|[\u4E00-\u9FAF]|[\uFE30-\uFE4F]|[\uFF00-\uFFEF]|[\uFFFD\u0373]
// BOPOMOFO = [\u3100-\u312F]
// GEOMETRIC = [\u25A0-\u25FF]
/* full width ascii, times, solidus, hyphen-minus, plus, smartquotes */
// WHATEVER = [\uFF01-\uFF5E\u00B7\u002F\u002D\u002B\u2018-\u201E]
// CJKROMAN = [\u2160-\u217F]
// CJK = ({CJKCHAR}|{BOPOMOFO}|{GEOMETRIC}|{CJKROMAN}|{WHATEVER})+
// ASCIINUMBER = [0-9]
// ASCIILETTER = [A-Za-z]
// LABEL = [A-Za-z0-9*-=&]+
// PUNCTMARK = [\u2000-\u206F]|[\,\.\?\!\"\'\`\$\~]|[\u00A1-\u00BF]|[\u2500-\u257F]
// PUNCT = {PUNCTMARK}+
// TERMINAL = ({CJK}|{PUNCTMARK}|{ASCIINUMBER}|{ASCIILETTER})+

/* Chris May 2005: it seems like it was stupid adding in new okay characters
 * every time we see a new one.  We now make it that everything except
 * parentheses, whitespace, and XML tags is Chinese.
 */

MISCXML = <[^>\r\n]+>
NONXML = [^<]+
NEWCJKCHAR = [^() \t\n\r\u2028\u2029\u000B\u000C\u0085]
LINEEND = \r|\n|\r\n|\u2028|\u2029|\u000B|\u000C|\u0085
SPACE = [ \t]+
PAREN = \(|\)
TERMINAL = {NEWCJKCHAR}+
XML_METADATA_ELEMENT_NAME = (DOCTYPE|DOCNO|DOCID|DATE|DATETIME|DATE_TIME|DATE\/TIME|ENDTIME|END_TIME|SRCID|CTBID)
XML_METADATA_OPEN_TAG = <{XML_METADATA_ELEMENT_NAME}({SPACE}[^>]+)?>
XML_METADATA_CLOSE_TAG = <\/{XML_METADATA_ELEMENT_NAME}>
XML_METADATA_ELEMENT = {XML_METADATA_OPEN_TAG}{NONXML}{XML_METADATA_CLOSE_TAG}

%%

// Penn Chinese Treebank XML is always on separate lines.
// There are two types:
// (1) XML elements that have trees inside:
//   S, P, segment, seg, TEXT, DOC, BODY, HEADER, HEADLINE, su, TURN, msg
// We parse these as individual "MISCXML" and ignore them
// (2) XML elements tha have a value that is not trees but metadata text, even though
// this text may contain parentheses. These always occur all on one line.
//   DOCTYPE, DOCNO, DOCID, DATE, DATETIME, DATE_TIME, ENDTIME, END_TIME
// We parse a whole line of these at once as an ignore token.

{XML_METADATA_ELEMENT} { if (DBG) System.err.printf("Ignoring |%s|%n", yytext());
                          return IGNORE; }
{MISCXML}               { if (DBG) System.err.printf("Ignoring |%s|%n", yytext());
                          return IGNORE; }
{PAREN}                 { if (DBG) System.err.printf("Accepting |%s|%n", yytext());
                          return ACCEPT; }
{TERMINAL}              { if (DBG) System.err.printf("Accepting |%s|%n", yytext());
                          return ACCEPT; }
{LINEEND}               { return IGNORE; }
{SPACE}                 { return IGNORE; }
