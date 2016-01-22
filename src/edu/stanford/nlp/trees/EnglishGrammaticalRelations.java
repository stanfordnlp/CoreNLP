package edu.stanford.nlp.trees;

import edu.stanford.nlp.trees.GrammaticalRelation.GrammaticalRelationAnnotation;
import edu.stanford.nlp.trees.GrammaticalRelation.Language;
import edu.stanford.nlp.trees.tregex.TregexPatternCompiler;
import edu.stanford.nlp.util.Generics;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static edu.stanford.nlp.trees.GrammaticalRelation.*;


/**
 * <code>EnglishGrammaticalRelations</code> is a
 * set of {@link GrammaticalRelation} objects for the English language.
 * These relations are commonly called Stanford Dependencies (SD).
 * <p/>
 * Grammatical relations can either be shown in their basic form, where each
 * input token receives a relation, or "collapsed" which does certain normalizations
 * which group words or turns them into relations. See
 * {@link EnglishGrammaticalStructure}.  What is presented here mainly
 * shows the basic form, though there is some mixture. The "collapsed" grammatical
 * relations primarily differ as follows:
 * <ul>
 * <li>Some multiword conjunctions and prepositions are treated as single
 * words, and then processed as below.</li>
 * <li>Prepositions do not appear as words but are turned into new "prep" or "prepc"
 * grammatical relations, one for each preposition.</li>
 * <li>Conjunctions do not appear as words but are turned into new "conj"
 * grammatical relations, one for each conjunction.</li>
 * <li>The possessive "'s" is deleted, leaving just the relation between the
 * possessor and possessum.</li>
 * <li>Agents of passive sentences are recognized and marked as agent and not as prep_by.</li>
 * </ul>
 * <p/>
 * This set of English grammatical relations is not intended to be
 * exhaustive or immutable.  It's just where we're at now.
 * <p/>
 * <p/>
 * See {@link GrammaticalRelation} for details of fields and matching.
 * <p/>
 * <p/>
 * If using LexicalizedParser, it should be run with the
 * <code>-retainTmpSubcategories</code> option and one of the
 * <code>-splitTMP</code> options (e.g., <code>-splitTMP 1</code>) in order to
 * get the temporal NP dependencies maximally right!
 * <p/>
 * <i>Implementation notes: </i> Don't change the set of GRs without discussing it
 * with people first.  If a change is needed, to add a new grammatical relation:
 * <ul>
 * <li> Governor nodes of the grammatical relations should be the lowest ones.</li>
 * <li> Check the semantic head rules in SemanticHeadFinder and
 * ModCollinsHeadFinder, both in the trees package. That's what will be used to
 * match here.</li>
 * <li> Create and define the GrammaticalRelation similarly to the others.</li>
 * <li> Add it to the <code>values</code> array at the end of the file.</li>
 * </ul>
 * The patterns in this code assume that an NP may be followed by either a
 * -ADV or -TMP functional tag but there are no other functional tags represented.
 * This corresponds to what we currently get from NPTmpRetainingTreeNormalizer or
 * DependencyTreeTransformer.
 *
 * @author Bill MacCartney
 * @author Marie-Catherine de Marneffe
 * @author Christopher Manning
 * @author Galen Andrew (refactoring English-specific stuff)
 * @see GrammaticalStructure
 * @see GrammaticalRelation
 * @see EnglishGrammaticalStructure
 */

public class EnglishGrammaticalRelations {

  //todo: Things still to fix: comparatives, in order to clauses, automatic Vadas-like NP structure

  /** This class is just a holder for static classes
   *  that act a bit like an enum.
   */
  private EnglishGrammaticalRelations() {}

  private static final String timeWordRegex =
    "/^(?i:Mondays?|Tuesdays?|Wednesdays?|Thursdays?|Fridays?|Saturdays?|Sundays?|years?|months?|weeks?|days?|mornings?|evenings?|nights?|January|Jan\\.|February|Feb\\.|March|Mar\\.|April|Apr\\.|May|June|July|August|Aug\\.|September|Sept\\.|October|Oct\\.|November|Nov\\.|December|Dec\\.|today|yesterday|tomorrow|spring|summer|fall|autumn|winter)$/";
  private static final String timeWordLotRegex =
    "/^(?i:Mondays?|Tuesdays?|Wednesdays?|Thursdays?|Fridays?|Saturdays?|Sundays?|years?|months?|weeks?|days?|mornings?|evenings?|nights?|January|Jan\\.|February|Feb\\.|March|Mar\\.|April|Apr\\.|May|June|July|August|Aug\\.|September|Sept\\.|October|Oct\\.|November|Nov\\.|December|Dec\\.|today|yesterday|tomorrow|spring|summer|fall|autumn|winter|lot)$/";
  // r is for texting r = are
  private static final String copularWordRegex =
    "/^(?i:am|is|are|r|be|being|'s|'re|'m|was|were|been|s|ai|seem|seems|seemed|seeming|appear|appears|appeared|stay|stays|stayed|remain|remains|remained|resemble|resembles|resembled|resembling|become|becomes|became|becoming)$/";
  private static final String passiveAuxWordRegex =
    "/^(?i:am|is|are|r|be|being|'s|'re|'m|was|were|been|s|ai|seem|seems|seemed|seeming|appear|appears|appeared|become|becomes|became|becoming|get|got|getting|gets|gotten|remains|remained|remain)$/";
  private static final String beAuxiliaryRegex =
    "/^(?i:am|is|are|r|be|being|'s|'re|'m|was|were|been|s|ai)$/";
  private static final String haveRegex =
    "/^(?i:have|had|has|having)$/";
  private static final String stopKeepRegex =
    "/^(?i:stop|stops|stopped|stopping|keep|keeps|kept|keeping)$/";

  private static final TregexPatternCompiler tregexCompiler = new TregexPatternCompiler(new SemanticHeadFinder());

  /**
   * The "predicate" grammatical relation.  The predicate of a
   * clause is the main VP of that clause; the predicate of a
   * subject is the predicate of the clause to which the subject
   * belongs.<p>
   * <p/>
   * Example: <br/>
   * "Reagan died" &rarr; <code>pred</code>(Reagan, died)
   */
  public static final GrammaticalRelation PREDICATE =
    new GrammaticalRelation(Language.English, "pred", "predicate",
        PredicateGRAnnotation.class, DEPENDENT, "S|SINV", tregexCompiler,
        new String[] {
          "S|SINV <# VP=target"
        });
  public static class PredicateGRAnnotation extends GrammaticalRelationAnnotation { }


  /**
   * The "auxiliary" grammatical relation.  An auxiliary of a clause is a
   * non-main verb of the clause.<p>
   * <p/>
   * Example: <br/>
   * "Reagan has died" &rarr; <code>aux</code>(died, has)
   */
  public static final GrammaticalRelation AUX_MODIFIER =
    new GrammaticalRelation(Language.English, "aux", "auxiliary",
        AuxModifierGRAnnotation.class, DEPENDENT, "VP|SQ|SINV|CONJP", tregexCompiler,
        new String[] {
          "VP < VP < /^(?:TO|MD|VB.*|AUXG?|POS)$/=target",
          "SQ|SINV < (/^(?:VB|MD|AUX)/=target $++ /^(?:VP|ADJP)/)",
          "CONJP < TO=target < VB", // (CONJP not to mention)
          // add handling of tricky VP fronting cases...
          "SINV < (VP=target < (/^(?:VB|AUX|POS)/ < " + beAuxiliaryRegex + ") $-- (VP < VBG))",
        });
  public static class AuxModifierGRAnnotation extends GrammaticalRelationAnnotation { }


  /**
    * The "passive auxiliary" grammatical relation. A passive auxiliary of a
    * clause is a
    * non-main verb of the clause which contains the passive information.
    * <p/>
    * Example: <br/>
    * "Kennedy has been killed" &rarr; <code>auxpass</code>(killed, been)
    */
  public static final GrammaticalRelation AUX_PASSIVE_MODIFIER =
 	  new GrammaticalRelation(Language.English, "auxpass", "passive auxiliary",
              AuxPassiveGRAnnotation.class, AUX_MODIFIER, "VP|SQ|SINV", tregexCompiler,
 	      new String[] {
	        "VP < (/^(?:VB|AUX|POS)/=target < " + passiveAuxWordRegex + " ) < (VP|ADJP [ < VBN|VBD | < (VP|ADJP < VBN|VBD) < CC ] )",
          "SQ|SINV < (/^(?:VB|AUX|POS)/=target < " + beAuxiliaryRegex + " $++ (VP < VBD|VBN))",
 	        // add handling of tricky VP fronting cases...
 	        "SINV < (VP=target < (/^(?:VB|AUX|POS)/ < " + beAuxiliaryRegex + ") $-- (VP < VBD|VBN))",
 	        "SINV < (VP=target < (VP < (/^(?:VB|AUX|POS)/ < " + beAuxiliaryRegex + ")) $-- (VP < VBD|VBN))",
 	      });
  public static class AuxPassiveGRAnnotation extends GrammaticalRelationAnnotation { }

  /**
   * The "copula" grammatical relation.  A copula is the relation between
   * the complement of a copular verb and the copular verb.<p>
   * <p/>
   * Examples: <br/>
   * "Bill is big" &rarr; <code>cop</code>(big, is) <br/>
   * "Bill is an honest man" &rarr; <code>cop</code>(man, is)
   */
  public static final GrammaticalRelation COPULA =
    new GrammaticalRelation(Language.English, "cop", "copula",
        CopulaGRAnnotation.class, AUX_MODIFIER, "VP|SQ|SINV", tregexCompiler,
        new String[] {
          // disallow things like NP-TMP in this pattern, so don't use @NP
          "VP < (/^(?:VB|AUX)/=target < " + copularWordRegex + " [ $++ (/^(?:ADJP|NP$|WHNP$)/ !< VBN|VBD) | $++ (S <: (ADJP < JJ)) ] )",
          "SQ|SINV < (/^(?:VB|AUX)/=target < " + copularWordRegex + " [ $++ (ADJP !< VBN|VBD) | $++ (NP $++ NP) | $++ (S <: (ADJP < JJ)) ] )",
        });

  public static class CopulaGRAnnotation extends GrammaticalRelationAnnotation {
  }


  /**
   * The "conjunct" grammatical relation.  A conjunct is the relation between
   * two elements connected by a conjunction word.  We treat conjunctions
   * asymmetrically: The head of the relation is the first conjunct and other
   * conjunctions depend on it via the <i>conj</i> relation.<p>
   * <p/>
   * Example: <br/>
   * "Bill is big and honest" &rarr; <code>conj</code>(big, honest)
   * <p/>
   * <i>Note:</i>Modified in 2010 to exclude the case of a CC/CONJP first in its phrase: it has to conjoin things.
   */
  public static final GrammaticalRelation CONJUNCT =
    new GrammaticalRelation(Language.English, "conj", "conjunct",
        ConjunctGRAnnotation.class, DEPENDENT, "VP|(?:WH)?NP(?:-TMP|-ADV)?|ADJP|PP|QP|ADVP|UCP(?:-TMP|-ADV)?|S|NX|SBAR|SBARQ|SINV|SQ|JJP|NML", tregexCompiler,
        new String[] { // remember conjunction can be left or right headed....
          // this is more ugly, but the first 3 patterns are now duplicated and for clausal things, that daughter to the left of the CC/CONJP can't be a PP or RB or ADVP either
          // non-parenthetical or comma in suitable phrase with conjunction to left
          "VP|S|SBAR|SBARQ|SINV|SQ < (CC|CONJP $-- !/^(?:``|-LRB-|PRN|PP|ADVP|RB)/ $+ !/^(?:PRN|``|''|-[LR]RB-|,|:|\\.)$/=target)",
          // non-parenthetical or comma in suitable phrase with conj then adverb to left
          "VP|S|SBAR|SBARQ|SINV|SQ < (CC|CONJP $-- !/^(?:``|-LRB-|PRN|PP|ADVP|RB)/ $+ (ADVP $+ !/^(?:PRN|``|''|-[LR]RB-|,|:|\\.)$/=target))",
          // content phrase to the right of a comma or a parenthetical
          // The test at the end is to make sure that a conjunction or
          // comma etc actually show up between the target of the conj
          // dependency and the head of the phrase.  Otherwise, a
          // different relationship is probably more appropriate.
          "VP|S|SBAR|SBARQ|SINV|SQ=root < (CC|CONJP $-- !/^(?:``|-LRB-|PRN|PP|ADVP|RB)/) < (/^(?:PRN|``|''|-[LR]RB-|,|:|\\.)$/ $+ (/^S|SINV$|^(?:A|N|V|PP|PRP|J|W|R)/=target $-- (/^CC|CONJP|:|,$/ $-- (__ ># =root))) )",

          // non-parenthetical or comma in suitable phrase with conjunction to left
          "/^(?:ADJP|JJP|PP|QP|(?:WH)?NP(?:-TMP|-ADV)?|ADVP|UCP(?:-TMP|-ADV)?|NX|NML)$/ < (CC|CONJP $-- !/^(?:``|-LRB-|PRN)$/ $+ !/^(?:PRN|``|''|-[LR]RB-|,|:|\\.)$/=target)",
          // non-parenthetical or comma in suitable phrase with conj then adverb to left
          "/^(?:ADJP|PP|(?:WH)?NP(?:-TMP|-ADV)?|ADVP|UCP(?:-TMP|-ADV)?|NX|NML)$/ < (CC|CONJP $-- !/^(?:``|-LRB-|PRN)$/ $+ (ADVP $+ !/^(?:PRN|``|''|-[LR]RB-|,|:|\\.)$/=target))",
          // content phrase to the right of a comma or a parenthetical
          "/^(?:ADJP|PP|(?:WH)?NP(?:-TMP|-ADV)?|ADVP|UCP(?:-TMP|-ADV)?|NX|NML)$/ < (CC|CONJP $-- !/^(?:``|-LRB-|PRN)$/) < (/^(?:PRN|``|''|-[LR]RB-|,|:|\\.)$/ $+ /^S|SINV$|^(?:A|N|V|PP|PRP|J|W|R)/=target)",

          // content phrase to the left of a comma for at least NX
          "NX|NML < (CC|CONJP $- __) < (/^,$/ $- /^(?:A|N|V|PP|PRP|J|W|R|S)/=target)",
          // to take the conjunct in a preconjunct structure "either X or Y"
          "/^(?:VP|S|SBAR|SBARQ|SINV|ADJP|PP|QP|(?:WH)?NP(?:-TMP|-ADV)?|ADVP|UCP(?:-TMP|-ADV)?|NX|NML)$/ < (CC $++ (CC|CONJP $+ !/^(?:PRN|``|''|-[LR]RB-|,|:|\\.)$/=target))",
        });
  public static class ConjunctGRAnnotation extends GrammaticalRelationAnnotation { }


