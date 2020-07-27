---
layout: page
title: Named Entity Recognition
keywords: ner, named entity recognition, NERCombinerAnnotator
permalink: '/ner.html'
nav_order: 11
parent: Pipeline
---

## Description

Recognizes named entities (person and company names, etc.) in text.
Principally, this annotator uses one or more machine learning sequence
models to label entities, but it may also call specialist rule-based
components, such as for labeling and interpreting times and dates.
Numerical entities that require normalization, e.g., dates,
have their normalized value stored in NormalizedNamedEntityTagAnnotation.
For more extensive support for rule-based NER, you may also want to
look at the [RegexNER annotator](regexner.html).
The set of entities recognized is language-dependent, and the
recognized set of entities is frequently more limited for other
languages than what is
described below for English. As the name “NERClassifierCombiner”
implies, commonly this annotator will run several named entity
recognizers and then combine their results but it can run just a
single annotator or only rule-based quantity NER.

For English, by default, this annotator recognizes
named (PERSON, LOCATION, ORGANIZATION, MISC), numerical (MONEY,
NUMBER, ORDINAL, PERCENT), and temporal (DATE, TIME, DURATION, SET)
entities (12 classes). Adding the `regexner` annotator and using the supplied [RegexNER](regexner.html) pattern
files adds support for the fine-grained and additional entity classes
EMAIL, URL, CITY, STATE\_OR\_PROVINCE, COUNTRY, NATIONALITY, RELIGION,
(job) TITLE, IDEOLOGY, CRIMINAL\_CHARGE, CAUSE\_OF\_DEATH, (Twitter,
etc.) HANDLE (12 classes)
for a total of 24 classes. Named entities are recognized using a combination of three
CRF sequence taggers trained on various corpora, including CoNLL, ACE,
MUC, and ERE corpora. Numerical entities are recognized using a rule-based
system. 

| Property name | Annotator class name | Generated Annotation |
| --- | --- | --- |
| ner | NERClassifierCombiner | NamedEntityTagAnnotation and NormalizedNamedEntityTagAnnotation |

## Options

