---
layout: page
title: TokensRegex
keywords: tokensregex
permalink: '/tokensregex.html'
nav_order: 4
toc: false
parent: Additional Tools
---

StanfordCoreNLP includes [TokensRegex](http://nlp.stanford.edu/software/tokensregex.html), a framework for defining regular expressions over 
text and tokens, and mapping matched text to semantic objects.

TokensRegex is a complex and powerful library for identifying and acting on patterns in text.
To fully utilize TokensRegex, you should review the high level overview and then work through
the specific examples below.

## Other Resources

In addition to the info on this page, there are some other great resources to check out with very useful information:

* [Original slides](https://nlp.stanford.edu/software/tokensregex/TokensRegexOverview.pdf/)
* [Original TokensRegex site](https://nlp.stanford.edu/software/tokensregex.html)
* [SequenceMatchRules Javadoc](https://nlp.stanford.edu/nlp/javadoc/javanlp/edu/stanford/nlp/ling/tokensregex/SequenceMatchRules.html)
* [Expressions Javadoc](https://nlp.stanford.edu/nlp/javadoc/javanlp/edu/stanford/nlp/ling/tokensregex/types/Expressions.html)

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

### Java API

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

  // My custom tokens
  public static class MyTokensAnnotation implements CoreAnnotation<List<? extends CoreMap>> {
    @Override
    public Class<List<? extends CoreMap>> getType() {
      return ErasureUtils.<Class<List<? extends CoreMap>>> uncheckedCast(List.class);
    }
  }

  // My custom type
  public static class MyTypeAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return ErasureUtils.<Class<String>> uncheckedCast(String.class);
    }
  }

  // My custom value
  public static class MyValueAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return ErasureUtils.<Class<String>> uncheckedCast(String.class);
    }
  }

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

### Running TokensRegex As An Annotator In A StanfordCoreNLP Pipeline

Another way to run TokensRegex rules is to use the TokensRegexAnnotator.  For instance
you might want to run a full StanfordCoreNLP pipeline, but run named entity recogntion
with TokensRegex rules.  This can be achieved with the `tokensregex` annotator.

Here is an example command (see `basic_ner.rules` file below):

```bash
java -Xmx2g edu.stanford.nlp.pipeline.StanfordCoreNLP -annotators tokenize,ssplit,pos,lemma,tokensregex -tokensregex.rules basic_ner.rules -file example.txt -outputFormat text
```

If you run this command, it will run the TokensRegex rules of `basic_ner.rules` as part of the pipeline when the `tokensregex` annotator runs.

Here is example usage in Java code:

```java
package edu.stanford.nlp.examples;

import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.pipeline.*;
import edu.stanford.nlp.util.*;
import java.util.List;
import java.util.Properties;

public class TokensRegexAnnotatorDemo {

  // key for matched expressions
  public static class MyMatchedExpressionAnnotation implements CoreAnnotation<List<CoreMap>> {
    @Override
    public Class<List<CoreMap>> getType() {
      return ErasureUtils.<Class<List<CoreMap>>> uncheckedCast(String.class);
    }
  }

  public static void main(String[] args) throws ClassNotFoundException {
    // set properties
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize,ssplit,pos,lemma,tokensregex");
    props.setProperty("tokensregex.rules", "basic_ner.rules");
    props.setProperty("tokensregex.matchedExpressionsAnnotationKey",
        "edu.stanford.nlp.examples.TokensRegexAnnotatorDemo$MyMatchedExpressionAnnotation");
    // build pipeline
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
    // annotate
    Annotation ann = new Annotation("There will be a big announcement by Apple Inc today at 5:00pm.  " +
        "She has worked at Miller Corp. for 5 years.");
    pipeline.annotate(ann);
    // show results
    System.out.println("---");
    System.out.println("tokens\n");
    for (CoreMap sentence : ann.get(CoreAnnotations.SentencesAnnotation.class)) {
      for (CoreLabel token : sentence.get(CoreAnnotations.TokensAnnotation.class)) {
        System.out.println(token.word() + "\t" + token.ner());
      }
      System.out.println("");
    }
    System.out.println("---");
    System.out.println("matched expressions\n");
    for (CoreMap me : ann.get(MyMatchedExpressionAnnotation.class)) {
      System.out.println(me);
    }
  }
}
```

## TokensRegex Pipeline Overview

As you work through the examples, it is helpful to understand the high level design of a TokensRegex pipeline.
The pipeline is specified in a set of rules files, that are evaluated with respect to an environment.  The
environment can be initialized in Java code, and altered in the rules files.

Generally assignments are made at the top, which set up variables to be used in the TokensRegex pipeline.

Then the rules are specified.  A TokensRegex pipeline can be split into stages (as many as you'd like).  Each
stage runs until completion, and then the next stage runs.  The Multi-Step NER example below illustrates this.

There are four types of extraction rule: text, token, composite, and filter.

* text - find patterns in a String
* tokens - find patterns in a list of tokens
* composite - recursive rules
* filter - filter out matched expressions that match a pattern

The Basic NER example shows token rules, the Extract Quotes example shows a text rule, the Process Math Expressions example
shows a composite rule, and the Multi-Step NER example shows a filter rule.

In the pipeline all of the text/token rules are run, then the composite rules are run over and over again
until no changes occur, and finally the filter rules are run.

Later in this documentation there will be a section focusing on each of the rule types to better explain the format.

## Tokens Rules

The most common type of rule is the "tokens" rule.  This rule type searches for patterns over a list of tokens.

Here is a simple example (Note: it is assumed that tokenize, ssplit, pos, and lemma have been run before the TokensRegex pipeline in this example)

```
{ ruleType: "tokens", pattern: ([{word:"I"}] [{word:/like|love/} & {tag:"VBP"}] ([{word:"pizza"}])), action: Annotate($1, ner, "FOOD"), result: "PIZZA" }

```

Let's walk through this rule.  The "ruleType" field specify that this is a "tokens" rule which will try to find patterns in a list of tokens.

The "pattern" field specifies the pattern to search for in the list of tokens.  Here is a description of the pattern in this example:

* `[{word:"I"}]` represents exact matching a token with text "I".
* `[{word:/like|love/} & {tag:"VBP"}]` represents matching a token with text "like" or "love" AND that has the part of speech tag "VBP". 
* `[{word:"pizza"}]` represents exact matching a token with text "pizza".

This pattern will match "I like pizza" or "I love pizza" (assuming like and love have the proper part of speech tag).

Note there are parenthesis around the pizza token.  This specifies a group.  In this example, the whole match is group $0, and the
match on "pizza" is group $1.  We can use the group numbers to specify lists of tokens to alter in the action part of the rule.

The "action" field specifies an action to take.  The [Expressions Javadoc](https://nlp.stanford.edu/nlp/javadoc/javanlp/edu/stanford/nlp/ling/tokensregex/types/Expressions.html)
shows more info about the kinds of actions there are.  The most common one is Annotate().  In this example we specify to annotate the word "pizza" with the NER tag
"FOOD".  (Note: make sure that "ner" is tied to CoreAnnotations.NamedEntityTagAnnotation.class which is shown below).  The Annotate() call says to annotate
all tokens in group match $1 with named entity tag "FOOD".

Finally the "result" is the value that the MatchedExpression will have.  Here we just make it the String "PIZZA".

## Tokens Rules Patterns

There are a lot of ways to match patterns in token sequences.  Below is a helpful cheat sheet

* `[{word:"pizza"}]` - exact matches the word "pizza" (example: "pizza")
* `[{word:"works"}] [{word:"for"}] [{word:"the"}]` - exact matches "works for the" (example: "works for the")
* `/works/ /for/ /the/` - exact matches "works for the" (example: "works for the")
* `[{word:"works"}] [{word:"for"}] [{word:/the|a/}]` - matches "works for the" or "works for a" (example: "works for a")
* `[{word:/[A-Z][A-Za-z]+/}]+` - matches any number of capitalized words in a row that only contain letters (examples: "Joe Smith", "Some Capitalized Words In A Row") 
* `[{word:/[A-Z][A-Za-z]+/} & {tag:"NNP"}]+` - same as above, but only matches if the tokens have the part of speech tag "NNP"
* `[{word:/[A-Z][A-Za-z]+/} & {tag:"NNP"}]{n,m}` - same as above, but only matches sequences of length between n and m (instead of any number)
* `[{ner:"PERSON"}]+` - matches any number of tokens in a row with the "PERSON" named entity tag
* `[{ner:"LOCATION"} | {ner: "ORGANIZATION"}]+` - matches any number of tokens in a row that have either LOCATION or ORGANIZATION as their ner tag
* `[{ner:"PERSON"} & {tag:"NNP"}]+` - matches any number of tokens in a row that have the PERSON ner tag and the NNP part-of-speech tag
* `[{ner:"PERSON"}]+ /works/ /at/` - matches any number of tokens with PERSON ner, followed by "works at"
* `[{word::IS_NUM}]` - matches a token parseable as a number (example: "3")...note this doesn't mean "three" or "1,000,000"
* `[!{tag:/VB.*/}]` - any token that is not a verb
* `/from/ /\d\d?:\d\d/ /to/ /\d\d?:\d\d/` - matches expressions like "from 8:00 to 10:00"

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
# uncomment to make all patterns case-insensitive in the rules file
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
{ ruleType: "tokens", pattern: ([{word:$JOB_TITLE_BASES}]), action: Annotate($0, ner, "JOB_TITLE_BASE") }

# second phase identifies complete job titles from components found in first phase
ENV.defaults["stage"] = 2
{ ruleType: "tokens", pattern: ([{ner: "JOB_TITLE_MODIFIER"}]+ [{ner: "JOB_TITLE_BASE"}]), 
  action: Annotate($0, ner, "COMPLETE_JOB_TITLE"), result: "FOUND_COMPLETE_JOB_TITLE"}

# third phase is a filter phase, and it removes matched expressions that the filter matches
ENV.defaults["stage"] = 3
# clean up component named entity tags from stage 1
{ ruleType: "tokens", pattern: ([{ner:"JOB_TITLE_MODIFIER"} | {ner:"JOB_TITLE_BASE"}]+), action: Annotate($0, ner, "O") }
# filter out the matched expression "deputy vice president"
{ ruleType: "filter", pattern: ([{word:"deputy"}] [{word:"vice"}] [{word:"president"}]) }
```

You can run this for yourself with this command:

```bash
java -Xmx2g edu.stanford.nlp.examples.TokensRegexDemo -annotators tokenize,ssplit,pos,lemma,ner -rulesFiles multi_step_ner.rules -inputText multi_step_ner.txt
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
He		PRP	O
is		VBZ	O
the		DT	O
vice		NN	COMPLETE_JOB_TITLE
president		NN	COMPLETE_JOB_TITLE
.		.	O

matched expression: vice president
matched expression value: STRING(FOUND_COMPLETE_JOB_TITLE)
matched expression char offsets: (10,24)
matched expression tokens:[vice-4, president-5]
---
sentence number: 1
sentence text: He is the assistant vice president.
He		PRP	O
is		VBZ	O
the		DT	O
assistant		JJ	COMPLETE_JOB_TITLE
vice		NN	COMPLETE_JOB_TITLE
president		NN	COMPLETE_JOB_TITLE
.		.	O

matched expression: assistant vice president
matched expression value: STRING(FOUND_COMPLETE_JOB_TITLE)
matched expression char offsets: (36,60)
matched expression tokens:[assistant-4, vice-5, president-6]
---
sentence number: 2
sentence text: He is the deputy vice president.
He		PRP	O
is		VBZ	O
the		DT	O
deputy		NN	COMPLETE_JOB_TITLE
vice		NN	COMPLETE_JOB_TITLE
president		NN	COMPLETE_JOB_TITLE
.		.	O
---
sentence number: 3
sentence text: He is the president.
He		PRP	O
is		VBZ	O
the		DT	O
president		NN	O
.		.	O
---
sentence number: 4
sentence text: He is the President.
He		PRP	O
is		VBZ	O
the		DT	O
President		NNP	O
.		.	O
```

Note that the sentence containing "deputy vice president" does have those tokens tagged as COMPLETE_JOB_TITLE's,
but that no matched expression is found for "deputy vice president" because of the filter.  Note that the last
two sentences have no named entity tags because we added a cleanup rule at the end.  If we didn't have that cleanup
rule "president" and "President" would've been tagged with "JOB_TITLE_BASE".

## Example 3: Extract Quotes (Find A Pattern In A String Instead Of Over Tokens)

In this example we will show how to find patterns in a String rather than over a sequence of tokens.  The
pattern we will look for is a beginning quotation mark, any number of characters, and an ending quotation mark.

If the pattern is found, we will get a MatchedExpression which will contain a list of tokens.  This could be
useful if you wanted to find quoted text and then work on the tokens of the quote.

Here is the rules file `basic_quote_extraction.rules`

```
# example rule matching over text instead of tokens
# this is a Java regular expression that matches a quotation mark followed by characters ending with a quotation mark
# it returns a value of the String "QUOTE" to the MatchedExpression
{ text: /".*"/ => "QUOTE" }
```

If you run this command:

```bash
java -Xmx2g edu.stanford.nlp.examples.TokensRegexDemo -annotators tokenize,ssplit,pos,lemma,ner -rulesFiles basic_quote_extraction.rules -inputText basic_quote_extraction.txt
```

on this file `basic_quote_extraction.txt`

```
John said, "I thought the pizza was great!"
```

you should get this output:

```
---
sentence number: 0
sentence text: John said, "I thought the pizza was great!"
John		NNP	PERSON
said		VBD	O
,		,	O
``		``	O
I		PRP	O
thought		VBD	O
the		DT	O
pizza		NN	O
was		VBD	O
great		JJ	O
!		.	O
''		''	O

matched expression: "I thought the pizza was great!"
matched expression value: STRING(QUOTE)
matched expression char offsets: (11,43)
matched expression tokens:[``-4, I-5, thought-6, the-7, pizza-8, was-9, great-10, !-11, ''-12]
```

The pattern finds a quote, and it returns a MatchedExpression with the values shown in the output.

## Example 4: Basic Relation Extraction

In this example we will implement some basic relation extraction.

What we will state is that we have found evidence of a "works for" relation when we see
"works for" or "is employed at|by" in between a PERSON and an ORGANIZATION.

When such a pattern is detected in the text, the resulting MatchedExpression in the Java
code will be given a tuple value containing the relation info.  Note that the overall
MatchedExpression will have the text and tokens list, so you would have both the
main relation info in the value field, and access to the provenance of the relation. 

Here is the rules file `basic_relation.rules`

```
# rules for finding employment relations
{ ruleType: "tokens", pattern: (([{ner:"PERSON"}]+) /works/ /for/ ([{ner:"ORGANIZATION"}]+)), 
  result: Concat("(", $$1.text, ",", "works_for", ",", $$2.text, ")") } 

{ ruleType: "tokens", pattern: (([{ner:"PERSON"}]+) /is/ /employed/ /at|by/ ([{ner:"ORGANIZATION"}]+)), 
  result: Concat("(", $$1.text, ",", "works_for", ",", $$2.text, ")") } 
```

If you run this command:

```bash
java -Xmx4g edu.stanford.nlp.examples.TokensRegexDemo -annotators tokenize,ssplit,pos,lemma,ner -rulesFiles basic_relation.rules -inputText basic_relation.txt
```

on this example text `basic_relation.txt`

```
Joe Smith works for Google.
Jane Smith is employed by Apple.
```

you should get this output:

```
---
sentence number: 0
sentence text: Joe Smith works for Google.
Joe		NNP	PERSON
Smith		NNP	PERSON
works		VBZ	O
for		IN	O
Google		NNP	ORGANIZATION
.		.	O

matched expression: Joe Smith works for Google
matched expression value: STRING((Joe Smith,works_for,Google))
matched expression char offsets: (0,26)
matched expression tokens:[Joe-1, Smith-2, works-3, for-4, Google-5]
---
sentence number: 1
sentence text: Jane Smith is employed by Apple.
Jane		NNP	PERSON
Smith		NNP	PERSON
is		VBZ	O
employed		VBN	O
by		IN	O
Apple		NNP	ORGANIZATION
.		.	O

matched expression: Jane Smith is employed by Apple
matched expression value: STRING((Jane Smith,works_for,Apple))
matched expression char offsets: (28,59)
matched expression tokens:[Jane-1, Smith-2, is-3, employed-4, by-5, Apple-6]
```

The MatchedExpression has the full text and tokens with the supporting evidence for the relation, and
it has a value tuple containing the core relation info.

## Example 5: Process Math Expressions (Composite Rules)

This example demonstrates the composite rule type, it will run on a math equation and calculate the value of it.
The rule should match (among other things) two numbers separated by an operator, and assign that expression
the value of executing the operation on the operands.  "(", expression, ")" will be matched to be the same
expression and have the same value as the enclosed expression (this is to process parenthesis).  Every time
a pattern is matched, all of the tokens in the pattern match will be replaced with an "aggregate token" 
representing the whole matched pattern.

For instance if you process `(5 + 5) + 5` it will run the composite rules and end up calculating 15. 

The composite rules are run over and over again until nothing changes.  Matched expressions are replaced with
an aggregate token which represents the whole matched expression.

To illustrate this, let's examine what happens to this example sentence.  We will represent an aggregate token
with aggregate_token[string, value]

```
# initial 
# (7 tokens) ["(", "5", "+", "5", ")", "+", "5"]
(5 + 5) + 5
# first run of composite rules, after first rule 
# "5 + 5" is matched and replaced with aggregate_token["5 + 5", 10]
# (5 tokens) ["(", aggregrate_token["5 + 5", 10], ")", "+", "5"]
(aggregate_token["5 + 5", 10]) + 5
# first run of composite rules, after second rule
# "(aggregate_token["5 + 5", 10])" is matched, given value of 10 which is same as internal expression
# (3 tokens) [aggregate_token["(aggregate_token["5 + 5", 10])", 10], "+", "5"]
aggregate_token["(aggregate_token["5 + 5", 10])", 10] + 5
# second run of composite rules, after first rule
# aggregate_token["(aggregate_token["5 + 5", 10])", 10] + 5 is matched, given value of 15
# (1 token) [aggregate_token["aggregate_token["(aggregate_token["5 + 5", 10])", 10] + 5", 15]]
aggregate_token["aggregate_token["(aggregate_token["5 + 5", 10])", 10] + 5", 15]
# second run of composite rules, after second rule
# (1 token) [aggregate_token["aggregate_token["(aggregate_token["5 + 5", 10])", 10] + 5", 15]]
aggregate_token["aggregate_token["(aggregate_token["5 + 5", 10])", 10] + 5", 15]
# third run of composite rules, after first rule
# (1 token) [aggregate_token["aggregate_token["(aggregate_token["5 + 5", 10])", 10] + 5", 15]]
aggregate_token["aggregate_token["(aggregate_token["5 + 5", 10])", 10] + 5", 15]
# third run of composite rules, after second rule
# (1 token) [aggregate_token["aggregate_token["(aggregate_token["5 + 5", 10])", 10] + 5", 15]]
aggregate_token["aggregate_token["(aggregate_token["5 + 5", 10])", 10] + 5", 15]
# no change detected after third run of all composite rules, so the composite phase ends
```

Here is the rules file that implements this `math_expression.rules`

```
orig = { type: "CLASS", value: "edu.stanford.nlp.ling.CoreAnnotations$OriginalTextAnnotation" }
numtokens = { type: "CLASS", value: "edu.stanford.nlp.ling.CoreAnnotations$NumerizedTokensAnnotation" }
numcomptype = { type: "CLASS", value: "edu.stanford.nlp.ling.CoreAnnotations$NumericCompositeTypeAnnotation" }
numcompvalue = { type: "CLASS", value: "edu.stanford.nlp.ling.CoreAnnotations$NumericCompositeValueAnnotation" }

mytokens = { type: "CLASS", value: "edu.stanford.nlp.examples.TokensRegexDemo$MyTokensAnnotation" }
type = { type: "CLASS", value: "edu.stanford.nlp.examples.TokensRegexDemo$MyTypeAnnotation" }
value = { type: "CLASS", value: "edu.stanford.nlp.examples.TokensRegexDemo$MyValueAnnotation" }

ENV.defaultResultAnnotationKey = ( type, value )
ENV.defaultNestedResultsAnnotationKey = mytokens
ENV.defaults["stage.limitIters"] = 0

// Numbers
{ ruleType: "tokens", pattern: ( [ numcomptype:"NUMBER" ] ), result: ( "EXPR", $0[0].numcompvalue ) }

// Operators
{ pattern: ( "+" ),            result: ( "OP", "Add" ),      priority: 1}
{ pattern: ( /plus/ ),         result: ( "OP", "Add" ),      priority: 1}
{ pattern: ( "-" ),            result: ( "OP", "Subtract" ), priority: 1}
{ pattern: ( /minus/ ),        result: ( "OP", "Subtract" ), priority: 1}
{ pattern: ( "*" ),            result: ( "OP", "Multiply" ), priority: 2}
{ pattern: ( /times/ ),        result: ( "OP", "Multiply" ), priority: 2}
{ pattern: ( "/" ),            result: ( "OP", "Divide" ),   priority: 2}
{ pattern: ( /divided/ /by/ ), result: ( "OP", "Divide" ),   priority: 2}
{ pattern: ( "^" ),            result: ( "OP", "Pow" ),      priority: 3}

$OP = ( [ type:"OP" ] )
$EXPR = ( [ type:"EXPR" ] )

{ ruleType: "composite", pattern: ( ($EXPR) ($OP) ($EXPR) ), result: ("EXPR", Call($2[0].value, $1[0].value, $3[0].value)) }

{ ruleType: "composite", pattern: ( [orig:"("] ($EXPR) [orig:")"] ), result: ("EXPR", $1[0].value) }
```

If you run on this example sentence: `math_expression.txt`

```
(5 + 5) + 5
```

With this command:

```bash
java -Xmx2g edu.stanford.nlp.examples.TokensRegexDemo -annotators tokenize,ssplit,pos,lemma,ner -rulesFiles math_expressions.rules -inputText math_expressions.txt 
```

You should get this output:

```
---
sentence number: 0
sentence text: (5 + 5) + 5
-LRB-		-LRB-	O
5		CD	NUMBER
+		CC	O
5		CD	NUMBER
-RRB-		-RRB-	O
+		CC	O
5		CD	NUMBER

matched expression: -LRB-5 + 5-RRB- + 5
matched expression value: LIST([STRING(EXPR), NUMBER(15)])
matched expression char offsets: (0,11)
matched expression tokens:[-LRB--1, 5-2, +-3, 5-4, -RRB--5, +-6, 5-7]
```
