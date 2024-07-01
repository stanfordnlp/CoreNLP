// EnglishGrammaticalStructureTest -- unit tests for Stanford dependencies.
// Copyright (c) 2005, 2011, 2013 The Board of Trustees of
// The Leland Stanford Junior University. All Rights Reserved.
//
// This program is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License
// as published by the Free Software Foundation; either version 2
// of the License, or (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
//
// For more information, bug reports, fixes, contact:
//    Christopher Manning
//    Dept of Computer Science, Gates 1A
//    Stanford CA 94305-9010
//    USA
//    Support/Questions: parser-user@lists.stanford.edu
//    Licensing: parser-support@lists.stanford.edu

package edu.stanford.nlp.trees;

import org.junit.Test;
import static org.junit.Assert.assertEquals;


/** Test cases for English typed dependencies (Stanford dependencies).
 *
 *  @author Marie-Catherine de Marneffe (mcdm)
 *  @author Christopher Manning
 *  @author John Bauer
 */
public class EnglishGrammaticalStructureTest {


  /**
   * Tests that we can extract the basic grammatical relations correctly from
   * some hard-coded trees.
   *
   * Sentence examples from the manual to at least test each relation.
   *
   */
  @Test
  public void testBasicRelations() {
    // the trees to test
    String[] testTrees = {
         "(ROOT (S (NP (NNP Reagan)) (VP (VBZ has) (VP (VBN died))) (. .)))",
         "(ROOT (S (NP (NNP Kennedy)) (VP (VBZ has) (VP (VBN been) (VP (VBN killed)))) (. .)))",
         "(ROOT (S (NP (NNP Bill)) (VP (VBZ is) (NP (DT an) (JJ honest) (NN man))) (. .)))",
         "(ROOT (S (NP (NNP Bill)) (VP (VBZ is) (ADJP (JJ big) (CC and) (JJ honest))) (. .)))",
         "(ROOT (S (NP (NNP Clinton)) (VP (VBD defeated) (NP (NNP Dole))) (. .)))",
         "(ROOT (S (SBAR (WHNP (WP What)) (S (NP (PRP she)) (VP (VBD said)))) (VP (VBZ is) (ADJP (JJ untrue))) (. .)))",
         "(ROOT (S (NP (NNP Dole)) (VP (VBD was) (VP (VBN defeated) (PP (IN by) (NP (NNP Clinton))))) (. .)))",
         "(ROOT (S (SBAR (IN That) (S (NP (PRP she)) (VP (VBD lied)))) (VP (VBD was) (VP (VBN suspected) (PP (IN by) (NP (NN everyone))))) (. .)))",
         "(ROOT (S (NP (PRP She)) (VP (VBD gave) (NP (PRP me)) (NP (DT a) (NN raise))) (. .)))",
         "(ROOT (S (NP (PRP I)) (VP (VBP like) (S (VP (TO to) (VP (VB swim))))) (. .)))",
         "(ROOT (S (NP (PRP I)) (VP (VBD sat) (PP (IN on) (NP (DT the) (NN chair)))) (. .)))",
         "(ROOT (S (NP (PRP We)) (VP (VBP have) (NP (NP (DT no) (JJ useful) (NN information)) (PP (IN on) (SBAR (IN whether) (S (NP (NNS users)) (VP (VBP are) (PP (IN at) (NP (NN risk))))))))) (. .)))",
         "(ROOT (S (NP (PRP They)) (VP (VBD heard) (PP (IN about) (NP (NN asbestos))) (S (VP (VBG having) (NP (JJ questionable) (NNS properties))))) (. .)))",
         "(ROOT (S (NP (PRP He)) (VP (VBZ says) (SBAR (IN that) (S (NP (PRP you)) (VP (VBP like) (S (VP (TO to) (VP (VB swim)))))))) (. .)))",
         "(ROOT (S (NP (PRP I)) (VP (VBP am) (ADJP (JJ certain) (SBAR (IN that) (S (NP (PRP he)) (VP (VBD did) (NP (PRP it))))))) (. .)))",
         "(ROOT (S (NP (PRP I)) (VP (VBP am) (ADJP (JJ ready) (S (VP (TO to) (VP (VB leave)))))) (. .)))",
         "(ROOT (S (NP (NNP U.S.) (NNS forces)) (VP (VBP have) (VP (VBN been) (VP (VBN engaged) (PP (IN in) (NP (JJ intense) (NN fighting))) (SBAR (IN after) (S (NP (NNS insurgents)) (VP (VBD launched) (NP (JJ simultaneous) (NNS attacks)))))))) (. .)))",
         "(ROOT (S (NP (PRP I)) (VP (VBD saw) (NP (NP (DT the) (NN man)) (SBAR (WHNP (WP who)) (S (NP (PRP you)) (VP (VBP love)))))) (. .)))",
         "(ROOT (S (NP (PRP I)) (VP (VBD saw) (NP (NP (DT the) (NN man)) (SBAR (WHNP (WP$ whose) (NP (NN wife))) (S (NP (PRP you)) (VP (VBP love)))))) (. .)))", // wrong but common misparse
         "(ROOT (S (NP (PRP I)) (VP (VBD saw) (NP (NP (DT the) (NN man)) (SBAR (WHNP (WP$ whose) (NN wife)) (S (NP (PRP you)) (VP (VBP love)))))) (. .)))",
         "(ROOT (S (NP (EX There)) (VP (VBZ is) (NP (NP (DT a) (NN statue)) (PP (IN in) (NP (DT the) (NN corner))))) (. .)))",
         "(ROOT (S (NP (PRP She)) (VP (VBZ looks) (ADJP (RB very) (JJ beautiful))) (. .)))",
         "(ROOT (S (NP (DT The) (NN accident)) (VP (VBD happened) (SBAR (IN as) (S (NP (DT the) (NN night)) (VP (VBD was) (VP (VBG falling)))))) (. .)))",
         "(ROOT (S (SBAR (IN If) (S (NP (PRP you)) (VP (VBP know) (SBAR (WHNP (WP who)) (S (VP (VBD did) (NP (PRP it)))))))) (, ,) (NP (PRP you)) (VP (MD should) (VP (VB tell) (NP (DT the) (NN teacher)))) (. .)))",
         "(ROOT (S (NP-TMP (JJ Last) (NN night)) (, ,) (NP (PRP I)) (VP (VBP swam) (PP (IN in) (NP (DT the) (NN pool)))) (. .)))",
         "(ROOT (S (NP (PRP He)) (VP (VBD talked) (PP (TO to) (NP (DT the) (NN president))) (SBAR (IN in) (NN order) (S (VP (TO to) (VP (VB secure) (NP (DT the) (NN account))))))) (. .)))",
         "(ROOT (S (NP (PRP I)) (VP (VBD saw) (NP (NP (DT the) (NN book)) (SBAR (WHNP (WDT which)) (S (NP (PRP you)) (VP (VBD bought)))))) (. .)))",
         "(ROOT (S (NP (NNP Sam)) (VP (VBZ eats) (NP (CD 3) (NN sheep))) (. .)))",
         "(ROOT (S (NP (PRP I)) (VP (VBD lost) (NP (QP ($ $) (CD 3.2) (CD billion)))) (. .)))",
         "(ROOT (S (NP (QP (RB About) (CD 200)) (NNS people)) (VP (VBD came) (PP (TO to) (NP (DT the) (NN party)))) (. .)))",
         "(ROOT (S (NP (NP (NNP Sam)) (, ,) (NP (PRP$ my) (NN brother)) (, ,)) (VP (VBZ eats) (NP (JJ red) (NN meat))) (. .)))",
         "(ROOT (NP (NP (DT The) (JJ Australian) (NNP Broadcasting) (NNP Corporation)) (PRN (-LRB- -LRB-) (NP (NNP ABC)) (-RRB- -RRB-)) (. .)))",
         "(ROOT (S (NP (NNP Bill)) (VP (VBD picked) (NP (NP (NNP Fred)) (PP (IN for) (NP (NP (DT the) (NN team)) (VP (VBG demonstrating) (NP (PRP$ his) (NN incompetence))))))) (. .)))",
         "(ROOT (S (NP (NNP Bill)) (VP (VBZ is) (RB not) (NP (DT a) (NN scientist))) (. .)))",
         "(ROOT (S (NP (NNP Bill)) (VP (VBZ does) (RB n't) (VP (VB drive))) (. .)))",
         "(ROOT (S (NP (DT The) (NN director)) (VP (VBZ is) (ADJP (NP (CD 65) (NNS years)) (JJ old))) (. .)))",
         "(ROOT (S (NP (DT The) (NN man)) (VP (VBZ is) (ADVP (RB here))) (. .)))",
         "(ROOT (SBARQ (WHPP (IN In) (WHNP (WDT which) (NN city))) (SQ (VBP do) (NP (PRP you)) (VP (VB live))) (. ?)))",
         "(ROOT (SBARQ (WHNP (WP What)) (SQ (VBD did) (NP (NNP Charles) (NNP Babbage)) (VP (VB invent))) (? ?)))",
         "(ROOT (SBARQ (WHNP (WP What)) (SQ (VBZ is) (NP (DT the) (NN esophagus)) (VP (VBN used) (PP (IN for)))) (? ?)))",
         "(ROOT (S (NP (PDT All) (DT the) (NNS boys)) (VP (VBP are) (ADVP (RB here))) (. .)))",
         "(ROOT (S (NP (CC Both) (NP (DT the) (NNS boys)) (CC and) (NP (DT the) (NNS girls))) (VP (VBP are) (ADVP (RB here))) (. .)))",
         "(ROOT (S (NP (PRP They)) (VP (VBD shut) (PRT (RP down)) (NP (DT the) (NN station))) (. .)))",
         "(ROOT (S (NP (NP (NNS Truffles)) (VP (VBN picked) (PP (IN during) (NP (DT the) (NN spring))))) (VP (VBP are) (ADJP (JJ tasty))) (. .)))",
         "(ROOT (S  (NP-SBJ-38 (DT Neither) (NP (PRP they) ) (CC nor) (NP (NNP Mr.) (NNP McAlpine) )) (VP (MD could) (VP (VB be) (VP (VBN reached) (NP (-NONE- *-38) ) (PP-PRP (IN for) (NP (NN comment) ))))) (. .) ))",
         "(ROOT (S (NP (NNP Xml) (NN field)) (VP (MD should) (VP (VB include) (NP (PDT both) (NP (DT the) (NN entity) (NN id)) (CC and) (NP (DT the) (NN entity) (NN name))) (SBAR (IN since) (S (NP (DT the) (NN entity) (NNS names)) (VP (VBP are) (RB not) (ADJP (JJ unique))))))) (. .)))",
         "(ROOT (S (S (NP (DT The) (NN government)) (VP (VBZ counts) (NP (NN money)) (SBAR (IN as) (S (NP (PRP it)) (VP (VBZ is) (VP (VBN spent))))))) (: ;) (S (NP (NNP Dodge)) (VP (VBZ counts) (NP (NNS contracts)) (SBAR (WHADVP (WRB when)) (S (NP (PRP they)) (VP (VBP are) (VP (VBN awarded))))))) (. .)))",
         "( (S (CC But) (NP (PRP she)) (VP (VBD did) (RB n't) (VP (VB deserve) (S (VP (TO to) (VP (VB have) (S (NP (PRP$ her) (NN head)) (VP (VBN chopped) (PRT (RP off))))))))) (. .)))",
         "( (S (NP (PRP I)) (VP (VBP like) (NP (NP (NNS dogs)) (CONJP (RB rather) (IN than)) (NP (NNS cats)))) (. .)))",
         "( (S (NP (PRP I)) (VP (VBP like) (NP (NP (NN brandy)) (CONJP (RB not) (TO to) (VB mention)) (NP (NN cognac)))) (. .)))",
         "( (S (NP (PRP I)) (VP (VBP like) (NP (CONJP (RB not) (RB only)) (NP (NNS cats)) (CONJP (CC but) (RB also)) (NP (NN dogs)))) (. .)))",
         "( (S (NP (PRP He)) (VP (VBZ knows) (NP (DT the) (NML (JJ mechanical) (NN engineering)) (NN industry))) (. .)))",
         "( (SBARQ (WHNP (WP What) (NN weapon)) (SQ (VBZ is) (NP (DT the) (JJ mythological) (NN character) (NN Apollo)) (ADJP (RBS most) (JJ proficient) (PP (IN with)))) (. ?)))",   // "proficient" should be the head
         "( (SINV (CC Nor) (VBP are) (NP (PRP you)) (ADJP (JJ free) (S (VP (TO to) (VP (VB reprint) (NP (JJ such) (NN material))))))) )",
         "(ROOT (SBARQ (WHNP (WHADJP (WRB How) (JJ many)) (NP (NNP James) (NNP Bond) (NNS novels))) (SQ (VBP are) (NP (EX there))) (. ?)))",
         "( (S (NP (NP (NNS Investments)) (PP (IN in) (NP (NNP South) (NNP Africa)))) (VP (MD will) (VP (VB be) (VP (VBN excluded)))) (. .)))",
         "( (SINV (ADVP (RB Also)) (VP (VBN excluded)) (VP (MD will) (VP (VB be))) (NP (NP (NNS investments)) (PP (IN in) (NP (NNP South) (NNP Africa)))) (. .)))",
         "( (SINV (VP (VBG Defending) (NP (PRP$ their) (NNS ramparts))) (VP (VBP are)) (NP (NP (NNP Wall) (NNP Street) (POS 's)) (NNP Old) (NNP Guard)) (. .)))",
         "( (S (NP-SBJ (JJ Institutional) (NNS investors)) (ADVP (RB mostly)) (VP (VBD remained) (PP-LOC-PRD (IN on) (NP (DT the) (NNS sidelines))) (NP-TMP (NNP Tuesday))) (. .)))",
         "( (SQ (VBZ Is) (NP-SBJ (DT this)) (NP-PRD (NP (DT the) (NN future)) (PP (IN of) (NP (NN chamber) (NN music)))) (. ?)))",
         "( (SQ (VBZ Is) (NP-SBJ (DT the) (NN trouble)) (ADVP-PRD (RP over)) (. ?)))",
         "( (SBARQ (SBAR (IN Although) (S (NP (NNP Sue)) (VP (VBP is) (ADJP (JJ smart))))) (, ,) (WHNP (WP who)) (SQ (MD will) (VP (VB win))) (. ?)))",
         "(NP (NP (NNP Xerox))(, ,) (SBAR (WHNP (WHNP (WP$ whose) (JJ chief) (JJ executive) (NN officer))(, ,) (NP (NNP James) (NNP Gatward))(, ,)) (S (NP-SBJ (-NONE- *T*-1)) (VP (VBZ has) (VP (VBN resigned))))))",
         "(ROOT (S (NP (PRP He)) (VP (VBZ gets) (NP (PRP me)) (ADVP-TMP (DT every) (NN time))) (. .)))",
         "( (S (NP-SBJ (CC Both) (NP (NNP Mr.) (NNP Parenteau)) (CC and) (NP (NNP Ms.) (NNP Doyon))) (, ,) (ADVP (RB however)) (, ,) (VP (VBD were) (VP (VBG bleeding) (ADVP (RB badly)))) (. .)))",
         // This pattern of ADJP < RP without an intervening PRT occurs in the Web Treebank...
         "(NP-SBJ-1 (ADJP (ADJP (VBN Rusted) (RP out)) (CC and) (ADJP (JJ unsafe))) (NNS cars))",
         "( (S (NP-SBJ (PRP u)) (VP (VBP r) (VP (VBG holding) (NP (PRP it)) (ADVP (RB too) (RB tight))))))",
         "( (S (NP-SBJ (PRP You)) (VP (MD should) (VP (GW e) (VB mail) (NP (PRP her)) (ADVP-TMP (RB sometimes)))) (. .)))",
         "( (S (NP-SBJ (NN Interest)) (VP (VBZ is) (ADJP-PRD (ADJP (NP-ADV (DT a) (JJ great) (NN deal)) (JJR higher)) (SBAR (IN than) (S (NP-SBJ (PRP it)) (VP (VBD was) (ADJP-PRD (-NONE- *?*)) (ADVP-TMP (NP (DT a) (NN year)) (RB ago))))))) (. .)))",
         "( (S (NP-SBJ (DT The) (NN strike)) (VP (MD may) (VP (VB have) (VP (VBN ended) (SBAR-TMP (ADVP (RB almost)) (IN before) (S (NP-SBJ (PRP it)) (VP (VBD began)))))))))",
         "( (S (SBAR-ADV (IN Although) (S (VP (VBN set) (PP-LOC (IN in) (NP (NNP Japan)))))) (, ,) (NP-SBJ-2 (NP (DT the) (NN novel) (POS 's)) (NN texture)) (VP (VBZ is) (ADJP (JJ American))) (. .)))",
         "( (S-IMP (INTJ (UH please)) (NP-SBJ (-NONE- *PRO*)) (VP (VB specify) (NP (WDT which) (NML (NNP royal) (CC or) (NNP carnival)) (NN ship))) (NFP -LRB-:)))",
         "(NP (DT those) (RRC (ADVP-TMP (RB still)) (PP-LOC (IN under) (NP (NNP GASB) (NNS rules)))))",
         "(NP (NP (DT the) (NN auction) (NN house)) (RRC (RRC (VP (VBN founded) (NP (-NONE- *)) (PP-LOC (IN in) (NP (NNP London))) (NP-TMP (CD 1744)))) (CC and) (RRC (ADVP-TMP (RB now)) (PP (IN under) (NP (NP (DT the) (NN umbrella)) (PP (IN of) (NP (NP (NNP Sotheby) (POS 's)) (NNPS Holdings) (NNP Inc.))))))))",
         // tough movement example
         "(S (NP-SBJ (NNS morcillas)) (VP (VBP are) (ADVP (RB basically)) (ADJP-PRD (JJ impossible) (SBAR (WHNP-1 (-NONE- *0*)) (S (NP-SBJ (-NONE- *PRO*)) (VP (TO to) (VP (VB find) (NP-1 (-NONE- *T*)) (PP-LOC (IN in) (NP (NNP California))))))))))",
         // S parataxis
         "( (S (S (NP-SBJ (-NONE- *)) (VP (VBP Do) (RB n't) (VP (VB wait)))) (: --) (S (NP-SBJ (-NONE- *)) (VP (VBP act) (ADVP-TMP (RB now)))) (. !)))",
         // Two tricky conjunctions with punctuation and/or interjections
         "( (S (NP-SBJ (DT The) (NNPS Parks) (NNP Council)) (VP (VBD wrote) (NP (DT the) (NNP BPCA)) (SBAR (IN that) (S (NP-SBJ (DT this) (ADJP (`` ``) (RB too) (`` `) (JJ private) ('' ') (: ...) (JJ exclusive) (, ,) ('' '') (JJ complex) (CC and) (JJ expensive)) (`` ``) (VBN enclosed) (NN garden)) (: ...) (VP (VBZ belongs) (PP-LOC-CLR (IN in) (NP (NP (RB almost) (DT any) (NN location)) (CC but) (NP (DT the) (NN waterfront)))))))) (. .) ('' '')))",
         "( (S (`` ``) (CC And) (NP-SBJ (PRP you)) (VP (MD ca) (RB n't) (VP (VB have) (S (NP-SBJ (NP (NNS taxpayers)) (VP (VBG coming) (PP-DIR (IN into) (NP (DT an) (NN audit))))) (VP (VBG hearing) (NP (`` `) (UH oohs) (: ') (CC and) (`` `) (UH ahs)))))) (. .) ('' ') ('' '')))",
         "( (S (NP-SBJ-1 (VBN Freed) (JJ black) (NNS nationalists)) (VP (VP (VBD resumed) (NP (JJ political) (NN activity)) (PP-LOC (IN in) (NP (NNP South) (NNP Africa)))) (CC and) (VP (VBD vowed) (S (NP-SBJ (-NONE- *-1)) (VP (TO to) (VP (VB fight) (PP-CLR (IN against) (NP (NN apartheid))))))) (, ,) (S-ADV (NP-SBJ (-NONE- *)) (VP (VBG raising) (NP (NP (NNS fears)) (PP (IN of) (NP (DT a) (JJ possible) (JJ white) (NN backlash))))))) (. .)))",
         "( (S (S-NOM-SBJ (NP-SBJ-1 (-NONE- *)) (VP (VBG Being) (VP (VBN held) (S (NP-SBJ (-NONE- *-1)) (PP-PRD (ADVP (RB well)) (IN below) (NP (NN capacity))))))) (VP (VP (ADVP-MNR (RB greatly)) (VBZ irritates) (NP (PRP them))) (, ,) (CC and) (VP (VBZ has) (VP (VBN led) (PP-CLR (TO to) (NP (JJ widespread) (NN cheating)))))) (. .)))",
         "( (S (NP-SBJ (PRP They)) (VP (VBD acquired) (NP (NP (NNS stakes)) (PP (IN in) (NP (NP (VBG bottling) (NNS companies)) (UCP-LOC (PP (IN in) (NP (DT the) (NNP U.S.))) (CC and) (ADVP (RB overseas))))))) (. .)))",
         "( (S (NP (DT Some) (ADJP (NP (NN gun)) (HYPH -) (VBG toting)) (NNS guards)) (VP (VBD arrived)) (. .)))",
         "( (S (NP (DT Some) (ADJP (NN gun) (HYPH -) (VBG toting)) (NNS guards)) (VP (VBD arrived)) (. .)))",
         "( (S (NP (PRP She)) (VP (VBD asked) (NP (DT the) (NN man)) (S (VP (TO to) (VP (VB leave))))) (. .)))",
    };

    // the expected dependency answers (basic)
    String[] testAnswers = {
        "nsubj(died-3, Reagan-1)\n" + "aux(died-3, has-2)\n" + "root(ROOT-0, died-3)\n",
        "nsubjpass(killed-4, Kennedy-1)\n" + "aux(killed-4, has-2)\n" + "auxpass(killed-4, been-3)\n" + "root(ROOT-0, killed-4)\n",
        "nsubj(man-5, Bill-1)\n" + "cop(man-5, is-2)\n" + "det(man-5, an-3)\n" + "amod(man-5, honest-4)\n" + "root(ROOT-0, man-5)\n",
        "nsubj(big-3, Bill-1)\n" + "cop(big-3, is-2)\n" + "root(ROOT-0, big-3)\n" + "cc(big-3, and-4)\n" + "conj(big-3, honest-5)\n",
        "nsubj(defeated-2, Clinton-1)\n" + "root(ROOT-0, defeated-2)\n" + "dobj(defeated-2, Dole-3)\n",
        "dobj(said-3, What-1)\n" + "nsubj(said-3, she-2)\n" + "csubj(untrue-5, said-3)\n" + "cop(untrue-5, is-4)\n" + "root(ROOT-0, untrue-5)\n",
        "nsubjpass(defeated-3, Dole-1)\n" + "auxpass(defeated-3, was-2)\n" + "root(ROOT-0, defeated-3)\n" + "prep(defeated-3, by-4)\n" + "pobj(by-4, Clinton-5)\n",
        "mark(lied-3, That-1)\n" + "nsubj(lied-3, she-2)\n" + "csubjpass(suspected-5, lied-3)\n" + "auxpass(suspected-5, was-4)\n" + "root(ROOT-0, suspected-5)\n" + "prep(suspected-5, by-6)\n" + "pobj(by-6, everyone-7)\n",
        "nsubj(gave-2, She-1)\n" + "root(ROOT-0, gave-2)\n" + "iobj(gave-2, me-3)\n" + "det(raise-5, a-4)\n" + "dobj(gave-2, raise-5)\n",
        "nsubj(like-2, I-1)\n" + "root(ROOT-0, like-2)\n" + "aux(swim-4, to-3)\n" + "xcomp(like-2, swim-4)\n",
        "nsubj(sat-2, I-1)\n" + "root(ROOT-0, sat-2)\n" + "prep(sat-2, on-3)\n" + "det(chair-5, the-4)\n" + "pobj(on-3, chair-5)\n",
        "nsubj(have-2, We-1)\n" + "root(ROOT-0, have-2)\n" + "neg(information-5, no-3)\n" + "amod(information-5, useful-4)\n" + "dobj(have-2, information-5)\n" + "prep(information-5, on-6)\n" + "mark(are-9, whether-7)\n" + "nsubj(are-9, users-8)\n" + "pcomp(on-6, are-9)\n" + "prep(are-9, at-10)\n" + "pobj(at-10, risk-11)\n",
        "nsubj(heard-2, They-1)\n" + "root(ROOT-0, heard-2)\n" + "prep(heard-2, about-3)\n" + "pobj(about-3, asbestos-4)\n" + "xcomp(heard-2, having-5)\n" + "amod(properties-7, questionable-6)\n" + "dobj(having-5, properties-7)\n",
        "nsubj(says-2, He-1)\n" + "root(ROOT-0, says-2)\n" + "mark(like-5, that-3)\n" + "nsubj(like-5, you-4)\n" + "ccomp(says-2, like-5)\n" + "aux(swim-7, to-6)\n" + "xcomp(like-5, swim-7)\n",
        "nsubj(certain-3, I-1)\n" + "cop(certain-3, am-2)\n" + "root(ROOT-0, certain-3)\n" + "mark(did-6, that-4)\n" + "nsubj(did-6, he-5)\n" + "ccomp(certain-3, did-6)\n" + "dobj(did-6, it-7)\n",
        "nsubj(ready-3, I-1)\n" + "cop(ready-3, am-2)\n" + "root(ROOT-0, ready-3)\n" + "aux(leave-5, to-4)\n" + "xcomp(ready-3, leave-5)\n",
        "nn(forces-2, U.S.-1)\n" + "nsubjpass(engaged-5, forces-2)\n" + "aux(engaged-5, have-3)\n" + "auxpass(engaged-5, been-4)\n" + "root(ROOT-0, engaged-5)\n" + "prep(engaged-5, in-6)\n" + "amod(fighting-8, intense-7)\n" + "pobj(in-6, fighting-8)\n" + "mark(launched-11, after-9)\n" + "nsubj(launched-11, insurgents-10)\n" + "advcl(engaged-5, launched-11)\n" + "amod(attacks-13, simultaneous-12)\n" + "dobj(launched-11, attacks-13)\n",
        "nsubj(saw-2, I-1)\n" + "root(ROOT-0, saw-2)\n" + "det(man-4, the-3)\n" + "dobj(saw-2, man-4)\n" + "dobj(love-7, who-5)\n" + "nsubj(love-7, you-6)\n" + "rcmod(man-4, love-7)\n",
        "nsubj(saw-2, I-1)\n" + "root(ROOT-0, saw-2)\n" + "det(man-4, the-3)\n" + "dobj(saw-2, man-4)\n" + "poss(wife-6, whose-5)\n" + "dobj(love-8, wife-6)\n" + "nsubj(love-8, you-7)\n" + "rcmod(man-4, love-8)\n",
        "nsubj(saw-2, I-1)\n" + "root(ROOT-0, saw-2)\n" + "det(man-4, the-3)\n" + "dobj(saw-2, man-4)\n" + "poss(wife-6, whose-5)\n" + "dobj(love-8, wife-6)\n" + "nsubj(love-8, you-7)\n" + "rcmod(man-4, love-8)\n",
        "expl(is-2, There-1)\n" + "root(ROOT-0, is-2)\n" + "det(statue-4, a-3)\n" + "nsubj(is-2, statue-4)\n" + "prep(statue-4, in-5)\n" + "det(corner-7, the-6)\n" + "pobj(in-5, corner-7)\n",
        "nsubj(looks-2, She-1)\n" + "root(ROOT-0, looks-2)\n" + "advmod(beautiful-4, very-3)\n" + "acomp(looks-2, beautiful-4)\n",
        "det(accident-2, The-1)\n" + "nsubj(happened-3, accident-2)\n" + "root(ROOT-0, happened-3)\n" + "mark(falling-8, as-4)\n" + "det(night-6, the-5)\n" + "nsubj(falling-8, night-6)\n" + "aux(falling-8, was-7)\n" + "advcl(happened-3, falling-8)\n",
        "mark(know-3, If-1)\n" + "nsubj(know-3, you-2)\n" + "advcl(tell-10, know-3)\n" + "nsubj(did-5, who-4)\n" + "ccomp(know-3, did-5)\n" + "dobj(did-5, it-6)\n" + "nsubj(tell-10, you-8)\n" + "aux(tell-10, should-9)\n" + "root(ROOT-0, tell-10)\n" + "det(teacher-12, the-11)\n" + "dobj(tell-10, teacher-12)\n",
        "amod(night-2, Last-1)\n" + "tmod(swam-5, night-2)\n" + "nsubj(swam-5, I-4)\n" + "root(ROOT-0, swam-5)\n" + "prep(swam-5, in-6)\n" + "det(pool-8, the-7)\n" + "pobj(in-6, pool-8)\n",
        "nsubj(talked-2, He-1)\n" + "root(ROOT-0, talked-2)\n" + "prep(talked-2, to-3)\n" + "det(president-5, the-4)\n" + "pobj(to-3, president-5)\n" + "mark(secure-9, in-6)\n" + "dep(secure-9, order-7)\n" + "aux(secure-9, to-8)\n" + "advcl(talked-2, secure-9)\n" + "det(account-11, the-10)\n" + "dobj(secure-9, account-11)\n",
        "nsubj(saw-2, I-1)\n" + "root(ROOT-0, saw-2)\n" + "det(book-4, the-3)\n" + "dobj(saw-2, book-4)\n" + "dobj(bought-7, which-5)\n" + "nsubj(bought-7, you-6)\n" + "rcmod(book-4, bought-7)\n",
        "nsubj(eats-2, Sam-1)\n" + "root(ROOT-0, eats-2)\n" + "num(sheep-4, 3-3)\n" + "dobj(eats-2, sheep-4)\n",
        "nsubj(lost-2, I-1)\n" + "root(ROOT-0, lost-2)\n" + "dobj(lost-2, $-3)\n" + "number(billion-5, 3.2-4)\n" + "num($-3, billion-5)\n",
        "quantmod(200-2, About-1)\n" + "num(people-3, 200-2)\n" + "nsubj(came-4, people-3)\n" + "root(ROOT-0, came-4)\n" + "prep(came-4, to-5)\n" + "det(party-7, the-6)\n" + "pobj(to-5, party-7)\n",
        "nsubj(eats-6, Sam-1)\n" + "poss(brother-4, my-3)\n" + "appos(Sam-1, brother-4)\n" + "root(ROOT-0, eats-6)\n" + "amod(meat-8, red-7)\n" + "dobj(eats-6, meat-8)\n",
        "det(Corporation-4, The-1)\n" + "amod(Corporation-4, Australian-2)\n" + "nn(Corporation-4, Broadcasting-3)\n" + "root(ROOT-0, Corporation-4)\n" + "appos(Corporation-4, ABC-6)\n",
        "nsubj(picked-2, Bill-1)\n" + "root(ROOT-0, picked-2)\n" + "dobj(picked-2, Fred-3)\n" + "prep(Fred-3, for-4)\n" + "det(team-6, the-5)\n" + "pobj(for-4, team-6)\n" + "vmod(team-6, demonstrating-7)\n" + "poss(incompetence-9, his-8)\n" + "dobj(demonstrating-7, incompetence-9)\n",
        "nsubj(scientist-5, Bill-1)\n" + "cop(scientist-5, is-2)\n" + "neg(scientist-5, not-3)\n" + "det(scientist-5, a-4)\n" + "root(ROOT-0, scientist-5)\n",
        "nsubj(drive-4, Bill-1)\n" + "aux(drive-4, does-2)\n" + "neg(drive-4, n't-3)\n" + "root(ROOT-0, drive-4)\n",
        "det(director-2, The-1)\n" + "nsubj(old-6, director-2)\n" + "cop(old-6, is-3)\n" + "num(years-5, 65-4)\n" + "npadvmod(old-6, years-5)\n" + "root(ROOT-0, old-6)\n",
        "det(man-2, The-1)\n" + "nsubj(is-3, man-2)\n" + "root(ROOT-0, is-3)\n" + "advmod(is-3, here-4)\n",
        "prep(live-6, In-1)\n" + "det(city-3, which-2)\n" + "pobj(In-1, city-3)\n" + "aux(live-6, do-4)\n" + "nsubj(live-6, you-5)\n" + "root(ROOT-0, live-6)\n",
        "dobj(invent-5, What-1)\n" + "aux(invent-5, did-2)\n" + "nn(Babbage-4, Charles-3)\n" + "nsubj(invent-5, Babbage-4)\n" + "root(ROOT-0, invent-5)\n",
        "pobj(for-6, What-1)\n" + "auxpass(used-5, is-2)\n" + "det(esophagus-4, the-3)\n" + "nsubjpass(used-5, esophagus-4)\n" + "root(ROOT-0, used-5)\n" + "prep(used-5, for-6)\n",
        "predet(boys-3, All-1)\n" + "det(boys-3, the-2)\n" + "nsubj(are-4, boys-3)\n" + "root(ROOT-0, are-4)\n" + "advmod(are-4, here-5)\n",
        "preconj(boys-3, Both-1)\n" + "det(boys-3, the-2)\n" + "nsubj(are-7, boys-3)\n" + "cc(boys-3, and-4)\n" + "det(girls-6, the-5)\n" + "conj(boys-3, girls-6)\n" + "root(ROOT-0, are-7)\n" + "advmod(are-7, here-8)\n",
        "nsubj(shut-2, They-1)\n" + "root(ROOT-0, shut-2)\n" + "prt(shut-2, down-3)\n" + "det(station-5, the-4)\n" + "dobj(shut-2, station-5)\n",
        "nsubj(tasty-7, Truffles-1)\n" + "vmod(Truffles-1, picked-2)\n" + "prep(picked-2, during-3)\n" + "det(spring-5, the-4)\n" + "pobj(during-3, spring-5)\n" + "cop(tasty-7, are-6)\n" + "root(ROOT-0, tasty-7)\n",
        "preconj(they-2, Neither-1)\n" + "nsubjpass(reached-8, they-2)\n" + "cc(they-2, nor-3)\n" + "nn(McAlpine-5, Mr.-4)\n" + "conj(they-2, McAlpine-5)\n" + "aux(reached-8, could-6)\n" + "auxpass(reached-8, be-7)\n" + "root(ROOT-0, reached-8)\n" + "prep(reached-8, for-9)\n" + "pobj(for-9, comment-10)\n",
        "nn(field-2, Xml-1)\n" +
                "nsubj(include-4, field-2)\n" +
                "aux(include-4, should-3)\n" + "root(ROOT-0, include-4)\n" +
                "preconj(id-8, both-5)\n" +
                "det(id-8, the-6)\n" +
                "nn(id-8, entity-7)\n" +
                "dobj(include-4, id-8)\n" +
                "cc(id-8, and-9)\n" +
                "det(name-12, the-10)\n" +
                "nn(name-12, entity-11)\n" +
                "conj(id-8, name-12)\n" +
                "mark(unique-19, since-13)\n" +
                "det(names-16, the-14)\n" +
                "nn(names-16, entity-15)\n" +
                "nsubj(unique-19, names-16)\n" +
                "cop(unique-19, are-17)\n" +
                "neg(unique-19, not-18)\n" +
                "advcl(include-4, unique-19)\n",
        "det(government-2, The-1)\n" +
                "nsubj(counts-3, government-2)\n" +  "root(ROOT-0, counts-3)\n" +
                "dobj(counts-3, money-4)\n" +
                "mark(spent-8, as-5)\n" +
                "nsubjpass(spent-8, it-6)\n" +
                "auxpass(spent-8, is-7)\n" +
                "advcl(counts-3, spent-8)\n" +
                "nsubj(counts-11, Dodge-10)\n" +
                "parataxis(counts-3, counts-11)\n" +
                "dobj(counts-11, contracts-12)\n" +
                "advmod(awarded-16, when-13)\n" +
                "nsubjpass(awarded-16, they-14)\n" +
                "auxpass(awarded-16, are-15)\n" +
                "advcl(counts-11, awarded-16)\n",
        "cc(deserve-5, But-1)\n" +
                "nsubj(deserve-5, she-2)\n" +
                "aux(deserve-5, did-3)\n" +
                "neg(deserve-5, n't-4)\n" +
                "root(ROOT-0, deserve-5)\n" +
                "aux(have-7, to-6)\n" +
                "xcomp(deserve-5, have-7)\n" +
                "poss(head-9, her-8)\n" +
                "nsubj(chopped-10, head-9)\n" +
                "ccomp(have-7, chopped-10)\n" +
                "prt(chopped-10, off-11)\n",
            "nsubj(like-2, I-1)\n" + "root(ROOT-0, like-2)\n" +
                    "dobj(like-2, dogs-3)\n" +
                    "cc(dogs-3, rather-4)\n" +
                    "mwe(rather-4, than-5)\n" +
                    "conj(dogs-3, cats-6)\n",
        "nsubj(like-2, I-1)\n" + "root(ROOT-0, like-2)\n" +
                "dobj(like-2, brandy-3)\n" +
                "neg(mention-6, not-4)\n" +
                "aux(mention-6, to-5)\n" +
                "cc(brandy-3, mention-6)\n" +
                "conj(brandy-3, cognac-7)\n",
            "nsubj(like-2, I-1)\n" + "root(ROOT-0, like-2)\n" +
                    "neg(only-4, not-3)\n" +
                    "preconj(cats-5, only-4)\n" +
                    "dobj(like-2, cats-5)\n" +
                    "cc(also-7, but-6)\n" +
                    "cc(cats-5, also-7)\n" +
                    "conj(cats-5, dogs-8)\n",
        "nsubj(knows-2, He-1)\n" + "root(ROOT-0, knows-2)\n" +
                "det(industry-6, the-3)\n" +
                "amod(engineering-5, mechanical-4)\n" +
                "nn(industry-6, engineering-5)\n" +
                "dobj(knows-2, industry-6)\n",

        "det(weapon-2, What-1)\n" +
                "pobj(with-10, weapon-2)\n" +
                "cop(proficient-9, is-3)\n" +
                "det(Apollo-7, the-4)\n" +
                "amod(Apollo-7, mythological-5)\n" +
                "nn(Apollo-7, character-6)\n" +
                "nsubj(proficient-9, Apollo-7)\n" +
                "advmod(proficient-9, most-8)\n" +
                "root(ROOT-0, proficient-9)\n" +
                "prep(proficient-9, with-10)\n",

        "cc(free-4, Nor-1)\n" +
                "cop(free-4, are-2)\n" +
                "nsubj(free-4, you-3)\n" + "root(ROOT-0, free-4)\n" +
                "aux(reprint-6, to-5)\n" +
                "xcomp(free-4, reprint-6)\n" +
                "amod(material-8, such-7)\n" +
                "dobj(reprint-6, material-8)\n",
        "advmod(many-2, How-1)\n" +
                "amod(novels-5, many-2)\n" +
                "nn(novels-5, James-3)\n" +
                "nn(novels-5, Bond-4)\n" +
                "nsubj(are-6, novels-5)\n" + "root(ROOT-0, are-6)\n" +
                "expl(are-6, there-7)\n",
        "nsubjpass(excluded-7, Investments-1)\n" +
                "prep(Investments-1, in-2)\n" +
                "nn(Africa-4, South-3)\n" +
                "pobj(in-2, Africa-4)\n" +
                "aux(excluded-7, will-5)\n" +
                "auxpass(excluded-7, be-6)\n" + "root(ROOT-0, excluded-7)\n",
        "advmod(excluded-2, Also-1)\n" + "root(ROOT-0, excluded-2)\n" +
                "aux(excluded-2, will-3)\n" +
                "auxpass(excluded-2, be-4)\n" +
                "nsubjpass(excluded-2, investments-5)\n" +
                "prep(investments-5, in-6)\n" +
                "nn(Africa-8, South-7)\n" +
                "pobj(in-6, Africa-8)\n",
        "root(ROOT-0, Defending-1)\n" + "poss(ramparts-3, their-2)\n" +
                "dobj(Defending-1, ramparts-3)\n" +
                "aux(Defending-1, are-4)\n" +
                "nn(Street-6, Wall-5)\n" +
                "poss(Guard-9, Street-6)\n" +
                "possessive(Street-6, 's-7)\n" +
                "nn(Guard-9, Old-8)\n" +
                "nsubj(Defending-1, Guard-9)\n",
        "amod(investors-2, Institutional-1)\n" +
                "nsubj(remained-4, investors-2)\n" +
                "advmod(remained-4, mostly-3)\n" + "root(ROOT-0, remained-4)\n" +
                "prep(remained-4, on-5)\n" +
                "det(sidelines-7, the-6)\n" +
                "pobj(on-5, sidelines-7)\n" +
                "tmod(remained-4, Tuesday-8)\n",
        "cop(future-4, Is-1)\n" +
                "nsubj(future-4, this-2)\n" +
                "det(future-4, the-3)\n" + "root(ROOT-0, future-4)\n" +
                "prep(future-4, of-5)\n" +
                "nn(music-7, chamber-6)\n" +
                "pobj(of-5, music-7)\n",
        "root(ROOT-0, Is-1)\n" + "det(trouble-3, the-2)\n" +
                "nsubj(Is-1, trouble-3)\n" +
                "advmod(Is-1, over-4)\n",
        "mark(smart-4, Although-1)\n" +
                "nsubj(smart-4, Sue-2)\n" +
                "cop(smart-4, is-3)\n" +
                "advcl(win-8, smart-4)\n" +
                "nsubj(win-8, who-6)\n" +
                "aux(win-8, will-7)\n" + "root(ROOT-0, win-8)\n",
        "root(ROOT-0, Xerox-1)\n" +
                "poss(officer-6, whose-3)\n" +
                "amod(officer-6, chief-4)\n" +
                "amod(officer-6, executive-5)\n" +
                "nsubj(resigned-12, officer-6)\n" +
                "nn(Gatward-9, James-8)\n" +
                "appos(officer-6, Gatward-9)\n" +
                "aux(resigned-12, has-11)\n" +
                "rcmod(Xerox-1, resigned-12)\n",
        "nsubj(gets-2, He-1)\n" +
                "root(ROOT-0, gets-2)\n" +
                "dobj(gets-2, me-3)\n" +
                "det(time-5, every-4)\n" +
                "advmod(gets-2, time-5)\n",
        "preconj(Parenteau-3, Both-1)\n" +
                "nn(Parenteau-3, Mr.-2)\n" +
                "nsubj(bleeding-11, Parenteau-3)\n" +
                "cc(Parenteau-3, and-4)\n" +
                "nn(Doyon-6, Ms.-5)\n" +
                "conj(Parenteau-3, Doyon-6)\n" +
                "advmod(bleeding-11, however-8)\n" +
                "aux(bleeding-11, were-10)\n" +
                "root(ROOT-0, bleeding-11)\n" +
                "advmod(bleeding-11, badly-12)\n",
        "amod(cars-5, Rusted-1)\n" +
                "prt(Rusted-1, out-2)\n" +
                "cc(Rusted-1, and-3)\n" +
                "conj(Rusted-1, unsafe-4)\n" +
                "root(ROOT-0, cars-5)\n",
        "nsubj(holding-3, u-1)\n" +
            "aux(holding-3, r-2)\n" +
            "root(ROOT-0, holding-3)\n" +
            "dobj(holding-3, it-4)\n" +
            "advmod(tight-6, too-5)\n" +
            "advmod(holding-3, tight-6)\n",
        "nsubj(mail-4, You-1)\n" +
            "aux(mail-4, should-2)\n" +
            "goeswith(mail-4, e-3)\n" +
            "root(ROOT-0, mail-4)\n" +
            "dobj(mail-4, her-5)\n" +
            "advmod(mail-4, sometimes-6)\n",
        "nsubj(higher-6, Interest-1)\n" +
            "cop(higher-6, is-2)\n" +
            "det(deal-5, a-3)\n" +
            "amod(deal-5, great-4)\n" +
            "npadvmod(higher-6, deal-5)\n" +
            "root(ROOT-0, higher-6)\n" +
            "mark(was-9, than-7)\n" +
            "nsubj(was-9, it-8)\n" +
            "ccomp(higher-6, was-9)\n" +
            "det(year-11, a-10)\n" +
            "npadvmod(ago-12, year-11)\n" +
            "advmod(was-9, ago-12)\n",
        "det(strike-2, The-1)\n" +
            "nsubj(ended-5, strike-2)\n" +
            "aux(ended-5, may-3)\n" +
            "aux(ended-5, have-4)\n" +
            "root(ROOT-0, ended-5)\n" +
            "advmod(began-9, almost-6)\n" +
            "mark(began-9, before-7)\n" +
            "nsubj(began-9, it-8)\n" +
            "advcl(ended-5, began-9)\n",
        "mark(set-2, Although-1)\n" +
            "advcl(American-11, set-2)\n" +
            "prep(set-2, in-3)\n" +
            "pobj(in-3, Japan-4)\n" +
            "det(novel-7, the-6)\n" +
            "poss(texture-9, novel-7)\n" +
            "possessive(novel-7, 's-8)\n" +
            "nsubj(American-11, texture-9)\n" +
            "cop(American-11, is-10)\n" +
            "root(ROOT-0, American-11)\n",
        "discourse(specify-2, please-1)\n" +
            "root(ROOT-0, specify-2)\n" +
            "det(ship-7, which-3)\n" +
            "nn(ship-7, royal-4)\n" +
            "cc(royal-4, or-5)\n" +
            "conj(royal-4, carnival-6)\n" +
            "dobj(specify-2, ship-7)\n" +
            "discourse(specify-2, (:-8)\n",
        "root(ROOT-0, those-1)\n" +
                "advmod(under-3, still-2)\n" +
                "rcmod(those-1, under-3)\n" +
                "nn(rules-5, GASB-4)\n" +
                "pobj(under-3, rules-5)\n",
        "det(house-3, the-1)\n" +
                "nn(house-3, auction-2)\n" +
                "root(ROOT-0, house-3)\n" +
                "rcmod(house-3, founded-4)\n" +
                "prep(founded-4, in-5)\n" +
                "pobj(in-5, London-6)\n" +
                "tmod(founded-4, 1744-7)\n" +
                "cc(founded-4, and-8)\n" +
                "advmod(under-10, now-9)\n" +
                "conj(founded-4, under-10)\n" +
                "det(umbrella-12, the-11)\n" +
                "pobj(under-10, umbrella-12)\n" +
                "prep(umbrella-12, of-13)\n" +
                "poss(Inc.-17, Sotheby-14)\n" +
                "possessive(Sotheby-14, 's-15)\n" +
                "nn(Inc.-17, Holdings-16)\n" +
                "pobj(of-13, Inc.-17)\n",
        "nsubj(impossible-4, morcillas-1)\n" +
                "cop(impossible-4, are-2)\n" +
                "advmod(impossible-4, basically-3)\n" +
                "root(ROOT-0, impossible-4)\n" +
                "aux(find-6, to-5)\n" +
                "ccomp(impossible-4, find-6)\n" +
                "prep(find-6, in-7)\n" +
                "pobj(in-7, California-8)\n",
        "aux(wait-3, Do-1)\n" +
                "neg(wait-3, n't-2)\n" +
                "root(ROOT-0, wait-3)\n" +
                "parataxis(wait-3, act-5)\n" +
                "advmod(act-5, now-6)\n",
        "det(Council-3, The-1)\n" +
                "nn(Council-3, Parks-2)\n" +
                "nsubj(wrote-4, Council-3)\n" +
                "root(ROOT-0, wrote-4)\n" +
                "det(BPCA-6, the-5)\n" +
                "dobj(wrote-4, BPCA-6)\n" +
                "mark(belongs-25, that-7)\n" +
                "det(garden-23, this-8)\n" +
                "advmod(private-12, too-10)\n" +
                "amod(garden-23, private-12)\n" +
                "conj(private-12, exclusive-15)\n" +
                "conj(private-12, complex-18)\n" +
                "cc(private-12, and-19)\n" +
                "conj(private-12, expensive-20)\n" +
                "amod(garden-23, enclosed-22)\n" +
                "nsubj(belongs-25, garden-23)\n" +
                "ccomp(wrote-4, belongs-25)\n" +
                "prep(belongs-25, in-26)\n" +
                "advmod(location-29, almost-27)\n" +
                "det(location-29, any-28)\n" +
                "pobj(in-26, location-29)\n" +
                "cc(location-29, but-30)\n" +
                "det(waterfront-32, the-31)\n" +
                "conj(location-29, waterfront-32)\n",
        "cc(have-6, And-2)\n" +
                "nsubj(have-6, you-3)\n" +
                "aux(have-6, ca-4)\n" +
                "neg(have-6, n't-5)\n" +
                "root(ROOT-0, have-6)\n" +
                "nsubj(hearing-12, taxpayers-7)\n" +
                "vmod(taxpayers-7, coming-8)\n" +
                "prep(coming-8, into-9)\n" +
                "det(audit-11, an-10)\n" +
                "pobj(into-9, audit-11)\n" +
                "ccomp(have-6, hearing-12)\n" +
                "dobj(hearing-12, oohs-14)\n" +
                "cc(oohs-14, and-16)\n" +
                "conj(oohs-14, ahs-18)\n",
        "amod(nationalists-3, Freed-1)\n" +
                "amod(nationalists-3, black-2)\n" +
                "nsubj(resumed-4, nationalists-3)\n" +
                "root(ROOT-0, resumed-4)\n" +
                "amod(activity-6, political-5)\n" +
                "dobj(resumed-4, activity-6)\n" +
                "prep(resumed-4, in-7)\n" +
                "nn(Africa-9, South-8)\n" +
                "pobj(in-7, Africa-9)\n" +
                "cc(resumed-4, and-10)\n" +
                "conj(resumed-4, vowed-11)\n" +
                "aux(fight-13, to-12)\n" +
                "xcomp(vowed-11, fight-13)\n" +
                "prep(fight-13, against-14)\n" +
                "pobj(against-14, apartheid-15)\n" +
                "vmod(resumed-4, raising-17)\n" +
                "dobj(raising-17, fears-18)\n" +
                "prep(fears-18, of-19)\n" +
                "det(backlash-23, a-20)\n" +
                "amod(backlash-23, possible-21)\n" +
                "amod(backlash-23, white-22)\n" +
                "pobj(of-19, backlash-23)\n",
                "auxpass(held-2, Being-1)\n" +
                        "csubj(irritates-7, held-2)\n" +
                        "advmod(below-4, well-3)\n" +
                        "prep(held-2, below-4)\n" +
                        "pobj(below-4, capacity-5)\n" +
                        "advmod(irritates-7, greatly-6)\n" +
                        "root(ROOT-0, irritates-7)\n" +
                        "dobj(irritates-7, them-8)\n" +
                        "cc(irritates-7, and-10)\n" +
                        "aux(led-12, has-11)\n" +
                        "conj(irritates-7, led-12)\n" +
                        "prep(led-12, to-13)\n" +
                        "amod(cheating-15, widespread-14)\n" +
                        "pobj(to-13, cheating-15)\n",
        "nsubj(acquired-2, They-1)\n" +
                "root(ROOT-0, acquired-2)\n" +
                "dobj(acquired-2, stakes-3)\n" +
                "prep(stakes-3, in-4)\n" +
                "amod(companies-6, bottling-5)\n" +
                "pobj(in-4, companies-6)\n" +
                "prep(companies-6, in-7)\n" +
                "det(U.S.-9, the-8)\n" +
                "pobj(in-7, U.S.-9)\n" +
                "cc(in-7, and-10)\n" +
                "conj(in-7, overseas-11)\n",
            "det(guards-5, Some-1)\n" +
                    "npadvmod(toting-4, gun-2)\n" +
                    "amod(guards-5, toting-4)\n" +
                    "nsubj(arrived-6, guards-5)\n" +
                    "root(ROOT-0, arrived-6)\n",
            "det(guards-5, Some-1)\n" +
                    "npadvmod(toting-4, gun-2)\n" +
                    "amod(guards-5, toting-4)\n" +
                    "nsubj(arrived-6, guards-5)\n" +
                    "root(ROOT-0, arrived-6)\n",
            "nsubj(asked-2, She-1)\n" +
                    "root(ROOT-0, asked-2)\n" +
                    "det(man-4, the-3)\n" +
                    "dobj(asked-2, man-4)\n" +
                    "aux(leave-6, to-5)\n" +
                    "xcomp(asked-2, leave-6)\n",

    };

    assertEquals("Test array lengths mismatch!", testTrees.length, testAnswers.length);

    TreeReaderFactory trf = new PennTreeReaderFactory();
    for (int i = 0; i < testTrees.length; i++) {
      String testTree = testTrees[i];
      String testAnswer = testAnswers[i];

      // specifying our own TreeReaderFactory is vital so that functional
      // categories - that is -TMP and -ADV in particular - are not stripped off
      Tree tree = Tree.valueOf(testTree, trf);
      GrammaticalStructure gs = new EnglishGrammaticalStructure(tree);

      assertEquals("Unexpected basic dependencies for tree " + testTree,
          testAnswer, GrammaticalStructureConversionUtils.dependenciesToString(gs, gs.typedDependencies(GrammaticalStructure.Extras.NONE), tree, false, false, false));
    }

  }

