package edu.stanford.nlp.ie.machinereading.domains.roth;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.stanford.nlp.ie.machinereading.GenericDataSetReader;
import edu.stanford.nlp.ie.machinereading.structure.AnnotationUtils;
import edu.stanford.nlp.ie.machinereading.structure.EntityMention;
import edu.stanford.nlp.ie.machinereading.structure.ExtractionObject;
import edu.stanford.nlp.ie.machinereading.structure.MachineReadingAnnotations;
import edu.stanford.nlp.ie.machinereading.structure.RelationMention;
import edu.stanford.nlp.ie.machinereading.structure.Span;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.StringUtils;

/**
 * A Reader designed for the relation extraction data studied in Dan Roth and Wen-tau Yih,
 * A Linear Programming Formulation for Global Inference in Natural Language Tasks. CoNLL 2004.
 * The format is a somewhat ad-hoc tab-separated value file format.
 *
 * @author Mihai, David McClosky, and agusev
 * @author Sonal Gupta (sonalg@stanford.edu)
 */
public class RothCONLL04Reader extends GenericDataSetReader {

  public RothCONLL04Reader() {
    super(null, true, true, true);

    // change the logger to one from our namespace
    logger = Logger.getLogger(RothCONLL04Reader.class.getName());
    // run quietly by default
    logger.setLevel(Level.SEVERE);
  }

  @Override
  public Annotation read(String path) throws IOException {
    Annotation doc = new Annotation("");

    logger.info("Reading file: " + path);

    // Each iteration through this loop processes a single sentence along with any relations in it
    for (Iterator<String> lineIterator = IOUtils.readLines(path).iterator(); lineIterator.hasNext(); ) {
      Annotation sentence = readSentence(path, lineIterator);
      AnnotationUtils.addSentence(doc, sentence);
    }

    return doc;
  }

  private boolean warnedNER; // = false;

  private String getNormalizedNERTag(String ner) {
    if (ner.equalsIgnoreCase("O")) {
      return "O";
    } else if (ner.equalsIgnoreCase("Peop")) {
      return "PERSON";
    } else if (ner.equalsIgnoreCase("Loc")) {
      return "LOCATION";
    } else if(ner.equalsIgnoreCase("Org")) {
      return "ORGANIZATION";
    } else if(ner.equalsIgnoreCase("Other")) {
      return "OTHER";
    } else {
      if ( ! warnedNER) {
        warnedNER = true;
        logger.warning("This file contains NER tags not in the original Roth/Yih dataset, e.g.: " + ner);
      }
    }
    throw new RuntimeException("Cannot normalize ner tag " + ner);
  }

  private Annotation readSentence(String docId, Iterator<String> lineIterator) {
    Annotation sentence = new Annotation("");
    sentence.set(CoreAnnotations.DocIDAnnotation.class, docId);
    sentence.set(MachineReadingAnnotations.EntityMentionsAnnotation.class, new ArrayList<>());
    // we'll need to set things like the tokens and textContent after we've
    // fully read the sentence

    // contains the full text that we've read so far
    StringBuilder textContent = new StringBuilder();
    int tokenCount = 0; // how many tokens we've seen so far
    List<CoreLabel> tokens = new ArrayList<>();

    // when we've seen two blank lines in a row, this sentence is over (one
    // blank line separates the sentence and the relations
    int numBlankLinesSeen = 0;
    String sentenceID = null;

    // keeps tracks of entities we've seen so far for use by relations
    Map<String, EntityMention> indexToEntityMention = new HashMap<>();

    while (lineIterator.hasNext() && numBlankLinesSeen < 2) {
      String currentLine = lineIterator.next();
      currentLine = currentLine.replace("COMMA", ",");

      List<String> pieces = StringUtils.split(currentLine);
      String identifier;

      int size = pieces.size();
      switch (size) {
      case 1: // blank line between sentences or relations
        numBlankLinesSeen++;
        break;
      case 3: // relation
        String type = pieces.get(2);
        List<ExtractionObject> args = new ArrayList<>();
        EntityMention entity1 = indexToEntityMention.get(pieces.get(0));
        EntityMention entity2 = indexToEntityMention.get(pieces.get(1));
        args.add(entity1);
        args.add(entity2);
        Span span = new Span(entity1.getExtentTokenStart(), entity2
            .getExtentTokenEnd());
        // identifier = "relation" + sentenceID + "-" + sentence.getAllRelations().size();
        identifier = RelationMention.makeUniqueId();
        RelationMention relationMention = new RelationMention(identifier,
            sentence, span, type, null, args);
        AnnotationUtils.addRelationMention(sentence, relationMention);
        break;
      case 9: // token
        /*
         * Roth token lines look like this:
         *
         * 19 Peop 9 O NNP/NNP Jamal/Ghosheh O O O
         */

        // Entities may be multiple words joined by '/'; we split these up
        List<String> words = StringUtils.split(pieces.get(5), "/");
        //List<String> postags = StringUtils.split(pieces.get(4),"/");

        String text = StringUtils.join(words, " ");
        identifier = "entity" + pieces.get(0) + '-' + pieces.get(2);
        String nerTag = getNormalizedNERTag(pieces.get(1)); // entity type of the word/expression

        if (sentenceID == null)
          sentenceID = pieces.get(0);

        if (!nerTag.equals("O")) {
          Span extentSpan = new Span(tokenCount, tokenCount + words.size());
          // Temporarily sets the head span to equal the extent span.
          // This is so the entity has a head (in particular, getValue() works) even if preprocessSentences isn't called.
          // The head span is later modified if preprocessSentences is called.
          EntityMention entity = new EntityMention(identifier, sentence,
              extentSpan, extentSpan, nerTag, null, null);
          AnnotationUtils.addEntityMention(sentence, entity);

          // we can get by using these indices as strings since we only use them
          // as a hash key
          String index = pieces.get(2);
          indexToEntityMention.put(index, entity);
        }

        // int i =0;
        for (String word : words) {
          CoreLabel label = new CoreLabel();
          label.setWord(word);
          //label.setTag(postags.get(i));
          label.set(CoreAnnotations.TextAnnotation.class, word);
          label.set(CoreAnnotations.ValueAnnotation.class, word);
          // we don't set TokenBeginAnnotation or TokenEndAnnotation since we're
          // not keeping track of character offsets
          tokens.add(label);
          // i++;
        }

        textContent.append(text);
        textContent.append(' ');
        tokenCount += words.size();
        break;
      }
    }

    sentence.set(CoreAnnotations.TextAnnotation.class, textContent.toString());
    sentence.set(CoreAnnotations.ValueAnnotation.class, textContent.toString());
    sentence.set(CoreAnnotations.TokensAnnotation.class, tokens);
    sentence.set(CoreAnnotations.SentenceIDAnnotation.class, sentenceID);

    return sentence;
  }

