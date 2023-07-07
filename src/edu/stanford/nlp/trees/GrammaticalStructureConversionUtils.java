package edu.stanford.nlp.trees;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.parser.lexparser.TreebankLangParserParams;
import edu.stanford.nlp.process.PTBTokenizer;
import edu.stanford.nlp.process.WhitespaceTokenizer;
import edu.stanford.nlp.semgraph.SemanticGraphFactory;
import edu.stanford.nlp.trees.international.pennchinese.CTBErrorCorrectingTreeNormalizer;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Filters;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.ReflectionLoading;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.logging.Redwood;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Contains several utility methods to convert constituency trees to
 * dependency trees.
 *
 * Used by {@link GrammaticalStructure#main(String[])}.
 */

public class GrammaticalStructureConversionUtils {

  public static final String DEFAULT_PARSER_FILE = "edu/stanford/nlp/models/lexparser/englishPCFG.ser.gz";


  private GrammaticalStructureConversionUtils() {} // static methods

  /**
   * Print typed dependencies in either the Stanford dependency representation
   * or in the conllx format.
   *
   * @param deps Typed dependencies to print
   * @param tree Tree corresponding to typed dependencies (only necessary if conllx
   *          == true)
   * @param conllx If true use conllx format, otherwise use Stanford representation
   * @param extraSep If true, in the Stanford representation, the extra dependencies
   *          (which do not preserve the tree structure) are printed after the
   *          basic dependencies
   * @param convertToUPOS If true convert the POS tags to universal POS tags and output
   *                      them along the original POS tags.
   */
  public static void printDependencies(GrammaticalStructure gs, Collection<TypedDependency> deps, Tree tree,
                                       boolean conllx, boolean extraSep, boolean convertToUPOS) {
    System.out.println(dependenciesToString(gs, deps, tree, conllx, extraSep, convertToUPOS));
  }


  /**
   * Calls dependenciesToCoNLLXString with the basic dependencies
   * from a grammatical structure.
   *
   * (see {@link #dependenciesToCoNLLXString(Collection, CoreMap)})
   */
  public static String dependenciesToCoNLLXString(GrammaticalStructure gs, CoreMap sentence) {
    return dependenciesToCoNLLXString(gs.typedDependencies(), sentence);
  }


  /**
   *
   * Returns a dependency tree in CoNNL-X format.
   * It requires a CoreMap for the sentence with a TokensAnnotation.
   * Each token has to contain a word and a POS tag.
   *
   * @param deps The list of TypedDependency relations.
   * @param sentence The corresponding CoreMap for the sentence.
   * @return Dependency tree in CoNLL-X format.
   */
  public static String dependenciesToCoNLLXString(Collection<TypedDependency> deps, CoreMap sentence) {
    StringBuilder bf = new StringBuilder();

    HashMap<Integer, TypedDependency> indexedDeps = new HashMap<>(deps.size());
    for (TypedDependency dep : deps) {
      indexedDeps.put(dep.dep().index(), dep);
    }

    List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
    if (tokens == null) {
      throw new RuntimeException("dependenciesToCoNLLXString: CoreMap does not have required TokensAnnotation.");
    }
    int idx = 1;

    for (CoreLabel token : tokens) {
      String word = token.value();
      String pos = token.tag();
      String cPos = (token.get(CoreAnnotations.CoarseTagAnnotation.class) != null) ?
          token.get(CoreAnnotations.CoarseTagAnnotation.class) : pos;
      String lemma = token.lemma() != null ? token.lemma() : "_";
      Integer gov = indexedDeps.containsKey(idx) ? indexedDeps.get(idx).gov().index() : 0;
      String reln = indexedDeps.containsKey(idx) ? indexedDeps.get(idx).reln().toString() : "erased";
      String out = String.format("%d\t%s\t%s\t%s\t%s\t_\t%d\t%s\t_\t_\n", idx, word, lemma, cPos, pos, gov, reln);
      bf.append(out);
      idx++;
    }
    return bf.toString();
  }

  public static String dependenciesToString(GrammaticalStructure gs, Collection<TypedDependency> deps, Tree tree,
                                            boolean conllx, boolean extraSep, boolean convertToUPOS) {
    StringBuilder bf = new StringBuilder();

    Map<Integer, Integer> indexToPos = Generics.newHashMap();
    indexToPos.put(0,0); // to deal with the special node "ROOT"
    List<Tree> gsLeaves = gs.root().getLeaves();
    for (int i = 0; i < gsLeaves.size(); i++) {
      TreeGraphNode leaf = (TreeGraphNode) gsLeaves.get(i);
      indexToPos.put(leaf.label().index(), i + 1);
    }

    if (conllx) {

      List<Tree> leaves = tree.getLeaves();
      List<Label> uposLabels; // = null; // initialized below
      if (convertToUPOS) {
        Tree uposTree = UniversalPOSMapper.mapTree(tree);
        uposLabels = uposTree.preTerminalYield();
      } else {
        uposLabels = tree.preTerminalYield();
      }
      int index = 0;
      CoreMap sentence = new CoreLabel();
      List<CoreLabel> tokens = new ArrayList<>(leaves.size());
      for (Tree leaf : leaves) {
        index++;
        if (!indexToPos.containsKey(index)) {
          continue;
        }
        CoreLabel token = new CoreLabel();
        token.setIndex(index);
        token.setValue(leaf.value());
        token.setWord(leaf.value());
        token.setTag(leaf.parent(tree).value());
        token.set(CoreAnnotations.CoarseTagAnnotation.class, uposLabels.get(index - 1).value());
        tokens.add(token);
      }
      sentence.set(CoreAnnotations.TokensAnnotation.class, tokens);
      bf.append(dependenciesToCoNLLXString(deps, sentence));
    } else {
      if (extraSep) {
        List<TypedDependency> extraDeps = new ArrayList<>();
        for (TypedDependency dep : deps) {
          if (dep.extra()) {
            extraDeps.add(dep);
          } else {
            bf.append(toStringIndex(dep, indexToPos));
            bf.append('\n');
          }
        }
        // now we print the separator for extra dependencies, and print these if
        // there are some
        if (!extraDeps.isEmpty()) {
          bf.append("======\n");
          for (TypedDependency dep : extraDeps) {
            bf.append(toStringIndex(dep, indexToPos));
            bf.append('\n');
          }
        }
      } else {
        for (TypedDependency dep : deps) {
          bf.append(toStringIndex(dep, indexToPos));
          bf.append('\n');
        }
      }
    }

    return bf.toString();
  }

  private static String toStringIndex(TypedDependency td, Map<Integer, Integer> indexToPos) {
    IndexedWord gov = td.gov();
    IndexedWord dep = td.dep();
    return td.reln() + "(" + gov.value() + "-" + indexToPos.get(gov.index()) + gov.toPrimes() + ", " + dep.value() + "-" + indexToPos.get(dep.index()) + dep.toPrimes() + ")";
  }


  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(GrammaticalStructureConversionUtils.class);

  private static String[] parseClassConstructArgs(String namePlusArgs) {
    String[] args = StringUtils.EMPTY_STRING_ARRAY;
    String name = namePlusArgs;
    if (namePlusArgs.matches(".*\\([^)]*\\)$")) {
      String argStr = namePlusArgs.replaceFirst("^.*\\(([^)]*)\\)$", "$1");
      args = argStr.split(",");
      name = namePlusArgs.replaceFirst("\\([^)]*\\)$", "");
    }
    String[] tokens = new String[1 + args.length];
    tokens[0] = name;
    System.arraycopy(args, 0, tokens, 1, args.length);
    return tokens;
  }


  private static DependencyReader loadAlternateDependencyReader(String altDepReaderName) {
    Class<? extends DependencyReader> altDepReaderClass = null;
    String[] toks = parseClassConstructArgs(altDepReaderName);
    altDepReaderName = toks[0];
    String[] depReaderArgs = new String[toks.length - 1];
    System.arraycopy(toks, 1, depReaderArgs, 0, toks.length - 1);

    try {
      Class<?> cl = Class.forName(altDepReaderName);
      altDepReaderClass = cl.asSubclass(DependencyReader.class);
    } catch (ClassNotFoundException e) {
      // have a second go below
    }
    if (altDepReaderClass == null) {
      try {
        Class<?> cl = Class.forName("edu.stanford.nlp.trees." + altDepReaderName);
        altDepReaderClass = cl.asSubclass(DependencyReader.class);
      } catch (ClassNotFoundException e) {
        //
      }
    }
    if (altDepReaderClass == null) {
      log.info("Can't load dependency reader " + altDepReaderName + " or edu.stanford.nlp.trees." + altDepReaderName);
      return null;
    }

    DependencyReader altDepReader; // initialized below
    if (depReaderArgs.length == 0) {
      try {
        altDepReader = altDepReaderClass.newInstance();
      } catch (InstantiationException e) {
        throw new RuntimeException(e);
      } catch (IllegalAccessException e) {
        log.info("No argument constructor to " + altDepReaderName + " is not public");
        return null;
      }
    } else {
      try {
        altDepReader = altDepReaderClass.getConstructor(String[].class).newInstance((Object) depReaderArgs);
      } catch (IllegalArgumentException | SecurityException | InvocationTargetException e) {
        throw new RuntimeException(e);
      } catch (InstantiationException e) {
        e.printStackTrace();
        return null;
      } catch (IllegalAccessException e) {
        log.info(depReaderArgs.length + " argument constructor to " + altDepReaderName + " is not public.");
        return null;
      } catch (NoSuchMethodException e) {
        log.info("String arguments constructor to " + altDepReaderName + " does not exist.");
        return null;
      }
    }
    return altDepReader;
  }



  private static DependencyPrinter loadAlternateDependencyPrinter(String altDepPrinterName) {
    Class<? extends DependencyPrinter> altDepPrinterClass = null;
    String[] toks = parseClassConstructArgs(altDepPrinterName);
    altDepPrinterName = toks[0];
    String[] depPrintArgs = new String[toks.length - 1];
    System.arraycopy(toks, 1, depPrintArgs, 0, toks.length - 1);

    try {
      Class<?> cl = Class.forName(altDepPrinterName);
      altDepPrinterClass = cl.asSubclass(DependencyPrinter.class);
    } catch (ClassNotFoundException e) {
      //
    }
    if (altDepPrinterClass == null) {
      try {
        Class<?> cl = Class.forName("edu.stanford.nlp.trees." + altDepPrinterName);
        altDepPrinterClass = cl.asSubclass(DependencyPrinter.class);
      } catch (ClassNotFoundException e) {
        //
      }
    }
    if (altDepPrinterClass == null) {
      System.err.printf("Unable to load alternative printer %s or %s. Is your classpath set correctly?\n", altDepPrinterName, "edu.stanford.nlp.trees." + altDepPrinterName);
      return null;
    }
    try {
      DependencyPrinter depPrinter;
      if (depPrintArgs.length == 0) {
        depPrinter = altDepPrinterClass.newInstance();
      } else {
        depPrinter = altDepPrinterClass.getConstructor(String[].class).newInstance((Object) depPrintArgs);
      }
      return depPrinter;
    } catch (IllegalArgumentException | SecurityException | IllegalAccessException | InstantiationException
            | InvocationTargetException e) {
      e.printStackTrace();
      return null;
    } catch (NoSuchMethodException e) {
      if (depPrintArgs.length == 0) {
        System.err.printf("Can't find no-argument constructor %s().%n", altDepPrinterName);
      } else {
        System.err.printf("Can't find constructor %s(%s).%n", altDepPrinterName, Arrays.toString(depPrintArgs));
      }
      return null;
    }
  }

  private static Function<List<? extends HasWord>, Tree> loadParser(String parserFile, String parserOptions, boolean makeCopulaHead) {
    if (parserFile == null || parserFile.isEmpty()) {
      parserFile = DEFAULT_PARSER_FILE;
      if (parserOptions == null) {
        parserOptions = "-retainTmpSubcategories";
      }
    }
    if (parserOptions == null) {
      parserOptions = "";
    }
    if (makeCopulaHead) {
      parserOptions = "-makeCopulaHead " + parserOptions;
    }
    parserOptions = parserOptions.trim();
    // Load parser by reflection, so that this class doesn't require parser
    // for runtime use
    // LexicalizedParser lp = LexicalizedParser.loadModel(parserFile);
    // For example, the tregex package uses TreePrint, which uses
    // GrammaticalStructure, which would then import the
    // LexicalizedParser.  The tagger can read trees, which means it
    // would depend on tregex and therefore depend on the parser.
    Function<List<? extends HasWord>, Tree> lp;
    try {
      Class<?>[] classes = new Class<?>[] { String.class, String[].class };
      Method method = Class.forName("edu.stanford.nlp.parser.lexparser.LexicalizedParser").getMethod("loadModel", classes);
      String[] opts = StringUtils.EMPTY_STRING_ARRAY;
      if ( ! parserOptions.isEmpty()) {
        opts = parserOptions.split(" +");
      }
      lp = (Function<List<? extends HasWord>,Tree>) method.invoke(null, parserFile, opts);
    } catch (Exception cnfe) {
      throw new RuntimeException(cnfe);
    }
    return lp;
  }


  /**
   * Allow a collection of trees, that is a Treebank, appear to be a collection
   * of GrammaticalStructures.
   *
   * @author danielcer
   *
   */
  private static class TreeBankGrammaticalStructureWrapper implements Iterable<GrammaticalStructure> {

    private final Iterable<Tree> trees;
    private final boolean keepPunct;
    private final TreebankLangParserParams params;

    private final Map<GrammaticalStructure, Tree> origTrees = new WeakHashMap<>();

    public TreeBankGrammaticalStructureWrapper(Iterable<Tree> wrappedTrees, boolean keepPunct, TreebankLangParserParams params) {
      trees = wrappedTrees;
      this.keepPunct = keepPunct;
      this.params = params;
    }

    @Override
    public Iterator<GrammaticalStructure> iterator() {
      return new GsIterator();
    }

    public Tree getOriginalTree(GrammaticalStructure gs) {
      return origTrees.get(gs);
    }


    private class GsIterator implements Iterator<GrammaticalStructure> {

      private final Iterator<Tree> tbIterator = trees.iterator();
      private final Predicate<String> puncFilter;
      private final HeadFinder hf;
      private GrammaticalStructure next;

      public GsIterator() {
        if (keepPunct) {
          puncFilter = Filters.acceptFilter();
        } else if (params.generateOriginalDependencies()) {
          puncFilter = params.treebankLanguagePack().punctuationWordRejectFilter();
        } else {
          puncFilter = params.treebankLanguagePack().punctuationTagRejectFilter();
        }
        hf = params.typedDependencyHeadFinder();
        primeGs();
      }

      private void primeGs() {
        GrammaticalStructure gs = null;
        while (gs == null && tbIterator.hasNext()) {
          Tree t = tbIterator.next();
          // log.info("GsIterator: Next tree is");
          // log.info(t);
          if (t == null) {
            continue;
          }
          try {
            gs = params.getGrammaticalStructure(t, puncFilter, hf);
            origTrees.put(gs, t);
            next = gs;
            // log.info("GsIterator: Next tree is");
            // log.info(t);
            return;
          } catch (NullPointerException npe) {
            log.info("Bung tree caused below dump. Continuing....");
            log.info(t);
            npe.printStackTrace();
          }
        }
        next = null;
      }

      @Override
      public boolean hasNext() {
        return next != null;
      }

      @Override
      public GrammaticalStructure next() {
        GrammaticalStructure ret = next;
        if (ret == null) {
          throw new NoSuchElementException();
        }
        primeGs();
        return ret;
      }

      @Override
      public void remove() {
        throw new UnsupportedOperationException();
      }

    }
  } // end static class TreebankGrammaticalStructureWrapper


  /**
   * Enum to identify the different TokenizerTypes. To add a new
   * TokenizerType, add it to the list with a default options string
   * and add a clause in getTokenizerType to identify it.
   */
  public enum ConverterOptions {
    UniversalEnglish("en", new NPTmpRetainingTreeNormalizer(0, false, 1, false),
        "edu.stanford.nlp.parser.lexparser.EnglishTreebankParserParams", false, true),
    UniversalChinese("zh", new CTBErrorCorrectingTreeNormalizer(false, false, false, false),
        "edu.stanford.nlp.parser.lexparser.ChineseTreebankParserParams", false, false),
    English("en-sd", new NPTmpRetainingTreeNormalizer(0, false, 1, false),
        "edu.stanford.nlp.parser.lexparser.EnglishTreebankParserParams", true, true),
    Chinese("zh-sd", new CTBErrorCorrectingTreeNormalizer(false, false, false, false),
        "edu.stanford.nlp.parser.lexparser.ChineseTreebankParserParams", true, false);

    public final String abbreviation;
    public final TreeNormalizer treeNormalizer;
    public final String tlPPClassName;
    public final boolean stanfordDependencies;
    /* Conversion to UPOS is currently only supported for English. */
    public final boolean convertToUPOS;

    ConverterOptions(String abbreviation, TreeNormalizer treeNormalizer, String tlPPClassName,
                     boolean stanfordDependencies, boolean convertToUPOS) {
      this.abbreviation = abbreviation;
      this.treeNormalizer = treeNormalizer;
      this.tlPPClassName = tlPPClassName;
      /* Generate old Stanford Dependencies instead of UD, when set to true. */
      this.stanfordDependencies = stanfordDependencies;
      this.convertToUPOS = convertToUPOS;
    }

    private static final Map<String, ConverterOptions> nameToTokenizerMap = initializeNameMap();

    private static Map<String, ConverterOptions> initializeNameMap() {
      Map<String, ConverterOptions> map = Generics.newHashMap();
      for (ConverterOptions opts : ConverterOptions.values()) {
        if (opts.abbreviation != null) {
          map.put(opts.abbreviation.toUpperCase(), opts);
        }
        map.put(opts.toString().toUpperCase(), opts);
      }
      return Collections.unmodifiableMap(map);
    }

    public static ConverterOptions getConverterOptions(String language) {
      if (language == null) { return nameToTokenizerMap.get("EN"); }
      ConverterOptions opts = nameToTokenizerMap.get(language.toUpperCase());
      return opts != null ? opts : nameToTokenizerMap.get("EN");
    }
  }

  /**
   * Given sentences or trees, output the typed dependencies.
   * <p>
   * By default, the method outputs the collapsed typed dependencies with
   * processing of conjuncts. The input can be given as plain text (one sentence
   * by line) using the option -sentFile, or as trees using the option
   * -treeFile. For -sentFile, the input has to be strictly one sentence per
   * line. You can specify where to find a parser with -parserFile
   * serializedParserPath. See LexicalizedParser for more flexible processing of
   * text files (including with Stanford Dependencies output). The above options
   * assume a file as input. You can also feed trees (only) via stdin by using
   * the option -filter.  If one does not specify a -parserFile, one
   * can specify which language pack to use with -tLPP, This option
   * specifies a class which determines which GrammaticalStructure to
   * use, which HeadFinder to use, etc.  It will default to
   * edu.stanford.nlp.parser.lexparser.EnglishTreebankParserParams,
   * but any TreebankLangParserParams can be specified.
   * <p>
   * If no method of producing trees is given other than to use the
   * LexicalizedParser, but no parser is specified, a default parser
   * is used, the English parser.  You can specify options to load
   * with the parser using the -parserOpts flag.  If the default
   * parser is used, and no options are provided, the option
   * -retainTmpSubcategories is used.
   * <p>
   * The following options can be used to specify the types of dependencies
   * wanted: </p>
   * <ul>
   * <li> -collapsed collapsed dependencies
   * <li> -basic non-collapsed dependencies that preserve a tree structure
   * <li> -nonCollapsed non-collapsed dependencies that do not preserve a tree
   * structure (the basic dependencies plus the extra ones)
   * <li> -CCprocessed
   * collapsed dependencies and conjunctions processed (dependencies are added
   * for each conjunct) -- this is the default if no options are passed
   * <li> -collapsedTree collapsed dependencies retaining a tree structure
   * <li> -makeCopulaHead Contrary to the approach argued for in the SD papers,
   *  nevertheless make the verb 'to be' the head, not the predicate noun, adjective,
   *  etc. (However, when the verb 'to be' is used as an auxiliary verb, the main
   *  verb is still treated as the head.)
   * <li> -originalDependencies generate the dependencies using the original converter
   * instead of the Universal Dependencies converter.
   * </ul>
   * <p>
   * The {@code -conllx} option will output the dependencies in the CoNLL format,
   * instead of in the standard Stanford format (relation(governor,dependent))
   * and will retain punctuation by default.
   * When used in the "collapsed" format, words such as prepositions, conjunctions
   * which get collapsed into the grammatical relations and are not part of the
   * sentence per se anymore will be annotated with "erased" as grammatical relation
   * and attached to the fake "ROOT" node with index 0.
   * <p>
   * Keeping punctuation is the default behavior.  This can be stopped with
   * {@code -keepPunct false}
   * <p>
   * The {@code -extraSep} option used with -nonCollapsed will print the basic
   * dependencies first, then a separator ======, and then the extra
   * dependencies that do not preserve the tree structure. The -test option is
   * used for debugging: it prints the grammatical structure, as well as the
   * basic, collapsed and CCprocessed dependencies. It also checks the
   * connectivity of the collapsed dependencies. If the collapsed dependencies
   * list doesn't constitute a connected graph, it prints the possible offending
   * nodes (one of them is the real root of the graph).
   * <p>
   * Using the -conllxFile, you can pass a file containing Stanford dependencies
   * in the CoNLL format (e.g., the basic dependencies), and obtain another
   * representation using one of the representation options.
   * <p>
   * Usage: <br>
   * <code>java edu.stanford.nlp.trees.GrammaticalStructure [-treeFile FILE | -sentFile FILE | -conllxFile FILE | -filter] <br>
   * [-collapsed -basic -CCprocessed -test -generateOriginalDependencies]</code>
   *
   * @param args Command-line arguments, as above
   */
  @SuppressWarnings("unchecked")
  public static void convertTrees(String[] args, String defaultLang) {

    /* Use a tree normalizer that removes all empty nodes.
       This prevents wrong indexing of the nodes in the dependency relations. */

    Iterable<GrammaticalStructure> gsBank = null;
    Properties props = StringUtils.argsToProperties(args);

    String language = props.getProperty("language", defaultLang);
    ConverterOptions opts = ConverterOptions.getConverterOptions(language);


    MemoryTreebank tb = new MemoryTreebank(opts.treeNormalizer);
    Iterable<Tree> trees = tb;



    String encoding = props.getProperty("encoding", "utf-8");
    try {
      System.setOut(new PrintStream(System.out, true, encoding));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    String treeFileName = props.getProperty("treeFile");
    String sentFileName = props.getProperty("sentFile");
    String conllXFileName = props.getProperty("conllxFile");
    String altDepPrinterName = props.getProperty("altprinter");
    String altDepReaderName = props.getProperty("altreader");
    String altDepReaderFilename = props.getProperty("altreaderfile");

    String filter = props.getProperty("filter");

    boolean makeCopulaHead = props.getProperty("makeCopulaHead") != null;
    boolean generateOriginalDependencies = props.getProperty("originalDependencies") != null || opts.stanfordDependencies;

    // TODO: if a parser is specified, load this from the parser
    // instead of ever loading it from this way
    String tLPP = props.getProperty("tLPP", opts.tlPPClassName);
    TreebankLangParserParams params = ReflectionLoading.loadByReflection(tLPP);
    params.setGenerateOriginalDependencies(generateOriginalDependencies);

    if (makeCopulaHead) {
      // TODO: generalize and allow for more options
      String[] options = { "-makeCopulaHead" };
      params.setOptionFlag(options, 0);
    }

    if (sentFileName == null && (altDepReaderName == null || altDepReaderFilename == null) && treeFileName == null && conllXFileName == null && filter == null) {
      try {
        System.err.printf("Usage: java %s%n", GrammaticalStructure.class.getCanonicalName());
        System.err.println("Options:");
        System.err.println("  Dependency representation:");
        System.err.println("    -basic:\t\tGenerate basic dependencies.");
        System.err.println("    -enhanced:\t\tGenerate enhanced dependencies, currently only implemented for English UD.");
        System.err.println("    -enhanced++:\tGenerate enhanced++ dependencies (default), currently only implemented for English UD.");
        System.err.println("    -collapsed:\t\tGenerate collapsed dependencies, deprecated.");
        System.err.println("    -CCprocessed:\tGenerate CC-processed dependencies, deprecated.");
        System.err.println("    -collapsedTree:\tGenerate collapsed-tree dependencies, deprecated.");
        System.err.println("");
        System.err.println("  Input:");
        System.err.println("    -treeFile <FILE>:\tConvert from constituency trees in <FILE>");
        System.err.println("    -sentFile <FILE>:\tParse and convert sentences from <FILE>. Only implemented for English.");
        System.err.println("");
        System.err.println("  Output:");
        System.err.println("    -conllx:\t\tOutput dependencies in CoNLL format.");
        System.err.println("");
        System.err.println("  Language:");
        System.err.println("    -language [en|zh|en-sd|zh-sd]:\t (Universal English Dependencies, Universal Chinese Dependencies, English Stanford Dependencies, Chinese Stanford Dependencies)");
        System.err.println("");
        System.err.println("");
        System.err.println("");
        System.err.println("Example:");
        TreeReader tr = new PennTreeReader(new StringReader("((S (NP (NNP Sam)) (VP (VBD died) (NP-TMP (NN today)))))"));
        tb.add(tr.readTree());
      } catch (Exception e) {
        log.info("Horrible error: " + e);
        e.printStackTrace();
      }
    } else if (altDepReaderName != null && altDepReaderFilename != null) {
      DependencyReader altDepReader = loadAlternateDependencyReader(altDepReaderName);
      try {
        gsBank = altDepReader.readDependencies(altDepReaderFilename);
      } catch (IOException e) {
        log.info("Error reading " + altDepReaderFilename);
        return;
      }
    } else if (treeFileName != null) {
      tb.loadPath(treeFileName);
    } else if (filter != null) {
      tb.load(IOUtils.readerFromStdin());
    } else if (conllXFileName != null) {
      try {
        gsBank = params.readGrammaticalStructureFromFile(conllXFileName);
      } catch (RuntimeIOException e) {
        log.info("Error reading " + conllXFileName);
        return;
      }
    } else {
      String parserFile = props.getProperty("parserFile");
      String parserOpts = props.getProperty("parserOpts");
      boolean tokenized = props.getProperty("tokenized") != null;
      Function<List<? extends HasWord>, Tree> lp = loadParser(parserFile, parserOpts, makeCopulaHead);
      trees = new LazyLoadTreesByParsing(sentFileName, encoding, tokenized, lp);

      // Instead of getting this directly from the LP, use reflection
      // so that a package which uses GrammaticalStructure doesn't
      // necessarily have to use LexicalizedParser
      try {
        Method method = lp.getClass().getMethod("getTLPParams");
        params = (TreebankLangParserParams) method.invoke(lp);
        params.setGenerateOriginalDependencies(generateOriginalDependencies);
      } catch (Exception cnfe) {
        throw new RuntimeException(cnfe);
      }
    }

    // treats the output according to the options passed
    boolean basic = props.getProperty("basic") != null;
    boolean collapsed = props.getProperty("collapsed") != null;
    boolean CCprocessed = props.getProperty("CCprocessed") != null;
    boolean collapsedTree = props.getProperty("collapsedTree") != null;
    boolean nonCollapsed = props.getProperty("nonCollapsed") != null;
    boolean extraSep = props.getProperty("extraSep") != null;
    boolean parseTree = props.getProperty("parseTree") != null;
    boolean test = props.getProperty("test") != null;
    boolean keepPunct = PropertiesUtils.getBool(props, "keepPunct", true);
    boolean conllx = props.getProperty("conllx") != null;
    // todo: Support checkConnected on more options (including basic)
    boolean checkConnected = props.getProperty("checkConnected") != null;
    boolean portray = props.getProperty("portray") != null;
    boolean enhanced = props.getProperty("enhanced") != null;
    boolean enhancedPlusPlus = props.getProperty("enhanced++") != null;


    // If requested load alternative printer
    DependencyPrinter altDepPrinter = null;
    if (altDepPrinterName != null) {
      altDepPrinter = loadAlternateDependencyPrinter(altDepPrinterName);
    }

    // log.info("First tree in tb is");
    // log.info(((MemoryTreebank) tb).get(0));

    Method m = null;
    if (test) {
      // see if we can use SemanticGraph(Factory) to check for being a DAG
      // Do this by reflection to avoid this becoming a dependency when we distribute the parser
      try {
        Class sgf = Class.forName("edu.stanford.nlp.semgraph.SemanticGraphFactory");
        m = sgf.getDeclaredMethod("makeFromTree", GrammaticalStructure.class, SemanticGraphFactory.Mode.class, GrammaticalStructure.Extras.class, Predicate.class);
      } catch (Exception e) {
        log.info("Test cannot check for cycles in tree format (classes not available)");
      }
    }

    if (gsBank == null) {
      gsBank = new TreeBankGrammaticalStructureWrapper(trees, keepPunct, params);
    }

    for (GrammaticalStructure gs : gsBank) {

      Tree tree;
      if (gsBank instanceof TreeBankGrammaticalStructureWrapper) {
        // log.info("Using TreeBankGrammaticalStructureWrapper branch");
        tree = ((TreeBankGrammaticalStructureWrapper) gsBank).getOriginalTree(gs);
        // log.info("Tree is: ");
        // log.info(t);
      } else {
        // log.info("Using gs.root() branch");
        tree = gs.root(); // recover tree
        // log.info("Tree from gs is");
        // log.info(t);
      }

      if (test) { // print the grammatical structure, the basic, collapsed and CCprocessed

        System.out.println("============= parse tree =======================");
        tree.pennPrint();
        System.out.println();

        System.out.println("------------- GrammaticalStructure -------------");
        System.out.println(gs);

        boolean allConnected = true;
        boolean connected;
        Collection<TypedDependency> bungRoots = null;
        System.out.println("------------- basic dependencies ---------------");
        List<TypedDependency> gsb = gs.typedDependencies(GrammaticalStructure.Extras.NONE);
        System.out.println(StringUtils.join(gsb, "\n"));
        connected = GrammaticalStructure.isConnected(gsb);
        if ( ! connected && bungRoots == null) {
          bungRoots = GrammaticalStructure.getRoots(gsb);
        }
        allConnected = connected && allConnected;

        System.out.println("------------- non-collapsed dependencies (basic + extra) ---------------");
        List<TypedDependency> gse = gs.typedDependencies(GrammaticalStructure.Extras.MAXIMAL);
        System.out.println(StringUtils.join(gse, "\n"));
        connected = GrammaticalStructure.isConnected(gse);
        if ( ! connected && bungRoots == null) {
          bungRoots = GrammaticalStructure.getRoots(gse);
        }
        allConnected = connected && allConnected;

        System.out.println("------------- collapsed dependencies -----------");
        System.out.println(StringUtils.join(gs.typedDependenciesCollapsed(GrammaticalStructure.Extras.MAXIMAL), "\n"));

        System.out.println("------------- collapsed dependencies tree -----------");
        System.out.println(StringUtils.join(gs.typedDependenciesCollapsedTree(), "\n"));

        System.out.println("------------- CCprocessed dependencies --------");
        List<TypedDependency> gscc = gs.typedDependenciesCollapsed(GrammaticalStructure.Extras.MAXIMAL);
        System.out.println(StringUtils.join(gscc, "\n"));

        System.out.println("-----------------------------------------------");
        // connectivity tests
        connected = GrammaticalStructure.isConnected(gscc);
        if ( ! connected && bungRoots == null) {
          bungRoots = GrammaticalStructure.getRoots(gscc);
        }
        allConnected = connected && allConnected;
        if (allConnected) {
          System.out.println("dependencies form connected graphs.");
        } else {
          System.out.println("dependency graph NOT connected! possible offending nodes: " + bungRoots);
        }

        // test for collapsed dependencies being a tree:
        // make sure at least it doesn't contain cycles (i.e., is a DAG)
        // Do this by reflection so parser doesn't need SemanticGraph and its
        // libraries
        if (m != null) {
          try {
            // the first arg is null because it's a static method....
            Object semGraph = m.invoke(null, gs, SemanticGraphFactory.Mode.CCPROCESSED, GrammaticalStructure.Extras.MAXIMAL, null);
            Class sg = Class.forName("edu.stanford.nlp.semgraph.SemanticGraph");
            Method mDag = sg.getDeclaredMethod("isDag");
            boolean isDag = (Boolean) mDag.invoke(semGraph);

            System.out.println("tree dependencies form a DAG: " + isDag);
          } catch (Exception e) {
            e.printStackTrace();
          }
        }
      }// end of "test" output

      else {
        if (parseTree) {
          System.out.println("============= parse tree =======================");
          tree.pennPrint();
          System.out.println();
        }

        if (basic) {
          if (collapsed || CCprocessed || collapsedTree || nonCollapsed || enhanced || enhancedPlusPlus) {
            System.out.println("------------- basic dependencies ---------------");
          }
          if (altDepPrinter == null) {
            printDependencies(gs, gs.typedDependencies(GrammaticalStructure.Extras.NONE), tree, conllx, false, opts.convertToUPOS);
          } else {
            System.out.println(altDepPrinter.dependenciesToString(gs, gs.typedDependencies(GrammaticalStructure.Extras.NONE), tree));
          }
        }

        if (nonCollapsed) {
          if (basic || CCprocessed || collapsed || collapsedTree) {
            System.out.println("----------- non-collapsed dependencies (basic + extra) -----------");
          }
          printDependencies(gs, gs.allTypedDependencies(), tree, conllx, extraSep, opts.convertToUPOS);
        }

        if (collapsed) {
          if (basic || CCprocessed || collapsedTree || nonCollapsed) {
            System.out.println("----------- collapsed dependencies -----------");
          }
          printDependencies(gs, gs.typedDependenciesCollapsed(GrammaticalStructure.Extras.MAXIMAL), tree, conllx, false, opts.convertToUPOS);
        }

        if (CCprocessed) {
          if (basic || collapsed || collapsedTree || nonCollapsed) {
            System.out.println("---------- CCprocessed dependencies ----------");
          }
          List<TypedDependency> deps = gs.typedDependenciesCCprocessed(GrammaticalStructure.Extras.MAXIMAL);
          if (checkConnected) {
            if (!GrammaticalStructure.isConnected(deps)) {
              log.info("Graph is not connected for:");
              log.info(tree);
              log.info("possible offending nodes: " + GrammaticalStructure.getRoots(deps));
            }
          }
          printDependencies(gs, deps, tree, conllx, false, opts.convertToUPOS);
        }

        if (collapsedTree) {
          if (basic || CCprocessed || collapsed || nonCollapsed) {
            System.out.println("----------- collapsed dependencies tree -----------");
          }
          printDependencies(gs, gs.typedDependenciesCollapsedTree(), tree, conllx, false, opts.convertToUPOS);
        }

        if (enhanced) {
          if (basic || enhancedPlusPlus) {
            System.out.println("----------- enhanced dependencies tree -----------");
          }
          printDependencies(gs, gs.typedDependenciesEnhanced(), tree, conllx, false, opts.convertToUPOS);
        }

        if (enhancedPlusPlus) {
          if (basic || enhanced) {
            System.out.println("----------- enhanced++ dependencies tree -----------");
          }
          printDependencies(gs, gs.typedDependenciesEnhancedPlusPlus(), tree, conllx, false, opts.convertToUPOS);
        }

        // default use: enhanced++ for UD, CCprocessed for SD (to parallel what happens within the parser)
        if (!basic && !collapsed && !CCprocessed && !collapsedTree && !nonCollapsed && !enhanced && !enhancedPlusPlus) {
          // System.out.println("----------- CCprocessed dependencies -----------");

          if (generateOriginalDependencies) {
            printDependencies(gs, gs.typedDependenciesCCprocessed(GrammaticalStructure.Extras.MAXIMAL), tree, conllx, false, opts.convertToUPOS);
          } else {
            printDependencies(gs, gs.typedDependenciesEnhancedPlusPlus(), tree, conllx, false, opts.convertToUPOS);
          }
        }
      }

      if (portray) {
        try {
          // put up a window showing it
          Class sgu = Class.forName("edu.stanford.nlp.rte.gui.SemanticGraphVisualization");
          Method mRender = sgu.getDeclaredMethod("render", GrammaticalStructure.class, String.class);
          // the first arg is null because it's a static method....
          mRender.invoke(null, gs, "Collapsed, CC processed deps");
        } catch (Exception e) {
          throw new RuntimeException("Couldn't use swing to portray semantic graph", e);
        }
      }

    } // end for
  } // end convertTrees


  // todo [cdm 2013]: Take this out and make it a trees class: TreeIterableByParsing
  static class LazyLoadTreesByParsing implements Iterable<Tree> {

    final Reader reader;
    final String filename;
    final boolean tokenized;
    final String encoding;
    final Function<List<? extends HasWord>, Tree> lp;

    public LazyLoadTreesByParsing(String filename, String encoding, boolean tokenized, Function<List<? extends HasWord>, Tree> lp) {
      this.filename = filename;
      this.encoding = encoding;
      this.reader = null;
      this.tokenized = tokenized;
      this.lp = lp;
    }

    @Override
    public Iterator<Tree> iterator() {
      final BufferedReader iReader;
      if (reader != null) {
        iReader = new BufferedReader(reader);
      } else {
        try {
          iReader = new BufferedReader(new InputStreamReader(new FileInputStream(filename), encoding));
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }

      return new Iterator<Tree>() {

        String line; // = null;

        @Override
        public boolean hasNext() {
          if (line != null) {
            return true;
          } else {
            try {
              do {
                line = iReader.readLine();
                if (line != null) {
                  line = line.trim();
                }
              } while ("".equals(line));
            } catch (IOException e) {
              throw new RuntimeIOException(e);
            }
            if (line == null) {
              try {
                if (reader == null) iReader.close();
              } catch (IOException e) {
                throw new RuntimeIOException(e);
              }
              return false;
            }
            return true;
          }
        }

        @Override
        public Tree next() {
          if (line == null) {
            throw new NoSuchElementException();
          }
          Reader lineReader = new StringReader(line);
          line = null;
          List<Word> words;
          if (tokenized) {
            words = WhitespaceTokenizer.newWordWhitespaceTokenizer(lineReader).tokenize();
          } else {
            words = PTBTokenizer.newPTBTokenizer(lineReader).tokenize();
          }
          if (!words.isEmpty()) {
            // the parser throws an exception if told to parse an empty sentence.
            Tree parseTree = lp.apply(words);
            return parseTree;
          } else {
            return new SimpleTree();
          }
        }

        @Override
        public void remove() {
          throw new UnsupportedOperationException();
        }

      };
    }

  } // end static class LazyLoadTreesByParsing


}
