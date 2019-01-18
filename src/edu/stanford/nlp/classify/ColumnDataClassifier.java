// Stanford Classifier, ColumnDataClassifier - a multiclass maxent classifier
// Copyright (c) 2003-2012 The Board of Trustees of
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
// This code is a parameter language for front-end feature
// generation for the loglinear model classification code in
// the Stanford Classifier package (mainly written by Dan Klein).
//
// For more information, bug reports, fixes, contact:
//    Christopher Manning
//    Dept of Computer Science, Gates 1A
//    Stanford CA 94305-9010
//    USA
//    Support/Questions: java-nlp-user@lists.stanford.edu
//    Licensing: java-nlp-support@lists.stanford.edu
//    http://www-nlp.stanford.edu/software/classifier.shtml

package edu.stanford.nlp.classify;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.BasicDatum;
import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.objectbank.ObjectBank;
import edu.stanford.nlp.optimization.DiffFunction;
import edu.stanford.nlp.optimization.Minimizer;
import edu.stanford.nlp.process.PTBTokenizer;
import edu.stanford.nlp.process.Tokenizer;
import edu.stanford.nlp.process.TokenizerFactory;
import edu.stanford.nlp.process.WordShapeClassifier;
import edu.stanford.nlp.stats.*;
import edu.stanford.nlp.util.*;

import java.io.*;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;


import edu.stanford.nlp.util.logging.Redwood;


