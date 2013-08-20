package edu.stanford.nlp.process;

import java.io.File;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.ie.crf.CRFClassifier;
import edu.stanford.nlp.ie.ner.CMMClassifier;
import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.Function;


/**
 * Wrappers for TransformXML. The {@link TransformXMLApplications#main
 * <code>main</code>} method creates a {@link LexicalizedParser
 * <code>LexicalizedParser</code>} and applies it to natural language
 * sentences appearing in XML documents. CMM and CRF applications also appear.
 *
 * @author Bill MacCartney
 */
public class TransformXMLApplications {


  private static class ParserWrapper implements Function<String,Tree> {

    private final LexicalizedParser lp;

    public ParserWrapper(String parserFileName) {
      lp = LexicalizedParser.loadModel(parserFileName);
    }

    public Tree apply(String o) {
      List<Word> words = (PTBTokenizer.newPTBTokenizer(new StringReader(o))).tokenize();
      ArrayList<Word> sentence = new ArrayList<Word>(words);
      return lp.parseTree(sentence);
    }
  }

  /**
   * Read XML from input file and write XML to stdout (or output
   * file), while parsing text appearing inside the specified XML
   * tags using a {@link LexicalizedParser
   * <code>LexicalizedParser</code>} created from the specified
   * serialized parser file.  The command-line arguments are:
   * <ol>
   * <li>a comma-separated list of XML tags within which
   * the transformation should be applied</li>
   * <li>the filename of a serialized parser file, typically
   * <code>/u/nlp/data/lexparser/englishPCFG.ser.gz</code></li>
   * <li>the name of the input file</li>
   * <li>(optional) the name of the output file</li>
   * </ol>
   *
   * @param args Command line arguments, as above
   */
  public static void main(String[] args) {
    if (args.length < 3) {
      System.err.println("Usage: java TransformXML comma-separated-tag-list serialized-parser-file in-filename [out-filename]");
      System.err.println(" or: -loadCMMClassifier commaSeparatedTagList serializedCMMClassifier inFilename [outFilename]");
      System.exit(1);
    }
    TransformXML txml = new TransformXML();
    if (args[0].startsWith("-")) {
      if (args[0].equals("-loadFunction")) {
        Function func = null;
        try {
          func = (Function) Class.forName(args[2]).newInstance();
        } catch (Exception e) {
          System.err.println("Couldn't instantiate " + args[2]);
          System.exit(1);
        }
        if (args.length == 4) {
          txml.transformXML(args[1].split(","), func, new File(args[3]));
        } else {
          txml.transformXML(args[1].split(","), func, new File(args[3]), new File(args[4]));
        }
      } else if (args[0].equals("-loadCMMClassifier")) {
        try {
          CMMClassifier cmmc = CMMClassifier.getClassifier(args[2]);
          if (args.length == 4) {
            txml.transformXML(args[1].split(","), cmmc, new File(args[3]));
          } else {
            txml.transformXML(args[1].split(","), cmmc, new File(args[3]), new File(args[4]));
          }
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      } else if (args[0].equals("-loadCRFClassifier")) {
        try {
          CRFClassifier cmmc = CRFClassifier.getClassifier(args[2]);
          if (args.length == 4) {
            txml.transformXML(args[1].split(","), cmmc, new File(args[3]));
          } else {
            txml.transformXML(args[1].split(","), cmmc, new File(args[3]), new File(args[4]));
          }
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      } else {
        System.err.println("Unknown option: " + args[0]);
        System.exit(1);
      }
    } else {
      if (args.length == 3) {
        txml.transformXML(args[0].split(","), new ParserWrapper(args[1]), new File(args[2]));
      } else {
        txml.transformXML(args[0].split(","), new ParserWrapper(args[1]), new File(args[2]), new File(args[3]));
      }
    }
  }

}
