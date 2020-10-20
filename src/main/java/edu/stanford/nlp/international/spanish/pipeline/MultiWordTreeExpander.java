package edu.stanford.nlp.international.spanish.pipeline;

import java.util.Arrays;
import java.util.List;

import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeFactory;
import edu.stanford.nlp.trees.TreeNormalizer;
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
 *
 * @author Jon Gauthier
 */
public class MultiWordTreeExpander {

  /**
   * Regular expression to match groups inside which we want to expand things
   */
  private static final String CANDIDATE_GROUPS = "(^grup\\.(adv|c[cs]|[iwz]|nom|prep|pron|verb)|\\.inter)";

  private static final String PREPOSITIONS =
    "(por|para|pro|al?|del?|con(?:tra)?|sobre|en(?:tre)?|hacia|sin|según|hasta|bajo)";

  private final TregexPattern parentheticalExpression = TregexPattern.compile(
    "fpa=left > /^grup\\.nom$/ " + "$++ fpt=right");

  private final TsurgeonPattern groupParentheticalExpression
    = Tsurgeon.parseOperation("createSubtree grup.nom.inter4 left right");

  /**
   * Yes, some multiword tokens contain multiple clauses..
   */
  private final TregexPattern multipleClauses
    = TregexPattern.compile(
      // Nested nominal group containing period punctuation
      "/^grup\\.nom/ > /^grup\\.nom/ < (fp !$-- fp $- /^[^g]/=right1 $+ __=left2)" +
      // Match boundaries for subtrees created
      " <, __=left1 <` __=right2");

  private final TsurgeonPattern expandMultipleClauses
    = Tsurgeon.parseOperation("[createSubtree grup.nom left1 right1]" +
      "[createSubtree grup.nom left2 right2]");

  private final TregexPattern prepositionalPhrase
    = TregexPattern.compile(// Match candidate preposition
                            "sp000=tag < /(?iu)^" + PREPOSITIONS + "$/" +
                            // Headed by a group that was generated from
                            // multi-word token expansion and that we
                            // wish to expand further
                            " > (/" + CANDIDATE_GROUPS + "/ <- __=right)" +
                            // With an NP on the left (-> this is a
                            // prep. phrase) and not preceded by any
                            // other prepositions
                            " $+ /^([adnswz]|p[ipr])/=left !$-- sp000");

  private final TregexPattern leadingPrepositionalPhrase
    = TregexPattern.compile(// Match candidate preposition
                            "sp000=tag < /(?iu)^" + PREPOSITIONS + "$/" +
                            // Which is the first child in a group that
                            // was generated from multi-word token
                            // expansion and that we wish to expand
                            // further
                            " >, (/" + CANDIDATE_GROUPS + "/ <- __=right)" +
                            // With an NP on the left (-> this is a
                            // prep. phrase) and not preceded by any
                            // other prepositions
                            " $+ /^([adnswz]|p[ipr])/=left !$-- sp000");

  /**
   * First step in expanding prepositional phrases: group NP to right of
   * preposition under a `grup.nom` subtree (specially labeled for now
   * so that we can target it in the next step)
   */
  private final TsurgeonPattern expandPrepositionalPhrase1 =
    Tsurgeon.parseOperation("[createSubtree grup.nom.inter left right]");

  /**
   * Matches intermediate prepositional phrase structures as produced by
   * the first step of expansion.
   */
  private final TregexPattern intermediatePrepositionalPhrase
    = TregexPattern.compile("sp000=preptag $+ /^grup\\.nom\\.inter$/=gn");

  /**
   * Second step: replace intermediate prepositional phrase structure
   * with final result.
   */
  private final TsurgeonPattern expandPrepositionalPhrase2 =
    Tsurgeon.parseOperation("[adjoinF (sp (prep T=preptarget) (sn foot@)) gn]" +
                            "[relabel gn /.inter$//]" +
                            "[replace preptarget preptag]" +
                            "[delete preptag]");

  private final TregexPattern prepositionalVP =
    TregexPattern.compile("sp000=tag < /(?i)^(para|al?|del?)$/" +
                          " > (/" + CANDIDATE_GROUPS + "/ <- __=right)" +
                          " $+ vmn0000=left !$-- sp000");

  private final TsurgeonPattern expandPrepositionalVP1 =
    Tsurgeon.parseOperation("[createSubtree S.inter left right]" +
                            "[adjoinF (infinitiu foot@) left]");

  private final TregexPattern intermediatePrepositionalVP =
    TregexPattern.compile("sp000=preptag $+ /^S\\.inter$/=si");

