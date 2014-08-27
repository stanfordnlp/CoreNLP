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
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
//
// For more information, bug reports, fixes, contact:
//    Christopher Manning
//    Dept of Computer Science, Gates 1A
//    Stanford CA 94305-9010
//    USA
//    Support/Questions: java-nlp-user@lists.stanford.edu
//    Licensing: java-nlp-support@lists.stanford.edu
//    http://nlp.stanford.edu/downloads/crf-classifier.shtml

package edu.stanford.nlp.ie;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.stanford.nlp.io.IOUtils;
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
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.trees.international.pennchinese.RadicalMap;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.PaddedList;
import edu.stanford.nlp.util.Timing;


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
 *     <code>featuresC</code> code, if both the current and previous class,
 *     then <code>featuresCpC</code>, etc.</li>
 * </ol>
 * <p> Parameters can be defined using a Properties file
 * (specified on the command-line with <code>-prop</code> <i>propFile</i>),
 * or directly on the command line. The following properties are recognized:
 * </p>
 * <table border="1">
 * <tr><td><b>Property Name</b></td><td><b>Type</b></td><td><b>Default Value</b></td><td><b>Description</b></td></tr>
 * <tr><td> loadClassifier </td><td>String</td><td>n/a</td><td>Path to serialized classifier to load</td></tr>
 * <tr><td> loadAuxClassifier </td><td>String</td><td>n/a</td><td>Path to auxiliary classifier to load.</td></tr>
 * <tr><td> serializeTo</td><td>String</td><td>n/a</td><td>Path to serialize classifier to</td></tr>
 * <tr><td> trainFile</td><td>String</td><td>n/a</td><td>Path of file to use as training data</td></tr>
 * <tr><td> testFile</td><td>String</td><td>n/a</td><td>Path of file to use as training data</td></tr>
 * <tr><td> map</td><td>String</td><td>see below</td><td>This applies at training time or if testing on tab-separated column data.  It says what is in each column.  It doesn't apply when running on plain text data.  The simplest scenario for training is having words and classes in two column.  word=0,answer=1 is the default if conllNoTags is specified; otherwise word=0,tag=1,answer=2 is the default.  But you can add other columns, such as for a part-of-speech tag, presences in a lexicon, etc.  That would only be useful at runtime if you have part-of-speech information or whatever available and are passing it in with the tokens (that is, you can pass to classify CoreLabel tokens with additional fields stored in them).</td></tr>
 * <tr><td> useWord</td><td>boolean</td><td>true</td><td>Gives you feature for w</td></tr>
 * <tr><td> useBinnedLength</td><td>String</td><td>null</td><td>If non-null, treat as a sequence of comma separated integer bounds, where items above the previous bound up to the next bound are binned Len-<i>range</i></td></tr>
 * <tr><td> useNGrams</td><td>boolean</td><td>false</td><td>Make features from letter n-grams, i.e., substrings of the word</td></tr>
 * <tr><td> lowercaseNGrams</td><td>boolean</td><td>false</td><td>Make features from letter n-grams only lowercase</td></tr>
 * <tr><td> dehyphenateNGrams</td><td>boolean</td><td>false</td><td>Remove hyphens before making features from letter n-grams</td></tr>
 * <tr><td> conjoinShapeNGrams</td><td>boolean</td><td>false</td><td>Conjoin word shape and n-gram features</td></tr>
 * <tr><td> useNeighborNGrams</td><td>boolean</td><td>false</td><td>Use letter n-grams for the previous and current words in the CpC clique.  This feature helps languages such as Chinese, but not so much for English</td></tr>
 * <tr><td> usePrev</td><td>boolean</td><td>false</td><td>Gives you feature for (pw,c), and together with other options enables other previous features, such as (pt,c) [with useTags)</td></tr>
 * <tr><td> useNext</td><td>boolean</td><td>false</td><td>Gives you feature for (nw,c), and together with other options enables other next features, such as (nt,c) [with useTags)</td></tr>
 * <tr><td> useTags</td><td>boolean</td><td>false</td><td>Gives you features for (t,c), (pt,c) [if usePrev], (nt,c) [if useNext]</td></tr>
 * <tr><td> useWordPairs</td><td>boolean</td><td>false</td><td>Gives you
 * features for (pw, w, c) and (w, nw, c)</td></tr>
 * <p>
 * <tr><td> useGazettes</td><td>boolean</td><td>false</td><td>If true, use gazette features (defined by other flags)</td></tr>
 * <tr><td> gazette</td><td>String</td><td>null</td><td>The value can be one or more filenames (names separated by a comma, semicolon or space).
 * If provided gazettes are loaded from these files.  Each line should be an entity class name, followed by whitespace followed by an entity (which might be a phrase of several tokens with a single space between words).
 * Giving this property turns on useGazettes, so you normally don't need to specify it (but can use it to turn off gazettes specified in a properties file).</td></tr>
 * <tr><td> sloppyGazette</td><td>boolean</td><td>false</td><td>If true, a gazette feature fires when any token of a gazette entry matches</td></tr>
 * <tr><td> cleanGazette</td><td>boolean</td><td>false</td><td></td>If true, a gazette feature fires when all tokens of a gazette entry match</tr>
 * <p>
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
 * <p/>
 * <tr><td> useSum</td><td>boolean</td><td>false</td><td></td></tr>
 * <tr><td> tolerance</td><td>double</td><td>1e-4</td><td>Convergence tolerance in optimization</td></tr>
 * <tr><td> printFeatures</td><td>String</td><td>null</td><td>print out all the features generated by the classifier for a dataset to a file based on this name (starting with "features-", suffixed "-1" and "-2" for train and test). This simply prints the feature names, one per line.</td></tr>
 * <tr><td> printFeaturesUpto</td><td>int</td><td>-1</td><td>Print out features for only the first this many datums, if the value is positive. </td></tr>
 * <p/>
 * <tr><td> useSymTags</td><td>boolean</td><td>false</td><td>Gives you
 * features (pt, t, nt, c), (t, nt, c), (pt, t, c)</td></tr>
 * <tr><td> useSymWordPairs</td><td>boolean</td><td>false</td><td>Gives you
 * features (pw, nw, c)</td></tr>
 * <p/>
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
 * <p/>
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
 * <tr><td> useClassFeature</td><td>boolean</td><td>false</td><td>Include a feature for the class (as a class marginal).  Puts a prior on the classes which is equivalent to how often the feature appeared in the training data.</td></tr>
 * <tr><td> useShapeConjunctions</td><td>boolean</td><td>false</td><td>Conjoin shape with tag or position</td></tr>
 * <tr><td> useWordTag</td><td>boolean</td><td>false</td><td>Include word and tag pair features</td></tr>
 * <tr><td> useLastRealWord</td><td>boolean</td><td>false</td><td>Iff the prev word is of length 3 or less, add an extra feature that combines the word two back and the current word's shape. <i>Weird!</i></td></tr>
 * <tr><td> useNextRealWord</td><td>boolean</td><td>false</td><td>Iff the next word is of length 3 or less, add an extra feature that combines the word after next and the current word's shape. <i>Weird!</i></td></tr>
 * <tr><td> useTitle</td><td>boolean</td><td>false</td><td>Match a word against a list of name titles (Mr, Mrs, etc.)</td></tr>
 * <tr><td> useOccurrencePatterns</td><td>boolean</td><td>false</td><td>This is a very engineered feature designed to capture multiple references to names.  If the current word isn't capitalized, followed by a non-capitalized word, and preceded by a word with alphabetic characters, it returns NO-OCCURRENCE-PATTERN.  Otherwise, if the previous word is a capitalized NNP, then if in the next 150 words you find this PW-W sequence, you get XY-NEXT-OCCURRENCE-XY, else if you find W you get XY-NEXT-OCCURRENCE-Y.  Similarly for backwards and XY-PREV-OCCURRENCE-XY and XY-PREV-OCCURRENCE-Y.  Else (if the previous word isn't a capitalized NNP), under analogous rules you get one or more of X-NEXT-OCCURRENCE-YX, X-NEXT-OCCURRENCE-XY, X-NEXT-OCCURRENCE-X, X-PREV-OCCURRENCE-YX, X-PREV-OCCURRENCE-XY, X-PREV-OCCURRENCE-X.</td></tr>
 * <tr><td> useTypeySequences</td><td>boolean</td><td>false</td><td>Some first order word shape patterns.</td></tr>
 * <tr><td> useGenericFeatures</td><td>boolean</td><td>false</td><td>If true, any features you include in the map will be incorporated into the model with values equal to those given in the file; values are treated as strings unless you use the "realValued" option (described below)</td></tr>
 * <tr><td> justify</td><td>boolean</td><td>false</td><td>Print out all
 * feature/class pairs and their weight, and then for each input data
 * point, print justification (weights) for active features</td></tr>
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
 * <p/>
 * Note: flags/properties overwrite left to right.  That is, the parameter
 * setting specified <i>last</i> is the one used.
 * <p/>
 * <pre>
 * DOCUMENTATION ON FEATURE TEMPLATES
 * <p/>
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
 * <p/>
 * useReverse reverses meaning of prev, next everywhere below (on in macro)
 * <p/>
 * "Prolog" booleans: , = AND and ; = OR
 * <p/>
 * Mac: Y = turned on in -macro,
 *      + = additional positive things relative to -macro for CoNLL NERFeatureFactory
 *          (perhaps none...)
 *      - = Known negative for CoNLL NERFeatureFactory relative to -macro
 * <p/>p
 * Bio: + = additional things that are positive for BioCreative
 *      - = things negative relative to -macro
 * <p/>
 * HighMagnitude: There are no (0) to a few (+) to many (+++) high weight
 * features of this template. (? = not used in goodCoNLL, but usually = 0)
 * <p/>
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
 * <p/>
 * pc,c                   Y     usePrev,useSequences,usePrevSequences   +++
 * pc,w,c                 Y     usePrev,useSequences,usePrevSequences   0
 * nc,c                         useNext,useSequences,useNextSequences   ?
 * w,nc,c                       useNext,useSequences,useNextSequences   ?
 * pc,nc,c                      useNext,usePrev,useSequences,usePrevSequences,useNextSequences  ?
 * w,pc,nc,c                    useNext,usePrev,useSequences,usePrevSequences,useNextSequences   ?
 * <p/>
 * (pw;p2w;p3w;p4w),c        +  useDisjunctive  (out to disjunctionWidth now)   +++
 * (nw;n2w;n3w;n4w),c        +  useDisjunctive  (out to disjunctionWidth now)   ++++
 * (pw;p2w;p3w;p4w),s,c      +  useDisjunctiveShapeInteraction          ?
 * (nw;n2w;n3w;n4w),s,c      +  useDisjunctiveShapeInteraction          ?
 * (pw;p2w;p3w;p4w),c        +  useWideDisjunctive (to wideDisjunctionWidth)   ?
 * (nw;n2w;n3w;n4w),c        +  useWideDisjunctive (to wideDisjunctionWidth)   ?
 * (ps;p2s;p3s;p4s),c           useDisjShape  (out to disjunctionWidth now)   ?
 * (ns;n2s;n3s;n4s),c           useDisjShape  (out to disjunctionWidth now)   ?
 * <p/>
 * pt,pc,t,c              Y     useTaggySequences                        +
 * p2t,p2c,pt,pc,t,c      Y     useTaggySequences,maxLeft&gt;=2          +
 * p3t,p3c,p2t,p2c,pt,pc,t,c Y  useTaggySequences,maxLeft&gt;=3,!dontExtendTaggy   ?
 * p2c,pc,c               Y     useLongSequences                         ++
 * p3c,p2c,pc,c           Y     useLongSequences,maxLeft&gt;=3           ?
 * p4c,p3c,p2c,pc,c       Y     useLongSequences,maxLeft&gt;=4           ?
 * p2c,pc,c,pw=BOUNDARY         useBoundarySequences                     0 (OK, but!)
 * <p/>
 * p2t,pt,t,c             -     useExtraTaggySequences                   ?
 * p3t,p2t,pt,t,c         -     useExtraTaggySequences                   ?
 * <p/>
 * p2t,pt,t,s,p2c,pc,c    -     useTaggySequencesShapeInteraction        ?
 * p3t,p2t,pt,t,s,p3c,p2c,pc,c  useTaggySequencesShapeInteraction        ?
 * <p/>
 * s,pc,c                 Y     useTypeySequences                        ++
 * ns,pc,c                Y     useTypeySequences  // error for ps? not? 0
 * ps,pc,s,c              Y     useTypeySequences                        0
 * // p2s,p2c,ps,pc,s,c      Y     useTypeySequences,maxLeft&gt;=2 // duplicated a useTypeSeqs2 feature
 * <p/>
 * n(w),c                 Y     useNGrams (noMidNGrams, MaxNGramLeng, lowercaseNGrams, dehyphenateNGrams)   +++
 * n(w),s,c                     useNGrams,conjoinShapeNGrams             ?
 * <p/>
 * g,c                        + useGazFeatures   // test refining this?   ?
 * pg,pc,c                    + useGazFeatures                           ?
 * ng,c                       + useGazFeatures                           ?
 * // pg,g,c                    useGazFeatures                           ?
 * // pg,g,ng,c                 useGazFeatures                           ?
 * // p2g,p2c,pg,pc,g,c         useGazFeatures                           ?
 * g,w,c                        useMoreGazFeatures                       ?
 * pg,pc,g,c                    useMoreGazFeatures                       ?
 * g,ng,c                       useMoreGazFeatures                       ?
 * <p/>
 * g(w),c                       useGazette,sloppyGazette (contains same word)   ?
 * g(w),[pw,nw,...],c           useGazette,cleanGazette (entire entry matches)   ?
 * <p/>
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
 * <p/>
 * p2w,s,c if l(pw) &lt;= 3 Y     useLastRealWord // weird features, but work   0
 * n2w,s,c if l(nw) &lt;= 3 Y     useNextRealWord                        ++
 * o(pw,w,nw),c           Y     useOccurrencePatterns // don't fully grok but has to do with capitalized name patterns   ++
 * <p/>
 * a,c                          useAbbr;useMinimalAbbr
 * pa,a,c                       useAbbr
 * a,na,c                       useAbbr
 * pa,a,na,c                    useAbbr
 * pa,pc,a,c                    useAbbr;useMinimalAbbr
 * p2a,p2c,pa,pc,a              useAbbr
 * w,a,c                        useMinimalAbbr
 * p2a,p2c,a,c                  useMinimalAbbr
 * <p/>
 * RESTR. w,(pw,pc;p2w,p2c;p3w,p3c;p4w,p4c)   + useParenMatching,maxLeft&gt;=n
 * <p/>
 * c                          - useClassFeature
 * <p/>
  * p,s,c                      - useShapeConjunctions
 * t,s,c                      - useShapeConjunctions
 * <p/>
 * w,t,c                      + useWordTag                      ?
 * w,pt,c                     + useWordTag                      ?
 * w,nt,c                     + useWordTag                      ?
 * <p/>
 * r,c                          useNPGovernor (only for baseNP words)
 * r,t,c                        useNPGovernor (only for baseNP words)
 * h,c                          useNPHead (only for baseNP words)
 * h,t,c                        useNPHead (only for baseNP words)
 * <p/>
 * </pre>
 *
 * @author Dan Klein
 * @author Jenny Finkel
 * @author Christopher Manning
 * @author Shipra Dingare
 * @author Huy Nguyen
 * @author Mengqiu Wang
 */
