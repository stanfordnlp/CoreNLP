package edu.stanford.nlp.pipeline; 
import edu.stanford.nlp.util.logging.Redwood;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.parser.common.ParserUtils;
import edu.stanford.nlp.parser.charniak.CharniakParser;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.util.ArraySet;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Generics;

/**
 * This class will add parse information to an Annotation from the BLLIP parser.
 * It allows you to use the Charniak parser or Charniak and Johnson reranking parser
 * along with any existing parser and reranking model.
 *
 * It assumes that the Annotation already contains the tokenized words
 * as a {@code List<List<CoreLabel>>} under
 * {@code CoreAnnotations.SentencesAnnotation.class}.
 * If the words have POS tags, they will not be used.
 *
 * @author David McClosky
 */
public class CharniakParserAnnotator implements Annotator  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(CharniakParserAnnotator.class);

  // TODO: make this an option?
  private static final boolean BUILD_GRAPHS = true;

  private final GrammaticalStructureFactory gsf = new EnglishGrammaticalStructureFactory();

  private final boolean VERBOSE;
  private final CharniakParser parser;

  public CharniakParserAnnotator(String parserModel, String parserExecutable, boolean verbose, int maxSentenceLength) {
    VERBOSE = verbose;
    parser = new CharniakParser(parserExecutable, parserModel);
    parser.setMaxSentenceLength(maxSentenceLength);
  }

  public CharniakParserAnnotator() {
    VERBOSE = false;
    parser = new CharniakParser();
  }

  @Override
  public void annotate(Annotation annotation) {
    if (annotation.containsKey(CoreAnnotations.SentencesAnnotation.class)) {
      // parse a tree for each sentence
      for (CoreMap sentence: annotation.get(CoreAnnotations.SentencesAnnotation.class)) {
        List<CoreLabel> words = sentence.get(CoreAnnotations.TokensAnnotation.class);
        if (VERBOSE) {
          log.info("Parsing: " + words);
        }
        int maxSentenceLength = parser.getMaxSentenceLength();
        // generate the constituent tree
        Tree tree; // initialized below
        if (maxSentenceLength <= 0 || words.size() < maxSentenceLength) {
          tree = parser.getBestParse(words);
        }
        else {
          tree = ParserUtils.xTree(words);
        }

        List<Tree> trees = Generics.newArrayList(1);
        trees.add(tree);
        ParserAnnotatorUtils.fillInParseAnnotations(VERBOSE, BUILD_GRAPHS, gsf, sentence, trees, GrammaticalStructure.Extras.NONE);
      }
    } else {
      throw new RuntimeException("unable to find sentences in: " + annotation);
    }
  }

  @Override
  public Set<Class<? extends CoreAnnotation>> requires() {
    return Collections.unmodifiableSet(new ArraySet<>(Arrays.asList(
        CoreAnnotations.TextAnnotation.class,
        CoreAnnotations.TokensAnnotation.class,
        CoreAnnotations.CharacterOffsetBeginAnnotation.class,
        CoreAnnotations.CharacterOffsetEndAnnotation.class,
        CoreAnnotations.SentencesAnnotation.class
    )));
  }

  @Override
  public Set<Class<? extends CoreAnnotation>> requirementsSatisfied() {
    return Collections.unmodifiableSet(new ArraySet<>(Arrays.asList(
        CoreAnnotations.PartOfSpeechAnnotation.class,
        TreeCoreAnnotations.TreeAnnotation.class,
        CoreAnnotations.CategoryAnnotation.class
    )));
  }

}
