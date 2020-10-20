package edu.stanford.nlp.semgraph.semgrex.ssurgeon;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.text.DateFormat;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.logging.RedwoodConfiguration;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphUtils;
import edu.stanford.nlp.semgraph.semgrex.ssurgeon.pred.*;
import edu.stanford.nlp.semgraph.semgrex.SemgrexPattern;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.logging.Redwood;


/**
 * This is the primary class for loading and saving out Ssurgeon patterns.
 * This is also the class that maintains the current list of resources loaded into Ssurgeon: any pattern
 * loaded can reference these resources.
 *
 * @author Eric Yeh
 */
public class Ssurgeon  {

  private static final boolean VERBOSE = false;

  // singleton, to ensure all use the same resources
  private static Ssurgeon instance = null;

  private Ssurgeon() {}

  public static Ssurgeon inst() {
    synchronized(Ssurgeon.class) {
      if (instance == null)
        instance = new Ssurgeon();
    }
    return instance;
  }

  // Logging to file facilities.
  // The prefix is used to append stuff in front of the logging messages
  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(Ssurgeon.class);

  private String logPrefix = null;
  public void initLog(File logFilePath) throws IOException {
    RedwoodConfiguration.empty()
      .handlers(RedwoodConfiguration.Handlers.chain(
        RedwoodConfiguration.Handlers.showAllChannels(), RedwoodConfiguration.Handlers.stderr),
        RedwoodConfiguration.Handlers.file(logFilePath.toString())
      ).apply();
    // fh.setFormatter(new NewlineLogFormatter());

    System.out.println("Starting Ssurgeon log, at "+logFilePath.getAbsolutePath()+" date=" + DateFormat.getDateInstance(DateFormat.FULL).format(new Date()));
    log.info("Starting Ssurgeon log, date=" + DateFormat.getDateInstance(DateFormat.FULL).format(new Date()));
  }

  public void setLogPrefix(String logPrefix) {
    this.logPrefix = logPrefix;
  }



  /**
   * Given a list of SsurgeonPattern edit scripts, and a SemanticGraph
   * to operate over, returns a list of expansions of that graph, with
   * the result of each edit applied against a copy of the graph.
   */
  public  List<SemanticGraph> expandFromPatterns(List<SsurgeonPattern> patternList, SemanticGraph sg) throws Exception {
    List<SemanticGraph> retList = new ArrayList<>();
    for (SsurgeonPattern pattern :patternList) {
      Collection<SemanticGraph> generated = pattern.execute(sg);
      for (SemanticGraph orderedGraph : generated) {
        //orderedGraph.vertexList(true);
        //orderedGraph.edgeList(true);
        retList.add(orderedGraph);
        System.out.println("\ncompact = "+orderedGraph.toCompactString());
        System.out.println("regular=" + orderedGraph);
      }

      if (generated.size() > 0) {
        if (log != null) {
          log.info("* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *");
          log.info("Pre remove duplicates, num="+generated.size());
        }
        SemanticGraphUtils.removeDuplicates(generated, sg);
        if (log != null) {
          log.info("Expand from patterns");
          if (logPrefix != null) log.info(logPrefix);
          log.info("Pattern = '"+pattern.getUID()+"' generated "+generated.size()+" matches");
          log.info("= = = = = = = = = =\nSrc graph:\n" + sg + "\n= = = = = = = = = =\n");
          int index=1;
          for (SemanticGraph genSg : generated) {
            log.info("REWRITE "+(index++));
            log.info(genSg.toString());
            log.info(". . . . .\n");
          }
        }
      }
    }
    return retList;
  }