  /**
   * The "coordination" grammatical relation.  A coordination is the relation
   * between an element and a conjunction.
   * <p/>
   * Example: <br/>
   * "Bill is big and honest." &rarr; <code>cc</code>(big, and)
   */
  public static final GrammaticalRelation COORDINATION =
    new GrammaticalRelation(Language.English, "cc", "coordination",
        CoordinationGRAnnotation.class, DEPENDENT, ".*", tregexCompiler,
        new String[] {
          "__ [ < (CC=target !< /^(?i:either|neither|both)$/ ) | < (CONJP=target !< (RB < /^(?i:not)$/ $+ (RB|JJ < /^(?i:only|just|merely)$/))) ]"
        });
  public static class CoordinationGRAnnotation extends GrammaticalRelationAnnotation { }


  /**
   * The "punctuation" grammatical relation.  This is used for any piece of
   * punctuation in a clause, if punctuation is being retained in the
   * typed dependencies.
   * <p/>
   * Example: <br/>
   * "Go home!" &rarr; <code>punct</code>(Go, !)
   * <p/>
   * The condition for NFP to appear hear is that it does not match the emoticon patterns under discourse.
   */
  public static final GrammaticalRelation PUNCTUATION =
    new GrammaticalRelation(Language.English, "punct", "punctuation",
        PunctuationGRAnnotation.class, DEPENDENT, ".*", tregexCompiler,
        new String[] {
          "__ < /^(?:\\.|:|,|''|``|\\*|-LRB-|-RRB-|HYPH)$/=target",
          "__ < (NFP=target !< /^(?:[<>]?[:;=8][\\-o\\*']?(?:-RRB-|-LRB-|[DPdpO\\/\\\\\\:}{@\\|\\[\\]])|(?:-RRB-|-LRB-|[DPdpO\\/\\\\\\:}{@\\|\\[\\]])[\\-o\\*']?[:;=8][<>]?)$/"
                        + "!< /^(?:-LRB-)?[\\-\\^x=~<>'][_.]?[\\-\\^x=~<>'](?:-RRB-)?$/)",
        });
  public static class PunctuationGRAnnotation extends GrammaticalRelationAnnotation { }


  /**
   * The "argument" grammatical relation.  An argument of a VP is a
   * subject or complement of that VP; an argument of a clause is
   * an argument of the VP which is the predicate of that
   * clause.<p>
   * <p/>
   * Example: <br/>
   * "Clinton defeated Dole" &rarr; <code>arg</code>(defeated, Clinton), <code>arg</code>(defeated, Dole)
   */
  public static final GrammaticalRelation ARGUMENT =
    new GrammaticalRelation(Language.English, "arg", "argument",
        ArgumentGRAnnotation.class, DEPENDENT);
  public static class ArgumentGRAnnotation extends GrammaticalRelationAnnotation { }


  /**
   * The "subject" grammatical relation.  The subject of a VP is
   * the noun or clause that performs or experiences the VP; the
   * subject of a clause is the subject of the VP which is the
   * predicate of that clause.<p>
   * <p/>
   * Examples: <br/>
   * "Clinton defeated Dole" &rarr; <code>subj</code>(defeated, Clinton) <br/>
   * "What she said is untrue" &rarr; <code>subj</code>(is, What she said)
   */
  public static final GrammaticalRelation SUBJECT =
    new GrammaticalRelation(Language.English, "subj", "subject",
        SubjectGRAnnotation.class, ARGUMENT);
  public static class SubjectGRAnnotation extends GrammaticalRelationAnnotation { }


  /**
   * The "nominal subject" grammatical relation.  A nominal subject is
   * a subject which is an noun phrase.<p>
   * <p/>
   * Example: <br/>
   * "Clinton defeated Dole" &rarr; <code>nsubj</code>(defeated, Clinton)
   */
  public static final GrammaticalRelation NOMINAL_SUBJECT =
    new GrammaticalRelation(Language.English, "nsubj", "nominal subject",
        NominalSubjectGRAnnotation.class, SUBJECT, "S|SQ|SBARQ|SINV|SBAR|PRN", tregexCompiler,
        new String[] {
          "S < ((NP|WHNP=target !< EX !<# (/^NN/ < (" + timeWordRegex + "))) $++ VP)",
          "S < ( NP=target <# (/^NN/ < " + timeWordRegex + ") !$++ NP $++VP)",
          "SQ|PRN < (NP=target !< EX $++ VP)",
          "SQ < ((NP=target !< EX) $- /^(?:VB|AUX)/ !$++ VP)",
          "SQ < ((NP=target !< EX) $- (RB $- /^(?:VB|AUX)/) ![$++ VP])",
          "SBARQ < WHNP=target < (SQ < (VP ![$-- NP]))",
          "SBARQ < (SQ=target < /^(?:VB|AUX)/ !< VP)",
          // matches subj in SINV
          "SINV < (NP|WHNP=target [ $- VP|VBZ|VBD|VBP|VB|MD|AUX | $- (@RB|ADVP $- VP|VBZ|VBD|VBP|VB|MD|AUX) | !$- __ !$ @NP] )",
          //matches subj in xcomp like "He considered him a friend"
          "S < (NP=target $+ NP|ADJP) > VP",
          // matches subj in relative clauses
          "SBAR < WHNP=target [ < (S < (VP !$-- NP) !< SBAR) | < (VP !$-- NP) !< S ]",  // second disjunct matches errors where there is no S under SBAR and otherwise does no harm
          // matches subj in relative clauses
          "SBAR !< WHNP < (S !< (NP $++ VP)) > (VP > (S $- WHNP=target))",
          // matches subj in existential "there" SQ
          "SQ < ((NP < EX) $++ NP=target)",
          // matches subj in existential "there" S
          "S < (NP < EX) <+(VP) (VP < NP=target)"
        });
  public static class NominalSubjectGRAnnotation extends GrammaticalRelationAnnotation { }


  /**
   * The "nominal passive subject" grammatical relation.  A nominal passive
   * subject is a subject of a passive which is an noun phrase.<p>
   * <p/>
   * Example: <br/>
   * "Dole was defeated by Clinton" &rarr; <code>nsubjpass</code>(defeated, Dole)
   * <p>
   * This pattern recognizes basic (non-coordinated) examples.  The coordinated
   * examples are currently handled by correctDependencies() in
   * EnglishGrammaticalStructure.  This seemed more accurate than any tregex
   * expression we could come up with.
   */
  public static final GrammaticalRelation NOMINAL_PASSIVE_SUBJECT =
    new GrammaticalRelation(Language.English, "nsubjpass", "nominal passive subject",
        NominalPassiveSubjectGRAnnotation.class, NOMINAL_SUBJECT, "S|SQ", tregexCompiler,
        new String[] {
          "S|SQ < (WHNP|NP=target !< EX) < (VP < (/^(?:VB|AUX)/ < " + passiveAuxWordRegex + ")  < (VP < VBN|VBD))",
        });
  public static class NominalPassiveSubjectGRAnnotation extends GrammaticalRelationAnnotation { }


  /**
   * The "clausal subject" grammatical relation.  A clausal subject is
   * a subject which is a clause.<p>
   * <p/>
   * Examples: (subject is "what she said" in both examples) <br/>
   * "What she said makes sense" &rarr; <code>csubj</code>(makes, said) <br/>
   * "What she said is untrue" &rarr; <code>csubj</code>(untrue, said)
   */
  public static final GrammaticalRelation CLAUSAL_SUBJECT =
    new GrammaticalRelation(Language.English, "csubj", "clausal subject",
        ClausalSubjectGRAnnotation.class, SUBJECT, "S", tregexCompiler,
        new String[] {
          "S < (SBAR|S=target !$+ /^,$/ $++ (VP !$-- NP))"
        });
  public static class ClausalSubjectGRAnnotation extends GrammaticalRelationAnnotation { }



  /**
   * The "clausal passive subject" grammatical relation.  A clausal passive subject is
   * a subject of a passive verb which is a clause.<p>
   * <p/>
   * Example: (subject is "that she lied") <br/>
   * "That she lied was suspected by everyone" &rarr; <code>csubjpass</code>(suspected, lied)
   */
  public static final GrammaticalRelation CLAUSAL_PASSIVE_SUBJECT =
    new GrammaticalRelation(Language.English, "csubjpass", "clausal subject",
        ClausalPassiveSubjectGRAnnotation.class, CLAUSAL_SUBJECT, "S", tregexCompiler,
        new String[] {
          "S < (SBAR|S=target !$+ /^,$/ $++ (VP < (VP < VBN|VBD) < (/^(?:VB|AUXG?)/ < " + passiveAuxWordRegex + ") !$-- NP))",
          "S < (SBAR|S=target !$+ /^,$/ $++ (VP <+(VP) (VP < VBN|VBD > (VP < (/^(?:VB|AUX)/ < " + passiveAuxWordRegex + "))) !$-- NP))"
        });
  public static class ClausalPassiveSubjectGRAnnotation extends GrammaticalRelationAnnotation { }



  /**
   * The "complement" grammatical relation.  A complement of a VP
   * is any object (direct or indirect) of that VP, or a clause or
   * adjectival phrase which functions like an object; a complement
   * of a clause is an complement of the VP which is the predicate
   * of that clause.<p>
   * <p/>
   * Examples: <br/>
   * "She gave me a raise" &rarr;
   * <code>comp</code>(gave, me),
   * <code>comp</code>(gave, a raise) <br/>
   * "I like to swim" &rarr;
   * <code>comp</code>(like, to swim)
   */
  public static final GrammaticalRelation COMPLEMENT =
    new GrammaticalRelation(Language.English, "comp", "complement",
        ComplementGRAnnotation.class, ARGUMENT);
  public static class ComplementGRAnnotation extends GrammaticalRelationAnnotation { }


  /**
   * The "object" grammatical relation.  An object of a VP
   * is any direct object or indirect object of that VP; an object
   * of a clause is an object of the VP which is the predicate
   * of that clause.<p>
   * <p/>
   * Examples: <br/>
   * "She gave me a raise" &rarr;
   * <code>obj</code>(gave, me),
   * <code>obj</code>(gave, raise)
   */
  public static final GrammaticalRelation OBJECT =
    new GrammaticalRelation(Language.English, "obj", "object",
        ObjectGRAnnotation.class, COMPLEMENT);
  public static class ObjectGRAnnotation extends GrammaticalRelationAnnotation { }


