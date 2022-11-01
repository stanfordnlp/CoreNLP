// Stanford Dependencies - Code for producing and using Stanford dependencies.
// Copyright © 2005-2014 The Board of Trustees of
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
//    parser-support@lists.stanford.edu
//    http://nlp.stanford.edu/software/stanford-dependencies.shtml

package edu.stanford.nlp.trees.international.pennchinese;

import edu.stanford.nlp.international.Language;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.trees.HeadFinder;
import edu.stanford.nlp.trees.tregex.TregexPatternCompiler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static edu.stanford.nlp.trees.GrammaticalRelation.DEPENDENT;
import static edu.stanford.nlp.trees.GrammaticalRelation.GOVERNOR;


/**
 * ChineseGrammaticalRelations is a
 * set of {@link GrammaticalRelation} objects for the Chinese language.
 * Examples are from CTB_001.fid
 *
 * @author Galen Andrew
 * @author Pi-Chuan Chang
 * @author Huihsin Tseng
 * @author Marie-Catherine de Marneffe
 * @see edu.stanford.nlp.trees.GrammaticalStructure
 * @see GrammaticalRelation
 * @see ChineseGrammaticalStructure
 */
public class ChineseGrammaticalRelations {

  /** This class is just a holder for static classes
   *  that act a bit like an enum.
   */
  private ChineseGrammaticalRelations() {}

  // By setting the HeadFinder to null, we find out right away at
  // runtime if we have incorrectly set the HeadFinder for the
  // dependency tregexes
  private static final TregexPatternCompiler tregexCompiler = new TregexPatternCompiler((HeadFinder) null);

  /** Return an unmodifiable list of grammatical relations.
   *  Note: the list can still be modified by others, so you
   *  should still get a lock with {@code valuesLock()} before
   *  iterating over this list.
   *
   *  @return A list of grammatical relations
   */
  public static List<GrammaticalRelation> values() {
    return Collections.unmodifiableList(values);
  }

  public static final ReadWriteLock valuesLock = new ReentrantReadWriteLock();

  public static Lock valuesLock() {
    return valuesLock.readLock();
  }


  public static GrammaticalRelation valueOf(String s) {
    return GrammaticalRelation.valueOf(s, values(), valuesLock());
  }

  /*
   * The "predicate" grammatical relation.
   */
  // Fri Feb 20 15:42:54 2009 (pichuan)
  // I'm surprise this relation has patterns.
  // However it doesn't seem to match anything in CTB6.
  /*
  public static final GrammaticalRelation PREDICATE =
    new GrammaticalRelation(Language.Chinese, "pred", "predicate",
                            PredicateGRAnnotation.class, DEPENDENT, "IP",
                            new String[]{
                              " IP=target !> IP"
                            });
  public static class PredicateGRAnnotation
    extends GrammaticalRelationAnnotation { }
  */

  /**
   * The "argument" grammatical relation.
   */
  public static final GrammaticalRelation ARGUMENT =
    new GrammaticalRelation(Language.Chinese, "arg", "argument", DEPENDENT);


  /**
   * The "conjunct" grammatical relation.
   * Example:
   * <code>
   * <pre>
   * (ROOT
   *   (IP
   *     (NP
   *       (NP (NR 上海) (NR 浦东))
   *       (NP (NN 开发)
   *         (CC 与)
   *         (NN 法制) (NN 建设)))
   *     (VP (VV 同步))))
   *
   * "The development of Shanghai 's Pudong is in step with the
   * establishment of its legal system"
   *
   * conj(建设, 开发)
   * </pre>
   * </code>
   */
  public static final GrammaticalRelation CONJUNCT =
    new GrammaticalRelation(Language.Chinese,
      "conj", "conjunct",
      DEPENDENT, "FRAG|INC|IP|VP|NP|ADJP|PP|ADVP|UCP", tregexCompiler,
        "NP|ADJP|PP|ADVP|UCP < (!PU=target $+ CC)",
        // Split the first rule to the second rule to avoid the duplication:
        // ccomp(前来-12, 投资-13)
        // conj(前来-12, 投资-13)
        //
        //      (IP
        //        (VP
        //          (VP (VV 前来))
        //          (VP
        //            (VCD (VV 投资) (VV 办厂)))
        //          (CC 和)
        //          (VP (VV 洽谈)
        //            (NP (NN 生意))))))
        "VP < (!PU=target !$- VP $+ CC)",
        // TODO: this following line has to be fixed.
        //       I think for now it just doesn't match anything.
        "VP|NP|ADJP|PP|ADVP|UCP < ( __=target $+ PU $+ CC)",
        //"VP|NP|ADJP|PP|ADVP|UCP < ( __=target $+ (PU < 、) )",
        // Consider changing the rule ABOVE to these rules.
          "VP   < ( /^V/=target  $+ ((PU < 、) $+ /^V/))",
          "NP   < ( /^N/=target  $+ ((PU < 、) $+ /^N/))",
          "ADJP < ( JJ|ADJP=target  $+ ((PU < 、) $+ JJ|ADJP))",
          "PP   < ( /^P/=target  $+ ((PU < 、) $+ /^P/))",
        //"ADVP < ( /^AD/=target $+ ((PU < 、) $+ /^AD/))",
          "ADVP < ( /^AD/ $+ ((PU < 、) $+ /^AD/=target))",
          "UCP  < ( __=target    $+ (PU < 、) )",
        // This is for the 'conj's separated by commas.
        // For now this creates too much duplicates with 'ccomp'.
        // Need to look at more examples.

        "PP < (PP $+ PP=target )",
        "NP <( NP=target $+ ((PU < 、) $+ NP) )",
        "NP <( NN|NR|NT|PN=target $+ ((PU < ，|、) $+ NN|NR|NT|PN) )",
        "VP < (CC $+ VV=target)",
        // Original version of this did not have the outer layer of
        // the FRAG|INC|IP|VP.  This caused a bug where the basic
        // dependencies could have cycles.
        "FRAG|INC|IP|VP < (VP  < VV|VC|VRD|VCD|VE|VA < NP|QP|LCP  $ IP|VP|VRD|VCD|VE|VC|VA=target)  ",
         "IP|VP < ( IP|VP < NP|QP|LCP $ IP|VP=target )"
      );


