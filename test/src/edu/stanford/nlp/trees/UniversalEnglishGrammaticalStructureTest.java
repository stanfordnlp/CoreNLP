// EnglishGrammaticalStructureTest -- unit tests for Stanford dependencies.
// Copyright © 2005, 2011, 2013, 2019 The Board of Trustees of
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
// along with this program.  If not, see http://www.gnu.org/licenses/ .
//
// For more information, bug reports, fixes, contact:
//    Christopher Manning
//    Dept of Computer Science, Gates 2A
//    Stanford CA 94305-9020
//    USA
//    Support/Questions: parser-user@lists.stanford.edu
//    Licensing: parser-support@lists.stanford.edu

package edu.stanford.nlp.trees;

import java.util.Arrays;
import java.util.Collection;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import edu.stanford.nlp.trees.GrammaticalStructure.Extras;


/** Test cases for Universal Dependencies English typed dependencies (Stanford dependencies).
 *
 *  @author Marie-Catherine de Marneffe (mcdm)
 *  @author Christopher Manning
 *  @author John Bauer
 *  @author Sebastian Schuster
 */

@RunWith(Parameterized.class)
public class UniversalEnglishGrammaticalStructureTest extends Assert {


  private String testTree;
  private String testAnswer;
  private TestType type;


  private enum TestType {
    BASIC, //Basic conversion, no extra dependencies or collapsing
    COPULA_HEAD, //Basic conversion with copula being the head
    NON_COLLAPSED, //Basic conversion + extra dependencies
    NON_COLLAPSED_SEPARATOR, //Basic conversion + extra dependencies appended at the end
    COLLAPSED, //Collapsed relations
    CC_PROCESSED, //CCprocessed relations
  }


