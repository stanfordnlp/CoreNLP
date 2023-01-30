package edu.stanford.nlp.ie;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.pipeline.DefaultPaths;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.semgraph.semgrex.SemgrexBatchParser;
import edu.stanford.nlp.semgraph.semgrex.SemgrexMatcher;
import edu.stanford.nlp.semgraph.semgrex.SemgrexPattern;
import edu.stanford.nlp.simple.Sentence;
import edu.stanford.nlp.util.ArgumentParser;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.logging.Redwood;
import edu.stanford.nlp.util.logging.RedwoodConfiguration;

import java.io.*;
import java.util.*;

/**
 * A tokensregex extractor for KBP.
 *
 * @author Gabor Angeli
 */
public class KBPSemgrexExtractor implements KBPRelationExtractor {
  protected final Redwood.RedwoodChannels logger = Redwood.channels(KBPSemgrexExtractor.class);

  @ArgumentParser.Option(name="dir", gloss="The semgrex directory")
  public static String DIR = DefaultPaths.DEFAULT_KBP_SEMGREX_DIR;

  @ArgumentParser.Option(name="test", gloss="The dataset to test on")
  public static File TEST_FILE = new File("test.conll");

  @ArgumentParser.Option(name="predictions", gloss="Dump model predictions to this file")
  public static Optional<String> PREDICTIONS = Optional.empty();

  private final Map<RelationType, Collection<SemgrexPattern> > rules = new HashMap<>();

  public KBPSemgrexExtractor(String semgrexdir) throws IOException {
    this(semgrexdir, false);
  }

  public KBPSemgrexExtractor(String semgrexdir, boolean verbose) throws IOException {
    if (verbose)
      logger.log("Creating SemgrexRegexExtractor");
    // Create extractors
    for (RelationType rel : RelationType.values()) {
      String relFileNameComponent = rel.canonicalName.replaceAll(":", "_");
      String filename = semgrexdir + File.separator + relFileNameComponent.replace("/", "SLASH") + ".rules";
      if (IOUtils.existsInClasspathOrFileSystem(filename)) {

        List<SemgrexPattern> rulesforrel = SemgrexBatchParser.compileStream(IOUtils.getInputStreamFromURLOrClasspathOrFileSystem(filename));
        if (verbose)
          logger.log("Read " + rulesforrel.size() + " rules from " + filename + " for relation " + rel);
        rules.put(rel, rulesforrel);
      }
    }
  }


  @Override
  public Pair<String, Double> classify(KBPInput input) {
    for (RelationType rel : RelationType.values()) {

      if (rules.containsKey(rel) &&
          rel.entityType == input.subjectType &&
          rel.validNamedEntityLabels.contains(input.objectType)) {
        Collection<SemgrexPattern> rulesForRel = rules.get(rel);
        CoreMap sentence = input.sentence.asCoreMap(Sentence::nerTags, Sentence::dependencyGraph);
        SemgrexPattern matchedPattern = matches(sentence, rulesForRel, input,
                                                sentence.get(SemanticGraphCoreAnnotations.EnhancedPlusPlusDependenciesAnnotation.class));
        if (matchedPattern == null) {
          matchedPattern = matches(sentence, rulesForRel, input,
                                   sentence.get(SemanticGraphCoreAnnotations.AlternativeDependenciesAnnotation.class));
        }
        if (matchedPattern != null) {
          //logger.log("MATCH for " + rel +  ".  sentence:" + sentence + " with rules for  " + rel + ": " + matchedPattern);
          return Pair.makePair(rel.canonicalName, 1.0);
        }
      }
    }

    return Pair.makePair(NO_RELATION, 1.0);
  }


  /**
   * Returns whether any of the given patterns match this tree.
   */
  private SemgrexPattern matches(CoreMap sentence, Collection<SemgrexPattern> rulesForRel,
                          KBPInput input, SemanticGraph graph) {
    if (graph == null || graph.isEmpty()) {
      return null;
    }

    List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
    for (int i : input.subjectSpan) {
      if ("O".equals(tokens.get(i).ner())) {
        tokens.get(i).setNER(input.subjectType.name);
      }
    }
    for (int i : input.objectSpan) {
      if ("O".equals(tokens.get(i).ner())) {
        tokens.get(i).setNER(input.objectType.name);
      }
    }

    for (SemgrexPattern p : rulesForRel) {

      SemgrexMatcher n = p.matcher(graph);
      while (n.find()) {
        IndexedWord entity = n.getNode("entity");
        IndexedWord slot = n.getNode("slot");
        if (entity == null) {
          // really this is a hideous bug, right?  these rules
          // should all have entity and slot set
          logger.warn("Found a relation with a missing entity: " + p);
          continue;
        }
        if (slot == null) {
          logger.warn("Found a relation with a missing slot: " + p);
          continue;
        }
        boolean hasSubject = entity.index() >= input.subjectSpan.start() + 1 && entity.index() <= input.subjectSpan.end();
        boolean hasObject  = slot.index() >= input.objectSpan.start() + 1 && slot.index() <= input.objectSpan.end();
        
        if (hasSubject && hasObject) {
          return p;
        } 
      }
    }
    return null;
  }


  public static void main(String[] args) throws IOException {
    RedwoodConfiguration.standard().apply();  // Disable SLF4J crap.
    ArgumentParser.fillOptions(KBPSemgrexExtractor.class, args);
    KBPSemgrexExtractor extractor = new KBPSemgrexExtractor(DIR);
    List<Pair<KBPInput, String>> testExamples = KBPRelationExtractor.readDataset(TEST_FILE);

    extractor.computeAccuracy(testExamples.stream(), PREDICTIONS.map(x -> {
      try {
        return "stdout".equalsIgnoreCase(x) ? System.out : new PrintStream(new FileOutputStream(x));
      } catch (IOException e) {
        throw new RuntimeIOException(e);
      }
    }));
  }

}
