
/* 
* 	@Author:  Danqi Chen
* 	@Email:  danqi@cs.stanford.edu
*	@Created:  2014-08-25
* 	@Last Modified:  2014-10-05
*/

package edu.stanford.nlp.parser.nndep;

import edu.stanford.nlp.ling.HasTag;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.parser.nndep.util.ArcStandard;
import edu.stanford.nlp.parser.nndep.util.CONST;
import edu.stanford.nlp.parser.nndep.util.Configuration;
import edu.stanford.nlp.parser.nndep.util.Counter;
import edu.stanford.nlp.parser.nndep.util.DependencyTree;
import edu.stanford.nlp.parser.nndep.util.ParsingSystem;

import edu.stanford.nlp.parser.nndep.util.Util;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.trees.EnglishGrammaticalRelations;
import edu.stanford.nlp.trees.EnglishGrammaticalStructure;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.trees.GrammaticalStructure;
import edu.stanford.nlp.trees.TreeGraphNode;
import edu.stanford.nlp.trees.TypedDependency;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.PropertiesUtils;

import java.util.*;
import java.io.*;
import java.util.stream.Collectors;

public class NNParser 
{
  public static final String DEFAULT_MODEL = "edu/stanford/nlp/models/parser/nndep/PTB_Stanford_params.txt.gz";

	List<String> wordDict, posDict, labelDict;
	Map<String, Integer> wordMap, posMap, labelMap;
	Dataset trainSet;

    List<Integer> preComputed;

	Classifier classifier;
	ParsingSystem system;

    Map<String, Integer> embedID;
    double[][] embeddings;

  private final Config config;

  NNParser() {
    this(new Properties());
  }

  public NNParser(Properties properties) {
    config = new Config(properties);
  }

	public int getWordID(String s)
	{
        //NOTE: to use the previous trained parameters, we need to add one line
        if (s == CONST.ROOT) s = CONST.NULL;
		return wordMap.containsKey(s) ? wordMap.get(s) : wordMap.get(CONST.UNKNOWN);
	}

	public int getPosID(String s)
	{
        //NOTE: to use the previous trained parameters, we need to add one line
        if (s == CONST.ROOT) s = CONST.NULL;
		return posMap.containsKey(s) ? posMap.get(s) : posMap.get(CONST.UNKNOWN);
	}

	public int getLabelID(String s)
	{
		return labelMap.get(s);
	}

	public List<Integer> getFeatures(Configuration c)
	{
		List<Integer> fWord = new ArrayList<Integer>();
		List<Integer> fPos = new ArrayList<Integer>();
		List<Integer> fLabel = new ArrayList<Integer>();
		for (int j = 2; j >= 0; -- j)
		{
			int index = c.getStack(j);
			fWord.add(getWordID(c.getWord(index)));
			fPos.add(getPosID(c.getPOS(index)));
		}
		for (int j = 0; j <= 2; ++ j)
		{
			int index = c.getBuffer(j);
			fWord.add(getWordID(c.getWord(index)));
			fPos.add(getPosID(c.getPOS(index)));
		}
		for (int j = 0; j <= 1; ++ j)
		{
			int k = c.getStack(j);
			int index = c.getLeftChild(k);
			fWord.add(getWordID(c.getWord(index)));
			fPos.add(getPosID(c.getPOS(index)));
			fLabel.add(getLabelID(c.getLabel(index)));

			index = c.getRightChild(k);
			fWord.add(getWordID(c.getWord(index)));
			fPos.add(getPosID(c.getPOS(index)));
			fLabel.add(getLabelID(c.getLabel(index)));

			index = c.getLeftChild(k, 2);
			fWord.add(getWordID(c.getWord(index)));
			fPos.add(getPosID(c.getPOS(index)));
			fLabel.add(getLabelID(c.getLabel(index)));

			index = c.getRightChild(k, 2);
			fWord.add(getWordID(c.getWord(index)));
			fPos.add(getPosID(c.getPOS(index)));
			fLabel.add(getLabelID(c.getLabel(index)));

			index = c.getLeftChild(c.getLeftChild(k));
			fWord.add(getWordID(c.getWord(index)));
			fPos.add(getPosID(c.getPOS(index)));
			fLabel.add(getLabelID(c.getLabel(index)));

			index = c.getRightChild(c.getRightChild(k));
			fWord.add(getWordID(c.getWord(index)));
			fPos.add(getPosID(c.getPOS(index)));
			fLabel.add(getLabelID(c.getLabel(index)));
		}

		List<Integer> feature = new ArrayList<Integer>(fWord);
		feature.addAll(fPos);
		feature.addAll(fLabel);
		return feature;
	}

