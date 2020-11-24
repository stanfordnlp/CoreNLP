package edu.stanford.nlp.international.arabic.process;

import java.io.Reader;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.process.LexedTokenFactory;
import edu.stanford.nlp.process.CoreLabelTokenFactory;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.PropertiesUtils;

/**
 * Tokenizer for UTF-8 Arabic. Supports raw text and both sections
 * (vocalized and unvocalized) of the ATB.
 *
 * @author Spence Green
 */

%%

%class ArabicLexer
%unicode
%function next
%type Object
%caseless
%char

%{
 private LexedTokenFactory<?> tokenFactory;
 private boolean invertible;
 private CoreLabel prevWord;
 private StringBuilder prevWordAfter;
 
 // Convert Arabic digits to ASCII digits
 private boolean normArDigits;
 
 // Convert Arabic punctuation to ASCII equivalents
 private boolean normArPunc;
 
 // Substitute newlines with newlineChar.
 // Otherwise, treat them like whitespace
 private boolean tokenizeNLs;
 public static final String NEWLINE_TOKEN = "*NL*";

 // Use \u2026 for ellipses
 private boolean useUTF8Ellipsis;
 
 // Arabic-specific orthographic normalization rules
 private boolean normAlif;
 private boolean normYa;
 private boolean removeDiacritics;
 private boolean removeTatweel;
 private boolean removeQuranChars;
 
 // Penn ATB vocalized section normalizations
 private boolean removeProMarker;
 private boolean removeSegMarker;
 private boolean removeMorphMarker;
 
 // Lengthening effects (yAAAAAAA): replace three or more of the same character with one
 private boolean removeLengthening;

 private final Pattern segmentationMarker = Pattern.compile("^-+|-+$");
 
 // Escape parens for ATB parsing
 private boolean atbEscaping;

 private Map<String,String> normMap;
 
 public ArabicLexer(Reader r, LexedTokenFactory<?> tf, Properties props) {
   this(r);
   this.tokenFactory = tf;
   
   tokenizeNLs = PropertiesUtils.getBool(props, "tokenizeNLs", false);
   useUTF8Ellipsis = PropertiesUtils.getBool(props, "useUTF8Ellipsis", false);
   invertible = PropertiesUtils.getBool(props, "invertible", false);
   normArDigits = PropertiesUtils.getBool(props, "normArDigits", false);
   normArPunc = PropertiesUtils.getBool(props, "normArPunc", false);
   normAlif = PropertiesUtils.getBool(props, "normAlif", false);
   normYa = PropertiesUtils.getBool(props, "normYa", false);
   removeDiacritics = PropertiesUtils.getBool(props, "removeDiacritics", false);
   removeTatweel = PropertiesUtils.getBool(props, "removeTatweel", false);
   removeQuranChars = PropertiesUtils.getBool(props, "removeQuranChars", false);
   removeProMarker = PropertiesUtils.getBool(props, "removeProMarker", false);
   removeSegMarker = PropertiesUtils.getBool(props, "removeSegMarker", false);
   removeMorphMarker = PropertiesUtils.getBool(props, "removeMorphMarker", false);
   removeLengthening = PropertiesUtils.getBool(props, "removeLengthening", false);
   atbEscaping = PropertiesUtils.getBool(props, "atbEscaping", false);

   setupNormalizationMap();

   if (invertible) {
     if (!(tf instanceof CoreLabelTokenFactory)) {
       throw new IllegalArgumentException("ArabicLexer: the invertible option requires a CoreLabelTokenFactory");
     }
     prevWord = (CoreLabel) tf.makeToken("", 0, 0);
     prevWordAfter = new StringBuilder();
   }
 }

 private void setupNormalizationMap() {
   normMap = Generics.newHashMap(200);

   // Junk characters that we always remove
   normMap.put("\u0600","#");
   normMap.put("\u0601","");
   normMap.put("\u0602","");
   normMap.put("\u0603","");
   normMap.put("\u0606","\u221B");
   normMap.put("\u0607","\u221C");
   normMap.put("\u0608","");
   normMap.put("\u0609","%");
   normMap.put("\u060A","%");
   normMap.put("\u060B","");
   normMap.put("\u060E","");
   normMap.put("\u060F","");
   normMap.put("\u066E","\u0628");
   normMap.put("\u066F","\u0642");
   normMap.put("\u06CC","\u0649");
   normMap.put("\u06D6","");
   normMap.put("\u06D7","");
   normMap.put("\u06D8","");
   normMap.put("\u06D9","");
   normMap.put("\u06DA","");
   normMap.put("\u06DB","");
   normMap.put("\u06DC","");
   normMap.put("\u06DD","");
   normMap.put("\u06DE","");
   normMap.put("\u06DF","");
   normMap.put("\u06E0","");
   normMap.put("\u06E1","");
   normMap.put("\u06E2","");
   normMap.put("\u06E3","");
   normMap.put("\u06E4","");
   normMap.put("\u06E5","");
   normMap.put("\u06E6","");
   normMap.put("\u06E7","");
   normMap.put("\u06E8","");
   normMap.put("\u06E9","");
   normMap.put("\u06EA","");
   normMap.put("\u06EB","");
   normMap.put("\u06EC","");
   normMap.put("\u06ED","");

   if (normArDigits) {
      normMap.put("\u0660","0");
      normMap.put("\u0661","1");
      normMap.put("\u0662","2");
      normMap.put("\u0663","3");
      normMap.put("\u0664","4");
      normMap.put("\u0665","5");
      normMap.put("\u0666","6");
      normMap.put("\u0667","7");
      normMap.put("\u0668","8");
      normMap.put("\u0669","9");
      normMap.put("\u06F0","0");
      normMap.put("\u06F1","1");
      normMap.put("\u06F2","2");
      normMap.put("\u06F3","3");
      normMap.put("\u06F4","4");
      normMap.put("\u06F5","5");
      normMap.put("\u06F6","6");
      normMap.put("\u06F7","7");
      normMap.put("\u06F8","8");
      normMap.put("\u06F9","9");
   }
   if (normArPunc) {
      normMap.put("\u00BB","\"");
      normMap.put("\u00AB","\"");
      normMap.put("\u060C",",");
      normMap.put("\u060D",",");
      normMap.put("\u061B",";");
      normMap.put("\u061E",".");
      normMap.put("\u061F","?");
      normMap.put("\u066A","%");
      normMap.put("\u066B",",");
      normMap.put("\u066C","\u0027");
      normMap.put("\u066F","*");
      normMap.put("\u06DF",".");
   }
   if (normAlif) {
      normMap.put("\u0622","\u0627");
      normMap.put("\u0623","\u0627");
      normMap.put("\u0625","\u0627");
      normMap.put("\u0671","\u0627");
      normMap.put("\u0672","\u0627");
      normMap.put("\u0673","\u0627");
   }
   if (normYa) {
      normMap.put("\u064A","\u0649");
   }
   if (removeDiacritics) {
      normMap.put("\u064B","");
      normMap.put("\u064C","");
      normMap.put("\u064D","");
      normMap.put("\u064E","");
      normMap.put("\u064F","");
      normMap.put("\u0650","");
      normMap.put("\u0651","");
      normMap.put("\u0652","");
      normMap.put("\u0653","");
      normMap.put("\u0654","");
      normMap.put("\u0655","");
      normMap.put("\u0656","");
      normMap.put("\u0657","");
      normMap.put("\u0658","");
      normMap.put("\u0659","");
      normMap.put("\u065A","");
      normMap.put("\u065B","");
      normMap.put("\u065C","");
      normMap.put("\u065D","");
      normMap.put("\u065E","");
      normMap.put("\u0670","");
   }
   if (removeTatweel) {
      normMap.put("\u0640","");
      normMap.put("_","");
   }
   if (removeQuranChars) {
      // Arabic honorifics
      normMap.put("\u0610","");
      normMap.put("\u0611","");
      normMap.put("\u0612","");
      normMap.put("\u0613","");
      normMap.put("\u0614","");
      normMap.put("\u0615","");
      normMap.put("\u0616","");
      normMap.put("\u0617","");
      normMap.put("\u0618","");
      normMap.put("\u0619","");
      normMap.put("\u061A","");
   }
   if (atbEscaping) {
      normMap.put("(","-LRB-");
      normMap.put(")","-RRB-");
   }
 }

 private String normalizeToken(String text, boolean isWord) {
   // Remove segmentation markers from the ATB
   if (isWord && removeSegMarker) {
     text = segmentationMarker.matcher(text).replaceAll("");
   }
   int len = text.length();
   StringBuilder sb = new StringBuilder(len);
   for (int i = 0; i < len; ++i) {
     String thisChar = String.valueOf(text.charAt(i));
     // Remove morpheme markers from the ATB vocalized section
     if (isWord && removeMorphMarker && thisChar.equals("+")) {
       continue;
     }
     if (removeLengthening && isLengthening(text, i)) {
       continue;
     }
     if (normMap.containsKey(thisChar)) {
       thisChar = normMap.get(thisChar);
     }
     if (thisChar.length() > 0) {
       sb.append(thisChar);
     }
   }
   return sb.toString();
 }
 
 private boolean isLengthening(String text, int pos) {
   if (pos == 0) return false;
   String thisChar = String.valueOf(text.charAt(pos));
   if (!thisChar.equals(String.valueOf(text.charAt(pos - 1))))
     return false;
   if (pos < text.length() - 1 && thisChar.equals(String.valueOf(text.charAt(pos + 1))))
     return true;
   if (pos >= 2 && thisChar.equals(String.valueOf(text.charAt(pos - 2))))
     return true;
   return false;
 }
 
   /** Make the next token.
   *
   *  @param txt What the token should be
   *  @param originalText The original String that got transformed into txt
   */
  private Object getNext(String txt, String originalText) {
    if (tokenFactory == null) {
      throw new RuntimeException(this.getClass().getName() + ": Token factory is null.");
    }
    if (invertible) {
      String str = prevWordAfter.toString();
      prevWordAfter.setLength(0);
      CoreLabel word = (CoreLabel) tokenFactory.makeToken(txt, Math.toIntExact(yychar), yylength());
      word.set(CoreAnnotations.OriginalTextAnnotation.class, originalText);
      word.set(CoreAnnotations.BeforeAnnotation.class, str);
      prevWord.set(CoreAnnotations.AfterAnnotation.class, str);
      prevWord = word;
      return word;
    } else {
      return tokenFactory.makeToken(txt, Math.toIntExact(yychar), yylength());
    }
  }
  
  private Object getNext(boolean isWord) {
    String text = yytext();
    String normText = normalizeToken(text, isWord);
    return getNext(normText, text);
  }

  private Object getEllipsis() {
    String ellipsisString = useUTF8Ellipsis ? "\u2026" : "...";
    return getNext(ellipsisString, yytext());
  }

%}

