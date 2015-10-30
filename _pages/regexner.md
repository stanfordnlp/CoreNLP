---
title: RegexNERAnnotator 
keywords: regexner
permalink: '/regexner.html'
---

## Description

Implements a simple, rule-based NER over token sequences using Java regular expressions. The goal of this Annotator is to provide a simple framework to incorporate NE labels that are not annotated in traditional NL corpora. For example, the default list of regular expressions that we distribute in the models file recognizes ideologies (IDEOLOGY), nationalities (NATIONALITY), religions (RELIGION), and titles (TITLE). Here is [a simple example](http://nlp.stanford.edu/software/regexner/) of how to use RegexNER. For more complex applications, you might consider [TokensRegex](http://nlp.stanford.edu/software/tokensregex.shtml).

| Property name | Annotator class name | Generated Annotation |
| --- | --- | --- |
| regexner | RegexNERAnnotator | NamedEntityTagAnnotation |

## Options

| Field | Description |
| --- | --- |
| `regexner.ignoreCase` | If `true`, case is ignored. Default: `false` |
| `regexner.mapping` | Comma separated list of mapping files to use.  Each mapping file is a <b>tab</b> delimited file. Default: `edu/stanford/nlp/models/regexner/type_map_clean` |
| `regexner.mapping.header` | Comma separated list of header fields (or `true` if header is specified in the mapping file). Default: `pattern,ner,overwrite,priority,group` |
| `regexner.mapping.field.<fieldname>` | Class mapping for annotation fields other than ner. |
| `regexner.commonWords` | Comma separated list of files for common words to not annotate (in case your mapping isn't very clean).  |
| `regexner.backgroundSymbol` | Comma separated list of NER labels to always replace.  Default: `O,MISC` |
| `regexner.posmatchtype` | How should `validpospattern` be used to match the POS of the tokens. `MATCH_ALL_TOKENS` - All tokens has to match.<br/> `MATCH_AT_LEAST_ONE_TOKEN` - At least one token has to match.<br/> `MATCH_ONE_TOKEN_PHRASE_ONLY` - Only has to match for one token phrases. Default: `MATCH_AT_LEAST_ONE_TOKEN` |
| `regexner.validpospattern` | Regular expression pattern for matching POS tags. |
| `regexner.noDefaultOverwriteLabels` | Comma separated list of output types for which default NER labels are not overwritten.  For these types, only if the matched expression has NER type matching the specified `overwriteableType` for the regex will the NER type be overwritten. |
| `regexner.verbose` | If `true`, turns on extra debugging messages. Default: `false` |

## More information
 
The `pattern` field can be either

* a sequence of regex, each separated by whitespace (matching "\s+").

or

* a TokensRegex expression (marked by starting with "( " and ending with " )".  See {@link TokenSequencePattern} for TokensRegex syntax.
   <em>Example</em>: <code>( /University/ /of/ [ {ner:LOCATION} ] )    SCHOOL</code>



RegexNER is described in more detail on the Stanford NLP [website](http://nlp.stanford.edu/software/regexner/).
