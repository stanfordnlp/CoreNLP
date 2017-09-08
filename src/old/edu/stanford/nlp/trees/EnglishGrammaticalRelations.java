package old.edu.stanford.nlp.trees;

import static old.edu.stanford.nlp.trees.GrammaticalRelation.*;
import old.edu.stanford.nlp.util.Generics;

import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;


/**
 * <code>EnglishGrammaticalRelations</code> is a
 * set of {@link GrammaticalRelation} objects for the English language.
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
 * <li>Agents is passive sentences are recognized and marked as agent and not as prep_by.</li>
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
 * get these temporal NP dependencies right!
 * <p/>
 * <i>Implementation note: </i> To add a new grammatical relation:
 * <ul>
 * <li> Governor nodes of the grammatical relations should be the lowest ones.</li>
 * <li> Check the semantic head rules in SemanticHeadFinder and
 * ModCollinsHeadFinder, both in the trees package.</li>
 * <li> Create and define the GrammaticalRelation similarly to the others.</li>
 * <li> Add it to the <code>values</code> array at the end of the file.</li>
 * </ul>
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

  /** This class is just a holder for static classes
   *  that act a bit like an enum.
   */
  private EnglishGrammaticalRelations() {}

  private static final String timeWordRegex =
    "/(?i)^Mondays?|Tuesdays?|Wednesdays?|Thursdays?|Fridays?|Saturdays?|Sundays?|years?|months?|weeks?|days?|mornings?|evenings?|January|February|March|April|May|June|July|August|September|October|November|December|today|yesterday|tomorrow|spring|summer|fall|autumn|winter$/";
  private static final String timeWordLotRegex =
    "/(?i)^Mondays?|Tuesdays?|Wednesdays?|Thursdays?|Fridays?|Saturdays?|Sundays?|years?|months?|weeks?|days?|mornings?|evenings?|January|February|March|April|May|June|July|August|September|October|November|December|today|yesterday|tomorrow|spring|summer|fall|autumn|winter|lot$/";


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
        PredicateGRAnnotation.class, DEPENDENT, "S|SINV",
        new String[] {
          "S < VP=target"
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
        AuxModifierGRAnnotation.class, DEPENDENT, "VP|SQ|SINV",
        new String[] {
          "VP < VP < /^(?:TO|MD|VB.*|AUXG?)$/=target",
          "SQ|SINV < (/^VB|MD|AUXG?/=target $++ /^(?:VP|ADJP)/)"
        });
  public static class AuxModifierGRAnnotation extends GrammaticalRelationAnnotation { }


  /**
   * The "passive auxiliary" grammatical relation.  A passive auxiliary of a
   * clause is a
   * non-main verb of the clause which contains the passive information.
   * <p/>
   * Example: <br/>
   * "Kennedy has been killed" &rarr; <code>auxpass</code>(killed, been)
   */
  public static final GrammaticalRelation AUX_PASSIVE_MODIFIER =
    new GrammaticalRelation(Language.English, "auxpass", "passive auxiliary",
        AuxPassiveGRAnnotation.class, AUX_MODIFIER, "VP|SQ",
        new String[] {
          "VP < (/^(?:VB|AUXG?)/=target < /be|was|'s|is|are|were|been|being|am|Been|Being|WAS|IS|get|got|getting|gets|Get|gotten|becomes|become|became|felt|feels|feel|seems|seem|seemed|remains|remained|remain/) < (VP|ADJP < VBN|VBD)",
          "VP < (/^(?:VB|AUXG?)/=target < /be|was|'s|is|are|were|been|being|am|Been|Being|WAS|IS|get|got|getting|gets|Get|gotten|becomes|become|became|felt|feels|feel|seems|seem|seemed|remains|remained|remain/) < (VP|ADJP < (VP|ADJP < VBN|VBD) < CC)",
          "SQ < (/^(?:VB|AUX)/=target < /^(?:was|is|are|were|am|Was|Is|Are|Were|Am|WAS|IS|ARE|WERE|AM)$/ $++ (VP < /^VB[DN]$/))"
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
        CopulaGRAnnotation.class, AUX_MODIFIER, "VP|SQ",
        new String[] {
          "VP < (/^VB|AUX?/=target < /(?i)^(?:am|'m|are|'re|is|'s|be|being|was|were|seem|seems|seemed|appear|appears|appeared|stay|stays|stayed|remain|remains|remained|resemble|resembles|resembled|become|becomes|became)$/ [ $++ (ADJP|NP !< VBN) | $++ (S <: (ADJP < JJ)) ] )",
          "SQ <, (/^VB|AUX?/=target < /(?i)^(am|are|is|was|were)$/) !$ /^WH*/"
        });

  public static class CopulaGRAnnotation extends GrammaticalRelationAnnotation {
  }


  /**
   * The "conjunct" grammatical relation.  A conjunct is the relation between
   * two elements connected by a conjunction word.  We treat conjunctions
   * asymmetrically: The head of the relation is the first conjunct and other
   * conjunctions depend on it via the \emph{conj} relation.<p>
   * <p/>
   * Example: <br/>
   * "Bill is big and honest" &rarr; <code>conj</code>(big, honest)
   */
  public static final GrammaticalRelation CONJUNCT =
    new GrammaticalRelation(Language.English, "conj", "conjunct",
        ConjunctGRAnnotation.class, DEPENDENT, "VP|NP|ADJP|PP|QP|ADVP|UCP|S|NX|SBAR",
        new String[] { // remember conjunction can be left or right headed....
          // non-parenthetical in suitable phrase with conjunction to left
          "VP|ADJP|PP|QP|NP|ADVP|UCP|S|NX|SBAR < (CC|CONJP $+ !PRN=target) !<, CC",
          // non-parenthetical in suitable phrase with conj then adverb to left
          "VP|ADJP|PP|NP|ADVP|UCP|S|NX|SBAR < (CC|CONJP $+ (ADVP $+ !PRN=target))",
          // to the right of a comma
          "VP|ADJP|PP|NP|ADVP|UCP|S|NX|SBAR < CC|CONJP < (/^,$/ $+ /^S$|^(A|N|V|PP|PRP|J|W|R)/=target)",
          // to the right of a parenthetical
          "VP|ADJP|PP|NP|ADVP|UCP|S|NX|SBAR < CC|CONJP < (PRN $+ /^(A|N|V|PP|PRP|J|W|R|S)/=target)",
          // to the left of a comma for at least NX
          "NX < CC|CONJP < (/^,$/ $- /^(A|N|V|PP|PRP|J|W|R|S)/=target)",
          // to take the conjunct in a preconjunct structure either X or Y
          "VP|ADJP|PP|QP|NP|ADVP|UCP|S|NX|SBAR < (CC $++ (CC $+ !PRN=target))"
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
        CoordinationGRAnnotation.class, DEPENDENT, "S|VP|NP|ADJP|PP|QP|ADVP|UCP|NX|SBAR",
        new String[] {
          "S|VP|NP|QP|ADJP|PP|ADVP|UCP|NX|SBAR < (CC|CONJP=target !< /either|neither|both|Either|Neither|Both/)"
        });
  public static class CoordinationGRAnnotation extends GrammaticalRelationAnnotation { }


  /**
   * The "punctuation" grammatical relation.  This is used for any piece of
   * punctuation in a clause, if punctuation is being retained in the
   * typed dependencies.
   * <p/>
   * Example: <br/>
   * "Go home!" &rarr; <code>punct</code>(Go, !)
   */
  public static final GrammaticalRelation PUNCTUATION =
    new GrammaticalRelation(Language.English, "punct", "punctuation",
        PunctuationGRAnnotation.class, DEPENDENT, "S|NP|VP|SQ|PRN|SINV|SBAR|UCP",
        new String[] {
          "__ < /^(?:\\.|:|,|''|``|-LRB-|-RRB-)$/=target"
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
        NominalSubjectGRAnnotation.class, SUBJECT, "S|SQ|SBARQ|SINV|SBAR",
        new String[] {
          "S < ((NP|WHNP=target !< EX !< (/^NN/ < (" + timeWordRegex + "))) $++ VP)",
          "S < ( NP=target < (/^NN/ < " + timeWordRegex + ") !$++ NP $++VP)",
          "SQ < ((NP=target !< EX) $++ VP)",
          "SQ < ((NP=target !< EX) $- /^VB|AUX/ !$++ VP)",
          "SQ < ((NP=target !< EX) $- (RB $- /^VB|AUX/) ![$++ VP])",
          "SBARQ < WHNP=target < (SQ < (VP ![$-- NP]))",
          "SBARQ < (SQ=target < /^VB|AUX/ !< VP)",
          // matches subj in SINV
          "SINV < (VP|VBZ|VBD|AUX $+ /^NP|WHNP$/=target)",
          //matches subj in xcomp like "He considered him a friend"
          "S < (NP=target $+ NP|ADJP) > VP",
          // matches subj in relative clauses
          "SBAR <, WHNP=target < (S < (VP !$-- NP) !< SBAR)",
          // matches subj in relative clauses
          "SBAR !< WHNP < (S !< (NP $++ VP)) > (VP > (S $- WHNP=target))",
          // matches subj in existential "there" SQ
          "SQ < ((NP < EX) $++ NP=target)",
          // matches subj in existential "there" S
          "S < (NP < EX) < (VP < NP=target)"
        });
  public static class NominalSubjectGRAnnotation extends GrammaticalRelationAnnotation { }


  /**
   * The "nominal passive subject" grammatical relation.  A nominal passive
   * subject is a subject of a passive which is an noun phrase.<p>
   * <p/>
   * Example: <br/>
   * "Dole was defeated by Clinton" &rarr; <code>nsubjpass</code>(defeated, Dole)
   */
  public static final GrammaticalRelation NOMINAL_PASSIVE_SUBJECT =
    new GrammaticalRelation(Language.English, "nsubjpass", "nominal passive subject",
        NominalPassiveSubjectGRAnnotation.class, NOMINAL_SUBJECT, "S|VP",
        new String[] {
          "S < /^NP|WHNP$/=target < (VP|SQ < (VP < VBN|VBD) < (/^(VB|AUXG?)/ < /be|was|is|are|were|been|being|'s|'re|'m|am|Been|Being|WAS|IS|get|got|getting|gets|Get|gotten|becomes|become|became|felt|feels|feel|seems|seem|seemed|remains|remained|remain/))",
          "S < /^(NP|WHNP)$/=target < (VP|SQ <+(VP) (VP < VBN|VBD > (VP < (/^(VB|AUXG?)/ < /be|was|is|are|were|been|being|'s|'re|'m|am|Been|Being|WAS|IS|get|got|getting|gets|Get|gotten|becomes|become|became|felt|feels|feel|seems|seem|seemed|remains|remained|remain/))))",
          /*"VP <, VBN > NP=target"*/
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
        ClausalSubjectGRAnnotation.class, SUBJECT, "S",
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
        ClausalPassiveSubjectGRAnnotation.class, CLAUSAL_SUBJECT, "S",
        new String[] {
          "S < (SBAR|S=target !$+ /^,$/ $++ (VP < (VP < VBN|VBD) < (/^(VB|AUXG?)/ < /be|was|is|are|were|been|being|'s|'re|'m|am|Been|Being|WAS|IS|get|got|getting|gets|Get|gotten|becomes|become|became|felt|feels|feel|seems|seem|seemed|remains|remained|remain/) !$-- NP))",
          "S < (SBAR|S=target !$+ /^,$/ $++ (VP <+(VP) (VP < VBN|VBD > (VP < (/^(VB|AUXG?)/ < /be|was|is|are|were|been|being|'s|'re|'m|am|Been|Being|WAS|IS|get|got|getting|gets|Get|gotten|becomes|become|became|felt|feels|feel|seems|seem|seemed|remains|remained|remain/))) !$-- NP))"
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
        DirectObjectGRAnnotation.class, OBJECT, "SBARQ|VP|SBAR",
        new String[] {
          // case with an iobj before
          "VP < (NP $+ (/^(NP|WHNP)$/=target !< (/^NN/ < " + timeWordLotRegex + "))) !<(/^VB|AUXG?/ < /^(am|is|are|being|Being|be|'s|'re|'m|was|were|seem|seems|seemed|appear|appears|appeared|stay|stays|stayed|remain|remains|remained|resemble|resembles|resembled|become|becomes|became)$/) ",  // this time one also included "lot"

          // match "give it next week"
          "VP < (NP < (NP $+ (/^(NP|WHNP)$/=target !< (/^NN/ < " + timeWordLotRegex + "))))!< (/^VB|AUX?/ < /^(am|is|are|be|being|Being|'s|'re|'m|was|were|seem|seems|seemed|appear|appears|appeared|stay|stays|stayed|remain|remains|remained|resemble|resembles|resembled|become|becomes|became)$/)",  // this time one also included "lot"
          "VP !<(/^VB|AUXG?/ < /^(am|is|are|be|being|Being|'s|'re|'m|was|were|seem|seems|seemed|appear|appears|appeared|stay|stays|stayed|remain|remains|remained|resemble|resembles|resembled|become|becomes|became)$/) < (/^(NP|WHNP)$/=target !< (/^NN/ < " + timeWordRegex + ") !$+ NP)",
          "VP !<(/^VB|AUXG?/ < /^(am|is|are|be|being|Being|'s|'re|'m|was|were|seem|seems|seemed|appear|appears|appeared|stay|stays|stayed|remain|remains|remained|resemble|resembles|resembled|become|becomes|became)$/) < (/^(NP|WHNP)$/=target $+ NP-TMP)",

          // matches direct object in relative clauses "I saw the book that you bought"
          "VP !<(/^VB|AUXG?/ < /^(am|is|are|be|being|Being|'s|'re|'m|was|were|seem|seems|seemed|appear|appears|appeared|stay|stays|stayed|remain|remains|remained|resemble|resembles|resembled|become|becomes|became)$/) < (/^(NP|WHNP)$/=target $+ (NP < (/^NN/ < " + timeWordRegex + ")))",
          "SBARQ <, (WHNP=target !< WRB) << (VP !< (S < (VP < TO)) $-- NP)",
          "SBAR <, (WHNP=target !< WRB) < (S < NP < (VP !< PP !< SBAR !< (S < (VP < TO))))",

          // matches direct object in relative clauses "I saw the book that you said you bought"
          "SBAR !< WHNP < (S < (NP $++ (VP !$++ NP))) > (VP > (S < NP $- WHNP=target))",

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
        IndirectObjectGRAnnotation.class, OBJECT, "VP",
        new String[] {
          "VP < (NP=target !< /\\$/ !< (/^NN/ < " + timeWordRegex + ") $+ (NP !< (/^NN/ < " + timeWordRegex + ")))",
          "VP < (NP=target < (NP !< /\\$/ $++ (NP !< (/^NN/ < /^lot$/)) !$ CC !$ CONJP !$ /^,$/ !$++ /^:$/))"
        });
  public static class IndirectObjectGRAnnotation extends GrammaticalRelationAnnotation { }


  /**
   * The "prepositional object" grammatical relation.  The object of a
   * preposition is the head of a noun phrase following the preposition, or
   * the adverbs "here" and "there". 
   * (The preposition in turn may be modifying a noun, verb, etc.)
   * We here define cases of VBG quasi-prepositions like "including",
   * "concerning", etc. as instances of pobj.
   * <p/>
   * Example: <br/>
   * "I sat on the chair" &rarr;
   * <code>pobj</code>(on, chair)
   * <p/>
   * (The preposition can be called a FW for pace, versus, etc.  It can also
   * be called a CC - but we don't currently handle that and would need to
   * distinguish from conjoined PPs.)
   */
  public static final GrammaticalRelation PREPOSITIONAL_OBJECT =
    new GrammaticalRelation(Language.English, "pobj", "prepositional object",
        PrepositionalObjectGRAnnotation.class, OBJECT, "^PP(?:-TMP)?$|WHPP",
        new String[] {
          "/^PP(?:-TMP)?$|WHPP/ < /^IN|VBG|TO|FW/ < /^NP(?:-TMP)?$|WHNP|ADJP/=target",
          "/^PP(?:-TMP)?$/ < (/^IN|VBG|TO/ $+ (ADVP=target < (ADVP < /^NP(?:-TMP)?$/)))",
          "/^PP(?:-TMP)?$/ < (/^IN|VBG|TO/ $+ (ADVP=target < (RB < /^(here|there)$/)))"
        });
  public static class PrepositionalObjectGRAnnotation extends GrammaticalRelationAnnotation { }


  /**
   * The "prepositional complement" grammatical relation. The prepositional complement of
   * a preposition is the head of a sentence following the preposition.
   * <p/>
   * Examples: <br/>
   * "We have no useful information on whether users are at risk" &arr;
   * <code>pcomp</code>(on, are) <br/>
   * "They heard about you missing classes." &arr;
   * <code>pcomp</code>(about, missing)
   */
  public static final GrammaticalRelation PREPOSITIONAL_COMPLEMENT =
    new GrammaticalRelation(Language.English, "pcomp", "prepositional complement",
        PrepositionalComplementGRAnnotation.class, OBJECT, "^PP(?:-TMP)?$",
        new String[] {
          "/^PP(?:-TMP)?$/ < (IN|VBG|TO $+ SBAR|S=target)",
          "/^PP(?:-TMP)?$/ < (SBAR=target <, (IN $+ S))"
        });
  public static class PrepositionalComplementGRAnnotation extends GrammaticalRelationAnnotation { }


  /**
   * The "attributive" grammatical relation. The attributive is the complement of a
   * verb such as "to be, to seem, to appear".
   */
  public static final GrammaticalRelation ATTRIBUTIVE =
    new GrammaticalRelation(Language.English, "attr", "attributive",
        AttributiveGRAnnotation.class, COMPLEMENT, "VP|SBARQ|SQ",
        new String[] {
          "VP !$ (NP < EX) < NP=target <(/^VB|AUXG?/ < /^(am|is|are|be|being|'s|'re|'m|was|were|seem|seems|seemed|appear|appears|appeared|stay|stays|stayed|remain|remains|remained|resemble|resembles|resembled|become|becomes|became)$/)",
          // "What is that?"
          "SBARQ < (WHNP=target $+ (SQ < (/^VB|AUX?/ < /^(am|is|are|'s|'re|'m|was|were|seem|seems|seemed|appear|appears|appeared|stay|stays|stayed|remain|remains|remained|resemble|resembles|resembled|become|becomes|became)$/ !$++ (VP < VBG))))",
          //"Is he the man?"
          "SQ <, (/^VB|AUX?/ < /^(Am|am|Is|is|Are|are|be|being|'s|'re|'m|Was|was|Were|were)$/) < (NP=target $-- (NP !< EX))"
        });
  public static class AttributiveGRAnnotation extends GrammaticalRelationAnnotation { }


  /**
   * The "clausal complement" grammatical relation.  A clausal
   * complement of a VP or an ADJP is a clause with internal subject
   * which functions like an object of the verb or of the adjective;
   * a clausal complement of a clause is the clausal
   * complement of the VP or of the ADJP which is the predicate of that
   * clause.  Such clausal complements are usually finite (though there
   * are occasional remnant English subjunctives).<p>
   * <p/>
   * Example: <br/>
   * "He says that you like to swim" &rarr;
   * <code>ccomp</code>(says, like) <br/>
   * "I am certain that he did it" &rarr;
   * <code>ccomp</code>(certain, did)
   */
  public static final GrammaticalRelation CLAUSAL_COMPLEMENT =
    new GrammaticalRelation(Language.English, "ccomp", "clausal complement",
        ClausalComplementGRAnnotation.class, COMPLEMENT, "VP|SINV|S|ADJP",
        new String[] { // note if you add more words in the pattern, be sure to add them in the ADV_CLAUSE_MODIFIER too!
          "VP < (S=target < (VP !<, TO|VBG) !$-- NP)",
          "VP < (SBAR=target < (S <+(S) VP) <, (IN|DT < /^(that|whether)$/))",
          "VP < (SBAR=target < (S < VP) !$-- NP !<, (IN|WHADVP))",
          "VP < (SBAR=target < (S < VP) !$-- NP <, (WHADVP < (WRB < /^[Hh]ow$/)))",
          // to find "...", he said or "...?" he asked.
          "S|SINV < (S|SBARQ=target $+ /^(,|.|'')$/ !$- /^:|CC$/ !< (VP < TO|VBG))",
          "ADJP < (SBAR=target < (S < VP))",
          // That ... he know
          "S <, (SBAR=target <, (IN < /^([Tt]hat|[Ww]hether)$/) !$+ VP)"
        });
  public static class ClausalComplementGRAnnotation extends GrammaticalRelationAnnotation { }


  /**
   * An open clausal complement (\emph{xcomp}) of a VP or an ADJP is a clausal
   * complement without its own subject, whose reference is determined by an
   * external subject.  These complements are always non-finite.
   * The name \emph{xcomp} is borrowed from Lexical-Functional Grammar.
   * (Only "TO-clause" are recognized.)
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
        XClausalComplementGRAnnotation.class, COMPLEMENT, "VP|ADJP",
        new String[] {
          "VP !> (VP < (VB|AUX < be)) < (S=target !$- (NN < /^order$/) < (VP < TO))",
          "ADJP < (S=target <, (VP <, TO))",
          "VP < (S=target !$- (NN < /^order$/) < (NP $+ NP|ADJP))",
          // to find "help sustain ...
          "VP < (/^VB|AUX/ $+ (VP=target < VB < NP))",
          "VP !> (VP < (VB|AUX < be)) < (SBAR=target < (S !$- (NN < /^order$/) < (VP < TO)))",
          "VP > VP < (S=target !$- (NN < /^order$/) <: NP)",
          // stop eating
          "VP < (S=target !< NP < (VP < VBG))"
        });
  public static class XClausalComplementGRAnnotation extends GrammaticalRelationAnnotation { }


  /**
   * The "complementizer" grammatical relation.  A
   * complementizer of a clausal complement is the word introducing it.
   * <p/>
   * <p/>
   * Example: <br/>
   * "He says that you like to swim" &rarr;
   * <code>complm</code>(like, that)
   */
  public static final GrammaticalRelation COMPLEMENTIZER =
    new GrammaticalRelation(Language.English, "complm", "complementizer",
        ComplementizerGRAnnotation.class, COMPLEMENT, "SBAR",
        new String[] {
          "SBAR <, (IN|DT=target < /^(that|whether)$/) $-- /^VB|AUXG?/",
          "SBAR <, (IN|DT=target < /^(that|whether)$/) $- NP",
          "SBAR <, (IN|DT=target < /^(that|whether)$/) > ADJP|PP",
          "SBAR <, (IN|DT=target < /^(That|Whether)$/)"
        });
  public static class ComplementizerGRAnnotation extends GrammaticalRelationAnnotation { }


  /**
   * The "marker" grammatical relation.  A
   * marker of an adverbial clausal complement is the word introducing it.
   * <p/>
   * Example: <br/>
   * "U.S. forces have been engaged in intense fighting after insurgents launched simultaneous attacks" &rarr;
   * <code>mark</code>(launched, after)
   */
  public static final GrammaticalRelation MARKER =
    new GrammaticalRelation(Language.English, "mark", "marker",
        MarkerGRAnnotation.class, COMPLEMENT, "^SBAR(?:-TMP)?$",
        new String[] {
          "/^SBAR(?:-TMP)?$/ <, (IN=target !< /(?i)^that|whether$/) < S|FRAG"
        });
  public static class MarkerGRAnnotation extends GrammaticalRelationAnnotation { }


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
   * relatives are analyzed as regular subject structures.
   */
  public static final GrammaticalRelation RELATIVE =
    new GrammaticalRelation(Language.English, "rel", "relative",
        RelativeGRAnnotation.class, COMPLEMENT, "SBAR",
        new String[] {
          "SBAR <, /^WH/=target > /^NP/ [ !<, /^WHNP/ | < (S < (VP $-- (/^NP/ !< /^-NONE-$/)))]"
        });
  public static class RelativeGRAnnotation extends GrammaticalRelationAnnotation { }


  /**
   * The "referent" grammatical relation.  A
   * referent of the head of a NP is  the relative word introducing the relative clause modifying the NP.
   * <p/>
   * Example: <br/>
   * "I saw the book which you bought" &rarr;
   * <code>ref</code>(book, which)
   */
  public static final GrammaticalRelation REFERENT =
    new GrammaticalRelation(Language.English, "ref", "referent",
        ReferentGRAnnotation.class, DEPENDENT, "NP",
        new String[] {
          "NP $+ (SBAR < (WHNP=target !< /^WP\\$/)) > NP",
          "NP $+ (SBAR < (WHPP < (WHNP=target !< /^WP\\$/))) > NP",
          "NP $+ (/^(,|PP|PRN)$/ $+ (SBAR < (WHNP=target !< /^WP\\$/)))",
          // to find referent for "the man, who I trust, ..." as well as referent in structure such as NP PP SBAR
          // !< /^WP\$/ is added to prevent something like "whose wife" to get a referent between the antecedent and "wife"
          // which is the head of the WHNP
          "NP $+ (/^(,|PP|PRN)$/ $+ (SBAR < (WHPP < (WHNP=target !< /^WP\\$/)))) > NP"
        });
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
        ExpletiveGRAnnotation.class, DEPENDENT, "S|SQ",
        new String[] {
          "S|SQ < (NP=target < EX)"
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
        AdjectivalComplementGRAnnotation.class, COMPLEMENT, "VP",
        new String[] {
          "VP < (ADJP=target !$-- NP)"
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
   * modifier of a VP is a clause modifying the verb (temporal clauses,
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
        AdvClauseModifierGRAnnotation.class, MODIFIER, "VP|S|SQ",
        new String[] {   // !$+ (NN < /^order$/) has been added so that "in order to" is not marked as an advcl
          "VP < (/^SBAR(?:-TMP)?$/=target <, (IN !< /^([Tt]hat|[Ww]hether)$/ !$+ (NN < /^order$/)))",
          "S|SQ <, (/^SBAR(?:-TMP)?$/=target <, (IN !< /^([Tt]hat|[Ww]hether)$/ !$+ (NN < /^order$/)) !$+ VP)",
          // to get "rather than"
          "S|SQ <, (/^SBAR(?:-TMP)?$/=target <2 (IN !< /^([Tt]hat|[Ww]hether)$/ !$+ (NN < /^order$/)))",
          "VP < (/^SBAR(?:-TMP)?$/=target <, (WHADVP|WHNP < (WRB !< /^[Hh]ow$/)) !< (S < (VP < TO)))", // added the (S < (VP <TO)) part so that "I tell them how to do so" doesn't get a wrong advcl
          "S|SQ <, (/^SBAR(?:-TMP)$/=target <, (WHADVP|WHNP < (WRB !< /^[Hh]ow$/)) !< (S < (VP < TO)))",
          "S|SQ <, (PP=target <, RB)"
        });
  public static class AdvClauseModifierGRAnnotation extends GrammaticalRelationAnnotation { }


  /**
   * The "purpose clause modifier" grammatical relation.  A purpose clause
   * modifier of a VP is a clause headed by "(in order) to" specifying a
   * purpose.  Note: at present we only recognize ones that have
   * "in order to" as otherwise we can't give our surface representations
   * distinguish these from xcomp's. We can also recognize "to" clauses
   * introduced by "be VBN".
   * <p/>
   * Example: <br/>
   * "He talked to the president in order to secure the account" &rarr;
   * <code>purpcl</code>(talked, secure)
   */
  public static final GrammaticalRelation PURPOSE_CLAUSE_MODIFIER =
    new GrammaticalRelation(Language.English, "purpcl", "purpose clause modifier",
        PurposeClauseModifierGRAnnotation.class, MODIFIER, "VP",
        new String[] {
          "VP < (/^SBAR/=target < (IN < in) < (NN < order) < (S < (VP < TO)))",
          "VP > (VP < (VB|AUX < be)) < (S=target !$- /^,$/ < (VP < TO|VBG) !$-- NP)"
        });
  public static class PurposeClauseModifierGRAnnotation extends GrammaticalRelationAnnotation { }


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
        TemporalModifierGRAnnotation.class, MODIFIER, "VP|S|ADJP",
        new String[] {
          "VP|ADJP < /^NP-TMP$/=target",
          "VP < (NP=target < (/^NN/ < " + timeWordRegex + "))",
          "S < (/^NP-TMP$/=target $++ (NP $++ VP))",
          "S < (NP=target < (/^NN/ < " + timeWordRegex + ") $++ (NP $++ VP))"
          });
  public static class TemporalModifierGRAnnotation extends GrammaticalRelationAnnotation { }


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
        RelativeClauseModifierGRAnnotation.class, MODIFIER, "NP",
        new String[] {
          "NP $++ (SBAR=target <+(SBAR) WHPP|WHNP) > NP",
          "NP $++ (SBAR=target <: (S !<, (VP <, TO))) > NP",
          // this pattern is restricted to where and why because "when" is usually incorrectly parsed: temporal clauses are put inside the NP; 2nd is for case of relative clauses with no relativizer (it doesn't distinguish whether actually gapped).
          "NP $++ (SBAR=target < (WHADVP < (WRB </^(where|why)/))) > NP",
          "NP $++ RRC=target"
        });
  public static class RelativeClauseModifierGRAnnotation extends GrammaticalRelationAnnotation { }


  /**
   * The "adjectival modifier" grammatical relation.  An adjectival
   * modifier of an NP is any adjectival phrase that serves to modify
   * the meaning of the NP.<p>
   * <p/>
   * Example: <br/>
   * "Sam eats red meat" &rarr;
   * <code>amod</code>(meat, red)
   */
  public static final GrammaticalRelation ADJECTIVAL_MODIFIER =
    new GrammaticalRelation(Language.English, "amod", "adjectival modifier",
        AdjectivalModifierGRAnnotation.class, MODIFIER, "^NP(?:-TMP|-ADV)?|NX|WHNP$",
        new String[] {
          "/^NP(?:-TMP|-ADV)?|NX|WHNP$/ < (ADJP|WHADJP|JJ|JJR|JJS|VBN|VBG|VBD=target !< QP !$- CC)"
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
        NumericModifierGRAnnotation.class, MODIFIER, "NP(?:-TMP|-ADV)?",
        new String[] {
          "/^NP(?:-TMP|-ADV)?$/ < (CD|QP=target !$- CC)",
          "/^NP(?:-TMP|-ADV)?$/ < (ADJP=target <: QP)"
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
        NumberModifierGRAnnotation.class, MODIFIER, "QP",
        new String[] {
          "QP < (CD=target !$- CC)"
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
        QuantifierModifierGRAnnotation.class, MODIFIER, "QP",
        new String[] {
          "QP < /^IN|RB|DT|JJ|XS$/=target"
        });
  public static class QuantifierModifierGRAnnotation extends GrammaticalRelationAnnotation { }


  /**
   * The "noun compound modifier" grammatical relation.  A noun compound
   * modifier of an NP is any noun that serves to modify the head noun.
   * Note that this has all nouns modify the rightmost a la Penn headship
   * rules.  There is no intelligent noun compound analysis. <p>
   * <p/>
   * Example: <br/>
   * "Oil price futures" &rarr;
   * <code>nn</code>(futures, oil),
   * <code>nn</code>(futures, price)
   */
  public static final GrammaticalRelation NOUN_COMPOUND_MODIFIER =
    new GrammaticalRelation(Language.English, "nn", "nn modifier",
        NounCompoundModifierGRAnnotation.class, MODIFIER, "^NP(?:-TMP|-ADV)?$",
        new String[] {
          "/^NP(?:-TMP|-ADV)?$/ < (NP|NN|NNS|NNP|NNPS|FW=target $++ NN|NNS|NNP|NNPS|FW|CD !<- POS !$- /^,$/ )",
          "/^NP(?:-TMP|-ADV)?$/ < (NP|NN|NNS|NNP|NNPS|FW=target !<- POS $+ JJ|JJR|JJS) <# NN|NNS|NNP|NNPS !<- POS"
        });
  public static class NounCompoundModifierGRAnnotation extends GrammaticalRelationAnnotation { }


  /**
   * The "appositional modifier" grammatical relation.  An appositional
   * modifier of an NP is an NP that serves to modify
   * the meaning of the NP.  It includes parenthesized examples
   * <p/>
   * Examples: <br/>
   * "Sam, my brother, eats red meat" &rarr;
   * <code>appos</code>(Sam, brother) <br/>
   * "Bill (John's cousin)" &rarr; <code>appos</code>(Bill, cousin)
   */
  public static final GrammaticalRelation APPOSITIONAL_MODIFIER =
    new GrammaticalRelation(Language.English, "appos", "appositional modifier",
        AppositionalModifierGRAnnotation.class, MODIFIER, "^NP(?:-TMP|-ADV)?$",
        new String[] {
          "/^NP(?:-TMP|-ADV)?$/ < (NP=target $- /^,$/ $-- NP !$ CC|CONJP)",
          "/^NP(?:-TMP|-ADV)?$/ < (PRN=target < (NP < /^NNS?|CD$/ $-- /^-LRB-$/ $+ /^-RRB-$/))",
          // TODO
          // last pattern with NNP doesn't work because leftmost NNP is deemed head in a
          // structure like (NP (NNP Norway) (, ,) (NNP Verdens_Gang) (, ,))
          "/^NP(?:-TMP|-ADV)?$/ < (NNP $+ (/^,$/ $+ NNP=target)) !< CC|CONJP"
        });
  public static class AppositionalModifierGRAnnotation extends GrammaticalRelationAnnotation { }


  /**
   * The "abbreviation modifier" grammatical relation.
   * An abbreviation modifier of an NP is a parenthesized NP that serves to
   * abbreviate the NP (or to define an abbreviation).
   * Abbreviations are recognized either by being deemed proper nouns or
   * by matching a regex pattern.<p>
   * <p/>
   * Example: <br/>
   * "The Australian Broadcasting Corporation (ABC)" &rarr;
   * <code>abbrev</code>(Corporation, ABC)
   */
  public static final GrammaticalRelation ABBREVIATION_MODIFIER =
    new GrammaticalRelation(Language.English, "abbrev", "abbreviation modifier",
        AbbreviationModifierGRAnnotation.class, APPOSITIONAL_MODIFIER, "^NP(?:-TMP|-ADV)?$",
        // for biomedical English, the former NNP heuristic really doesn't work, because they use NN for all chemical entities
        // while not unfoolable, this version produces less false positives and more true positives.
        new String[] {
          "/^NP(?:-TMP|-ADV)?$/ < (PRN=target <, /^-LRB-$/ <- /^-RRB-$/ !<< /^(?:POS|PRP\\$|[,$#]|CC|RB|CD)$/ <+(NP) (NNP|NN < /^(?:[A-Z]\\.?){2,}/) )"
        });

  public static class AbbreviationModifierGRAnnotation extends GrammaticalRelationAnnotation { }


  /**
   * The "participial modifier" grammatical relation.  A participial
   * modifier of an NP or VP is a VP[part] that serves to modify
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
        ParticipialModifierGRAnnotation.class, MODIFIER, "^NP(?:-TMP|-ADV)?|VP|S$",
        new String[] {
          "/^NP(?:-TMP|-ADV)?$/ < (VP=target < VBG|VBN $-- NP)",
          // to get "MBUSA, headquarted ..."
          "/^NP(?:-TMP|-ADV)?$/ < (/^,$/ $+ (VP=target <, VBG|VBN))",
          // to get "John, knowing ..., announced "
          "S <, (NP $+ (/^,$/ $+ (S=target < (VP <, VBG|VBN))))"
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
        InfinitivalModifierGRAnnotation.class, MODIFIER, "^NP(?:-TMP|-ADV)?$",
        new String[] {
          "/^NP(?:-[A-Z]+)?$/ < (S=target < (VP < TO) $-- /^NP|NNP?S?$/)",
          "/^NP(?:-[A-Z]+)?$/ < (SBAR=target < (S < (VP < TO)) $-- /^NP|NNP?S?$/)"
        });
  public static class InfinitivalModifierGRAnnotation extends GrammaticalRelationAnnotation { }

  // match "not", "n't", or "never" as _complete_ string
  private static final String NOT_PAT = "/^(?i:not|n't|never)$/";

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
        "VP|ADJP|WHADJP|ADVP|WHADVP|S|SBAR|SINV|SQ|SBARQ|XS|NP(?:-TMP|-ADV)?|RRC",
        new String[] {
          "/^VP|ADJP|WHADJP|S|SBAR|SINV|SQ|XS|NP(?:-TMP|-ADV)?|RRC$/ < (RB|RBR|RBS|WRB|ADVP|WHADVP=target !< " + NOT_PAT + ")",
          // avoids adverb conjunctions matching as advmod; added JJ to catch How long
          "ADVP|WHADVP < (RB|RBR|RBS|WRB|ADVP|WHADVP|JJ=target !< " + NOT_PAT + ") !< CC !< CONJP",
        //this one gets "at least" advmod(at, least) or "fewer than" advmod(than, fewer)
          "SBAR < (WHNP=target < WRB)", "SBARQ <, WHADVP=target", "XS < /^JJ$/=target"
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
        "VP|ADJP|S|SBAR|SINV|SQ|NP(?:-TMP|-ADV)?|FRAG",
        new String[] {
          "/^VP|NP(?:-TMP|-ADV)?|ADJP|SQ|S|FRAG$/< (RB=target < " + NOT_PAT + ")",
          "VP|ADJP|S|SBAR|SINV|FRAG < (ADVP=target <# (RB < " + NOT_PAT + "))",
          "VP > SQ $-- (RB=target < " + NOT_PAT + ")"
        });
  public static class NegationModifierGRAnnotation extends GrammaticalRelationAnnotation { }


  /**
   * The "measure-phrase" grammatical relation. The measure-phrase is the relation between
   * the head of an ADJP/ADVP and the head of a measure-phrase modifying the ADJP/ADVP.
   * <p/>
   * Example: <br/>
   * "The director is 65 years old" &rarr;
   * <code>measure</code>(old, years)
   */
  public static final GrammaticalRelation MEASURE_PHRASE =
    new GrammaticalRelation(Language.English, "measure", "measure-phrase",
        MeasurePhraseGRAnnotation.class, MODIFIER, "ADJP|ADVP",
        new String[] {
          "ADJP <- JJ <, (NP=target !< NNP)",
          "ADVP|ADJP <# (/^JJ|IN$/ $- NP=target)"
        });
  public static class MeasurePhraseGRAnnotation extends GrammaticalRelationAnnotation { }


  /**
   * The "determiner" grammatical relation.
   * <p> <p/>
   * Examples: <br/>
   * "The man is here" &rarr; <code>det</code>(man,the) <br/>
   * "Which man do you prefer?" &rarr; <code>det</code>(man,which)
   */
  public static final GrammaticalRelation DETERMINER =
    new GrammaticalRelation(Language.English, "det", "determiner",
        DeterminerGRAnnotation.class, MODIFIER, "^NP(?:-TMP|-ADV)?|WHNP",
        new String[] {
          "/^NP(?:-TMP|-ADV)?$/ < (DT=target !</both|either|neither/ !$- DT !$++ CC $++ /^N[NX]/)",
          "/^NP(?:-TMP|-ADV)?$/ < (DT=target < /both|either|neither/ !$- DT !$++ CC $++ /^N[NX]/ !$++ (NP < CC))",
          "/^NP(?:-TMP|-ADV)?$/ < (DT=target !< /both|neither|either/ $++ CC $++ /^N[NX]/)",
          "/^NP(?:-TMP|-ADV)?$/ < (DT=target $++ (/^JJ/ !$+ /^NN/) !$++CC)",
          "/^NP(?:-TMP|-ADV)?$/ < (RB=target $++ (/PDT/ $+ /^NN/))",
          "WHNP < (NP $-- (WHNP=target < WDT))",
          "WHNP < (/^NN/ $-- WDT|WP=target)"
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
        PredeterminerGRAnnotation.class, MODIFIER, "^NP(?:-TMP|-ADV)?",
        new String[] {
          "/^NP(?:-TMP|-ADV)?$/ < (PDT|DT=target $+ /DT|PRP\\$/ $++ /^N[NX]/ !$++ CC)",
          "/^NP(?:-TMP|-ADV)?$/ < (PDT|DT=target $+ DT $++ (/^JJ/ !$+ /^NN/)) !$++ CC",
          "/^NP(?:-TMP|-ADV)?$/ < PDT=target <- DT"
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
        "S|VP|ADJP|PP|ADVP|UCP|NX|SBAR|^NP(?:-TMP|-ADV)?",
        new String[] {
          "/^NP(?:-TMP|-ADV)?|NX$/ < (PDT|CC=target < /Both|both|Neitehr|neither|Either|either/ $++ /^N[NX]/) $++ CC",
          "/^NP(?:-TMP|-ADV)?|NX$/ < (PDT|CC=target < /Both|both|Neitehr|neither|Either|either/ $++ (/^JJ/ !$+ /^NN/)) $++ CC",
          "/^NP(?:-TMP|-ADV)?|NX$/ < (PDT|CC|DT=target < /Both|both|Neitehr|neither|Either|either/ $++ CC)",
          "/^NP(?:-TMP|-ADV)?|NX$/ < (PDT|CC|DT=target </Both|both|Neitehr|neither|Either|either/) < (NP < CC)",
          "S|VP|ADJP|PP|ADVP|UCP|NX|SBAR < (PDT|DT|CC=target < /Both|both|Neitehr|neither|Either|either/ $++ CC)"
        });
  public static class PreconjunctGRAnnotation extends GrammaticalRelationAnnotation { }


  /**
   * The "possession" grammatical relation.<p>
   * </p>
   * Examples: <br/>
   * "their offices" &rarr;
   * <code>poss</code>(offices, their)<br/>
   * "Bill 's clothes" &rarr;
   * <code>poss</code>(clothes, Bill)
   */
  public static final GrammaticalRelation POSSESSION_MODIFIER =
    new GrammaticalRelation(Language.English, "poss", "possession modifier",
        PossessionModifierGRAnnotation.class, MODIFIER, "^NP(?:-TMP|-ADV)?$",
        new String[] {
          "/^NP(?:-TMP|-ADV)?$/ < (/^PRP\\$/=target $++ /^NN/)",
          "/^NP(?:-TMP|-ADV)?$/ < (NP=target < POS)",
          "/^NP(?:-TMP|-ADV)?$/ < (NNS=target $+ (POS < /'/))"
        });
  public static class PossessionModifierGRAnnotation extends GrammaticalRelationAnnotation { }


  /**
   * The "possessive" grammatical relation.<p>
   * </p>
   * Example: <br/>
   * "John's book" &rarr;
   * <code>possessive</code>(John, 's)
   */
  public static final GrammaticalRelation POSSESSIVE_MODIFIER =
    new GrammaticalRelation(Language.English, "possessive", "possessive modifier",
        PossessiveModifierGRAnnotation.class, MODIFIER, "^NP(?:-TMP|-ADV)?$",
        new String[] {
          "/^NP(?:-TMP|-ADV)?$/ <- POS=target"
        });
  public static class PossessiveModifierGRAnnotation extends GrammaticalRelationAnnotation { }


  /**
   * The "prepositional modifier" grammatical relation.  A prepositional
   * modifier of a verb, adjective, or noun is any prepositional phrase that serves to modify
   * the meaning of the verb, adjective, or noun.<p>
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
        PrepositionalModifierGRAnnotation.class, MODIFIER, "NP(?:-TMP|-ADV)?|VP|S|SINV|ADJP|SBARQ|NAC",
        new String[] {
          "/^NP(?:-TMP|-ADV)?|VP|ADJP|NAC$/ < /^PP(?:-TMP)?$/=target",
          "S|SINV < (/^PP(?:-TMP)?$/=target !< SBAR) < VP",
          "SBARQ < /^WHPP/=target < SQ"
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
        PhrasalVerbParticleGRAnnotation.class, MODIFIER, "VP",
        new String[] {
          "VP < PRT=target"
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
        ParataxisGRAnnotation.class, DEPENDENT, "S|VP",
        new String[]{
          "VP < (PRN=target < /^S|SBAR$/)", // parenthetical
          "VP $ (PRN=target < /^S|SBAR$/)", // parenthetical
          "S|VP < (/^:$/ $+ /^S/=target)",  // colon between sentences

        });

  public static class ParataxisGRAnnotation extends GrammaticalRelationAnnotation { }

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
        ControllingSubjectGRAnnotation.class, SEMANTIC_DEPENDENT, "VP",
        new String[] {
          "VP < TO > (S !$- NP !< NP !>> (VP < (VB|AUX < be)) >+(VP) (VP $-- NP=target))"
        });
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
      COMPLEMENTIZER,
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
      ABBREVIATION_MODIFIER,
      PARTICIPIAL_MODIFIER,
      INFINITIVAL_MODIFIER,
      ADVERBIAL_MODIFIER,
      NEGATION_MODIFIER,
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
      PURPOSE_CLAUSE_MODIFIER,
      QUANTIFIER_MODIFIER,
      MEASURE_PHRASE,
      PARATAXIS
    }));
  /* Cache frequently used views of the values list */
  private static final List<GrammaticalRelation> unmodifiableValues =
    Collections.unmodifiableList(values);
  private static final List<GrammaticalRelation> synchronizedValues =
    Collections.synchronizedList(values);
  private static final List<GrammaticalRelation> unmodifiableSynchronizedValues =
    Collections.unmodifiableList(values);
  public static final ReadWriteLock valuesLock = new ReentrantReadWriteLock();


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
    } finally {
      valuesLock.writeLock().unlock();
    }
  }



  // TODO would be nice to have PREPOSITION and CONJUNCTION parents for the below

  // the exhaustive list of conjunction relations
  private static final Map<String, GrammaticalRelation> conjs = Generics.newHashMap();

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
      result = new GrammaticalRelation(Language.English, "conj", "conj_collapsed", null, DEPENDENT, conjunctionString);
      conjs.put(conjunctionString, result);
      threadSafeAddRelation(result);
    }
    return result;
  }

  // the exhaustive list of preposition relations
  private static final Map<String, GrammaticalRelation> preps = Generics.newHashMap();
  private static final Map<String, GrammaticalRelation> prepsC = Generics.newHashMap();


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
   * @param prepositionString The presposition to make a GrammaticalRelation out of
   * @return A grammatical relation for this presposition
   */
  public static GrammaticalRelation getPrep(String prepositionString) {
    GrammaticalRelation result = preps.get(prepositionString);
    if (result == null) {
      result = new GrammaticalRelation(Language.English, "prep", "prep_collapsed", null, DEPENDENT, prepositionString);
      preps.put(prepositionString, result);
      threadSafeAddRelation(result);
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
   * @param prepositionString The presposition to make a GrammaticalRelation out of
   * @return A grammatical relation for this presposition
   */
  public static GrammaticalRelation getPrepC(String prepositionString) {
    GrammaticalRelation result = prepsC.get(prepositionString);
    if (result == null) {
      result = new GrammaticalRelation(Language.English, "prepc", "prepc_collapsed", null, DEPENDENT, prepositionString);
      prepsC.put(prepositionString, result);
      threadSafeAddRelation(result);
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
