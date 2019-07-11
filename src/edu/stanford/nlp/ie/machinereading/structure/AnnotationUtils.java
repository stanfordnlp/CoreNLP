package edu.stanford.nlp.ie.machinereading.structure; 
import edu.stanford.nlp.util.logging.Redwood;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.sequences.SeqClassifierFlags;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.StringUtils;

/**
 * Utilities to manipulate Annotations storing datasets or sentences with Machine Reading info
 * @author Mihai
 *
 */
public class AnnotationUtils  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(AnnotationUtils.class);
  private AnnotationUtils() {} // only static methods

  /**
   * Given a list of sentences (as CoreMaps), wrap it in a new Annotation.
   */
  public static Annotation createDataset(List<CoreMap> sentences) {
    Annotation dataset = new Annotation("");
    addSentences(dataset,sentences);
    return dataset;
  }

  /**
   * Randomized shuffle of all sentences int this dataset
   * @param dataset
   */
  public static void shuffleSentences(CoreMap dataset) {
    List<CoreMap> sentences = dataset.get(CoreAnnotations.SentencesAnnotation.class);

    // we use a constant seed for replicability of experiments
    Collections.shuffle(sentences, new Random(0));

    dataset.set(CoreAnnotations.SentencesAnnotation.class, sentences);
  }

  /**
   * Converts the labels of all entity mentions in this dataset to sequences of CoreLabels
   * @param dataset
   * @param annotationsToSkip
   * @param useSubTypes
   */
  public static List<List<CoreLabel>> entityMentionsToCoreLabels(CoreMap dataset, Set<String> annotationsToSkip, boolean useSubTypes, boolean useBIO) {
    List<List<CoreLabel>> retVal = new ArrayList<>();
    List<CoreMap> sentences = dataset.get(CoreAnnotations.SentencesAnnotation.class);

    for (CoreMap sentence : sentences) {
      List<CoreLabel> labeledSentence = sentenceEntityMentionsToCoreLabels(sentence, true, annotationsToSkip, null, useSubTypes, useBIO);
      assert(labeledSentence != null);
      retVal.add(labeledSentence);
    }

    return retVal;
  }

  /**
   * Converts the labels of all entity mentions in this sentence to sequences of CoreLabels
   * @param sentence
   * @param addAnswerAnnotation
   * @param annotationsToSkip
   * @param useSubTypes
   */
  public static List<CoreLabel> sentenceEntityMentionsToCoreLabels(
      CoreMap sentence,
      boolean addAnswerAnnotation,
      Set<String> annotationsToSkip,
      Set<String> mentionTypesToUse,
      boolean useSubTypes,
      boolean useBIO) {
    /*
    Tree completeTree = sentence.get(TreeCoreAnnotations.TreeAnnotation.class);
    if(completeTree == null){
      throw new RuntimeException("ERROR: TreeAnnotation MUST be set before calling this method!");
    }
    */

    //
    // Set TextAnnotation and PartOfSpeechAnnotation (using the parser data)
    //
    /*
    List<CoreLabel> labels = new ArrayList<CoreLabel>();
    List<Tree> tokenList = completeTree.getLeaves();
    for (Tree tree : tokenList) {
      Word word = new Word(tree.label());
      CoreLabel label = new CoreLabel();
      label.set(CoreAnnotations.TextAnnotation.class, word.value());
      if (addAnswerAnnotation) {
        label.set(CoreAnnotations.AnswerAnnotation.class,
            SeqClassifierFlags.DEFAULT_BACKGROUND_SYMBOL);
      }
      label.set(CoreAnnotations.PartOfSpeechAnnotation.class, tree.parent(completeTree).label().value());
      labels.add(label);
    }
    */
    // use the token CoreLabels not the parser data => more robust
    List<CoreLabel> labels = new ArrayList<>();
    for(CoreLabel l: sentence.get(CoreAnnotations.TokensAnnotation.class)){
      CoreLabel nl = new CoreLabel(l);
      if (addAnswerAnnotation) {
        nl.set(CoreAnnotations.AnswerAnnotation.class, SeqClassifierFlags.DEFAULT_BACKGROUND_SYMBOL);
      }
      labels.add(nl);
    }

    // Add AnswerAnnotation from the types of the entity mentions
    if (addAnswerAnnotation) {
      List<EntityMention> entities = sentence.get(MachineReadingAnnotations.EntityMentionsAnnotation.class);
      if(entities != null){
        for (EntityMention entity : entities) {
          // is this a type that we should skip?
          if(annotationsToSkip != null && annotationsToSkip.contains(entity.getType())) continue;
          // is this a valid mention type?
          if(mentionTypesToUse != null && ! mentionTypesToUse.contains(entity.getMentionType())) continue;

          // ignore entities without head span
          if(entity.getHead() != null){
            for(int i = entity.getHeadTokenStart(); i < entity.getHeadTokenEnd(); i ++){
              String tag = entity.getType();
              if(useSubTypes && entity.getSubType() != null) tag += "-" + entity.getSubType();
              if(useBIO){
                if(i == entity.getHeadTokenStart()) tag = "B-" + tag;
                else tag = "I-" + tag;
              }
              labels.get(i).set(CoreAnnotations.AnswerAnnotation.class, tag);
            }
          }
        }
      }
    }

    /*
    // Displaying the CoreLabels generated for this sentence
    log.info("sentence to core labels:");
    for(CoreLabel l: labels){
      log.info(" " + l.word() + "/" + l.getString(CoreAnnotations.PartOfSpeechAnnotation.class));
      String tag = l.getString(CoreAnnotations.AnswerAnnotation.class);
      if(tag != null && ! tag.equals(SeqClassifierFlags.DEFAULT_BACKGROUND_SYMBOL)){
        log.info("/" + tag);
      }
    }
    log.info();
    */

    return labels;
  }

  public static CoreMap getSentence(CoreMap dataset, int i) {
    return dataset.get(CoreAnnotations.SentencesAnnotation.class).get(i);
  }

  public static int sentenceCount(CoreMap dataset) {
    List<CoreMap> sents = dataset.get(CoreAnnotations.SentencesAnnotation.class);
    if(sents != null) return sents.size();
    return 0;
  }

  public static void addSentence(CoreMap dataset, CoreMap sentence) {
    List<CoreMap> sents = dataset.get(CoreAnnotations.SentencesAnnotation.class);
    if(sents == null){
      sents = new ArrayList<>();
      dataset.set(CoreAnnotations.SentencesAnnotation.class, sents);
    }
    sents.add(sentence);
  }

  public static void addSentences(CoreMap dataset, List<CoreMap> sentences) {
    List<CoreMap> sents = dataset.get(CoreAnnotations.SentencesAnnotation.class);
    if(sents == null){
      sents = new ArrayList<>();
      dataset.set(CoreAnnotations.SentencesAnnotation.class, sents);
    }
    for(CoreMap sentence: sentences){
      sents.add(sentence);
    }
  }

  /**
   * Creates a deep copy of the given dataset with new lists for all mentions (entity, relation, event)
   * @param dataset
   */
  public static Annotation deepMentionCopy(CoreMap dataset) {
    Annotation newDataset = new Annotation("");

    List<CoreMap> sents = dataset.get(CoreAnnotations.SentencesAnnotation.class);
    List<CoreMap> newSents = new ArrayList<>();
    if(sents != null){
      for(CoreMap sent: sents){
        if(! (sent instanceof Annotation)){
          throw new RuntimeException("ERROR: Sentences must instantiate Annotation!");
        }
        CoreMap newSent = sentenceDeepMentionCopy((Annotation) sent);
        newSents.add(newSent);
      }
    }

    addSentences(newDataset, newSents);
    return newDataset;
  }

  /**
   * Deep copy of the sentence: we create new entity/relation/event lists here.
   * However,  we do not deep copy the ExtractionObjects themselves!
   * @param sentence
   */
  public static Annotation sentenceDeepMentionCopy(Annotation sentence) {
    Annotation newSent = new Annotation(sentence.get(CoreAnnotations.TextAnnotation.class));

    newSent.set(CoreAnnotations.TokensAnnotation.class, sentence.get(CoreAnnotations.TokensAnnotation.class));
    newSent.set(TreeCoreAnnotations.TreeAnnotation.class, sentence.get(TreeCoreAnnotations.TreeAnnotation.class));
    newSent.set(SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class, sentence.get(SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class));
    newSent.set(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class, sentence.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class));
    newSent.set(SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation.class, sentence.get(SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation.class));
    newSent.set(CoreAnnotations.DocIDAnnotation.class, sentence.get(CoreAnnotations.DocIDAnnotation.class));

    // deep copy of all mentions lists
    List<EntityMention> ents = sentence.get(MachineReadingAnnotations.EntityMentionsAnnotation.class);
    if(ents != null) newSent.set(MachineReadingAnnotations.EntityMentionsAnnotation.class, new ArrayList<>(ents));
    List<RelationMention> rels = sentence.get(MachineReadingAnnotations.RelationMentionsAnnotation.class);
    if(rels != null) newSent.set(MachineReadingAnnotations.RelationMentionsAnnotation.class, new ArrayList<>(rels));
    List<EventMention> evs = sentence.get(MachineReadingAnnotations.EventMentionsAnnotation.class);
    if(evs != null) newSent.set(MachineReadingAnnotations.EventMentionsAnnotation.class, new ArrayList<>(evs));

    return newSent;
  }

  /**
   * Return the relation that holds between the given entities.
   * Return a relation of type UNRELATED if this sentence contains no relation between the entities.
   */
  public static RelationMention getRelation(RelationMentionFactory factory, CoreMap sentence, ExtractionObject ... args) {
    return getRelations(factory, sentence, args).get(0);
  }

  /**
   * Return all the relations that holds between the given entities.
   * Returns a list containing a relation of type UNRELATED if this sentence contains no relation between the entities.
   */
  public static List<RelationMention> getRelations(RelationMentionFactory factory, CoreMap sentence, ExtractionObject... args) {
    List<RelationMention> relationMentions = sentence.get(MachineReadingAnnotations.RelationMentionsAnnotation.class);
    List<RelationMention> matchingRelationMentions = new ArrayList<>();
    if (relationMentions != null) {
      for (RelationMention rel : relationMentions) {
        if (rel.argsMatch(args)) {
          matchingRelationMentions.add(rel);
        }
      }
    }
    if (matchingRelationMentions.size() == 0) {
      matchingRelationMentions.add(RelationMention.createUnrelatedRelation(factory, args));
    }
    return matchingRelationMentions;
  }

  /**
   * Get list of all relations and non-relations between EntityMentions in this sentence
   * Use with care. This is an expensive call due to getAllUnrelatedRelations, which creates all non-existing relations between all entity mentions
   */
  public static List<RelationMention> getAllRelations(RelationMentionFactory factory, CoreMap sentence, boolean createUnrelatedRelations) {
    List<RelationMention> relationMentions = sentence.get(MachineReadingAnnotations.RelationMentionsAnnotation.class);
    List<RelationMention> allRelations = new ArrayList<>();
    if(relationMentions != null) allRelations.addAll(relationMentions);
    if(createUnrelatedRelations){
      allRelations.addAll(getAllUnrelatedRelations(factory, sentence, true));
    }
    return allRelations;
  }

  public static List<RelationMention> getAllUnrelatedRelations(RelationMentionFactory factory, CoreMap sentence, boolean checkExisting) {

    List<RelationMention> relationMentions = (checkExisting ? sentence.get(MachineReadingAnnotations.RelationMentionsAnnotation.class) : null);
    List<EntityMention> entityMentions = sentence.get(MachineReadingAnnotations.EntityMentionsAnnotation.class);
    List<RelationMention> nonRelations = new ArrayList<>();

    //
    // scan all possible arguments
    //
    if(entityMentions != null){
      for(int i = 0; i < entityMentions.size(); i ++){
        for(int j = 0; j < entityMentions.size(); j ++){
          if(i == j) continue;
          EntityMention arg1 = entityMentions.get(i);
          EntityMention arg2 = entityMentions.get(j);
          boolean match = false;
          if(relationMentions != null){
            for (RelationMention rel : relationMentions) {
              if (rel.argsMatch(arg1, arg2)) {
                match = true;
                break;
              }
            }
          }
          if (match == false) {
          	nonRelations.add(RelationMention.createUnrelatedRelation(factory, arg1,arg2));
          }
        }
      }
    }

    return nonRelations;
  }

  public static void addEntityMention(CoreMap sentence, EntityMention arg) {
    List<EntityMention> l = sentence.get(MachineReadingAnnotations.EntityMentionsAnnotation.class);
    if(l == null){
      l = new ArrayList<>();
      sentence.set(MachineReadingAnnotations.EntityMentionsAnnotation.class, l);
    }
    l.add(arg);
  }

  public static void addEntityMentions(CoreMap sentence, Collection<EntityMention> args) {
    List<EntityMention> l = sentence.get(MachineReadingAnnotations.EntityMentionsAnnotation.class);
    if(l == null){
      l = new ArrayList<>();
      sentence.set(MachineReadingAnnotations.EntityMentionsAnnotation.class, l);
    }
    l.addAll(args);
  }

  public List<EntityMention> getEntityMentions(CoreMap sent) {
    return Collections.unmodifiableList(sent.get(MachineReadingAnnotations.EntityMentionsAnnotation.class));
  }

  public static void addRelationMention(CoreMap sentence, RelationMention arg) {
    List<RelationMention> l = sentence.get(MachineReadingAnnotations.RelationMentionsAnnotation.class);
    if(l == null){
      l = new ArrayList<>();
      sentence.set(MachineReadingAnnotations.RelationMentionsAnnotation.class, l);
    }
    l.add(arg);
  }

  public static void addRelationMentions(CoreMap sentence, Collection<RelationMention> args) {
    List<RelationMention> l = sentence.get(MachineReadingAnnotations.RelationMentionsAnnotation.class);
    if(l == null){
      l = new ArrayList<>();
      sentence.set(MachineReadingAnnotations.RelationMentionsAnnotation.class, l);
    }
    l.addAll(args);
  }

  public List<RelationMention> getRelationMentions(CoreMap sent) {
    return Collections.unmodifiableList(sent.get(MachineReadingAnnotations.RelationMentionsAnnotation.class));
  }

  public static void addEventMention(CoreMap sentence, EventMention arg) {
    List<EventMention> l = sentence.get(MachineReadingAnnotations.EventMentionsAnnotation.class);
    if(l == null){
      l = new ArrayList<>();
      sentence.set(MachineReadingAnnotations.EventMentionsAnnotation.class, l);
    }
    l.add(arg);
  }

  public static void addEventMentions(CoreMap sentence, Collection<EventMention> args) {
    List<EventMention> l = sentence.get(MachineReadingAnnotations.EventMentionsAnnotation.class);
    if(l == null){
      l = new ArrayList<>();
      sentence.set(MachineReadingAnnotations.EventMentionsAnnotation.class, l);
    }
    l.addAll(args);
  }

  public List<EventMention> getEventMentions(CoreMap sent) {
    return Collections.unmodifiableList(sent.get(MachineReadingAnnotations.EventMentionsAnnotation.class));
  }

	/**
   * Prepare a string for printing in a spreadsheet for Mechanical Turk input.
   * @param s String to be formatted
   * @return String string enclosed in quotes with other quotes escaped, and with better formatting for readability by Turkers.
   */
  public static String prettify(String s) {
    if (s==null) return "";
    return s.replace(
        " ,",",").replace(
            " .",".").replace(
                " :",":").replace(
                    "( ","(").replace(
                        "[ ","[").replace(
                            " )",")").replace(
                                " ]","]").replace(
                                    " - ","-").replace(
                                        " '","'").replace(
                                            "-LRB- ","(").replace(
                                                " -RRB-",")").replace(
                                                    "` ` ","\"").replace(
                                                        " ' '","\"").replace(
                                                            " COMMA",",");
  }

  /**
   * Fetches the sentence text in a given token span
   * @param span
   */
  public static String getTextContent(CoreMap sent, Span span) {
    List<CoreLabel> tokens = sent.get(CoreAnnotations.TokensAnnotation.class);
    StringBuffer buf = new StringBuffer();
    assert(span != null);
    for(int i = span.start(); i < span.end(); i ++){
      if(i > span.start()) buf.append(" ");
      buf.append(tokens.get(i).word());
    }
    return buf.toString();
  }

  public static String sentenceToString(CoreMap sent) {
    StringBuilder sb = new StringBuilder(512);
    List<CoreLabel> tokens = sent.get(CoreAnnotations.TokensAnnotation.class);
    sb.append("\"" + StringUtils.join(tokens, " ") + "\"");
    sb.append("\n");

    List<RelationMention> relationMentions = sent.get(MachineReadingAnnotations.RelationMentionsAnnotation.class);
    if(relationMentions != null){
      for (RelationMention rel : relationMentions) {
        sb.append("\n");
        sb.append(rel);
      }
    }

    // TODO: add entity and event mentions

    return sb.toString();
  }

  public static String tokensAndNELabelsToString(CoreMap sentence) {
    StringBuffer os = new StringBuffer();
    List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
    if(tokens != null){
      boolean first = true;
      for(CoreLabel token: tokens) {
        if(! first) os.append(" ");
        os.append(token.word());
        if(token.ner() != null && ! token.ner().equals("O")){
          os.append("/" + token.ner());
        }
        first = false;
      }
    }
    return os.toString();
  }

  public static String datasetToString(CoreMap dataset){
    List<CoreMap> sents = dataset.get(CoreAnnotations.SentencesAnnotation.class);
    StringBuffer b = new StringBuffer();
    if(sents != null){
      for(CoreMap sent: sents){
        b.append(sentenceToString(sent));
      }
    }
    return b.toString();
  }

  /*
  public static List<CoreLabel> wordsToCoreLabels(List<Word> words) {
    List<CoreLabel> labels = new ArrayList<CoreLabel>();
    for(Word word: words){
      CoreLabel l = new CoreLabel();
      l.setWord(word.word());
      l.set(CoreAnnotations.TextAnnotation.class, word.word());
      l.setBeginPosition(word.beginPosition());
      l.setEndPosition(word.endPosition());
      labels.add(l);
    }
    return labels;
  }
  */

  public static String tokensToString(List<CoreLabel> tokens) {
    StringBuffer os = new StringBuffer();
    boolean first = true;
    for(CoreLabel t: tokens){
      if(! first) os.append(" ");
      os.append(t.word() + "{" + t.beginPosition() + ", " + t.endPosition() + "}");
      first = false;
    }
    return os.toString();
  }

  /*
  public static boolean sentenceContainsSpan(CoreMap sentence, Span span) {
    List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
    int sentenceStart = tokens.get(0).beginPosition();
    int sentenceEnd = tokens.get(tokens.size() - 1).endPosition();
    return sentenceStart <= span.start() && sentenceEnd >= span.end();
  }
  */

  /*
   * Shift the character offsets of all tokens by offset.
   */
  public static void updateOffsets(List<Word> tokens, int offset) {
    for(Word l: tokens) {
      l.setBeginPosition(l.beginPosition() + offset);
      l.setEndPosition(l.endPosition() + offset);
    }
  }

  /*
   * Shift the character offsets of all tokens by offset.
   */
  public static void updateOffsetsInCoreLabels(List<CoreLabel> tokens, int offset) {
    for(CoreLabel l: tokens) {
      l.set(CoreAnnotations.CharacterOffsetBeginAnnotation.class, l.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class) + offset);
      l.set(CoreAnnotations.CharacterOffsetEndAnnotation.class, l.get(CoreAnnotations.CharacterOffsetEndAnnotation.class) + offset);
    }
  }

  /**
   * Process string to be a cell in Excel file.
   * Escape any quotes in the string and enclose the whole string with quotes.
   */
  public static String excelify(String s) {
    return '"'+s.replace("\"","\"\"")+'"';
  }

  public static List<CoreMap> readSentencesFromFile(String path) throws IOException, ClassNotFoundException {
    Annotation doc = (Annotation) IOUtils.readObjectFromFile(path);
    return doc.get(CoreAnnotations.SentencesAnnotation.class);

  }

}