  /**
   * More tests that we can extract the basic grammatical relations correctly from
   * some hard-coded trees.
   */
  @Test
  public void testMoreBasicRelations() {
    // the trees to test
    String[] testTrees = {
        // This is the say-complement case that we don't yet handle, but might someday.
        // "( (SBAR (WHNP-9 (WDT Which)) (S (NP-SBJ (PRP I)) (ADVP-TMP (RB then)) (VP (VBD realized) (SBAR (-NONE- *0*) (S (NP-SBJ (PRP I)) (VP (VBD missed) (NP-9 (-NONE- *T*))))))) (. .)))",
        "(ROOT (S (NP (PRP I)) (VP (VBD saw) (NP (NP (DT the) (NN woman)) (SBAR (WHNP (WP whom)) (S (NP (PRP you)) (VP (VBD gave) (NP (DT the) (NN package)) (PP (TO to))))))) (. .)))",
        "( (S (NP-SBJ (PRP i)) (VP (VBP m) (ADJP-PRD (JJ fat)))))",
        // this is a WHNP that gets converted rel to pobj in dependency postprocessing
        "(NP (NP (NNP Mr.) (NNP Laidig)) (, ,) (SBAR (WHNP-1 (WP whom)) (S (NP-SBJ (PRP he)) (VP (VBD referred) (PP-CLR (TO to) (NP (-NONE- *T*-1))) (PP-CLR (IN as) (NP (DT a) (NN friend)))))))",
        "( (SBARQ (WHNP-9 (WP what)) (SQ (VBZ does) (NP-SBJ (PRP it)) (VP (VB mean) (NP-9 (-NONE- *T*)) (SBAR-TMP (WHADVP-1 (WRB when)) (S (NP-SBJ (DT a) (JJ veiled) (NN chameleon) (NN egg)) (VP (VBZ is) (ADJP-PRD (JJ soft)) (ADVP-TMP-1 (-NONE- *T*))))))) (. ?)))",
        "( (S (NP-SBJ (PRP it)) (VP (VBD wase) (RB nt) (VP (VBG going))) (. ....)))",
        // Relative clauses used to not be recognized off NP-ADV or NP-TMP
        "( (S (NP-SBJ (DT An) (NN arbitrator) ) (VP (VP (VBD awarded) (NP (NNP Eastern) (NNPS Airlines) (NNS pilots) ) (NP (NP (QP (IN between) ($ $) (CD 60) (CD million) (CC and) ($ $) (CD 100) (CD million) ) (-NONE- *U*) ) (PP (IN in) (NP (JJ back) (NN pay) )))) (, ,) (NP-ADV (NP (DT a) (NN decision) ) (SBAR (WHNP-285 (WDT that) ) (S (NP-SBJ (-NONE- *T*-285) ) (VP (MD could) (VP (VB complicate) (NP (NP (DT the) (NN carrier) (POS 's) ) (NN bankruptcy-law) (NN reorganization) ))))))) (. .) ))",
        // Check same regardless of ROOT or none and functional categories or none
        "(ROOT (S (NP (CD Two) (JJ former) (NNS ministers) ) (VP (VBD were) (ADJP (ADJP (ADVP (RB heavily) ) (VBN implicated) )) (PP (IN in) (NP (DT the) (NNP Koskotas) (NN affair) )))))",
        "( (S (NP-SBJ (CD Two) (JJ former) (NNS ministers) ) (VP (VBD were) (ADJP-PRD (ADJP (ADVP (RB heavily) ) (VBN implicated) )) (PP-LOC (IN in) (NP (DT the) (NNP Koskotas) (NN affair) )))))",
        "(NP-ADV (NP (DT The) (JJR more) (NNS accounts) ) (SBAR (WHNP-1 (-NONE- 0) ) (S (NP-SBJ (NNS customers) ) (VP (VBP have) (NP (-NONE- *T*-1) )))))",
        "(NP-ADV (NP-ADV (DT a) (NN-ADV lesson)) (VP (ADVP (RB once)) (VBN learned) (PP (IN by) (NP (NNP Henry) (NNP Kissinger)))))",
        // you get PP structures with a CC-as-IN for vs., plus, less, but
        "(NP (NP (NNP U.S.)) (PP (CC v.) (NP (NNP Hudson) (CC and) (NNP Goodwin))))",
        "(NP (NP (NN nothing)) (PP (CC but) (NP (PRP$ their) (NNS scratches))))",
        // You'd like this one to come out with an nsubjpass, but there are many other cases that are tagging mistakes. Decide what to do
        // "( (S-HLN (NP-SBJ-1 (NN ABORTION) (NN RULING)) (VP (VBN UPHELD) (NP (-NONE- *-1))) (: :)))",
        "(FRAG (ADVP (ADVP (RB So) (RB long)) (SBAR (IN as) (S (NP-SBJ (PRP you)) (VP (VBP do) (RB n't) (VP (VB look) (ADVP-DIR (RB down))))))) (. .))",
        "( (S (NP (NNS Hippos)) (VP (VBP weigh) (NP (QP (RP up) (IN to) (CD 2,700)) (NNS kilograms))) (. .)))",
        "( (S (NP (NNS Hippos)) (VP (VBP weigh) (NP (QP (RB up) (IN to) (CD 2,700)) (NNS kilograms))) (. .)))",
        "( (S (NP (PRP I)) (VP (VBD purchased) (NP (QP (RP up) (IN to) (CD 192,000)) (JJ additional) (JJ ordinary) (NNS shares))) (. .)))",
    };

    // the expected dependency answers (basic)
    String[] testAnswers = {
        // "dobj(missed-6, Which-1)\n" + "nsubj(realized-4, I-2)\n" + "advmod(realized-4, then-3)\n" + "root(ROOT-0, realized-4)\n" + "nsubj(missed-6, I-5)\n" + "ccomp(realized-4, missed-6)\n",
        "nsubj(saw-2, I-1)\n" +
                "root(ROOT-0, saw-2)\n" +
                "det(woman-4, the-3)\n" +
                "dobj(saw-2, woman-4)\n" +
                "pobj(to-10, whom-5)\n" +
                "nsubj(gave-7, you-6)\n" +
                "rcmod(woman-4, gave-7)\n" +
                "det(package-9, the-8)\n" +
                "dobj(gave-7, package-9)\n" +
                "prep(gave-7, to-10)\n",
        "nsubj(fat-3, i-1)\n" + "cop(fat-3, m-2)\n" + "root(ROOT-0, fat-3)\n",
        "nn(Laidig-2, Mr.-1)\n" +
                "root(ROOT-0, Laidig-2)\n" +
                "pobj(to-7, whom-4)\n" +
                "nsubj(referred-6, he-5)\n" +
                "rcmod(Laidig-2, referred-6)\n" +
                "prep(referred-6, to-7)\n" +
                "prep(referred-6, as-8)\n" +
                "det(friend-10, a-9)\n" +
                "pobj(as-8, friend-10)\n",
        "dobj(mean-4, what-1)\n" +
                "aux(mean-4, does-2)\n" +
                "nsubj(mean-4, it-3)\n" +
                "root(ROOT-0, mean-4)\n" +
                "advmod(soft-11, when-5)\n" +
                "det(egg-9, a-6)\n" +
                "amod(egg-9, veiled-7)\n" +
                "nn(egg-9, chameleon-8)\n" +
                "nsubj(soft-11, egg-9)\n" +
                "cop(soft-11, is-10)\n" +
                "advcl(mean-4, soft-11)\n",
        "nsubj(going-4, it-1)\n" + "aux(going-4, wase-2)\n" + "neg(going-4, nt-3)\n" + "root(ROOT-0, going-4)\n" + "punct(going-4, ....-5)\n",
        "det(arbitrator-2, An-1)\n" +
                "nsubj(awarded-3, arbitrator-2)\n" +
                "root(ROOT-0, awarded-3)\n" +
                "nn(pilots-6, Eastern-4)\n" +
                "nn(pilots-6, Airlines-5)\n" +
                "iobj(awarded-3, pilots-6)\n" +
                "quantmod($-8, between-7)\n" +
                "dobj(awarded-3, $-8)\n" +
                "number(million-10, 60-9)\n" +
                "num($-8, million-10)\n" +
                "cc($-8, and-11)\n" +
                "conj($-8, $-12)\n" +
                "number(million-14, 100-13)\n" +
                "num($-12, million-14)\n" +
                "prep($-8, in-15)\n" +
                "amod(pay-17, back-16)\n" +
                "pobj(in-15, pay-17)\n" +
                "det(decision-20, a-19)\n" +
                "npadvmod(awarded-3, decision-20)\n" +
                "nsubj(complicate-23, that-21)\n" +
                "aux(complicate-23, could-22)\n" +
                "rcmod(decision-20, complicate-23)\n" +
                "det(carrier-25, the-24)\n" +
                "poss(reorganization-28, carrier-25)\n" +
                "possessive(carrier-25, 's-26)\n" +
                "nn(reorganization-28, bankruptcy-law-27)\n" +
                "dobj(complicate-23, reorganization-28)\n",
        "num(ministers-3, Two-1)\n" +
                "amod(ministers-3, former-2)\n" +
                "nsubjpass(implicated-6, ministers-3)\n" +
                "auxpass(implicated-6, were-4)\n" +
                "advmod(implicated-6, heavily-5)\n" +
                "root(ROOT-0, implicated-6)\n" +
                "prep(implicated-6, in-7)\n" +
                "det(affair-10, the-8)\n" +
                "nn(affair-10, Koskotas-9)\n" +
                "pobj(in-7, affair-10)\n",
        "num(ministers-3, Two-1)\n" +
                "amod(ministers-3, former-2)\n" +
                "nsubjpass(implicated-6, ministers-3)\n" +
                "auxpass(implicated-6, were-4)\n" +
                "advmod(implicated-6, heavily-5)\n" +
                "root(ROOT-0, implicated-6)\n" +
                "prep(implicated-6, in-7)\n" +
                "det(affair-10, the-8)\n" +
                "nn(affair-10, Koskotas-9)\n" +
                "pobj(in-7, affair-10)\n",
        "det(accounts-3, The-1)\n" +
                "amod(accounts-3, more-2)\n" +
                "root(ROOT-0, accounts-3)\n" +
                "nsubj(have-5, customers-4)\n" +
                "rcmod(accounts-3, have-5)\n",
        "det(lesson-2, a-1)\nroot(ROOT-0, lesson-2)\nadvmod(learned-4, once-3)\nvmod(lesson-2, learned-4)\nprep(learned-4, by-5)\nnn(Kissinger-7, Henry-6)\npobj(by-5, Kissinger-7)\n",
        "root(ROOT-0, U.S.-1)\n" +
                "prep(U.S.-1, v.-2)\n" +
                "pobj(v.-2, Hudson-3)\n" +
                "cc(Hudson-3, and-4)\n" +
                "conj(Hudson-3, Goodwin-5)\n",
        "root(ROOT-0, nothing-1)\n" +
                "prep(nothing-1, but-2)\n" +
                "poss(scratches-4, their-3)\n" +
                "pobj(but-2, scratches-4)\n",
        // "nn(RULING-2, ABORTION-1)\n" +
        //         "nsubjpass(UPHELD-3, RULING-2)\n" +
        //         "root(ROOT-0, UPHELD-3)\n",
        "advmod(long-2, So-1)\n" +
                "root(ROOT-0, long-2)\n" +
                "mark(look-7, as-3)\n" +
                "nsubj(look-7, you-4)\n" +
                "aux(look-7, do-5)\n" +
                "neg(look-7, n't-6)\n" +
                "advcl(long-2, look-7)\n" +
                "advmod(look-7, down-8)\n",

            "nsubj(weigh-2, Hippos-1)\n" +
                    "root(ROOT-0, weigh-2)\n" +
                    "quantmod(2,700-5, up-3)\n" +
                    "mwe(up-3, to-4)\n" +
                    "num(kilograms-6, 2,700-5)\n" +
                    "dobj(weigh-2, kilograms-6)\n",

            "nsubj(weigh-2, Hippos-1)\n" +
                    "root(ROOT-0, weigh-2)\n" +
                    "quantmod(2,700-5, up-3)\n" +
                    "mwe(up-3, to-4)\n" +
                    "num(kilograms-6, 2,700-5)\n" +
                    "dobj(weigh-2, kilograms-6)\n",

            "nsubj(purchased-2, I-1)\n" +
                    "root(ROOT-0, purchased-2)\n" +
                    "quantmod(192,000-5, up-3)\n" +
                    "mwe(up-3, to-4)\n" +
                    "num(shares-8, 192,000-5)\n" +
                    "amod(shares-8, additional-6)\n" +
                    "amod(shares-8, ordinary-7)\n" +
                    "dobj(purchased-2, shares-8)\n",

    };

    assertEquals("Test array lengths mismatch!", testTrees.length, testAnswers.length);
    // TreeReaderFactory trf = new PennTreeReaderFactory();
    TreeReaderFactory trf = new NPTmpRetainingTreeNormalizer.NPTmpAdvRetainingTreeReaderFactory();
    for (int i = 0; i < testTrees.length; i++) {
      String testTree = testTrees[i];
      String testAnswer = testAnswers[i];

      // specifying our own TreeReaderFactory is vital so that functional
      // categories - that is -TMP and -ADV in particular - are not stripped off
      Tree tree = Tree.valueOf(testTree, trf);
      GrammaticalStructure gs = new EnglishGrammaticalStructure(tree);

      assertEquals("Unexpected basic dependencies for tree " + testTree,
          testAnswer, GrammaticalStructureConversionUtils.dependenciesToString(gs, gs.typedDependencies(GrammaticalStructure.Extras.NONE), tree, false, false, false));
    }
  }