  /**
   * The "direct object" grammatical relation.  The direct object
   * of a VP is the noun phrase which is the (accusative) object of
   * the verb; the direct object of a clause is the direct object of the VP
   * which is the predicate of that clause.<p>
   * <p/>
   * Example: <br/>
   * "She gave me a raise" &rarr;
   * <code>dobj</code>(gave, raise)
   */
  public static final GrammaticalRelation DIRECT_OBJECT =
    new GrammaticalRelation(Language.English, "dobj", "direct object",
        DirectObjectGRAnnotation.class, OBJECT, "SBARQ|VP|SBAR", tregexCompiler,
        new String[] {
          // case with an iobj before
          "VP < (NP $+ (NP|WHNP=target !< (/^NN/ < " + timeWordLotRegex + "))) !<(/^(?:VB|AUX)/ < " + copularWordRegex + ")",  // this time one also included "lot"
          // match "give it next week"
          "VP < (NP < (NP $+ (/^(NP|WHNP)$/=target !< (/^NN/ < " + timeWordLotRegex + "))))!< (/^(?:VB|AUX)/ < " + copularWordRegex + ")",  // this time one also included "lot"
          // basic direct object cases
          "VP !<(/^(?:VB|AUX)/ < " + copularWordRegex + ") < (NP|WHNP=target [ [ !<# (/^NN/ < " + timeWordRegex + ") !$+ NP ] | $+ NP-TMP | $+ (NP <# (/^NN/ < " + timeWordRegex + ")) ] )",
          // cdm Jul 2010: No longer require WHNP as first child of SBARQ below: often not because of adverbials, quotes, etc., and removing restriction does no harm
          // this next pattern used to assume no empty NPs. Corrected.  If you adjust this pattern, also adjust the corresponding one for attr!
          "SBARQ < (WHNP=target !< WRB !<# (/^NN/ < " + timeWordRegex + ")) <+(SQ|SINV|S|VP) (VP !< NP|TO !< (S < (VP < TO)) !< (/^(?:VB|AUX)/ < " + copularWordRegex + " $++ (VP < VBN|VBD)) !<- PRT !<- (PP <: IN) $-- (NP !< /^-NONE-$/))",
          // matches direct object in relative clauses "I saw the book that you bought"
          "SBAR < (WHNP=target !< WRB) < (S < NP < (VP !< SBAR !<+(VP) (PP <- IN) !< (S < (VP < TO))))",

          // matches direct object in relative clauses "I saw the book that you said you bought"
          "SBAR !< WHNP|WHADVP < (S < (@NP $++ (VP !$++ NP))) > (VP > (S < NP $- WHNP=target))",

          // matches direct object for long dependencies in relative clause without explicit relative pronouns
          "SBAR !< (WHPP|WHNP|WHADVP) < (S < (@NP $+ (VP !< (/^(?:VB|AUX)/ < " + copularWordRegex + " !$+ VP)  !<+(VP) (/^(?:VB|AUX)/ < " + copularWordRegex + " $+ (VP < VBN|VBD)) !<+(VP) NP !< SBAR !<+(VP) (PP <- IN)))) !$-- CC $-- NP > NP=target",
          "SBAR !< (WHPP|WHNP|WHADVP) < (S < (@NP $+ (ADVP $+ (VP !< (/^(?:VB|AUX)/ < " + copularWordRegex + " !$+ VP) !<+(VP) (/^(?:VB|AUX)/ < " + copularWordRegex + " $+ (VP < VBN|VBD)) !<+(VP) NP !< SBAR !<+(VP) (PP <- IN))))) !$-- CC $-- NP > NP=target"

          // we now don't match "VBG > PP $+ NP=target", since it seems better to CM to regard these quasi preposition uses (like "including soya") as prepositions rather than verbs with objects -- that's certainly what the phrase structure at least suggests in the PTB.  They're now matched as pobj
        });
  public static class DirectObjectGRAnnotation extends GrammaticalRelationAnnotation { }


  /**
   * The "indirect object" grammatical relation.  The indirect
   * object of a VP is the noun phrase which is the (dative) object
   * of the verb; the indirect object of a clause is the indirect
   * object of the VP which is the predicate of that clause.
   * <p/>
   * Example:  <br/>
   * "She gave me a raise" &rarr;
   * <code>iobj</code>(gave, me)
   */
  public static final GrammaticalRelation INDIRECT_OBJECT =
    new GrammaticalRelation(Language.English, "iobj", "indirect object",
        IndirectObjectGRAnnotation.class, OBJECT, "VP", tregexCompiler,
        new String[] {
          "VP < (NP=target !< /\\$/ !<# (/^NN/ < " + timeWordRegex + ") $+ (NP !<# (/^NN/ < " + timeWordRegex + ")))",
          // this next one was meant to fix common mistakes of our parser, but is perhaps too dangerous to keep
          "VP < (NP=target < (NP !< /\\$/ $++ (NP !< (/^NN/ < " + timeWordLotRegex + ")) !$ CC|CONJP !$ /^,$/ !$++ /^:$/))",
        });
  public static class IndirectObjectGRAnnotation extends GrammaticalRelationAnnotation { }


  /**
   * The "prepositional object" grammatical relation.  The object of a
   * preposition is the head of a noun phrase following the preposition, or
   * the adverbs "here" and "there".
   * (The preposition in turn may be modifying a noun, verb, etc.)
   * We here define cases of VBG quasi-prepositions like "including",
   * "concerning", etc. as instances of pobj (unlike the Penn Treebank).
   * <p/>
   * Example: <br/>
   * "I sat on the chair" &rarr;
   * <code>pobj</code>(on, chair)
   * <p/>
   * (The preposition can be called a FW for pace, versus, etc.  It can also
   * be called a CC - but we don't currently handle that and would need to
   * distinguish from conjoined PPs. Jan 2010 update: We now insist that the
   * NP must follow the preposition. This prevents a preceding NP measure
   * phrase being matched as a pobj.  We do allow a preposition tagged RB
   * followed by an NP pobj, as happens in the Penn Treebank for adverbial uses
   * of PP like "up 19%")
   */
  public static final GrammaticalRelation PREPOSITIONAL_OBJECT =
    new GrammaticalRelation(Language.English, "pobj", "prepositional object",
        PrepositionalObjectGRAnnotation.class, OBJECT, "PP(?:-TMP)?|WHPP|PRT|ADVP|WHADVP|XS", tregexCompiler,
        new String[] {
          "/^(?:PP(?:-TMP)?|(?:WH)?(?:PP|ADVP))$/ < (IN|VBG|TO|FW|RB|RBR $++ (/^(?:WH)?(?:NP|ADJP)(?:-TMP|-ADV)?$/=target !$- @NP))",
          // We allow ADVP with NP objects for cases like (ADVP earlier this year)
          "/^PP(?:-TMP)?$/ < (/^(?:IN|VBG|TO)$/ $+ (ADVP=target [ < (RB < /^(?i:here|there)$/) | < (ADVP < /^NP(?:-TMP)?$/) ] ))",
                // second disjunct is weird ADVP, only matches 1 tree in 2-21
          // to deal with preposition stranding in questions (e.g., "Which city do you live in?") -- the preposition is sometimes treated as a particle by the parser (works well but doesn't preserve the tree structure!)
          "PRT >- (VP !< (S < (VP < TO)) >+(SQ|SINV|S|VP) (SBARQ <, (WHNP=target !< WRB)) $-- (NP !< /^-NONE-$/))",
          "(PP <: IN|TO) >- (VP !< (S < (VP < TO)) >+(SQ|SINV|S|VP) (SBARQ <, (WHNP=target !< WRB)) $-- (NP !< /^-NONE-$/))",
          "(PP <: IN|TO) $- (NP $-- (VBZ|VBD) !$++ VP) >+(SQ) (SBARQ <, (WHNP=target !< WRB)) $-- (NP !< /^-NONE-$/)",

         // to deal with preposition stranding in relative clause (works well but doesn't preserve the tree structure!)
         "(PP <- IN|TO) >+(@VP|S|SINV|SBAR) (SBAR !< (WHPP|WHNP) < (S < (NP $+ (VP !<(/^(?:VB|AUX)/ < " + copularWordRegex + " !$+ VP) !<+(VP) NP !< SBAR ))) $-- NP > NP=target)",
         "XS|ADVP < (IN < /^(?i:at)$/) < JJS|DT=target", // at least, at most, at best, at worst, at all
         "PP < (CC < less) < NP"
        });
  public static class PrepositionalObjectGRAnnotation extends GrammaticalRelationAnnotation { }


  /**
   * The "prepositional complement" grammatical relation.
   * This is used when the complement of a preposition is a clause or
   * an adverbial or prepositional phrase.
   * The prepositional complement of
   * a preposition is the head of the sentence following the preposition,
   * or the preposition head of the PP.
   * <p/>
   * Examples: <br/>
   * "We have no useful information on whether users are at risk" &arr;
   * <code>pcomp</code>(on, are) <br/>
   * "They heard about you missing classes." &arr;
   * <code>pcomp</code>(about, missing) <br/>
   * It is warmer in Greece than in Italy &arr;
   * <code>pcomp</code>(than, in)
   */
  public static final GrammaticalRelation PREPOSITIONAL_COMPLEMENT =
    new GrammaticalRelation(Language.English, "pcomp", "prepositional complement",
        PrepositionalComplementGRAnnotation.class, COMPLEMENT, "PP(?:-TMP)?", tregexCompiler,
        new String[] {
          "@PP|WHPP < (IN|VBG|VBN|TO $+ @SBAR|S|PP|ADVP=target)", // no intervening NP; VBN is for "compared with"
          "@PP|WHPP < (RB $+ @SBAR|S=target)", // RB is for weird tagging like "after/RB adjusting for inflation"
          "@PP|WHPP !< IN|TO < (SBAR=target <, (IN $+ S))",
        });
  public static class PrepositionalComplementGRAnnotation extends GrammaticalRelationAnnotation { }


  /**
   * The "attributive" grammatical relation. The attributive is the complement of a
   * verb such as "to be, to seem, to appear".
   * <p>
   * These mainly occur in questions.  Arguably they shouldn't and we should treat the question
   * WHNP and WHADJP as predicates (as we do for ADJP and NP complements (NP-PRD and ADJP-PRD),
   * but we at present don't produce this.
   */
  public static final GrammaticalRelation ATTRIBUTIVE =
    new GrammaticalRelation(Language.English, "attr", "attributive",
        AttributiveGRAnnotation.class, COMPLEMENT, "VP|SBARQ|SQ", tregexCompiler,
        new String[] {
          "VP < NP=target <(/^(?:VB|AUX)/ < " + copularWordRegex + ") !$ (NP < EX)",
          // "What is that?"
          "SBARQ < (WHNP|WHADJP=target $+ (SQ < (/^(?:VB|AUX)/ < " + copularWordRegex + " !$++ VP) !< (VP <- (PP <:IN)) !<- (PP <: IN)))",
          "SBARQ <, (WHNP|WHADJP=target !< WRB) <+(SQ|SINV|S|VP) (VP !< (S < (VP < TO)) < (/^(?:VB|AUX)/ < " + copularWordRegex + " $++ (VP < VBN|VBD)) !<- PRT !<- (PP <: IN) $-- (NP !< /^-NONE-$/))",
          //"Is he the man?"
          "SQ <, (/^(?:VB|AUX)/ < " + copularWordRegex + ") < (NP=target $-- (NP !< EX))"
        });
  public static class AttributiveGRAnnotation extends GrammaticalRelationAnnotation { }


  /**
   * The "clausal complement" grammatical relation.  A clausal complement of
   * a verb or adjective is a dependent clause with an internal subject which
   * functions like an object of the verb, or adjective.  Clausal complements
   * for nouns are limited to complement clauses with a subset of nouns
   * like "fact" or "report".  We analyze them the same (parallel to the
   * analysis of this class as "content clauses" in Huddleston and Pullum 2002).
   * Clausal complements are usually finite (though there
   * are occasional exceptions including remnant English subjunctives, and we
   * also classify the complement of causative "have" (She had him arrested)
   * in this category.<p>
   * <p/>
   * Example: <br/>
   * "He says that you like to swim" &rarr;
   * <code>ccomp</code>(says, like) <br/>
   * "I am certain that he did it" &rarr;
   * <code>ccomp</code>(certain, did) <br/>
   * "I admire the fact that you are honest" &rarr;
   * <code>ccomp</code>(fact, honest)
   */
  public static final GrammaticalRelation CLAUSAL_COMPLEMENT =
    new GrammaticalRelation(Language.English, "ccomp", "clausal complement",
        ClausalComplementGRAnnotation.class, COMPLEMENT, "VP|SINV|S|ADJP|ADVP|NP", tregexCompiler,
        new String[] { // note if you add more words in the pattern, be sure to add them in the ADV_CLAUSE_MODIFIER too!
          "VP < (S=target < (VP !<, TO|VBG|VBN) !$-- NP)",
          "VP < (SBAR=target < (S <+(S) VP) <, (IN|DT < /^(?i:that|whether)$/))",
          "VP < (SBAR=target < (SBAR < (S <+(S) VP) <, (IN|DT < /^(?i:that|whether)$/)) < CC|CONJP)",
          "VP < (SBAR=target < (S < VP) !$-- NP !<, (IN|WHADVP))",
          "VP < (SBAR=target < (S < VP) !$-- NP <, (WHADVP < (WRB < /^(?i:how)$/)))",
          "VP < (/^VB/ < " + haveRegex + ") < (S=target < @NP < VP)",
          // to find "...", he said or "...?" he asked.
          "S|SINV < (S|SBARQ=target $+ /^(,|\\.|'')$/ !$- /^(?:CC|:)$/ !< (VP < TO|VBG|VBN))",
          "ADVP < (SBAR=target < (IN < /^(?i:as|that)/) < (S < (VP !< TO)))", // ADVP is things like "As long as they spend ..."
          "ADJP < (SBAR=target !< (IN < as) < S)", // ADJP is things like "sure (that) he'll lose" or for/to ones or object of comparison with than "than we were led to expect"; Leave aside as in "as clever as we thought.
          // That ... he know
          "S <, (SBAR=target <, (IN < /^(?i:that|whether)$/) !$+ VP)",
          // JJ catches a couple of funny NPs with heads like "enough"
          // Note that we eliminate SBAR which also match an infmod pattern
          "@NP < JJ|NN|NNS < (SBAR=target [ !<(S < (VP < TO )) | !$-- NP|NN|NNP|NNS ] )"
        });
  public static class ClausalComplementGRAnnotation extends GrammaticalRelationAnnotation { }


