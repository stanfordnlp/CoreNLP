package edu.stanford.nlp.ie.machinereading.structure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.IdentityHashSet;

/**
 * Each relation has a type and set of arguments
 * 
 * @author Andrey Gusev
 * @author Mihai
 * @author David McClosky
 * 
 */
public class RelationMention extends ExtractionObject {

  private static final long serialVersionUID = 8962289597607972827L;
  public static final Logger logger = Logger.getLogger(RelationMention.class.getName());

  // index of the next unique id
  private static int MENTION_COUNTER = 0;
  
  public static final String UNRELATED = "_NR";

  /**
   * List of argument names in this relation
   */
  protected List<String> argNames;
  
  /** 
   * List of arguments in this relation
   * If unnamed, arguments MUST be stored in semantic order, e.g., ARG0 must be a person in a employed-by relation
   */
  protected List<ExtractionObject> args;
  
  /** 
   * A signature for a given relation mention, e.g., a concatenation of type and argument strings
   * This is used in KBP, where we merge all RelationMentions corresponding to the same abstract relation 
   */
  protected String signature;
  
  public RelationMention(String objectId, 
      CoreMap sentence,
      Span span,
      String type,
      String subtype,
      List<ExtractionObject> args) {
    super(objectId, sentence, span, type, subtype);
    this.args = args;
    this.argNames = null;
    this.signature = null;
  }
  
  public RelationMention(String objectId, 
      CoreMap sentence,
      Span span,
      String type,
      String subtype,
      List<ExtractionObject> args,
      List<String> argNames) {
    super(objectId, sentence, span, type, subtype);
    this.args = args;
    this.argNames = argNames;
    this.signature = null;
  }
  
  public RelationMention(String objectId, 
      CoreMap sentence, 
      Span span,
      String type,
      String subtype,
      ExtractionObject... args) {
    this(objectId, sentence, span, type, subtype, Arrays.asList(args));
  }

  public boolean argsMatch(RelationMention rel) {
    return argsMatch(rel.getArgs());
  }
  
  public boolean argsMatch(ExtractionObject... inputArgs) {
    return argsMatch(Arrays.asList(inputArgs));
  }

  /**
   * Verifies if the two sets of arguments match
   * @param inputArgs List of arguments
   */
  public boolean argsMatch(List<ExtractionObject> inputArgs) {
    if (inputArgs.size() != this.args.size()) {
      return false;
    }

    for (int ind = 0; ind < this.args.size(); ind++) {
      ExtractionObject a1 = this.args.get(ind);
      ExtractionObject a2 = inputArgs.get(ind);
      if(! a1.equals(a2)) return false;
    }

    return true;
  }

  public List<ExtractionObject> getArgs() {
    return Collections.unmodifiableList(this.args);
  }
  public void setArgs(List<ExtractionObject> args) {
    this.args = args;
  }
  
  /**
   * Fetches the arguments of this relation that are entity mentions
   * @return List of entity-mention args sorted in semantic order
   */
  public List<EntityMention> getEntityMentionArgs() {
    List<EntityMention> ents = new ArrayList<>();
    for(ExtractionObject o: args) {
      if(o instanceof EntityMention){
        ents.add((EntityMention) o);
      }
    }
    return ents;
  }

  public ExtractionObject getArg(int argpos) {
    return this.args.get(argpos);
  }
  
  public List<String> getArgNames() {
    return argNames;
  }
  
  public void setArgNames(List<String> argNames) {
    this.argNames = argNames;
  }
  
  public void addArg(ExtractionObject a) {
    this.args.add(a);
  }
  
  public boolean isNegativeRelation() {
    return isUnrelatedLabel(getType());
  }

  /**
   * Find the left-most position of an argument's syntactic head
   */
  public int getFirstSyntacticHeadPosition() {
    int pos = Integer.MAX_VALUE;
    for (ExtractionObject obj : args) {
      if(obj instanceof EntityMention){
        EntityMention em = (EntityMention) obj;
        if(em.getSyntacticHeadTokenPosition() < pos) {
          pos = em.getSyntacticHeadTokenPosition();
        }
      }
    }
    if(pos != Integer.MAX_VALUE) return pos;
    return -1;
  }

  /**
   * Find the right-most position of an argument's syntactic head
   */
  public int getLastSyntacticHeadPosition() {
    int pos = Integer.MIN_VALUE;
    for (ExtractionObject obj : args) {
      if(obj instanceof EntityMention){
        EntityMention em = (EntityMention) obj;
        if(em.getSyntacticHeadTokenPosition() > pos) {
          pos = em.getSyntacticHeadTokenPosition();
        }
      }
    }
    if(pos != Integer.MIN_VALUE) return pos;
    return -1;
  }
  
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("RelationMention [type=" + type
      + (subType != null ? ", subType=" + subType : "")
      + ", start=" + getExtentTokenStart() + ", end=" + getExtentTokenEnd());
    if(typeProbabilities != null){
      sb.append(", " + probsToString());
    }
    
