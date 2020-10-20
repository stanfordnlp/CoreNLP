package edu.stanford.nlp.patterns.dep;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Predicate;

//import org.jdom.Element;
//import org.jdom.Namespace;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.patterns.GetPatternsFromDataMultiClass;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.semgraph.semgrex.SemgrexMatcher;
import edu.stanford.nlp.semgraph.semgrex.SemgrexPattern;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.util.CollectionValuedMap;
import edu.stanford.nlp.util.IntPair;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.StringUtils;

public class ExtractPhraseFromPattern {

  public List<String> cutoffRelations = new ArrayList<>();
  public int maxDepth = Integer.MAX_VALUE;
  public static List<String> ignoreTags = Arrays.asList("PRP", "PRP$", "CD",
      "DT", ".", "..", ",", "SYM");
  boolean ignoreCommonTags = true;
  public static ArrayList<String> cutoffTags = new ArrayList<>();
  public int maxPhraseLength = Integer.MAX_VALUE;
  //Namespace curNS;

  Map<SemgrexPattern, List<Pair<String, SemanticGraph>>> matchedGraphsForPattern = new HashMap<>();

  // 0 means none, 1 means partial, 2 means it shows sentences and their
  // techniques, app and focus, and 3 means full
  private static int DEBUG = 1;

  public ExtractPhraseFromPattern() {}
  public ExtractPhraseFromPattern(boolean ignoreCommonTags, int maxPhraseLength) {
    this.maxPhraseLength = maxPhraseLength;
    this.ignoreCommonTags = ignoreCommonTags;
    //this.curNS = null;
  }

  public void setMaxPhraseLength(int maxPhraseLength){
    this.maxPhraseLength = maxPhraseLength;
  }

  //public ExtractPhraseFromPattern(Namespace curNS) {
  //  this.curNS = curNS;
  //}

  private boolean checkIfSatisfiedMaxDepth(SemanticGraph g, IndexedWord parent,
      IndexedWord child, IntPair depths) {
    if (depths.get(0) == Integer.MAX_VALUE)
      return true;

    if (parent.equals(child))
      return true;

    boolean foundInMaxDepth = false;

    for (IndexedWord c : g.getChildren(parent)) {
      if (c.equals(child)) {
        return true;
      }
    }

    depths.set(1, depths.get(1) + 1);

    if (depths.get(1) >= depths.get(0))
      return false;

    for (IndexedWord c : g.getChildren(parent)) {
      foundInMaxDepth = checkIfSatisfiedMaxDepth(g, c, child, depths);
      if (foundInMaxDepth == true)
        return foundInMaxDepth;
    }

    return false;
  }

  public void processSentenceForType(SemanticGraph g,
      List<SemgrexPattern> typePatterns, List<String> textTokens,
      Collection<String> typePhrases, Collection<IntPair> typeIndices,
      Collection<IndexedWord> typeTriggerWords, boolean findSubTrees, Collection<ExtractedPhrase> extractedPhrases, boolean lowercase) {

    for (SemgrexPattern pattern : typePatterns) {
      Collection<IndexedWord> triggerWords = getSemGrexPatternNodes(g,
          textTokens, typePhrases, typeIndices, pattern,
          findSubTrees, extractedPhrases, lowercase, o -> true);
      for (IndexedWord w : triggerWords) {
        if (!typeTriggerWords.contains(w))
          typeTriggerWords.add(w);
      }
      // System.out.println("the string is " + StringUtils.join(focuss, ";"));
    }

  }