  /**
   * Similar to the expandFromPatterns, but performs an exhaustive
   * search, performing simplifications on the graphs until exhausted.
   *
   * TODO: ensure cycles do not occur
   * NOTE: put in an arbitrary depth limit of 3, to prevent churning way too much (heuristic)
   *
   */
  public  Collection<SemanticGraph> exhaustFromPatterns(List<SsurgeonPattern> patternList, SemanticGraph sg) throws Exception {
    Collection<SemanticGraph> generated = exhaustFromPatterns(patternList, sg, 1);
    if (generated.size() > 1) {
      if (log != null)
        log.info("Before remove dupe, size="+generated.size());
      generated = SemanticGraphUtils.removeDuplicates(generated, sg);
      if (log != null)
        log.info("AFTER remove dupe, size="+generated.size());
    }
    return generated;
  }
  private  List<SemanticGraph> exhaustFromPatterns(List<SsurgeonPattern> patternList, SemanticGraph sg, int depth) throws Exception {
    List<SemanticGraph> retList = new ArrayList<>();
    for (SsurgeonPattern pattern : patternList) {
      Collection<SemanticGraph> generated = pattern.execute(sg);
      for (SemanticGraph modGraph : generated) {
        //modGraph = SemanticGraphUtils.resetVerticeOrdering(modGraph);
        //modGraph.vertexList(true);
        //modGraph.edgeList(true);
        retList.add(modGraph);
      }

      if (log != null && generated.size() > 0) {
        log.info("* * * * * * * * * ** * * * * * * * * *");
        log.info("Exhaust from patterns, depth="+depth);
        if (logPrefix != null) log.info(logPrefix);
        log.info("Pattern = '"+pattern.getUID()+"' generated "+generated.size()+" matches");
        log.info("= = = = = = = = = =\nSrc graph:\n"+sg.toString()+"\n= = = = = = = = = =\n");
        int index=1;
        for (SemanticGraph genSg : generated) {
          log.info("REWRITE "+(index++));
          log.info(genSg.toString());
          log.info(". . . . .\n");
        }
      }
    }

    if (retList.size() > 0) {
      List<SemanticGraph> referenceList = new ArrayList<>(retList);
      for (SemanticGraph childGraph : referenceList) {
        if (depth < 3)
          retList.addAll(exhaustFromPatterns(patternList, childGraph, depth + 1));
      }
    }
    return retList;
  }


  /**
   * Given a path to a file, converts it into a SsurgeonPattern
   * TODO: finish implementing this stub.
   */
  public static SsurgeonPattern getOperationFromFile(String path) {
    return null;
  }

  //
  // Resource management
  //
  private Map<String, SsurgeonWordlist> wordListResources = Generics.newHashMap();

  /**
   * Places the given word list resource under the given ID.
   * Note: can overwrite existing one in place.
   */
  private void addResource(SsurgeonWordlist resource) {
    wordListResources.put(resource.getID(), resource);
  }

  /**
   * Returns the given resource with the id.
   * If does not exist, will throw exception.
   */
  public SsurgeonWordlist getResource(String id) {
    return wordListResources.get(id);
  }

  public Collection<SsurgeonWordlist> getResources() {
    return wordListResources.values();
  }


  public static final String GOV_NODENAME_ARG = "-gov";
  public static final String DEP_NODENAME_ARG = "-dep";
  public static final String EDGE_NAME_ARG = "-edge";
  public static final String NODENAME_ARG = "-node";
  public static final String RELN_ARG = "-reln";
  public static final String NODE_PROTO_ARG = "-nodearg";
  public static final String WEIGHT_ARG = "-weight";
  public static final String NAME_ARG = "-name";


  // args for Ssurgeon edits, allowing us to not
  // worry about arg order (and to make things appear less confusing)
  protected static class SsurgeonArgs {
    // Below are values keyed by Semgrex name
    public String govNodeName = null;

    public String dep = null;

    public String edge = null;

    public String reln = null;

    public String node = null;

    // below are string representations of the intended values
    public String nodeString = null;

    public double weight = 1.0;

    public String name = null;
  }

  /**
   * This is a specialized args parser, as we want to split on
   * whitespace, but retain everything inside quotes, so we can pass
   * in hashmaps in String form.
   */
  private static String[] parseArgs(String argsString) {
    List<String> retList = new ArrayList<>();
    String patternString = "(?:[^\\s\\\"]++|\\\"[^\\\"]*+\\\"|(\\\"))++";
    Pattern pattern = Pattern.compile(patternString);
    Matcher matcher = pattern.matcher(argsString);
    while (matcher.find()) {
      if (matcher.group(1) == null) {
        String matched = matcher.group();
        if (matched.charAt(0) == '"' &&
            matched.charAt(matched.length()-1) == '"')
          retList.add(matched.substring(1, matched.length()-1));
        else
          retList.add(matched);
      }  else
        throw new IllegalArgumentException("Unmatched quote in string to parse");
    }
    return retList.toArray(StringUtils.EMPTY_STRING_ARRAY);
  }

