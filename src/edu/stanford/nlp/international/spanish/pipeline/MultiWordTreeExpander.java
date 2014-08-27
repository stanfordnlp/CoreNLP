package edu.stanford.nlp.international.spanish.pipeline;

import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.tregex.TregexMatcher;
import edu.stanford.nlp.trees.tregex.TregexPattern;
import edu.stanford.nlp.trees.tregex.tsurgeon.Tsurgeon;
import edu.stanford.nlp.trees.tregex.tsurgeon.TsurgeonPattern;
import edu.stanford.nlp.util.Pair;

/**
 * Provides routines for "decompressing" further the expanded trees
 * formed by multiword token splitting.
 *
 * Multiword token expansion leaves constituent words as siblings in a
 * "flat" tree structure. This often represents an incorrect parse of
 * the sentence. For example, the phrase "Ministerio de Finanzas" should
 * not be parsed as a flat structure like
 *
 *     (grup.nom (np00000 Ministerio) (sp000 de) (np00000 Finanzas))
 *
 * but rather a "deep" structure like
 *
 *     (grup.nom (sp (prep (sp000 de))
 *                   (sn (grup.nom (np0000 Finanzas)))))
 *
 * This class provides methods for detecting common linguistic patterns
 * that should be expanded in this way.
 */
public class MultiWordTreeExpander {

  private static String PREPOSITIONS =
    "(para|al?|del?|con|sobre|en(?:tre)?)";

  private static TregexPattern prepositionalPhrase
    = TregexPattern.compile(// Match candidate preposition
                            "sp000=tag < /(?i)^" + PREPOSITIONS + "$/" +
                            // Headed by a group that was generated from
                            // multi-word token expansion and that we
                            // wish to expand further
                            " > (/(^grup\\.(c[cs]|[iwz]|nom|pron)|\\.inter)/ <- __=right)" +
                            // With an NP on the left (-> this is a
                            // prep. phrase) and not preceded by any
                            // other prepositions
                            " $+ /^[dn]/=left !$-- sp000");

  private static TregexPattern leadingPrepositionalPhrase
    = TregexPattern.compile(// Match candidate preposition
                            "sp000=tag < /(?i)^" + PREPOSITIONS + "$/" +
                            // Which is the first child in a group that
                            // was generated from multi-word token
                            // expansion and that we wish to expand
                            // further
                            " >, (/(^grup\\.(c[cs]|[iwz]|nom|pron)|\\.inter)/ <- __=right)" +
                            // With an NP on the left (-> this is a
                            // prep. phrase) and not preceded by any
                            // other prepositions
                            " $+ /^[dn]/=left !$-- sp000");

  /**
   * First step in expanding prepositional phrases: group NP to right of
   * preposition under a `grup.nom` subtree (specially labeled for now
   * so that we can target it in the next step)
   */
  private static TsurgeonPattern expandPrepositionalPhrase1 =
    Tsurgeon.parseOperation("[createSubtree grup.nom.inter left right]");

  /**
   * Matches intermediate prepositional phrase structures as produced by
   * the first step of expansion.
   */
  private static TregexPattern intermediatePrepositionalPhrase
    = TregexPattern.compile("sp000=preptag $+ /^grup\\.nom\\.inter$/=gn");

  /**
   * Second step: replace intermediate prepositional phrase structure
   * with final result.
   */
  private static TsurgeonPattern expandPrepositionalPhrase2 =
    Tsurgeon.parseOperation("[adjoinF (sp (prep T=preptarget) (sn foot@)) gn]" +
                            "[relabel gn /.inter$//]" +
                            "[replace preptarget preptag]" +
                            "[delete preptag]");

  private static TregexPattern prepositionalVP =
    TregexPattern.compile("sp000=tag < /^(para|al?|del?)$/" +
                          " > (/^grup\\.(c[cs]|[iwz]|nom|pron)/ <- __=right)" +
                          " $+ vmn0000=left !$-- sp000");

  private static TsurgeonPattern expandPrepositionalVP1 =
    Tsurgeon.parseOperation("[createSubtree S.inter left right]" +
                            "[adjoinF (infinitiu foot@) left]");

  private static TregexPattern intermediatePrepositionalVP =
    TregexPattern.compile("sp000=preptag $+ /^S\\.inter$/=si");

  private static TsurgeonPattern expandPrepositionalVP2 =
    Tsurgeon.parseOperation("[adjoin (sp=target S@) si] [move preptag >0 target]");

  private static TregexPattern conjunctPhrase =
    TregexPattern.compile("cc=cc" +
                          // In one of our expanded phrases (match
                          // bounds of this expanded phrase; these form
                          // the left edge of first new subtree and the
                          // right edge of the second new subtree)
                          " > (/^grup\\.nom/ <, __=left1 <` __=right2)" +
                          // Fetch more bounds: node to immediate left
                          // of cc is the right edge of the first new
                          // subtree, and node to right of cc is the
                          // left edge of the second new subtree
                          //
                          // NB: left1 may the same as right1; likewise
                          // for the second tree
                          " $- /^[^g]/=right1 $+ /^[^g]/=left2");

