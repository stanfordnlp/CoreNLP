package edu.stanford.nlp.pipeline;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.StringUtils;


/**
 * Write a subset of our CoreNLP output in CoNLL format.
 * The output can be customized to write any set of keys available with names as defined by AnnotationLookup,
 * and in addition these specials: ID (token index in sentence, numbering from 1).
 *
 * The default fields currently output are:
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
 *     <td>ID (idx)</td>
 *     <td>Token Counter, starting at 1 for each new sentence.</td>
 *   </tr>
 *   <tr>
 *     <td>2</td>
 *     <td>FORM (word)</td>
 *     <td>Word form or punctuation symbol.</td>
 *   </tr>
 *   <tr>
 *     <td>3</td>
 *     <td>LEMMA (lemma)</td>
 *     <td>Lemma of word form, or an underscore if not available.</td>
 *   </tr>
 *   <tr>
 *     <td>4</td>
 *     <td>POSTAG (pos)</td>
 *     <td>Fine-grained part-of-speech tag, or underscore if not available.</td>
 *   </tr>
 *   <tr>
 *     <td>5</td>
 *     <td>NER (ner)</td>
 *     <td>Named Entity tag, or underscore if not available.</td>
 *   </tr>
 *   <tr>
 *     <td>6</td>
 *     <td>HEAD (headidx)</td>
 *     <td>Head of the current token, which is either a value of ID or zero ('0').
 *         This is underscore if not available.</td>
 *   </tr>
 *   <tr>
 *     <td>7</td>
 *     <td>DEPREL (deprel)</td>
 *     <td>Dependency relation to the HEAD, or underscore if not available.</td>
 *   </tr>
 * </table>
 *
 * @author Gabor Angeli
 */
public class CoNLLOutputter extends AnnotationOutputter {

  private static final String NULL_PLACEHOLDER = "_";


  public CoNLLOutputter() {}


  private static String orNeg(int in) {
    if (in < 0) {
      return NULL_PLACEHOLDER;
    } else {
      return Integer.toString(in);
    }
  }

  private static String orNull(Object in) {
    if (in == null) {
      return NULL_PLACEHOLDER;
    } else {
      return in.toString();
    }
  }

  /**
   * Produce a line of the CoNLL output.
   */
  private static String line(int index, CoreLabel token, int head, String deprel, Options options) {
    List<Class<? extends CoreAnnotation<?>>> keysToPrint = options.keysToPrint;
    ArrayList<String> fields = new ArrayList<>(keysToPrint.size());

    for (Class<? extends CoreAnnotation<?>> keyClass : keysToPrint) {
      if (keyClass.equals(CoreAnnotations.IndexAnnotation.class)) {
        fields.add(orNull(index));
      } else if (keyClass.equals(CoreAnnotations.CoNLLDepTypeAnnotation.class)) {
        fields.add(orNull(deprel));
      } else if (keyClass.equals(CoreAnnotations.CoNLLDepParentIndexAnnotation.class)) {
        fields.add(orNeg(head));
      } else {
        fields.add(orNull(token.get((Class) keyClass)));
      }
    }

    /*
    fields.add(Integer.toString(index)); // 1
    fields.add(orNull(token.word()));    // 2
    fields.add(orNull(token.lemma()));   // 3
    fields.add(orNull(token.tag()));     // 4
    fields.add(orNull(token.ner()));     // 5
    if (head >= 0) {
      fields.add(Integer.toString(head));  // 6
      fields.add(deprel);                  // 7
    } else {
      fields.add(NULL_PLACEHOLDER);
      fields.add(NULL_PLACEHOLDER);
    }
    */

    if (options.pretty) {
      return StringUtils.join(fields, "\t");
    } else {
      return StringUtils.join(fields, "/");
    }
  }

  /** Print an Annotation to an output stream.
   *  The target OutputStream is assumed to already by buffered.
   *
   *  @param doc
   *  @param target
   *  @param options
   *  @throws IOException
   */
  @Override
  public void print(Annotation doc, OutputStream target, Options options) throws IOException {
    PrintWriter writer = new PrintWriter(IOUtils.encodedOutputStreamWriter(target, options.encoding));

    // vv A bunch of nonsense to get tokens vv
    if (doc.get(CoreAnnotations.SentencesAnnotation.class) != null) {
      for (CoreMap sentence : doc.get(CoreAnnotations.SentencesAnnotation.class)) {
        if (sentence.get(CoreAnnotations.TokensAnnotation.class) != null) {
          List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
          SemanticGraph depTree = sentence.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);
          for (int i = 0; i < tokens.size(); ++i) {
            // ^^ end nonsense to get tokens ^^

            // Try to get the incoming dependency edge
            int head = -1;
            String deprel = null;
            if (depTree != null) {
              Set<Integer> rootSet = depTree.getRoots().stream().map(IndexedWord::index).collect(Collectors.toSet());
              IndexedWord node = depTree.getNodeByIndexSafe(i + 1);
              if (node != null) {
                List<SemanticGraphEdge> edgeList = depTree.getIncomingEdgesSorted(node);
                if (!edgeList.isEmpty()) {
                  assert edgeList.size() == 1;
                  head = edgeList.get(0).getGovernor().index();
                  deprel = edgeList.get(0).getRelation().toString();
                } else if (rootSet.contains(i + 1)) {
                  head = 0;
                  deprel = "ROOT";
                }
              }
            }

            // Write the token
            writer.print(line(i + 1, tokens.get(i), head, deprel, options));
            if (options.pretty) {
              writer.println();
            } else if (i < tokens.size() - 1) {
              writer.print(' ');
            }
          }
        }
        writer.println(); // extra blank line at end of sentence
      }
    }
    writer.flush();
  }

  public static void conllPrint(Annotation annotation, OutputStream os) throws IOException {
    new CoNLLOutputter().print(annotation, os);
  }

  public static void conllPrint(Annotation annotation, OutputStream os, StanfordCoreNLP pipeline) throws IOException {
    new CoNLLOutputter().print(annotation, os, pipeline);
  }

  public static void conllPrint(Annotation annotation, OutputStream os, Options options) throws IOException {
    new CoNLLOutputter().print(annotation, os, options);
  }

}
