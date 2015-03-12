package edu.stanford.nlp.ie;

import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.sequences.SequenceModel;
import edu.stanford.nlp.sequences.SequenceListener;
import edu.stanford.nlp.ling.CoreAnnotations;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * This class keeps track of all labeled entities and updates the
 * its list whenever the label at a point gets changed.  This allows
 * you to not have to regereate the list everytime, which can be quite
 * inefficient.
 *
 * @author Mengqiu Wang
 **/
public abstract class EntityCachingAbstractSequencePriorBIO <IN extends CoreMap> implements SequenceModel, SequenceListener {

  protected int[] sequence;
  protected int backgroundSymbol;
  protected int numClasses;
  protected int[] possibleValues;
  protected Index<String> classIndex;
  protected Index<String> tagIndex;
  private List<String> wordDoc;

  public EntityCachingAbstractSequencePriorBIO(String backgroundSymbol, Index<String> classIndex, Index<String> tagIndex, List<IN> doc) {
    this.classIndex = classIndex;
    this.tagIndex = tagIndex;
    this.backgroundSymbol = classIndex.indexOf(backgroundSymbol);
    this.numClasses = classIndex.size();
    this.possibleValues = new int[numClasses];
    for (int i=0; i<numClasses; i++) {
      possibleValues[i] = i;
    }
    this.wordDoc = new ArrayList<String>(doc.size());
    for (IN w: doc) {
      wordDoc.add(w.get(CoreAnnotations.TextAnnotation.class));
    }
  }

  private boolean VERBOSE = false;

  EntityBIO[] entities;

  public int leftWindow() {
    return Integer.MAX_VALUE; // not Markovian!
  }

  public int rightWindow() {
    return Integer.MAX_VALUE; // not Markovian!
  }

  public int[] getPossibleValues(int position) {
    return possibleValues;
  }

  public double scoreOf(int[] sequence, int pos) {
    return scoresOf(sequence, pos)[sequence[pos]];
  }

