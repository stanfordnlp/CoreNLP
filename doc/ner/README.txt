Stanford NER - v3.2.0 - 2013-06-19
----------------------------------------------

This package provides a high-performance machine learning based named
entity recognition system, including facilities to train models from
supervised training data and pre-trained models for English.

(c) 2002-2012.  The Board of Trustees of The Leland
    Stanford Junior University. All Rights Reserved. 

Original CRF code by Jenny Finkel.
Additional modules, features, internationalization, compaction, and
support code by Christopher Manning, Dan Klein, Christopher Cox, Huy Nguyen
Shipra Dingare, Anna Rafferty, and John Bauer.
This release prepared by John Bauer.

LICENSE 

The software is licensed under the full GPL.  Please see the file LICENCE.txt

For more information, bug reports, and fixes, contact:
    Christopher Manning
    Dept of Computer Science, Gates 1A
    Stanford CA 94305-9010
    USA
    java-nlp-support@lists.stanford.edu
    http://www-nlp.stanford.edu/software/CRF-NER.shtml

CONTACT

For questions about this distribution, please contact Stanford's JavaNLP group
at java-nlp-support@lists.stanford.edu.  We provide assistance on a best-effort
basis.

TUTORIAL

Quickstart guidelines, primarily for end users who wish to use the included NER
models, are below.  For further instructions on training your own NER model,
go to http://www-nlp.stanford.edu/software/crf-faq.shtml.

INCLUDED SERIALIZED MODELS / TRAINING DATA

The basic included serialized model is a 3 class NER tagger that can
label: PERSON, ORGANIZATION, and LOCATION entities.  It is included as
english.all.3class.distsim.crf.ser.gz.  It is trained on data from
CoNLL, MUC6, MUC7, and ACE.  Because this model is trained on both US
and UK newswire, it is fairly robust across the two domains.

We have also included a 4 class NER tagger trained on the CoNLL 2003
Shared Task training data that labels for PERSON, ORGANIZATION,
LOCATION, and MISC.  It is named
english.conll.4class.caseless.distsim.crf.ser.gz .

A third model is trained only on data from MUC and distinguishes
between 7 different classes,
english.muc.7class.caseless.distsim.crf.ser.gz.

All of the serialized classifiers come in two versions, the second of
which uses a distributional similarity lexicon to improve performance
(by about 1.5% F-measure).  These classifiers have additional features
which make them perform substantially better, but they require rather
more memory.  The distsim models are included in the release package,
and nodistsim versions of the same models are available on the
Stanford NER webpage.

There are also case-insensitive versions of the three models available
on the webpage.

Finally, a package with two German models is also available for download.


QUICKSTART INSTRUCTIONS

This NER system requires Java 1.6 or later.   We have only tested it on
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

java -mx600m edu.stanford.nlp.ie.crf.CRFClassifier -loadClassifier classifiers/all.3class.crf.ser.gz -textFile sample.txt

When run from a jar file, you also have the option of using a serialized
classifier contained in the jar file.

If you use the -jar command, or double-click the jar file, NERGUI is
automatically started, and you will also be given the option (under the
'Classifier' menu item) to load a default supplied classifier:

java -mx1000m -jar stanford-ner.jar


PROGRAMMATIC USE

The NERDemo file illustrates a couple of ways of calling the system
programatically.  You should get the same results from

java -mx300m NERDemo classifiers/all.3class.crf.ser.gz sample.txt

as from using CRFClassifier.  For more information on API calls, look in
the enclosed javadoc directory: load index.html in a browser and look
first at the edu.stanford.nlp.ie.crf package and CRFClassifier class.
If you wish to train your own NER systems, look also at the
edu.stanford.nlp.ie package NERFeatureFactory class. 


SERVER VERSION

The NER code may also be run as a server listening on a socket:

java -mx1000m -cp stanford-ner.jar edu.stanford.nlp.ie.NERServer 1234

You can specify which model to load with flags, either one on disk:

java -mx1000m -cp stanford-ner.jar edu.stanford.nlp.ie.NERServer -loadClassifier classifiers/all.3class.crf.ser.gz 1234

Or if you have put a model inside the jar file:

java -mx1000m -cp stanford-ner.jar edu.stanford.nlp.ie.NERServer -loadJarClassifier all.3class.crf.ser.gz 1234


RUNNING CLASSIFIERS FROM INSIDE A JAR FILE

The software can run any serialized classifier from within a jar file by
giving the flag -loadJarClassifier resourceName .  An end user can make
their own jar files with the desired NER models contained inside.  The
serialized classifier must be located immediately under classifiers/ in
the jar file, with the name given.  This allows single jar file
deployment.


PERFORMANCE GUIDELINES

Performance depends on many factors.  Speed and memory use depend on
hardware, operating system, and JVM.  Accuracy depends on the data
tested on.  Nevertheless, in the belief that something is better than
nothing, here are some statistics from one machine on one test set, in
semi-realistic conditions (where the test data is somewhat varied).

ner-eng-ie.crf-3-all2006-distsim.ser.gz (older version of ner-eng-ie.crf-3-all2008-distsim.ser.gz)
Memory: 320MB (on a 32 bit machine)
PERSON	ORGANIZATION	LOCATION
91.88	82.91		88.21


--------------------
CHANGES
--------------------

2013-06-19    3.2.0     Improve handling of line-by-line input 

2013-04-04    1.2.8     nthreads option 

2012-11-11    1.2.7     Improved English 3 class model by including 
                        data from Wikipedia, release Chinese model 

2012-07-09    1.2.6     Minor bug fixes 

2012-05-22    1.2.5     Fix encoding issue

2012-04-07    1.2.4     Caseless version of English models supported

2012-01-06    1.2.3     Minor bug fixes

2011-09-14    1.2.2     Improved thread safety

2011-06-19    1.2.1     Models reduced in size but on average improved 
                        in accuracy (improved distsim clusters)

2011-05-16      1.2     Normal download includes 3, 4, and 7 
                        class models. Updated for compatibility 
                        with other software releases.

2009-01-16    1.1.1     Minor bug and usability fixes, changed API

2008-05-07      1.1     Additional feature flags, various code updates

2006-09-18      1.0     Initial release

