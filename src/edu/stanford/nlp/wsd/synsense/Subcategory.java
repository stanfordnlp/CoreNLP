package edu.stanford.nlp.wsd.synsense;

import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;

import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A Class for representing Subcategorizations.
 *
 * @author Galen Andrew (pupochik@stanford.edu)
 * @version Jan 11, 2004 11:32:10 PM
 */

public class Subcategory implements Serializable, Comparable {

  private static final long serialVersionUID = -7014059206051706414L;

  public static int numPassUpgrades; // = 0;
  public static Counter<Subcategory> partCounter = new ClassicCounter<Subcategory>();

  private final String subcatName;
  private final String[] patterns;

  // important to keep this the same as the length of DEFECTIVE_SUBCAT_ARRAY
  private static final int numDefective = 5;

  public static final String BE = "'s|'re|'m|is|are|was|were|be|am|been|being";
  public static final String GET = "get|gets|got|gotten|getting";
  public static final String HAVE = "have|has|had|having|'d|'s|'ve";
  public static final String DO = "do|does|did|done|doing";
  public static final String BE_GET = BE + '|' + GET;

  // ordinal of next subcat to be created
  private static int nextIndex = -numDefective;
  private final int index = nextIndex++;

  public int index() {
    return index;
  }

  private Subcategory(String subcatName, String[] patterns) {
    this.subcatName = subcatName;
    this.patterns = patterns;
  }

  @Override
  public String toString() {
    return subcatName + '_' + index;
  }

  public int compareTo(Object o) {
    return index - ((Subcategory) o).index;
  }

  // necessary to canonicalize
  private Object readResolve() throws ObjectStreamException {
    if (index < 0) {
      return DEFECTIVE_SUBCAT_ARRAY[index + numDefective];
    } else {
      return REAL_SUBCAT_ARRAY[index];

    }
  }