  /**
   * @return the length of the sequence
   */
  public int length() {
    return wordDoc.size();
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

  public  double[] scoresOf (int[] sequence, int position) {
    double[] probs = new double[numClasses];
    int origClass = sequence[position];
    int oldVal = origClass;
    // if (BisequenceEmpiricalNERPrior.debugIndices.indexOf(position) != -1)
    //  EmpiricalNERPriorBIO.DEBUG = true;  
    for (int label = 0; label < numClasses; label++) {
      if (label != origClass) {
        sequence[position] = label;
        updateSequenceElement(sequence, position, oldVal);
        probs[label] = scoreOf(sequence);
        oldVal = label;
        // if (BisequenceEmpiricalNERPrior.debugIndices.indexOf(position) != -1)
        //   System.out.println(this);
      }
    }
    sequence[position] = origClass;
    updateSequenceElement(sequence, position, oldVal);
    probs[origClass] = scoreOf(sequence);
    // EmpiricalNERPriorBIO.DEBUG = false;
    return probs;
  }

  public void setInitialSequence(int[] initialSequence) {
    this.sequence = initialSequence;
    entities = new EntityBIO[initialSequence.length];
    Arrays.fill(entities, null);
    String rawTag = null;
    String[] parts = null;
    for (int i = 0; i < initialSequence.length; i++) {
      if (initialSequence[i] != backgroundSymbol) {
        rawTag = classIndex.get(sequence[i]);
        parts = rawTag.split("-");
        //TODO(mengqiu) this needs to be updated, so that initial can be I as well
        if (parts[0].equals("B")) { // B-
          EntityBIO entity = extractEntity(initialSequence, i, parts[1]);
          addEntityToEntitiesArray(entity);
          i += entity.words.size() - 1;
        }
      }
    }
  }

  private void addEntityToEntitiesArray(EntityBIO entity) {
    for (int j = entity.startPosition; j < entity.startPosition + entity.words.size(); j++) {
      entities[j] = entity;
    }
  }

  /**
   * extracts the entity starting at the given position
   * and adds it to the entity list.  returns the index
   * of the last element in the entity (<b>not</b> index+1)
   **/
  public EntityBIO extractEntity(int[] sequence, int position, String tag) {
    EntityBIO entity = new EntityBIO();
    entity.type = tagIndex.indexOf(tag);
    entity.startPosition = position;
    entity.words = new ArrayList<String>();
    entity.words.add(wordDoc.get(position));
    int pos = position + 1;
    String rawTag = null;
    String[] parts = null;
    for ( ; pos < sequence.length; pos++) {
      rawTag = classIndex.get(sequence[pos]);
      parts = rawTag.split("-");
      if (parts[0].equals("I") && parts[1].equals(tag)) {
      	String word = wordDoc.get(pos);
        entity.words.add(word);
      } else {
        break;
      }
    }
    entity.otherOccurrences = otherOccurrences(entity);
    return entity;
  }

  /**
   * finds other locations in the sequence where the sequence of
   * words in this entity occurs.
   */
  public int[] otherOccurrences(EntityBIO entity){
    List<Integer> other = new ArrayList<Integer>();
    for (int i = 0; i < wordDoc.size(); i++) {
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

  public boolean matches(EntityBIO entity, int position) {
  	String word = wordDoc.get(position);
    if (word.equalsIgnoreCase(entity.words.get(0))) {
      for (int j = 1; j < entity.words.size(); j++) {
        if (position + j >= wordDoc.size()) {
          return false;
        }
        String nextWord = wordDoc.get(position+j);
        if (!nextWord.equalsIgnoreCase(entity.words.get(j))) {
          return false;
        }
      }
      return true;
    }
    return false;
  }

  public void updateSequenceElement(int[] sequence, int position, int oldVal) {
    this.sequence = sequence;

    if (sequence[position] == oldVal)
      return;

    if (VERBOSE) System.err.println("changing position "+position+" from " +classIndex.get(oldVal)+" to "+classIndex.get(sequence[position]));

    if (sequence[position] == backgroundSymbol) { // new tag is O
      String oldRawTag = classIndex.get(oldVal);
      String[] oldParts = oldRawTag.split("-");
      if (oldParts[0].equals("B")) { // old tag was a B, current entity definitely affected, also check next one
        EntityBIO entity = entities[position];
        if (entity == null)
          throw new RuntimeException("oldTag starts with B, entity at position should not be null");
        // remove entities for all words affected by this entity
        for (int i=0; i < entity.words.size(); i++) {
          entities[position+i] = null;
        }
      } else { // old tag was a I, check previous one
        if (entities[position] != null) { // this was part of an entity, shortened
          if (VERBOSE) System.err.println("splitting off prev entity");
          EntityBIO oldEntity = entities[position];
          int oldLen = oldEntity.words.size();
          int offset = position - oldEntity.startPosition;
          List<String> newWords = new ArrayList<String>();
          for (int i=0; i<offset; i++) {
            newWords.add(oldEntity.words.get(i));
          }
          oldEntity.words = newWords;
          oldEntity.otherOccurrences = otherOccurrences(oldEntity);
          // need to clean any remaining entity
          for (int i=0 ; i < oldLen - offset; i++) {
            entities[position+i] = null;
          }
          if (VERBOSE && position > 0) 
            System.err.println("position:" + position +", entities[position-1] = " + entities[position-1].toString(tagIndex));
        } // otherwise, non-entity part I-xxx -> O, no enitty affected
      }
    } else {
      String rawTag = classIndex.get(sequence[position]);
      String[] parts = rawTag.split("-");
      if (parts[0].equals("B")) { // new tag is B
        if (oldVal == backgroundSymbol) { // start a new entity, may merge with the next word
          EntityBIO entity = extractEntity(sequence, position, parts[1]);
          addEntityToEntitiesArray(entity);
        } else {
          String oldRawTag = classIndex.get(oldVal);
          String[] oldParts = oldRawTag.split("-");
          if (oldParts[0].equals("B")) { // was a different B-xxx
            EntityBIO oldEntity = entities[position];
            if (oldEntity.words.size() > 1) { // remove all old entity, add new singleton
              for (int i=0; i< oldEntity.words.size(); i++)
                entities[position+i] = null;
              EntityBIO entity = extractEntity(sequence, position, parts[1]);
              addEntityToEntitiesArray(entity);
            } else { // extract entity
              EntityBIO entity = extractEntity(sequence, position, parts[1]);
              addEntityToEntitiesArray(entity);
            }
          } else { // was I
            EntityBIO oldEntity = entities[position];
            if (oldEntity != null) {// break old entity
              int oldLen = oldEntity.words.size();
              int offset = position - oldEntity.startPosition;
              List<String> newWords = new ArrayList<String>();
              for (int i=0; i<offset; i++) {
                newWords.add(oldEntity.words.get(i));
              }
              oldEntity.words = newWords;
              oldEntity.otherOccurrences = otherOccurrences(oldEntity);
              // need to clean any remaining entity
              for (int i=0 ; i < oldLen - offset; i++) {
                entities[position+i] = null;
              }
            }
            EntityBIO entity = extractEntity(sequence, position, parts[1]);
            addEntityToEntitiesArray(entity);
          }
        }
      } else { // new tag is I
        if (oldVal == backgroundSymbol) { // check if previous entity extends into this one
          if (position > 0) {
            if (entities[position-1] != null) {
              String oldTag = tagIndex.get(entities[position-1].type);
              EntityBIO entity = extractEntity(sequence, position-1-entities[position-1].words.size()+1, oldTag);
              addEntityToEntitiesArray(entity);
            }
          }
        } else {
          String oldRawTag = classIndex.get(oldVal);
          String[] oldParts = oldRawTag.split("-");
          if (oldParts[0].equals("B")) { // was a B, clean the B entity first, then check if previous is an entity
            EntityBIO oldEntity = entities[position];
            for (int i=0; i<oldEntity.words.size(); i++)
              entities[position+i] = null;

            if (position > 0) {
              if (entities[position-1] != null) {
                String oldTag = tagIndex.get(entities[position-1].type);
                if (VERBOSE)
                  System.err.println("position:" + position +", entities[position-1] = " + entities[position-1].toString(tagIndex));
                EntityBIO entity = extractEntity(sequence, position-1-entities[position-1].words.size()+1, oldTag);
                addEntityToEntitiesArray(entity);
              }
            }
          } else { // was a differnt I-xxx, 
            if (entities[position] != null) { // shorten the previous one, remove any additional parts
              EntityBIO oldEntity = entities[position];
              int oldLen = oldEntity.words.size();
              int offset = position - oldEntity.startPosition;
              List<String> newWords = new ArrayList<String>();
              for (int i=0; i<offset; i++) {
                newWords.add(oldEntity.words.get(i));
              }
              oldEntity.words = newWords;
              oldEntity.otherOccurrences = otherOccurrences(oldEntity);
              // need to clean any remaining entity
              for (int i=0 ; i < oldLen - offset; i++) {
                entities[position+i] = null;
              }
            } else { // re-calc entity of the previous entity if exist
              if (position > 0) {
                if (entities[position-1] != null) {
                  String oldTag = tagIndex.get(entities[position-1].type);
                  EntityBIO entity = extractEntity(sequence, position-1-entities[position-1].words.size()+1, oldTag);
                  addEntityToEntitiesArray(entity);
                }
              }
            }
          }
        }
      }
    }
  }

  @Override
  public String toString() {
    StringBuffer sb = new StringBuffer();
    for (int i = 0; i < entities.length; i++) {
      sb.append(i);
      sb.append("\t");
      String word = wordDoc.get(i);
      sb.append(word);
      sb.append("\t");
      sb.append(classIndex.get(sequence[i]));
      if (entities[i] != null) {
        sb.append("\t");
        sb.append(entities[i].toString(tagIndex));
      }
      sb.append("\n");
    }
    return sb.toString();
  }

  public String toString(int pos) {
    StringBuffer sb = new StringBuffer();
    for (int i = Math.max(0, pos - 3); i < Math.min(entities.length, pos + 3); i++) {
      sb.append(i);
      sb.append("\t");
      String word = wordDoc.get(i);
      sb.append(word);
      sb.append("\t");
      sb.append(classIndex.get(sequence[i]));
      if (entities[i] != null) {
        sb.append("\t");
        sb.append(entities[i].toString(tagIndex));
      }
      sb.append("\n");
    }
    return sb.toString();
  }
}

class EntityBIO {
  public int startPosition;
  public List<String> words;
  public int type;

  /**
   * the begining index of other locations where this sequence of
   * words appears.
   */
  public int[] otherOccurrences;

  public String toString(Index<String> tagIndex) {
    StringBuffer sb = new StringBuffer();
    sb.append("\"");
    sb.append(StringUtils.join(words, " "));
    sb.append("\" start: ");
    sb.append(startPosition);
    sb.append(" type: ");
    sb.append(tagIndex.get(type));
    sb.append(" other_occurrences: ");
    sb.append(Arrays.toString(otherOccurrences));
    return sb.toString();
  }
}
