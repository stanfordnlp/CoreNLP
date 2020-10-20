package edu.stanford.nlp.trees.international.french; 
import edu.stanford.nlp.util.logging.Redwood;

import java.io.*;
import java.util.*;

import javax.xml.parsers.DocumentBuilder;

import edu.stanford.nlp.ling.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import edu.stanford.nlp.io.ReaderInputStream;
import edu.stanford.nlp.ling.SentenceUtils;
import edu.stanford.nlp.trees.LabeledScoredTreeFactory;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeFactory;
import edu.stanford.nlp.trees.TreeNormalizer;
import edu.stanford.nlp.trees.TreeReader;
import edu.stanford.nlp.trees.TreeReaderFactory;
import edu.stanford.nlp.trees.TreebankLanguagePack;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.XMLUtils;

/**
 * A reader for XML format French Treebank files. Note that the raw
 * XML files are in ISO-8859-1 format, so they must be converted to UTF-8.
 * <p>
 * Handles multiword expressions (MWEs).
 * <p>
 * One difference worth documenting between this and the
 * PennTreeReader is that this does not unescape \* and \/ the way the
 * PennTreeReader does.  The French Treebank we are using does not
 * use those escapings.
 *
 * @author Spence Green
 *
 */
public class FrenchXMLTreeReader implements TreeReader  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(FrenchXMLTreeReader.class);

  private InputStream stream;
  private final TreeNormalizer treeNormalizer;
  private final TreeFactory treeFactory;

  private static final String NODE_SENT = "SENT";
  private static final String NODE_WORD = "w";

  private static final String ATTR_NUMBER = "nb";
  private static final String ATTR_POS = "cat";
  private static final String ATTR_POS_MWE = "catint";
  private static final String ATTR_LEMMA = "lemma";
  private static final String ATTR_MORPH = "mph";
  private static final String ATTR_EE = "ee";
  private static final String ATTR_SUBCAT = "subcat";

  // Prefix for MWE nodes
  private static final String MWE_PHRASAL = "MW";

  public static final String EMPTY_LEAF = "-NONE-";
  public static final String MISSING_PHRASAL = "DUMMYP";
  public static final String MISSING_POS = "DUMMY";

  private NodeList sentences;
  private int sentIdx;

  /**
   * Read parse trees from a Reader.
   *
   * @param in The <code>Reader</code>
   */
  public FrenchXMLTreeReader(Reader in, boolean ccTagset) {
    this(in, new LabeledScoredTreeFactory(), new FrenchTreeNormalizer(ccTagset));
  }

  /**
   * Read parse trees from a Reader.
   *
   * @param in Reader
   * @param tf TreeFactory -- factory to create some kind of Tree
   * @param tn the method of normalizing trees
   */
  public FrenchXMLTreeReader(Reader in, TreeFactory tf, TreeNormalizer tn) {
    TreebankLanguagePack tlp = new FrenchTreebankLanguagePack();
    stream = new ReaderInputStream(in,tlp.getEncoding());
    treeFactory = tf;
    treeNormalizer = tn;

    DocumentBuilder parser = XMLUtils.getXmlParser();
    try {
      final Document xml = parser.parse(stream);
      final Element root = xml.getDocumentElement();
      sentences = root.getElementsByTagName(NODE_SENT);
      sentIdx = 0;

    } catch (SAXException | IOException e) {
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
      Node sentRoot = sentences.item(sentIdx++);
      t = getTreeFromXML(sentRoot);

      if(t != null) {
        t = treeNormalizer.normalizeWholeTree(t, treeFactory);
        if(t.label() instanceof CoreLabel) {
          String ftbId = ((Element) sentRoot).getAttribute(ATTR_NUMBER);
          ((CoreLabel) t.label()).set(CoreAnnotations.SentenceIDAnnotation.class, ftbId);
        }
      }
    }
    return t;
  }

  //wsg2010: Sometimes the cat attribute is not present, in which case the POS
  //is in the attribute catint, which indicates a part of a compound / MWE
  private String getPOS(Element node) {
    String attrPOS = node.hasAttribute(ATTR_POS) ? node.getAttribute(ATTR_POS).trim() : "";
    String attrPOSMWE = node.hasAttribute(ATTR_POS_MWE) ? node.getAttribute(ATTR_POS_MWE).trim() : "";

    if(attrPOS != "")
      return attrPOS;
    else if(attrPOSMWE != "")
      return attrPOSMWE;

    return MISSING_POS;
  }

  /**
   * Extract the lemma attribute.
   *
   * @param node
   */
  private List<String> getLemma(Element node) {
    String lemma = node.getAttribute(ATTR_LEMMA);
    if (lemma == null || lemma.equals(""))
      return null;
    return getWordString(lemma);
  }

  /**
   * Extract the morphological analysis from a leaf. Note that the "ee" field
   * contains the relativizer flag.
   *
   * @param node
   */
  private String getMorph(Element node) {
    String ee = node.getAttribute(ATTR_EE);
    return ee == null ? "" : ee;
  }

  /**
   * Get the POS subcategory.
   *
   * @param node
   * @return
   */
  private String getSubcat(Element node) {
    String subcat = node.getAttribute(ATTR_SUBCAT);
    return subcat == null ? "" : subcat;
  }

  /**
   * Terminals may consist of one or more whitespace-delimited tokens.
   * <p>
   * wsg2010: Marie recommends replacing empty terminals with -NONE- instead of using the lemma
   * (these are usually the determiner)
   *
   * @param text
   */
  private List<String> getWordString(String text) {
    List<String> toks = new ArrayList<>();
    if(text == null || text.equals(""))
      toks.add(EMPTY_LEAF);
    else {
      //Strip spurious parens
      if(text.length() > 1)
        text = text.replaceAll("[\\(\\)]", "");

      //Check for numbers and punctuation
      String noWhitespaceStr = text.replaceAll("\\s+", "");
      if(noWhitespaceStr.matches("\\d+") || noWhitespaceStr.matches("\\p{Punct}+"))
        toks.add(noWhitespaceStr);
      else
        toks = Arrays.asList(text.split("\\s+"));
    }

    if(toks.size() == 0)
      throw new RuntimeException(this.getClass().getName() + ": Zero length token list for: " + text);

    return toks;
  }

  private Tree getTreeFromXML(Node root) {
    final Element eRoot = (Element) root;

    if (eRoot.getNodeName().equals(NODE_WORD) &&
        eRoot.getElementsByTagName(NODE_WORD).getLength() == 0) {
      String posStr = getPOS(eRoot);
      posStr = treeNormalizer.normalizeNonterminal(posStr);

      List<String> lemmas = getLemma(eRoot);
      String morph = getMorph(eRoot);
      List<String> leafToks = getWordString(eRoot.getTextContent().trim());
      String subcat = getSubcat(eRoot);

      if (lemmas != null && lemmas.size() != leafToks.size()) {
        // If this happens (and it does for a few poorly editted trees)
        // we assume something has gone wrong and ignore the lemmas.
        log.info("Lemmas don't match tokens, ignoring lemmas: " +
                           "lemmas " + lemmas + ", tokens " + leafToks);
        lemmas = null;
      }

      //Terminals can have multiple tokens (MWEs). Make these into a
      //flat structure for now.
      Tree t = null;
      List<Tree> kids = new ArrayList<>();
      if(leafToks.size() > 1) {
        for (int i = 0; i < leafToks.size(); ++i) {
          String tok = leafToks.get(i);
          String s = treeNormalizer.normalizeTerminal(tok);
          List<Tree> leafList = new ArrayList<>();
          Tree leafNode = treeFactory.newLeaf(s);
          if(leafNode.label() instanceof HasWord)
            ((HasWord) leafNode.label()).setWord(s);
          if (leafNode.label() instanceof CoreLabel && lemmas != null) {
            ((CoreLabel) leafNode.label()).setLemma(lemmas.get(i));
          }
          if(leafNode.label() instanceof HasContext) {
            ((HasContext) leafNode.label()).setOriginalText(morph);
          }
          if (leafNode.label() instanceof HasCategory) {
            ((HasCategory) leafNode.label()).setCategory(subcat);
          }
          leafList.add(leafNode);

          Tree posNode = treeFactory.newTreeNode(MISSING_POS, leafList);
          if(posNode.label() instanceof HasTag)
            ((HasTag) posNode.label()).setTag(MISSING_POS);

          kids.add(posNode);
        }
        t = treeFactory.newTreeNode(MISSING_PHRASAL, kids);

      } else {
        String leafStr = treeNormalizer.normalizeTerminal(leafToks.get(0));
        Tree leafNode = treeFactory.newLeaf(leafStr);
        if (leafNode.label() instanceof HasWord)
          ((HasWord) leafNode.label()).setWord(leafStr);
        if (leafNode.label() instanceof CoreLabel && lemmas != null) {
          ((CoreLabel) leafNode.label()).setLemma(lemmas.get(0));
        }
        if (leafNode.label() instanceof HasContext) {
          ((HasContext) leafNode.label()).setOriginalText(morph);
        }
        if (leafNode.label() instanceof HasCategory) {
          ((HasCategory) leafNode.label()).setCategory(subcat);
        }
        kids.add(leafNode);

        t = treeFactory.newTreeNode(posStr, kids);
        if (t.label() instanceof HasTag) ((HasTag) t.label()).setTag(posStr);
      }

      return t;
    }

    List<Tree> kids = new ArrayList<>();
    for(Node childNode = eRoot.getFirstChild(); childNode != null; childNode = childNode.getNextSibling()) {
      if(childNode.getNodeType() != Node.ELEMENT_NODE) continue;
      Tree t = getTreeFromXML(childNode);
      if(t == null) {
        System.err.printf("%s: Discarding empty tree (root: %s)%n", this.getClass().getName(),childNode.getNodeName());
      } else {
        kids.add(t);
      }
    }

    // MWEs have a label with a
    String rootLabel = eRoot.getNodeName().trim();
    boolean isMWE = rootLabel.equals("w") && eRoot.hasAttribute(ATTR_POS);
    if(isMWE)
      rootLabel = eRoot.getAttribute(ATTR_POS).trim();

    Tree t = (kids.size() == 0) ? null : treeFactory.newTreeNode(treeNormalizer.normalizeNonterminal(rootLabel), kids);

    if(t != null && isMWE)
      t = postProcessMWE(t);

    return t;
  }


  private Tree postProcessMWE(Tree t) {
    String tYield = SentenceUtils.listToString(t.yield()).replaceAll("\\s+", "");
    if(tYield.matches("[\\d\\p{Punct}]*")) {
      List<Tree> kids = new ArrayList<>();
      kids.add(treeFactory.newLeaf(tYield));
      t = treeFactory.newTreeNode(t.value(), kids);
    } else {
      t.setValue(MWE_PHRASAL + t.value());
    }
    return t;
  }


  /**
   * For debugging.
   *
   * @param args
   */
  public static void main(String[] args) {
    if(args.length < 1) {
      System.err.printf("Usage: java %s tree_file(s)%n%n",FrenchXMLTreeReader.class.getName());
      System.exit(-1);
    }

    List<File> fileList = new ArrayList<>();
    for (String arg : args) fileList.add(new File(arg));

    TreeReaderFactory trf = new FrenchXMLTreeReaderFactory(false);
    int totalTrees = 0;
    Set<String> morphAnalyses = Generics.newHashSet();
    try {
      for(File file : fileList) {
        TreeReader tr = trf.newTreeReader(new BufferedReader(new InputStreamReader(new FileInputStream(file),"UTF-8")));

        Tree t;
        int numTrees;
        String canonicalFileName = file.getName().substring(0, file.getName().lastIndexOf('.'));

        for(numTrees = 0; (t = tr.readTree()) != null; numTrees++) {
          String ftbID = ((CoreLabel) t.label()).get(CoreAnnotations.SentenceIDAnnotation.class);
          System.out.printf("%s-%s\t%s%n",canonicalFileName, ftbID, t.toString());
          List<Label> leaves = t.yield();
          for(Label label : leaves) {
            if(label instanceof CoreLabel)
              morphAnalyses.add(((CoreLabel) label).originalText());
          }
        }

        tr.close();
        System.err.printf("%s: %d trees%n",file.getName(),numTrees);
        totalTrees += numTrees;
      }

//wsg2011: Print out the observed morphological analyses
//      for(String analysis : morphAnalyses)
//        log.info(analysis);

      System.err.printf("%nRead %d trees%n",totalTrees);

    } catch (FileNotFoundException e) {
      e.printStackTrace();

    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
