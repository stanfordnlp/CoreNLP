package edu.stanford.nlp.ie.machinereading;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.stanford.nlp.ie.machinereading.structure.MachineReadingAnnotations;
import edu.stanford.nlp.ie.machinereading.structure.RelationMention;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.util.CoreMap;

/**
 * Simple extractor which combines several other Extractors.  Currently only works with RelationMentions.
 * Also note that this implementation uses Sets and will mangle the original order of RelationMentions.
 *
 * @author David McClosky
 */
public class ExtractorMerger implements Extractor {

  private static final long serialVersionUID = 1L;
  private static final Logger logger = Logger.getLogger(ExtractorMerger.class.getName());
  private Extractor[] extractors;

  public ExtractorMerger(Extractor[] extractors) {
    if (extractors.length < 2) {
      throw new IllegalArgumentException("We need at least 2 extractors for ExtractorMerger to make sense.");
    }
    this.extractors = extractors;
  }

  @Override
  public void annotate(Annotation dataset) {
    // TODO for now, we only merge RelationMentions
    logger.info("Extractor 0 annotating dataset.");
    extractors[0].annotate(dataset);

    // store all the RelationMentions per sentence
    List<Set<RelationMention>> allRelationMentions = new ArrayList<>();
    for (CoreMap sentence : dataset.get(CoreAnnotations.SentencesAnnotation.class)) {
      List<RelationMention> relationMentions = sentence.get(MachineReadingAnnotations.RelationMentionsAnnotation.class);
      Set<RelationMention> uniqueRelationMentions = new HashSet<>(relationMentions);
      allRelationMentions.add(uniqueRelationMentions);
    }

    // skip first extractor since we did it at the top
    for (int extractorIndex = 1; extractorIndex < extractors.length; extractorIndex++) {
      logger.info("Extractor " + extractorIndex + " annotating dataset.");
      Extractor extractor = extractors[extractorIndex];
      extractor.annotate(dataset);

      // walk through all sentences and merge our RelationMentions with the combined set
      int sentenceIndex = 0;
      for (CoreMap sentence : dataset.get(CoreAnnotations.SentencesAnnotation.class)) {
        List<RelationMention> relationMentions = sentence.get(MachineReadingAnnotations.RelationMentionsAnnotation.class);
        allRelationMentions.get(sentenceIndex).addAll(relationMentions);
      }
    }

    // put all merged relations back into the dataset
    int sentenceIndex = 0;
    for (CoreMap sentence : dataset.get(CoreAnnotations.SentencesAnnotation.class)) {
      Set<RelationMention> uniqueRelationMentions = allRelationMentions.get(sentenceIndex);
      List<RelationMention> relationMentions = new ArrayList<>(uniqueRelationMentions);
      sentence.set(MachineReadingAnnotations.RelationMentionsAnnotation.class, relationMentions);
      sentenceIndex++;
    }
  }

  public static Extractor buildRelationExtractorMerger(String[] extractorModelNames) {
    BasicRelationExtractor[] relationExtractorComponents = new BasicRelationExtractor[extractorModelNames.length];
    for (int i = 0; i < extractorModelNames.length; i++) {
      String modelName = extractorModelNames[i];
      logger.info("Loading model " + i + " for model merging from " + modelName);
      try {
        relationExtractorComponents[i] = BasicRelationExtractor.load(modelName);
      } catch (IOException | ClassNotFoundException e) {
        logger.severe("Error loading model:");
        e.printStackTrace();
      }
    }
    ExtractorMerger relationExtractor = new ExtractorMerger(relationExtractorComponents);
    return relationExtractor;
  }

  @Override
  public void setLoggerLevel(Level level) {
    logger.setLevel(level);
  }

  // stubs required by Extractor interface -- they don't do anything since this model is not trainable or savable
  @Override
  public void save(String path) throws IOException {
  }

  @Override
  public void train(Annotation dataset) {
  }

}