/**
 * ColumnDataClassifier provides a command-line interface for doing
 * context-free (independent) classification of a series of data items,
 * where each data item is represented by a line of
 * a file, as a list of String variables, in tab-separated columns.  Some
 * features will interpret these variables as numbers, but
 * the code is mainly oriented towards generating features for string
 * classification.  To designate a real-valued feature, use the realValued
 * option described below. The classifier by default is a maxent classifier
 * (also known as a softmax classifier or a discriminative loglinear classifier;
 * equivalent to multiclass logistic regression apart from a slightly different
 * symmetric parameterization. It also implements a Bernoulli Naive
 * Bayes model and can implement an SVM by an external call to SVMlight.
 *
 * You can also use ColumnDataClassifier programmatically, where its main
 * usefulness beyond simply building your own LinearClassifier is that it
 * provides easy conversion of data items into features, using the same
 * properties as the command-line version. You can see example of usage in
 * the class {@link edu.stanford.nlp.classify.demo.ClassifierDemo}.
 *
 * Input files are expected to
 * be one data item per line with two or more columns indicating the class
 * of the item and one or more predictive features.  Columns are
 * separated by tab characters.  Tab and newline characters cannot occur
 * inside field values (there is no escaping mechanism); any other characters
 * are legal in field values.
 *
 * Typical usage:
 *
 * {@code java edu.stanford.nlp.classify.ColumnDataClassifier -prop propFile }
 *
 * or
 *
 * {@code java -mx300m edu.stanford.nlp.classify.ColumnDataClassifier
 * -trainFile trainFile -testFile testFile -useNGrams|... &gt; output }
 *
 * (Note that for large data sets, you may wish to specify
 * the amount of memory available to Java, such
 * as in the second example above.)
 *
 * In the simplest case, there are just two tab-separated columns in the
 * training input: the first for the class, and the second for the String
 * datum which has that class.   In more complex uses, each datum can
 * be multidimensional, and there are many columns of data attributes.
 *
 * To illustrate simple uses, and the behavior of Naive Bayes and Maximum
 * entropy classifiers, example files corresponding to the examples from the
 * Manning and Klein maxent classifier tutorial, slides 46-49, available at
 * http://nlp.stanford.edu/downloads/classifier.shtml are included in the
 * classify package source directory (files starting with "easy").  Other
 * examples appear in the {@code examples} directory of the distributed
 * classifier.
 *
 * In many instances, parameters can either be given on the command line
 * or provided using a Properties file
 * (specified on the command-line with {@code -prop} <i>propFile</i>).
 * Option names are the same as property names with a preceding dash.  Boolean
 * properties can simply be turned on via the command line.  Parameters of
 * types int, String, and double take a following argument after the option.
 * Command-line parameters can only define features for the first column
 * describing the datum.  If you have multidimensional data, you need to use
 * a properties file.  Property names, as below, are either global (things
 * like the testFile name) or are seen as properties that define features
 * for the first data column (NOTE: we count columns from 0 - unlike the Unix cut
 * command!).  To specify features for a particular data column, precede a
 * feature by a column number and then a period (for example,
 * {@code 3.wordShape=chris4}).  If no number is specified, then the
 * default interpretation is column 0. Note that in properties files you must
 * give a value to boolean properties (e.g., {@code 2.useString=true});
 * just giving the property name (as {@code 2.useString}) isn't
 * sufficient.
 *
 * The following properties are recognized:
 *
 * <table border="1">
 *   <caption>Properties for ColumnDataClassifier</caption>
 * <tr><th><b>Property Name</b></th><th><b>Type</b></th><th><b>Default Value</b></th><th><b>Description</b></th><th><b>FeatName</b></th></tr>
 * <tr><td> loadClassifier </td><td>String</td><td>n/a</td><td>Path of serialized classifier file to load</td></tr>
 * <tr><td> serializeTo</td><td>String</td><td>n/a</td><td>Path to serialize classifier to</td></tr>
 * <tr><td> printTo</td><td>String</td><td>n/a</td><td>Path to print a text representation of the linear classifier to</td></tr>
 * <tr><td> trainFile</td><td>String</td><td>n/a</td><td>Path of file to use as training data</td></tr>
 * <tr><td> testFile</td><td>String</td><td>n/a</td><td>Path of file to use as test data</td></tr>
 * <tr><td> encoding</td><td>String</td><td><i>utf-8</i></td><td>Character encoding of training and test file, e.g., utf-8, GB18030, or iso-8859-1</td></tr>
 * <tr><td> displayedColumn</td><td>int</td><td>1</td><td>Column number that will be printed out to stdout in the output next to the gold class and the chosen class.  This is just an aide memoire.  If the value is negative, nothing is printed. </td></tr>
 * <tr><td> displayAllAnswers</td><td>boolean</td><td>false</td><td>If true, print all classes and their probability, sorted by probability, rather than just the highest scoring and correct classes. </td></tr>
 * <tr><td> goldAnswerColumn</td><td>int</td><td>0</td><td>Column number that contains the correct class for each data item (again, columns are numbered from 0 up).</td></tr>
 * <tr><td> groupingColumn</td><td>int</td><td>-1</td><td>Column for grouping multiple data items for the purpose of computing ranking accuracy.  This is appropriate when only one datum in a group can be correct, and the intention is to choose the highest probability one, rather than accepting all above a threshold.  Multiple items in the same group must be contiguous in the test file (otherwise it would be necessary to cache probabilities and groups for the entire test file to check matches).  If it is negative, no grouping column is used, and no ranking accuracy is reported.</td></tr>
 * <tr><td> rankingScoreColumn</td><td>int</td><td>-1</td><td>If this parameter is non-negative and a groupingColumn is defined, then an average ranking score will be calculated by scoring the chosen candidate from a group according to its value in this column (for instance, the values of this column can be set to a mean reciprocal rank of 1.0 for the best answer, 0.5 for the second best and so on, or the value of this column can be a similarity score reflecting the similarity of the answer to the true answer.</td></tr>
 * <tr><td> rankingAccuracyClass</td><td>String</td><td>null</td><td>If this and groupingColumn are defined (positive), then the system will compute a ranking accuracy under the assumption that there is (at most) one assignment of this class for each group, and ranking accuracy counts the classifier as right if that datum is the one with highest probability according to the model.</td></tr>
 * <tr></tr>
 * <tr><td> useString</td><td>boolean</td><td>false</td><td>Gives you a feature for whole string s</td><td>S-<i>str</i></td></tr>
 * <tr><td> useClassFeature</td><td>boolean</td><td>false</td><td>Include a feature for the class (as a class marginal).  This is the same thing as having a bias vector or having an always-on feature in a model.</td><td>CLASS</td></tr>
 * <tr><td> binnedLengths</td><td>String</td><td>null</td><td>If non-null, treat as a sequence of comma separated integer bounds, where items above the previous bound (if any) up to the next bound (inclusive) are binned (e.g., "1,5,15,30,60"). The feature represents the length of the String in this column.</td><td>Len-<i>range</i></td></tr>
 * <tr><td> binnedLengthsStatistics</td><td>boolean</td><td>false</td><td>If true, print to stderr contingency table of statistics for binnedLengths.</td><td></td></tr>
 * <tr><td> binnedValues</td><td>String</td><td>null</td><td>If non-null, treat as a sequence of comma separated double bounds, where data items above the previous bound up to the next bound (inclusive) are binned. If a value in this column isn't a legal {@code double}, then the value is treated as {@code binnedValuesNaN}.</td><td>Val-<i>range</i></td></tr>
 * <tr><td> binnedValuesNaN</td><td>double</td><td>-1.0</td><td>If the value of a numeric binnedValues field is not a number, it will be given this value.</td></tr>
 * <tr><td> binnedValuesStatistics</td><td>boolean</td><td>false</td><td>If true, print to stderr a contingency table of statistics for binnedValues.</td><td></td></tr>
 * <tr><td> countChars</td><td>String</td><td>null</td><td>If non-null, count the number of occurrences of each character in the String, and make a feature for each character, binned according to {@code countCharsBins}</td><td>Char-<i>ch</i>-<i>range</i></td></tr>
 * <tr><td> countCharsBins</td><td>String</td><td>"0,1"</td><td>Treat as a sequence of comma separated integer bounds, where character counts above the previous bound up to and including the next bound are binned. For instance, a value of "0,2" will give 3 bins, dividing a character count into bins of 0, 1-or-2, and 3-or-more occurrences.</td><td></td></tr>
 * <tr><td> splitWordsRegexp</td><td>String</td><td>null</td><td>If defined, use this as a regular expression on which to split the whole string (as in the String.split() function, which will return the things between delimiters, and discard the delimiters).  The resulting split-up "words" will be used in classifier features iff one of the other "useSplit" options is turned on.</td></tr>
 * <tr><td> splitWordsTokenizerRegexp</td><td>String</td><td>null</td><td>If defined, use this as a regular expression to cut initial pieces off a String.  Either this regular expression or {@code splitWordsIgnoreRegexp} <i>should always match</i> the start of the String, and the size of the token is the number of characters matched.  So, for example, one can group letter and number characters but do nothing else with a regular expression like {@code ([A-Za-z]+|[0-9]+|.)}, where the last disjunct will match any other single character.  (If neither regular expression matches, the first character of the string is treated as a one character word, and then matching is tried again, but in this case a warning message is printed.)  Note that, for Java regular expressions with disjunctions like this, the match is the first matching disjunction, not the longest matching disjunction, so patterns with common prefixes need to be ordered from most specific (longest) to least specific (shortest).)  The resulting split up "words" will be used in classifier features iff one of the other "useSplit" options is turned on.  Note that as usual for Java String processing, backslashes must be doubled in the regular expressions that you write.</td></tr>
 * <tr><td> splitWordsIgnoreRegexp</td><td>String</td><td>\\s+</td><td>If non-empty, this regexp is used to determine character sequences which should not be returned as tokens when using {@code splitWordsTokenizerRegexp} or {@code splitWordsRegexp}. With the former, first the program attempts to match this regular expression at the start of the string (with {@code lookingAt()}) and if it matches, those characters are discarded, but if it doesn't match then {@code splitWordsTokenizerRegexp} is tried. With {@code splitWordsRegexp}, this is used to filter tokens (with {@code matches()} resulting from the splitting.  By default this regular expression is set to be all whitespace tokens (i.e., \\s+). Set it to an empty string to get all tokens returned.</td></tr>
 * <tr><td> splitWordsWithPTBTokenizer</td><td>boolean</td><td>false</td><td>If true, and {@code splitWordsRegexp} and {@code splitWordsTokenizerRegexp} are false, then will tokenize using the {@code PTBTokenizer}</td></tr>
 * <tr><td> useSplitWords</td><td>boolean</td><td>false</td><td>Make features from the "words" that are returned by dividing the string on splitWordsRegexp or splitWordsTokenizerRegexp.  Requires splitWordsRegexp or splitWordsTokenizerRegexp.</td><td>SW-<i>str</i></td></tr>
 * <tr><td> useLowercaseSplitWords</td><td>boolean</td><td>false</td><td>Make features from the "words" that are returned by dividing the string on splitWordsRegexp or splitWordsTokenizerRegexp and then lowercasing the result.  Requires splitWordsRegexp or splitWordsTokenizerRegexp.  Note that this can be specified independently of useSplitWords. You can put either or both original cased and lowercased words in as features.</td><td>LSW-<i>str</i></td></tr>
 * <tr><td> useSplitWordPairs</td><td>boolean</td><td>false</td><td>Make features from the pairs of adjacent "words" that are returned by dividing the string into splitWords.  Requires splitWordsRegexp or splitWordsTokenizerRegexp. This doesn't add features for the first and last words being next to a boundary; if you want those, also set useSplitFirstLastWords.</td><td>SWP-<i>str1</i>-<i>str2</i></td></tr>
 * <tr><td> useLowercaseSplitWordPairs</td><td>boolean</td><td>false</td><td>Make features from the lowercased form of the pairs of adjacent "words" that are returned by dividing the string into splitWords.  Requires splitWordsRegexp or splitWordsTokenizerRegexp. This doesn't add features for the first and last words being next to a boundary; if you want those, also set useLowercaseSplitFirstLastWords.</td><td>LSWP-<i>str1</i>-<i>str2</i></td></tr>
 * <tr><td> useAllSplitWordPairs</td><td>boolean</td><td>false</td><td>Make features from all pairs of "words" that are returned by dividing the string into splitWords.  Requires splitWordsRegexp or splitWordsTokenizerRegexp.</td><td>ASWP-<i>str1</i>-<i>str2</i></td></tr>
 * <tr><td> useAllSplitWordTriples</td><td>boolean</td><td>false</td><td>Make features from all triples of "words" that are returned by dividing the string into splitWords.  Requires splitWordsRegexp or splitWordsTokenizerRegexp.</td><td>ASWT-<i>str1</i>-<i>str2</i>-<i>str3</i></td></tr>
 * <tr><td> useSplitWordNGrams</td><td>boolean</td><td>false</td><td>Make features of adjacent word n-grams of lengths between minWordNGramLeng and maxWordNGramLeng inclusive. Note that these are word sequences, not character n-grams.</td><td>SW#-<i>str1-str2-strN</i></td></tr>
 *
 * <tr><td> splitWordCount</td><td>boolean</td><td>false</td><td>If true, the value of this real-valued feature is the number of split word tokens in the column.</td><td>SWNUM</td></tr>
 * <tr><td> logSplitWordCount</td><td>boolean</td><td>false</td><td>If true, the value of this real-valued feature is the log of the number of split word tokens in the column.</td><td>LSWNUM</td></tr>
 * <tr><td> binnedSplitWordCounts</td><td>String</td><td>null</td><td>If non-null, treat as a sequence of comma-separated integer bounds, where items above the previous bound (if any) up to the next bound (inclusive) are binned (e.g., "1,5,15,30,60"). The feature represents the number of split words in this column.</td><td>SWNUMBIN-<i>range</i></td></tr>

 * <tr><td> maxWordNGramLeng</td><td>int</td><td>-1</td><td>If this number is positive, word n-grams above this size will not be used in the model</td></tr>
 * <tr><td> minWordNGramLeng</td><td>int</td><td>1</td><td>Must be positive. word n-grams below this size will not be used in the model</td></tr>
 * <tr><td> wordNGramBoundaryRegexp</td><td>String</td><td>null</td><td>If this is defined and the regexp matches, then the ngram stops</td></tr>
 * <tr><td> useSplitFirstLastWords</td><td>boolean</td><td>false</td><td>Make a feature from each of the first and last "words" that are returned as splitWords.  This is equivalent to having word bigrams with boundary tokens at each end of the sequence (they get a special feature).  Requires splitWordsRegexp or splitWordsTokenizerRegexp.</td><td>SFW-<i>str</i>, SLW-<i>str</i></td></tr>
 * <tr><td> useLowercaseSplitFirstLastWords</td><td>boolean</td><td>false</td><td>Make a feature from each of the first and last "words" that are returned as splitWords.  This is equivalent to having word bigrams with boundary tokens at each end of the sequence (they get a special feature).  Requires splitWordsRegexp or splitWordsTokenizerRegexp.</td><td>LSFW-<i>str</i>, LSLW-<i>str</i></td></tr>
 * <tr><td> useSplitNGrams</td><td>boolean</td><td>false</td><td>Make features from letter n-grams - internal as well as edge all treated the same - after the data string has been split into tokens.  Requires splitWordsRegexp or splitWordsTokenizerRegexp.</td><td>S#-<i>str</i></td></tr>
 * <tr><td> useSplitPrefixSuffixNGrams</td><td>boolean</td><td>false</td><td>Make features from prefixes and suffixes of each token, after splitting string with splitWordsRegexp.  Requires splitWordsRegexp or splitWordsTokenizerRegexp.</td><td>S#B-<i>str</i>, S#E-<i>str</i></td></tr>
 * <tr><td> useSplitWordVectors</td><td>String</td><td>null</td><td>(If non-null) load word vectors from this file and add their average over all split words as real-valued features.  Requires splitWordsRegexp or splitWordsTokenizerRegexp. Note that for best results you need a close match between your tokenization and that used by the word vectors.</td><td>SWV-<i>num</i></td></tr>

 * <tr><td> useNGrams</td><td>boolean</td><td>false</td><td>Make features from letter n-grams - internal as well as edge all treated the same.</td><td>#-<i>str</i></td></tr>
 * <tr><td> usePrefixSuffixNGrams</td><td>boolean</td><td>false</td><td>Make features from prefix and suffix substrings of the string.</td><td>#B-<i>str</i>, #E-<i>str</i></td></tr>
 * <tr><td> lowercase</td><td>boolean</td><td>false</td><td>Make the input string lowercase so all features work uncased</td></tr>
 * <tr><td> lowercaseNGrams</td><td>boolean</td><td>false</td><td>Make features from letter n-grams all lowercase (for all of useNGrams, usePrefixSuffixNGrams, useSplitNGrams, and useSplitPrefixSuffixNGrams)</td></tr>
 * <tr><td> maxNGramLeng</td><td>int</td><td>-1</td><td>If this number is positive, n-grams above this size will not be used in the model</td></tr>
 * <tr><td> minNGramLeng</td><td>int</td><td>2</td><td>Must be positive. n-grams below this size will not be used in the model</td></tr>
 * <tr><td> partialNGramRegexp</td><td>String</td><td>null</td><td>If this is defined and the regexp matches, then n-grams are made only from the matching text (if no capturing groups are defined) or from the first capturing group of the regexp, if there is one.  This substring is used for both useNGrams and usePrefixSuffixNGrams.</td></tr>
 * <tr><td> realValued</td><td>boolean</td><td>false</td><td>Treat this column as real-valued and do not perform any transforms on the feature value.</td><td>Value</td></tr>
 * <tr><td> logTransform</td><td>boolean</td><td>false</td><td>Treat this column as real-valued and use the log of the value as the feature value.</td><td>Log</td></tr>
 * <tr><td> logitTransform</td><td>boolean</td><td>false</td><td>Treat this column as real-valued and use the logit of the value as the feature value.</td><td>Logit</td></tr>
 * <tr><td> sqrtTransform</td><td>boolean</td><td>false</td><td>Treat this column as real-valued and use the square root of the value as the feature value.</td><td>Sqrt</td></tr>
 * <tr><td> filename</td><td>boolean</td><td>false</td><td>Treat this column as a filename (path) and then use the contents of that file (assumed to be plain text) in the calculation of features according to other flag specifications.</td><td></td></tr>
 * <tr><td> wordShape</td><td>String</td><td>none</td><td>Either "none" for no wordShape use, or the name of a word shape function recognized by {@link edu.stanford.nlp.process.WordShapeClassifier#lookupShaper(String)}, such as "dan1" or "chris4".  WordShape functions equivalence-class strings based on the pattern of letter, digit, and symbol characters that they contain.  The details depend on the particular function chosen.</td><td>SHAPE-<i>str</i></td></tr>
 * <tr><td> splitWordShape</td><td>String</td><td>none</td><td>Either "none" for no wordShape or the name of a word shape function recognized by {@link WordShapeClassifier#lookupShaper(String)}.  This is applied to each "word" found by splitWordsRegexp or splitWordsTokenizerRegexp.</td><td>SSHAPE-<i>str</i></td></tr>
 * <tr></tr>
 * <tr><td> featureMinimumSupport</td><td>int</td><td>0</td><td>A feature, that is, an (observed,class) pair, will only be included in the model providing it is seen a minimum of this number of times in the training data.</td></tr>
 * <tr><td> biasedHyperplane</td><td>String</td><td>null</td><td>If non-null, a sequence of comma-separated pairs of <i>className prob</i>.  An item will only be classified to a certain class <i>className</i> if its probability of class membership exceeds the given conditional probability <i>prob</i>; otherwise it will be assigned to a different class.  If this list of classes is exhaustive, and no condition is satisfied, then the most probable class is chosen.</td></tr>
 * <tr><td> printFeatures</td><td>String</td><td>null</td><td>Print out the features and their values for each instance to a file based on this name.</td></tr>
 * <tr><td> printClassifier</td><td>String</td><td>null</td><td>Style in which to print the classifier. One of: HighWeight, HighMagnitude, AllWeights, WeightHistogram, WeightDistribution. See LinearClassifier class for details.</td></tr>
 * <tr><td> printClassifierParam</td><td>int</td><td>100</td><td>A parameter to the printing style, which may give, for example the number of parameters to print (for HighWeight or HighMagnitude).</td></tr>
 * <tr><td> justify</td><td>boolean</td><td>false</td><td>For each test data item, print justification (weights) for active features used in classification.</td></tr>
 * <tr><td> exitAfterTrainingFeaturization</td><td>boolean</td><td>false</td><td>If true, the program exits after reading the training data (trainFile) and before building a classifier.  This is useful in conjunction with printFeatures, if one only wants to convert data to features for use with another classifier.</td></tr>
 * <tr><td> verboseOptimization</td><td>boolean</td><td>false</td><td>If true, print much more detail about classifier optimization.</td></tr>
 * <tr></tr>
 * <tr><td> intern</td><td>boolean</td><td>false</td><td>If true, (String) intern all of the (final) feature names.  Recommended (this saves memory, but slows down feature generation in training).</td></tr>
 * <tr><td> cacheNGrams</td><td>boolean</td><td>false</td><td>If true, record the NGram features that correspond to a String (under the current option settings and reuse rather than recalculating if the String is seen again.  <b>Disrecommended (speeds training but can require enormous amounts of memory).</b></td></tr>
 * <tr></tr>
 * <tr><td> useNB</td><td>boolean</td><td>false</td><td>Use a Naive Bayes generative classifier (over all features) rather than a discriminative logistic regression classifier.  (Set {@code useClass} to true to get a prior term.)</td></tr>
 * <tr><td> useBinary</td><td>boolean</td><td>false</td><td>Use the binary classifier (i.e. use LogisticClassifierFactory, rather than LinearClassifierFactory) to get classifier</td></tr>
 * <tr><td> l1reg</td><td>double</td><td>0.0</td><td>If set to be larger than 0, uses L1 regularization</td></tr>
 * <tr><td> useAdaptL1</td><td>boolean</td><td>false</td><td>If true, uses adaptive L1 regularization to find value of l1reg that gives the desired number of features set by limitFeatures</td></tr>
 * <tr><td> l1regmin</td><td>double</td><td>0.0</td><td>Minimum L1 in search</td></tr>
 * <tr><td> l1regmax</td><td>double</td><td>500.0</td><td>Maximum L1 in search</td></tr>
 * <tr><td> featureWeightThreshold</td><td>double</td><td>0.0</td><td>Threshold of model weight at which feature is kept. "Unimportant" low weight features are discarded. (Currently only implemented for adaptL1.)</td></tr>
 * <tr><td> limitFeaturesLabels</td><td>String</td><td>null</td><td>If set, only include features for these labels in the desired number of features</td></tr>
 * <tr><td> limitFeatures</td><td>int</td><td>0</td><td>If set to be larger than 0, uses adaptive L1 regularization to find value of l1reg that gives the desired number of features</td></tr>
 * <tr><td>prior</td><td>String/int</td><td>quadratic</td><td>Type of prior (regularization penalty on weights). Possible values are null, "no", "quadratic", "huber", "quartic", "cosh", or "adapt". See {@link edu.stanford.nlp.classify.LogPrior LogPrior} for more information.</td></tr>
 * <tr><td> useSum</td><td>boolean</td><td>false</td><td>Do optimization via summed conditional likelihood, rather than the product.  (This is expensive, non-standard, and somewhat unstable, but can be quite effective: see Klein and Manning 2002 EMNLP paper.)</td></tr>
 * <tr><td> tolerance</td><td>double</td><td>1e-4</td><td>Convergence tolerance in parameter optimization</td></tr>
 * <tr><td> sigma</td><td>double</td><td>1.0</td><td>A parameter to several of the smoothing (i.e., regularization) methods, usually giving a degree of smoothing as a standard deviation (with small positive values being stronger smoothing, and bigger values weaker smoothing). However, for Naive Bayes models it is the amount of add-sigma smoothing, so a bigger number is more smoothing.</td></tr>
 * <tr><td> epsilon</td><td>double</td><td>0.01</td><td>Used only as a parameter in the Huber loss: this is the distance from 0 at which the loss changes from quadratic to linear</td></tr>
 * <tr><td>useQN</td><td>boolean</td><td>true</td><td>Use Quasi-Newton optimization if true, otherwise use Conjugate Gradient optimization.  Recommended.</td></tr>
 * <tr><td>QNsize</td><td>int</td><td>15</td><td>Number of previous iterations of Quasi-Newton to store (this increases memory use, but speeds convergence by letting the Quasi-Newton optimization more effectively approximate the second derivative).</td></tr>
 * <tr><td>featureFormat</td><td>boolean</td><td>false</td><td>Assumes the input file isn't text strings but already featurized.  One column is treated as the class column (as defined by {@code goldAnswerColumn}, and all other columns are treated as features of the instance.  (If answers are not present, set {@code goldAnswerColumn} to a negative number.)</td></tr>
 * <tr><td>trainFromSVMLight</td><td>boolean</td><td>false</td><td>Assumes the trainFile is in SVMLight format (see <a href="http://svmlight.joachims.org/">SVMLight web page</a> for more information)</td></tr>
 * <tr><td>testFromSVMLight</td><td>boolean</td><td>false</td><td>Assumes the testFile is in SVMLight format</td></tr>
 * <tr><td>printSVMLightFormatTo</td><td>String</td><td>null</td><td>If non-null, print the featurized training data to an SVMLight format file (usually used with exitAfterTrainingFeaturization). This is just an option to write out data in a particular format. After that, you're on your own using some other piece of software that reads SVMlight format files.</td></tr>
 * <tr><td>crossValidationFolds</td><td>int</td><td>-1</td><td>If positive, the training data is divided in to this many folds and cross-validation is done on the training data (prior to testing on test data, if it is also specified)</td></tr>
 * <tr><td>printCrossValidationDecisions</td><td>boolean</td><td>false</td><td>Whether to print the individual classification decisions in cross-validation training, if crossValidationFolds is positive.</td></tr>
 * <tr><td>shuffleTrainingData</td><td>boolean</td><td>false</td><td>If true, the training data is shuffled prior to training and cross-validation. This is vital in cross-validation if the training data is otherwise sorted by class.</td></tr>
 * <tr><td>shuffleSeed</td><td>long</td><td>0</td><td>If non-zero, and the training data is being shuffled, this is used as the seed for the Random. Otherwise, System.nanoTime() is used.</td></tr>
 * <tr><td>csvInput</td><td>boolean</td><td>false</td><td>If true, reads train and test file in csv format, with support for quoted fields.</td></tr>
 * <tr><td>inputFormat</td><td>String</td><td>null</td><td>If "header" then reads file with first line treated as header; if "comments" treats lines starting with # as comments; else treated as "plain" tsv/csv file</td></tr>
 * <tr><td>csvOutput</td><td>String</td><td>null</td><td>If non-null, used to format the output of the classifier. This is a printf-style format specification where %0 through %9 can print columns of the input, %c prints the assigned class and %n a newline character. This option can produce Kaggle-format output files!</td></tr>
 * </table>
 *
 * @author Christopher Manning
 * @author Anna Rafferty
 * @author Angel Chang (added options for using l1reg)
 */
public class ColumnDataClassifier  {

  private static final double DEFAULT_VALUE = 1.0; // default value for setting categorical, boolean features
  private static final String DEFAULT_IGNORE_REGEXP = "\\s+";

  private final Flags[] flags;
  private final Flags globalFlags; // simply points to flags[0]
  private Classifier<String,String> classifier; // really only assigned once too (either in train or load in setProperties)
  private TokenizerFactory<Word> ptbFactory;

  private enum InputFormat { PLAIN, COMMENTS, HEADER }

  /** A logger for this class */
  private static final Redwood.RedwoodChannels logger = Redwood.channels(ColumnDataClassifier.class);

  /**
   * Entry point for taking a String (formatted as a line of a TSV file) and
   * translating it into a Datum of features. If real-valued features are used,
   * this method returns an RVFDatum; otherwise, categorical features are used.
   *
   * @param line Line of file
   * @return A Datum (may be an RVFDatum; never null)
   */
  public Datum<String,String> makeDatumFromLine(String line) {
    return makeDatumFromStrings(splitLineToFields(line));
  }