public class NERFeatureFactory<IN extends CoreLabel> extends FeatureFactory<IN> {

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
    Collection<String> features = Generics.newHashSet();
    String domain = cInfo.get(0).get(CoreAnnotations.DomainAnnotation.class);
    final boolean doFE = domain != null;

//    System.err.println(doFE+"\t"+domain);

    // there are two special cases below, because 2 cliques have 2 names
    Collection<String> c;
    String suffix;
    if (clique == cliqueC) {
      //200710: tried making this clique null; didn't improve performance (rafferty)
      c = featuresC(cInfo, loc);
      suffix = "C";
    } else if (clique == cliqueCpC) {
      c = featuresCpC(cInfo, loc);
      suffix = "CpC";
      addAllInterningAndSuffixing(features, c, suffix);
      if (doFE) {
        addAllInterningAndSuffixing(features, c, domain + '-' + suffix);
      }
      c = featuresCnC(cInfo, loc-1);
      suffix = "CnC";
    } else if (clique == cliqueCp2C) {
      c = featuresCp2C(cInfo, loc);
      suffix = "Cp2C";
    } else if (clique == cliqueCp3C) {
      c = featuresCp3C(cInfo, loc);
      suffix = "Cp3C";
    } else if (clique == cliqueCp4C) {
      c = featuresCp4C(cInfo, loc);
      suffix = "Cp4C";
    } else if (clique == cliqueCp5C) {
      c = featuresCp5C(cInfo, loc);
      suffix = "Cp5C";
    } else if (clique == cliqueCpCp2C) {
      c = featuresCpCp2C(cInfo, loc);
      suffix = "CpCp2C";
      addAllInterningAndSuffixing(features, c, suffix);
      if (doFE) {
        addAllInterningAndSuffixing(features, c, domain+ '-' + suffix);
      }
      c = featuresCpCnC(cInfo, loc-1);
      suffix = "CpCnC";
    } else if (clique == cliqueCpCp2Cp3C) {
      c = featuresCpCp2Cp3C(cInfo, loc);
      suffix = "CpCp2Cp3C";
    } else if (clique == cliqueCpCp2Cp3Cp4C) {
      c = featuresCpCp2Cp3Cp4C(cInfo, loc);
      suffix = "CpCp2Cp3Cp4C";
    } else {
      throw new IllegalArgumentException("Unknown clique: " + clique);
    }

    addAllInterningAndSuffixing(features, c, suffix);
    if (doFE) {
      addAllInterningAndSuffixing(features, c, domain + '-' + suffix);
    }