  /**
   * The "copula" grammatical relation.
   */
  public static final GrammaticalRelation AUX_MODIFIER =
    new GrammaticalRelation(Language.Chinese, "cop", "copula",
                            DEPENDENT, "VP", tregexCompiler,
            " VP < VC=target");


  /**
   * The "coordination" grammatical relation.
   * A coordination is the relation between
   * an element and a conjunction.<p>
   * <br>
   * Example:
   * <code>
   * <pre>
   * (ROOT
   *   (IP
   *     (NP
   *       (NP (NR 上海) (NR 浦东))
   *       (NP (NN 开发)
   *         (CC 与)
   *         (NN 法制) (NN 建设)))
   *     (VP (VV 同步))))
   *
   * cc(建设, 与)
   * </pre>
   * </code>
   */

  public static final GrammaticalRelation COORDINATION =
    new GrammaticalRelation(Language.Chinese,
      "cc", "coordination", DEPENDENT,
      "VP|NP|ADJP|PP|ADVP|UCP|IP|QP", tregexCompiler,
            "VP|NP|ADJP|PP|ADVP|UCP|IP|QP < (CC=target)");


  /**
   * The "punctuation" grammatical relation.  This is used for any piece of
   * punctuation in a clause, if punctuation is being retained in the
   * typed dependencies.
   */
  public static final GrammaticalRelation PUNCTUATION =
    new GrammaticalRelation(Language.Chinese, "punct", "punctuation",
        DEPENDENT, ".*", tregexCompiler,
        "__ < PU=target");


  /**
   * The "subject" grammatical relation.
   */
  public static final GrammaticalRelation SUBJECT =
    new GrammaticalRelation(Language.Chinese, "subj", "subject",
                            ARGUMENT);


  /**
   * The "nominal subject" grammatical relation.  A nominal subject is
   * a subject which is an noun phrase.<p>
   * <br>
   * Example:
   * <code>
   * <pre>
   * (ROOT
   *   (IP
   *     (NP
   *       (NP (NR 上海) (NR 浦东))
   *       (NP (NN 开发)
   *         (CC 与)
   *         (NN 法制) (NN 建设)))
   *     (VP (VV 同步))))
   *
   * nsubj(同步, 建设)
   * </pre>
   * </code>
   */
  public static final GrammaticalRelation NOMINAL_SUBJECT =
    new GrammaticalRelation(Language.Chinese, "nsubj", "nominal subject",
        SUBJECT, "IP|VP", tregexCompiler,
        "IP <( ( NP|QP=target!< NT ) $++ ( /^VP|VCD|IP/  !< VE !<VC !<SB !<LB  ))",
        "NP !$+ VP < ( (  NP|DP|QP=target !< NT ) $+ ( /^VP|VCD/ !<VE !< VC !<SB !<LB))");


  /**
   * The "topic" grammatical relation.
   * Example:
   * <code>
   * <pre>
   * (IP
   *   (NP (NN 建筑))
   *   (VP (VC 是)
   *     (NP
   *       (CP
   *         (IP
   *           (VP (VV 开发)
   *             (NP (NR 浦东))))
   *         (DEC 的))
   *       (QP (CD 一)
   *         (CLP (M 项)))
   *       (ADJP (JJ 主要))
   *       (NP (NN 经济) (NN 活动)))))
   *
   * top(是, 建筑)
   * </pre>
   * </code>
   */

  public static final GrammaticalRelation TOP_SUBJECT =
    new GrammaticalRelation(Language.Chinese,
      "top", "topic", SUBJECT, "IP|VP", tregexCompiler,
            "IP|VP < ( NP|DP=target $+ ( VP < VC|VE ) )",
            "IP < (IP=target $+ ( VP < VC|VE))");


  /**
   * The "nsubjpass" grammatical relation.
   * The noun is the subject of a passive sentence.
   * The passive marker in Chinese is "被".
   * <p>Example:
   * <code>
   * <pre>
   * (IP
   *   (NP (NN 镍))
   *   (VP (SB 被)
   *     (VP (VV 称作)
   *       (NP (PU “)
   *         (DNP
   *           (NP
   *             (ADJP (JJ 现代))
   *             (NP (NN 工业)))
   *           (DEG 的))
   *         (NP (NN 维生素))
   *         (PU ”)))))
   *
   * nsubjpass(称作-3, 镍-1)
   * </pre>
   * </code>
   */
  public static final GrammaticalRelation NOMINAL_PASSIVE_SUBJECT =
    new GrammaticalRelation(Language.Chinese,
      "nsubjpass", "nominal passive subject",
      NOMINAL_SUBJECT, "IP", tregexCompiler,
            "IP < (NP=target $+ (VP|IP < SB|LB))");


