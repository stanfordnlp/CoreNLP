---
title: Named Entity Recognition – NERClassifierCombiner
keywords: ner
permalink: '/ner.html'
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
(job) TITLE, IDEOLOGY, CRIMINAL\_CHARGE, CAUSE\_OF\_DEATH (11 classes)
for a total of 23 classes. Named entities are recognized using a combination of three
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
| ner.applyNumericClassifiers | boolean | true | Whether or not to use numeric classifiers, for money, percent, numbers, including [SUTime](http://nlp.stanford.edu/software/regexner/).  These are hardcoded for English, so if using a different language, this should be set to false. |
| ner.useSUTime | boolean | true | Whether or not to use SUTime. SUTime at present only supports English; if not processing English, make sure to set this to false. |
| sutime.markTimeRanges | boolean | false | Tells SUTime whether to mark phrases such as “From January to March” as a range, instead of marking "January" and "March" separately. |
| sutime.includeRange | boolean | false | If marking time ranges, set the time range in the TIMEX output from SUTime. |
| maxAdditionalKnownLCWords | int | - | Limit the size of the known lower case words set.  Set this to 0 to prevent ordering issues (i.e. when this is nonzero running on document1 then document2 can have different results than running on document2 then document1 |  

## NER Pipeline Overview

The full named entity recognition pipeline has become fairly complex and involves
a set of distinct phases integrating statistical and rule based approaches.  Here
is a breakdown of those distinct phases.

### Statistical Model

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

* NORMAL - any given tag can only be applied by one model (the first model that applies a tag)
* HIGH_RECALL - all models can apply all tags

So for example, if the `ner.combinationMode` is set to `NORMAL`, only the 3-class
model's ORGANIZATION tags will be applied.  If it is set to `HIGH_RECALL`, the 7-class
and 4-class models' ORGANIZATION tags will also be applied.

If you do not want to run any statistical models, set `ner.model` to the empty string.

## SUTime

StanfordCoreNLP includes [SUTime](http://nlp.stanford.edu/software/sutime.html), Stanford's temporal expression
recognizer. SUTime is transparently called from the "ner" annotator,
so no configuration is necessary. Furthermore, the "cleanxml"
annotator can extract the reference date for a given XML document, so
relative dates, e.g., "yesterday", are transparently normalized with
no configuration necessary.

SUTime supports the same annotations as before, i.e.,
NamedEntityTagAnnotation is set with the label of the numeric entity (DATE,
TIME, DURATION, MONEY, PERCENT, or NUMBER) and
NormalizedNamedEntityTagAnnotation is set to the value of the normalized
temporal expression. Note that NormalizedNamedEntityTagAnnotation now
follows the TIMEX3 standard, rather than Stanford's internal representation,
e.g., "2010-01-01" for the string "January 1, 2010", rather than "20100101".

Also, SUTime now sets the TimexAnnotation key to an
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


## Caseless models

It is possible to run StanfordCoreNLP with NER
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
java -Xmx2g edu.stanford.nlp.ie.crf.CRFClassifier -prop ner.model.props
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

## Adding A Rules-Based Layer

By default the `ner` annotator will run a set of fine-grained rules for finding types such as `CAUSE_OF_DEATH, CITY, COUNTRY, CRIMINAL_CHARGE, EMAIL, IDEOLOGY, NATIONALITY, RELIGION, STATE_OR_PROVINCE, TITLE, URL`.  This is new in 3.9.1.  The rules for these can be found in `edu/stanford/nlp/models/kbp/english/gazetteers/regexner_caseless.tab` and `edu/stanford/nlp/models/kbp/english/gazetteers/regexner_cased.tab`.

If you do not want to run these fine-grained rules, add `-ner.applyFineGrained false` to your command (or set that property accordingly if using the Java API).

If you want to add custom rules that will run along with the fine-grained rules, use the `-ner.extraTokensRegexNERMappings` option.  This option is currently only available with the GitHub version, but should be included in version 3.9.2.

As an example, suppose you wanted to customize to detect sports teams.  Here would be an example rules file `sports_teams.rules`.

```java
Boston Red Sox       SPORTS_TEAM     ORGANIZATION,MISC       1
Denver Broncos       SPORTS_TEAM     ORGANIZATION,MISC       1
Detroit Red Wings    SPORTS_TEAM     ORGANIZATION,MISC       1
Los Angeles Lakers   SPORTS_TEAM     ORGANIZATION,MISC       1
```

If any of those token sequences were detected and flagged as O, ORGANIZATION or MISC, they would be re-tagged as SPORTS_TEAM.

Here is an example command adding these custom rules to the baseline `ner` system.

```bash
java -Xmx5g edu.stanford.nlp.pipeline.StanfordCoreNLP -annotators tokenize,ssplit,pos,lemma,ner -ner.extraTokensRegexNERMappings sports_teams.rules -file example.txt -outputFormat text
```

For more control, you can directly set the `ner.fine.regexner.mapping` option.  Here is what that is set to by default (with additional settings to just the rules files):

```java
ignorecase=true,validpospattern=^(NN|JJ).*,edu/stanford/nlp/models/kbp/english/gazetteers/regexner_caseless.tab;edu/stanford/nlp/models/kbp/english/gazetteers/regexner_cased.tab
```

You can specify multiple rules files and options for each rule file.  So the format would be something like `rule_file_1_option1,rule_file_1_option2,rule_file_1;rule_file_2_option1,rule_file_2`, if you wanted to specify 2 rules, where the first rule had 2 options, and the second rule had 1 option.

The `ignorecase` option says to make all of the rules case-insensitive.  The `validpospattern` option sets a part of speech tag pattern that must also be
matched for any of the rules to fire.  Then the actual rules file is specified.  The second rules file has no options specified in this example.

## More information 

For more details on the CRF tagger see [this page](http://nlp.stanford.edu/software/CRF-NER.html).
