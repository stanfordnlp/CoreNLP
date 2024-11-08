package edu.stanford.nlp.trees; 

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.trees.tregex.TregexPattern;
import edu.stanford.nlp.trees.tregex.TregexPatternCompiler;
import edu.stanford.nlp.trees.tregex.tsurgeon.Tsurgeon;
import edu.stanford.nlp.trees.tregex.tsurgeon.TsurgeonPattern;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.logging.Redwood;

/**
 * Helper class to perform a context-sensitive mapping of PTB POS
 * tags in a tree to universal POS tags.
 *
 * Author: Sebastian Schuster
 * Author: Christopher Manning
 *
 * The original Penn Treebank WSJ contains 45 POS tags (but almost certainly # for British pound currency is a bad idea!)
 *  {#=173, $=9,039, ''=8,658, ,=60,489, -LRB-=1,672, -RRB-=1,689, .=48,733, :=6,087, CC=29,462, CD=44,937, DT=101,190,
 *   EX=1,077, FW=268, IN=121,903, JJ=75,266, JJR=4,042, JJS=2,396, LS=64, MD=11,997, NN=163,935, NNP=114,053,
 *   NNPS=3,087, NNS=73,964, PDT=441, POS=10,801, PRP=21,357, PRP$=10,241, RB=38,197, RBR=2,175, RBS=555, RP=3,275,
 *   SYM=70, TO=27,449, UH=117, VB=32,565, VBD=37,493, VBG=18,239, VBN=24,865, VBP=15,377, VBZ=26,436, WDT=5,323,
 *   WP=2,887, WP$=219, WRB=2,625, ``=8,878}
 *
 * The Web Treebank corpus adds 6 tags, but doesn't have #, yielding 50 POS tags:
 *   ADD, AFX, GW, HYPH, NFP, XX
 *
 * OntoNotes 4.0 has 53 tags. It doesn't have # but adds: -LSB-, -RSB- [both mistakes!], ADD, AFX, CODE, HYPH, NFP,
 *   X [mistake!], XX
 *
 * @author Sebastian Schuster
 */

