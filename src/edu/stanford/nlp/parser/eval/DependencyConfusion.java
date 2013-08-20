package edu.stanford.nlp.parser.eval;

import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import edu.stanford.nlp.ling.HasTag;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.parser.lexparser.EnglishTreebankParserParams;
import edu.stanford.nlp.parser.lexparser.TreebankLangParserParams;
import edu.stanford.nlp.stats.EquivalenceClassEval;
import edu.stanford.nlp.stats.GeneralizedCounter;
import edu.stanford.nlp.trees.DependencyTyper;
import edu.stanford.nlp.trees.HeadFinder;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeTransformer;
import edu.stanford.nlp.trees.Treebank;
import edu.stanford.nlp.trees.Trees;
import edu.stanford.nlp.util.BinaryHeapPriorityQueue;
import edu.stanford.nlp.util.Filter;
import edu.stanford.nlp.util.Filters;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.PriorityQueue;
import edu.stanford.nlp.util.StringUtils;

/** Produces a confusion matrix of word-word dependencies as grouped
 * by dependents. Only works on gold/test pairs with identical
 * tokenizations.
 * fact that in a headed syntactic tree, each word is dependent on exactly one one other word (the head word of the sentence
 * is assumed to be dependent on a special root symbol).  This class is primarily meant to be used from the command line.
 *
 * <p>
 * <h3>Explanation:</h3>
 * <p>
 * Suppose we have parses of the string "the girl saw the boy with the cat", and in the gold-standard parse the PP "with the telescope"
 * modifies the NP "the boy" and in the guessed parse the PP modifies the VP "saw the boy".  In the word-word dependency view
 * of these parses, the only thing wrong about the guess parse is that the IN-tagged "with" is dependent on the VBZ-tagged "saw" within a VP context, whereas it should be
 * dependent on the NN-tagged "boy" within an NP context.  At the syntactic category level this can be represented as the confusion
 * <blockquote>
 * PP -> NP confused for PP -> VP
 * </blockquote>
 * At the tag level this can be represented as
 * <blockquote>
 * IN -> NN confused for IN -> VBZ
 * </blockquote>
 * DependencyConfusion tabulates
 *
 *<p>
 * Some notes:
 * <ul>
 * <li>A confusion does not necessarily involve an incorrect context.  In the NP "drivers of cars with power steering",
 * if the PP "with power steering" is mistakenly parsed as a dependent of "drivers" rather than of "cars", there is a dependency confusion
 * even though the context is the same for both the correct and wrong parses.  This would be a syntactic confusion of type
 * <blockquote>
 * PP -> NP confused for PP -> NP
 * </blockquote>
 * or a tag-level confusion of type
 * <blockquote>
 * IN -> NNS confused for IN -> NNS
 * </blockquote>
 *
 * <li>DependencyConfusion only works on gold/test pairs with identical tokenizations.
 *
 * <li> There are several differences in dependency count from {@link CandidateParseManager}.  In DependencyConfusion,
 * index position of the word is taken into account in determining dependency accuracy, which is not the case in {@link CandidateParseManager}.
 *   Also, DependencyConfusion scores the choice of the head word, which {@link CandidateParseManager} doesn't.
 * </ul>
 * @author Roger Levy (rog@stanford.edu)
 * @author javanlp
 * @author Sarah Spikes (sdspikes@cs.stanford.edu) - cleanup and filling in types
 */
public class DependencyConfusion {

  private static final Filter<Pair<TypedDependency,TypedDependency>> printConfusionsFilter =
          new Filter<Pair<TypedDependency,TypedDependency>>() {
            private static final long serialVersionUID = 3404301580750043668L;

            public boolean accept(Pair<TypedDependency, TypedDependency> pair) {
              return false;
            }
          };


  private static enum GroupByType {
    DEPCAT,DEPTAG,DEPWORD;

    public String depGroupType(TypedDependency td) {
      String result = null;
      switch(this) {
        case DEPCAT: result = td.depCat; break;
        case DEPTAG: result = td.depTag; break;
        case DEPWORD: result = td.depTerm; break;
      }
      return result;
    }

    public String governorGroupType(TypedDependency td) {
      String result = null;
      switch(this) {
        case DEPCAT: result = td.headCat; break;
        case DEPTAG: result = td.headTag; break;
        case DEPWORD: result = td.headTerm; break;
      }
      return result;
    }

  }

  private GroupByType groupType= GroupByType.DEPCAT;

  public void setGroupType(GroupByType gbt) {
    groupType = gbt;
  }

  private static class InternalHeadFinder implements HeadFinder {
    /**
     * 
     */
    private static final long serialVersionUID = -5764886599783226680L;
    HeadFinder hf;

