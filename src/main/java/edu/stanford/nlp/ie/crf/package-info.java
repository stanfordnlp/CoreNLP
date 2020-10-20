/**
 * A package for doing inference with conditional random fields.  This
 * implements the common, standard case of a linear chain CRF of arbitrary
 * order (though usually first order in practice) where each position can
 * be labeled with one of a fixed set of classes.
 * <p>
 * Through the use of different
 * {@code edu.stanford.nlp.sequences.FeatureFactory} classes and different
 * {@code edu.stanford.nlp.sequences.DocumentReaderAndWriter} classes,
 * it can read data in various formats, and be customized for various
 * sequence inference tasks.  Most of its use has been for Named Entity
 * Recognition, but it has also been used for other applications such as
 * Chinese word segmentation and OCR.
 * <p>
 * For more usage information, consult the Javadoc of the
 * {@code CRFClassifier} class.
 *
 * @author Jenny Finkel
 */
package edu.stanford.nlp.ie.crf;