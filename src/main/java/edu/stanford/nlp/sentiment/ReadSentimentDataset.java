package edu.stanford.nlp.sentiment; 
import edu.stanford.nlp.util.logging.Redwood;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.process.PTBEscapingProcessor;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.LabeledScoredTreeNode;
import edu.stanford.nlp.trees.tregex.TregexPattern;
import edu.stanford.nlp.trees.tregex.tsurgeon.Tsurgeon;
import edu.stanford.nlp.trees.tregex.tsurgeon.TsurgeonPattern;
import edu.stanford.nlp.util.CollectionUtils;
import edu.stanford.nlp.util.Pair;
import java.util.function.Function;
import edu.stanford.nlp.util.Generics;

/**
 * Reads the sentiment dataset and writes it to the appropriate files.
 *
 * @author John Bauer
 */
public class ReadSentimentDataset  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(ReadSentimentDataset.class);
  static final Function<Tree, String> TRANSFORM_TREE_TO_WORD = tree -> tree.label().value();

  static final Function<String, String> TRANSFORM_PARENS = word -> {
    if (word.equals("(")) {
      return "-LRB-";
    }
    if (word.equals(")")) {
      return "-RRB-";
    }
    return word;
  };

  // A bunch of words need to be replaced - it's kind of awkward to do
  // this in code but we just want to get it done
  static final List<Pair<String, String>> singleWordReplacements = new ArrayList<Pair<String, String>>() {{
      add(new Pair<>("Stortelling", "Storytelling"));
      add(new Pair<>("surfacey", "surfacy"));
      add(new Pair<>("hotsies", "hotties"));
      add(new Pair<>("be-bop", "bebop"));
      add(new Pair<>("kibbitzes", "kibitzes"));
      add(new Pair<>("Formuliac", "Formulaic"));
      add(new Pair<>("unhibited", "uninhibited"));
      add(new Pair<>("democracie", "democracies"));
      add(new Pair<>("scuzbag", "scuzzbag"));
      add(new Pair<>("provocatuers", "provocateurs"));
      add(new Pair<>("aborbing", "absorbing"));
      add(new Pair<>("idoosyncratic", "idiosyncratic"));
      add(new Pair<>("corruscating", "coruscating"));
      add(new Pair<>("alientation", "alienation"));
    }};
  
  static private class Transformation {
    final TregexPattern tregex;
    final TsurgeonPattern surgery;
    
    Transformation(TregexPattern tregex, TsurgeonPattern surgery) {
      this.tregex = tregex;
      this.surgery = surgery;
    }
  }

  // A bunch of trees have some funky tokenization which we can
  // somewhat correct using these tregex / tsurgeon expressions.
  static final Transformation[] transformations = {
    new Transformation(TregexPattern.compile("__=single <1 (__ < /^-LRB-$/) <2 (__ <... { (__ < /^[a-zA-Z]$/=letter) ; (__ < /^-RRB-$/) }) > (__ <2 =single <1 (__=useless <<- (__=word !< __)))"),
                       Tsurgeon.parseOperation("[relabel word /^.*$/={word}={letter}/] [prune single] [excise useless useless]")),

    new Transformation(TregexPattern.compile("__=single <1 (__ < /^-LRB-$/) <2 (__ <... { (__ < /^[aA]$/=letter) ; (__ < /^-RRB-$/) }) > (__ <1 =single <2 (__=useless <<, /^n$/=word))"),
                       Tsurgeon.parseOperation("[relabel word /^.*$/={letter}n/] [prune single] [excise useless useless]")),
                           
    new Transformation(TregexPattern.compile("__=single <1 (__ < /^-LRB-$/) <2 (__=A <... { (__ < /^[aA]$/=letter) ; (__=paren < /^-RRB-$/) })"),
                       Tsurgeon.parseOperation("[excise single A] [prune paren]")),
    
    new Transformation(TregexPattern.compile("__ <1 (__ <<- (/^(?i:provide)$/=provide !<__)) <2 (__ <<, (__=s > __=useless <... { (__ <: -LRB-) ; (__ <1 (__ <: s)) } ))"),
                       Tsurgeon.parseOperation("[relabel provide /^.*$/={provide}s/] [prune s] [excise useless useless]")),
    
    new Transformation(TregexPattern.compile("__=single <1 (__ < /^-LRB-$/) <2 (__ <... { (__ < /^[a-zA-Z]$/=letter) ; (__ < /^-RRB-$/) }) > (__ <1 =single <2 (__=useless <<, (__=word !< __)))"),
                       Tsurgeon.parseOperation("[relabel word /^.*$/={letter}={word}/] [prune single] [excise useless useless]")),
    
    new Transformation(TregexPattern.compile("-LRB-=lrb !, __ : (__=ltop > __ <<, =lrb <<- (-RRB-=rrb > (__ > __=rtop)) !<< (-RRB- !== =rrb))"),
                       Tsurgeon.parseOperation("[prune lrb] [prune rrb] [excise ltop ltop] [excise rtop rtop]")),
    
    // uncensor "fucked"
    new Transformation(TregexPattern.compile("__=top <1 (__=f1 < f) <2 (__=f2 <... { (__ < /^[*\\\\]+$/) ; (__ < ed) })"),
                       Tsurgeon.parseOperation("replace top (0 fucked)")),
    
    // fix don ' t
    new Transformation(TregexPattern.compile("__=top <1 (__=f1 <1 (__ < don=do) <2 (__ < /^[\']$/=apos)) <2 (__=wrong < t)"),
                       Tsurgeon.parseOperation("[prune wrong] [relabel do do] [relabel apos /^.*$/n={apos}t/] [excise top top]")),

    // Note: the next couple leave unitary nodes, so we then fix them at the end
    
    // parens at the start of a sentence - always appears wrong
    new Transformation(TregexPattern.compile("-LRB-=lrb !, __ .. (-RRB-=rrb !< __ !.. -RRB-)"),
                       Tsurgeon.parseOperation("[prune rrb] [prune lrb]")),
    
    // parens with a single word that we can drop
    new Transformation(TregexPattern.compile("-LRB-=lrb . and|Haneke|is|Evans|Harmon|Harris|its|it|Aniston|headbanger|Testud|but|frames|yet|Denis|DeNiro|sinks|screenwriter|Cho|meditation|Watts|that|the|this|Madonna|Ahola|Franco|Hopkins|Crudup|writer-director|Diggs|very|Crane|Frei|Reno|Jones|Quills|Bobby|Hill|Kim|subjects|Wang|Jaglom|Vega|Sabara|Sade|Goldbacher|too|being|opening|enough|long|like|sci-fi=last : (=last . -RRB-=rrb)"),
                       Tsurgeon.parseOperation("[prune rrb] [prune lrb]")),
    
    // parens with two word expressions
    new Transformation(TregexPattern.compile("-LRB-=lrb . (__=n1 !< __ . (__=n2 !< __ . -RRB-=rrb)) : (=n1 (== Besson|Kissinger|Godard|Seagal|jaglon|It|it|Tsai|Nelson|Rifkan|Shakespeare|Solondz|Madonna|Herzog|Witherspoon|Woo|Eyre|there|Moore|Ricci|Seinfeld|Jaglom|Rifkin . (=n2 == /^\'s$/)) | (== Denis|Skins|Spears|Assayas . (=n2 == /^\'$/)) | (== Je-Gyu . (=n2 == is)) | (== the . (=n2 == leads|film|story|characters)) | (== Monsoon . (=n2 == Wedding)) | (== De . (=n2 == Niro)) | (== Roman . (=n2 == Coppola)) | (== than . (=n2 == Leon)) | (==Colgate . (=n2 == /^U.$/)) | (== teen . (=n2 == comedy)) | (== a . (=n2 == remake)) | (== Powerpuff . (=n2 == Girls)) | (== Woody . (=n2 == Allen)))"),
                       Tsurgeon.parseOperation("[prune rrb] [prune lrb]")),
    
    // parens with three word expressions
    new Transformation(TregexPattern.compile("-LRB-=lrb . (__=n1 !< __ . (__=n2 !< __ . (__=n3 !< __ . -RRB-=rrb))) : (=n1 [ (== the . (=n2 == characters . (=n3 == /^\'$/))) | (== the . (=n2 == movie . (=n3 == /^\'s$/))) | (== of . (=n2 == middle-aged . (=n3 == romance))) | (== Binoche . (=n2 == and . (=n3 == Magimel))) | (==the . (=n2 == Funk . (=n3 == Brothers))) | (== Jack . (=n2 == Nicholson . (=n3 == /^\'s$/))) | (== De . (=n2 == Palma . (=n3 == /^\'s$/))) | (== Clara . (=n2 == and . (=n3 == Paul))) | (== Sex . (=n2 == and . (=n3 == Lucía))) ])"),
                       Tsurgeon.parseOperation("[prune rrb] [prune lrb]")),
    
    // parens with four word expressions
    new Transformation(TregexPattern.compile("-LRB-=lrb . (__=n1 !< __ . (__=n2 !< __ . (__=n3 !< __ . (__=n4 !< __ . -RRB-=rrb)))) : (=n1 (== Scorsese . (=n2 ==/^\'s$/ . (=n3 == Mean . (=n4 == Streets)))) | (== the . (=n2 ==warden . (=n3 ==/^\'s$/ . (=n4 ==daughter)))) | (== Attal . (=n2 ==and . (=n3 ==Gainsbourg . (=n4 ==/^\'s$/)))) | (== as . (=n2 ==The . (=n3 ==Full . (=n4 ==Monty)))) | (== wo . (=n2 ==n't . (=n3 ==be . (=n4 ==an)))) )"),
                       Tsurgeon.parseOperation("[prune rrb] [prune lrb]")),
    
    // parens with five word expressions - one example
    new Transformation(TregexPattern.compile("-LRB-=lrb . (Time . (Out . (and . (Human . (Resources . -RRB-=rrb)))))"),
                       Tsurgeon.parseOperation("[prune rrb] [prune lrb]")),
    
    // only one of these, so can be very general
    new Transformation(TregexPattern.compile("/^401$/ > (__ > __=top)"),
                       Tsurgeon.parseOperation("replace top (2 (2 401k) (2 statement))")),
    
    new Transformation(TregexPattern.compile("by . (all > (__=all > __=allgp) . (means > (__=means > __=meansgp))) : (=allgp !== =meansgp)"),
                       Tsurgeon.parseOperation("[move means $- all] [excise meansgp meansgp] [createSubtree 2 all means]")),
    
    // 20th century, 21st century
    new Transformation(TregexPattern.compile("/^(?:20th|21st)$/ . Century=century"),
                       Tsurgeon.parseOperation("relabel century century")),

    // empowerment was separated, with a stray ' at the end of the sentence
    new Transformation(TregexPattern.compile("__=top < (__ < /'em/) < (__ < /powerment/) >> (__ !> __ < (__=apos < /'/))"),
                       Tsurgeon.parseOperation("[replace top (3 empowerment)] [prune apos]")),

    // Blair Witch-style was mangled in the parsing
    new Transformation(TregexPattern.compile("/Witch-style/ > (/2/ > (/2/ > (/3/=top < (/2/ < Blair))))"),
                       Tsurgeon.parseOperation("[replace top (3 (2 (2 Blair) (2 (2 Witch) (2 (2 -) (2 style)))) (2 commitment))]")),

    // huge-screen also got mis-attached.  This creates some unaries which get pruned later
    new Transformation(TregexPattern.compile("/huge-screen/ > (/3/=huge > (/3/ < (/3/ < (/2/=format < format)) > (/3/ < /2/=the)))"),
                       Tsurgeon.parseOperation("[replace the (2 (2 the) (2 (2 (2 huge) (2 (2 -) (2 screen))) (2 format)))] " +
                                               "[prune huge] [prune format]")),

    // another dash that got mangled
    new Transformation(TregexPattern.compile("/Banderas-Lucy/ > (/2/=oops > (/2/ > (/2/ < (/2/=banderas < Antonio)) < (/2/ < (/2/=liu < Liu))))"),
                       Tsurgeon.parseOperation("[replace liu (2 (2 Lucy) (2 Liu))] " +
                                               "[replace banderas (2 (2 Antonio) (2 Banderas))] " +
                                               "[replace oops (2 -)] ")),
    new Transformation(TregexPattern.compile("/already-shallow/ > /1/=shallow . (genre > /2/=genre)"),
                       Tsurgeon.parseOperation("[prune genre] [replace shallow (1 (1 (2 already) (2 (2 -) (2 shallow))) (2 genre))]")),
    new Transformation(TregexPattern.compile("/Pie-like/ > /2/=pie , (American > /2/=american)"),
                       Tsurgeon.parseOperation("[prune pie] " +
                                               "[replace american (2 (2 American) (2 (2 Pie) (2 (2 (2 -) (2 like)))))]")),
    new Transformation(TregexPattern.compile("/([0-9]+)-minute/#1%time=node > __=parent"),
                       Tsurgeon.parseOperation("[relabel node /^.*$/%{time}/] " + // turn 88-minute into 88
                                               "[adjoin (2 (2@) (2 (2 -) (2 minute))) parent]")), // add - minute as new nodes
    
    //new Transformation(TregexPattern.compile("again-courage=again > (__ > (__ > (__=fork <2 __=courage)))"),
    //                   Tsurgeon.parseOperation("[relabel again again]")),

    // Fix any stranded unitary nodes
    new Transformation(TregexPattern.compile("__ <: (__=unitary < __)"),
                       Tsurgeon.parseOperation("[excise unitary unitary]")),

    // 2 for 1: misspelled AND mistokenized!
    new Transformation(TregexPattern.compile("__=top < (__ < Pasach)"),
                       Tsurgeon.parseOperation("[replace top (2 (2 Pesach) (2 Burstein))]")),

    // relabel some nodes where punctuation changes the score for no apparent reason
    // new Transformation(TregexPattern.compile("__=node <2 (__ < /^[!.?,;]$/) !<1 ~node <1 __=child > ~child"),
    // Tsurgeon.parseOperation("relabel node /^.*$/={child}/")),

    // TODO: relabel words in some less expensive way?
    new Transformation(TregexPattern.compile("/^[1]$/=label <: /^(?i:protagonist)$/"),
                       Tsurgeon.parseOperation("relabel label /^.*$/2/")),

    new Transformation(TregexPattern.compile("/^[2]$/=label <: /^(?i:courage)$/"),
                       Tsurgeon.parseOperation("relabel label /^.*$/3/")),
  };


  static final Transformation[] binaryTransformations = {
    new Transformation(TregexPattern.compile("/^[2]$/=label < __"),  // non-leaf
                       Tsurgeon.parseOperation("relabel label /^.*$/-1/")),

    new Transformation(TregexPattern.compile("/^[1]$/=label < __"),  // non-leaf
                       Tsurgeon.parseOperation("relabel label /^.*$/0/")),

    new Transformation(TregexPattern.compile("/^[34]$/=label < __"),  // non-leaf
                       Tsurgeon.parseOperation("relabel label /^.*$/1/")),
  };

  static final Transformation[] threeClassTransformations = {
    new Transformation(TregexPattern.compile("/^[1]$/=label < __"),  // non-leaf
                       Tsurgeon.parseOperation("relabel label /^.*$/0/")),

    new Transformation(TregexPattern.compile("/^[2]$/=label < __"),  // non-leaf
                       Tsurgeon.parseOperation("relabel label /^.*$/1/")),

    new Transformation(TregexPattern.compile("/^[34]$/=label < __"),  // non-leaf
                       Tsurgeon.parseOperation("relabel label /^.*$/2/")),
  };

  private ReadSentimentDataset() {} // static class

  public static Tree convertTree(List<Integer> parentPointers, List<String> sentence, Map<List<String>, Integer> phraseIds, Map<Integer, Double> sentimentScores, PTBEscapingProcessor escaper, int numClasses) {
    int maxNode = 0;
    for (Integer parent : parentPointers) {
      maxNode = Math.max(maxNode, parent);
    }

    Tree[] subtrees = new Tree[maxNode + 1];
    for (int i = 0; i < sentence.size(); ++i) {
      CoreLabel word = new CoreLabel();
      word.setValue(sentence.get(i));
      Tree leaf = new LabeledScoredTreeNode(word);
      subtrees[i] = new LabeledScoredTreeNode(new CoreLabel());
      subtrees[i].addChild(leaf);
    }

    for (int i = sentence.size(); i <= maxNode; ++i) {
      subtrees[i] = new LabeledScoredTreeNode(new CoreLabel());
    }

    boolean[] connected = new boolean[maxNode + 1];
    Tree root = null;
    for (int index = 0; index < parentPointers.size(); ++index) {
      if (parentPointers.get(index) == -1) {
        if (root != null) {
          throw new RuntimeException("Found two roots for sentence " + sentence);
        }
        root = subtrees[index];
      } else {
        // Walk up the tree structure to make sure that leftmost
        // phrases are added first.  Otherwise, if the numbers are
        // inverted, we might get the right phrase added to a parent
        // first, resulting in "case zero in this", for example,
        // instead of "in this case zero"
        // Note that because we keep track of which ones are already
        // connected, we process this at most once per parent, so the
        // overall construction time is still efficient.
        connect(parentPointers, subtrees, connected, index);
      }
    }

    for (int i = 0; i <= maxNode; ++i) {
      List<Tree> leaves = subtrees[i].getLeaves();
      List<String> words = CollectionUtils.transformAsList(leaves, TRANSFORM_TREE_TO_WORD);
      // First we look for a copy of the phrase with -LRB- -RRB-
      // instead of ().  The sentiment trees sometimes have both, and
      // the escaped versions seem to have more reasonable scores.
      // If a particular phrase doesn't have -LRB- -RRB- we fall back
      // to the unescaped versions.
      Integer phraseId = phraseIds.get(CollectionUtils.transformAsList(words, TRANSFORM_PARENS));
      if (phraseId == null) {
        phraseId = phraseIds.get(words);
      }
      if (phraseId == null) {
        throw new RuntimeException("Could not find phrase id for phrase " + sentence);
      }
      // TODO: should we make this an option?  Perhaps we want cases
      // where the trees have the phrase id and not their class
      Double score = sentimentScores.get(phraseId);
      if (score == null) {
        throw new RuntimeException("Could not find sentiment score for phrase id " + phraseId);
      }

      int classLabel = Math.round((float) Math.floor(score * (float) 5));
      if (classLabel > 4 || classLabel < 0) {
        throw new RuntimeException("Unexpected class label: score " + score + " became " + classLabel);
      }
      subtrees[i].label().setValue(Integer.toString(classLabel));
    }

    for (int i = 0; i < sentence.size(); ++i) {
      Tree leaf = subtrees[i].children()[0];
      for (Pair<String, String> replacement : singleWordReplacements) {
        if (leaf.label().value().equals(replacement.first)) {
          leaf.label().setValue(replacement.second);
        }
      }
      leaf.label().setValue(escaper.escapeString(leaf.label().value()));
    }

    for (int i = 0; i < transformations.length; ++i) {
      root = Tsurgeon.processPattern(transformations[i].tregex,
                                     transformations[i].surgery, root);
    }

    if (numClasses == 2) {
      for (Transformation trans : binaryTransformations) {
        root = Tsurgeon.processPattern(trans.tregex, trans.surgery, root);
      }
    } else if (numClasses == 3) {
      for (Transformation trans : threeClassTransformations) {
        root = Tsurgeon.processPattern(trans.tregex, trans.surgery, root);
      }
    }

    return root;
  }

  private static void connect(List<Integer> parentPointers, Tree[] subtrees, boolean[] connected, int index) {
    if (connected[index]) {
      return;
    }
    if (parentPointers.get(index) < 0) {
      return;
    }
    subtrees[parentPointers.get(index)].addChild(subtrees[index]);
    connected[index] = true;
    connect(parentPointers, subtrees, connected, parentPointers.get(index));
  }

  private static void writeTrees(String filename, List<Tree> trees, List<Integer> treeIds) {
    try {
      FileOutputStream fos = new FileOutputStream(filename);
      BufferedWriter bout = new BufferedWriter(new OutputStreamWriter(fos));

      for (Integer id : treeIds) {
        bout.write(trees.get(id).toString());
        bout.write("\n");
      }
      bout.flush();
      fos.close();
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
  }

  /**
   * This program converts the format of the Sentiment data set
   * prepared by Richard, Jean, etc. into trees readable with the
   * normal TreeReaders.
   * <br>
   * An example command line is
   * <br>
   * <code>java edu.stanford.nlp.sentiment.ReadSentimentDataset -dictionary stanfordSentimentTreebank/dictionary.txt -sentiment stanfordSentimentTreebank/sentiment_labels.txt -tokens stanfordSentimentTreebank/SOStr.txt -parse stanfordSentimentTreebank/STree.txt  -split stanfordSentimentTreebank/datasetSplit.txt  -train train.txt -dev dev.txt -test test.txt</code>
   * <br>
   * The arguments are as follows: <br>
   * <code>-dictionary</code>, <code>-sentiment</code>,
   * <code>-tokens</code>, <code>-parse</code>, <code>-split</code>
   * Path to the corresponding files from the dataset <br>
   * <code>-train</code>, <code>-dev</code>, <code>-test</code>
   * Paths for saving the corresponding output files <br>
   * Each of these arguments is required.
   * <br>
   * Macro arguments exist in -inputDir and -outputDir, so you can for example run <br>
   * <code>java edu.stanford.nlp.sentiment.ReadSentimentDataset -inputDir /u/nlp/data/sentiment/stanfordSentimentTreebank  -outputDir .</code>
   * or if you're on my laptop:
   * <code>java edu.stanford.nlp.sentiment.ReadSentimentDataset -inputDir ../data/sentiment/stanfordSentimentTreebank  -outputDir .</code>
   * or if you're on my desktop (please go away):
   * <code>java edu.stanford.nlp.sentiment.ReadSentimentDataset -inputDir ../../extern_data/sentiment/stanfordSentimentTreebank -outputDir ../../extern_data/sentiment/sst/fiveclass</code>
   * <code>java edu.stanford.nlp.sentiment.ReadSentimentDataset -inputDir ../../extern_data/sentiment/stanfordSentimentTreebank -outputDir ../../extern_data/sentiment/sst/binary -numClasses 2</code>
   */
  public static void main(String[] args) {
    String dictionaryFilename = null;
    String sentimentFilename = null;
    String tokensFilename = null;
    String parseFilename = null;
    String splitFilename = null;

    String trainFilename = null;
    String devFilename = null;
    String testFilename = null;

    int numClasses = 5;

    // find numClasses first so we can dynamically change the names of
    // the ouput files
    for (int argIndex = 0; argIndex < args.length; ++argIndex) {
      if (args[argIndex].equalsIgnoreCase("-numClasses")) {
        numClasses = Integer.parseInt(args[argIndex + 1]);
        if (numClasses != 2 && numClasses != 3 && numClasses != 5) {
          throw new IllegalArgumentException("numClasses must be 2, 3, or 5");
        }
        break;
      }
    }

    int argIndex = 0;
    while (argIndex < args.length) {
      if (args[argIndex].equalsIgnoreCase("-dictionary")) {
        dictionaryFilename = args[argIndex + 1];
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-sentiment")) {
        sentimentFilename = args[argIndex + 1];
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-tokens")) {
        tokensFilename = args[argIndex + 1];
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-parse")) {
        parseFilename = args[argIndex + 1];
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-split")) {
        splitFilename = args[argIndex + 1];
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-inputDir") ||
                 args[argIndex].equalsIgnoreCase("-inputDirectory")) {
        dictionaryFilename = args[argIndex + 1] + "/dictionary.txt";
        sentimentFilename = args[argIndex + 1] + "/sentiment_labels.txt";
        tokensFilename = args[argIndex + 1] + "/SOStr.txt";
        parseFilename = args[argIndex + 1] + "/STree.txt";
        splitFilename = args[argIndex + 1] + "/datasetSplit.txt";
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-train")) {
        trainFilename = args[argIndex + 1];
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-dev")) {
        devFilename = args[argIndex + 1];
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-test")) {
        testFilename = args[argIndex + 1];
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-outputDir") ||
                 args[argIndex].equalsIgnoreCase("-outputDirectory")) {
        if (numClasses == 2) {
          trainFilename = args[argIndex + 1] + "/train-binary.txt";
          devFilename = args[argIndex + 1] + "/dev-binary.txt";
          testFilename = args[argIndex + 1] + "/test-binary.txt";
        } else if (numClasses == 3) {
          trainFilename = args[argIndex + 1] + "/train-3class.txt";
          devFilename = args[argIndex + 1] + "/dev-3class.txt";
          testFilename = args[argIndex + 1] + "/test-3class.txt";
        } else {
          trainFilename = args[argIndex + 1] + "/train.txt";
          devFilename = args[argIndex + 1] + "/dev.txt";
          testFilename = args[argIndex + 1] + "/test.txt";
        }
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-numClasses")) {
        // already been processed
        argIndex += 2;
      } else {
        throw new IllegalArgumentException("Unknown argument " + args[argIndex]);
      }
    }

    // Sentence file is formatted
    //   w1|w2|w3...
    List<List<String>> sentences = Generics.newArrayList();
    for (String line : IOUtils.readLines(tokensFilename, "utf-8")) {
      String[] sentence = line.split("\\|");
      sentences.add(Arrays.asList(sentence));
    }

    // Split and read the phrase ids file.  This file is in the format
    //   w1 w2 w3 ... | id
    Map<List<String>, Integer> phraseIds = Generics.newHashMap();
    for (String line : IOUtils.readLines(dictionaryFilename, "utf-8")) {
      String[] pieces = line.split("\\|");
      String[] sentence = pieces[0].split(" ");
      Integer id = Integer.valueOf(pieces[1]);
      phraseIds.put(Arrays.asList(sentence), id);
    }

    // Split and read the sentiment scores file.  Each line of this
    // file is of the format:
    //   phrasenum | score
    Map<Integer, Double> sentimentScores = Generics.newHashMap();
    for (String line : IOUtils.readLines(sentimentFilename, "utf-8")) {
      if (line.startsWith("phrase")) {
        continue;
      }
      String[] pieces = line.split("\\|");
      Integer id = Integer.valueOf(pieces[0]);
      final Double score;
      if (pieces[1].equals("1")) {
        score = 0.9999;
      } else {
        score = Double.valueOf(pieces[1]);
      }
      sentimentScores.put(id, score);
    }

    // Read lines from the tree structure file.  This is a file of parent pointers for each tree.
    int index = 0;
    PTBEscapingProcessor escaper = new PTBEscapingProcessor();
    List<Tree> trees = Generics.newArrayList();
    for (String line : IOUtils.readLines(parseFilename, "utf-8")) {
      String[] pieces = line.split("\\|");
      List<Integer> parentPointers = CollectionUtils.transformAsList(Arrays.asList(pieces), arg -> Integer.valueOf(arg) - 1);
      Tree tree = convertTree(parentPointers, sentences.get(index), phraseIds, sentimentScores, escaper, numClasses);
      ++index;
      trees.add(tree);
    }

    Map<Integer, List<Integer>> splits = Generics.newHashMap();
    splits.put(1, Generics.<Integer>newArrayList());
    splits.put(2, Generics.<Integer>newArrayList());
    splits.put(3, Generics.<Integer>newArrayList());
    for (String line : IOUtils.readLines(splitFilename, "utf-8")) {
      if (line.startsWith("sentence_index")) {
        continue;
      }
      String[] pieces = line.split(",");
      Integer treeId = Integer.valueOf(pieces[0]) - 1;
      Integer fileId = Integer.valueOf(pieces[1]);
      splits.get(fileId).add(treeId);
    }

    writeTrees(trainFilename, trees, splits.get(1));
    writeTrees(testFilename, trees, splits.get(2));
    writeTrees(devFilename, trees, splits.get(3));
  }
}
