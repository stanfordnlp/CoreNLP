---
title: SUTime
keywords: sutime
permalink: '/sutime.html'
---

StanfordCoreNLP includes [SUTime](https://nlp.stanford.edu/software/sutime.shtml), a library for processing temporal expressions
such as `February 4th, 2019.`  SUTime is built on top of [TokensRegex](https://stanfordnlp.github.io/CoreNLP/tokensregex.html).


## Other Resources

In addition to the info on this page, there are some other great resources to check out with very useful information:

* [Original slides](https://nlp.stanford.edu/software/SUTime.pptx)
* [Original TokensRegex site](https://nlp.stanford.edu/software/sutime.shtml)

## High Level Overview

SUTime will match a variety of temporal expressions and link them to a [TIMEX3](http://www.timeml.org/site/publications/timeMLdocs/timeml_1.2.1.html#timex3)
object.

4 types of temporal expression can be recognized.

| Temporal type | Example |
| :--- | :--- |
| DATE | February 4th, 2019 |
| TIME | 4:00pm |
| DURATION | 15 years |
| SET | Every 3rd Wednesday |

Phrases are recognized and linked based on a set of TokensRegex rules.

The English rules are `edu/stanford/nlp/models/sutime/defs.sutime.txt,edu/stanford/nlp/models/sutime/english.sutime.txt,edu/stanford/nlp/models/sutime/english.holidays.sutime.txt`.  These files are available in the default models jar and can also be viewed on [GitHub](https://github.com/stanfordnlp/CoreNLP/tree/master/src/edu/stanford/nlp/time/rules).

SUTime is generally run as a subcomponent of the `ner` annotator.  After it has run, the tokens of a time phrase will have a `NamedEntityTagAnnotation` for the 
type (e.g. DATE, TIME, DURATION, SET), and will have a `edu.stanford.nlp.time.Timex` object stored in the `TimexAnnotation` field.

Recognized temporal expressions can be resolved to the document date.  For instance, the expression `this Wednesday` will be resolved to the 
Wednesday that is closest to the document date, be it the current date or any other date.  The document date can be set in several ways
as will be documented below.

If you would like to customize SUTime or make additions, you can alter the rules files accordingly or add new rules files.  Setting
the property `sutime.rules = /path/to/my-rules.txt` will set the pipeline to use your custom rules.


### Java API

```java
package edu.stanford.nlp.examples;

import edu.stanford.nlp.pipeline.*;
import edu.stanford.nlp.time.*;

import java.util.Properties;

public class SUTimeBasicExample {

    public static String[] examples = {
            "The concert will be on February 4th at the ampitheater.",
            "The meeting will be held at 4:00pm in the library",
            "The conflict has lasted for over 15 years and shows no signs of abating."
    };


    public static void main(String[] args) {
        // set up pipeline properties
        Properties props = new Properties();
        // general properties
        props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner");
        props.setProperty("ner.docdate.usePresent", "true");
        props.setProperty("sutime.includeRange", "true");
        props.setProperty("sutime.markTimeRanges", "true");
        // build pipeline
        StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
        for (String example : examples) {
            CoreDocument document = new CoreDocument(example);
            pipeline.annotate(document);
            for (CoreEntityMention cem : document.entityMentions()) {
                System.out.println("temporal expression: "+cem.text());
                System.out.println("temporal value: "+cem.coreMap().get(TimeAnnotations.TimexAnnotation.class));
            }
        }
    }
}
```

### Running SUTime in a Stanford CoreNLP Pipeline

SUTime will be run automatically as a subcomponent of the `ner` annotator.

Several properties can be set to alter the behavior of SUTime.

| Property | Type | Description |
| :--- | :--- | :--- |
| sutime.markTimeRanges | boolean | Whether or not to recognize time ranges such as "July to August" |
| sutime.includeNested | boolean | Whether to mark time expressions within time expressions as well (e.g. "July" in "July to August") |
| sutime.teRelHeurLevel | String | Heuristic setting for resolving time expressions (NONE, BASIC, MORE) |
| sutime.includeRange | boolean | Whether or not to add range info to the TIMEX3 object |
| ner.docdate.useFixedDate | String | Provide a fixed date for each document. |
| ner.docdate.useMappingFile | boolean | Use a tab-delimited file to specify doc dates. First column is document ID, second column is date. |
| ner.docdate.usePresent | boolean | Give every document the present date. |
| ner.docdate.useRegex | String | Specify a regular expression matching file names. The first group will be extracted as the date. |

The document date used by SUTime can also be set by specifying a date in an `xml` file and using the `cleanxml` annotator.
