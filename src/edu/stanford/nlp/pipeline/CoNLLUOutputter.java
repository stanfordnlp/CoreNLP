package edu.stanford.nlp.pipeline;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.*;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.trees.ud.CoNLLUDocumentWriter;
import edu.stanford.nlp.util.CoreMap;

/**
 * <p>Write a subset of our CoreNLP output in CoNLL-U format.</p>
 *
 * <p>The fields currently output are:</p>
 *
 * <table>
 * <caption>Output fields</caption>
 *   <tr>
 *     <td>Field Number</td>
 *     <td>Field Name</td>
 *     <td>Description</td>
 *   </tr>
 *   <tr>
 *     <td>1</td>
 *     <td>ID</td>
 *     <td>Word index, integer starting at 1 for each new sentence; may be a range for tokens with multiple words.</td>
 *   </tr>
 *   <tr>
 *     <td>2</td>
 *     <td>FORM</td>
 *     <td>Word form or punctuation symbol.</td>
 *   </tr>
 *   <tr>
 *     <td>3</td>
 *     <td>LEMMA</td>
 *     <td>Lemma or stem of word form, or an underscore if not available.</td>
 *   </tr>
 *   <tr>
 *     <td>4</td>
 *     <td>CPOSTAG</td>
 *     <td>Universal part-of-speech tag, or underscore if not available.</td>
 *   </tr>
 *   <tr>
 *     <td>5</td>
 *     <td>POSTAG</td>
 *     <td>Language-specific part-of-speech tag, or underscore if not available.</td>
 *   </tr>
 *   <tr>
 *     <td>6</td>
 *     <td>FEATS</td>
 *     <td>List of morphological features from the universal feature inventory or from a defined language-specific extension; underscore if not available.</td>
 *   </tr>
 *   <tr>
 *     <td>7</td>
 *     <td>HEAD</td>
 *     <td>Head of the current token, which is either a value of ID or zero ('0').
 *         This is underscore if not available.</td>
 *   </tr>
 *   <tr>
 *     <td>8</td>
 *     <td>DEPREL</td>
 *     <td>Dependency relation to the HEAD, or underscore if not available.</td>
 *   </tr>
 *   <tr>
 *     <td>9</td>
 *     <td>DEPS</td>
 *     <td>List of secondary dependencies</td>
 *   </tr>
 *   <tr>
 *     <td>10</td>
 *     <td>MISC</td>
 *     <td>Any other annotation</td>
 *   </tr>
 * </table>
 *
 * @author Sebastian Schuster
 * @author Gabor Angeli
 *
 */
public class CoNLLUOutputter extends AnnotationOutputter {

  private static final CoNLLUDocumentWriter conllUWriter = new CoNLLUDocumentWriter();

  /**
   * The type of dependencies to print, options:
   * - basic
   * - enhanced
   * - enhancedPlusPlus
   *
   * basic is the default
   */
  private final SemanticGraphCoreAnnotations.DependenciesType dependenciesType;

  public CoNLLUOutputter() {
    this(new Properties());
  }

  public CoNLLUOutputter(String type) {
    this(new Properties() {{
           setProperty("output.dependenciesType", type);
    }});
  }

  public CoNLLUOutputter(Properties props) {
    dependenciesType = SemanticGraphCoreAnnotations.DependenciesType.valueOf(props.getProperty("output.dependenciesType", "basic").toUpperCase(Locale.ROOT));
  }

  @Override
  public void print(Annotation doc, OutputStream target, Options options) throws IOException {
    PrintWriter writer = new PrintWriter(IOUtils.encodedOutputStreamWriter(target, options.encoding));

    List<CoreMap> sentences = doc.get(CoreAnnotations.SentencesAnnotation.class);
    for (CoreMap sentence : sentences) {
      SemanticGraph sg = sentence.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);
      if (sg != null) {
        switch (dependenciesType) {
        case ENHANCED:
        case ENHANCEDPLUSPLUS:
          writer.print(conllUWriter.printSemanticGraph(sg, sentence.get(dependenciesType.annotation())));
          break;
        case BASIC:
          writer.print(conllUWriter.printSemanticGraph(sg));
          break;
        default:
          throw new IllegalArgumentException("CoNLLUOutputter: unknown dependencies type " + dependenciesType);
        }
      } else {
        writer.print(conllUWriter.printPOSAnnotations(sentence, options.printFakeDeps));
      }
    }
    writer.flush();
  }


  public static void conllUPrint(Annotation annotation, OutputStream os) throws IOException {
    new CoNLLUOutputter().print(annotation, os);
  }

  public static void conllUPrint(Annotation annotation, OutputStream os, StanfordCoreNLP pipeline) throws IOException {
    new CoNLLUOutputter().print(annotation, os, pipeline);
  }

  public static void conllUPrint(Annotation annotation, OutputStream os, Options options) throws IOException {
    new CoNLLUOutputter().print(annotation, os, options);
  }

}
