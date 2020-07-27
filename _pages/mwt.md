---
layout: page
title: Multi Word Token Expansion
keywords: mwt, MWTAnnotator 
permalink: '/mwt.html'
nav_order: 8
parent: Pipeline
---

## Description

Multi Word Token Expansion is the process of splitting tokens into syntactic words which are used by downstream tasks such as part of speech tagging
and dependency parsing. In CoreNLP, MWT expansion is only performed for French, German, and Spanish. The expansions are designed to be consistent
with the UD 2.0 standard.

Each language has different rules for MWT expansion. For instance consider the Spanish sentence `Pude haber querido escribirlo.`. This sentence
contains an example of an enclitic pronoun, which is split off from the verb by MWT expansion. So `escribirlo` is split into `escribir` and `lo`.

Some MWT split decisions are made by a deterministic dictionary, while others are made by a statistical model.

| Name | Annotator class name | Requirement | Generated Annotation | Description |
| --- | --- | --- | --- | --- |
| mwt | MWTAnnotator | TokensAnnotation, SentencesAnnotation | - | Splits multi word tokens according to UD 2.0 standard. |

## Options

| Option name | Type | Default | Description |
| --- | --- | --- | --- |
| mwt.mappingFile | String | - | Mapping file containing dictionary for splitting MWT tokens (e.g. "escribirlo -> escribir + lo"). The format of the mapping file must be 2 tab delimited columns. The first column is the original word (e.g. "escribirlo"), and the second column should be a comma separated list of multi word tokens (e.g. "escribir,lo") for the original token. An example of such a file is `edu/stanford/nlp/models/mwt/spanish/spanish-mwt.tsv` located in the Spanish models jar. |
| mwt.pos.model | String | - | Part of speech tag model to use for MWT expansion. This is a special part of speech tag model exclusively used for MWT expansion, and its tags will not be used after MWT expansion is completed. For example, French uses the special MWT pos model `edu/stanford/nlp/models/mwt/french/french-mwt.tagger` as part of the decision process for splitting the word "des". The model should apply special part of speech tags to words that should be split, while the corresponding statisticalMappingFile will designate what to do in the event of a split decision. For instance, the French tagging model tags instances of the word "des" that should be split with the tag "ADP_DET". The corresponding French statisticalMappingFile designates what to do when tag is seen with the word "des". This model will provide tags that the statisticalMappingFile will use. |
| mwt.statisticalMappingFile | String | - | Mapping file for statistical MWT decisions. The format is 2 tab separated columns. The first column is of the form `word-tag` representing word tag pairs that should be split (e.g. "des-ADP_DET"). The second column should be a comma separated list of MWT tokens (e.g. "de,les"). An example of this file for French is `edu/stanford/nlp/models/mwt/french/french-mwt-statistical.tsv`. |

## Statistical Multi Word Expansion

In French, the word "des" is only split in certain circumstances. Thus, a statistical model is needed to make decisions on when to split "des" into "de" and "les".
First a part of speech tag model for MWT expansion is applied (`mwt.pos.model`). This model applies special tags to words that should be split. For French, it applies
the tag "ADP_DET" to the word "des" in cases where "des" should be split. A corresponding dictionary is provided via `mwt.statisticalMappingFile` which maps word-tag
pairs to splits. In the case of French, "des-ADP_DET" is mapped to "de,les". Mapping files should consist of 2 tab separated columns. The first column contains
the word and tag (e.g. "des-ADP_DET"), and the second column should consist of a comma separated list of multi word tokens (e.g. "de,les").

An example of the statistical model file is `edu/stanford/nlp/models/mwt/french/french-mwt-statistical.tsv` in the French models jar.

```
des-ADP_DET    de,les
```

## Multi Word Token Expansion From The Command Line

This command will take in the text of the file `input.txt` (assumed to be French in this example) and produce a human readable output of the sentences, 
with MWT expansion applied.

```bash
java -Xmx5g edu.stanford.nlp.pipeline.StanfordCoreNLP -props french -annotators tokenize,ssplit,mwt -file input.txt
```

Other output formats include `conllu`, `conll`, `json`, and `serialized`.

## Multi Word Token Expansion From Java

```java
package edu.stanford.nlp.examples;

import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.pipeline.*;
import edu.stanford.nlp.util.StringUtils;

import java.util.*;

public class MWTExpansionExample {

  public static String text = "Pude haber querido escribirlo.";

  public static void main(String[] args) {
    // set the list of annotators to run
    Properties props = StringUtils.argsToProperties("-props", "spanish");
    props.setProperty("annotators", "tokenize,ssplit,mwt");
    // build pipeline
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
    // create a document object
    CoreDocument doc = new CoreDocument(text);
    // annotate
    pipeline.annotate(doc);
    // display tokens
    for (CoreLabel tok : doc.tokens()) {
      System.out.println(String.format("%s", tok.word()));
    }
  }
}
```

This demo code will produce this output, which shows the enclitic pronoun being split from the verb:

```
Pude
haber
querido
escribir
lo
.
```
