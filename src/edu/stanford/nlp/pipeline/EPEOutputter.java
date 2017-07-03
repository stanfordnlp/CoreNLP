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
import java.util.List;
import java.util.function.Consumer;

/**
 * Output dependency annotations in the format for the EPE task
 * at Depling 2017.
 *
 * @author Sebastian Schuster
 */
public class EPEOutputter extends JSONOutputter {


  private static String OUTPUT_REPRESENTATION = System.getProperty("outputRepresentation", "basic");

  @Override
  public void print(Annotation doc, OutputStream target, Options options) throws IOException {
    
    PrintWriter writer = new PrintWriter(IOUtils.encodedOutputStreamWriter(target, options.encoding));
    JSONWriter l0 = new JSONWriter(writer, options);

    if (doc.get(CoreAnnotations.SentencesAnnotation.class) != null) {
      doc.get(CoreAnnotations.SentencesAnnotation.class).stream().forEach(sentence -> {
        l0.object(l1 -> {
          l1.set("id", sentence.get(CoreAnnotations.SentenceIndexAnnotation.class) + 1);
          SemanticGraph sg;
          if (OUTPUT_REPRESENTATION.equalsIgnoreCase("basic")) {
            sg = sentence.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);
          } else if (OUTPUT_REPRESENTATION.equalsIgnoreCase("enhanced")) {
            sg = sentence.get(SemanticGraphCoreAnnotations.EnhancedDependenciesAnnotation.class);
          } else {
            sg = sentence.get(SemanticGraphCoreAnnotations.EnhancedPlusPlusDependenciesAnnotation.class);
          }
          l1.set("nodes", getNodes(sg));
        });
        l0.writer.append("\n");
        l0.writer.flush();
      });
     }
  }

  private static Object getNodes(SemanticGraph graph) {
    if(graph != null) {

      List<IndexedWord> vertexList = graph.vertexListSorted();
      int maxIndex = vertexList.get(vertexList.size() - 1).index();
      return vertexList.stream().map( (IndexedWord token) -> (Consumer<Writer>) node -> {
            if (token.copyCount() == 0) {
              node.set("id", getNodeIndex(token, maxIndex));
              node.set("start", token.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class));
              node.set("end", token.get(CoreAnnotations.CharacterOffsetEndAnnotation.class));
            } else {
              node.set("id", getNodeIndex(token, maxIndex));
              node.set("source", token.index());
            }
          node.set("form", token.word());

          if (graph.getRoots().contains(token)) node.set("top", true);
            node.set("properties", (Consumer<Writer>) propertiesWriter -> {
              propertiesWriter.set("xpos", token.tag());
              propertiesWriter.set("upos", token.get(CoreAnnotations.CoarseTagAnnotation.class));
              propertiesWriter.set("lemma", token.lemma());
            });

            node.set("edges", graph.getOutEdgesSorted(token).stream().map( (SemanticGraphEdge dep) -> (Consumer<Writer>) edge -> {
              edge.set("target", getNodeIndex(dep.getDependent(), maxIndex));
              edge.set("label", dep.getRelation().toString());
            }));
      } );
    } else {
      return null;
    }
  }

  private static int getNodeIndex(IndexedWord token, int maxIndex) {
    if (token.copyCount() == 0) {
      return token.index();
    } else {
      return token.index() + maxIndex * token.copyCount();
    }
  }

}