  /**
   * Given a string entry, converts it into a SsurgeonEdit object.
   */
  public static SsurgeonEdit parseEditLine(String editLine) {
    // Extract the operation name first
    String[] tuples1 = editLine.split("\\s+", 2);
    if (tuples1.length < 2) {
      throw new IllegalArgumentException("Error in SsurgeonEdit.parseEditLine: invalid number of arguments");
    }
    String command = tuples1[0];
    String[] argsArray = parseArgs(tuples1[1]);
    SsurgeonArgs argsBox = new SsurgeonArgs();

    for (int argIndex = 0; argIndex < argsArray.length; ++argIndex) {
      switch (argsArray[argIndex]) {
        case GOV_NODENAME_ARG:
          argsBox.govNodeName = argsArray[argIndex + 1];
          argIndex += 2;
          break;
        case DEP_NODENAME_ARG:
          argsBox.dep = argsArray[argIndex + 1];
          argIndex += 2;
          break;
        case EDGE_NAME_ARG:
          argsBox.edge = argsArray[argIndex + 1];
          argIndex += 2;
          break;
        case RELN_ARG:
          argsBox.reln = argsArray[argIndex + 1];
          argIndex += 2;
          break;
        case NODENAME_ARG:
          argsBox.node = argsArray[argIndex + 1];
          argIndex += 2;
          break;
        case NODE_PROTO_ARG:
          argsBox.nodeString = argsArray[argIndex + 1];
          argIndex += 2;
          break;
        case WEIGHT_ARG:
          argsBox.weight = Double.valueOf(argsArray[argIndex + 1]);
          argIndex += 2;
          break;
        case NAME_ARG:
          argsBox.name = argsArray[argIndex + 1];
          argIndex += 2;
          break;
        default:
          throw new IllegalArgumentException("Parsing Ssurgeon args: unknown flag " + argsArray[argIndex]);
      }
    }


    // Parse the arguments based upon the type of command to execute.
    // TODO: this logic really should be moved into the individual classes.  The string-->class
    // mappings should also be stored in more appropriate data structure.
    SsurgeonEdit retEdit;
    if (command.equalsIgnoreCase(AddDep.LABEL)) {
      retEdit = AddDep.createEngAddDep(argsBox.govNodeName, argsBox.reln, argsBox.nodeString);
    } else if (command.equalsIgnoreCase(AddNode.LABEL)) {
      retEdit = AddNode.createAddNode(argsBox.nodeString, argsBox.name);
    } else if (command.equalsIgnoreCase(AddEdge.LABEL)) {
      retEdit = AddEdge.createEngAddEdge(argsBox.govNodeName, argsBox.dep, argsBox.reln);
    } else if (command.equalsIgnoreCase(DeleteGraphFromNode.LABEL)) {
      retEdit = new DeleteGraphFromNode(argsBox.node);
    } else if (command.equalsIgnoreCase(RemoveEdge.LABEL)) {
      retEdit = new RemoveEdge(GrammaticalRelation.valueOf(argsBox.reln), argsBox.govNodeName, argsBox.dep);
    } else if (command.equalsIgnoreCase(RemoveNamedEdge.LABEL)) {
      retEdit = new RemoveNamedEdge(argsBox.edge, argsBox.govNodeName, argsBox.dep);
    } else if (command.equalsIgnoreCase(SetRoots.LABEL)) {
      String[] names = tuples1[1].split("\\s+");
      List<String> newRoots = Arrays.asList(names);
      retEdit = new SetRoots(newRoots);
    } else if (command.equalsIgnoreCase(KillNonRootedNodes.LABEL)) {
      retEdit = new KillNonRootedNodes();
    } else if (command.equalsIgnoreCase(KillAllIncomingEdges.LABEL)) {
      retEdit = new KillAllIncomingEdges(argsBox.node);
    } else {
      throw new IllegalArgumentException("Error in SsurgeonEdit.parseEditLine: command '"+command+"' is not supported");
    }
    return retEdit;
  }

