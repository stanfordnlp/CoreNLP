package edu.stanford.nlp.ie.ner;

%%

%class BioCreativeLexer
%standalone
%unicode

%implements edu.stanford.nlp.io.Lexer

%{
public void pushBack(int n) {
yypushback(n);
}

public int getYYEOF() {
return YYEOF;
}

%}

WORD = [^ \t\n.,:;/?=()\[\]<>]+

%%

{WORD}		{ return ACCEPT; }
[ \t\r\f]	{ /* ignore */ } 
\n		{ return ACCEPT; }
.		{ return ACCEPT; }
<<EOF>> 	{ return YYEOF; }
