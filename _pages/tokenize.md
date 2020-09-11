---
layout: page
title: Tokenization
keywords: tokenize, TokenizerAnnotator, tokenization
permalink: '/tokenize.html'
nav_order: 5
parent: Pipeline
---

## Description

Tokenization is the process of turning text into tokens. For instance the sentence `Marie was born in Paris.` would be tokenized as the list `"Marie", "was", "born", "in", "Paris", "."`.
CoreNLP splits texts into tokens with an elaborate collection of rules, designed to follow UD 2.0 specifications.

| Name | Annotator class name | Requirement | Generated Annotation | Description |
| --- | --- | --- | --- | --- |
| tokenize | TokenizerAnnotator | - | TokensAnnotation (list of tokens), and CharacterOffsetBeginAnnotation, CharacterOffsetEndAnnotation, TextAnnotation (for each token) | Tokenizes text |

## Tokenization For French, German, and Spanish

It is important to note that the full tokenization process for French, German, and Spanish also involves running the [MWTAnnotator](https://stanfordnlp.github.io/corenlp-docs-dev/mwt.html) for multi word token expansion after sentence splitting. Most of the following documentation is focused on English tokenization.

## Options

| Option name | Type | Default | Description |
| --- | --- | --- | --- |
| tokenize.language | Enum { English, French, German, Spanish, Unspecified, Whitespace } | Unspecified | Use the appropriate tokenizer for the given language. If the tokenizer is Unspecified, it defaults to using the English PTBTokenizer. |
| tokenize.class | class name | null | If non-null, use this class as the `Tokenizer`. In general, you can now more easily do this by specifying a language to the TokenizerAnnotator. |
| tokenize.whitespace | boolean | false | If set to true, separates words only when whitespace is encountered. |
| tokenize.keepeol | boolean | false | If true, end-of-line tokens are kept and used as sentence boundaries with the WhitespaceTokenizer. |
| tokenize.options | String | null | Accepts the options of `PTBTokenizer` for example, things like "americanize=false" or "strictTreebank3=true,untokenizable=allKeep". |
| tokenize.verbose | boolean | false | Make the TokenizerAnnotator verbose - that is, it prints out all tokenizations it performs. |

The `tokenize.options` option accepts a wide variety of settings for the `PTBTokenizer`.

| Option name | Description |
| --- | --- |
| invertible | Store enough information about the original form of the token and the whitespace around it that a list of tokens can be faithfully converted back to the original String. Valid only if the LexedTokenFactory is an instance of CoreLabelTokenFactory. The keys used are: `TextAnnotation` for the tokenized form, `OriginalTextAnnotation` for the original string, `BeforeAnnotation` and `AfterAnnotation` for the whitespace before and after a token, and perhaps `BeginPositionAnnotation` and `EndPositionAnnotation` to record token begin/after end character offsets, if they were specified to be recorded in TokenFactory construction. (Like the Java String class, begin and end are done so end - begin gives the token length.) |
| tokenizeNLs | Whether end-of-lines should become tokens (or just be treated as part of whitespace). |
| tokenizePerLine | Run the tokenizer separately on each line of a file. This has the following consequences: (i) A token (currently only SGML tokens) cannot span multiple lines of the original input, and (ii) The tokenizer will not examine/wait for input from the next line before deciding tokenization decisions on this line. The latter property stops the tokenizer getting extra information from the next line to help decide whether a period after an acronym should be treated as an end-of-sentence period or not. **Use this option for strictly line-oriented processing: Having this true is necessary to stop the tokenizer blocking and waiting for input after a newline is seen when the previous line ends with an abbreviation.** |
| ptb3Escaping | Enable all traditional PTB3 token transforms (like parentheses becoming -LRB-, -RRB-). This is a macro flag that sets or clears all the options below. (This escaping used to be the default in CoreNLP versions 3 and below. It is not what is used by CoreNLP version 4 models.) |
| ud | Tokenize in the way expected by Universal Dependencies (ud) corpora. This does less normalization. In particular parentheses tokens tokenize
just as themselves ("(" and ")") rather than being weirdly escaped. This is the default, used by all CoreNLP version 4 models.
| americanize | Whether to rewrite common British English spellings as American English spellings, e.g. "colour" becomes "color". |
| normalizeSpace | Whether any spaces in tokens (for example, in phone numbers or mixed fractions) get turned into U+00A0 (non-breaking space). The default is `true` and it's dangerous to turn this option off for most of our Stanford NLP software, which assumes no spaces in tokens. |
| normalizeAmpersandEntity | Whether to map the XML &amp; to an ampersand. |
| normalizeCurrency | Whether to do some awful lossy currency mappings to turn common currency characters into $, #, or "cents", reflecting the fact that nothing else appears in the old PTB3 WSJ (e.g., no Euro! Default is false. |
| normalizeFractions | Whether to map certain common composed fraction characters to spelled out letter forms, e.g., "½" becomes "1/2"). |
| normalizeParentheses | Whether to map round parentheses to -LRB-, -RRB-, as in the Penn Treebank |
| normalizeOtherBrackets | Whether to map other common bracket characters to -LCB-, -LRB-, -RCB-, -RRB-, roughly as in the Penn Treebank | 
| quotes | Select a style of mapping quotes. An enum with possible values (case insensitive):
latex, unicode, ascii, not\_cp1252, original. "ascii" maps all quote characters to the traditional ' and ".
"latex" maps quotes to ``, `, ', '', as in Latex and the PTB3 WSJ (though this is now heavily frowned on in Unicode).
"unicode" maps quotes to the range U+2018 to U+201D, the preferred unicode encoding of single and double quotes.
"original" leaves all quotes as they were. "not\_cp1252" only remaps invalid cp1252 quotes to Unicode.
The default is "not\_cp1252". |
| ellipses | Select a style for mapping ellipses (3 dots).  An enum with possible values
(case insensitive): unicode, ptb3, not\_cp1252, original. "ptb3" maps ellipses to three dots (...), the
old PTB3 WSJ coding of an ellipsis. "unicode" maps three dot and space three dot sequences to
U+2026 (…), the Unicode ellipsis character. "not\_cp1252" only remaps invalid cp1252 ellipses to unicode.
"original" leaves all ellipses as they were. The default is "not\_cp1252". |
| dashes | Select a style for mapping dashes. An enum with possible values (case insensitive): unicode, ptb3, not\_cp1252, original.
"ptb3" maps dashes to "--", the most prevalent old PTB3 WSJ coding of a dash (though some are just "-" HYPHEN-MINUS).
"unicode" maps "-", "--", and "---" HYPHEN-MINUS sequences and CP1252 dashes to Unicode en and em dashes.
"not\_cp1252" only remaps invalid cp1252 dashes to unicode. "original" leaves all dashes as they were. The default is "not\_cp1252".
| splitAssimilations | If true tokenize words like "gonna" as multiple tokens "gon", "na". If false, keep as one token. Default is true. |
| splitHyphenated | Whether or not to tokenize hyphenated words as several tokens ("school" "-" "aged", "frog" "-" "lipped"), keeping together the exceptions in Supplementary Guidelines for ETTB 2.0 by Justin Mott, Colin Warner, Ann Bies, Ann Taylor and CLEAR guidelines (Bracketing Biomedical Text) by Colin Warner et al. (2012), e.g., keeping together prefixes like "co-indexation". Default is currently true. |
| splitForwardSlash: Whether to tokenize segments of slashed tokens separately ("Asian" "/" "Indian", "and" "/" "or"). Default is true.
| escapeForwardSlashAsterisk | Whether to put a backslash escape in front of / and * as the old PTB3 WSJ does for some reason (something to do with Lisp readers??). |
| untokenizable | What to do with untokenizable characters (ones not known to the tokenizer). Six options combining whether to log a warning for none, the first, or all, and whether to delete them or to include them as single character tokens in the output: noneDelete, firstDelete, allDelete, noneKeep, firstKeep, allKeep. The default is "firstDelete". |
| strictTreebank3 | PTBTokenizer deliberately deviates from strict PTB3 WSJ tokenization in two cases. Setting this improves compatibility for those cases. They are: (i) When an acronym is followed by a sentence end, such as "U.K." at the end of a sentence, the PTB3 has tokens of "Corp" and ".", while by default PTBTokenizer duplicates the period returning tokens of "Corp." and ".", and (ii) PTBTokenizer will return numbers with a whole number and a fractional part like "5 7/8" as a single token, with a non-breaking space in the middle, while the PTB3 separates them into two tokens "5" and "7/8". (Exception: for only "U.S." the PTB3 treebank does have the two tokens "U.S." and "." like our default; strictTreebank3 now mimics that too.) The default is false. |
| strictFraction | Only split mixed fractions into two tokens (see under `strictTreebank3`). |
| strictAcronym |Only adopt the PTB3 tokenization of sentence final acronyms (see under `strictTreebank3`). |

## Tokenizing From The Command Line

This command will take in the text of the file `input.txt` and produce a human readable output of the tokens and their character offsets:

```bash
java edu.stanford.nlp.pipeline.StanfordCoreNLP -annotators tokenize -file input.txt
```

To just turn a file into a list of tokens, one per line, you can use:

```bash
java edu.stanford.nlp.pipeline.StanfordCoreNLP -annotators tokenize -outputFormat conll -output.columns word -file input.txt
```

Other output formats include `conllu`, `json`, and `serialized`.

The following command is an example of specifying `PTBTokenizer` options with the `tokenize.options` option:

```bash
java -Xmx5g edu.stanford.nlp.pipeline.StanfordCoreNLP -annotators tokenize -tokenize.options "splitHyphenated=false,americanize=false" -file input.txt
```

## Tokenizing From Java

```java
package edu.stanford.nlp.examples;

import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.pipeline.*;

import java.util.*;

public class PipelineExample {

  public static String text = "Marie was born in Paris.";

  public static void main(String[] args) {
    // set up pipeline properties
    Properties props = new Properties();
    // set the list of annotators to run
    props.setProperty("annotators", "tokenize");
    // example of how to customize the PTBTokenizer (these are just random example settings!!)
    props.setProperty("tokenize.options", "splitHyphenated=false,americanize=false");
    // build pipeline
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
    // create a document object
    CoreDocument doc = new CoreDocument(text);
    // annotate
    pipeline.annotate(doc);
    // display tokens
    for (CoreLabel tok : doc.tokens()) {
      System.out.println(String.format("%s\t%d\t%d", tok.word(), tok.beginPosition(), tok.endPosition()));
    }
  }
}
```

This demo code will produce the tokens and the character offsets of the text.

```
Marie	0	5
was	6	9
born	10	14
in	15	17
Paris	18	23
.	23	24
```
