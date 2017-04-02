Stanford NER - v3.7.0 - 2016-10-31
----------------------------------------------

This package provides a high-performance machine learning based named
entity recognition system, including facilities to train models from
supervised training data and pre-trained models for English.

(c) 2002-2015.  The Board of Trustees of The Leland
    Stanford Junior University. All Rights Reserved.

Original CRF code by Jenny Finkel.
Additional modules, features, internationalization, compaction, and
support code by Christopher Manning, Dan Klein, Christopher Cox, Huy Nguyen
Shipra Dingare, Anna Rafferty, and John Bauer.
This release prepared by Jason Bolton.

LICENSE

The software is licensed under the full GPL v2+.  Please see the file LICENCE.txt

For more information, bug reports, and fixes, contact:
    Christopher Manning
    Dept of Computer Science, Gates 2A
    Stanford CA 94305-9020
    USA
    java-nlp-support@lists.stanford.edu
    http://www-nlp.stanford.edu/software/CRF-NER.shtml

CONTACT

For questions about this distribution, please contact Stanford's JavaNLP group
at java-nlp-user@lists.stanford.edu.  We provide assistance on a best-effort
basis.

TUTORIAL

Quickstart guidelines, primarily for end users who wish to use the included NER
models, are below.  For further instructions on training your own NER model,
go to http://www-nlp.stanford.edu/software/crf-faq.shtml.

INCLUDED SERIALIZED MODELS / TRAINING DATA

The basic included serialized model is a 3 class NER tagger that can
label: PERSON, ORGANIZATION, and LOCATION entities.  It is included as
english.all.3class.distsim.crf.ser.gz.  It is trained on data from
CoNLL, MUC6, MUC7, ACE, OntoNotes, and Wikipedia.
Because this model is trained on both US
and UK newswire, it is fairly robust across the two domains.

We have also included a 4 class NER tagger trained on the CoNLL 2003
Shared Task training data that labels for PERSON, ORGANIZATION,
LOCATION, and MISC.  It is named
english.conll.4class.distsim.crf.ser.gz .

A third model is trained only on data from MUC and
distinguishes between 7 different classes:
english.muc.7class.distsim.crf.ser.gz.

All of the serialized classifiers come in two versions, one trained to
basically expected standard written English capitalization, and the other
to ignore capitalization information. The case-insensitive versions
of the three models available on the Stanford NER webpage.
These models use a distributional similarity lexicon to improve performance
(by between 1.5%-3% F-measure).  The distributional similarity features
make the models perform substantially better, but they require rather
more memory.  The distsim models are included in the release package.
The nodistsim versions of the same models may be available on the
Stanford NER webpage.

Finally, we have models for other languages, including two German models,
a Chinese model, and a Spanish model.  The files for these models can be
found at:

http://nlp.stanford.edu/software/CRF-NER.shtml


QUICKSTART INSTRUCTIONS

This NER system requires Java 1.8 or later.