  /**
   * An open clausal complement (<i>xcomp</i>) of a VP or an ADJP is a clausal
   * complement without its own subject, whose reference is determined by an
   * external subject.  These complements are always non-finite.
   * The name <i>xcomp</i> is borrowed from Lexical-Functional Grammar.
   * (Mainly "TO-clause" are recognized, but also some VBG like "stop eating")
   * <p/>
   * <p/>
   * Examples: <br/>
   * "I like to swim" &rarr;
   * <code>xcomp</code>(like, swim) <br/>
   * "I am ready to leave" &rarr;
   * <code>xcomp</code>(ready, leave)
   */
  public static final GrammaticalRelation XCLAUSAL_COMPLEMENT =
    new GrammaticalRelation(Language.English, "xcomp", "xclausal complement",
        XClausalComplementGRAnnotation.class, COMPLEMENT, "VP|ADJP", tregexCompiler,
        new String[] {
          // basic VP complement xcomp; this used to exclude embedding under a VP headed by be, as some are purpose clauses, but it seems like the vast majority aren't so I've removed that restriction
          "VP < (S=target !$- (NN < order) < (VP < TO))",    // used to have !> (VP < (VB|AUX < be))
          "ADJP < (S=target <, (VP <, TO))",
          "VP < (S=target !$- (NN < order) < (NP $+ NP|ADJP))",
          // to find "help sustain ...
          "VP < (/^(?:VB|AUX)/ $+ (VP=target < VB < NP))",
          "VP < (SBAR=target < (S !$- (NN < order) < (VP < TO))) !> (VP < (VB|AUX < be)) ",
          "VP < (S=target !$- (NN < order) <: NP) > VP",
          // stop eating
          // note that we eliminate parentheticals and clauses that could match a partmod
          "(VP < (S=target < (VP < VBG ) !< NP !$- (/^,$/ [$- @NP  |$- (@PP $-- @NP ) |$- (@ADVP $-- @NP)]) !$-- /^:$/))",
        });
  public static class XClausalComplementGRAnnotation extends GrammaticalRelationAnnotation { }


  /**
   * The "relative" grammatical relation.  A
   * relative of a relative clause is the head word of the WH-phrase
   * introducing it.
   * <p/>
   * <p/>
   * Examples: <br/>
   * "I saw the man that you love" &rarr;
   * <code>rel</code>(love, that) <br/>
   * "I saw the man whose wife you love" &rarr;
   * <code>rel</code>(love, wife) <br/>
   * <p/>
   * Note that this is designed to *not* match cases when there is no overt
   * subject NP.  They are instead matched by the nsubj rule.  Effectively
   * this gives us an HPSG-like relative clause analysis, where subject
   * relatives are analyzed as regular subject structures.  And "why" (WHADVP)
   * cases are treated as advmod not as rel.
   */
  public static final GrammaticalRelation RELATIVE =
    new GrammaticalRelation(Language.English, "rel", "relative",
        RelativeGRAnnotation.class, COMPLEMENT, "SBAR", tregexCompiler,
        new String[] {
          "SBAR <, WHNP|WHPP|WHADJP=target > /^NP/ [ !<, /^WHNP/ | < (S < (VP $-- (/^NP/ !< /^-NONE-$/)))]"
        });
  public static class RelativeGRAnnotation extends GrammaticalRelationAnnotation { }


  /**
   * The "referent" grammatical relation.  A
   * referent of the Wh-word of a NP is  the relative word introducing the relative clause modifying the NP.
   * <p/>
   * Example: <br/>
   * "I saw the book which you bought" &rarr;
   * <code>ref</code>(book, which) <br/>
   * "I saw the book the cover of which you designed" &rarr;
   * <code>ref</code>(book, which)
   */
  public static final GrammaticalRelation REFERENT =
    new GrammaticalRelation(Language.English, "ref", "referent",
        ReferentGRAnnotation.class, DEPENDENT);
  public static class ReferentGRAnnotation extends GrammaticalRelationAnnotation { }



  /**
   * The "expletive" grammatical relation.
   * This relation captures an existential there.
   * <p/>
   * <p/>
   * Example: <br/>
   * "There is a statue in the corner" &rarr;
   * <code>expl</code>(is, there)
   */
  public static final GrammaticalRelation EXPLETIVE =
    new GrammaticalRelation(Language.English, "expl", "expletive",
        ExpletiveGRAnnotation.class, DEPENDENT, "S|SQ|SINV", tregexCompiler,
        new String[] {
          "S|SQ|SINV < (NP=target <+(NP) EX)"
        });
  public static class ExpletiveGRAnnotation extends GrammaticalRelationAnnotation { }


  /**
   * The "adjectival complement" grammatical relation.  An
   * adjectival complement of a VP is an adjectival phrase which
   * functions as the complement (like an object of the verb); an adjectival
   * complement of a clause is the adjectival complement of the VP which is
   * the predicate of that clause.<p>
   * <p/>
   * Example: <br/>
   * "She looks very beautiful" &rarr;
   * <code>acomp</code>(looks, beautiful)
   */
  public static final GrammaticalRelation ADJECTIVAL_COMPLEMENT =
    new GrammaticalRelation(Language.English, "acomp", "adjectival complement",
        AdjectivalComplementGRAnnotation.class, COMPLEMENT, "VP", tregexCompiler,
        new String[] {
          "VP [ < (ADJP=target !$-- NP) |  < (/^VB/ $+ (@S=target < (@ADJP < /^JJ/ ! $-- @NP|S))) ]",
        });
  public static class AdjectivalComplementGRAnnotation extends GrammaticalRelationAnnotation { }


  /**
   * The "modifier" grammatical relation.  A modifier of a VP is
   * any constituent that serves to modify the meaning of the VP
   * (but is not an <code>ARGUMENT</code> of that
   * VP); a modifier of a clause is an modifier of the VP which is
   * the predicate of that clause.<p>
   * <p/>
   * Examples: <br/>
   * "Last night, I swam in the pool" &rarr;
   * <code>mod</code>(swam, in the pool),
   * <code>mod</code>(swam, last night)
   */
  public static final GrammaticalRelation MODIFIER =
    new GrammaticalRelation(Language.English, "mod", "modifier",
        ModifierGRAnnotation.class, DEPENDENT);
  public static class ModifierGRAnnotation extends GrammaticalRelationAnnotation { }


  /**
   * The "adverbial clause modifier" grammatical relation.  An adverbial clause
   * modifier of a VP or (inverted) sentence is a clause modifying the verb (temporal clauses,
   * consequences, conditional clauses, etc.).
   * <p/>
   * Examples: <br/>
   * "The accident happened as the night was falling" &rarr;
   * <code>advcl</code>(happened, falling) <br/>
   * "If you know who did it, you should tell the teacher" &rarr;
   * <code>advcl</code>(tell, know)
   */
  public static final GrammaticalRelation ADV_CLAUSE_MODIFIER =
    new GrammaticalRelation(Language.English, "advcl", "adverbial clause modifier",
        AdvClauseModifierGRAnnotation.class, MODIFIER, "VP|S|SQ|SINV|SBARQ", tregexCompiler,
        new String[] {
          // second disjunct matches inverted "had he investigated" cases, 3rd case is "so that" purpose clauses, first case includes regular in order to purpose clauses
          "VP < (@SBAR=target [ < (IN !< /^(?i:that|whether)$/) | <: (SINV <1 /^(?:VB|MD|AUX)/) | < (IN < that) < (RB|IN < so) ] )",
          "S|SQ|SINV < (SBAR|SBAR-TMP=target <, (IN !< /^(?i:that|whether)$/ !$+ (NN < order)) !$-- /^(?!CC|CONJP|``|,|INTJ|PP(-.*)?).*$/ !$+ VP)",
          // to get "rather than"
          "S|SQ|SINV < (SBAR|SBAR-TMP=target <2 (IN !< /^(?i:that|whether)$/ !$+ (NN < order)) !$-- /^(?!CC|CONJP|``|,|INTJ|PP(-.*)?$).*$/)",
          "S|SQ|SINV < (SBAR|SBAR-TMP=target <, (IN !< /^(?i:that|whether)$/ !$+ (NN < order)) !$+ @VP $+ /^,$/ $++ @NP)", // this one might just be better, but at any rate license one with quotation marks or a conjunction beforehand
          "SBARQ < (SBAR|SBAR-TMP|SBAR-ADV=target <, (IN !< /^(?i:that|whether)$/ !$+ (NN < order)) $+ /^,$/ $++ @SQ|S|SBARQ)", // the last part should probably only be @SQ, but this captures some strays at no cost
          "VP < (SBAR|SBAR-TMP=target <, (WHADVP|WHNP < (WRB !< /^(?i:how)$/)) !< (S < (VP < TO)))", // added the (S < (VP <TO)) part so that "I tell them how to do so" doesn't get a wrong advcl
          "S|SQ < (SBAR|SBAR-TMP=target <, (WHADVP|WHNP < (WRB !< /^(?i:how)$/)) !< (S < (VP < TO)))",
          // "S|SQ < (PP=target <, RB < @S)", // caught as prep and pcomp.
          "@S < (@SBAR=target $++ @NP $++ @VP)",  // fronted adverbial clause
          "@S < (@S=target < (VP < TO) $+ (/^,$/ $++ @NP))", // part of former purpcl: This is fronted infinitives: "To find out why, we went to ..."
          // "VP > (VP < (VB|AUX < be)) < (S=target !$- /^,$/ < (VP < TO|VBG) !$-- NP)", // part of former purpcl [cdm 2010: this pattern was added by me in 2006, but it is just bad!]

        });
  public static class AdvClauseModifierGRAnnotation extends GrammaticalRelationAnnotation { }


  /*
   * The "purpose clause modifier" grammatical relation has been discontinued
   * It is now just seen as a special case of an advcl.  A purpose clause
   * modifier of a VP is a clause headed by "(in order) to" specifying a
   * purpose.  Note: at present we only recognize ones that have
   * "in order to" or are fronted.  Otherwise we can't use our surface representations to
   * distinguish these from xcomp's. We can also recognize "to" clauses
   * introduced by "be VBN".
   * <p/>
   * Example: <br/>
   * "He talked to the president in order to secure the account" &rarr;
   * <code>purpcl</code>(talked, secure)
   */


  /**
   * The "relative clause modifier" grammatical relation.  A relative clause
   * modifier of an NP is a relative clause modifying the NP.  The link
   * points from the head noun of the NP to the head of the relative clause,
   * normally a verb.
   * <p/>
   * <p/>
   * Examples: <br/>
   * "I saw the man you love" &rarr;
   * <code>rcmod</code>(man, love)  <br/>
   * "I saw the book which you bought" &rarr;
   * <code>rcmod</code>(book, bought)
   */
  public static final GrammaticalRelation RELATIVE_CLAUSE_MODIFIER =
    new GrammaticalRelation(Language.English, "rcmod", "relative clause modifier",
        RelativeClauseModifierGRAnnotation.class, MODIFIER, "(?:WH)?NP|NML|ADVP", tregexCompiler,
        new String[] {
          // Each of the following expressions includes a section
          // which makes sure it does not have a left sister
          // equivalent to the current node.  The reason for this is
          // to make sure you do not get two neighboring nodes both
          // labeled as rcmod to the same sbar expression.  For
          // example, this prevents rcmod(34, works) in a sentence
          // such as "John Bauer, 34, who works at Stanford..."
          // It does also prevent rcmods in potentially useful
          // situations, such as "John Bauer, programmer, who works at
          // Stanford..."  However, it seems better to eliminate some
          // useful dependencies rather than introduce some wrote
          // dependencies.
          "NP|WHNP|NML $++ (SBAR=target <+(SBAR) WHPP|WHNP) !$-- NP|WHNP|NML > @NP|WHNP",
          "NP|WHNP|NML $++ (SBAR=target <: (S !<, (VP <, TO))) !$-- NP|WHNP|NLP > @NP|WHNP",
          // this next pattern is restricted to where and why because
          // "when" is usually incorrectly parsed: temporal clauses
          // are put inside the NP; 2nd is for case of relative
          // clauses with no relativizer (it doesn't distinguish
          // whether actually gapped).
          "NP|NML $++ (SBAR=target < (WHADVP < (WRB </^(?i:where|why|when)/))) !$-- NP|NML > @NP",
          "NP|WHNP|NML $++ RRC=target !$-- NP|WHNP|NML",
          "@ADVP < (@ADVP < (RB < /where$/)) < @SBAR=target",
        });
  public static class RelativeClauseModifierGRAnnotation extends GrammaticalRelationAnnotation { }


