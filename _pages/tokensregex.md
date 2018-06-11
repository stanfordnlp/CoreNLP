---
title: TokensRegex
keywords: tokensregex
permalink: '/tokensregex.html'
---

StanfordCoreNLP includes [TokensRegex](http://nlp.stanford.edu/software/tokensregex.html), a framework for defining regular expressions over 
text and tokens, and mapping matched text to semantic objects.

TokensRegex is a complex and powerful library for identifying and acting on patterns in text.
To fully utilize TokensRegex, you should review the high level overview and then work through
the specific examples below.

## High Level Overview

With TokensRegex, you can build a rule based system for searching for patterns and performing
actions when the patterns are found.

In a typical TokensRegex pipeline, you will need some basic Java code to load in your text, split 
it into sentences, and set up the CoreMapExpressionExtractor.  Below is an example class
TokensRegexDemo which contains the necessary code for running a TokensRegex pipeline.

In this code, some util classes and a StanfordCoreNLP pipeline are used to load the text and
split it into sentences.  

Then the TokensRegex pipeline is built (not be confused with a general StanfordCoreNLP pipeline).
The TokensRegex pipeline is specified by a list of rules files, and an Env object.  The next section
will contain a thorough example of a rules file which specifies a TokensRegex pipeline.  In the example 
code you can see how the environment is built, and an example of specifying that you want all patterns 
to be case-insensitive.  The list of rules files and the Env are used to construct a CoreMapExpressionExtractor,
the main class which runs a TokensRegex pipeline.

When a CoreMapExpressionExtractor is run on a sentence, it will run a TokensRegex pipeline on the sentence, 
alter the token CoreLabel’s annotations in the sentence, and potentially generate a list of MatchedExpression 
objects which can be used in your overall logic.

In the next section we will walk through a specific example of a TokensRegex pipeline to illustrate all of this.

```java
package edu.stanford.nlp.examples;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.ling.tokensregex.*;
import edu.stanford.nlp.pipeline.*;
import edu.stanford.nlp.util.*;
import java.util.List;
import java.util.Properties;
import java.util.regex.*;

public class TokensRegexDemo {

  public static void main(String[] args) {

    // load settings from the command line
    Properties props = StringUtils.argsToProperties(args);

    // get the text to process

    // load sentences
    String exampleSentences = IOUtils.stringFromFile(props.getProperty("inputText"));

    // build pipeline to get sentences and do basic tagging
    Properties pipelineProps = new Properties();
    pipelineProps.setProperty("annotators", props.getProperty("annotators"));
    pipelineProps.setProperty("ner.applyFineGrained", "false");
    pipelineProps.setProperty("ssplit.eolonly", "true");
    StanfordCoreNLP pipeline = new StanfordCoreNLP(pipelineProps);

    // get sentences
    Annotation exampleSentencesAnnotation = new Annotation(exampleSentences);
    pipeline.annotate(exampleSentencesAnnotation);

    // set up the TokensRegex pipeline

    // get the rules files
    String[] rulesFiles = props.getProperty("rulesFiles").split(",");

    // set up an environment with reasonable defaults
    Env env = TokenSequencePattern.getNewEnv();
    // set to case insensitive
    env.setDefaultStringMatchFlags(NodePattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    env.setDefaultStringPatternFlags(Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    // build the CoreMapExpressionExtractor
    CoreMapExpressionExtractor
        extractor = CoreMapExpressionExtractor.createExtractorFromFiles(env, rulesFiles);

    // for each sentence in the input text, run the TokensRegex pipeline
    int sentNum = 0;
    for (CoreMap sentence : exampleSentencesAnnotation.get(CoreAnnotations.SentencesAnnotation.class)) {
      System.out.println("---");
      System.out.println("sentence number: "+sentNum);
      System.out.println("sentence text: "+sentence.get(CoreAnnotations.TextAnnotation.class));
      sentNum++;
      List<MatchedExpression> matchedExpressions = extractor.extractExpressions(sentence);
      // print out the results of the rules actions
      for (CoreLabel token : sentence.get(CoreAnnotations.TokensAnnotation.class)) {
        System.out.println(token.word() + "\t" + token.ner());
      }
      // print out the matched expressions
      for (MatchedExpression me : matchedExpressions) {
        System.out.println("matched expression: "+me.getText());
        System.out.println("matched expression value: "+me.getValue());
        System.out.println("matched expression char offsets: "+me.getCharOffsets());
        System.out.println("matched expression tokens:" +
            me.getAnnotation().get(CoreAnnotations.TokensAnnotation.class));
      }
    }
  }

}
```

## Example 1: Multi-Step NER

A good way to understand TokensRegex pipelines is to look at a specific example.  In this section we will
go through building a pipeline that performs multi-step named entity recognition.

In the first phase, we will identify basic components of a job title.  The two we will identify
are JOB_TITLE_BASE (e.g. "president") and JOB_TITLE_MODIFIER (e.g. "vice").

In the second phase, we will build on named entity tags that were applied in the first phase.
Every time we see a sequence of JOB_TITLE_MODIFIER's ending in a JOB_TITLE_BASE we will mark
all of those tokens as a COMPLETE_JOB_TITLE.

In the third and final phase, we will filter out the COMPLETE_JOB_TITLE "deputy vice president."

The following rules file `multi_step_ner.rules` implements this multi-step pipeline.

```bash
# make all patterns case-insensitive in the rules file
# ENV.defaultStringMatchFlags = 66
# ENV.defaultStringPatternFlags = 66

# these Java classes will be used by the rules
ner = { type: "CLASS", value: "edu.stanford.nlp.ling.CoreAnnotations$NamedEntityTagAnnotation" }
tokens = { type: "CLASS", value: "edu.stanford.nlp.ling.CoreAnnotations$TokensAnnotation" }

# variables for complex regexes
$JOB_TITLE_BASES = "/president|secretary|general/"
$JOB_TITLE_MODIFIERS = "/vice|assistant|deputy/"

# first phase identifies components of job titles
# a TokensRegex pipeline can run various stages
# to specify a particular stage, set ENV.defaults["stage"] to the stage number
ENV.defaults["stage"] = 1

# tokens match phase
{ ruleType: "tokens", pattern: ([{word:$JOB_TITLE_MODIFIERS}]+), action: Annotate($0, ner, "JOB_TITLE_MODIFIER") }
{ ruleType: "tokens", pattern: ($JOB_TITLE_BASES), action: Annotate($0, ner, "JOB_TITLE_BASE") }

# second phase identifies complete job titles from components found in first phase
ENV.defaults["stage"] = 2
{ ruleType: "tokens", pattern: ([{ner: "JOB_TITLE_MODIFIER"}]+ [{ner: "JOB_TITLE_BASE"}]),
  over: tokens, action: Annotate($0, ner, "COMPLETE_JOB_TITLE"), result: "FOUND_COMPLETE_JOB_TITLE"}

# third phase is a filter phase, and it removes matched expressions that the filter matches
ENV.defaults["stage"] = 3
{ ruleType: "filter", pattern: (/deputy/ /vice/ /president/) } 
```

You can run this for yourself with this command:

```bash
java -Xmx4g edu.stanford.nlp.examples.TokensRegexDemo -annotators tokenize,ssplit -rulesFiles multi_step_ner.rules -inputText multi_step_ner.txt
```

If you run it on this example file `multi_step_ner.txt`

```bash
He is the vice president.
He is the assistant vice president.
He is the deputy vice president.
He is the president.
He is the President.
```

You should get this output:

```bash
---
sentence number: 0
sentence text: He is the vice president.
He	null
is	null
the	null
vice	COMPLETE_JOB_TITLE
president	COMPLETE_JOB_TITLE
.	null
matched expression: vice president
matched expression value: STRING(FOUND_COMPLETE_JOB_TITLE)
matched expression char offsets: (10,24)
matched expression tokens:[vice-4, president-5]
---
sentence number: 1
sentence text: He is the assistant vice president.
He	null
is	null
the	null
assistant	COMPLETE_JOB_TITLE
vice	COMPLETE_JOB_TITLE
president	COMPLETE_JOB_TITLE
.	null
matched expression: assistant vice president
matched expression value: STRING(FOUND_COMPLETE_JOB_TITLE)
matched expression char offsets: (36,60)
matched expression tokens:[assistant-4, vice-5, president-6]
---
sentence number: 2
sentence text: He is the deputy vice president.
He	null
is	null
the	null
deputy	COMPLETE_JOB_TITLE
vice	COMPLETE_JOB_TITLE
president	COMPLETE_JOB_TITLE
.	null
---
sentence number: 3
sentence text: He is the president.
He	null
is	null
the	null
president	JOB_TITLE_BASE
.	null
---
sentence number: 4
sentence text: He is the President.
He	null
is	null
the	null
President	JOB_TITLE_BASE
.	null
```
