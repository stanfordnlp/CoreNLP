package edu.stanford.nlp.process;

import java.util.*;

import junit.framework.TestCase;


/** @author Christopher Manning */
public class WordShapeClassifierTest extends TestCase {

  private static String[] inputs = { "fabulous", "Jørgensen", "--",
          "beta-carotene", "x-ray", "A.", "supercalifragilisticexpialadocious",
          "58", "59,000", "NF-kappa", "Exxon-Mobil", "a", "A4",
          "IFN-gamma-inducible", "PPARgamma", "NF-kappaB", "CBF1/RBP-Jkappa",
          "", "It's", "A-4", "congrès", "3,35%", "6€", "}", "《", "０-９",
          "四千", "五亿◯", "ＰＱ", "الحرازي", "2008", "427891", "A.B.C.",
          "22-34", "Ak47", "frEaKy", "美方称",
          "alphabeta", "betaalpha", "betalpha", "alpha-beta", "beta-alpha",
          "zalphabeta", "zbetaalpha", "zbetalpha", "zalpha-beta", "zbeta-alpha",
          "????", "***",
  };

  private static String[] chris1outputs = { "LOWERCASE", "CAPITALIZED", "SYMBOL",
          "LOWERCASE-DASH", "LOWERCASE-DASH", "ACRONYM1", "LOWERCASE",
          "CARDINAL13", "NUMBER", "CAPITALIZED-DASH", "CAPITALIZED-DASH", "LOWERCASE", "ALLCAPS-DIGIT",
          "CAPITALIZED-DASH", "CAPITALIZED", "CAPITALIZED-DASH", "CAPITALIZED-DIGIT-DASH",
          "SYMBOL", "CAPITALIZED", "ALLCAPS-DIGIT-DASH", "LOWERCASE", "SYMBOL-DIGIT", "SYMBOL-DIGIT", "SYMBOL", "SYMBOL", "DIGIT-DASH",
          "LOWERCASE", "LOWERCASE", "ALLCAPS", "LOWERCASE", "CARDINAL4", "CARDINAL5PLUS", "ACRONYM",
          "DIGIT-DASH", "CAPITALIZED-DIGIT", "MIXEDCASE", "LOWERCASE",
          "LOWERCASE", "LOWERCASE", "LOWERCASE", "LOWERCASE-DASH", "LOWERCASE-DASH",
          "LOWERCASE", "LOWERCASE", "LOWERCASE", "LOWERCASE-DASH", "LOWERCASE-DASH",
          "SYMBOL", "SYMBOL",
  };

  private static String[] chris2outputs = { "xxxxx", "Xxxxx", "--",
          "g-xxx", "x-xxx", "X.", "xxxxx",
          "dd", "dd,ddd", "XX-g", "Xx-Xxxx", "x", "Xd",
          "XX-Xgxxx", "XXXg", "XX-gX", "XX-/Xdg",
          "", "Xx'x", "X-d", "xxxxx", "d,dd%", "d€", "}", "《", "d-d",
          "四千", "五亿◯", "XX", "الاحرزي", "dddd", "ddddd", "X..XX.",
          "dd-dd", "Xxdd", "xxXxXx", "美方称",
          "gg", "gg", "gxxx", "g-g", "g-g", 
          "xgg", "xgg", "xgxxx", "xg-g", "xg-g",
          "????", "***",
  };

  private static String[] chris2KnownLCoutputs = { "xxxxxk", "Xxxxx", "--",
          "g-xxx", "x-xxx", "X.", "xxxxx",
          "dd", "dd,ddd", "XX-g", "Xx-Xxxx", "xk", "Xd",
          "XX-Xgxxx", "XXXg", "XX-gX", "XX-/Xdg",
          "", "Xx'x", "X-d", "xxxxx", "d,dd%", "d€", "}", "《", "d-d",
          "四千", "五亿◯", "XX", "الاحرزي", "dddd", "ddddd", "X..XX.",
          "dd-dd", "Xxdd", "xxXxXx", "美方称",
          "gg", "gg", "gxxx", "g-g", "g-g", 
          "xgg", "xgg", "xgxxx", "xg-g", "xg-g",
          "????", "***",
  };

  private static String[] chris3outputs = { "xxxx", "Xxxx", "--",
          "g-xx", "x-xx", "X.", "xxxx",
          "dd", "dd,dd", "XX-g", "Xx-xx", "x", "Xd",
          "XX-gxx", "XXg", "XX-gX", "XX-/dg",
          "", "Xx'x", "X-d", "xxxx", "d,d%", "d€", "}", "《", "d-d",
          "四千", "五亿◯", "XX", "الحرزي", "dddd", "dddd", "X.X.",
          "dd-dd", "Xxdd", "xxXx", "美方称",
          "g", "g", "gxx", "g-", "g-", 
          "xg", "xg", "xgxx", "xg-", "xg-",
          "????", "***",
  };

  private static String[] chris3KnownLCoutputs = { "xxxxk", "Xxxx", "--",
          "g-xx", "x-xx", "X.", "xxxx",
          "dd", "dd,dd", "XX-g", "Xx-xx", "xk", "Xd",
          "XX-gxx", "XXg", "XX-gX", "XX-/dg",
          "", "Xx'x", "X-d", "xxxx", "d,d%", "d€", "}", "《", "d-d",
          "四千", "五亿◯", "XX", "الحرزي", "dddd", "dddd", "X.X.",
          "dd-dd", "Xxdd", "xxXx", "美方称",
          "g", "g", "gxx", "g-", "g-", 
          "xg", "xg", "xgxx", "xg-", "xg-",
          "????", "***",
  };