CR = \r|\r?\n|\u2028|\u2029|\u000B|\u000C|\u0085
SPACE = [ \t\u00A0\u2000-\u200A\u202F\u3000]
SPACES = {SPACE}+
ELLIPSIS = \.\.\.\.*
ARPUNC = [\u0600-\u060B\u060D\u061B-\u061F\u066A-\u066D\u06D4]
LATINPUNC = [\u0022-\u002B\u002D\u002F\u003A-\u003E\u0040\u005B-\u0060\u007B-\u007E\u00A1-\u00BF\u2010-\u2027\u2030-\u205E\u20A0-\u20B5\u2E2E]
PUNC = ({ARPUNC}|{LATINPUNC})+
DIGIT = [:digit:]|[\u0660-\u0669\u06F0-\u06F9]
DIGITS = {DIGIT}+
/* If a number ends with +, ., etc, chop that off instead of keeping it. */
NUMBER = {DIGITS}([_\-,\+/\\\.\u066B\u066C\u060C\u060D]+{DIGITS}+)*
LATINWORD = ([a-zA-Z]|{DIGIT})+

/* Some of these single punctuations get their own token, although note that ... is covered earlier by ELLIPSIS */
PERIOD = \.
COMMA = ,|\u060C
/* !?!?!?!?! */
EXCLAM = [!?]+

