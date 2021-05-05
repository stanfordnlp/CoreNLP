Stanford Lexicalized Parser v4.2.1 - 2021-05-05
-----------------------------------------------

Copyright (c) 2002-2020 The Board of Trustees of The Leland Stanford Junior
University. All Rights Reserved.

Original core parser code by Dan Klein.  Support code, additional
modules, languages, features, internationalization, compaction, typed
dependencies, etc. by Christopher Manning, Roger Levy, Teg Grenager,
Galen Andrew, Marie-Catherine de Marneffe, Jenny Finkel, Spence Green,
Bill MacCartney, Anna Rafferty, Huihsin Tseng, Pi-Chuan Chang,
Wolfgang Maier, Richard Eckart, Richard Socher, John Bauer,
Sebastian Schuster, and Jon Gauthier.

This release was prepared by Jason Bolton.

This package contains 6 parsers: a high-accuracy unlexicalized PCFG; a
lexicalized dependency parser; a factored model, where the estimates
of dependencies and an unlexicalized PCFG are jointly optimized to
give a lexicalized PCFG treebank parser; a TreeRNN parser, where
recursive neural networks trained with semantic word vectors are used
to score parse trees; a Shift-Reduce Constituency Parser;
and a transition-based neural dependency parser.
Also included are grammars for various languages for use with these parsers.

For more information about the parser API, point a web browser at the
included javadoc directory (use the browser's Open File command to open
the index.html file inside the javadoc folder).  Start by looking at the
Package page for the edu.stanford.nlp.parser.lexparser package, and then
look at the page for the LexicalizedParser class documentation therein,
particularly documentation of the main method.

Secondly, you should also look at the Parser FAQ on the web:

    https://nlp.stanford.edu/software/parser-faq.html

This software requires Java 8 (JDK 1.8.0+).  (You must have installed it
separately. Check that the command "java -version" works and gives 1.8+.)


QUICKSTART

UNIX COMMAND-LINE USAGE

On a Unix system you should be able to parse the English test file with the
following command:

    ./lexparser.sh data/testsent.txt

This uses the PCFG parser, which is quick to load and run, and quite accurate.

[Notes: it takes a few seconds to load the parser data before parsing
begins; continued parsing is quicker. To use the lexicalized parser, replace
englishPCFG.ser.gz with englishFactored.ser.gz in the lexparser.sh script
and use the flag -mx600m to give more memory to java.]

WINDOWS GUI USAGE

On a Windows system, assuming that java is on your PATH, you should be able
to run a parsing GUI by double-clicking on the lexparser-gui.bat icon,
or giving the command lexparser-gui in this directory from a command prompt.

Click Load File, Browse, and navigate to and select testsent.txt in
the top directory of the parser distribution.  Click Load Parser,
Browse, and select the models jar, also in the top directory of the
parser distribution.  From the models jar, select englishPCFG.ser.gz.
Click Parse to parse the first sentence.

NEURAL NETWORK DEPENDENCY PARSER USAGE

To use the neural net dependency parser, issue the following command:

    java -Xmx2g -cp "*" edu.stanford.nlp.parser.nndep.DependencyParser \
    -model edu/stanford/nlp/models/parser/nndep/english_UD.gz \
    -textFile data/english-onesent.txt -outFile data/english-onesent.txt.out

The output will be written to data/english-onesent.txt.out

If you want to run on a language other than English, you will need to use
a language specific POS tagger.  Here is an example for Chinese:

    java -Xmx2g -cp "*" edu.stanford.nlp.parser.nndep.DependencyParser \
    -model edu/stanford/nlp/models/parser/nndep/UD_Chinese.gz \
    -tagger.model edu/stanford/nlp/models/pos-tagger/chinese-distsim.tagger \
    -textFile data/chinese-onesent-utf8.txt -outFile data/chinese-onesent-utf8.txt.out

OTHER USE CASES

The GUI is also available under Unix:

    lexparser-gui.sh

Under Mac OS X, you can double-click on lexparser-gui.command to invoke the
GUI.  The command-line version works on all platforms.	Use lexparser.bat
to run it under Windows.  The GUI is only for exploring the parser. It does
not allow you to save output.  You need to use the command-line program or
programmatic API to do serious work with the parser.

ADDITIONAL GRAMMARS

The parser is supplied with several trained grammars. There are English
grammars based on the standard LDC Penn Treebank WSJ training sections 2-21
(wsj*), and ones based on an augmented data set, better for questions,
commands, and recent English and biomedical text (english*).

All grammars are located in the included models jar. (If you'd like to have
grammar files like in older versions of the parser, you can get them by
extracting them from the jar file with the 'jar -xf' command.)

MULTILINGUAL PARSING
In addition to the English grammars, the parser comes with trained grammars
for Arabic, Chinese, French, and German. To parse with these grammars, run

    lexparser-lang.sh

with no arguments to see usage instructions. You can change language-specific
settings passed to the parser by modifying lexparser_lang.def.

You can also train and evaluate new grammars using:

    lexparser-lang-train-test.sh