	public void genTrainExamples(List<CoreMap> sents, List<DependencyTree> trees)
	{
		trainSet = new Dataset(config.numTokens, system.transitions.size());

        Counter<Integer> tokPosCount = new Counter<Integer>();
		System.out.println(CONST.SEPARATOR);
		System.out.println("Generate training examples...");

		for (int i = 0; i < sents.size(); ++ i)
		{
			if (i > 0)
			{
				if (i % 1000 == 0)
					System.out.print(i + " ");
				if (i % 10000 == 0 || i == sents.size() - 1)
					System.out.println();
			}

			if (trees.get(i).isProjective())
			{
				Configuration c = system.initialConfiguration(sents.get(i));
				//NOTE: here I use 2n transitions instead of 2n-1 transitions.
				for (int k = 0; k < trees.get(i).n * 2; ++ k)
				{
					String oracle = system.getOracle(c, trees.get(i));
					List<Integer> feature = getFeatures(c);
					List<Integer> label = new ArrayList<Integer>();
					for (int j = 0; j < system.transitions.size(); ++ j)
					{
						String str = system.transitions.get(j);
						if (str.equals(oracle)) label.add(1);
							else if (system.canApply(c, str)) label.add(0);
								else label.add(-1);
					}
					trainSet.addExample(feature, label);
                    for (int j = 0; j < feature.size(); ++ j)
                        tokPosCount.add(feature.get(j) * feature.size() + j);
					system.apply(c, oracle);
				}
			}
		}
		System.out.println("#Train Examples: " + trainSet.n);

        preComputed = tokPosCount.getSortedKeys(config.numPreComputed);
	}

    public void genMapping()
    {
        wordMap = new HashMap<String, Integer>();
        posMap = new HashMap<String, Integer>();
        labelMap = new HashMap<String, Integer>();

        int index = 0;
        for (int i = 0; i < wordDict.size(); ++ i)
            wordMap.put(wordDict.get(i), (index++));
        for (int i = 0; i < posDict.size(); ++ i)
            posMap.put(posDict.get(i), (index++));
        for (int i = 0; i < labelDict.size(); ++ i)
            labelMap.put(labelDict.get(i), (index++));
    }

  public void genDictionaries(List<CoreMap> sents, List<DependencyTree> trees)
  {
    List<String> word = new ArrayList<String>();
    List<String> pos = new ArrayList<String>();
    List<String> label = new ArrayList<String>();

    for (CoreMap sentence : sents) {
      List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);

      for (CoreLabel token : tokens) {
        word.add(token.word());
        pos.add(token.tag());
      }
    }

    String rootLabel = null;
    for (int i = 0; i < trees.size(); ++ i)
      for (int k = 1; k <= trees.get(i).n; ++ k)
        if (trees.get(i).getHead(k) == 0)
          rootLabel = trees.get(i).getLabel(k);
        else
          label.add(trees.get(i).getLabel(k));

		wordDict = Util.generateDict(word, config.wordCutOff);
		posDict = Util.generateDict(pos);
		labelDict = Util.generateDict(label);
		labelDict.add(0, rootLabel);

		wordDict.add(0, CONST.UNKNOWN);
		wordDict.add(1, CONST.NULL);
		wordDict.add(2, CONST.ROOT);

		posDict.add(0, CONST.UNKNOWN);
		posDict.add(1, CONST.NULL);
		posDict.add(2, CONST.ROOT);

		labelDict.add(0, CONST.NULL);
        genMapping();

