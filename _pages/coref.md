---
title: CorefAnnotator
keywords: coref
permalink: '/coref.html'
---

## Description

The CorefAnnotator finds mentions of the same entity in a text, such as when “Theresa May” and “she” refer to the same person. The annotator implements both pronominal and nominal coreference resolution. The entire coreference graph (with head words of mentions as nodes) is saved as a CorefChainAnnotation.

## Overview

There are three different coreference systems available in CoreNLP.

* **Deterministic:** Fast rule-based coreference resolution for English and Chinese.

* **Statistical:** Machine-learning-based coreference resolution for English. Unlike the other systems, this one only requires dependency parses, which are faster to produce than constituency parses.

* **Neural:** Most accurate but slow neural-network-based coreference resolution for English and Chinese.

(We briefly also had a fourth **hybrid** or **hcoref** system, but it is no longer supported and models are no longer provided in current releases.)

The following table gives an overview of the system performances.

* The F1 scores are on the [CoNLL 2012](http://conll.cemantix.org/2012/introduction.html) evaluation data. Numbers are lower than reported in the associated [papers](#citing-stanford-coreference) because these models are designed for general-purpose use, not getting a high CoNLL score (see [Running on CoNLL 2012](#running-on-conll-2012)).

* The speed measurements show the average time for processing a document in the CoNLL 2012 test set using a 2013 Macbook Pro with a 2.4 GHz Intel Core i7 processor. Preprocessing speed measures the time required for POS tagging, syntax parsing, mention detection, etc., while coref speed refers to the time spent by the coreference system.


| System | Language | Preprocessing Time | Coref Time | Total Time | F1 Score |
| :--- | :--- | :--- | :--- | :--- | :--- |
| Deterministic | English | 3.87s | 0.11s | 3.98s | 49.5 |
| Statistical | English | 0.48s | 1.23s | 1.71s | 56.2 |
| Neural | English | 3.22s | 4.96s | 8.18s | 60.0 |
| Deterministic | Chinese | 0.39s | 0.16s | 0.55s | 47.5 |
| Neural | Chinese | 0.42s | 7.02s | 7.44s | 53.9 |

## Command Line Usage
There are example properties files for using the coreference systems in [edu/stanford/nlp/coref/properties](https://github.com/stanfordnlp/CoreNLP/tree/master/src/edu/stanford/nlp/coref/properties). The properties are named `[system]-[language].properties`. For example, to run the deterministic system on Chinese:

```bash
java -Xmx5g -cp stanford-corenlp-3.7.0.jar:stanford-chinese-corenlp-models-3.7.0.jar:* edu.stanford.nlp.pipeline.StanfordCoreNLP -props edu/stanford/nlp/coref/properties/deterministic-chinese.properties -file example_file.txt
```

Alternatively, the properties can be set manually. For example, to run the neural system on English:

```bash
java -Xmx5g -cp stanford-corenlp-3.7.0.jar:stanford-corenlp-models-3.7.0.jar:* edu.stanford.nlp.pipeline.StanfordCoreNLP -annotators tokenize,ssplit,pos,lemma,ner,parse,mention,coref -coref.algorithm neural -file example_file.txt
```

See [below](#more-details) for further options.

## API

The following example shows how to access coref and mention information from an Annotation:

```java
import java.util.Properties;

import edu.stanford.nlp.coref.CorefCoreAnnotations;
import edu.stanford.nlp.coref.data.CorefChain;
import edu.stanford.nlp.coref.data.Mention;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;

public class CorefExample {
  public static void main(String[] args) throws Exception {
    Annotation document = new Annotation("Barack Obama was born in Hawaii.  He is the president. Obama was elected in 2008.");
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner,parse,mention,coref");
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
    pipeline.annotate(document);
    System.out.println("---");
    System.out.println("coref chains");
    for (CorefChain cc : document.get(CorefCoreAnnotations.CorefChainAnnotation.class).values()) {
      System.out.println("\t" + cc);
    }
    for (CoreMap sentence : document.get(CoreAnnotations.SentencesAnnotation.class)) {
      System.out.println("---");
      System.out.println("mentions");
      for (Mention m : sentence.get(CorefCoreAnnotations.CorefMentionsAnnotation.class)) {
        System.out.println("\t" + m);
       }
    }
  }
}
```



## More Details

### Deterministic System
This is a multi-pass sieve rule-based coreference system. See [the Stanford Deterministic Coreference Resolution System page](http://nlp.stanford.edu/software/dcoref.html) for usage and more details.

### Statistical System
This is a mention-ranking model using a large set of features. It operates by iterating through each mention in the document, possibly adding a coreference link between the current one and a preceding mention at each step. Some relevant options:

* **coref.maxMentionDistance:** How many mentions back to look when considering possible antecedents of the current mention. Decreasing the value will cause the system to run faster but less accurately. The default value is 50.


* **coref.maxMentionDistanceWithStringMatch:** The system will consider linking the current mention to a preceding one further than coref.maxMentionDistance away if they share a noun or proper noun. In this case, it looks coref.maxMentionDistanceWithStringMatch away instead. The default value is 500.

* **coref.statisical.pairwiseScoreThresholds**: A number between 0 and 1 determining how greedy the model is about making coreference decisions. A value of 0 causes the system to add no coreference links and a value of 1 causes the system to link every pair of mentions, combining them all into a single coreference cluster. The default value is 0.35. The value can also be a comma-separated list of 4 numbers, in which case there are separate thresholds for when both mentions are pronouns, only the first mention is a pronoun, only the last mention is a pronoun, and neither mention is a pronoun.

### Neural System
This is a neural-network-based mention-ranking model. Some relevant options:

* **coref.maxMentionDistance** and **coref.maxMentionDistanceWithStringMatch**: See above.

* **coref.neural.greedyness**: A number between 0 and 1 determining how greedy the model is about making coreference decisions (more greedy means more coreference links). The default value is 0.5.

## Running on CoNLL 2012

If you would like to run our system on the CoNLL 2012 eval data:

1. Get the CoNLL scoring script from [here](http://conll.cemantix.org/2012/software.html)
2. Get the CoNLL 2012 eval data from [here](http://conll.cemantix.org/2012/data.html)
3. Run the CorefSystem main method. For example, for the English neural system:

```bash
java -Xmx6g -cp stanford-corenlp-3.7.0.jar:stanford-english-corenlp-models-3.7.0.jar:* edu.stanford.nlp.coref.CorefSystem -props edu/stanford/nlp/coref/properties/neural-english-conll.properties -coref.data <path-to-conll-data> -coref.conllOutputPath <where-to-save-system-output> -coref.scorer <path-to-scoring-script>
```

The CoNLL 2012 coreference data differs from the normal coreference use case in a few ways:

* There is provided POS, NER, Parsing, etc. instead of the annotations produced by CoreNLP.

* There are speaker annotations indicating who is saying which quote.

* There are document genre annotations.

Because of this, we train models with a few extra features for running on this dataset. We configure these models for accuracy over speed (e.g., by not having a maximum mention distance for the mention-ranking models). These models can be run using the `-conll` properties files (e.g., `neural-english-conll.properties`). Note that the CoNLL-specific models for English are in the
[English models jar](http://nlp.stanford.edu/software/stanford-english-corenlp-2016-01-10-models.jar), not the default CoreNLP models jar.

## Training New Models

### Deterministic System

As a rule-based system, there is nothing to train, but there are various data files for demonyms and to indicate noun gender, animacy, and plurality, which can be edited. See [the Stanford Deterministic Coreference Resolution System page](http://nlp.stanford.edu/software/dcoref.html).

### Statistical System
Training a statistical model on the CoNLL data can be done with the following command:

```bash
java -Xmx60g -cp stanford-corenlp-3.7.0.jar:stanford-english-corenlp-models-3.7.0.jar:* edu.stanford.nlp.coref.statistical.StatisticalCorefTrainer -props <properties-file>
```

See [here](https://github.com/stanfordnlp/CoreNLP/blob/master/src/edu/stanford/nlp/coref/statistical/properties/english-conll-training.properties) for an example properties file. Training over the full CoNLL 2012 training set requires a large amount of memory. To reduce the memory footprint and runtime of training, the following options can be added to the properties file:

* **coref.statistical.minClassImbalance**: Use this to downsample negative examples from each document. A value less than 0.05 is recommended.

* **coref.statisical.maxTrainExamplesPerDocument**:  Use this to downsample examples from larger documents. A value larger than 1000 is recommended.

### Neural System
The code for training the neural coreference system is implemented in python. It is available on github [here](https://github.com/clarkkev/deep-coref).


## Citing Stanford Coreference

The deterministic coreference system for English

> Marta Recasens, Marie-Catherine de Marneffe, and Christopher Potts. 2013. The Life and Death of Discourse Entities: Identifying Singleton Mentions. In *Proceedings of the NAACL*. \[[pdf](http://nlp.stanford.edu/pubs/discourse-referent-lifespans.pdf)\] \[[bib](http://nlp.stanford.edu/pubs/discourse-referent-lifespans.bib)\]

> Heeyoung Lee, Yves Peirsman, Angel Chang, Nathanael Chambers, Mihai Surdeanu, Dan Jurafsky. 2011. Stanford's Multi-Pass Sieve Coreference Resolution System at the CoNLL-2011 Shared Task. In *Proceedings of the CoNLL-2011 Shared Task.* \[[pdf](http://nlp.stanford.edu/pubs/conllst2011-coref.pdf)\] \[[bib](http://nlp.stanford.edu/pubs/conllst2011-coref.bib)\]

> Karthik Raghunathan, Heeyoung Lee, Sudarshan Rangarajan, Nathanael Chambers, Mihai Surdeanu, Dan Jurafsky and Christopher Manning. 2010. A Multi-Pass Sieve for Coreference Resolution.
*Empirical Methods in Natural Language Processing (EMNLP).* \[[pdf](https://nlp.stanford.edu/pubs/coreference-emnlp10.pdf)\] \[[bib](https://nlp.stanford.edu/pubs/coreference-emnlp10.bib)\]

The deterministic coreference system for Chinese and English

> Heeyoung Lee, Angel Chang, Yves Peirsman, Nathanael Chambers, Mihai Surdeanu and Dan Jurafsky. 2013. Deterministic coreference resolution based on entity-centric, precision-ranked rules. In *Computational Linguistics 39(4)*. \[[pdf](http://www.mitpressjournals.org/doi/pdf/10.1162/COLI_a_00152)\]

The statistical coreference system

> Kevin Clark and Christopher D. Manning.  2015. Entity-Centric Coreference Resolution with Model Stacking. In *Proceedings of the ACL*. \[[pdf](http://nlp.stanford.edu/pubs/clark-manning-acl15-entity.pdf)\] \[[bib](http://nlp.stanford.edu/pubs/clark-manning-acl15-entity.bib)\]

The neural coreference system

> Kevin Clark and Christopher D. Manning. 2016. Deep Reinforcement Learning for Mention-Ranking Coreference Models. In *Proceedings of EMNLP*. \[[pdf](http://nlp.stanford.edu/pubs/clark2016deep.pdf)\] \[[bib](http://nlp.stanford.edu/pubs/clark2016deep.bib)\]

> Kevin Clark and Christopher D. Manning. 2016. Improving Coreference Resolution by Learning Entity-Level Distributed Representations. In *Proceedings of the ACL*. \[[pdf](http://nlp.stanford.edu/pubs/clark2016improving.pdf)\] \[[bib](http://nlp.stanford.edu/pubs/clark2016improvingp.bib)\]