/* Sometimes _ is used for tatweel \u0640, so include it in this set */
ARCHAR = [_\u060E-\u061A\u0621-\u065E\u066E-\u06D3\u06D5-\u06EF\u06FA-\u06FF]

/* Null pronoun marker in the vocalized section of the ATB */
NULLPRONSEG = \-*\[\u0646\u064F\u0644\u0644\]\-
NULLPRON = \-*\[\u0646\u064F\u0644\u0644\]

/* An word is a sequence of Arabic ligatures, possibly preceded and/or
   succeeded by ATB segmentation markers "-", and possibly separated by
   ATB morpheme boundaries "+" */
ARWORD = \-*({ARCHAR}\+*)+\-*

/* Some Arabic words consist of the the determiner attached to latin
   cardinals (DT+11 --> "the eleventh"). Don't separate these. */
ARNUMWORD = {ARWORD}{DIGITS}

/* Tokens from other writing systems */
FORNWORD = ([:letter:]|[\u00AD\u0237-\u024F\u02C2-\u02C5\u02D2-\u02DF\u02E5-\u02FF\u0300-\u036F\u0370-\u037D\u0384\u0385\u03CF\u03F6\u03FC-\u03FF\u0483-\u0487\u04CF\u04F6-\u04FF\u0510-\u0525\u055A-\u055F\u0591-\u05BD\u05BF\u05C1\u05C2\u05C4\u05C5\u05C7\u070F\u0711\u0730-\u074F\u0750-\u077F\u07A6-\u07B1\u07CA-\u07F5\u07FA\u0900-\u0903\u093C\u093E-\u094E\u0951-\u0955\u0962-\u0963\u0981-\u0983\u09BC-\u09C4\u09C7\u09C8\u09CB-\u09CD\u09D7\u09E2\u09E3\u0A01-\u0A03\u0A3C\u0A3E-\u0A4F\u0A81-\u0A83\u0ABC-\u0ACF\u0B82\u0BBE-\u0BC2\u0BC6-\u0BC8\u0BCA-\u0BCD\u0C01-\u0C03\u0C3E-\u0C56\u0D3E-\u0D44\u0D46-\u0D48\u0E30-\u0E3A\u0E47-\u0E4E\u0EB1-\u0EBC\u0EC8-\u0ECD])+