  /**
   * Takes a String[] of elements and translates them into a Datum of features.
   * If real-valued features are used, this method accesses makeRVFDatumFromLine
   * and returns an RVFDatum; otherwise, categorical features are used.
   *
   * @param strings The elements that features are made from (the columns of a TSV/CSV file)
   * @return A Datum (may be an RVFDatum; never null)
   */
  public Datum<String,String> makeDatumFromStrings(String[] strings) {
    if (globalFlags.usesRealValues) {
      return makeRVFDatumFromStrings(strings);
    }

    if (globalFlags.featureFormat) {
      Collection<String> theFeatures = new ArrayList<>();
      for (int i = 0; i < strings.length; i++) {
        if (i != globalFlags.goldAnswerColumn) {
          if (globalFlags.significantColumnId) {
            theFeatures.add(String.format("%d:%s", i, strings[i]));
          } else {
            theFeatures.add(strings[i]);
          }
        }
      }
      return new BasicDatum<>(theFeatures, strings[globalFlags.goldAnswerColumn]);
    } else {
      //logger.info("Read in " + strings);
      return makeDatum(strings);
    }
  }

  private static boolean isRealValued(Flags flags) {
    return flags != null && (flags.isRealValued || flags.logTransform || flags.logitTransform || flags.sqrtTransform);
  }

  private RVFDatum<String,String> makeRVFDatumFromStrings(String[] strings) {
    if (globalFlags.featureFormat) {
      ClassicCounter<String> theFeatures = new ClassicCounter<>();
      for (int i = 0; i < strings.length; i++) {
        if (i != globalFlags.goldAnswerColumn) {
          if (isRealValued(flags[i])) {
            addFeatureValue(strings[i], flags[i], theFeatures);
          } else {
            theFeatures.setCount(strings[i], 1.0);
          }
        }
      }
      return new RVFDatum<>(theFeatures, strings[globalFlags.goldAnswerColumn]);
    } else {
      //logger.info("Read in " + strings);
      return makeRVFDatum(strings);
    }
  }


  /** Read a set of training examples from a file, and return the data in a
   *  featurized form. If feature selection is asked for, the returned
   *  featurized form is after feature selection has been applied.
   *
   *  @param fileName File with supervised training examples.
   *  @return A GeneralDataset, where the labels and features are Strings.
   */
  public GeneralDataset<String,String> readTrainingExamples(String fileName) {
    return readAndReturnTrainingExamples(fileName).first();
  }


  /** Read a set of training examples from a file, and return the data in a
   *  featurized form and in String form. If feature selection is asked for, the returned
   *  featurized form is after feature selection has been applied.
   *  (Note that at present we sometimes need the String form, e.g., for cross-validation, and so
   *  we always pass to readDataset that we are inTestPhase (even though we are training), so this second
   *  element is filled in.)
   *
   *  @param fileName File with supervised training examples.
   *  @return A Pair of a GeneralDataset, where the labels and features are Strings and a List of the input examples
   */
  public Pair<GeneralDataset<String,String>, List<String[]>> readAndReturnTrainingExamples(String fileName) {
    if (globalFlags.printFeatures != null) {
      newFeaturePrinter(globalFlags.printFeatures, "train", Flags.encoding);
    }
    Pair<GeneralDataset<String,String>, List<String[]>> dataInfo = readDataset(fileName, true);
    GeneralDataset<String,String> train = dataInfo.first();
    if (globalFlags.featureMinimumSupport > 1) {
      logger.info("Removing Features with counts < " + globalFlags.featureMinimumSupport);
      train.applyFeatureCountThreshold(globalFlags.featureMinimumSupport);
    }
    train.summaryStatistics();
    return dataInfo;
  }


  /** Read a data set from a file at test time, and return it.
   *
   *  @param filename The file to read the examples from.
   *  @return A Pair. The first item of the pair is the featurized data set,
   *     ready for passing to the classifier.  The second item of the pair
   *     is simply each line of the file split into tab-separated columns.
   *     This is at present necessary for the built-in evaluation, which uses
   *     the gold class from here, and may also be helpful when wanting to
   *     print extra output about the classification process.
   */
  public Pair<GeneralDataset<String,String>,List<String[]>> readTestExamples(String filename) {
    return readDataset(filename, true);
  }

  private static List<String[]> makeSVMLightLineInfos(List<String> lines) {
    List<String[]> lineInfos = new ArrayList<>(lines.size());
    for (String line : lines) {
      line = line.replaceFirst("#.*$", ""); // remove any trailing comments
      // in principle, it'd be nice to save the comment, though, for possible use as a displayedColumn - make it column 1??
      lineInfos.add(line.split("\\s+"));
    }
    return lineInfos;
  }


  /** NB: This is meant to do splitting strictly only on tabs, and to thus
   *  work with things that are exactly TSV files.  It shouldn't split on
   *  all whitespace, because it is useful to be able to have spaces inside
   *  fields for short text documents, and then to be able to split them into
   *  words with features like useSplitWords.
   */
  private static final Pattern tab = Pattern.compile("\\t");


  /** Read a data set from a file and convert it into a Dataset object.
   *  In test phase, returns the {@code List<String[]>} with the data columns for printing purposes.
   *  Otherwise, returns {@code null} for the second item.
   *
   *  @param filename Where to read data from
   *  @param inTestPhase Whether to return the read String[] for each data item
   *  @return A Pair of a GeneralDataSet of Datums and a List of datums in String form.
   */
  @SuppressWarnings("NonThreadSafeLazyInitialization")
  private Pair<GeneralDataset<String,String>, List<String[]>> readDataset(String filename, boolean inTestPhase) {
    Timing tim = new Timing();
    GeneralDataset<String,String> dataset;
    List<String[]> lineInfos = null;
    if ((inTestPhase && Flags.testFromSVMLight) || (!inTestPhase && Flags.trainFromSVMLight)) {
      List<String> lines = null;
      if (inTestPhase) {
        lines = new ArrayList<>();
      }
      if (globalFlags.usesRealValues) {
        dataset = RVFDataset.readSVMLightFormat(filename, lines);
      } else {
        dataset = Dataset.readSVMLightFormat(filename, lines);
      }
      if (lines != null) {
        lineInfos = makeSVMLightLineInfos(lines);
      }
    } else {
      try {
        if (inTestPhase) {
          lineInfos = new ArrayList<>();
        }
        if (globalFlags.usesRealValues) {
          dataset = new RVFDataset<>();
        } else {
          dataset = new Dataset<>();
        }
        int lineNo = 0;
        int minColumns = Integer.MAX_VALUE;
        int maxColumns = 0;
        for (String line : ObjectBank.getLineIterator(new File(filename), Flags.encoding)) {
          lineNo++;
          if (Flags.inputFormat == InputFormat.HEADER) {
            if (lineNo == 1) {
              if (storedHeader == null) {
                storedHeader = line; // store it because need elements of it to print header in output
              }
              continue;
            }
          } else if (Flags.inputFormat == InputFormat.COMMENTS) {
            if (line.matches("\\s#.*")) {
              continue;
            }
          }
          String[] strings = splitLineToFields(line);
          if (strings.length < 2) {
            throw new RuntimeException("Line format error at line " + lineNo + ": " + line);
          }
          if (strings.length < minColumns) {
            minColumns = strings.length;
          }
          if (strings.length > maxColumns) {
            maxColumns = strings.length;
          }
          if (inTestPhase) {
            lineInfos.add(strings);
          }
          if (strings.length < flags.length) {
            throw new RuntimeException("Error: Line has too few tab-separated columns (" + maxColumns +
                    ") for " + flags.length + " columns required by specified properties: " + line);
          }
          dataset.add(makeDatumFromStrings(strings));
        }
        if (lineNo > 0 && minColumns != maxColumns) {
          logger.info("WARNING: Number of tab-separated columns in " +
                  filename + " varies between " + minColumns + " and " + maxColumns);
        }
      } catch (Exception e) {
        throw new RuntimeException("Dataset could not be loaded", e);
      }
    }
    logger.info("Reading dataset from " + filename + " ... done [" + tim.toSecondsString() + "s, " + dataset.size() + " items].");
    return new Pair<>(dataset, lineInfos);
  }

  /** Split according to whether we are using tsv file (default) or csv files. */
  private String[] splitLineToFields(String line) {
    if (globalFlags.csvInput) {
      String[] strings = StringUtils.splitOnCharWithQuoting(line, ',', '"', '"');
      for (int i = 0; i < strings.length; ++i) {
        if (strings[i].startsWith("\"") && strings[i].endsWith("\""))
          strings[i] = strings[i].substring(1,strings[i].length()-1);
      }
      return strings;
    }
    else {
      return tab.split(line);
    }
  }

  /**
   * Write summary statistics about a group of answers.
   */
  private Pair<Double, Double> writeResultsSummary(int num, Counter<String> contingency, Collection<String> labels) {
    logger.info("");
    String message = "";
    message += num + " examples";
    if (globalFlags.groupingColumn >= 0 && globalFlags.rankingAccuracyClass != null) {
      message += " and " + numGroups + " ranking groups";
    }
    logger.info(message + " in test set");
    int numClasses = 0;
    double microAccuracy = 0.0;
    double macroF1 = 0.0;
    for (String key : labels) {
      numClasses++;
      int tp = (int) contingency.getCount(key + "|TP");
      int fn = (int) contingency.getCount(key + "|FN");
      int fp = (int) contingency.getCount(key + "|FP");
      int tn = (int) contingency.getCount(key + "|TN");
      double p = (tp + fp == 0) ? 1.0 : ((double) tp) / (tp + fp); // If nothing selected, then vacuous 1.0
      double r = (tp + fn == 0) ? 1.0 : ((double) tp) / (tp + fn); // If nothing to find, then vacuous 1.0
      double f = (p == 0.0 && r == 0.0) ? 0.0 : 2 * p * r / (p + r);
      double acc = ((double) tp + tn)/num;
      macroF1 += f;
      microAccuracy += tp;
      logger.info("Cls " + key + ": TP=" + tp + " FN=" + fn + " FP=" + fp + " TN=" + tn + "; Acc " + nf.format(acc) + " P " + nf.format(p) + " R " + nf.format(r) + " F1 " + nf.format(f));
    }

    if (globalFlags.groupingColumn >= 0 && globalFlags.rankingAccuracyClass != null) {
      double cor = (int) contingency.getCount("Ranking|Correct");
      double err = (int) contingency.getCount("Ranking|Error");
      double rankacc = (cor + err == 0) ? 0 : cor / (cor + err);
      logger.info("Ranking accuracy: " + nf.format(rankacc));
      double cov = (int) contingency.getCount("Ranking|Covered");
      double coverr = (int) contingency.getCount("Ranking|Uncovered");
      double covacc = (cov + coverr == 0) ? 0 : cov / (cov + coverr);
      if (coverr > 0.5) {
        double ce = (int) (contingency.getCount("Ranking|Error") - contingency.getCount("Ranking|Uncovered"));
        double crankacc = (cor + ce == 0) ? 0 : cor / (cor + ce);
        logger.info(" (on " + nf.format(covacc) + " of groups with correct answer: " + nf.format(crankacc) + ')');
      } else {
        logger.info("");
      }

      if (globalFlags.rankingScoreColumn >= 0) {
        double totalSim = contingency.getCount("Ranking|Score");
        double ranksim = (cor + err == 0) ? 0 : totalSim / (cor + err);
        logger.info("Ranking average score: " + nf.format(ranksim));
      }
    }
    microAccuracy = microAccuracy / num;
    macroF1 = macroF1 / numClasses;
    NumberFormat nf2 = new DecimalFormat("0.00000");
    logger.info("Accuracy/micro-averaged F1: " + nf2.format(microAccuracy));
    logger.info("Macro-averaged F1: " + nf2.format(macroF1));
    return new Pair<>(microAccuracy, macroF1);
  }

  // These variables are only used by the private methods used by main() for displaying
  // performance statistics when running the command-line version. So their being
  // static does no harm.
  private static int numGroups = 0;
  private static String lastGroup = "";
  private static int numInGroup = 0;
  private static double bestProb = 0.0;
  private static double bestSim = 0.0;
  private static boolean currentHighestProbCorrect = false;
  private static boolean foundAnswerInGroup = false;

  private static String storedHeader;

  private static final NumberFormat nf = new DecimalFormat("0.000");

  /**
   * Write out an answer, and update statistics.
   */
  private void writeAnswer(String[] strs, String clAnswer, Distribution<String> cntr) {
    String goldAnswer = globalFlags.goldAnswerColumn < strs.length ? strs[globalFlags.goldAnswerColumn]: "";
    String printedText = "";
    if (globalFlags.displayedColumn >= 0) {
      printedText = strs[globalFlags.displayedColumn];
    }
    String results;
    if (globalFlags.displayAllAnswers) {
      // sort the labels by probability
      TreeSet<Pair<Double,String>> sortedLabels = new TreeSet<>();
      for (String key : cntr.keySet()) {
        sortedLabels.add(new Pair<>(cntr.probabilityOf(key), key));
      }
      StringBuilder builder = new StringBuilder();
      for (Pair<Double,String> pair : sortedLabels.descendingSet()) {
        if (builder.length() > 0) {
          builder.append('\t');
        }
        builder.append(pair.first()).append('\t').append(pair.second());
      }
      results = builder.toString();
    } else {
      results = clAnswer + '\t' + nf.format(cntr.probabilityOf(clAnswer)) + '\t' + nf.format(cntr.probabilityOf(goldAnswer));
    }

    String line;
    if (printedText.isEmpty()) {
      line = goldAnswer + '\t' + results;
     } else {
      line = printedText + '\t' + goldAnswer + '\t' + results;
    }
    System.out.println(line);
  }

  private void updatePerformanceStatistics(String[] strs, String clAnswer, Distribution<String> cntr, Counter<String> contingency, Classifier<String, String> c, double sim) {
    String goldAnswer = globalFlags.goldAnswerColumn < strs.length ? strs[globalFlags.goldAnswerColumn]: "";
    // NB: This next bit judges correctness by surface String equality, not our internal indices, so strs has to be right even for svmlightFormat
    for (String next : c.labels()) {
      if (next.equals(goldAnswer)) {
        if (next.equals(clAnswer)) {
          contingency.incrementCount(next + "|TP");
        } else {
          contingency.incrementCount(next + "|FN");
        }
      } else {
        if (next.equals(clAnswer)) {
          contingency.incrementCount(next + "|FP");
        } else {
          contingency.incrementCount(next + "|TN");
        }
      }
    }
    if (globalFlags.groupingColumn >= 0 && globalFlags.rankingAccuracyClass != null) {
      String group = strs[globalFlags.groupingColumn];
      // logger.info("Group is " + group);
      if (group.equals(lastGroup)) {
        numInGroup++;
        double prob = cntr.probabilityOf(globalFlags.rankingAccuracyClass);
        // logger.info("  same group; prob is " + prob);
        if (prob > bestProb) {
          bestProb = prob;
          bestSim = sim;
          // logger.info("  better prob than before");
          currentHighestProbCorrect = goldAnswer.equals(globalFlags.rankingAccuracyClass);
        }
        if (globalFlags.rankingAccuracyClass.equals(goldAnswer)) {
          foundAnswerInGroup = true;
        }
      } else {
        finishRanking(contingency, bestSim);
        numGroups++;
        lastGroup = group;
        bestProb = cntr.probabilityOf(globalFlags.rankingAccuracyClass);
        bestSim = sim;
        // logger.info("  different; prob is " + bestProb);
        numInGroup = 1;
        currentHighestProbCorrect = goldAnswer.equals(globalFlags.rankingAccuracyClass);
        foundAnswerInGroup = globalFlags.rankingAccuracyClass.equals(goldAnswer);
      }
    }
  }