  /**
   * The "clausal subject" grammatical relation.  A clausal subject is
   * a subject which is a clause.
   * <br>
   * Examples:
   * <code>
   * <pre>
   * </pre>
   * </code>
   * <br>
   * Note: This one might not exist in Chinese, or very rare.
   */
  public static final GrammaticalRelation CLAUSAL_SUBJECT =
    new GrammaticalRelation(Language.Chinese,
      "csubj", "clausal subject",
      SUBJECT, "IP", tregexCompiler
        // This following rule is too general and collides with 'ccomp'.
        // Delete it for now.
        // TODO: come up with a new rule. Does this exist in Chinese?
        //"IP < (IP=target $+ ( VP !< VC))",
      );


  /**
   * The "comp" grammatical relation.
   */
  public static final GrammaticalRelation COMPLEMENT =
    new GrammaticalRelation(Language.Chinese, "comp", "complement",
                            ARGUMENT);


  /**
   * The "obj" grammatical relation.
   */
  public static final GrammaticalRelation OBJECT =
    new GrammaticalRelation(Language.Chinese, "obj", "object",
                            COMPLEMENT);


  /**
   * The "direct object" grammatical relation.
   * <p />Examples:
   * <code>
   * <pre>
   *(IP
   *   (NP (NR 上海) (NR 浦东))
   *   (VP
   *     (VCD (VV 颁布) (VV 实行))
   *          (AS 了)
   *          (QP (CD 七十一)
   *              (CLP (M 件)))
   *          (NP (NN 法规性) (NN 文件))))
   *
   *  In recent years Shanghai 's Pudong has promulgated and implemented
   *  some regulatory documents.
   *
   * dobj(颁布, 文件)
   * </pre>
   * </code>
   */
  public static final GrammaticalRelation DIRECT_OBJECT =
    new GrammaticalRelation(Language.Chinese,
      "dobj", "direct object",
      OBJECT, "CP|VP", tregexCompiler,
            "VP < ( /^V*/ $+ NP $+ NP|DP=target ) !< VC ",
            " VP < ( /^V*/ $+ NP|DP=target ! $+ NP|DP) !< VC ",
            "CP < (IP $++ NP=target ) !<< VC");


  /*
   * The "indirect object" grammatical relation.
   */
  // deleted by pichuan: no real matches
  /*
  public static final GrammaticalRelation INDIRECT_OBJECT =
    new GrammaticalRelation(Language.Chinese,
      "iobj", "indirect object",
      IndirectObjectGRAnnotation.class,  OBJECT, "VP",
      new String[]{
        " CP !> VP < ( VV $+ ( NP|DP|QP|CLP=target . NP|DP ) )"
      });
  public static class IndirectObjectGRAnnotation
    extends GrammaticalRelationAnnotation { }
  */

  /**
   * The "range" grammatical relation.  The indirect
   * object of a VP is the quantifier phrase which is the (dative) object
   * of the verb.<p>
   *       (VP (VV 成交)
   *         (NP (NN 药品))
   *         (QP (CD 一亿多)
   *           (CLP (M 元))))
   * {@code range }(成交, 元)
   */
  public static final GrammaticalRelation RANGE =
    new GrammaticalRelation(Language.Chinese,
      "range", "range",
      OBJECT, "VP", tregexCompiler,
            " VP < ( NP|DP|QP $+ NP|DP|QP=target)",
            "VP < ( VV $+ QP=target )");


  /**
   * The "prepositional object" grammatical relation.
   * (PP (P 根据)
   *       (NP
   *         (DNP
   *           (NP
   *             (NP (NN 国家))
   *             (CC 和)
   *             (NP (NR 上海市)))
   *           (DEG 的))
   *         (ADJP (JJ 有关))
   *         (NP (NN 规定))))
   * Example:
   * pobj(根据-13, 规定-19)
   */
  public static final GrammaticalRelation PREPOSITIONAL_OBJECT =
    new GrammaticalRelation(Language.Chinese,
      "pobj", "prepositional object",
      OBJECT, "^PP", tregexCompiler,
            "/^PP/ < /^P/ < /^NP|^DP|QP/=target");


  /**
   * The "localizer object" grammatical relation.
   * (LCP
   *       (NP (NT 近年))
   *       (LC 来))
   * lobj(来-4, 近年-3)
   */
  public static final GrammaticalRelation TIME_POSTPOSITION =
    new GrammaticalRelation(Language.Chinese, "lobj", "localizer object",
                            OBJECT, "LCP", tregexCompiler,
            "LCP < ( NP|QP|DP=target $+ LC)");



  /**
   * The "attributive" grammatical relation.
   *	 (IP
   *        (NP (NR 浦东))
   *      (VP (VC 是)
   *          (NP (NN 工程)))))
   * {@code attr } (是, 工程)
   */
  public static final GrammaticalRelation ATTRIBUTIVE =
    new GrammaticalRelation(Language.Chinese, "attr", "attributive",
                            COMPLEMENT, "VP", tregexCompiler,
            "VP < /^VC$/ < NP|QP=target");