 /*
 	* The "complementizer" grammatical relation is a discontinued grammatical relation. A
 	* A complementizer of a clausal complement was the word introducing it.
  * It only matched "that" or "whether". We've now merged this in with "mark" which plays a similar
  * role with other clausal modifiers.
  * <p/>
 	* <p/>
  * Example: <br/>
  * "He says that you like to swim" &rarr;
  * <code>complm</code>(like, that)
 	*/


  /**
   * The "marker" grammatical relation.  A marker is the word introducing a finite clause subordinate to another clause.
   * For a complement clause, this will typically be "that" or "whether".
   * For an adverbial clause, the marker is typically a preposition like "while" or "although".
   * <p/>
   * Example: <br/>
   * "U.S. forces have been engaged in intense fighting after insurgents launched simultaneous attacks" &rarr;
   * <code>mark</code>(launched, after)
   */
  public static final GrammaticalRelation MARKER =
    new GrammaticalRelation(Language.English, "mark", "marker",
        MarkerGRAnnotation.class, MODIFIER, "SBAR(?:-TMP)?", tregexCompiler,
        new String[] {
          "SBAR|SBAR-TMP < (IN|DT=target $++ S|FRAG)",
     	    "SBAR < (IN|DT=target < that|whether) [ $-- /^(?:VB|AUX)/ | $- NP|NN|NNS | > ADJP|PP | > (@NP|UCP|SBAR < CC|CONJP $-- /^(?:VB|AUX)/) ]",
        });
  public static class MarkerGRAnnotation extends GrammaticalRelationAnnotation { }


  /**
   * The "adjectival modifier" grammatical relation.  An adjectival
   * modifier of an NP is any adjectival phrase that serves to modify
   * the meaning of the NP.<p>
   * <p/>
   * Example: <br/>
   * "Sam eats red meat" &rarr;
   * <code>amod</code>(meat, red) <p/>
   * The relation amod is also used for multiword country adjectives, despite their
   * questionable treebank representation.
   * <p/>
   * Example: <br/>
   * "the West German economy" &rarr;
   * <code>amod</code>(German, West),
   * <code>amod</code>(economy, German)
   */
  public static final GrammaticalRelation ADJECTIVAL_MODIFIER =
    new GrammaticalRelation(Language.English, "amod", "adjectival modifier",
        AdjectivalModifierGRAnnotation.class, MODIFIER, "NP(?:-TMP|-ADV)?|NX|NML|NAC|WHNP|ADJP", tregexCompiler,
        new String[] {
          "/^(?:NP(?:-TMP|-ADV)?|NX|NML|NAC|WHNP)$/ < (ADJP|WHADJP|JJ|JJR|JJS|JJP|VBN|VBG|VBD|IN=target !< QP !$- CC)",
                // IN above is needed for "next" in "next week" etc., which is often tagged IN.
          "ADJP !< CC|CONJP < (JJ|NNP $ JJ|NNP=target)",
          // Cover the case of "John, 34, works at Stanford" - similar to an expression for appos
          "WHNP|WHNP-TMP|WHNP-ADV|NP|NP-TMP|NP-ADV < (NP=target <: CD $- /^,$/ $-- /^(?:WH)?NP/ !$ CC|CONJP)",
        });
  public static class AdjectivalModifierGRAnnotation extends GrammaticalRelationAnnotation { }


  /**
   * The "numeric modifier" grammatical relation.  A numeric
   * modifier of an NP is any number phrase that serves to modify
   * the meaning of the NP.<p>
   * <p/>
   * Example: <br/>
   * "Sam eats 3 sheep" &rarr;
   * <code>num</code>(sheep, 3)
   */
  public static final GrammaticalRelation NUMERIC_MODIFIER =
    new GrammaticalRelation(Language.English, "num", "numeric modifier",
        NumericModifierGRAnnotation.class, MODIFIER, "(?:WH)?NP(?:-TMP|-ADV)?|NML|NX", tregexCompiler,
        new String[] {
          "/^(?:WH)?(?:NP|NX|NML)(?:-TMP|-ADV)?$/ < (CD|QP=target !$- CC)",
          "/^(?:WH)?(?:NP|NX|NML)(?:-TMP|-ADV)?$/ < (ADJP=target <: QP)"
        });
  public static class NumericModifierGRAnnotation extends GrammaticalRelationAnnotation { }


  /**
   * The "compound number modifier" grammatical relation.  A compound number
   * modifier is a part of a number phrase or currency amount.
   * <p/>
   * Example: <br/>
   * "I lost $ 3.2 billion" &rarr;
   * <code>number</code>($, billion)
   */
  public static final GrammaticalRelation NUMBER_MODIFIER =
    new GrammaticalRelation(Language.English, "number", "compound number modifier",
        NumberModifierGRAnnotation.class, MODIFIER, "QP|ADJP", tregexCompiler,
        new String[] {
          "QP|ADJP < (/^(?:CD|$|#)$/=target !$- CC)"
        });
  public static class NumberModifierGRAnnotation extends GrammaticalRelationAnnotation { }


  /**
   * The "quantifier phrase modifier" grammatical relation.  A quantifier
   * modifier is an element modifying the head of a QP constituent.
   * <p/>
   * Example: <br/>
   * "About 200 people came to the party" &rarr;
   * <code>quantmod</code>(200, About)
   */
  public static final GrammaticalRelation QUANTIFIER_MODIFIER =
    new GrammaticalRelation(Language.English, "quantmod", "quantifier modifier",
        QuantifierModifierGRAnnotation.class, MODIFIER, "QP", tregexCompiler,
        new String[] {
          "QP < IN|RB|RBR|RBS|PDT|DT|JJ|JJR|JJS|XS=target"
        });
  public static class QuantifierModifierGRAnnotation extends GrammaticalRelationAnnotation { }


  /**
   * The "noun compound modifier" grammatical relation.  A noun compound
   * modifier of an NP is any noun that serves to modify the head noun.
   * Note that this has all nouns modify the rightmost a la Penn headship
   * rules.  There is no intelligent noun compound analysis.
   * <p/>
   * We eliminate nouns that are detected as part of a POS, since that
   * will turn into the dependencies denoting possession instead.
   * Note we have to include (VBZ &lt; /^\'s$/) as part of the POS
   * elimination, since quite a lot of text such as
   * "yesterday's widely published sequester" was misannotated as a
   * VBZ instead of a POS.  TODO: remove that if a revised PTB is ever
   * released.
   * <p/>
   * Example: <br/>
   * "Oil price futures" &rarr;
   * <code>nn</code>(futures, oil),
   * <code>nn</code>(futures, price) <p/>
   */
  public static final GrammaticalRelation NOUN_COMPOUND_MODIFIER =
    new GrammaticalRelation(Language.English, "nn", "nn modifier",
        NounCompoundModifierGRAnnotation.class, MODIFIER, "(?:WH)?(?:NP|NX|NAC|NML|ADVP|ADJP)(?:-TMP|-ADV)?", tregexCompiler,
        new String[] {
          // added AFX: can't really tell it's natural POS; this seems the best one can do
          "/^(?:WH)?(?:NP|NX|NAC|NML)(?:-TMP|-ADV)?$/ < (NP|NML|NN|NNS|NNP|NNPS|FW|AFX=target $++ NN|NNS|NNP|NNPS|FW|CD !<<- POS !<<- (VBZ < /^\'s$/) !$- /^,$/ )",
          "/^(?:WH)?(?:NP|NX|NAC|NML)(?:-TMP|-ADV)?$/ < JJ|JJR|JJS=sister < (NP|NML|NN|NNS|NNP|NNPS|FW=target !<<- POS !<<- (VBZ < /^\'s$/) $+ =sister) <# NN|NNS|NNP|NNPS !<<- POS !<<- (VBZ < /^\'s$/) ",
          "ADJP|ADVP < (FW [ $- FW=target | $- (IN=target < in|In) ] )",  // in vitro, in vivo, etc., in Genia
        });
  public static class NounCompoundModifierGRAnnotation extends GrammaticalRelationAnnotation { }

  /*
   * There used to be a relation "abbrev" for when abbreviations were defined in brackets after a noun
   * phrase, like "the Australian Broadcasting Corporation (ABC)", but it has now been disbanded, and
   * subsumed under appos.
   */

  /**
   * The "appositional modifier" grammatical relation.  An appositional
   * modifier of an NP is an NP that serves to modify
   * the meaning of the NP.  It includes parenthesized examples, as well as defining abbreviations.
   * <p/>
   * Examples: <br/>
   * "Sam, my brother, eats red meat" &rarr;
   * <code>appos</code>(Sam, brother) <br/>
   * "Bill (John's cousin)" &rarr; <code>appos</code>(Bill, cousin).
   *
   * "The Australian Broadcasting Corporation (ABC)" &rarr;
 	*  <code>appos</code>(Corporation, ABC)
   */
  public static final GrammaticalRelation APPOSITIONAL_MODIFIER =
    new GrammaticalRelation(Language.English, "appos", "appositional modifier",
        AppositionalModifierGRAnnotation.class, MODIFIER, "(?:WH)?NP(?:-TMP|-ADV)?", tregexCompiler,
        new String[] {
          // Note that we disallow a single CD node as the child of
          // the NP.  This eliminates numbers being used as ages,
          // which is the normal case for such a pattern.
          "WHNP|WHNP-TMP|WHNP-ADV|NP|NP-TMP|NP-ADV < (NP=target !<: CD $- /^,$/ $-- /^(?:WH)?NP/ !$ CC|CONJP)",
          "WHNP|WHNP-TMP|WHNP-ADV|NP|NP-TMP|NP-ADV < (PRN=target < (NP < /^(?:NN|CD)/ $-- /^-LRB-$/ $+ /^-RRB-$/))",
          "@WHNP|NP < (@NP=target !<: CD <, /^-LRB-$/ <` /^-RRB-$/ $-- /^(?:WH)?NP/ !$ CC|CONJP)",
          // TODO: next pattern with NNP doesn't work because leftmost NNP is deemed head in a
          // structure like (NP (NNP Norway) (, ,) (NNP Verdens_Gang) (, ,))
          "NP|NP-TMP|NP-ADV < (NNP $+ (/^,$/ $+ NNP=target)) !< CC|CONJP",
         // find abbreviations
         // for biomedical English, the former NNP heuristic really doesn't work, because they use NN for all chemical entities
 	       // while not unfoolable, this version produces less false positives and more true positives.
         "WHNP|WHNP-TMP|WHNP-ADV|NP|NP-TMP|NP-ADV < (PRN=target <, /^-LRB-$/ <- /^-RRB-$/ !<< /^(?:POS|(?:WP|PRP)\\$|[,$#]|CC|RB|CD)$/ <+(NP) (NNP|NN < /^(?:[A-Z]\\.?){2,}/) )"


        });
  public static class AppositionalModifierGRAnnotation extends GrammaticalRelationAnnotation { }


  /**
   * The "discourse element" grammatical relation. This is used for interjections and
   * other discourse particles and elements (which are not clearly linked to the structure
   * of the sentence, except in an expressive way). We generally follow the
   * guidelines of what the Penn Treebanks count as an INTJ.  They
   * define this to include: interjections (oh, uh-huh, Welcome), fillers (um, ah),
   * and discourse markers (well, like, actually, but not: you know).
   * We also use it for emoticons.
   */
   public static final GrammaticalRelation DISCOURSE_ELEMENT =
    new GrammaticalRelation(Language.English, "discourse", "discourse element",
        DiscourseElementGRAnnotation.class, MODIFIER, ".*", tregexCompiler,
        new String[] {
                // smiley faces (escaped), based on Chris Potts' sentiment tutorial
                "__ < (NFP=target < /^(?:[<>]?[:;=8][\\-o\\*']?(?:-RRB-|-LRB-|[DPdpO\\/\\\\\\:}{@\\|\\[\\]])|(?:-RRB-|-LRB-|[DPdpO\\/\\\\\\:}{@\\|\\[\\]])[\\-o\\*']?[:;=8][<>]?)$/)",
                // and some simple Asian ones
                "__ < (NFP=target < /^(?:-LRB-)?[\\-\\^x=~<>'][_.]?[\\-\\^x=~<>'](?:-RRB-)?$/)",
                "__ [ < INTJ=target | < (PRN=target <1 /^(?:,|-LRB-)$/ <2 INTJ [ !<3 __ | <3 /^(?:,|-RRB-)$/ ] ) ]"

        });
  public static class DiscourseElementGRAnnotation extends GrammaticalRelationAnnotation { }