  private void finishRanking(Counter<String> contingency, double sim) {
    if (numInGroup > 0) {
      if (globalFlags.justify) {
        String message = "Previous group of " + numInGroup + ": ";
        if (!foundAnswerInGroup) {
          message += "no correct answer; ";
        }
        message += "highest ranked guess was: " + ((currentHighestProbCorrect ? "correct" : "incorrect"));
        logger.info(message);
        logger.info(" (sim. = " + nf.format(sim) + ')');
      }
      if (currentHighestProbCorrect) {
        contingency.incrementCount("Ranking|Correct");
      } else {
        contingency.incrementCount("Ranking|Error");
      }
      if (foundAnswerInGroup) {
        contingency.incrementCount("Ranking|Covered");
      } else {
        contingency.incrementCount("Ranking|Uncovered");
      }
      contingency.incrementCount("Ranking|Score", sim);
    }
  }


  /** Test and evaluate classifier on examples with their String representation and gold classification available.
   *
   * @param cl The classifier to test
   * @param test The dataset to test on
   * @param lineInfos Duplicate List of the items to be classified, each an array of Strings (like a line of a TSV file)
   * @return A Pair consisting of the accuracy (micro-averaged F1) and macro-averaged F1 for the dataset
   */
  private Pair<Double, Double> testExamples(Classifier<String, String> cl, GeneralDataset<String, String> test, List<String[]> lineInfos) {
    // usually suppress item level printing in crossvalidation
    if ( ! (globalFlags.crossValidationFolds > 0 && ! globalFlags.printCrossValidationDecisions)) {
      String message = "";
      if (globalFlags.csvOutput != null) {
        message += formatCsv(globalFlags.csvOutput, storedHeader.split("\t"), null);
      } else {
        message += "Output format: ";
        if (globalFlags.displayedColumn >= 0) {
          message += "dataColumn" + globalFlags.displayedColumn + '\t';
        }
        message += "goldAnswer\t";
        if (globalFlags.displayAllAnswers) {
          logger.info(message + "[P(class) class]+ {sorted by probability}");
        } else {
          logger.info(message + "classifierAnswer\tP(clAnswer)\tP(goldAnswer)");
        }
      }
    }

    Counter<String> contingency = new ClassicCounter<>();  // store tp,fp,fn,tn
    for (int i = 0, sz = test.size(); i < sz; i++) {
      testExample(cl, test, lineInfos, contingency, i);
    }

    if (globalFlags.groupingColumn >= 0 && globalFlags.rankingAccuracyClass != null)
      finishRanking(contingency, bestSim);

    return writeResultsSummary(test.size(), contingency, cl.labels());
  }

  private void testExample(Classifier<String, String> cl,
                           GeneralDataset<String, String> test,
                           List<String[]> lineInfos,
                           Counter<String> contingency,
                           int i) {
    String[] example = lineInfos.get(i);
    Datum<String,String> d;
    if (globalFlags.usesRealValues) {
      d = test.getRVFDatum(i);
    } else {
      d = test.getDatum(i);
    }
    if (globalFlags.justify) {
      logger.info("### Test item " + i);
      logger.info(StringUtils.join(example, "\t"));
      if (cl instanceof LinearClassifier) {
        ((LinearClassifier<String,String>)cl).justificationOf(d);
      }
      logger.info();
    }
    Counter<String> logScores;
    if (globalFlags.usesRealValues) {
      logScores = ErasureUtils.<RVFClassifier<String,String>>uncheckedCast(cl).scoresOf((RVFDatum<String,String>)d);
    } else {
      logScores = cl.scoresOf(d);
    }
    Distribution<String> dist = Distribution.distributionFromLogisticCounter(logScores);
    String answer = null;
    if (globalFlags.biasedHyperplane != null) {
      // logger.info("Biased using counter: " +
      //         globalFlags.biasedHyperplane);
      List<String> biggestKeys = new ArrayList<>(logScores.keySet());
      biggestKeys.sort(Counters.toComparatorDescending(logScores));
      for (String key : biggestKeys) {
        double prob = dist.probabilityOf(key);
        double threshold = globalFlags.biasedHyperplane.getCount(key);
        // logger.info("  Trying " + key + " prob is " + prob +
        //           " threshold is " + threshold);
        if (prob > threshold) {
          answer = key;
          break;
        }
      }
    }
    if (answer == null) {
      if (globalFlags.usesRealValues) {
        answer = ErasureUtils.<RVFClassifier<String,String>>uncheckedCast(cl).classOf((RVFDatum<String,String>) d);
      } else {
        answer = cl.classOf(d);
      }
    }
    double sim = 0.0;
    if (globalFlags.rankingScoreColumn >= 0) {
      try {
        sim = Double.parseDouble(example[globalFlags.rankingScoreColumn]);
      } catch (NumberFormatException nfe) {
        // just don't print it
      }
    }
    if ( ! (globalFlags.crossValidationFolds > 0 && ! globalFlags.printCrossValidationDecisions)) {
      if (globalFlags.csvOutput != null) {
        System.out.print(formatCsv(globalFlags.csvOutput, example, answer));
      } else {
        writeAnswer(example, answer, dist);
      }
    }
    updatePerformanceStatistics(example, answer, dist, contingency, cl, sim);
  }

  private String formatCsv(String format, String[] fields, String answer) {
    StringBuilder out = new StringBuilder();
    for (int i = 0, len = format.length(); i < len; i++) {
      char ch = format.charAt(i);
      if (ch == '%' && i + 1 < len) {
        char ch2 = format.charAt(i + 1);
        if (ch2 >= '0' && ch2 <= '9') {
          int field = ch2 - '0';
          if (field < fields.length) {
            out.append(fields[field]);
          } else {
            throw new IllegalArgumentException("Not enough columns for format " + format);
          }
        } else if (ch2 == 'c') {
          if (answer != null) {
            out.append(answer);
          } else if (globalFlags.goldAnswerColumn < fields.length) {
            out.append(fields[globalFlags.goldAnswerColumn]);
          } else {
            out.append("Class");
          }
        } else if (ch2 == 'n') {
          out.append('\n');
        } else {
          throw new IllegalArgumentException("Unrecognized format specification in " + format);
        }
        i++; // have also dealt with next character giving format
      } else {
        out.append(ch);
      }
    }
    return out.toString();
  }


  /**
   * Extracts all the features from a certain input datum.
   *
   * @param strs The data String[] to extract features from
   * @return The constructed Datum
   */
  private Datum<String,String> makeDatum(String[] strs) {
    String goldAnswer = globalFlags.goldAnswerColumn < strs.length ? strs[globalFlags.goldAnswerColumn]: "";
    List<String> theFeatures = new ArrayList<>();
    Collection<String> globalFeatures = Generics.newHashSet();
    if (globalFlags.useClassFeature) {
      globalFeatures.add("CLASS");
    }
    addAllInterningAndPrefixing(theFeatures, globalFeatures, "");

    for (int i = 0; i < flags.length; i++) {
      Collection<String> featuresC = Generics.newHashSet();//important that this is a hash set to prevent same feature from being added multiple times
      makeDatum(strs[i], flags[i], featuresC, goldAnswer);
      addAllInterningAndPrefixing(theFeatures, featuresC, i + "-");
    }

    if (globalFlags.printFeatures != null) {
      printFeatures(strs, theFeatures);
    }
    //System.out.println("Features are: " + theFeatures);
    return new BasicDatum<>(theFeatures, goldAnswer);
  }

  /**
   * Extracts all the features from a certain input array and makes
   * a real valued feature datum; those features that are not real valued
   * are given value 1.0.
   *
   * @param strs The data String[] to extract features from
   * @return The constructed RVFDatum
   */
  private RVFDatum<String,String> makeRVFDatum(String[] strs) {
    String goldAnswer = globalFlags.goldAnswerColumn < strs.length ? strs[globalFlags.goldAnswerColumn]: "";
    ClassicCounter<String> theFeatures = new ClassicCounter<>();
    ClassicCounter<String> globalFeatures = new ClassicCounter<>();
    if (globalFlags.useClassFeature) {
      globalFeatures.setCount("CLASS", 1.0);
    }
    addAllInterningAndPrefixingRVF(theFeatures, globalFeatures, "");

    for (int i = 0; i < flags.length; i++) {
      ClassicCounter<String> featuresC = new ClassicCounter<>();
      makeDatum(strs[i], flags[i], featuresC, goldAnswer);
      addAllInterningAndPrefixingRVF(theFeatures, featuresC, i + "-");
    }

    if (globalFlags.printFeatures != null) {
      printFeatures(strs, theFeatures);
    }
    //System.out.println("Features are: " + theFeatures);
    return new RVFDatum<>(theFeatures, goldAnswer);
  }

  private void addAllInterningAndPrefixingRVF(ClassicCounter<String> accumulator, ClassicCounter<String> addend, String prefix) {
    assert prefix != null;
    for (String protoFeat : addend.keySet()) {
      double count = addend.getCount(protoFeat);
      if ( ! prefix.isEmpty()) {
        protoFeat = prefix + protoFeat;
      }
      if (globalFlags.intern) {
        protoFeat = protoFeat.intern();
      }
      accumulator.incrementCount(protoFeat, count);
    }
  }

  private void addAllInterningAndPrefixing(Collection<String> accumulator, Collection<String> addend, String prefix) {
    assert prefix != null;
    for (String protoFeat : addend) {
      if ( ! prefix.isEmpty()) {
        protoFeat = prefix + protoFeat;
      }
      if (globalFlags.intern) {
        protoFeat = protoFeat.intern();
      }
      accumulator.add(protoFeat);
    }
  }

  /**
   * This method takes care of adding features to the collection-ish object features when
   * the value of the feature must be parsed as a real number, including performing
   * math transforms.
   */
  private static void addFeatureValue(String cWord, Flags flags, Object featuresC) {
    double value = Double.valueOf(cWord);
    if (flags.logTransform) {
      double log = Math.log(value);
      if(Double.isInfinite(log) || Double.isNaN(log)) {
        logger.info("WARNING: Log transform attempted on out of range value; feature ignored");
      } else
        addFeature(featuresC, "Log", log);
    } else if(flags.logitTransform) {
      double logit = Math.log(value/(1-value));
      if(Double.isInfinite(logit) || Double.isNaN(logit)) {
        logger.info("WARNING: Logit transform attempted on out of range value; feature ignored");
      } else {
        addFeature(featuresC, "Logit", logit);
      }
    } else if(flags.sqrtTransform) {
      double sqrt = Math.sqrt(value);
      addFeature(featuresC, "Sqrt", sqrt);
    } else {
      addFeature(featuresC, Flags.realValuedFeaturePrefix, value);
    }
  }

  /**
   * This method takes care of adding features to the collection-ish object features via
   * instanceof checks.  Features must be a type of collection or a counter, and value is used
   * iff it is a counter
   */
    private static <F> void addFeature(Object features, F newFeature, double value) {
      if (features instanceof Counter<?>) {
        ErasureUtils.<Counter<F>>uncheckedCast(features).setCount(newFeature, value);
      } else if(features instanceof Collection<?>) {
        ErasureUtils.<Collection<F>>uncheckedCast(features).add(newFeature);
      } else {
        throw new RuntimeException("addFeature was called with a features object that is neither a counter nor a collection!");
      }
    }

