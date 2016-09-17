package edu.stanford.nlp.process;

/** Undoes the more common cases of Penn Treebank quoting to producing
 *  something resembling normal text.  That is, it is an approximate inverse
 *  of PTBTokenizer.
 *
 *  Jan 2008: This was both made better, and some quirky weird stuff was added
 *  so as to better handle the NIST MT08 translation system's output.
 *
 *  @author Joseph Smarr
 *  @author Christopher Manning
 */


%%

%class PTB2TextLexer
%unicode
%function next
%type String
%caseless

%state INQUOTE

%{

/*
"'T WAS"
{ return("'TWAS"); }
"'T was"
{ return("'Twas"); }
"'t was"
{ return("'twas"); }
"'T IS"
{ return("'TIS"); }
"'T is"
{ return("'Tis"); }
"'t is"
{ return("'tis"); }
*/

  private static String removeWhite(String in) {
    StringBuilder out = new StringBuilder();
    for (int i = 0, len = in.length(); i < len; i++) {
      char ch = in.charAt(i);
      if (ch != ' ') {
        out.append(ch);
      }
    }
    return out.toString();
  }

%}

SPACE = [ ]
DQUOT = \"|&{SPACE}?(amp{SPACE}?;{SPACE}?)?quot{SPACE}?;?
AMP = &amp{SPACE}?;?
LT = &lt{SPACE}?;
GT = &gt{SPACE}?;
MESSYPOSS = [a-z]{3,30}{SPACE}'{SPACE}s
HYPHENDONTCOLLAPSE = ([:letter:]|[:digit:])+{SPACE}-{SPACE}(in|as|at|for|therefore|so|thus|they|who|which|and|such|including|according|to|the|a|one|that|this|those|these|some|she|he|we|you|on|before|after|there|here|are|is|was|were|has|have|should|would|AFP|Reuters|News)|(said|says|say|saying|headline){SPACE}-{SPACE}([:letter:]|[:digit:])+
QUOTEDONTCOLLAPSE = ([:letter:]|[:digit:])+{SPACE}'(cause|n'|em|till?|[2-9]0s)


%%

<YYINITIAL> {DQUOT}{SPACE}/[:letter:]
  { yybegin(INQUOTE); return "\""; }
<YYINITIAL> {DQUOT}{SPACE}{DQUOT}{SPACE}/[:letter:]
  { yybegin(INQUOTE); return "\" \""; }
<YYINITIAL> {DQUOT}
  { yybegin(INQUOTE); return "\""; }
{SPACE}{DQUOT}$
  { return "\""; }
<INQUOTE> {SPACE}{DQUOT}
  { yybegin(YYINITIAL); return "\""; }
<INQUOTE> {DQUOT}
  { yybegin(YYINITIAL); return "\""; }
{HYPHENDONTCOLLAPSE}/{SPACE}
  { return yytext(); }
{HYPHENDONTCOLLAPSE}$
  { return yytext(); }
{QUOTEDONTCOLLAPSE}/{SPACE}
  { return yytext(); }
{QUOTEDONTCOLLAPSE}$
  { return yytext(); }
([:letter:]|[:digit:])+({SPACE}-{SPACE}[:letter:]+){1,3}
  { return removeWhite(yytext()); }
{LT}
  { return "<"; }
{GT}
  { return ">"; }
{AMP}
  { return "&"; }
"&"
  { return "&"; }
"can not"
  { return "cannot"; }
{MESSYPOSS}/{SPACE}
  { return removeWhite(yytext()); }
" ''"
  { return "\""; }
"`` "
  { return("\""); }
" " ("." | ":" | "," | ";" | "?" | "!" | "...")
  { return(yytext().substring(1, yytext().length())); }
"` "
  { return("`"); }
" '"[^\n]
  { return(yytext().substring(1, yytext().length())); }
" n't"
  { return("n't"); }
" ?\\/"
  { return "/"; }
\\\/
  { return "/"; }
("-LRB-" | "(") " "
  { return "("; }
" " ("-RRB-" | ")")
  { return(")"); }
("-LCB-" | "{") " "
  { return("{"); }
" " ("-RCB-" | "}")
  { return("}"); }
" %"
  { return("%"); }
"$ "
  { return("$"); }
[^ \n\\/&\"]+
  { return(yytext()); }
"/"
  { return yytext(); }
"\\"
  { return yytext(); }
" "
  { return(yytext()); }
"\n"
  { yybegin(YYINITIAL); return(yytext()); }
<<EOF>>
  { return null; }