  /**
   * The "participial modifier" grammatical relation.  A participial
   * modifier of an NP, VP, or S is a VP[part] that serves to modify
   * the meaning of the NP or VP.<p>
   * <p/>
   * Examples: <br/>
   * "truffles picked during the spring are tasty" &rarr;
   * <code>partmod</code>(truffles, picked) <br/>
   * "Bill picked Fred for the team demonstrating his incompetence" &rarr;
   * <code>partmod</code>(picked, demonstrating)
   */
  public static final GrammaticalRelation PARTICIPIAL_MODIFIER =
    new GrammaticalRelation(Language.English, "partmod", "participial modifier",
        ParticipialModifierGRAnnotation.class, MODIFIER, "(?:WH)?NP(?:-TMP|-ADV)?|VP|S|SINV", tregexCompiler,
        new String[] {
          "WHNP|WHNP-TMP|WHNP-ADV|NP|NP-TMP|NP-ADV < (VP=target < VBG|VBN|VBD $-- NP)",  // also allow VBD since it quite often occurs in treebank errors and parse errors
          // to get "MBUSA, headquartered ..."
          "WHNP|WHNP-TMP|WHNP-ADV|NP|NP-TMP|NP-ADV < (/^,$/ $+ (VP=target <, VBG|VBN))",
          // to get "John, knowing ..., announced "
          "S|SINV < (S=target < (VP <, VBG|VBN) [ $- (/^,$/ [ $- @NP | $- (@PP $ @NP) ] ) | $+ (/^,$/ $+ @NP) ] )",
          "(VP < (@S=target < (VP <1 VBG|VBN )$- (/^,$/ [$- @NP  |$- (@PP $-- @NP ) |$- (@ADVP $-- @NP)])))",
          // We could use something like this keying off -ADV annotation, but not yet operational, as we don't keep S-ADV, only NP-ADV
          // "VP < (/^S-ADV$/=target < (VP <, VBG|VBN) )",
        });
  public static class ParticipialModifierGRAnnotation extends GrammaticalRelationAnnotation { }


  /**
   * The "infinitival modifier" grammatical relation.  An infinitival
   * modifier of an NP is an S/VP that serves to modify
   * the meaning of the NP.<p>
   * <p/>
   * Example: <br/>
   * "points to establish are ..." &rarr;
   * <code>infmod</code>(points, establish)
   */
  public static final GrammaticalRelation INFINITIVAL_MODIFIER =
    new GrammaticalRelation(Language.English, "infmod", "infinitival modifier",
        InfinitivalModifierGRAnnotation.class, MODIFIER, "NP(?:-TMP|-ADV)?", tregexCompiler,
        new String[] {
          "/^NP(?:-[A-Z]+)?$/ < (S=target < (VP < TO) $-- NP|NN|NNP|NNS)",
          "/^NP(?:-[A-Z]+)?$/ < (SBAR=target < (S < (VP < TO)) $-- NP|NN|NNP|NNS)"
        });
  public static class InfinitivalModifierGRAnnotation extends GrammaticalRelationAnnotation { }

  // match "not", "n't", "nt" (for informal writing), or "never" as _complete_ string
  private static final String NOT_PAT = "/^(?i:n[o']?t|never)$/";

  /**
   * The "adverbial modifier" grammatical relation.  An adverbial
   * modifier of a word is a (non-clausal) RB or ADVP that serves to modify
   * the meaning of the word.<p>
   * <p/>
   * Examples: <br/>
   * "genetically modified food" &rarr;
   * <code>advmod</code>(modified, genetically) <br/>
   * "less often" &rarr;
   * <code>advmod</code>(often, less)
   */
  public static final GrammaticalRelation ADVERBIAL_MODIFIER =
    new GrammaticalRelation(Language.English, "advmod", "adverbial modifier",
        AdverbialModifierGRAnnotation.class, MODIFIER,
        "VP|ADJP|WHADJP|ADVP|WHADVP|S|SBAR|SINV|SQ|SBARQ|XS|(?:WH)?(?:PP|NP)(?:-TMP|-ADV)?|RRC|CONJP|JJP", tregexCompiler,
        new String[] {
          "/^(?:VP|ADJP|JJP|WHADJP|SQ?|SBARQ?|SINV|XS|RRC|(?:WH)?NP(?:-TMP|-ADV)?)$/ < (RB|RBR|RBS|WRB|ADVP|WHADVP=target !< " + NOT_PAT + ")",
          // avoids adverb conjunctions matching as advmod; added JJ to catch How long
          "ADVP|WHADVP < (RB|RBR|RBS|WRB|ADVP|WHADVP|JJ=target !< " + NOT_PAT + ") !< CC !< CONJP",
          //this one gets "at least" advmod(at, least) or "fewer than" advmod(than, fewer)
          "SBAR < (WHNP=target < WRB)", "SBARQ <, WHADVP=target", "XS < JJ=target",
          // for PP, only ones before head, or after NP, since others afterwards are pcomp
          "/(?:WH)?PP(?:-TMP|-ADV)?$/ <# (__ $-- (RB|RBR|RBS|WRB|ADVP|WHADVP=target !< " + NOT_PAT + "))",
          "/(?:WH)?PP(?:-TMP|-ADV)?$/ < @NP|WHNP < (RB|RBR|RBS|WRB|ADVP|WHADVP=target !< " + NOT_PAT + ")",
          "CONJP < (RB=target !< " + NOT_PAT + ")",
        });
  public static class AdverbialModifierGRAnnotation extends GrammaticalRelationAnnotation { }


  /**
   * The "negation modifier" grammatical relation.  The negation modifier
   * is the relation between a negation word and the word it modifies.
   * <p/>
   * Examples: <br/>
   * "Bill is not a scientist" &rarr;
   * <code>neg</code>(scientist, not) <br/>
   * "Bill doesn't drive" &rarr;
   * <code>neg</code>(drive, n't)
   */
  public static final GrammaticalRelation NEGATION_MODIFIER =
    new GrammaticalRelation(Language.English, "neg", "negation modifier",
        NegationModifierGRAnnotation.class, ADVERBIAL_MODIFIER,
        "VP|ADJP|S|SBAR|SINV|SQ|NP(?:-TMP|-ADV)?|FRAG|CONJP|PP", tregexCompiler,
        new String[] {
          "/^(?:VP|NP(?:-TMP|-ADV)?|ADJP|SQ|S|FRAG|CONJP|PP)$/< (RB=target < " + NOT_PAT + ")",
          "VP|ADJP|S|SBAR|SINV|FRAG < (ADVP=target <# (RB < " + NOT_PAT + "))",
          "VP > SQ $-- (RB=target < " + NOT_PAT + ")"
        });
  public static class NegationModifierGRAnnotation extends GrammaticalRelationAnnotation { }


  /**
   * The "noun phrase as adverbial modifier" grammatical relation.
   * This relation captures various places where something syntactically a noun
   * phrase is used as an adverbial modifier in a sentence.  These usages include:
   * <ul>
   * <li> A measure phrase, which is the relation between
   * the head of an ADJP/ADVP and the head of a measure-phrase modifying the ADJP/ADVP.
   * <p/>
   * Example: <br/>
   * "The director is 65 years old" &rarr;
   * <code>npadvmod</code>(old, years)
   * </li>
   * <li> Noun phrases giving extent inside a VP which are not objects
   * <p/>
   * Example: <br/>
   * "Shares eased a fraction" &rarr;
   * <code>npadvmod</code>(eased, fraction)
   * </li>
   * <li> Financial constructions involving an adverbial or PP-like NP, notably
   * the following construction where the NP means "per share"
   * <p/>
   * Example: <br/>
   * "IBM earned $ 5 a share" &rarr;
   * <code>npadvmod</code>($, share)
   * </li>
   * <li>Reflexives
   * <p/>
   * Example: <br/>
   * "The silence is itself significant" &rarr;
   * <code>npadvmod</code>(significant, itself)
   * </li>
   * <li>Certain other absolutive NP constructions.
   * <p/>
   * Example: <br/>
   * "90% of Australians like him, the most of any country" &rarr;
   * <code>npadvmod</code>(like, most)
   * </ul>
   * A temporal modifier (tmod) is a subclass of npadvmod which is distinguished
   * as a separate relation.
   */
  public static final GrammaticalRelation NP_ADVERBIAL_MODIFIER =
    new GrammaticalRelation(Language.English, "npadvmod", "noun phrase adverbial modifier",
        NpAdverbialModifierGRAnnotation.class, MODIFIER, "VP|(?:WH)?(?:NP|ADJP|ADVP|PP)(?:-TMP|-ADV)?", tregexCompiler,
        new String[] {
          // measure phrases pattern (don't allow VBG/VBN cases, as often participles)
          "@ADVP|ADJP|WHADJP|WHADVP|PP|WHPP <# (JJ|JJR|IN|RB|RBR !< notwithstanding $- (@NP=target !< NNP|NNPS))",
          // one word nouns like "cost efficient", "ice-free"
          "@ADJP < (NN=target $++ /^JJ/) !< CC|CONJP",
          "@NP|WHNP < /^NP-ADV/=target",
          // this next one is for weird financial listings: 4.7% three months
          "@NP <1 (@NP <<# /^%$/) <2 (@NP=target <<# days|month|months) !<3 __",
          "@VP < /^NP-ADV/=target",
        });
  public static class NpAdverbialModifierGRAnnotation extends GrammaticalRelationAnnotation { }


  /**
   * The "temporal modifier" grammatical relation.  A temporal
   * modifier of a VP or an ADJP is any constituent that serves to modify the
   * meaning of the VP or the ADJP by specifying a time; a temporal modifier of a
   * clause is an temporal modifier of the VP which is the
   * predicate of that clause.<p>
   * <p/>
   * Example: <br/>
   * "Last night, I swam in the pool" &rarr;
   * <code>tmod</code>(swam, night)
   */
  public static final GrammaticalRelation TEMPORAL_MODIFIER =
    new GrammaticalRelation(Language.English, "tmod", "temporal modifier",
        TemporalModifierGRAnnotation.class, NP_ADVERBIAL_MODIFIER, "VP|S|ADJP|PP|SBAR|SBARQ|NP", tregexCompiler,
        new String[] {
          "VP|ADJP < NP-TMP=target",
          "VP|ADJP < (NP=target <# (/^NN/ < " + timeWordRegex + ") !$+ (/^JJ/ < old))",
          // CDM Jan 2010: For constructions like "during the same period last year"
          "@PP < (IN|TO|VBG|FW $++ (@NP $+ NP-TMP=target))",
          "@PP < (IN|TO|VBG|FW $++ (@NP $+ (NP=target <# (/^NN/ < " + timeWordRegex + "))))",
          "S < (NP-TMP=target $++ VP [ $++ NP | $-- NP] )",
          "S < (NP=target <# (/^NN/ < " + timeWordRegex + ") $++ (NP $++ VP))",
          // matches when relative clauses as temporal modifiers of verbs!
          "SBAR < (@WHADVP < (WRB < when)) < (S < (NP $+ (VP !< (/^(?:VB|AUX)/ < " + copularWordRegex + " !$+ VP) ))) !$-- CC $-- NP > NP=target",
          "SBARQ < (@WHNP=target <# (/^NN/ < " + timeWordRegex + ")) < (SQ < @NP)",
          "NP < NP-TMP=target"
          });
  public static class TemporalModifierGRAnnotation extends GrammaticalRelationAnnotation { }


  /**
   * The "multi-word expression" grammatical relation.
   * This covers various multi-word constructions for which it would
   * seem pointless or arbitrary to claim grammatical relations between words:
   * as well as, rather than, instead of, but also;
   * such as, because of, all but, in addition to ....
   * <p/>
   * Examples: <br/>
   * "dogs as well as cats" &rarr;
   * <code>mwe</code>(well, as)<br/>
   * <code>mwe</code>(well, as)<p/>
   * "fewer than 700 bottles" &rarr;
   * <code>mwe</code>(than, fewer)
   */
  public static final GrammaticalRelation MULTI_WORD_EXPRESSION =
    new GrammaticalRelation(Language.English, "mwe", "multi-word expression",
        MultiWordExpressionGRAnnotation.class, MODIFIER, "PP|XS|ADVP|CONJP", tregexCompiler,
        new String[] {
          "PP|XS < (IN|TO < as|of|at|to|in) < (JJ|IN|JJR|JJS|NN=target < such|because|Because|least|instead|due|Due|addition|to)",
          "ADVP < (RB|IN < well) < (IN|RB|JJS=target < as)",
          "ADVP < (DT < all) < (CC < but)",
          "CONJP < (RB < rather|well|instead) < (RB|IN=target < as|than|of)",
          "CONJP < (IN < in) < (NN|TO=target < addition|to)",
          // todo: note inconsistent head finding for "rather than"!
          "XS < JJR|JJS=target" // more than, fewer than, well over -- maybe change some of these?
        });
  public static class MultiWordExpressionGRAnnotation extends GrammaticalRelationAnnotation { }

  /* mihai: this block needs to be uncommented to get the KBP 2010 system to work (due to the cached sentences using old code)
   * (Note: in 2011, the measure phrase relation was collapsed into the scope of npadvmod, rather than being separated out.)
   **
   * The "measure-phrase" grammatical relation. The measure-phrase is the relation between
   * the head of an ADJP/ADVP and the head of a measure-phrase modifying the ADJP/ADVP.
   * <p/>
   * Example: <br/>
   * "The director is 65 years old" &rarr;
   * <code>measure</code>(old, years)
   *
  public static final GrammaticalRelation MEASURE_PHRASE =
    new GrammaticalRelation(Language.English, "measure", "measure-phrase",
        MeasurePhraseGRAnnotation.class, MODIFIER, "ADJP|ADVP", tregexCompiler,
        new String[] {
          "ADJP <- JJ <, (NP=target !< NNP)",
          "ADVP|ADJP <# (JJ|IN $- NP=target)"
        });
  public static class MeasurePhraseGRAnnotation extends GrammaticalRelationAnnotation { }
  */ // mihai: end block

