package edu.stanford.nlp.ie.hmm;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.regex.Pattern;

import edu.stanford.nlp.annotation.TaggedStreamTokenizer;
import edu.stanford.nlp.ie.Corpus;
import edu.stanford.nlp.ie.TypedTaggedDocument;
import edu.stanford.nlp.ling.Document;
import edu.stanford.nlp.ling.TypedTaggedWord;
import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.objectbank.IteratorFromReaderFactory;
import edu.stanford.nlp.util.StringUtils;

/**
 * Reads in GENIA biomedical docs in XML format, and vends {@link TypedTaggedDocument}s
 *
 * @author Joseph Smarr (jsmarr@stanford.edu)
 * @author Huy Nguyen (htnguyen@cs.stanford.edu)
 * @author Sarah Spikes (sdspikes@cs.stanford.edu) (Templatization)
 *
 * @param <L> The type of the labels
 */
public class GeniaDocumentIterator<L> extends IECollectionIterator<Document<L, Word, Word>> {
  private static final String XMLTAG = "<?xml";

  private static final String ABSTRACT_START = "<abstract>";
  private static final String ABSTRACT_END = "</abstract>";
  private static final String SENTENCE_START = "<sentence>";
  private static final String SENTENCE_END = "</sentence>";

  /**
   * Returns a list of the standard 24 GENIA target fields.
   */
  public static final String[] allTargetFields = new String[]{"amino_acid_monomer", "atom", "body_part", "carbohydrate", "cell_component", "cell_line", "cell_type", "coordinated", "DNA", "inorganic", "lipid", "mono_cell", "multi_cell", "nucleotide", "organic", "other_artificial_source", "other_name", "other_organic_compound", "peptide", "polynucleotide", "protein", "RNA", "tissue", "virus"};

  private static final Pattern proteinCollapser = getSubTagCollapsingPattern("protein");
  private static final Pattern dnaCollapser = getSubTagCollapsingPattern("DNA");
  private static final Pattern rnaCollapser = getSubTagCollapsingPattern("RNA");

  private Reader r;
  private String text;
  private boolean ignoreSubTags; // whether to collapse subtypes of protein, DNA, and RNA


  /**
   * Calls {@link #factory(String[], boolean) factory(targetFields, true)}.
   */
  public static <L> IteratorFromReaderFactory<Document<L, Word, Word>> factory(String[] targetFields) {
    return new GeniaDocumentIteratorFactory<L>(targetFields, true);
  }

  /**
   * Returns a factory that vends GeniaDocumentIterators.
   *
   * @param ignoreSubTags whether to collapse the sub-target-fields of protein, DNA, and RNA.
   *                      There are actually several protein tags (protein_complex, protein_molecule,
   *                      etc.) but as far as I can tell, in a lot of work they just treat all protein
   *                      tags as one target field. Default is to ignore sub tags.
   */
  public static <L> IteratorFromReaderFactory<Document<L, Word, Word>> factory(String[] targetFields, boolean ignoreSubTags) {
    return new GeniaDocumentIteratorFactory<L>(targetFields, ignoreSubTags);

  }

  @Override
  public boolean hasNext() {
    return text != null;
  }

  @Override
  public Document<L, Word, Word> next() {
    if (text == null) {
      throw new NoSuchElementException();
    }
    Document<L, Word, Word> o = parse(text);
    text = read();
    return o;
  }


  /**
   * Constructs a new GeniaDocumentIterator from the given input source
   * that will tag the given target fields. If targetFields[0] is not
   * "(Background)" it is added as such internally.
   */
  protected GeniaDocumentIterator(Reader r, String[] targetFields, boolean ignoreSubTags) {
    this.r = r;
    setTargetFields(targetFields);
    this.ignoreSubTags = ignoreSubTags;
  }

