// NERFeatureFactory -- features for a probabilistic Named Entity Recognizer
// Copyright (c) 2002-2008 Leland Stanford Junior University
// Additional features (c) 2003 The University of Edinburgh
//
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
//    Support/Questions: java-nlp-user@lists.stanford.edu
//    Licensing: java-nlp-support@lists.stanford.edu
//    https://nlp.stanford.edu/software/CRF-NER.html

package edu.stanford.nlp.ie;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreLabel.GenericAnnotation;
import edu.stanford.nlp.objectbank.ObjectBank;
import edu.stanford.nlp.process.WordShapeClassifier;
import edu.stanford.nlp.sequences.Clique;
import edu.stanford.nlp.sequences.CoNLLDocumentReaderAndWriter;
import edu.stanford.nlp.sequences.FeatureFactory;
import edu.stanford.nlp.sequences.SeqClassifierFlags;
import edu.stanford.nlp.trees.international.pennchinese.RadicalMap;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Interner;
import edu.stanford.nlp.util.PaddedList;
import edu.stanford.nlp.util.Timing;
import edu.stanford.nlp.util.logging.Redwood;


/**
 * Features for Named Entity Recognition.  The code here creates the features
 * by processing Lists of CoreLabels.
 * Look at {@link SeqClassifierFlags} to see where the flags are set for
 * what options to use for what flags.
 * <p>
 * To add a new feature extractor, you should do the following:
 * <ol>
 * <li>Add a variable (boolean, int, String, etc. as appropriate) to
 *     SeqClassifierFlags to mark if the new extractor is turned on or
 *     its value, etc. Add it at the <i>bottom</i> of the list of variables
 *     currently in the class (this avoids problems with older serialized
 *     files breaking). Make the default value of the variable false/null/0
 *     (this is again for backwards compatibility).</li>
 * <li>Add a clause to the big if/then/else of setProperties(Properties) in
 *     SeqClassifierFlags.  Unless it is a macro option, make the option name
 *     the same as the variable name used in step 1.</li>
 * <li>Add code to NERFeatureFactory for this feature. First decide which
 *     classes (hidden states) are involved in the feature.  If only the
 *     current class, you add the feature extractor to the
 *     {@code featuresC} code, if both the current and previous class,
 *     then {@code featuresCpC}, etc.</li>
 * </ol>
 * <p>
 * Parameters can be defined using a Properties file
 * (specified on the command-line with {@code -prop} <i>propFile</i>),
 * or directly on the command line. The following properties are recognized:
 *
 * <table border="1">
 * <caption>NER Parameters</caption>
 * <tr><td><b>Property Name</b></td><td><b>Type</b></td><td><b>Default Value</b></td><td><b>Description</b></td></tr>
 * <tr><td> loadClassifier </td><td>String</td><td>n/a</td><td>Path to serialized classifier to load</td></tr>
 * <tr><td> loadAuxClassifier </td><td>String</td><td>n/a</td><td>Path to auxiliary classifier to load.</td></tr>
 * <tr><td> serializeTo</td><td>String</td><td>n/a</td><td>Path to serialize classifier to</td></tr>
 * <tr><td> trainFile</td><td>String</td><td>n/a</td><td>Path of file to use as training data</td></tr>
 * <tr><td> testFile</td><td>String</td><td>n/a</td><td>Path of file to use as test data</td></tr>
 * <tr><td> map</td><td>String</td><td>see below</td><td>This applies at training time or if testing on tab-separated column data.  It says what is in each column.  It doesn't apply when running on plain text data.  The simplest scenario for training is having words and classes in two column.  word=0,answer=1 is the default if conllNoTags is specified; otherwise word=0,tag=1,answer=2 is the default.  But you can add other columns, such as for a part-of-speech tag, presences in a lexicon, etc.  That would only be useful at runtime if you have part-of-speech information or whatever available and are passing it in with the tokens (that is, you can pass to classify CoreLabel tokens with additional fields stored in them).</td></tr>
 * <tr><td> useWord</td><td>boolean</td><td>true</td><td>Gives you feature for w</td></tr>
 * <tr><td> useBinnedLength</td><td>String</td><td>null</td><td>If non-null, treat as a sequence of comma separated integer bounds, where items above the previous bound up to the next bound are binned Len-<i>range</i></td></tr>
 * <tr><td> useNGrams</td><td>boolean</td><td>false</td><td>Make features from letter n-grams, i.e., substrings of the word</td></tr>
 * <tr><td> lowercaseNGrams</td><td>boolean</td><td>false</td><td>Make features from letter n-grams only lowercase</td></tr>
 * <tr><td> dehyphenateNGrams</td><td>boolean</td><td>false</td><td>Remove hyphens before making features from letter n-grams</td></tr>
 * <tr><td> conjoinShapeNGrams</td><td>boolean</td><td>false</td><td>Conjoin word shape and n-gram features</td></tr>
 * <tr><td> useNeighborNGrams</td><td>boolean</td><td>false</td><td>Use letter n-grams for the previous and current words in the CpC clique.  This feature helps languages such as Chinese, but not so much for English</td></tr>
 * <tr><td> useMoreNeighborNGrams</td><td>boolean</td><td>false</td><td>Use letter n-grams for the previous and next words in the C clique.  This feature helps languages such as Chinese, but not so much for English</td></tr>
 * <tr><td> usePrev</td><td>boolean</td><td>false</td><td>Gives you feature for (pw,c), and together with other options enables other previous features, such as (pt,c) [with useTags)</td></tr>
 * <tr><td> useNext</td><td>boolean</td><td>false</td><td>Gives you feature for (nw,c), and together with other options enables other next features, such as (nt,c) [with useTags)</td></tr>
 * <tr><td> useTags</td><td>boolean</td><td>false</td><td>Gives you features for (t,c), (pt,c) [if usePrev], (nt,c) [if useNext]</td></tr>
 * <tr><td> useWordPairs</td><td>boolean</td><td>false</td><td>Gives you
 * features for (pw, w, c) and (w, nw, c)</td></tr>
 *
 * <tr><td> useGazettes</td><td>boolean</td><td>false</td><td>If true, use gazette features (defined by other flags)</td></tr>
 * <tr><td> gazette</td><td>String</td><td>null</td><td>The value can be one or more filenames (names separated by a comma, semicolon or space).
 * If provided gazettes are loaded from these files.  Each line should be an entity class name, followed by whitespace followed by an entity (which might be a phrase of several tokens with a single space between words).
 * Giving this property turns on useGazettes, so you normally don't need to specify it (but can use it to turn off gazettes specified in a properties file).  Note that you WILL need to specify one or both of cleanGazette or sloppyGazette</td></tr>
 * <tr><td> sloppyGazette</td><td>boolean</td><td>false</td><td>If true, a gazette feature fires when any token of a gazette entry matches</td></tr>
 * <tr><td> cleanGazette</td><td>boolean</td><td>false</td><td>If true, a gazette feature fires when all tokens of a gazette entry match</td></tr>
 *
 * <tr><td> wordShape</td><td>String</td><td>none</td><td>Either "none" for no wordShape use, or the name of a word shape function recognized by {@link WordShapeClassifier#lookupShaper(String)}</td></tr>
 * <tr><td> useSequences</td><td>boolean</td><td>true</td><td>Does not use any class combination features if this is false</td></tr>
 * <tr><td> usePrevSequences</td><td>boolean</td><td>false</td><td>Does not use any class combination features using previous classes if this is false</td></tr>
 * <tr><td> useNextSequences</td><td>boolean</td><td>false</td><td>Does not use any class combination features using next classes if this is false</td></tr>
 * <tr><td> useLongSequences</td><td>boolean</td><td>false</td><td>Use plain higher-order state sequences out to minimum of length or maxLeft</td></tr>
 * <tr><td> useBoundarySequences</td><td>boolean</td><td>false</td><td>Use extra second order class sequence features when previous is CoNLL boundary, so entity knows it can span boundary.</td></tr>
 * <tr><td> useTaggySequences</td><td>boolean</td><td>false</td><td>Use first, second, and third order class and tag sequence interaction features</td></tr>
 * <tr><td> useExtraTaggySequences</td><td>boolean</td><td>false</td><td>Add in sequences of tags with just current class features</td></tr>
 * <tr><td> useTaggySequencesShapeInteraction</td><td>boolean</td><td>false</td><td>Add in terms that join sequences of 2 or 3 tags with the current shape</td></tr>
 * <tr><td> strictlyFirstOrder</td><td>boolean</td><td>false</td><td>As an override to whatever other options are in effect, deletes all features other than C and CpC clique features when building the classifier</td></tr>
 * <tr><td> entitySubclassification</td><td>String</td><td>"IO"</td><td>If
 * set, convert the labeling of classes (but not  the background) into
 * one of several alternate encodings (IO, IOB1, IOB2, IOE1, IOE2, SBIEO, with
 * a S(ingle), B(eginning),
 * E(nding), I(nside) 4-way classification for each class.  By default, we
 * either do no re-encoding, or the CoNLLDocumentIteratorFactory does a
 * lossy encoding as IO.  Note that this is all CoNLL-specific, and depends on
 * their way of prefix encoding classes, and is only implemented by
 * the CoNLLDocumentIteratorFactory. </td></tr>
 *
 * <tr><td> useSum</td><td>boolean</td><td>false</td><td></td></tr>
 * <tr><td> tolerance</td><td>double</td><td>1e-4</td><td>Convergence tolerance in optimization</td></tr>
 * <tr><td> printFeatures</td><td>String</td><td>null</td><td>print out all the features generated by the classifier for a dataset to a file based on this name (starting with "features-", suffixed "-1" and "-2" for train and test). This simply prints the feature names, one per line.</td></tr>
 * <tr><td> printFeaturesUpto</td><td>int</td><td>-1</td><td>Print out features for only the first this many datums, if the value is positive. </td></tr>
 *
 * <tr><td> useSymTags</td><td>boolean</td><td>false</td><td>Gives you
 * features (pt, t, nt, c), (t, nt, c), (pt, t, c)</td></tr>
 * <tr><td> useSymWordPairs</td><td>boolean</td><td>false</td><td>Gives you
 * features (pw, nw, c)</td></tr>
 *
 * <tr><td> printClassifier</td><td>String</td><td>null</td><td>Style in which to print the classifier. One of: HighWeight, HighMagnitude, Collection, AllWeights, WeightHistogram</td></tr>
 * <tr><td> printClassifierParam</td><td>int</td><td>100</td><td>A parameter
 * to the printing style, which may give, for example the number of parameters
 * to print</td></tr>
 * <tr><td> intern</td><td>boolean</td><td>false</td><td>If true,
 * (String) intern read in data and classes and feature (pre-)names such
 * as substring features</td></tr>
 * <tr><td> intern2</td><td>boolean</td><td>false</td><td>If true, intern all (final) feature names (if only current word and ngram features are used, these will already have been interned by intern, and this is an unnecessary no-op)</td></tr>
 * <tr><td> cacheNGrams</td><td>boolean</td><td>false</td><td>If true,
 * record the NGram features that correspond to a String (under the current
 * option settings) and reuse rather than recalculating if the String is seen
 * again.</td></tr>
 * <tr><td> selfTest</td><td>boolean</td><td>false</td><td></td></tr>
 *
 * <tr><td> noMidNGrams</td><td>boolean</td><td>false</td><td>Do not include character n-gram features for n-grams that contain neither the beginning or end of the word</td></tr>
 * <tr><td> maxNGramLeng</td><td>int</td><td>-1</td><td>If this number is
 * positive, n-grams above this size will not be used in the model</td></tr>
 * <tr><td> useReverse</td><td>boolean</td><td>false</td><td></td></tr>
 * <tr><td> retainEntitySubclassification</td><td>boolean</td><td>false</td><td>If true, rather than undoing a recoding of entity tag subtypes (such as BIO variants), just leave them in the output.</td></tr>
 * <tr><td> useLemmas</td><td>boolean</td><td>false</td><td>Include the lemma of a word as a feature.</td></tr>
 * <tr><td> usePrevNextLemmas</td><td>boolean</td><td>false</td><td>Include the previous/next lemma of a word as a feature.</td></tr>
 * <tr><td> useLemmaAsWord</td><td>boolean</td><td>false</td><td>Include the lemma of a word as a feature.</td></tr>
 * <tr><td> normalizeTerms</td><td>boolean</td><td>false</td><td>If this is true, some words are normalized: day and month names are lowercased (as for normalizeTimex) and some British spellings are mapped to American English spellings (e.g., -our/-or, etc.).</td></tr>
 * <tr><td> normalizeTimex</td><td>boolean</td><td>false</td><td>If this is true, capitalization of day and month names is normalized to lowercase</td></tr>
 * <tr><td> useNB</td><td>boolean</td><td>false</td><td></td></tr>
 * <tr><td> useTypeSeqs</td><td>boolean</td><td>false</td><td>Use basic zeroeth order word shape features.</td></tr>
 * <tr><td> useTypeSeqs2</td><td>boolean</td><td>false</td><td>Add additional first and second order word shape features</td></tr>
 * <tr><td> useTypeSeqs3</td><td>boolean</td><td>false</td><td>Adds one more first order shape sequence</td></tr>
 * <tr><td> useDisjunctive</td><td>boolean</td><td>false</td><td>Include in features giving disjunctions of words anywhere in the left or right disjunctionWidth words (preserving direction but not position)</td></tr>
 * <tr><td> disjunctionWidth</td><td>int</td><td>4</td><td>The number of words on each side of the current word that are included in the disjunction features</td></tr>
 * <tr><td> useDisjunctiveShapeInteraction</td><td>boolean</td><td>false</td><td>Include in features giving disjunctions of words anywhere in the left or right disjunctionWidth words (preserving direction but not position) interacting with the word shape of the current word</td></tr>
 * <tr><td> useWideDisjunctive</td><td>boolean</td><td>false</td><td>Include in features giving disjunctions of words anywhere in the left or right wideDisjunctionWidth words (preserving direction but not position)</td></tr>
 * <tr><td> wideDisjunctionWidth</td><td>int</td><td>4</td><td>The number of words on each side of the current word that are included in the disjunction features</td></tr>
 * <tr><td> usePosition</td><td>boolean</td><td>false</td><td>Use combination of position in sentence and class as a feature</td></tr>
 * <tr><td> useBeginSent</td><td>boolean</td><td>false</td><td>Use combination of initial position in sentence and class (and word shape) as a feature.  (Doesn't seem to help.)</td></tr>
 * <tr><td> useDisjShape</td><td>boolean</td><td>false</td><td>Include features giving disjunctions of word shapes anywhere in the left or right disjunctionWidth words (preserving direction but not position)</td></tr>
 * <tr><td> useClassFeature</td><td>boolean</td><td>false</td><td>Include a feature for the class (as a class marginal).  Puts a prior on the classes which is equivalent to how often the feature appeared in the training data. This is the same thing as having a bias vector or having an always-on feature in a model.</td></tr>
 * <tr><td> useShapeConjunctions</td><td>boolean</td><td>false</td><td>Conjoin shape with tag or position</td></tr>
 * <tr><td> useWordTag</td><td>boolean</td><td>false</td><td>Include word and tag pair features</td></tr>
 * <tr><td> useLastRealWord</td><td>boolean</td><td>false</td><td>Iff the prev word is of length 3 or less, add an extra feature that combines the word two back and the current word's shape. <i>Weird!</i></td></tr>
 * <tr><td> useNextRealWord</td><td>boolean</td><td>false</td><td>Iff the next word is of length 3 or less, add an extra feature that combines the word after next and the current word's shape. <i>Weird!</i></td></tr>
 * <tr><td> useTitle</td><td>boolean</td><td>false</td><td>Match a word against a list of name titles (Mr, Mrs, etc.). Doesn't really seem to help.</td></tr>
 * <tr><td> useTitle2</td><td>boolean</td><td>false</td><td>Match a word against a better list of English name titles (Mr, Mrs, etc.). Still doesn't really seem to help.</td></tr>
 * <tr><td> useDistSim</td><td>boolean</td><td>false</td><td>Load a file of distributional similarity classes (specified by {@code distSimLexicon}) and use it for features</td></tr>
 * <tr><td> distSimLexicon</td><td>String</td><td></td><td>The file to be loaded for distsim classes.</td></tr>
 * <tr><td> distSimFileFormat</td><td>String</td><td>alexclark</td><td>Files should be formatted as tab separated rows where each row is a word/class pair.  alexclark=word first, terrykoo=class first</td></tr>
 * <tr><td> useOccurrencePatterns</td><td>boolean</td><td>false</td><td>This is a very engineered feature designed to capture multiple references to names.  If the current word isn't capitalized, followed by a non-capitalized word, and preceded by a word with alphabetic characters, it returns NO-OCCURRENCE-PATTERN.  Otherwise, if the previous word is a capitalized NNP, then if in the next 150 words you find this PW-W sequence, you get XY-NEXT-OCCURRENCE-XY, else if you find W you get XY-NEXT-OCCURRENCE-Y.  Similarly for backwards and XY-PREV-OCCURRENCE-XY and XY-PREV-OCCURRENCE-Y.  Else (if the previous word isn't a capitalized NNP), under analogous rules you get one or more of X-NEXT-OCCURRENCE-YX, X-NEXT-OCCURRENCE-XY, X-NEXT-OCCURRENCE-X, X-PREV-OCCURRENCE-YX, X-PREV-OCCURRENCE-XY, X-PREV-OCCURRENCE-X.</td></tr>
 * <tr><td> useTypeySequences</td><td>boolean</td><td>false</td><td>Some first order word shape patterns.</td></tr>
 * <tr><td> useGenericFeatures</td><td>boolean</td><td>false</td><td>If true, any features you include in the map will be incorporated into the model with values equal to those given in the file; values are treated as strings unless you use the "realValued" option (described below)</td></tr>
 * <tr><td> justify</td><td>boolean</td><td>false</td><td>Print out all
 * feature/class pairs and their weight, and then for each input data
 * point, print justification (weights) for active features. Only implemented for CMMClassifier.</td></tr>
 * <tr><td> normalize</td><td>boolean</td><td>false</td><td>For the CMMClassifier (only) if this is true then the Scorer normalizes scores as probabilities.</td></tr>
 * <tr><td> useHuber</td><td>boolean</td><td>false</td><td>Use a Huber loss prior rather than the default quadratic loss.</td></tr>
 * <tr><td> useQuartic</td><td>boolean</td><td>false</td><td>Use a Quartic prior rather than the default quadratic loss.</td></tr>
 * <tr><td> sigma</td><td>double</td><td>1.0</td><td></td></tr>
 * <tr><td> epsilon</td><td>double</td><td>0.01</td><td>Used only as a parameter in the Huber loss: this is the distance from 0 at which the loss changes from quadratic to linear</td></tr>
 * <tr><td> beamSize</td><td>int</td><td>30</td><td></td></tr>
 * <tr><td> maxLeft</td><td>int</td><td>2</td><td>The number of things to the left that have to be cached to run the Viterbi algorithm: the maximum context of class features used.</td></tr>
 * <tr><td> maxRight</td><td>int</td><td>2</td><td>The number of things to the right that have to be cached to run the Viterbi algorithm: the maximum context of class features used.  The maximum possible clique size to use is (maxLeft + maxRight + 1)</td></tr>
 * <tr><td> dontExtendTaggy</td><td>boolean</td><td>false</td><td>Don't extend the range of useTaggySequences when maxLeft is increased.</td></tr>
 * <tr><td> numFolds </td><td>int</td><td>1</td><td>The number of folds to use for cross-validation. CURRENTLY NOT IMPLEMENTED.</td></tr>
 * <tr><td> startFold </td><td>int</td><td>1</td><td>The starting fold to run. CURRENTLY NOT IMPLEMENTED.</td></tr>
 * <tr><td> endFold </td><td>int</td><td>1</td><td>The last fold to run. CURRENTLY NOT IMPLEMENTED.</td></tr>
 * <tr><td> mergeTags </td><td>boolean</td><td>false</td><td>Whether to merge B- and I- tags.</td></tr>
 * <tr><td> splitDocuments</td><td>boolean</td><td>true</td><td>Whether or not to split the data into separate documents for training/testing</td></tr>
 * <tr><td> maxDocSize</td><td>int</td><td>10000</td><td>If this number is greater than 0, attempt to split documents bigger than this value into multiple documents at sentence boundaries during testing; otherwise do nothing.</td></tr>
 * </table>
 * <p>
 * Note: flags/properties overwrite left to right.  That is, the parameter
 * setting specified <i>last</i> is the one used.
 * </p>
 * <pre>
 * DOCUMENTATION ON FEATURE TEMPLATES
 * <br>
 * w = word
 * t = tag
 * p = position (word index in sentence)
 * c = class
 * p = paren
 * g = gazette
 * a = abbrev
 * s = shape
 * r = regent (dependency governor)
 * h = head word of phrase
 * n(w) = ngrams from w
 * g(w) = gazette entries containing w
 * l(w) = length of w
 * o(...) = occurrence patterns of words
 * <br>
 * useReverse reverses meaning of prev, next everywhere below (on in macro)
 * <br>
 * "Prolog" booleans: , = AND and ; = OR
 * <br>
 * Mac: Y = turned on in -macro,
 *      + = additional positive things relative to -macro for CoNLL NERFeatureFactory
 *          (perhaps none...)
 *      - = Known negative for CoNLL NERFeatureFactory relative to -macro
 * <br>
 * Bio: + = additional things that are positive for BioCreative
 *      - = things negative relative to -macro
 * <br>
 * HighMagnitude: There are no (0) to a few (+) to many (+++) high weight
 * features of this template. (? = not used in goodCoNLL, but usually = 0)
 * <br>
 * Feature              Mac Bio CRFFlags                   HighMagnitude
 * ---------------------------------------------------------------------
 * w,c                    Y     useWord                    0 (useWord is almost useless with unlimited ngram features, but helps a fraction in goodCoNLL, if only because of prior fiddling
 * p,c                          usePosition                ?
 * p=0,c                        useBeginSent               ?
 * p=0,s,c                      useBeginSent               ?
 * t,c                    Y     useTags                    ++
 * pw,c                   Y     usePrev                    +
 * pt,c                   Y     usePrev,useTags            0
 * nw,c                   Y     useNext                    ++
 * nt,c                   Y     useNext,useTags            0
 * pw,w,c                 Y     useWordPairs               +
 * w,nw,c                 Y     useWordPairs               +
 * pt,t,nt,c                    useSymTags                 ?
 * t,nt,c                       useSymTags                 ?
 * pt,t,c                       useSymTags                 ?
 * pw,nw,c                      useSymWordPairs            ?
 * <br>
 * pc,c                   Y     usePrev,useSequences,usePrevSequences   +++
 * pc,w,c                 Y     usePrev,useSequences,usePrevSequences   0
 * nc,c                         useNext,useSequences,useNextSequences   ?
 * w,nc,c                       useNext,useSequences,useNextSequences   ?
 * pc,nc,c                      useNext,usePrev,useSequences,usePrevSequences,useNextSequences  ?
 * w,pc,nc,c                    useNext,usePrev,useSequences,usePrevSequences,useNextSequences   ?
 * <br>
 * (pw;p2w;p3w;p4w),c        +  useDisjunctive  (out to disjunctionWidth now)   +++
 * (nw;n2w;n3w;n4w),c        +  useDisjunctive  (out to disjunctionWidth now)   ++++
 * (pw;p2w;p3w;p4w),s,c      +  useDisjunctiveShapeInteraction          ?
 * (nw;n2w;n3w;n4w),s,c      +  useDisjunctiveShapeInteraction          ?
 * (pw;p2w;p3w;p4w),c        +  useWideDisjunctive (to wideDisjunctionWidth)   ?
 * (nw;n2w;n3w;n4w),c        +  useWideDisjunctive (to wideDisjunctionWidth)   ?
 * (ps;p2s;p3s;p4s),c           useDisjShape  (out to disjunctionWidth now)   ?
 * (ns;n2s;n3s;n4s),c           useDisjShape  (out to disjunctionWidth now)   ?
 * <br>
 * pt,pc,t,c              Y     useTaggySequences                        +
 * p2t,p2c,pt,pc,t,c      Y     useTaggySequences,maxLeft&gt;=2          +
 * p3t,p3c,p2t,p2c,pt,pc,t,c Y  useTaggySequences,maxLeft&gt;=3,!dontExtendTaggy   ?
 * p2c,pc,c               Y     useLongSequences                         ++
 * p3c,p2c,pc,c           Y     useLongSequences,maxLeft&gt;=3           ?
 * p4c,p3c,p2c,pc,c       Y     useLongSequences,maxLeft&gt;=4           ?
 * p2c,pc,c,pw=BOUNDARY         useBoundarySequences                     0 (OK, but!)
 * <br>
 * p2t,pt,t,c             -     useExtraTaggySequences                   ?
 * p3t,p2t,pt,t,c         -     useExtraTaggySequences                   ?
 * <br>
 * p2t,pt,t,s,p2c,pc,c    -     useTaggySequencesShapeInteraction        ?
 * p3t,p2t,pt,t,s,p3c,p2c,pc,c  useTaggySequencesShapeInteraction        ?
 * <br>
 * s,pc,c                 Y     useTypeySequences                        ++
 * ns,pc,c                Y     useTypeySequences  // error for ps? not? 0
 * ps,pc,s,c              Y     useTypeySequences                        0
 * // p2s,p2c,ps,pc,s,c      Y     useTypeySequences,maxLeft&gt;=2 // duplicated a useTypeSeqs2 feature
 * <br>
 * n(w),c                 Y     useNGrams (noMidNGrams, MaxNGramLeng, lowercaseNGrams, dehyphenateNGrams)   +++
 * n(w),s,c                     useNGrams,conjoinShapeNGrams             ?
 * <br>
 * g,c                        + useGazFeatures   // test refining this?   ?
 * pg,pc,c                    + useGazFeatures                           ?
 * ng,c                       + useGazFeatures                           ?
 * // pg,g,c                    useGazFeatures                           ?
 * // pg,g,ng,c                 useGazFeatures                           ?
 * // p2g,p2c,pg,pc,g,c         useGazFeatures                           ?
 * g,w,c                        useMoreGazFeatures                       ?
 * pg,pc,g,c                    useMoreGazFeatures                       ?
 * g,ng,c                       useMoreGazFeatures                       ?
 * <br>
 * g(w),c                       useGazette,sloppyGazette (contains same word)   ?
 * g(w),[pw,nw,...],c           useGazette,cleanGazette (entire entry matches)   ?
 * <br>
 * s,c                    Y     wordShape &gt;= 0                       +++
 * ps,c                   Y     wordShape &gt;= 0,useTypeSeqs           +
 * ns,c                   Y     wordShape &gt;= 0,useTypeSeqs           +
 * pw,s,c                 Y     wordShape &gt;= 0,useTypeSeqs           +
 * s,nw,c                 Y     wordShape &gt;= 0,useTypeSeqs           +
 * ps,s,c                 Y     wordShape &gt;= 0,useTypeSeqs           0
 * s,ns,c                 Y     wordShape &gt;= 0,useTypeSeqs           ++
 * ps,s,ns,c              Y     wordShape &gt;= 0,useTypeSeqs           ++
 * pc,ps,s,c              Y     wordShape &gt;= 0,useTypeSeqs,useTypeSeqs2   0
 * p2c,p2s,pc,ps,s,c      Y     wordShape &gt;= 0,useTypeSeqs,useTypeSeqs2,maxLeft&gt;=2   +++
 * pc,ps,s,ns,c                 wordShape &gt;= 0,useTypeSeqs,useTypeSeqs3   ?
 * <br>
 * p2w,s,c if l(pw) &lt;= 3 Y     useLastRealWord // weird features, but work   0
 * n2w,s,c if l(nw) &lt;= 3 Y     useNextRealWord                        ++
 * o(pw,w,nw),c           Y     useOccurrencePatterns // don't fully grok but has to do with capitalized name patterns   ++
 * <br>
 * a,c                          useAbbr;useMinimalAbbr
 * pa,a,c                       useAbbr
 * a,na,c                       useAbbr
 * pa,a,na,c                    useAbbr
 * pa,pc,a,c                    useAbbr;useMinimalAbbr
 * p2a,p2c,pa,pc,a              useAbbr
 * w,a,c                        useMinimalAbbr
 * p2a,p2c,a,c                  useMinimalAbbr
 * <br>
 * RESTR. w,(pw,pc;p2w,p2c;p3w,p3c;p4w,p4c)   + useParenMatching,maxLeft&gt;=n
 * <br>
 * c                          - useClassFeature
 * <br>
  * p,s,c                      - useShapeConjunctions
 * t,s,c                      - useShapeConjunctions
 * <br>
 * w,t,c                      + useWordTag                      ?
 * w,pt,c                     + useWordTag                      ?
 * w,nt,c                     + useWordTag                      ?
 * <br>
 * r,c                          useNPGovernor (only for baseNP words)
 * r,t,c                        useNPGovernor (only for baseNP words)
 * h,c                          useNPHead (only for baseNP words)
 * h,t,c                        useNPHead (only for baseNP words)
 * <br>
 * </pre>
 *
 * @author Dan Klein
 * @author Jenny Finkel
 * @author Christopher Manning
 * @author Shipra Dingare
 * @author Huy Nguyen
 * @author Mengqiu Wang
 */