    public InternalHeadFinder(HeadFinder hf) {
      this.hf = hf;
    }

    public Tree determineHead(Tree t, Tree parent) {
      return determineHead(t);
    }

    public Tree determineHead(Tree t) {
      if(t.value().equals(SPECIAL_ROOT_SYMBOL)) {
        for(Tree child : t.children())
          if(child.value().equals(SPECIAL_HEAD_SYMBOL))
            return child;
        return t.children()[0];
      }
      return hf.determineHead(t);
    }
  }

  public static final String SPECIAL_ROOT_SYMBOL = "-ROOT-";
  public static final String SPECIAL_HEAD_SYMBOL = "-HEAD-";


  private static Tree addSpecialRoot(Tree t) {
    return t.treeFactory().newTreeNode(SPECIAL_ROOT_SYMBOL, Arrays.asList(new Tree[] { t,t.treeFactory().newTreeNode(SPECIAL_HEAD_SYMBOL, Arrays.asList(new Tree[] { t.treeFactory().newLeaf(SPECIAL_HEAD_SYMBOL) }))}) );
  }

  /**
   * Constructs a new DependencyConfusion object with a parameters class tlpp.
   */
  public DependencyConfusion(TreebankLangParserParams tlpp,EquivalenceClassEval.EqualityChecker<TypedDependency> eq) {
    this.eq = eq;
    confusion = new GeneralizedCounter(3);
    HeadFinder internalHeadFinder = new InternalHeadFinder(tlpp.headFinder());
    this.objectifier = CandidateParseManager.dependencyObjectifier(new TypedDependencyTyper(internalHeadFinder),tlpp.collinizerEvalb(),internalHeadFinder);
    tdt = new TypedDependencyTyper(internalHeadFinder);
    this.tlpp = tlpp;
    collinizer = tlpp.collinizerEvalb();
  }

  public DependencyConfusion(TreebankLangParserParams tlpp) {
    this(tlpp,EquivalenceClassEval.DEFAULT_CHECKER);
  }

  private boolean punctuationGetsPruned = true;
  TreeObjectifier<TypedDependency> objectifier;
  EquivalenceClassEval.EqualityChecker<TypedDependency> eq;
  GeneralizedCounter confusion; // 3D: [target, is_equal, proposed]
  private static final String LANG_OPTION = "-lang";
  TypedDependencyTyper tdt;
  TreeTransformer collinizer;
  Map<Object,Collection<Pair<Tree,Tree>>> confusionCollection;
  TreebankLangParserParams tlpp;

  /* returns deps as follows: <head,dep,parent,headCat,depCat,direction,headIndex,depIndex */
  /* Also has special behavior if head is null: treats it as top dep in the tree. */
  private class TypedDependencyTyper implements DependencyTyper<TypedDependency> {
    HeadFinder hf;

    public TypedDependencyTyper(HeadFinder hf) {
      this.hf = hf;
    }

    public TypedDependency makeDependency(Tree head, Tree dep, Tree root) {
      TypedDependency result = new TypedDependency();
      Tree depTerm = dep.headTerminal(hf);
      result.depTerm = depTerm.value();
      result.depIndex = Trees.leftEdge(depTerm,root);
      result.depCat = dep.value();
      result.depTag = depTerm.parent(dep).value();
      Tree headTerm = head.headTerminal(hf);
      result.headTerm = headTerm.value();
      result.headIndex = Trees.leftEdge(headTerm,root);
      result.leftHeaded = result.headIndex < result.depIndex;
      result.parentCat = head.parent(root).value();
      result.headCat = head.value();
      result.headTag = headTerm.parent(head).value();
      return result;
    }
  }

  protected class TypedDependency {
    String headTerm;
    String depTerm;
    String headTag;
    String depTag;
    String parentCat;
    String headCat;
    String depCat;
    boolean leftHeaded;
    int headIndex;
    int depIndex;

    @Override
    public String toString() {
      return "<" + headTerm +":" + headIndex + "," + depTerm + ":" + depIndex + "," + headTag + "," + depTag + "," + parentCat + "," + headCat + "," + depCat + ">";
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof TypedDependency)) {
        return false;
      }

      final TypedDependency typedDependency = (TypedDependency) o;

      if (depIndex != typedDependency.depIndex) {
        return false;
      }
      if (headIndex != typedDependency.headIndex) {
        return false;
      }
      if (depTerm != null ? !depTerm.equals(typedDependency.depTerm) : typedDependency.depTerm != null) {
        return false;
      }
      if (headTerm != null ? !headTerm.equals(typedDependency.headTerm) : typedDependency.headTerm != null) {
        return false;
      }