To see how we trained the grammars supplied in this distribution, see

    bin/makeSerialized.csh

You will not be able to run this script (since it uses Stanford-specific file
paths), but you should be able to see what we did.

Arabic

Trained on parts 1-3 of the Penn Arabic Treebank (ATB) using the
pre-processing described in (Green and Manning, 2010). The default input
encoding is UTF-8 Arabic script. You can convert text in Buckwalter encoding to UTF-8
with the package edu.stanford.nlp.international.arabic.Buckwalter which is included
in stanford-parser.jar.

The parser *requires* segmentation and tokenization of raw text per the ATB standard
prior to parsing. You can generate this segmentation and tokenization with the Stanford
Word Segmenter, which is available separately at:

  https://nlp.stanford.edu/software/segmenter.html

Chinese

There are Chinese grammars trained just on mainland material from
Xinhua and more mixed material from the LDC Chinese Treebank. The default
input encoding is GB18030.

French

The standalone parser distribution comes with a neural dependency parser
model trained on the French-GSD data set (version 2.2). The standalone
parser distribution does not include functionality for producing UD 2.2
tokenization, so pre-tokenized text (text tokenized by whitespace) must
be provided when running the neural dependency parser, and the "-tokenized"
flag must be used. 

Example command:

    java -Xmx2g -cp "*" edu.stanford.nlp.parser.nndep.DependencyParser \
    -model edu/stanford/nlp/models/parser/nndep/UD_French.gz \
    -tagger.model edu/stanford/nlp/models/pos-tagger/french-ud.tagger \
    -tokenized -textFile example.txt -outFile example.txt.out

Note that "example.txt" should contain UD 2.2 tokens, separated by whitespace.

The only provided French constituency parser is a shift-reduce parser. At this
time running the shift-reduce parser on French text requires running a pipeline 
with the full Stanford CoreNLP package.

To use the shift-reduce constituency parser on text and the UD 2.2 tokenization, 
upgrade to the full Stanford CoreNLP package and run a French pipeline.

German

The constituency parser was trained on the Negra corpus. Details are included in 
(Rafferty and Manning, 2008). This parser expects UD 2.2 tokenization. Input
text files must be UD 2.2 tokens separated by whitespace. The "-tokenized" flag
must be used.

The neural dependency parser was trained on the German-GSD data set (version 2.2).
The standalone parser distribution does not include functionality for producing 
UD 2.2 tokenization, so pre-tokenized text (text tokenized by whitespace) must
be provided when running the neural dependency parser, and the "-tokenized"
flag must be used. 

Example command:

    java -Xmx2g -cp "*" edu.stanford.nlp.parser.nndep.DependencyParser \
    -model edu/stanford/nlp/models/parser/nndep/UD_German.gz \
    -tagger.model edu/stanford/nlp/models/pos-tagger/german-ud.tagger \
    -tokenized -textFile example.txt -outFile example.txt.out

German shift reduce parsers are available, but running them on input German text
requires the use of a full Stanford CoreNLP pipeline. The lower accuracy lexicalized
parsers have options for running on input text.

To use the shift-reduce constituency parser on text and the UD 2.2 tokenization, upgrade
to the full Stanford CoreNLP package and run a German pipeline.

Spanish

The constituency parser was trained on the Spanish AnCora treebank and
LDC provided discussion forum and newswire treebanks. This parser expects UD 2.0
tokenization. 

The neural dependency parser was trained on the Spanish AnCora data set (version 2.0).
The standalone parser distribution does not include functionality for producing
UD 2.0 tokenization, so pre-tokenized text (text tokenized by whitespace) must
be provided when running the neural dependency parser, and the "-tokenized"
flag must be used.

Example command:

    java -Xmx2g -cp "*" edu.stanford.nlp.parser.nndep.DependencyParser \
    -model edu/stanford/nlp/models/parser/nndep/UD_Spanish.gz \
    -tagger.model edu/stanford/nlp/models/pos-tagger/spanish-ud.tagger \
    -tokenized -textFile example.txt -outFile example.txt.out

Spanish shift reduce parsers are available, but running them on input Spanish text
requires the use of a full Stanford CoreNLP pipeline. The lower accuracy lexicalized
parsers have options for running on input text.

To use the shift-reduce constituency parser on text and the UD 2.2 tokenization, upgrade
to the full Stanford CoreNLP package and run a Spanish pipeline.

TREEBANK PREPROCESSING

The pre-processed versions of the ATB described
in (Green and Manning, 2010) and the FTB described in (Green et al.,
2011) can be reproduced using the TreebankPreprocessor included in this
release. The configuration files are located in /conf. For example,
to create the ATB data, run:

    bin/run-tb-preproc -v conf/atb-latest.conf

Note that you'll need to update the conf file paths to your local treebank
distributions as the data is not distributed with the parser. You'll
also need to set the classpath in the cmd_line variable of run-tb-preproc.

The TreebankPreprocessor conf files support various options, which are
documented in

    edu.stanford.nlp.international.process.ConfigParser

