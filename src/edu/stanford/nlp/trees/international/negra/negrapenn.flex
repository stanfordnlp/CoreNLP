package edu.stanford.nlp.trees.international.negra;
import java.util.*;
import java.io.*;
import edu.stanford.nlp.io.*;

/** A lexer for the Penn Treebank-style context-free version of the
 * NEGRA corpus.  Inherits ACCEPT and IGNORE fields from
 * edu.stanford.nlp.trees.international.<code>Lexer</code>.
 *
 * @author Roger Levy
 */

%%

%class NegraPennLexer
%implements Lexer
%standalone
%unicode
%int

%{
  public void pushBack(int n) {
    yypushback(n);
  }

  public int getYYEOF() {
    return YYEOF;
  }
%}


%state SENTENCE

COMMENT = \%\%\ Sent\ [0-9]+ 
PAREN = [()]
SPACE = [ \t]
LINEEND = \r|\n|\r\n|\u2028|\u2029|\u000B|\u000C|\u0085
TOKEN = [-A-Za-z!\"#$&'`*+,_./\\0-9:;<=>?@\[\]\u00A7\u00B7\u00C0-\u00FF\u0021-\u0027]+
/* TOKEN = !({COMMENT}|{PAREN}|{SPACE}|{LINEEND}) */


%%

<YYINITIAL> {
  {COMMENT}                 { return IGNORE; }
  {SPACE}                   { return IGNORE; }
  {LINEEND}                 { return IGNORE; }
  {PAREN}                   { return ACCEPT; }
  {TOKEN}                   { return ACCEPT; }
  .	                  { System.err.println("Error: " + yytext());
                            return IGNORE; }
}  
