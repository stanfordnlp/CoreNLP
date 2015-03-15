package edu.stanford.nlp.international.arabic.subject;

import java.io.*;
import java.util.*;

import edu.stanford.nlp.international.arabic.pipeline.LDCPosMapper;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.ling.Sentence;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.trees.treebank.Mapper;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;
import edu.stanford.nlp.trees.LabeledScoredTreeFactory;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeFactory;
import edu.stanford.nlp.trees.TreeReader;
import edu.stanford.nlp.trees.BobChrisTreeNormalizer.EmptyFilter;
import edu.stanford.nlp.trees.international.arabic.ATBTreeUtils;
import edu.stanford.nlp.trees.international.arabic.ArabicTreeReaderFactory;
import edu.stanford.nlp.util.Filter;

public class LabelerController {

  //Classifier labels
  public static final String subjSingleLabel = "NP_SUBJ_SINGLE";
  public static final String subjStartLabel = "NP_SUBJ_START";
  public static final String subjInsideLabel = "NP_SUBJ_IN";
  public static final String subjEndLabel = "NP_SUBJ_END";
  public static final String subjComplementLabel = "NP_NOT_SUBJ";
  public static final String verbLabel = "VERB";
  public static final String otherLabel = "OTHER";
  public static final String dummyLabel = "UNSPECIFIED";

  private static int subjectLenLimit = Integer.MAX_VALUE;

  //wsg: The sentence boundary should be a blank line so that the CRF treats each
  //sentence as a separate "document." This greatly reduces the model's memory
  //requirement (per Jenny's advice).
  private final String sentBoundary = "";

  private final String outFileName;
  private final String inFileName;

  //MADA features
  private final String madaFile;

  //POS Tagger for MT input
  private static MaxentTagger tagger;
  private String TAGGER_MODEL;

  //Gold analysis to Bies tag mapper
  private Mapper ldcMapper;
  private String MAPPER_FILE;

  //For cleaning up raw trees
  private final static Filter<Tree> emptyFilter = new EmptyFilter();
  private final static TreeFactory tf = new LabeledScoredTreeFactory();

  //Options
  private static boolean VERBOSE = false;
  private static boolean COLLAPSE_TAGS = false;
  private static boolean TEST_FLAT = false;
  private static boolean TEST_TREE = false;
  private static boolean USE_GOLD = false;

  public LabelerController(String inFile, String outFile, String madFile) {
    inFileName = inFile;
    outFileName = outFile;
    madaFile = madFile;
  }

  public void setVerbose(boolean v) {
    VERBOSE = v;
  }

  public void setOptions(boolean collapseTags, boolean testFlat,
      boolean testTree, boolean useGold, int subjLenLimit,
      String mapperFile, String modelFile) {

    COLLAPSE_TAGS = collapseTags;
    TEST_FLAT = testFlat;
    TEST_TREE = testTree;
    USE_GOLD = useGold;

    subjectLenLimit = subjLenLimit;
    TAGGER_MODEL = modelFile;
    MAPPER_FILE = mapperFile;

    if(MAPPER_FILE != null)
      initializeMapper();

    if(TAGGER_MODEL != null) {
      try {
        System.err.printf("%s: Loading MaxEnt tagger...", this.getClass().getName());
        tagger = new MaxentTagger(TAGGER_MODEL);
        System.err.println("\nDone!");
      } catch (RuntimeException e) {
        System.err.printf("%s: Could not load POS tagger model %s\n", this.getClass().getName(), TAGGER_MODEL);
        e.printStackTrace();
      }
    }
  }

  private void initializeMapper() {
    ldcMapper = new LDCPosMapper(true);
    try {
      LineNumberReader reader = new LineNumberReader(new FileReader(new File(MAPPER_FILE)));
      while(reader.ready()) {
        String fileName = reader.readLine();
        File file = new File(fileName);
        if(file.exists())
          ldcMapper.setup(file);
        else
          System.err.printf("%s: Skipping non-existant mapping file (%s)\n", this.getClass().getName(), fileName);
      }
      reader.close();
    } catch (FileNotFoundException e) {
      System.err.printf("%s: Unable to open mapping file (%s)\n", this.getClass().getName(), MAPPER_FILE);
    } catch (IOException e) {
      System.err.printf("%s: Error while reading mapper file (%s)\n", this.getClass().getName(), MAPPER_FILE);
      e.printStackTrace();
    }
  }

