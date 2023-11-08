---
layout: page
title: Shift Reduce Parser
keywords: lexparser, parser, srparser, shift reduce parser, constituency
permalink: '/tools_srparser.html'
nav_order: 15
toc: true
parent: Additional Tools
---

### Introduction

Previous versions of the Stanford Parser for constituency parsing used chart-
based algorithms (dynamic programming) to find the highest scoring parse under
a PCFG; this is accurate but slow. Meanwhile, for dependency parsing,
transition-based parsers that use shift and reduce operations to build
dependency trees have long been known to get very good performance in a
fraction of the time of more complicated algorithms. Recent work has shown
that similar shift-reduce algorithms are also effective for building
constituency trees.

Based on this work, we built a Shift-Reduce Parser which is far faster than
previous versions of the Stanford Parser while being more accurate than any
version other than the RNN parser.

### Acknowledgements

Stanford's Shift-Reduce Parser was written by John Bauer. It is based on the
prior work of several other researchers:

  * [Fast and Accurate Shift-Reduce Constituent Parsing](http://www.aclweb.org/anthology/P13-1043.pdf) by Muhua Zhu, Yue Zhang, Wenliang Chen, Min Zhang and Jingbo Zhu 
  * [Transition-Based Parsing of the Chinese Treebank using a Global Discriminative Model](http://aclweb.org/anthology//W/W09/W09-3825.pdf) by Yue Zhang and Stephen Clark 
  * [A Classifier-Based Parser with Linear Run-Time Complexity](http://people.ict.usc.edu/~sagae/docs/sagae-iwpt05.pdf) by Kenji Sagae and Alon Lavie 
  * [A Dynamic Oracle for Arc-Eager Dependency Parsing](http://aclweb.org/anthology//C/C12/C12-1059.pdf) by Yoav Goldberg and Joakim Nivre 
  * [Learning Sparser Perceptron Models](http://www.cs.bgu.ac.il/~yoavg/publications/acl2011sparse.pdf) by Yoav Goldberg and Michael Elhadad 

### Obtaining the software

You must download the following packages:

  1. Either download 
    * only the [latest Stanford parser](stanford-parser-full-2014-10-31.zip) ([more details](tools_lex_parser.md)) and [latest Stanford tagger](stanford-postagger-2014-10-31.zip) ([more details](tools_pos_tagger.html)); or 
    * all of [Stanford CoreNLP](index.md), which contains the parser, the tagger, and other things which you may or may not need. 
  2. The [shift-reduce parser models](index.md), part of the English Extra package (these are distributed separately because they are quite large). 

### Using the Shift-Reduce Parser using Stanford CoreNLP

Read this if you just want to use the Shift-Reduce Parser from the command-
line.

The new parser is integrated into [Stanford CoreNLP](index.md). The
simplest way to use it is to give StanfordCoreNLP the flag

> `-parse.model <location>`

Using resources from a release of `English Extra Models`,
you might use:

> `-parse.model edu/stanford/nlp/models/srparser/englishSR.ser.gz`

On the Stanford cluster, the location is in `/u/nlp/data/srparser`, so

> `-parse.model /u/nlp/data/srparser/englishSR.ser.gz`

There are other models as well. For example, there is a model trained to use
beam search. By default, this model will use a beam of size 4. If you want to
change that, you can use the flag `-parse.flags " -beamsize 4"` Note that the
space after the quote is necessary for our options processing code. The full
list of models currently distributed is:

>  edu/stanford/nlp/models/srparser/arabicSR.ser.gz  
>  edu/stanford/nlp/models/srparser/englishSR.beam.ser.gz  
>  edu/stanford/nlp/models/srparser/englishSR.ser.gz  
>  edu/stanford/nlp/models/srparser/germanSR.ser.gz  
>  edu/stanford/nlp/models/srparser/chineseSR.ser.gz  
>  edu/stanford/nlp/models/srparser/frenchSR.beam.ser.gz `

### Calling Parsing from Java

Read this if you want to call the Shift-Reduce Parser from your own code. In
the parser package, there is a ShiftReduceDemo.java class.

To compile it (the two jar files are provided by Stanford Parser and the
Stanford Tagger, respectively):

> javac -cp stanford-parser.jar:stanford-postagger-3.5.0.jar ShiftReduceDemo.java

To run it:

```
java -cp .:stanford-parser.jar:stanford-postagger-3.5.0.jar:stanford-srparser-2014-10-23-models.jar ShiftReduceDemo -model edu/stanford/nlp/models/srparser/englishSR.ser.gz -tagger english-left3words-distsim.tagger

Note that we need to include the jar file where the parser models are stored,
as well as specifying the tagger model (which came from the Stanford Tagger
package). This should load the tagger, parser, and parse the example sentence,
finishing in under 20 seconds. The output:

> Reading POS tagger model from stanford-postagger-full-2014-10-23/models/english-left3words-distsim.tagger ... done [1.2 sec].  
> Loading parser from serialized file edu/stanford/nlp/models/srparser/englishSR.ser.gz ...done [14.8 sec].  
>  (ROOT (S (NP (PRP$ My) (NN dog)) (VP (VBZ likes) (S (VP (TO to) (VP (VB shake) (NP (PRP$ his) (VBN stuffed) (NN chickadee) (NN toy)))))) (. .))) `

### How Shift-Reduce Parsing Works

The Shift-Reduce Parser parses by maintaining a state of the current parsed
tree, with the words of the sentence on a queue and partially completed trees
on a stack, and applying transitions to the state until the queue is empty and
the current stack only contains a finished tree.

The initial state is to have all of the words in order on the queue, with an
empty stack. The transitions which can be applied are:

  * Shift. A word moves from the queue onto the stack. 
  * Unary reduce. The label of the first constituent on the stack changes. There is a different unary transition for every possible unary node in the treebank used for training. 
  * Binary reduce. The first two nodes on the stack are combined with a new label. These are either right sided or left sided, indicating which child is treated as the head. Once again, there is a different binary transition for every possible binary node. This includes temporary nodes, as trees are built as binarized trees and then later debinarized. 
  * Finalize. A tree is not considered finished until the parser chooses the finalize transition. 
  * Idle. In the case of beam searching, Zhu et al. showed that training an idle transition compensates for different candidate trees using different numbers of transitions. 

Transitions are determined by featurizing the current state and using a
multiclass perceptron to determine the next transition. Various legality
constraints are applied to the transitions to make sure the state remains
legal and solvable.

Part-of-speech tags are not assigned by this parser, and are in fact used as
features. This is accomplished by pretagging the text, meaning the `pos`
annotator needs to be used in StanfordCoreNLP, for example.

Training is conducted by iterating over the trees repeatedly until some
measure of convergence is reached. There are various ways to process the trees
during each iteration. The one used by default is to start from the basic
state and apply the transitions chosen by the parser until it gets a
transition wrong, i.e., it can no longer rebuild the gold tree. The features
used at the time of the incorrect decision have their weights adjusted, with
the correct transition getting the feature weights increased and the incorrect
transition decreased.

#### Beam Search

In general, the parser uses greedy transitions, continuing until the sentence
is finalized. It is also possible to use it in beam search mode, though. In
this mode, the parser keeps an agenda of the highest scoring candidate states.
At each step, each of the states has a transition applied, updating the agenda
with the new highest scoring states. This process continues until the highest
scoring state on the agenda is finalized.

Models need to be specifically trained to work with beam search. Otherwise,
scores actually get worse. While this increases accuracy quite a bit, it also
has the drawback of significantly increasing the size of the model. A model
not trained for beam search only ever has features which were present in
states reached by the gold sequence of transitions on the gold training trees.
A model trained to use beam search trains negative features for incorrect
states on the beam, resulting in many more features and therefore a much
larger model.

### Training the Shift-Reduce Parser

Read this section if you want to train your own Shift-Reduce Parser. This
requires you have data in the standard Treebank format.

#### Basic Guidelines

New English `WSJ` models can be trained as follows:

```
java -mx10g edu.stanford.nlp.parser.shiftreduce.ShiftReduceParser -trainTreebank <trainpath> -devTreebank <devpath> -serializedPath model.ser.gz
```

Concretely, on the NLP machines, this would be

```
java -mx10g edu.stanford.nlp.parser.shiftreduce.ShiftReduceParser -trainTreebank /afs/ir/data/linguistic-data/Treebank/3/parsed/mrg/wsj 200-2199 -devTreebank /afs/ir/data/linguistic-data/Treebank/3/parsed/mrg/wsj 2200-2219 -serializedPath model.ser.gz
```

More details on how it trains are below. The key summary is that some time
later, probably an hour on a decent machine, there will be a new file
`model.ser.gz` which is the newly trained Shift-Reduce Parser. This model can
be tested as follows:

```
java -mx6g edu.stanford.nlp.parser.shiftreduce.ShiftReduceParser -testTreebank <testpath> -serializedPath model.ser.gz
```

```
java -mx6g edu.stanford.nlp.parser.shiftreduce.ShiftReduceParser -testTreebank /afs/ir/data/linguistic-data/Treebank/3/parsed/mrg/wsj 2300-2399 -serializedPath model.ser.gz
```

However, this ignores a key aspect of the Shift-Reduce Parser. This parser
does not produce its own tags and instead expects to use automatically
produced tags as features when parsing. The commands above will work, but they
will train a model using the gold tags in the treebank. It is generally better
to train on the tags provided by the tagger which will be used as test time.
This can be done with the flags `-pretag -taggerSerializedFile <tagger>`

```
java -mx10g edu.stanford.nlp.parser.shiftreduce.ShiftReduceParser -trainTreebank /afs/ir/data/linguistic-data/Treebank/3/parsed/mrg/wsj 200-2199 -devTreebank /afs/ir/data/linguistic-data/Treebank/3/parsed/mrg/wsj 2200-2219 -preTag -taggerSerializedFile /u/nlp/data/pos-tagger/distrib/wsj-0-18-bidirectional-nodistsim.tagger -serializedPath model.ser.gz
```

```
java -mx6g edu.stanford.nlp.parser.shiftreduce.ShiftReduceParser -devTreebank /afs/ir/data/linguistic-data/Treebank/3/parsed/mrg/wsj 2300-2399 -preTag -taggerSerializedFile /u/nlp/data/pos-tagger/distrib/wsj-0-18-bidirectional-nodistsim.tagger -serializedPath model.ser.gz
```

The `bidirectional` model is significantly slower than the `left3words` model,
but is somewhat more accurate. It is still faster than the parsing itself.
Alternatively, one can just use the `left3words` tagger for better speed and
slightly less accuracy.

#### Other Languages

It is possible to train the Shift-Reduce Parser for languages other than
English. An appropriate HeadFinder needs to be provided. This and other
options are handled by specifying the `-tlpp` flag, which lets you choose the
class for a `TreebankLangParserParams`. A language appropriate tagger is also
required.

For example, here is a command used to train a Chinese model. The options not
already explained are explained in the next section.

```
java -mx10g edu.stanford.nlp.parser.shiftreduce.ShiftReduceParser -trainTreebank /u/nlp/data/chinese/ctb7/train.mrg -devTreebank /u/nlp/data/chinese/ctb7/dev_small.mrg -preTag -taggerSerializedFile /u/nlp/data/pos-tagger/distrib/chinese-nodistsim.tagger -serializedPath chinese.ser.gz -tlpp edu.stanford.nlp.parser.lexparser.ChineseTreebankParserParams -trainingThreads 4 -batchSize 12 -trainingIterations 100 -stalledIterationLimit 20
```

The resulting model is both faster and more accurate than any other model we
have, including the Chinese RNN model.

#### FeatureFactory classes

The features for the Perceptron are extracted using a FeatureFactory. By
default, the parser uses
`edu.stanford.nlp.parser.shiftreduce.BasicFeatureFactory`. This FeatureFactory
includes most of the basic features you would want, such as features for the
head word of the current stack node and several previous stack nodes, the word
and tag of incoming queue items, and combinations of those strings.

Another included FeatureFactory is the DistsimFeatureFactory. This can be used
by setting the `-featureFactory` argument:

```
-featureFactory edu.stanford.nlp.parser.shiftreduce.DistsimFeatureFactory(<path_to_distsim>)
```

Multiple FeatureFactory classes can be combined by using a semicolon separated
list. For example:

```
-featureFactory edu.stanford.nlp.parser.shiftreduce.BasicFeatureFactory;edu.stanford.nlp.parser.shiftreduce.DistsimFeatureFactory(<path_to_distsim>)
```

#### Additional Options

‑trainingMethod| See below.  
---|---  
‑beamSize| Size of the beam to use when running beam search. 4 is already sufficient to greatly increase accuracy without affecting speed too badly.  
‑trainBeamSize| Size of the beam to use when training.  
‑trainingThreads| Training can be run in parallel. This is done by training on multiple trees simultaneously.  
‑batchSize| How many trees to batch together when training. This allows training in parallel to get repeatable results, as each of the trees are scored using the weights at the start of the training batch, and then all updates are applied at once.  
‑trainingIterations| The maximum number of iterations to train. Defaults to 40.  
‑stalledIterationLimit| The heuristic for ending training before `-trainingIterations` iterations is to stop when the current dev set score has not improved for this many iterations. Defaults to 20.  
‑averagedModels| When the perceptron has finished training, in general, the model with the best score on the dev set is kept. This flag averages together the best K models and uses that as the final model instead. Defaults to 8. This has the potential to greatly increase the amount of memory needed, so can be set to a lower number if memory is a barrier.  
‑featureFrequencyCutoff| If a feature is not seen this many times when training, it is removed from the final model. This can eliminate rarely seen features without impacting overall accuracy too much. It is especially useful in the case of model training using a beam (or oracle, if that method is ever made to work), as that training method results in many features that were only seen once and don't really have much impact on the final model.
‑saveIntermediateModels| By default, training does not save the intermediate models any more, since they basically don't do anything. Use this flag to turnit back on.  
‑featureFactory| The feature factory class to use.  
  
There are several training methods implemented. The default is
`EARLY_TERMINATION`, in which training on an individual tree is halted as soon
as the current model is incorrect. Alternatives are:

-trainingMethod  
---  
GOLD| Force the parser to make the correct transition while training,
continuing after errors. Takes longer than EARLY_TERMINATION and does not
improve accuracy.  
EARLY_TERMINATION| As soon as the current model gets a transition wrong when
parsing a tree, stops training on that tree for this iteration.  
BEAM| An agenda of the highest scoring candidate states is kept. Training
continues until the gold state is no longer on the agenda. At each step, the
gold transition for the gold state gets its feature weights increased, and
transition chosen for the highest scoring state gets its feature weights
decreased.  
ORACLE| An experimental training method in which an oracle is used to figure
out what transition would result in the best possible parse from the current
state. Unfortunately, this results in poor scores, either because of a bug in
the oracle training code or incorrect oracle logic.  
  
### Performance

The tables below summarize the Shift-Reduce Parser's performance, based on
experiments run in 2014.

| English Penn WSJ sec 23 (all lengths) |
|---|
|  |
|  |
| wsjPCFG.ser.gz | 1.0 | 426 | 85.54 |
| wsjSR.ser.gz | 4.5 | 14 | 85.99 |
| wsjFactored.ser.gz | 7.6 | 1759 | 86.53 |
| wsjSR.beam.ser.gz, beam size
        4 | 15.4 | 34 | 88.55 |
| wsjRNN.ser.gz | 2.6 | 1038 | 89.96 |

  
| SR Model | Previous Best Stanford model F1 | SR Parser F1 |
|---|---|---|
|  |
| Arabic | 78.15 | 79.66 |
| Chinese | 75.66 | 80.23 |
| French (beam) | 76.22 | 80.27 |
| German | 72.19 | 74.04 |

