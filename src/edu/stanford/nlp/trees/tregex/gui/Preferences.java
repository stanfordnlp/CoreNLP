package edu.stanford.nlp.trees.tregex.gui;

import java.awt.Color;

import edu.stanford.nlp.trees.HeadFinder;
import edu.stanford.nlp.trees.CollinsHeadFinder;
import edu.stanford.nlp.trees.LabeledScoredTreeReaderFactory;
import edu.stanford.nlp.trees.LeftHeadFinder;
import edu.stanford.nlp.trees.ModCollinsHeadFinder;
import edu.stanford.nlp.trees.PennTreeReaderFactory;
import edu.stanford.nlp.trees.SemanticHeadFinder;
import edu.stanford.nlp.trees.StringLabeledScoredTreeReaderFactory;
import edu.stanford.nlp.trees.TreeReaderFactory;
import edu.stanford.nlp.trees.UniversalSemanticHeadFinder;
import edu.stanford.nlp.trees.international.arabic.ArabicHeadFinder;
import edu.stanford.nlp.trees.international.arabic.ArabicTreeReaderFactory;
import edu.stanford.nlp.trees.international.french.DybroFrenchHeadFinder;
import edu.stanford.nlp.trees.international.french.FrenchTreeReaderFactory;
import edu.stanford.nlp.trees.international.negra.NegraHeadFinder;
import edu.stanford.nlp.trees.international.pennchinese.BikelChineseHeadFinder;
import edu.stanford.nlp.trees.international.pennchinese.ChineseHeadFinder;
import edu.stanford.nlp.trees.international.pennchinese.ChineseSemanticHeadFinder;
import edu.stanford.nlp.trees.international.pennchinese.CTBTreeReaderFactory;
import edu.stanford.nlp.trees.international.pennchinese.NoEmptiesCTBTreeReaderFactory;
import edu.stanford.nlp.trees.international.pennchinese.SunJurafskyChineseHeadFinder;
import edu.stanford.nlp.trees.international.tuebadz.TueBaDZHeadFinder;
import edu.stanford.nlp.trees.tregex.TregexPattern;

/**
 * Manages storage and retrieval of application preferences.
 *
 * @author Jon Gauthier
 */
public class Preferences {

  static final java.util.prefs.Preferences prefs =
    java.util.prefs.Preferences.userRoot().node(TregexGUI.class.getName());

  // Preference keys
  static final String PREF_FONT = "font";
  static final String PREF_FONT_SIZE = "fontSize";
  static final String PREF_TREE_COLOR = "treeColor";
  static final String PREF_MATCHED_COLOR = "matchedColor";
  static final String PREF_HIGHLIGHT_COLOR = "highlightColor";
  static final String PREF_HISTORY_SIZE = "historySize";
  static final String PREF_MAX_MATCHES = "maxMatches";
  static final String PREF_ENABLE_TSURGEON = "enableTsurgeon";
  static final String PREF_MATCH_PORTION_ONLY = "matchPortionOnly";
  static final String PREF_HEAD_FINDER = "headFinder";
  static final String PREF_TREE_READER_FACTORY = "treeReaderFactory";
  static final String PREF_ENCODING = "encoding";

  // Preference defaults
  static final String DEFAULT_FONT = "Dialog";
  static final int DEFAULT_FONT_SIZE = 12;
  static final int DEFAULT_TREE_COLOR = Color.BLACK.getRGB();
  static final int DEFAULT_MATCHED_COLOR = Color.RED.getRGB();
  static final int DEFAULT_HIGHLIGHT_COLOR = Color.CYAN.getRGB();
  static final int DEFAULT_HISTORY_SIZE = 5;
  static final int DEFAULT_MAX_MATCHES = 1000;
  static final boolean DEFAULT_ENABLE_TSURGEON = false;
  static final boolean DEFAULT_MATCH_PORTION_ONLY = false;
  static final String DEFAULT_HEAD_FINDER = "CollinsHeadFinder";
  static final String DEFAULT_TREE_READER_FACTORY = "TregexTreeReaderFactory";
  static final String DEFAULT_ENCODING = "UTF-8";

  /** Creates a Preferences; all of its methods are static. */
  public Preferences() { }

  /**
   * Returns the saved name of the font for displaying trees and matches, or Dialog
   * if none is saved.
   *
   * @return The font name
   */
  public static String getFont() { return prefs.get(PREF_FONT, DEFAULT_FONT); }
  /**
   * Saves the name of the font for displaying trees and matches.
   *
   * @param font The font name
   */
  public static void setFont(String font) { prefs.put(PREF_FONT, font); }

  /**
   * Returns the saved font size for displaying trees, or 12 if none is saved.
   *
   * @return The font size
   */
  public static int getFontSize() { return prefs.getInt(PREF_FONT_SIZE, DEFAULT_FONT_SIZE); }
  /**
   * Saves the font size for displaying trees.
   *
   * @param fontSize The font size
   */
  public static void setFontSize(int fontSize) { prefs.putInt(PREF_FONT_SIZE, fontSize); }