  /**
   *
   * Contains a list of type-constituency tree-dependency relations triplets.
   * Depending on the test t
   */
  @Parameters
  public static Collection<Object[]> testCases() {
    return Arrays.asList(new Object[][] {
        /* basic relations */
        {TestType.BASIC,
          "(ROOT (S (NP (NNP Reagan)) (VP (VBZ has) (VP (VBN died))) (. .)))",
          "nsubj(died-3, Reagan-1)\n" +
           "aux(died-3, has-2)\n" +
           "root(ROOT-0, died-3)\n"},
         {TestType.BASIC,
          "(ROOT (S (NP (NNP Kennedy)) (VP (VBZ has) (VP (VBN been) (VP (VBN killed)))) (. .)))",
          "nsubj:pass(killed-4, Kennedy-1)\n" +
           "aux(killed-4, has-2)\n" +
           "aux:pass(killed-4, been-3)\n" +
           "root(ROOT-0, killed-4)\n"},
         {TestType.BASIC,
          "(ROOT (S (NP (NNP Bill)) (VP (VBZ is) (NP (DT an) (JJ honest) (NN man))) (. .)))",
          "nsubj(man-5, Bill-1)\n" +
           "cop(man-5, is-2)\n" +
           "det(man-5, an-3)\n" +
           "amod(man-5, honest-4)\n" +
           "root(ROOT-0, man-5)\n"},
         {TestType.BASIC,
          "(ROOT (S (NP (NNP Bill)) (VP (VBZ is) (ADJP (JJ big) (CC and) (JJ honest))) (. .)))",
          "nsubj(big-3, Bill-1)\n" +
           "cop(big-3, is-2)\n" +
           "root(ROOT-0, big-3)\n" +
           "cc(honest-5, and-4)\n" +
           "conj(big-3, honest-5)\n"},
         {TestType.BASIC,
          "(ROOT (S (NP (NNP Clinton)) (VP (VBD defeated) (NP (NNP Dole))) (. .)))",
          "nsubj(defeated-2, Clinton-1)\n" +
           "root(ROOT-0, defeated-2)\n" +
           "obj(defeated-2, Dole-3)\n"},
         {TestType.BASIC,
          "(ROOT (S (SBAR (WHNP (WP What)) (S (NP (PRP she)) (VP (VBD said)))) (VP (VBZ is) (ADJP (JJ untrue))) (. .)))",
          "obj(said-3, What-1)\n" +
           "nsubj(said-3, she-2)\n" +
           "csubj(untrue-5, said-3)\n" +
           "cop(untrue-5, is-4)\n" +
           "root(ROOT-0, untrue-5)\n"},
         {TestType.BASIC,
          "(ROOT (S (NP (NNP Dole)) (VP (VBD was) (VP (VBN defeated) (PP (IN by) (NP (NNP Clinton))))) (. .)))",
          "nsubj:pass(defeated-3, Dole-1)\n" +
           "aux:pass(defeated-3, was-2)\n" +
           "root(ROOT-0, defeated-3)\n" +
           "case(Clinton-5, by-4)\n" +
           "obl(defeated-3, Clinton-5)\n"},
         {TestType.BASIC,
          "(ROOT (S (SBAR (IN That) (S (NP (PRP she)) (VP (VBD lied)))) (VP (VBD was) (VP (VBN suspected) (PP (IN by) (NP (NN everyone))))) (. .)))",
          "mark(lied-3, That-1)\n" +
           "nsubj(lied-3, she-2)\n" +
           "csubj:pass(suspected-5, lied-3)\n" +
           "aux:pass(suspected-5, was-4)\n" +
           "root(ROOT-0, suspected-5)\n" +
           "case(everyone-7, by-6)\n" +
           "obl(suspected-5, everyone-7)\n"},
         {TestType.BASIC,
          "(ROOT (S (NP (PRP She)) (VP (VBD gave) (NP (PRP me)) (NP (DT a) (NN raise))) (. .)))",
          "nsubj(gave-2, She-1)\n" +
           "root(ROOT-0, gave-2)\n" +
           "iobj(gave-2, me-3)\n" +
           "det(raise-5, a-4)\n" +
           "obj(gave-2, raise-5)\n"},
         {TestType.BASIC,
          "(ROOT (S (NP (PRP I)) (VP (VBP like) (S (VP (TO to) (VP (VB swim))))) (. .)))",
          "nsubj(like-2, I-1)\n" +
           "root(ROOT-0, like-2)\n" +
           "mark(swim-4, to-3)\n" +
           "xcomp(like-2, swim-4)\n"},
         {TestType.BASIC,
          "(ROOT (S (NP (PRP I)) (VP (VBD sat) (PP (IN on) (NP (DT the) (NN chair)))) (. .)))",
          "nsubj(sat-2, I-1)\n" +
           "root(ROOT-0, sat-2)\n" +
           "case(chair-5, on-3)\n" +
           "det(chair-5, the-4)\n" +
           "obl(sat-2, chair-5)\n"},
         {TestType.BASIC,
          "(ROOT (S (NP (PRP We)) (VP (VBP have) (NP (NP (DT no) (JJ useful) (NN information)) (PP (IN on) (SBAR (IN whether) (S (NP (NNS users)) (VP (VBP are) (PP (IN at) (NP (NN risk))))))))) (. .)))",
          "nsubj(have-2, We-1)\n" +
           "root(ROOT-0, have-2)\n" +
           "det(information-5, no-3)\n" +
           "amod(information-5, useful-4)\n" +
           "obj(have-2, information-5)\n" +
           "mark(risk-11, on-6)\n" +
           "mark(risk-11, whether-7)\n" +
           "nsubj(risk-11, users-8)\n" +
           "cop(risk-11, are-9)\n" +
           "case(risk-11, at-10)\n" +
           "acl(information-5, risk-11)\n"},
         {TestType.BASIC,
          "(ROOT (S (NP (PRP They)) (VP (VBD heard) (PP (IN about) (NP (NN asbestos))) (S (VP (VBG having) (NP (JJ questionable) (NNS properties))))) (. .)))",
          "nsubj(heard-2, They-1)\n" +
           "root(ROOT-0, heard-2)\n" +
           "case(asbestos-4, about-3)\n" +
           "obl(heard-2, asbestos-4)\n" +
           "xcomp(heard-2, having-5)\n" +
           "amod(properties-7, questionable-6)\n" +
           "obj(having-5, properties-7)\n"},
         {TestType.BASIC,
          "(ROOT (S (NP (PRP He)) (VP (VBZ says) (SBAR (IN that) (S (NP (PRP you)) (VP (VBP like) (S (VP (TO to) (VP (VB swim)))))))) (. .)))",
          "nsubj(says-2, He-1)\n" +
           "root(ROOT-0, says-2)\n" +
           "mark(like-5, that-3)\n" +
           "nsubj(like-5, you-4)\n" +
           "ccomp(says-2, like-5)\n" +
           "mark(swim-7, to-6)\n" +
           "xcomp(like-5, swim-7)\n"},
         {TestType.BASIC,
          "(ROOT (S (NP (PRP I)) (VP (VBP am) (ADJP (JJ certain) (SBAR (IN that) (S (NP (PRP he)) (VP (VBD did) (NP (PRP it))))))) (. .)))",
          "nsubj(certain-3, I-1)\n" +
           "cop(certain-3, am-2)\n" +
           "root(ROOT-0, certain-3)\n" +
           "mark(did-6, that-4)\n" +
           "nsubj(did-6, he-5)\n" +
           "ccomp(certain-3, did-6)\n" +
           "obj(did-6, it-7)\n"},
         {TestType.BASIC,
          "(ROOT (S (NP (PRP I)) (VP (VBP am) (ADJP (JJ ready) (S (VP (TO to) (VP (VB leave)))))) (. .)))",
          "nsubj(ready-3, I-1)\n" +
           "cop(ready-3, am-2)\n" +
           "root(ROOT-0, ready-3)\n" +
           "mark(leave-5, to-4)\n" +
           "xcomp(ready-3, leave-5)\n"},
         {TestType.BASIC,
          "(ROOT (S (NP (NNP U.S.) (NNS forces)) (VP (VBP have) (VP (VBN been) (VP (VBN engaged) (PP (IN in) (NP (JJ intense) (NN fighting))) (SBAR (IN after) (S (NP (NNS insurgents)) (VP (VBD launched) (NP (JJ simultaneous) (NNS attacks)))))))) (. .)))",
          "compound(forces-2, U.S.-1)\n" +
           "nsubj:pass(engaged-5, forces-2)\n" +
           "aux(engaged-5, have-3)\n" +
           "aux:pass(engaged-5, been-4)\n" +
           "root(ROOT-0, engaged-5)\n" +
           "case(fighting-8, in-6)\n" +
           "amod(fighting-8, intense-7)\n" +
           "obl(engaged-5, fighting-8)\n" +
           "mark(launched-11, after-9)\n" +
           "nsubj(launched-11, insurgents-10)\n" +
           "advcl(engaged-5, launched-11)\n" +
           "amod(attacks-13, simultaneous-12)\n" +
           "obj(launched-11, attacks-13)\n"},
         {TestType.BASIC,
          "(ROOT (S (NP (PRP I)) (VP (VBD saw) (NP (NP (DT the) (NN man)) (SBAR (WHNP (WP who)) (S (NP (PRP you)) (VP (VBP love)))))) (. .)))",
          "nsubj(saw-2, I-1)\n" +
           "root(ROOT-0, saw-2)\n" +
           "det(man-4, the-3)\n" +
           "obj(saw-2, man-4)\n" +
           "obj(love-7, who-5)\n" +
           "nsubj(love-7, you-6)\n" +
           "acl:relcl(man-4, love-7)\n"},
         {TestType.BASIC,
          "(ROOT (S (NP (PRP I)) (VP (VBD saw) (NP (NP (DT the) (NN man)) (SBAR (WHNP (WP$ whose) (NP (NN wife))) (S (NP (PRP you)) (VP (VBP love)))))) (. .)))",
          "nsubj(saw-2, I-1)\n" +
           "root(ROOT-0, saw-2)\n" +
           "det(man-4, the-3)\n" +
           "obj(saw-2, man-4)\n" +
           "nmod:poss(wife-6, whose-5)\n" +
           "obj(love-8, wife-6)\n" +
           "nsubj(love-8, you-7)\n" +
           "acl:relcl(man-4, love-8)\n"},
         {TestType.BASIC,
          "(ROOT (S (NP (PRP I)) (VP (VBD saw) (NP (NP (DT the) (NN man)) (SBAR (WHNP (WP$ whose) (NN wife)) (S (NP (PRP you)) (VP (VBP love)))))) (. .)))",
          "nsubj(saw-2, I-1)\n" +
           "root(ROOT-0, saw-2)\n" +
           "det(man-4, the-3)\n" +
           "obj(saw-2, man-4)\n" +
           "nmod:poss(wife-6, whose-5)\n" +
           "obj(love-8, wife-6)\n" +
           "nsubj(love-8, you-7)\n" +
           "acl:relcl(man-4, love-8)\n"},
         {TestType.BASIC,
          "(ROOT (S (NP (EX There)) (VP (VBZ is) (NP (DT a) (NN statue)) (PP (IN in) (NP (DT the) (NN corner)))) (. .)))",
          "expl(is-2, There-1)\n" +
           "root(ROOT-0, is-2)\n" +
           "det(statue-4, a-3)\n" +
           "nsubj(is-2, statue-4)\n" +
           "case(corner-7, in-5)\n" +
           "det(corner-7, the-6)\n" +
           "obl(is-2, corner-7)\n"},
         {TestType.BASIC,
          "(ROOT (S (NP (PRP She)) (VP (VBZ looks) (ADJP (RB very) (JJ beautiful))) (. .)))",
          "nsubj(looks-2, She-1)\n" +
           "root(ROOT-0, looks-2)\n" +
           "advmod(beautiful-4, very-3)\n" +
           "xcomp(looks-2, beautiful-4)\n"},
         {TestType.BASIC,
          "(ROOT (S (NP (DT The) (NN accident)) (VP (VBD happened) (SBAR (IN as) (S (NP (DT the) (NN night)) (VP (VBD was) (VP (VBG falling)))))) (. .)))",
          "det(accident-2, The-1)\n" +
           "nsubj(happened-3, accident-2)\n" +
           "root(ROOT-0, happened-3)\n" +
           "mark(falling-8, as-4)\n" +
           "det(night-6, the-5)\n" +
           "nsubj(falling-8, night-6)\n" +
           "aux(falling-8, was-7)\n" +
           "advcl(happened-3, falling-8)\n"},
         {TestType.BASIC,
          "(ROOT (S (SBAR (IN If) (S (NP (PRP you)) (VP (VBP know) (SBAR (WHNP (WP who)) (S (VP (VBD did) (NP (PRP it)))))))) (, ,) (NP (PRP you)) (VP (MD should) (VP (VB tell) (NP (DT the) (NN teacher)))) (. .)))",
          "mark(know-3, If-1)\n" +
           "nsubj(know-3, you-2)\n" +
           "advcl(tell-10, know-3)\n" +
           "nsubj(did-5, who-4)\n" +
           "ccomp(know-3, did-5)\n" +
           "obj(did-5, it-6)\n" +
           "nsubj(tell-10, you-8)\n" +
           "aux(tell-10, should-9)\n" +
           "root(ROOT-0, tell-10)\n" +
           "det(teacher-12, the-11)\n" +
           "obj(tell-10, teacher-12)\n"},
         {TestType.BASIC,
          "(ROOT (S (NP-TMP (JJ Last) (NN night)) (, ,) (NP (PRP I)) (VP (VBP swam) (PP (IN in) (NP (DT the) (NN pool)))) (. .)))",
          "amod(night-2, Last-1)\n" +
           "obl:tmod(swam-5, night-2)\n" +
           "nsubj(swam-5, I-4)\n" +
           "root(ROOT-0, swam-5)\n" +
           "case(pool-8, in-6)\n" +
           "det(pool-8, the-7)\n" +
           "obl(swam-5, pool-8)\n"},
         {TestType.BASIC,
          "(ROOT (S (NP (PRP He)) (VP (VBD talked) (PP (TO to) (NP (DT the) (NN president))) (SBAR (IN in) (NN order) (S (VP (TO to) (VP (VB secure) (NP (DT the) (NN account))))))) (. .)))",
          "nsubj(talked-2, He-1)\n" +
           "root(ROOT-0, talked-2)\n" +
           "case(president-5, to-3)\n" +
           "det(president-5, the-4)\n" +
           "obl(talked-2, president-5)\n" +
           "mark(secure-9, in-6)\n" +
           "fixed(in-6, order-7)\n" +
           "mark(secure-9, to-8)\n" +
           "advcl(talked-2, secure-9)\n" +
           "det(account-11, the-10)\n" +
           "obj(secure-9, account-11)\n"},
         {TestType.BASIC,
          "(ROOT (S (NP (PRP I)) (VP (VBD saw) (NP (NP (DT the) (NN book)) (SBAR (WHNP (WDT which)) (S (NP (PRP you)) (VP (VBD bought)))))) (. .)))",
          "nsubj(saw-2, I-1)\n" +
           "root(ROOT-0, saw-2)\n" +
           "det(book-4, the-3)\n" +
           "obj(saw-2, book-4)\n" +
           "obj(bought-7, which-5)\n" +
           "nsubj(bought-7, you-6)\n" +
           "acl:relcl(book-4, bought-7)\n"},
         {TestType.BASIC,
          "(ROOT (S (NP (NNP Sam)) (VP (VBZ eats) (NP (CD 3) (NN sheep))) (. .)))",
          "nsubj(eats-2, Sam-1)\n" +
           "root(ROOT-0, eats-2)\n" +
           "nummod(sheep-4, 3-3)\n" +
           "obj(eats-2, sheep-4)\n"},
         {TestType.BASIC,
          "(ROOT (S (NP (PRP I)) (VP (VBD lost) (NP (QP ($ $) (CD 3.2) (CD billion)))) (. .)))",
          "nsubj(lost-2, I-1)\n" +
           "root(ROOT-0, lost-2)\n" +
           "obj(lost-2, $-3)\n" +
           "compound(billion-5, 3.2-4)\n" +
           "nummod($-3, billion-5)\n"},
         {TestType.BASIC,
          "(ROOT (S (NP (QP (RB About) (CD 200)) (NNS people)) (VP (VBD came) (PP (TO to) (NP (DT the) (NN party)))) (. .)))",
          "advmod(200-2, About-1)\n" +
           "nummod(people-3, 200-2)\n" +
           "nsubj(came-4, people-3)\n" +
           "root(ROOT-0, came-4)\n" +
           "case(party-7, to-5)\n" +
           "det(party-7, the-6)\n" +
           "obl(came-4, party-7)\n"},
         {TestType.BASIC,
          "(ROOT (S (NP (NP (NNP Sam)) (, ,) (NP (PRP$ my) (NN brother)) (, ,)) (VP (VBZ eats) (NP (JJ red) (NN meat))) (. .)))",
          "nsubj(eats-6, Sam-1)\n" +
           "nmod:poss(brother-4, my-3)\n" +
           "appos(Sam-1, brother-4)\n" +
           "root(ROOT-0, eats-6)\n" +
           "amod(meat-8, red-7)\n" +
           "obj(eats-6, meat-8)\n"},
         {TestType.BASIC,
          "(ROOT (NP (NP (DT The) (JJ Australian) (NNP Broadcasting) (NNP Corporation)) (PRN (-LRB- -LRB-) (NP (NNP ABC)) (-RRB- -RRB-)) (. .)))",
          "det(Corporation-4, The-1)\n" +
           "amod(Corporation-4, Australian-2)\n" +
           "compound(Corporation-4, Broadcasting-3)\n" +
           "root(ROOT-0, Corporation-4)\n" +
           "appos(Corporation-4, ABC-6)\n"},
         {TestType.BASIC,
          "(ROOT (S (NP (NNP Bill)) (VP (VBD picked) (NP (NNP Fred)) (PP (IN for) (NP (NP (DT the) (NN team)) (VP (VBG demonstrating) (NP (PRP$ his) (NN incompetence)))))) (. .)))",
          "nsubj(picked-2, Bill-1)\n" +
           "root(ROOT-0, picked-2)\n" +
           "obj(picked-2, Fred-3)\n" +
           "case(team-6, for-4)\n" +
           "det(team-6, the-5)\n" +
           "obl(picked-2, team-6)\n" +
           "acl(team-6, demonstrating-7)\n" +
           "nmod:poss(incompetence-9, his-8)\n" +
           "obj(demonstrating-7, incompetence-9)\n"},
         {TestType.BASIC,
          "(ROOT (S (NP (NNP Bill)) (VP (VBZ is) (RB not) (NP (DT a) (NN scientist))) (. .)))",
          "nsubj(scientist-5, Bill-1)\n" +
           "cop(scientist-5, is-2)\n" +
           "advmod(scientist-5, not-3)\n" +
           "det(scientist-5, a-4)\n" +
           "root(ROOT-0, scientist-5)\n"},
         {TestType.BASIC,
          "(ROOT (S (NP (NNP Bill)) (VP (VBZ does) (RB n't) (VP (VB drive))) (. .)))",
          "nsubj(drive-4, Bill-1)\n" +
           "aux(drive-4, does-2)\n" +
           "advmod(drive-4, n't-3)\n" +
           "root(ROOT-0, drive-4)\n"},
         {TestType.BASIC,
          "(ROOT (S (NP (DT The) (NN director)) (VP (VBZ is) (ADJP (NP (CD 65) (NNS years)) (JJ old))) (. .)))",
          "det(director-2, The-1)\n" +
           "nsubj(old-6, director-2)\n" +
           "cop(old-6, is-3)\n" +
           "nummod(years-5, 65-4)\n" +
           "obl:npmod(old-6, years-5)\n" +
           "root(ROOT-0, old-6)\n"},
         {TestType.BASIC,
          "(ROOT (S (NP (DT The) (NN man)) (VP (VBZ is) (ADVP (RB here))) (. .)))",
          "det(man-2, The-1)\n" +
           "nsubj(is-3, man-2)\n" +
           "root(ROOT-0, is-3)\n" +
           "advmod(is-3, here-4)\n"},
         {TestType.BASIC,
          "(ROOT (SBARQ (WHPP (IN In) (WHNP (WDT which) (NN city))) (SQ (VBP do) (NP (PRP you)) (VP (VB live))) (. ?)))",
          "case(city-3, In-1)\n" +
           "det(city-3, which-2)\n" +
           "obl(live-6, city-3)\n" +
           "aux(live-6, do-4)\n" +
           "nsubj(live-6, you-5)\n" +
           "root(ROOT-0, live-6)\n"},
         {TestType.BASIC,
          "(ROOT (SBARQ (WHNP (WP What)) (SQ (VBD did) (NP (NNP Charles) (NNP Babbage)) (VP (VB invent))) (. ?)))",
          "obj(invent-5, What-1)\n" +
           "aux(invent-5, did-2)\n" +
           "compound(Babbage-4, Charles-3)\n" +
           "nsubj(invent-5, Babbage-4)\n" +
           "root(ROOT-0, invent-5)\n"},
         {TestType.BASIC,
          "(ROOT (SBARQ (WHNP (WP What)) (SQ (VBZ is) (NP (DT the) (NN esophagus)) (VP (VBN used) (PP (IN for)))) (. ?)))",
          "obl(used-5, What-1)\n" +
           "aux:pass(used-5, is-2)\n" +
           "det(esophagus-4, the-3)\n" +
           "nsubj:pass(used-5, esophagus-4)\n" +
           "root(ROOT-0, used-5)\n" +
           "case(What-1, for-6)\n"},
         {TestType.BASIC,
          "(ROOT (S (NP (PDT All) (DT the) (NNS boys)) (VP (VBP are) (ADVP (RB here))) (. .)))",
          "det:predet(boys-3, All-1)\n" +
           "det(boys-3, the-2)\n" +
           "nsubj(are-4, boys-3)\n" +
           "root(ROOT-0, are-4)\n" +
           "advmod(are-4, here-5)\n"},
         {TestType.BASIC,
          "(ROOT (S (NP (CC Both) (NP (DT the) (NNS boys)) (CC and) (NP (DT the) (NNS girls))) (VP (VBP are) (ADVP (RB here))) (. .)))",
          "cc:preconj(boys-3, Both-1)\n" +
           "det(boys-3, the-2)\n" +
           "nsubj(are-7, boys-3)\n" +
           "cc(girls-6, and-4)\n" +
           "det(girls-6, the-5)\n" +
           "conj(boys-3, girls-6)\n" +
           "root(ROOT-0, are-7)\n" +
           "advmod(are-7, here-8)\n"},
         {TestType.BASIC,
          "(ROOT (S (NP (PRP They)) (VP (VBD shut) (PRT (RP down)) (NP (DT the) (NN station))) (. .)))",
          "nsubj(shut-2, They-1)\n" +
           "root(ROOT-0, shut-2)\n" +
           "compound:prt(shut-2, down-3)\n" +
           "det(station-5, the-4)\n" +
           "obj(shut-2, station-5)\n"},
         {TestType.BASIC,
          "(ROOT (S (NP (NP (NNS Truffles)) (VP (VBN picked) (PP (IN during) (NP (DT the) (NN spring))))) (VP (VBP are) (ADJP (JJ tasty))) (. .)))",
          "nsubj(tasty-7, Truffles-1)\n" +
           "acl(Truffles-1, picked-2)\n" +
           "case(spring-5, during-3)\n" +
           "det(spring-5, the-4)\n" +
           "obl(picked-2, spring-5)\n" +
           "cop(tasty-7, are-6)\n" +
           "root(ROOT-0, tasty-7)\n"},
          //TODO: this should be flat(Mr, McAlipine)
         {TestType.BASIC,
          "(ROOT (S  (NP-SBJ-38 (DT Neither) (NP (PRP they) ) (CC nor) (NP (NNP Mr.) (NNP McAlpine) )) (VP (MD could) (VP (VB be) (VP (VBN reached) (NP (-NONE- *-38) ) (PP-PRP (IN for) (NP (NN comment) ))))) (. .) ))",
          "cc:preconj(they-2, Neither-1)\n" +
           "nsubj:pass(reached-8, they-2)\n" +
           "cc(McAlpine-5, nor-3)\n" +
           "compound(McAlpine-5, Mr.-4)\n" +
           "conj(they-2, McAlpine-5)\n" +
           "aux(reached-8, could-6)\n" +
           "aux:pass(reached-8, be-7)\n" +
           "root(ROOT-0, reached-8)\n" +
           "case(comment-10, for-9)\n" +
           "obl(reached-8, comment-10)\n"},
         {TestType.BASIC,
          "(ROOT (S (NP (NNP Xml) (NN field)) (VP (MD should) (VP (VB include) (NP (PDT both) (NP (DT the) (NN entity) (NN id)) (CC and) (NP (DT the) (NN entity) (NN name))) (SBAR (IN since) (S (NP (DT the) (NN entity) (NNS names)) (VP (VBP are) (RB not) (ADJP (JJ unique))))))) (. .)))",
          "compound(field-2, Xml-1)\n" +
           "nsubj(include-4, field-2)\n" +
           "aux(include-4, should-3)\n" +
           "root(ROOT-0, include-4)\n" +
           "cc:preconj(id-8, both-5)\n" +
           "det(id-8, the-6)\n" +
           "compound(id-8, entity-7)\n" +
           "obj(include-4, id-8)\n" +
           "cc(name-12, and-9)\n" +
           "det(name-12, the-10)\n" +
           "compound(name-12, entity-11)\n" +
           "conj(id-8, name-12)\n" +
           "mark(unique-19, since-13)\n" +
           "det(names-16, the-14)\n" +
           "compound(names-16, entity-15)\n" +
           "nsubj(unique-19, names-16)\n" +
           "cop(unique-19, are-17)\n" +
           "advmod(unique-19, not-18)\n" +
           "advcl(include-4, unique-19)\n"},
         {TestType.BASIC,
          "(ROOT (S (S (NP (DT The) (NN government)) (VP (VBZ counts) (NP (NN money)) (SBAR (IN as) (S (NP (PRP it)) (VP (VBZ is) (VP (VBN spent))))))) (: ;) (S (NP (NNP Dodge)) (VP (VBZ counts) (NP (NNS contracts)) (SBAR (WHADVP (WRB when)) (S (NP (PRP they)) (VP (VBP are) (VP (VBN awarded))))))) (. .)))",
          "det(government-2, The-1)\n" +
           "nsubj(counts-3, government-2)\n" +
           "root(ROOT-0, counts-3)\n" +
           "obj(counts-3, money-4)\n" +
           "mark(spent-8, as-5)\n" +
           "nsubj:pass(spent-8, it-6)\n" +
           "aux:pass(spent-8, is-7)\n" +
           "advcl(counts-3, spent-8)\n" +
           "nsubj(counts-11, Dodge-10)\n" +
           "parataxis(counts-3, counts-11)\n" +
           "obj(counts-11, contracts-12)\n" +
           "advmod(awarded-16, when-13)\n" +
           "nsubj:pass(awarded-16, they-14)\n" +
           "aux:pass(awarded-16, are-15)\n" +
           "advcl(counts-11, awarded-16)\n"},
         {TestType.BASIC,
          "( (S (CC But) (NP (PRP she)) (VP (VBD did) (RB n't) (VP (VB deserve) (S (VP (TO to) (VP (VB have) (S (NP (PRP$ her) (NN head)) (VP (VBN chopped) (PRT (RP off))))))))) (. .)))",
          "cc(deserve-5, But-1)\n" +
           "nsubj(deserve-5, she-2)\n" +
           "aux(deserve-5, did-3)\n" +
           "advmod(deserve-5, n't-4)\n" +
           "root(ROOT-0, deserve-5)\n" +
           "mark(have-7, to-6)\n" +
           "xcomp(deserve-5, have-7)\n" +
           "nmod:poss(head-9, her-8)\n" +
           "nsubj(chopped-10, head-9)\n" +
           "ccomp(have-7, chopped-10)\n" +
           "compound:prt(chopped-10, off-11)\n"},
         {TestType.BASIC,
          "( (S (NP (PRP I)) (VP (VBP like) (NP (NP (NNS dogs)) (CONJP (RB rather) (IN than)) (NP (NNS cats)))) (. .)))",
          "nsubj(like-2, I-1)\n" +
           "root(ROOT-0, like-2)\n" +
           "obj(like-2, dogs-3)\n" +
           "cc(cats-6, rather-4)\n" +
           "fixed(rather-4, than-5)\n" +
           "conj(dogs-3, cats-6)\n"},
         {TestType.BASIC,
          "( (S (NP (PRP I)) (VP (VBP like) (NP (NP (NN brandy)) (CONJP (RB not) (TO to) (VB mention)) (NP (NN cognac)))) (. .)))",
          "nsubj(like-2, I-1)\n" +
           "root(ROOT-0, like-2)\n" +
           "obj(like-2, brandy-3)\n" +
           "cc(cognac-7, not-4)\n" +
           "fixed(not-4, to-5)\n" +
           "fixed(not-4, mention-6)\n" +
           "conj(brandy-3, cognac-7)\n"},
         {TestType.BASIC,
          "( (S (NP (PRP I)) (VP (VBP like) (NP (CONJP (RB not) (RB only)) (NP (NNS cats)) (CONJP (CC but) (RB also)) (NP (NN dogs)))) (. .)))",
          "nsubj(like-2, I-1)\n" +
           "root(ROOT-0, like-2)\n" +
           "advmod(cats-5, not-3)\n" +
           "advmod(cats-5, only-4)\n" +
           "obj(like-2, cats-5)\n" +
           "cc(dogs-8, but-6)\n" +
           "advmod(dogs-8, also-7)\n" +
           "conj(cats-5, dogs-8)\n"},
         {TestType.BASIC,
          "( (S (NP (PRP He)) (VP (VBZ knows) (NP (DT the) (NML (JJ mechanical) (NN engineering)) (NN industry))) (. .)))",
          "nsubj(knows-2, He-1)\n" +
           "root(ROOT-0, knows-2)\n" +
           "det(industry-6, the-3)\n" +
           "amod(engineering-5, mechanical-4)\n" +
           "compound(industry-6, engineering-5)\n" +
           "obj(knows-2, industry-6)\n"},
         {TestType.BASIC,
          "( (SBARQ (WHNP (WP What) (NN weapon)) (SQ (VBZ is) (NP (DT the) (JJ mythological) (NN character) (NN Apollo)) (ADJP (RBS most) (JJ proficient) (PP (IN with)))) (. ?)))",
          "det(weapon-2, What-1)\n" +
           "obl(proficient-9, weapon-2)\n" +
           "cop(proficient-9, is-3)\n" +
           "det(Apollo-7, the-4)\n" +
           "amod(Apollo-7, mythological-5)\n" +
           "compound(Apollo-7, character-6)\n" +
           "nsubj(proficient-9, Apollo-7)\n" +
           "advmod(proficient-9, most-8)\n" +
           "root(ROOT-0, proficient-9)\n" +
           "case(weapon-2, with-10)\n"},
         {TestType.BASIC,
          "( (SINV (CC Nor) (VBP are) (NP (PRP you)) (ADJP (JJ free) (S (VP (TO to) (VP (VB reprint) (NP (JJ such) (NN material))))))) )",
          "cc(free-4, Nor-1)\n" +
           "cop(free-4, are-2)\n" +
           "nsubj(free-4, you-3)\n" +
           "root(ROOT-0, free-4)\n" +
           "mark(reprint-6, to-5)\n" +
           "xcomp(free-4, reprint-6)\n" +
           "amod(material-8, such-7)\n" +
           "obj(reprint-6, material-8)\n"},
         {TestType.BASIC,
          "(ROOT (SBARQ (WHNP (WHADJP (WRB How) (JJ many)) (NP (NNP James) (NNP Bond) (NNS novels))) (SQ (VBP are) (NP (EX there))) (. ?)))",
          "advmod(many-2, How-1)\n" +
           "amod(novels-5, many-2)\n" +
           "compound(novels-5, James-3)\n" +
           "compound(novels-5, Bond-4)\n" +
           "nsubj(are-6, novels-5)\n" +
           "root(ROOT-0, are-6)\n" +
           "expl(are-6, there-7)\n"},
         {TestType.BASIC,
          "( (S (NP (NP (NNS Investments)) (PP (IN in) (NP (NNP South) (NNP Africa)))) (VP (MD will) (VP (VB be) (VP (VBN excluded)))) (. .)))",
          "nsubj:pass(excluded-7, Investments-1)\n" +
           "case(Africa-4, in-2)\n" +
           "compound(Africa-4, South-3)\n" +
           "nmod(Investments-1, Africa-4)\n" +
           "aux(excluded-7, will-5)\n" +
           "aux:pass(excluded-7, be-6)\n" +
           "root(ROOT-0, excluded-7)\n"},
         {TestType.BASIC,
          "( (SINV (ADVP (RB Also)) (VP (VBN excluded)) (VP (MD will) (VP (VB be))) (NP (NP (NNS investments)) (PP (IN in) (NP (NNP South) (NNP Africa)))) (. .)))",
          "advmod(excluded-2, Also-1)\n" +
           "root(ROOT-0, excluded-2)\n" +
           "aux(excluded-2, will-3)\n" +
           "aux:pass(excluded-2, be-4)\n" +
           "nsubj:pass(excluded-2, investments-5)\n" +
           "case(Africa-8, in-6)\n" +
           "compound(Africa-8, South-7)\n" +
           "nmod(investments-5, Africa-8)\n"},
         {TestType.BASIC,
          "( (SINV (VP (VBG Defending) (NP (PRP$ their) (NNS ramparts))) (VP (VBP are)) (NP (NP (NNP Wall) (NNP Street) (POS 's)) (NNP Old) (NNP Guard)) (. .)))",
          "root(ROOT-0, Defending-1)\n" +
           "nmod:poss(ramparts-3, their-2)\n" +
           "obj(Defending-1, ramparts-3)\n" +
           "aux(Defending-1, are-4)\n" +
           "compound(Street-6, Wall-5)\n" +
           "nmod:poss(Guard-9, Street-6)\n" +
           "case(Street-6, 's-7)\n" +
           "compound(Guard-9, Old-8)\n" +
           "nsubj(Defending-1, Guard-9)\n"},
         {TestType.BASIC,
          "( (S (NP-SBJ (JJ Institutional) (NNS investors)) (ADVP (RB mostly)) (VP (VBD remained) (PP-LOC-PRD (IN on) (NP (DT the) (NNS sidelines))) (NP-TMP (NNP Tuesday))) (. .)))",
          "amod(investors-2, Institutional-1)\n" +
           "nsubj(remained-4, investors-2)\n" +
           "advmod(remained-4, mostly-3)\n" +
           "root(ROOT-0, remained-4)\n" +
           "case(sidelines-7, on-5)\n" +
           "det(sidelines-7, the-6)\n" +
           "obl(remained-4, sidelines-7)\n" +
           "obl:tmod(remained-4, Tuesday-8)\n"},
         {TestType.BASIC,
          "( (SQ (VBZ Is) (NP-SBJ (DT this)) (NP-PRD (NP (DT the) (NN future)) (PP (IN of) (NP (NN chamber) (NN music)))) (. ?)))",
          "cop(future-4, Is-1)\n" +
           "nsubj(future-4, this-2)\n" +
           "det(future-4, the-3)\n" +
           "root(ROOT-0, future-4)\n" +
           "case(music-7, of-5)\n" +
           "compound(music-7, chamber-6)\n" +
           "nmod(future-4, music-7)\n"},
         {TestType.BASIC,
          "( (SQ (VBZ Is) (NP-SBJ (DT the) (NN trouble)) (ADVP-PRD (RP over)) (. ?)))",
          "root(ROOT-0, Is-1)\n" +
           "det(trouble-3, the-2)\n" +
           "nsubj(Is-1, trouble-3)\n" +
           "advmod(Is-1, over-4)\n"},
         {TestType.BASIC,
          "( (SBARQ (SBAR (IN Although) (S (NP (NNP Sue)) (VP (VBP is) (ADJP (JJ smart))))) (, ,) (WHNP (WP who)) (SQ (MD will) (VP (VB win))) (. ?)))",
          "mark(smart-4, Although-1)\n" +
           "nsubj(smart-4, Sue-2)\n" +
           "cop(smart-4, is-3)\n" +
           "advcl(win-8, smart-4)\n" +
           "nsubj(win-8, who-6)\n" +
           "aux(win-8, will-7)\n" +
           "root(ROOT-0, win-8)\n"},
         {TestType.BASIC,
          "(NP (NP (NNP Xerox))(, ,) (SBAR (WHNP (WHNP (WP$ whose) (JJ chief) (JJ executive) (NN officer))(, ,) (NP (NNP James) (NNP Gatward))(, ,)) (S (NP-SBJ (-NONE- *T*-1)) (VP (VBZ has) (VP (VBN resigned))))))",
          "root(ROOT-0, Xerox-1)\n" +
           "nmod:poss(officer-6, whose-3)\n" +
           "amod(officer-6, chief-4)\n" +
           "amod(officer-6, executive-5)\n" +
           "nsubj(resigned-12, officer-6)\n" +
           "compound(Gatward-9, James-8)\n" +
           "appos(officer-6, Gatward-9)\n" +
           "aux(resigned-12, has-11)\n" +
           "acl:relcl(Xerox-1, resigned-12)\n"},
         {TestType.BASIC,
          "(ROOT (S (NP (PRP He)) (VP (VBZ gets) (NP (PRP me)) (ADVP-TMP (DT every) (NN time))) (. .)))",
          "nsubj(gets-2, He-1)\n" +
           "root(ROOT-0, gets-2)\n" +
           "obj(gets-2, me-3)\n" +
           "det(time-5, every-4)\n" +
           "advmod(gets-2, time-5)\n"},
         {TestType.BASIC,
          "( (S (NP-SBJ (CC Both) (NP (NNP Mr.) (NNP Parenteau)) (CC and) (NP (NNP Ms.) (NNP Doyon))) (, ,) (ADVP (RB however)) (, ,) (VP (VBD were) (VP (VBG bleeding) (ADVP (RB badly)))) (. .)))",
          "cc:preconj(Parenteau-3, Both-1)\n" +
           "compound(Parenteau-3, Mr.-2)\n" +
           "nsubj(bleeding-11, Parenteau-3)\n" +
           "cc(Doyon-6, and-4)\n" +
           "compound(Doyon-6, Ms.-5)\n" +
           "conj(Parenteau-3, Doyon-6)\n" +
           "advmod(bleeding-11, however-8)\n" +
           "aux(bleeding-11, were-10)\n" +
           "root(ROOT-0, bleeding-11)\n" +
           "advmod(bleeding-11, badly-12)\n"},
         {TestType.BASIC,
           // This pattern of ADJP < RP without an intervening PRT occurs in the Web Treebank...
          "(NP-SBJ-1 (ADJP (ADJP (VBN Rusted) (RP out)) (CC and) (ADJP (JJ unsafe))) (NNS cars))",
          "amod(cars-5, Rusted-1)\n" +
           "compound:prt(Rusted-1, out-2)\n" +
           "cc(unsafe-4, and-3)\n" +
           "conj(Rusted-1, unsafe-4)\n" +
           "root(ROOT-0, cars-5)\n"},
         {TestType.BASIC,
          "( (S (NP-SBJ (PRP u)) (VP (VBP r) (VP (VBG holding) (NP (PRP it)) (ADVP (RB too) (RB tight))))))",
          "nsubj(holding-3, u-1)\n" +
           "aux(holding-3, r-2)\n" +
           "root(ROOT-0, holding-3)\n" +
           "obj(holding-3, it-4)\n" +
           "advmod(tight-6, too-5)\n" +
           "advmod(holding-3, tight-6)\n"},
         {TestType.BASIC,
          "( (S (NP-SBJ (PRP You)) (VP (MD should) (VP (GW e) (VB mail) (NP (PRP her)) (ADVP-TMP (RB sometimes)))) (. .)))",
          "nsubj(mail-4, You-1)\n" +
           "aux(mail-4, should-2)\n" +
           "goeswith(mail-4, e-3)\n" +
           "root(ROOT-0, mail-4)\n" +
           "obj(mail-4, her-5)\n" +
           "advmod(mail-4, sometimes-6)\n"},
         {TestType.BASIC,
          "( (S (NP-SBJ (NN Interest)) (VP (VBZ is) (ADJP-PRD (ADJP (NP-ADV (DT a) (JJ great) (NN deal)) (JJR higher)) (SBAR (IN than) (S (NP-SBJ (PRP it)) (VP (VBD was) (ADJP-PRD (-NONE- *?*)) (ADVP-TMP (NP (DT a) (NN year)) (RB ago))))))) (. .)))",
          "nsubj(higher-6, Interest-1)\n" +
           "cop(higher-6, is-2)\n" +
           "det(deal-5, a-3)\n" +
           "amod(deal-5, great-4)\n" +
           "obl:npmod(higher-6, deal-5)\n" +
           "root(ROOT-0, higher-6)\n" +
           "mark(was-9, than-7)\n" +
           "nsubj(was-9, it-8)\n" +
           "ccomp(higher-6, was-9)\n" +
           "det(year-11, a-10)\n" +
           "obl:npmod(ago-12, year-11)\n" +
           "advmod(was-9, ago-12)\n"},
         {TestType.BASIC,
          "( (S (NP-SBJ (DT The) (NN strike)) (VP (MD may) (VP (VB have) (VP (VBN ended) (SBAR-TMP (ADVP (RB almost)) (IN before) (S (NP-SBJ (PRP it)) (VP (VBD began)))))))))",
          "det(strike-2, The-1)\n" +
           "nsubj(ended-5, strike-2)\n" +
           "aux(ended-5, may-3)\n" +
           "aux(ended-5, have-4)\n" +
           "root(ROOT-0, ended-5)\n" +
           "advmod(began-9, almost-6)\n" +
           "mark(began-9, before-7)\n" +
           "nsubj(began-9, it-8)\n" +
           "advcl(ended-5, began-9)\n"},
         {TestType.BASIC,
          "( (S (SBAR-ADV (IN Although) (S (VP (VBN set) (PP-LOC (IN in) (NP (NNP Japan)))))) (, ,) (NP-SBJ-2 (NP (DT the) (NN novel) (POS 's)) (NN texture)) (VP (VBZ is) (ADJP (JJ American))) (. .)))",
          "mark(set-2, Although-1)\n" +
           "advcl(American-11, set-2)\n" +
           "case(Japan-4, in-3)\n" +
           "obl(set-2, Japan-4)\n" +
           "det(novel-7, the-6)\n" +
           "nmod:poss(texture-9, novel-7)\n" +
           "case(novel-7, 's-8)\n" +
           "nsubj(American-11, texture-9)\n" +
           "cop(American-11, is-10)\n" +
           "root(ROOT-0, American-11)\n"},
        {TestType.BASIC,   // test the fallthrough relation when 's gets labeled VBZ
          "( (S (SBAR-ADV (IN Although) (S (VP (VBN set) (PP-LOC (IN in) (NP (NNP Japan)))))) (, ,) (NP-SBJ-2 (NP (DT the) (NN novel) (VBZ 's)) (NN texture)) (VP (VBZ is) (ADJP (JJ American))) (. .)))",
          "mark(set-2, Although-1)\n" +
           "advcl(American-11, set-2)\n" +
           "case(Japan-4, in-3)\n" +
           "obl(set-2, Japan-4)\n" +
           "det(novel-7, the-6)\n" +
           "nmod:poss(texture-9, novel-7)\n" +
           "case(novel-7, 's-8)\n" +
           "nsubj(American-11, texture-9)\n" +
           "cop(American-11, is-10)\n" +
           "root(ROOT-0, American-11)\n"},
        {TestType.BASIC,   // should also work when ’s gets labeled VBZ
          "( (S (SBAR-ADV (IN Although) (S (VP (VBN set) (PP-LOC (IN in) (NP (NNP Japan)))))) (, ,) (NP-SBJ-2 (NP (DT the) (NN novel) (VBZ ’s)) (NN texture)) (VP (VBZ is) (ADJP (JJ American))) (. .)))",
          "mark(set-2, Although-1)\n" +
           "advcl(American-11, set-2)\n" +
           "case(Japan-4, in-3)\n" +
           "obl(set-2, Japan-4)\n" +
           "det(novel-7, the-6)\n" +
           "nmod:poss(texture-9, novel-7)\n" +
           "case(novel-7, ’s-8)\n" +
           "nsubj(American-11, texture-9)\n" +
           "cop(American-11, is-10)\n" +
           "root(ROOT-0, American-11)\n"},
         {TestType.BASIC,
          "( (S-IMP (INTJ (UH please)) (NP-SBJ (-NONE- *PRO*)) (VP (VB specify) (NP (WDT which) (NML (NNP royal) (CC or) (NNP carnival)) (NN ship))) (NFP -LRB-:)))",
          "discourse(specify-2, please-1)\n" +
           "root(ROOT-0, specify-2)\n" +
           "det(ship-7, which-3)\n" +
           "compound(ship-7, royal-4)\n" +
           "cc(carnival-6, or-5)\n" +
           "conj(royal-4, carnival-6)\n" +
           "obj(specify-2, ship-7)\n" +
           "discourse(specify-2, (:-8)\n"},
         {TestType.BASIC,
          "(NP (DT those) (RRC (ADVP-TMP (RB still)) (PP-LOC (IN under) (NP (NNP GASB) (NNS rules)))))",
          "root(ROOT-0, those-1)\n" +
           "advmod(rules-5, still-2)\n" +
           "case(rules-5, under-3)\n" +
           "compound(rules-5, GASB-4)\n" +
           "acl:relcl(those-1, rules-5)\n"},
         {TestType.BASIC,
          "(NP (NP (DT the) (NN auction) (NN house)) (RRC (RRC (VP (VBN founded) (NP (-NONE- *)) (PP-LOC (IN in) (NP (NNP London))) (NP-TMP (CD 1744)))) (CC and) (RRC (ADVP-TMP (RB now)) (PP (IN under) (NP (NP (DT the) (NN umbrella)) (PP (IN of) (NP (NP (NNP Sotheby) (POS 's)) (NNPS Holdings) (NNP Inc.))))))))",
          "det(house-3, the-1)\n" +
           "compound(house-3, auction-2)\n" +
           "root(ROOT-0, house-3)\n" +
           "acl:relcl(house-3, founded-4)\n" +
           "case(London-6, in-5)\n" +
           "obl(founded-4, London-6)\n" +
           "obl:tmod(founded-4, 1744-7)\n" +
           "cc(umbrella-12, and-8)\n" +
           "advmod(umbrella-12, now-9)\n" +
           "case(umbrella-12, under-10)\n" +
           "det(umbrella-12, the-11)\n" +
           "conj(founded-4, umbrella-12)\n" +
           "case(Inc.-17, of-13)\n" +
           "nmod:poss(Inc.-17, Sotheby-14)\n" +
           "case(Sotheby-14, 's-15)\n" +
           "compound(Inc.-17, Holdings-16)\n" +
           "nmod(umbrella-12, Inc.-17)\n"},
         {TestType.BASIC, // tests a common mislabeling of 's
          "(NP (NP (DT the) (NN auction) (NN house)) (RRC (RRC (VP (VBN founded) (NP (-NONE- *)) (PP-LOC (IN in) (NP (NNP London))) (NP-TMP (CD 1744)))) (CC and) (RRC (ADVP-TMP (RB now)) (PP (IN under) (NP (NP (DT the) (NN umbrella)) (PP (IN of) (NP (NP (NNP Sotheby) (VBZ 's)) (NNPS Holdings) (NNP Inc.))))))))",
          "det(house-3, the-1)\n" +
           "compound(house-3, auction-2)\n" +
           "root(ROOT-0, house-3)\n" +
           "acl:relcl(house-3, founded-4)\n" +
           "case(London-6, in-5)\n" +
           "obl(founded-4, London-6)\n" +
           "obl:tmod(founded-4, 1744-7)\n" +
           "cc(umbrella-12, and-8)\n" +
           "advmod(umbrella-12, now-9)\n" +
           "case(umbrella-12, under-10)\n" +
           "det(umbrella-12, the-11)\n" +
           "conj(founded-4, umbrella-12)\n" +
           "case(Inc.-17, of-13)\n" +
           "nmod:poss(Inc.-17, Sotheby-14)\n" +
           "case(Sotheby-14, 's-15)\n" +
           "compound(Inc.-17, Holdings-16)\n" +
           "nmod(umbrella-12, Inc.-17)\n"},
         {TestType.BASIC, // tests a common mislabeling of ’s
          "(NP (NP (DT the) (NN auction) (NN house)) (RRC (RRC (VP (VBN founded) (NP (-NONE- *)) (PP-LOC (IN in) (NP (NNP London))) (NP-TMP (CD 1744)))) (CC and) (RRC (ADVP-TMP (RB now)) (PP (IN under) (NP (NP (DT the) (NN umbrella)) (PP (IN of) (NP (NP (NNP Sotheby) (VBZ ’s)) (NNPS Holdings) (NNP Inc.))))))))",
          "det(house-3, the-1)\n" +
           "compound(house-3, auction-2)\n" +
           "root(ROOT-0, house-3)\n" +
           "acl:relcl(house-3, founded-4)\n" +
           "case(London-6, in-5)\n" +
           "obl(founded-4, London-6)\n" +
           "obl:tmod(founded-4, 1744-7)\n" +
           "cc(umbrella-12, and-8)\n" +
           "advmod(umbrella-12, now-9)\n" +
           "case(umbrella-12, under-10)\n" +
           "det(umbrella-12, the-11)\n" +
           "conj(founded-4, umbrella-12)\n" +
           "case(Inc.-17, of-13)\n" +
           "nmod:poss(Inc.-17, Sotheby-14)\n" +
           "case(Sotheby-14, ’s-15)\n" +
           "compound(Inc.-17, Holdings-16)\n" +
           "nmod(umbrella-12, Inc.-17)\n"},
         // tough movement example
         {TestType.BASIC,
          "(S (NP-SBJ (NNS morcillas)) (VP (VBP are) (ADVP (RB basically)) (ADJP-PRD (JJ impossible) (SBAR (WHNP-1 (-NONE- *0*)) (S (NP-SBJ (-NONE- *PRO*)) (VP (TO to) (VP (VB find) (NP-1 (-NONE- *T*)) (PP-LOC (IN in) (NP (NNP California))))))))))",
          "nsubj(impossible-4, morcillas-1)\n" +
           "cop(impossible-4, are-2)\n" +
           "advmod(impossible-4, basically-3)\n" +
           "root(ROOT-0, impossible-4)\n" +
           "mark(find-6, to-5)\n" +
           "ccomp(impossible-4, find-6)\n" +
           "case(California-8, in-7)\n" +
           "obl(find-6, California-8)\n"},
         {TestType.BASIC,
          // S parataxis
          "( (S (S (NP-SBJ (-NONE- *)) (VP (VBP Do) (RB n't) (VP (VB wait)))) (: --) (S (NP-SBJ (-NONE- *)) (VP (VBP act) (ADVP-TMP (RB now)))) (. !)))",
          "aux(wait-3, Do-1)\n" +
           "advmod(wait-3, n't-2)\n" +
           "root(ROOT-0, wait-3)\n" +
           "parataxis(wait-3, act-5)\n" +
           "advmod(act-5, now-6)\n"},
         {TestType.BASIC,
          // Two tricky conjunctions with punctuation and/or interjections
          "( (S (NP-SBJ (DT The) (NNPS Parks) (NNP Council)) (VP (VBD wrote) (NP (DT the) (NNP BPCA)) (SBAR (IN that) (S (NP-SBJ (DT this) (ADJP (`` ``) (RB too) (`` `) (JJ private) ('' ') (: ...) (JJ exclusive) (, ,) ('' '') (JJ complex) (CC and) (JJ expensive)) (`` ``) (VBN enclosed) (NN garden)) (: ...) (VP (VBZ belongs) (PP-LOC-CLR (IN in) (NP (NP (RB almost) (DT any) (NN location)) (CC but) (NP (DT the) (NN waterfront)))))))) (. .) ('' '')))",
          "det(Council-3, The-1)\n" +
           "compound(Council-3, Parks-2)\n" +
           "nsubj(wrote-4, Council-3)\n" +
           "root(ROOT-0, wrote-4)\n" +
           "det(BPCA-6, the-5)\n" +
           "obj(wrote-4, BPCA-6)\n" +
           "mark(belongs-25, that-7)\n" +
           "det(garden-23, this-8)\n" +
           "advmod(private-12, too-10)\n" +
           "amod(garden-23, private-12)\n" +
           "conj(private-12, exclusive-15)\n" +
           "conj(private-12, complex-18)\n" +
           "cc(expensive-20, and-19)\n" +
           "conj(private-12, expensive-20)\n" +
           "amod(garden-23, enclosed-22)\n" +
           "nsubj(belongs-25, garden-23)\n" +
           "ccomp(wrote-4, belongs-25)\n" +
           "case(location-29, in-26)\n" +
           "advmod(location-29, almost-27)\n" +
           "det(location-29, any-28)\n" +
           "obl(belongs-25, location-29)\n" +
           "cc(waterfront-32, but-30)\n" +
           "det(waterfront-32, the-31)\n" +
           "conj(location-29, waterfront-32)\n"},
         {TestType.BASIC,
          "( (S (`` ``) (CC And) (NP-SBJ (PRP you)) (VP (MD ca) (RB n't) (VP (VB have) (S (NP-SBJ (NP (NNS taxpayers)) (VP (VBG coming) (PP-DIR (IN into) (NP (DT an) (NN audit))))) (VP (VBG hearing) (NP (`` `) (UH oohs) (: ') (CC and) (`` `) (UH ahs)))))) (. .) ('' ') ('' '')))",
          "cc(have-6, And-2)\n" +
           "nsubj(have-6, you-3)\n" +
           "aux(have-6, ca-4)\n" +
           "advmod(have-6, n't-5)\n" +
           "root(ROOT-0, have-6)\n" +
           "nsubj(hearing-12, taxpayers-7)\n" +
           "acl(taxpayers-7, coming-8)\n" +
           "case(audit-11, into-9)\n" +
           "det(audit-11, an-10)\n" +
           "obl(coming-8, audit-11)\n" +
           "ccomp(have-6, hearing-12)\n" +
           "obj(hearing-12, oohs-14)\n" +
           "cc(ahs-18, and-16)\n" +
           "conj(oohs-14, ahs-18)\n"},
         {TestType.BASIC,
          "( (S (NP-SBJ-1 (VBN Freed) (JJ black) (NNS nationalists)) (VP (VP (VBD resumed) (NP (JJ political) (NN activity)) (PP-LOC (IN in) (NP (NNP South) (NNP Africa)))) (CC and) (VP (VBD vowed) (S (NP-SBJ (-NONE- *-1)) (VP (TO to) (VP (VB fight) (PP-CLR (IN against) (NP (NN apartheid))))))) (, ,) (S-ADV (NP-SBJ (-NONE- *)) (VP (VBG raising) (NP (NP (NNS fears)) (PP (IN of) (NP (DT a) (JJ possible) (JJ white) (NN backlash))))))) (. .)))",
          "amod(nationalists-3, Freed-1)\n" +
           "amod(nationalists-3, black-2)\n" +
           "nsubj(resumed-4, nationalists-3)\n" +
           "root(ROOT-0, resumed-4)\n" +
           "amod(activity-6, political-5)\n" +
           "obj(resumed-4, activity-6)\n" +
           "case(Africa-9, in-7)\n" +
           "compound(Africa-9, South-8)\n" +
           "obl(resumed-4, Africa-9)\n" +
           "cc(vowed-11, and-10)\n" +
           "conj(resumed-4, vowed-11)\n" +
           "mark(fight-13, to-12)\n" +
           "xcomp(vowed-11, fight-13)\n" +
           "case(apartheid-15, against-14)\n" +
           "obl(fight-13, apartheid-15)\n" +
           "advcl(resumed-4, raising-17)\n" +
           "obj(raising-17, fears-18)\n" +
           "case(backlash-23, of-19)\n" +
           "det(backlash-23, a-20)\n" +
           "amod(backlash-23, possible-21)\n" +
           "amod(backlash-23, white-22)\n" +
           "nmod(fears-18, backlash-23)\n"},
         {TestType.BASIC,
          "( (S (S-NOM-SBJ (NP-SBJ-1 (-NONE- *)) (VP (VBG Being) (VP (VBN held) (S (NP-SBJ (-NONE- *-1)) (PP-PRD (ADVP (RB well)) (IN below) (NP (NN capacity))))))) (VP (VP (ADVP-MNR (RB greatly)) (VBZ irritates) (NP (PRP them))) (, ,) (CC and) (VP (VBZ has) (VP (VBN led) (PP-CLR (TO to) (NP (JJ widespread) (NN cheating)))))) (. .)))",
          "aux:pass(held-2, Being-1)\n" +
           "csubj(irritates-7, held-2)\n" +
           "advmod(capacity-5, well-3)\n" +
           "case(capacity-5, below-4)\n" +
           "obl(held-2, capacity-5)\n" +
           "advmod(irritates-7, greatly-6)\n" +
           "root(ROOT-0, irritates-7)\n" +
           "obj(irritates-7, them-8)\n" +
           "cc(led-12, and-10)\n" +
           "aux(led-12, has-11)\n" +
           "conj(irritates-7, led-12)\n" +
           "case(cheating-15, to-13)\n" +
           "amod(cheating-15, widespread-14)\n" +
           "obl(led-12, cheating-15)\n"},
         {TestType.BASIC,
          "( (S (NP-SBJ (PRP They)) (VP (VBD acquired) (NP (NP (NNS stakes)) (PP (IN in) (NP (NP (VBG bottling) (NNS companies)) (UCP-LOC (PP (IN in) (NP (DT the) (NNP U.S.))) (CC and) (ADVP (RB overseas))))))) (. .)))",
          "nsubj(acquired-2, They-1)\n" +
           "root(ROOT-0, acquired-2)\n" +
           "obj(acquired-2, stakes-3)\n" +
           "case(companies-6, in-4)\n" +
           "amod(companies-6, bottling-5)\n" +
           "nmod(stakes-3, companies-6)\n" +
           "case(U.S.-9, in-7)\n" +
           "det(U.S.-9, the-8)\n" +
           "nmod(companies-6, U.S.-9)\n" +
           "cc(overseas-11, and-10)\n" +
           "conj(U.S.-9, overseas-11)\n"},
           // This is the say-complement case that we don't yet handle, but might someday.
           //{TestType.BASIC,
           //  "( (SBAR (WHNP-9 (WDT Which)) (S (NP-SBJ (PRP I)) (ADVP-TMP (RB then)) (VP (VBD realized) (SBAR (-NONE- *0*) (S (NP-SBJ (PRP I)) (VP (VBD missed) (NP-9 (-NONE- *T*))))))) (. .)))",
           //  "obj(missed-6, Which-1)\n" +
           //   "nsubj(realized-4, I-2)\n" +
           //   "advmod(realized-4, then-3)\n" +
           //   "root(ROOT-0, realized-4)\n" +
           //   "nsubj(missed-6, I-5)\n" +
           //   "ccomp(realized-4, missed-6)\n"},
            {TestType.BASIC,
             "(ROOT (S (NP (PRP I)) (VP (VBD saw) (NP (NP (DT the) (NN woman)) (SBAR (WHNP (WP whom)) (S (NP (PRP you)) (VP (VBD gave) (NP (DT the) (NN package)) (PP (TO to))))))) (. .)))",
             "nsubj(saw-2, I-1)\n" +
              "root(ROOT-0, saw-2)\n" +
              "det(woman-4, the-3)\n" +
              "obj(saw-2, woman-4)\n" +
              "obl(gave-7, whom-5)\n" +
              "nsubj(gave-7, you-6)\n" +
              "acl:relcl(woman-4, gave-7)\n" +
              "det(package-9, the-8)\n" +
              "obj(gave-7, package-9)\n" +
              "case(whom-5, to-10)\n"},
            {TestType.BASIC,
             "( (S (NP-SBJ (PRP i)) (VP (VBP m) (ADJP-PRD (JJ fat)))))",
             "nsubj(fat-3, i-1)\n" +
              "cop(fat-3, m-2)\n" +
              "root(ROOT-0, fat-3)\n"},
             {TestType.BASIC,
              //this is a WHNP that gets converted rel to pobj in dependency postprocessing
              "(NP (NP (NNP Mr.) (NNP Laidig)) (, ,) (SBAR (WHNP-1 (WP whom)) (S (NP-SBJ (PRP he)) (VP (VBD referred) (PP-CLR (TO to) (NP (-NONE- *T*-1))) (PP-CLR (IN as) (NP (DT a) (NN friend)))))))",
              "compound(Laidig-2, Mr.-1)\n" +
               "root(ROOT-0, Laidig-2)\n" +
               "obl(referred-6, whom-4)\n" +
               "nsubj(referred-6, he-5)\n" +
               "acl:relcl(Laidig-2, referred-6)\n" +
               "case(whom-4, to-7)\n" +
               "case(friend-10, as-8)\n" +
               "det(friend-10, a-9)\n" +
               "obl(referred-6, friend-10)\n"},
            {TestType.BASIC,
             "( (SBARQ (WHNP-9 (WP what)) (SQ (VBZ does) (NP-SBJ (PRP it)) (VP (VB mean) (NP-9 (-NONE- *T*)) (SBAR-TMP (WHADVP-1 (WRB when)) (S (NP-SBJ (DT a) (JJ veiled) (NN chameleon) (NN egg)) (VP (VBZ is) (ADJP-PRD (JJ soft)) (ADVP-TMP-1 (-NONE- *T*))))))) (. ?)))",
             "obj(mean-4, what-1)\n" +
              "aux(mean-4, does-2)\n" +
              "nsubj(mean-4, it-3)\n" +
              "root(ROOT-0, mean-4)\n" +
              "advmod(soft-11, when-5)\n" +
              "det(egg-9, a-6)\n" +
              "amod(egg-9, veiled-7)\n" +
              "compound(egg-9, chameleon-8)\n" +
              "nsubj(soft-11, egg-9)\n" +
              "cop(soft-11, is-10)\n" +
              "advcl(mean-4, soft-11)\n"},
            {TestType.BASIC,
             "( (S (NP-SBJ (PRP it)) (VP (VBD wase) (RB nt) (VP (VBG going))) (. ....)))",
             "nsubj(going-4, it-1)\n" +
              "aux(going-4, wase-2)\n" +
              "advmod(going-4, nt-3)\n" +
              "root(ROOT-0, going-4)\n"},
            {TestType.BASIC,
             // Relative clauses used to not be recognized off NP-ADV or NP-TMP
             "( (S (NP-SBJ (DT An) (NN arbitrator) ) (VP (VP (VBD awarded) (NP (NNP Eastern) (NNPS Airlines) (NNS pilots) ) (NP (NP (QP (IN between) ($ $) (CD 60) (CD million) (CC and) ($ $) (CD 100) (CD million) ) (-NONE- *U*) ) (PP (IN in) (NP (JJ back) (NN pay) )))) (, ,) (NP-ADV (NP (DT a) (NN decision) ) (SBAR (WHNP-285 (WDT that) ) (S (NP-SBJ (-NONE- *T*-285) ) (VP (MD could) (VP (VB complicate) (NP (NP (DT the) (NN carrier) (POS 's) ) (NN bankruptcy-law) (NN reorganization) ))))))) (. .) ))",
             "det(arbitrator-2, An-1)\n" +
              "nsubj(awarded-3, arbitrator-2)\n" +
              "root(ROOT-0, awarded-3)\n" +
              "compound(pilots-6, Eastern-4)\n" +
              "compound(pilots-6, Airlines-5)\n" +
              "iobj(awarded-3, pilots-6)\n" +
              "advmod($-8, between-7)\n" +
              "obj(awarded-3, $-8)\n" +
              "compound(million-10, 60-9)\n" +
              "nummod($-8, million-10)\n" +
              "cc($-12, and-11)\n" +
              "conj($-8, $-12)\n" +
              "compound(million-14, 100-13)\n" +
              "nummod($-12, million-14)\n" +
              "case(pay-17, in-15)\n" +
              "amod(pay-17, back-16)\n" +
              "nmod($-8, pay-17)\n" +
              "det(decision-20, a-19)\n" +
              "obl:npmod(awarded-3, decision-20)\n" +
              "nsubj(complicate-23, that-21)\n" +
              "aux(complicate-23, could-22)\n" +
              "acl:relcl(decision-20, complicate-23)\n" +
              "det(carrier-25, the-24)\n" +
              "nmod:poss(reorganization-28, carrier-25)\n" +
              "case(carrier-25, 's-26)\n" +
              "compound(reorganization-28, bankruptcy-law-27)\n" +
              "obj(complicate-23, reorganization-28)\n"},
            // Check same regardless of ROOT or none and functional categories or none
            {TestType.BASIC,
             "(ROOT (S (NP (CD Two) (JJ former) (NNS ministers) ) (VP (VBD were) (ADJP (ADJP (ADVP (RB heavily) ) (VBN implicated) )) (PP (IN in) (NP (DT the) (NNP Koskotas) (NN affair) )))))",
             "nummod(ministers-3, Two-1)\n" +
              "amod(ministers-3, former-2)\n" +
              "nsubj:pass(implicated-6, ministers-3)\n" +
              "aux:pass(implicated-6, were-4)\n" +
              "advmod(implicated-6, heavily-5)\n" +
              "root(ROOT-0, implicated-6)\n" +
              "case(affair-10, in-7)\n" +
              "det(affair-10, the-8)\n" +
              "compound(affair-10, Koskotas-9)\n" +
              "obl(implicated-6, affair-10)\n"},
            {TestType.BASIC,
             "( (S (NP-SBJ (CD Two) (JJ former) (NNS ministers) ) (VP (VBD were) (ADJP-PRD (ADJP (ADVP (RB heavily) ) (VBN implicated) )) (PP-LOC (IN in) (NP (DT the) (NNP Koskotas) (NN affair) )))))",
             "nummod(ministers-3, Two-1)\n" +
              "amod(ministers-3, former-2)\n" +
              "nsubj:pass(implicated-6, ministers-3)\n" +
              "aux:pass(implicated-6, were-4)\n" +
              "advmod(implicated-6, heavily-5)\n" +
              "root(ROOT-0, implicated-6)\n" +
              "case(affair-10, in-7)\n" +
              "det(affair-10, the-8)\n" +
              "compound(affair-10, Koskotas-9)\n" +
              "obl(implicated-6, affair-10)\n"},
            {TestType.BASIC,
             "(NP-ADV (NP (DT The) (JJR more) (NNS accounts) ) (SBAR (WHNP-1 (-NONE- 0) ) (S (NP-SBJ (NNS customers) ) (VP (VBP have) (NP (-NONE- *T*-1) )))))",
             "det(accounts-3, The-1)\n" +
              "amod(accounts-3, more-2)\n" +
              "root(ROOT-0, accounts-3)\n" +
              "nsubj(have-5, customers-4)\n" +
              "acl:relcl(accounts-3, have-5)\n"},
            {TestType.BASIC,
             "(NP-ADV (NP-ADV (DT a) (NN-ADV lesson)) (VP (ADVP (RB once)) (VBN learned) (PP (IN by) (NP (NNP Henry) (NNP Kissinger)))))",
             "det(lesson-2, a-1)\n" +
              "root(ROOT-0, lesson-2)\n" +
              "advmod(learned-4, once-3)\n" +
              "acl(lesson-2, learned-4)\n" +
              "case(Kissinger-7, by-5)\n" +
              "compound(Kissinger-7, Henry-6)\n" +
              "obl(learned-4, Kissinger-7)\n"},
            {TestType.BASIC,
             // you get PP structures with a CC-as-IN for vs., plus, less, but
             "(NP (NP (NNP U.S.)) (PP (CC v.) (NP (NNP Hudson) (CC and) (NNP Goodwin))))",
             "root(ROOT-0, U.S.-1)\n" +
              "case(Hudson-3, v.-2)\n" +
              "nmod(U.S.-1, Hudson-3)\n" +
              "cc(Goodwin-5, and-4)\n" +
              "conj(Hudson-3, Goodwin-5)\n"},
            {TestType.BASIC,
             "(NP (NP (NN nothing)) (PP (CC but) (NP (PRP$ their) (NNS scratches))))",
             "root(ROOT-0, nothing-1)\n" +
              "case(scratches-4, but-2)\n" +
              "nmod:poss(scratches-4, their-3)\n" +
              "nmod(nothing-1, scratches-4)\n"},
            // You'd like this one to come out with an nsubj:pass, but there are many other cases that are tagging mistakes. Decide what to do
            //{TestType.BASIC,
            // "( (S-HLN (NP-SBJ-1 (NN ABORTION) (NN RULING)) (VP (VBN UPHELD) (NP (-NONE- *-1))) (: :)))",
            // "nn(RULING-2, ABORTION-1)\n" +
            //  "nsubj:pass(UPHELD-3, RULING-2)\n" +
            //  "root(ROOT-0, UPHELD-3)\n"},
            {TestType.BASIC,
             "(FRAG (ADVP (ADVP (RB So) (RB long)) (SBAR (IN as) (S (NP-SBJ (PRP you)) (VP (VBP do) (RB n't) (VP (VB look) (ADVP-DIR (RB down))))))) (. .))",
             "advmod(long-2, So-1)\n" +
              "root(ROOT-0, long-2)\n" +
              "mark(look-7, as-3)\n" +
              "nsubj(look-7, you-4)\n" +
              "aux(look-7, do-5)\n" +
              "advmod(look-7, n't-6)\n" +
              "advcl(long-2, look-7)\n" +
              "advmod(look-7, down-8)\n"},
            {TestType.BASIC,
              "(ROOT (S (NP (NNP Bill)) (VP (VBZ is) (PP (IN from) (NP (NNP California)))) (. .)))",
              "nsubj(California-4, Bill-1)\n" +
               "cop(California-4, is-2)\n" +
               "case(California-4, from-3)\n" +
               "root(ROOT-0, California-4)\n"},
            {TestType.BASIC,
              "( (SBARQ (WHNP (WDT What) (NN radio) (NN station)) (SQ (VBD did) (NP (NNP Paul) (NNP Harvey)) (VP (VB work) (PP (IN for)))) (. ?)))",
              "det(station-3, What-1)\n" +
               "compound(station-3, radio-2)\n" +
               "obl(work-7, station-3)\n" +
               "aux(work-7, did-4)\n" +
               "compound(Harvey-6, Paul-5)\n" +
               "nsubj(work-7, Harvey-6)\n" +
               "root(ROOT-0, work-7)\n" +
               "case(station-3, for-8)\n"},
            {TestType.BASIC,
              "((S (NP (NN Media) (NNS reports))(VP (VBP are)(NP (NP (DT a) (JJ poor) (NN approximation)) (PP (IN of) (NP (NN reality)))) (PP (IN because) (IN of) (NP (NP (DT the) (NN lack)) (PP (IN of) (NP (JJ good) (NNS sources)))))) (. .)))",
              "compound(reports-2, Media-1)\n" +
              "nsubj(approximation-6, reports-2)\n" +
              "cop(approximation-6, are-3)\n" +
              "det(approximation-6, a-4)\n" +
              "amod(approximation-6, poor-5)\n" +
              "root(ROOT-0, approximation-6)\n" +
              "case(reality-8, of-7)\n" +
              "nmod(approximation-6, reality-8)\n" +
              "case(lack-12, because-9)\n" +
              "fixed(because-9, of-10)\n" +
              "det(lack-12, the-11)\n" +
              "obl(approximation-6, lack-12)\n" +
              "case(sources-15, of-13)\n" +
              "amod(sources-15, good-14)\n" +
              "nmod(lack-12, sources-15)\n"},
            {TestType.BASIC,
              "((S (S-IMP (NP-SBJ (-NONE- *PRO*)) (VP (VP (VB Try) (S-NOM (NP-SBJ (-NONE- *PRO*)) (VP (VBG googling) (NP (PRP it))))) (CC or) (VP (VB type) (NP (PRP it)) (PP-CLR (IN into) (NP (NNP youtube)))))) (S (NP-SBJ (PRP you)) (VP (MD might) (VP (VB get) (ADJP-PRD (JJ lucky))))) (. .)))",
              "root(ROOT-0, Try-1)\n" +
               "xcomp(Try-1, googling-2)\n" +
               "obj(googling-2, it-3)\n" +
               "cc(type-5, or-4)\n" +
               "conj(Try-1, type-5)\n" +
               "obj(type-5, it-6)\n" +
               "case(youtube-8, into-7)\n" +
               "obl(type-5, youtube-8)\n" +
               "nsubj(get-11, you-9)\n" +
               "aux(get-11, might-10)\n" +
               "parataxis(Try-1, get-11)\n" +
               "xcomp(get-11, lucky-12)\n"},

            /* Added in 2019 to improve new treebank tokenization */
            {TestType.BASIC,
                    "( (S (NP (DT Some) (ADJP (NP (NN gun)) (HYPH -) (VBG toting)) (NNS guards)) (VP (VBD arrived)) (. .)))",
                    "det(guards-5, Some-1)\n" +
                            "obl(toting-4, gun-2)\n" +
                            "punct(toting-4, --3)\n" +
                            "amod(guards-5, toting-4)\n" +
                            "nsubj(arrived-6, guards-5)\n" +
                            "root(ROOT-0, arrived-6)\n"},
            {TestType.BASIC,
                    "( (S (NP (DT Some) (ADJP (NN gun) (HYPH -) (VBG toting)) (NNS guards)) (VP (VBD arrived)) (. .)))",
                    "det(guards-5, Some-1)\n" +
                            "obl(toting-4, gun-2)\n" +
                            "punct(toting-4, --3)\n" +
                            "amod(guards-5, toting-4)\n" +
                            "nsubj(arrived-6, guards-5)\n" +
                            "root(ROOT-0, arrived-6)\n"},
            {TestType.BASIC,
                    "( (S (NP (PRP She)) (VP (VBD asked) (NP (DT the) (NN man)) (S (VP (TO to) (VP (VB leave))))) (. .)))",
                    "nsubj(asked-2, She-1)\n" +
                            "root(ROOT-0, asked-2)\n" +
                            "det(man-4, the-3)\n" +
                            "obj(asked-2, man-4)\n" +
                            "mark(leave-6, to-5)\n" +
                            "xcomp(asked-2, leave-6)\n"},

            /* Test the various verb "to be" cases in statements, questions, and imperatives. */
            {TestType.BASIC,
              "(ROOT (S (NP (NNP Sue)) (VP (VBZ is) (VP (VBG speaking))) (. .)))",
              "nsubj(speaking-3, Sue-1)\n" +
               "aux(speaking-3, is-2)\n" +
               "root(ROOT-0, speaking-3)\n"},
             {TestType.NON_COLLAPSED,
              "(ROOT (S (NP (NNP Sue)) (VP (VBZ is) (VP (VBG speaking))) (. .)))",
              "nsubj(speaking-3, Sue-1)\n" +
               "aux(speaking-3, is-2)\n" +
               "root(ROOT-0, speaking-3)\n"},
             {TestType.BASIC,
              "(ROOT (SBARQ (WHNP (WP Who)) (SQ (VBZ is)  (VP (VBG speaking))) (. ?)))",
              "nsubj(speaking-3, Who-1)\n" +
               "aux(speaking-3, is-2)\n" +
               "root(ROOT-0, speaking-3)\n"},
             {TestType.NON_COLLAPSED,
              "(ROOT (SBARQ (WHNP (WP Who)) (SQ (VBZ is)  (VP (VBG speaking))) (. ?)))",
              "nsubj(speaking-3, Who-1)\n" +
               "aux(speaking-3, is-2)\n" +
               "root(ROOT-0, speaking-3)\n"},
             {TestType.BASIC,
              "(ROOT (S (VP (VB Be) (ADJP (JJ honest))) (. .)))",
              "cop(honest-2, Be-1)\n" +
               "root(ROOT-0, honest-2)\n"},
             {TestType.NON_COLLAPSED,
              "(ROOT (S (VP (VB Be) (ADJP (JJ honest))) (. .)))",
              "cop(honest-2, Be-1)\n" +
               "root(ROOT-0, honest-2)\n"},
             {TestType.BASIC,
              "(ROOT (SBARQ (WHNP (WP What) ) (SQ (VBZ is) (NP (PRP he) ) (VP (VBG doing)))))",
              "obj(doing-4, What-1)\n" +
               "aux(doing-4, is-2)\n" +
               "nsubj(doing-4, he-3)\n" +
               "root(ROOT-0, doing-4)\n"},
             {TestType.NON_COLLAPSED,
              "(ROOT (SBARQ (WHNP (WP What) ) (SQ (VBZ is) (NP (PRP he) ) (VP (VBG doing)))))",
              "obj(doing-4, What-1)\n" +
               "aux(doing-4, is-2)\n" +
               "nsubj(doing-4, he-3)\n" +
               "root(ROOT-0, doing-4)\n"},
             {TestType.BASIC,
              "(ROOT (SBARQ (WHNP (WP What) ) (SQ (VBP am) (NP (PRP I) ) (VP (VBG doing) (PP (IN in) (NP (NNP Jackson) (NNP Hole) )))) (. ?) ))",
              "obj(doing-4, What-1)\n" +
               "aux(doing-4, am-2)\n" +
               "nsubj(doing-4, I-3)\n" +
               "root(ROOT-0, doing-4)\n" +
               "case(Hole-7, in-5)\n" +
               "compound(Hole-7, Jackson-6)\n" +
               "obl(doing-4, Hole-7)\n"},
             {TestType.NON_COLLAPSED,
              "(ROOT (SBARQ (WHNP (WP What) ) (SQ (VBP am) (NP (PRP I) ) (VP (VBG doing) (PP (IN in) (NP (NNP Jackson) (NNP Hole) )))) (. ?) ))",
              "obj(doing-4, What-1)\n" +
               "aux(doing-4, am-2)\n" +
               "nsubj(doing-4, I-3)\n" +
               "root(ROOT-0, doing-4)\n" +
               "case(Hole-7, in-5)\n" +
               "compound(Hole-7, Jackson-6)\n" +
               "obl(doing-4, Hole-7)\n"},
             {TestType.BASIC,
              "(ROOT (SBARQ (WHNP (WP Who)) (SQ (VBP am) (NP (PRP I)) (S (VP (TO to) (VP (VB judge))))) (. ?)))",
              "root(ROOT-0, Who-1)\n" +
               "cop(Who-1, am-2)\n" +
               "nsubj(Who-1, I-3)\n" +
               "mark(judge-5, to-4)\n" +
               "advcl(Who-1, judge-5)\n"}, //advcl??
             {TestType.NON_COLLAPSED,
              "(ROOT (SBARQ (WHNP (WP Who)) (SQ (VBP am) (NP (PRP I)) (S (VP (TO to) (VP (VB judge))))) (. ?)))",
              "root(ROOT-0, Who-1)\n" +
               "cop(Who-1, am-2)\n" +
               "nsubj(Who-1, I-3)\n" +
               "mark(judge-5, to-4)\n" +
               "advcl(Who-1, judge-5)\n"},
             {TestType.BASIC,
              "(ROOT (S (NP (NNP Bill)) (VP (VBZ is) (NP (DT an) (JJ honest) (NN man))) (. .)))",
              "nsubj(man-5, Bill-1)\n" +
               "cop(man-5, is-2)\n" +
               "det(man-5, an-3)\n" +
               "amod(man-5, honest-4)\n" +
               "root(ROOT-0, man-5)\n"},
             {TestType.NON_COLLAPSED,
              "(ROOT (S (NP (NNP Bill)) (VP (VBZ is) (NP (DT an) (JJ honest) (NN man))) (. .)))",
              "nsubj(man-5, Bill-1)\n" +
               "cop(man-5, is-2)\n" +
               "det(man-5, an-3)\n" +
               "amod(man-5, honest-4)\n" +
               "root(ROOT-0, man-5)\n"},
             {TestType.BASIC,
              "(ROOT (SBARQ (WHNP (WP What) (NN dignity) ) (SQ (VBZ is) (NP (EX there)) (PP (IN in) (NP (DT that) ))) (. ?)))",
              "det(dignity-2, What-1)\n" +
               "nsubj(is-3, dignity-2)\n" +
               "root(ROOT-0, is-3)\n" +
               "expl(is-3, there-4)\n" +
               "case(that-6, in-5)\n" +
               "obl(is-3, that-6)\n"},
             {TestType.NON_COLLAPSED,
              "(ROOT (SBARQ (WHNP (WP What) (NN dignity) ) (SQ (VBZ is) (NP (EX there)) (PP (IN in) (NP (DT that) ))) (. ?)))",
              "det(dignity-2, What-1)\n" +
               "nsubj(is-3, dignity-2)\n" +
               "root(ROOT-0, is-3)\n" +
               "expl(is-3, there-4)\n" +
               "case(that-6, in-5)\n" +
               "obl(is-3, that-6)\n"},
             {TestType.BASIC,
              "(ROOT (S (NP (NN Hand-holding) ) (VP (VBZ is) (VP (VBG becoming) (NP (DT an) (NN investment-banking) (NN job) (NN requirement) ))) (. .) ))",
              "nsubj(becoming-3, Hand-holding-1)\n" +
               "aux(becoming-3, is-2)\n" +
               "root(ROOT-0, becoming-3)\n" +
               "det(requirement-7, an-4)\n" +
               "compound(requirement-7, investment-banking-5)\n" +
               "compound(requirement-7, job-6)\n" +
               "xcomp(becoming-3, requirement-7)\n"},
             {TestType.NON_COLLAPSED,
              "(ROOT (S (NP (NN Hand-holding) ) (VP (VBZ is) (VP (VBG becoming) (NP (DT an) (NN investment-banking) (NN job) (NN requirement) ))) (. .) ))",
              "nsubj(becoming-3, Hand-holding-1)\n" +
               "aux(becoming-3, is-2)\n" +
               "root(ROOT-0, becoming-3)\n" +
               "det(requirement-7, an-4)\n" +
               "compound(requirement-7, investment-banking-5)\n" +
               "compound(requirement-7, job-6)\n" +
               "xcomp(becoming-3, requirement-7)\n"},
             {TestType.BASIC,
              "(ROOT (SBARQ (WHNP (WP What)) (SQ (VBZ is) (ADJP (JJ wrong) (PP (IN with) (S (VP (VBG expecting) (NP (NN pizza))))))) (. ?)))",
              "nsubj(wrong-3, What-1)\n" +
               "cop(wrong-3, is-2)\n" +
               "root(ROOT-0, wrong-3)\n" +
               "mark(expecting-5, with-4)\n" +
               "advcl(wrong-3, expecting-5)\n" +
               "obj(expecting-5, pizza-6)\n"},
             {TestType.NON_COLLAPSED,
              "(ROOT (SBARQ (WHNP (WP What)) (SQ (VBZ is) (ADJP (JJ wrong) (PP (IN with) (S (VP (VBG expecting) (NP (NN pizza))))))) (. ?)))",
              "nsubj(wrong-3, What-1)\n" +
               "cop(wrong-3, is-2)\n" +
               "root(ROOT-0, wrong-3)\n" +
               "mark(expecting-5, with-4)\n" +
               "advcl(wrong-3, expecting-5)\n" +
               "obj(expecting-5, pizza-6)\n"},
             {TestType.BASIC,
              "(ROOT (SBARQ (WHNP (WP Who) ) (SQ (VBZ is) (VP (VBG going) (S (VP (TO to) (VP (VB carry) (NP (DT the) (NN water) )))))) (. ?)))",
              "nsubj(going-3, Who-1)\n" +
               "aux(going-3, is-2)\n" +
               "root(ROOT-0, going-3)\n" +
               "mark(carry-5, to-4)\n" +
               "xcomp(going-3, carry-5)\n" +
               "det(water-7, the-6)\n" +
               "obj(carry-5, water-7)\n"},
             {TestType.NON_COLLAPSED,
              "(ROOT (SBARQ (WHNP (WP Who) ) (SQ (VBZ is) (VP (VBG going) (S (VP (TO to) (VP (VB carry) (NP (DT the) (NN water) )))))) (. ?)))",
              "nsubj(going-3, Who-1)\n" +
               "nsubj:xsubj(carry-5, Who-1)\n" +
               "aux(going-3, is-2)\n" +
               "root(ROOT-0, going-3)\n" +
               "mark(carry-5, to-4)\n" +
               "xcomp(going-3, carry-5)\n" +
               "det(water-7, the-6)\n" +
               "obj(carry-5, water-7)\n"},
             {TestType.BASIC,
              "(ROOT (SBARQ (WHNP (WP What)) (SQ (VBP am) (NP (PRP I)) (VP (VBG doing) (S (VP (VBG dating) (NP (PRP her)))))) (. ?)))",
              "obj(doing-4, What-1)\n" +
               "aux(doing-4, am-2)\n" +
               "nsubj(doing-4, I-3)\n" +
               "root(ROOT-0, doing-4)\n" +
               "advcl(doing-4, dating-5)\n" +
               "obj(dating-5, her-6)\n"},
             {TestType.NON_COLLAPSED,
              "(ROOT (SBARQ (WHNP (WP What)) (SQ (VBP am) (NP (PRP I)) (VP (VBG doing) (S (VP (VBG dating) (NP (PRP her)))))) (. ?)))",
              "obj(doing-4, What-1)\n" +
               "aux(doing-4, am-2)\n" +
               "nsubj(doing-4, I-3)\n" +
               "root(ROOT-0, doing-4)\n" +
               "advcl(doing-4, dating-5)\n" +
               "obj(dating-5, her-6)\n"},
             {TestType.BASIC,
              "(ROOT (SBARQ (WHNP (WP What)) (SQ (VBZ is) (NP (DT that))) (. ?)))",
              "root(ROOT-0, What-1)\n" +
               "cop(What-1, is-2)\n" +
               "nsubj(What-1, that-3)\n"},
             {TestType.NON_COLLAPSED,
              "(ROOT (SBARQ (WHNP (WP What)) (SQ (VBZ is) (NP (DT that))) (. ?)))",
              "root(ROOT-0, What-1)\n" +
               "cop(What-1, is-2)\n" +
               "nsubj(What-1, that-3)\n"},
             {TestType.BASIC,
              "(ROOT (SBARQ (WHNP (WP Who)) (SQ (VBZ is) (NP (NNP John))) (. ?)))",
              "root(ROOT-0, Who-1)\n" +
               "cop(Who-1, is-2)\n" +
               "nsubj(Who-1, John-3)\n"},
             {TestType.NON_COLLAPSED,
              "(ROOT (SBARQ (WHNP (WP Who)) (SQ (VBZ is) (NP (NNP John))) (. ?)))",
              "root(ROOT-0, Who-1)\n" +
               "cop(Who-1, is-2)\n" +
               "nsubj(Who-1, John-3)\n"},
             {TestType.BASIC,
              "(ROOT (SBARQ (WHNP (WDT What) (NN dog)) (SQ (VP (VBZ is) (VP (VBG barking) (ADVP (RB so) (RB loudly))))) (. ?)))",
              "det(dog-2, What-1)\n" +
               "nsubj(barking-4, dog-2)\n" +
               "aux(barking-4, is-3)\n" +
               "root(ROOT-0, barking-4)\n" +
               "advmod(loudly-6, so-5)\n" +
               "advmod(barking-4, loudly-6)\n"},
             {TestType.NON_COLLAPSED,
              "(ROOT (SBARQ (WHNP (WDT What) (NN dog)) (SQ (VP (VBZ is) (VP (VBG barking) (ADVP (RB so) (RB loudly))))) (. ?)))",
              "det(dog-2, What-1)\n" +
               "nsubj(barking-4, dog-2)\n" +
               "aux(barking-4, is-3)\n" +
               "root(ROOT-0, barking-4)\n" +
               "advmod(loudly-6, so-5)\n" +
               "advmod(barking-4, loudly-6)\n"},
             {TestType.BASIC,
              "(ROOT (SBARQ (WHNP (WP Who)) (SQ (VP (VBZ is) (VP (VBG barking) (ADVP (RB so) (RB much))))) (. ?)))",
              "nsubj(barking-3, Who-1)\n" +
               "aux(barking-3, is-2)\n" +
               "root(ROOT-0, barking-3)\n" +
               "advmod(much-5, so-4)\n" +
               "advmod(barking-3, much-5)\n"},
             {TestType.NON_COLLAPSED,
              "(ROOT (SBARQ (WHNP (WP Who)) (SQ (VP (VBZ is) (VP (VBG barking) (ADVP (RB so) (RB much))))) (. ?)))",
              "nsubj(barking-3, Who-1)\n" +
               "aux(barking-3, is-2)\n" +
               "root(ROOT-0, barking-3)\n" +
               "advmod(much-5, so-4)\n" +
               "advmod(barking-3, much-5)\n"},
             {TestType.BASIC,
              "(ROOT (SBARQ (WHADVP (WRB Why)) (SQ (VBZ is) (NP (NNP Dave)) (VP (VBG becoming) (NP (DT a) (NN problem)))) (. ?)))",
              "advmod(becoming-4, Why-1)\n" +
               "aux(becoming-4, is-2)\n" +
               "nsubj(becoming-4, Dave-3)\n" +
               "root(ROOT-0, becoming-4)\n" +
               "det(problem-6, a-5)\n" +
               "xcomp(becoming-4, problem-6)\n"},
             {TestType.NON_COLLAPSED,
              "(ROOT (SBARQ (WHADVP (WRB Why)) (SQ (VBZ is) (NP (NNP Dave)) (VP (VBG becoming) (NP (DT a) (NN problem)))) (. ?)))",
              "advmod(becoming-4, Why-1)\n" +
               "aux(becoming-4, is-2)\n" +
               "nsubj(becoming-4, Dave-3)\n" +
               "root(ROOT-0, becoming-4)\n" +
               "det(problem-6, a-5)\n" +
               "xcomp(becoming-4, problem-6)\n"},
             {TestType.BASIC,
              "(ROOT (SBARQ (WHNP (WP What)) (SQ (VBZ is) (NP (NNP UAL) (NN stock) ) (ADJP (NN worth) )) (. ?)))",
              "obj(worth-5, What-1)\n" +
               "cop(worth-5, is-2)\n" +
               "compound(stock-4, UAL-3)\n" +
               "nsubj(worth-5, stock-4)\n" +
               "root(ROOT-0, worth-5)\n"},
             {TestType.NON_COLLAPSED,
              "(ROOT (SBARQ (WHNP (WP What)) (SQ (VBZ is) (NP (NNP UAL) (NN stock) ) (ADJP (NN worth) )) (. ?)))",
              "obj(worth-5, What-1)\n" +
               "cop(worth-5, is-2)\n" +
               "compound(stock-4, UAL-3)\n" +
               "nsubj(worth-5, stock-4)\n" +
               "root(ROOT-0, worth-5)\n"},
             {TestType.BASIC,
              "(ROOT (SBARQ (WHNP (WP Who)) (SQ (VBP am) (NP (PRP I)) ) (. ?)))",
              "root(ROOT-0, Who-1)\n" +
               "cop(Who-1, am-2)\n" +
               "nsubj(Who-1, I-3)\n"},
             {TestType.NON_COLLAPSED,
              "(ROOT (SBARQ (WHNP (WP Who)) (SQ (VBP am) (NP (PRP I)) ) (. ?)))",
              "root(ROOT-0, Who-1)\n" +
               "cop(Who-1, am-2)\n" +
               "nsubj(Who-1, I-3)\n"},
             {TestType.BASIC,
              "(ROOT (SBARQ (WHNP (WP Who)) (SQ (VP (VBD told) (NP (PRP him)))) (. ?)))",
              "nsubj(told-2, Who-1)\n" +
               "root(ROOT-0, told-2)\n" +
               "obj(told-2, him-3)\n"},
             {TestType.NON_COLLAPSED,
              "(ROOT (SBARQ (WHNP (WP Who)) (SQ (VP (VBD told) (NP (PRP him)))) (. ?)))",
              "nsubj(told-2, Who-1)\n" +
               "root(ROOT-0, told-2)\n" +
               "obj(told-2, him-3)\n"},
             {TestType.BASIC,
              "(ROOT (S (NP (NNP Sue)) (VP (VBZ is) (NP (DT a) (NN lawyer))) (. .)))",
              "nsubj(lawyer-4, Sue-1)\n" +
               "cop(lawyer-4, is-2)\n" +
               "det(lawyer-4, a-3)\n" +
               "root(ROOT-0, lawyer-4)\n"},
             {TestType.NON_COLLAPSED,
              "(ROOT (S (NP (NNP Sue)) (VP (VBZ is) (NP (DT a) (NN lawyer))) (. .)))",
              "nsubj(lawyer-4, Sue-1)\n" +
               "cop(lawyer-4, is-2)\n" +
               "det(lawyer-4, a-3)\n" +
               "root(ROOT-0, lawyer-4)\n"},
             {TestType.BASIC,
              "(ROOT (S (NP (NNP Sue)) (VP (VBZ is) (ADJP (JJ intelligent))) (. .)))",
              "nsubj(intelligent-3, Sue-1)\n" +
               "cop(intelligent-3, is-2)\n" +
               "root(ROOT-0, intelligent-3)\n"},
             {TestType.NON_COLLAPSED,
              "(ROOT (S (NP (NNP Sue)) (VP (VBZ is) (ADJP (JJ intelligent))) (. .)))",
              "nsubj(intelligent-3, Sue-1)\n" +
               "cop(intelligent-3, is-2)\n" +
               "root(ROOT-0, intelligent-3)\n"},
             {TestType.BASIC,
              "(ROOT (SBARQ (WHNP (WP Who)) (SQ (VBZ is) (ADJP (JJ nervous))) (. ?)))",
              "nsubj(nervous-3, Who-1)\n" +
               "cop(nervous-3, is-2)\n" +
               "root(ROOT-0, nervous-3)\n"},
             {TestType.NON_COLLAPSED,
              "(ROOT (SBARQ (WHNP (WP Who)) (SQ (VBZ is) (ADJP (JJ nervous))) (. ?)))",
              "nsubj(nervous-3, Who-1)\n" +
               "cop(nervous-3, is-2)\n" +
               "root(ROOT-0, nervous-3)\n"},
             {TestType.BASIC,
              "(ROOT (S (NP (EX There)) (VP (VBZ is) (NP (NP (DT a) (NN cow))) (PP (IN in) (NP (DT the) (NN field)))) (. .)))",
              "expl(is-2, There-1)\n" +
               "root(ROOT-0, is-2)\n" +
               "det(cow-4, a-3)\n" +
               "nsubj(is-2, cow-4)\n" +
               "case(field-7, in-5)\n" +
               "det(field-7, the-6)\n" +
               "obl(is-2, field-7)\n"},
             {TestType.NON_COLLAPSED,
              "(ROOT (S (NP (EX There)) (VP (VBZ is) (NP (NP (DT a) (NN cow))) (PP (IN in) (NP (DT the) (NN field)))) (. .)))",
              "expl(is-2, There-1)\n" +
               "root(ROOT-0, is-2)\n" +
               "det(cow-4, a-3)\n" +
               "nsubj(is-2, cow-4)\n" +
               "case(field-7, in-5)\n" +
               "det(field-7, the-6)\n" +
               "obl(is-2, field-7)\n"},
             {TestType.BASIC,
              "(ROOT (SBARQ (WHNP (WP What)) (SQ (VBZ is) (NP (EX there)) (PP (IN in) (NP (DT the) (NN field)))) (. ?)))",
              "nsubj(is-2, What-1)\n" +
               "root(ROOT-0, is-2)\n" +
               "expl(is-2, there-3)\n" +
               "case(field-6, in-4)\n" +
               "det(field-6, the-5)\n" +
               "obl(is-2, field-6)\n"},
             {TestType.NON_COLLAPSED,
              "(ROOT (SBARQ (WHNP (WP What)) (SQ (VBZ is) (NP (EX there)) (PP (IN in) (NP (DT the) (NN field)))) (. ?)))",
              "nsubj(is-2, What-1)\n" +
               "root(ROOT-0, is-2)\n" +
               "expl(is-2, there-3)\n" +
               "case(field-6, in-4)\n" +
               "det(field-6, the-5)\n" +
               "obl(is-2, field-6)\n"},
             {TestType.BASIC,
              "(ROOT (SINV (ADVP (RB Here)) (VP (VBP are)) (NP (DT some) (NNS bags))))",
              "advmod(are-2, Here-1)\n" +
               "root(ROOT-0, are-2)\n" +
               "det(bags-4, some-3)\n" +
               "nsubj(are-2, bags-4)\n"},
             {TestType.NON_COLLAPSED,
              "(ROOT (SINV (ADVP (RB Here)) (VP (VBP are)) (NP (DT some) (NNS bags))))",
              "advmod(are-2, Here-1)\n" +
               "root(ROOT-0, are-2)\n" +
               "det(bags-4, some-3)\n" +
               "nsubj(are-2, bags-4)\n"},
             {TestType.BASIC,
              "(ROOT (S (NP (PRP He)) (VP (VBZ is) (PP (IN in) (NP (DT the) (NN garden))))))",
              "nsubj(garden-5, He-1)\n" +
               "cop(garden-5, is-2)\n" +
               "case(garden-5, in-3)\n" +
               "det(garden-5, the-4)\n" +
               "root(ROOT-0, garden-5)\n"},
             {TestType.NON_COLLAPSED,
              "(ROOT (S (NP (PRP He)) (VP (VBZ is) (PP (IN in) (NP (DT the) (NN garden))))))",
              "nsubj(garden-5, He-1)\n" +
               "cop(garden-5, is-2)\n" +
               "case(garden-5, in-3)\n" +
               "det(garden-5, the-4)\n" +
               "root(ROOT-0, garden-5)\n"},
             {TestType.BASIC,
              "(ROOT (SBARQ (WHNP (WP What)) (SQ (VBZ 's) (PP (IN on) (NP (DT the) (NN test)))) (. ?)))",
              "nsubj('s-2, What-1)\n" +
               "root(ROOT-0, 's-2)\n" +
               "case(test-5, on-3)\n" +
               "det(test-5, the-4)\n" +
               "obl('s-2, test-5)\n"},
             {TestType.NON_COLLAPSED,
              "(ROOT (SBARQ (WHNP (WP What)) (SQ (VBZ 's) (PP (IN on) (NP (DT the) (NN test)))) (. ?)))",
              "nsubj('s-2, What-1)\n" +
               "root(ROOT-0, 's-2)\n" +
               "case(test-5, on-3)\n" +
               "det(test-5, the-4)\n" +
               "obl('s-2, test-5)\n"},
             {TestType.BASIC,
              "(ROOT (SBARQ (WHADVP (WRB Why)) (SQ (VBZ is) (NP (DT the) (NN dog)) (ADJP (JJ pink))) (. ?)))",
              "advmod(pink-5, Why-1)\n" +
               "cop(pink-5, is-2)\n" +
               "det(dog-4, the-3)\n" +
               "nsubj(pink-5, dog-4)\n" +
               "root(ROOT-0, pink-5)\n"},
             {TestType.NON_COLLAPSED,
              "(ROOT (SBARQ (WHADVP (WRB Why)) (SQ (VBZ is) (NP (DT the) (NN dog)) (ADJP (JJ pink))) (. ?)))",
              "advmod(pink-5, Why-1)\n" +
               "cop(pink-5, is-2)\n" +
               "det(dog-4, the-3)\n" +
               "nsubj(pink-5, dog-4)\n" +
               "root(ROOT-0, pink-5)\n"},
             {TestType.BASIC,
              "(ROOT (S (NP (DT The) (NN dog)) (VP (VBZ is) (ADJP (JJ pink))) (. .)))",
              "det(dog-2, The-1)\n" +
               "nsubj(pink-4, dog-2)\n" +
               "cop(pink-4, is-3)\n" +
               "root(ROOT-0, pink-4)\n"},
             {TestType.NON_COLLAPSED,
              "(ROOT (S (NP (DT The) (NN dog)) (VP (VBZ is) (ADJP (JJ pink))) (. .)))",
              "det(dog-2, The-1)\n" +
               "nsubj(pink-4, dog-2)\n" +
               "cop(pink-4, is-3)\n" +
               "root(ROOT-0, pink-4)\n"},
             // This tree is incorrect, but we added a rule to cover it so
             // parsers which get this incorrect result (that is, the Charniak/Brown parser) don't get bad
             // dependencies
             {TestType.BASIC,
              "(ROOT (SBARQ (WHNP (WDT What) (NN disease)) (SQ (VP (VBZ causes) (NP (NN pain)))) (. ?)))",
              "det(disease-2, What-1)\n" +
               "nsubj(causes-3, disease-2)\n" +
               "root(ROOT-0, causes-3)\n" +
               "obj(causes-3, pain-4)\n"},
             {TestType.NON_COLLAPSED,
              "(ROOT (SBARQ (WHNP (WDT What) (NN disease)) (SQ (VP (VBZ causes) (NP (NN pain)))) (. ?)))",
              "det(disease-2, What-1)\n" +
               "nsubj(causes-3, disease-2)\n" +
               "root(ROOT-0, causes-3)\n" +
               "obj(causes-3, pain-4)\n"},
             {TestType.BASIC,
              "(ROOT (SBARQ (WHNP (WDT What) (NN disease)) (SQ (VBZ causes) (NP (NN pain))) (. ?)))",
              "det(disease-2, What-1)\n" +
               "nsubj(causes-3, disease-2)\n" +
               "root(ROOT-0, causes-3)\n" +
               "obj(causes-3, pain-4)\n"},
             {TestType.NON_COLLAPSED,
              "(ROOT (SBARQ (WHNP (WDT What) (NN disease)) (SQ (VBZ causes) (NP (NN pain))) (. ?)))",
              "det(disease-2, What-1)\n" +
               "nsubj(causes-3, disease-2)\n" +
               "root(ROOT-0, causes-3)\n" +
               "obj(causes-3, pain-4)\n"},
             {TestType.BASIC,
              "(ROOT (S (VP (VB Be) (VP (VBG waiting) (PP (IN in) (NP (NN line))) (PP-TMP (IN at) (NP (CD 3) (NN p.m.))))) (. !)))",
              "aux(waiting-2, Be-1)\n" +
               "root(ROOT-0, waiting-2)\n" +
               "case(line-4, in-3)\n" +
               "obl(waiting-2, line-4)\n" +
               "case(p.m.-7, at-5)\n" +
               "nummod(p.m.-7, 3-6)\n" +
               "obl(waiting-2, p.m.-7)\n"},
             {TestType.NON_COLLAPSED,
              "(ROOT (S (VP (VB Be) (VP (VBG waiting) (PP (IN in) (NP (NN line))) (PP-TMP (IN at) (NP (CD 3) (NN p.m.))))) (. !)))",
              "aux(waiting-2, Be-1)\n" +
               "root(ROOT-0, waiting-2)\n" +
               "case(line-4, in-3)\n" +
               "obl(waiting-2, line-4)\n" +
               "case(p.m.-7, at-5)\n" +
               "nummod(p.m.-7, 3-6)\n" +
               "obl(waiting-2, p.m.-7)\n"},
             {TestType.BASIC,
              "(ROOT (S (VP (VB Be) (NP (DT a) (NN man))) (. !)))",
              "cop(man-3, Be-1)\n" +
               "det(man-3, a-2)\n" +
               "root(ROOT-0, man-3)\n"},
             {TestType.NON_COLLAPSED,
              "(ROOT (S (VP (VB Be) (NP (DT a) (NN man))) (. !)))",
              "cop(man-3, Be-1)\n" +
               "det(man-3, a-2)\n" +
               "root(ROOT-0, man-3)\n"},
             {TestType.BASIC,
              "(ROOT (SBARQ (RB So) (WHNP (WP what)) (SQ (VBZ is) (NP (NNP Santa) (NNP Fe) ) (ADJP (IN worth) )) (. ?) ))",
              "advmod(worth-6, So-1)\n" +
               "obj(worth-6, what-2)\n" +
               "cop(worth-6, is-3)\n" +
               "compound(Fe-5, Santa-4)\n" +
               "nsubj(worth-6, Fe-5)\n" +
               "root(ROOT-0, worth-6)\n"},
             {TestType.NON_COLLAPSED,
              "(ROOT (SBARQ (RB So) (WHNP (WP what)) (SQ (VBZ is) (NP (NNP Santa) (NNP Fe) ) (ADJP (IN worth) )) (. ?) ))",
              "advmod(worth-6, So-1)\n" +
               "obj(worth-6, what-2)\n" +
               "cop(worth-6, is-3)\n" +
               "compound(Fe-5, Santa-4)\n" +
               "nsubj(worth-6, Fe-5)\n" +
               "root(ROOT-0, worth-6)\n"},
             {TestType.BASIC,
              "(ROOT (SBARQ (WHNP (WP What)) (SQ (VBZ is) (NP (NP (PRP$ your) (NN sister) (POS 's)) (NN name))) (. ?)))",
              "root(ROOT-0, What-1)\n" +
               "cop(What-1, is-2)\n" +
               "nmod:poss(sister-4, your-3)\n" +
               "nmod:poss(name-6, sister-4)\n" +
               "case(sister-4, 's-5)\n" +
               "nsubj(What-1, name-6)\n"},
             {TestType.NON_COLLAPSED,
              "(ROOT (SBARQ (WHNP (WP What)) (SQ (VBZ is) (NP (NP (PRP$ your) (NN sister) (POS 's)) (NN name))) (. ?)))",
              "root(ROOT-0, What-1)\n" +
               "cop(What-1, is-2)\n" +
               "nmod:poss(sister-4, your-3)\n" +
               "nmod:poss(name-6, sister-4)\n" +
               "case(sister-4, 's-5)\n" +
               "nsubj(What-1, name-6)\n"},
             {TestType.BASIC,
              "(ROOT (SBARQ (WHNP (WP What)) (SQ (VBZ is) (NP (NP (DT the) (NN fear)) (PP (IN of) (NP (NNS cockroaches)))) (VP (VBN called))) (. ?)))",
              "obj(called-7, What-1)\n" +
               "aux:pass(called-7, is-2)\n" +
               "det(fear-4, the-3)\n" +
               "nsubj:pass(called-7, fear-4)\n" +
               "case(cockroaches-6, of-5)\n" +
               "nmod(fear-4, cockroaches-6)\n" +
               "root(ROOT-0, called-7)\n"},
             {TestType.NON_COLLAPSED,
              "(ROOT (SBARQ (WHNP (WP What)) (SQ (VBZ is) (NP (NP (DT the) (NN fear)) (PP (IN of) (NP (NNS cockroaches)))) (VP (VBN called))) (. ?)))",
              "obj(called-7, What-1)\n" +
               "aux:pass(called-7, is-2)\n" +
               "det(fear-4, the-3)\n" +
               "nsubj:pass(called-7, fear-4)\n" +
               "case(cockroaches-6, of-5)\n" +
               "nmod(fear-4, cockroaches-6)\n" +
               "root(ROOT-0, called-7)\n"},


             /* Basic relations with copulae being the head. */
             {TestType.COPULA_HEAD,
               "(ROOT (S (NP (NNP Reagan)) (VP (VBZ has) (VP (VBN died))) (. .)))",
               "nsubj(died-3, Reagan-1)\n" +
                "aux(died-3, has-2)\n" +
                "root(ROOT-0, died-3)\n"},
              {TestType.COPULA_HEAD,
               "(ROOT (S (NP (NNP Kennedy)) (VP (VBZ has) (VP (VBN been) (VP (VBN killed)))) (. .)))",
               "nsubj:pass(killed-4, Kennedy-1)\n" +
                "aux(killed-4, has-2)\n" +
                "aux:pass(killed-4, been-3)\n" +
                "root(ROOT-0, killed-4)\n"},
              {TestType.COPULA_HEAD,
               "(ROOT (S (NP (NNP Bill)) (VP (VBZ is) (NP (DT an) (JJ honest) (NN man))) (. .)))",
               "nsubj(is-2, Bill-1)\n" +
                "root(ROOT-0, is-2)\n" +
                "det(man-5, an-3)\n" +
                "amod(man-5, honest-4)\n" +
                "xcomp(is-2, man-5)\n"},
              {TestType.COPULA_HEAD,
               "(ROOT (S (NP (NNP Bill)) (VP (VBZ is) (ADJP (JJ big) (CC and) (JJ honest))) (. .)))",
               "nsubj(is-2, Bill-1)\n" +
                "root(ROOT-0, is-2)\n" +
                "xcomp(is-2, big-3)\n" +
                "cc(honest-5, and-4)\n" +
                "conj(big-3, honest-5)\n"},
              {TestType.COPULA_HEAD,
               "(ROOT (S (NP (NNP Clinton)) (VP (VBD defeated) (NP (NNP Dole))) (. .)))",
               "nsubj(defeated-2, Clinton-1)\n" +
                "root(ROOT-0, defeated-2)\n" +
                "obj(defeated-2, Dole-3)\n"},
              {TestType.COPULA_HEAD,
               "(ROOT (S (SBAR (WHNP (WP What)) (S (NP (PRP she)) (VP (VBD said)))) (VP (VBZ is) (ADJP (JJ untrue))) (. .)))",
               "obj(said-3, What-1)\n" +
                "nsubj(said-3, she-2)\n" +
                "csubj(is-4, said-3)\n" +
                "root(ROOT-0, is-4)\n" +
                "xcomp(is-4, untrue-5)\n"},
              {TestType.COPULA_HEAD,
               "(ROOT (S (NP (NNP Dole)) (VP (VBD was) (VP (VBN defeated) (PP (IN by) (NP (NNP Clinton))))) (. .)))",
               "nsubj:pass(defeated-3, Dole-1)\n" +
                "aux:pass(defeated-3, was-2)\n" +
                "root(ROOT-0, defeated-3)\n" +
                "case(Clinton-5, by-4)\n" +
                "obl(defeated-3, Clinton-5)\n"},
              {TestType.COPULA_HEAD,
               "(ROOT (S (NP (PRP I)) (VP (VBP like) (S (VP (TO to) (VP (VB swim))))) (. .)))",
               "nsubj(like-2, I-1)\n" +
                "root(ROOT-0, like-2)\n" +
                "mark(swim-4, to-3)\n" +
                "xcomp(like-2, swim-4)\n"},
              {TestType.COPULA_HEAD,
               "(ROOT (S (NP (PRP We)) (VP (VBP have) (NP (NP (DT no) (JJ useful) (NN information)) (PP (IN on) (SBAR (IN whether) (S (NP (NNS users)) (VP (VBP are) (PP (IN at) (NP (NN risk))))))))) (. .)))",
               "nsubj(have-2, We-1)\n" +
                "root(ROOT-0, have-2)\n" +
                "det(information-5, no-3)\n" +
                "amod(information-5, useful-4)\n" +
                "obj(have-2, information-5)\n" +
                "mark(are-9, on-6)\n" +
                "mark(are-9, whether-7)\n" +
                "nsubj(are-9, users-8)\n" +
                "acl(information-5, are-9)\n" +
                "case(risk-11, at-10)\n" +
                "obl(are-9, risk-11)\n"},
              {TestType.COPULA_HEAD,
               "(ROOT (S (NP (PRP I)) (VP (VBP am) (ADJP (JJ certain) (SBAR (IN that) (S (NP (PRP he)) (VP (VBD did) (NP (PRP it))))))) (. .)))",
               "nsubj(am-2, I-1)\n" +
                "root(ROOT-0, am-2)\n" +
                "xcomp(am-2, certain-3)\n" +
                "mark(did-6, that-4)\n" +
                "nsubj(did-6, he-5)\n" +
                "ccomp(certain-3, did-6)\n" +
                "obj(did-6, it-7)\n"},
              {TestType.COPULA_HEAD,
               "(ROOT (S (NP (PRP I)) (VP (VBP am) (ADJP (JJ ready) (S (VP (TO to) (VP (VB leave)))))) (. .)))",
               "nsubj(am-2, I-1)\n" +
                "root(ROOT-0, am-2)\n" +
                "xcomp(am-2, ready-3)\n" +
                "mark(leave-5, to-4)\n" +
                "xcomp(ready-3, leave-5)\n"},
              {TestType.COPULA_HEAD,
               "(ROOT (S (NP (EX There)) (VP (VBZ is) (NP (NP (DT a) (NN statue)) (PP (IN in) (NP (DT the) (NN corner))))) (. .)))",
               "expl(is-2, There-1)\n" +
                "root(ROOT-0, is-2)\n" +
                "det(statue-4, a-3)\n" +
                "nsubj(is-2, statue-4)\n" +
                "case(corner-7, in-5)\n" +
                "det(corner-7, the-6)\n" +
                "nmod(statue-4, corner-7)\n"},
              {TestType.COPULA_HEAD,
               "(ROOT (S (NP (DT The) (NN director)) (VP (VBZ is) (ADJP (NP (CD 65) (NNS years)) (JJ old))) (. .)))",
               "det(director-2, The-1)\n" +
                "nsubj(is-3, director-2)\n" +
                "root(ROOT-0, is-3)\n" +
                "nummod(years-5, 65-4)\n" +
                "obl:npmod(old-6, years-5)\n" +
                "xcomp(is-3, old-6)\n"},
              {TestType.COPULA_HEAD,
               "(ROOT (S (NP (DT The) (NN man)) (VP (VBZ is) (ADVP (RB here))) (. .)))",
               "det(man-2, The-1)\n" +
                "nsubj(is-3, man-2)\n" +
                "root(ROOT-0, is-3)\n" +
                "advmod(is-3, here-4)\n"},
              {TestType.COPULA_HEAD,
               "(ROOT (S (NP (NNP Xml) (NN field)) (VP (MD should) (VP (VB include) (NP (PDT both) (NP (DT the) (NN entity) (NN id)) (CC and) (NP (DT the) (NN entity) (NN name))) (SBAR (IN since) (S (NP (DT the) (NN entity) (NNS names)) (VP (VBP are) (RB not) (ADJP (JJ unique))))))) (. .)))",
               "compound(field-2, Xml-1)\n" +
                "nsubj(include-4, field-2)\n" +
                "aux(include-4, should-3)\n" +
                "root(ROOT-0, include-4)\n" +
                "cc:preconj(id-8, both-5)\n" +
                "det(id-8, the-6)\n" +
                "compound(id-8, entity-7)\n" +
                "obj(include-4, id-8)\n" +
                "cc(name-12, and-9)\n" +
                "det(name-12, the-10)\n" +
                "compound(name-12, entity-11)\n" +
                "conj(id-8, name-12)\n" +
                "mark(are-17, since-13)\n" +
                "det(names-16, the-14)\n" +
                "compound(names-16, entity-15)\n" +
                "nsubj(are-17, names-16)\n" +
                "advcl(include-4, are-17)\n" +
                "advmod(are-17, not-18)\n" +
                "xcomp(are-17, unique-19)\n"},
              {TestType.COPULA_HEAD,
               "(ROOT (S (S (NP (DT The) (NN government)) (VP (VBZ counts) (NP (NN money)) (SBAR (IN as) (S (NP (PRP it)) (VP (VBZ is) (VP (VBN spent))))))) (: ;) (S (NP (NNP Dodge)) (VP (VBZ counts) (NP (NNS contracts)) (SBAR (WHADVP (WRB when)) (S (NP (PRP they)) (VP (VBP are) (VP (VBN awarded))))))) (. .)))",
               "det(government-2, The-1)\n" +
                "nsubj(counts-3, government-2)\n" +
                "root(ROOT-0, counts-3)\n" +
                "obj(counts-3, money-4)\n" +
                "mark(spent-8, as-5)\n" +
                "nsubj:pass(spent-8, it-6)\n" +
                "aux:pass(spent-8, is-7)\n" +
                "advcl(counts-3, spent-8)\n" +
                "nsubj(counts-11, Dodge-10)\n" +
                "parataxis(counts-3, counts-11)\n" +
                "obj(counts-11, contracts-12)\n" +
                "advmod(awarded-16, when-13)\n" +
                "nsubj:pass(awarded-16, they-14)\n" +
                "aux:pass(awarded-16, are-15)\n" +
                "advcl(counts-11, awarded-16)\n"},

              /* Non-collapsed dependencies (with extra dependencies) */
              {TestType.NON_COLLAPSED,
               "(ROOT (S (NP (PRP I)) (VP (VBP like) (S (VP (TO to) (VP (VB swim))))) (. .)))",
               "nsubj(like-2, I-1)\n" +
                "nsubj:xsubj(swim-4, I-1)\n" +
                "root(ROOT-0, like-2)\n" +
                "mark(swim-4, to-3)\n" +
                "xcomp(like-2, swim-4)\n"},
              {TestType.NON_COLLAPSED,
               "(ROOT (S (NP (PRP He)) (VP (VBZ says) (SBAR (IN that) (S (NP (PRP you)) (VP (VBP like) (S (VP (TO to) (VP (VB swim)))))))) (. .)))",
               "nsubj(says-2, He-1)\n" +
                "root(ROOT-0, says-2)\n" +
                "mark(like-5, that-3)\n" +
                "nsubj(like-5, you-4)\n" +
                "nsubj:xsubj(swim-7, you-4)\n" +
                "ccomp(says-2, like-5)\n" +
                "mark(swim-7, to-6)\n" +
                "xcomp(like-5, swim-7)\n"},
              {TestType.NON_COLLAPSED,
               "(ROOT (S (NP (PRP I)) (VP (VBD saw) (NP (NP (DT the) (NN man)) (SBAR (WHNP (WP who)) (S (NP (PRP you)) (VP (VBP love)))))) (. .)))",
               "nsubj(saw-2, I-1)\n" +
                "root(ROOT-0, saw-2)\n" +
                "det(man-4, the-3)\n" +
                "obj(saw-2, man-4)\n" +
                "ref(man-4, who-5)\n" +
                "obj(love-7, who-5)\n" +
                "nsubj(love-7, you-6)\n" +
                "acl:relcl(man-4, love-7)\n"},
              {TestType.NON_COLLAPSED,
               "(ROOT (S (NP (PRP I)) (VP (VBD saw) (NP (NP (DT the) (NN man)) (SBAR (WHNP (WP$ whose) (NP (NN wife))) (S (NP (PRP you)) (VP (VBP love)))))) (. .)))",
               "nsubj(saw-2, I-1)\n" +
                "root(ROOT-0, saw-2)\n" +
                "det(man-4, the-3)\n" +
                "obj(saw-2, man-4)\n" +
                "ref(man-4, whose-5)\n" +
                "nmod:poss(wife-6, whose-5)\n" +
                "obj(love-8, wife-6)\n" +
                "nsubj(love-8, you-7)\n" +
                "acl:relcl(man-4, love-8)\n"},
              {TestType.NON_COLLAPSED,
               "(ROOT (S (NP (PRP I)) (VP (VBD saw) (NP (NP (DT the) (NN book)) (SBAR (WHNP (WDT which)) (S (NP (PRP you)) (VP (VBD bought)))))) (. .)))",
               "nsubj(saw-2, I-1)\n" +
                "root(ROOT-0, saw-2)\n" +
                "det(book-4, the-3)\n" +
                "obj(saw-2, book-4)\n" +
                "ref(book-4, which-5)\n" +
                "obj(bought-7, which-5)\n" +
                "nsubj(bought-7, you-6)\n" +
                "acl:relcl(book-4, bought-7)\n"},
              {TestType.NON_COLLAPSED,
               "(ROOT (SBARQ (WHNP (WP What)) (SQ (VBZ is) (NP (DT the) (NN esophagus)) (VP (VBN used) (PP (IN for)))) (. ?)))",
               "obl(used-5, What-1)\n" +
                "aux:pass(used-5, is-2)\n" +
                "det(esophagus-4, the-3)\n" +
                "nsubj:pass(used-5, esophagus-4)\n" +
                "root(ROOT-0, used-5)\n" +
                "case(What-1, for-6)\n"},
              {TestType.NON_COLLAPSED,
               "(ROOT (S (NP (PRP I)) (VP (VBD saw) (NP (NP (DT the) (NN woman)) (SBAR (WHNP (WP whom)) (S (NP (PRP you)) (VP (VBD gave) (NP (DT the) (NN package)) (PP (TO to))))))) (. .)))",
               "nsubj(saw-2, I-1)\n" +
                "root(ROOT-0, saw-2)\n" +
                "det(woman-4, the-3)\n" +
                "obj(saw-2, woman-4)\n" +
                "ref(woman-4, whom-5)\n" +
                "obl(gave-7, whom-5)\n" +
                "nsubj(gave-7, you-6)\n" +
                "acl:relcl(woman-4, gave-7)\n" +
                "det(package-9, the-8)\n" +
                "obj(gave-7, package-9)\n" +
                "case(whom-5, to-10)\n"},

             /* Test printing of extra dependencies after basic dependencies. */
             {TestType.NON_COLLAPSED_SEPARATOR,
               "(ROOT (S (NP (PRP I)) (VP (VBP like) (S (VP (TO to) (VP (VB swim))))) (. .)))",
               "nsubj(like-2, I-1)\n" +
                "root(ROOT-0, like-2)\n" +
                "mark(swim-4, to-3)\n" +
                "xcomp(like-2, swim-4)\n" +
                "======\n" +
                "nsubj:xsubj(swim-4, I-1)\n"
             },

             /* Test preposition collapsing */
             {TestType.COLLAPSED,
               "(ROOT (S (NP (NNP Lufthansa)) (VP (VBZ flies) (PP (TO to) (CC and) (IN from) (NP (NNP Serbia)))) (. .)))",
               "nsubj(flies-2, Lufthansa-1)\n" +
                "root(ROOT-0, flies-2)\n" +
                "conj:and(flies-2, flies-2')\n" +
                "case(Serbia-6, to-3)\n" +
                "cc(from-5, and-4)\n" +
                "conj:and(to-3, from-5)\n" +
                "obl:from(flies-2', Serbia-6)\n" +
                "obl:to(flies-2, Serbia-6)\n"},
             {TestType.COLLAPSED,
                "(ROOT (S (NP (NNP Lufthansa)) (VP (VBZ flies) (PP (TO to) (CC and) (IN from) (NP (NP (NNP Serbia)) (CC and) (NP (NNP France))))) (. .)))",
                "nsubj(flies-2, Lufthansa-1)\n" +
                "root(ROOT-0, flies-2)\n" +
                "conj:and(flies-2, flies-2')\n" +
                "case(Serbia-6, to-3)\n" +
                "cc(from-5, and-4)\n" +
                "conj:and(to-3, from-5)\n" +
                "obl:from(flies-2', Serbia-6)\n" +
                "obl:to(flies-2, Serbia-6)\n" +
                "cc(France-8, and-7)\n" +
                "conj:and(Serbia-6, France-8)\n"},
            {TestType.COLLAPSED,
              "(ROOT (S (NP (NNP Lufthansa)) (VP (VP (VBZ flies) (PP (IN from) (NP (NNP Serbia)))) (CC and) (VP (VBZ flies) (PP (TO to) (NP (NNP Serbia))))) (. .)))",
              "nsubj(flies-2, Lufthansa-1)\n" +
              "root(ROOT-0, flies-2)\n" +
              "case(Serbia-4, from-3)\n" +
              "obl:from(flies-2, Serbia-4)\n" +
              "cc(flies-6, and-5)\n" +
              "conj:and(flies-2, flies-6)\n" +
              "case(Serbia-8, to-7)\n" +
              "obl:to(flies-6, Serbia-8)\n"},
             {TestType.COLLAPSED,
              "(ROOT (S (NP (NNP Lufthansa)) (VP (VBZ flies) (PP (PP (IN from) (NP (NNP Serbia))) (CC and) (PP (TO to) (NP (NNP France))))) (. .)))",
              "nsubj(flies-2, Lufthansa-1)\n" +
              "root(ROOT-0, flies-2)\n" +
              "conj:and(flies-2, flies-2')\n" +
              "case(Serbia-4, from-3)\n" +
              "obl:from(flies-2, Serbia-4)\n" +
              "cc(flies-2', and-5)\n" +
              "case(France-7, to-6)\n" +
              "obl:to(flies-2', France-7)\n"},
             {TestType.COLLAPSED,
              "(ROOT (S (NP (NNP Lufthansa)) (VP (VBZ flies) (PP (PP (IN from) (NP (NNP Austria))) (, ,) (PP (TO to) (NP (NNP Syria))) (CC and) (PP (IN through) (NP (NNP Serbia))))) (. .)))",
              "nsubj(flies-2, Lufthansa-1)\n" +
              "root(ROOT-0, flies-2)\n" +
              "conj:and(flies-2, flies-2')\n" +
              "conj:and(flies-2, flies-2'')\n" +
              "case(Austria-4, from-3)\n" +
              "obl:from(flies-2, Austria-4)\n" +
              "case(Syria-7, to-6)\n" +
              "obl:to(flies-2', Syria-7)\n" +
              "cc(flies-2'', and-8)\n" +
              "case(Serbia-10, through-9)\n" +
              "obl:through(flies-2'', Serbia-10)\n"},
             {TestType.COLLAPSED,
              "(ROOT (S (NP (NNP Dole)) (VP (VBD was) (VP (VBN defeated) (PP (IN by) (NP (NNP Clinton))))) (. .)))",
              "nsubj:pass(defeated-3, Dole-1)\n" +
               "aux:pass(defeated-3, was-2)\n" +
               "root(ROOT-0, defeated-3)\n" +
               "case(Clinton-5, by-4)\n" +
               "obl:agent(defeated-3, Clinton-5)\n"},
             {TestType.COLLAPSED,
              "(ROOT (S (SBAR (IN That) (S (NP (PRP she)) (VP (VBD lied)))) (VP (VBD was) (VP (VBN suspected) (PP (IN by) (NP (NN everyone))))) (. .)))",
              "mark(lied-3, That-1)\n" +
               "nsubj(lied-3, she-2)\n" +
               "csubj:pass(suspected-5, lied-3)\n" +
               "aux:pass(suspected-5, was-4)\n" +
               "root(ROOT-0, suspected-5)\n" +
               "case(everyone-7, by-6)\n" +
               "obl:agent(suspected-5, everyone-7)\n"},
             {TestType.COLLAPSED,
              "(ROOT (S (NP (PRP I)) (VP (VBP like) (S (VP (TO to) (VP (VB swim))))) (. .)))",
              "nsubj(like-2, I-1)\n" +
               "nsubj:xsubj(swim-4, I-1)\n" +
               "root(ROOT-0, like-2)\n" +
               "mark(swim-4, to-3)\n" +
               "xcomp(like-2, swim-4)\n"},
             {TestType.COLLAPSED,
              "(ROOT (S (NP (PRP I)) (VP (VBD sat) (PP (IN on) (NP (DT the) (NN chair)))) (. .)))",
              "nsubj(sat-2, I-1)\n" +
               "root(ROOT-0, sat-2)\n" +
               "case(chair-5, on-3)\n" +
               "det(chair-5, the-4)\n" +
               "obl:on(sat-2, chair-5)\n"},
             {TestType.COLLAPSED,
              "(ROOT (S (NP (PRP We)) (VP (VBP have) (NP (NP (DT no) (JJ useful) (NN information)) (PP (IN on) (SBAR (IN whether) (S (NP (NNS users)) (VP (VBP are) (PP (IN at) (NP (NN risk))))))))) (. .)))",
              "nsubj(have-2, We-1)\n" +
               "root(ROOT-0, have-2)\n" +
               "det(information-5, no-3)\n" +
               "amod(information-5, useful-4)\n" +
               "obj(have-2, information-5)\n" +
               "mark(risk-11, on-6)\n" +
               "mark(risk-11, whether-7)\n" +
               "nsubj(risk-11, users-8)\n" +
               "cop(risk-11, are-9)\n" +
               "case(risk-11, at-10)\n" +
               "acl(information-5, risk-11)\n"},
             {TestType.COLLAPSED,
              "(ROOT (S (NP (PRP They)) (VP (VBD heard) (PP (IN about) (NP (NN asbestos))) (S (VP (VBG having) (NP (JJ questionable) (NNS properties))))) (. .)))",
              "nsubj(heard-2, They-1)\n" +
               "root(ROOT-0, heard-2)\n" +
               "case(asbestos-4, about-3)\n" +
               "obl:about(heard-2, asbestos-4)\n" +
               "xcomp(heard-2, having-5)\n" +
               "amod(properties-7, questionable-6)\n" +
               "obj(having-5, properties-7)\n"},
             {TestType.COLLAPSED,
              "(ROOT (S (NP (PRP He)) (VP (VBZ says) (SBAR (IN that) (S (NP (PRP you)) (VP (VBP like) (S (VP (TO to) (VP (VB swim)))))))) (. .)))",
              "nsubj(says-2, He-1)\n" +
               "root(ROOT-0, says-2)\n" +
               "mark(like-5, that-3)\n" +
               "nsubj(like-5, you-4)\n" +
               "nsubj:xsubj(swim-7, you-4)\n" +
               "ccomp(says-2, like-5)\n" +
               "mark(swim-7, to-6)\n" +
               "xcomp(like-5, swim-7)\n"},
             {TestType.COLLAPSED,
              "(ROOT (S (NP (NNP U.S.) (NNS forces)) (VP (VBP have) (VP (VBN been) (VP (VBN engaged) (PP (IN in) (NP (JJ intense) (NN fighting))) (SBAR (IN after) (S (NP (NNS insurgents)) (VP (VBD launched) (NP (JJ simultaneous) (NNS attacks)))))))) (. .)))",
              "compound(forces-2, U.S.-1)\n" +
               "nsubj:pass(engaged-5, forces-2)\n" +
               "aux(engaged-5, have-3)\n" +
               "aux:pass(engaged-5, been-4)\n" +
               "root(ROOT-0, engaged-5)\n" +
               "case(fighting-8, in-6)\n" +
               "amod(fighting-8, intense-7)\n" +
               "obl:in(engaged-5, fighting-8)\n" +
               "mark(launched-11, after-9)\n" +
               "nsubj(launched-11, insurgents-10)\n" +
               "advcl(engaged-5, launched-11)\n" +
               "amod(attacks-13, simultaneous-12)\n" +
               "obj(launched-11, attacks-13)\n"},
             {TestType.COLLAPSED,
              "(ROOT (S (NP (PRP I)) (VP (VBD saw) (NP (NP (DT the) (NN man)) (SBAR (WHNP (WP who)) (S (NP (PRP you)) (VP (VBP love)))))) (. .)))",
              "nsubj(saw-2, I-1)\n" +
               "root(ROOT-0, saw-2)\n" +
               "det(man-4, the-3)\n" +
               "obj(saw-2, man-4)\n" +
               "obj(love-7, man-4)\n" +
               "ref(man-4, who-5)\n" +
               "nsubj(love-7, you-6)\n" +
               "acl:relcl(man-4, love-7)\n"},
             {TestType.COLLAPSED,
              "(ROOT (S (NP (PRP I)) (VP (VBD saw) (NP (NP (DT the) (NN man)) (SBAR (WHNP (WP$ whose) (NP (NN wife))) (S (NP (PRP you)) (VP (VBP love)))))) (. .)))",
              "nsubj(saw-2, I-1)\n" +
               "root(ROOT-0, saw-2)\n" +
               "det(man-4, the-3)\n" +
               "obj(saw-2, man-4)\n" +
               "nmod:poss(wife-6, man-4)\n" +
               "ref(man-4, whose-5)\n" +
               "obj(love-8, wife-6)\n" +
               "nsubj(love-8, you-7)\n" +
               "acl:relcl(man-4, love-8)\n"},
             {TestType.COLLAPSED,
              "(ROOT (S (NP (EX There)) (VP (VBZ is) (NP (NP (DT a) (NN statue)) (PP (IN in) (NP (DT the) (NN corner))))) (. .)))",
              "expl(is-2, There-1)\n" +
               "root(ROOT-0, is-2)\n" +
               "det(statue-4, a-3)\n" +
               "nsubj(is-2, statue-4)\n" +
               "case(corner-7, in-5)\n" +
               "det(corner-7, the-6)\n" +
               "nmod:in(statue-4, corner-7)\n"},
             {TestType.COLLAPSED,
              "(ROOT (S (NP (PRP He)) (VP (VBD talked) (PP (TO to) (NP (DT the) (NN president))) (SBAR (IN in) (NN order) (S (VP (TO to) (VP (VB secure) (NP (DT the) (NN account))))))) (. .)))",
              "nsubj(talked-2, He-1)\n" +
               "root(ROOT-0, talked-2)\n" +
               "case(president-5, to-3)\n" +
               "det(president-5, the-4)\n" +
               "obl:to(talked-2, president-5)\n" +
               "mark(secure-9, in-6)\n" +
               "fixed(in-6, order-7)\n" +
               "mark(secure-9, to-8)\n" +
               "advcl(talked-2, secure-9)\n" +
               "det(account-11, the-10)\n" +
               "obj(secure-9, account-11)\n"},
             {TestType.COLLAPSED,
              "(ROOT (S (NP (PRP I)) (VP (VBD saw) (NP (NP (DT the) (NN book)) (SBAR (WHNP (WDT which)) (S (NP (PRP you)) (VP (VBD bought)))))) (. .)))",
              "nsubj(saw-2, I-1)\n" +
               "root(ROOT-0, saw-2)\n" +
               "det(book-4, the-3)\n" +
               "obj(saw-2, book-4)\n" +
               "obj(bought-7, book-4)\n" +
               "ref(book-4, which-5)\n" +
               "nsubj(bought-7, you-6)\n" +
               "acl:relcl(book-4, bought-7)\n"},
             {TestType.COLLAPSED,
              "(ROOT (S (NP (NNP Bill)) (VP (VBD picked) (NP (NP (NNP Fred)) (PP (IN for) (NP (NP (DT the) (NN team)) (VP (VBG demonstrating) (NP (PRP$ his) (NN incompetence))))))) (. .)))",
              "nsubj(picked-2, Bill-1)\n" +
               "root(ROOT-0, picked-2)\n" +
               "obj(picked-2, Fred-3)\n" +
               "case(team-6, for-4)\n" +
               "det(team-6, the-5)\n" +
               "nmod:for(Fred-3, team-6)\n" +
               "acl(team-6, demonstrating-7)\n" +
               "nmod:poss(incompetence-9, his-8)\n" +
               "obj(demonstrating-7, incompetence-9)\n"},
             {TestType.COLLAPSED,
              "(ROOT (SBARQ (WHPP (IN In) (WHNP (WDT which) (NN city))) (SQ (VBP do) (NP (PRP you)) (VP (VB live))) (. ?)))",
              "case(city-3, In-1)\n" +
               "det(city-3, which-2)\n" +
               "obl:in(live-6, city-3)\n" +
               "aux(live-6, do-4)\n" +
               "nsubj(live-6, you-5)\n" +
               "root(ROOT-0, live-6)\n"},
             {TestType.COLLAPSED,
              "(ROOT (SBARQ (WHNP (WP What)) (SQ (VBZ is) (NP (DT the) (NN esophagus)) (VP (VBN used) (PP (IN for)))) (. ?)))",
              "obl:for(used-5, What-1)\n" +
               "aux:pass(used-5, is-2)\n" +
               "det(esophagus-4, the-3)\n" +
               "nsubj:pass(used-5, esophagus-4)\n" +
               "root(ROOT-0, used-5)\n" +
               "case(What-1, for-6)\n"},
             {TestType.COLLAPSED,
              "(ROOT (S (NP (CC Both) (NP (DT the) (NNS boys)) (CC and) (NP (DT the) (NNS girls))) (VP (VBP are) (ADVP (RB here))) (. .)))",
              "cc:preconj(boys-3, Both-1)\n" +
               "det(boys-3, the-2)\n" +
               "nsubj(are-7, boys-3)\n" +
               "cc(girls-6, and-4)\n" +
               "det(girls-6, the-5)\n" +
               "conj:and(boys-3, girls-6)\n" +
               "root(ROOT-0, are-7)\n" +
               "advmod(are-7, here-8)\n"},
             {TestType.COLLAPSED,
              "( (S (NP (NNP Fred)) (VP (VBD flew) (PP (IN across) (CC or) (IN across) (NP (NNP Serbia)))) (. .)))",
              "nsubj(flew-2, Fred-1)\n" +
               "root(ROOT-0, flew-2)\n" +
               "conj:or(flew-2, flew-2')\n" +
               "case(Serbia-6, across-3)\n" +
               "cc(across-5, or-4)\n" +
               "conj:or(across-3, across-5)\n" +
               "obl:across(flew-2, Serbia-6)\n" +
               "obl:across(flew-2', Serbia-6)\n"},
             {TestType.COLLAPSED,
              "(ROOT (SBARQ (WHPP (IN For) (WHNP (WRB how) (JJ long))) (SQ (VBZ is) (NP (DT an) (NN elephant)) (ADJP (JJ pregnant))) (. ?)))",
              "case(long-3, For-1)\n" +
               "advmod(long-3, how-2)\n" +
               "obl:for(pregnant-7, long-3)\n" +
               "cop(pregnant-7, is-4)\n" +
               "det(elephant-6, an-5)\n" +
               "nsubj(pregnant-7, elephant-6)\n" +
               "root(ROOT-0, pregnant-7)\n"},
             {TestType.COLLAPSED,
              "( (S (NP-SBJ-1 (PRP He)) (VP (VBD achieved) (NP (DT this)) (PP-MNR (PP (IN in) (NP (NN part))) (IN through) (NP (DT an) (JJ uncanny) (NN talent)))) (. .)))",
              "nsubj(achieved-2, He-1)\n" +
               "root(ROOT-0, achieved-2)\n" +
               "obj(achieved-2, this-3)\n" +
               "case(part-5, in-4)\n" +
               "obl:in(talent-9, part-5)\n" +
               "case(talent-9, through-6)\n" +
               "det(talent-9, an-7)\n" +
               "amod(talent-9, uncanny-8)\n" +
               "obl:through(achieved-2, talent-9)\n"},
             {TestType.COLLAPSED,
              "(ROOT (S (NP (DT The) (NN turf)) (ADVP (RB recently)) (VP (VBZ has) (VP (VBN ranged) (PP (PP (IN from) (NP (NNP Chile))) (PP (TO to) (NP (NNP Austria))) (PP (TO to) (NP (NNP Portugal)))))) (. .)))",
              "det(turf-2, The-1)\n" +
               "nsubj(ranged-5, turf-2)\n" +
               "advmod(ranged-5, recently-3)\n" +
               "aux(ranged-5, has-4)\n" +
               "root(ROOT-0, ranged-5)\n" +
               "case(Chile-7, from-6)\n" +
               "obl:from(ranged-5, Chile-7)\n" +
               "case(Austria-9, to-8)\n" +
               "obl:to(Chile-7, Austria-9)\n" +
               "case(Portugal-11, to-10)\n" +
               "obl:to(Chile-7, Portugal-11)\n"},
             {TestType.COLLAPSED,
              "( (S (CC But) (NP (NP (NNP Ms.) (NNP Poore)) (, ,) (NP (NP (DT the) (NN magazine) (POS 's)) (NN editor) (CC and) (NN publisher)) (, ,)) (VP (VBD resigned)) (. .)))",
              "cc(resigned-12, But-1)\n" +
               "compound(Poore-3, Ms.-2)\n" +
               "nsubj(resigned-12, Poore-3)\n" +
               "det(magazine-6, the-5)\n" +
               "nmod:poss(editor-8, magazine-6)\n" +
               "case(magazine-6, 's-7)\n" +
               "appos(Poore-3, editor-8)\n" +
               "cc(publisher-10, and-9)\n" +
               "conj:and(editor-8, publisher-10)\n" +
               "root(ROOT-0, resigned-12)\n"},
             {TestType.COLLAPSED,
              "( (S (NP (EX There)) (VP (VBZ 's) (ADVP (RB never)) (VP (VBN been) (NP (DT an) (NN exception)))) (. .)))",
              "expl(exception-6, There-1)\n" +
               "aux:pass(exception-6, 's-2)\n" +
               "advmod(exception-6, never-3)\n" +
               "cop(exception-6, been-4)\n" +
               "det(exception-6, an-5)\n" +
               "root(ROOT-0, exception-6)\n"},
             {TestType.COLLAPSED,
              "( (S (NP (NNP Sotheby) (POS 's)) (PRN (, ,) (S (NP (PRP she)) (VP (VBZ says))) (, ,)) (VP (VBZ is) (VP (`` ``) (VBG wearing) (NP (DT both) (NNS hats)))) (. .) ('' '')))",
              "nsubj(wearing-9, Sotheby-1)\n" +
               "case(Sotheby-1, 's-2)\n" +
               "nsubj(says-5, she-4)\n" +
               "parataxis(wearing-9, says-5)\n" +
               "aux(wearing-9, is-7)\n" +
               "root(ROOT-0, wearing-9)\n" +
               "det(hats-11, both-10)\n" +
               "obj(wearing-9, hats-11)\n"},
             {TestType.COLLAPSED,
              "( (S (NP (NP (JJ Average) (NN maturity)) (PP (IN of) (NP (NP (NML (DT the) (NNS funds)) (POS ')) (NNS investments)))) (VP (VBD lengthened)) (. .)))",
              "amod(maturity-2, Average-1)\n" +
               "nsubj(lengthened-8, maturity-2)\n" +
               "case(investments-7, of-3)\n" +
               "det(funds-5, the-4)\n" +
               "nmod:poss(investments-7, funds-5)\n" +
               "case(funds-5, '-6)\n" +
               "nmod:of(maturity-2, investments-7)\n" +
               "root(ROOT-0, lengthened-8)\n"},


             /* Test for CC processing */
             {TestType.CC_PROCESSED,
              "(ROOT (S (NP (NNP Bill)) (VP (VBZ is) (ADJP (JJ big) (CC and) (JJ honest))) (. .)))",
              "nsubj(big-3, Bill-1)\n" +
               "nsubj(honest-5, Bill-1)\n" +
               "cop(big-3, is-2)\n" +
               "root(ROOT-0, big-3)\n" +
               "cc(honest-5, and-4)\n" +
               "conj:and(big-3, honest-5)\n"},
             {TestType.CC_PROCESSED,
              "(ROOT (S (NP (CC Both) (NP (DT the) (NNS boys)) (CC and) (NP (DT the) (NNS girls))) (VP (VBP are) (ADVP (RB here))) (. .)))",
              "cc:preconj(boys-3, Both-1)\n" +
               "det(boys-3, the-2)\n" +
               "nsubj(are-7, boys-3)\n" +
               "cc(girls-6, and-4)\n" +
               "det(girls-6, the-5)\n" +
               "conj:and(boys-3, girls-6)\n" +
               "nsubj(are-7, girls-6)\n" +
               "root(ROOT-0, are-7)\n" +
               "advmod(are-7, here-8)\n"},
             {TestType.CC_PROCESSED,
              "(ROOT (S (NP-SBJ-38 (DT Neither) (NP (PRP they) ) (CC nor) (NP (NNP Mr.) (NNP McAlpine) )) (VP (MD could) (VP (VB be) (VP (VBN reached) (NP (-NONE- *-38) ) (PP-PRP (IN for) (NP (NN comment) ))))) (. .) ))",
              "cc:preconj(they-2, Neither-1)\n" +
               "nsubj:pass(reached-8, they-2)\n" +
               "cc(McAlpine-5, nor-3)\n" +
               "compound(McAlpine-5, Mr.-4)\n" +
               "conj:nor(they-2, McAlpine-5)\n" +
               "nsubj:pass(reached-8, McAlpine-5)\n" +
               "aux(reached-8, could-6)\n" +
               "aux:pass(reached-8, be-7)\n" +
               "root(ROOT-0, reached-8)\n" +
               "case(comment-10, for-9)\n" +
               "obl:for(reached-8, comment-10)\n"},
             {TestType.CC_PROCESSED,
              "(ROOT (S (NP (NNP John)) (VP (VBZ works) (PP (DT both) (PP (IN in) (NP (NNP Zurich))) (CC and) (PP (IN in) (NP (NNP London))))) (. .)))",
              "nsubj(works-2, John-1)\n" +
               "nsubj(works-2', John-1)\n" +
               "root(ROOT-0, works-2)\n" +
               "conj:and(works-2, works-2')\n" +
               "cc:preconj(Zurich-5, both-3)\n" +
               "case(Zurich-5, in-4)\n" +
               "obl:in(works-2, Zurich-5)\n" +
               "cc(works-2', and-6)\n" +
               "case(London-8, in-7)\n" +
               "obl:in(works-2', London-8)\n"},
             {TestType.CC_PROCESSED,
              "(ROOT (S (NP (NP (NNS Languages)) (PP (PP (IN with) (NP (NNS alphabets))) (CC and) (PP (IN without) (NP (NNS alphabets))))) (VP (VBP are) (ADJP (JJ difficult))) (. .)))",
              "conj:and(Languages-1, Languages-1')\n" +
               "nsubj(difficult-8, Languages-1)\n" +
               "nsubj(difficult-8, Languages-1')\n" +
               "case(alphabets-3, with-2)\n" +
               "nmod:with(Languages-1, alphabets-3)\n" +
               "cc(Languages-1', and-4)\n" +
               "case(alphabets-6, without-5)\n" +
               "nmod:without(Languages-1', alphabets-6)\n" +
               "cop(difficult-8, are-7)\n" +
               "root(ROOT-0, difficult-8)\n"},
             {TestType.CC_PROCESSED,
              "(ROOT (S (NP (PRP$ His) (NN term)) (VP (VP (VBZ has) (VP (VBN produced) (NP (NP (DT no) (JJ spectacular) (NNS failures)) (PP (PP (IN in) (NP (NNS politics))) (, ,) (PP (IN in) (NP (DT the) (NN economy))) (CC or) (PP (IN on) (NP (DT the) (JJ military) (NN front))))))) (, ,) (CC and) (VP (VBZ has) (VP (VBN chalked) (PRT (RP up)) (NP (DT some) (NNS successes))))) (. .)))",
              "nmod:poss(term-2, His-1)\n" +
               "nsubj(produced-4, term-2)\n" +
               "nsubj(chalked-22, term-2)\n" +
               "aux(produced-4, has-3)\n" +
               "root(ROOT-0, produced-4)\n" +
               "det(failures-7, no-5)\n" +
               "amod(failures-7, spectacular-6)\n" +
               "obj(produced-4, failures-7)\n" +
               "obj(produced-4, failures-7')\n" +
               "obj(produced-4, failures-7'')\n" +
               "conj:or(failures-7, failures-7')\n" +
               "conj:or(failures-7, failures-7'')\n" +
               "case(politics-9, in-8)\n" +
               "nmod:in(failures-7, politics-9)\n" +
               "case(economy-13, in-11)\n" +
               "det(economy-13, the-12)\n" +
               "nmod:in(failures-7', economy-13)\n" +
               "cc(failures-7'', or-14)\n" +
               "case(front-18, on-15)\n" +
               "det(front-18, the-16)\n" +
               "amod(front-18, military-17)\n" +
               "nmod:on(failures-7'', front-18)\n" +
               "cc(chalked-22, and-20)\n" +
               "aux(chalked-22, has-21)\n" +
               "conj:and(produced-4, chalked-22)\n" +
               "compound:prt(chalked-22, up-23)\n" +
               "det(successes-25, some-24)\n" +
               "obj(chalked-22, successes-25)\n"},
             {TestType.CC_PROCESSED,
              "(ROOT (S (NP (NNP Fred)) (VP (VBD walked) (PP (PP (IN out) (NP (DT the) (NN door))) (CC and) (PP (RB right) (IN into) (NP (DT a) (NN trap))))) (. .)))",
              "nsubj(walked-2, Fred-1)\n" +
               "nsubj(walked-2', Fred-1)\n" +
               "root(ROOT-0, walked-2)\n" +
               "conj:and(walked-2, walked-2')\n" +
               "case(door-5, out-3)\n" +
               "det(door-5, the-4)\n" +
               "obl:out(walked-2, door-5)\n" +
               "cc(walked-2', and-6)\n" +
               "advmod(trap-10, right-7)\n" +
               "case(trap-10, into-8)\n" +
               "det(trap-10, a-9)\n" +
               "obl:into(walked-2', trap-10)\n"},
             {TestType.CC_PROCESSED,
              "(ROOT (S (NP (NNP Fred)) (VP (VBD walked) (PP (PP (IN into) (NP (DT the) (NN house))) (CC and) (PP (RB right) (IN into) (NP (DT a) (NN trap))))) (. .)))",
              "nsubj(walked-2, Fred-1)\n" +
               "nsubj(walked-2', Fred-1)\n" +
               "root(ROOT-0, walked-2)\n" +
               "conj:and(walked-2, walked-2')\n" +
               "case(house-5, into-3)\n" +
               "det(house-5, the-4)\n" +
               "obl:into(walked-2, house-5)\n" +
               "cc(walked-2', and-6)\n" +
               "advmod(trap-10, right-7)\n" +
               "case(trap-10, into-8)\n" +
               "det(trap-10, a-9)\n" +
               "obl:into(walked-2', trap-10)\n"},
             {TestType.CC_PROCESSED,
              "(ROOT (S (NP (NNP Marie) (CC and) (NNP Chris)) (VP (VP (VBD went) (PRT (RP out))) (, ,) (VP (VBD drank) (NP (NN coffee))) (, ,) (CC and) (VP (VBD talked) (PP (IN about) (NP (NNP Stanford) (NNPS Dependencies))))) (. .)))",
              "nsubj(went-4, Marie-1)\n" +
               "nsubj(drank-7, Marie-1)\n" +
               "nsubj(talked-11, Marie-1)\n" +
               "cc(Chris-3, and-2)\n" +
               "conj:and(Marie-1, Chris-3)\n" +
               "nsubj(went-4, Chris-3)\n" +
               "root(ROOT-0, went-4)\n" +
               "compound:prt(went-4, out-5)\n" +
               "conj:and(went-4, drank-7)\n" +
               "obj(drank-7, coffee-8)\n" +
               "cc(talked-11, and-10)\n" +
               "conj:and(went-4, talked-11)\n" +
               "case(Dependencies-14, about-12)\n" +
               "compound(Dependencies-14, Stanford-13)\n" +
               "obl:about(talked-11, Dependencies-14)\n"},
//             {TestType.CC_PROCESSED,
//              "(ROOT (S (NP-TMP (DT These) (NNS days)) (NP (PRP he)) (VP (VBZ hustles) (PP (TO to) (NP (JJ house-painting) (NNS jobs))) (PP (IN in) (NP (PRP$ his) (NNP Chevy) (NN pickup))) (PP (IN before) (CC and) (IN after) (S (VP (NN training) (PP (IN with) (NP (DT the) (NNPS Tropics))))))) (. .)))",
//              "det(days-2, These-1)\n" +
//               "nmod:tmod(hustles-4, days-2)\n" +
//               "nsubj(hustles-4, he-3)\n" +
//               "nsubj(hustles-4', he-3)\n" +
//               "root(ROOT-0, hustles-4)\n" +
//               "conj:and(hustles-4, hustles-4')\n" +
//               "case(jobs-7, to-5)\n" +
//               "amod(jobs-7, house-painting-6)\n" +
//               "nmod:to(hustles-4, jobs-7)\n" +
//               "case(pickup-11, in-8)\n" +
//               "nmod:poss(pickup-11, his-9)\n" +
//               "compound(pickup-11, Chevy-10)\n" +
//               "nmod:in(hustles-4, pickup-11)\n" +
//               "case(training-15, before-12)\n" +
//               "cc(before-12, and-13)\n" +
//               "conj:and(before-12, after-14)\n" +
//               "advcl:after(hustles-4', training-15)\n" +
//               "advcl:before(hustles-4, training-15)\n" +
//               "case(Tropics-18, with-16)\n" +
//               "det(Tropics-18, the-17)\n" +
//               "nmod:with(training-15, Tropics-18)\n"},
             {TestType.CC_PROCESSED,
              "(ROOT (S (NP (NNP Jill)) (VP (VBD walked) (PP (PP (IN out) (NP (DT the) (NN door))) (, ,) (PP (IN over) (NP (DT the) (NN road))) (, ,) (PP (IN across) (NP (DT the) (JJ deserted) (NN block))) (, ,) (PP (IN around) (NP (DT the) (NN corner))) (, ,) (CC and) (PP (IN through) (NP (DT the) (NN park))))) (. .)))",
              "nsubj(walked-2, Jill-1)\n" +
               "nsubj(walked-2', Jill-1)\n" +
               "nsubj(walked-2'', Jill-1)\n" +
               "nsubj(walked-2''', Jill-1)\n" +
               "nsubj(walked-2'''', Jill-1)\n" +
               "root(ROOT-0, walked-2)\n" +
               "conj:and(walked-2, walked-2')\n" +
               "conj:and(walked-2, walked-2'')\n" +
               "conj:and(walked-2, walked-2''')\n" +
               "conj:and(walked-2, walked-2'''')\n" +
               "case(door-5, out-3)\n" +
               "det(door-5, the-4)\n" +
               "obl:out(walked-2, door-5)\n" +
               "case(road-9, over-7)\n" +
               "det(road-9, the-8)\n" +
               "obl:over(walked-2', road-9)\n" +
               "case(block-14, across-11)\n" +
               "det(block-14, the-12)\n" +
               "amod(block-14, deserted-13)\n" +
               "obl:across(walked-2'', block-14)\n" +
               "case(corner-18, around-16)\n" +
               "det(corner-18, the-17)\n" +
               "obl:around(walked-2''', corner-18)\n" +
               "cc(walked-2'''', and-20)\n" +
               "case(park-23, through-21)\n" +
               "det(park-23, the-22)\n" +
               "obl:through(walked-2'''', park-23)\n"},
             {TestType.CC_PROCESSED,
              "(ROOT (S (NP (NNP John)) (VP (VP (VBD noticed) (NP (DT a) (NN cockroach))) (CC and) (VP (VBD departed))) (. .)))",
              "nsubj(noticed-2, John-1)\n" +
               "nsubj(departed-6, John-1)\n" +
               "root(ROOT-0, noticed-2)\n" +
               "det(cockroach-4, a-3)\n" +
               "obj(noticed-2, cockroach-4)\n" +
               "cc(departed-6, and-5)\n" +
               "conj:and(noticed-2, departed-6)\n"},
             {TestType.CC_PROCESSED,
              "( (S (S (NP (RBR More) (JJ common) (NN chrysotile) (NNS fibers)) (VP (VP (VBP are) (ADJP (JJ curly))) (CC and) (VP (VBP are) (VP (ADVP (RBR more) (RB easily)) (VBN rejected) (PP (IN by) (NP (DT the) (NN body))))))) (, ,) (NP (NNP Dr.) (NNP Mossman)) (VP (VBD explained)) (. .)))",
              "advmod(fibers-4, More-1)\n" +
               "amod(fibers-4, common-2)\n" +
               "compound(fibers-4, chrysotile-3)\n" +
               "nsubj(curly-6, fibers-4)\n" +
               "nsubj:pass(rejected-11, fibers-4)\n" +
               "cop(curly-6, are-5)\n" +
               "ccomp(explained-18, curly-6)\n" +
               "cc(rejected-11, and-7)\n" +
               "aux:pass(rejected-11, are-8)\n" +
               "advmod(easily-10, more-9)\n" +
               "advmod(rejected-11, easily-10)\n" +
               "conj:and(curly-6, rejected-11)\n" +
               "ccomp(explained-18, rejected-11)\n" +
               "case(body-14, by-12)\n" +
               "det(body-14, the-13)\n" +
               "obl:agent(rejected-11, body-14)\n" +
               "compound(Mossman-17, Dr.-16)\n" +
               "nsubj(explained-18, Mossman-17)\n" +
               "root(ROOT-0, explained-18)\n"},
             {TestType.CC_PROCESSED,
              "( (S (NP (NNP John)) (VP (VP (VBD is) (VP (VBN appalled))) (CC and) (VP (MD will) (VP (VB complain)))) (. .)))",
              "nsubj:pass(appalled-3, John-1)\n" +
               "nsubj(complain-6, John-1)\n" +
               "aux:pass(appalled-3, is-2)\n" +
               "root(ROOT-0, appalled-3)\n" +
               "cc(complain-6, and-4)\n" +
               "aux(complain-6, will-5)\n" +
               "conj:and(appalled-3, complain-6)\n"},
             {TestType.CC_PROCESSED,
              "( (SBARQ (WHNP (WP What)) (SQ (VP (VBP are) (NP (NP (NP (NP (NNP Christopher) (NNP Marlowe) (POS 's)) (CC and) (NP (NNP Shakespeare) (POS 's))) (JJ literary) (NNS contributions)) (PP (TO to) (NP (JJ English) (NN literature)))))) (. ?)))",
              "nsubj(contributions-10, What-1)\n" +
               "cop(contributions-10, are-2)\n" +
               "compound(Marlowe-4, Christopher-3)\n" +
               "nmod:poss(contributions-10, Marlowe-4)\n" +
               "case(Marlowe-4, 's-5)\n" +
               "cc(Shakespeare-7, and-6)\n" +
               "conj:and(Marlowe-4, Shakespeare-7)\n" +
               "nmod:poss(contributions-10, Shakespeare-7)\n" +
               "case(Shakespeare-7, 's-8)\n" +
               "amod(contributions-10, literary-9)\n" +
               "root(ROOT-0, contributions-10)\n" +
               "case(literature-13, to-11)\n" +
               "amod(literature-13, English-12)\n" +
               "nmod:to(contributions-10, literature-13)\n"},
             {TestType.CC_PROCESSED,
              "( (SBARQ (WHNP (WP What)) (SQ (VP (VBP are) (NP (NP (NP (NP (NP (NNP Christopher) (NNP Marlowe)) (CC and) (NP (NNP Shakespeare))) (POS 's)) (JJ literary) (NNS contributions)) (PP (TO to) (NP (JJ English) (NN literature)))))) (. ?)))",
              "nsubj(contributions-9, What-1)\n" +
               "cop(contributions-9, are-2)\n" +
               "compound(Marlowe-4, Christopher-3)\n" +
               "nmod:poss(contributions-9, Marlowe-4)\n" +
               "cc(Shakespeare-6, and-5)\n" +
               "conj:and(Marlowe-4, Shakespeare-6)\n" +
               "nmod:poss(contributions-9, Shakespeare-6)\n" +
               "case(Marlowe-4, 's-7)\n" +
               "amod(contributions-9, literary-8)\n" +
               "root(ROOT-0, contributions-9)\n" +
               "case(literature-12, to-10)\n" +
               "amod(literature-12, English-11)\n" +
               "nmod:to(contributions-9, literature-12)\n"},
             {TestType.CC_PROCESSED,
              "( (S (NP (PRP I)) (VP (VBP like) (NP (NP (NNS dogs)) (CONJP (RB as) (RB well) (IN as)) (NP (NNS cats)))) (. .)))",
              "nsubj(like-2, I-1)\n" +
               "root(ROOT-0, like-2)\n" +
               "obj(like-2, dogs-3)\n" +
               "cc(cats-7, as-4)\n" +
               "fixed(as-4, well-5)\n" +
               "fixed(as-4, as-6)\n" +
               "obj(like-2, cats-7)\n" +
               "conj:and(dogs-3, cats-7)\n"},
             {TestType.CC_PROCESSED,
              "( (S (NP (PRP I)) (VP (VBP like) (NP (NP (NNS dogs)) (CONJP (RB rather) (IN than)) (NP (NNS cats)))) (. .)))",
              "nsubj(like-2, I-1)\n" +
               "root(ROOT-0, like-2)\n" +
               "obj(like-2, dogs-3)\n" +
               "cc(cats-6, rather-4)\n" +
               "fixed(rather-4, than-5)\n" +
               "obj(like-2, cats-6)\n" +
               "conj:negcc(dogs-3, cats-6)\n"},
             {TestType.CC_PROCESSED,
              "( (S (NP (PRP I)) (VP (VBP like) (NP (NP (NN brandy)) (CONJP (RB not) (TO to) (VB mention)) (NP (NN cognac)))) (. .)))",
              "nsubj(like-2, I-1)\n" +
               "root(ROOT-0, like-2)\n" +
               "obj(like-2, brandy-3)\n" +
               "cc(cognac-7, not-4)\n" +
               "fixed(not-4, to-5)\n" +
               "fixed(not-4, mention-6)\n" +
               "obj(like-2, cognac-7)\n" +
               "conj:and(brandy-3, cognac-7)\n"},
             {TestType.CC_PROCESSED,
              "( (S (NP (PRP I)) (VP (VBP like) (NP (CONJP (RB not) (RB only)) (NP (NNS cats)) (CONJP (CC but) (RB also)) (NP (NN dogs)))) (. .)))",
              "nsubj(like-2, I-1)\n" +
               "root(ROOT-0, like-2)\n" +
               "advmod(cats-5, not-3)\n" +
               "advmod(cats-5, only-4)\n" +
               "obj(like-2, cats-5)\n" +
               "cc(dogs-8, but-6)\n" +
               "advmod(dogs-8, also-7)\n" +
               "obj(like-2, dogs-8)\n" +
               "conj:and(cats-5, dogs-8)\n"},
             {TestType.CC_PROCESSED,
              "((S (NP (NNP Fred)) (VP (VBD flew) (PP (CONJP (RB not) (JJ only)) (PP (TO to) (NP (NNP Greece))) (CONJP (CC but) (RB also)) (PP (TO to) (NP (NNP Serbia))))) (. .)))",
              "nsubj(flew-2, Fred-1)\n" +
               "nsubj(flew-2', Fred-1)\n" +
               "root(ROOT-0, flew-2)\n" +
               "conj:and(flew-2, flew-2')\n" +
               "advmod(Greece-6, not-3)\n" +
               "advmod(Greece-6, only-4)\n" +
               "case(Greece-6, to-5)\n" +
               "obl:to(flew-2, Greece-6)\n" +
               "cc(flew-2', but-7)\n" +
               "advmod(Serbia-10, also-8)\n" +
               "case(Serbia-10, to-9)\n" +
               "obl:to(flew-2', Serbia-10)\n"},
             {TestType.CC_PROCESSED,
              "( (SINV (ADVP-TMP (RB Only) (RB recently)) (SINV (VBZ has) (NP (PRP it)) (VP (VBN been) (VP (ADVP-MNR (RB attractively)) (VBN redesigned)))) (CC and) (SINV (NP (PRP$ its) (JJ editorial) (NN product)) (VP (VBN improved))) (. .)))",
              "advmod(recently-2, Only-1)\n" +
               "advmod(redesigned-7, recently-2)\n" +
               "aux(redesigned-7, has-3)\n" +
               "nsubj:pass(redesigned-7, it-4)\n" +
               "aux:pass(redesigned-7, been-5)\n" +
               "advmod(redesigned-7, attractively-6)\n" +
               "root(ROOT-0, redesigned-7)\n" +
               "cc(improved-12, and-8)\n" +
               "nmod:poss(product-11, its-9)\n" +
               "amod(product-11, editorial-10)\n" +
               "nsubj(improved-12, product-11)\n" +
               "conj:and(redesigned-7, improved-12)\n"},
             {TestType.CC_PROCESSED,
              "( (S (NP-SBJ (JJP (JJ Political) (CC and) (NN currency)) (NNS gyrations)) (VP (MD can) (VP (VB whipsaw) (NP (DT the) (NNS funds)))) (. .)))",
              "amod(gyrations-4, Political-1)\n" +
               "cc(currency-3, and-2)\n" +
               "conj:and(Political-1, currency-3)\n" +
               "amod(gyrations-4, currency-3)\n" +
               "nsubj(whipsaw-6, gyrations-4)\n" +
               "aux(whipsaw-6, can-5)\n" +
               "root(ROOT-0, whipsaw-6)\n" +
               "det(funds-8, the-7)\n" +
               "obj(whipsaw-6, funds-8)\n"},
             {TestType.CC_PROCESSED,
              "(NP-SBJ (NNS Managers) (CC and) (NNS presenters))",
              "root(ROOT-0, Managers-1)\n" +
               "cc(presenters-3, and-2)\n" +
               "conj:and(Managers-1, presenters-3)\n"},
             {TestType.CC_PROCESSED,
              "(NP (NN education) (, ,) (NN science) (CC and) (NN culture))",
              "root(ROOT-0, education-1)\n" +
               "conj:and(education-1, science-3)\n" +
               "cc(culture-5, and-4)\n" +
               "conj:and(education-1, culture-5)\n"},
             {TestType.CC_PROCESSED,
              "(NP (NN education) (, ,) (NN science) (, ,) (CC and) (NN culture))",
              "root(ROOT-0, education-1)\n" +
                "conj:and(education-1, science-3)\n" +
                "cc(culture-6, and-5)\n" +
                "conj:and(education-1, culture-6)\n"},
             {TestType.CC_PROCESSED,
              "(NP (NNP Digital) (, ,) (NNP Hewlett) (CC and) (NNP Sun) ) ",
              "root(ROOT-0, Digital-1)\n" +
               "conj:and(Digital-1, Hewlett-3)\n" +
               "cc(Sun-5, and-4)\n" +
               "conj:and(Digital-1, Sun-5)\n"},
             {TestType.CC_PROCESSED,
              "(NP (NNP Digital) (, ,) (NNP Hewlett) (, ,) (CC and) (NNP Sun))",
              "root(ROOT-0, Digital-1)\n" +
               "conj:and(Digital-1, Hewlett-3)\n" +
               "cc(Sun-6, and-5)\n" +
               "conj:and(Digital-1, Sun-6)\n"},
             {TestType.CC_PROCESSED,
              "(NP (NP (NNP Home) (NNP Depot) ) (, ,) (NP (NNP Sun) ) (, ,) (CC and) (NP (NNP Coke) ) )",
              "compound(Depot-2, Home-1)\n" +
               "root(ROOT-0, Depot-2)\n" +
               "conj:and(Depot-2, Sun-4)\n" +
               "cc(Coke-7, and-6)\n" +
               "conj:and(Depot-2, Coke-7)\n"},
             {TestType.CC_PROCESSED,
              "(NP (NP (NNP Home) (NNP Depot) ) (, ,) (NP (NNP Sun) ) (CC and)  (NP (NNP Coke) ) )",
              "compound(Depot-2, Home-1)\n" +
               "root(ROOT-0, Depot-2)\n" +
               "conj:and(Depot-2, Sun-4)\n" +
               "cc(Coke-6, and-5)\n" +
               "conj:and(Depot-2, Coke-6)\n"},
             {TestType.CC_PROCESSED,
              "(S (NP (NP (NN Activation)) (PP (IN of) (NP (NP (NN Akt)) (, ,) (NP (NN NFkappaB)) (, ,) (CC and) (NP (NN Stat3)) (CONJP (CC but) (RB not)) (NP (NN MAPK) (NNS pathways))))) (VP (VBP are) (NP (NP (NNS characteristics)) (VP (VBN associated) (PP (IN with) (NP (NP (JJ malignant) (NN transformation)) ))))))",
              "nsubj(characteristics-14, Activation-1)\n" +
               "case(Akt-3, of-2)\n" +
               "nmod:of(Activation-1, Akt-3)\n" +
               "nmod:of(Activation-1, NFkappaB-5)\n" +
               "conj:and(Akt-3, NFkappaB-5)\n" +
               "cc(Stat3-8, and-7)\n" +
               "nmod:of(Activation-1, Stat3-8)\n" +
               "conj:and(Akt-3, Stat3-8)\n" +
               "cc(not-10, but-9)\n" +
               "cc(pathways-12, not-10)\n" +
               "compound(pathways-12, MAPK-11)\n" +
               "nmod:of(Activation-1, pathways-12)\n" +
               "conj:negcc(Akt-3, pathways-12)\n" +
               "cop(characteristics-14, are-13)\n" +
               "root(ROOT-0, characteristics-14)\n" +
               "acl(characteristics-14, associated-15)\n" +
               "case(transformation-18, with-16)\n" +
               "amod(transformation-18, malignant-17)\n" +
               "obl:with(associated-15, transformation-18)\n"},
             {TestType.COLLAPSED,
              "(ROOT (S (NP (NNP Bill)) (VP (VBD went) (PP (PP (IN over) (NP (DT the) (NN river))) (CC and) (PP (IN through) (NP (DT the) (NNS woods))))) (. .)))",
              "nsubj(went-2, Bill-1)\n" +
               "root(ROOT-0, went-2)\n" +
               "conj:and(went-2, went-2')\n" +
               "case(river-5, over-3)\n" +
               "det(river-5, the-4)\n" +
               "obl:over(went-2, river-5)\n" +
               "cc(went-2', and-6)\n" +
               "case(woods-9, through-7)\n" +
               "det(woods-9, the-8)\n" +
               "obl:through(went-2', woods-9)\n"},
             {TestType.CC_PROCESSED,
              "(ROOT (S (NP (NNP Lufthansa)) (VP (VBZ flies) (PP (TO to) (CC and) (IN from) (NP (NP (NNP Serbia)) (CC and) (NP (NNP France))))) (. .)))",
              "nsubj(flies-2, Lufthansa-1)\n" +
              "nsubj(flies-2', Lufthansa-1)\n" +
              "root(ROOT-0, flies-2)\n" +
              "conj:and(flies-2, flies-2')\n" +
              "case(Serbia-6, to-3)\n" +
              "cc(from-5, and-4)\n" +
              "conj:and(to-3, from-5)\n" +
              "obl:from(flies-2', Serbia-6)\n" +
              "obl:to(flies-2, Serbia-6)\n" +
              "cc(France-8, and-7)\n" +
              "obl:from(flies-2', France-8)\n" +
              "obl:to(flies-2, France-8)\n" +
              "conj:and(Serbia-6, France-8)\n"},
             {TestType.CC_PROCESSED,
              "((S (NP-SBJ (NP (DT The) (NN apartment)) (PP-LOC (IN across) (PP (IN from) (NP (PRP mine))))) (VP (VBD belonged) (PP-CLR (IN to) (NP (NP (DT a) (NN gang)) (PP (IN of) (NP (NNS bikers)))))) (. .)))",
              "det(apartment-2, The-1)\n" +
                "nsubj(belonged-6, apartment-2)\n" +
                "case(mine-5, across-3)\n" +
                "fixed(across-3, from-4)\n" +
                "nmod:across_from(apartment-2, mine-5)\n" +
                "root(ROOT-0, belonged-6)\n" +
                "case(gang-9, to-7)\n" +
                "det(gang-9, a-8)\n" +
                "obl:to(belonged-6, gang-9)\n" +
                "case(bikers-11, of-10)\n" +
                "nmod:of(gang-9, bikers-11)\n"},
             {TestType.CC_PROCESSED,
              "((S (NP-SBJ (PRP He)) (VP (VBZ is) (ADJP-PRD (JJ close) (PP (IN by) (NP (DT the ) (NN train) (NN station))))) (. .)))",
              "nsubj(station-7, He-1)\n" +
                  "cop(station-7, is-2)\n" +
                  "case(station-7, close-3)\n" +
                  "fixed(close-3, by-4)\n" +
                  "det(station-7, the-5)\n" +
                  "compound(station-7, train-6)\n" +
                  "root(ROOT-0, station-7)\n"},
              {TestType.CC_PROCESSED,
               "(ROOT (S (PP (ADVP (RB Apart)) (IN from) (NP (DT the) (NN roof))) (, ,) (NP (DT the) (NN house)) (VP (VBZ is) (PP (IN in) (NP (JJ good) (NN shape)))) (. .)))",
               "case(roof-4, Apart-1)\n" +
                   "fixed(Apart-1, from-2)\n" +
                   "det(roof-4, the-3)\n" +
                   "obl:apart_from(shape-11, roof-4)\n" +
                   "det(house-7, the-6)\n" +
                   "nsubj(shape-11, house-7)\n" +
                   "cop(shape-11, is-8)\n" +
                   "case(shape-11, in-9)\n" +
                   "amod(shape-11, good-10)\n" +
                   "root(ROOT-0, shape-11)\n"
              },
            {TestType.BASIC,
                    "( (S (NP (NNS Hippos)) (VP (VBP weigh) (NP (QP (RP up) (IN to) (CD 2,700)) (NNS kilograms))) (. .)))",
                    "nsubj(weigh-2, Hippos-1)\n" +
                            "root(ROOT-0, weigh-2)\n" +
                            "advmod(2,700-5, up-3)\n" +
                            "fixed(up-3, to-4)\n" +
                            "nummod(kilograms-6, 2,700-5)\n" +
                            "obj(weigh-2, kilograms-6)\n",
            },
            {TestType.BASIC,
                    "( (S (NP (NNS Hippos)) (VP (VBP weigh) (NP (QP (RB up) (IN to) (CD 2,700)) (NNS kilograms))) (. .)))",
                    "nsubj(weigh-2, Hippos-1)\n" +
                            "root(ROOT-0, weigh-2)\n" +
                            "advmod(2,700-5, up-3)\n" +
                            "fixed(up-3, to-4)\n" +
                            "nummod(kilograms-6, 2,700-5)\n" +
                            "obj(weigh-2, kilograms-6)\n",
            },

    });
  }