  /**
   * The "clausal" grammatical relation.
   *         (IP
   *             (VP
   *               (VP
   *                 (ADVP (AD 一))
   *                 (VP (VV 出现)))
   *               (VP
   *                 (ADVP (AD 就))
   *                 (VP (SB 被)
   *                   (VP (VV 纳入)
   *                     (NP (NN 法制) (NN 轨道)))))))))))
   * {@code ccomp } (出现, 纳入)
   */
  public static final GrammaticalRelation CLAUSAL_COMPLEMENT =
    new GrammaticalRelation(Language.Chinese,
      "ccomp", "clausal complement",
      COMPLEMENT, "VP|ADJP|IP", tregexCompiler,
            "  VP  < VV|VC|VRD|VCD  !< NP|QP|LCP  < IP|VP|VRD|VCD=target > IP|VP ");
        //        "  VP|IP <  ( VV|VC|VRD|VCD !$+  NP|QP|LCP ) > (IP   < IP|VP|VRD|VCD=target)   "
       //          "VP < (S=target < (VP !<, TO|VBG) !$-- NP)",




  /**
   * The "xclausal complement" grammatical relation.
   * Example:
   */
  // pichuan: this is difficult to recognize in Chinese.
  // remove the rules since it (always) collides with ccomp
  public static final GrammaticalRelation XCLAUSAL_COMPLEMENT =
    new GrammaticalRelation(Language.Chinese,
      "xcomp", "xclausal complement",
      COMPLEMENT, "VP|ADJP", tregexCompiler
        // TODO: these rules seem to always collide with ccomp.
        // Is this really desirable behavior?
        //"VP !> (/^VP/ < /^VC$/ ) < (IP=target < (VP < P))",
        //"ADJP < (IP=target <, (VP <, P))",
        //"VP < (IP=target < (NP $+ NP|ADJP))",
        //"VP < (/^VC/ $+ (VP=target < VC < NP))"
      );


  /**
   * The "cp marker" grammatical relation.
   * (CP
   *         (IP
   *           (VP
   *             (VP (VV 振兴)
   *               (NP (NR 上海)))
   *             (PU ，)
   *             (VP (VV 建设)
   *               (NP
   *                 (NP (NN 现代化))
   *                 (NP (NN 经济) (PU 、) (NN 贸易) (PU 、) (NN 金融))
   *                 (NP (NN 中心))))))
   *         (DEC 的))
   * Example:
   *{@code cpm } (振兴, 的)
   */

  public static final GrammaticalRelation COMPLEMENTIZER =
    new GrammaticalRelation(Language.Chinese, "cpm", "complementizer",
                            COMPLEMENT, "^CP", tregexCompiler,
            "/^CP/ < (__  $++ DEC=target)");


  /*
   * The "adjectival complement" grammatical relation.
   * Example:
   */
  // deleted by pichuan: no real matches
  /*
  public static final GrammaticalRelation ADJECTIVAL_COMPLEMENT =
    new GrammaticalRelation(Language.Chinese,
      "acomp", "adjectival complement",
      AdjectivalComplementGRAnnotation.class, COMPLEMENT, "VP", tregexCompiler,
      new String[]{
        "VP < (ADJP=target !$-- NP)"
      });
  public static class AdjectivalComplementGRAnnotation
    extends GrammaticalRelationAnnotation { }
  */

  /**
   * The "localizer complement" grammatical relation.
   * (VP (VV 占)
   *     (LCP
   *       (QP (CD 九成))
   *       (LC 以上)))
   *   (PU ，)
   *   (VP (VV 达)
   *     (QP (CD 四百三十八点八亿)
   *       (CLP (M 美元))))
   * {@code loc } (占-11, 以上-13)
   */
  public static final GrammaticalRelation LC_COMPLEMENT =
    new GrammaticalRelation(Language.Chinese,
      "loc", "localizer complement",
      COMPLEMENT, "VP|IP", tregexCompiler,
            "VP|IP < LCP=target ");


  /**
   * The "resultative complement" grammatical relation.
   */
  public static final GrammaticalRelation RES_VERB =
    new GrammaticalRelation(Language.Chinese,
      "rcomp", "result verb",
      COMPLEMENT, "VRD", tregexCompiler,
            "VRD < ( /V*/ $+ /V*/=target )");


  /**
   * The "modifier" grammatical relation.
   */
  public static final GrammaticalRelation MODIFIER =
    new GrammaticalRelation(Language.Chinese, "mod", "modifier",
                            DEPENDENT);


  /**
   * The "coordinated verb compound" grammatical relation.
   *   (VCD (VV 颁布) (VV 实行))
   * comod(颁布-5, 实行-6)
   */
  public static final GrammaticalRelation VERB_COMPOUND =
    new GrammaticalRelation(Language.Chinese, "comod", "coordinated verb compound",
                            MODIFIER, "VCD", tregexCompiler,
            "VCD < ( VV|VA $+  VV|VA=target)");


  /**
   * The "modal" grammatical relation.
   * (IP
   *           (NP (NN 利益))
   *           (VP (VV 能)
   *             (VP (VV 得到)
   *               (NP (NN 保障)))))))))
   * {@code mmod } (得到-64, 能-63)
   */
  public static final GrammaticalRelation MODAL_VERB =
    new GrammaticalRelation(Language.Chinese, "mmod", "modal verb",
                            MODIFIER, "VP", tregexCompiler,
            "VP < ( VV=target !< /^没有$/ $+ VP|VRD )");


  /**
   * The "passive" grammatical relation.
   */
  public static final GrammaticalRelation AUX_PASSIVE_MODIFIER
  = new GrammaticalRelation(Language.Chinese, "pass", "passive",
                            MODIFIER, "VP", tregexCompiler,
                            new String[]{
                              "VP < SB|LB=target"
                            });


