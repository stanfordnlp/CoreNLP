---
layout: page
title: Model Zoo
keywords: model zoo
permalink: '/model-zoo.html'
nav_order: 1
toc: false
parent: Resources
---

A variety of third-party groups have created extensions for Stanford CoreNLP.

In the table below we provide access to their work.  By simply adding the jar for
an entry to your classpath, you can begin using the extension.

For example, if you download `corenlp-swedish-1.0.0.jar` and place it in your
CLASSPATH, you can then run a POS tagger on Swedish.

```bash
# set up CLASSPATH to include model zoo jars
export CLASSPATH=$CLASSPATH:/path/to/model_zoo/*
# go to model zoo directory 
cd /path/to/model_zoo
# download file
wget https://nlp.stanford.edu/software/model_zoo/corenlp-swedish-1.0.0.jar
# run swedish pos tagger
java -Xmx2g edu.stanford.nlp.pipeline.StanfordCoreNLP -annotators tokenize,ssplit,pos -pos.model edu/stanford/nlp/models/zoo/corenlp-swedish/swedish.tagger -file example-swedish-sentence.txt -outputFormat text
```

If you would like to contribute to the Model Zoo,
<a href="mailto:java-nlp-support@lists.stanford.edu">contact us</a>
or issue a pull request on our
<a href="https://github.com/stanfordnlp/CoreNLP/blob/gh-pages/_pages/model-zoo.md">GitHub</a>!

| Name | Language | Description | Group | Version | Requires | Download |
| --- | --- | --- | --- | --- | --- | --- |
| Russian CoreNLP | Russian | POS and Parsing for Russian.  More info [here](https://github.com/MANASLU8/CoreNLP) | ITMO University | 1.0.0 | latest code on GitHub | [stanford-russian-corenlp-models.jar](https://drive.google.com/file/d/1_0oU8BOiYCqHvItSsz0BjJnSNp8PRWlC/view?usp=sharing) |
| Swedish CoreNLP | Swedish | A POS model for Swedish.  More info [here](https://medium.com/@klintcho/training-a-swedish-pos-tagger-for-stanford-corenlp-546e954a8ee7) | Andreas Klintberg | 1.0.0 | Stanford CoreNLP 3.9.2 | [corenlp-swedish-1.0.0.jar](https://nlp.stanford.edu/software/model_zoo/corenlp-swedish-1.0.0.jar) |
| Danish CoreNLP | Danish | An NER model for Danish.  More info [here](https://arxiv.org/abs/1906.11608) | [ITU Copenhagen](http://nlp.itu.dk/) | 1.0.0 | Stanford CoreNLP 3.9.2 | [da01.model.gz](https://github.com/ITUnlp/daner/blob/master/da01.model.gz) | 