    // System.err.println(StringUtils.join(features,"\n")+"\n");
    return features;
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
    Timing.startDoing("Loading distsim lexicon from " + flags.distSimLexicon);
    lexicon = Generics.newHashMap();
    boolean terryKoo = "terryKoo".equals(flags.distSimFileFormat);
    for (String line : ObjectBank.getLineIterator(flags.distSimLexicon,
                                                  flags.inputEncoding)) {
      String word;
      String wordClass;
      if (terryKoo) {
        String[] bits = line.split("\\t");
        word = bits[1];
        wordClass = bits[0];
        if (flags.distSimMaxBits > 0 && wordClass.length() > flags.distSimMaxBits) {
          wordClass = wordClass.substring(0, flags.distSimMaxBits);
        }
      } else {
        // "alexClark"
        String[] bits = line.split("\\s+");
        word = bits[0];
        wordClass = bits[1];
      }
      if ( ! flags.casedDistSim) {
        word = word.toLowerCase();
      }
      if (flags.numberEquivalenceDistSim) {
        word = WordShapeClassifier.wordShape(word, WordShapeClassifier.WORDSHAPEDIGITS);
      }
      lexicon.put(word, wordClass);
    }
    Timing.endDoing();
  }


  private void distSimAnnotate(PaddedList<IN> info) {
    for (CoreLabel fl : info) {
      if (fl.has(CoreAnnotations.DistSimAnnotation.class)) { return; }
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


  protected Collection<String> featuresC(PaddedList<IN> cInfo, int loc) {
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

    Collection<String> featuresC = new ArrayList<String>();

    if (flags.useDistSim) {
      distSimAnnotate(cInfo);
    }

    if (flags.useBagOfWords) {
      for (IN word : cInfo) {
        featuresC.add(getWord(word) + "-BAGOFWORDS");
      }
    }

    if (flags.useDistSim && flags.useMoreTags) {
      featuresC.add(p.get(CoreAnnotations.DistSimAnnotation.class) + '-' + cWord + "-PDISTSIM-CWORD");
    }


    if (flags.useDistSim) {
      featuresC.add(c.get(CoreAnnotations.DistSimAnnotation.class) + "-DISTSIM");
    }


    if (flags.useTitle) {
      Matcher m = titlePattern.matcher(cWord);
      if (m.matches()) {
        featuresC.add("IS_TITLE");
      }
    }


    if (flags.useInternal && flags.useExternal ) {

      if (flags.useWord) {
        featuresC.add(cWord + "-WORD");
      }

      if (flags.use2W) {
        featuresC.add(getWord(p2) + "-P2W");
        featuresC.add(getWord(n2) + "-N2W");
      }

      if (flags.useLC) {
        featuresC.add(cWord.toLowerCase() + "-CL");
        featuresC.add(pWord.toLowerCase() + "-PL");
        featuresC.add(nWord.toLowerCase() + "-NL");
      }

      if (flags.useUnknown) { // for true casing
        featuresC.add(c.get(CoreAnnotations.UnknownAnnotation.class)+"-UNKNOWN");
        featuresC.add(p.get(CoreAnnotations.UnknownAnnotation.class)+"-PUNKNOWN");
        featuresC.add(n.get(CoreAnnotations.UnknownAnnotation.class)+"-NUNKNOWN");
      }

      if (flags.useLemmas) {
        String lem = c.getString(CoreAnnotations.LemmaAnnotation.class);
        if (! "".equals(lem)) {
          featuresC.add(lem + "-LEM");
        }
      }
      if (flags.usePrevNextLemmas) {
        String plem = p.getString(CoreAnnotations.LemmaAnnotation.class);
        String nlem = n.getString(CoreAnnotations.LemmaAnnotation.class);
        if (! "".equals(plem)) {
          featuresC.add(plem + "-PLEM");
        }
        if (! "".equals(nlem)) {
          featuresC.add(nlem + "-NLEM");
        }
      }

      if (flags.checkNameList) {
        try {
          if (lastNames == null) {
            lastNames = Generics.newHashSet();

            for (String line : ObjectBank.getLineIterator(flags.lastNameList)) {
              String[] cols = line.split("\\s+");
              lastNames.add(cols[0]);
            }
          }
          if (maleNames == null) {
            maleNames = Generics.newHashSet();
            for (String line : ObjectBank.getLineIterator(flags.maleNameList)) {
              String[] cols = line.split("\\s+");
              maleNames.add(cols[0]);
            }
          }
          if (femaleNames == null) {
            femaleNames = Generics.newHashSet();
            for (String line : ObjectBank.getLineIterator(flags.femaleNameList)) {
              String[] cols = line.split("\\s+");
              femaleNames.add(cols[0]);
            }
          }

          String name = cWord.toUpperCase();
          if (lastNames.contains(name)) {
            featuresC.add("LAST_NAME");
          }

          if (maleNames.contains(name)) {
            featuresC.add("MALE_NAME");
          }

          if (femaleNames.contains(name)) {
            featuresC.add("FEMALE_NAME");
          }

        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }

      if (flags.binnedLengths != null) {
        int len = cWord.length();
        String featureName = null;
        for (int i = 0; i <= flags.binnedLengths.length; i++) {
          if (i == flags.binnedLengths.length) {
            featureName = "Len-" + flags.binnedLengths[flags.binnedLengths.length - 1] + "-Inf";
          } else if (len <= flags.binnedLengths[i]) {
            featureName = "Len-" + ((i == 0) ? 1 : flags.binnedLengths[i - 1]) + '-' + flags.binnedLengths[i];
            break;
          }
        }
        featuresC.add(featureName);
      }

      if (flags.useABGENE) {
        featuresC.add(c.get(CoreAnnotations.AbgeneAnnotation.class) + "-ABGENE");
        featuresC.add(p.get(CoreAnnotations.AbgeneAnnotation.class) + "-PABGENE");
        featuresC.add(n.get(CoreAnnotations.AbgeneAnnotation.class) + "-NABGENE");
      }

      if (flags.useABSTRFreqDict) {
        featuresC.add(c.get(CoreAnnotations.AbstrAnnotation.class) + "-ABSTRACT" + c.get(CoreAnnotations.FreqAnnotation.class) + "-FREQ" + c.getString(CoreAnnotations.PartOfSpeechAnnotation.class) + "-TAG");
        featuresC.add(c.get(CoreAnnotations.AbstrAnnotation.class) + "-ABSTRACT" + c.get(CoreAnnotations.DictAnnotation.class) + "-DICT" + c.getString(CoreAnnotations.PartOfSpeechAnnotation.class) + "-TAG");
        featuresC.add(c.get(CoreAnnotations.AbstrAnnotation.class) + "-ABSTRACT" + c.get(CoreAnnotations.DictAnnotation.class) + "-DICT" + c.get(CoreAnnotations.FreqAnnotation.class) + "-FREQ" + c.getString(CoreAnnotations.PartOfSpeechAnnotation.class) + "-TAG");
      }

      if (flags.useABSTR) {
        featuresC.add(c.get(CoreAnnotations.AbstrAnnotation.class) + "-ABSTRACT");
        featuresC.add(p.get(CoreAnnotations.AbstrAnnotation.class) + "-PABSTRACT");
        featuresC.add(n.get(CoreAnnotations.AbstrAnnotation.class) + "-NABSTRACT");
      }

      if (flags.useGENIA) {
        featuresC.add(c.get(CoreAnnotations.GeniaAnnotation.class) + "-GENIA");
        featuresC.add(p.get(CoreAnnotations.GeniaAnnotation.class) + "-PGENIA");
        featuresC.add(n.get(CoreAnnotations.GeniaAnnotation.class) + "-NGENIA");
      }
      if (flags.useWEBFreqDict) {
        featuresC.add(c.get(CoreAnnotations.WebAnnotation.class) + "-WEB" + c.get(CoreAnnotations.FreqAnnotation.class) + "-FREQ" + c.getString(CoreAnnotations.PartOfSpeechAnnotation.class) + "-TAG");
        featuresC.add(c.get(CoreAnnotations.WebAnnotation.class) + "-WEB" + c.get(CoreAnnotations.DictAnnotation.class) + "-DICT" + c.getString(CoreAnnotations.PartOfSpeechAnnotation.class) + "-TAG");
        featuresC.add(c.get(CoreAnnotations.WebAnnotation.class) + "-WEB" + c.get(CoreAnnotations.DictAnnotation.class) + "-DICT" + c.get(CoreAnnotations.FreqAnnotation.class) + "-FREQ" + c.getString(CoreAnnotations.PartOfSpeechAnnotation.class) + "-TAG");
      }

      if (flags.useWEB) {
        featuresC.add(c.get(CoreAnnotations.WebAnnotation.class) + "-WEB");
        featuresC.add(p.get(CoreAnnotations.WebAnnotation.class) + "-PWEB");
        featuresC.add(n.get(CoreAnnotations.WebAnnotation.class) + "-NWEB");
      }

      if (flags.useIsURL) {
        featuresC.add(c.get(CoreAnnotations.IsURLAnnotation.class) + "-ISURL");
      }
      if (flags.useEntityRule) {
        featuresC.add(c.get(CoreAnnotations.EntityRuleAnnotation.class)+"-ENTITYRULE");
      }
      if (flags.useEntityTypes) {
        featuresC.add(c.get(CoreAnnotations.EntityTypeAnnotation.class) + "-ENTITYTYPE");
      }
      if (flags.useIsDateRange) {
        featuresC.add(c.get(CoreAnnotations.IsDateRangeAnnotation.class) + "-ISDATERANGE");
      }

      if (flags.useABSTRFreq) {
        featuresC.add(c.get(CoreAnnotations.AbstrAnnotation.class) + "-ABSTRACT" + c.get(CoreAnnotations.FreqAnnotation.class) + "-FREQ");
      }

      if (flags.useFREQ) {
        featuresC.add(c.get(CoreAnnotations.FreqAnnotation.class) + "-FREQ");
      }

      if (flags.useMoreTags) {
        featuresC.add(p.getString(CoreAnnotations.PartOfSpeechAnnotation.class) + '-' + cWord + "-PTAG-CWORD");
      }

      if (flags.usePosition) {
        featuresC.add(c.get(CoreAnnotations.PositionAnnotation.class) + "-POSITION");
      }
      if (flags.useBeginSent) {
        String pos = c.get(CoreAnnotations.PositionAnnotation.class);
        if ("0".equals(pos)) {
          featuresC.add("BEGIN-SENT");
          featuresC.add(cShape + "-BEGIN-SENT");
        } else if (Integer.toString(cInfo.size() - 1).equals(pos)) {
          featuresC.add("END-SENT");
          featuresC.add(cShape + "-END-SENT");
        } else {
          featuresC.add("IN-SENT");
          featuresC.add(cShape + "-IN-SENT");
        }
      }
      if (flags.useTags) {
        featuresC.add(c.getString(CoreAnnotations.PartOfSpeechAnnotation.class) + "-TAG");
      }

      if (flags.useOrdinal) {
        if (isOrdinal(cInfo, loc)) {
          featuresC.add("C_ORDINAL");
          if (isOrdinal(cInfo, loc-1)) {
            //System.err.print(getWord(p) + " ");
            featuresC.add("PC_ORDINAL");
          }
          //System.err.println(cWord);
        }
        if (isOrdinal(cInfo, loc-1)) {
          featuresC.add("P_ORDINAL");
        }
      }

      if (flags.usePrev) {
        featuresC.add(pWord + "-PW");
        if (flags.useTags) {
          featuresC.add(p.getString(CoreAnnotations.PartOfSpeechAnnotation.class) + "-PTAG");
        }
        if (flags.useDistSim) {
          featuresC.add(p.get(CoreAnnotations.DistSimAnnotation.class) + "-PDISTSIM");
        }
        if (flags.useIsURL) {
          featuresC.add(p.get(CoreAnnotations.IsURLAnnotation.class) + "-PISURL");
        }
        if (flags.useEntityTypes) {
          featuresC.add(p.get(CoreAnnotations.EntityTypeAnnotation.class) + "-PENTITYTYPE");
        }
      }

      if (flags.useNext) {
        featuresC.add(nWord + "-NW");
        if (flags.useTags) {
          featuresC.add(n.getString(CoreAnnotations.PartOfSpeechAnnotation.class) + "-NTAG");
        }
        if (flags.useDistSim) {
          featuresC.add(n.get(CoreAnnotations.DistSimAnnotation.class) + "-NDISTSIM");
        }
        if (flags.useIsURL) {
          featuresC.add(n.get(CoreAnnotations.IsURLAnnotation.class) + "-NISURL");
        }
        if (flags.useEntityTypes) {
          featuresC.add(n.get(CoreAnnotations.EntityTypeAnnotation.class) + "-NENTITYTYPE");
        }
      }
      /*here, entityTypes refers to the type in the PASCAL IE challenge:
       * i.e. certain words are tagged "Date" or "Location" */


      if (flags.useEitherSideWord) {
        featuresC.add(pWord + "-EW");
        featuresC.add(nWord + "-EW");
      }

      if (flags.useWordPairs) {
        featuresC.add(cWord + '-' + pWord + "-W-PW");
        featuresC.add(cWord + '-' + nWord + "-W-NW");
      }

      if (flags.useSymTags) {
        if (flags.useTags) {
          featuresC.add(p.getString(CoreAnnotations.PartOfSpeechAnnotation.class) + '-' + c.getString(CoreAnnotations.PartOfSpeechAnnotation.class) + '-' + n.getString(CoreAnnotations.PartOfSpeechAnnotation.class) + "-PCNTAGS");
          featuresC.add(c.getString(CoreAnnotations.PartOfSpeechAnnotation.class) + '-' + n.getString(CoreAnnotations.PartOfSpeechAnnotation.class) + "-CNTAGS");
          featuresC.add(p.getString(CoreAnnotations.PartOfSpeechAnnotation.class) + '-' + c.getString(CoreAnnotations.PartOfSpeechAnnotation.class) + "-PCTAGS");
        }
        if (flags.useDistSim) {
          featuresC.add(p.get(CoreAnnotations.DistSimAnnotation.class) + '-' + c.get(CoreAnnotations.DistSimAnnotation.class) + '-' + n.get(CoreAnnotations.DistSimAnnotation.class) + "-PCNDISTSIM");
          featuresC.add(c.get(CoreAnnotations.DistSimAnnotation.class) + '-' + n.get(CoreAnnotations.DistSimAnnotation.class) + "-CNDISTSIM");
          featuresC.add(p.get(CoreAnnotations.DistSimAnnotation.class) + '-' + c.get(CoreAnnotations.DistSimAnnotation.class) + "-PCDISTSIM");
        }

      }

      if (flags.useSymWordPairs) {
        featuresC.add(pWord + '-' + nWord + "-SWORDS");
      }

      String pGazAnnotation = (flags.useGazFeatures || flags.useMoreGazFeatures) ? p.get(CoreAnnotations.GazAnnotation.class) : null;
      String nGazAnnotation = (flags.useGazFeatures || flags.useMoreGazFeatures) ? n.get(CoreAnnotations.GazAnnotation.class) : null;
      String cGazAnnotation = (flags.useGazFeatures || flags.useMoreGazFeatures) ? c.get(CoreAnnotations.GazAnnotation.class) : null;
      if (flags.useGazFeatures) {

        if (cGazAnnotation != null && !cGazAnnotation.equals(flags.dropGaz)) {
          featuresC.add(cGazAnnotation + "-GAZ");
        }
        // n
        if (nGazAnnotation != null && !nGazAnnotation.equals(flags.dropGaz)) {
          featuresC.add(nGazAnnotation + "-NGAZ");
        }
        // p
        if (pGazAnnotation != null && !pGazAnnotation.equals(flags.dropGaz)) {
          featuresC.add(pGazAnnotation + "-PGAZ");
        }
      }

      if (flags.useMoreGazFeatures) {
        if (cGazAnnotation != null && !cGazAnnotation.equals(flags.dropGaz)) {
          featuresC.add(cGazAnnotation + '-' + cWord + "-CG-CW-GAZ");

          // c-n
          if (nGazAnnotation != null && !nGazAnnotation.equals(flags.dropGaz)) {
            featuresC.add(cGazAnnotation + '-' + nGazAnnotation + "-CNGAZ");
          }

          // p-c
          if (pGazAnnotation != null && !pGazAnnotation.equals(flags.dropGaz)) {
            featuresC.add(pGazAnnotation + '-' + cGazAnnotation + "-PCGAZ");
          }
        }
      }

      if (flags.useAbbr || flags.useMinimalAbbr) {
        featuresC.add(c.get(CoreAnnotations.AbbrAnnotation.class) + "-ABBR");
      }

      if (flags.useAbbr1 || flags.useMinimalAbbr1) {
        if (!c.get(CoreAnnotations.AbbrAnnotation.class).equals("XX")) {
          featuresC.add(c.get(CoreAnnotations.AbbrAnnotation.class) + "-ABBR");
        }
      }

      if (flags.useAbbr) {
        featuresC.add(p.get(CoreAnnotations.AbbrAnnotation.class) + '-' + c.get(CoreAnnotations.AbbrAnnotation.class) + "-PCABBR");
        featuresC.add(c.get(CoreAnnotations.AbbrAnnotation.class) + '-' + n.get(CoreAnnotations.AbbrAnnotation.class) + "-CNABBR");
        featuresC.add(p.get(CoreAnnotations.AbbrAnnotation.class) + '-' + c.get(CoreAnnotations.AbbrAnnotation.class) + '-' + n.get(CoreAnnotations.AbbrAnnotation.class) + "-PCNABBR");
      }

      if (flags.useAbbr1) {
        if (!c.get(CoreAnnotations.AbbrAnnotation.class).equals("XX")) {
          featuresC.add(p.get(CoreAnnotations.AbbrAnnotation.class) + '-' + c.get(CoreAnnotations.AbbrAnnotation.class) + "-PCABBR");
          featuresC.add(c.get(CoreAnnotations.AbbrAnnotation.class) + '-' + n.get(CoreAnnotations.AbbrAnnotation.class) + "-CNABBR");
          featuresC.add(p.get(CoreAnnotations.AbbrAnnotation.class) + '-' + c.get(CoreAnnotations.AbbrAnnotation.class) + '-' + n.get(CoreAnnotations.AbbrAnnotation.class) + "-PCNABBR");
        }
      }

      if (flags.useChunks) {
        featuresC.add(p.get(CoreAnnotations.ChunkAnnotation.class) + '-' + c.get(CoreAnnotations.ChunkAnnotation.class) + "-PCCHUNK");
        featuresC.add(c.get(CoreAnnotations.ChunkAnnotation.class) + '-' + n.get(CoreAnnotations.ChunkAnnotation.class) + "-CNCHUNK");
        featuresC.add(p.get(CoreAnnotations.ChunkAnnotation.class) + '-' + c.get(CoreAnnotations.ChunkAnnotation.class) + '-' + n.get(CoreAnnotations.ChunkAnnotation.class) + "-PCNCHUNK");
      }

      if (flags.useMinimalAbbr) {
        featuresC.add(cWord + '-' + c.get(CoreAnnotations.AbbrAnnotation.class) + "-CWABB");
      }

      if (flags.useMinimalAbbr1) {
        if (!c.get(CoreAnnotations.AbbrAnnotation.class).equals("XX")) {
          featuresC.add(cWord + '-' + c.get(CoreAnnotations.AbbrAnnotation.class) + "-CWABB");
        }
      }

      String prevVB = "", nextVB = "";
      if (flags.usePrevVB) {
        for (int j = loc - 1; ; j--) {
          CoreLabel wi = cInfo.get(j);
          if (wi == cInfo.getPad()) {
            prevVB = "X";
            featuresC.add("X-PVB");
            break;
          } else if (wi.getString(CoreAnnotations.PartOfSpeechAnnotation.class).startsWith("VB")) {
            featuresC.add(getWord(wi) + "-PVB");
            prevVB = getWord(wi);
            break;
          }
        }
      }

      if (flags.useNextVB) {
        for (int j = loc + 1; ; j++) {
          CoreLabel wi = cInfo.get(j);
          if (wi == cInfo.getPad()) {
            featuresC.add("X-NVB");
            nextVB = "X";
            break;
          } else if (wi.getString(CoreAnnotations.PartOfSpeechAnnotation.class).startsWith("VB")) {
            featuresC.add(getWord(wi) + "-NVB");
            nextVB = getWord(wi);
            break;
          }
        }
      }

      if (flags.useVB) {
        featuresC.add(prevVB + '-' + nextVB + "-PNVB");
      }

      if (flags.useShapeConjunctions) {
        featuresC.add(c.get(CoreAnnotations.PositionAnnotation.class) + cShape + "-POS-SH");
        if (flags.useTags) {
          featuresC.add(c.tag() + cShape + "-TAG-SH");
        }
        if (flags.useDistSim) {
          featuresC.add(c.get(CoreAnnotations.DistSimAnnotation.class) + cShape + "-DISTSIM-SH");
        }

      }

      if (flags.useWordTag) {
        featuresC.add(cWord + '-' + c.getString(CoreAnnotations.PartOfSpeechAnnotation.class) + "-W-T");
        featuresC.add(cWord + '-' + p.getString(CoreAnnotations.PartOfSpeechAnnotation.class) + "-W-PT");
        featuresC.add(cWord + '-' + n.getString(CoreAnnotations.PartOfSpeechAnnotation.class) + "-W-NT");
      }

      if (flags.useNPHead) {
        featuresC.add(c.get(TreeCoreAnnotations.HeadWordAnnotation.class) + "-HW");
        if (flags.useTags) {
          featuresC.add(c.get(TreeCoreAnnotations.HeadWordAnnotation.class) + "-" + c.getString(CoreAnnotations.PartOfSpeechAnnotation.class) + "-HW-T");
        }
        if (flags.useDistSim) {
          featuresC.add(c.get(TreeCoreAnnotations.HeadWordAnnotation.class) + "-" + c.get(CoreAnnotations.DistSimAnnotation.class) + "-HW-DISTSIM");
        }
      }

      if (flags.useNPGovernor) {
        featuresC.add(c.get(CoreAnnotations.GovernorAnnotation.class) + "-GW");
        if (flags.useTags) {
          featuresC.add(c.get(CoreAnnotations.GovernorAnnotation.class) + '-' + c.getString(CoreAnnotations.PartOfSpeechAnnotation.class) + "-GW-T");
        }
        if (flags.useDistSim) {
          featuresC.add(c.get(CoreAnnotations.GovernorAnnotation.class) + '-' + c.get(CoreAnnotations.DistSimAnnotation.class) + "-DISTSIM-T1");
        }
      }

      if (flags.useHeadGov) {
        featuresC.add(c.get(TreeCoreAnnotations.HeadWordAnnotation.class) + "-" + c.get(CoreAnnotations.GovernorAnnotation.class) + "-HW_GW");
      }

      if (flags.useClassFeature) {
        featuresC.add("###");
      }

      if (flags.useFirstWord) {
        String firstWord = getWord(cInfo.get(0));
        featuresC.add(firstWord);
      }

      if (flags.useNGrams) {
        Collection<String> subs = null;
        if (flags.cacheNGrams) {
          subs = wordToSubstrings.get(cWord);
        }
        if (subs == null) {
          subs = new ArrayList<String>();
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
        featuresC.addAll(subs);
        if (flags.conjoinShapeNGrams) {
          for (String str : subs) {
            String feat = str + '-' + cShape + "-CNGram-CS";
            featuresC.add(feat);
          }
        }
      }

      if (flags.useGazettes) {
        if (flags.sloppyGazette) {
          Collection<String> entries = wordToGazetteEntries.get(cWord);
          if (entries != null) {
            featuresC.addAll(entries);
          }
        }
        if (flags.cleanGazette) {
          Collection<GazetteInfo> infos = wordToGazetteInfos.get(cWord);
          if (infos != null) {
            for (GazetteInfo gInfo : infos) {
              boolean ok = true;
              for (int gLoc = 0; gLoc < gInfo.words.length; gLoc++) {
                ok &= gInfo.words[gLoc].equals(getWord(cInfo.get(loc + gLoc - gInfo.loc)));
              }
              if (ok) {
                featuresC.add(gInfo.feature);
              }
            }
          }
        }
      }

      if ((flags.wordShape > WordShapeClassifier.NOWORDSHAPE) || (flags.useShapeStrings)) {
        featuresC.add(cShape + "-TYPE");
        if (flags.useTypeSeqs) {
          featuresC.add(pShape + "-PTYPE");
          featuresC.add(nShape + "-NTYPE");
          featuresC.add(pWord + "..." + cShape + "-PW_CTYPE");
          featuresC.add(cShape + "..." + nWord + "-NW_CTYPE");
          featuresC.add(pShape + "..." + cShape + "-PCTYPE");
          featuresC.add(cShape + "..." + nShape + "-CNTYPE");
          featuresC.add(pShape + "..." + cShape + "..." + nShape + "-PCNTYPE");
        }
      }

      if (flags.useLastRealWord) {
        if (pWord.length() <= 3) {
          // extending this to check for 2 short words doesn't seem to help....
          featuresC.add(getWord(p2) + "..." + cShape + "-PPW_CTYPE");
        }
      }

      if (flags.useNextRealWord) {
        if (nWord.length() <= 3) {
          // extending this to check for 2 short words doesn't seem to help....
          featuresC.add(getWord(n2) + "..." + cShape + "-NNW_CTYPE");
        }
      }

      if (flags.useOccurrencePatterns) {
        featuresC.addAll(occurrencePatterns(cInfo, loc));
      }

      if (flags.useDisjunctive) {
        for (int i = 1; i <= flags.disjunctionWidth; i++) {
          CoreLabel dn = cInfo.get(loc + i);
          CoreLabel dp = cInfo.get(loc - i);
          featuresC.add(getWord(dn) + "-DISJN");
          if (flags.useDisjunctiveShapeInteraction) {
            featuresC.add(getWord(dn) + '-' + cShape + "-DISJN-CS");
          }
          featuresC.add(getWord(dp) + "-DISJP");
          if (flags.useDisjunctiveShapeInteraction) {
            featuresC.add(getWord(dp) + '-' + cShape + "-DISJP-CS");
          }
        }
      }

      if (flags.useWideDisjunctive) {
        for (int i = 1; i <= flags.wideDisjunctionWidth; i++) {
          featuresC.add(getWord(cInfo.get(loc + i)) + "-DISJWN");
          featuresC.add(getWord(cInfo.get(loc - i)) + "-DISJWP");
        }
      }

      if (flags.useEitherSideDisjunctive) {
        for (int i = 1; i <= flags.disjunctionWidth; i++) {
          featuresC.add(getWord(cInfo.get(loc + i)) + "-DISJWE");
          featuresC.add(getWord(cInfo.get(loc - i)) + "-DISJWE");
        }
      }

      if (flags.useDisjShape) {
        for (int i = 1; i <= flags.disjunctionWidth; i++) {
          featuresC.add(cInfo.get(loc + i).get(CoreAnnotations.ShapeAnnotation.class) + "-NDISJSHAPE");
          // featuresC.add(cInfo.get(loc - i).get(CoreAnnotations.ShapeAnnotation.class) + "-PDISJSHAPE");
          featuresC.add(cShape + '-' + cInfo.get(loc + i).get(CoreAnnotations.ShapeAnnotation.class) + "-CNDISJSHAPE");
          // featuresC.add(c.get(CoreAnnotations.ShapeAnnotation.class) + "-" + cInfo.get(loc - i).get(CoreAnnotations.ShapeAnnotation.class) + "-CPDISJSHAPE");
        }
      }

      if (flags.useExtraTaggySequences) {
        if (flags.useTags) {
          featuresC.add(p2.getString(CoreAnnotations.PartOfSpeechAnnotation.class) + '-' + p.getString(CoreAnnotations.PartOfSpeechAnnotation.class) + '-' + c.getString(CoreAnnotations.PartOfSpeechAnnotation.class) + "-TTS");
          featuresC.add(p3.getString(CoreAnnotations.PartOfSpeechAnnotation.class) + '-' + p2.getString(CoreAnnotations.PartOfSpeechAnnotation.class) + '-' + p.getString(CoreAnnotations.PartOfSpeechAnnotation.class) + '-' + c.getString(CoreAnnotations.PartOfSpeechAnnotation.class) + "-TTTS");
        }
        if (flags.useDistSim) {
          featuresC.add(p2.get(CoreAnnotations.DistSimAnnotation.class) + '-' + p.get(CoreAnnotations.DistSimAnnotation.class) + '-' + c.get(CoreAnnotations.DistSimAnnotation.class) + "-DISTSIM_TTS1");
          featuresC.add(p3.get(CoreAnnotations.DistSimAnnotation.class) + '-' + p2.get(CoreAnnotations.DistSimAnnotation.class) + '-' + p.get(CoreAnnotations.DistSimAnnotation.class) + '-' + c.get(CoreAnnotations.DistSimAnnotation.class) + "-DISTSIM_TTTS1");
        }
      }

      if (flags.useMUCFeatures) {
        featuresC.add(c.get(CoreAnnotations.SectionAnnotation.class)+"-SECTION");
        featuresC.add(c.get(CoreAnnotations.WordPositionAnnotation.class)+"-WORD_POSITION");
        featuresC.add(c.get(CoreAnnotations.SentencePositionAnnotation.class)+"-SENT_POSITION");
        featuresC.add(c.get(CoreAnnotations.ParaPositionAnnotation.class)+"-PARA_POSITION");
        featuresC.add(c.get(CoreAnnotations.WordPositionAnnotation.class)+ '-' +c.get(CoreAnnotations.ShapeAnnotation.class)+"-WORD_POSITION_SHAPE");
      }
    } else if (flags.useInternal) {

      if (flags.useWord) {
        featuresC.add(cWord + "-WORD");
      }

      if (flags.useNGrams) {
        Collection<String> subs = wordToSubstrings.get(cWord);
        if (subs == null) {
          subs = new ArrayList<String>();
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
        featuresC.addAll(subs);
        if (flags.conjoinShapeNGrams) {
          String shape = c.get(CoreAnnotations.ShapeAnnotation.class);
          for (String str : subs) {
            String feat = str + '-' + shape + "-CNGram-CS";
            featuresC.add(feat);
          }
        }
      }

      if ((flags.wordShape > WordShapeClassifier.NOWORDSHAPE) || (flags.useShapeStrings)) {
        featuresC.add(cShape + "-TYPE");
      }

      if (flags.useOccurrencePatterns) {
        featuresC.addAll(occurrencePatterns(cInfo, loc));
      }

    } else if (flags.useExternal) {

      if (flags.usePrev) {
        featuresC.add(pWord + "-PW");
      }

      if (flags.useNext) {
        featuresC.add(nWord + "-NW");
      }

      if (flags.useWordPairs) {
        featuresC.add(cWord + '-' + pWord + "-W-PW");
        featuresC.add(cWord + '-' + nWord + "-W-NW");
      }

      if (flags.useSymWordPairs) {
        featuresC.add(pWord + '-' + nWord + "-SWORDS");
      }

      if ((flags.wordShape > WordShapeClassifier.NOWORDSHAPE) || (flags.useShapeStrings)) {
        if (flags.useTypeSeqs) {
          featuresC.add(pShape + "-PTYPE");
          featuresC.add(nShape + "-NTYPE");
          featuresC.add(pWord + "..." + cShape + "-PW_CTYPE");
          featuresC.add(cShape + "..." + nWord + "-NW_CTYPE");
          if (flags.maxLeft > 0) featuresC.add(pShape + "..." + cShape + "-PCTYPE"); // this one just isn't useful, at least given c,pc,s,ps.  Might be useful 0th-order
          featuresC.add(cShape + "..." + nShape + "-CNTYPE");
          featuresC.add(pShape + "..." + cShape + "..." + nShape + "-PCNTYPE");
        }
      }

      if (flags.useLastRealWord) {
        if (pWord.length() <= 3) {
          featuresC.add(getWord(p2) + "..." + cShape + "-PPW_CTYPE");
        }
      }

      if (flags.useNextRealWord) {
        if (nWord.length() <= 3) {
          featuresC.add(getWord(n2) + "..." + cShape + "-NNW_CTYPE");
        }
      }

      if (flags.useDisjunctive) {
        for (int i = 1; i <= flags.disjunctionWidth; i++) {
          CoreLabel dn = cInfo.get(loc + i);
          CoreLabel dp = cInfo.get(loc - i);
          featuresC.add(getWord(dn) + "-DISJN");
          if (flags.useDisjunctiveShapeInteraction) {
            featuresC.add(getWord(dn) + '-' + cShape + "-DISJN-CS");
          }
          featuresC.add(getWord(dp) + "-DISJP");
          if (flags.useDisjunctiveShapeInteraction) {
            featuresC.add(getWord(dp) + '-' + cShape + "-DISJP-CS");
          }
        }
      }

      if (flags.useWideDisjunctive) {
        for (int i = 1; i <= flags.wideDisjunctionWidth; i++) {
          featuresC.add(getWord(cInfo.get(loc + i)) + "-DISJWN");
          featuresC.add(getWord(cInfo.get(loc - i)) + "-DISJWP");
        }
      }

      if (flags.useDisjShape) {
        for (int i = 1; i <= flags.disjunctionWidth; i++) {
          featuresC.add(cInfo.get(loc + i).get(CoreAnnotations.ShapeAnnotation.class) + "-NDISJSHAPE");
          // featuresC.add(cInfo.get(loc - i).get(CoreAnnotations.ShapeAnnotation.class) + "-PDISJSHAPE");
          featuresC.add(c.get(CoreAnnotations.ShapeAnnotation.class) + '-' + cInfo.get(loc + i).get(CoreAnnotations.ShapeAnnotation.class) + "-CNDISJSHAPE");
          // featuresC.add(c.get(CoreAnnotations.ShapeAnnotation.class) + "-" + cInfo.get(loc - i).get(CoreAnnotations.ShapeAnnotation.class) + "-CPDISJSHAPE");
        }
      }

    }

    // Stuff to add binary features from the additional columns
    if (flags.twoStage) {
      featuresC.add(c.get(Bin1Annotation.class) + "-BIN1");
      featuresC.add(c.get(Bin2Annotation.class) + "-BIN2");
      featuresC.add(c.get(Bin3Annotation.class) + "-BIN3");
      featuresC.add(c.get(Bin4Annotation.class) + "-BIN4");
      featuresC.add(c.get(Bin5Annotation.class) + "-BIN5");
      featuresC.add(c.get(Bin6Annotation.class) + "-BIN6");
    }

    if(flags.useIfInteger){
      try {
        int val = Integer.parseInt(cWord);
        if(val > 0) featuresC.add("POSITIVE_INTEGER");
        else if(val < 0) featuresC.add("NEGATIVE_INTEGER");
        // System.err.println("FOUND INTEGER");
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
      for (Class key : genericAnnotationKeys) {
        //System.err.println("Adding feature: " + CoreLabel.genericValues.get(key) + " with value " + c.get(key));
        if (c.get(key) != null && c.get(key) instanceof Collection) {
          for (Object ob: (Collection)c.get(key)) {
            featuresC.add(ob + "-" + CoreLabel.genericValues.get(key));
          }
        } else {
          featuresC.add(c.get(key) + "-" + CoreLabel.genericValues.get(key));
        }
      }
    }

    if(flags.useTopics){
      //featuresC.add(p.get(CoreAnnotations.TopicAnnotation.class) + '-' + cWord + "--CWORD");
      featuresC.add(c.get(CoreAnnotations.TopicAnnotation.class)+ "-TopicID");
      featuresC.add(p.get(CoreAnnotations.TopicAnnotation.class) + "-PTopicID");
      featuresC.add(n.get(CoreAnnotations.TopicAnnotation.class) + "-NTopicID");
      //featuresC.add(p.get(CoreAnnotations.TopicAnnotation.class) + '-' + c.get(CoreAnnotations.TopicAnnotation.class) + '-' + n.get(CoreAnnotations.TopicAnnotation.class) + "-PCNTopicID");
      //featuresC.add(c.get(CoreAnnotations.TopicAnnotation.class) + '-' + n.get(CoreAnnotations.TopicAnnotation.class) + "-CNTopicID");
      //featuresC.add(p.get(CoreAnnotations.TopicAnnotation.class) + '-' + c.get(CoreAnnotations.TopicAnnotation.class) + "-PCTopicID");
      //featuresC.add(c.get(CoreAnnotations.TopicAnnotation.class) + cShape + "-TopicID-SH");
      //asdasd
    }

    // NER tag annotations from a previous NER system
    if (c.get(CoreAnnotations.StackedNamedEntityTagAnnotation.class) != null) {
      featuresC.add(c.get(CoreAnnotations.StackedNamedEntityTagAnnotation.class)+ "-CStackedNERTag");
      featuresC.add(cWord + "-" + c.get(CoreAnnotations.StackedNamedEntityTagAnnotation.class)+ "-WCStackedNERTag");

      if (flags.useNext) {
        featuresC.add(c.get(CoreAnnotations.StackedNamedEntityTagAnnotation.class) + '-' + n.get(CoreAnnotations.StackedNamedEntityTagAnnotation.class) + "-CNStackedNERTag");
        featuresC.add(cWord + "-" + c.get(CoreAnnotations.StackedNamedEntityTagAnnotation.class) + '-' + n.get(CoreAnnotations.StackedNamedEntityTagAnnotation.class) + "-WCNStackedNERTag");

        if (flags.usePrev) {
          featuresC.add(p.get(CoreAnnotations.StackedNamedEntityTagAnnotation.class) + '-' + c.get(CoreAnnotations.StackedNamedEntityTagAnnotation.class) + '-' + n.get(CoreAnnotations.StackedNamedEntityTagAnnotation.class) + "-PCNStackedNERTag");
          featuresC.add(p.get(CoreAnnotations.StackedNamedEntityTagAnnotation.class) + '-' + cWord + " -" + c.get(CoreAnnotations.StackedNamedEntityTagAnnotation.class)
              + '-' + n.get(CoreAnnotations.StackedNamedEntityTagAnnotation.class) + "-PWCNStackedNERTag");
        }
      }
      if (flags.usePrev) {
        featuresC.add(p.get(CoreAnnotations.StackedNamedEntityTagAnnotation.class) + '-' + c.get(CoreAnnotations.StackedNamedEntityTagAnnotation.class) + "-PCStackedNERTag");
      }
    }
    if(flags.useWordnetFeatures)
      featuresC.add(c.get(CoreAnnotations.WordnetSynAnnotation.class)+"-WordnetSyn");
    if(flags.useProtoFeatures)
      featuresC.add(c.get(CoreAnnotations.ProtoAnnotation.class)+"-Proto");
    if(flags.usePhraseWordTags)
      featuresC.add(c.get(CoreAnnotations.PhraseWordsTagAnnotation.class)+"-PhraseTag");
    if(flags.usePhraseWords)
    {
      for(String w: c.get(CoreAnnotations.PhraseWordsAnnotation.class))
      featuresC.add(w+"-PhraseWord");
    }
    if(flags.useCommonWordsFeature)
      featuresC.add(c.get(CoreAnnotations.CommonWordsAnnotation.class));

    if (flags.useRadical && cWord.length() > 0) {
      if (cWord.length() == 1) {
        featuresC.add(RadicalMap.getRadical(cWord.charAt(0)) +
                      "-SINGLE-CHAR-RADICAL");
      } else {
        featuresC.add(RadicalMap.getRadical(cWord.charAt(0)) +
                      "-START-RADICAL");
        featuresC.add(RadicalMap.getRadical(cWord.charAt(cWord.length() - 1)) +
                      "-END-RADICAL");
      }
      for (int i = 0; i < cWord.length(); ++i) {
        featuresC.add(RadicalMap.getRadical(cWord.charAt(i)) +
                      "-RADICAL");
      }
    }

    if(flags.splitWordRegex != null && !flags.splitWordRegex.isEmpty()){
      String[] ws = c.word().split(flags.splitWordRegex);
      for(String s: ws){
       featuresC.add(s+"-SPLITWORD");
      }
    }
    return featuresC;
  } // end featuresC()

  /**
   * Binary feature annotations
   */
  private static class Bin1Annotation implements CoreAnnotation<String> {
    public Class<String> getType() {  return String.class; } }

  private static class Bin2Annotation implements CoreAnnotation<String> {
    public Class<String> getType() {  return String.class; } }

  private static class Bin3Annotation implements CoreAnnotation<String> {
    public Class<String> getType() {  return String.class; } }

  private static class Bin4Annotation implements CoreAnnotation<String> {
    public Class<String> getType() {  return String.class; } }

  private static class Bin5Annotation implements CoreAnnotation<String> {
    public Class<String> getType() {  return String.class; } }

  private static class Bin6Annotation implements CoreAnnotation<String> {
    public Class<String> getType() {  return String.class; } }



  protected Collection<String> featuresCpC(PaddedList<IN> cInfo, int loc) {
    CoreLabel p = cInfo.get(loc - 1);
    CoreLabel c = cInfo.get(loc);
    CoreLabel n = cInfo.get(loc + 1);

    String cWord = getWord(c);
    String pWord = getWord(p);
    String cDS = c.getString(CoreAnnotations.DistSimAnnotation.class);
    String pDS = p.getString(CoreAnnotations.DistSimAnnotation.class);
    String cShape = c.getString(CoreAnnotations.ShapeAnnotation.class);
    String pShape = p.getString(CoreAnnotations.ShapeAnnotation.class);
    Collection<String> featuresCpC = new ArrayList<String>();

    if (flags.noEdgeFeature)
      return featuresCpC;

    if (flags.transitionEdgeOnly) {
      featuresCpC.add("PSEQ");
      return featuresCpC;
    }

    if (flags.useNeighborNGrams) {
      int maxLen = pWord.length();
      if (flags.maxNGramLeng >= 0 && flags.maxNGramLeng < maxLen) {
        maxLen = flags.maxNGramLeng;
      }
      for (int len = 1; len <= maxLen; ++len) {
        featuresCpC.add(pWord.substring(0, len) + "-PREVIOUS-PREFIX");
      }
      for (int pos = pWord.length() - maxLen; pos < pWord.length(); ++pos) {
        featuresCpC.add(pWord.substring(pos, pWord.length()) +
                        "-PREVIOUS-SUFFIX");
      }

      maxLen = cWord.length();
      if (flags.maxNGramLeng >= 0 && flags.maxNGramLeng < maxLen) {
        maxLen = flags.maxNGramLeng;
      }
      for (int len = 1; len <= maxLen; ++len) {
        featuresCpC.add(cWord.substring(0, len) + "-CURRENT-PREFIX");
      }
      for (int pos = cWord.length() - maxLen; pos < cWord.length(); ++pos) {
        featuresCpC.add(cWord.substring(pos, cWord.length()) +
                        "-CURRENT-SUFFIX");
      }
    }

    if (flags.useInternal && flags.useExternal ) {

      if (flags.useOrdinal) {
        if (isOrdinal(cInfo, loc)) {
          featuresCpC.add("C_ORDINAL");
          if (isOrdinal(cInfo, loc-1)) {
            featuresCpC.add("PC_ORDINAL");
          }
        }
        if (isOrdinal(cInfo, loc-1)) {
          featuresCpC.add("P_ORDINAL");
        }
      }

      if (flags.useAbbr || flags.useMinimalAbbr) {
        featuresCpC.add(p.get(CoreAnnotations.AbbrAnnotation.class) + '-' + c.get(CoreAnnotations.AbbrAnnotation.class) + "-PABBRANS");
      }

      if (flags.useAbbr1 || flags.useMinimalAbbr1) {
        if (!c.get(CoreAnnotations.AbbrAnnotation.class).equals("XX")) {
          featuresCpC.add(p.get(CoreAnnotations.AbbrAnnotation.class) + '-' + c.get(CoreAnnotations.AbbrAnnotation.class) + "-PABBRANS");
        }
      }

      if (flags.useChunkySequences) {
        featuresCpC.add(p.get(CoreAnnotations.ChunkAnnotation.class) + '-' + c.get(CoreAnnotations.ChunkAnnotation.class) + '-' + n.get(CoreAnnotations.ChunkAnnotation.class) + "-PCNCHUNK");
      }

      if (flags.usePrev) {
        if (flags.useSequences && flags.usePrevSequences) {
          featuresCpC.add("PSEQ");
          featuresCpC.add(cWord + "-PSEQW");
          featuresCpC.add(pWord+ '-' +cWord + "-PSEQW2");

          featuresCpC.add(pWord + "-PSEQpW");

          featuresCpC.add(pDS + "-PSEQpDS");
          featuresCpC.add(cDS + "-PSEQcDS");
          featuresCpC.add(pDS+ '-' +cDS + "-PSEQpcDS");

          if (((flags.wordShape > WordShapeClassifier.NOWORDSHAPE) || flags.useShapeStrings)) {
            featuresCpC.add(pShape + "-PSEQpS");
            featuresCpC.add(cShape + "-PSEQcS");
            featuresCpC.add(pShape+ '-' +cShape + "-PSEQpcS");
          }
        }
      }

      if (((flags.wordShape > WordShapeClassifier.NOWORDSHAPE) ||
           flags.useShapeStrings)
          && flags.useTypeSeqs && (flags.useTypeSeqs2 || flags.useTypeSeqs3)) {
        if (flags.useTypeSeqs3) {
          featuresCpC.add(pShape + '-' + cShape + '-' + n.get(CoreAnnotations.ShapeAnnotation.class) + "-PCNSHAPES");
        }
        if (flags.useTypeSeqs2) {
          featuresCpC.add(pShape + '-' + cShape + "-TYPES");
        }

        if (flags.useYetMoreCpCShapes) {
          String p2Shape = cInfo.get(loc - 2).getString(CoreAnnotations.ShapeAnnotation.class);
          featuresCpC.add(p2Shape + '-' + pShape + '-' + cShape + "-YMS");
          featuresCpC.add(pShape + '-' + cShape + "-" + n.getString(CoreAnnotations.ShapeAnnotation.class) + "-YMSPCN");
        }
      }

      if (flags.useTypeySequences) {
        featuresCpC.add(cShape + "-TPS2");
        featuresCpC.add(n.get(CoreAnnotations.ShapeAnnotation.class) + "-TNS1");
        // featuresCpC.add(pShape) + "-" + cShape) + "-TPS"); // duplicates -TYPES, so now omitted; you may need to slighly increase sigma to duplicate previous results, however.
      }

      if (flags.useTaggySequences) {
        if (flags.useTags) {
          featuresCpC.add(p.getString(CoreAnnotations.PartOfSpeechAnnotation.class) + '-' + c.getString(CoreAnnotations.PartOfSpeechAnnotation.class) + "-TS");
        }
        if (flags.useDistSim) {
          featuresCpC.add(p.get(CoreAnnotations.DistSimAnnotation.class) + '-' + c.get(CoreAnnotations.DistSimAnnotation.class) + "-DISTSIM_TS1");
        }
      }

      if (flags.useParenMatching) {
        if (flags.useReverse) {
          if (cWord.equals("(") || cWord.equals("[") || cWord.equals("-LRB-")) {
            if (pWord.equals(")") || pWord.equals("]") || pWord.equals("-RRB-")) {
              featuresCpC.add("PAREN-MATCH");
            }
          }
        } else {
          if (cWord.equals(")") || cWord.equals("]") || cWord.equals("-RRB-")) {
            if (pWord.equals("(") || pWord.equals("[") || pWord.equals("-LRB-")) {
              featuresCpC.add("PAREN-MATCH");
            }
          }
        }
      }
      if (flags.useEntityTypeSequences) {
        featuresCpC.add(p.get(CoreAnnotations.EntityTypeAnnotation.class) + '-' + c.get(CoreAnnotations.EntityTypeAnnotation.class) + "-ETSEQ");
      }
      if (flags.useURLSequences) {
        featuresCpC.add(p.get(CoreAnnotations.IsURLAnnotation.class) + '-' + c.get(CoreAnnotations.IsURLAnnotation.class) + "-URLSEQ");
      }
    } else if (flags.useInternal) {

      if (flags.useSequences && flags.usePrevSequences) {
        featuresCpC.add("PSEQ");
        featuresCpC.add(cWord + "-PSEQW");
      }

      if (flags.useTypeySequences) {
        featuresCpC.add(cShape + "-TPS2");
      }

    } else if (flags.useExternal) {

      if( ((flags.wordShape > WordShapeClassifier.NOWORDSHAPE) ||
           flags.useShapeStrings)
          && flags.useTypeSeqs && (flags.useTypeSeqs2 || flags.useTypeSeqs3)) {
        if (flags.useTypeSeqs3) {
          featuresCpC.add(pShape + '-' + cShape + '-' + n.get(CoreAnnotations.ShapeAnnotation.class) + "-PCNSHAPES");
        }
        if (flags.useTypeSeqs2) {
          featuresCpC.add(pShape + '-' + cShape + "-TYPES");
        }
      }

      if (flags.useTypeySequences) {
        featuresCpC.add(n.get(CoreAnnotations.ShapeAnnotation.class) + "-TNS1");
        featuresCpC.add(pShape + '-' + c.get(CoreAnnotations.ShapeAnnotation.class) + "-TPS");
      }
    }

    return featuresCpC;
  }

  protected Collection<String> featuresCp2C(PaddedList<IN> cInfo, int loc) {
    CoreLabel c = cInfo.get(loc);
    CoreLabel p = cInfo.get(loc - 1);
    CoreLabel p2 = cInfo.get(loc - 2);

    String cWord = getWord(c);
    String pWord = getWord(p);
    String p2Word = getWord(p2);
    Collection<String> featuresCp2C = new ArrayList<String>();

    if (flags.useMoreAbbr) {
      featuresCp2C.add(p2.get(CoreAnnotations.AbbrAnnotation.class) + '-' + c.get(CoreAnnotations.AbbrAnnotation.class) + "-P2ABBRANS");
    }

    if (flags.useMinimalAbbr) {
      featuresCp2C.add(p2.get(CoreAnnotations.AbbrAnnotation.class) + '-' + c.get(CoreAnnotations.AbbrAnnotation.class) + "-P2AP2CABB");
    }

    if (flags.useMinimalAbbr1) {
      if (!c.get(CoreAnnotations.AbbrAnnotation.class).equals("XX")) {
        featuresCp2C.add(p2.get(CoreAnnotations.AbbrAnnotation.class) + '-' + c.get(CoreAnnotations.AbbrAnnotation.class) + "-P2AP2CABB");
      }
    }

    if (flags.useParenMatching) {
      if (flags.useReverse) {
        if (cWord.equals("(") || cWord.equals("[") || cWord.equals("-LRB-")) {
          if ((p2Word.equals(")") || p2Word.equals("]") || p2Word.equals("-RRB-")) && ! (pWord.equals(")") || pWord.equals("]") || pWord.equals("-RRB-"))) {
            featuresCp2C.add("PAREN-MATCH");
          }
        }
      } else {
        if (cWord.equals(")") || cWord.equals("]") || cWord.equals("-RRB-")) {
          if ((p2Word.equals("(") || p2Word.equals("[") || p2Word.equals("-LRB-")) && ! (pWord.equals("(") || pWord.equals("[") || pWord.equals("-LRB-"))) {
            featuresCp2C.add("PAREN-MATCH");
          }
        }
      }
    }

    return featuresCp2C;
  }

  protected Collection<String> featuresCp3C(PaddedList<IN> cInfo, int loc) {
    CoreLabel c = cInfo.get(loc);
    CoreLabel p = cInfo.get(loc - 1);
    CoreLabel p2 = cInfo.get(loc - 2);
    CoreLabel p3 = cInfo.get(loc - 3);

    String cWord = getWord(c);
    String pWord = getWord(p);
    String p2Word = getWord(p2);
    String p3Word = getWord(p3);
    Collection<String> featuresCp3C = new ArrayList<String>();

    if (flags.useParenMatching) {
      if (flags.useReverse) {
        if (cWord.equals("(") || cWord.equals("[")) {
          if ((flags.maxLeft >= 3) && (p3Word.equals(")") || p3Word.equals("]")) && !(p2Word.equals(")") || p2Word.equals("]") || pWord.equals(")") || pWord.equals("]"))) {
            featuresCp3C.add("PAREN-MATCH");
          }
        }
      } else {
        if (cWord.equals(")") || cWord.equals("]")) {
          if ((flags.maxLeft >= 3) && (p3Word.equals("(") || p3Word.equals("[")) && !(p2Word.equals("(") || p2Word.equals("[") || pWord.equals("(") || pWord.equals("["))) {
            featuresCp3C.add("PAREN-MATCH");
          }
        }
      }
    }

    return featuresCp3C;
  }

  protected Collection<String> featuresCp4C(PaddedList<IN> cInfo, int loc) {
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

    Collection<String> featuresCp4C = new ArrayList<String>();

    if (flags.useParenMatching) {
      if (flags.useReverse) {
        if (cWord.equals("(") || cWord.equals("[")) {
          if ((flags.maxLeft >= 4) && (p4Word.equals(")") || p4Word.equals("]")) && !(p3Word.equals(")") || p3Word.equals("]") || p2Word.equals(")") || p2Word.equals("]") || pWord.equals(")") || pWord.equals("]"))) {
            featuresCp4C.add("PAREN-MATCH");
          }
        }
      } else {
        if (cWord.equals(")") || cWord.equals("]")) {
          if ((flags.maxLeft >= 4) && (p4Word.equals("(") || p4Word.equals("[")) && !(p3Word.equals("(") || p3Word.equals("[") || p2Word.equals("(") || p2Word.equals("[") || pWord.equals("(") || pWord.equals("["))) {
            featuresCp4C.add("PAREN-MATCH");
          }
        }
      }
    }

    return featuresCp4C;
  }

  protected Collection<String> featuresCp5C(PaddedList<IN> cInfo, int loc) {
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
    Collection<String> featuresCp5C = new ArrayList<String>();

    if (flags.useParenMatching) {
      if (flags.useReverse) {
        if (cWord.equals("(") || cWord.equals("[")) {
          if ((flags.maxLeft >= 5) && (p5Word.equals(")") || p5Word.equals("]")) && !(p4Word.equals(")") || p4Word.equals("]") || p3Word.equals(")") || p3Word.equals("]") || p2Word.equals(")") || p2Word.equals("]") || pWord.equals(")") || pWord.equals("]"))) {
            featuresCp5C.add("PAREN-MATCH");
          }
        }
      } else {
        if (cWord.equals(")") || cWord.equals("]")) {
          if ((flags.maxLeft >= 5) && (p5Word.equals("(") || p5Word.equals("[")) && !(p4Word.equals("(") || p4Word.equals("[") || p3Word.equals("(") || p3Word.equals("[") || p2Word.equals("(") || p2Word.equals("[") || pWord.equals("(") || pWord.equals("["))) {
            featuresCp5C.add("PAREN-MATCH");
          }
        }
      }
    }
    return featuresCp5C;
  }


  protected Collection<String> featuresCpCp2C(PaddedList<IN> cInfo, int loc) {
    CoreLabel c = cInfo.get(loc);
    CoreLabel p = cInfo.get(loc - 1);
    CoreLabel p2 = cInfo.get(loc - 2);

    String pWord = getWord(p);
    // String p2Word = getWord(p2);

    Collection<String> featuresCpCp2C = new ArrayList<String>();

    if (flags.useInternal && flags.useExternal) {

      if (false && flags.useTypeySequences && flags.maxLeft >= 2) {  // this feature duplicates -TYPETYPES one below, so don't include it (hurts to duplicate)!!!
        featuresCpCp2C.add(p2.get(CoreAnnotations.ShapeAnnotation.class) + '-' + p.get(CoreAnnotations.ShapeAnnotation.class) + '-' + c.get(CoreAnnotations.ShapeAnnotation.class) + "-TTPS");
      }

      if (flags.useAbbr) {
        featuresCpCp2C.add(p2.get(CoreAnnotations.AbbrAnnotation.class) + '-' + p.get(CoreAnnotations.AbbrAnnotation.class) + '-' + c.get(CoreAnnotations.AbbrAnnotation.class) + "-2PABBRANS");
      }

      if (flags.useChunks) {
        featuresCpCp2C.add(p2.get(CoreAnnotations.ChunkAnnotation.class) + '-' + p.get(CoreAnnotations.ChunkAnnotation.class) + '-' + c.get(CoreAnnotations.ChunkAnnotation.class) + "-2PCHUNKS");
      }

      if (flags.useLongSequences) {
        featuresCpCp2C.add("PPSEQ");
      }
      if (flags.useBoundarySequences && pWord.equals(CoNLLDocumentReaderAndWriter.BOUNDARY)) {
        featuresCpCp2C.add("BNDRY-SPAN-PPSEQ");
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
          featuresCpCp2C.add(p2.getString(CoreAnnotations.PartOfSpeechAnnotation.class) + '-' + p.getString(CoreAnnotations.PartOfSpeechAnnotation.class) + '-' + c.getString(CoreAnnotations.PartOfSpeechAnnotation.class) + "-TTS");
          if (flags.useTaggySequencesShapeInteraction) {
            featuresCpCp2C.add(p2.getString(CoreAnnotations.PartOfSpeechAnnotation.class) + '-' + p.getString(CoreAnnotations.PartOfSpeechAnnotation.class) + '-' + c.getString(CoreAnnotations.PartOfSpeechAnnotation.class) + '-' + c.get(CoreAnnotations.ShapeAnnotation.class) + "-TTS-CS");
          }
        }
        if (flags.useDistSim) {
          featuresCpCp2C.add(p2.get(CoreAnnotations.DistSimAnnotation.class) + '-' + p.get(CoreAnnotations.DistSimAnnotation.class) + '-' + c.get(CoreAnnotations.DistSimAnnotation.class) + "-DISTSIM_TTS1");
          if (flags.useTaggySequencesShapeInteraction) {
            featuresCpCp2C.add(p2.get(CoreAnnotations.DistSimAnnotation.class) + '-' + p.get(CoreAnnotations.DistSimAnnotation.class) + '-' + c.get(CoreAnnotations.DistSimAnnotation.class) + '-' + c.get(CoreAnnotations.ShapeAnnotation.class) + "-DISTSIM_TTS1-CS");
          }
        }
      }

      if (((flags.wordShape > WordShapeClassifier.NOWORDSHAPE) ||
           flags.useShapeStrings)
          && flags.useTypeSeqs && flags.useTypeSeqs2 && flags.maxLeft >= 2) {
        String cShape = c.get(CoreAnnotations.ShapeAnnotation.class);
        String pShape = p.get(CoreAnnotations.ShapeAnnotation.class);
        String p2Shape = p2.get(CoreAnnotations.ShapeAnnotation.class);
        featuresCpCp2C.add(p2Shape + '-' + pShape + '-' + cShape + "-TYPETYPES");
      }
    } else if (flags.useInternal) {

      if (flags.useLongSequences) {
        featuresCpCp2C.add("PPSEQ");
      }
    } else if (flags.useExternal) {

      if (flags.useLongSequences) {
        featuresCpCp2C.add("PPSEQ");
      }

      if (((flags.wordShape > WordShapeClassifier.NOWORDSHAPE) ||
           flags.useShapeStrings)
          && flags.useTypeSeqs && flags.useTypeSeqs2 && flags.maxLeft >= 2) {
        String cShape = c.get(CoreAnnotations.ShapeAnnotation.class);
        String pShape = p.get(CoreAnnotations.ShapeAnnotation.class);
        String p2Shape = p2.get(CoreAnnotations.ShapeAnnotation.class);
        featuresCpCp2C.add(p2Shape + '-' + pShape + '-' + cShape + "-TYPETYPES");
      }
    }

    return featuresCpCp2C;
  }


  protected Collection<String> featuresCpCp2Cp3C(PaddedList<IN> cInfo, int loc) {
    CoreLabel c = cInfo.get(loc);
    CoreLabel p = cInfo.get(loc - 1);
    CoreLabel p2 = cInfo.get(loc - 2);
    CoreLabel p3 = cInfo.get(loc - 3);

    Collection<String> featuresCpCp2Cp3C = new ArrayList<String>();

    if (flags.useTaggySequences) {
      if (flags.useTags) {
        if (flags.maxLeft >= 3 && !flags.dontExtendTaggy) {
          featuresCpCp2Cp3C.add(p3.getString(CoreAnnotations.PartOfSpeechAnnotation.class) + '-' + p2.getString(CoreAnnotations.PartOfSpeechAnnotation.class) + '-' + p.getString(CoreAnnotations.PartOfSpeechAnnotation.class) + '-' + c.getString(CoreAnnotations.PartOfSpeechAnnotation.class) + "-TTTS");
          if (flags.useTaggySequencesShapeInteraction) {
            featuresCpCp2Cp3C.add(p3.getString(CoreAnnotations.PartOfSpeechAnnotation.class) + '-' + p2.getString(CoreAnnotations.PartOfSpeechAnnotation.class) + '-' + p.getString(CoreAnnotations.PartOfSpeechAnnotation.class) + '-' + c.getString(CoreAnnotations.PartOfSpeechAnnotation.class) + '-' + c.get(CoreAnnotations.ShapeAnnotation.class) + "-TTTS-CS");
          }
        }
      }
      if (flags.useDistSim) {
        if (flags.maxLeft >= 3 && !flags.dontExtendTaggy) {
          featuresCpCp2Cp3C.add(p3.get(CoreAnnotations.DistSimAnnotation.class) + '-' + p2.get(CoreAnnotations.DistSimAnnotation.class) + '-' + p.get(CoreAnnotations.DistSimAnnotation.class) + '-' + c.get(CoreAnnotations.DistSimAnnotation.class) + "-DISTSIM_TTTS1");
          if (flags.useTaggySequencesShapeInteraction) {
            featuresCpCp2Cp3C.add(p3.get(CoreAnnotations.DistSimAnnotation.class) + '-' + p2.get(CoreAnnotations.DistSimAnnotation.class) + '-' + p.get(CoreAnnotations.DistSimAnnotation.class) + '-' + c.get(CoreAnnotations.DistSimAnnotation.class) + '-' + c.get(CoreAnnotations.ShapeAnnotation.class) + "-DISTSIM_TTTS1-CS");
          }
        }
      }
    }

    if (flags.maxLeft >= 3) {
      if (flags.useLongSequences) {
        featuresCpCp2Cp3C.add("PPPSEQ");
      }
      if (flags.useBoundarySequences && getWord(p).equals(CoNLLDocumentReaderAndWriter.BOUNDARY)) {
        featuresCpCp2Cp3C.add("BNDRY-SPAN-PPPSEQ");
      }
    }

    return featuresCpCp2Cp3C;
  }

  protected Collection<String> featuresCpCp2Cp3Cp4C(PaddedList<IN> cInfo, int loc) {
    Collection<String> featuresCpCp2Cp3Cp4C = new ArrayList<String>();

    CoreLabel p = cInfo.get(loc - 1);

    if (flags.maxLeft >= 4) {
      if (flags.useLongSequences) {
        featuresCpCp2Cp3Cp4C.add("PPPPSEQ");
      }
      if (flags.useBoundarySequences && getWord(p).equals(CoNLLDocumentReaderAndWriter.BOUNDARY)) {
        featuresCpCp2Cp3Cp4C.add("BNDRY-SPAN-PPPPSEQ");
      }
    }

    return featuresCpCp2Cp3Cp4C;
  }


  protected Collection<String> featuresCnC(PaddedList<IN> cInfo, int loc) {
    CoreLabel c = cInfo.get(loc);

    Collection<String> featuresCnC = new ArrayList<String>();

    if (flags.useNext) {
      if (flags.useSequences && flags.useNextSequences) {
        featuresCnC.add("NSEQ");
        featuresCnC.add(getWord(c) + "-NSEQW");
      }
    }

    return featuresCnC;
  }


  protected Collection<String> featuresCpCnC(PaddedList<IN> cInfo, int loc) {
    CoreLabel c = cInfo.get(loc);

    Collection<String> featuresCpCnC = new ArrayList<String>();

    if (flags.useNext && flags.usePrev) {
      if (flags.useSequences && flags.usePrevSequences && flags.useNextSequences) {
        featuresCpCnC.add("PNSEQ");
        featuresCpCnC.add(getWord(c) + "-PNSEQW");
      }
    }

    return featuresCpCnC;
  }


  int reverse(int i) {
    return (flags.useReverse ? -1 * i : i);
  }

  private Collection<String> occurrencePatterns(PaddedList<IN> cInfo, int loc) {
    // features on last Cap
    String word = getWord(cInfo.get(loc));
    String nWord = getWord(cInfo.get(loc + reverse(1)));
    CoreLabel p = cInfo.get(loc - reverse(1));
    String pWord = getWord(p);
    // System.err.println(word+" "+nWord);
    if (!(isNameCase(word) && noUpperCase(nWord) && hasLetter(nWord) && hasLetter(pWord) && p != cInfo.getPad())) {
      return Collections.singletonList("NO-OCCURRENCE-PATTERN");
    }
    // System.err.println("LOOKING");
    Set<String> l = Generics.newHashSet();
    if (cInfo.get(loc - reverse(1)).getString(CoreAnnotations.PartOfSpeechAnnotation.class) != null && isNameCase(pWord) && cInfo.get(loc - reverse(1)).getString(CoreAnnotations.PartOfSpeechAnnotation.class).equals("NNP")) {
      for (int jump = 3; jump < 150; jump++) {
        if (getWord(cInfo.get(loc + reverse(jump))).equals(word)) {
          if (getWord(cInfo.get(loc + reverse(jump - 1))).equals(pWord)) {
            l.add("XY-NEXT-OCCURRENCE-XY");
          } else {
            l.add("XY-NEXT-OCCURRENCE-Y");
          }
        }
      }
      for (int jump = -3; jump > -150; jump--) {
        if (getWord(cInfo.get(loc + reverse(jump))).equals(word)) {
          if (getWord(cInfo.get(loc + reverse(jump - 1))).equals(pWord)) {
            l.add("XY-PREV-OCCURRENCE-XY");
          } else {
            l.add("XY-PREV-OCCURRENCE-Y");
          }
        }
      }
    } else {
      for (int jump = 3; jump < 150; jump++) {
        if (getWord(cInfo.get(loc + reverse(jump))).equals(word)) {
          if (isNameCase(getWord(cInfo.get(loc + reverse(jump - 1)))) && (cInfo.get(loc + reverse(jump - 1))).getString(CoreAnnotations.PartOfSpeechAnnotation.class).equals("NNP")) {
            l.add("X-NEXT-OCCURRENCE-YX");
            // System.err.println(getWord(cInfo.get(loc+reverse(jump-1))));
          } else if (isNameCase(getWord(cInfo.get(loc + reverse(jump + 1)))) && (cInfo.get(loc + reverse(jump + 1))).getString(CoreAnnotations.PartOfSpeechAnnotation.class).equals("NNP")) {
            // System.err.println(getWord(cInfo.get(loc+reverse(jump+1))));
            l.add("X-NEXT-OCCURRENCE-XY");
          } else {
            l.add("X-NEXT-OCCURRENCE-X");
          }
        }
      }
      for (int jump = -3; jump > -150; jump--) {
        if (getWord(cInfo.get(loc + jump)) != null && getWord(cInfo.get(loc + jump)).equals(word)) {
          if (isNameCase(getWord(cInfo.get(loc + reverse(jump + 1)))) && (cInfo.get(loc + reverse(jump + 1))).getString(CoreAnnotations.PartOfSpeechAnnotation.class).equals("NNP")) {
            l.add("X-PREV-OCCURRENCE-YX");
            // System.err.println(getWord(cInfo.get(loc+reverse(jump+1))));
          } else if (isNameCase(getWord(cInfo.get(loc + reverse(jump - 1)))) && cInfo.get(loc + reverse(jump - 1)).getString(CoreAnnotations.PartOfSpeechAnnotation.class).equals("NNP")) {
            l.add("X-PREV-OCCURRENCE-XY");
            // System.err.println(getWord(cInfo.get(loc+reverse(jump-1))));
          } else {
            l.add("X-PREV-OCCURRENCE-X");
          }
        }
      }
    }
    /*
    if (!l.isEmpty()) {
      System.err.println(pWord+" "+word+" "+nWord+" "+l);
    }
    */
    return l;
  }

  String intern(String s) {
    if (flags.intern) {
      return s.intern();
    } else {
      return s;
    }
  }

  public void initGazette() {
    try {
      // read in gazettes
      if (flags.gazettes == null) { flags.gazettes = new ArrayList<String>(); }
      List<String> gazettes = flags.gazettes;
      for (String gazetteFile : gazettes) {
        BufferedReader r = IOUtils.readerFromString(gazetteFile, flags.inputEncoding);
        readGazette(r);
        r.close();
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

} // end class NERFeatureFactory