  /*
   * Given a SemanticGraph g and a SemgrexPattern pattern
   * And a bunch of other parameters,
   * run the pattern matcher (get SemgrexMatcher m)
   * Iterate through to get matching words/phrases
   *
   * Next, gets matchedGraphsForPattern.get(pattern),
   * a list of matched (String, semgraph) pairs
   * and adds the new graph and tokens if matched.
   *
   * I need to clarify what's going on with tokens.
   */
  public Set<IndexedWord> getSemGrexPatternNodes(SemanticGraph g,
      List<String> tokens, Collection<String> outputNodes, Collection<IntPair> outputIndices,
      SemgrexPattern pattern, boolean findSubTrees,
      Collection<ExtractedPhrase> extractedPhrases, boolean lowercase, Predicate<CoreLabel> acceptWord) {

    Set<IndexedWord> foundWordsParents = new HashSet<>();
    SemgrexMatcher m = pattern.matcher(g, lowercase);
    while (m.find()) {

      IndexedWord w = m.getNode("node");
      //System.out.println("found a match for " + pattern.pattern());

      IndexedWord parent = m.getNode("parent");

      boolean ifSatisfiedMaxDepth = checkIfSatisfiedMaxDepth(g, parent, w,
          new IntPair(maxDepth, 0));
      if (ifSatisfiedMaxDepth == false)
        continue;


      if(DEBUG > 3) {
        List<Pair<String, SemanticGraph>> matchedGraphs = matchedGraphsForPattern.get(pattern);

        if (matchedGraphs == null)
          matchedGraphs = new ArrayList<>();
        matchedGraphs.add(new Pair<>(StringUtils.join(
                tokens, " "), g));
        //if (DEBUG >= 3)
        //  System.out.println("matched pattern is " + pattern);
        matchedGraphsForPattern.put(pattern, matchedGraphs);
      }

      foundWordsParents.add(parent);

      // String relationName = m.getRelnString("reln");
      // System.out.println("word is " + w.lemma() + " and " + w.tag());
      ArrayList<IndexedWord> seenNodes = new ArrayList<>();
      List<String> cutoffrelations = new ArrayList<>();
//      if (elementStr.equalsIgnoreCase("technique"))
//        cutoffrelations = cutoffRelationsForTech;
//      if (elementStr.equalsIgnoreCase("app"))
//        cutoffrelations = this.cuttoffRelationsForApp;
      //System.out.println("g is ");
      //g.prettyPrint();
      printSubGraph(g, w, cutoffrelations, tokens, outputNodes, outputIndices, seenNodes, new ArrayList<>(),
          findSubTrees, extractedPhrases, pattern, acceptWord);
    }
    return foundWordsParents;
  }

