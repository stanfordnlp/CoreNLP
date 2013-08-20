package edu.stanford.nlp.trees.international.icegb;
import java.util.*;
import java.io.*;
import edu.stanford.nlp.io.*;

/** A lexer for the ICE-GB corpus. (still being worked on)
 * @author Pi-Chuan Chang
 */

%%

%class ICEGBLexer
//%implements edu.stanford.nlp.io.Lexer
%standalone
%unicode
%int

%{

  public static final int IGNORE = 0;
  //public static final int ACCEPT = 1;

  public static final int STRING = 1;
  public static final int LINEEND = 2;
  public static final int WHITESPACE = 3;
  public static final int LPAREN = 4;
  public static final int RPAREN = 5;
  public static final int LBR = 6;
  public static final int RBR = 7;
  public static final int TAG = 8;
  public static final int LSB = 9;
  public static final int RSB = 10;
  public static final int COMMA = 11;
  public static final int SEPARATE = 12;
  public static final int OTHER = 13;
  public static final int INSIDEBRACKET = 14;

  public String match() {
    return yytext();
  }

  public void pushBack(int n) {
    yypushback(n);
  }

  public int getYYEOF() {
    return YYEOF;
  }
%}


TAG = <[^>\]]*>
BROKEN_TAG = <e[A-Za-z]* | <b
LBR = \{
RBR = \}
LSQBRACKET = \[
RSQBRACKET = \]
IN_SQBRACKET = \[[^\]]*\]
LPAREN = \(
RPAREN = \)
LINEEND = \r\n|\r|\n // |\u2028|\u2029|\u000B|\u000C|\u0085 // copy from chtbl.flex
SEPARATE = \r\n\r\n|\n\n|\r\n\n|\n\r\n
PU = PU
WHITESPACE = [ \t]+
COMMA = ,
OTHER = [^\r\n \t<>(),\[\]\{\}]*

%%

<YYINITIAL> {
  {BROKEN_TAG}                  { //System.err.println("BROKEN_TAG---"+yytext()); 
                                  return TAG; }
  {TAG}                         { //System.err.println("TAG---"+yytext()); 
                                  return TAG; }
  {LSQBRACKET}                  { //System.err.println("LSB("+yytext()+")"); 
                                  return LSB; }
  {RSQBRACKET}                  { //System.err.println("RSB("+yytext()+")"); 
                                  return RSB; }
  {PU}                          { //System.err.println("PU---"+yytext()); 
                                  return STRING; }
  {WHITESPACE}                  { //System.err.println("WHITESPACE");
                                  return WHITESPACE; }
  {COMMA}                       { //System.err.println("COMMA"); 
                                  return COMMA; }
  {LPAREN}                      { //System.err.println("LPAREN"); 
                                  return LPAREN; }
  {RPAREN}                      { //System.err.println("RPAREN"); 
                                  return RPAREN; }
  {LBR}                         { //yybegin(BRACKET);
                                  return LBR; }
  {RBR}                         { //System.err.println("ERROR!!"); 
                                  //return IGNORE; 
                                  return RBR; }
  {SEPARATE}                    { return SEPARATE; }
  {LINEEND}                     { //System.err.println("LINEEND");
                                  return LINEEND; }
  {OTHER}                       { //System.err.println("STRING---"+yytext()+"---\t");
                                  return STRING; } //yybegin(AFTERSPACE); }
  .                             { //System.err.println ("#OTHER---"+yytext()); 
                                  return OTHER; } 
}

/*
<BRACKET> {
  {RBR}                         { yybegin(YYINITIAL); return RBR; }
  [^\}]*                        { //System.err.println("INSIDEBRACKET--"+yytext());
                                  return INSIDEBRACKET; }
}
*/
