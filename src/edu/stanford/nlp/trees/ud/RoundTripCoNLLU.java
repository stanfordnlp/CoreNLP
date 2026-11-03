package edu.stanford.nlp.trees.ud;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.CoNLLUReader;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.util.CoreMap;

/**
 * Rewrites CoNLL-U files through the CoNLL-U reader and writer, with no
 * edits, the same way the SsurgeonPattern command line writes a file.
 *<br>
 * The writer normalizes a few things a hand-edited treebank may not
 * have, such as the order of the keys in the Misc column, which it
 * sorts.  Ssurgeon writes back every sentence of a file it changes,
 * so the first Ssurgeon edit to such a file also changes the Misc
 * column of every sentence in it, and buries the edit in the diff.
 * Rewriting the files with this first, and committing that on its own,
 * leaves later Ssurgeon diffs with only the changes the edits made.
 *<br>
 * Usage:
 *<pre>
 * java edu.stanford.nlp.trees.ud.RoundTripCoNLLU file.conllu ...
 *</pre>
 * Each file is rewritten in place.  A file which is already in the
 * writer's form comes back byte for byte the same.
 *
 * @author John Bauer
 */
public class RoundTripCoNLLU {
  private RoundTripCoNLLU() { }

  /**
   * Reads a CoNLL-U file and writes it back out as CoNLL-U, with the
   * comments, the basic graph, and the enhanced graph of each sentence.
   *
   * @param path the file to read
   * @return the file as the writer writes it
   * @throws IOException if the file cannot be read
   */
  public static String roundTrip(String path) throws IOException {
    CoNLLUReader reader = new CoNLLUReader();
    CoNLLUDocumentWriter writer = new CoNLLUDocumentWriter();
    StringBuilder result = new StringBuilder();
    for (Annotation doc : reader.readCoNLLUFile(path)) {
      for (CoreMap sentence : doc.get(CoreAnnotations.SentencesAnnotation.class)) {
        SemanticGraph graph = sentence.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);
        SemanticGraph enhanced = sentence.get(SemanticGraphCoreAnnotations.EnhancedDependenciesAnnotation.class);
        List<String> comments = new ArrayList<>();
        if (sentence.get(CoreAnnotations.CommentsAnnotation.class) != null) {
          comments.addAll(sentence.get(CoreAnnotations.CommentsAnnotation.class));
        }
        result.append(writer.printSemanticGraph(graph, enhanced, false, comments));
      }
    }
    return result.toString();
  }

  /**
   * Rewrites each file named on the command line in place.
   *
   * @param args the files to rewrite
   * @throws IOException if a file cannot be read or written
   */
  public static void main(String[] args) throws IOException {
    if (args.length == 0) {
      System.err.println("Usage: java " + RoundTripCoNLLU.class.getName() + " file.conllu ...");
      System.exit(2);
    }
    for (String path : args) {
      String result = roundTrip(path);
      Files.write(new File(path).toPath(), result.getBytes(StandardCharsets.UTF_8));
      System.err.println("Rewrote " + path);
    }
  }
}