  /**
   * Test the various verb "to be" cases in statements, questions, and imperatives. Added as part of the SD reform
   * that abolished attr.
   */
  @Test
  public void testToBeRelations() {
    // the trees to test
    String[] testTrees = {
      "(ROOT (S (NP (NNP Sue)) (VP (VBZ is) (VP (VBG speaking))) (. .)))",
      "(ROOT (SBARQ (WHNP (WP Who)) (SQ (VBZ is)  (VP (VBG speaking))) (. ?)))",
      "(ROOT (S (VP (VB Be) (ADJP (JJ honest))) (. .)))",
      "(ROOT (SBARQ (WHNP (WP What) ) (SQ (VBZ is) (NP (PRP he) ) (VP (VBG doing)))))",
      "(ROOT (SBARQ (WHNP (WP What) ) (SQ (VBP am) (NP (PRP I) ) (VP (VBG doing) (PP (IN in) (NP (NNP Jackson) (NNP Hole) )))) (. ?) ))",
      "(ROOT (SBARQ (WHNP (WP Who)) (SQ (VBP am) (NP (PRP I)) (S (VP (TO to) (VP (VB judge))))) (. ?)))",
      "(ROOT (S (NP (NNP Bill)) (VP (VBZ is) (NP (DT an) (JJ honest) (NN man))) (. .)))",
      "(ROOT (SBARQ (WHNP (WP What) (NN dignity) ) (SQ (VBZ is) (NP (EX there)) (PP (IN in) (NP (DT that) ))) (. ?)))",
      "(ROOT (S (NP (NN Hand-holding) ) (VP (VBZ is) (VP (VBG becoming) (NP (DT an) (NN investment-banking) (NN job) (NN requirement) ))) (. .) ))",
      "(ROOT (SBARQ (WHNP (WP What)) (SQ (VBZ is) (ADJP (JJ wrong) (PP (IN with) (S (VP (VBG expecting) (NP (NN pizza))))))) (. ?)))",
      "(ROOT (SBARQ (WHNP (WP Who) ) (SQ (VBZ is) (VP (VBG going) (S (VP (TO to) (VP (VB carry) (NP (DT the) (NN water) )))))) (. ?)))",
      "(ROOT (SBARQ (WHNP (WP What)) (SQ (VBP am) (NP (PRP I)) (VP (VBG doing) (S (VP (VBG dating) (NP (PRP her)))))) (. ?)))",
      "(ROOT (SBARQ (WHNP (WP What)) (SQ (VBZ is) (NP (DT that))) (. ?)))",
      "(ROOT (SBARQ (WHNP (WP Who)) (SQ (VBZ is) (NP (NNP John))) (. ?)))",
      "(ROOT (SBARQ (WHNP (WDT What) (NN dog)) (SQ (VP (VBZ is) (VP (VBG barking) (ADVP (RB so) (RB loudly))))) (. ?)))",
      "(ROOT (SBARQ (WHNP (WP Who)) (SQ (VP (VBZ is) (VP (VBG barking) (ADVP (RB so) (RB much))))) (. ?)))",
      "(ROOT (SBARQ (WHADVP (WRB Why)) (SQ (VBZ is) (NP (NNP Dave)) (VP (VBG becoming) (NP (DT a) (NN problem)))) (. ?)))",
      "(ROOT (SBARQ (WHNP (WP What)) (SQ (VBZ is) (NP (NNP UAL) (NN stock) ) (ADJP (NN worth) )) (. ?)))",
      "(ROOT (SBARQ (WHNP (WP Who)) (SQ (VBP am) (NP (PRP I)) ) (. ?)))",
      "(ROOT (SBARQ (WHNP (WP Who)) (SQ (VP (VBD told) (NP (PRP him)))) (. ?)))",
      "(ROOT (S (NP (NNP Sue)) (VP (VBZ is) (NP (DT a) (NN lawyer))) (. .)))",
      "(ROOT (S (NP (NNP Sue)) (VP (VBZ is) (ADJP (JJ intelligent))) (. .)))",
      "(ROOT (SBARQ (WHNP (WP Who)) (SQ (VBZ is) (ADJP (JJ nervous))) (. ?)))",
      "(ROOT (S (NP (EX There)) (VP (VBZ is) (NP (NP (DT a) (NN cow))) (PP (IN in) (NP (DT the) (NN field)))) (. .)))",
      // From a parsing / understanding perspective, "there" is
      // ambiguous.  Once it is tagged "EX", though, the dependencies
      // are not ambiguous.
      "(ROOT (SBARQ (WHNP (WP What)) (SQ (VBZ is) (NP (EX there)) (PP (IN in) (NP (DT the) (NN field)))) (. ?)))",
      "(ROOT (SINV (ADVP (RB Here)) (VP (VBP are)) (NP (DT some) (NNS bags))))",
      "(ROOT (S (NP (PRP He)) (VP (VBZ is) (PP (IN in) (NP (DT the) (NN garden))))))",
      "(ROOT (SBARQ (WHNP (WP What)) (SQ (VBZ 's) (PP (IN on) (NP (DT the) (NN test)))) (. ?)))",
      "(ROOT (SBARQ (WHADVP (WRB Why)) (SQ (VBZ is) (NP (DT the) (NN dog)) (ADJP (JJ pink))) (. ?)))",
      "(ROOT (S (NP (DT The) (NN dog)) (VP (VBZ is) (ADJP (JJ pink))) (. .)))",
      "(ROOT (SBARQ (WHNP (WDT What) (NN disease)) (SQ (VP (VBZ causes) (NP (NN pain)))) (. ?)))",
      // This tree is incorrect, but we added a rule to cover it so
      // parsers which get this incorrect result (that is, the Charniak/Brown parser) don't get bad
      // dependencies
      "(ROOT (SBARQ (WHNP (WDT What) (NN disease)) (SQ (VBZ causes) (NP (NN pain))) (. ?)))",
      "(ROOT (S (VP (VB Be) (VP (VBG waiting) (PP (IN in) (NP (NN line))) (PP-TMP (IN at) (NP (CD 3) (NN p.m.))))) (. !)))",
      "(ROOT (S (VP (VB Be) (NP (DT a) (NN man))) (. !)))",
      "(ROOT (SBARQ (RB So) (WHNP (WP what)) (SQ (VBZ is) (NP (NNP Santa) (NNP Fe) ) (ADJP (IN worth) )) (. ?) ))",
      "(ROOT (SBARQ (WHNP (WP What)) (SQ (VBZ is) (NP (NP (PRP$ your) (NN sister) (POS 's)) (NN name))) (. ?)))",
      // TODO: add an example for "it is raining" once that is correct... needs expl(raining, It)
      // TODO: add an example for "It is clear that Sue is smart" once that is correct... needs expl(clear, It)
      "(ROOT (SBARQ (WHNP (WP What)) (SQ (VBZ is) (NP (NP (DT the) (NN fear)) (PP (IN of) (NP (NNS cockroaches)))) (VP (VBN called))) (. ?)))",
      "(ROOT (SBARQ (WHNP (WP What)) (SQ (VBP am) (NP (PRP I)) (ADJP (JJ good) (PP (IN at)))) (. ?)))",
    };

    // the expected dependency answers (basic)
    String[] basicAnswers = {
        "nsubj(speaking-3, Sue-1)\n" +
                "aux(speaking-3, is-2)\n" +
                "root(ROOT-0, speaking-3)\n",
        "nsubj(speaking-3, Who-1)\n" +
                "aux(speaking-3, is-2)\n" +
                "root(ROOT-0, speaking-3)\n",
        "cop(honest-2, Be-1)\n" +
                "root(ROOT-0, honest-2)\n",

        "dobj(doing-4, What-1)\n" +
                "aux(doing-4, is-2)\n" +
                "nsubj(doing-4, he-3)\n" +
                "root(ROOT-0, doing-4)\n",

        "dobj(doing-4, What-1)\n" +
                "aux(doing-4, am-2)\n" +
                "nsubj(doing-4, I-3)\n" +
                "root(ROOT-0, doing-4)\n" +
                "prep(doing-4, in-5)\n" +
                "nn(Hole-7, Jackson-6)\n" +
                "pobj(in-5, Hole-7)\n",

        "root(ROOT-0, Who-1)\n" +
                "cop(Who-1, am-2)\n" +
                "nsubj(Who-1, I-3)\n" +
                "aux(judge-5, to-4)\n" +
                "vmod(Who-1, judge-5)\n",

        "nsubj(man-5, Bill-1)\n" +
                "cop(man-5, is-2)\n" +
                "det(man-5, an-3)\n" +
                "amod(man-5, honest-4)\n" +
                "root(ROOT-0, man-5)\n",

        "det(dignity-2, What-1)\n" +
                "nsubj(is-3, dignity-2)\n" +
                "root(ROOT-0, is-3)\n" +
                "expl(is-3, there-4)\n" +
                "prep(is-3, in-5)\n" +
                "pobj(in-5, that-6)\n",

        "nsubj(becoming-3, Hand-holding-1)\n" +
                "aux(becoming-3, is-2)\n" +
                "root(ROOT-0, becoming-3)\n" +
                "det(requirement-7, an-4)\n" +
                "nn(requirement-7, investment-banking-5)\n" +
                "nn(requirement-7, job-6)\n" +
                "xcomp(becoming-3, requirement-7)\n",

        "nsubj(wrong-3, What-1)\n" +
                "cop(wrong-3, is-2)\n" +
                "root(ROOT-0, wrong-3)\n" +
                "prep(wrong-3, with-4)\n" +
                "pcomp(with-4, expecting-5)\n" +
                "dobj(expecting-5, pizza-6)\n",

        "nsubj(going-3, Who-1)\n" +
                "aux(going-3, is-2)\n" +
                "root(ROOT-0, going-3)\n" +
                "aux(carry-5, to-4)\n" +
                "xcomp(going-3, carry-5)\n" +
                "det(water-7, the-6)\n" +
                "dobj(carry-5, water-7)\n",

        "dobj(doing-4, What-1)\n" +
                "aux(doing-4, am-2)\n" +
                "nsubj(doing-4, I-3)\n" +
                "root(ROOT-0, doing-4)\n" +
                "vmod(doing-4, dating-5)\n" +
                "dobj(dating-5, her-6)\n",

        "root(ROOT-0, What-1)\n" +
                "cop(What-1, is-2)\n" +
                "nsubj(What-1, that-3)\n",

        "root(ROOT-0, Who-1)\n" +
                "cop(Who-1, is-2)\n" +
                "nsubj(Who-1, John-3)\n",

        "det(dog-2, What-1)\n" +
                "nsubj(barking-4, dog-2)\n" +
                "aux(barking-4, is-3)\n" +
                "root(ROOT-0, barking-4)\n" +
                "advmod(loudly-6, so-5)\n" +
                "advmod(barking-4, loudly-6)\n",


        "nsubj(barking-3, Who-1)\n" +
                "aux(barking-3, is-2)\n" +
                "root(ROOT-0, barking-3)\n" +
                "advmod(much-5, so-4)\n" +
                "advmod(barking-3, much-5)\n",

        "advmod(becoming-4, Why-1)\n" +
                "aux(becoming-4, is-2)\n" +
                "nsubj(becoming-4, Dave-3)\n" +
                "root(ROOT-0, becoming-4)\n" +
                "det(problem-6, a-5)\n" +
                "xcomp(becoming-4, problem-6)\n",

        "dobj(worth-5, What-1)\n" +
                "cop(worth-5, is-2)\n" +
                "nn(stock-4, UAL-3)\n" +
                "nsubj(worth-5, stock-4)\n" +
                "root(ROOT-0, worth-5)\n",

        "root(ROOT-0, Who-1)\n" +
                "cop(Who-1, am-2)\n" +
                "nsubj(Who-1, I-3)\n",

        "nsubj(told-2, Who-1)\n" +
                "root(ROOT-0, told-2)\n" +
                "dobj(told-2, him-3)\n",

        "nsubj(lawyer-4, Sue-1)\n" +
                "cop(lawyer-4, is-2)\n" +
                "det(lawyer-4, a-3)\n" +
                "root(ROOT-0, lawyer-4)\n",

        "nsubj(intelligent-3, Sue-1)\n" +
                "cop(intelligent-3, is-2)\n" +
                "root(ROOT-0, intelligent-3)\n",

        "nsubj(nervous-3, Who-1)\n" +
                "cop(nervous-3, is-2)\n" +
                "root(ROOT-0, nervous-3)\n",

        "expl(is-2, There-1)\n" +
                "root(ROOT-0, is-2)\n" +
                "det(cow-4, a-3)\n" +
                "nsubj(is-2, cow-4)\n" +
                "prep(is-2, in-5)\n" +
                "det(field-7, the-6)\n" +
                "pobj(in-5, field-7)\n",

        "nsubj(is-2, What-1)\n" +
                "root(ROOT-0, is-2)\n" +
                "expl(is-2, there-3)\n" +
                "prep(is-2, in-4)\n" +
                "det(field-6, the-5)\n" +
                "pobj(in-4, field-6)\n",

        "advmod(are-2, Here-1)\n" +
                "root(ROOT-0, are-2)\n" +
                "det(bags-4, some-3)\n" +
                "nsubj(are-2, bags-4)\n",

        "nsubj(is-2, He-1)\n" +
                "root(ROOT-0, is-2)\n" +
                "prep(is-2, in-3)\n" +
                "det(garden-5, the-4)\n" +
                "pobj(in-3, garden-5)\n",

        "nsubj('s-2, What-1)\n" +
                "root(ROOT-0, 's-2)\n" +
                "prep('s-2, on-3)\n" +
                "det(test-5, the-4)\n" +
                "pobj(on-3, test-5)\n",

        "advmod(pink-5, Why-1)\n" +
                "cop(pink-5, is-2)\n" +
                "det(dog-4, the-3)\n" +
                "nsubj(pink-5, dog-4)\n" +
                "root(ROOT-0, pink-5)\n",

        "det(dog-2, The-1)\n" +
                "nsubj(pink-4, dog-2)\n" +
                "cop(pink-4, is-3)\n" +
                "root(ROOT-0, pink-4)\n",

        "det(disease-2, What-1)\n" +
                "nsubj(causes-3, disease-2)\n" +
                "root(ROOT-0, causes-3)\n" +
                "dobj(causes-3, pain-4)\n",

        "det(disease-2, What-1)\n" +
                "nsubj(causes-3, disease-2)\n" +
                "root(ROOT-0, causes-3)\n" +
                "dobj(causes-3, pain-4)\n",

        "aux(waiting-2, Be-1)\n" +
                "root(ROOT-0, waiting-2)\n" +
                "prep(waiting-2, in-3)\n" +
                "pobj(in-3, line-4)\n" +
                "prep(waiting-2, at-5)\n" +
                "num(p.m.-7, 3-6)\n" +
                "pobj(at-5, p.m.-7)\n",

        "cop(man-3, Be-1)\n" +
                "det(man-3, a-2)\n" +
                "root(ROOT-0, man-3)\n",

        "advmod(worth-6, So-1)\n" +
                "dobj(worth-6, what-2)\n" +
                "cop(worth-6, is-3)\n" +
                "nn(Fe-5, Santa-4)\n" +
                "nsubj(worth-6, Fe-5)\n" +
                "root(ROOT-0, worth-6)\n",

        "root(ROOT-0, What-1)\n" +
                "cop(What-1, is-2)\n" +
                "poss(sister-4, your-3)\n" +
                "poss(name-6, sister-4)\n" +
                "possessive(sister-4, 's-5)\n" +
                "nsubj(What-1, name-6)\n",

        "dobj(called-7, What-1)\n" +
                "auxpass(called-7, is-2)\n" +
                "det(fear-4, the-3)\n" +
                "nsubjpass(called-7, fear-4)\n" +
                "prep(fear-4, of-5)\n" +
                "pobj(of-5, cockroaches-6)\n" +
                "root(ROOT-0, called-7)\n",

        "pobj(at-5, What-1)\n" +
                "cop(good-4, am-2)\n" +
                "nsubj(good-4, I-3)\n" +
                "root(ROOT-0, good-4)\n" +
                "prep(good-4, at-5)\n",
    };

    // the expected dependency answers (noncollapsed)
    String[] noncollapsedAnswers = {
        "nsubj(speaking-3, Sue-1)\n" +
                "aux(speaking-3, is-2)\n" +
                "root(ROOT-0, speaking-3)\n",
        "nsubj(speaking-3, Who-1)\n" +
                "aux(speaking-3, is-2)\n" +
                "root(ROOT-0, speaking-3)\n",
        "cop(honest-2, Be-1)\n" +
                "root(ROOT-0, honest-2)\n",

        "dobj(doing-4, What-1)\n" +
                "aux(doing-4, is-2)\n" +
                "nsubj(doing-4, he-3)\n" +
                "root(ROOT-0, doing-4)\n",

        "dobj(doing-4, What-1)\n" +
                "aux(doing-4, am-2)\n" +
                "nsubj(doing-4, I-3)\n" +
                "root(ROOT-0, doing-4)\n" +
                "prep(doing-4, in-5)\n" +
                "nn(Hole-7, Jackson-6)\n" +
                "pobj(in-5, Hole-7)\n",

        "root(ROOT-0, Who-1)\n" +
                "cop(Who-1, am-2)\n" +
                "nsubj(Who-1, I-3)\n" +
                "aux(judge-5, to-4)\n" +
                "vmod(Who-1, judge-5)\n",

        "nsubj(man-5, Bill-1)\n" +
                "cop(man-5, is-2)\n" +
                "det(man-5, an-3)\n" +
                "amod(man-5, honest-4)\n" +
                "root(ROOT-0, man-5)\n",

        "det(dignity-2, What-1)\n" +
                "nsubj(is-3, dignity-2)\n" +
                "root(ROOT-0, is-3)\n" +
                "expl(is-3, there-4)\n" +
                "prep(is-3, in-5)\n" +
                "pobj(in-5, that-6)\n",

        "nsubj(becoming-3, Hand-holding-1)\n" +
                "aux(becoming-3, is-2)\n" +
                "root(ROOT-0, becoming-3)\n" +
                "det(requirement-7, an-4)\n" +
                "nn(requirement-7, investment-banking-5)\n" +
                "nn(requirement-7, job-6)\n" +
                "xcomp(becoming-3, requirement-7)\n",

        "nsubj(wrong-3, What-1)\n" +
                "cop(wrong-3, is-2)\n" +
                "root(ROOT-0, wrong-3)\n" +
                "prep(wrong-3, with-4)\n" +
                "pcomp(with-4, expecting-5)\n" +
                "dobj(expecting-5, pizza-6)\n",

        "nsubj(going-3, Who-1)\n" +
                "nsubj(carry-5, Who-1)\n" +
                "aux(going-3, is-2)\n" +
                "root(ROOT-0, going-3)\n" +
                "aux(carry-5, to-4)\n" +
                "xcomp(going-3, carry-5)\n" +
                "det(water-7, the-6)\n" +
                "dobj(carry-5, water-7)\n",

        "dobj(doing-4, What-1)\n" +
                "aux(doing-4, am-2)\n" +
                "nsubj(doing-4, I-3)\n" +
                "root(ROOT-0, doing-4)\n" +
                "vmod(doing-4, dating-5)\n" +
                "dobj(dating-5, her-6)\n",

        "root(ROOT-0, What-1)\n" +
                "cop(What-1, is-2)\n" +
                "nsubj(What-1, that-3)\n",

        "root(ROOT-0, Who-1)\n" +
                "cop(Who-1, is-2)\n" +
                "nsubj(Who-1, John-3)\n",

        "det(dog-2, What-1)\n" +
                "nsubj(barking-4, dog-2)\n" +
                "aux(barking-4, is-3)\n" +
                "root(ROOT-0, barking-4)\n" +
                "advmod(loudly-6, so-5)\n" +
                "advmod(barking-4, loudly-6)\n",

        "nsubj(barking-3, Who-1)\n" +
                "aux(barking-3, is-2)\n" +
                "root(ROOT-0, barking-3)\n" +
                "advmod(much-5, so-4)\n" +
                "advmod(barking-3, much-5)\n",

        "advmod(becoming-4, Why-1)\n" +
                "aux(becoming-4, is-2)\n" +
                "nsubj(becoming-4, Dave-3)\n" +
                "root(ROOT-0, becoming-4)\n" +
                "det(problem-6, a-5)\n" +
                "xcomp(becoming-4, problem-6)\n",

        "dobj(worth-5, What-1)\n" +
                "cop(worth-5, is-2)\n" +
                "nn(stock-4, UAL-3)\n" +
                "nsubj(worth-5, stock-4)\n" +
                "root(ROOT-0, worth-5)\n",

        "root(ROOT-0, Who-1)\n" +
                "cop(Who-1, am-2)\n" +
                "nsubj(Who-1, I-3)\n",

        "nsubj(told-2, Who-1)\n" +
                "root(ROOT-0, told-2)\n" +
                "dobj(told-2, him-3)\n",

        "nsubj(lawyer-4, Sue-1)\n" +
                "cop(lawyer-4, is-2)\n" +
                "det(lawyer-4, a-3)\n" +
                "root(ROOT-0, lawyer-4)\n",

        "nsubj(intelligent-3, Sue-1)\n" +
                "cop(intelligent-3, is-2)\n" +
                "root(ROOT-0, intelligent-3)\n",

        "nsubj(nervous-3, Who-1)\n" +
                "cop(nervous-3, is-2)\n" +
                "root(ROOT-0, nervous-3)\n",

        "expl(is-2, There-1)\n" +
                "root(ROOT-0, is-2)\n" +
                "det(cow-4, a-3)\n" +
                "nsubj(is-2, cow-4)\n" +
                "prep(is-2, in-5)\n" +
                "det(field-7, the-6)\n" +
                "pobj(in-5, field-7)\n",

        "nsubj(is-2, What-1)\n" +
                "root(ROOT-0, is-2)\n" +
                "expl(is-2, there-3)\n" +
                "prep(is-2, in-4)\n" +
                "det(field-6, the-5)\n" +
                "pobj(in-4, field-6)\n",

        "advmod(are-2, Here-1)\n" +
                "root(ROOT-0, are-2)\n" +
                "det(bags-4, some-3)\n" +
                "nsubj(are-2, bags-4)\n",

        "nsubj(is-2, He-1)\n" +
                "root(ROOT-0, is-2)\n" +
                "prep(is-2, in-3)\n" +
                "det(garden-5, the-4)\n" +
                "pobj(in-3, garden-5)\n",

        "nsubj('s-2, What-1)\n" +
                "root(ROOT-0, 's-2)\n" +
                "prep('s-2, on-3)\n" +
                "det(test-5, the-4)\n" +
                "pobj(on-3, test-5)\n",

        "advmod(pink-5, Why-1)\n" +
                "cop(pink-5, is-2)\n" +
                "det(dog-4, the-3)\n" +
                "nsubj(pink-5, dog-4)\n" +
                "root(ROOT-0, pink-5)\n",

        "det(dog-2, The-1)\n" +
                "nsubj(pink-4, dog-2)\n" +
                "cop(pink-4, is-3)\n" +
                "root(ROOT-0, pink-4)\n",

        "det(disease-2, What-1)\n" +
                "nsubj(causes-3, disease-2)\n" +
                "root(ROOT-0, causes-3)\n" +
                "dobj(causes-3, pain-4)\n",

        "det(disease-2, What-1)\n" +
                "nsubj(causes-3, disease-2)\n" +
                "root(ROOT-0, causes-3)\n" +
                "dobj(causes-3, pain-4)\n",

            "aux(waiting-2, Be-1)\n" +
                    "root(ROOT-0, waiting-2)\n" +
                    "prep(waiting-2, in-3)\n" +
                    "pobj(in-3, line-4)\n" +
                    "prep(waiting-2, at-5)\n" +
                    "num(p.m.-7, 3-6)\n" +
                    "pobj(at-5, p.m.-7)\n",

            "cop(man-3, Be-1)\n" +
                     "det(man-3, a-2)\n" +
                     "root(ROOT-0, man-3)\n",

        "advmod(worth-6, So-1)\n" +
                "dobj(worth-6, what-2)\n" +
                "cop(worth-6, is-3)\n" +
                "nn(Fe-5, Santa-4)\n" +
                "nsubj(worth-6, Fe-5)\n" +
                "root(ROOT-0, worth-6)\n",

        "root(ROOT-0, What-1)\n" +
                "cop(What-1, is-2)\n" +
                "poss(sister-4, your-3)\n" +
                "poss(name-6, sister-4)\n" +
                "possessive(sister-4, 's-5)\n" +
                "nsubj(What-1, name-6)\n",

        "dobj(called-7, What-1)\n" +
                "auxpass(called-7, is-2)\n" +
                "det(fear-4, the-3)\n" +
                "nsubjpass(called-7, fear-4)\n" +
                "prep(fear-4, of-5)\n" +
                "pobj(of-5, cockroaches-6)\n" +
                "root(ROOT-0, called-7)\n",
        "pobj(at-5, What-1)\n" +
                "cop(good-4, am-2)\n" +
                "nsubj(good-4, I-3)\n" +
                "root(ROOT-0, good-4)\n" +
                "prep(good-4, at-5)\n"
    };

    assertEquals("Test array and basic answer array lengths mismatch!", testTrees.length, basicAnswers.length);
    assertEquals("Test array and noncollapsed answer array lengths mismatch!", testTrees.length, noncollapsedAnswers.length);
    // TreeReaderFactory trf = new PennTreeReaderFactory();
    TreeReaderFactory trf = new NPTmpRetainingTreeNormalizer.NPTmpAdvRetainingTreeReaderFactory();
    for (int i = 0; i < testTrees.length; i++) {
      String testTree = testTrees[i];
      String basicAnswer = basicAnswers[i];
      String noncollapsedAnswer = noncollapsedAnswers[i];

      // specifying our own TreeReaderFactory is vital so that functional
      // categories - that is -TMP and -ADV in particular - are not stripped off
      Tree tree = Tree.valueOf(testTree, trf);
      GrammaticalStructure gs = new EnglishGrammaticalStructure(tree);

      assertEquals("Unexpected basic dependencies for tree " + testTree,
          basicAnswer, GrammaticalStructureConversionUtils.dependenciesToString(gs, gs.typedDependencies(GrammaticalStructure.Extras.NONE), tree, false, false, false));
      assertEquals("Unexpected noncollapsed dependencies for tree " + testTree,
          noncollapsedAnswer, GrammaticalStructureConversionUtils.dependenciesToString(gs, gs.typedDependencies(GrammaticalStructure.Extras.MAXIMAL), tree, false, false, false));
    }
  }

