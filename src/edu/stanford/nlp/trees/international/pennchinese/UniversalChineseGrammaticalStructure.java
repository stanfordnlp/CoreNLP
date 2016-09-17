package edu.stanford.nlp.trees.international.pennchinese;
import edu.stanford.nlp.util.logging.Redwood;

import edu.stanford.nlp.international.Language;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.parser.lexparser.ChineseTreebankParserParams;
import edu.stanford.nlp.parser.ViterbiParserWithOptions;
import edu.stanford.nlp.trees.*;
import java.util.function.Predicate;
import edu.stanford.nlp.util.Filters;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.StringUtils;

import java.io.*;
import java.util.*;
import java.lang.reflect.Constructor;

import static edu.stanford.nlp.trees.GrammaticalRelation.DEPENDENT;

/**
 * A GrammaticalStructure for Chinese.
 *
 * @author Galen Andrew
 * @author Pi-Chuan Chang
 * @author Daniel Cer - support for printing CoNLL-X format, encoding update,
 *                      and preliminary changes to make
 *                      ChineseGrammaticalStructure behave more like
 *                      EnglishGrammaticalStructure on the command line
 *                      (ultimately, both classes should probably use the same
 *                      abstract main method).
 */
public class UniversalChineseGrammaticalStructure extends GrammaticalStructure  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(UniversalChineseGrammaticalStructure.class);

  private static HeadFinder shf = new UniversalChineseSemanticHeadFinder();
  //private static HeadFinder shf = new ChineseHeadFinder();


  /**
   * Construct a new <code>GrammaticalStructure</code> from an
   * existing parse tree.  The new <code>GrammaticalStructure</code>
   * has the same tree structure and label values as the given tree
   * (but no shared storage).  As part of construction, the parse tree
   * is analyzed using definitions from {@link GrammaticalRelation
   * <code>GrammaticalRelation</code>} to populate the new
   * <code>GrammaticalStructure</code> with as many labeled
   * grammatical relations as it can.
   *
   * @param t Tree to process
   */
  public UniversalChineseGrammaticalStructure(Tree t) {
    this(t, new ChineseTreebankLanguagePack().punctuationWordRejectFilter());
  }

  public UniversalChineseGrammaticalStructure(Tree t, Predicate<String> puncFilter) {
    this (t, puncFilter, shf);
  }

  public UniversalChineseGrammaticalStructure(Tree t, HeadFinder hf) {
    this (t, null, hf);
  }

  public UniversalChineseGrammaticalStructure(Tree t, Predicate<String> puncFilter, HeadFinder hf) {
    super(t, UniversalChineseGrammaticalRelations.values(), UniversalChineseGrammaticalRelations.valuesLock(), null, hf, puncFilter, Filters.acceptFilter());
  }

  /** Used for postprocessing CoNLL X dependencies */
  public UniversalChineseGrammaticalStructure(List<TypedDependency> projectiveDependencies, TreeGraphNode root) {
    super(projectiveDependencies, root);
  }



  @Override
  protected void collapseDependencies(List<TypedDependency> list, boolean CCprocess, Extras includeExtras) {
    //      collapseConj(list);
    collapsePrepAndPoss(list);
    //      collapseMultiwordPreps(list);
  }

  private static void collapsePrepAndPoss(Collection<TypedDependency> list) {
    Collection<TypedDependency> newTypedDeps = new ArrayList<>();

    // Construct a map from words to the set of typed
    // dependencies in which the word appears as governor.
    Map<IndexedWord, Set<TypedDependency>> map = Generics.newHashMap();
    for (TypedDependency typedDep : list) {
      if (!map.containsKey(typedDep.gov())) {
        map.put(typedDep.gov(), Generics.<TypedDependency>newHashSet());
      }
      map.get(typedDep.gov()).add(typedDep);
    }
    //log.info("here's the map: " + map);

    for (TypedDependency td1 : list) {
      if (td1.reln() != GrammaticalRelation.KILL) {
        IndexedWord td1Dep = td1.dep();
        String td1DepPOS = td1Dep.tag();
        // find all other typedDeps having our dep as gov
        Set<TypedDependency> possibles = map.get(td1Dep);
        if (possibles != null) {
          // look for the "second half"
          for (TypedDependency td2 : possibles) {
            // TreeGraphNode td2Dep = td2.dep();
            // String td2DepPOS = td2Dep.parent().value();
            if (td1.reln() == DEPENDENT && td2.reln() == DEPENDENT && td1DepPOS.equals("P")) {
              GrammaticalRelation td3reln = UniversalChineseGrammaticalRelations.valueOf(td1Dep.value());
              if (td3reln == null) {
                td3reln = GrammaticalRelation.valueOf(Language.UniversalChinese,
                                                      td1Dep.value());
              }
              TypedDependency td3 = new TypedDependency(td3reln, td1.gov(), td2.dep());
              //log.info("adding: " + td3);
              newTypedDeps.add(td3);
              td1.setReln(GrammaticalRelation.KILL);        // remember these are "used up"
              td2.setReln(GrammaticalRelation.KILL);        // remember these are "used up"
            }
          }

          // Now we need to see if there any TDs that will be "orphaned"
          // by this collapse.  Example: if we have:
          //   dep(drew, on)
          //   dep(on, book)
          //   dep(on, right)
          // the first two will be collapsed to on(drew, book), but then
          // the third one will be orphaned, since its governor no
          // longer appears.  So, change its governor to 'drew'.
          if (td1.reln().equals(GrammaticalRelation.KILL)) {
            for (TypedDependency td2 : possibles) {
              if (!td2.reln().equals(GrammaticalRelation.KILL)) {
                //log.info("td1 & td2: " + td1 + " & " + td2);
                td2.setGov(td1.gov());
              }
            }
          }
        }
      }
    }

    // now copy remaining unkilled TDs from here to new
    for (TypedDependency td : list) {
      if (!td.reln().equals(GrammaticalRelation.KILL)) {
        newTypedDeps.add(td);
      }
    }

    list.clear();                            // forget all (esp. killed) TDs
    list.addAll(newTypedDeps);
  }

  public static void main(String args[]) {
    Properties params = StringUtils.argsToProperties(args);

    if (params.getProperty("sentFile") != null) {
      log.error("Parsing sentences to constituency trees is not supported for Chinese. " +
          "Please parse your sentences first and then convert them to dependency trees using the -treeFile option." );
      return;
    }
    GrammaticalStructureConversionUtils.convertTrees(args, "zh");
  }


  public static List<GrammaticalStructure> readCoNLLXGrammaticalStructureCollection(String fileName) throws IOException {
    return readCoNLLXGrammaticalStructureCollection(fileName, UniversalChineseGrammaticalRelations.shortNameToGRel, new FromDependenciesFactory());
  }

  public static UniversalChineseGrammaticalStructure buildCoNLLXGrammaticalStructure(List<List<String>> tokenFields) {
    return (UniversalChineseGrammaticalStructure) buildCoNLLXGrammaticalStructure(tokenFields, UniversalChineseGrammaticalRelations.shortNameToGRel, new FromDependenciesFactory());
  }

  public static class FromDependenciesFactory
    implements GrammaticalStructureFromDependenciesFactory
  {
    public UniversalChineseGrammaticalStructure build(List<TypedDependency> tdeps, TreeGraphNode root) {
      return new UniversalChineseGrammaticalStructure(tdeps, root);
    }
  }

  private static final long serialVersionUID = 8877651855167458256L;

}
