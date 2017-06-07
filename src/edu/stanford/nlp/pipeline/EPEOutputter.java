package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.function.Consumer;

/**
 * Output dependency annotations in the format for the EPE task
 * at Depling 2017.
 *
 * @author Sebastian Schuster
 */
public class EPEOutputter extends JSONOutputter {

  @Override
  public void print(Annotation doc, OutputStream target, Options options) throws IOException {
    PrintWriter writer = new PrintWriter(IOUtils.encodedOutputStreamWriter(target, options.encoding));
    JSONWriter l0 = new JSONWriter(writer, options);

    if (doc.get(CoreAnnotations.SentencesAnnotation.class) != null) {
      doc.get(CoreAnnotations.SentencesAnnotation.class).stream().map(sentence -> {
        l0.object(l1 -> {
          l1.set("id", sentence.get(CoreAnnotations.SentenceIDAnnotation.class));
          l1.set("nodes", getNodes(sentence.get(SemanticGraphCoreAnnotations.EnhancedPlusPlusDependenciesAnnotation.class)));
        });
        l0.writer.flush();
      });


  }

  private static Object getNodes(SemanticGraph graph) {
    if(graph != null) {
      return graph.vertexListSorted().stream().map( (IndexedWord token) -> (Consumer<Writer>) node -> {
            node.set("id", token.index());
            node.set("form", token.word());
            node.set("start", token.beginPosition());
            node.set("end", token.endPosition());
            if (graph.getRoots().contains(node)) node.set("top", true);
            node.set("properties", (Consumer<Writer>) propertiesWriter -> {
              propertiesWriter.set("xpos", token.tag());
              propertiesWriter.set("upos", token.get(CoreAnnotations.CoarseTagAnnotation.class));
              propertiesWriter.set("lemma", token.lemma());
            });

            node.set("edges", graph.getOutEdgesSorted(token).stream().map( (SemanticGraphEdge dep) -> (Consumer<Writer>) edge -> {
              edge.set("target", dep.getDependent().index());
              edge.set("label", dep.getRelation().getShortName());
            });
      } );
    } else {
      return null;
    }
  }

}