  /**
   * Tests that we can extract the basic grammatical relations correctly from
   * some hard-coded trees.
   *
   * Sentence examples from the manual to at least test each relation.
   */
  @Test
  public void testBasicRelationsWithCopulaAsHead() {
    // the trees to test
    String[] testTrees = {
         "(ROOT (S (NP (NNP Reagan)) (VP (VBZ has) (VP (VBN died))) (. .)))",
         "(ROOT (S (NP (NNP Kennedy)) (VP (VBZ has) (VP (VBN been) (VP (VBN killed)))) (. .)))",
         "(ROOT (S (NP (NNP Bill)) (VP (VBZ is) (NP (DT an) (JJ honest) (NN man))) (. .)))",
         "(ROOT (S (NP (NNP Bill)) (VP (VBZ is) (ADJP (JJ big) (CC and) (JJ honest))) (. .)))",
         "(ROOT (S (NP (NNP Clinton)) (VP (VBD defeated) (NP (NNP Dole))) (. .)))",
         "(ROOT (S (SBAR (WHNP (WP What)) (S (NP (PRP she)) (VP (VBD said)))) (VP (VBZ is) (ADJP (JJ untrue))) (. .)))",
         "(ROOT (S (NP (NNP Dole)) (VP (VBD was) (VP (VBN defeated) (PP (IN by) (NP (NNP Clinton))))) (. .)))",
         "(ROOT (S (NP (PRP I)) (VP (VBP like) (S (VP (TO to) (VP (VB swim))))) (. .)))",
         "(ROOT (S (NP (PRP We)) (VP (VBP have) (NP (NP (DT no) (JJ useful) (NN information)) (PP (IN on) (SBAR (IN whether) (S (NP (NNS users)) (VP (VBP are) (PP (IN at) (NP (NN risk))))))))) (. .)))",
         "(ROOT (S (NP (PRP I)) (VP (VBP am) (ADJP (JJ certain) (SBAR (IN that) (S (NP (PRP he)) (VP (VBD did) (NP (PRP it))))))) (. .)))",
         "(ROOT (S (NP (PRP I)) (VP (VBP am) (ADJP (JJ ready) (S (VP (TO to) (VP (VB leave)))))) (. .)))",
         "(ROOT (S (NP (EX There)) (VP (VBZ is) (NP (NP (DT a) (NN statue)) (PP (IN in) (NP (DT the) (NN corner))))) (. .)))",
         "(ROOT (S (NP (DT The) (NN director)) (VP (VBZ is) (ADJP (NP (CD 65) (NNS years)) (JJ old))) (. .)))",
         "(ROOT (S (NP (DT The) (NN man)) (VP (VBZ is) (ADVP (RB here))) (. .)))",
         "(ROOT (S (NP (NNP Xml) (NN field)) (VP (MD should) (VP (VB include) (NP (PDT both) (NP (DT the) (NN entity) (NN id)) (CC and) (NP (DT the) (NN entity) (NN name))) (SBAR (IN since) (S (NP (DT the) (NN entity) (NNS names)) (VP (VBP are) (RB not) (ADJP (JJ unique))))))) (. .)))",
         "(ROOT (S (S (NP (DT The) (NN government)) (VP (VBZ counts) (NP (NN money)) (SBAR (IN as) (S (NP (PRP it)) (VP (VBZ is) (VP (VBN spent))))))) (: ;) (S (NP (NNP Dodge)) (VP (VBZ counts) (NP (NNS contracts)) (SBAR (WHADVP (WRB when)) (S (NP (PRP they)) (VP (VBP are) (VP (VBN awarded))))))) (. .)))",
         "(ROOT (SBARQ (WHNP (WP What)) (SQ (VBP am) (NP (PRP I)) (ADJP (JJ good) (PP (IN at)))) (. ?)))",

    };

    // the expected dependency answers (basic)
    String[] testAnswers = {
        "nsubj(died-3, Reagan-1)\n" + "aux(died-3, has-2)\n" + "root(ROOT-0, died-3)\n",
        "nsubjpass(killed-4, Kennedy-1)\n" + "aux(killed-4, has-2)\n" + "auxpass(killed-4, been-3)\n" + "root(ROOT-0, killed-4)\n",
        "nsubj(is-2, Bill-1)\n" + "root(ROOT-0, is-2)\n" + "det(man-5, an-3)\n" + "amod(man-5, honest-4)\n" + "xcomp(is-2, man-5)\n",
        "nsubj(is-2, Bill-1)\n" + "root(ROOT-0, is-2)\n" + "acomp(is-2, big-3)\n" + "cc(big-3, and-4)\n" + "conj(big-3, honest-5)\n",
        "nsubj(defeated-2, Clinton-1)\n" + "root(ROOT-0, defeated-2)\n" + "dobj(defeated-2, Dole-3)\n",
        "dobj(said-3, What-1)\n" + "nsubj(said-3, she-2)\n" + "csubj(is-4, said-3)\n" + "root(ROOT-0, is-4)\n" + "acomp(is-4, untrue-5)\n",
        "nsubjpass(defeated-3, Dole-1)\n" + "auxpass(defeated-3, was-2)\n" + "root(ROOT-0, defeated-3)\n" + "prep(defeated-3, by-4)\n" + "pobj(by-4, Clinton-5)\n",
        "nsubj(like-2, I-1)\n" + "root(ROOT-0, like-2)\n" + "aux(swim-4, to-3)\n" + "xcomp(like-2, swim-4)\n",
        "nsubj(have-2, We-1)\n" + "root(ROOT-0, have-2)\n" + "neg(information-5, no-3)\n" + "amod(information-5, useful-4)\n" + "dobj(have-2, information-5)\n" + "prep(information-5, on-6)\n" + "mark(are-9, whether-7)\n" + "nsubj(are-9, users-8)\n" + "pcomp(on-6, are-9)\n" + "prep(are-9, at-10)\n" + "pobj(at-10, risk-11)\n",
        "nsubj(am-2, I-1)\n" + "root(ROOT-0, am-2)\n" + "acomp(am-2, certain-3)\n" + "mark(did-6, that-4)\n" + "nsubj(did-6, he-5)\n" + "ccomp(certain-3, did-6)\n" + "dobj(did-6, it-7)\n",
        "nsubj(am-2, I-1)\n" + "root(ROOT-0, am-2)\n" + "acomp(am-2, ready-3)\n" + "aux(leave-5, to-4)\n" + "xcomp(ready-3, leave-5)\n",
        "expl(is-2, There-1)\n" + "root(ROOT-0, is-2)\n" + "det(statue-4, a-3)\n" + "nsubj(is-2, statue-4)\n" + "prep(statue-4, in-5)\n" + "det(corner-7, the-6)\n" + "pobj(in-5, corner-7)\n",
        "det(director-2, The-1)\n" + "nsubj(is-3, director-2)\n" + "root(ROOT-0, is-3)\n" + "num(years-5, 65-4)\n" + "npadvmod(old-6, years-5)\n" + "acomp(is-3, old-6)\n",
        "det(man-2, The-1)\n" + "nsubj(is-3, man-2)\n" + "root(ROOT-0, is-3)\n" + "advmod(is-3, here-4)\n",
        "nn(field-2, Xml-1)\n" +
                "nsubj(include-4, field-2)\n" +
                "aux(include-4, should-3)\n" + "root(ROOT-0, include-4)\n" +
                "preconj(id-8, both-5)\n" +
                "det(id-8, the-6)\n" +
                "nn(id-8, entity-7)\n" +
                "dobj(include-4, id-8)\n" +
                "cc(id-8, and-9)\n" +
                "det(name-12, the-10)\n" +
                "nn(name-12, entity-11)\n" +
                "conj(id-8, name-12)\n" +
                "mark(are-17, since-13)\n" +
                "det(names-16, the-14)\n" +
                "nn(names-16, entity-15)\n" +
                "nsubj(are-17, names-16)\n" +
                "advcl(include-4, are-17)\n" +
                "neg(are-17, not-18)\n" +
                "acomp(are-17, unique-19)\n",
        "det(government-2, The-1)\n" +
                "nsubj(counts-3, government-2)\n" + "root(ROOT-0, counts-3)\n" +
                "dobj(counts-3, money-4)\n" +
                "mark(spent-8, as-5)\n" +
                "nsubjpass(spent-8, it-6)\n" +
                "auxpass(spent-8, is-7)\n" +
                "advcl(counts-3, spent-8)\n" +
                "nsubj(counts-11, Dodge-10)\n" +
                "parataxis(counts-3, counts-11)\n" +
                "dobj(counts-11, contracts-12)\n" +
                "advmod(awarded-16, when-13)\n" +
                "nsubjpass(awarded-16, they-14)\n" +
                "auxpass(awarded-16, are-15)\n" +
                "advcl(counts-11, awarded-16)\n",
        "pobj(at-5, What-1)\n" +
                "root(ROOT-0, am-2)\n" +
                "nsubj(am-2, I-3)\n" +
                "acomp(am-2, good-4)\n" +
                "prep(good-4, at-5)\n"
    };

    assertEquals("Test array lengths mismatch!", testTrees.length, testAnswers.length);
    TreeReaderFactory trf = new PennTreeReaderFactory();
    for (int i = 0; i < testTrees.length; i++) {
      String testTree = testTrees[i];
      String testAnswer = testAnswers[i];

      // specifying our own TreeReaderFactory is vital so that functional
      // categories - that is -TMP and -ADV in particular - are not stripped off
      Tree tree = Tree.valueOf(testTree, trf);

      GrammaticalStructure gs = new EnglishGrammaticalStructure(tree,
              new PennTreebankLanguagePack().punctuationWordRejectFilter(),
              new SemanticHeadFinder(false));

      assertEquals("Unexpected basic dependencies for tree "+testTree,
          testAnswer, GrammaticalStructureConversionUtils.dependenciesToString(gs, gs.typedDependencies(GrammaticalStructure.Extras.NONE), tree, false, false, false));
    }
  }