  private static String[] chris4outputs = { "xxxxx", "Xxxxx", "--",
          "g-xxx", "x-xxx", "X.", "xxxxx",
          "dd", "dd.ddd", "XX-g", "Xx-Xxxx", "x", "Xd",
          "XX-Xgxxx", "XXXg", "XX-gX", "XX-.Xdg",
          "", "Xx'x", "X-d", "xxxxx", "d.dd%", "d$", ")", "(", "d-d",
          "dd", "ddd", "XX", "ccccc", "dddd", "ddddd", "X..XX.",
          "dd-dd", "Xxdd", "xxXxXx", "ccc",
          "gg", "gg", "gxxx", "g-g", "g-g", 
          "xgg", "xgg", "xgxxx", "xg-g", "xg-g",
          "....", "...",
  };

  private static String[] chris4KnownLCoutputs = { "xxxxxk", "Xxxxx", "--",
          "g-xxx", "x-xxx", "X.", "xxxxx",
          "dd", "dd.ddd", "XX-g", "Xx-Xxxx", "xk", "Xd",
          "XX-Xgxxx", "XXXg", "XX-gX", "XX-.Xdg",
          "", "Xx'x", "X-d", "xxxxx", "d.dd%", "d$", ")", "(", "d-d",
          "dd", "ddd", "XX", "ccccc", "dddd", "ddddd", "X..XX.",
          "dd-dd", "Xxdd", "xxXxXx", "ccc",
          "gg", "gg", "gxxx", "g-g", "g-g", 
          "xgg", "xgg", "xgxxx", "xg-g", "xg-g",
          "....", "...",
  };

  private static String[] digitsOutputs = { "fabulous", "Jørgensen", "--",
          "beta-carotene", "x-ray", "A.", "supercalifragilisticexpialadocious",
          "99", "99,999", "NF-kappa", "Exxon-Mobil", "a", "A9",
          "IFN-gamma-inducible", "PPARgamma", "NF-kappaB", "CBF9/RBP-Jkappa",
          "", "It's", "A-9", "congrès", "9,99%", "9€", "}", "《", "9-9",
          "四千", "五亿◯", "ＰＱ", "الحرازي", "9999", "999999", "A.B.C.",
          "99-99", "Ak99", "frEaKy", "美方称",
          "alphabeta", "betaalpha", "betalpha", "alpha-beta", "beta-alpha",
          "zalphabeta", "zbetaalpha", "zbetalpha", "zalpha-beta", "zbeta-alpha",
          "????", "***",
  };

  private static String[] knownLC = { "house", "fabulous", "octopus", "a" };


  private static void genericCheck(int wordshape, String[] in, String[] shape,
                                   String[] knownLCWords) {
    assertEquals("WordShapeClassifierTest is bung: array sizes differ",
                 in.length, shape.length);
    Set<String> knownLCset = null;
    if (knownLCWords != null) {
      knownLCset = new HashSet<>(Arrays.asList(knownLC));
    }
    for (int i = 0; i < in.length; i++) {
      assertEquals("WordShape " + wordshape + " for " + in[i] + " with " + (knownLCset == null ? "null": "non-null") + " knownLCwords is not correct!", shape[i], WordShapeClassifier.wordShape(in[i], wordshape, knownLCset));
    }
    try {
      WordShapeClassifier.wordShape(null, wordshape);
      fail("WordShapeClassifier threw no exception on null");
    } catch (NullPointerException npe) {
      // this is the good answer
    } catch (Exception e) {
      fail("WordShapeClassifier didn't throw NullPointerException on null");
    }
  }

  public static void outputResults(int wordshape, String[] in, String[] shape,
                                   String[] knownLCWords) {
    System.out.println("======================");
    System.out.println(" Classifier " + wordshape);
    System.out.println("======================");
    Set<String> knownLCset = null;
    if (knownLCWords != null) {
      knownLCset = new HashSet<>(Arrays.asList(knownLC));
    }
    for (int i = 0; i < in.length; ++i) {
      String result = 
        WordShapeClassifier.wordShape(in[i], wordshape, knownLCset);
      System.out.print("  " + in[i] + ": " + result);
      if (i < shape.length) {
        System.out.print("  (" + shape[i] + ')');
      }
      System.out.println();
    }
  }

  public void testChris1() {
    genericCheck(WordShapeClassifier.WORDSHAPECHRIS1, inputs, chris1outputs, null);
  }

  public void testChris2() {
    genericCheck(WordShapeClassifier.WORDSHAPECHRIS2, inputs, chris2outputs,  null);
    genericCheck(WordShapeClassifier.WORDSHAPECHRIS2USELC, inputs, chris2KnownLCoutputs, knownLC);
  }

  public void testChris3() {
    genericCheck(WordShapeClassifier.WORDSHAPECHRIS3, inputs, chris3outputs,  null);
    genericCheck(WordShapeClassifier.WORDSHAPECHRIS3USELC, inputs, chris3KnownLCoutputs, knownLC);
  }

  public void testChris4() {
    genericCheck(WordShapeClassifier.WORDSHAPECHRIS4, inputs, chris4outputs, null);
    genericCheck(WordShapeClassifier.WORDSHAPECHRIS4, inputs, chris4KnownLCoutputs, knownLC);
  }

  public void testDigits() {
    genericCheck(WordShapeClassifier.WORDSHAPEDIGITS, inputs, digitsOutputs, null);
  }

}