    /**
     * Extracts all the features from a certain input column.
     *
     * @param cWord The String to extract data from
     * @param flags Flags specifying which features to extract
     * @param featuresC Some kind of Collection or Counter to put features into
     * @param goldAns The goldAnswer for this whole datum or emptyString if none.
     *                    This is used only for filling in the binned lengths histogram counters
     */
    private void makeDatum(String cWord, Flags flags, Object featuresC, String goldAns) {

      //logger.info("Making features for " + cWord + " flags " + flags);
      if (flags == null) {
        // no features for this column
        return;
      }
      if (flags.filename) {
        cWord = IOUtils.slurpFileNoExceptions(cWord);
      }
      if (flags.lowercase) {
        cWord = cWord.toLowerCase(Locale.ENGLISH);
      }

      if (flags.useString) {
        addFeature(featuresC,"S-"+cWord,DEFAULT_VALUE);
      }
      if (flags.binnedLengths != null) {
        int len = cWord.length();
        String featureName = null;
        for (int i = 0; i <= flags.binnedLengths.length; i++) {
          if (i == flags.binnedLengths.length || len <= flags.binnedLengths[i]) {
            featureName = "Len-" + ((i == 0) ? 0 : (flags.binnedLengths[i - 1] + 1)) + '-' + ((i == flags.binnedLengths.length) ? "Inf" : Integer.toString(flags.binnedLengths[i]));
            if (flags.binnedLengthsCounter != null) {
              flags.binnedLengthsCounter.incrementCount(featureName, goldAns);
            }
            break;
          }
        }
        addFeature(featuresC,featureName,DEFAULT_VALUE);
      }
      if (flags.binnedValues != null) {
        double val = flags.binnedValuesNaN;
        try {
          val = Double.parseDouble(cWord);
        } catch (NumberFormatException nfe) {
          // do nothing -- keeps value of flags.binnedValuesNaN
        }
        String featureName = null;
        for (int i = 0; i <= flags.binnedValues.length; i++) {
          if (i == flags.binnedValues.length || val <= flags.binnedValues[i]) {
            featureName = "Val-(" + ((i == 0) ? "-Inf" : Double.toString(flags.binnedValues[i - 1])) + ',' + ((i == flags.binnedValues.length) ? "Inf" : Double.toString(flags.binnedValues[i])) + ']';
            if (flags.binnedValuesCounter != null) {
              flags.binnedValuesCounter.incrementCount(featureName, goldAns);
            }
            break;
          }
        }
        addFeature(featuresC,featureName,DEFAULT_VALUE);
      }
      if (flags.countChars != null) {
        int[] cnts = new int[flags.countChars.length];
        for (int i = 0; i < cnts.length; i++) {
          cnts[i] = 0;
        }
        for (int i = 0, len = cWord.length(); i < len; i++) {
          char ch = cWord.charAt(i);
          for (int j = 0; j < cnts.length; j++) {
            if (ch == flags.countChars[j]) {
              cnts[j]++;
            }
          }
        }
        for (int j = 0; j < cnts.length; j++) {
          String featureName = null;
          for (int i = 0; i <= flags.countCharsBins.length; i++) {
            if (i == flags.countCharsBins.length || cnts[j] <= flags.countCharsBins[i]) {
              featureName = "Char-" + flags.countChars[j] + '-' + ((i == 0) ? 0 : (flags.countCharsBins[i - 1] + 1)) + '-' + ((i == flags.countCharsBins.length) ? "Inf" : Integer.toString(flags.countCharsBins[i]));
              break;
            }
          }
          addFeature(featuresC,featureName,DEFAULT_VALUE);
        }
      }
      if (flags.splitWordsPattern != null || flags.splitWordsTokenizerPattern != null ||
              flags.splitWordsWithPTBTokenizer) {
        String[] bits;
        if (flags.splitWordsTokenizerPattern != null) {
          bits = regexpTokenize(flags.splitWordsTokenizerPattern, flags.splitWordsIgnorePattern, cWord);
        } else if (flags.splitWordsPattern != null) {
          bits = splitTokenize(flags.splitWordsPattern, flags.splitWordsIgnorePattern, cWord);
        } else { //PTB tokenizer
          bits = ptbTokenize(cWord);
        }
        if (flags.showTokenization) {
          logger.info("Tokenization: " + Arrays.toString(bits));
        }

        if (flags.splitWordCount) {
          addFeature(featuresC, "SWNUM", bits.length);
        }
        if (flags.logSplitWordCount) {
          addFeature(featuresC, "LSWNUM", Math.log(bits.length));
        }
        if (flags.binnedSplitWordCounts != null) {
          String featureName = null;
          for (int i = 0; i <= flags.binnedSplitWordCounts.length; i++) {
            if (i == flags.binnedSplitWordCounts.length || bits.length <= flags.binnedSplitWordCounts[i]) {
              featureName = "SWNUMBIN-" + ((i == 0) ? 0 : (flags.binnedSplitWordCounts[i - 1] + 1)) + '-' + ((i == flags.binnedSplitWordCounts.length) ? "Inf" : Integer.toString(flags.binnedSplitWordCounts[i]));
              break;
            }
          }
          addFeature(featuresC, featureName, DEFAULT_VALUE);
        }
        // add features over splitWords
        for (int i = 0; i < bits.length; i++) {
          if (flags.useSplitWords) {
            addFeature(featuresC, "SW-" + bits[i], DEFAULT_VALUE);
          }
          if (flags.useLowercaseSplitWords) {
            addFeature(featuresC, "LSW-" + bits[i].toLowerCase(), DEFAULT_VALUE);
          }
          if (flags.useSplitWordPairs) {
            if (i + 1 < bits.length) {
              addFeature(featuresC, "SWP-" + bits[i] + '-' + bits[i + 1], DEFAULT_VALUE);
            }
          }
          if (flags.useLowercaseSplitWordPairs) {
            if (i + 1 < bits.length) {
              addFeature(featuresC, "LSWP-" + bits[i].toLowerCase() + '-' + bits[i + 1].toLowerCase(), DEFAULT_VALUE);
            }
          }
          if (flags.useAllSplitWordPairs) {
            for (int j = i + 1; j < bits.length; j++) {
              // sort lexicographically
              if (bits[i].compareTo(bits[j]) < 0) {
                addFeature(featuresC, "ASWP-" + bits[i] + '-' + bits[j], DEFAULT_VALUE);
              } else {
                addFeature(featuresC, "ASWP-" + bits[j] + '-' + bits[i], DEFAULT_VALUE);
              }
            }
          }
          if (flags.useAllSplitWordTriples) {
            for (int j = i + 1; j < bits.length; j++) {
              for (int k = j + 1; k < bits.length; k++) {
                // sort lexicographically
                String[] triple = new String[3];
                triple[0] = bits[i];
                triple[1] = bits[j];
                triple[2] = bits[k];
                Arrays.sort(triple);
                addFeature(featuresC, "ASWT-" + triple[0] + '-' + triple[1] + '-' + triple[2], DEFAULT_VALUE);
              }
            }
          }
          if (flags.useSplitWordNGrams) {
            StringBuilder sb = new StringBuilder("SW#");
            for (int j = i; j < i+flags.minWordNGramLeng-1 && j < bits.length; j++) {
              sb.append('-');
              sb.append(bits[j]);
            }
            int maxIndex = (flags.maxWordNGramLeng > 0)? Math.min(bits.length, i + flags.maxWordNGramLeng): bits.length;
            for (int j = i + flags.minWordNGramLeng-1; j < maxIndex; j++) {
              if (flags.wordNGramBoundaryRegexp != null) {
                if (flags.wordNGramBoundaryPattern.matcher(bits[j]).matches()) {
                  break;
                }
              }
              sb.append('-');
              sb.append(bits[j]);
              addFeature(featuresC, sb.toString(), DEFAULT_VALUE);
            }
          }
          // this is equivalent to having boundary tokens in splitWordPairs -- they get a special feature
          if (flags.useSplitFirstLastWords) {
            if (i == 0) {
              addFeature(featuresC,"SFW-" + bits[i], DEFAULT_VALUE);
            } else if (i == bits.length - 1) {
              addFeature(featuresC,"SLW-" + bits[i], DEFAULT_VALUE);
            }
          }
          if (flags.useLowercaseSplitFirstLastWords) {
            if (i == 0) {
              addFeature(featuresC,"LSFW-" + bits[i].toLowerCase(), DEFAULT_VALUE);
            } else if (i == bits.length - 1) {
              addFeature(featuresC,"SLW-" + bits[i].toLowerCase(), DEFAULT_VALUE);
            }
          }
          if (flags.useSplitNGrams || flags.useSplitPrefixSuffixNGrams) {
            Collection<String> featureNames = makeNGramFeatures(bits[i], flags, true, "S#");
            for(String featureName : featureNames)
              addFeature(featuresC, featureName, DEFAULT_VALUE);
          }
          if (flags.splitWordShape > edu.stanford.nlp.process.WordShapeClassifier.NOWORDSHAPE) {
            String shape = edu.stanford.nlp.process.WordShapeClassifier.wordShape(bits[i], flags.splitWordShape);
            // logger.info("Shaper is " + flags.splitWordShape + " word len " + bits[i].length() + " shape is " + shape);
            addFeature(featuresC,"SSHAPE-" + shape,DEFAULT_VALUE);
          }
        } // for bits
        if (flags.wordVectors != null) {
          double[] averages = null;
          for (String bit : bits) {
            float[] wv = flags.wordVectors.get(bit);
            if (wv != null) {
              if (averages == null) {
                averages = new double[wv.length];
                for (int j = 0; j < wv.length; j++) {
                  averages[j] += wv[j];
                }
              }
            }
          }
          if (averages != null) {
            for (int j = 0; j < averages.length; j++) {
              averages[j] /= bits.length;
              addFeature(featuresC, "SWV-" + j, averages[j]);
            }
          // } else {
          //   logger.info("No word vectors found for words in |" + cWord + '|');
          }
        } // end if wordVectors
      } // end if uses some split words features

      if (flags.wordShape > WordShapeClassifier.NOWORDSHAPE) {
        String shape = edu.stanford.nlp.process.WordShapeClassifier.wordShape(cWord, flags.wordShape);
        addFeature(featuresC, "SHAPE-" + shape, DEFAULT_VALUE);
      }
      if (flags.useNGrams || flags.usePrefixSuffixNGrams) {
        Collection<String> featureNames = makeNGramFeatures(cWord, flags, false, "#");
        for(String featureName : featureNames)
          addFeature(featuresC,featureName,DEFAULT_VALUE);
      }
      if (isRealValued(flags)) {
        addFeatureValue(cWord, flags, featuresC);
      }
       //logger.info("Made featuresC " + featuresC);
    }  //end makeDatum

  /** Return the tokens using PTB tokenizer.
   *
   *  @param str String to tokenize
   *  @return List of tokens
   */
  // todo [cdm 2017]: Someday should generalize this to allow use of other tokenizers
  private String[] ptbTokenize(String str) {
    if (ptbFactory==null) {
      ptbFactory = PTBTokenizer.factory();
    }
    Tokenizer<Word> tokenizer = ptbFactory.getTokenizer(new StringReader(str));
    List<Word> words = tokenizer.tokenize();
    String[] res = new String[words.size()];
    for (int i = 0, sz = words.size(); i < sz; i++) {
      res[i] = words.get(i).word();
    }
    return res;
  }

  /**
   * Caches a hash of word to all substring features.  Uses a <i>lot</i> of memory!
   * If the String space is large, you shouldn't turn this on.
   */
  private static final Map<String,Collection<String>> wordToSubstrings = new ConcurrentHashMap<>();


  private String intern(String s) {
    if (globalFlags.intern) {
      return s.intern();
    }
    return s;
  }

  /**
   * Return a Collection of NGrams from the input String.
   */
  private Collection<String> makeNGramFeatures(final String input, Flags flags, boolean useSplit, String featPrefix) {
    String toNGrams = input;
    boolean internalNGrams;
    boolean prefixSuffixNGrams;
    if (useSplit) {
      internalNGrams = flags.useSplitNGrams;
      prefixSuffixNGrams = flags.useSplitPrefixSuffixNGrams;
    } else {
      internalNGrams = flags.useNGrams;
      prefixSuffixNGrams = flags.usePrefixSuffixNGrams;
    }
    if (flags.lowercaseNGrams) {
      toNGrams = toNGrams.toLowerCase(Locale.ENGLISH);
    }
    if (flags.partialNGramRegexp != null) {
      Matcher m = flags.partialNGramPattern.matcher(toNGrams);
      // log.info("Matching |" + flags.partialNGramRegexp +
      //                "| against |" + toNGrams + "|");
      if (m.find()) {
        if (m.groupCount() > 0) {
          toNGrams = m.group(1);
        } else {
          toNGrams = m.group();
        }
        // log.info(" Matched |" + toNGrams + "|");
      }
      // logger.info();
    }
    Collection<String> subs = null;
    if (flags.cacheNGrams) {
      subs = wordToSubstrings.get(toNGrams);
    }
    if (subs == null) {
      subs = new ArrayList<>();
      String strN = featPrefix + '-';
      String strB = featPrefix + "B-";
      String strE = featPrefix + "E-";
      int wleng = toNGrams.length();
      for (int i = 0; i < wleng; i++) {
        for (int j = i + flags.minNGramLeng, min = Math.min(wleng, i + flags.maxNGramLeng); j <= min; j++) {
          if (prefixSuffixNGrams) {
            if (i == 0) {
              subs.add(intern(strB + toNGrams.substring(i, j)));
            }
            if (j == wleng) {
              subs.add(intern(strE + toNGrams.substring(i, j)));
            }
          }
          if (internalNGrams) {
            subs.add(intern(strN + toNGrams.substring(i, j)));
          }
        }
      }
      if (flags.cacheNGrams) {
        wordToSubstrings.put(toNGrams, subs);
      }
    }
    return subs;
  }


  private static PrintWriter cliqueWriter;

  private static void newFeaturePrinter(String prefix, String suffix, String encoding) {
    if (cliqueWriter != null) {
      closeFeaturePrinter();
    }
    try {
      cliqueWriter = IOUtils.getPrintWriter(prefix + '.' + suffix, encoding);
    } catch (IOException ioe) {
      cliqueWriter = null;
    }
  }

  private static void closeFeaturePrinter() {
    cliqueWriter.close();
    cliqueWriter = null;
  }

  private static void printFeatures(String[] wi, ClassicCounter<String> features) {
    if (cliqueWriter != null) {
      for (int i = 0; i < wi.length; i++) {
        if (i > 0) {
          cliqueWriter.print("\t");
        }
        cliqueWriter.print(wi[i]);
      }
      for (String feat : features.keySet()) {
        cliqueWriter.print("\t");
        cliqueWriter.print(feat);
        cliqueWriter.print("\t");
        cliqueWriter.print(features.getCount(feat));
      }
      cliqueWriter.println();
    }
  }

  private static void printFeatures(String[] wi, List<String> features) {
    if (cliqueWriter != null) {
      for (int i = 0; i < wi.length; i++) {
        if (i > 0) {
          cliqueWriter.print("\t");
        }
        cliqueWriter.print(wi[i]);
      }
      for (String feat : features) {
        cliqueWriter.print("\t");
        cliqueWriter.print(feat);
      }
      cliqueWriter.println();
    }
  }

