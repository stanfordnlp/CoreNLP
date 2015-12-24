---
title: Download
keywords: download
permalink: '/download.html'
---

Stanford CoreNLP can be downloaded via the link below. This will download a large (536 MB) zip file containing (1) the CoreNLP code jar, (2) the CoreNLP models jar (required in your classpath for most tasks) (3) the libraries required to run CoreNLP, and (4) documentation / source code for the project.

<div style="text-align:center; margin-top: 2ex; margin-bottom:2ex;"> <a class="downloadbutton" href="http://nlp.stanford.edu/software/stanford-corenlp-full-2015-12-09.zip">Download CoreNLP 3.6.0</a> </div>

If you want to change the source code and recompile the files, see [these instructions](files/basic-compiling.txt).
Previous releases can be found [on the release history page](history.html).

**GitHub**: Here is the [Stanford CoreNLP GitHub site](https://github.com/stanfordnlp/CoreNLP).

**Maven**: You can find Stanford CoreNLP on [Maven Central](http://search.maven.org/#browse%7C11864822). The crucial thing to know is that CoreNLP needs its models to run (most parts beyond the tokenizer) and so you need to specify both the code jar and the models jar in your `pom.xml`, as follows:
(Note: Maven releases are made several days after the release on the website.)

``` xml
<dependencies>
<dependency>
    <groupId>edu.stanford.nlp</groupId>
    <artifactId>stanford-corenlp</artifactId>
    <version>3.5.2</version>
</dependency>
<dependency>
    <groupId>edu.stanford.nlp</groupId>
    <artifactId>stanford-corenlp</artifactId>
    <version>3.5.2</version>
    <classifier>models</classifier>
</dependency>
</dependencies>
```

NEW: If you want to get a language models jar off of Maven for Chinese, Spanish, or German, add this to your `pom.xml`:

``` xml
<dependency>
    <groupId>edu.stanford.nlp</groupId>
    <artifactId>stanford-corenlp</artifactId>
    <version>3.5.2</version>
    <classifier>models-chinese</classifier>
</dependency>
```

Replace "models-chinese" with "models-german" or "models-spanish" for the other two languages!