  /**
   * The "determiner" grammatical relation.
   * <p> <p/>
   * Examples: <br/>
   * "The man is here" &rarr; <code>det</code>(man,the) <br/>
   * "Which man do you prefer?" &rarr; <code>det</code>(man,which) <br>
   * (The ADVP match is because sometimes "a little" or "every time" is tagged
   * as an AVDVP with POS tags straight under it.)
   */
  public static final GrammaticalRelation DETERMINER =
    new GrammaticalRelation(Language.English, "det", "determiner",
        DeterminerGRAnnotation.class, MODIFIER, "(?:WH)?NP(?:-TMP|-ADV)?|NAC|NML|NX|X|ADVP", tregexCompiler,
        new String[] {
          // For this relation, we do not want to trigger if there are
          // possessive nodes of some kind between the target DT and
          // the node the determiner applies to.  Those nodes will be
          // found as predet instead.  In one rare instance in the
          // PTB, though, possessives in parentheticals in quotes all
          // in one flat NP node should be allowed.
          "/^(?:NP(?:-TMP|-ADV)?|NAC|NML|NX|X)$/ < (DT=target !< /^(?i:either|neither|both)$/ !$+ DT !$++ CC $++ /^(?:N[MNXP]|CD|JJ|FW|ADJP|QP|RB|PRP(?![$])|PRN)/=det !$++ (/^PRP[$]|POS/ $++ =det !$++ (/''/ $++ =det)))",
          "NP|NP-TMP|NP-ADV < (DT=target < /^(?i:either|neither|both)$/ !$+ DT !$++ CC $++ /^(?:NN|NX|NML)/ !$++ (NP < CC))",
          "NP|NP-TMP|NP-ADV < (DT=target !< /^(?i:either|neither|both)$/ $++ CC $++ /^(?:NN|NX|NML)/)",
          "NP|NP-TMP|NP-ADV < (DT=target $++ (/^JJ/ !$+ /^NN/) !$++CC !$+ DT)",
          // "NP|NP-TMP|NP-ADV < (RB=target $++ (/^PDT$/ $+ /^NN/))", // todo: This matches nothing. Was it meant to be a PDT rule for (NP almost/RB no/DT chairs/NNS)?
          "NP|NP-TMP|NP-ADV <<, PRP <- (NP|DT|RB=target <<- all|both|each)", // we all, them all; various structures
          "WHNP < (NP $-- (WHNP=target < WDT))",
          "@WHNP|ADVP < (/^(?:NP|NN|CD|RBS)/ $-- DT|WDT|WP=target)"
        });
  public static class DeterminerGRAnnotation extends GrammaticalRelationAnnotation { }


  /**
   * The "predeterminer" grammatical relation.
   * <p> <p/>
   * Example: <br/>
   * "All the boys are here" &rarr; <code>predet</code>(boys,all)
   */
  public static final GrammaticalRelation PREDETERMINER =
    new GrammaticalRelation(Language.English, "predet", "predeterminer",
        PredeterminerGRAnnotation.class, MODIFIER, "(?:WH)?(?:NP|NX|NAC|NML)(?:-TMP|-ADV)?", tregexCompiler,
        new String[] {
          "/^(?:(?:WH)?NP(?:-TMP|-ADV)?|NX|NAC|NML)$/ < (PDT|DT=target $+ /^(?:DT|WP\\$|PRP\\$)$/ $++ /^N[NXM]/ !$++ CC)",
          "WHNP|WHNP-TMP|WHNP-ADV|NP|NP-TMP|NP-ADV < (PDT|DT=target $+ DT $++ (/^JJ/ !$+ /^NN/)) !$++ CC",
          "WHNP|WHNP-TMP|WHNP-ADV|NP|NP-TMP|NP-ADV < PDT=target <- DT"
        });
  public static class PredeterminerGRAnnotation extends GrammaticalRelationAnnotation { }


  /**
   * The "preconjunct" grammatical relation.
   * <p> <p/>
   * Example: <br/>
   * "Both the boys and the girls are here" &rarr; <code>preconj</code>(boys,both)
   */
  public static final GrammaticalRelation PRECONJUNCT =
    new GrammaticalRelation(Language.English, "preconj", "preconjunct",
        PreconjunctGRAnnotation.class, MODIFIER,
        "S|VP|ADJP|PP|ADVP|UCP(?:-TMP|-ADV)?|NX|NML|SBAR|NP(?:-TMP|-ADV)?", tregexCompiler,
        new String[] {
          // "/^NP(?:-TMP|-ADV)?|NX|NML$/ < (PDT|CC=target < /^(?i:either|neither|both)$/ [ $++ /^N[NXM]/ | $++ (/^JJ/ !$+ /^NN/) ] ) $++ CC",  // cdm Jun 2010: This pattern was matching nothing, since it was looking for the second CC as sister of the top NP not of the preconjunct.  But if you move the close parenthesis right, you'd just get a more restricted form of the second pattern....
          // This is the normal/correct NP-internal preconjunct
          "NP|NP-TMP|NP-ADV|NX|NML < (PDT|CC|DT=target < /^(?i:either|neither|both)$/ $++ CC)",
          "NP|NP-TMP|NP-ADV|NX|NML < (CONJP=target < (RB < /^(?i:not)$/) < (RB|JJ < /^(?i:only|merely|just)$/) $++ CC|CONJP)",
          // This matches weird/wrong NP-internal preconjuncts where you get (NP PDT (NP NP CC NP)) or similar
          "NP|NP-TMP|NP-ADV|NX|NML < (PDT|CC|DT=target < /^(?i:either|neither|both)$/ ) < (NP < CC)",
          "/^S|VP|ADJP|PP|ADVP|UCP(?:-TMP|-ADV)?|NX|NML|SBAR$/ < (PDT|DT|CC=target < /^(?i:either|neither|both)$/ $++ CC)",
          "/^S|VP|ADJP|PP|ADVP|UCP(?:-TMP|-ADV)?|NX|NML|SBAR$/ < (CONJP=target < (RB < /^(?i:not)$/) < (RB|JJ < /^(?i:only|merely|just)$/) $++ CC|CONJP)"
        });
  public static class PreconjunctGRAnnotation extends GrammaticalRelationAnnotation { }


  /**
   * The "possession" grammatical relation between the possessum and the possessor.<p>
   * </p>
   * Examples: <br/>
   * "their offices" &rarr;
   * <code>poss</code>(offices, their)<br/>
   * "Bill 's clothes" &rarr;
   * <code>poss</code>(clothes, Bill)
   */
  public static final GrammaticalRelation POSSESSION_MODIFIER =
    new GrammaticalRelation(Language.English, "poss", "possession modifier",
        PossessionModifierGRAnnotation.class, MODIFIER, "(?:WH)?(NP|ADJP|INTJ|PRN|NAC|NX|NML)(?:-TMP|-ADV)?", tregexCompiler,
        new String[] {
          // possessive pronouns like "my", "whose"; [cdm 2010: Simplified; extra checks seemed unneeded (INTJ for "oh my god", though maybe it should really have internal NP....)
          "/^(?:WH)?(?:NP|INTJ|ADJP|PRN|NAC|NX|NML)(?:-TMP|-ADV)?$/ < /^(?:W|PR)P\\$$/=target",
          // todo: possessive pronoun under ADJP needs more work for one case of (ADJP his or her own)
          // basic NP possessive: we want to allow little conjunctions in head noun (NP (NP ... POS) NN CC NN) but not falsely match when there are conjoined NPs.  See tests.
          "/^(?:WH)?(?:NP|NML)(?:-TMP|-ADV)?$/ [ < (WHNP|WHNML|NP|NML=target [ < POS | < (VBZ < /^'s$/) ] ) !< (CC|CONJP $++ WHNP|WHNML|NP|NML) |  < (WHNP|WHNML|NP|NML=target < (CC|CONJP $++ WHNP|WHNML|NP|NML) < (WHNP|WHNML|NP|NML [ < POS | < (VBZ < /^'s$/) ] )) ]",
          // mediocrely handle a few too flat NPs
          "/^(?:WH)?(?:NP|NML)(?:-TMP|-ADV)?$/ < (/^NN/=target $+ (POS < /'/ $++ /^NN/))"
        });
  public static class PossessionModifierGRAnnotation extends GrammaticalRelationAnnotation { }


  /**
   * The "possessive" grammatical relation.  This is the relation given to
   * 's (or ' with plurals).<p>
   * </p>
   * Example: <br/>
   * "John's book" &rarr;
   * <code>possessive</code>(John, 's)
   */
  public static final GrammaticalRelation POSSESSIVE_MODIFIER =
    new GrammaticalRelation(Language.English, "possessive", "possessive modifier",
        PossessiveModifierGRAnnotation.class, MODIFIER, "(?:WH)?(?:NP|NML)(?:-TMP|-ADV)?", tregexCompiler,
        new String[] {
          "/^(?:WH)?(?:NP|NML)(?:-TMP|-ADV)?$/ < POS=target",
          "/^(?:WH)?(?:NP|NML)(?:-TMP|-ADV)?$/ < (VBZ=target < /^'s$/)"
        });
  public static class PossessiveModifierGRAnnotation extends GrammaticalRelationAnnotation { }


  /**
   * The "prepositional modifier" grammatical relation.  A prepositional
   * modifier of a verb, adjective, or noun is any prepositional phrase that serves to modify
   * the meaning of the verb, adjective, or noun.
   * We also generate prep modifiers of PPs to account for treebank (PP PP PP) constructions
   * (from 1984 through 2002). <p>
   * <p/>
   * Examples: <br/>
   * "I saw a cat in a hat" &rarr;
   * <code>prep</code>(cat, in) <br/>
   * "I saw a cat with a telescope" &rarr;
   * <code>prep</code>(saw, with) <br/>
   * "He is responsible for meals" &rarr;
   * <code>prep</code>(responsible, for)
   */
  public static final GrammaticalRelation PREPOSITIONAL_MODIFIER =
    new GrammaticalRelation(Language.English, "prep", "prepositional modifier",
        PrepositionalModifierGRAnnotation.class, MODIFIER, ".*", tregexCompiler,
        new String[] {
          // note that we disallow nodes which are next to a CC or
          // CONJP, which can happen to a PP when we are analyzing
          // structure under a UCP
          // Other PP parents still not covered: UCP, RRC
          "/^(?:(?:WH)?(?:NP|ADJP|ADVP|NX|NML)(?:-TMP|-ADV)?|VP|NAC|SQ|FRAG|PRN|X)$/ < (WHPP|WHPP-TMP|PP|PP-TMP=target !$- (@CC|CONJP $- __))",
          // only allow a PP < PP one if there is not a conj, verb, or other pattern that matches pcomp under it.  Else pcomp
          "WHPP|WHPP-TMP|WHPP-ADV|PP|PP-TMP|PP-ADV < (WHPP|WHPP-TMP|WHPP-ADV|PP|PP-TMP|PP-ADV=target !$- IN|VBG|VBN|TO) !< @CC|CONJP",
          "S|SINV < (PP|PP-TMP=target !< SBAR) < VP|S",
          "SBAR|SBARQ < /^(?:WH)?PP/=target < S|SQ"
        });
  public static class PrepositionalModifierGRAnnotation extends GrammaticalRelationAnnotation { }


  /**
   * The "phrasal verb particle" grammatical relation.  The "phrasal verb particle"
   * relation identifies phrasal verb.<p>
   * <p/>
   * Example: <br/>
   * "They shut down the station." &rarr;
   * <code>prt</code>(shut, down)
   */
  public static final GrammaticalRelation PHRASAL_VERB_PARTICLE =
    new GrammaticalRelation(Language.English, "prt", "phrasal verb particle",
        PhrasalVerbParticleGRAnnotation.class, MODIFIER, "VP|ADJP", tregexCompiler,
        new String[] {
          "VP < PRT=target",
          "ADJP < /^VB/ < RP=target"
        });
  public static class PhrasalVerbParticleGRAnnotation extends GrammaticalRelationAnnotation { }


    /**
   * The "parataxis" grammatical relation. Relation between the main verb of a sentence
   * and other sentential elements, such as a sentential parenthetical, a sentence after a ":" or a ";"
   * etc.
   * <p> <p/>
   * Examples: <br/>
   * "The guy, John said, left early in the morning." &rarr; <code>parataxis</code>(left,said) <br/>
   * "
   */
  public static final GrammaticalRelation PARATAXIS =
    new GrammaticalRelation(Language.English, "parataxis", "parataxis",
        ParataxisGRAnnotation.class, DEPENDENT, "S|VP", tregexCompiler,
        new String[]{
          "VP < (PRN=target < S|SINV|SBAR)", // parenthetical
          "VP $ (PRN=target [ < S|SINV|SBAR | < VP < @NP ] )", // parenthetical
          // The next relation handles a colon between sentences
          // and similar punct such as --
          // Sometimes these are lists, especially in the case of ";",
          // so we don't trigger if there is a CC|CONJP that occurs
          // anywhere other than the first child
          // First child can occur in rare circumstances such as
          // "But even if he agrees -- which he won't -- etc etc"
          "S|VP < (/^:$/ $+ /^S/=target) !<, (__ $++ CC|CONJP)",
          // two juxtaposed sentences; common in web materials.
          "@S < (@S $.. @S=target) !< @CC|CONJP",

        });
  public static class ParataxisGRAnnotation extends GrammaticalRelationAnnotation { }