public class UniversalPOSMapper  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(UniversalPOSMapper.class);

  private static final boolean DEBUG = System.getProperty("UniversalPOSMapper", null) != null;

  @SuppressWarnings("WeakerAccess")
  public static final String DEFAULT_TSURGEON_FILE = "edu/stanford/nlp/models/upos/ENUniversalPOS.tsurgeon";

  private static boolean loaded; // = false;

  private static List<Pair<TregexPattern, TsurgeonPattern>> operations; // = null;

  private static TreeTransformer transformer;

  private UniversalPOSMapper() {} // static methods

  public static void load() {
    transformer = new DependencyTreeTransformer();

    TregexPatternCompiler compiler = new TregexPatternCompiler(new UniversalSemanticHeadFinder(true));
    operations = new ArrayList<>();
    // ------------------------------
    // Context-sensitive mappings
    // ------------------------------

    String [][] toContextMappings = new String [][] {
      // TO -> PART (in CONJP phrases)
      { "@CONJP < TO=target < VB",                 "PART", },
      { "@VP < @VP < (/^TO$/=target <... {/.*/})", "PART", },
      { "@VP <: (/^TO$/=target <... {/.*/})",      "PART", },
      { "TO=target <... {/.*/}",                   "ADP", },   // otherwise TO -> ADP
    };
    for (String[] newOp : toContextMappings) {
      operations.add(new Pair<>(compiler.compile(newOp[0]),
                                Tsurgeon.parseOperation("relabel target " + newOp[1])));

    }

    List<TregexPattern> auxPatterns = new ArrayList<>();
    auxPatterns.addAll(UniversalEnglishGrammaticalRelations.AUX_MODIFIER.targetPatterns());
    auxPatterns.addAll(UniversalEnglishGrammaticalRelations.AUX_PASSIVE_MODIFIER.targetPatterns());
    auxPatterns.addAll(UniversalEnglishGrammaticalRelations.COPULA.targetPatterns());
    for (TregexPattern pattern : auxPatterns) {
      // note that the original patterns capture both VB and AUX...
      // if we capture AUX here, infinite loop!
      // also, we don't relabel POS, since that would be a really weird UPOS/XPOS combination
      final String newTregex;
      final String newTsurgeon;
      if (pattern.knownVariables().contains("aux")) {
        newTregex = pattern.pattern() + ": (=aux == /^(?:VB)/)";
        newTsurgeon = "relabel aux AUX";
      } else {
        newTregex = pattern.pattern() + ": (=target == /^(?:VB)/)";
        newTsurgeon = "relabel target AUX";
      }
      if (DEBUG) {
        System.err.println(newTregex + "\n  " + newTsurgeon);
      }
      operations.add(new Pair<>(compiler.compile(newTregex),
                                Tsurgeon.parseOperation(newTsurgeon)));
    }

    String [][] otherContextMappings = new String [][] {
      // this will capture all verbs not found by the AUX_MODIFIER, AUX_PASSIVE_MODIFIER, and COPULA expressions above
      // VB.* -> VERB
      { "/^VB.*/=target <... {/.*/}", "VERB", },

      // IN -> SCONJ (subordinating conjunctions)
      { "/^SBAR(-[^ ]+)?$/ < (IN=target $++ @S|FRAG|SBAR|SINV <... {/.*/})", "SCONJ", },

      // IN -> SCONJ (subordinating conjunctions II)
      { "@PP < (IN=target $+ @SBAR|S)", "SCONJ" },

      // IN -> ADP (otherwise)
      { "IN=target < __", "ADP" },

      // NN -> SYM (in case of the percent sign)
      { "NN=target <... {/[%]/}", "SYM" },

      // fused det-noun pronouns -> PRON
      { "NN=target < (/^(?i:(somebody|something|someone|anybody|anything|anyone|everybody|everything|everyone|nobody|nothing))$/)",
        "PRON" },

      // NN -> NOUN (otherwise)
      { "NN=target <... {/.*/}", "NOUN" },

      // NFP -> PUNCT (in case of possibly repeated hyphens, asterisks or tildes)
      { "NFP=target <... {/^(~+|\\*+|\\-+)$/}", "PUNCT", },

      // NFP -> SYM (otherwise)
      { "NFP=target <... {/.*/}", "SYM" },

      // RB -> PART when it is verbal negation (not or its reductions)
      { "@VP|SINV|SQ|FRAG|ADVP < (RB=target < /^(?i:not|n't|nt|t|n)$/)", "PART" },

      // "not" as part of a phrase such as "not only", "not just", etc is tagged as PART in UD
      { "@ADVP|CONJP <1 (RB=target < /^(?i:not|n't|nt|t|n)$/) <2 (__ < only|just|merely|even) !<3 __", "PART" },

      // Otherwise RB -> ADV
      { "RB=target <... {/.*/}", "ADV" },

      // DT -> PRON (pronominal this/that/these/those)
      { "@NP <: (DT=target < /^(?i:th(is|at|ose|ese))$/)", "PRON", },

      // DT -> DET
      { "DT=target < __", "DET" },

      // WDT -> PRON (pronominal that/which)
      { "@WHNP|NP <: (WDT=target < /^(?i:(that|which))$/)", "PRON" },

      // WDT->SCONJ (incorrectly tagged subordinating conjunctions)
      { "@SBAR < (WDT=target < /^(?i:(that|which))$/)", "SCONJ" },

      // WDT -> DET
      { "WDT=target <... {/.*/}", "DET" },
    };
    for (String[] newOp : otherContextMappings) {
      operations.add(new Pair<>(compiler.compile(newOp[0]),
                                Tsurgeon.parseOperation("relabel target " + newOp[1])));

    }


    String [][] one2oneMappings = new String [][] {
      {"CC", "CCONJ"},
      {"CD", "NUM"},
      {"EX", "PRON"},
      {"FW", "X"},
      {"/^JJ.*$/", "ADJ"},
      {"LS", "NUM"},
      {"MD", "AUX"},
      {"NNS", "NOUN"},
      {"NNP", "PROPN"},
      {"NNPS", "PROPN"},
      {"PDT", "DET"},
      {"POS", "PART"},
      {"PRP", "PRON"},
      {"/^PRP[$]$/", "PRON"},
      {"RBR", "ADV"},
      {"RBS", "ADV"},
      {"RP", "ADP"},
      {"UH", "INTJ"},
      {"WP", "PRON"},
      {"/^WP[$]$/", "PRON"},
      {"WRB", "ADV"},
      {"/^``$/", "PUNCT"},
      {"/^''$/", "PUNCT"},
      {"/^[()]$/", "PUNCT"},
      {"/^-[RL]RB-$/", "PUNCT"},
      {"/^[,.:]$/", "PUNCT"},
      {"HYPH", "PUNCT"},
      // Also note that there is a no-op rule of SYM -> SYM!
      {"/^[#$]$/", "SYM"},
      {"ADD", "X"},
      {"AFX", "X"},
      {"GW", "X"},
      {"XX", "X"},
    };
    for (String[] newOp : one2oneMappings) {
      operations.add(new Pair<>(compiler.compile(newOp[0] + "=target <: __"),
                                Tsurgeon.parseOperation("relabel target " + newOp[1])));

    }
    loaded = true;
  }

  public static Tree mapTree(Tree t) {
    if ( ! loaded) {
      load();
    }

    if (operations == null) {
      return t;
    }

    t = t.deepCopy();
    t = transformer.transformTree(t);
    return Tsurgeon.processPatternsOnTree(operations, t);
  }

}