Providing java is on your PATH, you should be able to run an NER GUI
demonstration by just clicking.  It might work to double-click on the
stanford-ner.jar archive but this may well fail as the operating system
does not give Java enough memory for our NER system, so it is safer to
instead double click on the ner-gui.bat icon (Windows) or ner-gui.sh
(Linux/Unix/MacOSX).  Then, using the top option from the Classifier
menu, load a CRF classifier from the classifiers directory of the
distribution.  You can then `either load a text file or web page from
the File menu, or decide to use the default text in the window. Finally,
you can now named entity tag the text by pressing the Run NER button.

From a command line, you need to have java on your PATH and the
stanford-ner.jar file and the lib directory in your CLASSPATH.  (The way of doing this depends on
your OS/shell.)  The supplied ner.bat and ner.sh should work to allow
you to tag a single file.  For example, for Windows:

    ner file

Or on Unix/Linux you should be able to parse the test file in the distribution
directory with the command:

java -mx600m -cp stanford-ner.jar:lib/* edu.stanford.nlp.ie.crf.CRFClassifier -loadClassifier classifiers/english.all.3class.distsim.crf.ser.gz -textFile sample.txt

Here's an output option that will print out entities and their class to
the first two columns of a tab-separated columns output file:

java -mx600m -cp stanford-ner.jar:lib/* edu.stanford.nlp.ie.crf.CRFClassifier -loadClassifier classifiers/english.all.3class.distsim.crf.ser.gz -outputFormat tabbedEntities -textFile sample.txt > sample.tsv

When run from a jar file, you also have the option of using a serialized
classifier contained in the jar file.

USING FULL STANFORD CORENLP NER FUNCTIONALITY

This standalone distribution also allows access to the full NER 
capabilities of the Stanford CoreNLP pipeline. These capabilities 
can be accessed via the NERClassifierCombiner class.
NERClassifierCombiner allows for multiple CRFs to be used together,
and has options for recognizing numeric sequence patterns and time
patterns with the rule-based NER of SUTime. 

Suppose one combines three CRF's CRF-1,CRF-2, and CRF-3 with the
NERClassifierCombiner.  When the NERClassiferCombiner runs, it will
first apply the NER tags of CRF-1 to the text, then it will apply
CRF-2's NER tags to any tokens not tagged by CRF-1 and so on.  If
the option ner.combinationMode is set to NORMAL (default), any label
applied by CRF-1 cannot be applied by subsequent CRF's.  For instance
if CRF-1 applies the LOCATION tag, no other CRF's LOCATION tag will be
used.  If ner.combinationMode is set to HIGH_RECALL, this limitation
will be deactivated.

To use NERClassifierCombiner at the command-line, the jars in lib
and stanford-ner.jar must be in the CLASSPATH.  Here is an example command:

java -mx2g edu.stanford.nlp.ie.NERClassifierCombiner -ner.model \
classifiers/english.conll.4class.distsim.crf.ser.gz,classifiers/english.muc.7class.distsim.crf.ser.gz \
-ner.useSUTime false -textFile sample-w-time.txt

Let's break this down a bit.  The flag "-ner.model" should be followed by a
list of CRF's to be combined by the NERClassifierCombiner.  Some serialized
CRF's are provided in the classifiers directory.  In this example the CRF's
trained on the CONLL 4 class data and the MUC 7 class data are being combined.

When the flag "-ner.useSUTime" is followed by "false", SUTime is shut off.  You should 
note that when the "false" is omitted, the text "4 days ago" suddenly is
tagged with DATE.  These are the kinds of phrases SUTime can identify.

NERClassifierCombiner can be run on different types of input as well.  Here is
an example which is run on CONLL style input:

java -mx2g edu.stanford.nlp.ie.NERClassifierCombiner -ner.model \
classifiers/english.conll.4class.distsim.crf.ser.gz,classifiers/english.muc.7class.distsim.crf.ser.gz \
-map word=0,answer=1 -testFile sample-conll-file.txt

It is crucial to include the "-map word=0,answer=1" , which is specifying that
the input test file has the words in the first column and the answer labels
in the second column.

It is also possible to serialize and load an NERClassifierCombiner.

This command loads the three sample crfs with combinationMode=HIGH_RECALL
and SUTime=false, and dumps them to a file named
test_serialized_ncc.ncc.ser.gz

java -mx2g edu.stanford.nlp.ie.NERClassifierCombiner -ner.model \
classifiers/english.conll.4class.distsim.crf.ser.gz,classifiers/english.muc.7class.distsim.crf.ser.gz,\
classifiers/english.all.3class.distsim.crf.ser.gz -ner.useSUTime false \
-ner.combinationMode HIGH_RECALL -serializeTo test.serialized.ncc.ncc.ser.gz

An example serialized NERClassifierCombiner with these settings is supplied in
the classifiers directory.  Here is an example of loading that classifier and
running it on the sample CONLL data:

java -mx2g edu.stanford.nlp.ie.NERClassifierCombiner -loadClassifier \
classifiers/example.serialized.ncc.ncc.ser.gz -map word=0,answer=1 \
-testFile sample-conll-file.txt

For a more exhaustive description of NERClassifierCombiner go to
http://nlp.stanford.edu/software/ncc-faq.shtml

PROGRAMMATIC USE

The NERDemo file illustrates a couple of ways of calling the system
programatically.  You should get the same results from

java -cp stanford-ner.jar:lib/*:. -mx300m NERDemo classifiers/english.all.3class.distsim.crf.ser.gz sample.txt

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

Or if you have put a model inside the jar file:

java -mx1000m -cp stanford-ner.jar:lib/* edu.stanford.nlp.ie.NERServer -loadJarClassifier all.3class.crf.ser.gz 1234


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

ner-eng-ie.crf-3-all2006-distsim.ser.gz (older version of ner-eng-ie.crf-3-all2008-distsim.ser.gz)
Memory: 320MB (on a 32 bit machine)
PERSON	ORGANIZATION	LOCATION
91.88	82.91		88.21


--------------------
CHANGES
--------------------

2016-10-31    3.7.0     Improved Chinese NER 

2015-12-09    3.6.0     Updated for compatibility 

2015-04-20    3.5.2     synch standalone and CoreNLP functionality 

2015-01-29    3.5.1     Substantial accuracy improvements 

2014-10-26    3.5.0     Upgrade to Java 1.8

2014-08-27    3.4.1     Add Spanish models

2014-06-16      3.4     Fix serialization bug

2014-01-04    3.3.1     Bugfix release

2013-11-12    3.3.0     Update for compatibility

2013-11-12    3.3.0     Update for compatibility

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

