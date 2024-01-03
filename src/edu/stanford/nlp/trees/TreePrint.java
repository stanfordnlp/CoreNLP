package edu.stanford.nlp.trees;

import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.process.PTBTokenizer;
import edu.stanford.nlp.trees.international.pennchinese.ChineseEnglishWordMap;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.util.XMLUtils;
import edu.stanford.nlp.util.logging.Redwood;

import java.io.*;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;


/**
 * A class for customizing the print method(s) for a
 * {@code edu.stanford.nlp.trees.Tree} as the output of the
 * parser.  This class supports printing in multiple ways and altering
 * behavior via properties specified at construction.
 *
 * @author Roger Levy
 * @author Christopher Manning
 * @author Galen Andrew
 */
public class TreePrint  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(TreePrint.class);

  // TODO: Add support for makeCopulaHead as an outputFormatOption here.

  public static final String rootLabelOnlyFormat = "rootSymbolOnly";
  public static final String headMark = "=H";

  /** The legal output tree formats. */
  public static final String[] outputTreeFormats = {
    "penn",
    "oneline",
    rootLabelOnlyFormat,
    "words",
    "wordsAndTags",
    "dependencies",
    "typedDependencies",
    "typedDependenciesCollapsed",
    "latexTree",
    "xmlTree",
    "collocations",
    "semanticGraph",
    "conllStyleDependencies",
    "conll2007"
  };

  private final LinkedHashMap<String, String> formats;
  private final LinkedHashMap<String, String> options;

  private final boolean markHeadNodes; // = false;
  private final boolean lexicalize; // = false;
  private final boolean removeEmpty;
  private final boolean ptb2text;
  private final boolean transChinese; // = false;
  private final boolean basicDependencies;
  private final boolean collapsedDependencies;
  private final boolean nonCollapsedDependencies;
  private final boolean nonCollapsedDependenciesSeparated;
  private final boolean CCPropagatedDependencies;
  private final boolean treeDependencies;

  private final boolean includeTags;

  private final HeadFinder hf;
  private final TreebankLanguagePack tlp;
  private final WordStemmer stemmer;
  private final Predicate<Dependency<Label, Label, Object>> dependencyFilter;
  private final Predicate<Dependency<Label, Label, Object>> dependencyWordFilter;
  private final GrammaticalStructureFactory gsf;

  /** Pool use of one WordNetConnection.  I don't really know if
   *  Dan Bikel's WordNet code is thread safe, but it definitely doesn't
   *  close its files, and too much of our code makes TreePrint objects and
   *  then drops them on the floor, and so we run out of file handles.
   *  That is, if this variable isn't static, code crashes.
   *  Maybe we should change this code to use jwnl(x)?
   *  CDM July 2006.
   */
  private static WordNetConnection wnc;


  /** This PrintWriter is used iff the user doesn't pass one in to a
   *  call to printTree().  It prints to System.out.
   */
  private final PrintWriter pw = new PrintWriter(System.out, true);


  /** Construct a new TreePrint that will print the given formats.
   *  Warning! This is the anglocentric constructor.
   *  It will work correctly only for English.
   *
   *  @param formats The formats to print the tree in.
   */
  public TreePrint(String formats) {
    this(formats, "", new PennTreebankLanguagePack());
  }

  /** Make a TreePrint instance with no options specified. */
  public TreePrint(String formats, TreebankLanguagePack tlp) {
    this(formats, "", tlp);
  }

  /** Make a TreePrint instance. This one uses the default tlp headFinder. */
  public TreePrint(String formats, String options, TreebankLanguagePack tlp) {
    this(formats, options, tlp, tlp.headFinder(), tlp.typedDependencyHeadFinder());
  }

  /**
   * Make a TreePrint instance.
   *
   * @param formatString A comma separated list of ways to print each Tree.
   *                For instance, "penn" or "words,typedDependencies".
   *                Known formats are: oneline, penn, latexTree, xmlTree, words,
   *                wordsAndTags, rootSymbolOnly, dependencies,
   *                typedDependencies, typedDependenciesCollapsed,
   *                collocations, semanticGraph, conllStyleDependencies,
   *                conll2007.  The last two are both tab-separated values
   *                formats.  The latter has a lot more columns filled with
   *                underscores. All of them print a blank line after
   *                the output except for oneline.  oneline is also not
   *                meaningful in XML output (it is ignored: use penn instead).
   *                (Use of typedDependenciesCollapsed is deprecated.  It
   *                works but we recommend instead selecting a type of
   *                dependencies using the optionsString argument.  Note in
   *                particular that typedDependenciesCollapsed does not do
   *                CC propagation, which we generally recommend.)
   * @param optionsString Options that additionally specify how trees are to
   *                be printed (for instance, whether stemming should be done).
   *                Known options are: {@code stem, lexicalize, markHeadNodes,
   *                xml, removeTopBracket, transChinese,
   *                includePunctuationDependencies, basicDependencies, treeDependencies,
   *                CCPropagatedDependencies, collapsedDependencies, nonCollapsedDependencies,
   *                nonCollapsedDependenciesSeparated, includeTags}.
   * @param tlp     The TreebankLanguagePack used to do things like delete
   *                or ignore punctuation in output
   * @param hf      The HeadFinder used in printing output
   */
  public TreePrint(String formatString, String optionsString, TreebankLanguagePack tlp, HeadFinder hf, HeadFinder typedDependencyHF) {
    formats = StringUtils.stringToPropertiesMap(formatString);
    options = StringUtils.stringToPropertiesMap(optionsString);
    List<String> okOutputs = Arrays.asList(outputTreeFormats);
    for (String format : formats.keySet()) {
      if ( ! okOutputs.contains(format)) {
        throw new RuntimeException("Error: output tree format " + format + " not supported. Known formats are: " + okOutputs);
      }
    }

    this.hf = hf;
    this.tlp = tlp;
    boolean includePunctuationDependencies;
    includePunctuationDependencies = propertyToBoolean(this.options,
                                                       "includePunctuationDependencies");

    boolean generateOriginalDependencies = tlp.generateOriginalDependencies();

    Predicate<String> puncFilter;
    if (includePunctuationDependencies) {
      dependencyFilter = Filters.acceptFilter();
      dependencyWordFilter = Filters.acceptFilter();
      puncFilter = Filters.acceptFilter();
    } else {
      dependencyFilter = new Dependencies.DependentPuncTagRejectFilter<>(tlp.punctuationTagRejectFilter());
      dependencyWordFilter = new Dependencies.DependentPuncWordRejectFilter<>(tlp.punctuationWordRejectFilter());
      //Universal dependencies filter punction by tags
      puncFilter = generateOriginalDependencies ? tlp.punctuationWordRejectFilter() : tlp.punctuationTagRejectFilter();
    }
    if (propertyToBoolean(this.options, "stem")) {
      stemmer = new WordStemmer();
    } else {
      stemmer = null;
    }
    if (formats.containsKey("typedDependenciesCollapsed") ||
        formats.containsKey("typedDependencies") ||
        (formats.containsKey("conll2007") && tlp.supportsGrammaticalStructures())) {
      gsf = tlp.grammaticalStructureFactory(puncFilter, typedDependencyHF);
    } else {
      gsf = null;
    }

    lexicalize = propertyToBoolean(this.options, "lexicalize");
    markHeadNodes = propertyToBoolean(this.options, "markHeadNodes");
    transChinese = propertyToBoolean(this.options, "transChinese");
    ptb2text = propertyToBoolean(this.options, "ptb2text");
    removeEmpty = propertyToBoolean(this.options, "noempty") || ptb2text;

    basicDependencies =  propertyToBoolean(this.options, "basicDependencies");
    collapsedDependencies = propertyToBoolean(this.options, "collapsedDependencies");
    nonCollapsedDependencies = propertyToBoolean(this.options, "nonCollapsedDependencies");
    nonCollapsedDependenciesSeparated = propertyToBoolean(this.options, "nonCollapsedDependenciesSeparated");
    treeDependencies = propertyToBoolean(this.options, "treeDependencies");

    includeTags = propertyToBoolean(this.options, "includeTags");

    // if no option format for the dependencies is specified, CCPropagated is the default
    if ( ! basicDependencies && ! collapsedDependencies && ! nonCollapsedDependencies && ! nonCollapsedDependenciesSeparated && ! treeDependencies) {
      CCPropagatedDependencies = true;
    } else {
      CCPropagatedDependencies = propertyToBoolean(this.options, "CCPropagatedDependencies");
    }
  }


  private static boolean propertyToBoolean(LinkedHashMap<String, String> prop, String key) {
    return Boolean.parseBoolean(prop.get(key));
  }

  /**
   * Prints the tree to the default PrintWriter.
   * @param t The tree to display
   */
  public void printTree(Tree t) {
    printTree(t, pw);
  }

  /**
   * Prints the tree, with an empty ID.
   * @param t The tree to display
   * @param pw The PrintWriter to print it to
   */
  public void printTree(final Tree t, PrintWriter pw) {
    printTree(t, "", pw);
  }


  /**
   * Prints the tree according to the options specified for this instance.
   * If the tree {@code t} is {@code null}, then the code prints
   * a line indicating a skipped tree.  Under the XML option this is
   * an {@code s} element with the {@code skipped} attribute having
   * value {@code true}, and, otherwise, it is the token
   * {@code SENTENCE_SKIPPED_OR_UNPARSABLE}.
   *
   * @param t The tree to display
   * @param id A name for this sentence
   * @param pw Where to display the tree
   */
  public void printTree(final Tree t, final String id, final PrintWriter pw) {
    final boolean inXml = propertyToBoolean(options, "xml");
    if (t == null) {
      // Parsing didn't succeed.
      if (inXml) {
        pw.print("<s");
        if ( ! StringUtils.isNullOrEmpty(id)) {
          pw.print(" id=\"" + XMLUtils.escapeXML(id) + '\"');
        }
        pw.println(" skipped=\"true\"/>");
        pw.println();
      } else {
        pw.println("SENTENCE_SKIPPED_OR_UNPARSABLE");
      }
    } else {
      if (inXml) {
        pw.print("<s");
        if ( ! StringUtils.isNullOrEmpty(id)) {
          pw.print(" id=\"" + XMLUtils.escapeXML(id) + '\"');
        }
        pw.println(">");
      }
      printTreeInternal(t, pw, inXml);
      if (inXml) {
        pw.println("</s>");
        pw.println();
      }
    }
  }


  /**
   * Prints the trees according to the options specified for this instance.
   * If the tree {@code t} is {@code null}, then the code prints
   * a line indicating a skipped tree.  Under the XML option this is
   * an {@code s} element with the {@code skipped} attribute having
   * value {@code true}, and, otherwise, it is the token
   * {@code SENTENCE_SKIPPED_OR_UNPARSABLE}.
   *
   * @param trees The list of trees to display
   * @param id A name for this sentence
   * @param pw Where to dislay the tree
   */
  public void printTrees(final List<ScoredObject<Tree>> trees, final String id, final PrintWriter pw) {
    final boolean inXml = propertyToBoolean(options, "xml");
    int ii = 0;  // incremented before used, so first tree is numbered 1
    for (ScoredObject<Tree> tp : trees) {
      ii++;
      Tree t = tp.object();
      double score = tp.score();

      if (t == null) {
        // Parsing didn't succeed.
        if (inXml) {
          pw.print("<s");
          if ( ! StringUtils.isNullOrEmpty(id)) {
            pw.print(" id=\"" + XMLUtils.escapeXML(id) + '\"');
          }
          pw.print(" n=\"");
          pw.print(ii);
          pw.print('\"');
          pw.print(" score=\"" + score + '\"');
          pw.println(" skipped=\"true\"/>");
          pw.println();
        } else {
          pw.println("SENTENCE_SKIPPED_OR_UNPARSABLE Parse #" + ii + " with score " + score);
        }
      } else {
        if (inXml) {
          pw.print("<s");
          if ( ! StringUtils.isNullOrEmpty(id)) {
            pw.print(" id=\"");
            pw.print(XMLUtils.escapeXML(id));
            pw.print('\"');
          }
          pw.print(" n=\"");
          pw.print(ii);
          pw.print('\"');
          pw.print(" score=\"");
          pw.print(score);
          pw.print('\"');
          pw.println(">");
        } else {
          pw.print("# Parse ");
          pw.print(ii);
          pw.print(" with score ");
          pw.println(score);
        }
        printTreeInternal(t, pw, inXml);
        if (inXml) {
          pw.println("</s>");
          pw.println();
        }
      }
    }
  }


  /** Print the internal part of a tree having already identified it.
   *  The ID and outer XML element is printed wrapping this method, but none
   *  of the internal content.
   *
   * @param t The tree to print. Now known to be non-null
   * @param pw Where to print it to
   * @param inXml Whether to use XML style printing
   */
  private void printTreeInternal(final Tree t, final PrintWriter pw, final boolean inXml) {
    Tree outputTree = t;

    if (formats.containsKey("conll2007") || removeEmpty) {
      outputTree = outputTree.prune(new BobChrisTreeNormalizer.EmptyFilter());
    }

    if (formats.containsKey("words")) {
      if (inXml) {
        ArrayList<Label> sentUnstemmed = outputTree.yield();
        pw.println("  <words>");
        int i = 1;
        for (Label w : sentUnstemmed) {
          pw.println("    <word ind=\"" + i + "\">" + XMLUtils.escapeXML(w.value()) + "</word>");
          i++;
        }
        pw.println("  </words>");
      } else {
        String sent = SentenceUtils.listToString(outputTree.yield(), false);
        if(ptb2text) {
          pw.println(PTBTokenizer.ptb2Text(sent));
        } else {
          pw.println(sent);
          pw.println();
        }
      }
    }

    if (propertyToBoolean(options, "removeTopBracket")) {
      String s = outputTree.label().value();
      if (tlp.isStartSymbol(s)) {
        if (outputTree.isUnaryRewrite()) {
          outputTree = outputTree.firstChild();
        } else {
          // It's not quite clear what to do if the tree isn't unary at the top
          // but we then don't strip the ROOT symbol, since that seems closer
          // than losing part of the tree altogether....
          log.info("TreePrint: can't remove top bracket: not unary");
        }
      }
      // Note that TreePrint is also called on dependency trees that have
      // a word as the root node, and so we don't error if there isn't
      // the root symbol at the top; rather we silently assume that this
      // is a dependency tree!!
    }
    if (stemmer != null) {
      stemmer.visitTree(outputTree);
    }
    if (lexicalize) {
      outputTree = Trees.lexicalize(outputTree, hf);
      Function<Tree, Tree> a =
        TreeFunctions.getLabeledToDescriptiveCoreLabelTreeFunction();
      outputTree = a.apply(outputTree);
    }

    if (formats.containsKey("collocations")) {
      outputTree = getCollocationProcessedTree(outputTree, hf);
    }

    if (!lexicalize) { // delexicalize the output tree
      Function<Tree, Tree> a =
        TreeFunctions.getLabeledTreeToStringLabeledTreeFunction();
      outputTree = a.apply(outputTree);
    }

    Tree outputPSTree = outputTree;  // variant with head-marking, translations

    if (markHeadNodes) {
      outputPSTree = markHeadNodes(outputPSTree);
    }

    if (transChinese) {
      TreeTransformer tt = t1 -> {
        t1 = t1.treeSkeletonCopy();
        for (Tree subtree : t1) {
          if (subtree.isLeaf()) {
            Label oldLabel = subtree.label();
            String translation = ChineseEnglishWordMap.getInstance().getFirstTranslation(oldLabel.value());
            if (translation == null) translation = "[UNK]";
            Label newLabel = new StringLabel(oldLabel.value() + ':' + translation);
            subtree.setLabel(newLabel);
          }
        }
        return t1;
      };
      outputPSTree = tt.transformTree(outputPSTree);
    }

    if (propertyToBoolean(options, "xml")) {
      if (formats.containsKey("wordsAndTags")) {
        ArrayList<TaggedWord> sent = outputTree.taggedYield();
        pw.println("  <words pos=\"true\">");
        int i = 1;
        for (TaggedWord tw : sent) {
          pw.println("    <word ind=\"" + i + "\" pos=\"" + XMLUtils.escapeXML(tw.tag()) + "\">" + XMLUtils.escapeXML(tw.word()) + "</word>");
          i++;
        }
        pw.println("  </words>");
      }
      if (formats.containsKey("penn")) {
        pw.println("  <tree style=\"penn\">");
        StringWriter sw = new StringWriter();
        PrintWriter psw = new PrintWriter(sw);
        outputPSTree.pennPrint(psw);
        pw.print(XMLUtils.escapeXML(sw.toString()));
        pw.println("  </tree>");
      }
      if (formats.containsKey("latexTree")) {
        pw.println("    <tree style=\"latexTrees\">");
        pw.println(".[");
        StringWriter sw = new StringWriter();
        PrintWriter psw = new PrintWriter(sw);
        outputTree.indentedListPrint(psw,false);
        pw.print(XMLUtils.escapeXML(sw.toString()));
        pw.println(".]");
        pw.println("  </tree>");
      }
      if (formats.containsKey("xmlTree")) {
        pw.println("<tree style=\"xml\">");
        outputTree.indentedXMLPrint(pw,false);
        pw.println("</tree>");
      }
      if (formats.containsKey("dependencies")) {
        Tree indexedTree = outputTree.deepCopy(outputTree.treeFactory(),
                                                 CoreLabel.factory());
        indexedTree.indexLeaves();
        Set<Dependency<Label, Label, Object>> depsSet = indexedTree.mapDependencies(dependencyWordFilter, hf);
        List<Dependency<Label, Label, Object>> sortedDeps = new ArrayList<>(depsSet);
        sortedDeps.sort(Dependencies.dependencyIndexComparator());
        pw.println("<dependencies style=\"untyped\">");
        for (Dependency<Label, Label, Object> d : sortedDeps) {
          pw.println(d.toString("xml"));
        }
        pw.println("</dependencies>");
      }
      if (formats.containsKey("conll2007") || formats.containsKey("conllStyleDependencies")) {
        log.info("The \"conll2007\" and \"conllStyleDependencies\" formats are ignored in xml.");
      }
      if (formats.containsKey("typedDependencies")) {
        GrammaticalStructure gs = gsf.newGrammaticalStructure(outputTree);
        if (basicDependencies) {
          print(gs.typedDependencies(), "xml", includeTags, pw);
        }
        if (nonCollapsedDependencies || nonCollapsedDependenciesSeparated) {
          print(gs.allTypedDependencies(), "xml", includeTags, pw);
        }
        if (collapsedDependencies) {
          print(gs.typedDependenciesCollapsed(GrammaticalStructure.Extras.MAXIMAL), "xml", includeTags, pw);
        }
        if (CCPropagatedDependencies) {
          print(gs.typedDependenciesCCprocessed(), "xml", includeTags, pw);
        }
        if(treeDependencies) {
          print(gs.typedDependenciesCollapsedTree(), "xml", includeTags, pw);
        }
      }
      if (formats.containsKey("typedDependenciesCollapsed")) {
        GrammaticalStructure gs = gsf.newGrammaticalStructure(outputTree);
        print(gs.typedDependenciesCCprocessed(), "xml", includeTags, pw);
      }

      // This makes parser require jgrapht.  Bad.
      // if (formats.containsKey("semanticGraph")) {
      //  SemanticGraph sg = SemanticGraph.makeFromTree(outputTree, true, false, false, null);
      //  pw.println(sg.toFormattedString());
      // }
    } else {
      // non-XML printing
      if (formats.containsKey("wordsAndTags")) {
        pw.println(SentenceUtils.listToString(outputTree.taggedYield(), false));
        pw.println();
      }
      if (formats.containsKey("oneline")) {
        pw.println(outputPSTree);
      }
      if (formats.containsKey("penn")) {
        outputPSTree.pennPrint(pw);
        pw.println();
      }
      if (formats.containsKey(rootLabelOnlyFormat)) {
        pw.println(outputTree.label().value());
      }
      if (formats.containsKey("latexTree")) {
        pw.println(".[");
        outputTree.indentedListPrint(pw,false);
        pw.println(".]");
      }
      if (formats.containsKey("xmlTree")) {
        outputTree.indentedXMLPrint(pw,false);
      }
      if (formats.containsKey("dependencies")) {
        Tree indexedTree = outputTree.deepCopy(outputTree.treeFactory());
        indexedTree.indexLeaves();
        List<Dependency<Label, Label, Object>> sortedDeps = getSortedDeps(indexedTree, dependencyWordFilter);
        for (Dependency<Label, Label, Object> d : sortedDeps) {
          pw.println(d.toString("predicate"));
        }
        pw.println();
      }
      if (formats.containsKey("conll2007")) {
        // CoNLL-X 2007 format: http://ilk.uvt.nl/conll/#dataformat
        // wsg: This code should be retained (and not subsumed into EnglishGrammaticalStructure) so
        //      that dependencies for other languages can be printed.
        // wsg2011: This code currently ignores the dependency label since the present implementation
        //          of mapDependencies() returns UnnamedDependency objects.
        // TODO: if there is a GrammaticalStructureFactory available, use that instead of mapDependencies
        Tree it = outputTree.deepCopy(outputTree.treeFactory(), CoreLabel.factory());
        it.indexLeaves();

        List<CoreLabel> tagged = it.taggedLabeledYield();
        List<Dependency<Label, Label, Object>> sortedDeps = getSortedDeps(it, Filters.acceptFilter());

        for (Dependency<Label, Label, Object> d : sortedDeps) {
          if (!dependencyFilter.test(d)) {
            continue;
          }
          if (!(d.dependent() instanceof HasIndex) || !(d.governor() instanceof HasIndex)) {
            throw new IllegalArgumentException("Expected labels to have indices");
          }
          HasIndex dep = (HasIndex) d.dependent();
          HasIndex gov = (HasIndex) d.governor();

          int depi = dep.index();
          int govi = gov.index();

          CoreLabel w = tagged.get(depi - 1);

          // Used for both course and fine POS tag fields
          String tag = PTBTokenizer.ptbToken2Text(w.tag());

          String word = PTBTokenizer.ptbToken2Text(w.word());
          String lemma = "_";
          String feats = "_";
          String pHead = "_";
          String pDepRel = "_";
          String depRel;
          if (d.name() != null) {
            depRel = d.name().toString();
          } else {
            depRel = (govi == 0) ? "ROOT" : "NULL";
          }

          // The 2007 format has 10 fields
          pw.printf("%d\t%s\t%s\t%s\t%s\t%s\t%d\t%s\t%s\t%s%n", depi, word, lemma, tag, tag, feats, govi, depRel, pHead, pDepRel);
        }
        pw.println();
      }
      if (formats.containsKey("conllStyleDependencies")) {
        // TODO: Rewrite this to output StanfordDependencies using EnglishGrammaticalStructure code
        BobChrisTreeNormalizer tn = new BobChrisTreeNormalizer();
        Tree indexedTree = outputTree.deepCopy(outputTree.treeFactory(),
                                                 CoreLabel.factory());
        // TODO: Can the below for-loop be deleted now?  (Now that the HeadFinder knows about NML.)
        for (Tree node : indexedTree) {
          if (node.label().value().startsWith("NML")) {
            node.label().setValue("NP");
          }
        }

        indexedTree = tn.normalizeWholeTree(indexedTree, outputTree.treeFactory());
        indexedTree.indexLeaves();
        Set<Dependency<Label, Label, Object>> depsSet = null;
        boolean failed = false;
        try {
          depsSet = indexedTree.mapDependencies(dependencyFilter, hf);
        } catch (Exception e) {
          failed = true;
        }
        if (failed) {
          log.info("failed: ");
          log.info(t);
          log.info();
        } else {
          Map<Integer,Integer> deps = Generics.newHashMap();
          for (Dependency<Label, Label, Object> dep : depsSet) {
            CoreLabel child = (CoreLabel)dep.dependent();
            CoreLabel parent = (CoreLabel)dep.governor();
            Integer childIndex =
              child.get(CoreAnnotations.IndexAnnotation.class);
            Integer parentIndex =
              parent.get(CoreAnnotations.IndexAnnotation.class);
//            log.info(childIndex+"\t"+parentIndex);
            deps.put(childIndex, parentIndex);
          }
          boolean foundRoot = false;
          int index = 1;
          for (Tree node : indexedTree.getLeaves()) {
            String word = node.label().value();
            String tag = node.parent(indexedTree).label().value();
            int parent = 0;
            if (deps.containsKey(index)) {
              parent = deps.get(index);
            } else {
              if (foundRoot) { throw new RuntimeException(); }
              foundRoot = true;
            }
            pw.println(index + '\t' + word + '\t' + tag + '\t' + parent);
            index++;
          }
          pw.println();
        }
      }
      if (formats.containsKey("typedDependencies")) {
        GrammaticalStructure gs = gsf.newGrammaticalStructure(outputTree);
        if (basicDependencies) {
          print(gs.typedDependencies(), includeTags, pw);
        }
        if (nonCollapsedDependencies) {
          print(gs.allTypedDependencies(), includeTags, pw);
        }
        if (nonCollapsedDependenciesSeparated) {
          print(gs.allTypedDependencies(), "separator", includeTags, pw);
        }
        if (collapsedDependencies) {
          print(gs.typedDependenciesCollapsed(GrammaticalStructure.Extras.MAXIMAL), includeTags, pw);
        }
        if (CCPropagatedDependencies) {
          print(gs.typedDependenciesCCprocessed(), includeTags, pw);
        }
        if (treeDependencies) {
          print(gs.typedDependenciesCollapsedTree(), includeTags, pw);
        }
      }
      if (formats.containsKey("typedDependenciesCollapsed")) {
        GrammaticalStructure gs = gsf.newGrammaticalStructure(outputTree);
        print(gs.typedDependenciesCCprocessed(), includeTags, pw);
      }
      // This makes parser require jgrapht.  Bad
      // if (formats.containsKey("semanticGraph")) {
      //  SemanticGraph sg = SemanticGraph.makeFromTree(outputTree, true, false, false, null);
      //  pw.println(sg.toFormattedString());
      // }
    }

    // flush to make sure we see all output
    pw.flush();
  }

  private List<Dependency<Label, Label, Object>> getSortedDeps(Tree tree, Predicate<Dependency<Label, Label, Object>> filter) {
    if (gsf != null) {
      GrammaticalStructure gs = gsf.newGrammaticalStructure(tree);
      Collection<TypedDependency> deps = gs.typedDependencies(GrammaticalStructure.Extras.NONE);
      List<Dependency<Label, Label, Object>> sortedDeps = new ArrayList<>();
      for (TypedDependency dep : deps) {
        sortedDeps.add(new NamedDependency(dep.gov(), dep.dep(), dep.reln().toString()));
      }
      sortedDeps.sort(Dependencies.dependencyIndexComparator());
      return sortedDeps;
    } else {
      Set<Dependency<Label, Label, Object>> depsSet = tree.mapDependencies(filter, hf, "root");
      List<Dependency<Label, Label, Object>> sortedDeps = new ArrayList<>(depsSet);
      sortedDeps.sort(Dependencies.dependencyIndexComparator());
      return sortedDeps;
    }
  }


  /** For the input tree, collapse any collocations in it that exist in
   *  WordNet and are contiguous in the tree into a single node.
   *  A single static Wordnet connection is used by all instances of this
   *  class.  Reflection to check that a Wordnet connection exists.  Otherwise
   *  we print an error and do nothing.
   *
   *  @param tree The input tree.  NOTE: This tree is mangled by this method
   *  @param hf The head finder to use
   *  @return The collocation collapsed tree
   */
  private static synchronized Tree getCollocationProcessedTree(Tree tree,
                                                               HeadFinder hf) {
    if (wnc == null) {
      try {
        Class<?> cl = Class.forName("edu.stanford.nlp.trees.WordNetInstance");
        wnc = (WordNetConnection) cl.getDeclaredConstructor().newInstance();
      } catch (Exception e) {
        log.info("Couldn't open WordNet Connection.  Aborting collocation detection.");
        log.info(e);
        wnc = null;
      }
    }
    if (wnc != null) {
      CollocationFinder cf = new CollocationFinder(tree, wnc, hf);
      tree = cf.getMangledTree();
    } else {
      log.error("WordNetConnection unavailable for collocations.");
    }
    return tree;
  }


  public void printHeader(PrintWriter pw, String charset) {
    if (propertyToBoolean(options, "xml")) {
      pw.println("<?xml version=\"1.0\" encoding=\"" + charset + "\"?>");
      pw.println("<corpus>");
    }
  }


  public void printFooter(PrintWriter pw) {
    if (propertyToBoolean(options, "xml")) {
      pw.println("</corpus>");
    }
  }


  public Tree markHeadNodes(Tree t) {
    return markHeadNodes(t, null);
  }

  private Tree markHeadNodes(Tree t, Tree head) {
    if (t.isLeaf()) {
      return t; // don't worry about head-marking leaves
    }
    Label newLabel;
    if (t == head) {
      newLabel = headMark(t.label());
    } else {
      newLabel = t.label();
    }
    Tree newHead = hf.determineHead(t);
    return t.treeFactory().newTreeNode(newLabel, Arrays.asList(headMarkChildren(t, newHead)));
  }

  private static Label headMark(Label l) {
    Label l1 = l.labelFactory().newLabel(l);
    l1.setValue(l1.value() + headMark);
    return l1;
  }

  private Tree[] headMarkChildren(Tree t, Tree head) {
    Tree[] kids = t.children();
    Tree[] newKids = new Tree[kids.length];
    for (int i = 0, n = kids.length; i < n; i++) {
      newKids[i] = markHeadNodes(kids[i], head);
    }
    return newKids;
  }

  /** This provides a simple main method for calling TreePrint.
   *  Flags supported are:
   *  <ol>
   *  <li> -format format (like -outputFormat of parser, default "penn")
   *  <li> -options options (like -outputFormatOptions of parser, default "")
   *  <li> -tLP class (the TreebankLanguagePack, default "edu.stanford.nlp.tree.PennTreebankLanguagePack")
   *  <li> -hf class (the HeadFinder, default, the one in the class specified by -tLP)
   *  <li> -useTLPTreeReader (use the treeReaderFactory() inside
   *       the -tLP class; otherwise a PennTreeReader with no normalization is used)
   *  </ol>
   *  The single argument should be a file containing Trees in the format that is either
   *  Penn Treebank s-expressions or as specified by -useTLPTreeReader and the -tLP class,
   *  or if there is no such argument, trees are read from stdin and the program runs as a
   *  filter.
   *
   *  @param args Command line arguments, as above.
   */
  public static void main(String[] args) {
    String format = "penn";
    String options = "";
    String tlpName = "edu.stanford.nlp.trees.PennTreebankLanguagePack";
    String hfName = null;
    Map<String,Integer> flagMap = Generics.newHashMap();
    flagMap.put("-format", 1);
    flagMap.put("-options", 1);
    flagMap.put("-tLP", 1);
    flagMap.put("-hf", 1);
    Map<String,String[]> argsMap = StringUtils.argsToMap(args,flagMap);
    args = argsMap.get(null);
    if (argsMap.containsKey("-format")) {
      format = argsMap.get("-format")[0];
    }
    if(argsMap.containsKey("-options")) {
      options = argsMap.get("-options")[0];
    }
    if (argsMap.containsKey("-tLP")) {
      tlpName = argsMap.get("-tLP")[0];
    }
    if (argsMap.containsKey("-hf")) {
      hfName = argsMap.get("-hf")[0];
    }
    TreebankLanguagePack tlp;
    try {
      tlp = (TreebankLanguagePack) Class.forName(tlpName).getDeclaredConstructor().newInstance();
    } catch (Exception e) {
      log.warning(e);
      return;
    }
    HeadFinder hf;
    if (hfName != null) {
      try {
        hf = (HeadFinder) Class.forName(hfName).getDeclaredConstructor().newInstance();
      } catch (Exception e) {
        log.warning(e);
        return;
      }
    } else {
      hf = tlp.headFinder();
    }
    TreePrint print = new TreePrint(format, options, tlp, (hf == null) ? tlp.headFinder(): hf, tlp.typedDependencyHeadFinder());
    Iterator<Tree> i; // initialized below
    if (args.length > 0) {
      Treebank trees; // initialized below
      TreeReaderFactory trf;
      if (argsMap.containsKey("-useTLPTreeReader")) {
        trf = tlp.treeReaderFactory();
      } else {
        trf = in -> new PennTreeReader(in, new LabeledScoredTreeFactory(new StringLabelFactory()), new TreeNormalizer());
      }
      trees = new DiskTreebank(trf);
      trees.loadPath(args[0]);
      i = trees.iterator();
    } else {
      i = tlp.treeTokenizerFactory().getTokenizer(new BufferedReader(new InputStreamReader(System.in)));
    }
    while(i.hasNext()) {
      print.printTree(i.next());
    }
  }

  /**
   * NO OUTSIDE USE
   * Returns a String representation of the result of this set of
   * typed dependencies in a user-specified format.
   * Currently, three formats are supported:
   * <dl>
   * <dt>"plain"</dt>
   * <dd>(Default.)  Formats the dependencies as logical relations,
   * as exemplified by the following:
   * <pre>
   *  nsubj(died-1, Sam-0)
   *  tmod(died-1, today-2)
   *  </pre>
   * </dd>
   * <dt>"readable"</dt>
   * <dd>Formats the dependencies as a table with columns
   * {@code dependent}, {@code relation}, and
   * {@code governor}, as exemplified by the following:
   * <pre>
   *  Sam-0               nsubj               died-1
   *  today-2             tmod                died-1
   *  </pre>
   * </dd>
   * <dt>"xml"</dt>
   * <dd>Formats the dependencies as XML, as exemplified by the following:
   * <pre>
   *  &lt;dependencies&gt;
   *    &lt;dep type="nsubj"&gt;
   *      &lt;governor idx="1"&gt;died&lt;/governor&gt;
   *      &lt;dependent idx="0"&gt;Sam&lt;/dependent&gt;
   *    &lt;/dep&gt;
   *    &lt;dep type="tmod"&gt;
   *      &lt;governor idx="1"&gt;died&lt;/governor&gt;
   *      &lt;dependent idx="2"&gt;today&lt;/dependent&gt;
   *    &lt;/dep&gt;
   *  &lt;/dependencies&gt;
   *  </pre>
   * </dd>
   * </dl>
   *
   * @param dependencies The TypedDependencies to print
   * @param format a {@code String} specifying the desired format
   * @return a {@code String} representation of the typed
   *         dependencies in this {@code GrammaticalStructure}
   */
  private static String toString(Collection<TypedDependency> dependencies, String format, boolean includeTags) {
    if (format != null && format.equals("xml")) {
      return toXMLString(dependencies, includeTags);
    } else if (format != null && format.equals("readable")) {
      return toReadableString(dependencies);
    } else if (format != null && format.equals("separator")) {
      return toString(dependencies, true, includeTags);
    } else {
      return toString(dependencies, false, includeTags);
    }
  }

  /**
   * NO OUTSIDE USE
   * Returns a String representation of this set of typed dependencies
   * as exemplified by the following:
   * <pre>
   *  tmod(died-6, today-9)
   *  nsubj(died-6, Sam-3)
   *  </pre>
   *
   * @param dependencies The TypedDependencies to print
   * @param extraSep boolean indicating whether the extra dependencies have to be printed separately, after the basic ones
   * @return a {@code String} representation of this set of
   *         typed dependencies
   */
  private static String toString(Collection<TypedDependency> dependencies, boolean extraSep, boolean includeTags) {
    CoreLabel.OutputFormat labelFormat = (includeTags) ? CoreLabel.OutputFormat.VALUE_TAG_INDEX : CoreLabel.OutputFormat.VALUE_INDEX;
    StringBuilder buf = new StringBuilder();
    if (extraSep) {
      List<TypedDependency> extraDeps = new ArrayList<>();
      for (TypedDependency td : dependencies) {
        if (td.extra()) {
          extraDeps.add(td);
        } else {
          buf.append(td.toString(labelFormat)).append('\n');
        }
      }
      // now we print the separator for extra dependencies, and print these if there are some
      if (!extraDeps.isEmpty()) {
        buf.append("======\n");
        for (TypedDependency td : extraDeps) {
          buf.append(td.toString(labelFormat)).append('\n');
        }
      }
    } else {
      for (TypedDependency td : dependencies) {
        buf.append(td.toString(labelFormat)).append('\n');
      }
    }
    return buf.toString();
  }

  // NO OUTSIDE USE
  private static String toReadableString(Collection<TypedDependency> dependencies) {
    StringBuilder buf = new StringBuilder();
    buf.append(String.format("%-20s%-20s%-20s%n", "dep", "reln", "gov"));
    buf.append(String.format("%-20s%-20s%-20s%n", "---", "----", "---"));
    for (TypedDependency td : dependencies) {
      buf.append(String.format("%-20s%-20s%-20s%n", td.dep(), td.reln(), td.gov()));
    }
    return buf.toString();
  }

  // NO OUTSIDE USE
  private static String toXMLString(Collection<TypedDependency> dependencies, boolean includeTags) {
    StringBuilder buf = new StringBuilder("<dependencies style=\"typed\">\n");
    for (TypedDependency td : dependencies) {
      String reln = td.reln().toString();
      String gov = td.gov().value();
      String govTag = td.gov().tag();
      int govIdx = td.gov().index();
      String dep = td.dep().value();
      String depTag = td.dep().tag();
      int depIdx = td.dep().index();
      boolean extra = td.extra();
      // add an attribute if the node is a copy
      // (this happens in collapsing when different prepositions are conjuncts)
      String govCopy = "";
      int copyGov = td.gov().copyCount();
      if (copyGov > 0) {
        govCopy = " copy=\"" + copyGov + '\"';
      }
      String depCopy = "";
      int copyDep = td.dep().copyCount();
      if (copyDep > 0) {
        depCopy = " copy=\"" + copyDep + '\"';
      }
      String govTagAttribute = (includeTags && govTag != null) ? " tag=\"" + govTag + "\"" : "";
      String depTagAttribute = (includeTags && depTag != null) ? " tag=\"" + depTag + "\"" : "";
      // add an attribute if the typed dependency is an extra relation (do not preserve the tree structure)
      String extraAttr = "";
      if (extra) {
        extraAttr = " extra=\"yes\"";
      }
      buf.append("  <dep type=\"").append(XMLUtils.escapeXML(reln)).append('\"').append(extraAttr).append(">\n");
      buf.append("    <governor idx=\"").append(govIdx).append('\"').append(govCopy).append(govTagAttribute).append('>').append(XMLUtils.escapeXML(gov)).append("</governor>\n");
      buf.append("    <dependent idx=\"").append(depIdx).append('\"').append(depCopy).append(depTagAttribute).append('>').append(XMLUtils.escapeXML(dep)).append("</dependent>\n");
      buf.append("  </dep>\n");
    }
    buf.append("</dependencies>");
    return buf.toString();
  }

  /**
   * USED BY TREEPRINT AND WSD.SUPWSD.PREPROCESS
   * Prints this set of typed dependencies to the specified
   * {@code PrintWriter}.
   * @param dependencies The collection of TypedDependency to print
   * @param pw Where to print them
   */
  public static void print(Collection<TypedDependency> dependencies, boolean includeTags, PrintWriter pw) {
    pw.println(toString(dependencies, false, includeTags));
  }

  /**
   * USED BY TREEPRINT
   * Prints this set of typed dependencies to the specified
   * {@code PrintWriter} in the specified format.
   * @param dependencies The collection of TypedDependency to print
   * @param format "xml" or "readable" or other
   * @param pw Where to print them
   */
  public static void print(Collection<TypedDependency> dependencies, String format, boolean includeTags, PrintWriter pw) {
    pw.println(toString(dependencies, format, includeTags));
  }

}
