package edu.stanford.nlp.parser.lexparser; 
import edu.stanford.nlp.util.logging.Redwood;

import java.util.*;

import edu.stanford.nlp.ling.CategoryWordTag;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.SentenceUtils;
import edu.stanford.nlp.trees.DiskTreebank;
import edu.stanford.nlp.trees.HeadFinder;
import edu.stanford.nlp.trees.MemoryTreebank;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeReaderFactory;
import edu.stanford.nlp.trees.TreeTransformer;
import edu.stanford.nlp.trees.international.negra.NegraHeadFinder;
import edu.stanford.nlp.trees.international.negra.NegraLabel;
import edu.stanford.nlp.trees.international.negra.NegraPennLanguagePack;
import edu.stanford.nlp.trees.international.negra.NegraPennTreeReaderFactory;
import edu.stanford.nlp.util.Index;


/**
 * Parameter file for parsing the Penn Treebank format of the Negra
 * Treebank (German).  STILL UNDER CONSTRUCTION!
 *
 * @author Roger Levy
 */

public class NegraPennTreebankParserParams extends AbstractTreebankParserParams  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(NegraPennTreebankParserParams.class);

  private static final long serialVersionUID = 757812264219400466L;

  private static final boolean DEBUG = false;

  //Features
  private boolean markRC = false;
  private boolean markZuVP = false;
  private boolean markLP = false;
  private boolean markColon = false;
  private boolean markKonjParent = false;
  private boolean markHDParent = false;
  private boolean markContainsV = false;

  //Grammatical function parameters
  private static final boolean defaultLeaveGF = false;
  private static final char defaultGFCharacter = '-';

  /** Node cleanup is how node names are normalized. The known values are:
   *  0 = do nothing;
   *  1 = keep category and function;
   *  2 = keep only category
   */
  private int nodeCleanup = 2;

  private HeadFinder headFinder;

  private boolean treeNormalizerInsertNPinPP = false;
  //TODO: fix this so it really works
  private boolean treeNormalizerLeaveGF = false;


  public NegraPennTreebankParserParams() {
    super(new NegraPennLanguagePack(defaultLeaveGF, defaultGFCharacter));

    //wsg2010: Commented out by Roger?
    //return new NegraHeadFinder();
    //return new LeftHeadFinder();
    headFinder = new NegraHeadFinder();

 // override output encoding: make it UTF-8
    setInputEncoding("UTF-8");
    setOutputEncoding("UTF-8");
  }

  /**
   * returns a NegraHeadFinder
   */
  @Override
  public HeadFinder headFinder() {
    return headFinder;
  }

  @Override
  public HeadFinder typedDependencyHeadFinder() {
    return headFinder();
  }

  /**
   * returns an ordinary Lexicon (could be tuned for German!)
   */
  @Override
  public Lexicon lex(Options op, Index<String> wordIndex, Index<String> tagIndex) {
    if (op.lexOptions.uwModelTrainer == null) {
      op.lexOptions.uwModelTrainer = "edu.stanford.nlp.parser.lexparser.GermanUnknownWordModelTrainer";
    }
    return new BaseLexicon(op, wordIndex, tagIndex);
  }

  private NegraPennTreeReaderFactory treeReaderFactory;

  public TreeReaderFactory treeReaderFactory() {
    if(treeReaderFactory == null)
      treeReaderFactory = new NegraPennTreeReaderFactory(nodeCleanup, treeNormalizerInsertNPinPP, treeNormalizerLeaveGF, treebankLanguagePack());
    return treeReaderFactory;
  }

  /* Returns a MemoryTreebank with a NegraPennTokenizer and a
   * NegraPennTreeNormalizer */
  @Override
  public MemoryTreebank memoryTreebank() {
    return new MemoryTreebank(treeReaderFactory(), inputEncoding);
  }

  /* Returns a DiskTreebank with a NegraPennTokenizer and a
   * NegraPennTreeNormalizer */
  public DiskTreebank diskTreebank() {
    return new DiskTreebank(treeReaderFactory(), inputEncoding);
  }

  /**
   * returns a NegraPennCollinizer
   */
  @Override
  public AbstractCollinizer collinizer() {
    return new NegraPennCollinizer(this);
  }

  /**
   * returns a NegraPennCollinizer
   */
  @Override
  public AbstractCollinizer collinizerEvalb() {
    return new NegraPennCollinizer(this, false);
  }


  /* parser tuning follows */

  @Override
  public String[] sisterSplitters() {
    return new String[0];
  }


  /**
   * Set language-specific options according to flags.
   * This routine should process the option starting in args[i] (which
   * might potentially be several arguments long if it takes arguments).
   * It should return the index after the last index it consumed in
   * processing.  In particular, if it cannot process the current option,
   * the return value should be i.
   */
  @Override
  public int setOptionFlag(String[] args, int i) {
    //lang. specific options
    if (args[i].equalsIgnoreCase("-nodeCleanup")) {
      nodeCleanup = Integer.parseInt(args[i + 1]);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-leaveGF")) {
      ((NegraPennLanguagePack) treebankLanguagePack()).setLeaveGF(true);
      treeNormalizerLeaveGF = true;
      i++;
    } else if (args[i].equalsIgnoreCase("-gfCharacter")) {
      String gfChar = args[i + 1];
      if(gfChar.length() > 1)
        System.out.println("Warning! gfCharacter argument ignored; must specify a character, not a String");
      treebankLanguagePack().setGfCharacter(gfChar.charAt(0));
      i+=2;
    } else if (args[i].equalsIgnoreCase("-markZuVP")) {
      markZuVP = true;
      i++;
    } else if (args[i].equalsIgnoreCase("-markRC")) {
      markRC = true;
      i++;
    } else if (args[i].equalsIgnoreCase("-insertNPinPP")) {
      treeNormalizerInsertNPinPP = true;
      i++;
    } else if (args[i].equalsIgnoreCase("-markLP")) {
      markLP = true;
      i++;
    } else if (args[i].equalsIgnoreCase("-markColon")) {
      markColon = true;
      i++;
    } else if (args[i].equalsIgnoreCase("-markKonjParent")) {
      markKonjParent = true;
      i++;
    } else if (args[i].equalsIgnoreCase("-markHDParent")) {
      markHDParent = true;
      i++;
    } else if (args[i].equalsIgnoreCase("-markContainsV")) {
      markContainsV = true;
      i++;
    } else if (args[i].equalsIgnoreCase("-evalGF")) {
      this.setEvalGF(Boolean.parseBoolean(args[i + 1]));
      i+=2;
    } else if (args[i].equalsIgnoreCase("-headFinder") && (i + 1 < args.length)) {
      try {
        headFinder = (HeadFinder) Class.forName(args[i + 1]).getDeclaredConstructor().newInstance();
      } catch (Exception e) {
        log.info(e);
        log.info(this.getClass().getName() + ": Could not load head finder " + args[i + 1]);
      }
      i+=2;
    }
    return i;
  }

  @Override
  public void display() {
    log.info("NegraPennTreebankParserParams");
    log.info("  markZuVP=" + markZuVP);
    log.info("  insertNPinPP=" + treeNormalizerInsertNPinPP);
    log.info("  leaveGF=" + treeNormalizerLeaveGF);
    System.out.println("markLP=" + markLP);
    System.out.println("markColon=" + markColon);
  }


  private String basicCat(String str) {
    return treebankLanguagePack().basicCategory(str);
  }

  /**
   * transformTree does all language-specific tree
   * transformations. Any parameterizations should be inside the
   * specific TreebankLangParserarams class.
   */
  @Override
  public Tree transformTree(Tree t, Tree root) {
    if (t == null || t.isLeaf()) {
      return t;
    }

    List<String> annotations = new ArrayList<>();

    CoreLabel lab = (CoreLabel) t.label();
    String word = lab.word();
    String tag = lab.tag();
    String cat = lab.value();
    String baseCat = treebankLanguagePack().basicCategory(cat);

     //Tree parent = t.parent(root);

    // String mcat = "";
    // if (parent != null) {
    //   mcat = parent.label().value();
    // }

    //categories -- at present there is no tag annotation!!
    if (t.isPhrasal()) {

      List<String> childBasicCats = childBasicCats(t);

      // mark vp's headed by "zu" verbs
      if (DEBUG) {
        if (markZuVP && baseCat.equals("VP")) {
          System.out.println("child basic cats: " + childBasicCats);
        }
      }
      if (markZuVP && baseCat.equals("VP") && (childBasicCats.contains("VZ") || childBasicCats.contains("VVIZU"))) {
        if (DEBUG) System.out.println("Marked zu VP" + t);
        annotations.add("%ZU");
      }

      // mark relative clause S's
      if (markRC && (t.label() instanceof NegraLabel) && baseCat.equals("S") && ((NegraLabel) t.label()).getEdge() != null && ((NegraLabel) t.label()).getEdge().equals("RC")) {
        if (DEBUG) {
          System.out.println("annotating this guy as RC:");
          t.pennPrint();
        }
        //throw new RuntimeException("damn, not a Negra Label");

        annotations.add("%RC");
      }

//      if(t.children().length == 1) {
//        annotations.add("%U");
//      }

      if(markContainsV && containsVP(t)) {
        annotations.add("%vp");
      }

      if(markLP && leftPhrasal(t)) {
        annotations.add("%LP");
      }

      if (markKonjParent) {
        // this depends on functional tags being present
        for (String cCat : childBasicCats) {
          if (cCat.contains("-KONJ")) {
            annotations.add("%konjp");
            break;
          }
        }
      }

      if (markHDParent) {
        // this depends on functional tags being present
        for (String cCat : childBasicCats) {
          if (cCat.contains("-HD")) {
            annotations.add("%hdp");
            break;
          }
        }
      }
    } else {
      //t.isPreTerminal() case
      if (markColon && cat.equals("$.") && (word.equals(":") || word.equals(";"))) {
        annotations.add("-%colon");
      }
    }

//    if(t.isPreTerminal()) {
//      if(parent != null) {
//        String parentVal = parent.label().value();
//        int cutOffPtD = parentVal.indexOf('-');
//        int cutOffPtC = parentVal.indexOf('^');
//        int curMin = parentVal.length();
//        if(cutOffPtD != -1) {
//          curMin = cutOffPtD;
//        }
//        if(cutOffPtC != -1) {
//          curMin = Math.min(curMin, cutOffPtC);
//        }
//        parentVal = parentVal.substring(0, curMin);
//        annotations.add("^" + parentVal);
//      }
//    }
    // put on all the annotations
    StringBuilder catSB = new StringBuilder(cat);
    for (String annotation : annotations) {
      catSB.append(annotation);
    }

    t.setLabel(new CategoryWordTag(catSB.toString(), word, tag));
    return t;
  }


  private static boolean leftPhrasal(Tree t) {
    while (!t.isLeaf()) {
      t = t.lastChild();
      String str = t.label().value();
      if (str.startsWith("NP") || str.startsWith("PP") || str.startsWith("VP") || str.startsWith("S") || str.startsWith("Q") || str.startsWith("A")) {
        return true;
      }
    }
    return false;
  }

  private boolean containsVP(Tree t) {
    String cat = tlp.basicCategory(t.label().value());
    if (cat.startsWith("V")) {
      return true;
    } else {
      Tree[] kids = t.children();
      for (Tree kid : kids) {
        if (containsVP(kid)) {
          return true;
        }
      }
      return false;
    }
  }

  private List<String> childBasicCats(Tree t) {
    Tree[] kids = t.children();
    List<String> l = new ArrayList<>();
    for (Tree kid : kids) {
      l.add(basicCat(kid.label().value()));
    }
    return l;
  }


  /**
   * Return a default sentence for the language (for testing)
   */
  public List<? extends HasWord> defaultTestSentence() {
    String[] sent = {"Solch", "einen", "Zuspruch", "hat", "Angela", "Merkel", "lange", "nicht", "mehr", "erlebt", "."};
    return SentenceUtils.toWordList(sent);
  }

}