FULLURL = https?:\/\/[^ \t\n\f\r\"<>|()]+[^ \t\n\f\r\"<>|.!?(){},-]
LIKELYURL = ((www\.([^ \t\n\f\r\"<>|.!?(){},]+\.)+[a-zA-Z]{2,4})|(([^ \t\n\f\r\"`'<>|.!?(){},-_$]+\.)+(com|net|org|edu)))(\/[^ \t\n\f\r\"<>|()]+[^ \t\n\f\r\"<>|.!?(){},-])?
EMAIL = [a-zA-Z0-9][^ \t\n\f\r\"<>|()\u00A0]*@([^ \t\n\f\r\"<>|().\u00A0]+\.)+[a-zA-Z]{2,4}

PAREN = -LRB-|-RRB-

%%

{ELLIPSIS}  { return getEllipsis(); }

{PAREN}     |
{FULLURL}   |
{LIKELYURL} |
{EMAIL}     |
{ARNUMWORD} |
{NUMBER}    |
{LATINWORD} |
{PERIOD}    |
{COMMA}     |
{EXCLAM}    |
{PUNC}      { return getNext(false); }

{NULLPRONSEG}  { if (removeProMarker) {
                if ( ! removeSegMarker) {
                  return getNext("-", yytext());
                } else if (invertible) {
                  prevWordAfter.append(yytext());
                }
              } else {
                return getNext(false);
              }
            }

{NULLPRON} { if (! removeProMarker) {
               return getNext(false);
             } else if (invertible) {
               prevWordAfter.append(yytext());
             }
           }

{ARWORD}    |
{FORNWORD}  { return getNext(true); }

{CR}        { if (tokenizeNLs) {
                return getNext(NEWLINE_TOKEN, yytext());
              } else if (invertible) {
                prevWordAfter.append(yytext());
              }
            } 
{SPACES}    { if (invertible) {
                prevWordAfter.append(yytext());
              }
            }
.           { System.err.printf("Untokenizable: %s%n", yytext());
	      return getNext(true);
	    }
<<EOF>>     { if (invertible) {
                String str = prevWordAfter.toString();
                prevWord.set(CoreAnnotations.AfterAnnotation.class, str);
                prevWordAfter.setLength(0);
              }
              return null; 
            }
