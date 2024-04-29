package edu.stanford.nlp.trees;

import edu.stanford.nlp.trees.tregex.TregexPattern;
import edu.stanford.nlp.trees.tregex.tsurgeon.Tsurgeon;
import edu.stanford.nlp.trees.tregex.tsurgeon.TsurgeonPattern;
import edu.stanford.nlp.util.StringUtils;

import java.util.Properties;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * Transforms an English structure parse tree in order to get the dependencies right:
 * Adds an extra structure in QP phrases:
 * <br>
 * (QP (RB well) (IN over) (CD 9)) becomes
 * <br>
 * (QP (XS (RB well) (IN over)) (CD 9))
 * <br>
 * (QP (...) (CC ...) (...)) becomes
 * <br>
 * (QP (NP ...) (CC ...) (NP ...))
 *
 *
 * @author mcdm
 */
public class QPTreeTransformer implements TreeTransformer {

  
  private boolean universalDependencies; // = false;
  
  public QPTreeTransformer() {
    this(false);
  }
    
  public QPTreeTransformer(boolean universalDependencies) {
    this.universalDependencies = universalDependencies;
  }
  
  
  /**
   * Right now (Jan 2013) we only deal with the following QP structures:
   * <ul>
   * <li> NP (QP ...) (QP (CC and/or) ...)
   * <li> QP (RB|RP IN CD|DT ...)   well over, more than, up to
   * <li> QP (JJR IN CD|DT ...)  fewer than
   * <li> QP (IN JJS CD|DT ...)  at least
   * <li> QP (... CC ...)        between 5 and 10
   * </ul>
   *
   * @param t tree to be transformed
   * @return The tree t with an extra layer if there was a QP structure matching the ones mentioned above
   */
  @Override
  public Tree transformTree(Tree t) {
     return QPtransform(t);
  }


  private static final TregexPattern flattenNPoverQPTregex =
    TregexPattern.compile("NP < (QP=left $+ (QP=right < CC))");

  private static final TsurgeonPattern flattenNPoverQPTsurgeon =
    Tsurgeon.parseOperation("[createSubtree QP left right] [excise left left] [excise right right]");

  private static final TregexPattern multiwordXSLTregex =
    // captures "up to"
    // once "up to" is captured in the XSL, the following XS operation won't accidentally grab it
    TregexPattern.compile("QP < ( RB|IN|RP=left < /^(?i:up)$/ $+ ( IN|TO=right < /^(?i:to)$/ ))");

  private static final TsurgeonPattern multiwordXSLTsurgeon =
    Tsurgeon.parseOperation("createSubtree XSL left right");

  private static final TregexPattern multiwordXSTregex =
    // TODO: should add NN and $ to the numeric expressions captured
    //   NN is for words such as "half" which are probably misparsed
    // TODO: <3 (IN < as|than) is to avoid one weird case in PTB,
    // "more than about".  Perhaps there is some way to generalize this
    // TODO: "all but X"
    // TODO: "all but about X"
    TregexPattern.compile("QP <1 /^RB|JJ|IN/=left [ ( <2 /^JJ|IN/=right <3 /^CD|DT/ ) | ( <2 /^JJ|IN/ <3 ( IN=right < /^(?i:as|than)$/ ) <4 /^CD|DT/ ) ] ");

  private static final TsurgeonPattern multiwordXSTsurgeon =
    Tsurgeon.parseOperation("createSubtree XS left right");

  // the old style split any flat QP with a CC in the middle
  // TOD: there should be some allowances for phrases such as "or more", "or so", etc
  private static final TregexPattern splitCCTregex =
    TregexPattern.compile("QP < (CC $- __=r1 $+ __=l2 ?$-- /^[$]|CC$/=lnum ?$++ /^[$]|CC$/=rnum) <1 __=l1 <- __=r2 !< (__ < (__ < __))");

  private static final TsurgeonPattern splitCCTsurgeon =
    Tsurgeon.parseOperation("[if exists lnum createSubtree QP l1 r1] [if not exists lnum createSubtree NP l1 r1] " +
                            "[if exists rnum createSubtree QP l2 r2] [if not exists rnum createSubtree NP l2 r2]");

