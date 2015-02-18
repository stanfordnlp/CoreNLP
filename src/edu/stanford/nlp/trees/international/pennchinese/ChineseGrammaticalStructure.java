package edu.stanford.nlp.trees.international.pennchinese;

import edu.stanford.nlp.parser.lexparser.ChineseTreebankParserParams;
import edu.stanford.nlp.parser.ViterbiParserWithOptions;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.util.Filter;
import edu.stanford.nlp.util.Filters;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.StringUtils;

import java.io.*;
import java.util.*;
import java.lang.reflect.Constructor;

import static edu.stanford.nlp.trees.GrammaticalRelation.DEPENDENT;

/**
 * A GrammaticalStructure for Chinese.
 *
 * @author Galen Andrew
 * @author Pi-Chuan Chang
 * @author Daniel Cer - support for printing CoNLL-X format, encoding update,
 *                      and preliminary changes to make
 *                      ChineseGrammaticalStructure behave more like
 *                      EnglishGrammaticalStructure on the command line
 *                      (ultimately, both classes should probably use the same
 *                      abstract main method).
 */
public class ChineseGrammaticalStructure extends GrammaticalStructure {

  private static HeadFinder shf = new ChineseSemanticHeadFinder();
  //private static HeadFinder shf = new ChineseHeadFinder();


  /**
   * Construct a new <code>GrammaticalStructure</code> from an
   * existing parse tree.  The new <code>GrammaticalStructure</code>
   * has the same tree structure and label values as the given tree
   * (but no shared storage).  As part of construction, the parse tree
   * is analyzed using definitions from {@link GrammaticalRelation
   * <code>GrammaticalRelation</code>} to populate the new
   * <code>GrammaticalStructure</code> with as many labeled
   * grammatical relations as it can.
   *
   * @param t Tree to process
   */
  public ChineseGrammaticalStructure(Tree t) {
    this(t, new ChineseTreebankLanguagePack().punctuationWordRejectFilter());
  }

  public ChineseGrammaticalStructure(Tree t, Filter<String> puncFilter) {
    this (t, puncFilter, shf);
  }

  public ChineseGrammaticalStructure(Tree t, HeadFinder hf) {
    this (t, null, hf);
  }

  public ChineseGrammaticalStructure(Tree t, Filter<String> puncFilter, HeadFinder hf) {
    super(t, ChineseGrammaticalRelations.values(), hf, puncFilter);
  }

  /** Used for postprocessing CoNLL X dependencies */
  public ChineseGrammaticalStructure(List<TypedDependency> projectiveDependencies, TreeGraphNode root) {
    super(projectiveDependencies, root);
  }



  @Override
  protected void collapseDependencies(List<TypedDependency> list, boolean CCprocess, boolean includeExtras) {
    //      collapseConj(list);
    collapsePrepAndPoss(list);
    //      collapseMultiwordPreps(list);
  }

  private static void collapsePrepAndPoss(Collection<TypedDependency> list) {
    Collection<TypedDependency> newTypedDeps = new ArrayList<TypedDependency>();

    // Construct a map from tree nodes to the set of typed
    // dependencies in which the node appears as governor.
    Map<TreeGraphNode, Set<TypedDependency>> map = Generics.newHashMap();
    for (TypedDependency typedDep : list) {
      if (!map.containsKey(typedDep.gov())) {
        map.put(typedDep.gov(), Generics.<TypedDependency>newHashSet());
      }
      map.get(typedDep.gov()).add(typedDep);
    }
    //System.err.println("here's the map: " + map);

    for (TypedDependency td1 : list) {
      if (td1.reln() != GrammaticalRelation.KILL) {
        TreeGraphNode td1Dep = td1.dep();
        String td1DepPOS = td1Dep.parent().value();
        // find all other typedDeps having our dep as gov
        Set<TypedDependency> possibles = map.get(td1Dep);
        if (possibles != null) {
          // look for the "second half"
          for (TypedDependency td2 : possibles) {
            // TreeGraphNode td2Dep = td2.dep();
            // String td2DepPOS = td2Dep.parent().value();
            if (td1.reln() == DEPENDENT && td2.reln() == DEPENDENT && td1DepPOS.equals("P")) {
              GrammaticalRelation td3reln = ChineseGrammaticalRelations.valueOf(td1Dep.value());
              if (td3reln == null) {
                td3reln = GrammaticalRelation.valueOf(GrammaticalRelation.Language.Chinese,
                                                      td1Dep.value());
              }
              TypedDependency td3 = new TypedDependency(td3reln, td1.gov(), td2.dep());
              //System.err.println("adding: " + td3);
              newTypedDeps.add(td3);
              td1.setReln(GrammaticalRelation.KILL);        // remember these are "used up"
              td2.setReln(GrammaticalRelation.KILL);        // remember these are "used up"
            }
          }

          // Now we need to see if there any TDs that will be "orphaned"
          // by this collapse.  Example: if we have:
          //   dep(drew, on)
          //   dep(on, book)
          //   dep(on, right)
          // the first two will be collapsed to on(drew, book), but then
          // the third one will be orphaned, since its governor no
          // longer appears.  So, change its governor to 'drew'.
          if (td1.reln().equals(GrammaticalRelation.KILL)) {
            for (TypedDependency td2 : possibles) {
              if (!td2.reln().equals(GrammaticalRelation.KILL)) {
                //System.err.println("td1 & td2: " + td1 + " & " + td2);
                td2.setGov(td1.gov());
              }
            }
          }
        }
      }
    }

    // now copy remaining unkilled TDs from here to new
    for (TypedDependency td : list) {
      if (!td.reln().equals(GrammaticalRelation.KILL)) {
        newTypedDeps.add(td);
      }
    }

    list.clear();                            // forget all (esp. killed) TDs
    list.addAll(newTypedDeps);
  }

