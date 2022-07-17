package edu.stanford.nlp.international.spanish.process;

import java.io.Reader;
import java.util.Locale;
import java.util.Properties;
import java.util.regex.Pattern;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.process.AbstractTokenizer;
import edu.stanford.nlp.process.CoreLabelTokenFactory;
import edu.stanford.nlp.process.LexedTokenFactory;
import edu.stanford.nlp.process.LexerUtils;
import edu.stanford.nlp.util.logging.Redwood;

/**
 *  A tokenizer for Spanish. Adapted from PTBTokenizer and
 *  FrenchTokenizer, but with extra rules for Spanish orthography.
 *
 *  @author Ishita Prasad
 */

%%

%class SpanishLexer
%unicode
%function next
%type Object
%char
%caseless

%{

  /**
       * Constructs a new SpanishLexer.  You specify the type of result tokens with a
       * LexedTokenFactory, and can specify the treatment of tokens by boolean
       * options given in a comma separated String
       * (e.g., "invertible,normalizeParentheses=true").
       * If the String is {@code null} or empty, you get the traditional
       * PTB3 normalization behaviour (i.e., you get ptb3Escaping=false).  If you
       * want no normalization, then you should pass in the String
       * "ptb3Escaping=false".  The known option names are:
       * <ol>
       * <li>invertible: Store enough information about the original form of the
       *     token and the whitespace around it that a list of tokens can be
       *     faithfully converted back to the original String.  Valid only if the
       *     LexedTokenFactory is an instance of CoreLabelTokenFactory.  The
       *     keys used in it are TextAnnotation for the tokenized form,
       *     OriginalTextAnnotation for the original string, BeforeAnnotation and
       *     AfterAnnotation for the whitespace before and after a token, and
       *     perhaps BeginPositionAnnotation and EndPositionAnnotation to record
       *     token begin/after end offsets, if they were specified to be recorded
       *     in TokenFactory construction.  (Like the String class, begin and end
       *     are done so end - begin gives the token length.)
       * <li>tokenizeNLs: Whether end-of-lines should become tokens (or just
       *     be treated as part of whitespace)
       * <li>ptb3Escaping: Enable all traditional PTB3 token transforms
       *     (like -LRB-, -RRB-).  This is a macro flag that sets or clears all the
       *     options below.
       * <li>normalizeAmpersandEntity: Whether to map the XML &amp;amp; to an
       *      ampersand
       * <li>normalizeFractions: Whether to map certain common composed
       *     fraction characters to spelled out letter forms like "1/2"
       * <li>normalizeParentheses: Whether to map round parentheses to -LRB-,
       *     -RRB-, as in the Penn Treebank
       * <li>normalizeOtherBrackets: Whether to map other common bracket characters
       *     to -LCB-, -LRB-, -RCB-, -RRB-, roughly as in the Penn Treebank
       * <li>ellipses: [From CoreNLP 4.0] Select a style for mapping ellipses (3 dots).  An enum with possible values
       *     (case insensitive): unicode, ascii, not_cp1252, original. "ascii" maps ellipses to three dots (...), the
       *     old PTB3 WSJ coding of an ellipsis. "unicode" maps three dot and optional space sequences to
       *     U+2026, the Unicode ellipsis character. "not_cp1252" only remaps invalid cp1252 ellipses to unicode.
       *     "original" uses all ellipses as they were. The default is ascii. </li>
       * <li>dashes: [From CoreNLP 4.0] Select a style for mapping dashes. An enum with possible values
       *     (case insensitive): unicode, ascii, not_cp1252, original. "ascii" maps dashes to "--", the
       *     most prevalent old PTB3 WSJ coding of a dash (though some are just "-" HYPHEN-MINUS).
       *     "unicode" maps "-", "--", and "---" HYPHEN-MINUS sequences and CP1252 dashes to Unicode en and em dashes.
       *     "not_cp1252" only remaps invalid cp1252 dashes to unicode.
       *     "original" leaves all dashes as they were. The default is "not_cp1252". </li>
       * <li>escapeForwardSlashAsterisk: Whether to put a backslash escape in front
       *     of / and * as the old PTB3 WSJ does for some reason (something to do
       *     with Lisp readers??).
       * <li>untokenizable: What to do with untokenizable characters (ones not
       *     known to the tokenizers).  Six options combining whether to log a
       *     warning for none, the first, or all, and whether to delete them or
       *     to include them as single character tokens in the output: noneDelete,
       *     firstDelete, allDelete, noneKeep, firstKeep, allKeep.
       *     The default is "firstDelete".
       * <li>strictTreebank3: PTBTokenizer deliberately deviates from strict PTB3
       *      WSJ tokenization in two cases.  Setting this improves compatibility
       *      for those cases.  They are: (i) When an acronym is followed by a
       *      sentence end, such as "Corp." at the end of a sentence, the PTB3
       *      has tokens of "Corp" and ".", while by default PTBTokenizer duplicates
       *      the period returning tokens of "Corp." and ".", and (ii) PTBTokenizer
       *      will return numbers with a whole number and a fractional part like
       *      "5 7/8" as a single token (with a non-breaking space in the middle),
       *      while the PTB3 separates them into two tokens "5" and "7/8".
       *      (Exception: for "U.S." the treebank does have the two tokens
       *      "U.S." and "." like our default; strictTreebank3 now does that too.)
       * </ol>
       *
       * @param r The Reader to tokenize text from
       * @param tf The LexedTokenFactory that will be invoked to convert
       *    each substring extracted by the lexer into some kind of Object
       *    (such as a Word or CoreLabel).
       * @param props Options to the tokenizer (see constructor Javadoc)
       */
      public SpanishLexer(Reader r, LexedTokenFactory<?> tf, Properties props) {
        this(r);
        this.tokenFactory = tf;
        for (String key : props.stringPropertyNames()) {
          String value = props.getProperty(key);
          boolean val = Boolean.parseBoolean(value);
          if ("".equals(key)) {
            // allow an empty item
          } else if ("noSGML".equals(key)) {
            noSGML = val;
          } else if ("invertible".equals(key)) {
            invertible = val;
          } else if ("tokenizeNLs".equals(key)) {
            tokenizeNLs = val;
          } else if ("ptb3Escaping".equals(key)) {
            normalizeAmpersandEntity = val;
            normalizeFractions = val;
            normalizeParentheses = val;
            normalizeOtherBrackets = val;
            ellipsisStyle = val ? LexerUtils.EllipsesEnum.ASCII : LexerUtils.EllipsesEnum.ORIGINAL;
            dashesStyle = val ? LexerUtils.DashesEnum.ASCII : LexerUtils.DashesEnum.ORIGINAL;
            quoteStyle = val ? LexerUtils.QuotesEnum.ASCII : LexerUtils.QuotesEnum.ORIGINAL;
          } else if ("quotes".equals(key)) {
            quoteStyle = LexerUtils.QuotesEnum.valueOf(key.trim().toLowerCase(Locale.ROOT));
          } else if ("normalizeAmpersandEntity".equals(key)) {
            normalizeAmpersandEntity = val;
          } else if ("normalizeFractions".equals(key)) {
            normalizeFractions = val;
          } else if ("normalizeParentheses".equals(key)) {
            normalizeParentheses = val;
          } else if ("normalizeOtherBrackets".equals(key)) {
            normalizeOtherBrackets = val;
          } else if ("ellipses".equals(key)) {
            try {
              ellipsisStyle = LexerUtils.EllipsesEnum.valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException iae) {
              throw new IllegalArgumentException ("Not a valid ellipses style: " + value);
            }
          } else if ("dashes".equals(key)) {
            try {
              dashesStyle = LexerUtils.DashesEnum.valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException iae) {
              throw new IllegalArgumentException ("Not a valid dashes style: " + value);
            }
          } else if ("escapeForwardSlashAsterisk".equals(key)) {
            escapeForwardSlashAsterisk = val;
          } else if ("untokenizable".equals(key)) {
            switch (value) {
              case "noneDelete":
                untokenizable = UntokenizableOptions.NONE_DELETE;
                break;
              case "firstDelete":
                untokenizable = UntokenizableOptions.FIRST_DELETE;
                break;
              case "allDelete":
                untokenizable = UntokenizableOptions.ALL_DELETE;
                break;
              case "noneKeep":
                untokenizable = UntokenizableOptions.NONE_KEEP;
                break;
              case "firstKeep":
                untokenizable = UntokenizableOptions.FIRST_KEEP;
                break;
              case "allKeep":
                untokenizable = UntokenizableOptions.ALL_KEEP;
                break;
              default:
                throw new IllegalArgumentException("SpanishLexer: Invalid option value in constructor: " + key + ": " + value);
            }
          } else if ("strictTreebank3".equals(key)) {
            strictTreebank3 = val;
          } else {
            throw new IllegalArgumentException(String.format("%s: Invalid options key in constructor: %s%n", this.getClass().getName(), key));
          }
        }
        // this.seenUntokenizableCharacter = false; // unnecessary, it's default initialized
        if (invertible) {
          if ( ! (tf instanceof CoreLabelTokenFactory)) {
            throw new IllegalArgumentException("SpanishLexer: the invertible option requires a CoreLabelTokenFactory");
          }
          prevWord = (CoreLabel) tf.makeToken("", 0, 0);
          prevWordAfter = new StringBuilder();
        }
      }


      /** Turn on to find out how things were tokenized. */
      private static final boolean DEBUG = false;

      /** A logger for this class */
      private static final Redwood.RedwoodChannels logger = Redwood.channels(SpanishLexer.class);

      private LexedTokenFactory<?> tokenFactory;
      private CoreLabel prevWord;
      private StringBuilder prevWordAfter;
      private boolean seenUntokenizableCharacter;
      private enum UntokenizableOptions { NONE_DELETE, FIRST_DELETE, ALL_DELETE, NONE_KEEP, FIRST_KEEP, ALL_KEEP }
      private UntokenizableOptions untokenizable = UntokenizableOptions.FIRST_DELETE;

      /* Flags begin with historical ptb3Escaping behavior */
      private boolean invertible;
      private boolean tokenizeNLs;
      private boolean noSGML;
      private boolean normalizeAmpersandEntity = true;
      private boolean normalizeFractions = true;
      private boolean normalizeParentheses;
      private boolean normalizeOtherBrackets;
      private LexerUtils.EllipsesEnum ellipsisStyle = LexerUtils.EllipsesEnum.ASCII;
      private LexerUtils.QuotesEnum quoteStyle = LexerUtils.QuotesEnum.ASCII;
      private LexerUtils.DashesEnum dashesStyle = LexerUtils.DashesEnum.NOT_CP1252;
      private boolean escapeForwardSlashAsterisk = false;
      private boolean strictTreebank3;


      /*
       * This has now been extended to cover the main Windows CP1252 characters,
       * at either their correct Unicode codepoints, or in their invalid
       * positions as 8 bit chars inside the iso-8859 control region.
       *
       * ellipsis  	85  	0133  	2026  	8230
       * single quote curly starting 	91 	0145 	2018 	8216
       * single quote curly ending 	92 	0146 	2019 	8217
       * double quote curly starting 	93 	0147 	201C 	8220
       * double quote curly ending 	94 	0148 	201D 	8221
       * en dash  	96  	0150  	2013  	8211
       * em dash  	97  	0151  	2014  	8212
       */

      /* Using Ancora style brackets and parens */
      public static final String openparen = "-LRB-";
      public static final String closeparen = "-RRB-";
      public static final String openbrace = "-LCB-";
      public static final String closebrace = "-RCB-";

      public static final String NEWLINE_TOKEN = "*NL*";
      public static final String COMPOUND_ANNOTATION = "comp";
      public static final String VB_PRON_ANNOTATION = "vb_pn_attached";
      public static final String CONTR_ANNOTATION = "contraction";

      private static final Pattern NO_BREAK_SPACE = Pattern.compile("\u00A0");


      private static String convertToEl(String l) {
        if (Character.isLowerCase(l.charAt(0))) {
          return "e" + l;
        } else {
          return "E" + l;
        }
      }

      private Object getNext() {
        final String txt = yytext();
        return getNext(txt, txt);
      }

      /** Make the next token.
       *  @param txt What the token should be
       *  @param originalText The original String that got transformed into txt
       */
      private Object getNext(String txt, String originalText) {
        return getNext(txt, originalText, null);
      }

      private Object getNext(String txt, String originalText, String annotation) {
        txt = LexerUtils.removeSoftHyphens(txt);
        Label w = (Label) tokenFactory.makeToken(txt, Math.toIntExact(yychar), yylength());
        if (invertible || annotation != null) {
          CoreLabel word = (CoreLabel) w;
          if (invertible) {
            String str = prevWordAfter.toString();
            prevWordAfter.setLength(0);
            word.set(CoreAnnotations.OriginalTextAnnotation.class, originalText);
            word.set(CoreAnnotations.BeforeAnnotation.class, str);
            prevWord.set(CoreAnnotations.AfterAnnotation.class, str);
            prevWord = word;
          }
          if (annotation != null) {
            word.set(CoreAnnotations.ParentAnnotation.class, annotation);
          }
        }
        return w;
      }

      private Object getNormalizedAmpNext() {
        final String txt = yytext();
        return normalizeAmpersandEntity ?
          getNext(LexerUtils.normalizeAmp(txt), txt) : getNext();
      }

%}