  private final TsurgeonPattern expandPrepositionalVP2 =
    Tsurgeon.parseOperation("[adjoin (sp prep=target S@) si] [move preptag >0 target]");

  private final TregexPattern conjunctPhrase =
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

  private final TsurgeonPattern expandConjunctPhrase =
    Tsurgeon.parseOperation("[adjoinF (conj foot@) cc]" +
                            "[createSubtree grup.nom.inter2 left1 right1]" +
                            "[createSubtree grup.nom.inter2 left2 right2]");

  /**
   * Simple intermediate conjunct: a constituent which heads a single
   * substantive
   */
  private final TregexPattern intermediateSubstantiveConjunct =
    TregexPattern.compile("/grup\\.nom\\.inter2/=target <: /^[dnpw]/");

  /**
   * Rename simple intermediate conjunct as a `grup.nom`
   */
  private final TsurgeonPattern expandIntermediateSubstantiveConjunct =
    Tsurgeon.parseOperation("[relabel target /grup.nom/]");

  /**
   * Simple intermediate conjunct: a constituent which heads a single
   * adjective
   */
  private final TregexPattern intermediateAdjectiveConjunct =
    TregexPattern.compile("/^grup\\.nom\\.inter2$/=target <: /^a/");

  /**
   * Rename simple intermediate adjective conjunct as a `grup.a`
   */
  private final TsurgeonPattern expandIntermediateAdjectiveConjunct =
    Tsurgeon.parseOperation("[relabel target /grup.a/]");

  /**
   * Match parts of an expanded conjunct which must be labeled as a noun
   * phrase given their children.
   */
  private final TregexPattern intermediateNounPhraseConjunct =
    TregexPattern.compile("/^grup\\.nom\\.inter2$/=target < /^s[pn]$/");

  private final TsurgeonPattern expandIntermediateNounPhraseConjunct =
    Tsurgeon.parseOperation("[relabel target sn]");

  /**
   * Intermediate conjunct: verb
   */
  private final TregexPattern intermediateVerbConjunct =
    TregexPattern.compile("/^grup\\.nom\\.inter2$/=gn <: /^vmi/");

  private final TsurgeonPattern expandIntermediateVerbConjunct =
    Tsurgeon.parseOperation("[adjoin (S (grup.verb@)) gn]");

  /**
   * Match parts of an expanded conjunct which should be labeled as
   * nominal groups.
   */
  private final TregexPattern intermediateNominalGroupConjunct =
    TregexPattern.compile("/^grup\\.nom\\.inter2$/=target !< /^[^n]/");

  private final TsurgeonPattern expandIntermediateNominalGroupConjunct =
    Tsurgeon.parseOperation("[relabel target /grup.nom/]");

  /**
   * Match articles contained within nominal groups of substantives so
   * that they can be moved out
   */
  private final TregexPattern articleLeadingNominalGroup =
    TregexPattern.compile("/^d[aip]/=art >, (/^grup\\.nom$/=ng > sn)");

  private final TsurgeonPattern expandArticleLeadingNominalGroup =
    Tsurgeon.parseOperation("[insert (spec=target) $+ ng] [move art >0 target]");

  private final TregexPattern articleInsideOrphanedNominalGroup =
    TregexPattern.compile("/^d[aip]/=d >, (/^grup\\.nom/=ng !> sn)");

  private final TsurgeonPattern expandArticleInsideOrphanedNominalGroup =
    Tsurgeon.parseOperation("[adjoinF (sn=sn spec=spec foot@) ng] [move d >0 spec]");

  private final TregexPattern determinerInsideNominalGroup =
    TregexPattern.compile("/^d[^n]/=det >, (/^grup\\.nom/=ng > sn) $ __");

  private final TsurgeonPattern expandDeterminerInsideNominalGroup =
    Tsurgeon.parseOperation("[insert (spec=target) $+ ng] [move det >0 target]");

  // "en opinion del X," "además del Y"
  private final TregexPattern contractionTrailingIdiomBeforeNominalGroup
    = TregexPattern.compile("sp000 >` (/^grup\\.prep$/ > (__ $+ /^grup\\.nom/=ng)) < /^(de|a)l$/=contraction");

  // -> "(en opinion de) (el X)," "(además de) (el Y)"
  private final TsurgeonPattern joinArticleWithNominalGroup
    = Tsurgeon.parseOperation("[relabel contraction /l//] [adjoinF (sn (spec (da0000 el)) foot@) ng]");

  private final TregexPattern contractionInSpecifier
    = TregexPattern.compile("sp000=parent < /(?i)^(a|de)l$/=contraction > spec");

