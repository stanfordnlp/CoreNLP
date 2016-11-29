package edu.stanford.nlp.ie;

import edu.stanford.nlp.sequences.ListeningSequenceModel;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.ling.CoreAnnotations;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * This class keeps track of all labeled entities and updates
 * its list whenever the label at a point gets changed.  This allows
 * you to not have to regenerate the list every time, which can be quite
 * inefficient.
 *
 * @author Jenny Finkel
 **/
public abstract class EntityCachingAbstractSequencePrior<IN extends CoreMap> implements ListeningSequenceModel {

  protected int[] sequence;
  protected final int backgroundSymbol;
  protected final int numClasses;
  protected final int[] possibleValues;
  protected final Index<String> classIndex;
  protected final List<IN> doc;

  public EntityCachingAbstractSequencePrior(String backgroundSymbol, Index<String> classIndex, List<IN> doc) {
    this.classIndex = classIndex;
    this.backgroundSymbol = classIndex.indexOf(backgroundSymbol);
    this.numClasses = classIndex.size();
    this.possibleValues = new int[numClasses];
    for (int i=0; i<numClasses; i++) {
      possibleValues[i] = i;
    }
    this.doc = doc;
  }

  private boolean VERBOSE = false;

  Entity[] entities;

  @Override
  public int leftWindow() {
    return Integer.MAX_VALUE; // not Markovian!
  }

  @Override
  public int rightWindow() {
    return Integer.MAX_VALUE; // not Markovian!
  }

  @Override
  public int[] getPossibleValues(int position) {
    return possibleValues;
  }

  @Override
  public double scoreOf(int[] sequence, int pos) {
    return scoresOf(sequence, pos)[sequence[pos]];
  }

  /**
   * @return the length of the sequence
   */
  @Override
  public int length() {
    return doc.size();
  }

  /**
   * get the number of classes in the sequence model.
   */
  public int getNumClasses() {
    return classIndex.size();
  }

  public  double[] getConditionalDistribution (int[] sequence, int position) {
    double[] probs = scoresOf(sequence, position);
    ArrayMath.logNormalize(probs);
    probs = ArrayMath.exp(probs);
    //System.out.println(this);
    return probs;
  }

  @Override
  public  double[] scoresOf (int[] sequence, int position) {
    double[] probs = new double[numClasses];
    int origClass = sequence[position];
    for (int label = 0; label < numClasses; label++) {
      sequence[position] = label;
      updateSequenceElement(sequence, position, 0);
      probs[label] = scoreOf(sequence);
    }
    sequence[position] = origClass;
    //System.out.println(this);
    return probs;
  }

  @Override
  public void setInitialSequence(int[] initialSequence) {
    this.sequence = initialSequence;
    entities = new Entity[initialSequence.length];
    // Arrays.fill(entities, null); // not needed; Java arrays zero initialized
    for (int i = 0; i < initialSequence.length; i++) {
      if (initialSequence[i] != backgroundSymbol) {
        Entity entity = extractEntity(initialSequence, i);
        addEntityToEntitiesArray(entity);
        i += entity.words.size() - 1;
      }
    }
  }

  private void addEntityToEntitiesArray(Entity entity) {
    for (int j = entity.startPosition; j < entity.startPosition + entity.words.size(); j++) {
      entities[j] = entity;
    }
  }

  /**
   * extracts the entity starting at the given position
   * and adds it to the entity list.  returns the index
   * of the last element in the entity (<b>not</b> index+1)
   **/
  public Entity extractEntity(int[] sequence, int position) {
    Entity entity = new Entity();
    entity.type = sequence[position];
    entity.startPosition = position;
    entity.words = new ArrayList<>();
    for ( ; position < sequence.length; position++) {
      if (sequence[position] == entity.type) {
      	String word = doc.get(position).get(CoreAnnotations.TextAnnotation.class);
        entity.words.add(word);
        if (position == sequence.length - 1) {
          entity.otherOccurrences = otherOccurrences(entity);
        }
      } else {
        entity.otherOccurrences = otherOccurrences(entity);
        break;
      }
    }
    return entity;
  }

