Stanford Classifier v3.7.0 - 2016-10-31
-------------------------------------------------

Copyright (c) 2003-2012 The Board of Trustees of 
The Leland Stanford Junior University. All Rights Reserved.

Original core classifier code and command line interface by Dan Klein
and Chris Manning.  Support code, additional features, etc. by
Kristina Toutanova, Jenny Finkel, Galen Andrew, Joseph Smarr, Chris
Cox, Roger Levy, Rajat Raina, Pi-Chuan Chang, Marie-Catherine de
Marneffe, Eric Yeh, Anna Rafferty, and John Bauer.  This release
prepared by John Bauer.

This package contains a maximum entropy classifier.

For more information about the classifier, point a web browser at the included javadoc directory, starting at the Package page for the edu.stanford.nlp.classify package, and looking also at the ColumnDataClassifier class documentation therein.

This software requires Java 8 (JDK 1.8.0+).  (You must have installed it
separately. Check the command "java -version".)


QUICKSTART

COMMAND LINE INTERFACE
To classify the included example dataset cheeseDisease (in the examples directory), type the following at the command line while in the main classifier directory:

java -cp "*:." edu.stanford.nlp.classify.ColumnDataClassifier -prop examples/cheese2007.prop

This will classify the included test data, cheeseDisease.test, based on the probability that each example is a cheese or a disease, as calculated by a linear classifier trained on cheeseDisease.train.  

The cheese2007.prop file demonstrates how features are specified.  The first feature in the file, useClassFeature, indicates that a feature should be used based on class frequency in the training set.  Most other features are calculated on specific columns of data in your tab-delimited text file.  For example, "1.useNGrams=true" indicates that n-gram features should be created for the values in column 1 (numbering begins at 0!).  Note that you must specify, for example, "true" in "1.useNGrams=true"; "1.useNGrams" alone will not cause n-gram features to be created.  N-gram features are character subsequences of the string in the column, for example, "t", "h", "e", "th", "he", "the" from the word "the". You can also specify various other kinds of features such as just using the string value as a categorical feature (1.useString=true) or splitting up a longer string into bag-of-words features (1.splitWordsRegexp=[ ]  1.useSplitWords=true).  The prop file also allows a choice of printing and optimization options, and allows you to specify training and test files (e.g., in cheese2007.prop under the "Training input" comment).  See the javadoc for ColumnDataClassifier within the edu.stanford.nlp.classify package for more information on these and other options.

Another included dataset is the iris dataset which uses numerical features to separate types of irises.   To specify the use of a real-valued rather than categorical feature, you can use one or more of "realValued", "logTransform", or "logitTransform" for a given column.  "realValued" adds the number in the given column as a feature value, while the transform options perform either a log or a logit transform on the value first.  The format of these feature options is the same as for categorical features; for instance, iris2007.prop shows the use of real valued features such as "2.realValued=true".

CLASSIFYING YOUR OWN DATA FILES
To classify your own data files, they should be in tab-delimited text from which to make features as shown above, SVMLight format, or as tab-delimited text with the exact feature values you would like.  Then specify the train and test files on the command line or in a .prop file with "trainFile=/myPath/myTrainFile.train" and "testFile==/myPath/myTestFile.test".  You can also create a serialized classifier using the serializeTo option followed by a file path.

CODE EXAMPLES
You can also directly use the classes in this package to train classifiers within other programs.  An example of this is shown in ClassifierExample, in the package edu.stanford.nlp.classify.  This class demonstrates how to build a classifier factory, creating a classifier and setting various parameters in the classifier, training the classifier, and finally testing the classifier on a different data set.  

NO GUI
This package does not provide a graphical user interface.  The
classifier is accessible only via the command line or programmatically.


LICENSE

// Stanford Classifier
// Copyright (c) 2003-2007 The Board of Trustees of 
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
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
//
// For more information, bug reports, fixes, contact:
//    Christopher Manning
//    Dept of Computer Science, Gates 1A
//    Stanford CA 94305-9010
//    USA
//    java-nlp-support@lists.stanford.edu
//    http://www-nlp.stanford.edu/software/classifier.shtml


-------------------------
CHANGES
-------------------------

2016-10-31    3.7.0     Update for compatibility 

2015-12-09    3.6.0     Update for compatibility 

2015-04-20    3.5.2     Update for compatibility 

2015-01-29    3.5.1     New input/output options, support for GloVe 
                        word vectors 

2014-10-26    3.5.0     Upgrade to Java 1.8 

2014-08-27    3.4.1     Update for compatibility 

2014-06-16      3.4     Update for compatibility 

2014-01-04    3.3.1     Bugfix release 

2013-11-12    3.3.0     Update for compatibility 

2013-06-19    3.2.0     Update for compatibility 

2013-04-04    2.1.8     Update to maintain compatibility 

2012-11-11    2.1.7     new pair-of-words features 

2012-07-09    2.1.6     Minor bug fixes 

2012-05-22    2.1.5     Re-release to maintain compatibility 
                        with other releases

2012-03-09    2.1.4     Bugfix for svmlight format

2011-12-16    2.1.3     Re-release to maintain compatibility 
                        with other releases

2011-09-14    2.1.2     Change ColumnDataClassifier to be an object
                        with API rather than static methods;
                        ColumnDataClassifier thread safe

2011-06-15    2.1.1     Re-release to maintain compatibility 
                        with other releases

2011-05-15      2.1     Updated with more documentation

2007-08-15      2.0     New command line interface, substantial
                        increase in options and features 
                        (updated on 2007-09-28 with a bug fix)

2003-05-26      1.0     Initial release