  //public static SsurgeonPattern fromXML(String xmlString) throws Exception {
  //SAXBuilder builder = new SAXBuilder();
  //Document jdomDoc = builder.build(xmlString);
  //jdomDoc.getRootElement().getChildren(SsurgeonPattern.SSURGEON_ELEM_TAG);
  //}

  /**
   * Given a target filepath and a list of Ssurgeon patterns, writes them out as XML forms.
   */
  public static void writeToFile(File tgtFile, List<SsurgeonPattern> patterns) {
    try {
      Document domDoc = createPatternXMLDoc(patterns);
      if (domDoc != null) {
        Transformer tformer = TransformerFactory.newInstance().newTransformer();
        tformer.setOutputProperty(OutputKeys.INDENT, "yes");
        tformer.transform(new DOMSource(domDoc), new StreamResult(tgtFile));
      } else {
        log.warning("Was not able to create XML document for pattern list, file not written.");
      }
    } catch (Exception e) {
      log.error(Ssurgeon.class.getName(), "writeToFile");
      log.error(e);
    }
  }

  public static String writeToString(SsurgeonPattern pattern) {
    try {
      List<SsurgeonPattern> patterns = new LinkedList<>();
      patterns.add(pattern);
      Document domDoc = createPatternXMLDoc(patterns);
      if (domDoc != null) {
        Transformer tformer = TransformerFactory.newInstance().newTransformer();
        tformer.setOutputProperty(OutputKeys.INDENT, "yes");
        StringWriter sw = new StringWriter();
        tformer.transform(new DOMSource(domDoc), new StreamResult(sw));
        return sw.toString();
      } else {
        log.warning("Was not able to create XML document for pattern list.");
      }
    } catch (Exception e) {
      log.info("Error in writeToString, could not process pattern="+pattern);
      log.info(e);
      return null;
    }
    return "";
  }


  private static Document createPatternXMLDoc(List<SsurgeonPattern> patterns) {
    try {
      DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
      DocumentBuilder db = dbf.newDocumentBuilder();
      Document domDoc = db.newDocument();
      Element rootElt = domDoc.createElement(SsurgeonPattern.ELT_LIST_TAG);
      domDoc.appendChild(rootElt);
      int ordinal = 1;
      for (SsurgeonPattern pattern : patterns) {
        Element patElt = domDoc.createElement(SsurgeonPattern.SSURGEON_ELEM_TAG);
        patElt.setAttribute(SsurgeonPattern.ORDINAL_ATTR, String.valueOf(ordinal));
        Element semgrexElt = domDoc.createElement(SsurgeonPattern.SEMGREX_ELEM_TAG);
        semgrexElt.appendChild(domDoc.createTextNode(pattern.getSemgrexPattern().pattern()));
        patElt.appendChild(semgrexElt);
        Element uidElem = domDoc.createElement(SsurgeonPattern.UID_ELEM_TAG);
        uidElem.appendChild(domDoc.createTextNode(pattern.getUID()));
        patElt.appendChild(uidElem);
        Element notesElem = domDoc.createElement(SsurgeonPattern.NOTES_ELEM_TAG);
        notesElem.appendChild(domDoc.createTextNode(pattern.getNotes()));
        patElt.appendChild(notesElem);

        SemanticGraph semgrexGraph = pattern.getSemgrexGraph();
        if (semgrexGraph != null) {
          Element patNode = domDoc.createElement(SsurgeonPattern.SEMGREX_GRAPH_ELEM_TAG);
          patNode.appendChild(domDoc.createTextNode(semgrexGraph.toCompactString()));
        }
        Element editList = domDoc.createElement(SsurgeonPattern.EDIT_LIST_ELEM_TAG);
        patElt.appendChild(editList);
        int editOrdinal = 1;
        for (SsurgeonEdit edit : pattern.getEditScript()) {
          Element editElem = domDoc.createElement(SsurgeonPattern.EDIT_ELEM_TAG);
          editElem.setAttribute(SsurgeonPattern.ORDINAL_ATTR, String.valueOf(editOrdinal));
          editElem.appendChild(domDoc.createTextNode(edit.toEditString()));
          editList.appendChild(editElem);
          editOrdinal++;
        }
        rootElt.appendChild(patElt);
        ordinal++;
      }
      return domDoc;
    } catch (Exception e) {
      log.error(Ssurgeon.class.getName(), "createPatternXML");
      log.error(e);
      return null;
    }
  }