  /**
   * The "ba" grammatical relation.
   */
 public static final GrammaticalRelation BA =
   new GrammaticalRelation(Language.Chinese, "ba", "ba",
                           DEPENDENT, "VP|IP", tregexCompiler,
           "VP|IP < BA=target ");


  /**
   * The "temporal modifier" grammatical relation.
   * (IP
   *           (VP
   *             (NP (NT 以前))
   *             (ADVP (AD 不))
   *             (ADVP (AD 曾))
   *             (VP (VV 遇到) (AS 过))))
   *(VP
   *     (LCP
   *       (NP (NT 近年))
   *       (LC 来))
   *     (VP
   *       (VCD (VV 颁布) (VV 实行))
   * {@code tmod } (遇到, 以前)
   */
  public static final GrammaticalRelation TEMPORAL_MODIFIER =
    new GrammaticalRelation(Language.Chinese,
      "tmod", "temporal modifier",
      MODIFIER, "VP|IP", tregexCompiler,
            "VP|IP < (NP=target < NT !.. /^VC$/ $++  VP)");


  /* This rule actually matches nothing.
     There's another tmod rule. This is removed for now.
     (pichuan) Sun Mar  8 18:22:40 2009
  */
  /*
  public static final GrammaticalRelation TEMPORAL_MODIFIER =
    new GrammaticalRelation(Language.Chinese,
      "tmod", "temporal modifier",
      TemporalModifierGRAnnotation.class, MODIFIER, "VP|IP|ADJP", tregexCompiler,
      new String[]{
        " VC|VE ! >> VP|ADJP < NP=target < NT",
        "VC|VE !>>IP <( NP=target < NT $++ VP !< VC|VE )"
      });
  public static class TemporalModifierGRAnnotation
    extends GrammaticalRelationAnnotation { }
  */
  /**
   * The "temporal clause" grammatical relation.
   *(VP(PP (P 等) (LCP (IP
   *                           (VP (VV 积累) (AS 了)
   *                             (NP (NN 经验))))
   *                         (LC 以后)))
   *                     (ADVP (AD 再))
   *                     (VP (VV 制定)
   *                       (NP (NN 法规) (NN 条例))))
   *                  (PU ”)))
   *               (DEC 的))
   *             (NP (NN 做法)))))))
   * {@code lccomp } (以后, 积累)
   */
  // pichuan: previously "tclaus"
  public static final GrammaticalRelation TIME =
    new GrammaticalRelation(Language.Chinese, "lccomp", "clausal complement of localizer",
                            MODIFIER, "LCP", tregexCompiler,
            "/LCP/ < ( IP=target $+ LC )");


  /**
   * The "relative clause modifier" grammatical relation.
   * (CP (IP (VP (NP (NT 以前))
   *             (ADVP (AD 不))
   *             (ADVP (AD 曾))
   *             (VP (VV 遇到) (AS 过))))
   *         (DEC 的))
   * (NP
   *   (NP
   *     (ADJP (JJ 新))
   *     (NP (NN 情况)))
   *   (PU 、)
   *   (NP
   *     (ADJP (JJ 新))
   *     (NP (NN 问题)))))))
   * (PU 。)))
   * the new problem that has not been encountered.
   * {@code rcmod } (问题, 遇到)
   */
  public static final GrammaticalRelation RELATIVE_CLAUSE_MODIFIER =
    new GrammaticalRelation(Language.Chinese, "rcmod", "relative clause modifier",
                            MODIFIER, "NP", tregexCompiler,
                              // TODO: we should figure out various ways to improve this pattern to
                              // improve both its precision and recall
                              "NP  $++ (CP=target ) > NP ",
                              "NP  < ( CP=target $++ NP  )"
                            );


  /**
   * The "number modifier" grammatical relation.
   * (NP
   *         (NP (NN 拆迁) (NN 工作))
   *         (QP (CD 若干))
   *         (NP (NN 规定)))
   * nummod(件-24, 七十一-23)
   * nummod(规定-48, 若干-47)
   */
  public static final GrammaticalRelation NUMERIC_MODIFIER =
    new GrammaticalRelation(Language.Chinese, "nummod", "numeric modifier",
                            MODIFIER,
                            "QP|NP", tregexCompiler,
            "QP < CD=target",
            "NP < ( QP=target !<< CLP )");


  /**
   * The "ordnumber modifier" grammatical relation.
   */
  public static final GrammaticalRelation ODNUMERIC_MODIFIER =
    new GrammaticalRelation(Language.Chinese, "ordmod", "numeric modifier",
                            MODIFIER,
                            "NP|QP", tregexCompiler,
            "NP < QP=target < ( OD !$+ CLP )",
            "QP < (OD=target $+ CLP)");


  /**
   * The "classifier modifier" grammatical relation.
   * (QP (CD 七十一)
   *           (CLP (M 件)))
   *         (NP (NN 法规性) (NN 文件)))))
   * {@code clf } (文件-26, 件-24)
   */
  public static final GrammaticalRelation NUMBER_MODIFIER =
    new GrammaticalRelation(Language.Chinese,
      "clf", "classifier modifier",
      MODIFIER, "^NP|DP|QP", tregexCompiler,
            "NP|QP < ( QP  =target << M $++ NN|NP|QP)",
            "DP < ( DT $+ CLP=target )");


