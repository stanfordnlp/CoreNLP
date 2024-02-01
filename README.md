# Stanford CoreNLP

[![Run Tests](https://github.com/stanfordnlp/CoreNLP/actions/workflows/run-tests.yaml/badge.svg)](https://github.com/stanfordnlp/CoreNLP/actions/workflows/run-tests.yaml)
[![Maven Central](https://img.shields.io/maven-central/v/edu.stanford.nlp/stanford-corenlp.svg)](https://mvnrepository.com/artifact/edu.stanford.nlp/stanford-corenlp)
[![Twitter](https://img.shields.io/twitter/follow/stanfordnlp.svg?style=social&label=Follow)](https://twitter.com/stanfordnlp/)

[Stanford CoreNLP](http://stanfordnlp.github.io/CoreNLP/) provides a set of natural language analysis tools written in Java. It can take raw human language text input and give the base forms of words, their parts of speech, whether they are names of companies, people, etc., normalize and interpret dates, times, and numeric quantities, mark up the structure of sentences in terms of syntactic phrases or dependencies, and indicate which noun phrases refer to the same entities. It was originally developed for English, but now also provides varying levels of support for (Modern Standard) Arabic, (mainland) Chinese, French, German, Hungarian, Italian, and Spanish. Stanford CoreNLP is an integrated framework, which makes it very easy to apply a bunch of language analysis tools to a piece of text. Starting from plain text, you can run all the tools with just two lines of code. Its analyses provide the foundational building blocks for higher-level and domain-specific text understanding applications. Stanford CoreNLP is a set of stable and well-tested natural language processing tools, widely used by various groups in academia, industry, and government. The tools variously use rule-based, probabilistic machine learning, and deep learning components.

The Stanford CoreNLP code is written in Java and licensed under the GNU General Public License (v2 or later). Note that this is the full GPL, which allows many free uses, but not its use in proprietary software that you distribute to others.

### Build Instructions

Several times a year we distribute a new version of the software, which corresponds to a stable commit.

During the time between releases, one can always use the latest, under development version of our code.

Here are some helpful instructions to use the latest code:

#### Provided build

Sometimes we will provide updated jars here which have the latest version of the code.

At present, [the current released version of the code](https://stanfordnlp.github.io/CoreNLP/#download) is our most recent released jar, though you can always build the very latest from GitHub HEAD yourself.

<!---
[stanford-corenlp.jar (last built: 2017-04-14)](http://nlp.stanford.edu/software/stanford-corenlp-2017-04-14-build.jar)
-->

#### Build with Ant

1. Make sure you have Ant installed, details here: [http://ant.apache.org/](http://ant.apache.org/)
2. Compile the code with this command: `cd CoreNLP ; ant`
3. Then run this command to build a jar with the latest version of the code: `cd CoreNLP/classes ; jar -cf ../stanford-corenlp.jar edu`
4. This will create a new jar called stanford-corenlp.jar in the CoreNLP folder which contains the latest code
5. The dependencies that work with the latest code are in CoreNLP/lib and CoreNLP/liblocal, so make sure to include those in your CLASSPATH.
6. When using the latest version of the code make sure to download the latest versions of the [corenlp-models](http://nlp.stanford.edu/software/stanford-corenlp-models-current.jar), [english-models](http://nlp.stanford.edu/software/stanford-english-corenlp-models-current.jar), and [english-models-kbp](http://nlp.stanford.edu/software/stanford-english-kbp-corenlp-models-current.jar) and include them in your CLASSPATH.  If you are processing languages other than English, make sure to download the latest version of the models jar for the language you are interested in.

#### Build with Maven

1. Make sure you have Maven installed, details here: [https://maven.apache.org/](https://maven.apache.org/)
2. If you run this command in the CoreNLP directory: `mvn package` , it should run the tests and build this jar file: `CoreNLP/target/stanford-corenlp-4.5.4.jar`
3. When using the latest version of the code make sure to download the latest versions of the [corenlp-models](http://nlp.stanford.edu/software/stanford-corenlp-models-current.jar), [english-extra-models](http://nlp.stanford.edu/software/stanford-english-extra-corenlp-models-current.jar), and [english-kbp-models](http://nlp.stanford.edu/software/stanford-english-kbp-corenlp-models-current.jar) and include them in your CLASSPATH.  If you are processing languages other than English, make sure to download the latest version of the models jar for the language you are interested in.  
4. If you want to use Stanford CoreNLP as part of a Maven project you need to install the models jars into your Maven repository.  Below is a sample command for installing the Spanish models jar.  For other languages just change the language name in the command.  To install `stanford-corenlp-models-current.jar` you will need to set `-Dclassifier=models`.  Here is the sample command for Spanish: `mvn install:install-file -Dfile=/location/of/stanford-spanish-corenlp-models-current.jar -DgroupId=edu.stanford.nlp -DartifactId=stanford-corenlp -Dversion=4.5.4 -Dclassifier=models-spanish -Dpackaging=jar`

#### Models

The models jars that correspond to the latest code can be found in the table below.

Some of the larger (English) models -- like the shift-reduce parser and WikiDict -- are not distributed with our default models jar.
These require downloading the English (extra) and English (kbp) jars. Resources for other languages require usage of the corresponding
models jar.

The best way to get the models is to use git-lfs and clone them from Hugging Face Hub.

For instance, to get the French models, run the following commands:

```
# Make sure you have git-lfs installed
# (https://git-lfs.github.com/)
git lfs install

git clone https://huggingface.co/stanfordnlp/corenlp-french
```

The jars can be directly downloaded from the links below or the Hugging Face Hub page as well. 

| Language | Model Jar | Last Updated |
| --- | --- | --- |
| Arabic  | [download](https://nlp.stanford.edu/software/stanford-arabic-corenlp-models-current.jar) [(HF Hub)](https://huggingface.co/stanfordnlp/corenlp-arabic/tree/main) | 4.5.6 |
| Chinese | [download](https://nlp.stanford.edu/software/stanford-chinese-corenlp-models-current.jar) [(HF Hub)](https://huggingface.co/stanfordnlp/corenlp-chinese/tree/main)| 4.5.6 |
| English (extra) | [download](https://nlp.stanford.edu/software/stanford-english-extra-corenlp-models-current.jar) [(HF Hub)](https://huggingface.co/stanfordnlp/corenlp-english-extra/tree/main) | 4.5.6 |
| English (KBP) | [download](https://nlp.stanford.edu/software/stanford-english-kbp-corenlp-models-current.jar) [(HF Hub)](https://huggingface.co/stanfordnlp/corenlp-english-kbp/tree/main) | 4.5.6 |
| French | [download](https://nlp.stanford.edu/software/stanford-french-corenlp-models-current.jar) [(HF Hub)](https://huggingface.co/stanfordnlp/corenlp-french/tree/main) | 4.5.6 |
| German | [download](https://nlp.stanford.edu/software/stanford-german-corenlp-models-current.jar) [(HF Hub)](https://huggingface.co/stanfordnlp/corenlp-german/tree/main) | 4.5.6 |
| Hungarian | [download](https://nlp.stanford.edu/software/stanford-hungarian-corenlp-models-current.jar) [(HF Hub)](https://huggingface.co/stanfordnlp/corenlp-hungarian/tree/main) | 4.5.6 |
| Italian | [download](https://nlp.stanford.edu/software/stanford-italian-corenlp-models-current.jar) [(HF Hub)](https://huggingface.co/stanfordnlp/corenlp-italian/tree/main)| 4.5.6 |
| Spanish | [download](https://nlp.stanford.edu/software/stanford-spanish-corenlp-models-current.jar) [(HF Hub)](https://huggingface.co/stanfordnlp/corenlp-spanish/tree/main)| 4.5.6 |

Thank you to [Hugging Face](https://huggingface.co/) for helping with our hosting!

### Install by Gradle

If you don't know Gradle itself, see official site: https://gradle.org

Write the following in your build.gradle according to [Maven Central](https://search.maven.org/artifact/edu.stanford.nlp/stanford-corenlp/4.5.5/jar):

```Gradle
dependencies {
    implementation 'edu.stanford.nlp:stanford-corenlp:4.5.5'
}
```

If you want to analyse English, add following:

```Gradle
    implementation "edu.stanford.nlp:stanford-corenlp:4.5.5:models"
    implementation "edu.stanford.nlp:stanford-corenlp:4.5.5:models-english"
    implementation "edu.stanford.nlp:stanford-corenlp:4.5.5:models-english-kbp"
```

If you use another version, replace "4.5.5" to a version you use.

### Useful resources

You can find releases of Stanford CoreNLP on [Maven Central](https://search.maven.org/artifact/edu.stanford.nlp/stanford-corenlp/4.5.4/jar).

You can find more explanation and documentation on [the Stanford CoreNLP homepage](http://stanfordnlp.github.io/CoreNLP/).

For information about making contributions to Stanford CoreNLP, see the file [CONTRIBUTING.md](CONTRIBUTING.md).

Questions about CoreNLP can either be posted on StackOverflow with the tag [stanford-nlp](http://stackoverflow.com/questions/tagged/stanford-nlp),
  or on the [mailing lists](https://nlp.stanford.edu/software/#Mail).