%include ../../../process/LexCommon.tokens

SENTEND = {SPACENL}({SPACENL}|([:uppercase:]|{SGML2}))
HYPHEN = [-_\u058A\u2010\u2011]
HYPHENS = \-+

DATE = {DIGIT}{1,2}[\-\/]{DIGIT}{1,2}[\-\/]{DIGIT}{2,4}

/* Handles Arabic numerals & soft-hyphens */
NUM = {DIGIT}+|{DIGIT}*([-_.:,\u00AD\u066B\u066C]{DIGIT}+)+

/* Now don't allow bracketed negative numbers!
   They have too many uses (e.g., years or times in parentheses), and
   having them in tokens messes up treebank parsing. */
NUMBER = [\-+]?{NUM}
SUBSUPNUM = [\u207A\u207B\u208A\u208B]?([\u2070\u00B9\u00B2\u00B3\u2074-\u2079]+|[\u2080-\u2089]+)

/* European 24hr time expression e.g. 20h14 */
TIMEXP = {DIGIT}{1,2}(h){DIGIT}{1,2}

/* Spanish ordinals */
ORDINAL = [:digit:]*([13]\.?er|[:digit:]\.?[oa\u00BA\u00AA\u\u00B0])

/* Constrain fraction to only match likely fractions */
FRAC = ({DIGIT}{1,4}[- \u00A0])?{DIGIT}{1,4}(\\?\/|\u2044){DIGIT}{1,4}
FRACSTB3 = ({DIGIT}{1,4}-)?{DIGIT}{1,4}(\\?\/|\u2044){DIGIT}{1,4}
FRAC2 = [\u00BC\u00BD\u00BE\u2153-\u215E]