  /**
   * Creates a classifier from training data.  A specialized training regimen.
   * It searches for an appropriate l1reg parameter to use to get specified number of features.
   * This is called from makeClassifier() when certain properties are set.
   *
   * @param train training data
   * @return trained classifier
   */
  private Classifier<String,String> makeClassifierAdaptL1(GeneralDataset<String,String> train) {
    assert(globalFlags.useAdaptL1 && globalFlags.limitFeatures > 0);
    Classifier<String, String> lc = null;
    double l1reg = globalFlags.l1reg;
    double l1regmax = globalFlags.l1regmax;
    double l1regmin = globalFlags.l1regmin;
    if (globalFlags.l1reg <= 0.0)  {
      logger.info("WARNING: useAdaptL1 set and limitFeatures to " + globalFlags.limitFeatures
              + ", but invalid value of l1reg=" + globalFlags.l1reg + ", defaulting to " + globalFlags.l1regmax);
      l1reg = l1regmax;
    } else {
      logger.info("TRAIN: useAdaptL1 set and limitFeatures to " + globalFlags.limitFeatures
              + ", l1reg=" + globalFlags.l1reg + ", l1regmax=" + globalFlags.l1regmax + ", l1regmin=" + globalFlags.l1regmin);

    }
    Set<String> limitFeatureLabels = null;
    if (globalFlags.limitFeaturesLabels != null) {
      String[] labels = globalFlags.limitFeaturesLabels.split(",");
      limitFeatureLabels = Generics.newHashSet();
      for (String label:labels) {
        limitFeatureLabels.add(label.trim());
      }
    }
    // Do binary search starting with specified l1reg to find reasonable value of l1reg that gives desired number of features
    double l1regtop = l1regmax;
    double l1regbottom = l1regmin;
    int limitFeatureTol = 5;
    double l1regminchange = 0.05;
    while (true) {
      logger.info("Training: l1reg=" + l1reg + ", threshold=" + globalFlags.featureWeightThreshold
              + ", target=" + globalFlags.limitFeatures);
      LinearClassifierFactory<String,String> lcf;
      Minimizer<DiffFunction> minim = ReflectionLoading.loadByReflection("edu.stanford.nlp.optimization.OWLQNMinimizer", l1reg);
      lcf = new LinearClassifierFactory<>(minim, globalFlags.tolerance, globalFlags.useSum, globalFlags.prior, globalFlags.sigma, globalFlags.epsilon);
      int featureCount = -1;
      try {
        LinearClassifier<String,String> c = lcf.trainClassifier(train);
        lc = c;
        featureCount = c.getFeatureCount(limitFeatureLabels, globalFlags.featureWeightThreshold, false /*useMagnitude*/);

        logger.info("Training Done: l1reg=" + l1reg + ", threshold=" + globalFlags.featureWeightThreshold
                + ", features=" + featureCount + ", target=" + globalFlags.limitFeatures);
        //         String classifierDesc = c.toString(globalFlags.printClassifier, globalFlags.printClassifierParam);
        List<Triple<String,String,Double>> topFeatures = c.getTopFeatures(
                limitFeatureLabels, globalFlags.featureWeightThreshold, false /*useMagnitude*/,
                globalFlags.limitFeatures, true /*descending order*/);
        String classifierDesc = c.topFeaturesToString(topFeatures);
        logger.info("Printing top " + globalFlags.limitFeatures + " features with weights above "
                + globalFlags.featureWeightThreshold);
        if (globalFlags.limitFeaturesLabels != null) {
          logger.info("  Limited to labels: " + globalFlags.limitFeaturesLabels);
        }
        logger.info(classifierDesc);
      } catch (RuntimeException ex) {
        if (ex.getMessage() != null && ex.getMessage().startsWith("L-BFGS chose a non-descent direction")) {
          logger.info("Error in optimization, will try again with different l1reg");
          ex.printStackTrace(System.err);
        } else {
          throw ex;
        }
      }
      if (featureCount < 0 || featureCount < globalFlags.limitFeatures - limitFeatureTol) {
        // Too few features or some other bad thing happened => decrease l1reg
        l1regtop = l1reg;
        l1reg = 0.5*(l1reg + l1regbottom);
        if (l1regtop - l1reg < l1regminchange) {
          logger.info("Stopping: old l1reg  " + l1regtop + "- new l1reg " + l1reg
                  + ", difference less than " + l1regminchange);
          break;
        }
      } else if (featureCount > globalFlags.limitFeatures + limitFeatureTol) {
        // Too many features => increase l1reg
        l1regbottom = l1reg;
        l1reg = 0.5*(l1reg + l1regtop);
        if (l1reg - l1regbottom < l1regminchange) {
          logger.info("Stopping: new l1reg  " + l1reg + "- old l1reg " + l1regbottom
                  + ", difference less than " + l1regminchange);
          break;
        }
      } else {
        logger.info("Stopping: # of features within " + limitFeatureTol + " of target");
        break;
      }
    }
    // Update flags for later serialization
    globalFlags.l1reg = l1reg;
    return lc;
  }

  /**
   * Creates a classifier from training data.
   *
   * @param train training data
   * @return trained classifier
   */
  public Classifier<String,String> makeClassifier(GeneralDataset<String,String> train) {
    Classifier<String, String> lc;
    if (globalFlags.useClassifierFactory != null) {
      ClassifierFactory<String, String, Classifier<String,String>> cf;
      if (globalFlags.classifierFactoryArgs != null) {
        cf = ReflectionLoading.loadByReflection(globalFlags.useClassifierFactory, globalFlags.classifierFactoryArgs);
      } else {
        cf = ReflectionLoading.loadByReflection(globalFlags.useClassifierFactory);
      }
      lc = cf.trainClassifier(train);
    } else if (globalFlags.useNB) {
      double sigma = (globalFlags.prior == 0) ? 0.0 : globalFlags.sigma;
      lc = new NBLinearClassifierFactory<String,String>(sigma, globalFlags.useClassFeature).trainClassifier(train);
    } else if (globalFlags.useBinary) {
      LogisticClassifierFactory<String,String> lcf = new LogisticClassifierFactory<>();
      LogPrior prior = new LogPrior(globalFlags.prior, globalFlags.sigma, globalFlags.epsilon);
      lc = lcf.trainClassifier(train, globalFlags.l1reg, globalFlags.tolerance, prior, globalFlags.biased);
    } else if (globalFlags.biased) {
      LogisticClassifierFactory<String,String> lcf = new LogisticClassifierFactory<>();
      LogPrior prior = new LogPrior(globalFlags.prior, globalFlags.sigma, globalFlags.epsilon);
      lc = lcf.trainClassifier(train, prior, true);
    } else if (globalFlags.useAdaptL1 && globalFlags.limitFeatures > 0) {
      lc = makeClassifierAdaptL1(train);
    } else {
      LinearClassifierFactory<String,String> lcf;
      if (globalFlags.l1reg > 0.0) {
        Minimizer<DiffFunction> minim = ReflectionLoading.loadByReflection("edu.stanford.nlp.optimization.OWLQNMinimizer", globalFlags.l1reg);
        lcf = new LinearClassifierFactory<>(minim, globalFlags.tolerance, globalFlags.useSum, globalFlags.prior, globalFlags.sigma, globalFlags.epsilon);
      } else {
        lcf  = new LinearClassifierFactory<>(globalFlags.tolerance, globalFlags.useSum, globalFlags.prior, globalFlags.sigma, globalFlags.epsilon, globalFlags.QNsize);
      }
      lcf.setVerbose(globalFlags.verboseOptimization);
      if ( ! globalFlags.useQN) {
        lcf.useConjugateGradientAscent();
      }

      lc = lcf.trainClassifier(train);
    }
    return lc;
  }


  private static String[] regexpTokenize(Pattern tokenizerRegexp, Pattern ignoreRegexp, String inWord) {
    List<String> al = new ArrayList<>();
    String word = inWord;
    while ( ! word.isEmpty()) {
      // logger.info("String to match on is " + word);
      Matcher mig = null;
      if (ignoreRegexp != null) {
        mig = ignoreRegexp.matcher(word);
      }
      if (mig != null && mig.lookingAt()) {
        word = word.substring(mig.end());
      } else {
        Matcher m = tokenizerRegexp.matcher(word);
        if (m.lookingAt()) {
          // Logging.logger(ColumnDataClassifier.class).info("Matched " + m.end() + " chars: " +
          //		       word.substring(0, m.end()));
          al.add(word.substring(0, m.end()));
          word = word.substring(m.end());
        } else {
          logger.info("Warning: regexpTokenize pattern " + tokenizerRegexp + " didn't match on |" +
                  word.substring(0, 1) + "| of |" + word + '|');
          // logger.info("Default matched 1 char: " +
          //		       word.substring(0, 1));
          al.add(word.substring(0, 1));
          word = word.substring(1);
        }
      }
    }
    String[] bits = al.toArray(new String[al.size()]);
    return bits;
  }

  private static String[] splitTokenize(Pattern splitRegexp, Pattern ignoreRegexp, String cWord) {
    String[] bits = splitRegexp.split(cWord);
    if (ignoreRegexp != null) {
      List<String> keepBits = new ArrayList<>(bits.length);
      for (String bit : bits) {
        if ( ! ignoreRegexp.matcher(bit).matches()) {
          keepBits.add(bit);
        }
      }
      if (keepBits.size() != bits.length) {
        bits = new String[keepBits.size()];
        keepBits.toArray(bits);
      }
    }
    return bits;
  }

  private static Map<String, float[]> loadWordVectors(String filename) {
    Timing timing = new Timing();
    Map<String,float[]> map = new HashMap<>(10000); // presumably they'll load a fair-sized vocab!?
    BufferedReader br = null;
    try {
      br = IOUtils.readerFromString(filename);
      int numDimensions = -1;
      boolean warned = false;
      for (String line; (line = br.readLine()) != null; ) {
        String[] fields = line.split("\\s+");
        if (numDimensions < 0) {
          numDimensions = fields.length - 1;
        } else {
          if (numDimensions != fields.length -1 && ! warned) {
            logger.info("loadWordVectors: Inconsistent vector size: " + numDimensions +
                    " vs. " + (fields.length - 1));
            warned = true;
          }
        }
        float[] vector = new float[fields.length - 1];
        for (int i = 1; i < fields.length; i++) {
          vector[i-1] = Float.parseFloat(fields[i]);
        }
        map.put(fields[0], vector);
      }
    } catch (IOException ioe) {
      throw new RuntimeIOException("Couldn't load word vectors", ioe);
    } finally {
      IOUtils.closeIgnoringExceptions(br);
    }
    timing.done("Loading word vectors from " + filename + " ... ");
    return map;
  }