  /**
   * The "goes with" grammatical relation.  This corresponds to use of the GW (goes with) part-of-speech tag
   * in the recent Penn Treebanks. It marks partial words that should be combined with some other word. <p>
   * <p/>
   * Example: <br/>
   * "They come here with out legal permission." &rarr;
   * <code>goeswith</code>(out, with)
   */
  public static final GrammaticalRelation GOES_WITH =
    new GrammaticalRelation(Language.English, "goeswith", "goes with",
        GoesWithGRAnnotation.class, MODIFIER, ".*", tregexCompiler,
        new String[] {
          "__ < GW=target",
        });
  public static class GoesWithGRAnnotation extends GrammaticalRelationAnnotation { }



  /**
   * The "semantic dependent" grammatical relation has been
   * introduced as a supertype for the controlling subject relation.
   */
  public static final GrammaticalRelation SEMANTIC_DEPENDENT =
    new GrammaticalRelation(Language.English, "sdep", "semantic dependent",
        SemanticDependentGRAnnotation.class, DEPENDENT);
  public static class SemanticDependentGRAnnotation extends GrammaticalRelationAnnotation { }


  /**
   * The "controlling subject" grammatical relation.<p>
   * A controlling subject is the relation between the head of an xcomp and the external subject
   * of that clause.
   * <p/>
   * Example: <br/>
   * "Tom likes to eat fish" &rarr;
   * <code>xsubj</code>(eat, Tom)
   */
  public static final GrammaticalRelation CONTROLLING_SUBJECT =
    new GrammaticalRelation(Language.English, "xsubj", "controlling subject",
        ControllingSubjectGRAnnotation.class, SEMANTIC_DEPENDENT);
  public static class ControllingSubjectGRAnnotation extends GrammaticalRelationAnnotation { }


  /**
   * The "agent" grammatical relation. The agent of a passive VP
   * is the complement introduced by "by" and doing the action.<p>
   * <p/>
   * Example: <br/>
   * "The man has been killed by the police" &rarr;
   * <code>agent</code>(killed, police)
   */
  public static final GrammaticalRelation AGENT =
    new GrammaticalRelation(Language.English, "agent", "agent",
        AgentGRAnnotation.class, DEPENDENT);
  public static class AgentGRAnnotation extends GrammaticalRelationAnnotation { }


  // TODO would be nice to have this set up automatically...
  /**
   * A list of GrammaticalRelation values.  New GrammaticalRelations must be
   * added to this list (until we make this an enum!).
   * The GR recognizers are tried in the order listed.  A taxonomic
   * relationship trumps an ordering relationship, but otherwise, the first
   * listed relation will appear in dependency output.  Known ordering
   * constraints where both match include:
   * <ul>
   * <li>NUMERIC_MODIFIER &lt; ADJECTIVAL_MODIFIER
   * </ul>
   */
  @SuppressWarnings({"RedundantArrayCreation"})
  private static final List<GrammaticalRelation> values =
    Generics.newArrayList(Arrays.asList(new GrammaticalRelation[] {
      GOVERNOR,
      DEPENDENT,
      PREDICATE,
      ATTRIBUTIVE,
      AUX_MODIFIER,
      AUX_PASSIVE_MODIFIER,
      COPULA,
      CONJUNCT,
      COORDINATION,
      PUNCTUATION,
      ARGUMENT,
      SUBJECT,
      NOMINAL_SUBJECT,
      NOMINAL_PASSIVE_SUBJECT,
      CLAUSAL_SUBJECT,
      CLAUSAL_PASSIVE_SUBJECT,
      COMPLEMENT,
      OBJECT,
      DIRECT_OBJECT,
      INDIRECT_OBJECT,
      PREPOSITIONAL_OBJECT,
      PREPOSITIONAL_COMPLEMENT,
      CLAUSAL_COMPLEMENT,
      XCLAUSAL_COMPLEMENT,
      MARKER,
      RELATIVE,
      REFERENT,
      EXPLETIVE,
      ADJECTIVAL_COMPLEMENT,
      MODIFIER,
      ADV_CLAUSE_MODIFIER,
      TEMPORAL_MODIFIER,
      RELATIVE_CLAUSE_MODIFIER,
      NUMERIC_MODIFIER,
      ADJECTIVAL_MODIFIER,
      NOUN_COMPOUND_MODIFIER,
      APPOSITIONAL_MODIFIER,
      PARTICIPIAL_MODIFIER,
      INFINITIVAL_MODIFIER,
      ADVERBIAL_MODIFIER,
      NEGATION_MODIFIER,
      MULTI_WORD_EXPRESSION,
      DETERMINER,
      PREDETERMINER,
      PRECONJUNCT,
      POSSESSION_MODIFIER,
      POSSESSIVE_MODIFIER,
      PREPOSITIONAL_MODIFIER,
      PHRASAL_VERB_PARTICLE,
      SEMANTIC_DEPENDENT,
      CONTROLLING_SUBJECT,
      AGENT,
      NUMBER_MODIFIER,
      QUANTIFIER_MODIFIER,
      NP_ADVERBIAL_MODIFIER,
      PARATAXIS,
      DISCOURSE_ELEMENT,
      GOES_WITH,
    }));
  /* Cache frequently used views of the values list */
  private static final List<GrammaticalRelation> unmodifiableValues =
    Collections.unmodifiableList(values);
  private static final List<GrammaticalRelation> synchronizedValues =
    Collections.synchronizedList(values);
  private static final List<GrammaticalRelation> unmodifiableSynchronizedValues =
    Collections.unmodifiableList(values);
  public static final ReadWriteLock valuesLock = new ReentrantReadWriteLock();

  // Map from English GrammaticalRelation short names to their corresponding
  // GrammaticalRelation objects
  public static final Map<String, GrammaticalRelation> shortNameToGRel = new ConcurrentHashMap<String, GrammaticalRelation>();
  static {
    for (GrammaticalRelation gr : values()) {
      shortNameToGRel.put(gr.toString().toLowerCase(), gr);
    }
  }

  public static List<GrammaticalRelation> values() {
    return values(false);
  }

  public static List<GrammaticalRelation> values(boolean threadSafe) {
    return threadSafe? unmodifiableSynchronizedValues : unmodifiableValues;
  }

  public static Lock valuesLock() {
    return valuesLock.readLock();
  }

  /**
   * This method is meant to be called when you want to add a relation
   * to the values list in a thread-safe manner.  Currently, this method
   * is always used in preference to values.add() because we expect to
   * add new EnglishGrammaticalRelations very rarely, so the eased
   * concurrency seems to outweigh the fairly slight cost of thread-safe
   * access.
   * @param relation the relation to be added to the values list
   */
  private static void threadSafeAddRelation(GrammaticalRelation relation) {
    valuesLock.writeLock().lock();
    try { // try-finally structure taken from Javadoc code sample for ReentrantReadWriteLock
      synchronizedValues.add(relation);
      shortNameToGRel.put(relation.toString(), relation);
    } finally {
      valuesLock.writeLock().unlock();
    }
  }



  // the exhaustive list of conjunction relations
  private static final Map<String, GrammaticalRelation> conjs = Generics.newConcurrentHashMap();

  public static Collection<GrammaticalRelation> getConjs() {
    return conjs.values();
  }

  /**
   * The "conj" grammatical relation. Used to collapse conjunct relations.
   * They will be turned into conj_word, where "word" is a conjunction.
   *
   * NOTE: Because these relations lack associated GrammaticalRelationAnnotations,
   *       they cannot be arcs of a TreeGraphNode.
   *
   * @param conjunctionString The conjunction to make a GrammaticalRelation out of
   * @return A grammatical relation for this conjunction
   */
  public static GrammaticalRelation getConj(String conjunctionString) {
    GrammaticalRelation result = conjs.get(conjunctionString);
    if (result == null) {
      synchronized(conjs) {
        result = conjs.get(conjunctionString);
        if (result == null) {
          result = new GrammaticalRelation(Language.English, "conj", "conj_collapsed", null, CONJUNCT, conjunctionString);
          conjs.put(conjunctionString, result);
          threadSafeAddRelation(result);
        }
      }
    }
    return result;
  }

  // the exhaustive list of preposition relations
  private static final Map<String, GrammaticalRelation> preps = Generics.newConcurrentHashMap();
  private static final Map<String, GrammaticalRelation> prepsC = Generics.newConcurrentHashMap();


  public static Collection<GrammaticalRelation> getPreps() {
    return preps.values();
  }

  public static Collection<GrammaticalRelation> getPrepsC() {
    return prepsC.values();
  }


  /**
   * The "prep" grammatical relation. Used to collapse prepositions.<p>
   * They will be turned into prep_word, where "word" is a preposition
   *
   * NOTE: Because these relations lack associated GrammaticalRelationAnnotations,
   *       they cannot be arcs of a TreeGraphNode.
   *
   * @param prepositionString The preposition to make a GrammaticalRelation out of
   * @return A grammatical relation for this preposition
   */
  public static GrammaticalRelation getPrep(String prepositionString) {
    GrammaticalRelation result = preps.get(prepositionString);
    if (result == null) {
      synchronized(preps) {
        result = preps.get(prepositionString);
        if (result == null) {
          result = new GrammaticalRelation(Language.English, "prep", "prep_collapsed", null, PREPOSITIONAL_MODIFIER, prepositionString);
          preps.put(prepositionString, result);
          threadSafeAddRelation(result);
        }
      }
    }
    return result;
  }


  /**
   * The "prepc" grammatical relation. Used to collapse preposition
   * complements.<p>
   * They will be turned into prep_word, where "word" is a preposition
   *
   * NOTE: Because these relations lack associated GrammaticalRelationAnnotations,
   *       they cannot be arcs of a TreeGraphNode.
   *
   * @param prepositionString The preposition to make a GrammaticalRelation out of
   * @return A grammatical relation for this preposition
   */
  public static GrammaticalRelation getPrepC(String prepositionString) {
    GrammaticalRelation result = prepsC.get(prepositionString);
    if (result == null) {
      synchronized(prepsC) {
        result = prepsC.get(prepositionString);
        if (result == null) {
          result = new GrammaticalRelation(Language.English, "prepc", "prepc_collapsed", null, DEPENDENT, prepositionString);
          prepsC.put(prepositionString, result);
          threadSafeAddRelation(result);
        }
      }
    }
    return result;
  }


  /**
   * Returns the EnglishGrammaticalRelation having the given string
   * representation (e.g. "nsubj"), or null if no such is found.
   *
   * @param s The short name of the GrammaticalRelation
   * @return The EnglishGrammaticalRelation with that name
   */
  public static GrammaticalRelation valueOf(String s) {
    return GrammaticalRelation.valueOf(s, values);

//    // TODO does this need to be changed?
//    // modification NOTE: do not commit until go-ahead
//    // If this is a collapsed relation (indicated by a "_" separating
//    // the type and the dependent, instantiate a collapsed version.
//    // Currently handcode against conjunctions and prepositions, but
//    // should do this in a more robust fashion.
//    String[] tuples = s.trim().split("_", 2);
//    if (tuples.length == 2) {
//      String reln = tuples[0];
//      String specific = tuples[1];
//      if (reln.equals(PREPOSITIONAL_MODIFIER.getShortName())) {
//        return getPrep(specific);
//      } else if (reln.equals(CONJUNCT.getShortName())) {
//        return getConj(specific);
//      }
//    }
//
//    return null;
  }

  /**
   * Returns an EnglishGrammaticalRelation based on the argument.
   * It works if passed a GrammaticalRelation or the String
   * representation of one (e.g. "nsubj").  It returns <code>null</code>
   * for other classes or if no string match is found.
   *
   * @param o A GrammaticalRelation or String
   * @return The EnglishGrammaticalRelation with that name
   */
  @SuppressWarnings("unchecked")
  public static GrammaticalRelation valueOf(Object o) {
    if (o instanceof GrammaticalRelation) {
      return (GrammaticalRelation) o;
    } else if (o instanceof Class) {
      try {
        return getRelation((Class<? extends GrammaticalRelationAnnotation>) o);
      } catch (Exception e) {
        return null;
      }
    } else if (o instanceof String) {
      return valueOf((String) o);
    } else {
      return null;
    }
  }

  /**
   * Prints out the English grammatical relations hierarchy.
   * See <code>EnglishGrammaticalStructure</code> for a main method that
   * will print the grammatical relations of a sentence or tree.
   *
   * @param args Args are ignored.
   */
  public static void main(String[] args) {
    System.out.println(DEPENDENT.toPrettyString());
  }
}
