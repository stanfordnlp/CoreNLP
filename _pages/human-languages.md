---
title: Using Stanford CoreNLP on different human languages
keywords: human languages
permalink: '/human-languages.html'
---

¡Hola! − 您好！

Out-of-the-box, Stanford CoreNLP expects and processes English language text. But, Stanford CoreNLP was designed from the start to work with multiple human languages and is careful about things like different character encodings.

To get CoreNLP to work with another human language, you need a language pack of appropriate models for that language. You can find them on Maven Central or on [the download page](download.html). You could then manually specify to use all those resources, but that is impractical. You also want to have a properties file appropriate for the language that you are processing. A default one is included with the models jar for each language.  You can override individual properties on the command-line, as usual, or make a customized language-specific properties file appropriate to your processing needs.

Below are a few examples of commands for processing text in different languages. Many of the other languages do not have all the components that are available for English. Also note that at the moment for Chinese you use `segment` rather than `tokenize`. We'll aim to unify them eventually

### Chinese

You can process Chinese with a command line this:

```sh
java -mx3g -cp "*" edu.stanford.nlp.pipeline.StanfordCoreNLP -props StanfordCoreNLP-chinese.properties -file chinese.txt -outputFormat text
```

The crucial `StanfordCoreNLP-chinese.properties` file sets up models for Chinese. Its contents might be something like this (but this version in the documentation may well be dated, so extract the version from the jar file that you are using if you really want to be sure of its contents!):

```
annotators = segment, ssplit, pos, ner, parse
customAnnotatorClass.segment = edu.stanford.nlp.pipeline.ChineseSegmenterAnnotator
segment.model = edu/stanford/nlp/models/segmenter/chinese/ctb.gz
segment.sighanCorporaDict = edu/stanford/nlp/models/segmenter/chinese
segment.serDictionary = edu/stanford/nlp/models/segmenter/chinese/dict-chris6.ser.gz
segment.sighanPostProcessing = true
ssplit.boundaryTokenRegex = [.]|[!?]+|[。]|[！？]+
pos.model = edu/stanford/nlp/models/pos-tagger/chinese-distsim/chinese-distsim.tagger
ner.model = edu/stanford/nlp/models/ner/chinese.misc.distsim.crf.ser.gz
ner.applyNumericClassifiers = false
ner.useSUTime = false
parse.model = edu/stanford/nlp/models/lexparser/chineseFactored.ser.gz
```

In code, an example would look something like this:

```java
String text = "克林顿说，华盛顿将逐步落实对韩国的经济援助。"
        + "金大中对克林顿的讲话报以掌声：克林顿总统在会谈中重申，他坚定地支持韩国摆脱经济危机。";
Properties props = PropertiesUtils.asProperties("-props",
		"edu/stanford/nlp/hcoref/properties/zh-dcoref-default.properties”);
Annotation document = new Annotation(text);
StanfordCoreNLP corenlp = new StanfordCoreNLP(props);
corenlp.annotate(document);
```

### Another language

The pattern for other languages is much the same, except that you substitute the appropriate language name and that for European languages, you are still using the `tokenize` annotator. So for Spanish, it would be:

```sh
java -mx3g -cp "*" edu.stanford.nlp.pipeline.StanfordCoreNLP -props StanfordCoreNLP-spanish.properties -file spanish.txt -outputFormat json
```