public class NERFeatureFactory<IN extends CoreLabel> extends FeatureFactory<IN>  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(NERFeatureFactory.class);

  private static final long serialVersionUID = -2329726064739185544L;

  public NERFeatureFactory() {
    super();
  }

  @Override
  public void init(SeqClassifierFlags flags) {
    super.init(flags);
    initGazette();
    if (flags.useDistSim) {
      initLexicon(flags);
    }
  }

  /**
   * Extracts all the features from the input data at a certain index.
   *
   * @param cInfo The complete data set as a List of WordInfo
   * @param loc  The index at which to extract features.
   */
  @Override
  public Collection<String> getCliqueFeatures(PaddedList<IN> cInfo, int loc, Clique clique) {
    Set<String> features = Generics.newHashSet(100);
    FeatureCollector c = new FeatureCollector(features);
    String domain = cInfo.get(0).get(CoreAnnotations.DomainAnnotation.class);

//    log.info(doFE+"\t"+domain);

    // there are two special cases below, because 2 cliques have 2 names
    c.setDomain(domain);
    if (clique == cliqueC) {
      //200710: tried making this clique null; didn't improve performance (rafferty)
      featuresC(cInfo, loc, c);
    } else if (clique == cliqueCpC) {
      featuresCpC(cInfo, loc, c);
      featuresCnC(cInfo, loc - 1, c);
    } else if (clique == cliqueCp2C) {
      featuresCp2C(cInfo, loc, c);
    } else if (clique == cliqueCp3C) {
      featuresCp3C(cInfo, loc, c);
    } else if (clique == cliqueCp4C) {
      featuresCp4C(cInfo, loc, c);
    } else if (clique == cliqueCp5C) {
      featuresCp5C(cInfo, loc, c);
    } else if (clique == cliqueCpCp2C) {
      featuresCpCp2C(cInfo, loc, c);
      featuresCpCnC(cInfo, loc - 1, c);
    } else if (clique == cliqueCpCp2Cp3C) {
      featuresCpCp2Cp3C(cInfo, loc, c);
    } else if (clique == cliqueCpCp2Cp3Cp4C) {
      featuresCpCp2Cp3Cp4C(cInfo, loc, c);
    } else {
      throw new IllegalArgumentException("Unknown clique: " + clique);
    }

    // log.info(StringUtils.join(features,"\n")+"\n");
    return features;
  }

  /**
   * This class handles collecting features into a set, in a more memory efficient way.
   *
   * The benefits arise from handling the concatenation and suffix appending re-using a
   * single buffer, rather than repeatedly concatenating strings.
   *
   * This class is <em>not thread safe</em>, but you are unlikely to want to parallelize
   * at this low level anyway.
   *
   * @author Erich Schubert
   */
  protected static class FeatureCollector {
    /** Suffix to append */
    String suffix = null;

    /** Domain (may be null) */
    String domain = null;

    /** String builder */
    private StringBuilder buf = new StringBuilder(100);

    /** Output collection */
    Set<String> collection;

    /**
     * Constructor
     *
     * @param output Output collection
     */
    public FeatureCollector(Set<String> output) {
      this.collection = output;
    }

    /**
     * Set the suffix to append to each token (separated by "|")
     *
     * @param suffix Suffix to use
     */
    public FeatureCollector setSuffix(String suffix) {
      assert suffix != null && !suffix.isEmpty() : "Only non-empty suffixes are supported right now";
      this.suffix = suffix;
      return this;
    }

    /**
     * Additional suffix domain.
     *
     * If a domain is set, then all features will be duplicated
     * with the alternate suffix "|" + domain + "-" + suffix".
     *
     * @param domain Suffix domain
     */
    public void setDomain(String domain) {
      this.domain = domain;
    }

    /** Handle string interning. A no-op right now. */
    private String intern(String s) {
      return s; // flags.intern2 ? s.intern() : s;
    }

    /**
     * Begin a new feature construction.
     *
     * @return this
     */
    public FeatureCollector build() {
      assert buf.length() == 0 : "Previous feature not added? " + buf.toString();
      buf.setLength(0);
      return this;
    }

    /**
     * Append to the current feature name
     *
     * @param s String fragment
     * @return this
     */
    public FeatureCollector append(String s) {
      buf.append(s);
      return this;
    }

    /**
     * Append to the current feature name
     *
     * @param c Character
     * @return this
     */
    public FeatureCollector append(char c) {
      buf.append(c);
      return this;
    }

    /**
     * Append '-' to the current feature name
     *
     * @return this
     */
    public FeatureCollector dash() {
      buf.append('-');
      return this;
    }

    /**
     * End the current feature, and add with suffixes.
     */
    public void add() {
      final int l = buf.append('|').length();
      collection.add(intern(buf.append(suffix).toString()));
      if (domain != null) {
        buf.setLength(l);
        collection.add(intern(buf.append(domain).append('-').append(suffix).toString()));
      }
      buf.setLength(0);
    }

    /**
     * Add a feature (+ suffix).
     *
     * @param feat Feature
     */
    public void add(String feat) {
      build().append(feat).add();
    }
  }

  // TODO: when breaking serialization, it seems like it would be better to
  // move the lexicon into (Abstract)SequenceClassifier and to do this
  // annotation as part of the ObjectBankWrapper.  But note that it is
  // serialized in this object currently and it would then need to be
  // serialized elsewhere or loaded each time
  private Map<String,String> lexicon;

  private void initLexicon(SeqClassifierFlags flags) {
    if (flags.distSimLexicon == null) {
      return;
    }
    if (lexicon != null) {
      return;
    }
    Timing timing = new Timing();
    // should work better than String.intern()
    // interning the strings like this means they should be serialized
    // in an interned manner, saving disk space and also memory when
    // loading them back in
    Interner<String> interner = new Interner<>();
    lexicon = Generics.newHashMap(10000);
    boolean terryKoo = "terryKoo".equals(flags.distSimFileFormat);
    Pattern p = Pattern.compile(terryKoo ? "\\t" : "\\s+");
    for (String line : ObjectBank.getLineIterator(flags.distSimLexicon,
                                                  flags.inputEncoding)) {
      String word;
      String wordClass;
      if (terryKoo) {
        String[] bits = p.split(line);
        word = bits[1];
        wordClass = bits[0];
        if (flags.distSimMaxBits > 0 && wordClass.length() > flags.distSimMaxBits) {
          wordClass = wordClass.substring(0, flags.distSimMaxBits);
        }
      } else {
        // "alexClark"
        String[] bits = p.split(line);
        word = bits[0];
        wordClass = bits[1];
      }
      if ( ! flags.casedDistSim) {
        word = word.toLowerCase();
      }
      if (flags.numberEquivalenceDistSim) {
        word = WordShapeClassifier.wordShape(word, WordShapeClassifier.WORDSHAPEDIGITS);
      }
      lexicon.put(word, interner.intern(wordClass));
    }
    timing.done(log, "Loading distsim lexicon from " + flags.distSimLexicon);
  }

  public String describeDistsimLexicon() {
    if (lexicon == null) {
      return "No distsim lexicon";
    } else {
      return "Distsim lexicon of size " + lexicon.size();
    }
  }

  private void distSimAnnotate(PaddedList<IN> info) {
    for (CoreLabel fl : info) {
      if (fl.containsKey(CoreAnnotations.DistSimAnnotation.class)) { return; }
      String word = getWord(fl);
      if ( ! flags.casedDistSim) {
        word = word.toLowerCase();
      }
      if (flags.numberEquivalenceDistSim) {
        word = WordShapeClassifier.wordShape(word, WordShapeClassifier.WORDSHAPEDIGITS);
      }
      String distSim = lexicon.get(word);
      if (distSim == null) {
        distSim = flags.unknownWordDistSimClass;
      }
      fl.set(CoreAnnotations.DistSimAnnotation.class, distSim);
    }
  }


  private Map<String,Collection<String>> wordToSubstrings = Generics.newHashMap();

  public void clearMemory() {
    wordToSubstrings = Generics.newHashMap();
    lexicon = null;
  }

  private static String dehyphenate(String str) {
    // don't take out leading or ending ones, just internal
    // and remember padded with < > characters
    String retStr = str;
    int leng = str.length();
    int hyphen = 2;
    do {
      hyphen = retStr.indexOf('-', hyphen);
      if (hyphen >= 0 && hyphen < leng - 2) {
        retStr = retStr.substring(0, hyphen) + retStr.substring(hyphen + 1);
      } else {
        hyphen = -1;
      }
    } while (hyphen >= 0);
    return retStr;
  }

  private static String greekify(String str) {
    // don't take out leading or ending ones, just internal
    // and remember padded with < > characters

    String pattern = "(alpha)|(beta)|(gamma)|(delta)|(epsilon)|(zeta)|(kappa)|(lambda)|(rho)|(sigma)|(tau)|(upsilon)|(omega)";

    Pattern p = Pattern.compile(pattern);
    Matcher m = p.matcher(str);
    return m.replaceAll("~");
  }

  /* end methods that do transformations */

  /*
   * static booleans that check strings for certain qualities *
   */

  // cdm: this could be improved to handle more name types, such as
  // O'Reilly, DeGuzman, etc. (need a little classifier?!?)
  private static boolean isNameCase(String str) {
    if (str.length() < 2) {
      return false;
    }
    if (!(Character.isUpperCase(str.charAt(0)) || Character.isTitleCase(str.charAt(0)))) {
      return false;
    }
    for (int i = 1; i < str.length(); i++) {
      if (Character.isUpperCase(str.charAt(i))) {
        return false;
      }
    }
    return true;
  }

  private static boolean noUpperCase(String str) {
    if (str.length() < 1) {
      return false;
    }
    for (int i = 0; i < str.length(); i++) {
      if (Character.isUpperCase(str.charAt(i))) {
        return false;
      }
    }
    return true;
  }

  private static boolean hasLetter(String str) {
    if (str.length() < 1) {
      return false;
    }
    for (int i = 0; i < str.length(); i++) {
      if (Character.isLetter(str.charAt(i))) {
        return true;
      }
    }
    return false;
  }

  private static final Pattern ordinalPattern = Pattern.compile("(?:(?:first|second|third|fourth|fifth|"+
                                                          "sixth|seventh|eighth|ninth|tenth|"+
                                                          "eleventh|twelfth|thirteenth|"+
                                                          "fourteenth|fifteenth|sixteenth|"+
                                                          "seventeenth|eighteenth|nineteenth|"+
                                                          "twenty|twentieth|thirty|thirtieth|"+
                                                          "forty|fortieth|fifty|fiftieth|"+
                                                          "sixty|sixtieth|seventy|seventieth|"+
                                                          "eighty|eightieth|ninety|ninetieth|"+
                                                          "one|two|three|four|five|six|seven|"+
                                                          "eight|nine|hundred|hundredth)-?)+|[0-9]+(?:st|nd|rd|th)", Pattern.CASE_INSENSITIVE);


  private static final Pattern numberPattern = Pattern.compile("[0-9]+");
  private static final Pattern ordinalEndPattern = Pattern.compile("(?:st|nd|rd|th)", Pattern.CASE_INSENSITIVE);

  private boolean isOrdinal(List<? extends CoreLabel> wordInfos, int pos) {
    CoreLabel c = wordInfos.get(pos);
    String cWord = getWord(c);
    Matcher m = ordinalPattern.matcher(cWord);
    if (m.matches()) { return true; }
    m = numberPattern.matcher(cWord);
    if (m.matches()) {
      if (pos+1 < wordInfos.size()) {
        CoreLabel n = wordInfos.get(pos+1);
        m = ordinalEndPattern.matcher(getWord(n));
        if (m.matches()) { return true; }
      }
      return false;
    }

    m = ordinalEndPattern.matcher(cWord);
    if (m.matches()) {
      if (pos > 0) {
        CoreLabel p = wordInfos.get(pos-1);
        m = numberPattern.matcher(getWord(p));
        if (m.matches()) { return true; }
      }
    }
    if (cWord.equals("-")) {
      if (pos+1 < wordInfos.size() && pos > 0) {
        CoreLabel p = wordInfos.get(pos-1);
        CoreLabel n = wordInfos.get(pos+1);
        m = ordinalPattern.matcher(getWord(p));
        if (m.matches()) {
          m = ordinalPattern.matcher(getWord(n));
          if (m.matches()) {
            return true;
          }
        }
      }
    }
    return false;
  }

  /* end static booleans that check strings for certain qualities */

  /**
   * Gazette Stuff.
   */
  private static class GazetteInfo implements Serializable {
    final String feature;
    final int loc;
    final String[] words;
    private static final long serialVersionUID = -5903728481621584810L;
    public GazetteInfo(String feature, int loc, String[] words) {
      this.feature = feature;
      this.loc = loc;
      this.words = words;
    }
  } // end class GazetteInfo

  private Map<String,Collection<String>> wordToGazetteEntries = Generics.newHashMap();
  private Map<String,Collection<GazetteInfo>> wordToGazetteInfos = Generics.newHashMap();

  /** Reads a gazette file.  Each line of it consists of a class name
   *  (a String not containing whitespace characters), followed by whitespace
   *  characters followed by a phrase, which is one or more tokens separated
   *  by a single space.
   *
   *  @param in Where to read the gazette from
   *  @throws IOException If IO errors
   */
  private void readGazette(BufferedReader in) throws IOException {
    Pattern p = Pattern.compile("^(\\S+)\\s+(.+)$");
    for (String line; (line = in.readLine()) != null; ) {
      Matcher m = p.matcher(line);
      if (m.matches()) {
        String type = intern(m.group(1));
        String phrase = m.group(2);
        String[] words = phrase.split(" ");
        for (int i = 0; i < words.length; i++) {
          String word = intern(words[i]);
          if (flags.sloppyGazette) {
            Collection<String> entries = wordToGazetteEntries.get(word);
            if (entries == null) {
              entries = Generics.newHashSet();
              wordToGazetteEntries.put(word, entries);
            }
            String feature = intern(type + "-GAZ" + words.length);
            entries.add(feature);
            feature = intern(type + "-GAZ");
            entries.add(feature);
          }
          if (flags.cleanGazette) {
            Collection<GazetteInfo> infos = wordToGazetteInfos.get(word);
            if (infos == null) {
              infos = Generics.newHashSet();
              wordToGazetteInfos.put(word, infos);
            }
            GazetteInfo info = new GazetteInfo(intern(type + "-GAZ" + words.length), i, words);
            infos.add(info);
            info = new GazetteInfo(intern(type + "-GAZ"), i, words);
            infos.add(info);
          }
        }
      }
    }
  }

  private Set<Class<? extends GenericAnnotation<?>>> genericAnnotationKeys; // = null; //cache which keys are generic annotations so we don't have to do too many instanceof checks

  @SuppressWarnings({"unchecked", "SuspiciousMethodCalls"})
  private void makeGenericKeyCache(CoreLabel c) {
    genericAnnotationKeys = Generics.newHashSet();
    for (Class<?> key : c.keySet()) {
      if (CoreLabel.genericValues.containsKey(key)) {
        Class<? extends GenericAnnotation<?>> genKey = (Class<? extends GenericAnnotation<?>>) key;
        genericAnnotationKeys.add(genKey);
      }
    }
  }

  private Set<String> lastNames; // = null;
  private Set<String> maleNames; // = null;
  private Set<String> femaleNames; // = null;

  private final Pattern titlePattern = Pattern.compile("(?:Mr|Ms|Mrs|Dr|Miss|Sen|Judge|Sir)\\.?"); // todo: should make static final and add more titles
  private static final Pattern titlePattern2 = Pattern.compile("(?i:Mr|Mrs|Ms|Miss|Drs?|Profs?|Sens?|Reps?|Attys?|Lt|Col|Gen|Messrs|Govs?|Adm|Rev|Maj|Sgt|Cpl|Pvt|Capt|Ste?|Ave|Pres|Lieut|Hon|Brig|Co?mdr|Pfc|Spc|Supts?|Det|Mt|Ft|Adj|Adv|Asst|Assoc|Ens|Insp|Mlle|Mme|Msgr|Sfc)\\.?");

  private static final Pattern splitSlashHyphenWordsPattern = Pattern.compile("[-/]");

  private void generateSlashHyphenFeatures(String word, String fragSuffix, String wordSuffix, FeatureCollector out) {
    String[] bits = splitSlashHyphenWordsPattern.split(word);
    for (String bit : bits) {
      if (flags.slashHyphenTreatment == SeqClassifierFlags.SlashHyphenEnum.WFRAG) {
        out.build().append(bit).append(fragSuffix).add();
      } else if (flags.slashHyphenTreatment == SeqClassifierFlags.SlashHyphenEnum.BOTH) {
        out.build().append(bit).append(fragSuffix).add();
        out.build().append(bit).append(wordSuffix).add();
      } else {
        // option WORD
        out.build().append(bit).append(wordSuffix).add();
      }
    }
  }


  protected void featuresC(PaddedList<IN> cInfo, int loc, FeatureCollector out) {
    out.setSuffix("C");

    CoreLabel p3 = cInfo.get(loc - 3);
    CoreLabel p2 = cInfo.get(loc - 2);
    CoreLabel p = cInfo.get(loc - 1);
    CoreLabel c = cInfo.get(loc);
    CoreLabel n = cInfo.get(loc + 1);
    CoreLabel n2 = cInfo.get(loc + 2);

    String cWord = getWord(c);
    String pWord = getWord(p);
    String nWord = getWord(n);
    String cShape = c.getString(CoreAnnotations.ShapeAnnotation.class);
    String pShape = p.getString(CoreAnnotations.ShapeAnnotation.class);
    String nShape = n.getString(CoreAnnotations.ShapeAnnotation.class);

    if (flags.useDistSim) {
      distSimAnnotate(cInfo);
    }

    if (flags.useBagOfWords) {
      for (IN word : cInfo) {
        out.build().append(getWord(word)).append("-BAGOFWORDS").add();
      }
    }

    if (flags.useDistSim && flags.useMoreTags) {
      out.build().append(p.get(CoreAnnotations.DistSimAnnotation.class)).dash().append(cWord).append("-PDISTSIM-CWORD").add();
    }

    if (flags.useDistSim) {
      out.build().append(c.get(CoreAnnotations.DistSimAnnotation.class)).append("-DISTSIM").add();
    }

    if (flags.useTitle) {
      if (titlePattern.matcher(cWord).matches()) {
        out.add("IS_TITLE");
      }
    }
    else if (flags.useTitle2) {
      if (titlePattern2.matcher(cWord).matches()) {
        out.add("IS_TITLE");
      }
    }

    if (flags.slashHyphenTreatment != SeqClassifierFlags.SlashHyphenEnum.NONE) {
      if (flags.useWord) {
        generateSlashHyphenFeatures(cWord, "-WFRAG", "-WORD", out);
      }
    }

    if (flags.useInternal && flags.useExternal ) {
      if (flags.useWord) {
        out.build().append(cWord).append("-WORD").add();
      }

      if (flags.use2W) {
        out.build().append(getWord(p2)).append("-P2W").add();
        out.build().append(getWord(n2)).append("-N2W").add();
      }

      if (flags.useLC) {
        out.build().append(cWord.toLowerCase()).append("-CL").add();
        out.build().append(pWord.toLowerCase()).append("-PL").add();
        out.build().append(nWord.toLowerCase()).append("-NL").add();
      }

      if (flags.useUnknown) { // for true casing
        out.build().append(c.get(CoreAnnotations.UnknownAnnotation.class)).append("-UNKNOWN").add();
        out.build().append(p.get(CoreAnnotations.UnknownAnnotation.class)).append("-PUNKNOWN").add();
        out.build().append(n.get(CoreAnnotations.UnknownAnnotation.class)).append("-NUNKNOWN").add();
      }

      if (flags.useLemmas) {
        String lem = c.getString(CoreAnnotations.LemmaAnnotation.class);
        if (!lem.isEmpty()) {
          out.build().append(lem).append("-LEM").add();
        }
      }
      if (flags.usePrevNextLemmas) {
        String plem = p.getString(CoreAnnotations.LemmaAnnotation.class);
        String nlem = n.getString(CoreAnnotations.LemmaAnnotation.class);
        if (!plem.isEmpty()) {
          out.build().append(plem).append("-PLEM").add();
        }
        if (!nlem.isEmpty()) {
          out.build().append(nlem).append("-NLEM").add();
        }
      }

      if (flags.checkNameList) {
        try {
          if (lastNames == null) {
            lastNames = Generics.newHashSet();
            for (String line : ObjectBank.getLineIterator(flags.lastNameList)) {
              lastNames.add(line.split("\\s+")[0]);
            }
          }
          if (maleNames == null) {
            maleNames = Generics.newHashSet();
            for (String line : ObjectBank.getLineIterator(flags.maleNameList)) {
              maleNames.add(line.split("\\s+")[0]);
            }
          }
          if (femaleNames == null) {
            femaleNames = Generics.newHashSet();
            for (String line : ObjectBank.getLineIterator(flags.femaleNameList)) {
              femaleNames.add(line.split("\\s+")[0]);
            }
          }

          String name = cWord.toUpperCase();
          if (lastNames.contains(name)) {
            out.add("LAST_NAME");
          }

          if (maleNames.contains(name)) {
            out.add("MALE_NAME");
          }

          if (femaleNames.contains(name)) {
            out.add("FEMALE_NAME");
          }
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }

      if (flags.binnedLengths != null) {
        int len = cWord.length(), beg = -1, end = -1;
        for (int i = 0; i < flags.binnedLengths.length; i++) {
          if (len <= flags.binnedLengths[i]) {
            beg = i == 0 ? 1 : flags.binnedLengths[i - 1];
            end = flags.binnedLengths[i];
            break;
          }
        }
        if (beg < 0) {
          beg = flags.binnedLengths[flags.binnedLengths.length - 1];
        }
        out.build().append("Len-").append(Integer.toString(beg)).dash().append(end > 0 ? Integer.toString(end) : "Inf").add();
      }

      if (flags.useABGENE) {
        out.build().append(c.get(CoreAnnotations.AbgeneAnnotation.class)).append("-ABGENE").add();
        out.build().append(p.get(CoreAnnotations.AbgeneAnnotation.class)).append("-PABGENE").add();
        out.build().append(n.get(CoreAnnotations.AbgeneAnnotation.class)).append("-NABGENE").add();
      }

      if (flags.useABSTRFreqDict) {
        out.build().append(c.get(CoreAnnotations.AbstrAnnotation.class)).append("-ABSTRACT").append(c.get(CoreAnnotations.FreqAnnotation.class)).append("-FREQ").append(c.getString(CoreAnnotations.PartOfSpeechAnnotation.class)).append("-TAG").add();
        out.build().append(c.get(CoreAnnotations.AbstrAnnotation.class)).append("-ABSTRACT").append(c.get(CoreAnnotations.DictAnnotation.class)).append("-DICT").append(c.getString(CoreAnnotations.PartOfSpeechAnnotation.class)).append("-TAG").add();
        out.build().append(c.get(CoreAnnotations.AbstrAnnotation.class)).append("-ABSTRACT").append(c.get(CoreAnnotations.DictAnnotation.class)).append("-DICT").append(c.get(CoreAnnotations.FreqAnnotation.class)).append("-FREQ").append(c.getString(CoreAnnotations.PartOfSpeechAnnotation.class)).append("-TAG").add();
      }

      if (flags.useABSTR) {
        out.build().append(c.get(CoreAnnotations.AbstrAnnotation.class)).append("-ABSTRACT").add();
        out.build().append(p.get(CoreAnnotations.AbstrAnnotation.class)).append("-PABSTRACT").add();
        out.build().append(n.get(CoreAnnotations.AbstrAnnotation.class)).append("-NABSTRACT").add();
      }

      if (flags.useGENIA) {
        out.build().append(c.get(CoreAnnotations.GeniaAnnotation.class)).append("-GENIA").add();
        out.build().append(p.get(CoreAnnotations.GeniaAnnotation.class)).append("-PGENIA").add();
        out.build().append(n.get(CoreAnnotations.GeniaAnnotation.class)).append("-NGENIA").add();
      }
      if (flags.useWEBFreqDict) {
        out.build().append(c.get(CoreAnnotations.WebAnnotation.class)).append("-WEB").append(c.get(CoreAnnotations.FreqAnnotation.class)).append("-FREQ").append(c.getString(CoreAnnotations.PartOfSpeechAnnotation.class)).append("-TAG").add();
        out.build().append(c.get(CoreAnnotations.WebAnnotation.class)).append("-WEB").append(c.get(CoreAnnotations.DictAnnotation.class)).append("-DICT").append(c.getString(CoreAnnotations.PartOfSpeechAnnotation.class)).append("-TAG").add();
        out.build().append(c.get(CoreAnnotations.WebAnnotation.class)).append("-WEB").append(c.get(CoreAnnotations.DictAnnotation.class)).append("-DICT").append(c.get(CoreAnnotations.FreqAnnotation.class)).append("-FREQ").append(c.getString(CoreAnnotations.PartOfSpeechAnnotation.class)).append("-TAG").add();
      }

      if (flags.useWEB) {
        out.build().append(c.get(CoreAnnotations.WebAnnotation.class)).append("-WEB").add();
        out.build().append(p.get(CoreAnnotations.WebAnnotation.class)).append("-PWEB").add();
        out.build().append(n.get(CoreAnnotations.WebAnnotation.class)).append("-NWEB").add();
      }

      if (flags.useIsURL) {
        out.build().append(c.get(CoreAnnotations.IsURLAnnotation.class)).append("-ISURL").add();
      }
      if (flags.useEntityRule) {
        out.build().append(c.get(CoreAnnotations.EntityRuleAnnotation.class)).append("-ENTITYRULE").add();
      }
      if (flags.useEntityTypes) {
        out.build().append(c.get(CoreAnnotations.EntityTypeAnnotation.class)).append("-ENTITYTYPE").add();
      }
      if (flags.useIsDateRange) {
        out.build().append(c.get(CoreAnnotations.IsDateRangeAnnotation.class)).append("-ISDATERANGE").add();
      }
      if (flags.useABSTRFreq) {
        out.build().append(c.get(CoreAnnotations.AbstrAnnotation.class)).append("-ABSTRACT").append(c.get(CoreAnnotations.FreqAnnotation.class)).append("-FREQ").add();
      }
      if (flags.useFREQ) {
        out.build().append(c.get(CoreAnnotations.FreqAnnotation.class)).append("-FREQ").add();
      }
      if (flags.useMoreTags) {
        out.build().append(p.getString(CoreAnnotations.PartOfSpeechAnnotation.class)).dash().append(cWord).append("-PTAG-CWORD").add();
      }
      if (flags.usePosition) {
        out.build().append(c.get(CoreAnnotations.PositionAnnotation.class)).append("-POSITION").add();
      }
      if (flags.useBeginSent) {
        String pos = c.get(CoreAnnotations.PositionAnnotation.class);
        if ("0".equals(pos)) {
          out.add("BEGIN-SENT");
          out.build().append(cShape).append("-BEGIN-SENT").add();
        } else if (Integer.toString(cInfo.size() - 1).equals(pos)) {
          out.add("END-SENT");
          out.build().append(cShape).append("-END-SENT").add();
        } else {
          out.add("IN-SENT");
          out.build().append(cShape).append("-IN-SENT").add();
        }
      }
      if (flags.useTags) {
        out.build().append(c.getString(CoreAnnotations.PartOfSpeechAnnotation.class)).append("-TAG").add();
      }

      if (flags.useOrdinal) {
        if (isOrdinal(cInfo, loc)) {
          out.add("C_ORDINAL");
          if (isOrdinal(cInfo, loc-1)) {
            //log.info(getWord(p) + " ");
            out.add("PC_ORDINAL");
          }
          //log.info(cWord);
        }
        if (isOrdinal(cInfo, loc-1)) {
          out.add("P_ORDINAL");
        }
      }

      if (flags.usePrev) {
        out.build().append(pWord).append("-PW").add();
        if (flags.useTags) {
          out.build().append(p.getString(CoreAnnotations.PartOfSpeechAnnotation.class)).append("-PTAG").add();
        }
        if (flags.useDistSim) {
          out.build().append(p.get(CoreAnnotations.DistSimAnnotation.class)).append("-PDISTSIM").add();
        }
        if (flags.useIsURL) {
          out.build().append(p.get(CoreAnnotations.IsURLAnnotation.class)).append("-PISURL").add();
        }
        if (flags.useEntityTypes) {
          out.build().append(p.get(CoreAnnotations.EntityTypeAnnotation.class)).append("-PENTITYTYPE").add();
        }
      }

      if (flags.useNext) {
        out.build().append(nWord).append("-NW").add();
        if (flags.useTags) {
          out.build().append(n.getString(CoreAnnotations.PartOfSpeechAnnotation.class)).append("-NTAG").add();
        }
        if (flags.useDistSim) {
          out.build().append(n.get(CoreAnnotations.DistSimAnnotation.class)).append("-NDISTSIM").add();
        }
        if (flags.useIsURL) {
          out.build().append(n.get(CoreAnnotations.IsURLAnnotation.class)).append("-NISURL").add();
        }
        if (flags.useEntityTypes) {
          out.build().append(n.get(CoreAnnotations.EntityTypeAnnotation.class)).append("-NENTITYTYPE").add();
        }
      }
      /*here, entityTypes refers to the type in the PASCAL IE challenge:
       * i.e. certain words are tagged "Date" or "Location" */


      if (flags.useEitherSideWord) {
        out.build().append(pWord).append("-EW").add();
        out.build().append(nWord).append("-EW").add();
      }

      if (flags.useWordPairs) {
        out.build().append(cWord).dash().append(pWord).append("-W-PW").add();
        out.build().append(cWord).dash().append(nWord).append("-W-NW").add();
      }

      if (flags.useSymTags) {
        if (flags.useTags) {
          out.build().append(p.getString(CoreAnnotations.PartOfSpeechAnnotation.class)).dash().append(c.getString(CoreAnnotations.PartOfSpeechAnnotation.class)).dash().append(n.getString(CoreAnnotations.PartOfSpeechAnnotation.class)).append("-PCNTAGS").add();
          out.build().append(c.getString(CoreAnnotations.PartOfSpeechAnnotation.class)).dash().append(n.getString(CoreAnnotations.PartOfSpeechAnnotation.class)).append("-CNTAGS").add();
          out.build().append(p.getString(CoreAnnotations.PartOfSpeechAnnotation.class)).dash().append(c.getString(CoreAnnotations.PartOfSpeechAnnotation.class)).append("-PCTAGS").add();
        }
        if (flags.useDistSim) {
          out.build().append(p.get(CoreAnnotations.DistSimAnnotation.class)).dash().append(c.get(CoreAnnotations.DistSimAnnotation.class)).dash().append(n.get(CoreAnnotations.DistSimAnnotation.class)).append("-PCNDISTSIM").add();
          out.build().append(c.get(CoreAnnotations.DistSimAnnotation.class)).dash().append(n.get(CoreAnnotations.DistSimAnnotation.class)).append("-CNDISTSIM").add();
          out.build().append(p.get(CoreAnnotations.DistSimAnnotation.class)).dash().append(c.get(CoreAnnotations.DistSimAnnotation.class)).append("-PCDISTSIM").add();
        }
      }

      if (flags.useSymWordPairs) {
        out.build().append(pWord).dash().append(nWord).append("-SWORDS").add();
      }

      if (flags.useGazFeatures || flags.useMoreGazFeatures) {
        String pGazAnnotation = p.get(CoreAnnotations.GazAnnotation.class);
        String nGazAnnotation = n.get(CoreAnnotations.GazAnnotation.class);
        String cGazAnnotation = c.get(CoreAnnotations.GazAnnotation.class);
        if (flags.useGazFeatures) {
          if (cGazAnnotation != null && !cGazAnnotation.equals(flags.dropGaz)) {
            out.build().append(cGazAnnotation).append("-GAZ").add();
          }
          // n
          if (nGazAnnotation != null && !nGazAnnotation.equals(flags.dropGaz)) {
            out.build().append(nGazAnnotation).append("-NGAZ").add();
          }
          // p
          if (pGazAnnotation != null && !pGazAnnotation.equals(flags.dropGaz)) {
            out.build().append(pGazAnnotation).append("-PGAZ").add();
          }
        }

        if (flags.useMoreGazFeatures) {
          if (cGazAnnotation != null && !cGazAnnotation.equals(flags.dropGaz)) {
            out.build().append(cGazAnnotation).dash().append(cWord).append("-CG-CW-GAZ").add();

            // c-n
            if (nGazAnnotation != null && !nGazAnnotation.equals(flags.dropGaz)) {
              out.build().append(cGazAnnotation).dash().append(nGazAnnotation).append("-CNGAZ").add();
            }

            // p-c
            if (pGazAnnotation != null && !pGazAnnotation.equals(flags.dropGaz)) {
              out.build().append(pGazAnnotation).dash().append(cGazAnnotation).append("-PCGAZ").add();
            }
          }
        }
      }

      if (flags.useAbbr || flags.useMinimalAbbr) {
        out.build().append(c.get(CoreAnnotations.AbbrAnnotation.class)).append("-ABBR").add();
      }

      if (flags.useAbbr1 || flags.useMinimalAbbr1) {
        if (!c.get(CoreAnnotations.AbbrAnnotation.class).equals("XX")) {
          out.build().append(c.get(CoreAnnotations.AbbrAnnotation.class)).append("-ABBR").add();
        }
      }

      if (flags.useAbbr) {
        out.build().append(p.get(CoreAnnotations.AbbrAnnotation.class)).dash().append(c.get(CoreAnnotations.AbbrAnnotation.class)).append("-PCABBR").add();
        out.build().append(c.get(CoreAnnotations.AbbrAnnotation.class)).dash().append(n.get(CoreAnnotations.AbbrAnnotation.class)).append("-CNABBR").add();
        out.build().append(p.get(CoreAnnotations.AbbrAnnotation.class)).dash().append(c.get(CoreAnnotations.AbbrAnnotation.class)).dash().append(n.get(CoreAnnotations.AbbrAnnotation.class)).append("-PCNABBR").add();
      }

      if (flags.useAbbr1) {
        if (!c.get(CoreAnnotations.AbbrAnnotation.class).equals("XX")) {
          out.build().append(p.get(CoreAnnotations.AbbrAnnotation.class)).dash().append(c.get(CoreAnnotations.AbbrAnnotation.class)).append("-PCABBR").add();
          out.build().append(c.get(CoreAnnotations.AbbrAnnotation.class)).dash().append(n.get(CoreAnnotations.AbbrAnnotation.class)).append("-CNABBR").add();
          out.build().append(p.get(CoreAnnotations.AbbrAnnotation.class)).dash().append(c.get(CoreAnnotations.AbbrAnnotation.class)).dash().append(n.get(CoreAnnotations.AbbrAnnotation.class)).append("-PCNABBR").add();
        }
      }

      if (flags.useChunks) {
        out.build().append(p.get(CoreAnnotations.ChunkAnnotation.class)).dash().append(c.get(CoreAnnotations.ChunkAnnotation.class)).append("-PCCHUNK").add();
        out.build().append(c.get(CoreAnnotations.ChunkAnnotation.class)).dash().append(n.get(CoreAnnotations.ChunkAnnotation.class)).append("-CNCHUNK").add();
        out.build().append(p.get(CoreAnnotations.ChunkAnnotation.class)).dash().append(c.get(CoreAnnotations.ChunkAnnotation.class)).dash().append(n.get(CoreAnnotations.ChunkAnnotation.class)).append("-PCNCHUNK").add();
      }

      if (flags.useMinimalAbbr) {
        out.build().append(cWord).dash().append(c.get(CoreAnnotations.AbbrAnnotation.class)).append("-CWABB").add();
      }

      if (flags.useMinimalAbbr1) {
        if (!c.get(CoreAnnotations.AbbrAnnotation.class).equals("XX")) {
          out.build().append(cWord).dash().append(c.get(CoreAnnotations.AbbrAnnotation.class)).append("-CWABB").add();
        }
      }

      String prevVB = "", nextVB = "";
      if (flags.usePrevVB) {
        for (int j = loc - 1; ; j--) {
          CoreLabel wi = cInfo.get(j);
          if (wi == cInfo.getPad()) {
            prevVB = "X";
            out.add("X-PVB");
            break;
          } else if (wi.getString(CoreAnnotations.PartOfSpeechAnnotation.class).startsWith("VB")) {
            out.build().append(getWord(wi)).append("-PVB").add();
            prevVB = getWord(wi);
            break;
          }
        }
      }

      if (flags.useNextVB) {
        for (int j = loc + 1; ; j++) {
          CoreLabel wi = cInfo.get(j);
          if (wi == cInfo.getPad()) {
            out.add("X-NVB");
            nextVB = "X";
            break;
          } else if (wi.getString(CoreAnnotations.PartOfSpeechAnnotation.class).startsWith("VB")) {
            out.build().append(getWord(wi)).append("-NVB").add();
            nextVB = getWord(wi);
            break;
          }
        }
      }

      if (flags.useVB) {
        out.build().append(prevVB).dash().append(nextVB).append("-PNVB").add();
      }

      if (flags.useShapeConjunctions) {
        out.build().append(c.get(CoreAnnotations.PositionAnnotation.class)).append(cShape).append("-POS-SH").add();
        if (flags.useTags) {
          out.build().append(c.tag()).append(cShape).append("-TAG-SH").add();
        }
        if (flags.useDistSim) {
          out.build().append(c.get(CoreAnnotations.DistSimAnnotation.class)).append(cShape).append("-DISTSIM-SH").add();
        }
      }

      if (flags.useWordTag) {
        out.build().append(cWord).dash().append(c.getString(CoreAnnotations.PartOfSpeechAnnotation.class)).append("-W-T").add();
        out.build().append(cWord).dash().append(p.getString(CoreAnnotations.PartOfSpeechAnnotation.class)).append("-W-PT").add();
        out.build().append(cWord).dash().append(n.getString(CoreAnnotations.PartOfSpeechAnnotation.class)).append("-W-NT").add();
      }

      if (flags.useNPHead) {
        // TODO: neat idea, but this would need to be set somewhere.
        // Probably should have its own annotation as this one would
        // be more narrow and would clobber other potential uses
        out.build().append(c.get(CoreAnnotations.HeadWordStringAnnotation.class)).append("-HW").add();
        if (flags.useTags) {
          out.build().append(c.get(CoreAnnotations.HeadWordStringAnnotation.class)).dash().append(c.getString(CoreAnnotations.PartOfSpeechAnnotation.class)).append("-HW-T").add();
        }
        if (flags.useDistSim) {
          out.build().append(c.get(CoreAnnotations.HeadWordStringAnnotation.class)).dash().append(c.get(CoreAnnotations.DistSimAnnotation.class)).append("-HW-DISTSIM").add();
        }
      }

      if (flags.useNPGovernor) {
        out.build().append(c.get(CoreAnnotations.GovernorAnnotation.class)).append("-GW").add();
        if (flags.useTags) {
          out.build().append(c.get(CoreAnnotations.GovernorAnnotation.class)).dash().append(c.getString(CoreAnnotations.PartOfSpeechAnnotation.class)).append("-GW-T").add();
        }
        if (flags.useDistSim) {
          out.build().append(c.get(CoreAnnotations.GovernorAnnotation.class)).dash().append(c.get(CoreAnnotations.DistSimAnnotation.class)).append("-DISTSIM-T1").add();
        }
      }

      if (flags.useHeadGov) {
        // TODO: neat idea, but this would need to be set somewhere.
        // Probably should have its own annotation as this one would
        // be more narrow and would clobber other potential uses
        out.build().append(c.get(CoreAnnotations.HeadWordStringAnnotation.class)).dash().append(c.get(CoreAnnotations.GovernorAnnotation.class)).append("-HW_GW").add();
      }

      if (flags.useClassFeature) {
        out.add("###");
      }

      if (flags.useFirstWord) {
        out.add(getWord(cInfo.get(0)));
      }

      if (flags.useNGrams) {
        Collection<String> subs = null;
        if (flags.cacheNGrams) {
          subs = wordToSubstrings.get(cWord);
        }
        if (subs == null) {
          subs = new ArrayList<>();
          String word = '<' + cWord + '>';
          if (flags.lowercaseNGrams) {
            word = word.toLowerCase();
          }
          if (flags.dehyphenateNGrams) {
            word = dehyphenate(word);
          }
          if (flags.greekifyNGrams) {
            word = greekify(word);
          }
          // minimum length substring is 2 letters (hardwired)
          // hoist flags.noMidNGrams so only linear in word length for that case
          if (flags.noMidNGrams) {
            int max = flags.maxNGramLeng >= 0 ? Math.min(flags.maxNGramLeng, word.length()) :
                                                word.length();
            for (int j = 2; j <= max; j++) {
              subs.add(intern('#' + word.substring(0, j) + '#'));
            }
            int start = flags.maxNGramLeng >= 0 ? Math.max(0, word.length() - flags.maxNGramLeng) :
                                                0;
            int lenM1 = word.length() - 1;
            for (int i = start; i < lenM1; i++) {
              subs.add(intern('#' + word.substring(i) + '#'));
            }
          } else {
            for (int i = 0; i < word.length(); i++) {
              for (int j = i + 2, max = Math.min(word.length(), i + flags.maxNGramLeng); j <= max; j++) {
                if (flags.maxNGramLeng >= 0 && j - i > flags.maxNGramLeng) {
                  continue;
                }
                subs.add(intern('#' + word.substring(i, j) + '#'));
              }
            }
          }
          if (flags.cacheNGrams) {
            wordToSubstrings.put(cWord, subs);
          }
        }
        for (String sub : subs) {
          out.add(sub);
        }
        if (flags.conjoinShapeNGrams) {
          for (String str : subs) {
            out.build().append(str).dash().append(cShape).append("-CNGram-CS").add();
          }
        }
      }

      if (flags.useGazettes) {
        if (flags.sloppyGazette) {
          Collection<String> entries = wordToGazetteEntries.get(cWord);
          if (entries != null) {
            for (String entry : entries) {
              out.add(entry);
            }
          }
        }
        if (flags.cleanGazette) {
          Collection<GazetteInfo> infos = wordToGazetteInfos.get(cWord);
          if (infos != null) {
            gazette: for (GazetteInfo gInfo : infos) {
              for (int gLoc = 0; gLoc < gInfo.words.length; gLoc++) {
                if (!gInfo.words[gLoc].equals(getWord(cInfo.get(loc + gLoc - gInfo.loc)))) {
                  continue gazette;
                }
              }
              out.add(gInfo.feature);
            }
          }
        }
      }

      if ((flags.wordShape > WordShapeClassifier.NOWORDSHAPE) || flags.useShapeStrings) {
        out.build().append(cShape).append("-TYPE").add();
        if (flags.useTypeSeqs) {
          out.build().append(pShape).append("-PTYPE").add();
          out.build().append(nShape).append("-NTYPE").add();
          out.build().append(pWord).append("...").append(cShape).append("-PW_CTYPE").add();
          out.build().append(cShape).append("...").append(nWord).append("-NW_CTYPE").add();
          out.build().append(pShape).append("...").append(cShape).append("-PCTYPE").add();
          out.build().append(cShape).append("...").append(nShape).append("-CNTYPE").add();
          out.build().append(pShape).append("...").append(cShape).append("...").append(nShape).append("-PCNTYPE").add();
        }
      }

      if (flags.useLastRealWord) {
        if (pWord.length() <= 3) {
          // extending this to check for 2 short words doesn't seem to help....
          out.build().append(getWord(p2)).append("...").append(cShape).append("-PPW_CTYPE").add();
        }
      }

      if (flags.useNextRealWord) {
        if (nWord.length() <= 3) {
          // extending this to check for 2 short words doesn't seem to help....
          out.build().append(getWord(n2)).append("...").append(cShape).append("-NNW_CTYPE").add();
        }
      }

      if (flags.useOccurrencePatterns) {
        occurrencePatterns(cInfo, loc, out);
      }

      if (flags.useDisjunctive) {
        for (int i = 1; i <= flags.disjunctionWidth; i++) {
          CoreLabel dn = cInfo.get(loc + i);
          CoreLabel dp = cInfo.get(loc - i);
          out.build().append(getWord(dn)).append("-DISJN").add();
          if (flags.useDisjunctiveShapeInteraction) {
            out.build().append(getWord(dn)).dash().append(cShape).append("-DISJN-CS").add();
          }
          out.build().append(getWord(dp)).append("-DISJP").add();
          if (flags.useDisjunctiveShapeInteraction) {
            out.build().append(getWord(dp)).dash().append(cShape).append("-DISJP-CS").add();
          }
        }
      }

      if (flags.useUndirectedDisjunctive) {
        for (int i = 1; i <= flags.disjunctionWidth; i++) {
          CoreLabel dn = cInfo.get(loc + i);
          CoreLabel dp = cInfo.get(loc - i);
          out.build().append(getWord(dn)).append("-DISJ").add();
          out.build().append(getWord(dp)).append("-DISJ").add();
        }
      }

      if (flags.useWideDisjunctive) {
        for (int i = 1; i <= flags.wideDisjunctionWidth; i++) {
          out.build().append(getWord(cInfo.get(loc + i))).append("-DISJWN").add();
          out.build().append(getWord(cInfo.get(loc - i))).append("-DISJWP").add();
        }
      }

      if (flags.useEitherSideDisjunctive) {
        for (int i = 1; i <= flags.disjunctionWidth; i++) {
          out.build().append(getWord(cInfo.get(loc + i))).append("-DISJWE").add();
          out.build().append(getWord(cInfo.get(loc - i))).append("-DISJWE").add();
        }
      }

      if (flags.useDisjShape) {
        for (int i = 1; i <= flags.disjunctionWidth; i++) {
          out.build().append(cInfo.get(loc + i).get(CoreAnnotations.ShapeAnnotation.class)).append("-NDISJSHAPE").add();
          // out.build().append((cInfo.get(loc - i).get(CoreAnnotations.ShapeAnnotation.class)).append("-PDISJSHAPE").add();
          out.build().append(cShape).dash().append(cInfo.get(loc + i).get(CoreAnnotations.ShapeAnnotation.class)).append("-CNDISJSHAPE").add();
          // out.build().append(c.get(CoreAnnotations.ShapeAnnotation.class)).dash().append(cInfo.get(loc - i).get(CoreAnnotations.ShapeAnnotation.class)).append("-CPDISJSHAPE").add();
        }
      }

      if (flags.useExtraTaggySequences) {
        if (flags.useTags) {
          out.build().append(p2.getString(CoreAnnotations.PartOfSpeechAnnotation.class)).dash().append(p.getString(CoreAnnotations.PartOfSpeechAnnotation.class)).dash().append(c.getString(CoreAnnotations.PartOfSpeechAnnotation.class)).append("-TTS").add();
          out.build().append(p3.getString(CoreAnnotations.PartOfSpeechAnnotation.class)).dash().append(p2.getString(CoreAnnotations.PartOfSpeechAnnotation.class)).dash().append(p.getString(CoreAnnotations.PartOfSpeechAnnotation.class)).dash().append(c.getString(CoreAnnotations.PartOfSpeechAnnotation.class)).append("-TTTS").add();
        }
        if (flags.useDistSim) {
          out.build().append(p2.get(CoreAnnotations.DistSimAnnotation.class)).dash().append(p.get(CoreAnnotations.DistSimAnnotation.class)).dash().append(c.get(CoreAnnotations.DistSimAnnotation.class)).append("-DISTSIM_TTS1").add();
          out.build().append(p3.get(CoreAnnotations.DistSimAnnotation.class)).dash().append(p2.get(CoreAnnotations.DistSimAnnotation.class)).dash().append(p.get(CoreAnnotations.DistSimAnnotation.class)).dash().append(c.get(CoreAnnotations.DistSimAnnotation.class)).append("-DISTSIM_TTTS1").add();
        }
      }

      if (flags.useMUCFeatures) {
        out.build().append(c.get(CoreAnnotations.SectionAnnotation.class)).append("-SECTION").add();
        out.build().append(c.get(CoreAnnotations.WordPositionAnnotation.class)).append("-WORD_POSITION").add();
        out.build().append(c.get(CoreAnnotations.SentencePositionAnnotation.class)).append("-SENT_POSITION").add();
        out.build().append(c.get(CoreAnnotations.ParaPositionAnnotation.class)).append("-PARA_POSITION").add();
        out.build().append(c.get(CoreAnnotations.WordPositionAnnotation.class)).dash().append(c.get(CoreAnnotations.ShapeAnnotation.class)).append("-WORD_POSITION_SHAPE").add();
      }
    } else if (flags.useInternal) {
      if (flags.useWord) {
        out.build().append(cWord).append("-WORD").add();
      }

      if (flags.useNGrams) {
        Collection<String> subs = wordToSubstrings.get(cWord);
        if (subs == null) {
          subs = new ArrayList<>();
          String word = '<' + cWord + '>';
          if (flags.lowercaseNGrams) {
            word = word.toLowerCase();
          }
          if (flags.dehyphenateNGrams) {
            word = dehyphenate(word);
          }
          if (flags.greekifyNGrams) {
            word = greekify(word);
          }
          for (int i = 0; i < word.length(); i++) {
            for (int j = i + 2; j <= word.length(); j++) {
              if (flags.noMidNGrams && i != 0 && j != word.length()) {
                continue;
              }
              if (flags.maxNGramLeng >= 0 && j - i > flags.maxNGramLeng) {
                continue;
              }
              //subs.add(intern("#" + word.substring(i, j) + "#"));
              subs.add(intern('#' + word.substring(i, j) + '#'));
            }
          }
          if (flags.cacheNGrams) {
            wordToSubstrings.put(cWord, subs);
          }
        }
        for (String sub : subs) {
          out.add(sub);
        }
        if (flags.conjoinShapeNGrams) {
          String shape = c.get(CoreAnnotations.ShapeAnnotation.class);
          for (String str : subs) {
            out.build().append(str).dash().append(shape).append("-CNGram-CS").add();
          }
        }
      }

      if ((flags.wordShape > WordShapeClassifier.NOWORDSHAPE) || flags.useShapeStrings) {
        out.build().append(cShape).append("-TYPE").add();
      }

      if (flags.useOccurrencePatterns) {
        occurrencePatterns(cInfo, loc, out);
      }
    } else if (flags.useExternal) {
      if (flags.usePrev) {
        out.build().append(pWord).append("-PW").add();
      }

      if (flags.useNext) {
        out.build().append(nWord).append("-NW").add();
      }

      if (flags.useWordPairs) {
        out.build().append(cWord).dash().append(pWord).append("-W-PW").add();
        out.build().append(cWord).dash().append(nWord).append("-W-NW").add();
      }

      if (flags.useSymWordPairs) {
        out.build().append(pWord).dash().append(nWord).append("-SWORDS").add();
      }

      if ((flags.wordShape > WordShapeClassifier.NOWORDSHAPE) || flags.useShapeStrings) {
        if (flags.useTypeSeqs) {
          out.build().append(pShape).append("-PTYPE").add();
          out.build().append(nShape).append("-NTYPE").add();
          out.build().append(pWord).append("...").append(cShape).append("-PW_CTYPE").add();
          out.build().append(cShape).append("...").append(nWord).append("-NW_CTYPE").add();
          if (flags.maxLeft > 0)
            out.build().append(pShape).append("...").append(cShape).append("-PCTYPE").add(); // this one just isn't useful, at least given c,pc,s,ps.  Might be useful 0th-order
          out.build().append(cShape).append("...").append(nShape).append("-CNTYPE").add();
          out.build().append(pShape).append("...").append(cShape).append("...").append(nShape).append("-PCNTYPE").add();
        }
      }

      if (flags.useLastRealWord) {
        if (pWord.length() <= 3) {
          out.build().append(getWord(p2)).append("...").append(cShape).append("-PPW_CTYPE").add();
        }
      }

      if (flags.useNextRealWord) {
        if (nWord.length() <= 3) {
          out.build().append(getWord(n2)).append("...").append(cShape).append("-NNW_CTYPE").add();
        }
      }

      if (flags.useDisjunctive) {
        for (int i = 1; i <= flags.disjunctionWidth; i++) {
          CoreLabel dn = cInfo.get(loc + i);
          CoreLabel dp = cInfo.get(loc - i);
          out.build().append(getWord(dn)).append("-DISJN").add();
          if (flags.useDisjunctiveShapeInteraction) {
            out.build().append(getWord(dn)).dash().append(cShape).append("-DISJN-CS").add();
          }
          out.build().append(getWord(dp)).append("-DISJP").add();
          if (flags.useDisjunctiveShapeInteraction) {
            out.build().append(getWord(dp)).dash().append(cShape).append("-DISJP-CS").add();
          }
        }
      }

      if (flags.useWideDisjunctive) {
        for (int i = 1; i <= flags.wideDisjunctionWidth; i++) {
          out.build().append(getWord(cInfo.get(loc + i))).append("-DISJWN").add();
          out.build().append(getWord(cInfo.get(loc - i))).append("-DISJWP").add();
        }
      }

      if (flags.useDisjShape) {
        for (int i = 1; i <= flags.disjunctionWidth; i++) {
          out.build().append(cInfo.get(loc + i).get(CoreAnnotations.ShapeAnnotation.class)).append("-NDISJSHAPE").add();
          // out.build().append((cInfo.get(loc - i).get(CoreAnnotations.ShapeAnnotation.class)).append("-PDISJSHAPE").add();
          out.build().append(c.get(CoreAnnotations.ShapeAnnotation.class)).dash().append(cInfo.get(loc + i).get(CoreAnnotations.ShapeAnnotation.class)).append("-CNDISJSHAPE").add();
          // out.build().append(c.get(CoreAnnotations.ShapeAnnotation.class)).dash().append(cInfo.get(loc - i).get(CoreAnnotations.ShapeAnnotation.class)).append("-CPDISJSHAPE").add();
        }
      }
    }

    // Stuff to add binary features from the additional columns
    if (flags.twoStage) {
      out.build().append(c.get(Bin1Annotation.class)).append("-BIN1").add();
      out.build().append(c.get(Bin2Annotation.class)).append("-BIN2").add();
      out.build().append(c.get(Bin3Annotation.class)).append("-BIN3").add();
      out.build().append(c.get(Bin4Annotation.class)).append("-BIN4").add();
      out.build().append(c.get(Bin5Annotation.class)).append("-BIN5").add();
      out.build().append(c.get(Bin6Annotation.class)).append("-BIN6").add();
    }

    if(flags.useIfInteger){
      try {
        int val = Integer.parseInt(cWord);
        if(val > 0) out.add("POSITIVE_INTEGER");
        else if(val < 0) out.add("NEGATIVE_INTEGER");
        // log.info("FOUND INTEGER");
      } catch(NumberFormatException e){
        // not an integer value, nothing to do
      }
    }

    //Stuff to add arbitrary features
    if (flags.useGenericFeatures) {
      //see if we need to cache the keys
      if (genericAnnotationKeys == null) {
        makeGenericKeyCache(c);
      }
      //now look through the cached keys
      for (Class<?> key : genericAnnotationKeys) {
        //log.info("Adding feature: " + CoreLabel.genericValues.get(key) + " with value " + c.get(key));
        Object col = c.get((Class<CoreAnnotation<Object>>) key);
        if (col instanceof Collection) {
          for (Object ob: (Collection<?>)col) {
            out.build().append(ob.toString()).dash().append(CoreLabel.genericValues.get(key)).add();
          }
        } else if (col != null) {
          out.build().append(col.toString()).dash().append(CoreLabel.genericValues.get(key)).add();
        }
      }
    }

    if(flags.useTopics){
      //out.build().append(p.get(CoreAnnotations.TopicAnnotation.class), "-", cWord, "--CWORD").add();
      out.build().append(c.get(CoreAnnotations.TopicAnnotation.class)).append("-TopicID").add();
      out.build().append(p.get(CoreAnnotations.TopicAnnotation.class)).append("-PTopicID").add();
      out.build().append(n.get(CoreAnnotations.TopicAnnotation.class)).append("-NTopicID").add();
      //out.build().append(p.get(CoreAnnotations.TopicAnnotation.class)).dash().append(c.get(CoreAnnotations.TopicAnnotation.class)).dash().append(n.get(CoreAnnotations.TopicAnnotation.class)).append("-PCNTopicID").add();
      //out.build().append(c.get(CoreAnnotations.TopicAnnotation.class)).dash().append(n.get(CoreAnnotations.TopicAnnotation.class)).append("-CNTopicID").add();
      //out.build().append(p.get(CoreAnnotations.TopicAnnotation.class)).dash().append(c.get(CoreAnnotations.TopicAnnotation.class)).append("-PCTopicID").add();
      //out.build().append(c.get(CoreAnnotations.TopicAnnotation.class)).append(cShape).append("-TopicID-SH").add();
    }

    // todo [cdm 2014]: Have this guarded by a flag and things would be a little faster. Set flag in current uses of this annotation.
    // NER tag annotations from a previous NER system
    if (c.get(CoreAnnotations.StackedNamedEntityTagAnnotation.class) != null) {
      out.build().append(c.get(CoreAnnotations.StackedNamedEntityTagAnnotation.class)).append("-CStackedNERTag").add();
      out.build().append(cWord).dash().append(c.get(CoreAnnotations.StackedNamedEntityTagAnnotation.class)).append("-WCStackedNERTag").add();

      if (flags.useNext) {
        out.build().append(c.get(CoreAnnotations.StackedNamedEntityTagAnnotation.class)).dash().append(n.get(CoreAnnotations.StackedNamedEntityTagAnnotation.class)).append("-CNStackedNERTag").add();
        out.build().append(cWord).dash().append(c.get(CoreAnnotations.StackedNamedEntityTagAnnotation.class)).dash().append(n.get(CoreAnnotations.StackedNamedEntityTagAnnotation.class)).append("-WCNStackedNERTag").add();

        if (flags.usePrev) {
          out.build().append(p.get(CoreAnnotations.StackedNamedEntityTagAnnotation.class)).dash().append(c.get(CoreAnnotations.StackedNamedEntityTagAnnotation.class)).dash().append(n.get(CoreAnnotations.StackedNamedEntityTagAnnotation.class)).append("-PCNStackedNERTag").add();
          out.build().append(p.get(CoreAnnotations.StackedNamedEntityTagAnnotation.class)).dash().append(cWord).append(" -").append(c.get(CoreAnnotations.StackedNamedEntityTagAnnotation.class)
             ).dash().append(n.get(CoreAnnotations.StackedNamedEntityTagAnnotation.class)).append("-PWCNStackedNERTag").add();
        }
      }
      if (flags.usePrev) {
        out.build().append(p.get(CoreAnnotations.StackedNamedEntityTagAnnotation.class)).dash().append(c.get(CoreAnnotations.StackedNamedEntityTagAnnotation.class)).append("-PCStackedNERTag").add();
      }
    }
    if(flags.useWordnetFeatures)
      out.build().append(c.get(CoreAnnotations.WordnetSynAnnotation.class)).append("-WordnetSyn").add();
    if(flags.useProtoFeatures)
      out.build().append(c.get(CoreAnnotations.ProtoAnnotation.class)).append("-Proto").add();
    if(flags.usePhraseWordTags)
      out.build().append(c.get(CoreAnnotations.PhraseWordsTagAnnotation.class)).append("-PhraseTag").add();
    if(flags.usePhraseWords)
    {
      for(String w: c.get(CoreAnnotations.PhraseWordsAnnotation.class)) {
        out.build().append(w).append("-PhraseWord").add();
      }
    }
    if(flags.useCommonWordsFeature)
      out.add(c.get(CoreAnnotations.CommonWordsAnnotation.class));

    if (flags.useRadical && cWord.length() > 0) {
      // todo [cdm 2016]: Really all stuff in this file should be fixed to work with codepoints outside BMP
      if (cWord.length() == 1) {
        out.build().append(RadicalMap.getRadical(cWord.charAt(0))).append("-SINGLE-CHAR-RADICAL").add();
      } else {
        out.build().append(RadicalMap.getRadical(cWord.charAt(0))).append("-START-RADICAL").add();
        out.build().append(RadicalMap.getRadical(cWord.charAt(cWord.length() - 1))).append("-END-RADICAL").add();
      }
      for (int i = 0; i < cWord.length(); ++i) {
        out.build().append(RadicalMap.getRadical(cWord.charAt(i))).append("-RADICAL").add();
      }
    }

    if (flags.splitWordRegex != null && !flags.splitWordRegex.isEmpty()){
      for(String s: c.word().split(flags.splitWordRegex)) {
        out.build().append(s).append("-SPLITWORD").add();
      }
    }

    if (flags.useMoreNeighborNGrams) {
      int maxLen = pWord.length();
      if (flags.maxNGramLeng >= 0 && flags.maxNGramLeng < maxLen) {
        maxLen = flags.maxNGramLeng;
      }
      for (int len = 1; len <= maxLen; ++len) {
        out.build().append(pWord.substring(0, len)).append("-PREV-PREFIX").add();
      }
      for (int pos = pWord.length() - maxLen; pos < pWord.length(); ++pos) {
        out.build().append(pWord.substring(pos, pWord.length())).append("-PREV-SUFFIX").add();
      }

      maxLen = nWord.length();
      if (flags.maxNGramLeng >= 0 && flags.maxNGramLeng < maxLen) {
        maxLen = flags.maxNGramLeng;
      }
      for (int len = 1; len <= maxLen; ++len) {
        out.build().append(nWord.substring(0, len)).append("-NEXT-PREFIX").add();
      }
      for (int pos = nWord.length() - maxLen; pos < nWord.length(); ++pos) {
        out.build().append(nWord.substring(pos, nWord.length())).append("-NEXT-SUFFIX").add();
      }
    }
  } // end featuresC()


  /**
   * Binary feature annotations
   */
  private static class Bin1Annotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {  return String.class; } }

  private static class Bin2Annotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {  return String.class; } }

  private static class Bin3Annotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {  return String.class; } }

  private static class Bin4Annotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {  return String.class; } }

  private static class Bin5Annotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {  return String.class; } }

  private static class Bin6Annotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {  return String.class; } }



  protected void featuresCpC(PaddedList<IN> cInfo, int loc, FeatureCollector out) {
    if (flags.noEdgeFeature) {
      return;
    }

    out.setSuffix("CpC");

    CoreLabel p = cInfo.get(loc - 1);
    CoreLabel c = cInfo.get(loc);
    CoreLabel n = cInfo.get(loc + 1);

    String cWord = getWord(c);
    String pWord = getWord(p);
    String cDS = c.getString(CoreAnnotations.DistSimAnnotation.class);
    String pDS = p.getString(CoreAnnotations.DistSimAnnotation.class);
    String cShape = c.getString(CoreAnnotations.ShapeAnnotation.class);
    String pShape = p.getString(CoreAnnotations.ShapeAnnotation.class);

    if (flags.transitionEdgeOnly) {
      out.add("PSEQ");
      return;
    }

    if (flags.useNeighborNGrams) {
      int maxLen = pWord.length();
      if (flags.maxNGramLeng >= 0 && flags.maxNGramLeng < maxLen) {
        maxLen = flags.maxNGramLeng;
      }
      for (int len = 1; len <= maxLen; ++len) {
        out.build().append(pWord.substring(0, len)).append("-PREVIOUS-PREFIX").add();
      }
      for (int pos = pWord.length() - maxLen; pos < pWord.length(); ++pos) {
        out.build().append(pWord.substring(pos, pWord.length())).append("-PREVIOUS-SUFFIX").add();
      }

      maxLen = cWord.length();
      if (flags.maxNGramLeng >= 0 && flags.maxNGramLeng < maxLen) {
        maxLen = flags.maxNGramLeng;
      }
      for (int len = 1; len <= maxLen; ++len) {
        out.build().append(cWord.substring(0, len)).append("-CURRENT-PREFIX").add();
      }
      for (int pos = cWord.length() - maxLen; pos < cWord.length(); ++pos) {
        out.build().append(cWord.substring(pos, cWord.length())).append("-CURRENT-SUFFIX").add();
      }
    }

    if (flags.useInternal && flags.useExternal ) {
      if (flags.useOrdinal) {
        if (isOrdinal(cInfo, loc)) {
          out.add("C_ORDINAL");
          if (isOrdinal(cInfo, loc-1)) {
            out.add("PC_ORDINAL");
          }
        }
        if (isOrdinal(cInfo, loc-1)) {
          out.add("P_ORDINAL");
        }
      }

      if (flags.useAbbr || flags.useMinimalAbbr) {
        out.build().append(p.get(CoreAnnotations.AbbrAnnotation.class)).dash().append(c.get(CoreAnnotations.AbbrAnnotation.class)).append("-PABBRANS").add();
      }

      if (flags.useAbbr1 || flags.useMinimalAbbr1) {
        if (!c.get(CoreAnnotations.AbbrAnnotation.class).equals("XX")) {
          out.build().append(p.get(CoreAnnotations.AbbrAnnotation.class)).dash().append(c.get(CoreAnnotations.AbbrAnnotation.class)).append("-PABBRANS").add();
        }
      }

      if (flags.useChunkySequences) {
        out.build().append(p.get(CoreAnnotations.ChunkAnnotation.class)).dash().append(c.get(CoreAnnotations.ChunkAnnotation.class)).dash().append(n.get(CoreAnnotations.ChunkAnnotation.class)).append("-PCNCHUNK").add();
      }

      if (flags.usePrev) {
        if (flags.useSequences && flags.usePrevSequences) {
          out.add("PSEQ");
          out.build().append(cWord).append("-PSEQW").add();

          if ( ! flags.strictGoodCoNLL) {
            out.build().append(pWord).dash().append(cWord).append("-PSEQW2").add();  // added later after goodCoNLL
            out.build().append(pWord).append("-PSEQpW").add(); // added later after goodCoNLL
          }

          if (flags.useDistSim) {
            out.build().append(pDS).append("-PSEQpDS").add();
            out.build().append(cDS).append("-PSEQcDS").add();
            out.build().append(pDS).dash().append(cDS).append("-PSEQpcDS").add();
          }

          if (((flags.wordShape > WordShapeClassifier.NOWORDSHAPE) || flags.useShapeStrings)) {
            if ( ! flags.strictGoodCoNLL) {     // These ones were added later after goodCoNLL
              out.build().append(pShape).append("-PSEQpS").add();
              out.build().append(cShape).append("-PSEQcS").add();
            }
            if (flags.strictGoodCoNLL && ! flags.removeStrictGoodCoNLLDuplicates) {
              out.build().append(pShape).dash().append(cShape).append("-PSEQpcS").add(); // Duplicate (in goodCoNLL orig, see -TYPES below)
            }
          }
        }
      }

      if (((flags.wordShape > WordShapeClassifier.NOWORDSHAPE) ||
           flags.useShapeStrings)
          && flags.useTypeSeqs && (flags.useTypeSeqs2 || flags.useTypeSeqs3)) {
        if (flags.useTypeSeqs3) {
          out.build().append(pShape).dash().append(cShape).dash().append(n.get(CoreAnnotations.ShapeAnnotation.class)).append("-PCNSHAPES").add();
        }
        if (flags.useTypeSeqs2) {
          out.build().append(pShape).dash().append(cShape).append("-TYPES").add();  // this duplicates PSEQpcS above
        }

        if (flags.useYetMoreCpCShapes) {
          String p2Shape = cInfo.get(loc - 2).getString(CoreAnnotations.ShapeAnnotation.class);
          out.build().append(p2Shape).dash().append(pShape).dash().append(cShape).append("-YMS").add();
          out.build().append(pShape).dash().append(cShape).dash().append(n.getString(CoreAnnotations.ShapeAnnotation.class)).append("-YMSPCN").add();
        }
      }

      if (flags.useTypeySequences) {
        out.build().append(cShape).append("-TPS2").add();
        out.build().append(n.get(CoreAnnotations.ShapeAnnotation.class)).append("-TNS1").add();
        // out.build().append(pShape)).dash().append(cShape)).append("-TPS").add(); // duplicates -TYPES, so now omitted; you may need to slightly increase sigma to duplicate previous results, however.
      }

      if (flags.useTaggySequences) {
        if (flags.useTags) {
          out.build().append(p.getString(CoreAnnotations.PartOfSpeechAnnotation.class)).dash().append(c.getString(CoreAnnotations.PartOfSpeechAnnotation.class)).append("-TS").add();
        }
        if (flags.useDistSim) {
          out.build().append(p.get(CoreAnnotations.DistSimAnnotation.class)).dash().append(c.get(CoreAnnotations.DistSimAnnotation.class)).append("-DISTSIM_TS1").add();
        }
      }

      if (flags.useParenMatching) {
        if (flags.useReverse) {
          if (cWord.equals("(") || cWord.equals("[") || cWord.equals("-LRB-")) {
            if (pWord.equals(")") || pWord.equals("]") || pWord.equals("-RRB-")) {
              out.add("PAREN-MATCH");
            }
          }
        } else {
          if (cWord.equals(")") || cWord.equals("]") || cWord.equals("-RRB-")) {
            if (pWord.equals("(") || pWord.equals("[") || pWord.equals("-LRB-")) {
              out.add("PAREN-MATCH");
            }
          }
        }
      }
      if (flags.useEntityTypeSequences) {
        out.build().append(p.get(CoreAnnotations.EntityTypeAnnotation.class)).dash().append(c.get(CoreAnnotations.EntityTypeAnnotation.class)).append("-ETSEQ").add();
      }
      if (flags.useURLSequences) {
        out.build().append(p.get(CoreAnnotations.IsURLAnnotation.class)).dash().append(c.get(CoreAnnotations.IsURLAnnotation.class)).append("-URLSEQ").add();
      }
    } else if (flags.useInternal) {
      if (flags.useSequences && flags.usePrevSequences) {
        out.add("PSEQ");
        out.build().append(cWord).append("-PSEQW").add();
      }

      if (flags.useTypeySequences) {
        out.build().append(cShape).append("-TPS2").add();
      }
    } else if (flags.useExternal) {
      if ((flags.wordShape > WordShapeClassifier.NOWORDSHAPE || flags.useShapeStrings) && flags.useTypeSeqs) {
        if (flags.useTypeSeqs3) {
          out.build().append(pShape).dash().append(cShape).dash().append(n.get(CoreAnnotations.ShapeAnnotation.class)).append("-PCNSHAPES").add();
        }
        if (flags.useTypeSeqs2) {
          out.build().append(pShape).dash().append(cShape).append("-TYPES").add();
        }
      }

      if (flags.useTypeySequences) {
        out.build().append(n.get(CoreAnnotations.ShapeAnnotation.class)).append("-TNS1").add();
        out.build().append(pShape).dash().append(c.get(CoreAnnotations.ShapeAnnotation.class)).append("-TPS").add();
      }
    }
  }

  protected void featuresCp2C(PaddedList<IN> cInfo, int loc, FeatureCollector out) {
    out.setSuffix("Cp2C");

    CoreLabel c = cInfo.get(loc);
    CoreLabel p = cInfo.get(loc - 1);
    CoreLabel p2 = cInfo.get(loc - 2);

    String cWord = getWord(c);
    String pWord = getWord(p);
    String p2Word = getWord(p2);

    if (flags.useMoreAbbr) {
      out.build().append(p2.get(CoreAnnotations.AbbrAnnotation.class)).dash().append(c.get(CoreAnnotations.AbbrAnnotation.class)).append("-P2ABBRANS").add();
    }

    if (flags.useMinimalAbbr) {
      out.build().append(p2.get(CoreAnnotations.AbbrAnnotation.class)).dash().append(c.get(CoreAnnotations.AbbrAnnotation.class)).append("-P2AP2CABB").add();
    }

    if (flags.useMinimalAbbr1) {
      if (!c.get(CoreAnnotations.AbbrAnnotation.class).equals("XX")) {
        out.build().append(p2.get(CoreAnnotations.AbbrAnnotation.class)).dash().append(c.get(CoreAnnotations.AbbrAnnotation.class)).append("-P2AP2CABB").add();
      }
    }

    if (flags.useParenMatching) {
      if (flags.useReverse) {
        if (cWord.equals("(") || cWord.equals("[") || cWord.equals("-LRB-")) {
          if ((p2Word.equals(")") || p2Word.equals("]") || p2Word.equals("-RRB-")) && ! (pWord.equals(")") || pWord.equals("]") || pWord.equals("-RRB-"))) {
            out.add("PAREN-MATCH");
          }
        }
      } else {
        if (cWord.equals(")") || cWord.equals("]") || cWord.equals("-RRB-")) {
          if ((p2Word.equals("(") || p2Word.equals("[") || p2Word.equals("-LRB-")) && ! (pWord.equals("(") || pWord.equals("[") || pWord.equals("-LRB-"))) {
            out.add("PAREN-MATCH");
          }
        }
      }
    }
  }

  protected void featuresCp3C(PaddedList<IN> cInfo, int loc, FeatureCollector out) {
    if (!flags.useParenMatching) {
      return;
    }
    out.setSuffix("Cp3C");

    CoreLabel c = cInfo.get(loc);
    CoreLabel p = cInfo.get(loc - 1);
    CoreLabel p2 = cInfo.get(loc - 2);
    CoreLabel p3 = cInfo.get(loc - 3);

    String cWord = getWord(c);
    String pWord = getWord(p);
    String p2Word = getWord(p2);
    String p3Word = getWord(p3);

    if (flags.useReverse) {
      if (cWord.equals("(") || cWord.equals("[")) {
        if ((flags.maxLeft >= 3) && (p3Word.equals(")") || p3Word.equals("]")) && !(p2Word.equals(")") || p2Word.equals("]") || pWord.equals(")") || pWord.equals("]"))) {
          out.add("PAREN-MATCH");
        }
      }
    } else {
      if (cWord.equals(")") || cWord.equals("]")) {
        if ((flags.maxLeft >= 3) && (p3Word.equals("(") || p3Word.equals("[")) && !(p2Word.equals("(") || p2Word.equals("[") || pWord.equals("(") || pWord.equals("["))) {
          out.add("PAREN-MATCH");
        }
      }
    }
  }

  protected void featuresCp4C(PaddedList<IN> cInfo, int loc, FeatureCollector out) {
    if (!flags.useParenMatching) {
      return;
    }
    out.setSuffix("Cp4C");

    CoreLabel c = cInfo.get(loc);
    CoreLabel p = cInfo.get(loc - 1);
    CoreLabel p2 = cInfo.get(loc - 2);
    CoreLabel p3 = cInfo.get(loc - 3);
    CoreLabel p4 = cInfo.get(loc - 4);

    String cWord = getWord(c);
    String pWord = getWord(p);
    String p2Word = getWord(p2);
    String p3Word = getWord(p3);
    String p4Word = getWord(p4);

    if (flags.useReverse) {
      if (cWord.equals("(") || cWord.equals("[")) {
        if ((flags.maxLeft >= 4) && (p4Word.equals(")") || p4Word.equals("]")) && !(p3Word.equals(")") || p3Word.equals("]") || p2Word.equals(")") || p2Word.equals("]") || pWord.equals(")") || pWord.equals("]"))) {
          out.add("PAREN-MATCH");
        }
      }
    } else {
      if (cWord.equals(")") || cWord.equals("]")) {
        if ((flags.maxLeft >= 4) && (p4Word.equals("(") || p4Word.equals("[")) && !(p3Word.equals("(") || p3Word.equals("[") || p2Word.equals("(") || p2Word.equals("[") || pWord.equals("(") || pWord.equals("["))) {
          out.add("PAREN-MATCH");
        }
      }
    }
  }

  protected void featuresCp5C(PaddedList<IN> cInfo, int loc, FeatureCollector out) {
    if (!flags.useParenMatching) {
      return;
    }
    out.setSuffix("Cp5C");

    CoreLabel c = cInfo.get(loc);
    CoreLabel p = cInfo.get(loc - 1);
    CoreLabel p2 = cInfo.get(loc - 2);
    CoreLabel p3 = cInfo.get(loc - 3);
    CoreLabel p4 = cInfo.get(loc - 4);
    CoreLabel p5 = cInfo.get(loc - 5);

    String cWord = getWord(c);
    String pWord = getWord(p);
    String p2Word = getWord(p2);
    String p3Word = getWord(p3);
    String p4Word = getWord(p4);
    String p5Word = getWord(p5);

    if (flags.useReverse) {
      if (cWord.equals("(") || cWord.equals("[")) {
        if ((flags.maxLeft >= 5) && (p5Word.equals(")") || p5Word.equals("]")) && !(p4Word.equals(")") || p4Word.equals("]") || p3Word.equals(")") || p3Word.equals("]") || p2Word.equals(")") || p2Word.equals("]") || pWord.equals(")") || pWord.equals("]"))) {
          out.add("PAREN-MATCH");
        }
      }
    } else {
      if (cWord.equals(")") || cWord.equals("]")) {
        if ((flags.maxLeft >= 5) && (p5Word.equals("(") || p5Word.equals("[")) && !(p4Word.equals("(") || p4Word.equals("[") || p3Word.equals("(") || p3Word.equals("[") || p2Word.equals("(") || p2Word.equals("[") || pWord.equals("(") || pWord.equals("["))) {
          out.add("PAREN-MATCH");
        }
      }
    }
  }

  protected void featuresCpCp2C(PaddedList<IN> cInfo, int loc, FeatureCollector out) {
    out.setSuffix("CpCp2C");

    CoreLabel c = cInfo.get(loc);
    CoreLabel p = cInfo.get(loc - 1);
    CoreLabel p2 = cInfo.get(loc - 2);

    String pWord = getWord(p);
    // String p2Word = getWord(p2);

    if (flags.useInternal && flags.useExternal) {
      if (flags.strictGoodCoNLL && ! flags.removeStrictGoodCoNLLDuplicates && flags.useTypeySequences && flags.maxLeft >= 2) {
        // this feature duplicates -TYPETYPES below, so probably don't include it, but it was in original tests of CMM goodCoNLL
        out.build().append(p2.get(CoreAnnotations.ShapeAnnotation.class)).dash().append(p.get(CoreAnnotations.ShapeAnnotation.class)).dash().append(c.get(CoreAnnotations.ShapeAnnotation.class)).append("-TTPS").add();
      }

      if (flags.useAbbr) {
        out.build().append(p2.get(CoreAnnotations.AbbrAnnotation.class)).dash().append(p.get(CoreAnnotations.AbbrAnnotation.class)).dash().append(c.get(CoreAnnotations.AbbrAnnotation.class)).append("-2PABBRANS").add();
      }

      if (flags.useChunks) {
        out.build().append(p2.get(CoreAnnotations.ChunkAnnotation.class)).dash().append(p.get(CoreAnnotations.ChunkAnnotation.class)).dash().append(c.get(CoreAnnotations.ChunkAnnotation.class)).append("-2PCHUNKS").add();
      }

      if (flags.useLongSequences) {
        out.add("PPSEQ");
      }
      if (flags.useBoundarySequences && pWord.equals(CoNLLDocumentReaderAndWriter.BOUNDARY)) {
        out.add("BNDRY-SPAN-PPSEQ");
      }
      // This more complex consistency checker didn't help!
      // if (flags.useBoundarySequences) {
      //   // try enforce consistency over "and" and "," as well as boundary
      //   if (pWord.equals(CoNLLDocumentIteratorFactory.BOUNDARY) ||
      //       pWord.equalsIgnoreCase("and") || pWord.equalsIgnoreCase("or") ||
      //       pWord.equals(",")) {
      //   }
      // }

      if (flags.useTaggySequences) {
        if (flags.useTags) {
          out.build().append(p2.getString(CoreAnnotations.PartOfSpeechAnnotation.class)).dash().append(p.getString(CoreAnnotations.PartOfSpeechAnnotation.class)).dash().append(c.getString(CoreAnnotations.PartOfSpeechAnnotation.class)).append("-TTS").add();
          if (flags.useTaggySequencesShapeInteraction) {
            out.build().append(p2.getString(CoreAnnotations.PartOfSpeechAnnotation.class)).dash().append(p.getString(CoreAnnotations.PartOfSpeechAnnotation.class)).dash().append(c.getString(CoreAnnotations.PartOfSpeechAnnotation.class)).dash().append(c.get(CoreAnnotations.ShapeAnnotation.class)).append("-TTS-CS").add();
          }
        }
        if (flags.useDistSim) {
          out.build().append(p2.get(CoreAnnotations.DistSimAnnotation.class)).dash().append(p.get(CoreAnnotations.DistSimAnnotation.class)).dash().append(c.get(CoreAnnotations.DistSimAnnotation.class)).append("-DISTSIM_TTS1").add();
          if (flags.useTaggySequencesShapeInteraction) {
            out.build().append(p2.get(CoreAnnotations.DistSimAnnotation.class)).dash().append(p.get(CoreAnnotations.DistSimAnnotation.class)).dash().append(c.get(CoreAnnotations.DistSimAnnotation.class)).dash().append(c.get(CoreAnnotations.ShapeAnnotation.class)).append("-DISTSIM_TTS1-CS").add();
          }
        }
      }

      if (((flags.wordShape > WordShapeClassifier.NOWORDSHAPE) ||
           flags.useShapeStrings)
          && flags.useTypeSeqs && flags.useTypeSeqs2 && flags.maxLeft >= 2) {
        String cShape = c.get(CoreAnnotations.ShapeAnnotation.class);
        String pShape = p.get(CoreAnnotations.ShapeAnnotation.class);
        String p2Shape = p2.get(CoreAnnotations.ShapeAnnotation.class);
        out.build().append(p2Shape).dash().append(pShape).dash().append(cShape).append("-TYPETYPES").add();
      }
    } else if (flags.useInternal) {
      if (flags.useLongSequences) {
        out.add("PPSEQ");
      }
    } else if (flags.useExternal) {
      if (flags.useLongSequences) {
        out.add("PPSEQ");
      }

      if (((flags.wordShape > WordShapeClassifier.NOWORDSHAPE) ||
           flags.useShapeStrings)
          && flags.useTypeSeqs && flags.useTypeSeqs2 && flags.maxLeft >= 2) {
        String cShape = c.get(CoreAnnotations.ShapeAnnotation.class);
        String pShape = p.get(CoreAnnotations.ShapeAnnotation.class);
        String p2Shape = p2.get(CoreAnnotations.ShapeAnnotation.class);
        out.build().append(p2Shape).dash().append(pShape).dash().append(cShape).append("-TYPETYPES").add();
      }
    }
  }


  protected void featuresCpCp2Cp3C(PaddedList<IN> cInfo, int loc, FeatureCollector out) {
    out.setSuffix("CpCp2Cp3C");
    CoreLabel p = cInfo.get(loc - 1);

    if (flags.useTaggySequences) {
      CoreLabel c = cInfo.get(loc);
      CoreLabel p2 = cInfo.get(loc - 2);
      CoreLabel p3 = cInfo.get(loc - 3);
      if (flags.useTags) {
        if (flags.maxLeft >= 3 && !flags.dontExtendTaggy) {
          out.build().append(p3.getString(CoreAnnotations.PartOfSpeechAnnotation.class)).dash().append(p2.getString(CoreAnnotations.PartOfSpeechAnnotation.class)).dash().append(p.getString(CoreAnnotations.PartOfSpeechAnnotation.class)).dash().append(c.getString(CoreAnnotations.PartOfSpeechAnnotation.class)).append("-TTTS").add();
          if (flags.useTaggySequencesShapeInteraction) {
            out.build().append(p3.getString(CoreAnnotations.PartOfSpeechAnnotation.class)).dash().append(p2.getString(CoreAnnotations.PartOfSpeechAnnotation.class)).dash().append(p.getString(CoreAnnotations.PartOfSpeechAnnotation.class)).dash().append(c.getString(CoreAnnotations.PartOfSpeechAnnotation.class)).dash().append(c.get(CoreAnnotations.ShapeAnnotation.class)).append("-TTTS-CS").add();
          }
        }
      }
      if (flags.useDistSim) {
        if (flags.maxLeft >= 3 && !flags.dontExtendTaggy) {
          out.build().append(p3.get(CoreAnnotations.DistSimAnnotation.class)).dash().append(p2.get(CoreAnnotations.DistSimAnnotation.class)).dash().append(p.get(CoreAnnotations.DistSimAnnotation.class)).dash().append(c.get(CoreAnnotations.DistSimAnnotation.class)).append("-DISTSIM_TTTS1").add();
          if (flags.useTaggySequencesShapeInteraction) {
            out.build().append(p3.get(CoreAnnotations.DistSimAnnotation.class)).dash().append(p2.get(CoreAnnotations.DistSimAnnotation.class)).dash().append(p.get(CoreAnnotations.DistSimAnnotation.class)).dash().append(c.get(CoreAnnotations.DistSimAnnotation.class)).dash().append(c.get(CoreAnnotations.ShapeAnnotation.class)).append("-DISTSIM_TTTS1-CS").add();
          }
        }
      }
    }

    if (flags.maxLeft >= 3) {
      if (flags.useLongSequences) {
        out.add("PPPSEQ");
      }
      if (flags.useBoundarySequences && getWord(p).equals(CoNLLDocumentReaderAndWriter.BOUNDARY)) {
        out.add("BNDRY-SPAN-PPPSEQ");
      }
    }
  }

  protected void featuresCpCp2Cp3Cp4C(PaddedList<IN> cInfo, int loc, FeatureCollector out) {
    if (flags.maxLeft >= 4) {
      out.setSuffix("CpCp2Cp3Cp4C");

      CoreLabel p = cInfo.get(loc - 1);
      if (flags.useLongSequences) {
        out.add("PPPPSEQ");
      }
      if (flags.useBoundarySequences && getWord(p).equals(CoNLLDocumentReaderAndWriter.BOUNDARY)) {
        out.add("BNDRY-SPAN-PPPPSEQ");
      }
    }
  }


  protected void featuresCnC(PaddedList<IN> cInfo, int loc, FeatureCollector out) {
    if (flags.useNext && flags.useSequences && flags.useNextSequences) {
      CoreLabel c = cInfo.get(loc);
      out.setSuffix("CnC");
      out.add("NSEQ");
      out.build().append(getWord(c)).append("-NSEQW").add();
    }
  }


  protected void featuresCpCnC(PaddedList<IN> cInfo, int loc, FeatureCollector out) {
    if (flags.useNext && flags.usePrev && flags.useSequences && flags.usePrevSequences && flags.useNextSequences) {
      CoreLabel c = cInfo.get(loc);
      out.setSuffix("CpCnC");
      out.add("PNSEQ");
      out.build().append(getWord(c)).append("-PNSEQW").add();
    }
  }

  private int reverse(int i) {
    return (flags.useReverse ? -1 * i : i);
  }

  private void occurrencePatterns(PaddedList<IN> cInfo, int loc, FeatureCollector out) {
    // features on last Cap
    String word = getWord(cInfo.get(loc));
    String nWord = getWord(cInfo.get(loc + reverse(1)));
    CoreLabel p = cInfo.get(loc - reverse(1));
    String pWord = getWord(p);
    // log.info(word+" "+nWord);
    if (!(isNameCase(word) && noUpperCase(nWord) && hasLetter(nWord) && hasLetter(pWord) && p != cInfo.getPad())) {
      out.add("NO-OCCURRENCE-PATTERN");
      return;
    }
    // log.info("LOOKING");
    if (cInfo.get(loc - reverse(1)).getString(CoreAnnotations.PartOfSpeechAnnotation.class) != null && isNameCase(pWord) && cInfo.get(loc - reverse(1)).getString(CoreAnnotations.PartOfSpeechAnnotation.class).equals("NNP")) {
      for (int jump = 3; jump < 150; jump++) {
        if (getWord(cInfo.get(loc + reverse(jump))).equals(word)) {
          if (getWord(cInfo.get(loc + reverse(jump - 1))).equals(pWord)) {
            out.add("XY-NEXT-OCCURRENCE-XY");
          } else {
            out.add("XY-NEXT-OCCURRENCE-Y");
          }
        }
      }
      for (int jump = -3; jump > -150; jump--) {
        if (getWord(cInfo.get(loc + reverse(jump))).equals(word)) {
          if (getWord(cInfo.get(loc + reverse(jump - 1))).equals(pWord)) {
            out.add("XY-PREV-OCCURRENCE-XY");
          } else {
            out.add("XY-PREV-OCCURRENCE-Y");
          }
        }
      }
    } else {
      for (int jump = 3; jump < 150; jump++) {
        if (getWord(cInfo.get(loc + reverse(jump))).equals(word)) {
          if (isNameCase(getWord(cInfo.get(loc + reverse(jump - 1)))) && (cInfo.get(loc + reverse(jump - 1))).getString(CoreAnnotations.PartOfSpeechAnnotation.class).equals("NNP")) {
            out.add("X-NEXT-OCCURRENCE-YX");
            // log.info(getWord(cInfo.get(loc+reverse(jump-1))));
          } else if (isNameCase(getWord(cInfo.get(loc + reverse(jump + 1)))) && (cInfo.get(loc + reverse(jump + 1))).getString(CoreAnnotations.PartOfSpeechAnnotation.class).equals("NNP")) {
            // log.info(getWord(cInfo.get(loc+reverse(jump+1))));
            out.add("X-NEXT-OCCURRENCE-XY");
          } else {
            out.add("X-NEXT-OCCURRENCE-X");
          }
        }
      }
      for (int jump = -3; jump > -150; jump--) {
        if (getWord(cInfo.get(loc + jump)) != null && getWord(cInfo.get(loc + jump)).equals(word)) {
          if (isNameCase(getWord(cInfo.get(loc + reverse(jump + 1)))) && (cInfo.get(loc + reverse(jump + 1))).getString(CoreAnnotations.PartOfSpeechAnnotation.class).equals("NNP")) {
            out.add("X-PREV-OCCURRENCE-YX");
            // log.info(getWord(cInfo.get(loc+reverse(jump+1))));
          } else if (isNameCase(getWord(cInfo.get(loc + reverse(jump - 1)))) && cInfo.get(loc + reverse(jump - 1)).getString(CoreAnnotations.PartOfSpeechAnnotation.class).equals("NNP")) {
            out.add("X-PREV-OCCURRENCE-XY");
            // log.info(getWord(cInfo.get(loc+reverse(jump-1))));
          } else {
            out.add("X-PREV-OCCURRENCE-X");
          }
        }
      }
    }
  }

  private String intern(String s) {
    return flags.intern ? s.intern() : s;
  }

  private void initGazette() {
    try {
      // read in gazettes
      if (flags.gazettes == null) { flags.gazettes = new ArrayList<>(); }
      List<String> gazettes = flags.gazettes;
      for (String gazetteFile : gazettes) {
        try (BufferedReader r = IOUtils.readerFromString(gazetteFile, flags.inputEncoding)) {
          readGazette(r);
        }
      }
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
  }

} // end class NERFeatureFactory