/* These are cent and pound sign, euro and euro, and Yen, Lira */
MONEYSIGN = [\$\u00A2\u00A3\u00A4\u00A5\u0080\u20A0\u20AA\u20AC\u060B\u0E3F\u20A4\uFFE0\uFFE1\uFFE5\uFFE6]


/* For some reason U+0237-U+024F (dotless j) isn't in [:letter:]. Recent additions? */
CHAR = [:letter:]|{SPLET}|[\u00AD\u0237-\u024F\u02C2-\u02C5\u02D2-\u02DF\u02E5-\u02FF\u0300-\u036F\u0370-\u037D\u0384\u0385\u03CF\u03F6\u03FC-\u03FF\u0483-\u0487\u04CF\u04F6-\u04FF\u0510-\u0525\u055A-\u055F\u0591-\u05BD\u05BF\u05C1\u05C2\u05C4\u05C5\u05C7\u0615-\u061A\u063B-\u063F\u064B-\u065E\u0670\u06D6-\u06EF\u06FA-\u06FF\u070F\u0711\u0730-\u074F\u0750-\u077F\u07A6-\u07B1\u07CA-\u07F5\u07FA\u0900-\u0903\u093C\u093E-\u094E\u0951-\u0955\u0962-\u0963\u0981-\u0983\u09BC-\u09C4\u09C7\u09C8\u09CB-\u09CD\u09D7\u09E2\u09E3\u0A01-\u0A03\u0A3C\u0A3E-\u0A4F\u0A81-\u0A83\u0ABC-\u0ACF\u0B82\u0BBE-\u0BC2\u0BC6-\u0BC8\u0BCA-\u0BCD\u0C01-\u0C03\u0C3E-\u0C56\u0D3E-\u0D44\u0D46-\u0D48\u0E30-\u0E3A\u0E47-\u0E4E\u0EB1-\u0EBC\u0EC8-\u0ECD]
WORD = ({CHAR})+