  private final TregexPattern delTodo = TregexPattern.compile("del=contraction . todo > sp000=parent");

  // "del X al Y"
  private final TregexPattern contractionInRangePhrase
    = TregexPattern.compile("sp000 < /(?i)^(a|de)l$/=contraction >: (conj $+ (/^grup\\.(w|nom)/=group))");

  private final TsurgeonPattern expandContractionInRangePhrase
    = Tsurgeon.parseOperation("[relabel contraction /(?i)l//] [adjoinF (sn (spec (da0000 el)) foot@) group]");

  /**
   * Operation to extract article from contraction and just put it next to the container
   */
  private final TsurgeonPattern extendContraction
    = Tsurgeon.parseOperation("[relabel contraction /l//] [insert (da0000 el) $- parent]");

  // ---------

  // Final cleanup operations

  private final TregexPattern terminalPrepositions
    = TregexPattern.compile("sp000=sp < /" + PREPOSITIONS + "/ >- (/^grup\\.nom/ >+(/^grup\\.nom/) sn=sn >>- =sn)");

  private final TsurgeonPattern extractTerminalPrepositions = Tsurgeon.parseOperation(
    "[insert (prep=prep) $- sn] [move sp >0 prep]");

  /**
   * Match terminal prepositions in prepositional phrases: "a lo largo de"
   */
  private final TregexPattern terminalPrepositions2
    = TregexPattern.compile("prep=prep >` (/^grup\\.nom$/ >: (sn=sn > /^(grup\\.prep|sp)$/))");

  private final TsurgeonPattern extractTerminalPrepositions2
    = Tsurgeon.parseOperation("move prep $- sn");

  /**
   * Match terminal prepositions in infinitive clause within prepositional phrase: "a partir de," etc.
   */
  private final TregexPattern terminalPrepositions3
    = TregexPattern.compile("sp000=sp $- infinitiu >` (S=S >` /^(grup\\.prep|sp)$/)");

  private final TsurgeonPattern extractTerminalPrepositions3
    = Tsurgeon.parseOperation("[insert (prep=prep) $- S] [move sp >0 prep]");

  private final TregexPattern adverbNominalGroups = TregexPattern.compile("/^grup\\.nom./=ng <: /^r[gn]/=r");
  private final TsurgeonPattern replaceAdverbNominalGroup = Tsurgeon.parseOperation("replace ng r");

  /**
   * Match blocks of only adjectives (one or more) with a nominal group parent. These constituents should be rewritten
   * beneath an adjectival group constituent.
   */
  private final TregexPattern adjectiveSpanInNominalGroup
    = TregexPattern.compile("/^grup\\.nom/=ng <, aq0000=left <` aq0000=right !< /^[^a]/");

  /**
   * Match dependent clauses mistakenly held under nominal groups ("lo que X")
   */
  private final TregexPattern clauseInNominalGroup
    = TregexPattern.compile("lo . (que > (pr000000=pr >, /^grup\\.nom/=ng $+ (/^v/=vb >` =ng)))");

  private final TsurgeonPattern labelClause
    = Tsurgeon.parseOperation("[relabel ng S] [adjoinF (relatiu foot@) pr] [adjoinF (grup.verb foot@) vb]");

  /**
   * Infinitive clause mistakenly held under nominal group
   */
  private final TregexPattern clauseInNominalGroup2 = TregexPattern.compile("/^grup\\.nom/=gn $- spec <: /^vmn/");
  private final TsurgeonPattern labelClause2 = Tsurgeon.parseOperation("[adjoin (S (infinitiu@)) gn]");

  private final TregexPattern clauseInNominalGroup3 = TregexPattern.compile("sn=sn <, (/^vmn/=inf $+ (sp >` =sn))");
  private final TsurgeonPattern labelClause3
    = Tsurgeon.parseOperation("[relabel sn S] [adjoinF (infinitiu foot@) inf]");

  private final TregexPattern loneAdjectiveInNominalGroup
    = TregexPattern.compile("/^a/=a > /^grup\\.nom/ $ /^([snwz]|p[ipr])/ !$ /^a/");
  private final TsurgeonPattern labelAdjective = Tsurgeon.parseOperation("[adjoinF (s.a (grup.a foot@)) a]");

  private final TsurgeonPattern groupAdjectives = Tsurgeon.parseOperation("createSubtree (s.a grup.a@) left right");