		System.out.println(CONST.SEPARATOR);
		System.out.println("#Word: " + wordDict.size());
		System.out.println("#POS:" + posDict.size());
		System.out.println("#Label: " + labelDict.size());
	}

	public void writeModelFile(String modelFile)
	{
		try
		{
            double[][] W1 = classifier.getW1();
            double[] b1 = classifier.getb1();
            double[][] W2 = classifier.getW2();
            double[][] E = classifier.getE();

			BufferedWriter output = new BufferedWriter(new FileWriter(modelFile));
            output.write("dict="+wordDict.size()+"\n");
            output.write("pos="+posDict.size()+"\n");
            output.write("label="+labelDict.size()+"\n");
            output.write("embeddingSize="+E[0].length+"\n");
            output.write("hiddenSize="+b1.length+"\n");
            output.write("numTokens="+(W1[0].length/E[0].length)+"\n");
            output.write("preComputed="+preComputed.size()+"\n");

            int index = 0;
            for (int i = 0; i < wordDict.size(); ++ i)
            {
                output.write(wordDict.get(i));
                for (int k = 0; k < E[index].length; ++ k)
                    output.write(" " + E[index][k]);
                output.write("\n");
                index = index + 1;
            }
            for (int i = 0; i < posDict.size(); ++ i)
            {
                output.write(posDict.get(i));
                for (int k = 0; k < E[index].length; ++ k)
                    output.write(" " + E[index][k]);
                output.write("\n");
                index = index + 1;
            }
            for (int i = 0; i < labelDict.size(); ++ i)
            {
                output.write(labelDict.get(i));
                for (int k = 0; k < E[index].length; ++ k)
                    output.write(" " + E[index][k]);
                output.write("\n");
                index = index + 1;
            }
            for (int j = 0; j < W1[0].length; ++ j)
                for (int i = 0; i < W1.length; ++ i)
                {
                    output.write("" + W1[i][j]);
                    if (i == W1.length - 1)
                        output.write("\n");
                    else
                        output.write(" ");
                }
            for (int i = 0; i < b1.length; ++ i)
            {
                output.write("" + b1[i]);
                if (i == b1.length - 1)
                    output.write("\n");
                else
                    output.write(" ");
            }
            for (int j = 0; j < W2[0].length; ++ j)
                for (int i = 0; i < W2.length; ++ i)
                {
                    output.write("" + W2[i][j]);
                    if (i == W2.length - 1)
                        output.write("\n");
                    else
                        output.write(" ");
                }
            for (int i = 0; i < preComputed.size(); ++ i)
            {
                output.write("" + preComputed.get(i));
                if ((i + 1) % 100 == 0 || i == preComputed.size() - 1)
                    output.write("\n");
                else
                    output.write(" ");
            }
            output.close();
		}
		catch (Exception e) { System.out.println(e); }
	}

  /**
   * Convenience method; see
   * {@link #loadFromModelFile(String, java.util.Properties)}.
   *
   * @see #loadFromModelFile(String, java.util.Properties)
   */
  public static NNParser loadFromModelFile(String modelFile) {
    return loadFromModelFile(modelFile, null);
  }

  /**
   * Load a saved parser model.
   *
   * @param modelFile Path to serialized model (may be GZipped)
   * @param extraProperties Extra test-time properties not already
   *                        associated with model (may be null)
   * @return Loaded and initialized (see {@link #initialize()} model
   */
  public static NNParser loadFromModelFile(String modelFile, Properties extraProperties) {
    NNParser parser = extraProperties == null ? new NNParser() : new NNParser(extraProperties);
    parser.loadModelFile(modelFile);
    parser.initialize();
    return parser;
  }

  // TODO replace with GrammaticalStructure's CoNLL loader
  private void loadModelFile(String modelFile) {
    try {
      System.out.println(CONST.SEPARATOR);
      System.out.println("Loading Model File: " + modelFile);
      String s;
      BufferedReader input = IOUtils.readerFromString(modelFile);

      int nDict, nPOS, nLabel;
      int eSize, hSize, nTokens, nPreComputed;
      nDict = nPOS = nLabel = eSize = hSize = nTokens = nPreComputed = 0;

      for (int k = 0; k < 7; ++k) {
        s = input.readLine();
        System.out.println(s);
        int number = Integer.parseInt(s.substring(s.indexOf("=") + 1, s.length()));
        switch (k) {
          case 0:
            nDict = number;
          case 1:
            nPOS = number;
          case 2:
            nLabel = number;
          case 3:
            eSize = number;
          case 4:
            hSize = number;
          case 5:
            nTokens = number;
          case 6:
            nPreComputed = number;
          default:
            break;
        }
      }

      wordDict = new ArrayList<String>();
      posDict = new ArrayList<String>();
      labelDict = new ArrayList<String>();
      double[][] E = new double[nDict + nPOS + nLabel][eSize];
      String[] splits;
      int index = 0;

      for (int k = 0; k < nDict; ++k) {
        s = input.readLine();
        splits = s.split(" ");
        wordDict.add(splits[0]);
        for (int i = 0; i < eSize; ++i)
          E[index][i] = Double.parseDouble(splits[i + 1]);
        index = index + 1;
      }
      for (int k = 0; k < nPOS; ++k) {
        s = input.readLine();
        splits = s.split(" ");
        posDict.add(splits[0]);
        for (int i = 0; i < eSize; ++i)
          E[index][i] = Double.parseDouble(splits[i + 1]);
        index = index + 1;
      }
      for (int k = 0; k < nLabel; ++k) {
        s = input.readLine();
        splits = s.split(" ");
        labelDict.add(splits[0]);
        for (int i = 0; i < eSize; ++i)
          E[index][i] = Double.parseDouble(splits[i + 1]);
        index = index + 1;
      }
      genMapping();

      double[][] W1 = new double[hSize][eSize * nTokens];
      for (int j = 0; j < W1[0].length; ++j) {
        s = input.readLine();
        splits = s.split(" ");
        for (int i = 0; i < W1.length; ++i)
          W1[i][j] = Double.parseDouble(splits[i]);
      }

      double[] b1 = new double[hSize];
      s = input.readLine();
      splits = s.split(" ");
      for (int i = 0; i < b1.length; ++i)
        b1[i] = Double.parseDouble(splits[i]);

      double[][] W2 = new double[nLabel * 2 - 1][hSize];
      for (int j = 0; j < W2[0].length; ++j) {
        s = input.readLine();
        splits = s.split(" ");
        for (int i = 0; i < W2.length; ++i)
          W2[i][j] = Double.parseDouble(splits[i]);
      }

      preComputed = new ArrayList<Integer>();
      while (preComputed.size() < nPreComputed) {
        s = input.readLine();
        splits = s.split(" ");
        for (int i = 0; i < splits.length; ++i)
          preComputed.add(Integer.parseInt(splits[i]));
      }
      input.close();
      classifier = new Classifier(config, E, W1, b1, W2, preComputed);
    } catch (Exception e) {
      System.out.println(e);
    }
  }

    public void readEmbedFile(String embedFile)
    {
        embedID = new HashMap<String, Integer>();
        if (embedFile == null)
            return;
        try
        {
            BufferedReader input = new BufferedReader(new FileReader(embedFile));
            String s;
            List<String> lines = new ArrayList<String>();
            while ((s = input.readLine()) != null)
                lines.add(s);
            input.close();

            int nWords = lines.size();
            String[] splits  = lines.get(0).split("\\s+");

            int dim = splits.length - 1;
            embeddings = new double[nWords][dim];
            System.out.println("Embedding File " + embedFile + ": #Words = "  + nWords + ", dim = " + dim);
            if (dim != config.embeddingSize)
                System.out.println("ERROR: embedding dimension mismatch");

            for (int i = 0; i < lines.size(); ++ i)
            {
                splits = lines.get(i).split("\\s+");
                embedID.put(splits[0], i);
                for (int j = 0; j < dim; ++ j)
                    embeddings[i][j] = Double.parseDouble(splits[j + 1]);
            }
        }
        catch (Exception e) { System.out.println(e); }
    }

  public void train(String trainFile, String devFile, String modelFile, String embedFile) {
    System.out.println("Train File: " + trainFile);
    System.out.println("Dev File: " + devFile);
    System.out.println("Model File: " + modelFile);
    System.out.println("Embedding File: " + embedFile);

    List<CoreMap> trainSents = new ArrayList<>();
    List<DependencyTree> trainTrees = new ArrayList<DependencyTree>();
    Util.loadConllFile(trainFile, trainSents, trainTrees);
    Util.printTreeStats("Train", trainTrees);

    List<CoreMap> devSents = new ArrayList<CoreMap>();
    List<DependencyTree> devTrees = new ArrayList<DependencyTree>();
    if (devFile != null) {
      Util.loadConllFile(devFile, devSents, devTrees);
      Util.printTreeStats("Dev", devTrees);
    }
    genDictionaries(trainSents, trainTrees);

    //NOTE: remove -NULL-, and the pass it to ParsingSystem
    List<String> lDict = new ArrayList<String>(labelDict);
    lDict.remove(0);
    system = new ArcStandard(lDict);

    double[][] E = new double[wordDict.size() + posDict.size() + labelDict.size()][config.embeddingSize];
    double[][] W1 = new double[config.hiddenSize][config.embeddingSize * config.numTokens];
    double[] b1 = new double[config.hiddenSize];
    double[][] W2 = new double[labelDict.size() * 2 - 1][config.hiddenSize];

    Random random = new Random();
    for (int i = 0; i < W1.length; ++i)
      for (int j = 0; j < W1[i].length; ++j)
        W1[i][j] = random.nextDouble() * 2 * config.initRange - config.initRange;

    for (int i = 0; i < b1.length; ++i)
      b1[i] = random.nextDouble() * 2 * config.initRange - config.initRange;

    for (int i = 0; i < W2.length; ++i)
      for (int j = 0; j < W2[i].length; ++j)
        W2[i][j] = random.nextDouble() * 2 * config.initRange - config.initRange;

    readEmbedFile(embedFile);
    int foundEmbed = 0;
    for (int i = 0; i < E.length; ++i) {
      int index = -1;
      if (i < wordDict.size()) {
        String str = wordDict.get(i);
        //NOTE: exact match first, and then try lower case..
        if (embedID.containsKey(str)) index = embedID.get(str);
        else if (embedID.containsKey(str.toLowerCase())) index = embedID.get(str.toLowerCase());
      }
      
      if (index >= 0) {
        ++foundEmbed;
        for (int j = 0; j < E[i].length; ++j)
          E[i][j] = embeddings[index][j];
      } else {
        for (int j = 0; j < E[i].length; ++j)
          E[i][j] = random.nextDouble() * config.initRange * 2 - config.initRange;
      }
    }
    System.out.println("Found embeddings: " + foundEmbed + " / " + wordDict.size());

    genTrainExamples(trainSents, trainTrees);
    classifier = new Classifier(config, trainSet, E, W1, b1, W2, preComputed);

    //TODO: save the best intermediate parameters
    long startTime = System.currentTimeMillis();
    for (int iter = 0; iter < config.maxIter; ++iter) {
      System.out.println("##### Iteration " + iter);

      // TODO track correct %
      Classifier.Cost cost = classifier.computeCostFunction(config.batchSize, config.regParameter, config.dropProb);
      System.out.println("Cost = " + cost.getCost() + ", Correct(%) = " + cost.getPercentCorrect());
      classifier.takeAdaGradientStep(cost, config.adaAlpha, config.adaEps);

      System.out.println("Elapsed Time: " + (System.currentTimeMillis() - startTime) / 1000.0 + " (s)");
      if (devFile != null && iter % config.evalPerIter == 0) {
        // Redo precomputation with updated weights. This is only
        // necessary because we're updating weights -- for normal
        // prediction, we just do this once in #initialize
        classifier.preCompute();

        List<DependencyTree> predicted = devSents.stream().map(this::predictInner).collect(Collectors.toList());
        System.out.println("UAS: " + system.getUASScore(devSents, predicted, devTrees));
      }
    }
    writeModelFile(modelFile);
  }

  public void train(String trainFile, String devFile, String modelFile) {
    train(trainFile, devFile, modelFile, null);
  }

	public void train(String trainFile, String modelFile)
	{
		train(trainFile, null, modelFile);
	}

  /**
   * Determine the dependency parse of the given sentence.
   *
   * This "inner" method returns a structure unique to this package;
   * use {@link #predict(edu.stanford.nlp.util.CoreMap)} for general
   * parsing purposes.
   */
  private DependencyTree predictInner(CoreMap sentence) {
    int numTrans = system.transitions.size();

    Configuration c = system.initialConfiguration(sentence);
    for (int k = 0; k < numTransitions(sentence); ++k) {
      double[] scores = classifier.computeScores(getFeatures(c));

      double optScore = Double.NEGATIVE_INFINITY;
      String optTrans = null;

      for (int j = 0; j < numTrans; ++j) {
        if (scores[j] > optScore && system.canApply(c, system.transitions.get(j))) {
          optScore = scores[j];
          optTrans = system.transitions.get(j);
        }
      }

      system.apply(c, optTrans);
    }

    return c.tree;
  }

  /**
   * Determine the dependency parse of the given sentence using the
   * loaded model. You must first initialize the parser after loading
   * or training a model using {@link #initialize()}.
   *
   * @throws java.lang.IllegalStateException If parser has not yet been
   *         properly initialized (see {@link #initialize()}
   */
  public GrammaticalStructure predict(CoreMap sentence) {
    if (system == null)
      throw new IllegalStateException("Parser has not been properly " +
          "initialized; first load a model and call .initialize()");

    DependencyTree result = predictInner(sentence);

    // The rest of this method is just busy-work to convert the
    // package-local representation into a CoreNLP-standard
    // GrammaticalStructure.

    List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
    List<TypedDependency> dependencies = new ArrayList<>();

    IndexedWord root = new IndexedWord(new Word("ROOT-" + (tokens.size() + 1)));
    root.set(CoreAnnotations.IndexAnnotation.class, -1);

    for (int i = 1; i < result.n; i++) {
      int head = result.getHead(i);
      String label = result.getLabel(i);

      IndexedWord thisWord = new IndexedWord(tokens.get(i - 1));
      IndexedWord headWord = head == 0 ? root
                                       : new IndexedWord(tokens.get(head - 1));

      // TODO English-specific
      GrammaticalRelation relation = head == 0
                                     ? GrammaticalRelation.ROOT
                                     : EnglishGrammaticalRelations.shortNameToGRel.get(label);

      dependencies.add(new TypedDependency(relation, headWord, thisWord));
    }

    // Build GrammaticalStructure
    // TODO ideally submodule should just return GrammaticalStructure
    TreeGraphNode rootNode = new TreeGraphNode(root);
    return new EnglishGrammaticalStructure(dependencies, rootNode);
  }

  /**
   * Convenience method for
   * {@link #predict(edu.stanford.nlp.util.CoreMap)}. The tokens of the
   * provided sentence must also have tag annotations (the parser
   * requires part-of-speech tags).
   *
   * @see #predict(edu.stanford.nlp.util.CoreMap)
   */
  public GrammaticalStructure predict(List<? extends HasWord> sentence) {
    CoreLabel sentenceLabel = new CoreLabel();
    List<CoreLabel> tokens = new ArrayList<>();

    for (HasWord wd : sentence) {
      CoreLabel label;
      if (wd instanceof CoreLabel) {
        label = (CoreLabel) wd;
        if (label.tag() == null)
          throw new IllegalArgumentException("Parser requires words " +
              "with part-of-speech tag annotations");
      } else {
        label = new CoreLabel();
        label.setValue(wd.word());
        label.setWord(wd.word());

        if (!(wd instanceof HasTag))
          throw new IllegalArgumentException("Parser requires words " +
              "with part-of-speech tag annotations");

        label.setTag(((HasTag) wd).tag());
      }

      tokens.add(label);
    }

    sentenceLabel.set(CoreAnnotations.TokensAnnotation.class, tokens);

    return predict(sentenceLabel);
  }

  //TODO: support sentence-only files as input
  public void test(String testFile, String modelFile, String outFile) {
    System.out.println("Test File: " + testFile);
    System.out.println("Model File: " + modelFile);

    loadModelFile(modelFile);
    initialize();

    List<CoreMap> testSents = new ArrayList<>();
    List<DependencyTree> testTrees = new ArrayList<DependencyTree>();
    Util.loadConllFile(testFile, testSents, testTrees);

    List<DependencyTree> predicted = testSents.stream().map(this::predictInner).collect(Collectors.toList());
    Map<String, Double> result = system.evaluate(testSents, predicted, testTrees);
    System.out.println("UAS = " + result.get("UASwoPunc"));
    System.out.println("LAS = " + result.get("LASwoPunc"));

    if (outFile != null)
      Util.writeConllFile(outFile, testSents, predicted);
  }

  public void test(String testFile, String modelFile) {
    test(testFile, modelFile, null);
  }

  /**
   * Prepare for parsing after a model has been loaded.
   */
  public void initialize() {
    if (labelDict == null)
      throw new IllegalStateException("Model has not been loaded or trained");

    //NOTE: remove -NULL-, and the pass it to ParsingSystem
    List<String> lDict = new ArrayList<>(labelDict);
    lDict.remove(0);
    system = new ArcStandard(lDict);

    // Pre-compute matrix multiplications
    if (config.numPreComputed > 0)
      classifier.preCompute();
  }

  /**
   * Determine the number of shift-reduce transitions necessary to
   * build a dependency parse of the given sentence.
   */
  private static int numTransitions(CoreMap sentence) {
    return 2 * sentence.get(CoreAnnotations.TokensAnnotation.class).size();
  }
}