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

package edu.stanford.nlp.trees.international.pennchinese;

import java.util.*;

import edu.stanford.nlp.util.ErasureUtils;
import edu.stanford.nlp.util.Filters;
import junit.framework.TestCase;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.util.Pair;

/**
 * Test cases for conversion of Chinese TreeBank to Universal
 * Dependencies with Chinese characteristics.
 * This code is adapted from EnglishGrammaticalStructureTest.java
 *
 * @author Percy Liang
 * @author Peng Qi
 */
public class UniversalChineseGrammaticalStructureTest extends TestCase {

  // Return a string which is the concatenation of |items|, with a new line after each line.
  // "a", "b" => "a\nb\n"
  private static String C(String... items) {
    StringBuilder out = new StringBuilder();
    for (String x : items) {
      out.append(x);
      out.append('\n');
    }
    return out.toString();
  }

  // Create a new example
  private static Pair<String, String> T(String tree, String ans) {
    return new Pair<String, String>(tree, ans);
  }


  /**
   * Tests that we can extract the basic grammatical relations correctly from
   * some hard-coded trees.
   *
   * Sentence examples from the manual to at least test each relation.
   *
   */
  public void testBasicRelations() {
    Pair<String, String>[] examples = ErasureUtils.uncheckedCast(new Pair[] {
      // Gloss: Shanghai Pudong de orderly advance
      T("(NP (DNP (NP (NP (NR 浦东)) (NP (NN 开发))) (DEG 的)) (ADJP (JJ 有序)) (NP (NN 进行)))",
        C("nmod:assmod(开发-2, 浦东-1)", "nmod:assmod(进行-5, 开发-2)", "case(开发-2, 的-3)", "amod(进行-5, 有序-4)", "root(ROOT-0, 进行-5)")),

      // Gloss: Shanghai Pudong expansion and legal-system synchronizing
      T("(ROOT (IP (NP (NP (NR 上海) (NR 浦东)) (NP (NN 开发) (CC 与) (NN 法制) (NN 建设))) (VP (VV 同步))))",
        C("name(浦东-2, 上海-1)", "nmod:assmod(建设-6, 浦东-2)", "conj(建设-6, 开发-3)", "cc(建设-6, 与-4)", "compound:nn(建设-6, 法制-5)", "nsubj(同步-7, 建设-6)", "root(ROOT-0, 同步-7)")),

      // Gloss: this-year
      T("(LCP (NP (NT 近年)) (LC 来))",
        C("root(ROOT-0, 近年-1)", "case(近年-1, 来-2)")),

      // Gloss: according country and Shanghai de relevant law
      T("(PP (P 根据) (NP (DNP (NP (NP (NN 国家)) (CC 和) (NP (NR 上海市))) (DEG 的)) (ADJP (JJ 有关)) (NP (NN 规定))))",
        C("case(规定-7, 根据-1)", "conj(上海市-4, 国家-2)", "cc(上海市-4, 和-3)", "nmod:assmod(规定-7, 上海市-4)", "case(上海市-4, 的-5)", "amod(规定-7, 有关-6)", "root(ROOT-0, 规定-7)")),

      // Gloss: building is expand Shanghai de primary economic activity
      T("(IP (NP (NN 建筑)) (VP (VC 是) (NP (CP (IP (VP (VV 开发) (NP (NR 浦东)))) (DEC 的)) (QP (CD 一) (CLP (M 项))) (ADJP (JJ 主要)) (NP (NN 经济) (NN 活动)))))",
        C("nsubj(活动-10, 建筑-1)", "cop(活动-10, 是-2)", "acl(活动-10, 开发-3)", "dobj(开发-3, 浦东-4)", "mark(开发-3, 的-5)", "nummod(活动-10, 一-6)", "mark:clf(一-6, 项-7)", "amod(活动-10, 主要-8)", "compound:nn(活动-10, 经济-9)", "root(ROOT-0, 活动-10)")),

      // Gloss: nickel has-been named modern industry de vitamins
      T("(IP (NP (NN 镍)) (VP (SB 被) (VP (VV 称作) (NP (PU “) (DNP (NP (ADJP (JJ 现代)) (NP (NN 工业))) (DEG 的)) (NP (NN 维生素)) (PU ”)))))",
        C("nsubjpass(称作-3, 镍-1)", "auxpass(称作-3, 被-2)", "root(ROOT-0, 称作-3)", "punct(维生素-8, “-4)", "amod(工业-6, 现代-5)", "nmod:assmod(维生素-8, 工业-6)", "case(工业-6, 的-7)", "dobj(称作-3, 维生素-8)", "punct(维生素-8, ”-9)")),

      // Gloss: once revealed then was included legal-system path
      T("(IP (VP (VP (ADVP (AD 一)) (VP (VV 出现))) (VP (ADVP (AD 就)) (VP (SB 被) (VP (VV 纳入) (NP (NN 法制) (NN 轨道)))))))))))",
        C("advmod(出现-2, 一-1)", "root(ROOT-0, 出现-2)", "advmod(纳入-5, 就-3)", "auxpass(纳入-5, 被-4)", "conj(出现-2, 纳入-5)", // todo: was dep
                "compound:nn(轨道-7, 法制-6)", "dobj(纳入-5, 轨道-7)")),

      T("(IP (NP (NP (NR 格林柯尔)) (NP (NN 制冷剂)) (PRN (PU （) (NP (NR 中国)) (PU ）)) (ADJP (JJ 有限)) (NP (NN 公司))) (VP (VC 是) (NP (CP (CP (IP (NP (NP (NR 格林柯尔) (NN 集团) (NR 北美) (NN 公司)) (CC 与) (NP (NP (NR 中国) (NR 天津)) (NP (NN 开发区)) (ADJP (JJ 总)) (NP (NN 公司))) (CC 和) (NP (NP (NR 中国)) (NP (NR 南方)) (NP (NN 证券)) (ADJP (JJ 有限)) (NP (NN 公司)))) (VP (VV 合建))) (DEC 的))) (ADJP (JJ 合资)) (NP (NN 企业)))) (PU 。))",
        C("compound:nn(公司-7, 格林柯尔-1)",
                "compound:nn(公司-7, 制冷剂-2)",
                "punct(中国-4, （-3)",
                "parataxis:prnmod(公司-7, 中国-4)",
                "punct(中国-4, ）-5)",
                "amod(公司-7, 有限-6)",
                "nsubj(企业-28, 公司-7)",
                "cop(企业-28, 是-8)",
                "compound:nn(公司-12, 格林柯尔-9)",
                "compound:nn(公司-12, 集团-10)",
                "compound:nn(公司-12, 北美-11)",
                "conj(公司-24, 公司-12)",
                "cc(公司-24, 与-13)",
                "name(天津-15, 中国-14)",
                "compound:nn(公司-18, 天津-15)",
                "compound:nn(公司-18, 开发区-16)",
                "amod(公司-18, 总-17)",
                "conj(公司-24, 公司-18)",
                "cc(公司-24, 和-19)",
                "compound:nn(公司-24, 中国-20)",
                "compound:nn(公司-24, 南方-21)",
                "compound:nn(公司-24, 证券-22)",
                "amod(公司-24, 有限-23)",
                "nsubj(合建-25, 公司-24)",
                "acl(企业-28, 合建-25)",
                "mark(合建-25, 的-26)",
                "amod(企业-28, 合资-27)",
                "root(ROOT-0, 企业-28)",
                "punct(企业-28, 。-29)")),

       T("(IP (NP (NR 汕头) (NN 机场)) (VP (VV 开通) (NP (NN 国际) (NN 国内) (NN 航线)) (QP (CD 四十四) (CLP (M 条)))) (PU 。))",
               C("compound:nn(机场-2, 汕头-1)",
                 "nsubj(开通-3, 机场-2)",
                 "root(ROOT-0, 开通-3)",
                 "compound:nn(航线-6, 国际-4)",
                 "compound:nn(航线-6, 国内-5)",
                 "dobj(开通-3, 航线-6)",
                 "nmod:range(开通-3, 四十四-7)",
                 "mark:clf(四十四-7, 条-8)",
                 "punct(开通-3, 。-9)")),

        T("(VP (NP (NT 以前)) (ADVP (AD 不)) (ADVP (AD 曾)) (VP (VV 遇到) (AS 过))))",
                    C("nmod:tmod(遇到-4, 以前-1)",
                      "neg(遇到-4, 不-2)",
                      "advmod(遇到-4, 曾-3)",
                      "root(ROOT-0, 遇到-4)",
                      "aux:asp(遇到-4, 过-5)")),

       // Test cases from CTB9, hand-annotated and might be unreliable (pengqi)
       T("( (IP (ADVP (AD 首先)) (NP (PN 我们)) (VP (ADVP (AD 先)) (VP (VV 来) (VP (VV 关心) (NP (DNP (NP (NR 南斯拉夫)) (NP (NN 总统) (NN 大选)) (DEG 的)) (NP (NN 状况)))))) (PU 。))) ",
               C("advmod(关心-5, 首先-1)",
                 "nsubj(关心-5, 我们-2)",
                 "advmod(关心-5, 先-3)",
                 "xcomp(关心-5, 来-4)",
                 "root(ROOT-0, 关心-5)",
                 "dep(大选-8, 南斯拉夫-6)",  // should be nmod:assmod or compound:nn
                 "compound:nn(大选-8, 总统-7)",
                 "nmod:assmod(状况-10, 大选-8)",
                 "case(大选-8, 的-9)",
                 "dobj(关心-5, 状况-10)",
                 "punct(关心-5, 。-11)")),

       // note: the original parentheses in this example don't seem to make sense...
       T("( (IP (ADVP (AD 而)) (NP (NR 英国) (NN 外交部)) (VP (NP (NT ５号)) (ADVP (AD 则)) (PP (P 对) (IP (NP (NR 南国) (NN 宪法) (NN 法庭)) (VP (VV 裁决) (IP (NP (NR 南国)) (VP (ADVP (AD 将)) (ADVP (AD 重新)) (VP (VV 举行) (NP (NN 总统) (NN 选举)))))))) (VP (VV 表示) (IP (VP (VV 谴责))))) (PU 。)))",
               C("advmod(表示-17, 而-1)",
                 "compound:nn(外交部-3, 英国-2)", // should be nmod:assmod?
                 "nsubj(表示-17, 外交部-3)",
                 "nmod:tmod(表示-17, ５号-4)",
                 "advmod(表示-17, 则-5)",
                 "case(裁决-10, 对-6)",
                 "compound:nn(法庭-9, 南国-7)",  // should be nmod:assmod?
                 "compound:nn(法庭-9, 宪法-8)",
                 "nsubj(裁决-10, 法庭-9)",
                 "nmod:prep(表示-17, 裁决-10)",
                 "nsubj(举行-14, 南国-11)",
                 "advmod(举行-14, 将-12)",
                 "advmod(举行-14, 重新-13)",
                 "ccomp(裁决-10, 举行-14)",
                 "compound:nn(选举-16, 总统-15)",
                 "dobj(举行-14, 选举-16)",
                 "root(ROOT-0, 表示-17)",
                 "ccomp(表示-17, 谴责-18)",
                 "punct(表示-17, 。-19)")),

       T("( (IP (NP (NP (NP (NR 菲律宾)) (NP (NN 总统))) (NP (NR 埃斯特拉达))) (VP (NP (NT ２号)) (PP (P 透过) (NP (NP (NR 马尼拉)) (NP (NN 当地) (NN 电台)))) (VP (VSB (VV 宣布) (VV 说)) (PU ，) (IP (PP (P 在) (LCP (NP (CP (CP (IP (VP (ADVP (AD 仍)) (VP (VV 遭到) (IP (NP (CP (CP (IP (VP (VA 激进))) (DEC 的))) (NP (NP (NP (NN 回教) (NN 阿卜)) (NP (NR 沙耶夫))) (NP (NN 组织)))) (VP (VV 羁押) (PP (P 在) (NP (NP (NP (NR 非国)) (NP (NN 南部))) (NP (NR 和落岛))))))))) (DEC 的))) (NP (QP (CD １６) (CLP (M 名))) (NP (NN 人质)))) (LC 当中))) (PU ，) (NP (NN 军方)) (VP (ADVP (AD 已经)) (VP (VRD (VV 营救) (VV 出)) (AS 了) (NP (QP (CD １１) (CLP (M 名))) (NP (NP (NR 菲律宾)) (NP (NN 人质))))))))) (PU 。))) ",
               C("nmod:assmod(总统-2, 菲律宾-1)",
                 "appos(埃斯特拉达-3, 总统-2)",
                 "nsubj(说-10, 埃斯特拉达-3)",
                 "nmod:tmod(说-10, ２号-4)",
                 "case(电台-8, 透过-5)",
                 "nmod:assmod(电台-8, 马尼拉-6)",
                 "compound:nn(电台-8, 当地-7)",
                 "nmod:prep(说-10, 电台-8)",
                 "compound:vc(说-10, 宣布-9)",
                 "root(ROOT-0, 说-10)",
                 "punct(说-10, ，-11)",
                 "case(人质-29, 在-12)",
                 "advmod(遭到-14, 仍-13)",
                 "acl(人质-29, 遭到-14)",
                 "amod(组织-20, 激进-15)",
                 "mark(激进-15, 的-16)",
                 "compound:nn(阿卜-18, 回教-17)",
                 "appos(沙耶夫-19, 阿卜-18)",
                 "compound:nn(组织-20, 沙耶夫-19)",
                 "nsubj(羁押-21, 组织-20)",
                 "ccomp(遭到-14, 羁押-21)",
                 "case(和落岛-25, 在-22)",
                 "nmod:assmod(南部-24, 非国-23)",
                 "nmod(和落岛-25, 南部-24)",
                 "nmod:prep(羁押-21, 和落岛-25)",
                 "mark(遭到-14, 的-26)",
                 "nummod(人质-29, １６-27)",
                 "mark:clf(１６-27, 名-28)",
                 "nmod:prep(营救-34, 人质-29)",
                 "case(人质-29, 当中-30)",
                 "punct(营救-34, ，-31)",
                 "nsubj(营救-34, 军方-32)",
                 "advmod(营救-34, 已经-33)",
                 "ccomp(说-10, 营救-34)",
                 "advmod:rcomp(营救-34, 出-35)",
                 "aux:asp(营救-34, 了-36)",
                 "nummod(人质-40, １１-37)",
                 "mark:clf(１１-37, 名-38)",
                 "nmod:assmod(人质-40, 菲律宾-39)",
                 "dobj(营救-34, 人质-40)",
                 "punct(说-10, 。-41)")),
       T("( (IP (IP (NP (NR 埃斯特拉达)) (LCP (IP (VP (NP (NT ２号)) (VP (VV 接受) (IP (NP (NP (NP (NR 菲律宾)) (QP (CD 一) (CLP (M 家))) (NP (NN 电台)))) (VP (VV 访问)))))) (LC 时)) (VP (VV 说) (PU ，) (IP (NP (NP (NN 三军) (NN 参谋总长)) (NP (NR 雷耶丝))) (VP (VP (VV 打) (NP (NN 电话))) (VP (PP (P 向) (NP (PN 他))) (VP (VSB (VV 报告) (VV 说)) (PU ，) (IP (NP (NP (NR 阿美达)) (CC 和) (NP (PN 其) (NN 随行者))) (VP (ADVP (AD 已经)) (VP (VV 在) (NP (DNP (NP (NR 和落岛) (NR 塔里班)) (DEG 的)) (NP (NR 马巴)))))))))))) (PU …) (PU …) (IP (VP (VV 继续) (VP (VC 是) (NP (CP (CP (IP (VP (VA 重要))) (DEC 的))) (NP (NN 国际) (NN 新闻)))))) (PU 。))) ",
               C("nsubj(说-10, 埃斯特拉达-1)",
                 "nmod:tmod(接受-3, ２号-2)",
                 "advcl:loc(说-10, 接受-3)",
                 "nmod(电台-7, 菲律宾-4)",
                 "nummod(电台-7, 一-5)",
                 "mark:clf(一-5, 家-6)",
                 "nsubj(访问-8, 电台-7)",
                 "ccomp(接受-3, 访问-8)",
                 "case(接受-3, 时-9)",
                 "root(ROOT-0, 说-10)",
                 "punct(说-10, ，-11)",
                 "compound:nn(参谋总长-13, 三军-12)",
                 "appos(雷耶丝-14, 参谋总长-13)",
                 "nsubj(打-15, 雷耶丝-14)",
                 "ccomp(说-10, 打-15)",
                 "dobj(打-15, 电话-16)",
                 "case(他-18, 向-17)",
                 "nmod:prep(说-20, 他-18)",
                 "compound:vc(说-20, 报告-19)",
                 "conj(打-15, 说-20)",
                 "punct(说-20, ，-21)",
                 "conj(随行者-25, 阿美达-22)",
                 "cc(随行者-25, 和-23)",
                 "nmod:poss(随行者-25, 其-24)",
                 "nsubj(在-27, 随行者-25)",
                 "advmod(在-27, 已经-26)",
                 "ccomp(说-20, 在-27)",
                 "name(塔里班-29, 和落岛-28)",
                 "nmod:assmod(马巴-31, 塔里班-29)",
                 "case(塔里班-29, 的-30)",
                 "dobj(在-27, 马巴-31)",
                 "punct(说-10, …-32)",
                 "punct(说-10, …-33)",
                 "xcomp(新闻-39, 继续-34)",
                 "cop(新闻-39, 是-35)",
                 "amod(新闻-39, 重要-36)",
                 "mark(重要-36, 的-37)",
                 "compound:nn(新闻-39, 国际-38)",
                 "conj(说-10, 新闻-39)",
                 "punct(说-10, 。-40)")),

       T("( (IP (IP (NP (NN 示威) (NN 人群)) (VP (VP (VV 挤进) (AS 了) (NP (DNP (NP (NR 贝尔格勒)) (DEG 的)) (NP (DP (DT 各) (CLP (M 个))) (ADJP (JJ 主要)) (NP (NN 街道) (CC 和) (NN 公园))))) (VP (VV 进行) (NP (NN 抗议))))) (PU ，) (IP (ADVP (AD 而)) (NP (NP (NR 南国)) (NP (NN 国会) (NN 大厦))) (VP (VC 是) (NP (CP (CP (IP (VP (VP (VV 抗议)))) (DEC 的))) (NP (ADJP (JJ 主要)) (NP (NN 中心)))))) (PU 。))) ",
               C("compound:nn(人群-2, 示威-1)",
                 "nsubj(挤进-3, 人群-2)",
                 "root(ROOT-0, 挤进-3)",
                 "aux:asp(挤进-3, 了-4)",
                 "nmod:assmod(公园-12, 贝尔格勒-5)",
                 "case(贝尔格勒-5, 的-6)",
                 "det(公园-12, 各-7)",
                 "mark:clf(各-7, 个-8)",
                 "amod(公园-12, 主要-9)",
                 "conj(公园-12, 街道-10)",
                 "cc(公园-12, 和-11)",
                 "dobj(挤进-3, 公园-12)",
                 "conj(挤进-3, 进行-13)",
                 "dobj(进行-13, 抗议-14)",
                 "punct(挤进-3, ，-15)",
                 "advmod(中心-24, 而-16)",
                 "nmod:assmod(大厦-19, 南国-17)",
                 "compound:nn(大厦-19, 国会-18)",
                 "nsubj(中心-24, 大厦-19)",
                 "cop(中心-24, 是-20)",
                 "acl(中心-24, 抗议-21)",
                 "mark(抗议-21, 的-22)",
                 "amod(中心-24, 主要-23)",
                 "conj(挤进-3, 中心-24)",
                 "punct(挤进-3, 。-25)")),

       T("( (IP (IP (NP (DP (DT 这些)) (CP (CP (IP (VV 示威)) (DEC 的))) (NP (NN 民众))) (VP (VV 来自) (NP (CP (CP (IP (VP (VA 不同))) (DEC 的))) (NP (NN 省份))))) (PU ，) (IP (NP (PN 他们)) (VP (VP (VV 吹) (AS 着) (NP (NN 口哨))) (PU ，) (VP (VV 敲) (AS 着) (NP (ADJP (JJ 小)) (NP (NN 鼓)))) (PU ，) (VP (VV 高喊) (CP (IP (NP (NR 米洛舍维奇)) (VP (ADVP (AD 已经)) (VP (VV 完蛋)))) (SP 了))))) (PU 。))) ",
               C("det(民众-4, 这些-1)",
                 "acl(民众-4, 示威-2)",
                 "mark(示威-2, 的-3)",
                 "nsubj(来自-5, 民众-4)",
                 "root(ROOT-0, 来自-5)",
                 "amod(省份-8, 不同-6)",
                 "mark(不同-6, 的-7)",
                 "dobj(来自-5, 省份-8)",
                 "punct(来自-5, ，-9)",
                 "nsubj(吹-11, 他们-10)",
                 "conj(来自-5, 吹-11)",
                 "aux:asp(吹-11, 着-12)",
                 "dobj(吹-11, 口哨-13)",
                 "punct(吹-11, ，-14)",
                 "conj(吹-11, 敲-15)",
                 "aux:asp(敲-15, 着-16)",
                 "amod(鼓-18, 小-17)",
                 "dobj(敲-15, 鼓-18)",
                 "punct(吹-11, ，-19)",
                 "conj(吹-11, 高喊-20)",
                 "nsubj(完蛋-23, 米洛舍维奇-21)",
                 "advmod(完蛋-23, 已经-22)",
                 "ccomp(高喊-20, 完蛋-23)",
                 "discourse(完蛋-23, 了-24)",
                 "punct(来自-5, 。-25)")),

       T("( (IP (NP (NR 普京)) (VP (PP (P 在) (LCP (IP (VP (VV 回国))) (LC 之后))) (ADVP (AD 将)) (ADVP (AD 努力)) (VP (VV 平息) (NP (NP (NR 南斯拉夫)) (CP (CP (IP (VP (PP (P 因为) (NP (NN 总统) (NN 大选))) (VP (MSP 所) (VP (VV 引爆) )))) (DEC 的))) (NP (NN 政治) (NN 危机))))) (PU 。))) ",
               C("nsubj(平息-7, 普京-1)",
                 "case(回国-3, 在-2)",
                 "nmod:prep(平息-7, 回国-3)",
                 "case(回国-3, 之后-4)",
                 "advmod(平息-7, 将-5)",
                 "advmod(平息-7, 努力-6)",
                 "root(ROOT-0, 平息-7)",
                 "nmod(危机-16, 南斯拉夫-8)",
                 "case(大选-11, 因为-9)",
                 "compound:nn(大选-11, 总统-10)",
                 "nmod:prep(引爆-13, 大选-11)",
                 "aux:prtmod(引爆-13, 所-12)",
                 "acl(危机-16, 引爆-13)",
                 "mark(引爆-13, 的-14)",
                 "compound:nn(危机-16, 政治-15)",
                 "dobj(平息-7, 危机-16)",
                 "punct(平息-7, 。-17)")),

      // TODO(pliang): add more test cases for all the relations not covered (see WARNING below)
    });

    Set<String> ignoreRelations = new HashSet<>(Arrays.asList("subj", "obj", "mod"));
    // Make sure all the relations are tested for
    Set<String> testedRelations = new HashSet<String>();
    for (Pair<String, String> ex : examples) {
      for (String item : ex.second.split("\n"))
        testedRelations.add(item.substring(0, item.indexOf('(')));
    }
    for (String relation : UniversalChineseGrammaticalRelations.shortNameToGRel.keySet()) {
      if (!testedRelations.contains(relation))
        if ( ! ignoreRelations.contains(relation)) {
          System.err.println("WARNING: relation '" + relation + "' not tested");
        }
    }

    TreeReaderFactory trf = new PennTreeReaderFactory();
    for (Pair<String, String> ex : examples) {
      String testTree = ex.first;
      String testAnswer = ex.second;

      // specifying our own TreeReaderFactory is vital so that functional
      // categories - that is -TMP and -ADV in particular - are not stripped off
      Tree tree = Tree.valueOf(testTree, trf);
      GrammaticalStructure gs = new UniversalChineseGrammaticalStructure(tree, Filters.acceptFilter()); // include punct

      assertEquals("Unexpected CC processed dependencies for tree "+testTree,
          testAnswer,
          GrammaticalStructureConversionUtils.dependenciesToString(gs, gs.typedDependenciesCCprocessed(GrammaticalStructure.Extras.MAXIMAL), tree, false, false, false));
    }
  }

}