  /**
   * Initialize using values in Properties file.
   *
   * @param props Properties, with the special format of column.flag used in ColumnDataClassifier
   * @return An array of flags for each data column, with additional global flags in element [0]
   */
  private static Pair<Flags[], Classifier<String,String>> setProperties(Properties props) {
    Flags[] myFlags;
    Classifier<String,String> classifier = null;
    boolean myUsesRealValues = false;

    Pattern prefix;
    try {
      prefix = Pattern.compile("([0-9]+)\\.(.*)");
    } catch (PatternSyntaxException pse) {
      throw new RuntimeException(pse);
    }

    // if we are loading a classifier then we have to load it before we do anything
    // else and have its Properties be the defaults that can then be overridden by
    // other command-line arguments
    String loadPath = props.getProperty("loadClassifier");
    if (loadPath != null) {
      Pair<Flags[],Classifier<String,String>> pair = loadClassifier(loadPath);
      myFlags = pair.first();
      classifier = pair.second();
    } else {
      myFlags = new Flags[1];
      myFlags[0] = new Flags();  // initialize zero column flags used for global flags; it can't be null
    }

    logger.info("Setting ColumnDataClassifier properties");
    for (String key : props.stringPropertyNames()) {
      String val = props.getProperty(key);
      logger.info(key + " = " + val);

      int col = 0;  // the default (first after class)
      Matcher matcher = prefix.matcher(key);
      if (matcher.matches()) {
        col = Integer.parseInt(matcher.group(1));
        key = matcher.group(2);
      }
      if (col >= myFlags.length) {
        myFlags = Arrays.copyOf(myFlags, col + 1);
      }
      if (myFlags[col] == null) {
        myFlags[col] = new Flags();
      }

      if (key.equals("useString")) {
        myFlags[col].useString = Boolean.parseBoolean(val);
      } else if (key.equals("binnedLengths")) {
        if (val != null) {
          String[] binnedLengthStrs = val.split("[, ]+");
          myFlags[col].binnedLengths = new int[binnedLengthStrs.length];
          for (int i = 0; i < myFlags[col].binnedLengths.length; i++) {
            myFlags[col].binnedLengths[i] = Integer.parseInt(binnedLengthStrs[i]);
          }
        }
      } else if (key.equals("binnedLengthsStatistics")) {
        if (Boolean.parseBoolean(val)) {
          myFlags[col].binnedLengthsCounter = new TwoDimensionalCounter<>();
        }
      } else if (key.equals("splitWordCount")) {
        myFlags[col].splitWordCount = Boolean.parseBoolean(val);
      } else if (key.equals("logSplitWordCount")) {
        myFlags[col].logSplitWordCount = Boolean.parseBoolean(val);
      } else if (key.equals("binnedSplitWordCounts")) {
        if (val != null) {
          String[] binnedSplitWordCountStrs = val.split("[, ]+");
          myFlags[col].binnedSplitWordCounts = new int[binnedSplitWordCountStrs.length];
          for (int i = 0; i < myFlags[col].binnedSplitWordCounts.length; i++) {
            myFlags[col].binnedSplitWordCounts[i] = Integer.parseInt(binnedSplitWordCountStrs[i]);
          }
        }
      } else if (key.equals("countChars")) {
        myFlags[col].countChars = val.toCharArray();
      } else if (key.equals("countCharsBins")) {
        if (val != null) {
          String[] binnedCountStrs = val.split("[, ]+");
          myFlags[col].countCharsBins = new int[binnedCountStrs.length];
          for (int i = 0; i < binnedCountStrs.length; i++) {
            myFlags[col].countCharsBins[i] = Integer.parseInt(binnedCountStrs[i]);
          }
        }
      } else if (key.equals("binnedValues")) {
        if (val != null) {
          String[] binnedValuesStrs = val.split("[, ]+");
          myFlags[col].binnedValues = new double[binnedValuesStrs.length];
          for (int i = 0; i < myFlags[col].binnedValues.length; i++) {
            myFlags[col].binnedValues[i] = Double.parseDouble(binnedValuesStrs[i]);
          }
        }
      } else if (key.equals("binnedValuesNaN")) {
        myFlags[col].binnedValuesNaN = Double.parseDouble(val);
      } else if (key.equals("binnedValuesStatistics")) {
        if (Boolean.parseBoolean(val)) {
          myFlags[col].binnedValuesCounter = new TwoDimensionalCounter<>();
        }
      } else if (key.equals("useNGrams")) {
        myFlags[col].useNGrams = Boolean.parseBoolean(val);
      } else if (key.equals("usePrefixSuffixNGrams")) {
        myFlags[col].usePrefixSuffixNGrams = Boolean.parseBoolean(val);
      } else if (key.equals("useSplitNGrams")) {
        myFlags[col].useSplitNGrams = Boolean.parseBoolean(val);
      } else if (key.equals("wordShape")) {
        myFlags[col].wordShape = WordShapeClassifier.lookupShaper(val);
      } else if (key.equals("splitWordShape")) {
        myFlags[col].splitWordShape = WordShapeClassifier.lookupShaper(val);
      } else if (key.equals("useSplitPrefixSuffixNGrams")) {
        myFlags[col].useSplitPrefixSuffixNGrams = Boolean.parseBoolean(val);
      } else if (key.equals("lowercaseNGrams")) {
        myFlags[col].lowercaseNGrams = Boolean.parseBoolean(val);
      } else if (key.equals("lowercase")) {
        myFlags[col].lowercase = Boolean.parseBoolean(val);
      } else if (key.equals("useLowercaseSplitWords")) {
        myFlags[col].useLowercaseSplitWords = Boolean.parseBoolean(val);
      } else if (key.equals("useSum")) {
        myFlags[col].useSum = Boolean.parseBoolean(val);
      } else if (key.equals("tolerance")) {
        myFlags[col].tolerance = Double.parseDouble(val);
      } else if (key.equals("printFeatures")) {
        myFlags[col].printFeatures = val;
      } else if (key.equals("printClassifier")) {
        myFlags[col].printClassifier = val;
      } else if (key.equals("printClassifierParam")) {
        myFlags[col].printClassifierParam = Integer.parseInt(val);
      } else if (key.equals("exitAfterTrainingFeaturization")) {
        myFlags[col].exitAfterTrainingFeaturization = Boolean.parseBoolean(val);
      } else if (key.equals("intern") || key.equals("intern2")) {
        myFlags[col].intern = Boolean.parseBoolean(val);
      } else if (key.equals("cacheNGrams")) {
        myFlags[col].cacheNGrams = Boolean.parseBoolean(val);
      } else if (key.equals("useClassifierFactory")) {
        myFlags[col].useClassifierFactory = val;
      } else if (key.equals("classifierFactoryArgs")) {
        myFlags[col].classifierFactoryArgs = val;
      } else if (key.equals("useNB")) {
        myFlags[col].useNB = Boolean.parseBoolean(val);
      } else if (key.equals("useBinary")) {
        myFlags[col].useBinary = Boolean.parseBoolean(val);
      } else if (key.equals("l1reg")) {
        myFlags[col].l1reg = Double.parseDouble(val);
      } else if (key.equals("useAdaptL1")) {
        myFlags[col].useAdaptL1 = Boolean.parseBoolean(val);
      } else if (key.equals("limitFeatures")) {
        myFlags[col].limitFeatures = Integer.parseInt(val);
      } else if (key.equals("l1regmin")) {
        myFlags[col].l1regmin = Double.parseDouble(val);
      } else if (key.equals("l1regmax")) {
        myFlags[col].l1regmax = Double.parseDouble(val);
      } else if (key.equals("limitFeaturesLabels")) {
        myFlags[col].limitFeaturesLabels = val;
      } else if (key.equals("featureWeightThreshold")) {
        myFlags[col].featureWeightThreshold = Double.parseDouble(val);
      } else if (key.equals("useClassFeature")) {
        myFlags[col].useClassFeature = Boolean.parseBoolean(val);
      } else if (key.equals("featureMinimumSupport")) {
        myFlags[col].featureMinimumSupport = Integer.parseInt(val);
      } else if (key.equals("prior")) {
        if (val.equalsIgnoreCase("no")) {
          myFlags[col].prior = LogPrior.LogPriorType.NULL.ordinal();
        } else if (val.equalsIgnoreCase("huber")) {
          myFlags[col].prior = LogPrior.LogPriorType.HUBER.ordinal();
        } else if (val.equalsIgnoreCase("quadratic")) {
          myFlags[col].prior = LogPrior.LogPriorType.QUADRATIC.ordinal();
        } else if (val.equalsIgnoreCase("quartic")) {
          myFlags[col].prior = LogPrior.LogPriorType.QUARTIC.ordinal();
        } else {
          try {
            myFlags[col].prior = Integer.parseInt(val);
          } catch (NumberFormatException nfe) {
            logger.info("Unknown prior " + val + "; using none.");
          }
        }
      } else if (key.equals("sigma")) {
        myFlags[col].sigma = Double.parseDouble(val);
      } else if (key.equals("epsilon")) {
        myFlags[col].epsilon = Double.parseDouble(val);
      } else if (key.equals("maxNGramLeng")) {
        myFlags[col].maxNGramLeng = Integer.parseInt(val);
      } else if (key.equals("minNGramLeng")) {
        myFlags[col].minNGramLeng = Integer.parseInt(val);
      } else if (key.equals("partialNGramRegexp")) {
        myFlags[col].partialNGramRegexp = val;
        try {
          myFlags[col].partialNGramPattern = Pattern.compile(myFlags[col].partialNGramRegexp);
        } catch (PatternSyntaxException pse) {
          logger.info("Ill-formed partialNGramPattern: " + myFlags[col].partialNGramPattern);
          myFlags[col].partialNGramRegexp = null;
        }
      } else if (key.equals("splitWordsRegexp")) {
        try {
          myFlags[col].splitWordsPattern = Pattern.compile(val);
        } catch (PatternSyntaxException pse) {
          logger.info("Ill-formed splitWordsRegexp: " + val);
        }
      } else if (key.equals("splitWordsTokenizerRegexp")) {
        try {
          myFlags[col].splitWordsTokenizerPattern = Pattern.compile(val);
        } catch (PatternSyntaxException pse) {
          logger.info("Ill-formed splitWordsTokenizerRegexp: " + val);
        }
      } else if (key.equals("splitWordsIgnoreRegexp")) {
        String trimVal = val.trim();
        if (trimVal.isEmpty()) {
          myFlags[col].splitWordsIgnorePattern = null;
        } else {
          try {
            myFlags[col].splitWordsIgnorePattern = Pattern.compile(trimVal);
          } catch (PatternSyntaxException pse) {
            logger.info("Ill-formed splitWordsIgnoreRegexp: " + trimVal);
          }
        }
      } else if (key.equals("useSplitWords")) {
        myFlags[col].useSplitWords = Boolean.parseBoolean(val);
      } else if (key.equals("useSplitWordPairs")) {
        myFlags[col].useSplitWordPairs = Boolean.parseBoolean(val);
      } else if (key.equals("useLowercaseSplitWordPairs")) {
        myFlags[col].useLowercaseSplitWordPairs = Boolean.parseBoolean(val);
      } else if (key.equals("useAllSplitWordPairs")) {
        myFlags[col].useAllSplitWordPairs = Boolean.parseBoolean(val);
      } else if (key.equals("useAllSplitWordTriples")) {
        myFlags[col].useAllSplitWordTriples = Boolean.parseBoolean(val);
      } else if (key.equals("useSplitWordNGrams")) {
        myFlags[col].useSplitWordNGrams = Boolean.parseBoolean(val);
      } else if (key.equals("maxWordNGramLeng")) {
        myFlags[col].maxWordNGramLeng = Integer.parseInt(val);
      } else if (key.equals("minWordNGramLeng")) {
        myFlags[col].minWordNGramLeng = Integer.parseInt(val);
        if (myFlags[col].minWordNGramLeng < 1) {
          logger.info("minWordNGramLeng set to " + myFlags[col].minWordNGramLeng + ", resetting to 1");
          myFlags[col].minWordNGramLeng = 1;
        }
      } else if (key.equals("wordNGramBoundaryRegexp")) {
        myFlags[col].wordNGramBoundaryRegexp = val;
        try {
          myFlags[col].wordNGramBoundaryPattern = Pattern.compile(myFlags[col].wordNGramBoundaryRegexp);
        } catch (PatternSyntaxException pse) {
          logger.info("Ill-formed wordNGramBoundary regexp: " + myFlags[col].wordNGramBoundaryRegexp);
          myFlags[col].wordNGramBoundaryRegexp = null;
        }
      } else if (key.equals("useSplitFirstLastWords")) {
        myFlags[col].useSplitFirstLastWords = Boolean.parseBoolean(val);
      } else if (key.equals("useLowercaseSplitFirstLastWords")) {
        myFlags[col].useLowercaseSplitFirstLastWords = Boolean.parseBoolean(val);
      } else if (key.equals("loadClassifier")) {
        myFlags[col].loadClassifier = val;
      } else if (key.equals("serializeTo")) {
        Flags.serializeTo = val;
      } else if (key.equals("printTo")) {
        Flags.printTo = val;
      } else if (key.equals("trainFile")) {
        Flags.trainFile = val;
      } else if (key.equals("displayAllAnswers")) {
        Flags.displayAllAnswers = Boolean.parseBoolean(val);
      } else if (key.equals("testFile")) {
        myFlags[col].testFile = val;
      } else if (key.equals("trainFromSVMLight")) {
        Flags.trainFromSVMLight = Boolean.parseBoolean(val);
      } else if (key.equals("testFromSVMLight")) {
        Flags.testFromSVMLight = Boolean.parseBoolean(val);
      } else if (key.equals("encoding")) {
        Flags.encoding = val;
      } else if (key.equals("printSVMLightFormatTo")) {
        Flags.printSVMLightFormatTo = val;
      } else if (key.equals("displayedColumn")) {
        myFlags[col].displayedColumn = Integer.parseInt(val);
      } else if (key.equals("groupingColumn")) {
        myFlags[col].groupingColumn = Integer.parseInt(val);
        // logger.info("Grouping column is " + (myFlags[col].groupingColumn));
      } else if (key.equals("rankingScoreColumn")) {
        myFlags[col].rankingScoreColumn = Integer.parseInt(val);
        // logger.info("Ranking score column is " + (myFlags[col].rankingScoreColumn));
      } else if (key.equals("rankingAccuracyClass")) {
        myFlags[col].rankingAccuracyClass = val;
      } else if (key.equals("goldAnswerColumn")) {
        myFlags[col].goldAnswerColumn = Integer.parseInt(val);
        // logger.info("Gold answer column is " + (myFlags[col].goldAnswerColumn));  // it's a nuisance to print this when used programmatically
      } else if (key.equals("useQN")) {
        myFlags[col].useQN = Boolean.parseBoolean(val);
      } else if (key.equals("QNsize")) {
        myFlags[col].QNsize = Integer.parseInt(val);
      } else if (key.equals("featureFormat")) {
        myFlags[col].featureFormat = Boolean.parseBoolean(val);
      } else if (key.equals("significantColumnId")) {
        myFlags[col].significantColumnId = Boolean.parseBoolean(val);
      } else if (key.equals("justify")) {
        myFlags[col].justify = Boolean.parseBoolean(val);
      } else if (key.equals("verboseOptimization")) {
        myFlags[col].verboseOptimization = Boolean.parseBoolean(val);
      }  else if (key.equals("realValued")) {
        myFlags[col].isRealValued = Boolean.parseBoolean(val);
        myUsesRealValues = myUsesRealValues || myFlags[col].isRealValued;
      } else if (key.equals("logTransform")) {
        myFlags[col].logTransform = Boolean.parseBoolean(val);
        myUsesRealValues = myUsesRealValues || myFlags[col].logTransform;
      } else if (key.equals("logitTransform")) {
        myFlags[col].logitTransform = Boolean.parseBoolean(val);
        myUsesRealValues = myUsesRealValues || myFlags[col].logitTransform;
      } else if (key.equals("sqrtTransform")) {
        myFlags[col].sqrtTransform = Boolean.parseBoolean(val);
        myUsesRealValues = myUsesRealValues || myFlags[col].sqrtTransform;
      } else if (key.equals("filename")) {
        myFlags[col].filename = Boolean.parseBoolean(val);
      } else if (key.equals("biased")) {
        myFlags[col].biased = Boolean.parseBoolean(val);
      } else if (key.equals("biasedHyperplane")) {
        // logger.info("Constraints is " + constraints);
        if (val != null && val.trim().length() > 0) {
          String[] bits = val.split("[, ]+");
          myFlags[col].biasedHyperplane = new ClassicCounter<>();
          for (int i = 0; i < bits.length; i += 2) {
            myFlags[col].biasedHyperplane.setCount(bits[i], Double.parseDouble(bits[i + 1]));
          }
        }
        // logger.info("Biased Hyperplane is " + biasedHyperplane);
      } else if (key.equals("crossValidationFolds")) {
        myFlags[col].crossValidationFolds = Integer.parseInt(val);
      } else if (key.equals("printCrossValidationDecisions")) {
        myFlags[col].printCrossValidationDecisions = Boolean.parseBoolean(val);
      } else if (key.equals("shuffleTrainingData")) {
        myFlags[col].shuffleTrainingData = Boolean.parseBoolean(val);
      } else if (key.equals("shuffleSeed")) {
        myFlags[col].shuffleSeed = Long.parseLong(val);
      } else if (key.equals("csvInput")) {
        myFlags[col].csvInput = Boolean.parseBoolean(val);
      } else if (key.equals("inputFormat")) {
        if (val.equalsIgnoreCase("header")) {
          myFlags[col].inputFormat = InputFormat.HEADER;
        } else if (val.equalsIgnoreCase("comments")) {
          myFlags[col].inputFormat = InputFormat.COMMENTS;
        } else if (val.equalsIgnoreCase("plain")) {
          myFlags[col].inputFormat = InputFormat.PLAIN;
        } else {
          logger.info("Unknown inputFormat: " + val);
        }
      } else if (key.equals("splitWordsWithPTBTokenizer")) {
        // System.out.println("splitting with ptb tokenizer");
        myFlags[col].splitWordsWithPTBTokenizer = Boolean.parseBoolean(val);
      } else if (key.equals("useSplitWordVectors")) {
        myFlags[col].wordVectors = loadWordVectors(val);
        myUsesRealValues = true;
      } else if (key.equals("showTokenization")) {
        myFlags[col].showTokenization = Boolean.parseBoolean(val);
      } else if (key.equals("csvOutput")) {
        myFlags[col].csvOutput = val;
      } else if ( ! key.isEmpty() && ! key.equals("prop")) {
        logger.info("Unknown property: |" + key + '|');
      }
    }
    myFlags[0].usesRealValues = myUsesRealValues;
    return new Pair<>(myFlags,classifier);
  }


  /** Construct a ColumnDataClassifier.
   *
   *  @param filename The file with properties which specifies all aspects of behavior.
   *               See the class documentation for details of the properties.
   */
  public ColumnDataClassifier(String filename) {
    this(StringUtils.propFileToProperties(filename));
  }

  /** Construct a ColumnDataClassifier.
   *
   *  @param props The properties object specifies all aspects of its behavior.
   *               See the class documentation for details of the properties.
   */
  public ColumnDataClassifier(Properties props) {
    this(setProperties(props));
  }

  /** Construct a ColumnDataClassifier.
   *
   *  @param flagsClassifierPair A Pair of a Flags object array specifies all aspects of featurization
   *                             and other behavior and a Classifier that will be used.
   */
  public ColumnDataClassifier(Pair<Flags[],Classifier<String,String>> flagsClassifierPair) {
    flags = flagsClassifierPair.first();
    globalFlags = flags[0];
    classifier = flagsClassifierPair.second();
  }


  private static Pair<Flags[],Classifier<String,String>> loadClassifier(String path) {
    Timing t = new Timing();
    try (ObjectInputStream ois = IOUtils.readStreamFromString(path)) {
      Pair<Flags[],Classifier<String,String>> pair = loadClassifier(ois);
      t.done(logger, "Loading classifier from " + path);
      return pair;
    } catch (IOException | ClassNotFoundException e) {
      throw new RuntimeIOException("Error loading classifier from " + path, e);
    }
  }

  private static Pair<Flags[],Classifier<String,String>> loadClassifier(ObjectInputStream ois)
          throws IOException, ClassNotFoundException {
    // load the classifier
    Classifier<String,String> classifier = ErasureUtils.<LinearClassifier<String,String>>uncheckedCast(ois.readObject());
    Flags[] myFlags = (Flags[]) ois.readObject();
    assert myFlags.length > 0;
    return new Pair<>(myFlags, classifier);
  }

  /** Return a new ColumnDataClassifier object based on a serialized object.
   *  The serialized object stores both a Flags[] that specifies feature extraction and
   *  other properties of the classifier and a Classifier object.
   *
   *  @param path A classpath resource, URL, or file system path
   *  @return The ColumnDataClassifier
   */
  public static ColumnDataClassifier getClassifier(String path) {
    return new ColumnDataClassifier(loadClassifier(path));
  }