APOS = ['\u0092\u2019]|&apos;
APOSETCETERA = {APOS}|[\u0091\u2018\u201B]

/* Includes words with numbers, eg. sp3 */
WORD2 = {WORD}(({NUM}|{APOSETCETERA}){WORD}?)+{WORD}

/* Includes words with apostrophes in the middle (french, english,
   catalan loanwords) */
WORD3 = {NUM}?({WORD}|{WORD2}){NUM}?

/* all types of "words" (word, word2, word3) */
ANYWORD = (({WORD})|({WORD2})|({WORD3}))

/* common units abbreviated - to differentiate between WORD_NUM
 * as WORD_NUMs shouldn't be split but these should. */
UNIT_PREF = [dcm\u00B5\uO3BCnpfazyhkMGTPEZY]|da
UNIT = ({UNIT_PREF})?m|kg|s|[A]|[K]|mol|rad|[H]z|N|Pa|J|W|C|V

/* prefixed compounds that shouldn't be split off */
PREFIX = (anti|co|ex|meso|neo|pre|pro|quasi|re|semi|sub){HYPHEN}
COMPOUND_NOSPLIT = {PREFIX}{ANYWORD}

/* spanish compounds */
COMPOUND = {WORD}({HYPHEN}{WORD})+

/* Spanish enclitic pronouns attached at the end of infinitive, gerund,
 * and imperative verbs should be split:
 *
 * cómpremelos => cómpre + me + los (buy + me + it)
 * házmelo => ház + me + lo (do it (for) me)
 * Escribámosela => Escribámo + se + la (write her it)
 *
 */

/* Patterns to help identify the various types of verb+enclitics */
OS = os(l[oa]s?)?
ATTACHED_PRON = ((me|te|se|nos|les?)(l[oa]s?)?)|l[oa]s?
VB_IRREG = d[ií]|h[aá]z|v[eé]|p[oó]n|s[aá]l|sé|t[eé]n|v[eé]n
VB_REG = ir|{WORD}([aeiáéí]r|[áé]ndo|[aeáé]n?|[aeáé]mos?)
VB_PREF = {VB_IRREG}|({VB_REG})

/* Handles second person plural imperatives:
 *
 * Sentaos => Senta + os (seat + yourselves)
 * Vestíos => Vestí + os (dress + yourselves)
 */
VB_2PP_PRON = ({CHAR}*)[aeiáéí]((d{ATTACHED_PRON})|{OS})

/* Handles all other verbs with attached pronouns  */
VB_ATTACHED_PRON = ({VB_PREF}){ATTACHED_PRON}|{OS}

/* Spanish contractions:
 *
 * al => a + l (to the)
 * del => de + l (of the)
 * conmigo/contigo/consigo => con + migo/tigo/sigo (with me/you/them)
 *
 */
CONTRACTION = del|al|con[mts]igo

/* URLs, email, and Twitter handles
   Technically, Twitter names should be capped at 15 characters.
   However, then you get into weirdness with what happens to the
   rest of the characters. */
FULLURL = https?:\/\/[^ \t\n\f\r\"<>|()]+[^ \t\n\f\r\"<>|.!?(){},-]
LIKELYURL = ((www\.([^ \t\n\f\r\"<>|.!?(){},]+\.)+[a-zA-Z]{2,4})|(([^ \t\n\f\r\"`'<>|.!?(){},-_$]+\.)+(com|net|org|edu)))(\/[^ \t\n\f\r\"<>|()]+[^ \t\n\f\r\"<>|.!?(){},-])?
EMAIL = [a-zA-Z0-9][^ \t\n\f\r\"<>|()\u00A0]*@([^ \t\n\f\r\"<>|().\u00A0]+\.)*([^ \t\n\f\r\"<>|().\u00A0]+)
TWITTER_NAME = @[a-zA-Z_][a-zA-Z_0-9]*
TWITTER_CATEGORY = #{WORD}
TWITTER = {TWITTER_NAME}|{TWITTER_CATEGORY}

/* ABBREVIATIONS - INDUCED FROM 1987 WSJ BY HAND */
ABMONTH = ene|feb|mar|abr|may|jun|jul|ago|sep|set|sept|oct|nov|dic
ABDAYS = lun|mar|mi[\u00E9e]|jue|vie|sab|dom

/* In the caseless world S.p.A. "Società Per Azioni (Italian: shared company)" is got as a regular acronym */
/* ??? */
ACRO = [A-Za-z]{1,2}(\.[A-Za-z]{1,2})+|(Canada|Sino|Korean|EU|Japan|non)-U\.S|U\.S\.-(U\.K|U\.S\.S\.R)
ABTITLE = Mr|Mrs|Ms|[M]iss|Drs?|Profs?|Sens?|Reps?|Attys?|Lt|Col|Gen|Messrs|Govs?|Adm|Rev|Maj|Sgt|Cpl|Pvt|Mt|Capt|Ste?|Ave|Pres|Lieut|Hon|Brig|Co?mdr|Pfc|Spc|Supts?|Det|M|MM|Mme|Mmes|Mlle|Mlles

ABTITLE_ES = Sr|Sra|Srta|D|Da|D[\u00F1n]a|Dr|Dra|Prof|Profa|Gob|Gral
ABPTIT_ES = Av|Avda|apdo|Esq|Uds?|Vds?


/* Bhd is Malaysian companies! */
/* TODO: Change the class of at least Pty as usually another one like Ltd following... */

ABCOMP = Inc|Cos?|Corp|Pp?tys?|Ltd|Plc|Bancorp|Dept|Bhd|Assn|Univ|Intl|Sys
ABCOMP2 = Invt|Elec|Natl|M[ft]g
/* Don't included fl. oz. since Oz turns up too much in caseless tokenizer. */

/* ABBREV1 abbreviations are normally followed by lower case words.  If
   they're followed by an uppercase one, we assume there is also a
   sentence boundary */
ABBREV1 = ({ABMONTH}|{ABDAYS}|{ABCOMP}|{ABPTIT_ES}|etc|al|seq|p\.ej)\.

/* ABRREV2 abbreviations are normally followed by an upper case word.  We
   assume they aren't used sentence finally */
/* ACRO Is a bad case -- can go either way! */
ABBREV4 = [A-Za-z]|{ABTITLE}|{ABTITLE_ES}|vs|Alex|Wm|Jos|Cie|a\.k\.a|cf|TREAS|{ACRO}|{ABCOMP2}
ABBREV2 = {ABBREV4}\.

/* Cie. is used by French companies sometimes before and sometimes at end as in English Co.  But we treat as allowed to have Capital following without being sentence end.  Cia. is used in Spanish/South American company abbreviations, which come before the company name, but we exclude that and lose, because in a caseless segmenter, it's too confusable with CIA. */

/* Fake duck feet appear sometimes in WSJ, and aren't likely to be SGML, less than, etc., so group. */
FAKEDUCKFEET = <<|>>
LESSTHAN = <|&lt;
GREATERTHAN = >|&gt;
OPBRAC = [<\[]|&lt;
CLBRAC = [>\]]|&gt;
LDOTS = \.\.\.+|[\u0085\u2026]
SPACEDLDOTS = \.[ \u00A0](\.[ \u00A0])+\.
ATS = @+
UNDS = _+
ASTS = \*+|(\\\*){1,3}
HASHES = #+
FNMARKS = {ATS}|{HASHES}|{UNDS}
INSENTP = [,;:\u3001]
QUOTES = {APOSETCETERA}|''|[`\u2018\u2019\u201A\u201B\u201C\u201D\u0082\u0084\u008B\u0091-\u0094\u009B\u201E\u201F\u2039\u203A\u00AB\u00BB]{1,2}

DBLQUOT = \"|&quot;

/* Smileys (based on Chris Potts' sentiment tutorial, but much more restricted set - e.g., no "8)", "do:" or "):", too ambiguous) and simple Asian smileys */
SMILEY = [<>]?[:;=][\-o\*']?[\(\)DPdpO\\{@\|\[\]]


/* U+2200-U+2BFF has a lot of the various mathematical, etc. symbol ranges */
MISCSYMBOL = [+%&~\^|\\¦\u00A7¨\u00A9\u00AC\u00AE¯\u00B0-\u00B3\u00B4-\u00BA\u00D7\u00F7\u0387\u05BE\u05C0\u05C3\u05C6\u05F3\u05F4\u0600-\u0603\u0606-\u060A\u060C\u0614\u061B\u061E\u066A\u066D\u0703-\u070D\u07F6\u07F7\u07F8\u0964\u0965\u0E4F\u1FBD\u2016\u2017\u2020-\u2023\u2030-\u2038\u203B\u203E-\u2042\u2044\u207A-\u207F\u208A-\u208E\u2100-\u214F\u2190-\u21FF\u2200-\u2BFF\u3012\u30FB\uFF01-\uFF0F\uFF1A-\uFF20\uFF3B-\uFF40\uFF5B-\uFF65\uFF65]
/* \uFF65 is Halfwidth katakana middle dot; \u30FB is Katakana middle dot */
/* Math and other symbols that stand alone: °²× ∀ */

/* CP1252: dagger, double dagger, per mille, bullet, small tilde, trademark */
CP1252_MISC_SYMBOL = [\u0086\u0087\u0089\u0095\u0098\u0099]


%%

{SGML2}       { if (!noSGML) {
                  return getNext();
                }
              }
{SPMDASH}               { final String origTxt = yytext();
                          String tok = LexerUtils.handleDashes(origTxt, dashesStyle);
                          if (DEBUG) { logger.info("Used {SPMDASH} to recognize " + origTxt + " as " + tok); }
                          return getNext(tok, origTxt);
                        }
{ORDINAL}/{SPACE}       { return getNext(); }
{SPAMP}			{ return getNormalizedAmpNext(); }
{SPPUNC} |
{TIMEXP}                { return getNext(); }

{CONTRACTION}           { final String origTxt = yytext();
                          return getNext(origTxt, origTxt, CONTR_ANNOTATION);}

{VB_ATTACHED_PRON} |
{VB_2PP_PRON}           { final String origTxt = yytext();
                          return getNext(origTxt, origTxt, VB_PRON_ANNOTATION);}

{COMPOUND_NOSPLIT}      { final String origTxt = yytext();
                          return getNext(LexerUtils.handleQuotes(LexerUtils.handleDashes(origTxt, dashesStyle), false, quoteStyle), origTxt);
                        }
{COMPOUND}              { final String origTxt = yytext();
                          return getNext(LexerUtils.handleQuotes(LexerUtils.handleDashes(origTxt, dashesStyle), false, quoteStyle), origTxt, COMPOUND_ANNOTATION);
                        }

{NUM}/{UNIT}            { return getNext(); }

{WORD2}                 { final String origTxt = yytext();
                          return getNext(LexerUtils.handleQuotes(origTxt, false, quoteStyle), origTxt);}

{WORD}|{WORD3}	        { return getNext(); }

{FULLURL} |
{LIKELYURL}		{ String txt = yytext();
                          if (escapeForwardSlashAsterisk) {
                            txt = LexerUtils.escapeChar(txt, '/');
                            txt = LexerUtils.escapeChar(txt, '*');
                          }
                          return getNext(txt, yytext()); }
{EMAIL}	|
{TWITTER}               { return getNext(); }

{DATE}			{ String txt = yytext();
                          if (escapeForwardSlashAsterisk) {
                            txt = LexerUtils.escapeChar(txt, '/');
                          }
                          return getNext(txt, yytext());
                         }
{NUMBER} |
{SUBSUPNUM}             { return getNext(); }
{MONEYSIGN}		{ String tok = yytext();
			  String norm = tok;
			  if ("\u0080".equals(tok)) {
			      norm = "\u20AC";
                          }
                          if (DEBUG) { logger.info("Used {MONEYSIGN} to recognize " + tok + " as " + norm); }
                          return getNext(norm, tok);
			}
{FRAC} |
{FRACSTB3} |
{FRAC2}                     { String txt = yytext();
                              String norm = LexerUtils.normalizeFractions(normalizeFractions, escapeForwardSlashAsterisk, txt);
                              if (DEBUG) { logger.info("Used {FRAC2} to recognize " + txt + " as " + norm +
                                                   "; normalizeFractions=" + normalizeFractions +
                                                   ", escapeForwardSlashAsterisk=" + escapeForwardSlashAsterisk); }
                              return getNext(norm, txt);
                            }

{ABBREV1}/{SENTEND}	{
                          String s;
                          if (strictTreebank3 && ! "U.S.".equals(yytext())) {
                            yypushback(1); // return a period for next time
                            s = yytext();
                          } else {
                            s = yytext();
                            yypushback(1); // return a period for next time
                          }
	                  return getNext(s, yytext()); }
{ABBREV1}/[^][^]	{ return getNext(); }
{ABBREV1}		{ // this one should only match if we're basically at the end of file
			  // since the last one matches two things, even newlines
                          String s;
                          if (strictTreebank3 && ! "U.S.".equals(yytext())) {
                            yypushback(1); // return a period for next time
                            s = yytext();
                          } else {
                            s = yytext();
                            yypushback(1); // return a period for next time
                          }
	                  return getNext(s, yytext()); }
{ABBREV2}		{ return getNext(); }
{ABBREV4}/{SPACENL_ONE_CHAR}	{ return getNext(); }
{ACRO}/{SPACENL_ONE_CHAR}	{ return getNext(); }
{DBLQUOT} |
{QUOTES}		{ final String origTxt = yytext();
                          return getNext(LexerUtils.handleQuotes(origTxt, false, quoteStyle), origTxt);
			      }

{FILENAME}/({SPACENL_ONE_CHAR}|[.?!,\"'<()]) {
                          String txt = yytext();
                          if (DEBUG) { logger.info("Used {FILENAME} to recognize " + txt); }
                          return getNext(txt, txt);
                        }
{PHONE}         { String txt = yytext();
		  String origTxt = txt;
		  txt = LexerUtils.pennNormalizeParens(txt, normalizeParentheses);
                  return getNext(txt, yytext());
		}
\x7F            { if (invertible) {
                    prevWordAfter.append(yytext());
                   }
                }
{LESSTHAN}      { return getNext("<", yytext()); }
{GREATERTHAN}   { return getNext(">", yytext()); }
{SMILEY}/[^\p{Alpha}\p{Digit}] { String txt = yytext();
                  String origText = txt;
		  txt = LexerUtils.pennNormalizeParens(txt, normalizeParentheses);
                  return getNext(txt, origText);
                }
{EMOJI}         { String txt = yytext();
                  if (DEBUG) { logger.info("Used {EMOJI} to recognize " + txt); }
                  return getNext(txt, txt);
                }
{OPBRAC}	{ if (normalizeOtherBrackets) {
                    return getNext(openparen, yytext()); }
                  else {
                    return getNext();
                  }
                }
{CLBRAC}	{ if (normalizeOtherBrackets) {
                    return getNext(closeparen, yytext()); }
                  else {
                    return getNext();
                  }
                }
\{		{ if (normalizeOtherBrackets) {
                    return getNext(openbrace, yytext()); }
                  else {
                    return getNext();
                  }
                }
\}		{ if (normalizeOtherBrackets) {
                    return getNext(closebrace, yytext()); }
                  else {
                    return getNext();
                  }
                }
\(		{ if (normalizeParentheses) {
                    return getNext(openparen, yytext()); }
                  else {
                    return getNext();
                  }
                }
\)		{ if (normalizeParentheses) {
                    return getNext(closeparen, yytext()); }
                  else {
                    return getNext();
                  }
                }
{HYPHENS}       { final String origTxt = yytext();
                  String tok = origTxt;
                  if (yylength() <= 4) {
                     tok = LexerUtils.handleDashes(origTxt, dashesStyle);
                  }
                  if (DEBUG) { logger.info("Used {SPMDASH} to recognize " + origTxt + " as " + tok); }
                  return getNext(tok, origTxt);
                }
{LDOTS}|{SPACEDLDOTS}    { String tok = yytext();
                           String norm = LexerUtils.handleEllipsis(tok, ellipsisStyle);
                           if (DEBUG) { logger.info("Used {LDOTS} to recognize " + tok + " as " + norm); }
                           return getNext(norm, tok);
                         }
{FNMARKS}	{ return getNext(); }
{ASTS}		{ if (escapeForwardSlashAsterisk) {
                    return getNext(LexerUtils.escapeChar(yytext(), '*'), yytext()); }
                  else {
                    return getNext();
                  }
                }
{INSENTP}	{ return getNext(); }
[?!]+           { return getNext(); }
[.¡¿\u037E\u0589\u061F\u06D4\u0700-\u0702\u07FA\u3002]	{ return getNext(); }
=		{ return getNext(); }
\/		{ if (escapeForwardSlashAsterisk) {
                    return getNext(LexerUtils.escapeChar(yytext(), '/'), yytext()); }
                  else {
                    return getNext();
                  }
                }

{FAKEDUCKFEET} |
{MISCSYMBOL}	{ return getNext(); }
{CP1252_MISC_SYMBOL}  { String tok = yytext();
                        String norm = LexerUtils.processCp1252misc(tok);
                        if (DEBUG) { logger.info("Used {CP1252_MISC_SYMBOL} to recognize " + tok + " as " + norm); }
                        return getNext(norm, tok);
                      }

\0|{SPACES}|[\u200B\u200E-\u200F\uFEFF]	{ if (invertible) {
                     prevWordAfter.append(yytext());
                  }
                }
{NEWLINE}	      { if (tokenizeNLs) {
                      return getNext(NEWLINE_TOKEN, yytext()); // js: for tokenizing carriage returns
                  } else if (invertible) {
                      prevWordAfter.append(yytext());
                  }
                }
&nbsp;		      { if (invertible) {
                     prevWordAfter.append(yytext());
                  }
                }
.       { String str = yytext();
          int first = str.charAt(0);
          String msg = String.format("Untokenizable: %s (U+%s, decimal: %s)", yytext(), Integer.toHexString(first).toUpperCase(), Integer.toString(first));
          switch (untokenizable) {
            case NONE_DELETE:
              if (invertible) {
                prevWordAfter.append(str);
              }
              break;
            case FIRST_DELETE:
              if (invertible) {
                prevWordAfter.append(str);
              }
              if ( ! this.seenUntokenizableCharacter) {
                logger.warning(msg);
                this.seenUntokenizableCharacter = true;
              }
              break;
            case ALL_DELETE:
              if (invertible) {
                prevWordAfter.append(str);
              }
              logger.warning(msg);
              this.seenUntokenizableCharacter = true;
              break;
            case NONE_KEEP:
              return getNext();
            case FIRST_KEEP:
              if ( ! this.seenUntokenizableCharacter) {
                logger.warning(msg);
                this.seenUntokenizableCharacter = true;
              }
              return getNext();
            case ALL_KEEP:
              logger.warning(msg);
              this.seenUntokenizableCharacter = true;
              return getNext();
          }
        }
<<EOF>> { if (invertible) {
            prevWordAfter.append(yytext());
            String str = prevWordAfter.toString();
            prevWordAfter.setLength(0);
            prevWord.set(CoreAnnotations.AfterAnnotation.class, str);
          }
          return null;
        }