| Option name | Type | Default | Description |
| --- | --- | --- | --- |
| ner.model | List(String) | null | A comma-separated list of NER model names (or just a single name is okay). If none are specified, a default list of English models is used (3class, 7class, and MISCclass, in that order). The names will be looked for as classpath resources, filenames, or URLs. |
| ner.rulesOnly | boolean | false | Whether or not to only run rules based NER. |
| ner.statisticalOnly | boolean | false | Whether or not to only run statistical NER. |
| ner.applyNumericClassifiers | boolean | true | Whether or not to use numeric classifiers, for money, percent, numbers, including [SUTime](http://nlp.stanford.edu/software/regexner/).  These are hardcoded for English, so if using a different language, this should be set to false. |
| ner.applyFineGrained | boolean | true | whether or not to apply fine-grained NER tags (e.g. LOCATION --> CITY) ; this will slow down performance |
| ner.buildEntityMentions | boolean | true | whether or not to build entity mentions from token NER tags |
| ner.combinationMode | String | NORMAL | when set to NORMAL each tag can only be applied by the first CRF classifier that applies that tag ; when set to HIGH_RECALL all CRF classifiers can apply all of their tags |
| ner.useNERSpecificTokenization | boolean | true | Whether or not to use NER-specific tokenization which merges tokens separated by hyphens. Models released with Stanford CoreNLP 4.0.0 expect a tokenization standard that does NOT split on hyphens. |
| ner.useSUTime | boolean | true | Whether or not to use SUTime. SUTime at present only supports English; if not processing English, make sure to set this to false. |
| sutime.markTimeRanges | boolean | false | Tells SUTime whether to mark phrases such as “From January to March” as a range, instead of marking "January" and "March" separately. |
| sutime.includeRange | boolean | false | If marking time ranges, set the time range in the TIMEX output from SUTime. |
| maxAdditionalKnownLCWords | int | - | Limit the size of the known lower case words set.  Set this to 0 to prevent ordering issues (i.e. when this is nonzero running on document1 then document2 can have different results than running on document2 then document1 |  

## NER Pipeline Overview

The full named entity recognition pipeline has become fairly complex and involves
a set of distinct phases integrating statistical and rule based approaches.  Here
is a breakdown of those distinct phases.

The main class that runs this process is `edu.stanford.nlp.pipeline.NERCombinerAnnotator`

### Statistical Models

During this phase a series of trained CRF's will be run on each sentence.  These
CRF's are trained on large tagged data sets.  They evaluate the entire sequence
and pick the optimal tag sequence.

These are the default models that are run:

```
# tags: LOCATION, ORGANIZATION, PERSON
edu/stanford/nlp/models/ner/english.all.3class.distsim.crf.ser.gz
# tags: DATE, LOCATION, MONEY, ORGANIZATION, PERCENT, PERSON, TIME
edu/stanford/nlp/models/ner/english.muc.7class.distsim.crf.ser.gz
# LOCATION, MISC, ORGANIZATION, PERSON
edu/stanford/nlp/models/ner/english.conll.4class.distsim.crf.ser.gz
```

Tags written by one model *cannot* be overwritten by subsequent models in the series.

There are two options for how the models are combined.  These are selected with the `ner.combinationMode`
property.

* *NORMAL* - any given tag can only be applied by one model (the first model that applies a tag)
* *HIGH_RECALL* - all models can apply all tags

So for example, if the `ner.combinationMode` is set to `NORMAL`, only the 3-class
model's ORGANIZATION tags will be applied.  If it is set to `HIGH_RECALL`, the 7-class
and 4-class models' ORGANIZATION tags will also be applied.

If you do not want to run any statistical models, set `ner.model` to the empty string.

### Numeric Sequences and SUTime

Next a series of rule based systems are run to recognize and tag numeric sequences and time related sequences.

This phase runs by default, but can be deactivated by setting `ner.applyNumericClassifiers` to `false`.

This produces tags such as `NUMBER, ORDINAL, MONEY, DATE, and TIME`

The class that runs this phase is `edu.stanford.nlp.ie.regexp.NumberSequenceClassifier`

SUTime (described in more detail below) is also used by default.  You can deactivate this
by setting `ner.useSUTime` to `false`.

### Fine Grained NER

At this point, a series of rules used for the KBP 2017 competition will be run to create more fine-grained
NER tags.  These rules are applied using a TokensRegexNERAnnotator sub-annotator.  That is the main
`NERCombinerAnnotator` builds a `TokensRegexNERAnnotator` as a sub-annotator and runs it on all sentences
as part of it's entire tagging process.  The purpose of these rules is give tokens more specific tags.
So for instance `California` would be tagged as a `STATE_OR_PROVINCE` rather than just a `LOCATION`.

The `TokensRegexNERAnnotator` runs TokensRegex rules.  You can review all of the settings for a TokensRegexNERAnnotator
[here](https://stanfordnlp.github.io/CoreNLP/regexner.html).

*NOTE:* applying these rules will significantly slow down the tagging process.

The tags set by this phase include: 

```
CAUSE_OF_DEATH, CITY, COUNTRY, CRIMINAL_CHARGE, EMAIL, HANDLE,
IDEOLOGY, NATIONALITY, RELIGION, STATE_OR_PROVINCE, TITLE, URL
```

If you do not want to run the fine-grained rules, set `ner.applyFineGrained` to `false`.

### RegexNER Rules Format

There is a more detailed write up about RegexNER [here](https://nlp.stanford.edu/software/regexner.html)

The format is a series of tab-delimited columns.

The first column is the tokens pattern, the second column is the NER tag to apply, the third is the types
of NER tags that can be overwritten, and the fourth is a priority used for tie-breaking if two rules
match a sequence.

Each space delimited entry represents a regex to match a token.

The rule (remember these are tab-delimited columns):

```
Los Angeles	CITY	LOCATION,MISC	1.0
```

means to match the token "Los" followed by the token "Angeles", and label them both as CITY,
provided they have a current NER tag of O, LOCATION, or MISC.

The rule:

```
Bachelor of (Arts|Science)	DEGREE	MISC	1.0
```

means to match the token "Bachelor", then the token "of", and finally either the token "Arts" or "Science".

### Customizing The Fine-Grained NER

Here is a breakdown of how to customize the fine-grained NER.  The overall `ner` annotator creates a sub-annotator
called `ner.fine.regexner` which is an instance of a `TokensRegexNERAnnotator`.  

The `ner.fine.regexner.mapping` property allows one to specify a set of rules files and additional properties for each rules file.

The format is as follows:
1. For each rules file there is a comma delimited list of options, ending in the path of the rules file
2. Each entry for a rules file is separated by a `;`

As an example, this is the default `ner.fine.regexner.mapping` setting:

```
ignorecase=true,validpospattern=^(NN|JJ).*,edu/stanford/nlp/models/kbp/english/gazetteers/regexner_caseless.tab;edu/stanford/nlp/models/kbp/english/gazetteers/regexner_cased.tab
```

The two rules files are:

```
edu/stanford/nlp/models/kbp/english/gazetteers/regexner_caseless.tab
edu/stanford/nlp/models/kbp/english/gazetteers/regexner_cased.tab
```

The options for `edu/stanford/nlp/models/kbp/english/gazetteers/regexner_caseless.tab` are:

`ignorecase=true,validpospattern=^(NN|JJ).*`

while there are no options set for `edu/stanford/nlp/models/kbp/english/gazetteers/regexner_cased.tab` in this example.

Here is a description of some common options for the `TokensRegexNERAnnotator` sub-annotator used by `ner`

You can find more details on the page for the `TokensRegexNERAnnotator` located [here](https://stanfordnlp.github.io/CoreNLP/regexner.html)

| Option name | Type | Default | Description |
| --- | --- | --- | --- |
| ignorecase | boolean | false | make patterns case-insensitive or not? |
| validpospattern | regex | null | part of speech tag pattern that has to be matched |

If you want to set global settings that will apply for all rules files, remember to use `ner.fine.regexner.ignorecase` 
and `ner.fine.regexner.validpospattern`.  If you are setting options for a specific rules file with the
`ner.fine.regexner.mapping` option, follow the pattern from above.

### Additional TokensRegexNER Rules

After the fine-grained rules are run, there is also an option for a user to specify additional rules they would like
to have run after the fine-grained NER phase.

This second `TokensRegexNERAnnotator` sub-annotator has the name `ner.additional.regexner` and is customized in
the same manner.  This is for the case when users want to run their own rules after the standard rules we provide.

For instance, suppose you want to match sports teams after the previous NER steps have been run.

Your rules file might look like this `/path/to/sports_teams.rules`

```java
Boston Red Sox       SPORTS_TEAM     ORGANIZATION,MISC       1
Denver Broncos       SPORTS_TEAM     ORGANIZATION,MISC       1
Detroit Red Wings    SPORTS_TEAM     ORGANIZATION,MISC       1
Los Angeles Lakers   SPORTS_TEAM     ORGANIZATION,MISC       1
```

You could integrate this into the entire NER process by setting `ner.additional.regexner.mapping` to
`/path/to/sports_teams.rules`

By default no additional rules are run, so leaving `ner.additional.regexner.mapping` blank will cause
this phase to not be run at all.

### Additional TokensRegex Rules

If you want to run a series of TokensRegex rules before entity building, you can also specify a set
of TokensRegex rules.  A `TokensRegexAnnotator` sub-annotator will be called.  It has the name `ner.additional.tokensregex`.

Example command:

```bash
java -Xmx5g edu.stanford.nlp.pipeline.StanfordCoreNLP -annotators tokenize,ssplit,pos,lemma,ner -ner.additional.tokensregex.rules example.rules -file example.txt -outputFormat text
```

You can learn more about TokensRegex rules [here](https://stanfordnlp.github.io/CoreNLP/tokensregex.html)

### Entity Mention Detection

After all of the previous steps have been run, entity detection will be run to combine the tagged tokens into entities.
The entity mention detection will be based off of the tagging scheme.  This is accomplished with an `EntityMentionsAnnotator`
sub-annotator.

You can find a more detailed description of this annotator [here](https://stanfordnlp.github.io/CoreNLP/entitymentions.html)

If a basic IO tagging scheme (example: PERSON, ORGANIZATION, LOCATION) is used, all contiguous sequences of tokens with the same tag will be marked as an entity.

If a more advanced tagging scheme (such as BIO with tags like B-PERSON and I-PERSON) is used, sequences with the same tag
split by a B-tag will be turned into multiple entities.

All of our models and rule files use a basic tagging scheme, but you could create your own models and rules that use BIO.

For instance `(Joe PERSON) (Smith PERSON) (Jane PERSON) (Smith PERSON)` will create the entity `Joe Smith Jane Smith`.

On the other hand `(Joe B-PERSON) (Smith I-PERSON) (Jane B-PERSON) (Smith I-PERSON)` will create two entities: `Joe Smith` and `Jane Smith`.

You can deactivate this with `ner.buildEntityMentions` being set to `false`.

At this point the NER process will be finished, having tagged tokens with NER tags and created entities.

## Command Line Examples

There a variety of ways to customize an NER pipeline.  Below are some example commands.

```bash
# run default NER
java -Xmx4g -cp "*" edu.stanford.nlp.pipeline.StanfordCoreNLP -annotators tokenize,ssplit,pos,lemma,ner -file example.txt -outputFormat text
```

```bash
# only run rules based NER (numeric classifiers, SUTime, TokensRegexNER, TokensRegex)
java -Xmx4g -cp "*" edu.stanford.nlp.pipeline.StanfordCoreNLP -annotators tokenize,ssplit,pos,lemma,ner -ner.rulesOnly -file example.txt 
```

```bash
# only run statistical NER
java -Xmx4g -cp "*" edu.stanford.nlp.pipeline.StanfordCoreNLP -annotators tokenize,ssplit,pos,lemma,ner -ner.statisticalOnly -file example.txt 
```

```bash
# shut off numeric classifiers
# note that in this case ner no longer requires pos or lemma
java -Xmx4g -cp "*" edu.stanford.nlp.pipeline.StanfordCoreNLP -annotators tokenize,ssplit,ner -ner.applyNumericClassifiers false -file example.txt -outputFormat text
```

```bash
# shut off SUTime
java -Xmx4g -cp "*" edu.stanford.nlp.pipeline.StanfordCoreNLP -annotators tokenize,ssplit,pos,lemma,ner -ner.useSUTime false -file example.txt -outputFormat text
```

```bash
# specify doc date for each document to be 2019-01-01
# other options for setting doc date specified below
java -Xmx4g -cp "*" edu.stanford.nlp.pipeline.StanfordCoreNLP -annotators tokenize,ssplit,pos,lemma,ner -ner.docdate.useFixedDate 2019-01-01 -file example.txt
```

```bash
# shut off fine grained NER
java -Xmx4g -cp "*" edu.stanford.nlp.pipeline.StanfordCoreNLP -annotators tokenize,ssplit,pos,lemma,ner -ner.applyFineGrained false -file example.txt -outputFormat text
```

```bash
# run fine-grained NER with a custom rules file
java -Xmx4g -cp "*" edu.stanford.nlp.pipeline.StanfordCoreNLP -annotators tokenize,ssplit,pos,lemma,ner -ner.fine.regexner.mapping custom.rules -file example.txt -outputFormat text
```

```bash
# run fine-grained NER with two custom rules files
# the first rules file caseless.rules should be case-insensitive, the second rules file uses default options
java -Xmx4g -cp "*" edu.stanford.nlp.pipeline.StanfordCoreNLP -annotators tokenize,ssplit,pos,lemma,ner -ner.fine.regexner.mapping "ignorecase=true,caseless.rules;cased.rules" -file example.txt -outputFormat text
```

```bash
# add additional rules to run after fine-grained NER
java -Xmx4g -cp "*" edu.stanford.nlp.pipeline.StanfordCoreNLP -annotators tokenize,ssplit,pos,lemma,ner -ner.additional.regexner.mapping additional.rules -file example.txt -outputFormat text
```

```bash
# run tokens regex rules
java -Xmx5g edu.stanford.nlp.pipeline.StanfordCoreNLP -annotators tokenize,ssplit,pos,lemma,ner -ner.additional.tokensregex.rules example.rules -file example.txt -outputFormat text
```

```bash
# don't build entity mentions
java -Xmx4g -cp "*" edu.stanford.nlp.pipeline.StanfordCoreNLP -annotators tokenize,ssplit,pos,lemma,ner -ner.buildEntityMentions false -file example.txt -outputFormat text
``` 

## Java API Example

```java
package edu.stanford.nlp.examples;

import edu.stanford.nlp.pipeline.*;

import java.util.Properties;
import java.util.stream.Collectors;

public class NERPipelineDemo {

  public static void main(String[] args) {
    // set up pipeline properties
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner");
    // example customizations (these are commented out but you can uncomment them to see the results

    // disable fine grained ner
    // props.setProperty("ner.applyFineGrained", "false");

    // customize fine grained ner
    // props.setProperty("ner.fine.regexner.mapping", "example.rules");
    // props.setProperty("ner.fine.regexner.ignorecase", "true");

    // add additional rules, customize TokensRegexNER annotator
    // props.setProperty("ner.additional.regexner.mapping", "example.rules");
    // props.setProperty("ner.additional.regexner.ignorecase", "true");

    // add 2 additional rules files ; set the first one to be case-insensitive
    // props.setProperty("ner.additional.regexner.mapping", "ignorecase=true,example_one.rules;example_two.rules");

    // set document date to be a specific date (other options are explained in the document date section)
    // props.setProperty("ner.docdate.useFixedDate", "2019-01-01");

    // only run rules based NER
    // props.setProperty("ner.rulesOnly", "true");

    // only run statistical NER
    // props.setProperty("ner.statisticalOnly", "true");

    // set up pipeline
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
    // make an example document
    CoreDocument doc = new CoreDocument("Joe Smith is from Seattle.");
    // annotate the document
    pipeline.annotate(doc);
    // view results
    System.out.println("---");
    System.out.println("entities found");
    for (CoreEntityMention em : doc.entityMentions())
      System.out.println("\tdetected entity: \t"+em.text()+"\t"+em.entityType());
    System.out.println("---");
    System.out.println("tokens and ner tags");
    String tokensAndNERTags = doc.tokens().stream().map(token -> "("+token.word()+","+token.ner()+")").collect(
        Collectors.joining(" "));
    System.out.println(tokensAndNERTags);
  }

}

```

## SUTime

Stanford CoreNLP includes [SUTime](http://nlp.stanford.edu/software/sutime.html), Stanford's temporal expression
recognizer. SUTime is transparently called from the "ner" annotator,
so no configuration is necessary. Furthermore, the "cleanxml"
annotator can extract the reference date for a given XML document, so
relative dates, e.g., "yesterday", are transparently normalized with
no configuration necessary.

SUTime supports the same annotations as before, i.e.,
NamedEntityTagAnnotation is set with the label of the numeric entity (DATE,
TIME, DURATION, MONEY, PERCENT, or NUMBER) and
NormalizedNamedEntityTagAnnotation is set to the value of the normalized
temporal expression.

Also, SUTime sets the TimexAnnotation key to an
edu.stanford.nlp.time.Timex object, which contains the complete list of
TIMEX3 fields for the corresponding expressions, such as "val", "alt_val",
"type", "tid". This might be useful to developers interested in recovering
complete TIMEX3 expressions.

Reference dates are by default extracted from the "datetime" and
"date" tags in an xml document.  To set a different set of tags to
use, use the clean.datetags property.  When using the API, reference
dates can be added to an `Annotation` via
`edu.stanford.nlp.ling.CoreAnnotations.DocDateAnnotation`,
although note that when processing an xml document, the cleanxml
annotator will overwrite the `DocDateAnnotation` if
"datetime" or "date" are specified in the document.

### Setting Document Date

The `DocDateAnnotator` provides a variety of options for setting the document date.
The `ner` annotator will run this annotator as a sub-annotator.  These can be 
specified by setting properties for the `ner.docdate` sub-annotator.

| Option | Example | Description |
| --- | --- | --- |
| useFixedDate | 2019-01-01 | Provide a fixed date for each document. |
| useMappingFile | dates.txt | Use a tab-delimited file to specify doc dates.  First column is document ID, second column is date. |
| usePresent | - | Give every document the present date. |
| useRegex | NYT-([0-9]{4}-[0-9]{2}-[0-9]{2}).xml | Specify a regular expression matching file names.  The first group will be extracted as the date. |

## Accessing Entity Confidences

The following example shows how to access label confidences for tokens and entities.
Each token stores the probability of its NER label given by the CRF that was used to
assign the label in the `CoreAnnotations.NamedEntityTagProbsAnnotation.class`.  Each
entity mention contains the probability of the token with the lowest label probability
in its span.  For example if `Los Angeles` had the following probabilities:

```
{word: 'Los', 'tag': 'LOCATION', 'prob': .992} 
{word: 'Angeles', 'tag': 'LOCATION', 'prob': .999}
```

the entity `Los Angeles` would be assigned the `LOCATION` tag with a confidence of `.992`.

Below is code for accessing these confidences.

```
package edu.stanford.nlp.examples;

import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.pipeline.*;
import java.util.*;

public class NERConfidenceExample {

    public static void main(String[] args) {
        String exampleText = "Joe Smith lives in California.";
        Properties props = new Properties();
        props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner");
        StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
        CoreDocument document = new CoreDocument(exampleText);
        pipeline.annotate(document);
        // get confidences for entities
        for (CoreEntityMention em : document.entityMentions()) {
            System.out.println(em.text() + "\t" + em.entityTypeConfidences());
        }
        // get confidences for tokens
        for (CoreLabel token : document.tokens()) {
            System.out.println(token.word() + "\t" + token.get(CoreAnnotations.NamedEntityTagProbsAnnotation.class));
        }
    }
}
```

## Caseless models

It is possible to run Stanford CoreNLP with NER
models that ignore capitalization. We have trained models like this
for English. You can find details on the
[Caseless models](caseless.html) page.

## Training or retraining new models

The train/dev/test data files should be in the following format:

```
Joe    PERSON
Smith  PERSON
lives  O
in     O
California    LOCATION
.    O

He    O
used    O
to    O
live    O
in    O
Oregon    LOCATION
.    O

```

In this example, each line is a token, followed by a tab, followed by the NER tag.  A blank line represents a sentence break.
The model that we release is trained on over a million tokens.  The more training data you have, the more accurate your model should be.

The standard training data sets used for PERSON/LOCATION/ORGANIZATION/MISC must be purchased from the LDC, we do not distribute them.

Here is the command for starting the training process (make sure your CLASSPATH is set up to include all of the Stanford CoreNLP jars):

```bash
java -Xmx2g -cp "*" edu.stanford.nlp.ie.crf.CRFClassifier -prop ner.model.props
```

The training process can be customized using a properties file.  Here is an example properties file for training an English model(ner.model.props):

```
# location of training data
trainFileList = /path/to/conll.3class.train
# location of test data
testFile = /path/to/all.3class.test
# where to store the saved model
serializeTo = ner.model.ser.gz

type = crf

wordFunction = edu.stanford.nlp.process.AmericanizeFunction

useDistSim = false

# establish the data file format
map = word=0,answer=1

saveFeatureIndexToDisk = true

useClassFeature=true
useWord=true
useNGrams=true
noMidNGrams=true
maxNGramLeng=6
usePrev=true
useNext=true
useLongSequences=true
useSequences=true
usePrevSequences=true
maxLeft=1
useTypeSeqs=true
useTypeSeqs2=true
useTypeySequences=true
useOccurrencePatterns=true
useLastRealWord=true
useNextRealWord=true
normalize=true
wordShape=chris2useLC
useDisjunctive=true
disjunctionWidth=5

readerAndWriter=edu.stanford.nlp.sequences.ColumnDocumentReaderAndWriter

useObservedSequencesOnly=true

useQN = true
QNsize = 25

# makes it go faster
featureDiffThresh=0.05

```

There is more info about training a CRF model [here](https://nlp.stanford.edu/software/crf-faq.html#a).

You can learn more about what the various properties above mean [here](https://nlp.stanford.edu/nlp/javadoc/javanlp/edu/stanford/nlp/ie/NERFeatureFactory.html).

SUTime rules can be changed by modifying its included
TokensRegex rule files. Changing other rule-based components (money,
etc.) requires changes to the Java source code.

## More information 

For more details on the CRF tagger see [this page](http://nlp.stanford.edu/software/CRF-NER.html).
