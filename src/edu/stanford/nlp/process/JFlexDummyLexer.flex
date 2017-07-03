package edu.stanford.nlp.process;

%%

%class JFlexDummyLexer
%unicode
%int

%implements edu.stanford.nlp.io.Lexer

%{

public void pushBack(int n) {
  yypushback(n);
}

public int getYYEOF() {
  return YYEOF;
}

%}


SPACES = [ \t\r\n\u2028\u2029\u000B\u000C\u0085]+
TEXT =  [^ \t\r\n\u2028\u2029\u000B\u000C\u0085]+

%%

{SPACES} {}
{TEXT}   {return ACCEPT; }
