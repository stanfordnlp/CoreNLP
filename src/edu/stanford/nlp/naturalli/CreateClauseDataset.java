package edu.stanford.nlp.naturalli;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.process.TSVSentenceProcessor;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.semgraph.semgrex.SemgrexMatcher;
import edu.stanford.nlp.semgraph.semgrex.SemgrexPattern;
import edu.stanford.nlp.trees.Span;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Execution;
import edu.stanford.nlp.util.StringUtils;

import java.io.InputStream;
import java.util.*;

/**
 * A script to convert a TSV dump from our KBP sentences table into a Turk-task ready clause splitting dataset.
 *
 * @author Gabor Angeli
 */
public class CreateClauseDataset implements TSVSentenceProcessor {

  @Execution.Option(name="in", gloss="The input to read from")
  private static InputStream in = System.in;

  private RelationTripleSegmenter segmenter;

  public CreateClauseDataset() {
    this.segmenter = new RelationTripleSegmenter();
  }


  private static Span toSpan(List<CoreLabel> chunk) {
    int min = Integer.MAX_VALUE;
    int max = -1;
    for (CoreLabel word : chunk) {
      min = Math.min(word.index() - 1, min);
      max = Math.max(word.index(), max);
    }
    assert min >= 0;
    assert max < Integer.MAX_VALUE && max > 0;
    return new Span(min, max);
  }

  @Override
  public void process(long id, Annotation doc) {
    CoreMap sentence = doc.get(CoreAnnotations.SentencesAnnotation.class).get(0);
    SemanticGraph depparse = sentence.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);
    List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
    System.err.println("| " + sentence.get(CoreAnnotations.TextAnnotation.class));

    // Get all valid subject spans
    BitSet consumedAsSubjects = new BitSet();
    List<Span> subjectSpans = new ArrayList<>();
    NEXTNODE: for (IndexedWord head : depparse.topologicalSort()) {
      // Check if the node is a noun/pronoun
      if (head.tag().startsWith("N") || head.tag().equals("PRP")) {
        // Try to get the NP chunk
        Optional<List<CoreLabel>> subjectChunk = segmenter.getValidChunk(depparse, head, segmenter.VALID_SUBJECT_ARCS, Optional.empty(), true);
        if (subjectChunk.isPresent()) {
          // Make sure it's not already a member of a larger NP
          for (CoreLabel tok : subjectChunk.get()) {
            if (consumedAsSubjects.get(tok.index())) {
              continue NEXTNODE;  // Already considered. Continue to the next node.
            }
          }
          // Register it as an NP
          for (CoreLabel tok : subjectChunk.get()) {
            consumedAsSubjects.set(tok.index());
          }
          // Add it as a subject
          subjectSpans.add(toSpan(subjectChunk.get()));
        }
      }
    }

    // Get all the VP spans
    List<List<CoreLabel>> vpChunks = new ArrayList<>();
    for (SemgrexPattern vpPattern : segmenter.VP_PATTERNS) {
      SemgrexMatcher matcher = vpPattern.matcher(depparse);
      while (matcher.find()) {
        // Get the verb and object
        IndexedWord verb = matcher.getNode("verb");
        IndexedWord object = matcher.getNode("object");
        if (verb != null && object != null) {
          // See if there is already a subject attached
          boolean hasSubject = false;
          for (SemanticGraphEdge edge : depparse.outgoingEdgeIterable(verb)) {
            if (edge.getRelation().toString().contains("subj")) {
              hasSubject = true;
            }
          }
          for (SemanticGraphEdge edge : depparse.outgoingEdgeIterable(object)) {
            if (edge.getRelation().toString().contains("subj")) {
              hasSubject = true;
            }
          }
          if (!hasSubject) {
            // Get the spans for the verb and object
            Optional<List<CoreLabel>> verbChunk = segmenter.getValidChunk(depparse, verb, segmenter.VALID_ADVERB_ARCS, Optional.empty(), true);
            Optional<List<CoreLabel>> objectChunk = segmenter.getValidChunk(depparse, object, segmenter.VALID_OBJECT_ARCS, Optional.empty(), true);
            if (verbChunk.isPresent() && objectChunk.isPresent()) {
              // Add the chunk
              List<CoreLabel> vpChunk = new ArrayList<>();
              vpChunk.addAll(verbChunk.get());
              vpChunk.addAll(objectChunk.get());
              Collections.sort(vpChunk, (a, b) -> a.index() - b.index());
              vpChunks.add(vpChunk);
            }
          }
        }
      }
    }

    // Print the example
    for (List<CoreLabel> vp : vpChunks) {
      System.err.println("  >> " + StringUtils.join(vp.stream().map(CoreLabel::word), " "));
      for (Span subj : subjectSpans) {
        System.err.println("    (.) " + StringUtils.join(tokens.subList(subj.start(), subj.end()).stream().map(CoreLabel::word), " "));
      }
      System.err.println();
    }

  }


  public static void main(String[] args) {
    Execution.fillOptions(CreateClauseDataset.class, args);

    new CreateClauseDataset().runAndExit(in, System.err, code -> code);
  }
}
