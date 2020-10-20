package edu.stanford.nlp.semgraph;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.StringUtils;

import java.util.*;

/**
 * Defines a class for pretty-printing SemanticGraphs.
 *
 * @author Bill MacCartney
 */
public class SemanticGraphFormatter {


  // named constants ------------------------------------------------------------

  private static final String LPAREN = "[";
  private static final String RPAREN = "]";
  private static final String SPACE = " ";
  private static final String COLON = ">";

  private static final int DEFAULT_WIDTH = 80;
  private static final int DEFAULT_INDENT = 4;
  private static final boolean DEFAULT_SMART_INDENT = true;
  private static final boolean DEFAULT_SHOW_RELNS = true;
  private static final boolean DEFAULT_SHOW_TAGS = true;
  private static final boolean DEFAULT_SHOW_ANNOS = false;
  private static final boolean DEFAULT_SHOW_INDICES = false;

  // member variables -----------------------------------------------------------

  private int width = DEFAULT_WIDTH;
  private int indent = DEFAULT_INDENT;
  private boolean smartIndent = DEFAULT_SMART_INDENT;
  private boolean showRelns = DEFAULT_SHOW_RELNS;
  private boolean showTags = DEFAULT_SHOW_TAGS;
  private boolean showAnnos = DEFAULT_SHOW_ANNOS;
  private boolean showIndices = DEFAULT_SHOW_INDICES;

  // working variables -- not thread-safe!!!
  private StringBuilder out;
  private Set<IndexedWord> used;


  // constructors ---------------------------------------------------------------

  public SemanticGraphFormatter() {
    this(DEFAULT_WIDTH,
         DEFAULT_INDENT,
         DEFAULT_SMART_INDENT,
         DEFAULT_SHOW_RELNS,
         DEFAULT_SHOW_TAGS,
         DEFAULT_SHOW_ANNOS,
         DEFAULT_SHOW_INDICES);
  }

  public SemanticGraphFormatter(int width,
                                int indent,
                                boolean smartIndent,
                                boolean showRelns,
                                boolean showTags,
                                boolean showAnnos,
                                boolean showIndices) {
    this.width = width;
    this.indent = indent;
    this.smartIndent = smartIndent;
    this.showRelns = showRelns;
    this.showTags = showTags;
    this.showAnnos = showAnnos;
    this.showIndices = showIndices;
  }


  // public method --------------------------------------------------------------

  /**
   * Returns a pretty-printed string representation of the given semantic graph,
   * on one or more lines.
   */
  public String formatSemanticGraph(SemanticGraph sg) {
    if (sg.vertexSet().isEmpty()) {
      return "[]";
    }
    out = new StringBuilder();           // not thread-safe!!!
    used = Generics.newHashSet();
    if (sg.getRoots().size() == 1) {
      formatSGNode(sg, sg.getFirstRoot(), 1);
    } else {
      int index = 0;
      for (IndexedWord root: sg.getRoots()) {
        index+=1;
        out.append("root_").append(index).append("> ");
        formatSGNode(sg, root, 9);
        out.append("\n");
      }
    }
    String result = out.toString();
    if (!result.startsWith("[")) {
      result = "[" + result + "]";
    }
    return result;
  }


  // private methods ------------------------------------------------------------

  /**
   * Appends to this.out a one-line or multi-line string representation of the given
   * semantic graph, using the given number of spaces for indentation.
   */
  private void formatSGNode(SemanticGraph sg,
                            IndexedWord node,
                            int spaces) {
    used.add(node);
    String oneline = formatSGNodeOneline(sg, node);
    boolean toolong = (spaces + oneline.length() > width);
    boolean breakable = sg.hasChildren(node);
    if (toolong && breakable) {
      formatSGNodeMultiline(sg, node, spaces);
    } else {
      out.append(oneline);
    }
  }

  private String formatSGNodeOneline(SemanticGraph sg,
      IndexedWord node) {
    StringBuilder sb = new StringBuilder();
    Set<IndexedWord> usedOneline = Generics.newHashSet();
    formatSGNodeOnelineHelper(sg, node, sb, usedOneline);
    return sb.toString();
  }