  public static String escapeRegexChars(String s) {
    StringBuilder buff = new StringBuilder();
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == '^') {
        buff.append('\\');
      }
      buff.append(c);
    }
    return buff.toString();
  }

  /**
   * Returns the subcategory of a word in a particular sentence.
   * If the word does not match any legal verb in the sentence (i.e., is
   * not a verb or is a form of "to be" or "to have" in a passive or
   * perfective construction) OTHER is returned.
   */
  public static Subcategory getSubcategory(Tree tree, String word) {
    word = escapeRegexChars(word);
    if (!TgrepMatcher.subtreeMatches(tree, "(VP.*<(VB.*<<" + word + "))")) {
      return ILLEGAL;
    }

    if (ILLEGAL.matches(tree, word)) {
      return ILLEGAL;
    }

    boolean upgrade2transitive = false;
    if (PASS.matches(tree)) { // || INV.matches(tree))
      upgrade2transitive = true;
    }

    boolean partUpgrade = false;
    if (PART.matches(tree)) {
      partUpgrade = true;
    }

    for (Subcategory subcat : SUBCATEGORIES) {
      if (subcat == OTHER) {
        continue;
      }
      if (subcat == PRT) {
        break;
      }
      if (subcat.matches(tree, word)) {
        Subcategory temp;
        if (upgrade2transitive) {
          if (subcat == NULL) {
            temp = NP;
          } else if (subcat == PP) {
            temp = NP_PP;
          } else if (subcat == VP_ING) {
            temp = NP_VP_ING;
          } else if (subcat == VP_TO) {
            temp = NP_VP_TO;
          } else if (subcat == NP) {
            temp = NP_NP;
          } else if (subcat == S) {
            temp = NP_S;
          } else {
            //            System.out.println("WEIRD!!!!!");
            // this shouldn't happen often, if ever.
            // it means there's a passive usage of something already subcategorized as transitive
            temp = subcat;
          }
        } else {
          temp = subcat;
        }

        //        {OTHER, NULL, PP, VP_ING, S_FOR_TO, VP_TO, NP_VP_ING, NP_NP, NP, NP_PP, NP_VP_TO, NP_S, S,
        //        PRT, NP_PRT, PP_PRT, VP_PRT, S_PRT};

        if (partUpgrade) {
          partCounter.incrementCount(temp);
          //          tree.pennPrint();
          //          System.out.println("original cat: " + temp);
          if (temp == NULL) {
            temp = PRT;
          } else if (temp == NP) {
            temp = NP_PRT;
          } else if (temp == PP) {
            temp = PP_PRT;
          } else if (temp == VP_TO || temp == VP_ING) {
            temp = VP_PRT;
          } else if (temp == S || temp == S_FOR_TO) {
            temp = S_PRT;
          } else {
            temp = NP_PRT;
          }
        }
        return temp;
      }
    }

    return OTHER;
  }

  /**
   * Returns the subcategory of a tree with VP as root.
   */
  public static Subcategory getSubcategory(Tree tree) {
    if (!tree.label().value().matches("VP.*")) {
      return ILLEGAL;
    }

    if (ILLEGAL.matches(tree)) {
      return ILLEGAL;
    }

    boolean upgrade2transitive = false;
    if (PASS.matches(tree)) { // || INV.matches(tree))
      upgrade2transitive = true;
      numPassUpgrades++;
    }

    boolean partUpgrade = false;
    if (PART.matches(tree)) {
      partUpgrade = true;
    }

    for (Subcategory subcat : SUBCATEGORIES) {
      if (subcat == OTHER) {
        continue;
      }
      if (subcat == PRT) {
        break;
      }
      if (subcat.matches(tree)) {
        Subcategory temp;
        if (upgrade2transitive) {
          if (subcat == NULL) {
            temp = NP;
          } else if (subcat == PP) {
            temp = NP_PP;
          } else if (subcat == VP_ING) {
            temp = NP_VP_ING;
          } else if (subcat == VP_TO) {
            temp = NP_VP_TO;
          } else if (subcat == NP) {
            temp = NP_NP;
          } else if (subcat == S) {
            temp = NP_S;
          } else {
            //            System.out.println("WEIRD!!!!!");
            // this shouldn't happen often, if ever.
            // it means there's a passive usage of something already subcategorized as transitive
            temp = subcat;
          }
        } else {
          temp = subcat;
        }

        //        {OTHER, NULL, PP, VP_ING, S_FOR_TO, VP_TO, NP_VP_ING, NP_NP, NP, NP_PP, NP_VP_TO, NP_S, S,
        //        PRT, NP_PRT, PP_PRT, VP_PRT, S_PRT};

        if (partUpgrade) {
          partCounter.incrementCount(temp);
          //          tree.pennPrint();
          //          System.out.println("original cat: " + temp);
          if (temp == NULL) {
            temp = PRT;
          } else if (temp == NP) {
            temp = NP_PRT;
          } else if (temp == PP) {
            temp = PP_PRT;
          } else if (temp == VP_TO || temp == VP_ING) {
            temp = VP_PRT;
          } else if (temp == S || temp == S_FOR_TO) {
            temp = S_PRT;
          } else {
            temp = NP_PRT;
          }
        }
        return temp;
      }
    }

    return Subcategory.OTHER;
  }

  public boolean matches(Tree tree) {
    for (String pattern : patterns) {
      if (TgrepMatcher.matches(tree, closeParens(pattern))) {
        return true;
      }
    }
    return false;
  }

  public boolean matches(Tree tree, String word) {
    for (String pattern : patterns) {
      if (TgrepMatcher.subtreeMatches(tree, closeParens(pattern + "<<" + word))) {
        return true;
      }
    }
    return false;
  }

  private static String closeParens(String s) {
    int num = 0;
    for (int i = 0; i < s.length(); i++) {
      if (s.charAt(i) == '(') {
        num++;
      } else if (s.charAt(i) == ')') {
        num--;
      }
    }
    for (int i = 0; i < num; i++) {
      s = s.concat(")");
    }
    return s;
  }

  // "defective" subcategories
  public static final Subcategory UNASSIGNED = new Subcategory("Unass", new String[0]);
  public static final Subcategory DISTRIBUTION = new Subcategory("Dist", new String[0]);
  public static final Subcategory ILLEGAL = new Subcategory("Ill", new String[]{"(VP.*<(VB.*<<" + DO + "%..VP.*", "(VP.*<(VB.*<<" + HAVE + "%..(VP.*<VBN.*)", "(VP.*<(VB.*<<" + BE});
  public static final Subcategory PASS = new Subcategory("Pass", new String[]{"(VP.*%(VB.*<<" + BE_GET + ")<(VBN.*"});
  // what is this really called?
  // whatever-- not using it now because it only has ~60% accuracy
  //  public static final Subcategory INV = new Subcategory("Inv", new String[] {
  //    "(VP.*%NP>(S>(SBAR>NP%NP))<(VB.*",
  //    "(VP.*>(VP.*<(VB.*<<," + DO + "|" + HAVE + "|" + BE + ")%NP>(S>(SBAR>NP%NP)))<(VB.*"
  //  });
  public static final Subcategory PART = new Subcategory("Part", new String[]{"(VP.*<(VB.*%PRT"});

  // real subcategories

  public static final Subcategory OTHER = new Subcategory("Other", null);

  public static final Subcategory NULL = new Subcategory("Null", new String[]{"(VP.*<(VB.*!%..NP|PP|S|SBAR|VP.*|FRAG.*"});
  public static final Subcategory PP = new Subcategory("PP", new String[]{"(VP.*<(VB.*!%..NP|S|SBAR|VP.*|FRAG.*%..PP"});
  public static final Subcategory VP_ING = new Subcategory("VPing", new String[]{"(VP.*<(VB.*!%..NP|VP.*|FRAG.*%..(S|SBAR<<,(VP.*<<,VBG.*))", "(VP.*<(VB.*!%..NP|S|SBAR|FRAG.*%..(VP.*<<,VBG.*)"});
  // must be before VP_TO
  public static final Subcategory S_FOR_TO = new Subcategory("SForto", new String[]{"(VP.*<(VB.*!%..NP|VP.*%..(PP%..(S|SBAR<<,(TO<<to)))"//,
                                                                                    //    "(VP.*<(VB.*!%..NP|VP.*%..(S|SBAR<(S|SBAR|<(TO<<to)))%..PP"
  });
  public static final Subcategory VP_TO = new Subcategory("VPto", new String[]{"(VP.*<(VB.*!%..NP|VP.*%..(S|SBAR<<,(TO<<to))"//,
                                                                               //  "(VP.*<(VB.*!%..NP|PP|VP.*%..(S|SBAR<(S|SBAR<(TO<<to)))"
  });
  public static final Subcategory NP_VP_ING = new Subcategory("NPVPing", new String[]{"(VP.*<(VB.*!%..VP.*|FRAG.*%..(S|SBAR<<,(NP%..(VP.*<<,VBG.*)))", "(VP.*<(VB.*!%..S|SBAR|FRAG.*%..(NP%..(VP.*<<,VBG.*))"});
  public static final Subcategory NP_NP = new Subcategory("NPNP", new String[]{"(VP.*<(VB.*!%..S|SBAR|VP.*|PP|FRAG.*%..(NP%..NP)"});
  // must follow NP_NP
  public static final Subcategory NP = new Subcategory("NP", new String[]{"(VP.*<(VB.*!%..PP|S|SBAR|VP.*|FRAG.*%..NP"});
  public static final Subcategory NP_PP = new Subcategory("NPPP", new String[]{"(VP.*<(VB.*!%..S|SBAR|VP.*|FRAG.*%..(NP%..PP)"});
  public static final Subcategory NP_VP_TO = new Subcategory("NPVPto", new String[]{"(VP.*<(VB.*!%..VP.*%..(NP%..(S|SBAR<<,(TO<<to)))", "(VP.*<(VB.*!%..VP.*%..(S|SBAR<(NP%..(VP<<,(TO<<to))))", "(VP.*<(VB.*!%..VP.*%..(S|SBAR<(NP%..(S|SBAR<<,(TO<<to))))"});
  // must follow NP_VPto and NP_VPing
  public static final Subcategory NP_S = new Subcategory("NP_S", new String[]{"(VP.*<(VB.*!%..(NP%..NP)!%..PP|VP%..(NP%..S|SBAR)"});
  // must follow all other S subcategories
  public static final Subcategory S = new Subcategory("S", new String[]{"(VP.*<(VB.*!%..NP|PP|VP.*|FRAG.*%..S|SBAR"});

  public static final Subcategory PRT = new Subcategory("PRT", null);
  public static final Subcategory NP_PRT = new Subcategory("NP_PRT", null);
  public static final Subcategory PP_PRT = new Subcategory("PP_PRT", null);
  public static final Subcategory VP_PRT = new Subcategory("VP_PRT", null);
  public static final Subcategory S_PRT = new Subcategory("S_PRT", null);

  // if the length of this array is changed, it must be reflected in
  // the static numDefective!!!
  private static final Subcategory[] DEFECTIVE_SUBCAT_ARRAY = {UNASSIGNED, DISTRIBUTION, ILLEGAL, PASS, PART};

  // PASS must be first
  private static final Subcategory[] REAL_SUBCAT_ARRAY = {OTHER, NULL, PP, VP_ING, S_FOR_TO, VP_TO, NP_VP_ING, NP_NP, NP, NP_PP, NP_VP_TO, NP_S, S, //        {0     1      2     3       4        5        6       7      8   9      10        11   12 }
                                                          PRT, NP_PRT, PP_PRT, VP_PRT, S_PRT};
  //       13   14       15       16     17
  public static final List<Subcategory> SUBCATEGORIES = Collections.unmodifiableList(Arrays.asList(REAL_SUBCAT_ARRAY));

}
