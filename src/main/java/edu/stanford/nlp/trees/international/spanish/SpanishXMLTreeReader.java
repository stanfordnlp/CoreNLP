package edu.stanford.nlp.trees.international.spanish;

import java.io.*;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import edu.stanford.nlp.io.ReaderInputStream;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.HasLemma;
import edu.stanford.nlp.ling.HasTag;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.trees.LabeledScoredTreeFactory;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeFactory;
import edu.stanford.nlp.trees.TreeNormalizer;
import edu.stanford.nlp.trees.TreeReader;
import edu.stanford.nlp.trees.TreebankLanguagePack;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.util.logging.Redwood;

/**
 * A reader for XML format AnCora treebank files.
 *
 * This reader makes AnCora-specific fixes; see
 * {@link #getPOS(Element)}.
 *
 * @author Jon Gauthier
 * @author Spence Green (original French XML reader)
 *
 */
public class SpanishXMLTreeReader implements TreeReader  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(SpanishXMLTreeReader.class);

  private InputStream stream;
  private final SpanishTreeNormalizer treeNormalizer;
  private final TreeFactory treeFactory;

  private boolean simplifiedTagset;
  private boolean detailedAnnotations;
  private boolean expandElisions;
  private boolean expandConmigo;

  private static final String NODE_SENT = "sentence";

  private static final String ATTR_WORD = "wd";
  private static final String ATTR_LEMMA = "lem";
  private static final String ATTR_FUNC = "func";
  private static final String ATTR_NAMED_ENTITY = "ne";
  private static final String ATTR_POS = "pos";
  private static final String ATTR_POSTYPE = "postype";
  private static final String ATTR_ELLIPTIC = "elliptic";
  private static final String ATTR_PUNCT = "punct";
  private static final String ATTR_GENDER = "gen";
  private static final String ATTR_NUMBER = "num";

  // Constituent annotations
  private static final String ATTR_COORDINATING = "coord";
  private static final String ATTR_CLAUSE_TYPE = "clausetype";

  private NodeList sentences;
  private int sentIdx;

  /**
   * Read parse trees from a Reader.
   *
   * @param filename
   * @param in The {@code Reader}
   * @param simplifiedTagset If `true`, convert part-of-speech labels to a
   *          simplified version of the EAGLES tagset, where the tags do not
   *          include extensive morphological analysis
   * @param aggressiveNormalization Perform aggressive "normalization"
   *          on the trees read from the provided corpus documents:
   *          split multi-word tokens into their constituent words (and
   *          infer parts of speech of the constituent words).
   * @param retainNER Retain NER information in preterminals (for later
   *          use in `MultiWordPreprocessor) and add NER-specific
   *          parents to single-word NE tokens
   * @param detailedAnnotations Retain detailed tree node annotations. These
   *          annotations on parse tree constituents may be useful for
   *          e.g. training a parser.
   * @param expandElisions MWT Expand words like del, al
   * @param expandConmigo MWT Expand words like conmigo, contigo
   *
   *
   */
  public SpanishXMLTreeReader(String filename, Reader in, boolean simplifiedTagset,
                              boolean aggressiveNormalization,
                              boolean retainNER, boolean detailedAnnotations, boolean expandElisions,
                              boolean expandConmigo) {
    TreebankLanguagePack tlp = new SpanishTreebankLanguagePack();

    this.simplifiedTagset = simplifiedTagset;
    this.detailedAnnotations = detailedAnnotations;
    this.expandElisions = expandElisions;
    this.expandConmigo = expandConmigo;

    stream = new ReaderInputStream(in, tlp.getEncoding());
    treeFactory = new LabeledScoredTreeFactory();
    treeNormalizer =
      new SpanishTreeNormalizer(simplifiedTagset,
                                aggressiveNormalization,
                                retainNER);

    DocumentBuilder parser = XMLUtils.getXmlParser();
    try {
      final Document xml = parser.parse(stream);
      final Element root = xml.getDocumentElement();
      sentences = root.getElementsByTagName(NODE_SENT);
      sentIdx = 0;

    } catch (SAXException e) {
      log.info("Parse exception while reading " + filename);
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public void close() {
    try {
      if(stream != null) {
        stream.close();
        stream = null;
      }
    } catch (IOException e) {
      //Silently ignore
    }
  }

  public Tree readTree() {
    Tree t = null;
    while(t == null && sentences != null && sentIdx < sentences.getLength()) {
      int thisSentenceId = sentIdx++;
      Node sentRoot = sentences.item(thisSentenceId);
      t = getTreeFromXML(sentRoot);

      if(t != null) {
        t = treeNormalizer.normalizeWholeTree(t, treeFactory, expandElisions, expandConmigo);

        if(t.label() instanceof CoreLabel)
          ((CoreLabel) t.label()).set(CoreAnnotations.SentenceIDAnnotation.class,
                                      Integer.toString(thisSentenceId));
      }
    }
    return t;
  }

  private static boolean isWordNode(Element node) {
    return node.hasAttribute(ATTR_WORD) && !node.hasChildNodes();
  }

  private static boolean isEllipticNode(Element node) {
    return node.hasAttribute(ATTR_ELLIPTIC);
  }

  /**
   * Determine the part of speech of the given leaf node.
   *
   * Use some heuristics to make up for missing part-of-speech labels.
   */
  private String getPOS(Element node) {
    String pos = node.getAttribute(ATTR_POS);

    String namedAttribute = node.getAttribute(ATTR_NAMED_ENTITY);
    if (pos.startsWith("np") && pos.length() == 7
        && pos.charAt(pos.length() - 1) == '0') {
      // Some nouns are missing a named entity annotation in the final
      // character of their POS tags, but still have a proper named
      // entity annotation in the `ne` attribute. Fix this:
      char annotation = '0';
      if (namedAttribute.equals("location")) {
        annotation = 'l';
      } else if (namedAttribute.equals("person")) {
        annotation = 'p';
      } else if (namedAttribute.equals("organization")) {
        annotation = 'o';
      }

      pos = pos.substring(0, 6) + annotation;
    } else if (pos.equals("")) {
      // Make up for some missing part-of-speech tags
      String word = getWord(node);
      if (word.equals("."))
        return "fp";

      if (namedAttribute.equals("date")) {
        return "w";
      } else if (namedAttribute.equals("number")) {
        return "z0";
      }

      String tagName = node.getTagName();
      if (tagName.equals("i")) {
        return "i";
      } else if (tagName.equals("r")) {
        return "rg";
      } else if (tagName.equals("z")) {
        return "z0";
      }

      // Handle icky issues related to "que"
      String posType = node.getAttribute(ATTR_POSTYPE);
      if (tagName.equals("c") && posType.equals("subordinating")) {
        return "cs";
      } else if (tagName.equals("p") && posType.equals("relative")
                 && word.equalsIgnoreCase("que")) {
        return "pr0cn000";
      }

      if (tagName.equals("s") && (word.equalsIgnoreCase("de") || word.equalsIgnoreCase("del")
        || word.equalsIgnoreCase("en"))) {
        return "sps00";
      } else if (word.equals("REGRESA")) {
        return "vmip3s0";
      }

      if (simplifiedTagset) {
        // If we are using the simplified tagset, we can make some more
        // broad inferences
        if (word.equals("verme")) {
          return "vmn0000";
        } else if (tagName.equals("a")) {
          return "aq0000";
        } else if (posType.equals("proper")) {
          return "np00000";
        } else if (posType.equals("common")) {
          return "nc0s000";
        } else if (tagName.equals("d") && posType.equals("numeral")) {
          return "dn0000";
        } else if (tagName.equals("d")
          && (posType.equals("article") || word.equalsIgnoreCase("el") || word.equalsIgnoreCase("la"))) {
          return "da0000";
        } else if (tagName.equals("p") && posType.equals("relative")) {
          return "pr000000";
        } else if (tagName.equals("p") && posType.equals("personal")) {
          return "pp000000";
        } else if (tagName.equals("p") && posType.equals("indefinite")) {
          return "pi000000";
        } else if (tagName.equals("s") && word.equalsIgnoreCase("como")) {
          return "sp000";
        } else if (tagName.equals("n")) {
          String gen = node.getAttribute(ATTR_GENDER);
          String num = node.getAttribute(ATTR_NUMBER);

          char genCode = gen == null ? '0' : gen.charAt(0);
          char numCode = num == null ? '0' : num.charAt(0);
          return 'n' + genCode + '0' + numCode + "000";
        }
      }

      if (node.hasAttribute(ATTR_PUNCT)) {
        if (word.equals("\""))
          return "fe";
        else if (word.equals("'"))
          return "fz";
        else if (word.equals("-"))
          return "fg";
        else if (word.equals("("))
          return "fpa";
        else if (word.equals(")"))
          return "fpt";

        return "fz";
      }
    }

    return pos;
  }

  private static String getWord(Element node) {
    String word = node.getAttribute(ATTR_WORD);
    if (word.isEmpty()) {
      return SpanishTreeNormalizer.EMPTY_LEAF_VALUE;
    }
    return word.trim();
  }

  private Tree getTreeFromXML(Node root) {
    final Element eRoot = (Element) root;

    if (isWordNode(eRoot)) {
      return buildWordNode(eRoot);
    } else if (isEllipticNode(eRoot)) {
      return buildEllipticNode(eRoot);
    } else {
      List<Tree> kids = new ArrayList<>();
      for (Node childNode = eRoot.getFirstChild(); childNode != null;
           childNode = childNode.getNextSibling()) {
        if (childNode.getNodeType() != Node.ELEMENT_NODE) continue;

        Tree t = getTreeFromXML(childNode);
        if (t == null) {
          System.err.printf("%s: Discarding empty tree (root: %s)%n", this.getClass().getName(), childNode.getNodeName());
        } else {
          kids.add(t);
        }
      }

      return kids.isEmpty() ? null : buildConstituentNode(eRoot, kids);
    }
  }

  /**
   * Build a parse tree node corresponding to the word in the given XML node.
   */
  private Tree buildWordNode(Node root) {
    Element eRoot = (Element) root;

    String posStr = getPOS(eRoot);
    posStr = treeNormalizer.normalizeNonterminal(posStr);

    String lemma = eRoot.getAttribute(ATTR_LEMMA);
    String word = getWord(eRoot);

    String leafStr = treeNormalizer.normalizeTerminal(word);
    Tree leafNode = treeFactory.newLeaf(leafStr);
    if (leafNode.label() instanceof HasWord)
      ((HasWord) leafNode.label()).setWord(leafStr);
    if (leafNode.label() instanceof HasLemma && lemma != null)
      ((HasLemma) leafNode.label()).setLemma(lemma);

    List<Tree> kids = new ArrayList<>();
    kids.add(leafNode);

    Tree t = treeFactory.newTreeNode(posStr, kids);
    if (t.label() instanceof HasTag) ((HasTag) t.label()).setTag(posStr);

    return t;
  }

  /**
   * Build a parse tree node corresponding to an elliptic node in the parse XML.
   */
  private Tree buildEllipticNode(Node root) {
    Element eRoot = (Element) root;
    String constituentStr = eRoot.getNodeName();

    List<Tree> kids = new ArrayList<>();
    Tree leafNode = treeFactory.newLeaf(SpanishTreeNormalizer.EMPTY_LEAF_VALUE);
    if (leafNode.label() instanceof HasWord)
      ((HasWord) leafNode.label()).setWord(SpanishTreeNormalizer.EMPTY_LEAF_VALUE);

    kids.add(leafNode);
    Tree t = treeFactory.newTreeNode(constituentStr, kids);

    return t;
  }

  /**
   * Build a parse tree node corresponding to a constituent.
   *
   * @param root Node describing the constituent
   * @param children Collected child nodes, already parsed
   */
  private Tree buildConstituentNode(Node root, List<Tree> children) {
    Element eRoot = (Element) root;
    String label = eRoot.getNodeName().trim();

    if (detailedAnnotations) {
      if (eRoot.getAttribute(ATTR_COORDINATING).equals("yes")) {
        label += "-coord";
      } else if (eRoot.hasAttribute(ATTR_CLAUSE_TYPE)) {
        label += '-' + eRoot.getAttribute(ATTR_CLAUSE_TYPE);
      }
    }

    return treeFactory.newTreeNode(treeNormalizer.normalizeNonterminal(label), children);
  }

  /**
   * Determine if the given tree contains a leaf which matches the
   * part-of-speech and lexical criteria.
   *
   * @param pos Regular expression to match part of speech (may be null,
   *     in which case any POS is allowed)
   * @param pos Regular expression to match word (may be null, in which
   *     case any word is allowed)
   */
  private static boolean shouldPrintTree(Tree tree, Pattern pos, Pattern word) {
    for(Tree t : tree) {
      if(t.isPreTerminal()) {
        CoreLabel label = (CoreLabel) t.label();
        String tpos = label.value();

        Tree wordNode = t.firstChild();
        CoreLabel wordLabel = (CoreLabel) wordNode.label();
        String tword = wordLabel.value();

        if((pos == null || pos.matcher(tpos).find())
           && (word == null || word.matcher(tword).find()))
          return true;
      }
    }
    return false;
  }

  private static String toString(Tree tree, boolean plainPrint) {
    if (!plainPrint)
      return tree.toString();

    StringBuilder sb = new StringBuilder();
    List<Tree> leaves = tree.getLeaves();
    for (Tree leaf : leaves) {
      sb.append(leaf.label().value()).append(' ');
    }
    return sb.toString();
  }

  /**
   * Read trees from the given file and output their processed forms to
   * standard output.
   */
  public static void process(File file, TreeReader tr,
                             Pattern posPattern, Pattern wordPattern,
                             boolean plainPrint) throws IOException {
    Tree t;
    int numTrees = 0, numTreesRetained = 0;
    String canonicalFileName = file.getName().substring(0, file.getName().lastIndexOf('.'));

    while ((t = tr.readTree()) != null) {
      numTrees++;
      if (!shouldPrintTree(t, posPattern, wordPattern))
        continue;
      numTreesRetained++;

      String ftbID = ((CoreLabel) t.label()).get(CoreAnnotations.SentenceIDAnnotation.class);
      String output = toString(t, plainPrint);

      System.out.printf("%s-%s\t%s%n", canonicalFileName, ftbID, output);
    }

    System.err.printf("%s: %d trees, %d matched and printed%n", file.getName(),
                      numTrees, numTreesRetained);
  }

  private static String usage() {
    StringBuilder sb = new StringBuilder();
    String nl = System.getProperty("line.separator");

    sb.append(String.format("Usage: java %s [OPTIONS] file(s)%n%n", SpanishXMLTreeReader.class.getName()));
    sb.append("Options:").append(nl);
    sb.append("   -help: Print this message").append(nl);
    sb.append("   -ner: Add NER-specific information to trees").append(nl);
    sb.append("   -detailedAnnotations: Retain detailed annotations on tree constituents (useful for making treebank for parser, etc.)").append(nl);
    sb.append("   -plain: Output corpus in plaintext rather than as trees").append(nl);
    sb.append("   -searchPos posRegex: Only print sentences which contain a token whose part of speech matches the given regular expression").append(nl);
    sb.append("   -searchWord wordRegex: Only print sentences which contain a token which matches the given regular expression").append(nl);

    return sb.toString();
  }

  private static Map<String, Integer> argOptionDefs() {
    Map<String, Integer> argOptionDefs = Generics.newHashMap();
    argOptionDefs.put("help", 0);
    argOptionDefs.put("ner", 0);
    argOptionDefs.put("detailedAnnotations", 0);
    argOptionDefs.put("plain", 0);

    argOptionDefs.put("searchPos", 1);
    argOptionDefs.put("searchWord", 1);
    return argOptionDefs;
  }

  public static void main(String[] args) {
    final Properties options = StringUtils.argsToProperties(args, argOptionDefs());
    if(args.length < 1 || options.containsKey("help")) {
      log.info(usage());
      return;
    }

    final Pattern posPattern = options.containsKey("searchPos")
      ? Pattern.compile(options.getProperty("searchPos")) : null;
    final Pattern wordPattern = options.containsKey("searchWord")
      ? Pattern.compile(options.getProperty("searchWord")) : null;
    final boolean plainPrint = PropertiesUtils.getBool(options, "plain", false);
    final boolean ner = PropertiesUtils.getBool(options, "ner", false);
    final boolean detailedAnnotations = PropertiesUtils.getBool(options, "detailedAnnotations", false);
    final boolean expandElisions = PropertiesUtils.getBool(options, "expandElisions", false);
    final boolean expandConmigo = PropertiesUtils.getBool(options, "expandConmigo", false);

    String[] remainingArgs = options.getProperty("").split(" ");
    List<File> fileList = new ArrayList<>();
    for (String remainingArg : remainingArgs) fileList.add(new File(remainingArg));

    final SpanishXMLTreeReaderFactory trf = new SpanishXMLTreeReaderFactory(true, true, ner, detailedAnnotations, expandElisions, expandConmigo);
    ExecutorService pool =
      Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    for (final File file : fileList) {
      pool.execute(()-> { try {
              Reader in = new BufferedReader(new InputStreamReader(new FileInputStream(file), "ISO-8859-1"));
              TreeReader tr = trf.newTreeReader(file.getPath(), in);
              process(file, tr, posPattern, wordPattern, plainPrint);
              tr.close();
            } catch (IOException e) {
              e.printStackTrace();
            }});
    }

    pool.shutdown();
    try {
      pool.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
    } catch (InterruptedException e) {
      throw new RuntimeInterruptedException(e);
    }
  }

}
