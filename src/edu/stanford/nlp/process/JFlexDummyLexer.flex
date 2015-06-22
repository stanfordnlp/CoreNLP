package edu.stanford.nlp.process;

import java.util.*;
import java.io.*;
import edu.stanford.nlp.trees.*;


%%

%class JFlexDummyLexer
%standalone
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


SPACE = [ \t\r\n\u2028\u2029\u000B\u000C\u0085]
SPACES = {SPACE}+
TEXT = [^ \t\r\n\u2028\u2029\u000B\u000C\u0085]+

%%

{SPACES} {}
{TEXT}   {return ACCEPT; }
.		{ System.err.println(yytext()); }