  /**
   * Returns the saved default color for drawing trees, or black if none is saved.
   *
   * @return The tree color
   */
  public static Color getTreeColor() { return new Color(prefs.getInt(PREF_TREE_COLOR, DEFAULT_TREE_COLOR)); }
  /**
   * Saves the default color for drawing trees.
   *
   * @param treeColor The tree color
   */
  public static void setTreeColor(Color treeColor) { prefs.putInt(PREF_TREE_COLOR, treeColor.getRGB()); }

  /**
   * Returns the saved color for matched nodes, or red if none is saved.
   *
   * @return The matched node color
   */
  public static Color getMatchedColor() { return new Color(prefs.getInt(PREF_MATCHED_COLOR, DEFAULT_MATCHED_COLOR)); }
  /**
   * Saves the color for matched nodes.
   *
   * @param matchedColor The matched node color
   */
  public static void setMatchedColor(Color matchedColor) { prefs.putInt(PREF_MATCHED_COLOR, matchedColor.getRGB()); }

  /**
   * Returns the saved highlight color for the list of matches, or cyan if none is
   * saved.
   *
   * @return The highlight color
   */
  public static Color getHighlightColor() { return new Color(prefs.getInt(PREF_HIGHLIGHT_COLOR, DEFAULT_HIGHLIGHT_COLOR)); }
  /**
   * Saves the highlight color for the list of matches.
   *
   * @param highlightColor The highlight color
   */
  public static void setHighlightColor(Color highlightColor) { prefs.putInt(PREF_HIGHLIGHT_COLOR, highlightColor.getRGB()); }

  /**
   * Returns the saved number of recent patterns to remember, or 5 if none is saved.
   *
   * @return The number of recent patterns
   */
  public static int getHistorySize() { return prefs.getInt(PREF_HISTORY_SIZE, DEFAULT_HISTORY_SIZE); }
  /**
   * Saves the number of recent patterns to remember.
   *
   * @param historySize The number of recent patterns
   */
  public static void setHistorySize(int historySize) { prefs.putInt(PREF_HISTORY_SIZE, historySize); }

  /**
   * Returns the saved maximum number of matches to display, or 1000 if none is
   * saved.
   *
   * @return The maximum number of matches
   */
  public static int getMaxMatches() { return prefs.getInt(PREF_MAX_MATCHES, DEFAULT_MAX_MATCHES); }
  /**
   * Saves the maximum number of matches to display.
   *
   * @param maxMatches The maximum number of matches
   */
  public static void setMaxMatches(int maxMatches) { prefs.putInt(PREF_MAX_MATCHES, maxMatches); }

  /**
   * Returns whether Tsurgeon is saved as enabled, or false if nothing is saved.
   *
   * @return Whether Tsurgeon is enabled
   */
  public static boolean getEnableTsurgeon() { return prefs.getBoolean(PREF_ENABLE_TSURGEON, DEFAULT_ENABLE_TSURGEON); }
  /**
   * Saves whether Tsurgeon is enabled.
   *
   * @param enableTsurgeon Whether Tsurgeon is enabled
   */
  public static void setEnableTsurgeon(boolean enableTsurgeon) { prefs.putBoolean(PREF_ENABLE_TSURGEON, enableTsurgeon); }

  /**
   * Returns whether only the matched portion of each tree is shown, or false if
   * nothing is saved.
   *
   * @return Whether only the matched portion is shown
   */
  public static boolean getMatchPortionOnly() { return prefs.getBoolean(PREF_MATCH_PORTION_ONLY, DEFAULT_MATCH_PORTION_ONLY); }
  /**
   * Saves whether only the matched portion of each tree is shown.
   *
   * @param matchPortionOnly Whether only the matched portion is shown
   */
  public static void setMatchPortionOnly(boolean matchPortionOnly) { prefs.putBoolean(PREF_MATCH_PORTION_ONLY, matchPortionOnly); }

  /**
   * Returns the saved encoding for reading tree files, or UTF-8 if none is saved.
   *
   * @return The encoding
   */
  public static String getEncoding() { return prefs.get(PREF_ENCODING, DEFAULT_ENCODING); }
  /**
   * Saves the encoding for reading tree files.
   *
   * @param encoding The encoding
   */
  public static void setEncoding(String encoding) { prefs.put(PREF_ENCODING, encoding); }

  /**
   * Returns a new instance of the saved head finder, or of CollinsHeadFinder if none
   * is saved.
   *
   * @return The head finder, or null if the saved name cannot be instantiated
   */
  public static HeadFinder getHeadFinder() {
    return lookupHeadFinder(prefs.get(PREF_HEAD_FINDER, DEFAULT_HEAD_FINDER));
  }

