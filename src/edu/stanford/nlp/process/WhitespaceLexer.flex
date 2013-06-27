package edu.stanford.nlp.process;

import java.io.Reader;
import edu.stanford.nlp.ling.Word;

/** Provides a Unicode-aware plain whitespace tokenizer.  This tokenizer separates words
 *  based on whitespace, including Unicode spaces such as the CJK ideographic space as well
 *  as traditional Unix whitespace characters.  It can optionally separate out and return
 *  newline characters, again recognizing all Unicode newline sequences.
 *  Designed to be called by <code>WhitespaceTokenizer</code>.
 *
 *  @author Roger Levy
 *  @author Christopher Manning
 */

%%

%class WhitespaceLexer
%unicode
%function next
%type Object
%char

%{
/** 
 * See: http://www.w3.org/TR/newline on Web newline chars: NEL, LS, PS.
   See: http://unicode.org/reports/tr13/tr13-9.html and
   http://www.unicode.org/unicode/reports/tr18/#Line_Boundaries
   for Unicode conventions,
   including other separators (vertical tab and form feed).
   <br>
   We do not interpret the zero width joiner/non-joiner (U+200C,
   U+200D) as white spaces. 
   <br>
   No longer %standalone.  See WhitespaceTokenizer for a main method. 
 */

  public WhitespaceLexer(Reader r, LexedTokenFactory<?> tf) {
    this(r);
    this.tokenFactory = tf;
  }

  private LexedTokenFactory<?> tokenFactory;

  static final String NEWLINE = "\n";
%}

CR = \r|\n|\r\n|\u0085|\u2028|\u2029
OTHERSEP = [\u000B\u000C]
SPACE = [ \t\u00A0\u1680\u180E\u2002-\u200B\u202F\u205F\u2060\u3000]
SPACES = {SPACE}+
TEXT = [^ \t\u00A0\u1680\u180E\u2002-\u200B\u202F\u205F\u2060\u3000\r\n\u0085\u2028\u2029\u000B\u000C]+

%%

{CR}     { return tokenFactory.makeToken(NEWLINE, yychar, yylength()); }
{OTHERSEP} { return tokenFactory.makeToken(NEWLINE, yychar, yylength()); }
{SPACES} { }
{TEXT}   { return tokenFactory.makeToken(yytext(), yychar, yylength()); }
<<EOF>>  { return null; }
