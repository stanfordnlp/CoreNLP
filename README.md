Stanford CoreNLP
================

Stanford CoreNLP provides a set of natural language analysis tools written in Java. It can take raw human language text input and give the base forms of words, their parts of speech, whether they are names of companies, people, etc., normalize and interpret dates, times, and numeric quantities, mark up the structure of sentences in terms of phrases or word dependencies, and indicate which noun phrases refer to the same entities. It was originally developed for English, but now also provides varying levels of support for (Modern Standard) Arabic, (mainland) Chinese, French, German, and Spanish. Stanford CoreNLP is an integrated framework, which make it very easy to apply a bunch of language analysis tools to a piece of text. Starting from plain text, you can run all the tools with just two lines of code. Its analyses provide the foundational building blocks for higher-level and domain-specific text understanding applications. Stanford CoreNLP is a set of stable and well-tested natural language processing tools, widely used by various groups in academia, industry, and government. The tools variously use rule-based, probabilistic machine learning, and deep learning components.

The Stanford CoreNLP code is written in Java and licensed under the GNU General Public License (v3 or later). Note that this is the full GPL, which allows many free uses, but not its use in proprietary software that you distribute to others.

#### How To Compile (with ant)

1. cd CoreNLP ; ant

#### How To Create A Jar 

1. compile the code
2. cd CoreNLP/classes ; jar -cf ../stanford-corenlp.jar edu

You can find releases of Stanford CoreNLP on [Maven Central](http://search.maven.org/#browse%7C11864822).

You can find more explanation and documentation on [the Stanford CoreNLP homepage](http://nlp.stanford.edu/software/corenlp.shtml#Demo).

The most recent models associated with the code in the HEAD of this repository can be found [here](http://nlp.stanford.edu/software/stanford-corenlp-models-current.jar).

Some of the larger (English) models -- like the shift-reduce parser and WikiDict -- are not distributed with our default models jar. 
The most recent version of these models can be found [here](http://nlp.stanford.edu/software/stanford-english-corenlp-models-current.jar).

We distribute models for other languages as well, including [Arabic](http://nlp.stanford.edu/software/stanford-arabic-corenlp-models-current.jar),
[Chinese](http://nlp.stanford.edu/software/stanford-chinese-corenlp-models-current.jar), [German]((http://nlp.stanford.edu/software/stanford-german-corenlp-models-current.jar),
and [Spanish](stanford-spanish-corenlp-models-current.jar)

For information about making contributions to Stanford CoreNLP, see the file [CONTRIBUTING.md](CONTRIBUTING.md).

Questions about CoreNLP can either be posted on StackOverflow with the tag [stanford-nlp](http://stackoverflow.com/questions/tagged/stanford-nlp), 
  or on the [mailing lists](http://nlp.stanford.edu/software/corenlp.shtml#Mail).