  public void run() {

    if(TEST_FLAT) {
      runForFlatInput();
      return;
    }

    try {
      PrintWriter crfOutFile = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outFileName),"UTF-8")));
      BufferedReader madaFeatFile = new BufferedReader(new InputStreamReader(new FileInputStream(new File(madaFile))));
      PrintWriter flatSentenceFile = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outFileName + ".raw"),"UTF-8")));

      ArabicTreeReaderFactory.ArabicRawTreeReaderFactory rf = new ArabicTreeReaderFactory.ArabicRawTreeReaderFactory();
      TreeReader treeReader = rf.newTreeReader(new InputStreamReader(new BufferedInputStream(new FileInputStream(new File(inFileName))),"UTF-8"));

      ConstituentFinder constituentFinder = new ConstituentFinder(VERBOSE);

      //Main processing loop
      int treeId = 1;
      for(Tree t; (t = treeReader.readTree()) != null; treeId++) {

        System.err.printf("\n>>> Processing tree %d <<<\n", treeId);

        t = t.prune(emptyFilter, tf);

        numberLeaves(t,0);

        List<Tree> mainSubj = constituentFinder.findSubjects(t);
        List<Tree> mainVerb = constituentFinder.findVerbs(t);
        List<Tree> nonSubjNPs = constituentFinder.findNPs(t);

        printLabeledSentence(treeId, t, crfOutFile, madaFeatFile, mainSubj, mainVerb, nonSubjNPs);
        printFlatSentence(t, flatSentenceFile);

        if((treeId % 500) == 0)
          System.out.print(".");
        else if((treeId % 10000) == 0)
          System.out.println();
      }

      System.out.printf("\n\nProcessed %d trees\n", treeId - 1);

      treeReader.close();
      crfOutFile.close();
      flatSentenceFile.close();
      madaFeatFile.close();

    }
    catch (FileNotFoundException e) {
      System.err.printf("%s: Could not open file\n", this.getClass().getName());
      e.printStackTrace();
    }
    catch (IOException e) {
      System.err.printf("%s: Unknown exception while opening files\n", this.getClass().getName());
    }
  }

  private void runForFlatInput() {

    int lineId = 0;
    try {
      BufferedReader madaFeatFile = new BufferedReader(new InputStreamReader(new FileInputStream(new File(madaFile)),"UTF-8"));
      BufferedReader infile = new BufferedReader(new InputStreamReader(new FileInputStream(new File(inFileName))));
      PrintWriter outFile = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outFileName),"UTF-8")));

      while(infile.ready()) {
        lineId++;

        String rawline = infile.readLine().trim();
        String[] rawTokens = rawline.split("\\s+");
        List<String> tokens = Arrays.asList(rawTokens);

        List<String> labels = new ArrayList<String>(Collections.nCopies(tokens.size(), dummyLabel));
        List<String> morphoFeats = parseMadaFeats(lineId,madaFeatFile,tokens.size());

        assert tokens.size() == morphoFeats.size();

        //Tag the input
        List<String> posTags = getTagsForSentence(Sentence.toUntaggedList(tokens));
        if(COLLAPSE_TAGS)
          collapseTags(posTags);

        Iterator<String> yieldItr = tokens.iterator();
        Iterator<String> labelItr = labels.iterator();
        Iterator<String> posTagItr = posTags.iterator();
        Iterator<String> featItr = morphoFeats.iterator();

        outFile.println(sentBoundary);
        while(yieldItr.hasNext() && labelItr.hasNext() && posTagItr.hasNext() && featItr.hasNext())
          outFile.printf("%s\t%s\t%s\t%s\n", yieldItr.next(), posTagItr.next(), labelItr.next(), featItr.next());

        if((lineId % 50) == 0)
          System.out.print(".");
        else if((lineId % 1000) == 0)
          System.out.println();

        if(yieldItr.hasNext())
          System.err.printf("Yield itr not finished for line %d\n", lineId);
        if(labelItr.hasNext())
          System.err.printf("Label itr not finished for line %d\n", lineId);
        if(posTagItr.hasNext())
          System.err.printf("POS tag itr not finished for line %d\n", lineId);
        if(featItr.hasNext())
          System.err.printf("Feats itr not finished for line %d\n", lineId);
      }

      System.out.printf("Processed %d flat input lines\n",lineId);

      infile.close();
      outFile.close();
      madaFeatFile.close();

    } catch (FileNotFoundException e) {
      System.err.printf("%s: Could not open file\n", this.getClass().getName());
      e.printStackTrace();
    } catch (IOException e) {
      System.err.printf("%s: Failed to read %s at line %d\n", this.getClass().getName(), inFileName, lineId);
    }
  }

  private List<String> parseMadaFeats(int treeId, BufferedReader madaFeatFile, int linesToRead) {
    List<String> feats = new ArrayList<String>(linesToRead);
    try {
      for(int i = 0; (i < linesToRead) && madaFeatFile.ready(); i++) {
        MorphoAnalysis m = MorphoAnalysis.parse(madaFeatFile.readLine());
        feats.add(m.toString());
      }
    }
    catch (IOException e) {
      System.err.printf("%s: Could not read from MADA feat file\n",this.getClass().getName());
    }

    return feats;
  }

  private void printFlatSentence(Tree t, PrintWriter outfile) {
    String sent = ATBTreeUtils.flattenTree(t);
    outfile.println(sent);
  }

  private void collapseTags(List<String> posTags) {

    for(int i = 0; i < posTags.size(); i++) {
      String thisLabel = posTags.get(i);

      if(thisLabel.matches("NN.*|NP.*"))
        posTags.set(i, "NN");
      else if(thisLabel.matches("VB.*"))
        posTags.set(i,"VB");
    }
  }

  private List<String> getTagsForSentence(ArrayList<Word> s) {
    ArrayList<TaggedWord> sentence = tagger.tagSentence(s);
    TaggedWord[] tags = sentence.toArray(new TaggedWord[sentence.size()]);
    List<String> posTags = new ArrayList<String>();
    for(int i = 0; i < tags.length; i++)
      posTags.add(tags[i].tag());

    assert posTags.size() == s.size();

    return posTags;
  }

  private void printLabeledSentence(int treeId, Tree t, PrintWriter outFile,
                                    BufferedReader madaFeatFile, List<Tree> sbjNPs,
                                    List<Tree> vb, List<Tree> nonSbjNPs)
  {
    String flatTree = ATBTreeUtils.flattenTree(t);
    String[] words = flatTree.split("\\s");
    List<String> sentence = Arrays.asList(words);

    List<String> madaFeats = parseMadaFeats(treeId, madaFeatFile, sentence.size());
    List<String> morphoFeats = madaFeats;
    List<Label> preTermYield = new ArrayList<Label>(t.preTerminalYield());
    List<String> posTags = null;

    if(TEST_TREE) { //ATB test and dev sets
      // TODO: how does this work?
      posTags = getTagsForSentence(Sentence.toUntaggedList(sentence));
    } else if(USE_GOLD) { //ATB training sets
      morphoFeats = MorphoAnalysis.parseGoldAnalyses(preTermYield,madaFeats);
      posTags = new ArrayList<String>();
      for(Label label : preTermYield) {
        String biesTag = ldcMapper.map(label.value(), null);
        posTags.add(biesTag);
      }
    } else {
      posTags = new ArrayList<String>();
      for(Label l : preTermYield)
        posTags.add(l.value());
    }

    if(COLLAPSE_TAGS)
      collapseTags(posTags);

    List<String> crfLabels = new ArrayList<String>(Collections.nCopies(sentence.size(), otherLabel));

    assert sentence.size() == morphoFeats.size();
    assert morphoFeats.size() == posTags.size();

    markNP(nonSbjNPs, crfLabels);
    markSubject(sbjNPs, crfLabels);
    markVerb(vb, crfLabels);

    assert posTags.size() == crfLabels.size();

    Iterator<String> wordItr = sentence.iterator();
    Iterator<String> crfLabelItr = crfLabels.iterator();
    Iterator<String> posTagItr = posTags.iterator();
    Iterator<String> featItr = morphoFeats.iterator();

    outFile.println(sentBoundary);
    while(wordItr.hasNext() && crfLabelItr.hasNext() && posTagItr.hasNext() && featItr.hasNext())
      outFile.printf("%s\t%s\t%s\t%s\n", wordItr.next(),posTagItr.next(), crfLabelItr.next(), featItr.next());

    if(wordItr.hasNext())
      System.err.printf("ERROR: Word itr not finished for tree %d\n", treeId);
    if(crfLabelItr.hasNext())
      System.err.printf("ERROR: CRF label itr not finished for tree %d\n", treeId);
    if(posTagItr.hasNext())
      System.err.printf("ERROR: POS tag itr not finished for tree %d\n", treeId);
    if(featItr.hasNext())
      System.err.printf("ERROR: Morpho features itr not finished for tree %d\n", treeId);
  }

  public static void setNPLabel(List<String> labels, String newLabel, List<Tree> span) {

    Iterator<Tree> itr = span.iterator();
    while(itr.hasNext()) {
      Tree leaf = itr.next();
      int index = (int) leaf.score();
      if(index >= labels.size())
        System.err.printf("ERROR: Out of bounds index %d at leaf\n", index);
      else if(newLabel.matches(".*NP_SUBJ.*") && labels.get(index).matches(".*NP_SUBJ.*"))
        System.err.printf("Skipping recursive NP-SBJ at index %d\n", index);
      else
        labels.set(index, new String(newLabel));
    }
  }

  public static void markSubject(List<Tree> sbj, List<String> labels) {
    if(sbj.size() == 0)
      return;

    Iterator<Tree> subjItr = sbj.iterator();
    while(subjItr.hasNext()) {

      Tree thisSubj = subjItr.next();
      List<Tree> scoredYield = thisSubj.getLeaves();

      if(scoredYield.size() > subjectLenLimit) {
        System.err.printf("Discarding subject of length %d (length limit = %d)\n", scoredYield.size(), subjectLenLimit);
        continue;
      } else if(scoredYield.size() == 1) {
        setNPLabel(labels,subjSingleLabel,scoredYield);
      }
      else if(scoredYield.size() == 2) {
        List<Tree> start = new ArrayList<Tree>();
        start.add(scoredYield.get(0));
        setNPLabel(labels,subjStartLabel,start);

        List<Tree> end = new ArrayList<Tree>();
        end.add(scoredYield.get(1));
        setNPLabel(labels,subjEndLabel,end);
      }
      else {
        List<Tree> start = new ArrayList<Tree>();
        start.add(scoredYield.get(0));
        setNPLabel(labels,subjStartLabel,start);

        List<Tree> end = new ArrayList<Tree>();
        end.add(scoredYield.get(scoredYield.size() - 1));
        setNPLabel(labels,subjEndLabel,end);

        scoredYield.remove(0);
        scoredYield.remove(scoredYield.size() - 1);
        setNPLabel(labels,subjInsideLabel,scoredYield);
      }
    }
  }

  private void markVerb(List<Tree> verb, List<String> labels) {

    Iterator<Tree> itr = verb.iterator();
    while(itr.hasNext())
    {
      Tree vpHead = itr.next();
      List<Tree> scoredYield = vpHead.getLeaves();
      if(scoredYield.size() != 1) {
        System.err.printf("Verb of length %d\n",scoredYield.size());
        continue;
      }

      int index = (int) scoredYield.get(0).score();
      if(!labels.get(index).matches(".*NP_SUBJ.*"))
        labels.set(index, verbLabel);
    }
  }

  private void markNP(List<Tree> nps, List<String> labels) {

    Iterator<Tree> itr = nps.iterator();
    while(itr.hasNext()) {
      Tree npHead = itr.next();
      List<Tree> scoredYield = npHead.getLeaves();
      List<Label> preTerminals = npHead.preTerminalYield();

      Iterator<Tree> itrYield = scoredYield.iterator();
      Iterator<Label> itrPreTerm = preTerminals.iterator();
      while(itrYield.hasNext() && itrPreTerm.hasNext()) {
        List<Tree> thisLeaf = new ArrayList<Tree>();
        thisLeaf.add(itrYield.next());
        setNPLabel(labels,subjComplementLabel,thisLeaf);
      }
    }
  }

  private int numberLeaves(Tree parseTree, int startIndex) {
    if(parseTree.isLeaf()) {
      parseTree.setScore((double) startIndex);
      return startIndex + 1;
    }
    else {
      for (Tree child : parseTree.children())
        startIndex = numberLeaves(child, startIndex);

      return startIndex;
    }
  }

}