  /**
   * Tests that we can extract the non-collapsed grammatical relations (basic + extra)
   * correctly from some hard-coded trees.
   *
   */
  @Test
  public void testNonCollapsedRelations() {
    // the trees to test
    String[] testTrees = {
         "(ROOT (S (NP (PRP I)) (VP (VBP like) (S (VP (TO to) (VP (VB swim))))) (. .)))",
         "(ROOT (S (NP (PRP He)) (VP (VBZ says) (SBAR (IN that) (S (NP (PRP you)) (VP (VBP like) (S (VP (TO to) (VP (VB swim)))))))) (. .)))",
         "(ROOT (S (NP (PRP I)) (VP (VBD saw) (NP (NP (DT the) (NN man)) (SBAR (WHNP (WP who)) (S (NP (PRP you)) (VP (VBP love)))))) (. .)))",
         "(ROOT (S (NP (PRP I)) (VP (VBD saw) (NP (NP (DT the) (NN man)) (SBAR (WHNP (WP$ whose) (NP (NN wife))) (S (NP (PRP you)) (VP (VBP love)))))) (. .)))",
         "(ROOT (S (NP (PRP I)) (VP (VBD saw) (NP (NP (DT the) (NN book)) (SBAR (WHNP (WDT which)) (S (NP (PRP you)) (VP (VBD bought)))))) (. .)))",
         "(ROOT (SBARQ (WHNP (WP What)) (SQ (VBZ is) (NP (DT the) (NN esophagus)) (VP (VBN used) (PP (IN for)))) (? ?)))",
         "(ROOT (S (NP (PRP I)) (VP (VBD saw) (NP (NP (DT the) (NN woman)) (SBAR (WHNP (WP whom)) (S (NP (PRP you)) (VP (VBD gave) (NP (DT the) (NN package)) (PP (TO to))))))) (. .)))",

    };

    // the expected dependency answers (basic + extra)
    String[] testAnswers = {
        "nsubj(like-2, I-1)\n" + "nsubj(swim-4, I-1)\n" + "root(ROOT-0, like-2)\n" + "aux(swim-4, to-3)\n" + "xcomp(like-2, swim-4)\n",
        "nsubj(says-2, He-1)\n" + "root(ROOT-0, says-2)\n" + "mark(like-5, that-3)\n" + "nsubj(like-5, you-4)\n" + "nsubj(swim-7, you-4)\n" + "ccomp(says-2, like-5)\n" + "aux(swim-7, to-6)\n" + "xcomp(like-5, swim-7)\n",
        "nsubj(saw-2, I-1)\n" + "root(ROOT-0, saw-2)\n" + "det(man-4, the-3)\n" + "dobj(saw-2, man-4)\n" + "ref(man-4, who-5)\n" + "dobj(love-7, who-5)\n" + "nsubj(love-7, you-6)\n" + "rcmod(man-4, love-7)\n",
        "nsubj(saw-2, I-1)\n" + "root(ROOT-0, saw-2)\n" + "det(man-4, the-3)\n" + "dobj(saw-2, man-4)\n" + "ref(man-4, whose-5)\n" + "poss(wife-6, whose-5)\n" + "dobj(love-8, wife-6)\n" + "nsubj(love-8, you-7)\n" + "rcmod(man-4, love-8)\n",
        "nsubj(saw-2, I-1)\n" + "root(ROOT-0, saw-2)\n" + "det(book-4, the-3)\n" + "dobj(saw-2, book-4)\n" + "ref(book-4, which-5)\n" + "dobj(bought-7, which-5)\n" + "nsubj(bought-7, you-6)\n" + "rcmod(book-4, bought-7)\n",
        "pobj(for-6, What-1)\n" + "auxpass(used-5, is-2)\n" + "det(esophagus-4, the-3)\n" + "nsubjpass(used-5, esophagus-4)\n" + "root(ROOT-0, used-5)\n" + "prep(used-5, for-6)\n",
        "nsubj(saw-2, I-1)\n" +
                    "root(ROOT-0, saw-2)\n" +
                    "det(woman-4, the-3)\n" +
                    "dobj(saw-2, woman-4)\n" +
                    "ref(woman-4, whom-5)\n" +
                    "pobj(to-10, whom-5)\n" +
                    "nsubj(gave-7, you-6)\n" +
                    "rcmod(woman-4, gave-7)\n" +
                    "det(package-9, the-8)\n" +
                    "dobj(gave-7, package-9)\n" +
                    "prep(gave-7, to-10)\n",

    };

    assertEquals("Test array lengths mismatch!", testTrees.length, testAnswers.length);
    TreeReaderFactory trf = new PennTreeReaderFactory();
    for (int i = 0; i < testTrees.length; i++) {
      String testTree = testTrees[i];
      String testAnswer = testAnswers[i];

      // specifying our own TreeReaderFactory is vital so that functional
      // categories - that is -TMP and -ADV in particular - are not stripped off
      Tree tree = Tree.valueOf(testTree, trf);
      GrammaticalStructure gs = new EnglishGrammaticalStructure(tree);

      assertEquals("Unexpected basic dependencies for tree "+testTree,
          testAnswer, GrammaticalStructureConversionUtils.dependenciesToString(gs, gs.allTypedDependencies(), tree, false, false, false));
    }
  }