  /** Return a new ColumnDataClassifier object based on a serialized object.
   *  The serialized stream stores both a Flags[] that specifies feature extraction and
   *  other properties of the classifier and a Classifier object.
   *
   *  @param ois Where to read a serialized classifier from
   *  @return The ColumnDataClassifier
   */
  public static ColumnDataClassifier getClassifier(ObjectInputStream ois)
          throws IOException, ClassNotFoundException {
    return new ColumnDataClassifier(loadClassifier(ois));
  }


  /**
   * Runs the ColumnDataClassifier from the command-line.  Usage:
   *
   * {@code java edu.stanford.nlp.classify.ColumnDataClassifier -trainFile trainFile
   * -testFile testFile [-useNGrams|-useString|-sigma sigma|...] }
   *
   * or
   *
   * {@code java ColumnDataClassifier -prop propFile }
   *
   * @param args Command line arguments, as described in the class documentation
   * @throws IOException If IO problems
   */
  public static void main(String[] args) throws IOException {
    StringUtils.logInvocationString(logger, args);
    // the constructor will load a classifier if one is specified with loadClassifier
    ColumnDataClassifier cdc = new ColumnDataClassifier(StringUtils.argsToProperties(args));
    String testFile = cdc.globalFlags.testFile;

    // check that we have roughly sensible options or else warn and exit
    if ((testFile == null && Flags.serializeTo == null && cdc.globalFlags.crossValidationFolds < 2) ||
            (Flags.trainFile == null && cdc.globalFlags.loadClassifier == null)) {
      logger.info("usage: java edu.stanford.nlp.classify.ColumnDataClassifier -prop propFile");
      logger.info("  and/or: -trainFile trainFile -testFile testFile|-serializeTo modelFile [-useNGrams|-sigma sigma|...]");
      return; // ENDS PROCESSING
    }

    if (cdc.globalFlags.loadClassifier == null) {
      // Otherwise we attempt to train one and exit if we don't succeed
      if ( ! cdc.trainClassifier(Flags.trainFile)) {
        return;
      }
    }

    if (testFile != null) {
      cdc.testClassifier(testFile);
    }
  } // end main()


  public boolean trainClassifier(String path) throws IOException {
    // build dataset of training data featurized
    Pair<GeneralDataset<String,String>, List<String[]>> dataInfo = readAndReturnTrainingExamples(path);
    GeneralDataset<String,String> train = dataInfo.first();
    List<String[]> lineInfos = dataInfo.second();

    // For things like cross validation, we may well need to sort data!  Data sets are often ordered by class.
    if (globalFlags.shuffleTrainingData) {
      long seed;
      if (globalFlags.shuffleSeed != 0) {
        seed = globalFlags.shuffleSeed;
      } else {
        seed = System.nanoTime();
      }
      train.shuffleWithSideInformation(seed, lineInfos);
    }

    // print any binned value histograms
    for (int i = 0; i < flags.length; i++) {
      if (flags[i] != null && flags[i].binnedValuesCounter != null) {
        logger.info("BinnedValuesStatistics for column " + i);
        logger.info(flags[i].binnedValuesCounter.toString());
      }
    }
    // print any binned length histograms
    for (int i = 0; i < flags.length; i++) {
      if (flags[i] != null && flags[i].binnedLengthsCounter != null) {
        logger.info("BinnedLengthsStatistics for column " + i);
        logger.info(flags[i].binnedLengthsCounter.toString());
      }
    }
    // print the training data in SVMlight format if desired
    if (Flags.printSVMLightFormatTo != null) {
      PrintWriter pw = IOUtils.getPrintWriter(Flags.printSVMLightFormatTo, Flags.encoding);
      train.printSVMLightFormat(pw);
      IOUtils.closeIgnoringExceptions(pw);
      train.featureIndex().saveToFilename(Flags.printSVMLightFormatTo + ".featureIndex");
      train.labelIndex().saveToFilename(Flags.printSVMLightFormatTo + ".labelIndex");
    }

    if (globalFlags.crossValidationFolds > 1) {
      crossValidate(train, lineInfos);
    }

    if (globalFlags.exitAfterTrainingFeaturization) {
      return false; // ENDS PROCESSING
    }

    // build the classifier
    classifier = makeClassifier(train);
    printClassifier(classifier);

    // serialize the classifier
    String serializeTo = Flags.serializeTo;
    if (serializeTo != null) {
      serializeClassifier(serializeTo);
    }
    return true;
  }

  /** Serialize a classifier to a file. This writes to the file both a LinearClassifier and the
   *  Flags[] object from a ColumnDataClassifier. The latter captures all the information about
   *  how the ColumnDataClassifier is extracting features from data items for the classifier.
   *
   *  @param serializeTo Filename to serialize the classifier to
   *  @throws IOException If any IO error
   */
  public void serializeClassifier(String serializeTo) throws IOException {
    logger.info("Serializing classifier to " + serializeTo + "...");
    ObjectOutputStream oos = IOUtils.writeStreamFromString(serializeTo);
    serializeClassifier(oos);
    oos.close();
    logger.info("Done.");
  }

  /** Serialize a classifier to an ObjectOutputStream. This writes to the file both a LinearClassifier and the
   *  Flags[] object from a ColumnDataClassifier. The latter captures all the information about
   *  how the ColumnDataClassifier is extracting features from data items for the classifier.
   *
   *  @param oos ObjectOutputStream to serialize the classifier to
   *  @throws IOException If any IO error
   */
  public void serializeClassifier(ObjectOutputStream oos) throws IOException {
    oos.writeObject(classifier);
    // Fiddle: Don't write a testFile to the serialized classifier.  It makes no sense and confuses people
    String testFile = globalFlags.testFile;
    globalFlags.testFile = null;
    oos.writeObject(flags);
    globalFlags.testFile = testFile;
  }

  private void printClassifier(Classifier classifier) {
    String classString;
    if (classifier instanceof LinearClassifier<?,?>) {
      classString = ((LinearClassifier<?,?>)classifier).toString(globalFlags.printClassifier, globalFlags.printClassifierParam);
    } else {
      classString = classifier.toString();
    }
    if (Flags.printTo != null) {
      PrintWriter fw = null;
      try {
        fw = IOUtils.getPrintWriter(Flags.printTo, Flags.encoding);
        fw.write(classString);
        fw.println();
      } catch (IOException ioe) {
        logger.warn(ioe);
      } finally {
        IOUtils.closeIgnoringExceptions(fw);
      }
      logger.info("Built classifier described in file " + Flags.printTo);
    } else {
      logger.info("Built this classifier: " + classString);
    }
  }

  /** Test and evaluate classifier on examples available in a file (or URL, classpath resource, etc.)
   *
   * @param testFile The path, classpath resource or URL to load TSV data from
   * @return A Pair consisting of the accuracy (micro-averaged F1) and macro-averaged F1 for the dataset
   */
  public Pair<Double, Double> testClassifier(String testFile) {
    if (globalFlags.printFeatures != null) {
      newFeaturePrinter(globalFlags.printFeatures, "test", Flags.encoding);
    }

    Pair<GeneralDataset<String,String>,List<String[]>> testInfo = readTestExamples(testFile);
    GeneralDataset<String,String> test = testInfo.first();
    List<String[]> lineInfos = testInfo.second();

    Pair<Double, Double> pair = testExamples(classifier, test, lineInfos);
    // ((LinearClassifier) classifier).dumpSorted();
    if (globalFlags.printFeatures != null) {
      closeFeaturePrinter();
    }
    return pair;
  }

  /** Run cross-validation on a dataset, and return accuracy and macro-F1 scores.
   *  The number of folds is given by the crossValidationFolds property.
   *
   *  @param dataset The dataset of examples to cross-validate on.
   *  @param lineInfos The String form of the items in the dataset. (Must be present.)
   *  @return Accuracy and macro F1
   */
  public Pair<Double,Double> crossValidate(GeneralDataset<String,String> dataset, List<String[]> lineInfos) {
    final int numFolds = globalFlags.crossValidationFolds;
    double accuracySum = 0.0;
    double macroF1Sum = 0.0;
    for (int fold = 0; fold < numFolds; fold++) {
      logger.info("");
      logger.info("### Fold " + fold);
      Pair<GeneralDataset<String,String>,GeneralDataset<String,String>> split =
              dataset.splitOutFold(fold, numFolds);
      GeneralDataset<String,String> devTrain = split.first();
      GeneralDataset<String,String> devTest = split.second();

      Classifier<String,String> cl = makeClassifier(devTrain);
      printClassifier(cl);

      int normalFoldSize = lineInfos.size()/numFolds;
      int start = normalFoldSize * fold;
      int end = start + normalFoldSize;
      if (fold == (numFolds - 1)) {
        end = lineInfos.size();
      }

      List<String[]> devTestLineInfos = lineInfos.subList(start, end);
      Pair<Double,Double> accuracies = testExamples(cl, devTest, devTestLineInfos);
      accuracySum += accuracies.first();
      macroF1Sum += accuracies.second();
    }
    double averageAccuracy = accuracySum / numFolds;
    double averageMacroF1 = macroF1Sum / numFolds;
    NumberFormat nf2 = new DecimalFormat("0.00000");
    logger.info("Average accuracy/micro-averaged F1: " + nf2.format(averageAccuracy));
    logger.info("Average macro-averaged F1: " + nf2.format(averageMacroF1));
    logger.info("");
    return new Pair<>(averageAccuracy, averageMacroF1);
  }

  public String classOf(Datum<String,String> example) {
    if (classifier == null) {
      throw new RuntimeException("Classifier is not initialized");
    }
    return classifier.classOf(example);
  }

  public Counter<String> scoresOf(Datum<String,String> example) {
    if (classifier == null) {
      throw new RuntimeException("Classifier is not initialized");
    }
    return classifier.scoresOf(example);
  }

  public Classifier<String,String> getClassifier() {
    if (classifier == null) {
      throw new RuntimeException("Classifier is not initialized");
    }
    return classifier;
  }


  static class Flags implements Serializable {

    // PLEASE ADD NEW FLAGS AT THE END OF THIS CLASS (SO AS TO NOT UNNECESSARILY BREAK SERIALIZED CLASSIFIERS)

    private static final long serialVersionUID = -7076671761070232566L;

    boolean useNGrams = false;
    boolean usePrefixSuffixNGrams = false;
    boolean lowercaseNGrams = false;
    boolean lowercase;

    boolean useSplitNGrams = false;
    boolean useSplitPrefixSuffixNGrams = false;

    boolean cacheNGrams = false;
    int maxNGramLeng = -1;
    int minNGramLeng = 2;
    String partialNGramRegexp = null;
    Pattern partialNGramPattern = null;

    boolean useSum = false;
    double tolerance = 1e-4;
    String printFeatures = null;
    String printClassifier = null;
    int printClassifierParam = 100;

    boolean exitAfterTrainingFeaturization = false;

    boolean intern = false;

    Pattern splitWordsPattern = null;
    Pattern splitWordsTokenizerPattern = null;
    Pattern splitWordsIgnorePattern = Pattern.compile(DEFAULT_IGNORE_REGEXP);
    boolean useSplitWords = false;
    boolean useSplitWordPairs = false;
    boolean useLowercaseSplitWordPairs = false;
    boolean useSplitFirstLastWords = false;
    boolean useLowercaseSplitWords = false;
    boolean useLowercaseSplitFirstLastWords = false;

    int wordShape = edu.stanford.nlp.process.WordShapeClassifier.NOWORDSHAPE;
    int splitWordShape = WordShapeClassifier.NOWORDSHAPE;

    boolean useString = false;
    boolean useClassFeature = false;

    int[] binnedLengths = null;
    TwoDimensionalCounter<String,String> binnedLengthsCounter = null;
    double[] binnedValues = null;
    TwoDimensionalCounter<String,String> binnedValuesCounter = null;
    double binnedValuesNaN = -1.0;

    //true iff this feature is real valued
    boolean isRealValued = false;
    public static final String realValuedFeaturePrefix = "Value"; //prefix to add before column number for denoting real valued features
    boolean logitTransform = false;
    boolean logTransform = false;
    boolean sqrtTransform = false;

    char[] countChars = null;
    int[] countCharsBins = {0, 1};

    ClassicCounter<String> biasedHyperplane = null;

    boolean justify = false;

    boolean featureFormat = false;
    boolean significantColumnId = false;

    String  useClassifierFactory;
    String  classifierFactoryArgs;

    boolean useNB = false;
    boolean useQN = true;
    int QNsize = 15;

    int prior = LogPrior.LogPriorType.QUADRATIC.ordinal();
    double sigma = 1.0;
    double epsilon = 0.01;

    int featureMinimumSupport = 0;

    int displayedColumn = 1;  // = 2nd column of data file! (Because we count from 0.)
    int groupingColumn = -1;
    int rankingScoreColumn = -1;
    String rankingAccuracyClass = null;

    int goldAnswerColumn = 0;

    boolean biased;

    boolean useSplitWordNGrams = false;
    int maxWordNGramLeng = -1;
    int minWordNGramLeng = 1;
    boolean useBinary = false;
    double l1reg = 0.0;
    String wordNGramBoundaryRegexp;
    Pattern wordNGramBoundaryPattern;

    boolean useAdaptL1 = false;
    int limitFeatures = 0;
    String limitFeaturesLabels = null;
    double l1regmin = 0.0;
    double l1regmax = 500.0;
    double featureWeightThreshold = 0;

    String testFile = null;  // this one would be better off static (we avoid serializing it)
    String loadClassifier = null;   // this one could also be static

    // these are static because we don't want them serialized
    static String trainFile = null;
    static String serializeTo = null;
    static String printTo = null;
    static boolean trainFromSVMLight = false; //train file is in SVMLight format
    static boolean testFromSVMLight = false; //test file is in SVMLight format
    static String encoding = null;
    static String printSVMLightFormatTo;

    static boolean displayAllAnswers = false;

    // Distinguishes whether this file has real valued features or if the more efficient non-RVF representation can be used.
    // This is set as a summary flag in globalFeatures based on whether anything uses real values.
    boolean usesRealValues;
    boolean filename;

    boolean useAllSplitWordPairs;
    boolean useAllSplitWordTriples;

    boolean showTokenization = false;

    int crossValidationFolds = -1;
    boolean shuffleTrainingData = false;
    long shuffleSeed = 0;

    static boolean csvInput = false; //train and test files are in csv format
    static InputFormat inputFormat = InputFormat.PLAIN;
    boolean splitWordsWithPTBTokenizer = false;

    boolean splitWordCount;
    boolean logSplitWordCount;
    int[] binnedSplitWordCounts;
    Map<String, float[]> wordVectors;

    static String csvOutput = null;
    boolean printCrossValidationDecisions = false;

    boolean verboseOptimization = false;

    @Override
    public String toString() {
      return "Flags[" +
        "goldAnswerColumn = " + goldAnswerColumn +
        ", useString = " + useString +
        ", useNGrams = " + useNGrams +
        ", usePrefixSuffixNGrams = " + usePrefixSuffixNGrams +
        ']';
    }

  } // end class Flags

} // end class ColumnDataClassifier
