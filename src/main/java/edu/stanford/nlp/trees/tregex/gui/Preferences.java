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

  public static String getFont() { return prefs.get(PREF_FONT, DEFAULT_FONT); }
  public static void setFont(String font) { prefs.put(PREF_FONT, font); }

  public static int getFontSize() { return prefs.getInt(PREF_FONT_SIZE, DEFAULT_FONT_SIZE); }
  public static void setFontSize(int fontSize) { prefs.putInt(PREF_FONT_SIZE, fontSize); }

  public static Color getTreeColor() { return new Color(prefs.getInt(PREF_TREE_COLOR, DEFAULT_TREE_COLOR)); }
  public static void setTreeColor(Color treeColor) { prefs.putInt(PREF_TREE_COLOR, treeColor.getRGB()); }

  public static Color getMatchedColor() { return new Color(prefs.getInt(PREF_MATCHED_COLOR, DEFAULT_MATCHED_COLOR)); }
  public static void setMatchedColor(Color matchedColor) { prefs.putInt(PREF_MATCHED_COLOR, matchedColor.getRGB()); }

  public static Color getHighlightColor() { return new Color(prefs.getInt(PREF_HIGHLIGHT_COLOR, DEFAULT_HIGHLIGHT_COLOR)); }
  public static void setHighlightColor(Color highlightColor) { prefs.putInt(PREF_HIGHLIGHT_COLOR, highlightColor.getRGB()); }

  public static int getHistorySize() { return prefs.getInt(PREF_HISTORY_SIZE, DEFAULT_HISTORY_SIZE); }
  public static void setHistorySize(int historySize) { prefs.putInt(PREF_HISTORY_SIZE, historySize); }

  public static int getMaxMatches() { return prefs.getInt(PREF_MAX_MATCHES, DEFAULT_MAX_MATCHES); }
  public static void setMaxMatches(int maxMatches) { prefs.putInt(PREF_MAX_MATCHES, maxMatches); }

  public static boolean getEnableTsurgeon() { return prefs.getBoolean(PREF_ENABLE_TSURGEON, DEFAULT_ENABLE_TSURGEON); }
  public static void setEnableTsurgeon(boolean enableTsurgeon) { prefs.putBoolean(PREF_ENABLE_TSURGEON, enableTsurgeon); }

  public static boolean getMatchPortionOnly() { return prefs.getBoolean(PREF_MATCH_PORTION_ONLY, DEFAULT_MATCH_PORTION_ONLY); }
  public static void setMatchPortionOnly(boolean matchPortionOnly) { prefs.putBoolean(PREF_MATCH_PORTION_ONLY, matchPortionOnly); }

  public static String getEncoding() { return prefs.get(PREF_ENCODING, DEFAULT_ENCODING); }
  public static void setEncoding(String encoding) { prefs.put(PREF_ENCODING, encoding); }

  public static HeadFinder getHeadFinder() {
    return lookupHeadFinder(prefs.get(PREF_HEAD_FINDER, DEFAULT_HEAD_FINDER));
  }

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
        HeadFinder hf = (HeadFinder) headfinder.newInstance();
        return hf;
      } catch (Exception e) {
        return null;
      }
    }
  }

  public static TreeReaderFactory getTreeReaderFactory() {
    return lookupTreeReaderFactory(prefs.get(PREF_TREE_READER_FACTORY, DEFAULT_TREE_READER_FACTORY));
  }

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
        TreeReaderFactory trf = (TreeReaderFactory) trfClass.newInstance();
        return trf;
      } catch (Exception e) {
        return new PennTreeReaderFactory();
      }
    }
  }

}
