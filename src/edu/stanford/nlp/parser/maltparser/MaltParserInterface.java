package edu.stanford.nlp.parser.maltparser;

import java.util.ArrayList;
import java.util.List;

import org.maltparser.MaltParserService;
import org.maltparser.core.exception.MaltChainedException;
import org.maltparser.core.symbol.SymbolTable;
import org.maltparser.core.syntaxgraph.DependencyStructure;
import org.maltparser.core.syntaxgraph.edge.Edge;
import org.maltparser.core.syntaxgraph.edge.GraphEdge;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations.LemmaAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.StemAnnotation;
import edu.stanford.nlp.trees.EnglishGrammaticalRelations;
import edu.stanford.nlp.trees.EnglishGrammaticalStructure;
import edu.stanford.nlp.util.Filter;

/**
 * Uses MaltParserService to load a malt parser.  Uses methods in
 * EnglishGrammaticalStructure to turn the results of a parsing into a
 * EnglishGrammaticalStructure which doesn't have a real tree backing it.
 *
 * @author John Bauer horatio@gmail.com
 */
public class MaltParserInterface {

  private final MaltParserService service;

  private final String modelDirectory;
  private final String modelFile;

  public MaltParserInterface(String model)
    throws MaltChainedException
  {
    service = new MaltParserService();

    int forwardSlashIndex = model.lastIndexOf("/");
    int backwardSlashIndex = model.lastIndexOf("\\");
    int index = Math.max(backwardSlashIndex, forwardSlashIndex);
    if (index < 0) {
      modelDirectory = null;
      modelFile = model;
    } else {
      if (index == model.length()) {
        throw new IllegalArgumentException("Parser path must be a file, " +
                                           "not a directory");
      }
      modelDirectory = model.substring(0, index + 1);
      modelFile = model.substring(index + 1);
    }

    // Initialize the parser model <model> and sets the working
    // directory to '.' and sets the logging file to 'parser.log'
    String command = ("-c " + modelFile +
                      ((modelDirectory != null) ?
                       " -w " + modelDirectory : "") +
                      " -m parse -lfi parser.log");
    System.out.println(command);
    service.initializeParserModel(command);
  }

  public DependencyStructure parse(String[] tokens)
    throws MaltChainedException
  {
    return service.parse(tokens);
  }

  public DependencyStructure parse(List<? extends CoreLabel> sentence)
    throws MaltChainedException
  {
    String[] tokens = new String[sentence.size()];
    int index = 0;
    for (CoreLabel word : sentence) {
      // TODO: reuse buildConnlStructure
      StringBuilder token = new StringBuilder();
      token.append((index + 1) + "\t");
      token.append(word.word() + "\t");
      if (!word.has(LemmaAnnotation.class))
        throw new IllegalArgumentException("Words must be morphaed first");
      token.append(word.get(LemmaAnnotation.class) + "\t");
      if (word.tag() == null)
        throw new IllegalArgumentException("Words must be tagged first");
      token.append(word.tag() + "\t");
      token.append(word.tag() + "\t-");

      tokens[index] = token.toString();
      ++index;
    }

    return parse(tokens);
  }

  public static List<List<String>>
  buildConnlStructure(List<? extends CoreLabel> sentence) {
    List<List<String>> tokens = new ArrayList<List<String>>();
    int index = 0;
    for (CoreLabel word : sentence) {
      ++index;
      List<String> token = new ArrayList<String>();
      token.add(Integer.toString(index));
      token.add(word.word());
      // TODO: stem or lemma?
      if (!word.has(LemmaAnnotation.class))
        throw new IllegalArgumentException("Words must be morphaed first");
      token.add(word.get(LemmaAnnotation.class));
      if (word.tag() == null)
        throw new IllegalArgumentException("Words must be tagged first");
      token.add(word.tag());
      token.add(word.tag());

      token.add("_");
      token.add("_");
      token.add("_");
      token.add("_");
      token.add("_");

      tokens.add(token);
    }
    return tokens;
  }

  static public List<MaltDependency>
  extractDependencies(DependencyStructure structure)
    throws MaltChainedException
  {
    List<MaltDependency> dependencies = new ArrayList<MaltDependency>();
    for (Edge e : structure.getEdges()) {
      if (!(e instanceof GraphEdge))
        throw new RuntimeException("Expected all the dependency edges " +
                                   "to be of type GraphEdge");
      GraphEdge ge = (GraphEdge) e;
      int governor = ge.getSource().getIndex();
      int dependent = ge.getTarget().getIndex();
      String label = null;
      if (ge.getLabelSet() == null && governor == 0) {
        // found something else that could be considered a root
        label = "root";
        System.err.println(" --- Warning: Unlabeled root from " +
                           governor + " to " + dependent);
      } else if (ge.getLabelSet() == null) {
        // TODO: could use "dep" but do we want to cover up errors
        // like that?
        throw new RuntimeException("No label set at all with a non-root " +
                                   "governor; don't know what label to use");
      } else if (ge.getLabelSet().size() != 1) {
        throw new RuntimeException("Expected the dependency edges to have " +
                                   "exactly one label");
      } else {
        for (SymbolTable table : ge.getLabelSet().keySet()) {
          label = ge.getLabelSymbol(table);
        }
      }
      if (governor != 0 && label.equals("root")) {
        label = "dep";
        System.err.println(" --- Warning: Unexpected root label changed to " +
                           "'dep' for an edge " + governor + "->" + dependent);
      }
      //System.out.println(governor + "->" + dependent + ": " + label);
      dependencies.add(new MaltDependency(governor, dependent, label));
    }
    return dependencies;
  }

