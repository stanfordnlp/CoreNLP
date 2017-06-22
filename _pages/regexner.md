---
title: RegexNERAnnotator 
keywords: regexner
permalink: '/regexner.html'
---

## Description

RegexNER Implements a simple, rule-based NER system over token sequences using an extension of Java regular expressions. The original goal of this Annotator was to provide a simple framework to incorporate named entities and named entity labels that are not annotated in traditional NER corpora, and hence not recoginized by our statistical NER classifiers. However, you can also use this annotator to simply do rule-based NER. Here is [a simple example](http://nlp.stanford.edu/software/regexner/) of how to use RegexNER. RegexNER is implemented using TokensRegex. For more complex applications, you might consider using [TokensRegex](http://nlp.stanford.edu/software/tokensregex.html) directly.

For English, we distribute CoreNLP with two files containing a default list of regular expressions, many just gazette entries, which label more fine-grained LOCATION-related subcategories (COUNTRY, STATE\_OR\_PROVINCE, CITY, NATIONALITY), the commonest online identifiers (URL, EMAIL), and a few miscellaneous categories originating from the TAC KBP evaluations (TITLE, IDEOLOGY, RELIGION, CRIMINAL\_CHARGE, CAUSE\_OF\_DEATH). Here, TITLE refers to job titles.


| Property name | Annotator class name | Generated Annotation |
| --- | --- | --- |
| regexner | RegexNERAnnotator | NamedEntityTagAnnotation |

## Options

| Option name | Type | Default | Description |
| --- | --- |
| `regexner.ignoreCase` | boolean | `false` |  If `true`, case is ignored for all patterns in all files. |
| `regexner.mapping` | String | `edu/stanford/nlp/models/regexner/type_map_clean` | Comma separated list of mapping files to use.  Each mapping file is a <b>tab</b>-delimited file. |
| `regexner.mapping.header` | String |`pattern,ner,overwrite,priority,group` | Comma separated list of header fields (or `true` if header is specified in the mapping file). |
| `regexner.mapping.field.<fieldname>` | String | null | Class name for CoreLabel key for annotation fields other than NER. |
| `regexner.commonWords` | String | null | Comma separated list of files for common words to not annotate (in case your mapping isn't very clean).  |
| `regexner.backgroundSymbol` | String | O,MISC | Comma separated list of NER labels that can always be replaced. |
| `regexner.posmatchtype` | enum | `MATCH_AT_LEAST_ONE_TOKEN` | How should `validpospattern` be used to match the POS of the tokens. <br/>`MATCH_ALL_TOKENS` - All tokens have to match. <br/> `MATCH_AT_LEAST_ONE_TOKEN` - At least one token has to match.<br/> `MATCH_ONE_TOKEN_PHRASE_ONLY` - Only has to match for one token phrases. |
| `regexner.validpospattern` | regex | null | Regular expression pattern for matching POS tags. |
| `regexner.noDefaultOverwriteLabels` | String | null | Comma-separated list of output types for which default NER labels are not overwritten. For  these types, only if the matched expression has an NER type matching the specified `overwriteableType` for the regex will the NER type be overwritten. |
| `regexner.verbose` | boolean | `false` | If `true`, turns on extra debugging messages. |

## Mapping files

The mapping file is a **tab**-delimited file.

The format of the default mapping file used by RegexNER is described in more detail on the Stanford NLP [website](http://nlp.stanford.edu/software/regexner/).

The format and the output fields can be changed by specifying a different `regexner.mapping.header`.

For instance, if you wanted to mark "Stanford University" as having NER tag of "SCHOOL" and linked to "https://en.wikipedia.org/wiki/Stanford_University",
you can do so by adding `normalized` to the headers:

    regexner.mapping.header=pattern,ner,normalized,overwrite,priority,group
    # Not needed, but illustrate how to link a field to an annotation
    regexner.mapping.field.normalized=edu.stanford.nlp.ling.CoreAnnotations$NormalizedNamedEntityTagAnnotation

And having your mapping file have entries such as:    

    Stanford University\tSCHOOL\thttps://en.wikipedia.org/wiki/Stanford_University
      
In the example, `\t` is used to indicate where a tab should occur.     
 
Note that the `pattern` field can be either

* a sequence of regex over the token text, each separated by whitespace (matching "\s+").
  <br/><em>Example</em>: `University of .*\tSCHOOL`

or

* a [TokensRegex expression](http://nlp.stanford.edu/software/tokensregex.html#TokensRegexPatterns) (marked by starting with "( " and ending with " )".
   <br/><em>Example</em>: `( /University/ /of/ [ {ner:LOCATION} ] )\tSCHOOL`
  
  Using TokensRegex patterns allows for matching on other annotated fields such as POS or NER.


