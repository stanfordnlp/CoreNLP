package edu.stanford.nlp.pipeline;

import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import edu.stanford.nlp.ie.machinereading.BasicEntityExtractor;
import edu.stanford.nlp.ie.machinereading.BasicRelationExtractor;
import edu.stanford.nlp.ie.machinereading.Extractor;
import edu.stanford.nlp.ie.machinereading.MachineReading;
import edu.stanford.nlp.ie.machinereading.domains.roth.RothCONLL04Reader;
import edu.stanford.nlp.ie.machinereading.structure.EntityMention;
import edu.stanford.nlp.ie.machinereading.structure.MachineReadingAnnotations;
import edu.stanford.nlp.ie.machinereading.structure.RelationMention;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.util.ArraySet;
import edu.stanford.nlp.util.CoreMap;

public class RelationExtractorAnnotator implements Annotator {
  MachineReading mr;
  private static boolean verbose = false;

  public RelationExtractorAnnotator(Properties props){
    verbose = Boolean.parseBoolean(props.getProperty("sup.relation.verbose", "false"));
    String entityModel = props.getProperty("sup.relation.entity.model", DefaultPaths.DEFAULT_SUP_RELATION_EX_ENTITY_MODEL);
    String relationModel = props.getProperty("sup.relation.model", DefaultPaths.DEFAULT_SUP_RELATION_EX_RELATION_MODEL);
    try {
      Extractor entityExtractor = BasicEntityExtractor.load(entityModel, BasicEntityExtractor.class, true);
      Extractor relationExtractor = BasicRelationExtractor.load(relationModel);
      mr = MachineReading.makeMachineReadingForAnnotation(new RothCONLL04Reader(), entityExtractor, relationExtractor, null, null,
          null, true, verbose);
    } catch(Exception e){
      e.printStackTrace();
      throw new RuntimeException(e);
    }
  }
  @Override
  public void annotate(Annotation annotation) {
 // extract entities and relations
    Annotation output = mr.annotate(annotation);
    
    // transfer entities/relations back to the original annotation
    List<CoreMap> outputSentences = output.get(SentencesAnnotation.class);
    List<CoreMap> origSentences = annotation.get(SentencesAnnotation.class);
    for(int i = 0; i < outputSentences.size(); i ++){
      CoreMap outSent = outputSentences.get(i);
      CoreMap origSent = origSentences.get(i);
      
      // set entities
      List<EntityMention> entities = outSent.get(MachineReadingAnnotations.EntityMentionsAnnotation.class);
      origSent.set(MachineReadingAnnotations.EntityMentionsAnnotation.class, entities);
      if(verbose && entities != null){
        System.err.println("Extracted the following entities:");
        for(EntityMention e: entities){
          System.err.println("\t" + e);
        }
      }
      
      // set relations
      List<RelationMention> relations = outSent.get(MachineReadingAnnotations.RelationMentionsAnnotation.class);
      origSent.set(MachineReadingAnnotations.RelationMentionsAnnotation.class, relations);
      if(verbose && relations != null){
        System.err.println("Extracted the following relations:");
        for(RelationMention r: relations){
          if(! r.getType().equals(RelationMention.UNRELATED)){
            System.err.println(r);
          }
        }
      }
      
    }    
  }

  @Override
  public Set<Requirement> requires() {
    return new ArraySet<Requirement>(TOKENIZE_REQUIREMENT, SSPLIT_REQUIREMENT, POS_REQUIREMENT, NER_REQUIREMENT, PARSE_REQUIREMENT);
  }

  @Override
  public Set<Requirement> requirementsSatisfied() {
    return Collections.singleton(RELATION_EXTRACTOR_REQUIREMENT);
  }

}
