package edu.stanford.nlp.trees.international.spanish;

import java.util.regex.Pattern;

import edu.stanford.nlp.international.spanish.SpanishMorphoFeatureSpecification;
import edu.stanford.nlp.international.morph.MorphoFeatureSpecification;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.process.TokenizerFactory;
import edu.stanford.nlp.trees.AbstractTreebankLanguagePack;
import edu.stanford.nlp.trees.CollinsHeadFinder;
import edu.stanford.nlp.trees.HeadFinder;


/**
 * Language pack for the Spanish treebank.
 *
 * @author mcdm
 */
public class SpanishTreebankLanguagePack extends AbstractTreebankLanguagePack {

  private static final long serialVersionUID = -7059939700276532428L;

  // The AnCora treebank is distributed in ISO 8859-1 XML, but the
  // processed treebank (PTB-style) is UTF-8
  public static final String STB_ENCODING = "ISO8859_1";

  private static final String[] punctTags = {
    "faa", "fat", "fc", "fca", "fct", "fd", "fe", "fg", "fh", "fia", "fit",
    "fla", "flt", "fp", "fpa", "fpt", "fra", "frc", "fs", "ft", "fx", "fz",
    "f0"
  };

  private static final String[] sentenceFinalPunctTags = {
    "fat", "fit", "fp", "fs"
  };

  private static final String[] punctWords = {
    "¡", "!", ",", "[", "]", ":", "\"", "-", "/", "¿", "?", "{", "}", ".",
    "=LRB=", "=RRB=", "«", "»", "…", "...", "%", ";", "_", "+", "=", "&", "@"
  };

  private static final String[] sentenceFinalPunctWords = {
    "!", "?", ".", "…", "..."
  };

  private static final String[] startSymbols = {"ROOT"};

  /**
   * Return the input Charset encoding for the Treebank. See
   * documentation for the <code>Charset</code> class.
   *
   * @return Name of Charset
   */
  @Override
  public String getEncoding() {
    return STB_ENCODING;
  }

  /**
   * Returns a String array of punctuation tags for this treebank/language.
   *
   * @return The punctuation tags
   */
  @Override
  public String[] punctuationTags() {
    return punctTags;
  }


  /**
   * Returns a String array of punctuation words for this treebank/language.
   *
   * @return The punctuation words
   */
  @Override
  public String[] punctuationWords() {
    return punctWords;
  }


  /**
   * Returns a String array of sentence final punctuation tags for this
   * treebank/language.
   *
   * @return The sentence final punctuation tags
   */
  @Override
  public String[] sentenceFinalPunctuationTags() {
    return sentenceFinalPunctTags;
  }

  /**
   * Returns a String array of sentence final punctuation words for this
   * treebank/language.
   *
   * @return The sentence final punctuation tags
   */
  public String[] sentenceFinalPunctuationWords() {
    return sentenceFinalPunctWords;
  }


  /**
   * Returns a String array of treebank start symbols.
   *
   * @return The start symbols
   */
  @Override
  public String[] startSymbols() {
    return startSymbols;
  }


  /**
   * Returns the extension of treebank files for this treebank.
   */
  public String treebankFileExtension() {
    return "xml";
  }

  /** {@inheritDoc} */
  public HeadFinder headFinder() {
    return new SpanishHeadFinder(this);
  }

  /** {@inheritDoc} */
  public HeadFinder typedDependencyHeadFinder() {
    return new CollinsHeadFinder(this);
  }

  @Override
  public MorphoFeatureSpecification morphFeatureSpec() {
    return new SpanishMorphoFeatureSpecification();
  }

}
