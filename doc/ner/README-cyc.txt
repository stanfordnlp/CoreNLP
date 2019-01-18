Stanford NER - September 2006 - binary release
----------------------------------------------

This package provides a high-performance machine learning based named
entity recognition system, including facilities to train models from
supervised training data and pre-trained models for English.

(c) 2002-2006.  The Board of Trustees of The Leland
    Stanford Junior University. All Rights Reserved.

Original CRF code by Jenny Finkel.
Additional modules, features, internationalization, compaction, and
support code by Christopher Manning, Christopher Cox, Huy Nguyen and
Shipra Dingare.


LICENSE

Please see the file LICENCE.txt

For information contact:
    Christopher Manning
    Dept of Computer Science, Gates 1A
    Stanford CA 94305-9010
    USA
    manning@cs.stanford.edu


INCLUDED SERIALIZED MODELS / TRAINING DATA

The basic included serialized model is a 3 class NER tagger that can
label: PERSON, ORGANIZATION, and LOCATION entities.  It is included as
ner-eng-ie.crf-3-all2006.ser.gz and within the jar file.  It is trained
on data from CoNLL, MUC6, MUC7, and ACE.  Because this model is trained
on both US and UK newswire, it is fairly robust across the two domains.

We have also included ner-eng.8class.crf.gz, which was trained on the
same data, and can label PERSON, ORGANIZATION, LOCATION, MISC, DATE,
TIME, PERCENT, and MONEY.  This "MISC" class is as labeled in the CoNLL
2003 training data (q.v.).  It is most commonly nationality words like
"Irish" but includes various other miscellaneous things.

Lastly, we have also included muc.7class.crf.gz, which was trained on
MUC6 and MUC7 data and can label PERSON, ORGANIZATION, LOCATION, DATE,
TIME, PERCENT, and MONEY.

All of the serialized classifiers come in two versions, the second of
which uses a distributional similarity lexicon to improve performance
(by about 1.5% F-measure).  These classifiers have additional features
which make them perform substantially better, but they require rather
more memory.


QUICKSTART INSTRUCTIONS

This NER system requires Java 1.5 or later.   We have only tested it on
the SUN JVM.

Providing java is on your PATH, you should just be able to run an NER
GUI demonstration by just clicking.  It might work to double-click on
the stanford-ner.jar archive but this may well fail as the operating
system does not give Java enough memory for our NER system, so it is
safer to instead double click on the ner-gui.bat icon (Windows) or
ner-gui.sh (Linux/Unix/MacOSX).  Then, from the Classifier menu, either
load a CRF classifier from the classifiers directory of the distribution
or you should be able to use the Load Default CRF option.  You can then
either load a text file or web page from the File menu, or decide to use
the default text in the window.  Finally, you can now named entity tag
the text by pressing the Run NER button.

From a command line, you need to have java on your PATH and the
stanford-ner.jar file in your CLASSPATH.  (The way of doing this depends on
your OS/shell.)  The supplied ner.bat and ner.sh should work to allow
you to tag a single file.  For example, for Windows:

    ner file

Or on Unix/Linux you should be able to parse the test file in the distribution
directory with the command:

java -mx600m edu.stanford.nlp.ie.crf.CRFClassifier -loadClassifier classifiers/ner-eng.8class.better.crf.gz -textFile sample.txt

When run from a jar file, you also have the option of using a serialized
classifier contained in the jar file.  A default serialized classifier
(ner-eng-ie.crf-3-all2006.ser.gz) is in the jar file and can be used by
just saying:

java -mx300m -cp stanford-ner.jar edu.stanford.nlp.ie.crf.CRFClassifier -textFile sample.txt

If you use the -jar command, or double-click the jar file, NERGUI is
automatically started, and you will also be given the option (under the
'Classifier' menu item) to load a default supplied classifier:

java -mx300m -jar stanford-ner.jar


PROGRAMMATIC USE

The NERDemo file illustrates a couple of ways of calling the system
programatically.  You should get the same results from

java -mx300m NERDemo classifiers/ner-eng-ie.crf-3-all2006.ser.gz sample.txt

as from using CRFClassifier.  For more information on API calls, look in
the enclosed javadoc directory: load index.html in a browser and look
first at the edu.stanford.nlp.ie.crf package and CRFClassifier class.
If you wish to train your own NER systems, look also at the
edu.stanford.nlp.ie package NERFeatureFactory class.


SERVER VERSION

The NER code may also be run as a server listening on a socket:

java -mx1000m -cp stanford-ner.jar:lib/* edu.stanford.nlp.ie.NERServer 1234

You can specify which model to load with flags, either one on disk:

java -mx1000m -cp stanford-ner.jar:lib/* edu.stanford.nlp.ie.NERServer -loadClassifier classifiers/all.3class.crf.ser.gz 1234

Or if you have put a model inside the jar file, as a resource under, say, models:

java -mx1000m -cp stanford-ner.jar:lib/* edu.stanford.nlp.ie.NERServer -loadClassifier models/all.3class.crf.ser.gz 1234


RUNNING CLASSIFIERS FROM INSIDE A JAR FILE

The software can run any serialized classifier from within a jar file by
following the -loadClassifier flag by some resource available within a
jar file on the CLASSPATH.  An end user can make
their own jar files with the desired NER models contained inside.
This allows single jar file deployment.


PERFORMANCE GUIDELINES

Performance depends on many factors.  Speed and memory use depend on
hardware, operating system, and JVM.  Accuracy depends on the data
tested on.  Nevertheless, in the belief that something is better than
nothing, here are some statistics from one machine on one test set, in
semi-realistic conditions (where the test data is somewhat varied).

ner-eng-ie.crf-3-all2006.ser.gz
Memory: 100 MB
PERSON	ORGANIZATION	LOCATION
89.19	80.15		85.48

ner-eng-ie.crf-3-all2006-distsim.ser.gz
Memory: 320MB
PERSON	ORGANIZATION	LOCATION
91.88	82.91		88.21

ner-eng-ie.crf-7-muc.ser.gz
Memory: 120MB
PERSON  ORGANIZATION    LOCATION        DATE    TIME    MONEY   PERCENT
74.45   59.93		76.27		55.59   73.12   64.96	71.05

ner-eng-ie.crf-7-muc-distsim.ser.gz
Memory: 350MB
PERSON  ORGANIZATION    LOCATION        DATE    TIME    MONEY   PERCENT
84.09   65.20		83.13		56.68   72.19	66.01   70.82

ner-eng-ie.crf-8-all2006.ser.gz
Memory: 120MB
PERSON  ORGANIZATION    LOCATION        DATE    TIME    MONEY   PERCENT
89.15   79.93		85.07		47.43   69.79   59.10	29.87

ner-eng-ie.crf-8-all2006-distsim.ser.gz
Memory: 350MB
PERSON  ORGANIZATION    LOCATION        DATE    TIME    MONEY   PERCENT
92.11   82.55		87.65		47.95   69.59	58.82   31.17


Note that the 8 class classifier is as good at the basic 3 class
classifier at the basic 3 classes, but is not strong in performance on
the other classes.  The MUC classifier gives better performance on these
classes.  The MISC class was not present in the evaluation material.