  /**
   * Some brute-force fixes:
   */
  private final TregexPattern alMenos
    = TregexPattern.compile("/(?i)^al$/ . /(?i)^menos$/ > (sp000 $+ rg > /^grup\\.adv$/=ga)");
  private final TsurgeonPattern fixAlMenos
    = Tsurgeon.parseOperation("replace ga (grup.adv (sp (prep (sp000 a)) (sn (spec (da0000 lo)) (grup.nom (s.a (grup.a (aq0000 menos)))))))");
  private final TregexPattern todoLoContrario
    = TregexPattern.compile("(__=ttodo < /(?i)^todo$/) $+ (__=tlo < /(?i)^lo$/ $+ (__=tcon < /(?i)^contrario$/))");
  private final TsurgeonPattern fixTodoLoContrario
    = Tsurgeon.parseOperation("[adjoin (sn (grup.nom (pp000000@))) tlo] [adjoin (grup.a (aq0000@)) tcon]");

  /**
   * Mark infinitives within verb groups ("hacer ver", etc.)
   */
  private final TregexPattern infinitiveInVerbGroup
    = TregexPattern.compile("/^grup\\.verb$/=grup < (/^v/ !$-- /^v/ $++ (/^vmn/=target !$++ /^vmn/))");
  private final TsurgeonPattern markInfinitive = Tsurgeon.parseOperation("[adjoinF (infinitiu foot@) target]");

  /**
   * The corpus marks entire multiword verb tokens like "teniendo en
   * cuenta" as gerunds / infinitives (by heading them with a
   * constituent "gerundi" / "infinitiu"). Now that we've split into
   * separate words, transfer this gerund designation so that it heads
   * the verb only.
   */
  private final TregexPattern floppedGerund
    = TregexPattern.compile("/^grup\\.verb$/=grup >: gerundi=ger < (/^vmg/=vb !$ /^vmg/)");
  private final TsurgeonPattern unflopFloppedGerund
    = Tsurgeon.parseOperation("[adjoinF (gerundi foot@) vb] [replace ger grup]");
  private final TregexPattern floppedInfinitive
    = TregexPattern.compile("/^grup\\.verb$/=grup >: infinitiu=inf < (/^vmn/=vb !$ /^vmn/)");
  private final TsurgeonPattern unflopFloppedInfinitive
    = Tsurgeon.parseOperation("[adjoinF (infinitiu foot@) vb] [replace inf grup]");

  /**
   * Match `sn` constituents which can (should) be rewritten as nominal groups
   */
  private final TregexPattern nominalGroupSubstantives =
    TregexPattern.compile("sn=target < /^[adnwz]/ !< /^([^adnswz]|neg)/");

  private final TregexPattern leftoverIntermediates =
    TregexPattern.compile("/^grup\\.nom\\.inter/=target");

  private final TsurgeonPattern makeNominalGroup =
    Tsurgeon.parseOperation("[relabel target /grup.nom/]");

  private final TregexPattern redundantNominalRewrite =
    TregexPattern.compile("/^grup\\.nom$/ <: sn=child >: sn=parent");

  private final TsurgeonPattern fixRedundantNominalRewrite =
    Tsurgeon.parseOperation("[replace parent child]");

  private final TregexPattern redundantPrepositionGroupRewrite =
    TregexPattern.compile("/^grup\\.prep$/=parent <: sp=child >: prep");

  private final TsurgeonPattern fixRedundantPrepositionGroupRewrite =
    Tsurgeon.parseOperation("[relabel child /grup.prep/] [replace parent child]");

  private final TregexPattern redundantPrepositionGroupRewrite2 = TregexPattern.compile("/^grup\\.prep$/=gp <: sp=sp");
  private final TsurgeonPattern fixRedundantPrepositionGroupRewrite2 = Tsurgeon.parseOperation("replace gp sp");

  /**
   * Patterns in this list turn flat structures into intermediate forms
   * which will eventually become deep phrase structures.
   */
  private final List<Pair<TregexPattern, TsurgeonPattern>> firstStepExpansions = Arrays.asList(
    // Should be first-ish
          new Pair<>(parentheticalExpression, groupParentheticalExpression),
          new Pair<>(multipleClauses, expandMultipleClauses),

          new Pair<>(leadingPrepositionalPhrase,
                  expandPrepositionalPhrase1),
          new Pair<>(conjunctPhrase, expandConjunctPhrase),
          new Pair<>(prepositionalPhrase, expandPrepositionalPhrase1),
          new Pair<>(prepositionalVP, expandPrepositionalVP1),

          new Pair<>(contractionTrailingIdiomBeforeNominalGroup,
                  joinArticleWithNominalGroup),
          new Pair<>(contractionInSpecifier, extendContraction),
          new Pair<>(delTodo, extendContraction),
          new Pair<>(contractionInRangePhrase,
                  expandContractionInRangePhrase),

    // Should not happen until the last moment! The function words
    // being targeted have weaker "scope" than others earlier
    // targeted, and so we don't want to clump things around them
    // until we know we have the right to clump
          new Pair<>(articleLeadingNominalGroup,
                  expandArticleLeadingNominalGroup),
          new Pair<>(articleInsideOrphanedNominalGroup,
                  expandArticleInsideOrphanedNominalGroup),
          new Pair<>(determinerInsideNominalGroup,
                  expandDeterminerInsideNominalGroup)
  );

