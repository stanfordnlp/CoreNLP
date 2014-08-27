package edu.stanford.nlp.international.spanish.pipeline;

import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.tregex.TregexMatcher;
import edu.stanford.nlp.trees.tregex.TregexPattern;
import edu.stanford.nlp.trees.tregex.tsurgeon.Tsurgeon;
import edu.stanford.nlp.trees.tregex.tsurgeon.TsurgeonPattern;

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

  /**
   * Match candidates for prepositional phrase expansion.
   */
  private static TregexPattern prepositionalPhrase
    = TregexPattern.compile(// Match candidate preposition
                            "sp000=tag < /^(para|al?|del?)$/" +
                            // Headed by a group that was generated from
                            // multi-word token expansion and that we
                            // wish to expand further
                            " > (/^grup\\.(c[cs]|[iwz]|nom|pron)/ <- __=right)" +
                            // With an NP on the left (-> this is a
                            // prep. phrase) and not preceded by any
                            // other prepositions
                            " $+ /^n/=left !$-- sp000");

  /**
   * First step in expanding prepositional phrases: group NP to right of
   * preposition under a `grup.nom` subtree (specially labeled for now
   * so that we can target it in the next step)
   */
  private static TsurgeonPattern expandPrepositionalPhrase1 =
    Tsurgeon.parseOperation("[createSubtree grup.nom.partOfSp left right]");

  /**
   * Matches intermediate prepositional phrase structures as produced by
   * the first step of expansion.
   */
  private static TregexPattern intermediatePrepositionalPhrase
    = TregexPattern.compile("sp000=preptag $+ /^grup\\.nom\\.partOfSp$/=gn");

  /**
   * Second step: replace intermediate prepositional phrase structure
   * with final result.
   */
  private static TsurgeonPattern expandPrepositionalPhrase2 =
    Tsurgeon.parseOperation("[adjoinF (sp (prep T=preptarget) (sn foot@)) gn]" +
                            "[relabel gn /.partOfSp$//]" +
                            "[replace preptarget preptag]" +
                            "[delete preptag]");

  /**
   * Recognize candidate patterns for expansion in the given tree and
   * perform the expansions. See the class documentation for more
   * information.
   */
  public static Tree expandPhrases(Tree t) {
    t = Tsurgeon.processPattern(prepositionalPhrase,
                                expandPrepositionalPhrase1, t);
    t = Tsurgeon.processPattern(intermediatePrepositionalPhrase,
                                expandPrepositionalPhrase2, t);

    return t;
  }

}

// Esta puntualizacio'n
