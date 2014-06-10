Stanford Arabic Segmenter - v1.6.7 - 2012-11-11
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

  java -mx1g edu.stanford.nlp.international.arabic.process.ArabicSegmenter -loadClassifier data/arabic-segmenter-atbtrain.ser.gz -textFile my_arabic_file.txt > my_arabic_file.txt.segmented

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
  atbEscaping       : Replace left/right parentheses with ATB escape characters

The orthographic normalization options must match at both training and test time!
Consequently, if you want to apply an orthographic normalization that differs from the
default, then you'll need to retrain ArabicSegmenter.

TRAINING THE SEGMENTER

The current model is trained on parts1-3 of the ATB. This corpus contains newswire text 
sampled from three different news agencies. To train a new model, you need to create a data file from 
the unpacked LDC distributions. You can create this data file with 
TreebankPreprocessor, which is included in the segmenter release.

Look at the example configuration file in arabic/atb-segmenter.conf. You'll need
 to update the paths to your unpacked LDC distributions. **MAKE SURE TO USE THE 
VOCALIZED SECTIONS.** Only the vocalized sections of the ATB contain gold 
segmentation markers required for training. Remove the SPLIT options in the 
configuration files as the train/dev/test split that we used is only valid 
for ATB parts 1-3.

Now run the following command:

  java edu.stanford.nlp.process.treebank.TreebankPreprocessor -v arabic/atb-segmenter.conf

You should see several files in your current working directory, including a file that contains
the entire ATB along with a split of the ATB. Suppose that you want to train on the file "1-Raw-All.utf8.txt"
You can use this file to retrain the segmenter with this command:

  java -Xmx6000m -Xms6000m edu.stanford.nlp.international.arabic.process.ArabicSegmenter -trainFile 1-Raw-All.utf8.txt -serializeTo my_trained_segmenter.ser.gz

This command will produce the serialized model "my_trained_segmenter.ser.gz"
that you can use for raw text processing as described in the "USAGE" section above.