  /**
   * Given a list of CoreLabels, this feeds the CoreLabels to the malt
   * parser and then turns them into an EnglishGrammaticalStructure.
   */
  public EnglishGrammaticalStructure
  parseToGrammaticalStructure(List<? extends CoreLabel> sentence)
    throws MaltChainedException
  {
    return parseToGrammaticalStructure(sentence, null);
  }

  /**
   * Given a list of CoreLabels, this feeds the CoreLabels to the malt
   * parser and then turns them into an EnglishGrammaticalStructure.
   * Dependencies that are not accepted by the dependencyFilter are
   * ignored.
   */
  public EnglishGrammaticalStructure
  parseToGrammaticalStructure(List<? extends CoreLabel> sentence,
                              Filter<String> dependencyFilter)
    throws MaltChainedException
  {
    DependencyStructure structure = parse(sentence);
    List<List<String>> tokens = buildConnlStructure(sentence);
    List<MaltDependency> dependencies = extractDependencies(structure);

    for (MaltDependency dependency : dependencies) {
      List<String> token = tokens.get(dependency.dependent - 1);
      if (dependencyFilter != null &&
          !dependencyFilter.accept(dependency.label)) {
        token.set(6, null);
        token.set(7, null);
      } else {
        token.set(6, Integer.toString(dependency.governor));
        token.set(7, dependency.label);
      }
    }

    return EnglishGrammaticalStructure.buildCoNNLXGrammaticStructure(tokens);
  }

  public EnglishGrammaticalStructure
  parseToGrammaticalStructure(List<? extends CoreLabel> sentence,
                              boolean splitUnderscores,
                              Filter<String> dependencyFilter)
    throws MaltChainedException
  {
    if (!splitUnderscores)
      return parseToGrammaticalStructure(sentence, dependencyFilter);

    List<Boolean> wasSplitWord = new ArrayList<Boolean>();
    List<Integer> splitIndex = new ArrayList<Integer>();

    List<CoreLabel> splitSentence = new ArrayList<CoreLabel>();
    for (CoreLabel word : sentence) {
      String label = word.word();
      String[] pieces = label.split("_");
      if (pieces.length <= 1) {
        // can 0 even happen?  hopefully not
        splitSentence.add(word);
        wasSplitWord.add(false);
        splitIndex.add(wasSplitWord.size());
      } else {
        wasSplitWord.add(true);
        for (String piece : pieces) {
          CoreLabel fakeWord = new CoreLabel(word);
          fakeWord.setWord(piece);
          // TODO: will Lemma and Stem be set in the normalized words?
          // If not, add it there.  Either way, then make sure to use
          // that here instead of the piece
          fakeWord.set(LemmaAnnotation.class, piece);
          fakeWord.set(StemAnnotation.class, piece);
          fakeWord.setTag(word.tag());
          splitSentence.add(fakeWord);
          splitIndex.add(wasSplitWord.size());
        }
      }
    }

    //System.out.println(wasSplitWord);
    //System.out.println(splitIndex);
    //System.out.println(splitSentence);

    DependencyStructure structure = parse(splitSentence);
    List<MaltDependency> dependencies = extractDependencies(structure);
    List<List<String>> tokens = buildConnlStructure(sentence);

    //System.out.println(structure);
    //System.out.println(dependencies);

    // initialized to all false
    boolean[] dependencySet = new boolean[sentence.size()];
    // TODO: can imagine cases where this causes a loop.  Watch out
    // for that.  Is that a problem?
    for (MaltDependency dependency : dependencies) {
      int dependent = splitIndex.get(dependency.dependent - 1);
      int governor = (dependency.governor == 0 ? 0 :
                      splitIndex.get(dependency.governor - 1));
      if (dependent == governor)
        continue;
      // TODO: handle conflicts better than this (shortest path to
      // root, for example)
      if (dependencySet[dependent - 1])
        continue;
      List<String> token = tokens.get(dependent - 1);
      if (dependencyFilter != null &&
          !dependencyFilter.accept(dependency.label)) {
        token.set(6, null);
        token.set(7, null);
      } else {
        token.set(6, Integer.toString(dependency.governor));
        token.set(7, dependency.label);
      }
      dependencySet[dependent - 1] = true;
    }

    //System.out.println(tokens);

    return EnglishGrammaticalStructure.buildCoNNLXGrammaticStructure(tokens);
  }