  /**
   * Patterns in this list clean up "intermediate" phrase structures
   * produced by previous step and produce something from them that
   * looks like the rest of the corpus.
   */
  private final List<Pair<TregexPattern, TsurgeonPattern>> intermediateExpansions = Arrays.asList(
          new Pair<>(intermediatePrepositionalPhrase,
                  expandPrepositionalPhrase2),
          new Pair<>(intermediatePrepositionalVP, expandPrepositionalVP2),

          new Pair<>(intermediateSubstantiveConjunct,
                  expandIntermediateSubstantiveConjunct),
          new Pair<>(intermediateAdjectiveConjunct,
                  expandIntermediateAdjectiveConjunct),
          new Pair<>(intermediateNounPhraseConjunct,
                  expandIntermediateNounPhraseConjunct),
          new Pair<>(intermediateVerbConjunct,
                  expandIntermediateVerbConjunct),
          new Pair<>(intermediateNominalGroupConjunct,
                  expandIntermediateNominalGroupConjunct)
  );

  /**
   * Patterns in this list perform last-minute cleanup of leftover
   * grammar mistakes which this class created.
   */
  private final List<Pair<TregexPattern, TsurgeonPattern>> finalCleanup = Arrays.asList(
          new Pair<>(terminalPrepositions, extractTerminalPrepositions),
          new Pair<>(terminalPrepositions2, extractTerminalPrepositions2),
          new Pair<>(terminalPrepositions3, extractTerminalPrepositions3),

          new Pair<>(nominalGroupSubstantives, makeNominalGroup),
          new Pair<>(adverbNominalGroups, replaceAdverbNominalGroup),
          new Pair<>(adjectiveSpanInNominalGroup, groupAdjectives),
          new Pair<>(clauseInNominalGroup, labelClause),
          new Pair<>(clauseInNominalGroup2, labelClause2),
          new Pair<>(clauseInNominalGroup3, labelClause3),
          new Pair<>(loneAdjectiveInNominalGroup, labelAdjective),

    // Verb phrase-related cleanup.. order is important!
          new Pair<>(infinitiveInVerbGroup, markInfinitive),
          new Pair<>(floppedGerund, unflopFloppedGerund),
          new Pair<>(floppedInfinitive, unflopFloppedInfinitive),

    // Fixes for specific common phrases
          new Pair<>(alMenos, fixAlMenos),
          new Pair<>(todoLoContrario, fixTodoLoContrario),

    // Lastly..
    //
    // These final fixes are not at all linguistically motivated -- just need to make the trees less dirty
          new Pair<>(redundantNominalRewrite, fixRedundantNominalRewrite),

          new Pair<>(redundantPrepositionGroupRewrite,
                  fixRedundantPrepositionGroupRewrite),

          new Pair<>(redundantPrepositionGroupRewrite2,
                  fixRedundantPrepositionGroupRewrite2),
          new Pair<>(leftoverIntermediates, makeNominalGroup)
  );

  /**
   * Recognize candidate patterns for expansion in the given tree and
   * perform the expansions. See the class documentation for more
   * information.
   */
  public Tree expandPhrases(Tree t, TreeNormalizer tn, TreeFactory tf) {
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

    // Normalize first to allow for contraction expansion, etc.
    t = tn.normalizeWholeTree(t, tf);

    // Final cleanup
    t = Tsurgeon.processPatternsOnTree(finalCleanup, t);

    return t;
  }

}

// GOOD EXAMPLES
// incidentes . lamentables (nested articles near middle)
// chiquilla . vistosa (giant multiword at end)
// espejo . deformante (article fun at start)
// menor . coste (watch "Comisión del Mercado" thing at end)
// totalmente . evitables ("en opinion del" at end)

// TODO (corpus)
// epígrafe . Arte (flat!)

// TODO (parser)
// debería .. encima ("por encima de" parse, coordinated NP)
// manía .. catalán ("castellana" parsed as being under a participi constituent)