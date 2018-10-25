---
title: Model Zoo
keywords: model zoo
permalink: '/model-zoo.html'
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

If you would like to contribute to the Model Zoo contact us or issue a pull request on our GitHub !

| Name | Description | Group | Version | Requires | Download |
| --- | --- | --- | --- | --- | --- |
| Swedish CoreNLP | A POS model for Swedish.  More info [here](https://medium.com/@klintcho/training-a-swedish-pos-tagger-for-stanford-corenlp-546e954a8ee7) | Andreas Klintberg | 1.0.0 | > Stanford CoreNLP 3.9.2 | [corenlp-swedish-1.0.0.jar](https://nlp.stanford.edu/software/corenlp-swedish-1.0.0.jar) | 
