Stanford Arabic Segmenter - v3.7.0 - 2016-10-31
--------------------------------------

(c) 2012  The Board of Trustees of The Leland Stanford Junior University.
All Rights Reserved.

Arabic segmenter by Spence Green
CRF code by Jenny Finkel
Support code by Stanford JavaNLP members

The system requires Java 6 (JDK1.6) or higher.

The Arabic word segmenter is based on a conditional random field (CRF) sequence
classifier.  The included TreebankPreprocessor package can be used to generate
training data for the model, assuming a copy of Penn Arabic Treebank (ATB) is available.

INSTALLATION

The segmenter does not require any platform-specific installation. Unpack the
gzip'd tar file in any convenient location. To invoke the segmenter, you'll need
to add the ".jar" files in the unpacked directory to your Java classpath. If you
don't know how to modify your classpath, read this tutorial:

  http://docs.oracle.com/javase/tutorial/essential/environment/paths.html

The commands in the remainder of this README assume that you've added the jars
to your classpath.


USAGE

The segmenter assumes that you have a newline delimited text file with UTF-8
encoding. If you're working with Arabic script, chances are that your file is
already encoded in UTF-8. If it is in some other encoding, then you should
check Google for a tutorial on "iconv," a handy tool that allows you to
automatically change file encodings.

Suppose that your raw Arabic file is called "my_arabic_file.txt". You can
segment the tokens in this file with the following command:

  java -mx1g edu.stanford.nlp.international.arabic.process.ArabicSegmenter -loadClassifier data/arabic-segmenter-atb+bn+arztrain.ser.gz -textFile my_arabic_file.txt > my_arabic_file.txt.segmented

Additional command line options are available to mark proclitics and enclitics
that were split by the segmenter. Suppose that the raw token "AABBBCC" was split
into three segments "AA BBB CC", where "AA" is a proclitic and "CC" is an
enclitic. You can direct the segmenter to mark these clitics with these command-line
options:

  -prefixMarker #
  -suffixMarker #

In this case, the segmenter would produce "AA# BBB #CC". Here '#' is the segment marker,
but the options accept any character as an argument. You can use different markers
for proclitics and enclitics.

ORTHOGRAPHIC NORMALIZATION

The segmenter contains a deterministic orthographic normalization package:

  edu.stanford.nlp.international.arabic.process.ArabicTokenizer

ArabicTokenizer supports various orthographic normalization options that can be configured
in ArabicSegmenter using the -orthoOptions flag. The argument to -orthoOptions is a comma-separated list of
normalization options. The following options are supported:

  useUTF8Ellipsis   : Replaces sequences of three or more full stops with \u2026
  normArDigits      : Convert Arabic digits to ASCII equivalents
  normArPunc        : Convert Arabic punctuation to ASCII equivalents
  normAlif          : Change all alif forms to bare alif
  normYa            : Map ya to alif maqsura
  removeDiacritics  : Strip all diacritics
  removeTatweel     : Strip tatweel elongation character
  removeQuranChars  : Remove diacritics that appear in the Quran
  removeProMarker   : Remove the ATB null pronoun marker
  removeSegMarker   : Remove the ATB clitic segmentation marker
  removeMorphMarker : Remove the ATB morpheme boundary markers
  removeLengthening : Replace sequences of three identical characters with one
  atbEscaping       : Replace left/right parentheses with ATB escape characters

The orthographic normalization options must match at both training and test time!
Consequently, if you want to apply an orthographic normalization that differs from the
default, then you'll need to retrain ArabicSegmenter.

SEGMENTING DIALECTAL TEXT

The segmenter supports segmenting dialectal Arabic using domain adaptation.
[Hal Daumé III, Frustratingly Easy Domain Adaptation, ACL 2007] The model that
comes with this distribution is trained to support Egyptian dialect. To
indicate that the provided text is in Egyptian dialect, add the command-line
option:

  -domain arz

You can also construct a file that specifies a dialect for each
newline-separated sentence, by adding "atb" [MSA] or "arz" [Egyptian] at the
beginning of each line followed by a space character. This feature is enabled
with the flag:

  -withDomains