  /**
   * The "noun compound modifier" grammatical relation.
   * Example:
   * (ROOT
   *   (IP
   *     (NP
   *       (NP (NR 上海) (NR 浦东))
   *       (NP (NN 开发)
   *         (CC 与)
   *         (NN 法制) (NN 建设)))
   *     (VP (VV 同步))))
   * {@code nn } (浦东, 上海)
   */
  public static final GrammaticalRelation NOUN_COMPOUND_MODIFIER =
    new GrammaticalRelation(Language.Chinese,
      "nn", "nn modifier",
      MODIFIER, "^NP", tregexCompiler,
            "NP < (NN|NR|NT=target $+ NN|NR|NT)",
            "NP < (NN|NR|NT $+ FW=target)",
            " NP <  (NP=target !$+ PU|CC $++ NP|PRN )");


  /**
   * The "adjetive modifier" grammatical relation.
   *         (NP
   *           (ADJP (JJ 新))
   *           (NP (NN 情况)))
   *         (PU 、)
   *         (NP
   *           (ADJP (JJ 新))
   *           (NP (NN 问题)))))))
   * {@code amod } (情况-34, 新-33)
   */
  public static final GrammaticalRelation ADJECTIVAL_MODIFIER =
    new GrammaticalRelation(Language.Chinese,
      "amod", "adjectival modifier",
      MODIFIER, "NP|CLP|QP", tregexCompiler,
            "NP|CLP|QP < (ADJP=target $++ NP|CLP|QP ) ");


  /**
   * The "adverbial modifier" grammatical relation.
   * (VP
   *     (ADVP (AD 基本))
   *     (VP (VV 做到) (AS 了)))
   * advmod(做到-74, 基本-73)
   */
  public static final GrammaticalRelation ADVERBIAL_MODIFIER =
    new GrammaticalRelation(Language.Chinese,
      "advmod", "adverbial modifier",
      MODIFIER,
      "VP|ADJP|IP|CP|PP|NP|QP", tregexCompiler,
            "VP|ADJP|IP|CP|PP|NP < (ADVP=target !< (AD < /^(\\u4e0d|\\u6CA1|\\u6CA1\\u6709)$/))",
            "VP|ADJP < AD|CS=target",
            "QP < (ADVP=target $+ QP)",
            "QP < ( QP $+ ADVP=target)");


  /**
   * The "verb modifier" grammatical relation.
   */
  public static final GrammaticalRelation IP_MODIFIER =
    new GrammaticalRelation(Language.Chinese,
      "vmod", "participle modifier",
      MODIFIER, "NP", tregexCompiler,
            "NP < IP=target ");


  /**
   * The "parenthetical modifier" grammatical relation.
   */
  public static final GrammaticalRelation PRN_MODIFIER =
    new GrammaticalRelation(Language.Chinese, "prnmod", "prn odifier",
                            MODIFIER, "NP", tregexCompiler,
            "NP < PRN=target ");


  /**
   * The "negative modifier" grammatical relation.
   *(VP
   *             (NP (NT 以前))
   *             (ADVP (AD 不))
   *             (ADVP (AD 曾))
   *             (VP (VV 遇到) (AS 过))))
   * neg(遇到-30, 不-28)
   */
  public static final GrammaticalRelation NEGATION_MODIFIER =
    new GrammaticalRelation(Language.Chinese,
      "neg", "negation modifier",
      ADVERBIAL_MODIFIER, "VP|ADJP|IP", tregexCompiler,
            "VP|ADJP|IP < (AD|VV=target < /^(\\u4e0d|\\u6CA1|\\u6CA1\\u6709)$/)",
            "VP|ADJP|IP < (ADVP|VV=target < (AD < /^(\\u4e0d|\\u6CA1|\\u6CA1\\u6709)$/))");


  /**
   * The "determiner modifier" grammatical relation.
   * (NP
   *           (DP (DT 这些))
   *           (NP (NN 经济) (NN 活动)))
   * det(活动-61, 这些-59)
   */
  public static final GrammaticalRelation DETERMINER =
    new GrammaticalRelation(Language.Chinese, "det", "determiner",
                            MODIFIER, "^NP|DP", tregexCompiler,
                              "/^NP/ < (DP=target $++ NP )"
                              //"DP < DT < QP=target"
                            );


  /*
   * The "possession modifier" grammatical relation.
   */
  // Fri Feb 20 15:40:13 2009 (pichuan)
  // I think this "poss" relation is just WRONG.
  // DEC is a complementizer or a nominalizer,
  // this rule probably originally want to capture "DEG".
  // But it seems like it's covered by "assm" (associative marker).
  /*
  public static final GrammaticalRelation POSSESSION_MODIFIER =
    new GrammaticalRelation(Language.Chinese,
      "poss", "possession modifier",
      PossessionModifierGRAnnotation.class, MODIFIER, "NP", tregexCompiler,
      new String[]{
        "NP < ( PN=target $+ DEC $+  NP )"
      });
  public static class PossessionModifierGRAnnotation
    extends GrammaticalRelationAnnotation { }
  */

  /*
   * The "possessive marker" grammatical relation.
   */
  // Similar to the comments to "poss",
  // I think this relation is wrong and will not appear.
  /*
  public static final GrammaticalRelation POSSESSIVE_MODIFIER =
    new GrammaticalRelation(Language.Chinese, "possm", "possessive marker",
                            PossessiveModifierGRAnnotation.class,
                            MODIFIER, "NP", tregexCompiler,
                            new String[]{
                              "NP < ( PN $+ DEC=target ) "
                            });
  public static class PossessiveModifierGRAnnotation
    extends GrammaticalRelationAnnotation { }
  */

