/**
 * <p>
 * A Maximum Entropy Part-of-Speech Tagger.  It can run either a Conditional
 * Markov Model (CMM) aka Maximum Entropy Markov Model (MEMM) tagger or a
 * cyclic dependency network tagger.
 * </p>
 *
 * <p>If you are only interested in using one of the trained taggers
 * included in the distribution, either from the commandline or via the
 * Java API, look at the documentation of the class
 * {@link edu.stanford.nlp.tagger.maxent.MaxentTagger}.</p>
 *
 * <p>Or, if you are interested in training a tagger from data using
 * some of the built-in architecture options (CMM or bi-directional
 * dependency network), also look at the documentation for
 * {@link edu.stanford.nlp.tagger.maxent.MaxentTagger}.</p>
 *
 * <p>The rest of this document is for more complex situations where you
 * want to define the features/architecture of your own tagger, which
 * requires delving into the code.</p>
 *
 * <p>The pre-defined features are for CMMs and bi-directional dependency
 * networks. The local models are log-linear models using features
 * specified via feature templates.</p>
 *
 *
 * <h2>Kinds of Templates</h2>
 *
 * <p>There are two kinds of templates: ones for rare words,
 * and ones for common words. For a context centered at a common word, only common
 * word features are active. For a context centered at a rare word, both common
 * and rare word features are active. Which words are considered common and which
 * rare is determined by the parameter GlobalHolder.rareThreshold. For example,
 * a threshold of five means that words occurring five times or less are considered
 * rare.</p>
 *
 * <p>The feature templates represent conditions on the words and tags
 * surrounding the current position and also the target tag. For example
 * <code>&lt;t<sub>_-1</sub>,t_<sub>0</sub>&gt;</code> is a common word
 * feature template. It is
 * instantiated for various values of the previous and current tag. A feature
 * formed by instantiating this template will look for example like this:
 * <code>&lt;t<sub>_-1</sub>=DT,t_<sub>0</sub>=NN&gt;</code>
 * and will have value 1 for a history <i>h</i> and tag <i>t</i>
 * iff the condition is satisfied. Every feature
 * template includes a specification of the current tag. It is not possible at the
 * moment to include features that are true if the current tag is one of several
 * possible, e.g. NN or NNS or NNP.</p>
 *
 * <p>To reduce the number
 * of features, cutoffs on the number of times a feature is active are introduced.
 * The cutoff for common word features is GlobalHolder.threshold, and
 * the cutoff for rare word features is GlobalHolder.thresholdRare.
 * The thresholds work like this: the part of the feature that does not include
 * the tag has to be active in the training set at least cutoff+1 times, and the
 * complete feature has to be active at least once in order for the feature to be
 *  included in the model. (Note, the cutoff for the current word feature is set to
 * 2 independent of threshold settings; cf. method
 * {@link edu.stanford.nlp.tagger.maxent.TaggerExperiments#populated(int, int)}.
  * </p>
 *
 * <h2>Training a Tagger</h2>
 *
 * <p >In order to train a tagger, we need to specify the feature
 * templates to be used, change the count cutoffs if we want, change the default
 * parameter estimation method if we want, perhaps hand-specify closed
 * class POS tags, and then train given tagged text.</p>
 *
 * <h3>Specifying Feature Templates</h3>
 *
 * <p >Feature templates inherit from the class {@link edu.stanford.nlp.tagger.maxent.Extractor}.
 * The main job of an Extractor is to extract the value it is interested in from a history.
 * Each instantiating feature for a given template will be true for a specific
 * value extracted from a history and a specific target tag.</p>
 *
 * <p>For example, this is a common word extractor that extracts
 * the current and next word. </p>
 *
 * <pre>
 * /**
 * * This extractor extracts the current and the next word in conjunction.
 * *\/
 * class ExtractorCWordNextWord extends Extractor {
 *
 * private final static String excl=&quot;!&quot;;
 *
 * public ExtractorCWordNextWord() {}
 *
 *  String extract(History h, PairsHolder pH) {
 * String s = pH.get(h, 0, false) + excl + pH.get(h, 1, false);
 * return s;
 * }
 *
 * }
 * </pre>
 *
 * <p>The method extract(History h) is defined in the base class
 * Extractor as:</p>
 * *
 * <pre>
 * String extract(History h) {
 * return extract(h, GlobalHolder.pairs);
 * }
 * </pre>
 *
 * <p>The PairsHolder contains an array of words and the tags. It
 * has a get method that can be used to extract things from the history. In
 * GlobalHolder.pairs , the whole training data is stored. String
 * GlobalHolder.pairs.get(History
 * <i>h</i>,int <i>position</i>, boolean <i>isTag</i>) , will return the tag or
 * word (depending on <i>isTag</i>), at position <i>position </i>relative to the
 * history <i>h</i>.</p>
 *
 * <p>Using this PairsHolder, we can extract features from the
 * whole sentence including the current word. The History object is basically a
 * specification of the start of the sentence, the current word, and the end of
 *  the sentence. </p>
 *
 * <p>In an extractor we can also specify for which tags to
 * instantiate the template. The method
 * {@code boolean precondition(String tag)} is by default true, meaning
 * that a feature can be created for every tag. Sometimes we would like to
 * restrict that, and say that features should be created for only the VB and VBP
 * tags, for example. In this case the method precondition has to be redefined to
 * return false for all other tags.</p>
 *
 * <p>The extractors for common word features have to be placed in
 * the static array ExtractorFrames.eFrames. The present state of this array for
 * the best tagger is:</p>
 * <blockquote><pre>
 * public static Extractor[] eFrames={cWord,prevWord,nextWord,prevTag,nextTag,
 * prevTwoTags,nextTwoTags,prevNextTag,prevTagWord,nextTagWord,cWordPrevWord,
 * cWordNextWord};
 * </pre></blockquote>
 *
 * <p>The extractors for rare word features commonly inherit from
 * RareExtractor, which inherits from Extractor. RareExtractor provides
 * some nice static methods for manipulating
 * strings, such as seeing whether they contain numbers, etc. The rare word
 * extractors have to be placed in the static array ExtractorFramesRare.eFrames.
 * For example, for a good English tagger, this array might be:</p>
 *
 * <blockquote><pre>
 * public static Extractor[] eFrames={cWordUppCase,cWordNumber,
 * cWordDash,cWordSuff1,cWordSuff2,cWordSuff3,cWordSuff4,
 * cAllCap,cMidSentence,cWordStartUCase,cWordMidUCase,
 * cWordPref1,cWordPref2,cWordPref3,cWordPref4,
 * new ExtractorCWordPref(5),new ExtractorCWordPref(6),
 * new ExtractorCWordPref(7), new ExtractorCWordPref(8),
 * new ExtractorCWordPref(9), new ExtractorCWordPref(10),
 * new ExtractorCWordSuff(5),new ExtractorCWordSuff(6),
 * new ExtractorCWordSuff(7),new ExtractorCWordSuff(8),
 * new ExtractorCWordSuff(9), new ExtractorCWordSuff(10),
 * cLetterDigitDash, cCompany,cAllCapitalized,cUpperDigitDash};
 * </pre></blockquote>
 *
 * <p>At present, many of the extractor and rare extractor combinations can
 * be flexibly set from a properties file by suitable specifications of the
 * {@code arch} option, whereas others require changing the code.</p>
 *
 * <h3>Specifying closed-class POS tags</h3>
 *
 * <p>
 * By default, all POS tags are assumed to be open classes.  In many cases,
 * it is useful to specify POS tags which are closed class, and can only be
 * applied to words seen in the training data (rather than being possible
 * tags for new words seen at runtime).  These closed class tags are
 * specified for a language (where a "language" is really a
 * (language,tag-set) pair: a different system of tagging is a new
 * language).  You do this by specifying the language in the properties
 * file, and specifying the closed class tags for that language in
 * {@link edu.stanford.nlp.tagger.maxent.TTags}.</p>
 */
package edu.stanford.nlp.tagger.maxent;
