/**
 * A package for doing Chinese word segmentation.
 * <p>
 * This package makes use of the CRFClassifier class (a conditional random
 * field sequence classifier) to do Chinese word segmentation.
 * </p>
 * <p>
 * On the Stanford NLP machines, usable properties files can be found at:
 * <code> /u/nlp/data/chinese-segmenter/Sighan2005/prop </code>
 * </p>
 * <p>
 * Usage: For simplified Chinese:
 * </p>
 * <blockquote><code>
 * java -mx200m edu.stanford.nlp.ie.crf.CRFClassifier -sighanCorporaDict $CH_SEG/data -NormalizationTable $CH_SEG/data/norm.simp.utf8 -normTableEncoding UTF-8 -loadClassifier $CH_SEG/data/ctb.gz -testFile $file -inputEncoding $enc
 * </code></blockquote>
 *
 * @author Pi-Chuan Chang
 * @author Huihsin Tseng
 * @author Galen Andrew
 */
package edu.stanford.nlp.wordseg;