  /**
   * Tests printing of the extra dependencies after the basic ones.
   *
   */
  @Test
  public void testNonCollapsedSeparator() {
    // the trees to test
    String[] testTrees = {
        "(ROOT (S (NP (PRP I)) (VP (VBP like) (S (VP (TO to) (VP (VB swim))))) (. .)))",

    };

    // the expected dependency answers (basic + extra)
    String[] testAnswers = {
        "nsubj(like-2, I-1)\n" + "root(ROOT-0, like-2)\n" + "aux(swim-4, to-3)\n" + "xcomp(like-2, swim-4)\n" + "======\n" + "nsubj(swim-4, I-1)\n",

    };

    assertEquals("Test array lengths mismatch!", testTrees.length, testAnswers.length);
    TreeReaderFactory trf = new PennTreeReaderFactory();
    for (int i = 0; i < testTrees.length; i++) {
      String testTree = testTrees[i];
      String testAnswer = testAnswers[i];

      // specifying our own TreeReaderFactory is vital so that functional
      // categories - that is -TMP and -ADV in particular - are not stripped off
      Tree tree = Tree.valueOf(testTree, trf);
      GrammaticalStructure gs = new EnglishGrammaticalStructure(tree);

      assertEquals("Unexpected basic dependencies for tree "+testTree,
        testAnswer, GrammaticalStructureConversionUtils.dependenciesToString(gs, gs.allTypedDependencies(), tree, false, true, false));
    }

  }