  /**
   * finds other locations in the sequence where the sequence of
   * words in this entity occurs.
   */
  public int[] otherOccurrences(Entity entity){
    List<Integer> other = new ArrayList<>();
    for (int i = 0; i < doc.size(); i++) {
      if (i == entity.startPosition) { continue; }
      if (matches(entity, i)) {
        other.add(Integer.valueOf(i));
      }
    }
    return toArray(other);
  }

  public static int[] toArray(List<Integer> list) {
    int[] arr = new int[list.size()];
    for (int i = 0; i < arr.length; i++) {
      arr[i] = list.get(i);
    }
    return arr;
  }

  public boolean matches(Entity entity, int position) {
  	String word = doc.get(position).get(CoreAnnotations.TextAnnotation.class);
    if (word.equalsIgnoreCase(entity.words.get(0))) {
      //boolean matches = true;
      for (int j = 1; j < entity.words.size(); j++) {
        if (position + j >= doc.size()) {
          return false;
        }
        String nextWord = doc.get(position+j).get(CoreAnnotations.TextAnnotation.class);
        if (!nextWord.equalsIgnoreCase(entity.words.get(j))) {
          return false;
        }
      }
      return true;
    }
    return false;
  }


  public boolean joiningTwoEntities(int[] sequence, int position) {
    if (sequence[position] == backgroundSymbol) { return false; }
    if (position > 0 && position < sequence.length - 1) {
      return (sequence[position] == sequence[position - 1] &&
              sequence[position] == sequence[position + 1]);
    }
    return false;
  }

  public boolean splittingTwoEntities(int[] sequence, int position) {
    if (position > 0 && position < sequence.length - 1) {
      return (entities[position - 1] == entities[position + 1] &&
              entities[position - 1] != null);
    }
    return false;
  }

  public boolean appendingEntity(int[] sequence, int position) {
    if (position > 0) {
      if (entities[position - 1] == null) { return false; }
      Entity prev = entities[position - 1];
      return (sequence[position] == sequence[position - 1] &&
              prev.startPosition + prev.words.size() == position);
    }
    return false;
  }

  public boolean prependingEntity(int[] sequence, int position) {
    if (position < sequence.length - 1) {
      if (entities[position + 1] == null) { return false; }
      return (sequence[position] == sequence[position + 1]);
    }
    return false;
  }

  public boolean addingSingletonEntity(int[] sequence, int position) {
    if (sequence[position] == backgroundSymbol) { return false; }
    if (position > 0) {
      if (sequence[position - 1] == sequence[position]) { return false; }
    }
    if (position < sequence.length - 1) {
      if (sequence[position + 1] == sequence[position]) { return false; }
    }
    return true;
  }

  public boolean removingEndOfEntity(int[] sequence, int position) {
    if (position > 0) {
      if (sequence[position - 1] == backgroundSymbol) { return false; }
      Entity prev = entities[position - 1];
      if (prev != null) {
        return (prev.startPosition + prev.words.size() > position);
      }
    }
    return false;
  }

  public boolean removingBeginningOfEntity(int[] sequence, int position) {
    if (position < sequence.length - 1) {
      if (sequence[position + 1] == backgroundSymbol) { return false; }
      Entity next = entities[position + 1];
      if (next != null) {
        return (next.startPosition <= position);
      }
    }
    return false;
  }

  public boolean noChange(int[] sequence, int position) {
    if (position > 0) {
      if (sequence[position - 1] == sequence[position]) {
        return entities[position - 1] == entities[position];
      }
    }
    if (position < sequence.length - 1) {
      if (sequence[position + 1] == sequence[position]) {
        return entities[position] == entities[position + 1];
      }
    }
    // actually, can't tell.  either no change, or singleton
    // changed type
    return false;
  }