  private static final TregexPattern splitMoneyTregex =
    TregexPattern.compile("QP < (/^[$]$/ !$++ /^(?!([$]|CD)).*$/ !$++ (__ < (__ < __)) $+ __=left) <- __=right");

  private static final TsurgeonPattern splitMoneyTsurgeon =
    Tsurgeon.parseOperation("createSubtree QP left right");

  // This fixes a very rare subset of parses
  // such as "(NP (QP just about all) the losses) ..."
  // in fact, that's the only example in ptb3-revised
  // because of previous MWE combinations, we may already get
  //     "(NP (QP at least a) day)"
  //  -> "(NP (QP (ADVP at least) a) day)"
  // and therefore the flattenAdvmodTsurgeon will also find that parse
  private static final TregexPattern groupADVPTregex =
    TregexPattern.compile("NP < (QP <1 RB=first <2 RB=second <3 (DT !$+ __) $++ /^N/)");

  private static final TsurgeonPattern groupADVPTsurgeon =
    Tsurgeon.parseOperation("createSubtree ADVP first second");

  // Remove QP in a structure such as
  //   (NP (QP nearly_RB all_DT) stuff_NN)
  // so that the converter can attach both `nearly` and `all` to `stuff`
  // not using a nummod, either, which is kind of annoying
  private static final TregexPattern flattenAdvmodTregex =
    TregexPattern.compile("NP < (QP=remove <1 ADVP|RB <2 (DT !$+ __) $++ /^N/)");

  private static final TsurgeonPattern flattenAdvmodTsurgeon =
    Tsurgeon.parseOperation("excise remove remove");

  /**
   * Transforms t if it contains one of the following QP structure:
   * <ul>
   * <li> NP (QP ...) (QP (CC and/or) ...)
   * <li> QP (RB IN CD|DT ...)   well over, more than
   * <li> QP (JJR IN CD|DT ...)  fewer than
   * <li> QP (IN JJS CD|DT ...)  at least
   * <li> QP (... CC ...)        between 5 and 10
   * </ul>
   *
   * @param t a tree to be transformed
   * @return t transformed
   */
  public Tree QPtransform(Tree t) {
    t = Tsurgeon.processPattern(flattenNPoverQPTregex, flattenNPoverQPTsurgeon, t);
    if (!universalDependencies) {
      t = Tsurgeon.processPattern(multiwordXSLTregex, multiwordXSLTsurgeon, t);
      t = Tsurgeon.processPattern(multiwordXSTregex, multiwordXSTsurgeon, t);
    }
    t = Tsurgeon.processPattern(splitCCTregex, splitCCTsurgeon, t);
    t = Tsurgeon.processPattern(splitMoneyTregex, splitMoneyTsurgeon, t);
    t = Tsurgeon.processPattern(groupADVPTregex, groupADVPTsurgeon, t);
    t = Tsurgeon.processPattern(flattenAdvmodTregex, flattenAdvmodTsurgeon, t);
    return t;
  }


  public static void main(String[] args) {

    QPTreeTransformer transformer = new QPTreeTransformer();
    Treebank tb = new MemoryTreebank();
    Properties props = StringUtils.argsToProperties(args);
    String treeFileName = props.getProperty("treeFile");

    if (treeFileName != null) {
      try {
        TreeReader tr = new PennTreeReader(new BufferedReader(new InputStreamReader(new FileInputStream(treeFileName))), new LabeledScoredTreeFactory());
        Tree t;
        while ((t = tr.readTree()) != null) {
          tb.add(t);
        }
      } catch (IOException e) {
        throw new RuntimeException("File problem: " + e);
      }

    }

    for (Tree t : tb) {
      System.out.println("Original tree");
      t.pennPrint();
      System.out.println();
      System.out.println("Tree transformed");
      Tree tree = transformer.transformTree(t);
      tree.pennPrint();
      System.out.println();
      System.out.println("----------------------------");
    }
  }

}
