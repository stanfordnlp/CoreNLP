package edu.stanford.nlp.scenegraph;

import java.util.List;

import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.semgrex.SemgrexMatcher;
import edu.stanford.nlp.semgraph.semgrex.SemgrexPattern;
import edu.stanford.nlp.util.Generics;

public class EntityExtractor {

  public static SemgrexPattern ENTITY_PATTERN = SemgrexPattern.compile("{tag:/NN.*/}=entity [ </^(nsubj(pass)?|[id]obj|nmod.*|acl|advcl|[xc]comp|conj.*|compound)$/ {} | == {$}]");
  public static SemgrexPattern[] ATTRIBUTE_PATTERNS = {
    SemgrexPattern.compile("{tag:/VB.*/}=attr [ </^(dep|acl|advcl|conj.*|[xc]comp)$/ {} | == {$}] !>/[id]obj|[xc]comp/ {}"),
    SemgrexPattern.compile("{tag:/JJ.*/}=attr [ </^(dep|acl|advcl|conj.*|[xc]comp|amod)$/ {} | == {$}]"),
    //E.g. "the lights are on" where on is tagged as IN
    SemgrexPattern.compile("{tag:IN}=attr [ </^(acl|advcl|conj.*|[xc]comp|amod)$/ {} | == {$}]"),
    SemgrexPattern.compile("{tag:/NN.*./}=attr [ <compound {} | == {$} <cop {}]")
  };

  public static List<IndexedWord> extractEntities(SemanticGraph sg) {
    List<IndexedWord> entities = Generics.newArrayList();
    SemgrexMatcher matcher = ENTITY_PATTERN.matcher(sg);
    while (matcher.findNextMatchingNode()) {
      entities.add(matcher.getNode("entity"));
    }
    return entities;
  }

  public static List<IndexedWord> extractAttributes(SemanticGraph sg) {
    List<IndexedWord> attributes = Generics.newArrayList();
    for (SemgrexPattern pat : ATTRIBUTE_PATTERNS) {
      SemgrexMatcher matcher = pat.matcher(sg);
      while (matcher.findNextMatchingNode()) {
        attributes.add(matcher.getNode("attr"));
      }
    }
    return attributes;
  }
}