  public UniversalEnglishGrammaticalStructureTest(TestType type, String testTree, String testAnswer) {
    this.testTree = testTree;
    this.testAnswer = testAnswer;
    this.type = type;
  }

  @Test
  public void doTest() {
    switch(this.type) {
      case BASIC:
        testBasicRelation();
        break;
      case COPULA_HEAD:
        testBasicRelationWithCopulaAsHead();
        break;
      case NON_COLLAPSED:
        testNonCollapsedRelation();
        break;
      case NON_COLLAPSED_SEPARATOR:
        testNonCollapsedSeparator();
        break;
      case COLLAPSED:
        testCollapsedRelation();
        break;
      case CC_PROCESSED:
        testCCProcessedRelation();
        break;
      default:
        throw new RuntimeException("No test defined for test type " + this.type);
    }
  }

  /**
   * Tests that we can extract the basic grammatical relations correctly from
   * some hard-coded trees.
   *
   * Uses the sentence examples from the manual to at least test each relation.
   *
   */
  private void testBasicRelation() {
    TreeReaderFactory trf = new NPTmpRetainingTreeNormalizer.NPTmpAdvRetainingTreeReaderFactory();
    // specifying our own TreeReaderFactory is vital so that functional
    // categories - that is -TMP and -ADV in particular - are not stripped off
    Tree tree = Tree.valueOf(testTree, trf);
    GrammaticalStructure gs = new UniversalEnglishGrammaticalStructure(tree);

    assertEquals("Unexpected basic dependencies for tree " + testTree,
          testAnswer, GrammaticalStructureConversionUtils.dependenciesToString(gs, gs.typedDependencies(), tree, false, false, false));
  }



