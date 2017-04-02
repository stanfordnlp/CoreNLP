package edu.stanford.nlp.time;

import java.util.*;

import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.Annotator;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.util.ArraySet;
import edu.stanford.nlp.util.CollectionUtils;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Iterables;

public class TimexTreeAnnotator implements Annotator {
	
  public static enum MatchType {ExactMatch, SmallestEnclosing}
  
  private MatchType matchType;
  
  public TimexTreeAnnotator(MatchType matchType) {
    this.matchType = matchType;
  }
  
  public void annotate(Annotation document) {
    for (CoreMap sentence: document.get(CoreAnnotations.SentencesAnnotation.class)) {
      final List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
      Tree tree = sentence.get(TreeCoreAnnotations.TreeAnnotation.class);
      tree.indexSpans(0);
      
      // add a tree to each timex annotation
      for (CoreMap timexAnn: sentence.get(TimeAnnotations.TimexAnnotations.class)) {
        Tree subtree;
        final int timexBegin = beginOffset(timexAnn);
        final int timexEnd = endOffset(timexAnn);
        Iterable<Tree> possibleMatches;
        switch (this.matchType) {
          
          // only use trees that match exactly
        case ExactMatch:
          possibleMatches = Iterables.filter(tree, tree1 -> {
            int treeBegin = beginOffset(tree, tokens);
            int treeEnd = endOffset(tree, tokens);
            return treeBegin == timexBegin && timexEnd == treeEnd;
          });
          Iterator<Tree> treeIter = possibleMatches.iterator();
          subtree = treeIter.hasNext() ? treeIter.next() : null;
          break;
          
          // select the smallest enclosing tree
        case SmallestEnclosing:
          possibleMatches = Iterables.filter(tree, tree1 -> {
            int treeBegin = beginOffset(tree, tokens);
            int treeEnd = endOffset(tree, tokens);
            return treeBegin <= timexBegin && timexEnd <= treeEnd;
          });
          List<Tree> sortedMatches = CollectionUtils.toList(possibleMatches);
          Collections.sort(sortedMatches, (tree1, tree2) -> {
            Integer width1 = endOffset(tree1, tokens) - beginOffset(tree1, tokens);
            Integer width2 = endOffset(tree2, tokens) - endOffset(tree2, tokens);
            return width1.compareTo(width2);
          });
          subtree = sortedMatches.get(0);
          break;
          
          // more cases could go here if they're added
        default:
          throw new RuntimeException("unexpected match type");
        }
  	
        // add the subtree to the time annotation
        if (subtree != null) {
          timexAnn.set(TreeCoreAnnotations.TreeAnnotation.class, subtree);
        }
      }
    }
  }
  
  private static int beginOffset(Tree tree, List<CoreLabel> tokens) {
    CoreMap label = (CoreMap)tree.label();
    int beginToken = label.get(CoreAnnotations.BeginIndexAnnotation.class);
    return beginOffset(tokens.get(beginToken));
  }
  
  private static int endOffset(Tree tree, List<CoreLabel> tokens) {
    CoreMap label = (CoreMap)tree.label();
    int endToken = label.get(CoreAnnotations.EndIndexAnnotation.class);
    if (endToken > tokens.size()) {
      String msg = "no token %d in tree:\n%s\ntokens:\n%s";
      throw new RuntimeException(String.format(msg, endToken - 1, tree, tokens));
    }
    return endOffset(tokens.get(endToken - 1));
  }
  
  private static int beginOffset(CoreMap map) {
    return map.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class);
  }
  
  private static int endOffset(CoreMap map) {
    return map.get(CoreAnnotations.CharacterOffsetEndAnnotation.class);
  }

  @Override
  public Set<Class<? extends CoreAnnotation>> requires() {
    return Collections.unmodifiableSet(new ArraySet<>(Arrays.asList(
        CoreAnnotations.TextAnnotation.class,
        CoreAnnotations.TokensAnnotation.class,
        CoreAnnotations.CharacterOffsetBeginAnnotation.class,
        CoreAnnotations.CharacterOffsetEndAnnotation.class,
        CoreAnnotations.SentencesAnnotation.class
    )));
  }

  @Override
  public Set<Class<? extends CoreAnnotation>> requirementsSatisfied() {
    // TODO: not sure what goes here
    return Collections.emptySet();
  }
}
