---
title: TokensRegex
keywords: tokensregex
permalink: '/tokensregex.html'
---

StanfordCoreNLP includes [TokensRegex](http://nlp.stanford.edu/software/tokensregex.html), a framework for defining regular expressions over 
text and tokens, and mapping matched text to semantic objects.

## Tutorial

Tokensregex is a powerful and complex library for identifying and acting on patterns in text.

A good way to learn how to use TokensRegex is to look at specific examples.

TokensRegex requires some Java code and rules written with the TokensRegex specification.

To start with, here is a Java class that runs a TokensRegex extraction process on an
annotation, and then prints out annotations done to tokens by the process and prints
out the final matched expressions found for each sentence.  This will be the basis
for the examples in this tutorial.

```java
package edu.stanford.nlp.examples;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.tokensregex.CoreMapExpressionExtractor;
import edu.stanford.nlp.ling.tokensregex.Env;
import edu.stanford.nlp.ling.tokensregex.*;
import edu.stanford.nlp.ling.tokensregex.TokenSequencePattern;
import edu.stanford.nlp.pipeline.*;
import edu.stanford.nlp.util.*;
import java.util.List;
import java.util.Properties;
import java.util.regex.*;

public class TokensRegexDemo {

  public static void main(String[] args) {

    Properties props = StringUtils.argsToProperties(args);

    // set up rules files
    String[] rulesFiles = props.getProperty("rulesFiles").split(",");

    // set up an environment with reasonable defaults
    Env env = TokenSequencePattern.getNewEnv();
    // set to case insensitive
    env.setDefaultStringMatchFlags(NodePattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    env.setDefaultStringPatternFlags(Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    // build the CoreMapExpressionExtractor
    CoreMapExpressionExtractor
        extractor = CoreMapExpressionExtractor.createExtractorFromFiles(env, rulesFiles);

    // load sentences
    String exampleSentences = IOUtils.stringFromFile(props.getProperty("inputTextFile"));

    // build pipeline to get sentences
    Properties pipelineProps = new Properties();
    pipelineProps.setProperty("annotators", "tokenize,ssplit");
    pipelineProps.setProperty("ssplit.eolonly", "true");
    StanfordCoreNLP pipeline = new StanfordCoreNLP(pipelineProps);

    // get sentences
    Annotation exampleSentencesAnnotation = new Annotation(exampleSentences);
    pipeline.annotate(exampleSentencesAnnotation);

    // for each sentence, run the CoreMapExpressionExtractor
    for (CoreMap sentence : exampleSentencesAnnotation.get(CoreAnnotations.SentencesAnnotation.class)) {
      System.out.println("---");
      List<MatchedExpression> matchedExpressions = extractor.extractExpressions(sentence);
      // print out the annotations caused by action
      for (CoreLabel token : sentence.get(CoreAnnotations.TokensAnnotation.class)) {
        System.out.println(token.word() + "\t" + token.ner());
      }
      // print out the matched expressions after the tokensregex pipeline is finished
      for (MatchedExpression me : matchedExpressions) {
        System.out.println("matched expression: "+me.getText());
        System.out.println("matched expression value: "+me.getValue());
      }
    }
  }

}
```

The examples in the tutorial can be run with a command like this:

```bash
java -Xmx2g edu.stanford.nlp.examples.TokensRegexDemo -rulesFiles compositeNER.rules -inputTextFile compositeNER.txt
```

Now we will go through examples of what can be done with TokensRegex

### Composite NER

In this example, we will illustrate the standard TokensRegex pipeline executing composite NER.

First some variables are assigned.  The most common variable assignments are

1.) assigning a variable to an Annotation class you want to modify when the rules are run
2.) variables for complicated regexes

Once the variables are assigned they can be used in the rules.

Each TokensRegex rule has a type, in this example there is a "tokens" rule, a "composite" rule,
and a "filter" rule.  When the TokensRegex process is run, the tokens rules are run first, then
the composite rules are run until there is no change, and then finally filtering is done.

To make this more concrete, in the first phase of our process, we want to identify tokens that
are either of type "JOB_TITLE_MODIFIER" (such as "vice" or "assistant") or "JOB_TITLE_BASE"
(such as "president", or "secretary").

After the "tokens" rule is run, appropriate tokens will be ner tagged with "JOB_TITLE_MODIFIER",
"JOB_TITLE_BASE", or nothing.  

In the second phase, we want to operate on the findings of the first phase.  So in our working
example, if we see "JOB_TITLE_MODIFIER" tokens preceding a "JOB_TITLE_BASE" token, we want to
mark this as a "COMPLETE_JOB_TITLE".  This is a "composite" rule that will be run after all
of the "tokens" rules have been run.

Note also that the composite rule returns 2000 as a result.  The appearance of a non-NIL
result when a rule is matched causes the TokensRegex pipeline to identify a matched expression.
If the result is NIL, the extractor won't find a matched expression.

Finally in the third phase of our example, the "filter" rule is run.  This will filter out
any patterns that match.  In our example we don't want to find "assistant vice president"
as a matched pattern, so we make a filter rule for it.

```bash
# set up variables to be used
  
# these Java classes will be used by the rules
ner = { type: "CLASS", value: "edu.stanford.nlp.ling.CoreAnnotations$NamedEntityTagAnnotation" }
tokens = { type: "CLASS", value: "edu.stanford.nlp.ling.CoreAnnotations$TokensAnnotation" }

# variables for complex regexes
$JOB_TITLE_MODIFIERS = "/vice|assistant/"
$JOB_TITLES = "/president|secretary/"

# tokens phase
{ ruleType: "tokens", pattern: ($JOB_TITLE_MODIFIERS), action: Annotate($0, ner, "JOB_TITLE_MODIFIER") }
{ ruleType: "tokens", pattern: ($JOB_TITLES), action: Annotate($0, ner, "JOB_TITLE_BASE") }

# composite phase
{ ruleType: "composite", pattern: ([{ner: "JOB_TITLE_MODIFIER"}]+ [{ner: "JOB_TITLE_BASE"}]),
  over: tokens, action: Annotate($0, ner, "COMPLETE_JOB_TITLE"), result: 2000}

# filter phase
{ ruleType: "filter", pattern: (/assistant/ /vice/ /president/) }
```

When run on this text:

```
He is the vice president.
He is the assistant vice president.
He is the president.
He is the President.
```

You should get this output:

```
---
He	null
is	null
the	null
vice	COMPLETE_JOB_TITLE
president	COMPLETE_JOB_TITLE
.	null
matched expression: vice president
matched expression value: INTEGER(2000)
---
He	null
is	null
the	null
assistant	COMPLETE_JOB_TITLE
vice	COMPLETE_JOB_TITLE
president	COMPLETE_JOB_TITLE
.	null
---
He	null
is	null
the	null
president	JOB_TITLE_BASE
.	null
---
He	null
is	null
the	null
President	JOB_TITLE_BASE
.	null
```

Note that "vice president" shows up as a matched expression, but "assistant vice president" does not
due to the filter.  But the filter does not effect the "action" part of the rules, so in all cases
the tokens NER tag is set by the rules.
