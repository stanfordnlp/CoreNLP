---
title: Caseless models
keywords: caseless
permalink: '/caseless.html'
---

If your text is all lowercase, all uppercase, or badly and
inconsistently capitalized (many web forums, texts, twitter, etc.)
then this will negatively effect the performance of most of our
annotators. Most of our annotators were trained on data that is
standardly edited and capitalized full sentences.

There are two strategies available to address this that may help. One
is to try to first correctly capitalize the text with a
<i>truecaser</i>, and then to process the text with the standard models.
See the [TrueCaseAnnotator](truecase.html) for how to do this.

An example command for using regular annotators following truecasing
is:
```
java -Xmx3g edu.stanford.nlp.pipeline.StanfordCoreNLP -annotators tokenize,ssplit,truecase,pos,lemma,ner,depparse -truecase.overwriteText true -file caseless.txt -outputFormat json
```

The other strategy is to use models more suited to ill-capitalized text.

The GATE folk made an English POS tagger model trained on twitter
text. You can get it from [the extensions page](extensions.html).

We have made slightly different Stanford CoreNLP models for the tagger, parser, and NER
that ignore capitalization.  We have only trained such models
for English, but the same method could be used for other languages. 

To use these models, you need to download a jar file with caseless
models. Prior to version 3.6, caseless models were packaged separately as
their own jar file (approximately treating "caseless English" like a
separate language). Starting with version 3.6, caseless models for
English were included in the new comprehensive english jar file. You
can find these jar files on the [Release history](history.html) page.

Be sure to include the path to the case-insensitive models jar in the
`-cp` classpath flag and then you can 
ask for these models to be used like this:

```
-pos.model edu/stanford/nlp/models/pos-tagger/english-caseless-left3words-distsim.tagger
-parse.model edu/stanford/nlp/models/lexparser/englishPCFG.caseless.ser.gz
-ner.model edu/stanford/nlp/models/ner/english.all.3class.caseless.distsim.crf.ser.gz,edu/stanford/nlp/models/ner/english.muc.7class.caseless.distsim.crf.ser.gz,edu/stanford/nlp/models/ner/english.conll.4class.caseless.distsim.crf.ser.gz
```

**Important note (2016):** The caseless NER model
  `edu/stanford/nlp/models/ner/english.all.3class.caseless.distsim.crf.ser.gz`
  released with version 3.6.0 was defective and has very poor
  performance. Sorry! Stuff happens. If you want good caseless NER,
  you should either run with caseless models from a 3.5.x series
  release (all of which are compatible with version 3.6.0) or download
  the new fixed model from version 3.7.0 or in the HEAD jar, which is available from
  [our GitHub page](https://github.com/stanfordnlp/CoreNLP). Since the
  version 3.5.x releases have a separate caseless jar, it is easy to
  also specify it as a dependency; you just have to make sure that it
  appears on your classpath *before* other jars which contain models
  with the same name.

To train your own caseless models, you need one additional property,
which asks for a function to be called before a token is processed
which leads to the case of all words being ignored. We use by default
a function that also Americanizes the spelling of certain words:

```
wordFunction = edu.stanford.nlp.process.LowercaseAndAmericanizeFunction
```

but there is also simply:

```
wordFunction = edu.stanford.nlp.process.LowercaseFunction
```

