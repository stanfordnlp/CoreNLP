---
layout: page
title: Download
keywords: download
permalink: '/download.html'
nav_order: 1
parent: Usage
---

## Getting a copy

Stanford CoreNLP can be downloaded via the link below. This will download a large (482 MB) zip file containing (1) the CoreNLP code jar, (2) the CoreNLP models jar (required in your classpath for most tasks) (3) the libraries required to run CoreNLP, and (4) documentation / source code for the project. This is everything for getting going on English!  Unzip this file, open the folder that results and you're ready to use it.

[<i class="fab fa-java"></i> Download CoreNLP 4.5.8](https://nlp.stanford.edu/software/stanford-corenlp-4.5.8.zip){: .btn .fs-5 .mr-2 .mb-md-0 }
[CoreNLP on 🤗](https://huggingface.co/stanfordnlp/CoreNLP/tree/main){: .btn .fs-5 .mr-2 .mb-md-0 }

**Other languages:** For working with another (human) language, you need additional model files. We have model files for several other languages. And we have more
model files for English, including for dealing with uncased English (that is, English which is not conventionally capitalized, whether texting or telegrams).
You can find the latest models in the table below.  Versions for earlier releases are available on [the release history page](history.html).

| Language | Model Jar | Version |
| :------- | :-------- | | :----- |
| Arabic | [download](https://search.maven.org/remotecontent?filepath=edu/stanford/nlp/stanford-corenlp/4.4.0/stanford-corenlp-4.4.0-models-arabic.jar) [(mirror)](https://nlp.stanford.edu/software/stanford-corenlp-4.5.8-models-arabic.jar) [(HF Hub)](https://huggingface.co/stanfordnlp/corenlp-arabic/tree/v4.5.8) | 4.5.8 |
| Chinese | [download](https://search.maven.org/remotecontent?filepath=edu/stanford/nlp/stanford-corenlp/4.4.0/stanford-corenlp-4.4.0-models-chinese.jar) [(mirror)](https://nlp.stanford.edu/software/stanford-corenlp-4.5.8-models-chinese.jar) [(HF Hub)](https://huggingface.co/stanfordnlp/corenlp-chinese/tree/v4.5.8) | 4.5.8 |
| English (extra) | [download](https://search.maven.org/remotecontent?filepath=edu/stanford/nlp/stanford-corenlp/4.4.0/stanford-corenlp-4.4.0-models-english.jar) [(mirror)](https://nlp.stanford.edu/software/stanford-corenlp-4.5.8-models-english.jar) [(HF Hub)](https://huggingface.co/stanfordnlp/corenlp-english-extra/tree/v4.5.8) | 4.5.8 |
| English (KBP) | [download](https://search.maven.org/remotecontent?filepath=edu/stanford/nlp/stanford-corenlp/4.4.0/stanford-corenlp-4.4.0-models-english-kbp.jar) [(mirror)](https://nlp.stanford.edu/software/stanford-corenlp-4.5.8-models-english-kbp.jar) [(HF Hub)](https://huggingface.co/stanfordnlp/corenlp-english-kbp/tree/v4.5.8) | 4.5.8 |
| French | [download](https://search.maven.org/remotecontent?filepath=edu/stanford/nlp/stanford-corenlp/4.4.0/stanford-corenlp-4.4.0-models-french.jar) [(mirror)](https://nlp.stanford.edu/software/stanford-corenlp-4.5.8-models-french.jar) [(HF Hub)](https://huggingface.co/stanfordnlp/corenlp-french/tree/v4.5.8) | 4.5.8 |
| German | [download](https://search.maven.org/remotecontent?filepath=edu/stanford/nlp/stanford-corenlp/4.4.0/stanford-corenlp-4.4.0-models-german.jar) [(mirror)](https://nlp.stanford.edu/software/stanford-corenlp-4.5.8-models-german.jar) [(HF Hub)](https://huggingface.co/stanfordnlp/corenlp-german/tree/v4.5.8) | 4.5.8 |
| Hungarian | [download](https://search.maven.org/remotecontent?filepath=edu/stanford/nlp/stanford-corenlp/4.4.0/stanford-corenlp-4.4.0-models-hungarian.jar) [(mirror)](https://nlp.stanford.edu/software/stanford-corenlp-4.5.8-models-hungarian.jar) [(HF Hub)](https://huggingface.co/stanfordnlp/corenlp-hungarian/tree/v4.5.8) | 4.5.8 |
| Italian | [download](https://search.maven.org/remotecontent?filepath=edu/stanford/nlp/stanford-corenlp/4.4.0/stanford-corenlp-4.4.0-models-italian.jar) [(mirror)](https://nlp.stanford.edu/software/stanford-corenlp-4.5.8-models-italian.jar) [(HF Hub)](https://huggingface.co/stanfordnlp/corenlp-italian/tree/v4.5.8) | 4.5.8 |
| Spanish | [download](https://search.maven.org/remotecontent?filepath=edu/stanford/nlp/stanford-corenlp/4.4.0/stanford-corenlp-4.4.0-models-spanish.jar) [(mirror)](https://nlp.stanford.edu/software/stanford-corenlp-4.5.8-models-spanish.jar) [(HF Hub)](https://huggingface.co/stanfordnlp/corenlp-spanish/tree/v4.5.8) | 4.5.8 |


If you want to change the source code and recompile the files, see [these instructions](files/basic-compiling.txt).
Previous releases can be found on [the release history page](history.html).


**GitHub**: Here is the [Stanford CoreNLP GitHub site](https://github.com/stanfordnlp/CoreNLP).

**Maven**: You can find Stanford CoreNLP on
[Maven Central](http://search.maven.org/#browse%7C11864822). The
crucial thing to know is that CoreNLP needs its models to run (most
parts beyond the tokenizer and sentence splitter) and so you need to specify both the code jar and the models jar in your `pom.xml`, as follows:
(Note: Maven releases are usually made several days after a release on the website.)

``` xml
<dependencies>
<dependency>
    <groupId>edu.stanford.nlp</groupId>
    <artifactId>stanford-corenlp</artifactId>
    <version>4.4.0</version>
</dependency>
<dependency>
    <groupId>edu.stanford.nlp</groupId>
    <artifactId>stanford-corenlp</artifactId>
    <version>4.4.0</version>
    <classifier>models</classifier>
</dependency>
</dependencies>
```

If you want to get a language models jar off of Maven for Arabic,
Chinese, German, or Spanish, also add this inside `dependencies` to your `pom.xml`:

``` xml
<dependency>
    <groupId>edu.stanford.nlp</groupId>
    <artifactId>stanford-corenlp</artifactId>
    <version>4.4.0</version>
    <classifier>models-chinese</classifier>
</dependency>
```

Replace "models-chinese" with one or more of "models-english", "models-english-kbp", "models-arabic", "models-french", "models-german" or "models-spanish" for resources for other languages!

There is an example Maven project using Stanford CoreNLP available with the GitHub release.

It can be found in the `examples/sample-maven-project` directory.

You can build the project with this command:

```
mvn compile
```

And you can run a demo with a command like this:

```
export MAVEN_OPTS="-Xmx14000m"
mvn exec:java -Dexec.mainClass="edu.stanford.nlp.StanfordCoreNLPEnglishTestApp"
```


## Steps to setup from the official release

This example goes over how to set up CoreNLP from the latest official release. This example will take you through downloading the package, and running a simple command-line invocation of CoreNLP.

### Prerequisites

* Java 8. The command `java -version` should complete successfully with a line like: java version "1.8.0_92".
* Zip tool
* For following exactly the steps below: bash or a similar shell, and
  wget or a similar downloader.

### Steps

1. Download the CoreNLP zip file at: http://stanfordnlp.github.io/CoreNLP/index.html#download:
```
wget http://nlp.stanford.edu/software/stanford-corenlp-latest.zip
```
Or using curl (what you get by default on macOS):
```
curl -O -L http://nlp.stanford.edu/software/stanford-corenlp-latest.zip
```
1. Unzip the release:
```
unzip stanford-corenlp-latest.zip
```
1. Enter the newly unzipped directory:
```
cd stanford-corenlp-4.5.8
```
1. Set up your classpath. If you're using an IDE, you should set the
   classpath in your IDE. If you are using bash or a bash-like shell,
   the following will work.
   ```
   for file in `find . -name "*.jar"`; do export
   CLASSPATH="$CLASSPATH:`realpath $file`"; done
   ```
If you'll be using CoreNLP frequently, the below line is a useful line to have in your `~/.bashrc` (or equivalent) file, replacing the directory `/path/to/corenlp/` with the appropriate path to where you unzipped CoreNLP:
```
for file in `find /path/to/corenlp/ -name "*.jar"`; do export
CLASSPATH="$CLASSPATH:`realpath $file`"; done
```
1. Try it out! For example, the following will make a simple text file
to annotate, and run CoreNLP over this file. The output will be saved
to `input.txt.out` as a JSON file. Note that running all the CoreNLP
annotators in the default annotation pipeline requires quite a bit of memory. You should give it at least 3GB (`-mx3g`) in most cases.
```
echo "the quick brown fox jumped over the lazy dog" > input.txt
java -mx3g edu.stanford.nlp.pipeline.StanfordCoreNLP -outputFormat json -file input.txt
```

## Steps to setup from the GitHub HEAD version

### Prerequisites

* Java 8. The command `java -version` should complete successfully with a line like: java version "1.8.0_92".
* [Apache Ant](http://ant.apache.org/)
* Zip tool
* For following exactly the steps below: bash or a similar shell, and
  wget or a similar downloader.

### Steps

1. Clone the CoreNLP Git repository:
```
git clone git@github.com:stanfordnlp/CoreNLP.git
```
1. Enter the CoreNLP directory:
```
cd CoreNLP
```
1. Build the project into a self-contained jar file. The easiest way to do this is with:
```
ant jar
```
1. Download the latest models.
```
wget http://nlp.stanford.edu/software/stanford-corenlp-models-current.jar
```
Or using curl (what you get by default on macOS):
```
curl -O -L http://nlp.stanford.edu/software/stanford-corenlp-models-current.jar
```
1. Set up your classpath. If you're using an IDE, you should set the
   classpath in your IDE. For bash or a bash-like shell, the following
   should work.
```
export CLASSPATH="$CLASSPATH:javanlp-core.jar:stanford-corenlp-models-current.jar";
for file in `find lib -name "*.jar"`; do export CLASSPATH="$CLASSPATH:`realpath $file`"; done
```
If you'll be using CoreNLP frequently, the below lines are useful to have in your `~/.bashrc` (or equivalent) file, replacing the directory `/path/to/corenlp/` with the appropriate path to where you unzipped CoreNLP (3 replacements):
```
export CLASSPATH="$CLASSPATH:/path/to/corenlp/javanlp-core.jar:/path/to/corenlp/stanford-corenlp-models-current.jar";
for file in `find /path/to/corenlp/lib -name "*.jar"`; do export CLASSPATH="$CLASSPATH:`realpath $file`"; done
```
1. Try it out! For example, the following will make a simple text file to annotate, and run CoreNLP over this file. The output will be saved to `input.txt.out` as a JSON file. Note that running all the CoreNLP
annotators in the default annotation pipeline requires quite a bit of memory. You should give it at least 3GB (`-mx3g`) in most cases.
```
echo "the quick brown fox jumped over the lazy dog" > input.txt
java -mx3g edu.stanford.nlp.pipeline.StanfordCoreNLP -outputFormat json -file input.txt
```