      return true;
    }

    @Override
    public int hashCode() {
      int result;
      result = (headTerm != null ? headTerm.hashCode() : 0);
      result = 29 * result + (depTerm != null ? depTerm.hashCode() : 0);
      result = 29 * result + headIndex;
      result = 29 * result + depIndex;
      return result;
    }
  }

  private void processGoldGuess(Tree goldTree, Tree guessTree) {
    Collection<TypedDependency> golds = objectifier.objectify(addSpecialRoot(goldTree));
    Collection<TypedDependency> guesses = objectifier.objectify(addSpecialRoot(guessTree));
    for(TypedDependency gold : golds) {
      TypedDependency guess = null;
      for(TypedDependency candidateGuess : guesses) {
        if(gold.depIndex == candidateGuess.depIndex) {
          guess = candidateGuess;
          break;
        }
      }
      if(guess == null) {
        System.err.println("Warning -- the following gold dependency has no corresponding guess dependency, and is ignored.");
        System.err.println(gold);
        continue;
      }

      if(printConfusionsFilter.accept(new Pair<TypedDependency, TypedDependency>(gold,guess))) {
        System.out.println("Captured confusion: gold " + gold + " guess " + guess);
        goldTree.pennPrint();
        guessTree.pennPrint();
      }

      Pair<String, String> guessDep = Generics.newPair(groupType.depGroupType(guess),groupType.governorGroupType(guess));
      Pair<String, String> goldDep = Generics.newPair(groupType.depGroupType(gold),groupType.governorGroupType(gold));

      boolean areEqual = eq.areEqual(guess,gold);
      confusion.incrementCount(Arrays.asList(new Object[] { goldDep, areEqual, guessDep,  }));

      // if there was a confusion, record the tree pair where it occurred.
      if(! areEqual) {
        Pair<Pair<String, String>, Pair<String, String>> thisConfusion = Generics.newPair(guessDep, goldDep);
        Collection<Pair<Tree,Tree>> thisConfs = confusionCollection.get(thisConfusion);
        if(thisConfs == null) {
          thisConfs = new ArrayList<Pair<Tree,Tree>>();
          confusionCollection.put(thisConfusion, thisConfs);
        }

        Tree goldTree1 = markTree(goldTree, gold.depIndex + (punctuationGetsPruned ? punctBetween(goldTree, gold.depIndex) : 0));
        Tree guessTree1 = markTree(guessTree, guess.depIndex + (punctuationGetsPruned ? punctBetween(guessTree, guess.depIndex) : 0));

        thisConfs.add(new Pair<Tree,Tree>(goldTree1,guessTree1));
      }
    }
  }

  /* necessary because the depIndex doesn't match the right terminal index if punctuation has been "collinized" out. */
  private int punctBetween(Tree t, int i) {
    int result = 0;
    ArrayList<TaggedWord> wds = t.taggedYield();
    for(int j = 0; j < i; j++) {
      HasTag tw = wds.get(j);
      if(tlpp.treebankLanguagePack().punctuationTagAcceptFilter().accept((tw.tag()))) {
        result++;
        i++;
      }
    }
    return result;
  }

  private Tree markTree(Tree t, int index) {
    Tree t1 = t.prune(Filters.<Tree>acceptFilter()); // copies tree
    Tree tNode = Trees.maximalProjection(Trees.getTerminal(t1, index),t1,tdt.hf);
    Tree tNodeGovernor = tNode.parent(t1);
    while(! tNode.isLeaf()) {
      Tree tNode1 = tdt.hf.determineHead(tNode);
      tNode.setLabel(tNode.labelFactory().newLabel("{" + tNode.label().value() + "}"));
      tNode = tNode1;
    }
    tNode.setLabel(tNode.labelFactory().newLabel(tNode.label().value() + "*Dep"));
    if(tNodeGovernor == null)
      return t1;
    while(! tNodeGovernor.isLeaf()) {
      Tree tNodeGovernor1 = tdt.hf.determineHead(tNodeGovernor);
      tNodeGovernor.setLabel(tNodeGovernor.labelFactory().newLabel("[" + tNodeGovernor.label().value() + "]"));
      tNodeGovernor = tNodeGovernor1;
    }
    tNodeGovernor.setLabel(tNodeGovernor.labelFactory().newLabel(tNodeGovernor.label().value() + "*Head"));
    return t1;
  }

  private void printConfusions() {
    printConfusions(new PrintWriter(System.out,true));
  }

  private void printConfusions(PrintWriter pw) {


    pw.println("Dependency     \tCorrect\tTotal\tAccuracy");
    double allCorrect = 0.0;
    double allTotal = 0.0;
    for(Object gold : confusion.topLevelKeySet()) {
      GeneralizedCounter thisConfusion = confusion.conditionalizeOnce(gold);
      double correct = thisConfusion.conditionalizeOnce(true).totalCount();
      double total = thisConfusion.conditionalizeOnce(false).totalCount() + correct;
      allCorrect += correct;
      allTotal += total;
      pw.print(gold);
      for (int z = 0; z < 15 - gold.toString().length(); z++) { pw.print(" "); } // empty-space buffer
      pw.println("\t" + ((int) correct) + "\t" + ((int) total) + "\t" + (correct/total) );
    }
    pw.println("Total:  \t" + ((int) allCorrect) + "\t" + ((int) allTotal) + "\t" + (allCorrect/allTotal));
    pw.println();

    //
    pw.println("Confusion matrix for incorrect deps (target then proposed)");
    for(Object gold : confusion.topLevelKeySet()) {
      GeneralizedCounter thisConfusion = confusion.conditionalizeOnce(gold).conditionalizeOnce(false);
      pw.println();
      pw.println(gold + "\t" + ((int) thisConfusion.totalCount()));
      for(Object guess : thisConfusion.topLevelKeySet()) {
        pw.println("\t" + guess + "\t" + ((int) thisConfusion.getCount(guess)));
      }
    }

    pw.println("Total dependencies: " + allTotal);
    pw.println("Total confusions: " + (allTotal - allCorrect));

    PriorityQueue pq = sortedConfusions();
    int num = Math.min(pq.size(), 50);
    pw.println("Top " + num + " confusions (target,proposed):");
    for (int i = 0; i < num; i++) {
      Object key = pq.getFirst();
      double score = pq.getPriority(key);
      pq.removeFirst();
      pw.println(key + "\t" + ((int) score));
    }
  }

  private static final String TYPED_DEPS_OPTION = "-typedDeps";

  /**
   *
   * Invocation:
   *
   * <p>
   * java edu.stanford.nlp.parser.eval.DependencyConfusion [options] &lt;goldtrees-file&gt; &lt;guesstrees-file&gt;
   * </p>
   *
   *
   * Options:
   * <ul>
   * <li><tt>-lang &lt;TreebankLangParserParams class&gt;</tt> specifies the {@link TreebankLangParserParams} class to use for the methods {@link TreebankLangParserParams#diskTreebank()} and {@link TreebankLangParserParams#headFinder()}.
   * <li><tt>-word</tt> specifies that dependency stats are reported by class WORD
   * <li><tt>-tag</tt> specifies that dependency stats are reported by class TAG
   * <li><tt>-cat</tt> specifies that dependency stats are reported by class CATEGORY
   * <li><tt>-show &lt;guess-dep&gt; &lt;guess-governor&gt; &lt;gold-dep&gt; &lt;gold-governor&gt;</tt> print all confusions where for a dependent D,
   * there is a guessed dependency of &lt;guess-dep&gt; into &lt;guess-governor&gt;, but the true dependency is of a &lt;gold-dep&gt; into a &lt;gold-governor&gt;.
   * In the guess and gold trees for each printed confusion, the dependent word will be marked with *Dep and the categories its head will be curly-braced {};
   * the head word will be marked with *Head and the categories it heads will be bracketed [].
   * <li><tt>-showOut <filename></tt> Print all confusions specified by the <tt>-show</tt> option to the file &lt;filename&gt;.  By default,
   * they are printed to standard error.
   * <li><tt>-typedDeps</tt> include mother syntactic category in determining dependency identity
   * </ul>
   *
   * @throws Exception
   */
  public static void main(String[] args) throws Exception {
    EquivalenceClassEval.EqualityChecker eq = EquivalenceClassEval.DEFAULT_CHECKER;
    Map<String, Integer> flagMap = new HashMap<String, Integer>();
    flagMap.put(LANG_OPTION, Integer.valueOf(1));
    flagMap.put("-word", Integer.valueOf(0));
    flagMap.put("-tag", Integer.valueOf(0));
    flagMap.put("-cat", Integer.valueOf(0));
    flagMap.put("-show", 4);
    flagMap.put("-showOut", 1);
    flagMap.put(TYPED_DEPS_OPTION,0);
    Map<String,String[]> argsMap = StringUtils.argsToMap(args, flagMap);
    args = argsMap.get(null);
    if(args.length ==0) {
      System.out.println("Usage: DependencyConfusion [-word|-tag|-cat] [-show <guess-dep> <guess-governor> <gold-dep> <gold-governor> [-showOut <filename>]] <gold-file> <guess-file>");
      System.exit(0);
    }

    System.out.println("A dependency of the form (X,Y) is a word-word dependency of a type-X dependent on a type-Y governor.");

    TreebankLangParserParams tlpp = null;
    if(argsMap.containsKey(TYPED_DEPS_OPTION))
      eq = new TypedEqualityChecker();
    if (argsMap.keySet().contains(LANG_OPTION)) {
      tlpp = (TreebankLangParserParams) Class.forName((argsMap.get(LANG_OPTION))[0]).newInstance();
      System.err.println("Using treebank language parameters "+ tlpp.getClass().getName());
    } else {
      tlpp = new EnglishTreebankParserParams();
    }
    GroupByType gbt;
    if (argsMap.keySet().contains("-word")) {
      gbt = GroupByType.DEPWORD;
    } else if (argsMap.keySet().contains("-cat")) {
      gbt = GroupByType.DEPCAT;
    } else { // -tag
      gbt = GroupByType.DEPTAG;
    }
    Treebank goldTrees = tlpp.diskTreebank();
    Treebank guessTrees = tlpp.diskTreebank();
    goldTrees.loadPath(args[0]);
    guessTrees.loadPath(args[1]);

    System.out.println("Recall-oriented results (gold is target, guess is proposed):");
    DependencyConfusion dcRecall = new DependencyConfusion(tlpp,eq);
    dcRecall.setGroupType(gbt);
    dcRecall.processTreebanks(goldTrees,guessTrees);
    dcRecall.printConfusions();
    if(argsMap.keySet().contains("-show")) {
      //System.out.println("Showing recall-based selected confusions.");
      String[] showArgs = argsMap.get("-show");
      Pair<Pair<String, String>, Pair<String, String>> showConfusion = Generics.newPair(Generics.newPair(showArgs[0],showArgs[1]),Generics.newPair(showArgs[2],showArgs[3]));
      if(argsMap.keySet().contains("-showOut")) {
        PrintWriter pw = new PrintWriter(new FileOutputStream(argsMap.get("-showOut")[0]),true);
        dcRecall.showConfusions(showConfusion,pw);
      }
      else
        dcRecall.showConfusions(showConfusion);
    }

    System.out.println("Precision-oriented results (guess is target, gold is proposed):");
    DependencyConfusion dcPrecision = new DependencyConfusion(tlpp);
    dcPrecision.setGroupType(gbt);
    dcPrecision.processTreebanks(guessTrees,goldTrees);
    dcPrecision.printConfusions();
  }

  private void processTreebanks(Treebank goldTrees, Treebank guessTrees) {
    confusionCollection = new HashMap();
    Iterator<Tree> i = goldTrees.iterator();
    Iterator<Tree> j = guessTrees.iterator();
    while(i.hasNext())
      processGoldGuess(i.next(),j.next());
  }

  /** returns a sorted list of the confusions in order of number of occurrences. */
  private PriorityQueue sortedConfusions() {
    PriorityQueue sortedConfs = new BinaryHeapPriorityQueue();
    for(Object k : confusion.counterView().keySet()) {
      List key = (List) k;
      if(key.get(1).equals(true))
        continue;
      sortedConfs.add(Generics.newPair(key.get(0),key.get(2)),confusion.getCounts(key)[confusion.depth()]);
    }
    return sortedConfs;
  }

  /**
   * Prints collected confusions, in context of their trees, to
   */
  private void showConfusions(Pair<Pair<String, String>, Pair<String, String>> showConfusion) {
    showConfusions(showConfusion,new PrintWriter(System.err,true));
  }


  /**
   * Prints the trees with highlighted confusions to pw.
   */
  private void showConfusions(Pair<Pair<String, String>, Pair<String, String>> showConfusion, PrintWriter pw) {
    for(Pair<Tree,Tree> p: confusionCollection.get(showConfusion)) {
      pw.println("Gold:");
      p.first().pennPrint(pw);
      pw.println("Guess:");
      p.second().pennPrint(pw);
    }
  }

  protected static class TypedEqualityChecker implements EquivalenceClassEval.EqualityChecker<TypedDependency> {
    public boolean areEqual(TypedDependency td, TypedDependency td1) {
      return td.headIndex==td1.headIndex && td.depIndex == td1.depIndex
              && td.headTerm.equals(td1.headTerm) && td.depTerm.equals(td1.depTerm)
              && td.headCat.equals(td1.headCat) && td.depCat.equals(td1.depCat);
    }
  }

}