  /**
   * Useful for debugging... parse the given tokens and then output
   * the results
   */
  public void output(List<CoreLabel> words)
    throws MaltChainedException
  {
    System.out.println("===============================");
    System.out.println("===============================");
    DependencyStructure dependencies = parse(words);
    List<MaltDependency> md = extractDependencies(dependencies);
    System.out.println("-------------------------------");
    System.out.println(words);
    System.out.println("-------------------------------");
    System.out.println(dependencies);
    System.out.println("-------------------------------");
    System.out.println(dependencies.getEdges());
    System.out.println("-------------------------------");
    System.out.println(md);
    System.out.println("-------------------------------");
    EnglishGrammaticalStructure gs =
      parseToGrammaticalStructure(words, false, null);
    System.out.println(gs);
    System.out.println(gs.dependencies());
    System.out.println(gs.typedDependencies());
    System.out.println("-------------------------------");
    gs = parseToGrammaticalStructure(words, true, null);
    System.out.println(gs);
    System.out.println(gs.dependencies());
    System.out.println(gs.typedDependencies());
    System.out.println("-------------------------------");
    gs = parseToGrammaticalStructure(words, false, noPunct);
    System.out.println(gs);
    System.out.println(gs.dependencies());
    System.out.println(gs.typedDependencies());
    System.out.println("-------------------------------");
    gs = parseToGrammaticalStructure(words, true, noPunct);
    System.out.println(gs);
    System.out.println(gs.dependencies());
    System.out.println(gs.typedDependencies());
    System.out.println("-------------------------------");
  }

  /**
   * A filter that accepts all dependencies except for punctuation
   */
  static public final Filter<String> noPunct = new Filter<String>() {
      public boolean accept(String value) {
        return (value != null && !value.equals(EnglishGrammaticalRelations.PUNCTUATION.getShortName()));
      }
    };

  static public CoreLabel fakeWord(String word, String tag, String lemma) {
    CoreLabel coreLabel = new CoreLabel();
    coreLabel.setWord(word);
    coreLabel.setTag(tag);
    coreLabel.set(LemmaAnnotation.class, lemma);
    return coreLabel;
  }

  static public void main(String[] args)
    throws MaltChainedException
  {
    ArrayList<CoreLabel> words = new ArrayList<CoreLabel>();
    words.add(fakeWord("Ms.",     "NNP",       "ms."));
    words.add(fakeWord("Haag",    "NNP",      "haag"));
    words.add(fakeWord("plays",   "VBZ",      "play"));
    words.add(fakeWord("Elianti", "NNP",       "ms."));
    words.add(fakeWord(".",         ".",   "elianti"));

    ArrayList<CoreLabel> words2 = new ArrayList<CoreLabel>();
    words2.add(fakeWord("Ms._Haag", "NNP",  "Ms._Haag"));
    words2.add(fakeWord("plays",    "VBZ",      "play"));
    words2.add(fakeWord("Elianti",  "NNP",       "ms."));
    words2.add(fakeWord(".",          ".",   "elianti"));

    ArrayList<CoreLabel> words3 = new ArrayList<CoreLabel>();
    words3.add(fakeWord("Some",      "DT",      "some"));
    words3.add(fakeWord("reptiles", "NNS",   "reptile"));
    words3.add(fakeWord("do",       "VBP",        "do"));
    words3.add(fakeWord("n't",       "RB",       "n't"));
    words3.add(fakeWord("like",      "IN",      "like"));
    words3.add(fakeWord("people",   "NNS",    "people"));
    words3.add(fakeWord(".",          ".",         "."));

    ArrayList<CoreLabel> words4 = new ArrayList<CoreLabel>();
    words4.add(fakeWord("David_Tennant", "NNP",  "David_Tennant"));
    words4.add(fakeWord("plays",         "VBZ",           "play"));
    words4.add(fakeWord("Doctor_Who",    "NNP",     "Doctor_Who"));
    words4.add(fakeWord(".",               ".",              "."));

    ArrayList<CoreLabel> words5 = new ArrayList<CoreLabel>();
    words5.add(fakeWord("My",             "NN",             "My"));
    words5.add(fakeWord("dog",            "NN",            "dog"));
    words5.add(fakeWord(",",               ",",              ","));
    words5.add(fakeWord("Cirrus",        "NNP",         "Cirrus"));
    words5.add(fakeWord(",",               ",",              ","));
    words5.add(fakeWord("likes",         "VBZ",           "like"));
    words5.add(fakeWord("eating",        "VBG",            "eat"));
    words5.add(fakeWord("sausage",        "NN",        "sausage"));
    words5.add(fakeWord(".",               ".",              "."));


    MaltParserInterface mpi = new MaltParserInterface(args[0]);
    mpi.output(words);
    mpi.output(words2);
    mpi.output(words3);
    mpi.output(words4);
    mpi.output(words5);
  }
}
