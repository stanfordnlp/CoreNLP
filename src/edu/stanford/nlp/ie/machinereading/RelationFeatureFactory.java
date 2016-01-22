package edu.stanford.nlp.ie.machinereading;

import edu.stanford.nlp.ie.machinereading.structure.RelationMention;
import edu.stanford.nlp.ling.Datum;

import java.util.Set;
import java.util.logging.Logger;

/**
 * Base class for feature factories
 * Created by Sonal Gupta.
 */
public abstract class RelationFeatureFactory {

  public static enum DEPENDENCY_TYPE {
    BASIC, COLLAPSED, COLLAPSED_CCPROCESSED;
  }

  /** If true, it does not create any lexicalized features from the first argument (needed for KBP) */
  protected boolean doNotLexicalizeFirstArg;

  /** Which dependencies to use for feature extraction */
  protected DEPENDENCY_TYPE dependencyType;

  public abstract Datum<String,String> createDatum(RelationMention rel, String label);

  public abstract Datum<String,String> createDatum(RelationMention rel);

  public void setDoNotLexicalizeFirstArgument(boolean doNotLexicalizeFirstArg){
    this.doNotLexicalizeFirstArg = doNotLexicalizeFirstArg;
  }

  public abstract String getFeature(RelationMention rel, String dependency_path_lowlevel);

  public abstract Set<String> getFeatures(RelationMention rel, String dependency_path_words);

  /*
   * If in case, creating test datum is different.
   */
  public abstract Datum<String,String> createTestDatum(RelationMention rel, Logger logger);

}
