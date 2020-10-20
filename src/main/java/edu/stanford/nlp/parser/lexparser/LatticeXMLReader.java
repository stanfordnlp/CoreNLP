package edu.stanford.nlp.parser.lexparser; 
import edu.stanford.nlp.util.logging.Redwood;

import java.io.*;
import java.util.*;

import javax.xml.parsers.DocumentBuilder;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import edu.stanford.nlp.parser.common.ParserConstraint;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.XMLUtils;

public class LatticeXMLReader implements Iterable<Lattice>  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(LatticeXMLReader.class);

//	private static final String ROOT = "sentences";
  public static final String SENTENCE = "sentence";
  public static final String NODE = "node";
  public static final String NODE_ID = "id";
  public static final String EDGE = "edge";
  public static final String FROM_NODE = "from";
  public static final String TO_NODE = "to";
  public static final String SEGMENT = "label";
  public static final String WEIGHT = "wt";
  public static final String E_ATTR_NODE = "attribute";
  public static final String E_ATTR = "attr";
  public static final String E_ATTR_VAL = "value";

  // This *must* be the same as the offset in lattice-gen.py
  private static final int NODE_OFFSET = 100;

  private List<Lattice> lattices;

  public LatticeXMLReader() {
    lattices = new ArrayList<>();
  }

  public Iterator<Lattice> iterator() { return lattices.iterator(); }

  public int getNumLattices() { return lattices.size(); }

  @SuppressWarnings("unchecked")
  private boolean load(ObjectInputStream os) {
    try {
      lattices = (List<Lattice>) os.readObject();
    } catch (IOException e) {
      e.printStackTrace();
      return false;

    } catch (ClassNotFoundException e) {
      e.printStackTrace();
      return false;
    }
    return true;
  }

  public boolean load(InputStream stream, boolean isObject) {
    if(isObject) {
      ObjectInputStream os = (ObjectInputStream) stream;
      return load(os);
    } else
      return load(stream);
  }

  private boolean load(InputStream stream) {
    DocumentBuilder parser = XMLUtils.getXmlParser();
    if(parser == null) return false;

    try {
      Document xmlDocument = parser.parse(stream);

      Element root = xmlDocument.getDocumentElement();
      NodeList sentences = root.getElementsByTagName(SENTENCE);
      for(int i = 0; i < sentences.getLength(); i++) {
        Element sentence = (Element) sentences.item(i);
        Lattice lattice = new Lattice();

        //Create the node map
        SortedSet<Integer> nodes = new TreeSet<>();
        NodeList xmlNodes = sentence.getElementsByTagName(NODE);
        for(int nodeIdx = 0; nodeIdx < xmlNodes.getLength(); nodeIdx++) {
          Element xmlNode = (Element) xmlNodes.item(nodeIdx);
          int nodeName = Integer.parseInt(xmlNode.getAttribute(NODE_ID));
          nodes.add(nodeName);
        }

        Map<Integer,Integer> nodeMap = Generics.newHashMap();
        int realNodeIdx = 0;
        int lastBoundaryNode = -1;
        for(int nodeName : nodes) {
          if(lastBoundaryNode == -1) {
            assert nodeName % NODE_OFFSET == 0;
            lastBoundaryNode = realNodeIdx;
          } else if(nodeName % NODE_OFFSET == 0) {
            ParserConstraint c = new ParserConstraint(lastBoundaryNode, realNodeIdx, ".*");
            lattice.addConstraint(c);
          }

          nodeMap.put(nodeName, realNodeIdx);
          realNodeIdx++;
        }

        //Read the edges
        NodeList xmlEdges = sentence.getElementsByTagName(EDGE);
        for(int edgeIdx = 0; edgeIdx < xmlEdges.getLength(); edgeIdx++) {
          Element xmlEdge = (Element) xmlEdges.item(edgeIdx);

          String segment = xmlEdge.getAttribute(SEGMENT);
          double weight = Double.parseDouble(xmlEdge.getAttribute(WEIGHT)); //Input weights should be log scale

          int from = Integer.parseInt(xmlEdge.getAttribute(FROM_NODE));
          int normFrom = nodeMap.get(from);

          int to = Integer.parseInt(xmlEdge.getAttribute(TO_NODE));
          int normTo = nodeMap.get(to);

          LatticeEdge e = new LatticeEdge(segment,weight,normFrom,normTo);

          // Set attributes below here
          NodeList xmlAttrs = xmlEdge.getElementsByTagName(E_ATTR_NODE);
          for(int attrIdx = 0; attrIdx < xmlAttrs.getLength(); attrIdx++) {
            Element xmlAttr = (Element) xmlAttrs.item(attrIdx);
            String key = xmlAttr.getAttribute(E_ATTR);
            String value = xmlAttr.getAttribute(E_ATTR_VAL);
            e.setAttr(key, value);
          }

          lattice.addEdge(e);
        }

        //Configure for parsing in ExhaustivePCFG parser
        lattice.addBoundary();

        lattices.add(lattice);
      }

    } catch (IOException e) {
      System.err.printf("%s: Error reading XML from input stream.%n", this.getClass().getName());
      e.printStackTrace();
      return false;

    } catch (SAXException e) {
      e.printStackTrace();
      return false;
    }

    return true;
  }

  /**
   */
  public static void main(String[] args) {
    LatticeXMLReader reader = new LatticeXMLReader();
    try {
      System.setIn(new FileInputStream(args[0]));
    } catch (FileNotFoundException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }

    reader.load(System.in);

    int numLattices = 0;
    for(Lattice lattice : reader) {
      System.out.println(lattice.toString());
      numLattices++;
    }
    System.out.printf("\nLoaded %d lattices\n", numLattices);
  }
}