  //Here, the index (startIndex, endIndex) seems to be inclusive of the endIndex
   public void printSubGraph(SemanticGraph g, IndexedWord w,
                             List<String> additionalCutOffRels,
                             List<String> textTokens,
                             Collection<String> listOfOutput, Collection<IntPair> listOfOutputIndices,
                             List<IndexedWord> seenNodes, List<IndexedWord> doNotAddThese,
                             boolean findSubTrees, Collection<ExtractedPhrase> extractedPhrases,
                             SemgrexPattern pattern, Predicate<CoreLabel> acceptWord) {
    try {
      if (seenNodes.contains(w))
        return;
      seenNodes.add(w);

      if (doNotAddThese.contains(w))
        return;

      List<IndexedWord> andNodes = new ArrayList<>();

      descendantsWithReln(g, w, "conj_and", new ArrayList<>(),
          andNodes);

      //System.out.println("and nodes are " + andNodes);

      for (IndexedWord w1 : andNodes) {
        printSubGraph(g, w1, additionalCutOffRels, textTokens,
            listOfOutput, listOfOutputIndices, seenNodes,
            doNotAddThese, findSubTrees, extractedPhrases, pattern, acceptWord);

      }
      doNotAddThese.addAll(andNodes);

      List<String> allCutOffRels = new ArrayList<>();
      if (additionalCutOffRels != null)
        allCutOffRels.addAll(additionalCutOffRels);
      allCutOffRels.addAll(cutoffRelations);

      CollectionValuedMap<Integer, String> featPerToken = new CollectionValuedMap<>();
      Collection<String> feat = new ArrayList<>();
      GetPatternsFromDataMultiClass.getFeatures(g, w, true, feat, null);


      Set<IndexedWord> words = descendants(g, w, allCutOffRels, doNotAddThese, ignoreCommonTags, acceptWord, featPerToken);


      // words.addAll(andNodes);

      // if (includeSiblings == true) {
      // for (IndexedWord ws : g.getSiblings(w)) {
      // if (additionalCutOffNodes == null
      // || !additionalCutOffNodes.contains(g.reln(g.getParent(w),
      // ws).getShortName()))
      // words.addAll(descendants(g, ws, additionalCutOffNodes, doNotAddThese));
      // }
      // }
      // if(afterand != null){
      // Set<IndexedWord> wordsAnd = descendants(g,afterand,
      // additionalCutOffNodes);
      // words.removeAll(wordsAnd);
      // printSubGraph(g,afterand, includeSiblings, additionalCutOffNodes);
      // }
      //System.out.println("words are " + words);
      if (words.size() > 0) {
        int min = Integer.MAX_VALUE, max = -1;
        for (IndexedWord word : words) {
          if (word.index() < min)
            min = word.index();
          if (word.index() > max)
            max = word.index();
        }

        IntPair indices;

        // Map<Integer, String> ph = new TreeMap<Integer, String>();
        // String phrase = "";
        // for (IndexedWord word : words) {
        // ph.put(word.index(), word.value());
        // }
        // phrase = StringUtils.join(ph.values(), " ");
        if ((max - min + 1) > maxPhraseLength){
          max = min + maxPhraseLength - 1 ;
        }
        indices = new IntPair(min - 1, max -1);
        String phrase = StringUtils.join(
          textTokens.subList(min - 1, max), " ");
        phrase = phrase.trim();
        feat.add("LENGTH-" + (max - min + 1));
        for(int i = min; i <= max; i++)
          feat.addAll(featPerToken.get(i));

        //System.out.println("phrase is " + phrase  + " index is " + indices + " and maxphraselength is " + maxPhraseLength + " and descendentset is " + words);
        ExtractedPhrase  extractedPh = new ExtractedPhrase(min - 1, max -1, pattern,  phrase, Counters.asCounter(feat));


        if (!listOfOutput.contains(phrase) && !doNotAddThese.contains(phrase)) {

//          if (sentElem != null) {
//            Element node = new Element(elemString, curNS);
//            node.addContent(phrase);
//            sentElem.addContent(node);
//          }
          listOfOutput.add(phrase);

          if (!listOfOutputIndices.contains(indices)) {
            listOfOutputIndices.add(indices);
            extractedPhrases.add(extractedPh);
          }

          if (findSubTrees == true) {
            for (IndexedWord word : words)
              if (!seenNodes.contains(word))
                printSubGraph(g, word, additionalCutOffRels,
                    textTokens, listOfOutput,
                    listOfOutputIndices, seenNodes, doNotAddThese,
                    findSubTrees, extractedPhrases, pattern, acceptWord);
          }
        }
      }
    } catch (Exception e) {
      e.printStackTrace();

    }
  }

  public static Set<IndexedWord> descendants(SemanticGraph g,
      IndexedWord vertex, List<String> allCutOffRels,
      List<IndexedWord> doNotAddThese, boolean ignoreCommonTags, Predicate<CoreLabel> acceptWord, CollectionValuedMap<Integer, String> feat) throws Exception {
    // Do a depth first search
    Set<IndexedWord> descendantSet = new HashSet<>();

    if (doNotAddThese !=null && doNotAddThese.contains(vertex))
      return descendantSet;

    if(!acceptWord.test(vertex.backingLabel()))
      return descendantSet;

    descendantsHelper(g, vertex, descendantSet, allCutOffRels, doNotAddThese,
            new ArrayList<>(), ignoreCommonTags, acceptWord, feat);
//    String descStr = "";
//    for(IndexedWord descendant: descendantSet){
//      descStr += descendant.word()+" ";
//    }
//    System.out.println(descStr);
    return descendantSet;
  }

