package edu.stanford.nlp.trees.international.negra;

import edu.stanford.nlp.io.Lexer;

/** A lexer for the NEGRA corpus export format.  Inherits ACCEPT and
 * IGNORE fields from
 * edu.stanford.nlp.trees.international.<code>Lexer</code>.
 *
 * @author Roger Levy
 */

%%

%class NegraLexer
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

LINEEND = \r|\n|\r\n|\u2028|\u2029|\u000B|\u000C|\u0085
FIELD = [^\r\n\r\n\u2028\u2029\u000B\u000C\u0085]+

BOS = .BOS[0-9 ]*
EOS = .EOS[0-9 ]*

%%

<YYINITIAL> {
  {BOS}                 { yybegin(SENTENCE);
			  /* System.out.println("Beginning of sentence"); */
			  return ACCEPT; }
  .                     { return IGNORE; }
}

<SENTENCE> {
  {EOS}                  { yybegin(YYINITIAL);
                     /* System.out.println("End of sentence"); */
                            return ACCEPT; }
  {LINEEND}	          { return IGNORE; }
  {FIELD}                 { return ACCEPT; }
/*   .	                  { System.err.println("Error: " + yytext());
                            return IGNORE: }  // Can't happen as above lines match everything for SENTENCE */
}



