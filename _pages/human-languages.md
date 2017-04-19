---
title: Using Stanford CoreNLP on other human languages
keywords: human languages
permalink: '/human-languages.html'
---

¡Hola! − 您好！

Out-of-the-box, Stanford CoreNLP expects and processes English language text. But, Stanford CoreNLP was designed from the start to work with multiple human languages and it is careful about things like different character encodings. We have developed components for several major languages, and make language packs (jar files) available for some of them. The table below summarizes our current first party foreign language support. Other people have developed [models for other languages](#models-for-other-languages).

| Annotator | ar | zh | en | fr | de | es |
| --------------- |:---:|:---:|:---:|:---:|:---:|:---:|
| Tokenize / Segment | ✔ | ✔  | ✔ | ✔  |     | ✔ |
| Sentence Split | ✔ | ✔  | ✔ | ✔  | ✔ | ✔ |
| Part of Speech | ✔ | ✔  | ✔ | ✔  | ✔ | ✔ |
| Lemma |   |   | ✔ |   |   |    |
| Named Entities |   | ✔  | ✔ |    | ✔ | ✔ |
| Constituency Parsing | ✔ | ✔  | ✔ | ✔ | ✔ | ✔ |
| Dependency Parsing |    | ✔  | ✔ | ✔ | ✔ |     |
| Sentiment Analysis |    |    | ✔ |  |  |     |
| Mention Detection |    | ✔  | ✔ |  |  |     |
| Coreference |    | ✔  | ✔ |  |  |     |
| Open IE |    |   | ✔ |  |  |     |

To get CoreNLP to work with another human language, you need a language pack of appropriate models for that language. You can find them on Maven Central or on [the download page](download.html). You could then manually specify to use all those resources, but that is impractical. You also want to have a properties file appropriate for the language that you are processing. A default one is included with the models jar for each language.  You can override individual properties on the command-line, as usual, or make a customized language-specific properties file appropriate to your processing needs.

Below are a few examples of commands for processing text in different languages. Many of the other languages do not have all the components that are available for English. Also note that at the moment for Chinese you use `segment` rather than `tokenize`. We'll aim to unify them eventually

### Chinese

You can process Chinese with a command line this:

```sh
java -mx3g -cp "*" edu.stanford.nlp.pipeline.StanfordCoreNLP -props StanfordCoreNLP-chinese.properties -file chinese.txt -outputFormat text
```

The crucial `StanfordCoreNLP-chinese.properties` file sets up models for Chinese. Its contents might be something like this (but this version in the documentation may well be dated, so extract the version from the jar file that you are using if you really want to be sure of its contents!):

```
# Pipeline options - lemma is no-op for Chinese but currently needed because coref demands it (bad old requirements system)
annotators = tokenize, ssplit, pos, lemma, ner, parse, mention, coref

# segment
tokenize.language = zh
segment.model = edu/stanford/nlp/models/segmenter/chinese/ctb.gz
segment.sighanCorporaDict = edu/stanford/nlp/models/segmenter/chinese
segment.serDictionary = edu/stanford/nlp/models/segmenter/chinese/dict-chris6.ser.gz
segment.sighanPostProcessing = true

# sentence split
ssplit.boundaryTokenRegex = [.。]|[!?！？]+

# pos
pos.model = edu/stanford/nlp/models/pos-tagger/chinese-distsim/chinese-distsim.tagger

# ner
ner.language = chinese
ner.model = edu/stanford/nlp/models/ner/chinese.misc.distsim.crf.ser.gz
ner.applyNumericClassifiers = true
ner.useSUTime = false

# regexner
regexner.mapping = edu/stanford/nlp/models/kbp/cn_regexner_mapping.tab
regexner.validpospattern = ^(NR|NN|JJ).*
regexner.ignorecase = true
regexner.noDefaultOverwriteLabels = CITY

# parse
parse.model = edu/stanford/nlp/models/srparser/chineseSR.ser.gz

# depparse
depparse.model    = edu/stanford/nlp/models/parser/nndep/UD_Chinese.gz
depparse.language = chinese

# coref
coref.sieves = ChineseHeadMatch, ExactStringMatch, PreciseConstructs, StrictHeadMatch1, StrictHeadMatch2, StrictHeadMatch3, StrictHeadMatch4, PronounMatch
coref.input.type = raw
coref.postprocessing = true
coref.calculateFeatureImportance = false
coref.useConstituencyTree = true
coref.useSemantics = false
coref.algorithm = hybrid
coref.path.word2vec =
coref.language = zh
coref.defaultPronounAgreement = true
coref.zh.dict = edu/stanford/nlp/models/dcoref/zh-attributes.txt.gz
coref.print.md.log = false
coref.md.type = RULE
coref.md.liberalChineseMD = false

# kbp
kbp.semgrex = edu/stanford/nlp/models/kbp/chinese/semgrex
kbp.tokensregex = edu/stanford/nlp/models/kbp/chinese/tokensregex
kbp.model = none

# entitylink
entitylink.wikidict = edu/stanford/nlp/models/kbp/wikidict_chinese.tsv.gz
```

In code, an example would look something like this:

```java
String text = "克林顿说，华盛顿将逐步落实对韩国的经济援助。"
        + "金大中对克林顿的讲话报以掌声：克林顿总统在会谈中重申，他坚定地支持韩国摆脱经济危机。";
Annotation document = new Annotation(text);
Properties props = PropertiesUtils.asProperties("props", "StanfordCoreNLP-chinese.properties");
StanfordCoreNLP corenlp = new StanfordCoreNLP(props);
corenlp.annotate(document);
```

### Another language

The pattern for other languages is much the same, except that you substitute the appropriate language name and that for European languages, you are still using the `tokenize` annotator. So for Spanish, it would be:

```sh
java -mx3g -cp "*" edu.stanford.nlp.pipeline.StanfordCoreNLP -props StanfordCoreNLP-spanish.properties -file spanish.txt -outputFormat json
```

### [Models for other languages created by other people](#models-for-other-languages)

Other people have developed models using or compatible with CoreNLP for several further languages. They may or may not be compatible with the most recent release of CoreNLP that we provide.

* **Italian:** [Tint](http://tint.fbk.eu/) by Alessio Palmero Aprosio and Giovanni Moretti (Fondazione Bruno Kessler) largely builds on CoreNLP, but adds some other components, to provide a quite complete processing pipeline for Italian.
* **Portuguese (European):** [LX parser](http://lxcenter.di.fc.ul.pt/tools/en/LXParserEN.html) by Patricia Gonçalves and João Silva (University of Lisbon)  provides a constituency parser. It was built with a now quite old version of Stanford NLP.
* **Swedish:** Andreas Klintberg has built an [NER model](https://medium.com/@klintcho/training-a-swedish-ner-model-for-stanford-corenlp-part-2-20a0cfd801dd#.vnow3swam) and a [POS tagger](https://medium.com/@klintcho/training-a-swedish-pos-tagger-for-stanford-corenlp-546e954a8ee7#.ms2ym1he3).