  /**
   * The "dvp marker" grammatical relation.
   *         (DVP
   *           (VP (VA 简单))
   *           (DEV 的))
   *         (VP (VV 采取)
   * dvpm(简单-7, 的-8)
   */
   public static final GrammaticalRelation DVP_MODIFIER =
     new GrammaticalRelation(Language.Chinese, "dvpm", "dvp marker",
                             MODIFIER, "DVP", tregexCompiler,
             "DVP < (__ $+ DEV=target ) ");


  /**
   * The "dvp modifier" grammatical relation.
   * <code>
   * <pre>
   * (ADVP (AD 不))
   *    (VP (VC 是)
   *       (VP
   *         (DVP
   *           (VP (VA 简单))
   *           (DEV 的))
   *         (VP (VV 采取)
   *
   * dvpmod(采取-9, 简单-7)
   * </pre>
   * </code>
   */
  public static final GrammaticalRelation DVPM_MODIFIER =
    new GrammaticalRelation(Language.Chinese, "dvpmod", "dvp modifier",
                            MODIFIER, "VP", tregexCompiler,
            " VP < ( DVP=target $+ VP) ");


  /**
   * The "associative marker" grammatical relation.
   * <code>
   * <pre>
   *       (NP (DNP
   *             (NP (NP (NR 浦东))
   *                     (NP (NN 开发)))
   *                 (DEG 的))
   *             (ADJP (JJ 有序))
   *             (NP (NN 进行)))
   *
   * assm(开发-31, 的-32)
   * </pre>
   * </code>
   */
  public static final GrammaticalRelation ASSOCIATIVE_MODIFIER =
    new GrammaticalRelation(Language.Chinese,
      "assm", "associative marker",
      MODIFIER, "DNP", tregexCompiler,
            " DNP < ( __ $+ DEG=target ) ");


  /**
   * The "associative modifier" grammatical relation.
   *    (NP
   *                 (NP (NR 深圳) (ETC 等))
   *                 (NP (NN 特区))))
   *             (DEG 的))
   *           (NP (NN 经验) (NN 教训))))
   * assmod(教训-40, 特区-37)
   */
  public static final GrammaticalRelation ASSOCIATIVEM_MODIFIER =
    new GrammaticalRelation(Language.Chinese,
      "assmod", "associative modifier",
      MODIFIER, "NP|QP", tregexCompiler,
            "NP|QP < ( DNP =target $++ NP|QP ) ");


  /**
   * The "prepositional modifier" grammatical relation.
   *(IP
   *  (PP (P 对)
   *   (NP (PN 此)))
   * (PU ，)
   * (NP (NR 浦东))
   * (VP
   *   (VP
   *     (ADVP (AD 不))
   *     (VP (VC 是)
   *       (VP
   *         (DVP
   *           (VP (VA 简单))
   *           (DEV 的))
   *         (VP (VV 采取)
   * {@code prep } (采取-9, 对-1)
   */
  public static final GrammaticalRelation PREPOSITIONAL_MODIFIER =
    new GrammaticalRelation(Language.Chinese,
      "prep", "prepositional modifier",
      MODIFIER, "^NP|VP|IP", tregexCompiler,
            "/^NP/ < /^PP/=target",
            "VP < /^PP/=target",
            "IP < /^PP/=target ");


  /**
   * The "clause modifier of a preposition" grammatical relation.
   * (PP (P 因为)
   *       (IP
   *         (VP
   *           (VP
   *             (ADVP (AD 一))
   *             (VP (VV 开始)))
   *           (VP
   *             (ADVP (AD 就))
   *             (ADVP (AD 比较))
   *             (VP (VA 规范)))))))
   * {@code pccomp } (因为-18, 开始-20)
   */
  // pichuan: previously "clmpd"
  public static final GrammaticalRelation CL_MODIFIER =
    new GrammaticalRelation(Language.Chinese,
      "pccomp", "clause complement of a preposition",
      MODIFIER, "^PP|IP", tregexCompiler,
            "PP < (P $+ IP|VP =target)",
            "IP < (CP=target $++ VP)");


  /**
   * The "prepositional localizer modifier" grammatical relation.
   * (PP (P 在)
   *     (LCP
   *       (NP
   *         (DP (DT 这)
   *             (CLP (M 片)))
   *         (NP (NN 热土)))
   *       (LC 上)))
   * plmod(在-25, 上-29)
   */
  public static final GrammaticalRelation PREPOSITIONAL_LOC_MODIFIER =
    new GrammaticalRelation(Language.Chinese,
      "plmod", "prepositional localizer modifier",
      MODIFIER, "PP", tregexCompiler,
            "PP < ( P $++ LCP=target )");


  /**
   * The "aspect marker" grammatical relation.
   * (VP
   *     (ADVP (AD 基本))
   *     (VP (VV 做到) (AS 了)
   * {@code asp } (做到,了)
   */
  public static final GrammaticalRelation PREDICATE_ASPECT =
    new GrammaticalRelation(Language.Chinese, "asp", "aspect",
                            MODIFIER, "VP", tregexCompiler,
            "VP < ( /^V*/ $+ AS=target)");