  private static TsurgeonPattern expandConjunctPhrase =
    Tsurgeon.parseOperation("[adjoinF (conj foot@) cc]" +
                            "[createSubtree grup.nom.inter2 left1 right1]" +
                            "[createSubtree grup.nom.inter2 left2 right2]");

  /**
   * Simple intermediate conjunct: a constituent which heads a single
   * substantive
   */
  private static TregexPattern intermediateSubstantiveConjunct =
    TregexPattern.compile("/grup\\.nom\\.inter2/=target <: /^[dnpw]/");

  /**
   * Rename simple intermediate conjunct as a `grup.nom`
   */
  private static TsurgeonPattern expandIntermediateSubstantiveConjunct =
    Tsurgeon.parseOperation("[relabel target /grup.nom/]");

  /**
   * Simple intermediate conjunct: a constituent which heads a single
   * adjective
   */
  private static TregexPattern intermediateAdjectiveConjunct =
    TregexPattern.compile("/^grup\\.nom\\.inter2$/=target <: /^a/");

  /**
   * Rename simple intermediate adjective conjunct as a `grup.a`
   */
  private static TsurgeonPattern expandIntermediateAdjectiveConjunct =
    Tsurgeon.parseOperation("[relabel target /grup.a/]");

  /**
   * Match parts of an expanded conjunct which must be labeled as a noun
   * phrase given their children.
   */
  private static TregexPattern intermediateNounPhraseConjunct =
    TregexPattern.compile("/^grup\\.nom\\.inter2$/=target < /^s[pn]/");

  private static TsurgeonPattern expandIntermediateNounPhraseConjunct =
    Tsurgeon.parseOperation("[relabel target sn]");

  /**
   * Match parts of an expanded conjunct which should be labeled as
   * nominal groups.
   */
  private static TregexPattern intermediateNominalGroupConjunct =
    TregexPattern.compile("/^grup\\.nom\\.inter2$/=target !< /^[^n]/");

  private static TsurgeonPattern expandIntermediateNominalGroupConjunct =
    Tsurgeon.parseOperation("[relabel target /grup.nom/]");

  /**
   * Match articles contained within nominal groups of substantives so
   * that they can be moved out
   */
  private static TregexPattern articleInsideNominalGroup =
    TregexPattern.compile("/^da/=art > (/^grup\\.nom$/=ng > sn)");

  private static TsurgeonPattern expandArticleInsideNominalGroup =
    Tsurgeon.parseOperation("[insert (spec=target) $+ ng] [move art >0 target]");

  // TODO intermediate adjectival conjunct
  // TODO intermediate verb conjunct

  // TODO fix articles
  //
  // /^da/=art > /^grup\.nom\.inter$/
  //
  // (hard mode: /^da/=art > /^grup\.nom\.inter$/ !>1 /^grup\.nom\.inter$/)

  // TODO date phrases

  /**
   * Expands flat structures into intermediate forms which will
   * eventually become deep phrase structures.
   */
  @SuppressWarnings("unchecked")
  private static List<Pair<TregexPattern, TsurgeonPattern>> firstStepExpansions =
    new ArrayList<Pair<TregexPattern, TsurgeonPattern>>() {{
      add(new Pair(leadingPrepositionalPhrase, expandPrepositionalPhrase1));
      add(new Pair(conjunctPhrase, expandConjunctPhrase));
      add(new Pair(prepositionalPhrase, expandPrepositionalPhrase1));
      add(new Pair(prepositionalVP, expandPrepositionalVP1));
    }};

  /**
   * Clean up "intermediate" phrase structures produced by previous step
   * and produce something from them that looks like the rest of the
   * corpus.
   */
  @SuppressWarnings("unchecked")
  private static List<Pair<TregexPattern, TsurgeonPattern>> intermediateExpansions =
    new ArrayList<Pair<TregexPattern, TsurgeonPattern>>() {{
      add(new Pair(intermediatePrepositionalPhrase, expandPrepositionalPhrase2));
      add(new Pair(intermediatePrepositionalVP, expandPrepositionalVP2));

      add(new Pair(intermediateSubstantiveConjunct, expandIntermediateSubstantiveConjunct));
      add(new Pair(intermediateAdjectiveConjunct, expandIntermediateAdjectiveConjunct));
      add(new Pair(intermediateNounPhraseConjunct, expandIntermediateNounPhraseConjunct));
      add(new Pair(intermediateNominalGroupConjunct, expandIntermediateNominalGroupConjunct));
    }};

  /**
   * Recognize candidate patterns for expansion in the given tree and
   * perform the expansions. See the class documentation for more
   * information.
   */
  public static Tree expandPhrases(Tree t) {
    // Keep running this sequence of patterns until no changes are
    // affected. We need this for nested expressions like "para tratar
    // de regresar al empleo." This first step produces lots of
    // "intermediate" tree structures which need to be cleaned up later.
    Tree oldTree;
    do {
      oldTree = t.deepCopy();
      t = Tsurgeon.processPatternsOnTree(firstStepExpansions, t);
    } while (!t.equals(oldTree));

    // Now clean up intermediate tree structures
    t = Tsurgeon.processPatternsOnTree(intermediateExpansions, t);

    return t;
  }

}

// Contrato . ayuda
// incidentes . lamentables (nested articles near middle)
// chiquilla . vistosa (giant multiword at end)
// espejo . deformante (article fun at start)
