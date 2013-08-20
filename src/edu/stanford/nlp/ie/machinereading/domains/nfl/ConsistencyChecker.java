package edu.stanford.nlp.ie.machinereading.domains.nfl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

import edu.stanford.nlp.ie.machinereading.Extractor;
import edu.stanford.nlp.ie.machinereading.structure.EntityMention;
import edu.stanford.nlp.ie.machinereading.structure.ExtractionObject;
import edu.stanford.nlp.ie.machinereading.structure.MachineReadingAnnotations;
import edu.stanford.nlp.ie.machinereading.structure.RelationMention;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.util.CoreMap;

/**
 * Implements consistency checks for the NFL domain.
 * This is necessary only when using the MaxRecall NER, which over-predicts entities.
 */
public class ConsistencyChecker implements Extractor {

  private static final long serialVersionUID = 1L;

  public void annotate(Annotation dataset) {
    for (CoreMap sentence : dataset.get(CoreAnnotations.SentencesAnnotation.class)){
      checkSentence(sentence);
    }
  }
  
  public void checkSentence(CoreMap sentence) {
    List<RelationMention> relations = sentence.get(MachineReadingAnnotations.RelationMentionsAnnotation.class);
    List<EntityMention> entities = sentence.get(MachineReadingAnnotations.EntityMentionsAnnotation.class);
    
    HashSet<EntityMention> entsInRels = new HashSet<EntityMention>();
    if(relations != null){
      for(RelationMention r: relations){
        for(ExtractionObject o: r.getArgs()){
          if(o instanceof EntityMention){
            entsInRels.add((EntityMention) o);
          }
        }
      }
    }
    
    //
    // keep only NFLGame entities that participate in a relation
    //
    List<EntityMention> validatedEntities = new ArrayList<EntityMention>();
    if(entities != null){
      for(EntityMention e: entities){
        if(e.getType().equals("NFLGame") && ! contained(e, entsInRels)){
          // skip this entity
        } else {
          validatedEntities.add(e);
        }
      }
    }
    sentence.set(MachineReadingAnnotations.EntityMentionsAnnotation.class, validatedEntities);
    
    //
    // TODO: add more domain-specific constraints here
    //
  }
  
  private static boolean contained(EntityMention e, Set<EntityMention> s) {
    for(EntityMention es: s){
      if(e.equals(es)){
        return true;
      }
    }
    return false;
  }

  public void save(String path) throws IOException {
    // nothing to save
  }

  public void train(Annotation dataset) {
    // no training necessary. this contains only some hard-coded runtime constraints
  }

  public void setLoggerLevel(Level level) {
  }
  
}
