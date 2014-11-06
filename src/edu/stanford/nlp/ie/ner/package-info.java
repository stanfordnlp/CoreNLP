/**
 * <body>
 * <p>
 * This package contains the older version of Stanford NER tagger that uses a
 * Conditional Markov Model (a.k.a., Maximum Entropy Markov Model or MEMM)
 * designed for Named Entity Recognition, and various suppport code.
 * For newer version of the Stanford NER tagger that is based on Conditional
 * Random Fields (CRF) models, please see <code>edu.stanford.nlp.ie.crf.CRFClassifier</code>.
 * The original version was written for the CoNLL 2003 Shared Task competition.
 * The design was considerably generalized, and many more features added for the
 * BioCreative 2003 evaluation.  For basic NER tagging, you should use
 * <code>CMMClassifier</code>.  (Earlier versions still exist as
 * <code>NGramStringClassifier</code> and <code>BioNGramStringClassifier</code>.)
 * The rest of the code in this directory is various remnants of our
 * BioCreative system and various support utilities for NER or CMMClassifier.
 * </p>
 * </body>
 */
package edu.stanford.nlp.ie.ner;