  private static void AddTreesFromFile(String treeFileName, String encoding, Treebank tb) {
    ChineseTreebankParserParams ctpp = new ChineseTreebankParserParams();
    try {
      TreeReaderFactory trf = ctpp.treeReaderFactory();
      TreeReader tr = trf.newTreeReader(new InputStreamReader(new FileInputStream(treeFileName), encoding));
      Tree t;
      while ((t = tr.readTree()) != null) {
        tb.add(t);
      }
      tr.close();
    } catch (IOException e) {
      throw new RuntimeException("File problem: " + e);
    }
  }

  /**
   * Tests generation of Chinese grammatical relations from a file.
   * Default encoding is utf-8.
   *
   * TODO: remove this main method and use the one in the abstract class GrammaticalStructure. Making this
   * change is non-trivial due to some of the English specific assumptions in the code currently invoked by
   * GrammaticalStructure#main.
   *
   * Usage: <br> <code>
   * java edu.stanford.nlp.trees.international.pennchinese.ChineseGrammaticalStructure -treeFile [treeFile] <br>
   * java edu.stanford.nlp.trees.international.pennchinese.ChineseGrammaticalStructure -sentFile [sentenceFile] </code>
   *
   * @param args Command line args as above
   */
  public static void main(String[] args) {

    // System.out.print("GrammaticalRelations under DEPENDENT:");
    // System.out.println(DEPENDENT.toPrettyString());

    Treebank tb = new MemoryTreebank();
    Properties props = StringUtils.argsToProperties(args);

    String encoding = props.getProperty("encoding", "utf-8");
    try {
      System.setOut(new PrintStream(System.out, true, encoding));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    String treeFileName = props.getProperty("treeFile");
    String treeDirname = props.getProperty("treeDir");
    String sentFileName = props.getProperty("sentFile");
    boolean conllx = props.getProperty("conllx") != null;
    boolean basic = props.getProperty("basic") != null;
    boolean nonCollapsed = props.getProperty("nonCollapsed") != null;
    boolean collapsed = props.getProperty("collapsed") != null;
    boolean parseTree = props.getProperty("parseTree") != null;
    boolean keepPunct = props.getProperty("keepPunct") != null;

    // force keepPunct, if conllx is turned on
    if (conllx) {
      keepPunct = true;
    }

    String hf = props.getProperty("hf");
    String parserModel = props.getProperty("parserModel", "/u/nlp/data/lexparser/chineseFactored.ser.gz");

    if (!basic && !collapsed) {
      if (conllx) {
        basic = true;     // default to basic dependencies for conllx
      } else {
        collapsed = true; // otherwise, default to collapsed dependencies
      }
    }

    try {
      if (hf != null) {
        shf = (HeadFinder)Class.forName(hf).newInstance();
        System.err.println("Using "+hf);
      }
    } catch (Exception e) {
      throw new RuntimeException("Fail to use HeadFinder: "+hf);
    }


    if (args.length == 0) {
      System.err.printf("Usage:\n\t%s [optional flags] -treeFile treeFile\n\nOr:\n\t%s [optional flags] -sentFile sentFile\n", ChineseGrammaticalStructure.class.getName(), ChineseGrammaticalStructure.class.getName());
      System.err.println("\nOptional flags:");
      System.err.println("\t-parseTree  : print phrase-structure parse tree");
      System.err.println("\t-basic : basic non-collapsed dependencies preserving a tree structure");
      System.err.println("\t-collapsed : collapsed dependencies");
      System.err.println("\t-conllx : conllx formatted dependencies, can be used with either basic\n\t or collaped dependencies, but basic is recommended");

    } else {
      if (treeDirname != null && treeFileName != null) {
        throw new RuntimeException("Only one of treeDirname or treeFileName should be set");
      }
      if (treeDirname != null) {
        File dir = new File(treeDirname);
        String[] files = dir.list();
        for (String file : files) {
          AddTreesFromFile(treeDirname+"/"+file, encoding, tb);
        }
      } else if (treeFileName != null) {
        AddTreesFromFile(treeFileName, encoding, tb);
      } else if (sentFileName != null) {
        // Load parser by reflection, so that this class doesn't require parser for runtime use
        // LexicalizedParser lp = new LexicalizedParser(parserModel);
        ViterbiParserWithOptions lp;
        try {
          Class<?>[] classes = new Class<?>[]{String.class};
          Constructor<?> constr = Class.forName("edu.stanford.nlp.parser.lexparser.LexicalizedParser").getConstructor(classes);
          String[] opts = {"-retainTmpSubcategories"};
          lp = (ViterbiParserWithOptions) constr.newInstance(parserModel);
          lp.setOptionFlags(opts);
        } catch (Exception cnfe) {
          cnfe.printStackTrace();
          return;
        }
        BufferedReader reader = null;
        try {
          reader = new BufferedReader(new FileReader(sentFileName));
        } catch (FileNotFoundException e) {
          System.err.println("Cannot find " + sentFileName);
          System.exit(1);
        }
        try {
          System.out.println("Processing sentence file " + sentFileName);
          String line;
          while ((line = reader.readLine()) != null) {
            CHTBTokenizer chtb = new CHTBTokenizer(new StringReader(line));
            List words = chtb.tokenize();
            lp.parse(words);
            Tree tree = lp.getBestParse();
            tb.add(tree);
          }
          reader.close();
        } catch (Exception e) {
          throw new RuntimeException("Exception reading key file " + sentFileName, e);
        }
      }
    }


    for (Tree t : tb) {
      Filter<String> puncFilter;

      if (keepPunct) {
        puncFilter = Filters.acceptFilter();
      } else {
        puncFilter = new ChineseTreebankLanguagePack().punctuationWordRejectFilter();
      }

      GrammaticalStructure gs = new ChineseGrammaticalStructure(t, puncFilter);

      if (parseTree) {
        System.out.println("============= parse tree =======================");
        t.pennPrint();
      }
      //System.out.println("----------------------------");
      //TreeGraph tg = new TreeGraph(t);
      //System.out.println(tg);

      //System.out.println("----------------------------");
      //System.out.println(gs);

      if (basic) {
        if (collapsed || nonCollapsed) {
          System.out.println("------------- basic dependencies ---------------");
        }
        printDependencies(gs, gs.typedDependencies(false), t, conllx, false);
      }

      if (nonCollapsed) {
        if (basic || collapsed) {
          System.out.println("------------- noncollapsed dependencies ---------------");
        }
        printDependencies(gs, gs.typedDependencies(true), t, conllx, false);
      }

      if (collapsed) {
        if (basic || nonCollapsed) {
          System.out.println("----------- collapsed dependencies -----------");
        }
        printDependencies(gs, gs.typedDependenciesCollapsed(true), t, conllx, false);
      }

      //gs.printTypedDependencies("xml");
    }
  }


  public static List<GrammaticalStructure> readCoNLLXGrammaticalStructureCollection(String fileName) throws IOException {
    return readCoNLLXGrammaticalStructureCollection(fileName, ChineseGrammaticalRelations.shortNameToGRel, new FromDependenciesFactory());
  }

  public static ChineseGrammaticalStructure buildCoNLLXGrammaticalStructure(List<List<String>> tokenFields) {
    return (ChineseGrammaticalStructure) buildCoNLLXGrammaticalStructure(tokenFields, ChineseGrammaticalRelations.shortNameToGRel, new FromDependenciesFactory());
  }

  public static class FromDependenciesFactory
    implements GrammaticalStructureFromDependenciesFactory
  {
    public ChineseGrammaticalStructure build(List<TypedDependency> tdeps, TreeGraphNode root) {
      return new ChineseGrammaticalStructure(tdeps, root);
    }
  }

  private static final long serialVersionUID = 8877651855167458256L;

}