  /**
   * Given a path to a file containing a list of SsurgeonPatterns, returns
   *
   * TODO: deal with resources
   * @throws Exception
   */
  @SuppressWarnings("unchecked")
  public List<SsurgeonPattern> readFromFile(File file) throws Exception {
    List<SsurgeonPattern> retList = new ArrayList<>();
    Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file);

    if (VERBOSE)
      System.out.println("Reading ssurgeon file="+file.getAbsolutePath());

    NodeList patternNodes = doc.getElementsByTagName(SsurgeonPattern.SSURGEON_ELEM_TAG);
    for (int i=0; i<patternNodes.getLength(); i++) {
      Node node = patternNodes.item(i);
      if (node.getNodeType() == Node.ELEMENT_NODE) {
        Element elt = (Element) node;
        SsurgeonPattern pattern = ssurgeonPatternFromXML(elt);
        retList.add(pattern);
      }
    }

    NodeList resourceNodes = doc.getElementsByTagName(SsurgeonPattern.RESOURCE_TAG);
    for (int i=0; i < resourceNodes.getLength(); i++) {
      Node node = patternNodes.item(i);
      if (node.getNodeType() == Node.ELEMENT_NODE) {
        Element resourceElt = (Element) node;
        SsurgeonWordlist wlRsrc = new SsurgeonWordlist(resourceElt);
        addResource(wlRsrc);
      }
    }

