Stanford Chinese Segmenter - v4.2.0 - 2020-11-17
--------------------------------------------

(c) 2003-2020  The Board of Trustees of The Leland Stanford Junior University.
All Rights Reserved. 

Chinese segmenter by Pi-Chuan Chang, Huihsin Tseng, and Galen Andrew
CRF code by Jenny Finkel
Support code by Stanford JavaNLP members

The system requires Java 6 (JDK1.6) or higher.


USAGE

Unix: 
> segment.sh [-k] [ctb|pku] <filename> <encoding> <size>
  ctb : Chinese Treebank
  pku : Beijing Univ.

filename: The file you want to segment. Each line is a sentence.
encoding: UTF-8, GB18030, etc. 
(This must be a character encoding name known by Java)
size: size of the n-best list (just put '0' to print the best hypothesis
without probabilities).
-k: keep all white spaces in the input

* Sample usage: segment.sh ctb test.simp.utf8 UTF-8

* Note: Large test file requires large memory usage.  For processing 
  large data files, you may want to change memory allocation in Java 
	(e.g., to be able to use 8Gb of memory, you need to change "-mx2g" 
	to "-mx8g" inside segment.sh). Another solution is to split the test 
	file to smaller ones to reduce memory usage.

* In addition to the command line scripts, there is a Java class 
  "SegDemo" which shows how to call the segmenter in Java code.
  Usage:
   java -mx2g -cp "*:." SegDemo test.simp.utf8

	SegDemo as supplied assumes that it is running in the home directory of the
	installation, and to run anywhere else, you need to set the path to the
	dictionaries.

SEGMENTATION MODELS

Two segmentation models are provided. The "ctb" model was trained with Chinese 
treebank (CTB) segmentation, and the "pku" model was trained with Beijing 
University's (PKU) segmentation. PKU models provide smaller vocabulary
sizes and OOV rates on test data than CTB models.

For both CTB and PKU, we provide two models representing slightly different
feature sets: 

Models "ctb" and "pku" incorporate lexicon features to increase consistency in
segmentation.


DATA

[Segmentation standard]
(Chinese Penn Treebank)
The supplied segmenter segments according to Chinese Penn Treebank
segmentation conventions.  For more information, see:

 http://www.cis.upenn.edu/~chinese/segguide.3rd.ch.pdf

(Beijing University)
This segmenter segments according to the Peking University standard.
For more information, see:

 http://sighan.cs.uchicago.edu/bakeoff2005/data/pku_spec.pdf

[Training data]
(Chinese Penn Treebank)
"data/ctb.gz" is trained with the training data in the LDC Chinese Treebank 7 

(Beijing University)
"data/pku.gz" is trained with the data provided by Peking University 
for the Second International Chinese Word Segmentation Bakeoff. 
See:

 http://sighan.cs.uchicago.edu/bakeoff2005/


MORE INFORMATION

The details of the segmenter can be found in this paper:

Huihsin Tseng, Pichuan Chang, Galen Andrew, Daniel Jurafsky 
and Christopher Manning. 
"A Conditional Random Field Word Segmenter."
In Fourth SIGHAN Workshop on Chinese Language Processing. 2005.

http://nlp.stanford.edu/pubs/sighan2005.pdf

(Notice that the training data, features and normalizations 
used in this distribution are not exactly the same as the systems 
described in the paper.)

The description of the lexicon features can be found in:

Pi-Chuan Chang, Michel Gally and Christopher Manning.
"Optimizing Chinese Word Segmentation for Machine Translation Performance"
In ACL 2008 Third Workshop on Statistical Machine Translation.

http://nlp.stanford.edu/pubs/acl-wmt08-cws.pdf


For more information, look in the included Javadoc, starting with the 
edu.stanford.nlp.ie.crf.CRFClasifier class documentation.

Send any questions or feedback to java-nlp-support@lists.stanford.edu.