  @Override
  public void updateSequenceElement(int[] sequence, int position, int oldVal) {
    if (VERBOSE) System.out.println("changing position "+position+" from " +classIndex.get(oldVal)+" to "+classIndex.get(sequence[position]));

    this.sequence = sequence;

    // no change?
    if (noChange(sequence, position)) {
      if (VERBOSE) System.out.println("no change");
      if (VERBOSE) System.out.println(this);
      return;
    }
    // are we joining 2 entities?
    else if (joiningTwoEntities(sequence, position)) {
      if (VERBOSE) System.out.println("joining 2 entities");
      Entity newEntity = new Entity();
      Entity prev = entities[position - 1];
      Entity next = entities[position + 1];
      newEntity.startPosition = prev.startPosition;
      newEntity.words = new ArrayList<>();
      newEntity.words.addAll(prev.words);
      String word = doc.get(position).get(CoreAnnotations.TextAnnotation.class);
      newEntity.words.add(word);
      newEntity.words.addAll(next.words);
      newEntity.type = sequence[position];
      List<Integer> other = new ArrayList<>();
      for (int i = 0; i < prev.otherOccurrences.length; i++) {
        int pos = prev.otherOccurrences[i];
        if (matches(newEntity, pos)) {
          other.add(Integer.valueOf(pos));
        }
      }
      newEntity.otherOccurrences = toArray(other);
      addEntityToEntitiesArray(newEntity);
      if (VERBOSE) System.out.println(this);
      return;
    }
    // are we splitting up an entity?
    else if (splittingTwoEntities(sequence, position)) {
      if (VERBOSE) System.out.println("splitting into 2 entities");
      Entity entity = entities[position];
      Entity prev = new Entity();
      prev.type = entity.type;
      prev.startPosition = entity.startPosition;
      prev.words = new ArrayList<>(entity.words.subList(0, position - entity.startPosition));
      prev.otherOccurrences = otherOccurrences(prev);
      addEntityToEntitiesArray(prev);
      Entity next = new Entity();
      next.type = entity.type;
      next.startPosition = position + 1;
      next.words = new ArrayList<>(entity.words.subList(position - entity.startPosition + 1, entity.words.size()));
      next.otherOccurrences = otherOccurrences(next);
      addEntityToEntitiesArray(next);
      if (sequence[position] == backgroundSymbol) {
        entities[position] = null;
      } else {
        Entity newEntity = new Entity();
        newEntity.startPosition = position;
        newEntity.type = sequence[position];
        newEntity.words = new ArrayList<>();
        String word = doc.get(position).get(CoreAnnotations.TextAnnotation.class);
        newEntity.words.add(word);
        newEntity.otherOccurrences = otherOccurrences(newEntity);
        entities[position] = newEntity;
      }
      if (VERBOSE) System.out.println(this);
      return;
    }
    // are we prepending to an entity ?
    else if (prependingEntity(sequence, position)) {
      if (VERBOSE) System.out.println("prepending entity");
      Entity newEntity = new Entity();
      Entity next = entities[position + 1];
      newEntity.startPosition = position;
      newEntity.words = new ArrayList<>();
      String word = doc.get(position).get(CoreAnnotations.TextAnnotation.class);
      newEntity.words.add(word);
      newEntity.words.addAll(next.words);
      newEntity.type = sequence[position];
      //List<Integer> other = new ArrayList<Integer>();
      newEntity.otherOccurrences = otherOccurrences(newEntity);
      addEntityToEntitiesArray(newEntity);

      if (removingEndOfEntity(sequence, position)) {
        if (VERBOSE) System.out.println(" ... and removing end of previous entity.");
        Entity prev = entities[position - 1];
        prev.words.remove(prev.words.size()-1);
        prev.otherOccurrences = otherOccurrences(prev);
      }
      if (VERBOSE) System.out.println(this);
      return;
    }
    // are we appending to an entity ?
    else if (appendingEntity(sequence, position)) {
      if (VERBOSE) System.out.println("appending entity");
      Entity newEntity = new Entity();
      Entity prev = entities[position - 1];
      newEntity.startPosition = prev.startPosition;
      newEntity.words = new ArrayList<>();
      newEntity.words.addAll(prev.words);
      String word = doc.get(position).get(CoreAnnotations.TextAnnotation.class);
      newEntity.words.add(word);
      newEntity.type = sequence[position];
      List<Integer> other = new ArrayList<>();
      for (int i = 0; i < prev.otherOccurrences.length; i++) {
        int pos = prev.otherOccurrences[i];
        if (matches(newEntity, pos)) {
          other.add(Integer.valueOf(pos));
        }
      }
      newEntity.otherOccurrences = toArray(other);
      addEntityToEntitiesArray(newEntity);

      if (removingBeginningOfEntity(sequence, position)) {
        if (VERBOSE) System.out.println(" ... and removing beginning of next entity.");
        entities[position + 1].words.remove(0);
        entities[position + 1].startPosition++;
      }
      if (VERBOSE) System.out.println(this);
      return;
    }
    // adding new singleton entity
    else if (addingSingletonEntity(sequence, position)) {
      Entity newEntity = new Entity();
      if (VERBOSE) System.out.println("adding singleton entity");
      newEntity.startPosition = position;
      newEntity.words = new ArrayList<>();
      String word = doc.get(position).get(CoreAnnotations.TextAnnotation.class);
      newEntity.words.add(word);
      newEntity.type = sequence[position];
      newEntity.otherOccurrences = otherOccurrences(newEntity);
      addEntityToEntitiesArray(newEntity);

      if (removingEndOfEntity(sequence, position)) {
        if (VERBOSE) System.out.println(" ... and removing end of previous entity.");
        Entity prev = entities[position - 1];
        prev.words.remove(prev.words.size()-1);
        prev.otherOccurrences = otherOccurrences(prev);
      }

      if (removingBeginningOfEntity(sequence, position)) {
        if (VERBOSE) System.out.println(" ... and removing beginning of next entity.");
        entities[position + 1].words.remove(0);
        entities[position + 1].startPosition++;
      }

      if (VERBOSE) System.out.println(this);
      return;
    }
    // are splitting off the prev entity?
    else if (removingEndOfEntity(sequence, position)) {
      if (VERBOSE) System.out.println("splitting off prev entity");
      Entity prev = entities[position - 1];
      prev.words.remove(prev.words.size() - 1);
      prev.otherOccurrences = otherOccurrences(prev);
      entities[position] = null;
    }
    // are we splitting off the next entity?
    else if (removingBeginningOfEntity(sequence, position)) {
      if (VERBOSE) System.out.println("splitting off next entity");
      Entity next = entities[position + 1];
      next.words.remove(0);
      next.startPosition++;
      next.otherOccurrences = otherOccurrences(next);
      entities[position] = null;
    } else {
      entities[position] = null;
    }
    if (VERBOSE) System.out.println(this);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < entities.length; i++) {
      sb.append(i);
      sb.append("\t");
      String word = doc.get(i).get(CoreAnnotations.TextAnnotation.class);
      sb.append(word);
      sb.append("\t");
      sb.append(classIndex.get(sequence[i]));
      if (entities[i] != null) {
        sb.append("\t");
        sb.append(entities[i].toString(classIndex));
      }
      sb.append("\n");
    }
    return sb.toString();
  }

  public String toString(int pos) {
    StringBuilder sb = new StringBuilder();
    for (int i = Math.max(0, pos - 10); i < Math.min(entities.length, pos + 10); i++) {
      sb.append(i);
      sb.append("\t");
      String word = doc.get(i).get(CoreAnnotations.TextAnnotation.class);
      sb.append(word);
      sb.append("\t");
      sb.append(classIndex.get(sequence[i]));
      if (entities[i] != null) {
        sb.append("\t");
        sb.append(entities[i].toString(classIndex));
      }
      sb.append("\n");
    }
    return sb.toString();
  }
}

class Entity {
  public int startPosition;
  public List<String> words;
  public int type;

  /**
   * the beginning index of other locations where this sequence of
   * words appears.
   */
  public int[] otherOccurrences;

  public String toString(Index<String> classIndex) {
    StringBuilder sb = new StringBuilder();
    sb.append("\"");
    sb.append(StringUtils.join(words, " "));
    sb.append("\" start: ");
    sb.append(startPosition);
    sb.append(" type: ");
    sb.append(classIndex.get(type));
    sb.append(" other_occurrences: ");
    sb.append(Arrays.toString(otherOccurrences));
    return sb.toString();
  }
}