  /**
   * Saves the head finder by its simple class name, which {@link #getHeadFinder} can
   * only turn back into a head finder for the classes it knows by name.
   *
   * @param hf The head finder
   */
  public static void setHeadFinder(HeadFinder hf) {
    prefs.put(PREF_HEAD_FINDER, hf.getClass().getSimpleName());
  }

  static HeadFinder lookupHeadFinder(String headfinderName) {
    if(headfinderName.equalsIgnoreCase("ArabicHeadFinder")) {
      return new ArabicHeadFinder();
    } else if(headfinderName.equalsIgnoreCase("BikelChineseHeadFinder")) {
      return new BikelChineseHeadFinder();
    } else if(headfinderName.equalsIgnoreCase("ChineseHeadFinder")) {
      return new ChineseHeadFinder();
    } else if(headfinderName.equalsIgnoreCase("ChineseSemanticHeadFinder")) {
      return new ChineseSemanticHeadFinder();
    } else if(headfinderName.equalsIgnoreCase("CollinsHeadFinder")) {
      return new CollinsHeadFinder();
    } else if(headfinderName.equalsIgnoreCase("DybroFrenchHeadFinder")) {
      return new DybroFrenchHeadFinder();
    } else if(headfinderName.equalsIgnoreCase("LeftHeadFinder")) {
      return new LeftHeadFinder();
    }  else if(headfinderName.equalsIgnoreCase("ModCollinsHeadFinder")) {
      return new ModCollinsHeadFinder();
    }  else if(headfinderName.equalsIgnoreCase("NegraHeadFinder")) {
      return new NegraHeadFinder();
    }  else if(headfinderName.equalsIgnoreCase("SemanticHeadFinder")) {
      return new SemanticHeadFinder();
    } else if(headfinderName.equalsIgnoreCase("SunJurafskyChineseHeadFinder")) {
      return new SunJurafskyChineseHeadFinder();
    } else if(headfinderName.equalsIgnoreCase("TueBaDZHeadFinder")) {
      return new TueBaDZHeadFinder();
    } else if (headfinderName.equalsIgnoreCase("UniversalSemanticHeadFinder")) {
      return new UniversalSemanticHeadFinder();
    } else {//try to find the class
      try {
        Class<?> headfinder = Class.forName(headfinderName);
        return (HeadFinder) headfinder.getDeclaredConstructor().newInstance();
      } catch (Exception e) {
        return null;
      }
    }
  }

  /**
   * Returns a new instance of the saved tree reader factory, or of
   * TregexTreeReaderFactory if none is saved.
   *
   * @return The tree reader factory; a PennTreeReaderFactory if the saved name cannot be instantiated
   */
  public static TreeReaderFactory getTreeReaderFactory() {
    return lookupTreeReaderFactory(prefs.get(PREF_TREE_READER_FACTORY, DEFAULT_TREE_READER_FACTORY));
  }

  /**
   * Saves the tree reader factory by its simple class name, which {@link
   * #getTreeReaderFactory} can only turn back into a factory for the classes it
   * knows by name.
   *
   * @param trf The tree reader factory
   */
  public static void setTreeReaderFactory(TreeReaderFactory trf) {
    prefs.put(PREF_TREE_READER_FACTORY, trf.getClass().getSimpleName());
  }

  static TreeReaderFactory lookupTreeReaderFactory(String trfName) {
    if(trfName.equalsIgnoreCase("ArabicTreeReaderFactory")) {
      return new ArabicTreeReaderFactory();
    } else if(trfName.equalsIgnoreCase("ArabicTreeReaderFactory.ArabicRawTreeReaderFactory")) {
      return new ArabicTreeReaderFactory.ArabicRawTreeReaderFactory();
    } else if(trfName.equalsIgnoreCase("CTBTreeReaderFactory")) {
      return new CTBTreeReaderFactory();
    } else if(trfName.equalsIgnoreCase("NoEmptiesCTBTreeReaderFactory")) {
      return new NoEmptiesCTBTreeReaderFactory();
    } else if(trfName.equalsIgnoreCase("Basic categories only (LabeledScoredTreeReaderFactory)")) {
      return new LabeledScoredTreeReaderFactory();
    } else if(trfName.equalsIgnoreCase("FrenchTreeReaderFactory")) {
      return new FrenchTreeReaderFactory();//PTB format
    } else if(trfName.equalsIgnoreCase("PennTreeReaderFactory")) {
      return new PennTreeReaderFactory();
    } else if(trfName.equalsIgnoreCase("StringLabeledScoredTreeReaderFactory")) {
      return new StringLabeledScoredTreeReaderFactory();
    } else if(trfName.equalsIgnoreCase("TregexTreeReaderFactory")) {
      return new TregexPattern.TRegexTreeReaderFactory();
    } else {//try to find the class
      try {
        Class<?> trfClass = Class.forName(trfName);
        return (TreeReaderFactory) trfClass.getDeclaredConstructor().newInstance();
      } catch (Exception e) {
        return new PennTreeReaderFactory();
      }
    }
  }

}
