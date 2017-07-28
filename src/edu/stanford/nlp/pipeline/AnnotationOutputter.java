package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.trees.TreePrint;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * <p>An interface for outputting CoreNLP Annotations to different output
 * formats.
 * These are intended to be for more or less human consumption (or for transferring
 * to other applications) -- that is, there output is not intended to be read back into
 * CoreNLP losslessly.</p>
 *
 * <p>For lossless (or near lossless) serialization,
 * see {@link edu.stanford.nlp.pipeline.AnnotationSerializer}; e.g.,
 * {@link edu.stanford.nlp.pipeline.ProtobufAnnotationSerializer}.
 *
 * @see edu.stanford.nlp.pipeline.XMLOutputter
 * @see edu.stanford.nlp.pipeline.JSONOutputter
 *
 * @author Gabor Angeli
 */
public abstract class AnnotationOutputter {
  static final TreePrint DEFAULT_CONSTITUENT_TREE_PRINTER = new TreePrint("penn");
  private static final Options DEFAULT_OPTIONS = new Options(); // IMPORTANT: must come after DEFAULT_CONSTITUENCY_TREE_PRINTER

  public static class Options {
    /** Should the document text be included as part of the XML output */
    public boolean includeText = false;
    /** Should a small window of context be provided with each coreference mention */
    public int coreferenceContextSize = 0;
    /** The output encoding to use */
    public String encoding = "UTF-8";
    /** How to print a constituent tree */
    public TreePrint constituentTreePrinter = DEFAULT_CONSTITUENT_TREE_PRINTER;
    /** If false, will print only non-singleton entities*/
    public boolean printSingletons = false;
    /** If false, try to compress whitespace as much as possible. This is particularly useful for sending over the wire. */
    public boolean pretty = true;

    public double relationsBeam = 0.0;
    public double beamPrintingOption = 0.0;
  }


  public abstract void print(Annotation doc, OutputStream target, Options options) throws IOException;

  public void print(Annotation annotation, OutputStream os) throws IOException {
    print(annotation, os, DEFAULT_OPTIONS);
  }

  public void print(Annotation annotation, OutputStream os, StanfordCoreNLP pipeline) throws IOException {
    print(annotation, os, getOptions(pipeline));
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
    return print(ann, getOptions(pipeline));
  }


  /**
   * Populates options from StanfordCoreNLP pipeline
   */
  public static Options getOptions(StanfordCoreNLP pipeline) {
    Options options = new Options();
    options.relationsBeam = pipeline.getBeamPrintingOption();
    options.constituentTreePrinter = pipeline.getConstituentTreePrinter();
    options.encoding = pipeline.getEncoding();
    options.printSingletons = pipeline.getPrintSingletons();
    options.beamPrintingOption = pipeline.getBeamPrintingOption();
    options.pretty = pipeline.getPrettyPrint();
    options.includeText = pipeline.getIncludeText();
    return options;
  }

}