  /**
   * Reads the next abstract (removes sentence tags but leaves cons tags).
   */
  protected String read() {
    BufferedReader br = (BufferedReader) r;
    StringBuffer sb = new StringBuffer();
    String line;
    boolean started = false; // have we found the next abstract
    try {
      while ((line = br.readLine()) != null) {
        line = line.trim();
        if (!started && !line.equals(ABSTRACT_START)) {
          continue; // skip to next abstract
        }
        if (line.equals(ABSTRACT_START)) {
          started = true; // found it
          continue; // skip start tag
        }
        if (started && line.equals(ABSTRACT_END)) {
          break; // done
        }

        // keeps ... in <sentence>...</sentence>
        if (line.startsWith(SENTENCE_START)) {
          line = line.substring(SENTENCE_START.length());
        }
        if (line.endsWith(SENTENCE_END)) {
          line = line.substring(0, line.length() - SENTENCE_END.length());
        }

        sb.append(line);
        sb.append("\n"); // to ensure sentences are broken up
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
    if (sb.length() > 0) {
      return (sb.toString().trim());
    }
    return (null);
  }

  /**
   * Returns a regexp pattern to turn "tag_extra_stuff" into "tag" ($1).
   */
  private static final Pattern getSubTagCollapsingPattern(String tag) {
    return (Pattern.compile("(sem=\"G#" + tag + ")_[^\"]+"));
  }


  /**
   * Parses the given text by looking for XML tags for target fields.
   * For text inside an XML tag, if the name of the tag is one of the
   * target fields passed in during construction, those words get a unique
   * type. All other words are given type 0 (i.e. background). For example,
   * if the targetField passed in was <tt>target</tt>, then the text
   * "this is a <cons sem="G#target">taget field</cons> in a doc" would be parsed into
   * 8 TypedTaggedWords, of which "target" and "field" have type 1 and all
   * the other words have type 0.
   *
   * @return a TypedTaggedDocument where tagged fields are given non-zero types.
   */
  public Document<L, Word, Word> parse(String text) {
    TypedTaggedDocument<L> doc = new TypedTaggedDocument<L>(targetFields);

    if (ignoreSubTags) {
      // collapses sub-tags into one main tag
      text = proteinCollapser.matcher(text).replaceAll("$1");
      text = dnaCollapser.matcher(text).replaceAll("$1");
      text = rnaCollapser.matcher(text).replaceAll("$1");
    }

    // holds the two markup forms recognized
    String[] tags = new String[targetFields.length];
    // start from 1 as targetFields[0] is background state
    for (int i = 1; i < targetFields.length; i++) {
      tags[i] = "<cons sem=\"G#" + targetFields[i] + "\">";
    }

    TaggedStreamTokenizer tokenizer = makeTokenizer(new StringReader(text));
    if (tokenizer == null) {
      throw(new IllegalStateException("Unable to create tokenizer"));
    }
    List<Word> words = new ArrayList<Word>();   // list of words for this document
    try {

      // check if it is an XML document
      tokenizer.setDiscardHtml(false);
      tokenizer.nextToken();

      if ((tokenizer.ttype == TaggedStreamTokenizer.TT_BACKGROUND_HTML) && tokenizer.sval.startsWith(XMLTAG)) {
        // then it is an XML document
        tokenizer.nextToken();
        while (tokenizer.ttype != TaggedStreamTokenizer.TT_EOF) {
          if (tokenizer.ttype == TaggedStreamTokenizer.TT_BACKGROUND_WORD || tokenizer.ttype == TaggedStreamTokenizer.TT_TARGET_WORD) {
            int newtype = -1;
            boolean set = false;
            if (tokenizer.ttype != TaggedStreamTokenizer.TT_TARGET_WORD) {
              newtype = 0;
            } else {
              for (int t = 1; t < targetFields.length && !set; t++) {
                if (tags[t].equals(tokenizer.attr)) {
                  set = true;
                }
                newtype = t;
              }
            }
            words.add(new TypedTaggedWord(tokenizer.sval, newtype));
          }
          tokenizer.nextToken();
        }
        doc.addAll(words); // add parsed words to this doc

      } else {

        // then it is not an XML document, do as before
        tokenizer.setDiscardHtml(true);
        while (tokenizer.ttype != TaggedStreamTokenizer.TT_EOF) {
          if (tokenizer.ttype == TaggedStreamTokenizer.TT_BACKGROUND_WORD || tokenizer.ttype == TaggedStreamTokenizer.TT_TARGET_WORD) {
            int newtype = -1;
            boolean set = false;
            if (tokenizer.ttype != TaggedStreamTokenizer.TT_TARGET_WORD) {
              newtype = 0;
            } else {
              for (int t = 1; t < targetFields.length && !set; t++) {
                if (tags[t].equals(tokenizer.attr)) {
                  set = true;
                }
                newtype = t;
              }
            }
            words.add(new TypedTaggedWord(tokenizer.sval, newtype));
          }
          tokenizer.nextToken();
        }
        doc.addAll(words); // add parsed words to this doc

      }
    } catch (Exception e) {
      e.printStackTrace();
    }

    return (doc);
  }

  private TaggedStreamTokenizer makeTokenizer(Reader input) {
    TaggedStreamTokenizer tokenizer;

    tokenizer = new TaggedStreamTokenizer(input);
    tokenizer.setKeeperCharacters(".,:!?;{}-()/$'@");

    for (int i = 1; i < targetFields.length; i++) {
      tokenizer.addTarget("<cons sem=\"G#" + targetFields[i] + "\">", "</cons>", true);
      //tokenizer.addTarget("<" + targetFields[i] + ">", "</" + targetFields[i] + ">", true);
      //tokenizer.addTarget("<tag name=\"" + targetFields[i] + "\" value=\"start\"/>", "<tag name=\"" + targetFields[i] + "\" value=\"end\"/>");
    }
    return tokenizer;
  }

  private static class GeniaDocumentIteratorFactory<L> implements IteratorFromReaderFactory<Document<L, Word, Word>> {
    String[] targetFields;
    boolean ignoreSubTags;

    public GeniaDocumentIteratorFactory(String[] targetFields, boolean ignoreSubTags) {
      this.targetFields = targetFields;
      this.ignoreSubTags = ignoreSubTags;
    }

    public Iterator<Document<L, Word, Word>> getIterator(Reader r) {
      return new GeniaDocumentIterator<L>(r, targetFields, ignoreSubTags);
    }

  }

  /**
   * For internal debugging purposes only.
   */
  public static void main(String[] args) throws Exception {
    if (args.length == 0) {
      System.err.println("Usage: java GeniaDocumentIterator xmlfile targetfield+");
      System.err.println("Consider using /u/nlp/data/iedata/GENIA-3.01/GENIAcorpus3.01.xml");
      System.err.println("Target fields: peptide, virus, lipid, etc.");
      System.exit(0);
    }

    PrintWriter out = new PrintWriter(System.err, true);

    String[] targetFields = new String[args.length - 1];
    for (int i = 1; i < args.length; i++) {
      targetFields[i - 1] = args[i];
    }
    if (targetFields.length == 0) {
      targetFields = allTargetFields;
    }
    out.println("Using target fields: " + StringUtils.join(targetFields));

    Corpus<String, Word> docs = new Corpus<String, Word>(targetFields);
    ArrayList<File> files = new ArrayList<File>();
    files.add(new File(args[0]));
    IteratorFromReaderFactory<Document<String, Word, Word>> iter = GeniaDocumentIterator.factory(targetFields);
    docs.load(files, iter); 

    /*BasicDataCollection datums = PnpClassifier.makeDatums(docs);
    int numFolds = 3;
    double trainFraction = 0.75;
    ClassifierFactory pnpcf = new PnpClassifierFactory();
    for (int i = 1; i <= numFolds; i++) {
        out.println("Fold " + i + " of " + numFolds);
        DataCollection[] splits = datums.splitRandom(trainFraction);
        out.println("Training pnp classifier with " + splits[0].size() + " datums...");
        Classifier pnpc = pnpcf.trainClassifier(splits[0]);
        out.println("Testing on " + splits[1].size() + " datums...");
        ClassifiedDatum[] results = ClassifierTester.testClassifier(pnpc, splits[1]);
        ClassifierTester.printConfusionMatrix(results, out, 8);
    } */
  }

}