EVALUATION METRICS

The Stanford parser comes with Java implementations of the following
evaluation metrics:

    Dependency Labeled Attachment

    Evalb         (Collins, 1997)
      -Includes per-category evaluation with the -c option

    Leaf Ancestor (Sampson and Babarczy, 2003)
      -Both micro- and macro-averaged score

    Tagging Accuracy

See the usage instructions and javadocs in the requisite classes located in
edu.stanford.nlp.parser.metrics.

UNIVERSAL DEPENDENCIES vs. STANFORD DEPENDENCIES

Since v3.5.2 the default dependency representation is the new Universal Dependencies
representation. Universal Dependencies were developed with the goal of being a
cross-linguistically valid representation. Note that some constructs such as prepositional
phrases are now analyzed differently and that the set of relations was updated. Please
look at the Universal Dependencies documentation for more information:

      http://www.universaldependencies.org

The parser also still supports the original Stanford Dependencies representation
as described in the StanfordDependenciesManual.pdf. Use the flag

     -originalDependencies

to obtain original Stanford Dependencies.

LICENSE

// StanfordLexicalizedParser -- a probabilistic lexicalized NL CFG parser
// Copyright (c) 2002-2020 The Board of Trustees of
// The Leland Stanford Junior University. All Rights Reserved.
//
// This program is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License
// as published by the Free Software Foundation; either version 2
// of the License, or (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see http://www.gnu.org/licenses/ .
//
// For more information, bug reports, fixes, contact:
//    Christopher Manning
//    Dept of Computer Science, Gates 2A
//    Stanford CA 94305-9020
//    USA
//    parser-support@lists.stanford.edu
//    https://nlp.stanford.edu/downloads/lex-parser.html


---------------------------------
CHANGES
---------------------------------

2021-05-05    4.2.1     Reduce size of srparser models

2020-11-17    4.2.0     Retrain English models with treebank fixes 

2020-05-22    4.0.0     Model tokenization updated to UDv2.0 

2018-10-16    3.9.2     Update for compatibility 

2018-02-27    3.9.1     new French and Spanish UD models, misc. UD 
                        enhancements, bug fixes 

2017-06-09    3.8.0     Updated for compatibility

2016-10-31    3.7.0     new UD models

2015-12-09    3.6.0     Updated for compatibility

2015-04-20    3.5.2     Switch to universal dependencies

2015-01-29    3.5.1     Dependency parser improvements; general
                        bugfixes

2014-10-26    3.5.0     Upgrade to Java 1.8; add neural-network
                        dependency parser

2014-08-27    3.4.1     Add Spanish models

2014-06-16      3.4     Shift-reduce parser

2014-01-04    3.3.1     Bugfix release, dependency improvements

2013-11-12    3.3.0     Remove the attr dependency, add imperatives to
                        English training data

2013-06-19    3.2.0     New RNN model for WSJ and English with
                        improved test set accuracy, rel dependency
                        removed

2013-04-05    2.0.5     Dependency improvements, ctb7 model, -nthreads
                        option

2012-11-12    2.0.4     Dependency speed improvements; other
                        dependency changes

2012-07-09    2.0.3     Minor bug fixes

2012-05-22    2.0.2     Supports adding extra data in non-tree format

2012-03-09    2.0.1     Caseless English model added, ready for maven

2012-01-11    2.0.0     Threadsafe!

2011-09-14    1.6.9     Added some imperatives to the English
                        training data; added root dependency.

2011-06-15    1.6.8     Added French parser and leaf ancestor
                        evaluation metric; reorganized distribution;
                        new data preparation scripts; rebuilt grammar
                        models; other bug fixes

2011-05-15    1.6.7     Minor bug fixes

2011-04-17    1.6.6     Compatible with tagger, corenlp and tregex.

2010-10-30    1.6.5     Further improvements to English Stanford
                        Dependencies and other minor changes

2010-08-16    1.6.4     More minor bug fixes and improvements to English
                        Stanford Dependencies and question parsing

2010-07-09    1.6.3     Improvements to English Stanford Dependencies and
                        question parsing, minor bug fixes

2010-02-25    1.6.2     Improvements to Arabic parser models,
                        and to English and Chinese Stanford Dependencies

2008-10-19    1.6.1     Slightly improved Arabic, German and
                        Stanford Dependencies

2007-08-18      1.6     Added Arabic, k-best PCCFG parsing;
                        improved English grammatical relations

2006-05-30    1.5.1     Improved English and Chinese grammatical relations;
                        fixed UTF-8 handling

2005-07-20      1.5     Added grammatical relations output;
                        fixed bugs introduced in 1.4

2004-03-24      1.4     Made PCFG faster again (by FSA minimization);
                        added German support

2003-09-06      1.3     Made parser over twice as fast;
                        added tokenization options

2003-07-20      1.2     Halved PCFG memory usage;
                        added support for Chinese

2003-03-25      1.1     Improved parsing speed; included GUI,
                        improved PCFG grammar

2002-12-05      1.0     Initial release