  private void formatSGNodeOnelineHelper(SemanticGraph sg,
                                         IndexedWord node,
                                         StringBuilder sb,
                                         Set<IndexedWord> usedOneline) {
    usedOneline.add(node);
    boolean isntLeaf = (sg.outDegree(node) > 0);
    if (isntLeaf) {
      sb.append(LPAREN);
    }
    sb.append(formatLabel(node));
    for (SemanticGraphEdge depcy : sg.getOutEdgesSorted(node)) {
      IndexedWord dep = depcy.getDependent();
      sb.append(SPACE);
      if (showRelns) {
        sb.append(depcy.getRelation());
        sb.append(COLON);
      }
      if (!usedOneline.contains(dep) &&
          !used.contains(dep)) { // avoid infinite loop
        formatSGNodeOnelineHelper(sg, dep, sb, usedOneline);
      } else {
        sb.append(formatLabel(dep));
      }
    }
    if (isntLeaf) {
      sb.append(RPAREN);
    }
  }

  /**
   * Appends to this.out a multi-line string representation of the given
   * semantic graph, using the given number of spaces for indentation.

   * The semantic graph's label and each of its children appear on separate
   * lines.  A child may appear with a one-line or multi-line representation,
   * depending upon available space.
   */
  private void formatSGNodeMultiline(SemanticGraph sg,
                                     IndexedWord node,
                                     int spaces) {
    out.append(LPAREN);
    out.append(formatLabel(node));
    if (smartIndent) {
      spaces += 1;
    } else {
      spaces += indent;
    }
    for (SemanticGraphEdge depcy : sg.getOutEdgesSorted(node)) {
      IndexedWord dep = depcy.getDependent();
      out.append("\n");
      out.append(StringUtils.repeat(SPACE, spaces));
      int sp = spaces;
      if (showRelns) {
        String reln = depcy.getRelation().toString();
        out.append(reln);
        out.append(COLON);
        if (smartIndent) {
          sp += (reln.length() + 1);
        }
      }
      if (!used.contains(dep)) { // avoid infinite loop
        formatSGNode(sg, dep, sp);
      }
    }
    out.append(RPAREN);
  }

  private String formatLabel(IndexedWord node) {
    String s = node.word();
    if (showIndices) {
      s = node.sentIndex()+":"+node.index()+"-"+s;
    }
    if (showTags) {
      String tag = node.tag();
      if (tag != null && tag.length() > 0) {
        s += "/" + tag;
      }
    }
    if (showAnnos) {
      s += node.toString(CoreLabel.OutputFormat.MAP);
    }
    return s;
  }


  // testing -----------------------------------------------------------------------

  private void test(String s) {
    SemanticGraph sg = SemanticGraph.valueOf(s);
    System.out.println(sg.toCompactString());
    System.out.println(formatSemanticGraph(sg));
    System.out.println();
  }

  public static void main(String[] args) {

    SemanticGraphFormatter fmt = new SemanticGraphFormatter();

    System.out.println("0        1         2         3         4         5         6         7         8");
    System.out.println("12345678901234567890123456789012345678901234567890123456789012345678901234567890");
    System.out.println();

    fmt.test("[like subj>Bill obj>[muffins compound>blueberrry]]");

    fmt.test("[eligible nsubj>Zambia cop>became xcomp>[receive mark>to obj>[assistance amod>UNCDF] nmod:in>1991]]");

    fmt.test("[say advcl>[are mark>If nsubj>[polls det>the] xcomp>[believed aux>to auxpass>be]] nsubj>[voters amod>American] aux>will advmod>[much dep>[same det>the]] nmod:to>[Republicans nmod:poss>[Bush case>'s compound>George] case>to] dep>[vote advmod>when nsubj>they nmod:in>[elections amod>congressional det>the case>in] nmod:on>[[November num>7th case>on]]]]");
  }

}


