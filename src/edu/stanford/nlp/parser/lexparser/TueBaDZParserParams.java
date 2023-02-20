package edu.stanford.nlp.parser.lexparser; 
import edu.stanford.nlp.util.logging.Redwood;

import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.ling.SentenceUtils;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.trees.international.tuebadz.TueBaDZHeadFinder;
import edu.stanford.nlp.trees.international.tuebadz.TueBaDZLanguagePack;
import edu.stanford.nlp.trees.international.tuebadz.TueBaDZTreeReaderFactory;
import edu.stanford.nlp.util.Index;


/** TreebankLangParserParams for the German Tuebingen corpus.
 *
 *  The TueBaDZTreeReaderFactory has been changed in order to use a
 *  TueBaDZPennTreeNormalizer.
 *
 *  @author Roger Levy (rog@stanford.edu)
 *  @author Wolfgang Maier (wmaier@sfs.uni-tuebingen.de)
 */
public class TueBaDZParserParams extends AbstractTreebankParserParams  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(TueBaDZParserParams.class);

  private HeadFinder hf = new TueBaDZHeadFinder();

  /** How to clean up node labels: 0 = do nothing, 1 = keep category and
   *  function, 2 = just category.
   */
  private int nodeCleanup = 0;
  private boolean markKonjParent = false;
  private boolean markContainsV = true;
  private boolean markZu = true;
  private boolean markColons = false;
  private boolean leftPhrasal = false;
  private boolean markHDParent = false;
  private boolean leaveGF = false;


  public TueBaDZParserParams() {
    super(new TueBaDZLanguagePack());
  }

  /** Returns the first sentence of TueBaDZ. */
  @Override
  public List<? extends HasWord> defaultTestSentence() {
    return SentenceUtils.toWordList("Veruntreute", "die", "AWO", "Spendengeld", "?");
  }

  @Override
  public String[] sisterSplitters() {
    return new String[0];
  }

  @Override
  public AbstractCollinizer collinizer() {
    return new TreeCollinizer(treebankLanguagePack());
  }

  @Override
  public AbstractCollinizer collinizerEvalb() {
    return new TreeCollinizer(treebankLanguagePack());
  }

  @Override
  public MemoryTreebank memoryTreebank() {
    return new MemoryTreebank(treeReaderFactory());
  }

  @Override
  public DiskTreebank diskTreebank() {
    return new DiskTreebank(treeReaderFactory());
  }

  @Override
  public TreeReaderFactory treeReaderFactory() {
    return new TueBaDZTreeReaderFactory(treebankLanguagePack(), nodeCleanup);
  }

  @Override
  public Lexicon lex(Options op, Index<String> wordIndex, Index<String> tagIndex) {
    if (op.lexOptions.uwModelTrainer == null) {
      op.lexOptions.uwModelTrainer = "edu.stanford.nlp.parser.lexparser.GermanUnknownWordModelTrainer";
    }
    return new BaseLexicon(op, wordIndex, tagIndex);
  }

  /**
   * Set language-specific options according to flags.
   * This routine should process the option starting in args[i] (which
   * might potentially be several arguments long if it takes arguments).
   * It should return the index after the last index it consumed in
   * processing.  In particular, if it cannot process the current option,
   * the return value should be i.
   * <p>
   * In the TueBaDZ ParserParams, all flags take 1 argument (and so can all
   * be turned on and off).
   */
  @Override
  public int setOptionFlag(String[] args, int i) {
    // [CDM 2008: there are no generic options!] first, see if it's a generic option
    // int j = super.setOptionFlag(args, i);
    // if(i != j) return j;

    //lang. specific options
    if (args[i].equalsIgnoreCase("-nodeCleanup")) {
      nodeCleanup = Integer.parseInt(args[i + 1]);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-markKonjParent")) {
      markKonjParent = Boolean.parseBoolean(args[i+1]);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-markContainsV")) {
      markContainsV = Boolean.parseBoolean(args[i+1]);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-markZu")) {
      markZu = Boolean.parseBoolean(args[i+1]);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-markColons")) {
      markColons = Boolean.parseBoolean(args[i+1]);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-leftPhrasal")) {
      leftPhrasal = Boolean.parseBoolean(args[i+1]);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-markHDParent")) {
      markHDParent = Boolean.parseBoolean(args[i+1]);
      i += 2;
    }  else if (args[i].equalsIgnoreCase("-leaveGF")) {
      leaveGF = Boolean.parseBoolean(args[i+1]);
      ((TueBaDZLanguagePack) treebankLanguagePack()).setLeaveGF(leaveGF);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-evalGF")) {
      this.setEvalGF(Boolean.parseBoolean(args[i + 1]));
      i+=2;
    } else if (args[i].equalsIgnoreCase("-limitedGF")) {
      ((TueBaDZLanguagePack) treebankLanguagePack()).setLimitedGF(Boolean.parseBoolean(args[i + 1]));
      i+=2;
    } else if (args[i].equalsIgnoreCase("-gfCharacter")) {
      String gfChar = args[i + 1];
      if (gfChar.length() > 1) {
        System.out.println("Warning! gfCharacter argument ignored; must specify a character, not a String");
      }
      treebankLanguagePack().setGfCharacter(gfChar.charAt(0));
      i+=2;
    }

    return i;
  }

  @Override
  public void display() {
    log.info("TueBaDZParserParams nodeCleanup=" + nodeCleanup +
                       " mKonjParent=" + markKonjParent + " mContainsV=" + markContainsV +
                       " mZu=" + markZu + " mColons=" + markColons);
  }

  /** returns a {@link TueBaDZHeadFinder}. */
  @Override
  public HeadFinder headFinder() {
    return hf;
  }

  @Override
  public HeadFinder typedDependencyHeadFinder() {
    return headFinder();
  }


  /** Annotates a tree according to options. */
  @Override
  public Tree transformTree(Tree t, Tree root) {
    if (t == null || t.isLeaf()) {
      return t;
    }

    List<String> annotations = new ArrayList<>();
    Label lab = t.label();
    String word = null;
    if (lab instanceof HasWord) {
      word = ((HasWord) lab).word();
    }
    String tag = null;
    if (lab instanceof HasTag) {
      tag = ((HasTag) lab).tag();
    }
    String cat = lab.value();
    // Tree parent = t.parent(root);

    if (t.isPhrasal()) {

      List<String> childBasicCats = childBasicCats(t);

      // cdm 2008: have form for with and without functional tags since this is a hash
      if (markZu && cat.startsWith("V") && (childBasicCats.contains("PTKZU") || childBasicCats.contains("PTKZU-HD") || childBasicCats.contains("VVIZU") || childBasicCats.contains("VVIZU-HD"))) {
        annotations.add("%ZU");
      }
      if (markContainsV && containsV(t)) {
        annotations.add("%vp");
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
      // t.isPreTerminal() case
//      if (word.equals("%")) {
//        annotations.add("-%");
//      }
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
      if (markColons && cat.equals("$.") && word != null && (word.equals(":") || word.equals(";"))) {
        annotations.add("-%colon");
      }

      if(leftPhrasal && leftPhrasal(t)) {
        annotations.add("%LP");
      }


    }
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

  private List<String> childBasicCats(Tree t) {
    Tree[] kids = t.children();
    List<String> l = new ArrayList<>();
    for (Tree kid : kids) {
      l.add(basicCat(kid.label().value()));
    }
    return l;
  }

  private String basicCat(String str) {
    return tlp.basicCategory(str);
  }

  private static boolean containsV(Tree t) {
    String cat = t.label().value();
    if (cat.startsWith("V")) {
      return true;
    } else {
      Tree[] kids = t.children();
      for (Tree kid : kids) {
        if (containsV(kid)) {
          return true;
        }
      }
      return false;
    }
  }


  private static final long serialVersionUID = 7303189408025355170L;

}
