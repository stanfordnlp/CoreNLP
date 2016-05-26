package edu.stanford.nlp.trees.ud;

/**
 * Options for enhancing a basic dependency tree.
 *
 * @author Sebastian Schuster
 */
public class EnhancementOptions {

  public boolean processMultiWordPrepositions;
  public boolean enhancePrepositionalModifiers;
  public boolean enhanceConjuncts;
  public boolean propagateDependents;
  public boolean addReferent;
  public boolean addCopyNodes;
  public boolean demoteQuantMod;
  public boolean addXSubj;


  /**
   * Constructor.
   *
   * @param processMultiWordPrepositions Turn multi-word prepositions into flat MWE.
   * @param enhancePrepositionalModifiers Add prepositions to relation labels.
   * @param enhanceConjuncts Add coordinating conjunctions to relation labels.
   * @param propagateDependents Propagate dependents.
   * @param addReferent Add "referent" relation in relative clauses.
   * @param addCopyNodes Add copy nodes for conjoined Ps and PPs.
   * @param demoteQuantMod Turn quantificational modifiers into flat multi-word expressions.
   * @param addXSubj Add relation between controlling subject and controlled verb.
   */
  public EnhancementOptions(boolean processMultiWordPrepositions, boolean enhancePrepositionalModifiers,
                            boolean enhanceConjuncts, boolean propagateDependents, boolean addReferent,
                            boolean addCopyNodes, boolean demoteQuantMod, boolean addXSubj) {
    this.processMultiWordPrepositions = processMultiWordPrepositions;
    this.enhancePrepositionalModifiers = enhancePrepositionalModifiers;
    this.enhanceConjuncts = enhanceConjuncts;
    this.propagateDependents = propagateDependents;
    this.addReferent = addReferent;
    this.addCopyNodes = addCopyNodes;
    this.demoteQuantMod = demoteQuantMod;
    this.addXSubj = addXSubj;
  }

  public EnhancementOptions(EnhancementOptions options) {
    this.processMultiWordPrepositions = options.processMultiWordPrepositions;
    this.enhancePrepositionalModifiers = options.enhancePrepositionalModifiers;
    this.enhanceConjuncts = options.enhanceConjuncts;
    this.propagateDependents = options.propagateDependents;
    this.addReferent = options.addReferent;
    this.addCopyNodes = options.addCopyNodes;
    this.demoteQuantMod = options.demoteQuantMod;
    this.addXSubj = options.addXSubj;
  }


}