  /**
   * Tests that we can extract the collapsed grammatical relations correctly from
   * some hard-coded trees.
   *
   */
  @Test
  public void testCollapsedRelations() {
    // the trees to test
    String[] testTrees = {
         "(ROOT (S (NP (NNP Dole)) (VP (VBD was) (VP (VBN defeated) (PP (IN by) (NP (NNP Clinton))))) (. .)))",
         "(ROOT (S (SBAR (IN That) (S (NP (PRP she)) (VP (VBD lied)))) (VP (VBD was) (VP (VBN suspected) (PP (IN by) (NP (NN everyone))))) (. .)))",
         "(ROOT (S (NP (PRP I)) (VP (VBP like) (S (VP (TO to) (VP (VB swim))))) (. .)))",
         "(ROOT (S (NP (PRP I)) (VP (VBD sat) (PP (IN on) (NP (DT the) (NN chair)))) (. .)))",
         "(ROOT (S (NP (PRP We)) (VP (VBP have) (NP (NP (DT no) (JJ useful) (NN information)) (PP (IN on) (SBAR (IN whether) (S (NP (NNS users)) (VP (VBP are) (PP (IN at) (NP (NN risk))))))))) (. .)))",
         "(ROOT (S (NP (PRP They)) (VP (VBD heard) (PP (IN about) (NP (NN asbestos))) (S (VP (VBG having) (NP (JJ questionable) (NNS properties))))) (. .)))",
         "(ROOT (S (NP (PRP He)) (VP (VBZ says) (SBAR (IN that) (S (NP (PRP you)) (VP (VBP like) (S (VP (TO to) (VP (VB swim)))))))) (. .)))",
         "(ROOT (S (NP (NNP U.S.) (NNS forces)) (VP (VBP have) (VP (VBN been) (VP (VBN engaged) (PP (IN in) (NP (JJ intense) (NN fighting))) (SBAR (IN after) (S (NP (NNS insurgents)) (VP (VBD launched) (NP (JJ simultaneous) (NNS attacks)))))))) (. .)))",
         "(ROOT (S (NP (PRP I)) (VP (VBD saw) (NP (NP (DT the) (NN man)) (SBAR (WHNP (WP who)) (S (NP (PRP you)) (VP (VBP love)))))) (. .)))",
         "(ROOT (S (NP (PRP I)) (VP (VBD saw) (NP (NP (DT the) (NN man)) (SBAR (WHNP (WP$ whose) (NP (NN wife))) (S (NP (PRP you)) (VP (VBP love)))))) (. .)))",
         "(ROOT (S (NP (EX There)) (VP (VBZ is) (NP (NP (DT a) (NN statue)) (PP (IN in) (NP (DT the) (NN corner))))) (. .)))",
         "(ROOT (S (NP (PRP He)) (VP (VBD talked) (PP (TO to) (NP (DT the) (NN president))) (SBAR (IN in) (NN order) (S (VP (TO to) (VP (VB secure) (NP (DT the) (NN account))))))) (. .)))",
         "(ROOT (S (NP (PRP I)) (VP (VBD saw) (NP (NP (DT the) (NN book)) (SBAR (WHNP (WDT which)) (S (NP (PRP you)) (VP (VBD bought)))))) (. .)))",
         "(ROOT (S (NP (NNP Bill)) (VP (VBD picked) (NP (NP (NNP Fred)) (PP (IN for) (NP (NP (DT the) (NN team)) (VP (VBG demonstrating) (NP (PRP$ his) (NN incompetence))))))) (. .)))",
         "(ROOT (SBARQ (WHPP (IN In) (WHNP (WDT which) (NN city))) (SQ (VBP do) (NP (PRP you)) (VP (VB live))) (. ?)))",
         "(ROOT (SBARQ (WHNP (WP What)) (SQ (VBZ is) (NP (DT the) (NN esophagus)) (VP (VBN used) (PP (IN for)))) (? ?)))",
         "(ROOT (S (NP (CC Both) (NP (DT the) (NNS boys)) (CC and) (NP (DT the) (NNS girls))) (VP (VBP are) (ADVP (RB here))) (. .)))",
         "( (S (NP (NNP Fred)) (VP (VBD flew) (PP (IN across) (CC or) (IN across) (NP (NNP Serbia)))) (. .)))", // This is a buggy parse tree, but our parser produces structures like this sometimes, and the collapsing process shouldn't barf on them.
         "(ROOT (SBARQ (WHPP (IN For) (WHNP (WRB how) (JJ long))) (SQ (VBZ is) (NP (DT an) (NN elephant)) (ADJP (JJ pregnant))) (. ?)))",
         "( (S (NP-SBJ-1 (PRP He)) (VP (VBD achieved) (NP (DT this)) (PP-MNR (PP (IN in) (NP (NN part))) (IN through) (NP (DT an) (JJ uncanny) (NN talent)))) (. .)))",
         "(ROOT (S (NP (DT The) (NN turf)) (ADVP (RB recently)) (VP (VBZ has) (VP (VBN ranged) (PP (PP (IN from) (NP (NNP Chile))) (PP (TO to) (NP (NNP Austria))) (PP (TO to) (NP (NNP Portugal)))))) (. .)))",
         "( (S (CC But) (NP (NP (NNP Ms.) (NNP Poore)) (, ,) (NP (NP (DT the) (NN magazine) (POS 's)) (NN editor) (CC and) (NN publisher)) (, ,)) (VP (VBD resigned)) (. .)))",
         "( (S (NP (EX There)) (VP (VBZ 's) (ADVP (RB never)) (VP (VBN been) (NP (DT an) (NN exception)))) (. .)))",
         // this next POS shouldn't disappear in collapsing!
         "( (S (NP (NNP Sotheby) (POS 's)) (PRN (, ,) (S (NP (PRP she)) (VP (VBZ says))) (, ,)) (VP (VBZ is) (VP (`` ``) (VBG wearing) (NP (DT both) (NNS hats)))) (. .) ('' '')))",
         "( (S (NP (NP (JJ Average) (NN maturity)) (PP (IN of) (NP (NP (NML (DT the) (NNS funds)) (POS ')) (NNS investments)))) (VP (VBD lengthened)) (. .)))",

    };

    // the expected dependency answers (collapsed)
    String[] testAnswers = {
        "nsubjpass(defeated-3, Dole-1)\n" + "auxpass(defeated-3, was-2)\n" + "root(ROOT-0, defeated-3)\n" + "agent(defeated-3, Clinton-5)\n",
        "mark(lied-3, That-1)\n" + "nsubj(lied-3, she-2)\n" + "csubjpass(suspected-5, lied-3)\n" + "auxpass(suspected-5, was-4)\n" + "root(ROOT-0, suspected-5)\n" + "agent(suspected-5, everyone-7)\n",
        "nsubj(like-2, I-1)\n" + "nsubj(swim-4, I-1)\n" + "root(ROOT-0, like-2)\n" + "aux(swim-4, to-3)\n" + "xcomp(like-2, swim-4)\n",
        "nsubj(sat-2, I-1)\n" + "root(ROOT-0, sat-2)\n" + "det(chair-5, the-4)\n" + "prep_on(sat-2, chair-5)\n",
        "nsubj(have-2, We-1)\n" + "root(ROOT-0, have-2)\n" + "neg(information-5, no-3)\n" + "amod(information-5, useful-4)\n" + "dobj(have-2, information-5)\n" + "mark(are-9, whether-7)\n" + "nsubj(are-9, users-8)\n" + "prepc_on(information-5, are-9)\n" + "prep_at(are-9, risk-11)\n",
        "nsubj(heard-2, They-1)\n" + "root(ROOT-0, heard-2)\n" + "prep_about(heard-2, asbestos-4)\n" + "xcomp(heard-2, having-5)\n" + "amod(properties-7, questionable-6)\n" + "dobj(having-5, properties-7)\n",
        "nsubj(says-2, He-1)\n" + "root(ROOT-0, says-2)\n" + "mark(like-5, that-3)\n" + "nsubj(like-5, you-4)\n" + "nsubj(swim-7, you-4)\n" + "ccomp(says-2, like-5)\n" + "aux(swim-7, to-6)\n" + "xcomp(like-5, swim-7)\n",
        "nn(forces-2, U.S.-1)\n" + "nsubjpass(engaged-5, forces-2)\n" + "aux(engaged-5, have-3)\n" + "auxpass(engaged-5, been-4)\n" + "root(ROOT-0, engaged-5)\n" + "amod(fighting-8, intense-7)\n" + "prep_in(engaged-5, fighting-8)\n" + "mark(launched-11, after-9)\n" + "nsubj(launched-11, insurgents-10)\n" + "advcl(engaged-5, launched-11)\n" + "amod(attacks-13, simultaneous-12)\n" + "dobj(launched-11, attacks-13)\n",
        "nsubj(saw-2, I-1)\n" + "root(ROOT-0, saw-2)\n" + "det(man-4, the-3)\n" + "dobj(saw-2, man-4)\n" + "dobj(love-7, man-4)\n" + "nsubj(love-7, you-6)\n" + "rcmod(man-4, love-7)\n",
        "nsubj(saw-2, I-1)\n" + "root(ROOT-0, saw-2)\n" + "det(man-4, the-3)\n" + "dobj(saw-2, man-4)\n" + "poss(wife-6, man-4)\n" + "dobj(love-8, wife-6)\n" + "nsubj(love-8, you-7)\n" + "rcmod(man-4, love-8)\n",
        "expl(is-2, There-1)\n" + "root(ROOT-0, is-2)\n" + "det(statue-4, a-3)\n" + "nsubj(is-2, statue-4)\n" + "det(corner-7, the-6)\n" + "prep_in(statue-4, corner-7)\n",
        "nsubj(talked-2, He-1)\n" + "root(ROOT-0, talked-2)\n" + "det(president-5, the-4)\n" + "prep_to(talked-2, president-5)\n" + "mark(secure-9, in-6)\n" + "dep(secure-9, order-7)\n" + "aux(secure-9, to-8)\n" + "advcl(talked-2, secure-9)\n" + "det(account-11, the-10)\n" + "dobj(secure-9, account-11)\n",
        "nsubj(saw-2, I-1)\n" + "root(ROOT-0, saw-2)\n" + "det(book-4, the-3)\n" + "dobj(saw-2, book-4)\n" + "dobj(bought-7, book-4)\n" + "nsubj(bought-7, you-6)\n" + "rcmod(book-4, bought-7)\n",
        "nsubj(picked-2, Bill-1)\n" + "root(ROOT-0, picked-2)\n" + "dobj(picked-2, Fred-3)\n" + "det(team-6, the-5)\n" + "prep_for(Fred-3, team-6)\n" + "vmod(team-6, demonstrating-7)\n" + "poss(incompetence-9, his-8)\n" + "dobj(demonstrating-7, incompetence-9)\n",
        "det(city-3, which-2)\n" + "prep_in(live-6, city-3)\n" + "aux(live-6, do-4)\n" + "nsubj(live-6, you-5)\n" + "root(ROOT-0, live-6)\n",
        "prep_for(used-5, What-1)\n" + "auxpass(used-5, is-2)\n" + "det(esophagus-4, the-3)\n" + "nsubjpass(used-5, esophagus-4)\n" + "root(ROOT-0, used-5)\n",
        "preconj(boys-3, Both-1)\n" + "det(boys-3, the-2)\n" + "nsubj(are-7, boys-3)\n" + "det(girls-6, the-5)\n" + "conj_and(boys-3, girls-6)\n" + "root(ROOT-0, are-7)\n" + "advmod(are-7, here-8)\n",
        "nsubj(flew-2, Fred-1)\n" + "root(ROOT-0, flew-2)\n" + "prep_across(flew-2, Serbia-6)\n",
        "advmod(long-3, how-2)\n" +
                "prep_for(pregnant-7, long-3)\n" +
                "cop(pregnant-7, is-4)\n" +
                "det(elephant-6, an-5)\n" +
                "nsubj(pregnant-7, elephant-6)\n" + "root(ROOT-0, pregnant-7)\n",
        "nsubj(achieved-2, He-1)\nroot(ROOT-0, achieved-2)\ndobj(achieved-2, this-3)\nprep_in(achieved-2, part-5)\ndet(talent-9, an-7)\namod(talent-9, uncanny-8)\nprep_through(achieved-2, talent-9)\n",
                "det(turf-2, The-1)\n" +
                "nsubj(ranged-5, turf-2)\n" +
                "advmod(ranged-5, recently-3)\n" +
                "aux(ranged-5, has-4)\n" + "root(ROOT-0, ranged-5)\n" +
                "prep_from(ranged-5, Chile-7)\n" +
                "prep_to(ranged-5, Austria-9)\n" +
                "prep_to(ranged-5, Portugal-11)\n",
        "cc(resigned-12, But-1)\n" +
                "nn(Poore-3, Ms.-2)\n" +
                "nsubj(resigned-12, Poore-3)\n" +
                "det(magazine-6, the-5)\n" +
                "poss(editor-8, magazine-6)\n" +
                "appos(Poore-3, editor-8)\n" +
                "conj_and(editor-8, publisher-10)\n" + "root(ROOT-0, resigned-12)\n",
        "expl(exception-6, There-1)\n" +
                "auxpass(exception-6, 's-2)\n" +
                "neg(exception-6, never-3)\n" +
                "cop(exception-6, been-4)\n" +
                "det(exception-6, an-5)\n" + "root(ROOT-0, exception-6)\n",
        "nsubj(wearing-9, Sotheby-1)\n" +
                "possessive(Sotheby-1, 's-2)\n" +
                "nsubj(says-5, she-4)\n" +
                "parataxis(wearing-9, says-5)\n" +
                "aux(wearing-9, is-7)\n" + "root(ROOT-0, wearing-9)\n" +
                "det(hats-11, both-10)\n" +
                "dobj(wearing-9, hats-11)\n",
        "amod(maturity-2, Average-1)\n" +
                "nsubj(lengthened-8, maturity-2)\n" +
                "det(funds-5, the-4)\n" +
                "poss(investments-7, funds-5)\n" +
                "prep_of(maturity-2, investments-7)\n" + "root(ROOT-0, lengthened-8)\n",
    };

    assertEquals("Test array lengths mismatch!", testTrees.length, testAnswers.length);
    TreeReaderFactory trf = new PennTreeReaderFactory();
    for (int i = 0; i < testTrees.length; i++) {
      String testTree = testTrees[i];
      String testAnswer = testAnswers[i];

      // specifying our own TreeReaderFactory is vital so that functional
      // categories - that is -TMP and -ADV in particular - are not stripped off
      Tree tree = Tree.valueOf(testTree, trf);
      GrammaticalStructure gs = new EnglishGrammaticalStructure(tree);

      String depString = GrammaticalStructureConversionUtils.dependenciesToString(gs, gs.typedDependenciesCollapsed(GrammaticalStructure.Extras.MAXIMAL), tree, false, false, false);
      assertEquals("Unexpected collapsed dependencies for tree "+testTree,
          testAnswer, depString);
    }
  }