  static boolean checkIfSatisfiesRelConstrains(SemanticGraph g,
      IndexedWord curr, IndexedWord child, List<String> allCutOffRels, GrammaticalRelation rel) {
    String relName = rel.getShortName();
    String relSpecificName = rel.toString();
    String relFullName = rel.getLongName();
    if(allCutOffRels!=null)
      for (String check : allCutOffRels) {
        if (relName.matches(check)
            || (relSpecificName != null && relSpecificName.matches(check))
            || (relFullName != null && relFullName.matches(check))) {
          return true;
        }
      }
    return false;
  }

  private static void descendantsHelper(SemanticGraph g, IndexedWord curr,
      Set<IndexedWord> descendantSet, List<String> allCutOffRels,
      List<IndexedWord> doNotAddThese, List<IndexedWord> seenNodes, boolean ignoreCommonTags, Predicate<CoreLabel> acceptWord, CollectionValuedMap<Integer, String> feat)
      throws Exception {

    if (seenNodes.contains(curr))
      return;

    seenNodes.add(curr);
    if (descendantSet.contains(curr) || (doNotAddThese!=null && doNotAddThese.contains(curr)) || !acceptWord.test(curr.backingLabel())) {
      return;
    }

    if (!ignoreCommonTags || !ignoreTags.contains(curr.tag().trim())) {
      descendantSet.add(curr);
    }

    for (IndexedWord child : g.getChildren(curr)) {
      boolean dontuse = false;
      if (doNotAddThese!=null &&doNotAddThese.contains(child))
        dontuse = true;

      GrammaticalRelation rel = null;
      if (dontuse == false) {
        rel = g.reln(curr, child);
        dontuse = checkIfSatisfiesRelConstrains(g, curr, child, allCutOffRels, rel);
      }
      if (dontuse == false) {
        for (String cutOffTagRegex : cutoffTags) {
          if (child.tag().matches(cutOffTagRegex)) {
            if (DEBUG >= 5)
              System.out.println("ignored tag " + child
                  + " because it satisfied " + cutOffTagRegex);
            dontuse = true;
            break;
          }
        }

      }
      if (dontuse == false){
        if(!feat.containsKey(curr.index())){
          feat.put(curr.index(), new ArrayList<>());
        }
        GetPatternsFromDataMultiClass.getFeatures(g, curr, false, feat.get(curr.index()), rel);
        //feat.add(curr.index(), "REL-" + rel.getShortName());
        descendantsHelper(g, child, descendantSet, allCutOffRels,
            doNotAddThese, seenNodes, ignoreCommonTags, acceptWord, feat);
      }
    }
  }

  // get descendants that have this relation
  private void descendantsWithReln(SemanticGraph g, IndexedWord w,
      String relation, List<IndexedWord> seenNodes,
      List<IndexedWord> descendantSet) {

    if (seenNodes.contains(w))
      return;
    seenNodes.add(w);
    if (descendantSet.contains(w))
      return;
    if (ignoreCommonTags && ignoreTags.contains(w.tag().trim()))
      return;
    for (IndexedWord child : g.getChildren(w)) {
      for (SemanticGraphEdge edge : g.getAllEdges(w, child)) {
        if (edge.getRelation().toString().equals(relation)) {
          descendantSet.add(child);
        }
      }
      descendantsWithReln(g, child, relation, seenNodes, descendantSet);
    }
  }

  public void printMatchedGraphsForPattern(String filename,
      int maxGraphsPerPattern) throws Exception {
    BufferedWriter w = new BufferedWriter(new FileWriter(filename));
    for (Entry<SemgrexPattern, List<Pair<String, SemanticGraph>>> en : matchedGraphsForPattern
        .entrySet()) {
      w.write("\n\nFor Pattern: " + en.getKey().pattern() + "\n");
      int num = 0;
      for (Pair<String, SemanticGraph> gEn : en.getValue()) {
        num++;
        if (num > maxGraphsPerPattern)
          break;
        w.write(gEn.first() + "\n" + gEn.second().toFormattedString() + "\n\n");
      }
    }
    w.close();
  }
}
