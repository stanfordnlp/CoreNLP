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
        System.out.println(token.word() + "\t" + token.tag() + "\t" + token.ner());
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

## Example 1: Basic NER

With this pipeline we will explore performing simple named entity recognition with TokensRegex.

Let’s imagine that we want to identify company names.  For our simple example, we will assume
we are interested in any string of capitalized tokens that ends with a company ending token.

We will define the company ending tokens to be “Corp” or “Inc” (and optionally containing a “.”).

To show more functionality, we will also add the constraint that the non-ending tokens in the pattern
have to have the part of speech tag “NNP”.

Here is the rules file that implements this TokensRegex pipeline `basic_ner.rules`:

```bash
# make all patterns case-sensitive
ENV.defaultStringMatchFlags = 0
ENV.defaultStringPatternFlags = 0

# these Java classes will be used by the rules
ner = { type: "CLASS", value: "edu.stanford.nlp.ling.CoreAnnotations$NamedEntityTagAnnotation" }
tokens = { type: "CLASS", value: "edu.stanford.nlp.ling.CoreAnnotations$TokensAnnotation" }

# define some regexes over tokens
$COMPANY_BEGINNING = "/[A-Z][A-Za-z]+/"
$COMPANY_ENDING = "/(Corp|Inc)\.?/"

# rule for recognizing company names
{ ruleType: "tokens", pattern: ([{word:$COMPANY_BEGINNING} & {tag:"NNP"}]+ [{word:$COMPANY_ENDING}]), action: Annotate($0, ner, "COMPANY"), result: "COMPANY_RESULT" }
```

Now let’s walk through this rules file.

The first section influences the environment of the pipeline.  Since our Java code sets the rules to
be case-insensitive, we will set them to case-sensitive in our rules file for this TokensRegex pipeline.
By setting those two flags to 0, the rules will be case-sensitive.

In the next section we bind certain variables to Java classes used as annotation keys.  Here we
bind “ner” and “tokens” to the corresponding annotation keys.

In the third section we bind some variables to larger regexes.  This would be especially useful if
the regexes were especially large, though they are small in our simple example.  Note that these
are regexes that match the full text of a token.  So $COMPANY_BEGINING will match all 
tokens that start with a capital letter and only contain letters (e.g. “Apple”).  $COMPANY_ENDING will
match the tokens “Corp”, “Inc”, “Corp.”, and “Inc.”  By setting these variables to regexes, we don’t
have to write out the full regex again every time we want to use it.

Finally in the fourth section we define the rule for the token pattern we wish to match.  The “ruleType”
of the rule is “tokens”, meaning we want to find patterns over sequences of tokens.  

The “pattern” of the rule defines the actual pattern we want to see in the tokens.  In this case we say we want to
see a number of $COMPANY_BEGINNING’s that have the “NNP” part of speech tag.  Then we want
to end with one token that matches the $COMPANY_ENDING pattern.  

If the pattern is matched,the “action” part of the rule will be executed.  In the most typical case, this means we want to annotate all of the tokens in the matched pattern in some manner.  This is done with the Annotate() function.
In this rule, we state we want to annotate all of the matched tokens in the pattern (indicated by the group $0 in 
the token pattern), and that we want to set their “CoreAnnotation.NamedEntityTagAnnotation.class” value 
(indicated by “ner”), to the value “COMPANY”.  This is where the actual CoreLabel’s are being altered by
having their CoreAnnotations.NamedEntityTagAnnotation.class field changed.

Finally we may want to produce a MatchedExpression for this to operate on in our Java code, and we
may want to set the value of that MatchedExpression to something.  So we have the rule return a “result”
when it fires, and we say the result is “COMPANY_RESULT”.  The value of the MatchedExpression will be set to
“COMPANY_RESULT” as a result.

If you run this TokensRegex pipeline on this file `basic_ner.txt`:

```
She has worked at Miller Corp. for 5 years.
There will be a big announcement by Apple Inc today at 5:00pm.
He works for apple inc in cupertino.
```

And run this Java command:

```bash
java -Xmx2g edu.stanford.nlp.examples.TokensRegexDemo -annotators tokenize,ssplit,pos -rulesFiles basic_ner.rules -inputText basic_ner.txt
```

*Note*: in this command we are only running `tokenize,ssplit,pos` so the CoreLabels will have
“null” for the NER token unless our rules find patterns in the input sentences. Also remember that the
Java code specifies to create sentences based on newlines, so the input file is interpreted as one-sentence-per-line.

You should see this output:

```
---
sentence number: 0
sentence text: She has worked at Miller Corp. for 5 years.
She		PRP	null
has		VBZ	null
worked		VBN	null
at		IN	null
Miller		NNP	COMPANY
Corp.		NNP	COMPANY
for		IN	null
5		CD	null
years		NNS	null
.		.	null

matched expression: Miller Corp.
matched expression value: STRING(COMPANY_RESULT)
matched expression char offsets: (18,30)
matched expression tokens:[Miller-5, Corp.-6]
---
sentence number: 1
sentence text: There will be a big announcement by Apple Inc today at 5:00pm.
There		EX	null
will		MD	null
be		VB	null
a		DT	null
big		JJ	null
announcement		NN	null
by		IN	null
Apple		NNP	COMPANY
Inc		NNP	COMPANY
today		NN	null
at		IN	null
5:00		CD	null
pm		NN	null
.		.	null

matched expression: Apple Inc
matched expression value: STRING(COMPANY_RESULT)
matched expression char offsets: (80,89)
matched expression tokens:[Apple-8, Inc-9]
---
sentence number: 2
sentence text: He works for apple inc in cupertino.
He		PRP	null
works		VBZ	null
for		IN	null
apple		NN	null
inc		NN	null
in		IN	null
cupertino		NN	null
.		.	null
```

In the output the tokens for each sentence will be printed out, and information about each
found matched expression for each sentence will be printed out.

Note that “apple inc” is not being matched, meaning we have successfully set the rules to be
case sensitive.

## Example 2: Multi-Step NER

In this section we will go through building a pipeline that performs multi-step named entity recognition.

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

```
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