  /**
   * Tests that we can extract the basic grammatical relations correctly from
   * a hard-coded tree with copulae being the head.
   *
   */
  private void testBasicRelationWithCopulaAsHead() {
      TreeReaderFactory trf = new NPTmpRetainingTreeNormalizer.NPTmpAdvRetainingTreeReaderFactory();
      // specifying our own TreeReaderFactory is vital so that functional
      // categories - that is -TMP and -ADV in particular - are not stripped off
      Tree tree = Tree.valueOf(testTree, trf);

      GrammaticalStructure gs = new UniversalEnglishGrammaticalStructure(tree,
              new PennTreebankLanguagePack().punctuationWordRejectFilter(),
              new UniversalSemanticHeadFinder(false));


      assertEquals("Unexpected basic dependencies with copula as head for tree "+ testTree,
          testAnswer, GrammaticalStructureConversionUtils.dependenciesToString(gs, gs.typedDependencies(), tree, false, false, false));
  }

   /**
   * Tests that we can extract the non-collapsed grammatical relations (basic + extra)
   * correctly from a hard-coded tree.
   *
   */
   private void testNonCollapsedRelation() {
    TreeReaderFactory trf = new NPTmpRetainingTreeNormalizer.NPTmpAdvRetainingTreeReaderFactory();
    // specifying our own TreeReaderFactory is vital so that functional
    // categories - that is -TMP and -ADV in particular - are not stripped off
    Tree tree = Tree.valueOf(testTree, trf);
    GrammaticalStructure gs = new UniversalEnglishGrammaticalStructure(tree);

    assertEquals("Unexpected non-collapsed dependencies for tree "+ testTree,
        testAnswer, GrammaticalStructureConversionUtils.dependenciesToString(gs, gs.allTypedDependencies(), tree, false, false, false));
  }