    if(args != null){
      for(int i = 0; i < args.size(); i ++){
        sb.append("\n\t");
        if(argNames != null) sb.append(argNames.get(i) + " ");
        sb.append(args.get(i));
      }
    }
    sb.append("\n]");
    return sb.toString();
  }
  
  /**
   * Replaces the arguments of this relations with equivalent mentions from the predictedMentions list
   * This works only for arguments that are EntityMention!
   * @param predictedMentions
   */
  public boolean replaceGoldArgsWithPredicted(List<EntityMention> predictedMentions) {
  	List<ExtractionObject> newArgs = new ArrayList<>();
  	for(ExtractionObject arg: args){
  		if(! (arg instanceof EntityMention)){
  			continue;
  		}
  		EntityMention goldEnt = (EntityMention) arg;
  		EntityMention newArg = null;
  		for(EntityMention pred: predictedMentions){
  			if(goldEnt.textEquals(pred)){
  				newArg = pred;
  				break;
  			}
  		}
  		if(newArg != null){
  			newArgs.add(newArg);
  			logger.info("Replacing relation argument: [" + goldEnt + "] with predicted mention [" + newArg + "]");
  		} else {
  			/*
  			logger.info("Failed to match relation argument: " + goldEnt);
  			return false;
  			*/
  			newArgs.add(goldEnt);
  			predictedMentions.add(goldEnt);
  			logger.info("Failed to match relation argument, so keeping gold: " + goldEnt);
  		}
  	}
  	this.args = newArgs;
  	return true;
  }
  
  public void removeArgument(ExtractionObject argToRemove, boolean removeParent) {
    Set<ExtractionObject> thisEvent = new IdentityHashSet<>();
    thisEvent.add(argToRemove);
    removeArguments(thisEvent, removeParent);
  }
  
  public void removeArguments(Set<ExtractionObject> argsToRemove, boolean removeParent) {
    List<ExtractionObject> newArgs = new ArrayList<>();
    List<String> newArgNames = new ArrayList<>();
    for(int i = 0; i < args.size(); i ++){
      ExtractionObject a = args.get(i);
      String n = argNames.get(i);
      if(! argsToRemove.contains(a)){
        newArgs.add(a);
        newArgNames.add(n);
      } else {
        if(a instanceof EventMention && removeParent){
          ((EventMention) a).removeParent(this);
        }
      }
    }
    args = newArgs;
    argNames = newArgNames;
  }
  
  public boolean printableObject(double beam) {
    return printableObject(beam, RelationMention.UNRELATED);
  }
  
  public void setSignature(String s) { signature = s; }
  public String getSignature() { return signature; }
  
  /*
   * Static utility functions
   */

  public static Collection<RelationMention> filterUnrelatedRelations(Collection<RelationMention> relationMentions) {
    Collection<RelationMention> filtered = new ArrayList<>();
    for (RelationMention relation : relationMentions) {
      if (!relation.getType().equals(UNRELATED)) {
        filtered.add(relation);
      }
    }
    return filtered;
  }
  
  /**
   * Creates a new unique id for a relation mention
   * @return the new id
   */
  public static synchronized String makeUniqueId() {
    MENTION_COUNTER++;
    return "RelationMention-" + MENTION_COUNTER;
  }
  
  public static RelationMention createUnrelatedRelation(RelationMentionFactory factory, ExtractionObject ... args) {
    return createUnrelatedRelation(factory, "",args);
  }
  
  private static RelationMention createUnrelatedRelation(RelationMentionFactory factory, String type, ExtractionObject ... args) {
    return factory.constructRelationMention(
        RelationMention.makeUniqueId(), args[0].getSentence(), ExtractionObject.getSpan(args),
        RelationMention.UNRELATED + type, null, Arrays.asList(args), null);
  }
  
  public static boolean isUnrelatedLabel(String label) {
    return label.startsWith(UNRELATED);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof RelationMention)) return false;
    if (!super.equals(o)) return false;

    RelationMention that = (RelationMention) o;

    if (argNames != null ? !argNames.equals(that.argNames) : that.argNames != null) return false;
    if (args != null ? !args.equals(that.args) : that.args != null) return false;
    if (signature != null ? !signature.equals(that.signature) : that.signature != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = argNames != null ? argNames.hashCode() : 0;
    result = 31 * result + (args != null ? args.hashCode() : 0);
    result = 31 * result + (signature != null ? signature.hashCode() : 0);
    return result;
  }
}