  /*
   * Gets the index of an object in a list using == to test (List.indexOf uses
   * equals() which could be problematic here)
   */
  private static <X> int getIndexByObjectEquality(List<X> list, X obj) {
    for (int i = 0, sz = list.size(); i < sz; i++) {
      if (list.get(i) == obj) {
        return i;
      }
    }
    return -1;
  }

  /*
   * Sets the head word and the index for an entity, given the parse tree for
   * the sentence containing the entity.
   *
   * This code is no longer used, but I've kept it around (at least for now) as
   * reference when we modify preProcessSentences().
   */
  @SuppressWarnings("unused")
  private void setHeadWord(EntityMention entity, Tree tree) {
    List<Tree> leaves = tree.getLeaves();
    Tree argRoot = tree.joinNode(leaves.get(entity.getExtentTokenStart()),
        leaves.get(entity.getExtentTokenEnd()));
    Tree headWordNode = argRoot.headTerminal(headFinder);

    int headWordIndex = getIndexByObjectEquality(leaves, headWordNode);

    if (StringUtils.isPunct(leaves.get(entity.getExtentTokenEnd()).label().value().trim())
        && (headWordIndex >= entity.getExtentTokenEnd()
            || headWordIndex < entity.getExtentTokenStart())) {

      argRoot = tree.joinNode(leaves.get(entity.getExtentTokenStart()), leaves
          .get(entity.getExtentTokenEnd() - 1));
      headWordNode = argRoot.headTerminal(headFinder);
      headWordIndex = getIndexByObjectEquality(leaves, headWordNode);

      if (headWordIndex >= entity.getExtentTokenStart()
          && headWordIndex <= entity.getExtentTokenEnd() - 1) {
        entity.setHeadTokenPosition(headWordIndex);
        entity.setHeadTokenSpan(new Span(headWordIndex, headWordIndex + 1));
      }
    }

    if (headWordIndex >= entity.getExtentTokenStart()
        && headWordIndex <= entity.getExtentTokenEnd()) {
      entity.setHeadTokenPosition(headWordIndex);
      entity.setHeadTokenSpan(new Span(headWordIndex, headWordIndex + 1));
    } else {
      // Re-parse the argument words by themselves
      // Get the list of words in the arg by looking at the leaves between
      // arg.getExtentTokenStart() and arg.getExtentTokenEnd() inclusive
      List<String> argWords = new ArrayList<>();
      for (int i = entity.getExtentTokenStart(); i <= entity.getExtentTokenEnd(); i++) {
        argWords.add(leaves.get(i).label().value());
      }
      if (StringUtils.isPunct(argWords.get(argWords.size() - 1))) {
        argWords.remove(argWords.size() - 1);
      }
      Tree argTree = parseStrings(argWords);
      headWordNode = argTree.headTerminal(headFinder);
      headWordIndex = getIndexByObjectEquality(argTree.getLeaves(),
          headWordNode)
          + entity.getExtentTokenStart();
      entity.setHeadTokenPosition(headWordIndex);
      entity.setHeadTokenSpan(new Span(headWordIndex, headWordIndex + 1));
    }
  }

  public static void main(String[] args) throws Exception {
    // just a simple test, to make sure stuff works
    Properties props = StringUtils.argsToProperties(args);
    RothCONLL04Reader reader = new RothCONLL04Reader();
    reader.setLoggerLevel(Level.INFO);
    reader.setProcessor(new StanfordCoreNLP(props));
    Annotation doc = reader.parse("/u/nlp/data/RothCONLL04/conll04.corp");
    System.out.println(AnnotationUtils.datasetToString(doc));
  }

}