    return retList;
  }

  /**
   * Reads all Ssurgeon patterns from file.
   * @throws Exception
   */
  public List<SsurgeonPattern> readFromDirectory(File dir) throws Exception {
    if (!dir.isDirectory()) throw new Exception("Given path not a directory, path="+dir.getAbsolutePath());
    if (VERBOSE)
      System.out.println("Reading Ssurgeon patterns from directory = "+dir.getAbsolutePath());
    File[] files = dir.listFiles((dir1, name) -> name.toLowerCase().endsWith(".xml"));
    List<SsurgeonPattern> patterns = new ArrayList<>();
    for (File file : files) {
      try {
        patterns.addAll(readFromFile(file));
      } catch (Exception e) {
        log.error(e);
      }
    }
    return patterns;
  }

  /**
   * Given the root Element for a SemgrexPattern (SSURGEON_ELEM_TAG), converts
   * it into its corresponding SemgrexPattern object.
   * @throws Exception
   */
  @SuppressWarnings("unchecked")
  public static SsurgeonPattern ssurgeonPatternFromXML(Element elt) throws Exception {
    String uid = getTagText(elt, SsurgeonPattern.UID_ELEM_TAG);
    String notes = getTagText(elt, SsurgeonPattern.NOTES_ELEM_TAG);
    String semgrexString = getTagText(elt, SsurgeonPattern.SEMGREX_ELEM_TAG);
    SemgrexPattern semgrexPattern = SemgrexPattern.compile(semgrexString);
    SsurgeonPattern retPattern = new SsurgeonPattern(uid, semgrexPattern);
    retPattern.setNotes(notes);
    NodeList editNodes = elt.getElementsByTagName(SsurgeonPattern.EDIT_LIST_ELEM_TAG);
    for (int i=0; i<editNodes.getLength(); i++) {
      Node node = editNodes.item(i);
      if (node.getNodeType() == Node.ELEMENT_NODE) {
        Element editElt = (Element) node;
        String editVal = getEltText(editElt);
        retPattern.addEdit(Ssurgeon.parseEditLine(editVal));
      }
    }


    // If predicate available, parse
    Element predElt = getFirstTag(elt, SsurgeonPattern.PREDICATE_TAG);
    if (predElt != null) {
      SsurgPred pred = assemblePredFromXML(getFirstChildElement(predElt));
      retPattern.setPredicate(pred);
    }
    return retPattern;
  }

  /**
   * Constructs a {@code SsurgPred} structure from file, given the root element.
   * @throws Exception
   */
  public static SsurgPred assemblePredFromXML(Element elt) throws Exception {
    String eltName = elt.getTagName();
    switch (eltName) {
      case SsurgeonPattern.PREDICATE_AND_TAG:
        SsurgAndPred andPred = new SsurgAndPred();
        for (Element childElt : getChildElements(elt)) {
          SsurgPred childPred = assemblePredFromXML(childElt);
          andPred.add(childPred);
          return andPred;
        }
        break;
      case SsurgeonPattern.PREDICATE_OR_TAG:
        SsurgOrPred orPred = new SsurgOrPred();
        for (Element childElt : getChildElements(elt)) {
          SsurgPred childPred = assemblePredFromXML(childElt);
          orPred.add(childPred);
          return orPred;
        }
        break;
      case SsurgeonPattern.PRED_WORDLIST_TEST_TAG:
        String id = elt.getAttribute(SsurgeonPattern.PRED_ID_ATTR);
        String resourceID = elt.getAttribute("resourceID");
        String typeStr = elt.getAttribute("type");
        String matchName = getEltText(elt).trim(); // node name to match on

        if (matchName == null) {
          throw new Exception("Could not find match name for " + elt);
        }
        if (id == null) {
          throw new Exception("No ID attribute for element = " + elt);
        }
        return new WordlistTest(id, resourceID, typeStr, matchName);
    }

    // Not a valid node, error out!
    throw new Exception("Invalid node encountered during Ssurgeon predicate processing, node name="+eltName);
  }



  /**
   * Reads in the test file and prints readable to string (for debugging).
   * Input file consists of semantic graphs, in compact form.
   */
  public void testRead(File tgtDirPath) throws Exception {
    List<SsurgeonPattern> patterns = readFromDirectory(tgtDirPath);

    System.out.println("Patterns, num = "+patterns.size());
    int num = 1;
    for (SsurgeonPattern pattern : patterns) {
      System.out.println("\n# "+(num++));
      System.out.println(pattern);
    }

    System.out.println("\n\nRESOURCES ");
    for (SsurgeonWordlist rsrc : inst().getResources()) {
      System.out.println(rsrc+"* * * * *");
    }

    BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
    boolean runFlag = true;
    Ssurgeon.inst().initLog(new File("./ssurgeon_run.log"));
    while (runFlag) {
      try {
        System.out.println("Enter a sentence:");
        String line = in.readLine();
        if (line.isEmpty()) {
          System.exit(0);
        }
        System.out.println("Parsing...");
        SemanticGraph sg = SemanticGraph.valueOf(line);
        System.out.println("Graph = "+sg);
        Collection<SemanticGraph> generated = Ssurgeon.inst().exhaustFromPatterns(patterns, sg);
        System.out.println("# generated = "+generated.size());
        int index = 1;
        for (SemanticGraph gsg : generated) {
          System.out.println("\n# "+index);
          System.out.println(gsg);
          index++;
        }
      } catch (Exception e) {
        log.error(e);
      }
    }
  }


  /*
   * XML convenience routines
   */
  // todo [cdm 2016]: Aren't some of these methods available as generic XML methods elsewhere??

  /**
   * For the given element, returns the text for the first child Element with
   * the given tag.
   */
  public static String getTagText(Element element, String tag) {
    try {
      // From root element, identify first with tag, then find the
      // first child under that, which we treat as a TEXT node.
      Element firstElt = getFirstTag(element, tag);
      if (firstElt == null) return "";
      return getEltText(firstElt);
    } catch (Exception e) {
      log.warning("Exception thrown attempting to get tag text for tag="+tag+", from element="+element);
    }
    return "";
  }

  /**
   * For a given Element, treats the first child as a text element
   * and returns its value.
   */
  public static String getEltText(Element element) {
    try {
      NodeList childNodeList = element.getChildNodes();
      if (childNodeList.getLength() == 0) return "";
      return childNodeList.item(0).getNodeValue();
    } catch (Exception e) {
      log.warning("Exception e=" + e.getMessage() + " thrown calling getEltText on element=" + element);
    }
    return "";
  }

  /**
   * For the given element, finds the first child Element with the given tag.
   */
  private static Element getFirstTag(Element element, String tag) {
    try {
      NodeList nodeList = element.getElementsByTagName(tag);
      if (nodeList.getLength() == 0) return null;
      for (int i=0; i < nodeList.getLength(); i++) {
        Node node = nodeList.item(i);
        if (node.getNodeType() == Node.ELEMENT_NODE)
          return (Element) node;
      }
    } catch (Exception e) {
      log.warning("Error getting first tag "+tag+" under element="+element);
    }
    return null;
  }

  /**
   * Returns the first child whose node type is Element under the given Element.
   */
  private static Element getFirstChildElement(Element element) {
    try {
      NodeList nodeList = element.getChildNodes();
      for (int i=0; i<nodeList.getLength(); i++) {
        Node node = nodeList.item(i);
        if (node.getNodeType() == Node.ELEMENT_NODE)
          return (Element) node;
      }
    } catch (Exception e) {
      log.warning("Error getting first child Element for element=" + element+", exception=" + e);
    }
    return null;
  }


  /**
   * Returns all of the Element typed children from the given element.  Note: disregards
   * other node types.
   */
  private static List<Element> getChildElements(Element element) {
    LinkedList<Element> childElements = new LinkedList<>();
    try {
      NodeList nodeList = element.getChildNodes();
      for (int i=0; i<nodeList.getLength(); i++) {
        Node node = nodeList.item(i);
        if (node.getNodeType() == Node.ELEMENT_NODE) {
          childElements.add((Element) node);
        }
      }
    } catch (Exception e) {
      log.warning("Exception thrown getting all children for element=" + element+ ", e=" + e);
    }
    return childElements;
  }

  /*
   * Main class evocation stuff
   */


  public enum RUNTYPE {
    interactive, // interactively test contents of pattern directory against entered sentences
    testinfo // test against a given infofile (RTE), generating rewrites for hypotheses
  }


  public static class ArgsBox {
    public RUNTYPE type = RUNTYPE.interactive;

    public String patternDirStr = null;
    public File patternDir = null;

    public String info = null;
    public File infoPath = null;

    public void init() {
      patternDir = new File(patternDirStr);
      if (type == RUNTYPE.testinfo)
        infoPath = new File(info);
    }

    @Override
    public String toString() {
      StringWriter buf = new StringWriter();
      buf.write("type ="+type+"\n");
      buf.write("pattern dir = "+patternDir.getAbsolutePath());
      if (type == RUNTYPE.testinfo) {
        buf.write("info file = "+info);
        if (info != null)
          buf.write(", path = "+infoPath.getAbsolutePath());
      }
      return buf.toString();
    }
  }

  protected static ArgsBox argsBox = new ArgsBox();

  /**
   * Performs a simple test and print of a given file.
   * Usage Ssurgeon [-info infoFile] -patterns patternDir [-type interactive|testinfo]
   */
  public static void main(String[] args) {
    for (int argIndex = 0; argIndex < args.length; ++argIndex) {
      if (args[argIndex].equalsIgnoreCase("-info")) {
        argsBox.info = args[argIndex + 1];
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-patterns")) {
        argsBox.patternDirStr = args[argIndex + 1];
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-type")) {
        argsBox.type = RUNTYPE.valueOf(args[argIndex + 1]);
        argIndex += 2;
      }
    }
    if (argsBox.patternDirStr == null) {
      throw new IllegalArgumentException("Need to give a pattern location with -patterns");
    }
    argsBox.init();

    System.out.println(argsBox);
    try {
      if (argsBox.type == RUNTYPE.interactive) {
        Ssurgeon.inst().testRead(argsBox.patternDir);
      }
    } catch (Exception e) {
      log.error(e);
    }
  }

}
