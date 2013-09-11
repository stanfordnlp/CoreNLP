package edu.stanford.nlp.parser.eval;

import java.io.*;

/** Counting parens with a lexer.
 *  @author Roger Levy
 */

%%

%{
  private int numParens = 0;
  public static final String SPLIT = "-SPLIT-";
%}

%class TopParenSplitter
%standalone
%unicode
%type String
%eofval{
  return null;
%eofval}

%%

<YYINITIAL> {
  \(    { numParens++; return numParens == 1 ? "" : yytext(); }
  \)    { if(numParens == 0) 
            throw new RuntimeException("error -- too many close parens!"); 
          else {
            numParens--;
            return numParens == 0 ? SPLIT : yytext();
          }
        }
  .     { return yytext(); }
}
