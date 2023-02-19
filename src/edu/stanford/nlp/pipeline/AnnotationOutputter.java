package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.ling.AnnotationLookup;
import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.semgraph.SemanticGraphFactory;
import edu.stanford.nlp.trees.TreePrint;
import edu.stanford.nlp.util.PropertiesUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * An interface for outputting CoreNLP Annotations to different output
 * formats.
 * These are intended to be for more or less for human consumption (or for transferring
 * to other applications) -- that is, their output is not intended to be read back into
 * CoreNLP losslessly.
 *
 * For lossless (or near lossless) serialization,
 * see {@link edu.stanford.nlp.pipeline.AnnotationSerializer}; e.g.,
 * {@link edu.stanford.nlp.pipeline.ProtobufAnnotationSerializer}.
 *
 * @see edu.stanford.nlp.pipeline.XMLOutputter
 * @see edu.stanford.nlp.pipeline.JSONOutputter
 *
 * @author Gabor Angeli
 */
public abstract class AnnotationOutputter {

  static final TreePrint DEFAULT_CONSTITUENCY_TREE_PRINTER = new TreePrint("penn");

  private static final TreePrint DEFAULT_DEPENDENCY_TREE_PRINTER = new TreePrint("typedDependenciesCollapsed");

  private static final String DEFAULT_KEYS = "idx,word,lemma,pos,ner,headidx,deprel";

  private static final Options DEFAULT_OPTIONS = new Options(); // IMPORTANT: must come after DEFAULT_CONSTITUENCY_TREE_PRINTER

  /** Some outputters output all of the SemanticGraphs.  Others may just output one */
  private static final SemanticGraphFactory.Mode DEFAULT_SEMANTIC_GRAPH = SemanticGraphFactory.Mode.ENHANCED_PLUS_PLUS;

  public static class Options {

    /** Should the document text be included as part of the XML output */
    public final boolean includeText;
    /** The output encoding to use */
    public final String encoding;
    /** If false, try to compress whitespace as much as possible to minimize the size of the output. This is
     *  particularly useful for sending over the wire. If true, the outputters should pretty-print the output.
     */
    public final boolean pretty;
    /** How to print a constituency tree for display. */
    public final TreePrint constituencyTreePrinter;
    /** How to print a dependency tree by default for display. */
    public final TreePrint dependencyTreePrinter;
    /** Should a small window of context be provided with each coreference mention */
    public final int coreferenceContextSize;
    /** If false, will print only non-singleton entities */
    public final boolean printSingletons;
    /** Provides a beam score cutoff on whether relations are printed
     *  (in XMLOutputter and TextOutputter from edu.stanford.nlp.ie.machinereading.structure.RelationMention).
     */
    public final double relationsBeam;
    /** Columns to print in CoNLL output. */
    public final List<Class<? extends CoreAnnotation<?>>> keysToPrint;
    /** Print some fake dependency info in the CoNLL output.
        Useful for the original conll eval script, for example */
    public final boolean printFakeDeps;
    /** Which graph to output if we only output one */
    public final SemanticGraphFactory.Mode semanticGraphMode;

    public Options() {
      // this creates the default options object
      this(true);
    }

    public Options(boolean pretty) {
      includeText = false;
      encoding = "UTF-8";
      this.pretty = pretty;
      constituencyTreePrinter = DEFAULT_CONSTITUENCY_TREE_PRINTER;
      dependencyTreePrinter = DEFAULT_DEPENDENCY_TREE_PRINTER;
      coreferenceContextSize = 0;
      printSingletons = false;
      relationsBeam = 0.0;
      keysToPrint = getKeysToPrint(DEFAULT_KEYS);
      printFakeDeps = false;
      semanticGraphMode = DEFAULT_SEMANTIC_GRAPH;
    }

    public Options(Properties properties) {
      includeText = PropertiesUtils.getBool(properties, "output.includeText", false);
      encoding = properties.getProperty("encoding", "UTF-8");
      pretty = PropertiesUtils.getBool(properties, "output.prettyPrint", true);
      String constituencyTreeStyle = properties.getProperty("output.constituencyTree", "penn");
      constituencyTreePrinter = new TreePrint(constituencyTreeStyle);
      String dependencyTreeStyle = properties.getProperty("output.dependencyTree", "typedDependenciesCollapsed");
      dependencyTreePrinter = new TreePrint(dependencyTreeStyle);
      coreferenceContextSize = PropertiesUtils.getInt(properties,"output.coreferenceContextSize", 0);
      printSingletons = PropertiesUtils.getBool(properties, "output.printSingletonEntities", false);
      relationsBeam = PropertiesUtils.getDouble(properties, "output.relation.beam", 0.0);
      keysToPrint = getKeysToPrint(properties.getProperty("output.columns", DEFAULT_KEYS));
      printFakeDeps = PropertiesUtils.getBool(properties, "output.printFakeDeps", false);
      String graphMode = properties.getProperty("output.dependencyType", null);
      if (graphMode == null) {
        semanticGraphMode = DEFAULT_SEMANTIC_GRAPH;
      } else {
        semanticGraphMode = SemanticGraphFactory.Mode.valueOf(graphMode.toUpperCase());
      }
    }

    private static List<Class<? extends CoreAnnotation<?>>> getKeysToPrint(String columns) {
      if (columns == null) {
        columns = DEFAULT_KEYS;
      }
      String[] keyArray = columns.split(" *, *");
      List<Class<? extends CoreAnnotation<?>>> keyList = new ArrayList<>();
      for (String key : keyArray) {
        keyList.add(AnnotationLookup.toCoreKey(key));
      }
      return keyList;
    }

  } // end static class Options


  public abstract void print(Annotation doc, OutputStream target, Options options) throws IOException;

  public void print(Annotation annotation, OutputStream os) throws IOException {
    print(annotation, os, DEFAULT_OPTIONS);
  }

  public void print(Annotation annotation, OutputStream os, StanfordCoreNLP pipeline) throws IOException {
    print(annotation, os, getOptions(pipeline.getProperties()));
  }

  public String print(Annotation ann, Options options) throws IOException {
    ByteArrayOutputStream os = new ByteArrayOutputStream();
    print(ann, os, options);
    os.close();
    return new String(os.toByteArray());
  }

  public String print(Annotation ann) throws IOException {
    return print(ann, DEFAULT_OPTIONS);
  }

  public String print(Annotation ann, StanfordCoreNLP pipeline) throws IOException {
    return print(ann, getOptions(pipeline.getProperties()));
  }


  /**
   * Populates options from StanfordCoreNLP pipeline.
   */
  public static Options getOptions(Properties properties) {
    return new Options(properties);
  }

}