  /**
   * Tests printing of the extra dependencies after the basic ones.
   *
   */
  private void testNonCollapsedSeparator() {

    TreeReaderFactory trf = new PennTreeReaderFactory();
    // specifying our own TreeReaderFactory is vital so that functional
    // categories - that is -TMP and -ADV in particular - are not stripped off
    Tree tree = Tree.valueOf(testTree, trf);
    GrammaticalStructure gs = new UniversalEnglishGrammaticalStructure(tree);

    assertEquals("Unexpected basic dependencies for tree "+testTree,
      testAnswer, GrammaticalStructureConversionUtils.dependenciesToString(gs, gs.allTypedDependencies(), tree, false, true, false));

  }


  /**
   * Tests that we can extract the collapsed grammatical relations correctly from
   * a hard-coded tree.
   *
   */
  private void testCollapsedRelation() {
    TreeReaderFactory trf = new PennTreeReaderFactory();

    // specifying our own TreeReaderFactory is vital so that functional
    // categories - that is -TMP and -ADV in particular - are not stripped off
    Tree tree = Tree.valueOf(testTree, trf);
    GrammaticalStructure gs = new UniversalEnglishGrammaticalStructure(tree);

    String depString = GrammaticalStructureConversionUtils.dependenciesToString(gs, gs.typedDependenciesCollapsed(Extras.MAXIMAL), tree, false, false, false);
    assertEquals("Unexpected collapsed dependencies for tree " + testTree,
          testAnswer, depString);
  }

  /**
   * Tests that we can extract the CCprocessed grammatical relations correctly from
   * a hard-coded tree.
   *
   */
  private void testCCProcessedRelation() {

    TreeReaderFactory trf = new PennTreeReaderFactory();

    // specifying our own TreeReaderFactory is vital so that functional
    // categories - that is -TMP and -ADV in particular - are not stripped off
    Tree tree = Tree.valueOf(testTree, trf);
    GrammaticalStructure gs = new UniversalEnglishGrammaticalStructure(tree);

    assertEquals("Unexpected CC processed dependencies for tree "+testTree,
          testAnswer, GrammaticalStructureConversionUtils.dependenciesToString(gs, gs.typedDependenciesCCprocessed(Extras.MAXIMAL), tree, false, false, false));
  }

}
