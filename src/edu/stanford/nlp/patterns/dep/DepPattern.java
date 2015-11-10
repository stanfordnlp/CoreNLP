package edu.stanford.nlp.patterns.dep;

import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.patterns.Pattern;
import edu.stanford.nlp.patterns.PatternFactory;
import edu.stanford.nlp.patterns.surface.Token;
import edu.stanford.nlp.semgraph.semgrex.SemgrexPattern;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.util.CollectionValuedMap;
import edu.stanford.nlp.util.Pair;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Created by sonalg on 10/31/14.
 */
public class DepPattern extends Pattern {

  int hashCode;

  List<Pair<Token, GrammaticalRelation>> relations;

  public DepPattern(List<Pair<Token, GrammaticalRelation>> relations)
  {
    super(PatternFactory.PatternType.DEP);
    this.relations = relations;
    hashCode = this.toString().hashCode();
  }

  public DepPattern(Token token, GrammaticalRelation relation)
  {
    super(PatternFactory.PatternType.DEP);
    this.relations = new ArrayList<>();
    relations.add(new Pair<>(token, relation));
    hashCode = this.toString().hashCode();
  }


  @Override
  public CollectionValuedMap<String, String> getRelevantWords() {
    CollectionValuedMap<String, String> relwordsThisPat = new CollectionValuedMap<>();
    for(Pair<Token, GrammaticalRelation> r: relations)
      getRelevantWordsBase(r.first(), relwordsThisPat);
    return relwordsThisPat;
  }

  @Override
  public int equalContext(Pattern p) {
    return -1;
  }

  @Override
  public String toStringSimple() {
    return toString();
  }

  @Override
  public String toString(List<String> notAllowedClasses) {
    //TODO: implement this
    return toString();
  }

  @Override
  public String toString(){
    if(relations.size() > 1)
      throw new UnsupportedOperationException();

    Pair<Token, GrammaticalRelation> rel = relations.get(0);
    //String pattern = "({" + wordType + ":/" + parent + "/}=parent >>" + rel + "=reln {}=node)";
    String p = "(" + rel.first().toString() + "=parent >"+rel.second().toString() + "=reln {}=node)";
    return p;
  }

  @Override
  public int hashCode(){
    return hashCode;
  }

  @Override
  public boolean equals(Object p){
    if(! (p instanceof DepPattern))
      return false;
    return this.toString().equals(((DepPattern)p).toString());
  }

  //TODO: implement compareTo

  //TODO: implement these
  public static boolean sameGenre(DepPattern p1, DepPattern p2){
    return true;
  }

  public static boolean subsumes(DepPattern pat, DepPattern p) {
    return false;
  }
}