  /**
   * The "participial modifier" grammatical relation.
   */
  public static final GrammaticalRelation PART_VERB =
    new GrammaticalRelation(Language.Chinese,
      "prtmod", "particle verb",
      MODIFIER, "VP|IP", tregexCompiler,
            "VP|IP < ( MSP=target )");


  /**
   * The "etc" grammatical relation.
   *(NP
   *                 (NP (NN 经济) (PU 、) (NN 贸易) (PU 、) (NN 建设) (PU 、) (NN 规划) (PU 、) (NN 科技) (PU 、) (NN 文教) (ETC 等))
   *                 (NP (NN 领域)))))
   * {@code etc } (办法-70, 等-71)
   */

  public static final GrammaticalRelation ETC =
    new GrammaticalRelation(Language.Chinese, "etc", "ETC",
                            MODIFIER, "^NP", tregexCompiler,
            "/^NP/ < (NN|NR . ETC=target)");


  /**
   * The "semantic dependent" grammatical relation.
   */
  public static final GrammaticalRelation SEMANTIC_DEPENDENT =
    new GrammaticalRelation(Language.Chinese, "sdep", "semantic dependent",
                            DEPENDENT);


  /**
   * The "xsubj" grammatical relation.
   *(IP
   *           (NP (PN 有些))
   *           (VP
   *             (VP
   *               (ADVP (AD 还))
   *               (ADVP (AD 只))
   *               (VP (VC 是)
   *                 (NP
   *                   (ADJP (JJ 暂行))
   *                   (NP (NN 规定)))))
   *             (PU ，)
   *             (VP (VV 有待)
   *               (IP
   *                 (VP
   *                   (PP (P 在)
   *                     (LCP
   *                       (NP (NN 实践))
   *                       (LC 中)))
   *                   (ADVP (AD 逐步))
   *                   (VP (VV 完善))))))))))
   * {@code xsubj } (完善-26, 有些-14)
   */
  public static final GrammaticalRelation CONTROLLED_SUBJECT =
    new GrammaticalRelation(Language.Chinese,
      "xsubj", "controlled subject",
      SEMANTIC_DEPENDENT, "VP", tregexCompiler,
            "VP !< NP < VP > (IP !$- NP !< NP !>> (VP < VC ) >+(VP) (VP $-- NP=target))");


  private static final GrammaticalRelation[] rawValues = {
    //ADJECTIVAL_COMPLEMENT,
    ADJECTIVAL_MODIFIER,
    ADVERBIAL_MODIFIER,
    ARGUMENT,
    ASSOCIATIVEM_MODIFIER,
    ASSOCIATIVE_MODIFIER,
    ATTRIBUTIVE,
    AUX_MODIFIER,
    AUX_PASSIVE_MODIFIER,
    BA,
    CLAUSAL_COMPLEMENT,
    CLAUSAL_SUBJECT,
    CL_MODIFIER,
    COMPLEMENT,
    COMPLEMENTIZER,
    CONJUNCT,
    CONTROLLED_SUBJECT,
    COORDINATION,
    DEPENDENT,
    DETERMINER,
    DIRECT_OBJECT,
    DVPM_MODIFIER,
    DVP_MODIFIER,
    ETC,
    GOVERNOR,
    //INDIRECT_OBJECT,
    IP_MODIFIER,
    LC_COMPLEMENT,
    MODAL_VERB,
    MODIFIER,
    NEGATION_MODIFIER,
    NOMINAL_PASSIVE_SUBJECT,
    NOMINAL_SUBJECT,
    NOUN_COMPOUND_MODIFIER,
    NUMBER_MODIFIER,
    NUMERIC_MODIFIER,
    OBJECT,
    ODNUMERIC_MODIFIER,
    PART_VERB,
    //POSSESSION_MODIFIER,
    //POSSESSIVE_MODIFIER,
    //PREDICATE,
    PREDICATE_ASPECT,
    PREPOSITIONAL_LOC_MODIFIER,
    PREPOSITIONAL_MODIFIER,
    PREPOSITIONAL_OBJECT,
    PRN_MODIFIER,
    PUNCTUATION,
    RANGE,
    RELATIVE_CLAUSE_MODIFIER,
    RES_VERB,
    SEMANTIC_DEPENDENT,
    SUBJECT,
    TEMPORAL_MODIFIER,
    TIME,
    TIME_POSTPOSITION,
    TOP_SUBJECT,
    VERB_COMPOUND,
    XCLAUSAL_COMPLEMENT,
  };

  private static final List<GrammaticalRelation> values = new ArrayList<>();
    // Cache frequently used views of the values list
  private static final List<GrammaticalRelation> synchronizedValues =
    Collections.synchronizedList(values);

  public static final Set<GrammaticalRelation> universalValues = new HashSet<>();

  /** Map from Chinese GrammaticalRelation short names to their corresponding
   * GrammaticalRelation objects.
   */
  public static final Map<String, GrammaticalRelation> shortNameToGRel = new ConcurrentHashMap<>();

  static {
    for (int i = 0; i < rawValues.length; i++) {
      GrammaticalRelation gr = rawValues[i];
      synchronizedValues.add(gr);
    }

    valuesLock().lock();
    try {
      for (GrammaticalRelation gr : ChineseGrammaticalRelations.values()) {
        shortNameToGRel.put(gr.getShortName(), gr);
      }
    } finally {
      valuesLock().unlock();
    }
  }

}