See the bottom of the next section for information about training the
segmenter on your own dialectal data.

TRAINING THE SEGMENTER

The current model is trained on parts 1-3 of the ATB, parts 1-8 of the ARZ treebank,
and the Broadcast News treebank. To train a new model, you need to create a data file from
the unpacked LDC distributions. You can create this data file with
the script tb-preproc, which is included in the segmenter release.

You'll need:

  - Python 2.7 (for running the preprocessing scripts)
  - an unpacked LDC distribution with files in *integrated* format
  - a directory with four text files called dev, train, test, and all, each of
    which lists filenames of integrated files (these usually end in .su.txt),
    one per line. dev, train, and test can be left empty if you have no need
    for a train/test split.

The splits that we use are included in the distribution. You can also find
them at

http://nlp.stanford.edu/software/parser-arabic-data-splits.shtml

Once you have these, run the tb-preproc script, providing the necessary
arguments:

  atb_base -      the most specific directory that is a parent of all
                  integrated files you wish to include. Files will be located
                  recursively by name in this directory; it is not recommended
                  to have several copies of the same distribution within this
                  directory (though all that will happen is that you will
                  train on redundant data).

  splits_dir -    the directory containing dev, train, and test listings

  output_prefix - the location and filename prefix that will identify the
                  output files. The preprocessor appends "-all.utf8.txt" to
                  this argument to give the name of the output file for the
                  "all" split (and similarly for dev, train, and test).

  domain -        [optional] a label for the Arabic dialect/genre that this
                  data is in. Our model uses "atb" for ATB1-3, "bn" for
                  Broadcast News, and "arz" for Egyptian. If a domain is
                  given, additional files will be generated (named e.g.
                  "output_prefix-withDomains-all.utf8.txt") for training the
                  domain adaptation model.

Suppose your output_prefix is "./atb". You should see files in the current
working directory named

  atb-dev.utf8.txt
  atb-train.utf8.txt
  atb-test.utf8.txt

You can use the train file to retrain the segmenter with this command:

  java -Xmx12g -Xms12g edu.stanford.nlp.international.arabic.process.ArabicSegmenter -trainFile atb-train.utf8.txt -serializeTo my_trained_segmenter.ser.gz

This command will produce the serialized model "my_trained_segmenter.ser.gz"
that you can use for raw text processing as described in the "USAGE" section above.

The command above train the model with L2 regularization. For the model
included in the distribution has been trained using L1 regularization, which
decreases the model file size dramatically in exchange for a usually
negligible drop in accuracy. To use L1 regularization, add the options

  -useOWLQN -priorLambda 0.05

TRAINING FOR DIALECT

To train a model with domain adaptation, first make sure you have generated a
training file with domain labels. You can create this using the preprocessing
script with the optional domain argument, or do it yourself with a simple sed
script (the -withDomains files differ from the simple training files only in
the presence of a domain identifier prepended to each line followed by a
space). These domain labels can be arbitrary strings, as long as they don't
contain whitespace characters; thus, if you have data available for other
dialects in ATB format, it is possible to train your own system that can
support these dialects. For best results, include MSA data as well as your
dialect data in your training. You can do this by simply concatenating the
ATB1-3 -withDomains file and the dialect -withDomains file. (Adding data from
dialects other than your target dialect should not hurt performance, as long
as they are marked as different domains--it may even help!)

The training command for domain-labeled data is:

  java -Xmx64g -Xms64g edu.stanford.nlp.international.arabic.process.ArabicSegmenter -withDomains -trainFile atb+arz-withDomains-train.utf8.txt -serializeTo my_trained_segmenter.ser.gz

Warning: training with lots of data from several domains requires a lot of
memory and processor time. If you have enough memory to fit all of the
weights for the entire dataset in RAM (this is a bit less than 64G for ATB1-3
+ BN + ARZ), training will take about ten days of single-threaded processor
time. This can be parallelized by adding the option

  -multiThreadGrad <num_threads>

If you are not running on a machine with 64G of RAM, the training is likely to
take much longer. You have been warned.
