package edu.stanford.nlp.trees.international.pennchinese;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;

/** 
 * A lexer for the Penn Chinese Treebank.  Supports Chinese characters.
 *
 * @author Roger Levy
 * @author Christopher Manning (redid to accept most stuff, add CTB 4-6 XML entities)
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
                                                "GB18030"), true);
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

%state DOCNO
%state DOCID
%state DOCTYPE
%state DATETIME
%state SRCID
%state HEADER
%state DATEINHEADER
%state PREAMBLE

MISCXML = <[^>\r\n]+>
NONXML = [^<]+


HEADERBEGINS = <HEADER>
HEADERENDS = <\/HEADER>
DOCNOBEGINS = <DOCNO>|<DOCID>
DOCNOENDS = <\/DOCNO>|<\/DOCID>
/* DOCTYPEBEGINS = <DOCTYPE( +[A-Za-z][A-Za-z0-9]* *= *['\"].*['\"])* *> */
DOCTYPEBEGINS = <DOCTYPE( [^>]+)>
DOCTYPEENDS = <\/DOCTYPE>
DATETIMEBEGINS = <DATE\/TIME>|<DATE>
DATETIMEENDS = <\/DATE\/TIME>|<\/DATE>
SRCIDBEGINS = <SRCID>|<CTBID>
SRCIDENDS = <\/SRCID>|<\/CTBID>
PREAMBLEBEGINS = <PREAMBLE>
PREAMBLEENDS = <\/PREAMBLE>


/* there are a few non-CJK characters as terminals; in particular 'II'
 * shows up as part of a terminal, and '-' and \u00B7 are used in nouns
 * in CTB4 there are some bopomofo and a "geometric shape" character (?) */

/* Chris May 2005: it seems like it was stupid adding in new okay characters
 * every time we see a new one.  We now make it that everything except 
 * parentheses, whitespace, and XML tags is Chinese.
 */

// CJKCHAR = \u00D7|\uE37B|\u2236|\u25CB|[\u3000-\u303F]|[\u3400-\u4DBF]|[\u4E00-\u9FAF]|[\uFE30-\uFE4F]|[\uFF00-\uFFEF]|[\uFFFD\u0373]
// BOPOMOFO = [\u3100-\u312F]
// GEOMETRIC = [\u25A0-\u25FF]
/* full width ascii, times, solidus, hyphen-minus, plus, smartquotes */
// WHATEVER = [\uFF01-\uFF5E\u00B7\u002F\u002D\u002B\u2018-\u201E]  
// CJKROMAN = [\u2160-\u217F]
// CJK = ({CJKCHAR}|{BOPOMOFO}|{GEOMETRIC}|{CJKROMAN}|{WHATEVER})+
// ASCIINUMBER = [0-9]
// ASCIILETTER = [A-Za-z]


NEWCJKCHAR = [^() \t\n\r\u2028\u2029\u000B\u000C\u0085]
LINEEND = \r|\n|\r\n|\u2028|\u2029|\u000B|\u000C|\u0085
SPACE = [ \t]+

PAREN = \(|\)

// LABEL = [A-Za-z0-9*-=&]+
// PUNCTMARK = [\u2000-\u206F]|[\,\.\?\!\"\'\`\$\~]|[\u00A1-\u00BF]|[\u2500-\u257F]
// PUNCT = {PUNCTMARK}+

/* TERMINAL = ({CJK}|{PUNCTMARK}|{ASCIINUMBER}|{ASCIILETTER})+ */
TERMINAL = {NEWCJKCHAR}+
/* It wasn't recognizing DOCID for some reason.  I don't know why, so force it! */
FULLDOCID = {DOCNOBEGINS}{NONXML}{DOCNOENDS}

%%

<YYINITIAL> {
  {FULLDOCID}           { if (DBG) System.err.printf("Ignoring |%s|, staying in YYINITIAL%n", yytext());
                          return IGNORE; }
  {PREAMBLEBEGINS}      { if (DBG) System.err.printf("Ignoring |%s|, moving to PREAMBLE%n", yytext());
                          yybegin(PREAMBLE); return IGNORE; }
  {HEADERBEGINS}        { if (DBG) System.err.printf("Ignoring |%s|, moving to HEADER%n", yytext());
                          yybegin(HEADER); return IGNORE; }
  {DOCNOBEGINS}         { if (DBG) System.err.printf("Ignoring |%s|, moving to DOCNO%n", yytext());
                          yybegin(DOCNO); return IGNORE; }
  {DOCTYPEBEGINS}       { if (DBG) System.err.printf("Ignoring |%s|, moving to DOCTYPE%n", yytext());
                          yybegin(DOCTYPE); return IGNORE; }
  {DATETIMEBEGINS}      { if (DBG) System.err.printf("Ignoring |%s|, moving to DATETIME%n", yytext());
                          yybegin(DATETIME); return IGNORE; }
  {SRCIDBEGINS}         { if (DBG) System.err.printf("Ignoring |%s|, moving to SRCID%n", yytext());
                          yybegin(SRCID); return IGNORE; }
  {MISCXML}             { if (DBG) System.err.printf("Ignoring |%s|, staying in YYINITIAL%n", yytext());
                          return IGNORE; }
  {PAREN}               { if (DBG) System.err.printf("Accepting |%s|, staying in YYINITIAL%n", yytext());
                          return ACCEPT; }
  {TERMINAL}            { if (DBG) System.err.printf("Accepting |%s|, staying in YYINITIAL%n", yytext());
                          return ACCEPT; }
  {LINEEND}             { return IGNORE; }
  {SPACE}                 { return IGNORE; }
  .                     { reportError(yytext()); }
}

<DOCNO> {
  {DOCNOENDS}   { // System.err.println("Transitioning to YYINITIAL");
                  yybegin(YYINITIAL); return IGNORE; }
  {NONXML}      { return IGNORE; }
  .             { reportError(yytext()); }
}

<DOCTYPE> {
  {DOCTYPEENDS} { //System.err.println("Transitioning to YYINITIAL");
                  yybegin(YYINITIAL); return IGNORE; }
  {NONXML}      { return IGNORE; }
  .             { reportError(yytext()); }
}

<DATETIME> {
  {DATETIMEENDS} { //System.err.println("Transitioning to YYINITIAL");
                   yybegin(YYINITIAL); return IGNORE; }
  {NONXML}       { return IGNORE; }
  .              { reportError(yytext()); }
}

<SRCID> {
  {SRCIDENDS} { //System.err.println("In SRCID; Transitioning to YYINITIAL");
                yybegin(YYINITIAL); return IGNORE; }
  {NONXML}    { return IGNORE; }
  .           { reportError(yytext()); }
}


<HEADER> {
  {DATETIMEBEGINS} { yybegin(DATEINHEADER); return IGNORE; }
  {HEADERENDS} { //System.err.println("Transitioning to YYINITIAL");
                yybegin(YYINITIAL); return IGNORE; }
  {NONXML}           { return IGNORE; }
  .             { reportError(yytext()); }
}

<DATEINHEADER> {
  {DATETIMEENDS} { yybegin(HEADER); return IGNORE; }
  {NONXML}           { return IGNORE; }
  .             { reportError(yytext()); }
}

<PREAMBLE> {
  {PREAMBLEENDS} { yybegin(YYINITIAL); return IGNORE; }
    {NONXML}           { return IGNORE; }
  .             { reportError(yytext()); }
}