  /**
   * Tests that we can extract the CCprocessed grammatical relations correctly from
   * some hard-coded trees.
   *
   */
  @Test
  public void testCCProcessedRelations() {
    // the trees to test
    String[] testTrees = {
         "(ROOT (S (NP (NNP Bill)) (VP (VBZ is) (ADJP (JJ big) (CC and) (JJ honest))) (. .)))",
         "(ROOT (S (NP (CC Both) (NP (DT the) (NNS boys)) (CC and) (NP (DT the) (NNS girls))) (VP (VBP are) (ADVP (RB here))) (. .)))",
         "(ROOT (S (NP-SBJ-38 (DT Neither) (NP (PRP they) ) (CC nor) (NP (NNP Mr.) (NNP McAlpine) )) (VP (MD could) (VP (VB be) (VP (VBN reached) (NP (-NONE- *-38) ) (PP-PRP (IN for) (NP (NN comment) ))))) (. .) ))",
         "(ROOT (S (NP (NNP John)) (VP (VBZ works) (PP (DT both) (PP (IN in) (NP (NNP Zurich))) (CC and) (PP (IN in) (NP (NNP London))))) (. .)))",
         "(ROOT (S (NP (NP (NNS Languages)) (PP (PP (IN with) (NP (NNS alphabets))) (CC and) (PP (IN without) (NP (NNS alphabets))))) (VP (VBP are) (ADJP (JJ difficult))) (. .)))",
         "(ROOT (S (NP (PRP$ His) (NN term)) (VP (VP (VBZ has) (VP (VBN produced) (NP (NP (DT no) (JJ spectacular) (NNS failures)) (PP (PP (IN in) (NP (NNS politics))) (, ,) (PP (IN in) (NP (DT the) (NN economy))) (CC or) (PP (IN on) (NP (DT the) (JJ military) (NN front))))))) (, ,) (CC and) (VP (VBZ has) (VP (VBN chalked) (PRT (RP up)) (NP (DT some) (NNS successes))))) (. .)))",
         "(ROOT (S (NP (NNP Fred)) (VP (VBD walked) (PP (PP (IN out) (NP (DT the) (NN door))) (CC and) (PP (RB right) (IN into) (NP (DT a) (NN trap))))) (. .)))",
         "(ROOT (S (NP (NNP Fred)) (VP (VBD walked) (PP (PP (IN into) (NP (DT the) (NN house))) (CC and) (PP (RB right) (IN into) (NP (DT a) (NN trap))))) (. .)))",
         "(ROOT (S (NP (NNP Marie) (CC and) (NNP Chris)) (VP (VP (VBD went) (PRT (RP out))) (, ,) (VP (VBD drank) (NP (NN coffee))) (, ,) (CC and) (VP (VBD talked) (PP (IN about) (NP (NNP Stanford) (NNPS Dependencies))))) (. .)))",
         "(ROOT (S (NP-TMP (DT These) (NNS days)) (NP (PRP he)) (VP (VBZ hustles) (PP (TO to) (NP (JJ house-painting) (NNS jobs))) (PP (IN in) (NP (PRP$ his) (NNP Chevy) (NN pickup))) (PP (IN before) (CC and) (IN after) (S (VP (NN training) (PP (IN with) (NP (DT the) (NNPS Tropics))))))) (. .)))",
         "(ROOT (S (NP (NNP Jill)) (VP (VBD walked) (PP (PP (IN out) (NP (DT the) (NN door))) (, ,) (PP (IN over) (NP (DT the) (NN road))) (, ,) (PP (IN across) (NP (DT the) (JJ deserted) (NN block))) (, ,) (PP (IN around) (NP (DT the) (NN corner))) (, ,) (CC and) (PP (IN through) (NP (DT the) (NN park))))) (. .)))",
         "(ROOT (S (NP (NNP John)) (VP (VP (VBD noticed) (NP (DT a) (NN cockroach))) (CC and) (VP (VBD departed))) (. .)))",
         "( (S (S (NP (RBR More) (JJ common) (NN chrysotile) (NNS fibers)) (VP (VP (VBP are) (ADJP (JJ curly))) (CC and) (VP (VBP are) (VP (ADVP (RBR more) (RB easily)) (VBN rejected) (PP (IN by) (NP (DT the) (NN body))))))) (, ,) (NP (NNP Dr.) (NNP Mossman)) (VP (VBD explained)) (. .)))",
         "( (S (NP (NNP John)) (VP (VP (VBD is) (VP (VBN appalled))) (CC and) (VP (MD will) (VP (VB complain)))) (. .)))",
         "( (SBARQ (WHNP (WP What)) (SQ (VP (VBP are) (NP (NP (NP (NP (NNP Christopher) (NNP Marlowe) (POS 's)) (CC and) (NP (NNP Shakespeare) (POS 's))) (JJ literary) (NNS contributions)) (PP (TO to) (NP (JJ English) (NN literature)))))) (. ?)))",
         "( (SBARQ (WHNP (WP What)) (SQ (VP (VBP are) (NP (NP (NP (NP (NP (NNP Christopher) (NNP Marlowe)) (CC and) (NP (NNP Shakespeare))) (POS 's)) (JJ literary) (NNS contributions)) (PP (TO to) (NP (JJ English) (NN literature)))))) (. ?)))",
         "( (S (NP (PRP I)) (VP (VBP like) (NP (NP (NNS dogs)) (CONJP (RB as) (RB well) (IN as)) (NP (NNS cats)))) (. .)))",
         "( (S (NP (PRP I)) (VP (VBP like) (NP (NP (NNS dogs)) (CONJP (RB rather) (IN than)) (NP (NNS cats)))) (. .)))",
         "( (S (NP (PRP I)) (VP (VBP like) (NP (NP (NN brandy)) (CONJP (RB not) (TO to) (VB mention)) (NP (NN cognac)))) (. .)))",
         "( (S (NP (PRP I)) (VP (VBP like) (NP (CONJP (RB not) (RB only)) (NP (NNS cats)) (CONJP (CC but) (RB also)) (NP (NN dogs)))) (. .)))",
         "((S (NP (NNP Fred)) (VP (VBD flew) (PP (CONJP (RB not) (JJ only)) (PP (TO to) (NP (NNP Greece))) (CONJP (CC but) (RB also)) (PP (TO to) (NP (NNP Serbia))))) (. .)))",
         "( (SINV (ADVP-TMP (RB Only) (RB recently)) (SINV (VBZ has) (NP (PRP it)) (VP (VBN been) (VP (ADVP-MNR (RB attractively)) (VBN redesigned)))) (CC and) (SINV (NP (PRP$ its) (JJ editorial) (NN product)) (VP (VBN improved))) (. .)))",
         "( (S (NP-SBJ (JJP (JJ Political) (CC and) (NN currency)) (NNS gyrations)) (VP (MD can) (VP (VB whipsaw) (NP (DT the) (NNS funds)))) (. .)))",
         "(NP-SBJ (NNS Managers) (CC and) (NNS presenters))",
         "(NP (NN education) (, ,) (NN science) (CC and) (NN culture))",
         "(NP (NN education) (, ,) (NN science) (, ,) (CC and) (NN culture))",
         "(NP (NNP Digital) (, ,) (NNP Hewlett) (CC and) (NNP Sun) ) ",
         "(NP (NNP Digital) (, ,) (NNP Hewlett) (, ,) (CC and) (NNP Sun))",
         "(NP (NP (NNP Home) (NNP Depot) ) (, ,) (NP (NNP Sun) ) (, ,) (CC and) (NP (NNP Coke) ) )",
         "(NP (NP (NNP Home) (NNP Depot) ) (, ,) (NP (NNP Sun) ) (CC and)  (NP (NNP Coke) ) )",
         "(S (NP (NP (NN Activation)) (PP (IN of) (NP (NP (NN Akt)) (, ,) (NP (NN NFkappaB)) (, ,) (CC and) (NP (NN Stat3)) (CONJP (CC but) (RB not)) (NP (NN MAPK) (NNS pathways))))) (VP (VBP are) (NP (NP (NNS characteristics)) (VP (VBN associated) (PP (IN with) (NP (NP (JJ malignant) (NN transformation)) ))))))", // test but not -> negcc
    };


    // the expected dependency answers (CCprocessed)
    String[] testAnswers = {
        "nsubj(big-3, Bill-1)\n" + "nsubj(honest-5, Bill-1)\n" + "cop(big-3, is-2)\n" + "root(ROOT-0, big-3)\n" + "conj_and(big-3, honest-5)\n",
        "preconj(boys-3, Both-1)\n" + "det(boys-3, the-2)\n" + "nsubj(are-7, boys-3)\n" + "det(girls-6, the-5)\n" + "conj_and(boys-3, girls-6)\n" + "nsubj(are-7, girls-6)\n" + "root(ROOT-0, are-7)\n" + "advmod(are-7, here-8)\n",
        "preconj(they-2, Neither-1)\n" + "nsubjpass(reached-8, they-2)\n" + "nn(McAlpine-5, Mr.-4)\n" + "conj_nor(they-2, McAlpine-5)\n" + "nsubjpass(reached-8, McAlpine-5)\n" + "aux(reached-8, could-6)\n" + "auxpass(reached-8, be-7)\n" + "root(ROOT-0, reached-8)\n" + "prep_for(reached-8, comment-10)\n",
        "nsubj(works-2, John-1)\n" + "root(ROOT-0, works-2)\n" + "preconj(works-2, both-3)\n" + "prep_in(works-2, Zurich-5)\n" + "prep_in(works-2, London-8)\n" + "conj_and(Zurich-5, London-8)\n",
        "conj_and(Languages-1, Languages-1')\n" + "nsubj(difficult-8, Languages-1)\n" + "nsubj(difficult-8, Languages-1')\n" + "prep_with(Languages-1, alphabets-3)\n" + "prep_without(Languages-1', alphabets-6)\n" + "cop(difficult-8, are-7)\n" + "root(ROOT-0, difficult-8)\n",
        "poss(term-2, His-1)\n" +
                "nsubj(produced-4, term-2)\n" +
                "nsubj(chalked-22, term-2)\n" +
                "aux(produced-4, has-3)\n" + "root(ROOT-0, produced-4)\n" +
                "neg(failures-7, no-5)\n" +
                "amod(failures-7, spectacular-6)\n" +
                "dobj(produced-4, failures-7)\n" +
                "dobj(produced-4, failures-7')\n" +
                "dobj(produced-4, failures-7'')\n" +
                "conj_or(failures-7, failures-7')\n" +
                "conj_or(failures-7, failures-7'')\n" +
                "prep_in(failures-7, politics-9)\n" +
                "det(economy-13, the-12)\n" +
                "prep_in(failures-7', economy-13)\n" +
                "det(front-18, the-16)\n" +
                "amod(front-18, military-17)\n" +
                "prep_on(failures-7'', front-18)\n" +
                "aux(chalked-22, has-21)\n" +
                "conj_and(produced-4, chalked-22)\n" +
                "prt(chalked-22, up-23)\n" +
                "det(successes-25, some-24)\n" +
                "dobj(chalked-22, successes-25)\n",
        "nsubj(walked-2, Fred-1)\n" +
                "nsubj(walked-2', Fred-1)\n" + "root(ROOT-0, walked-2)\n" +
                "conj_and(walked-2, walked-2')\n" +
                "det(door-5, the-4)\n" +
                "prep_out(walked-2, door-5)\n" +
                "advmod(walked-2, right-7)\n" +
                "det(trap-10, a-9)\n" +
                "prep_into(walked-2', trap-10)\n",
        "nsubj(walked-2, Fred-1)\n" + "root(ROOT-0, walked-2)\n" +
                "det(house-5, the-4)\n" +
                "prep_into(walked-2, house-5)\n" +
                "advmod(walked-2, right-7)\n" +
                "det(trap-10, a-9)\n" +
                "prep_into(walked-2, trap-10)\n" +
                "conj_and(house-5, trap-10)\n",
        "nsubj(went-4, Marie-1)\n" +
                "nsubj(drank-7, Marie-1)\n" +
                "nsubj(talked-11, Marie-1)\n" +
                "conj_and(Marie-1, Chris-3)\n" +
                "nsubj(went-4, Chris-3)\n" + "root(ROOT-0, went-4)\n" +
                "prt(went-4, out-5)\n" +
                "conj_and(went-4, drank-7)\n" +
                "dobj(drank-7, coffee-8)\n" +
                "conj_and(went-4, talked-11)\n" +
                "nn(Dependencies-14, Stanford-13)\n" +
                "prep_about(talked-11, Dependencies-14)\n",
        "det(days-2, These-1)\n" +
                "tmod(hustles-4, days-2)\n" +
                "nsubj(hustles-4, he-3)\n" +
                "nsubj(hustles-4', he-3)\n" + "root(ROOT-0, hustles-4)\n" +
                "conj_and(hustles-4, hustles-4')\n" +
                "amod(jobs-7, house-painting-6)\n" +
                "prep_to(hustles-4, jobs-7)\n" +
                "poss(pickup-11, his-9)\n" +
                "nn(pickup-11, Chevy-10)\n" +
                "prep_in(hustles-4, pickup-11)\n" +
                "prepc_after(hustles-4', training-15)\n" +
                "prepc_before(hustles-4, training-15)\n" +
                "det(Tropics-18, the-17)\n" +
                "prep_with(training-15, Tropics-18)\n",
        "nsubj(walked-2, Jill-1)\n" + "nsubj(walked-2', Jill-1)\n" + "nsubj(walked-2'', Jill-1)\n" + "nsubj(walked-2''', Jill-1)\n" + "nsubj(walked-2'''', Jill-1)\n" + "root(ROOT-0, walked-2)\n" + "conj_and(walked-2, walked-2')\n" + "conj_and(walked-2, walked-2'')\n" + "conj_and(walked-2, walked-2''')\n" + "conj_and(walked-2, walked-2'''')\n" + "det(door-5, the-4)\n" + "prep_out(walked-2, door-5)\n" + "det(road-9, the-8)\n" + "prep_over(walked-2', road-9)\n" + "det(block-14, the-12)\n" + "amod(block-14, deserted-13)\n" + "prep_across(walked-2'', block-14)\n" + "det(corner-18, the-17)\n" + "prep_around(walked-2''', corner-18)\n" + "det(park-23, the-22)\n" + "prep_through(walked-2'''', park-23)\n",
        "nsubj(noticed-2, John-1)\n" +
                "nsubj(departed-6, John-1)\n" + "root(ROOT-0, noticed-2)\n" +
                "det(cockroach-4, a-3)\n" +
                "dobj(noticed-2, cockroach-4)\n" +
                "conj_and(noticed-2, departed-6)\n",
        "advmod(fibers-4, More-1)\n" +
                "amod(fibers-4, common-2)\n" +
                "nn(fibers-4, chrysotile-3)\n" +
                "nsubj(curly-6, fibers-4)\n" +
                "nsubjpass(rejected-11, fibers-4)\n" +
                "cop(curly-6, are-5)\n" +
                "ccomp(explained-18, curly-6)\n" +
                "auxpass(rejected-11, are-8)\n" +
                "advmod(easily-10, more-9)\n" +
                "advmod(rejected-11, easily-10)\n" +
                "conj_and(curly-6, rejected-11)\n" +
                "ccomp(explained-18, rejected-11)\n" +
                "det(body-14, the-13)\n" +
                "agent(rejected-11, body-14)\n" +
                "nn(Mossman-17, Dr.-16)\n" +
                "nsubj(explained-18, Mossman-17)\n" + "root(ROOT-0, explained-18)\n",
        "nsubjpass(appalled-3, John-1)\n" +
                "nsubj(complain-6, John-1)\n" +
                "auxpass(appalled-3, is-2)\n" + "root(ROOT-0, appalled-3)\n"  +
                "aux(complain-6, will-5)\n" +
                "conj_and(appalled-3, complain-6)\n",
        "nsubj(contributions-10, What-1)\n" +
                "cop(contributions-10, are-2)\n" +
                "nn(Marlowe-4, Christopher-3)\n" +
                "poss(contributions-10, Marlowe-4)\n" +
                "conj_and(Marlowe-4, Shakespeare-7)\n" +
                "poss(contributions-10, Shakespeare-7)\n" +
                "amod(contributions-10, literary-9)\n" + "root(ROOT-0, contributions-10)\n" +
                "amod(literature-13, English-12)\n" +
                "prep_to(contributions-10, literature-13)\n",
        "nsubj(contributions-9, What-1)\n" +
                "cop(contributions-9, are-2)\n" +
                "nn(Marlowe-4, Christopher-3)\n" +
                "poss(contributions-9, Marlowe-4)\n" +
                "conj_and(Marlowe-4, Shakespeare-6)\n" +
                "poss(contributions-9, Shakespeare-6)\n" +
                "amod(contributions-9, literary-8)\n" + "root(ROOT-0, contributions-9)\n" +
                "amod(literature-12, English-11)\n" +
                "prep_to(contributions-9, literature-12)\n",
        "nsubj(like-2, I-1)\n" + "root(ROOT-0, like-2)\n" +
                "dobj(like-2, dogs-3)\n" +
                "dobj(like-2, cats-7)\n" +
                "conj_and(dogs-3, cats-7)\n",
        "nsubj(like-2, I-1)\n" + "root(ROOT-0, like-2)\n" +
                "dobj(like-2, dogs-3)\n" +
                "dobj(like-2, cats-6)\n" +
                "conj_negcc(dogs-3, cats-6)\n",
            "nsubj(like-2, I-1)\n" + "root(ROOT-0, like-2)\n" +
                    "dobj(like-2, brandy-3)\n" +
                    "dobj(like-2, cognac-7)\n" +
                    "conj_and(brandy-3, cognac-7)\n",
        "nsubj(like-2, I-1)\n" + "root(ROOT-0, like-2)\n" +
                "neg(only-4, not-3)\n" +
                "preconj(cats-5, only-4)\n" +
                "dobj(like-2, cats-5)\n" +
                "dobj(like-2, dogs-8)\n" +
                "conj_and(cats-5, dogs-8)\n",
        "nsubj(flew-2, Fred-1)\n" +
                "root(ROOT-0, flew-2)\n" +
                "neg(only-4, not-3)\n" +
                "preconj(flew-2, only-4)\n" +
                "prep_to(flew-2, Greece-6)\n" +
                "prep_to(flew-2, Serbia-10)\n" +
                "conj_and(Greece-6, Serbia-10)\n",

        "advmod(recently-2, Only-1)\n" +
                "advmod(redesigned-7, recently-2)\n" +
                "aux(redesigned-7, has-3)\n" +
                "nsubjpass(redesigned-7, it-4)\n" +
                "auxpass(redesigned-7, been-5)\n" +
                "advmod(redesigned-7, attractively-6)\n" +
                "root(ROOT-0, redesigned-7)\n" +
                "poss(product-11, its-9)\n" +
                "amod(product-11, editorial-10)\n" +
                "nsubj(improved-12, product-11)\n" +
                "conj_and(redesigned-7, improved-12)\n",
        "amod(gyrations-4, Political-1)\n" +
                "conj_and(Political-1, currency-3)\n" +
                "amod(gyrations-4, currency-3)\n" +
                "nsubj(whipsaw-6, gyrations-4)\n" +
                "aux(whipsaw-6, can-5)\n" +
                "root(ROOT-0, whipsaw-6)\n" +
                "det(funds-8, the-7)\n" +
                "dobj(whipsaw-6, funds-8)\n",
        "root(ROOT-0, Managers-1)\n" +
                "conj_and(Managers-1, presenters-3)\n",
        "root(ROOT-0, education-1)\n" +
                "conj_and(education-1, science-3)\n" +
                "conj_and(education-1, culture-5)\n",
        "root(ROOT-0, education-1)\n" +
                "conj_and(education-1, science-3)\n" +
                "conj_and(education-1, culture-6)\n",
        "root(ROOT-0, Digital-1)\n" +
                "conj_and(Digital-1, Hewlett-3)\n" +
                "conj_and(Digital-1, Sun-5)\n",
        "root(ROOT-0, Digital-1)\n" +
                "conj_and(Digital-1, Hewlett-3)\n" +
                "conj_and(Digital-1, Sun-6)\n",
        "nn(Depot-2, Home-1)\n" +
                "root(ROOT-0, Depot-2)\n" +
                "conj_and(Depot-2, Sun-4)\n" +
                "conj_and(Depot-2, Coke-7)\n",
        "nn(Depot-2, Home-1)\n" +
                "root(ROOT-0, Depot-2)\n" +
                "conj_and(Depot-2, Sun-4)\n" +
                "conj_and(Depot-2, Coke-6)\n",
        "nsubj(characteristics-14, Activation-1)\n" +
                "prep_of(Activation-1, Akt-3)\n" +
                "prep_of(Activation-1, NFkappaB-5)\n" +
                "conj_and(Akt-3, NFkappaB-5)\n" +
                "prep_of(Activation-1, Stat3-8)\n" +
                "conj_and(Akt-3, Stat3-8)\n" +
                "nn(pathways-12, MAPK-11)\n" +
                "prep_of(Activation-1, pathways-12)\n" +
                "conj_negcc(Akt-3, pathways-12)\n" +
                "cop(characteristics-14, are-13)\n" +
                "root(ROOT-0, characteristics-14)\n" +
                "vmod(characteristics-14, associated-15)\n" +
                "amod(transformation-18, malignant-17)\n" +
                "prep_with(associated-15, transformation-18)\n",
    };

    assertEquals("Test array lengths mismatch!", testTrees.length, testAnswers.length);
    TreeReaderFactory trf = new PennTreeReaderFactory();
    for (int i = 0; i < testTrees.length; i++) {
      String testTree = testTrees[i];
      String testAnswer = testAnswers[i];

      // specifying our own TreeReaderFactory is vital so that functional
      // categories - that is -TMP and -ADV in particular - are not stripped off
      Tree tree = Tree.valueOf(testTree, trf);
      GrammaticalStructure gs = new EnglishGrammaticalStructure(tree);

      assertEquals("Unexpected CC processed dependencies for tree "+testTree,
          testAnswer, GrammaticalStructureConversionUtils.dependenciesToString(gs, gs.typedDependenciesCCprocessed(GrammaticalStructure.Extras.MAXIMAL), tree, false, false, false));
    }
  }


  /**
   * Tests that the copy nodes are properly handled.
   */
  @Test
  public void testCopyNodes() {
    // the trees to test
    String[] testTrees = {
         "(ROOT (S (NP (NNP Bill)) (VP (VBD went) (PP (PP (IN over) (NP (DT the) (NN river))) (CC and) (PP (IN through) (NP (DT the) (NNS woods))))) (. .)))",
    };


    // the expected dependency answers (collapsed)
    String[] testAnswers = {
        "nsubj(went-2, Bill-1)\n" + "root(ROOT-0, went-2)\n" + "conj_and(went-2, went-2')\n" + "det(river-5, the-4)\n" + "prep_over(went-2, river-5)\n" + "det(woods-9, the-8)\n" + "prep_through(went-2', woods-9)\n",
    };

    assertEquals("Test array lengths mismatch!", testTrees.length, testAnswers.length);
    TreeReaderFactory trf = new PennTreeReaderFactory();
    for (int i = 0; i < testTrees.length; i++) {
      String testTree = testTrees[i];

      String testAnswer = testAnswers[i];

      // Specifying our own TreeReaderFactory is vital so that functional
      // categories - that is -TMP and -ADV in particular - are not stripped off
      Tree tree = Tree.valueOf(testTree, trf);
      GrammaticalStructure gs = new EnglishGrammaticalStructure(tree);

      assertEquals("Unexpected basic dependencies for tree "+testTree,
          testAnswer, GrammaticalStructureConversionUtils.dependenciesToString(gs, gs.typedDependenciesCollapsed(GrammaticalStructure.Extras.MAXIMAL), tree, false, false, false));
    }
  }

}
