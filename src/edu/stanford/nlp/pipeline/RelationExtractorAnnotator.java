package edu.stanford.nlp.pipeline;

import java.util.ArrayList;
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
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations.TreeAnnotation;
import edu.stanford.nlp.util.ArraySet;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;

public class RelationExtractorAnnotator implements Annotator {
  MachineReading mr;
  private static boolean verbose = false;

  public RelationExtractorAnnotator(Properties props){
    verbose = Boolean.parseBoolean(props.getProperty("relex.verbose", "false"));
    String entityModel = props.getProperty("relex.entity.model", DefaultPaths.DEFAULT_RELEX_ENTITY_MODEL);
    String relationModel = props.getProperty("relex.relation.model", DefaultPaths.DEFAULT_RELEX_RELATION_MODEL);
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
      
      // the NFLTokenizer might have changed some of the token texts (e.g., "10-5" -> "10 to 5")
      // revert all tokens to their original texts
      boolean verboseRevert = false;
      String origText = annotation.get(CoreAnnotations.TextAnnotation.class);
      if(origText == null) throw new RuntimeException("Found corpus without text!");
      if(verboseRevert) System.err.println("REVERTING SENT: " + origSent.get(TextAnnotation.class));
      List<CoreLabel> tokens = origSent.get(TokensAnnotation.class);
      List<Pair<Integer, String>> changes = new ArrayList<Pair<Integer,String>>();
      int position = 0;
      for(CoreLabel token: tokens) {
        String tokenText = token.word();
        if(verboseRevert) System.err.println("TOKEN " + tokenText + " " + token.beginPosition() + " " + token.endPosition());
        String origToken = origText.substring(token.beginPosition(), token.endPosition());
        if(! origToken.equals(tokenText)){
          if(verboseRevert) System.err.println("Found difference at position #" + position + ": token [" + tokenText + "] vs text [" + origToken + "]");
          token.set(TextAnnotation.class, origToken);
          changes.add(new Pair<Integer, String>(position, origToken));
        }
        position ++;
      }
      // revert Tree leaves as well, if tokens were modified
      Tree tree = origSent.get(TreeAnnotation.class);
      if(tree != null && changes.size() > 0){
        List<Tree> leaves = tree.getLeaves();
        for(Pair<Integer, String> change: changes) {
          Tree leaf = leaves.get(change.first);
          if(verboseRevert) System.err.println("CHANGING LEAF " + leaf);
          leaf.setValue(change.second);
          if(verboseRevert) System.err.println("NEW LEAF: " + leaf);
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
