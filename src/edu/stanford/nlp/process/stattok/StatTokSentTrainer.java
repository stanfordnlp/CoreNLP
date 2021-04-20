package edu.stanford.nlp.process.stattok;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.Annotator;
import edu.stanford.nlp.util.ArrayCoreMap;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.ArraySet;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.classify.ColumnDataClassifier;
import edu.stanford.nlp.util.logging.Redwood;
import edu.stanford.nlp.io.IOUtils;


import java.io.*;
import java.lang.Math;
import java.util.*;

	/*
	Input files:

	a ud-train file

	# newdoc = tanl
	# sent_id = isst_tanl-1
	# text = LONDRA.
	1	LONDRA	Londra	PROPN	SP	_	0	root	_	SpaceAfter=No
	2	.	.	PUNCT	FS	_	1	punct	_	_

	# sent_id = isst_tanl-2
	# text = Gas dalla statua.
	

	extracted features for tokenizer:
	
	n characters before
	n characters after
	character case
	*/

/*
 * This class is used to build models for Tokenization and S. Splitting.
 * The class can be called to train the model on any UD-formatted (CoNLL-U)
*/
public class StatTokSentTrainer{
	private String[] propertiesArguments;
	private static final Redwood.RedwoodChannels logger = Redwood.channels(StatTokSentTrainer.class);

	/*
	 * StatTokSentTrainer object constructor.
	*/
	public StatTokSentTrainer(String[] propertiesArguments){
		this.propertiesArguments = propertiesArguments;
	}

	/*
	 * This method generates the training set for the classifier given a CoNLL-U formatted training set and set of multi-word rules (either generated from the training or written in a file).
	*/
	public ArrayList<Pair<String, String>> fileToTrainSet(String trainFile, Map<String, String[]> multiWordRules)throws IOException, FileNotFoundException{

		ArrayList<Pair<String, String>> classChars = new ArrayList<Pair<String, String>>();
		
		try(BufferedReader br = new BufferedReader (new InputStreamReader(new FileInputStream(trainFile),"UTF8"))){
			String sentence="";
			List<String> tokenized = new ArrayList<String>();
			List<Integer> cliticIndex = new ArrayList<>();
			List<Integer> mutliWordIndex = new ArrayList<>();
			Boolean start = true;

			int cliticTokenLenght = -1;
			int tokenCounter = 0;

			for(String line; (line = br.readLine()) != null; ) {
				
				// Skip all lines starting with "#" except those containing the actual text of the sentence.
				if ((line.startsWith("#")) && (!line.contains("text = ")) || (line.length() == 0)){
					continue;
				}
				// Whenever a line with "text = " is found, elaborate previous sentence and restore utility variables.
				else if (line.contains("text = ")){
					if (sentence != ""){
						int charIdx = 0;
	    				List<Character> sentenceChars = new ArrayList<Character>();
	    				for(char c : sentence.toCharArray()){
	    					sentenceChars.add(c);
	    				}

	    				//Set class of each character based on its position (and its status as a multi-word token)
	    				List<Character> tokensChars = new ArrayList<Character>(sentenceChars);
	    				for(int tokIdx=0; tokIdx < tokenized.size(); tokIdx++){

	    					String token = tokenized.get(tokIdx);
	    					//Find position of token in the actual sentence
	    					int begin = sentence.indexOf(token, charIdx);
	    					int end = token.length()+begin-1;

	    					for (int i=begin; i <= end; i++){

	    						if (i == 0){
	    							tokensChars.set(i, 'S');
	    						}
	    						else if (i == begin && i != 0){
	    							tokensChars.set(i, 'T');
	    							if (cliticIndex.indexOf(tokIdx) != -1){
	    								tokensChars.set(i, 'C');
	    							}
	    						} 
	    						else{
	    							tokensChars.set(i, 'I');
	    						}	    						
	    					}
	    					charIdx = end+1;
	    				}				
	    				for (int i = 0; i < sentenceChars.size(); i++){
	    					if (tokensChars.get(i) ==' '){
	    						tokensChars.set(i, 'O');
	    					}
	    					// Randomly add a " " or "\u00A7" character to the end of each sentece.
	    					// This is done to simulate real world text behaviour for end of sentences (i.e., ". " or new line)
	    					if (tokensChars.get(i) == 'S'){
	    						if (!start){
		    						Double randSplit = Math.random();
		    						Pair<String,String> endSentenceAddition;
		    						if (randSplit <=0.2){
		    							endSentenceAddition = new Pair<String,String>("O", " ");
		    						}
		    						else{
		    							endSentenceAddition = new Pair<String,String>("O", "\u00A7");
		    						}
		    						classChars.add(endSentenceAddition);
		    					}
		    					
	    					}
	    					// Add results to the list that will be returned.
	    					String classString 	= tokensChars.get(i).toString();
	    					String charString 	= sentenceChars.get(i).toString();
	    					Pair<String,String> classAndChar = new Pair<String,String>(classString, charString);
	    					classChars.add(classAndChar);					
	    				}

	    				// Re-initialize support variables.
	    				start = false;
	    				tokenized = new ArrayList<>();
	    				sentence = "";
	    				String[] splitted = line.split("text = ");
						sentence = splitted[1];
						cliticIndex = new ArrayList<>();
						mutliWordIndex = new ArrayList<>();
						tokenCounter = 0;

					}
					// Store the sentence text.
					else{
						String[] splitted = line.split(" = ");
						sentence = splitted[1];
					}					
				}

				/* Any line starting with a digit is a new token.
				 * If it is a single token, and not a part of a multi-word token, simply add it to the list of tokens.
				 * If it is a multi-word token (identified by "-" in the index), it will be considered in two ways:
				 * 1) If it is in the multi-word token rules, it will be treated as a single token (its parts will be skipped)
				 * 2) If it is not in the multi-word token rules, indexes of its parts will be stored to identify the position in which to assign class "C" to the character.
				*/
				else if (Character.isDigit(line.charAt(0))){				
					String tokIdx = line.split("\t")[0];
					if (tokIdx.indexOf('-') != -1){
						String[] tokIdxSplit = tokIdx.split("-");
						if (multiWordRules.get(line.split("\t")[1].toLowerCase()) != null){
							mutliWordIndex.add(tokenCounter);
							int skip = Integer.parseInt(tokIdxSplit[1]) - Integer.parseInt(tokIdxSplit[0]);
		    				int i = 0;
		    				tokenized.add(line.split("\t")[1]);
		    				tokenCounter++;
		    				while (i <= skip){
		    					line = br.readLine();
		    					i++;
    						}
    						continue;
						}
						else{
							cliticTokenLenght = Integer.parseInt(tokIdxSplit[1]) - Integer.parseInt(tokIdxSplit[0]);
							continue;
						}
					}

					if (cliticTokenLenght > -1){  //ricontrolla che non ne vada aggiunto uno
						cliticIndex.add(tokenCounter);
						cliticTokenLenght--;
					}
    				tokenized.add(line.split("\t")[1]);
    				tokenCounter++;
    				
    			}

			}
			// Elaborate last sentence in the treebank
			// TODO: Code is repeated from above, the process could be improved and optimized.
			if (sentence != ""){
				//do end of sentence stuff						
				int charIdx = 0;
				List<Character> sentenceChars = new ArrayList<Character>();
				for(char c : sentence.toCharArray()){
					sentenceChars.add(c);
				}
				List<Character> tokensChars = new ArrayList<Character>(sentenceChars);

				for(int tokIdx=0; tokIdx < tokenized.size(); tokIdx++){

					String token = tokenized.get(tokIdx);
					int begin = sentence.indexOf(token, charIdx);
					int end = token.length()+begin-1;

					for (int i=begin; i <= end; i++){

						if (i == 0){
							tokensChars.set(i, 'S');
						}
						else if (i == begin && i != 0){
							tokensChars.set(i, 'T');
							if (cliticIndex.indexOf(tokIdx) != -1){
								tokensChars.set(i, 'C');
							}
						} 
						else{
							tokensChars.set(i, 'I');
						}	    						
					}
					charIdx = end+1;
				}				
				for (int i = 0; i < sentenceChars.size(); i++){

					if (tokensChars.get(i) ==' '){
						tokensChars.set(i, 'O');
					}

					if (tokensChars.get(i) == 'S'){
						if (!start){
						//generate random
    						Double randSplit = Math.random();
    						Pair<String,String> endSentenceAddition;
    						if (randSplit <=0.2){
    							endSentenceAddition = new Pair<String,String>("O", " ");
    						}
    						else{
    							endSentenceAddition = new Pair<String,String>("O", "\u00A7");
    						}
    						classChars.add(endSentenceAddition);
    					}		
					}
					String classString 	= tokensChars.get(i).toString();
					String charString 	= sentenceChars.get(i).toString();
					Pair<String,String> classAndChar = new Pair<String,String>(classString, charString);
					classChars.add(classAndChar);					
				}
				start = false;
				tokenized = new ArrayList<>();
				sentence = "";
				cliticIndex = new ArrayList<>();
				mutliWordIndex = new ArrayList<>();
				tokenCounter = 0;
			}
		}

		return classChars;
	}

	/*
	 * This method adds features for building the training input for the classifier.
	 * Generated features are:
	 * - Before and after charachers (given the window size)
	 * - Class for the previous character
	 * - Case of the character
	*/
	public List<String> addFeatures(ArrayList<Pair<String, String>> classCharsText, int windowSize){
				
		String[] window = new String[windowSize*2+1];
		String toWrite;
		List<String> featurized = new ArrayList<String>();
		for (int i = 0; i < classCharsText.size(); i++){
			//for each element of the list of <class, char> add features for classification
			String currentCharacter = classCharsText.get(i).second();
			Boolean isUpperCase = Character.isUpperCase(currentCharacter.charAt(0));

			toWrite = "";
			for (int j=-windowSize; j<=windowSize; j++){
				
				try{
					window[j+windowSize] = classCharsText.get(i+j).second();
				}
				catch (IndexOutOfBoundsException e){
					window[j+windowSize] = "\u00A7";
				}
			}
			int index = 0;
			for (String character : window){
				if (index == windowSize){
					index++;
					continue;
				}
				toWrite+=character+"\t";
				index++;
			}

			toWrite = classCharsText.get(i).first()+"\t"+currentCharacter+"\t" + toWrite+Integer.toString(isUpperCase ? 1 : 0);
			featurized.add(toWrite);
		}
		return featurized;
	}

	/*
	 * Method to read multi-word token rules from a file.
	*/
	private Map<String, String[]> readMultiWordRules(String multiWordRulesFile){
		Map<String, String[]> multiWordRules = new HashMap<String, String[]>();
		try{
			InputStream is = IOUtils.getInputStreamFromURLOrClasspathOrFileSystem(multiWordRulesFile);
			BufferedReader reader = new BufferedReader(new InputStreamReader(is));
			//BufferedReader reader = new BufferedReader(new FileReader(multiWordRulesFile));
			String line;
			while ((line = reader.readLine()) != null){
				String[] parts = line.split("\t");
				String token = parts[0];
				String[] tokenComponents = parts[1].split(",");
				multiWordRules.put(token, tokenComponents);
			}
		}catch (Exception e){
			e.printStackTrace();
		}
		return multiWordRules;
	}

	/*
	 * Method to infer multi-word token rules directly from the training set for tokenization.
	*/
	private Map<String, String[]> inferMultiWordRules (String trainFile){
		Map<String, String[]> multiWordRules = new HashMap<String, String[]>();
		try{
			BufferedReader reader = new BufferedReader(new FileReader(trainFile));
			String line;
			while ((line = reader.readLine()) != null){
				if ((line.length() > 0) && (Character.isDigit(line.charAt(0)))){
					String tokIdx = line.split("\t")[0];
					//if it is a composed token, store token length
					if (tokIdx.indexOf('-') != -1){
						if (multiWordRules.get(line.split("\t")[1].toLowerCase()) == null){
							String token = line.split("\t")[1];
							String[] tokIdxSplit = tokIdx.split("-");
							int partsLen =  Integer.parseInt(tokIdxSplit[1]) - Integer.parseInt(tokIdxSplit[0]);
							int i = 0;
		    				String[] parts = new String[partsLen+1];
		    				while (i <= partsLen){
		    					line = reader.readLine();
		    					String part = line.split("\t")[1];
		    					parts[i] = part;
		    					i++;
    						}
    						multiWordRules.put(token, parts);
						}
					}
				}
			}
		}catch (Exception e){
			e.printStackTrace();
		}
		return multiWordRules;
	}

	/*
 	 * Main method to train the tokenizer.
 	 * The training set and optionally the multi-word rules are obtained via properties.
 	 * Then, a temporary training file is created. The file contains one character per line and features for the carachter.
 	 * The file is then fed to the ColumnDataClassifier for training.
 	 * Properties for the ColumnDataClassifier are specified in the same properties file, and follows properties of the original CoreNLP class.
	*/
	public static void main(String[] args) throws IOException{

		StatTokSentTrainer trainTokenizer = new StatTokSentTrainer(args);

		Properties properties 		= StringUtils.argsToProperties(args);
		String trainFile 			= properties.getProperty("trainFile", null);
		String multiWordRulesFile 	= properties.getProperty("multiWordRulesFile", null);
		int windowSize 				= Integer.parseInt(properties.getProperty("windowSize", "4")); //must reflect actual n. of features
		boolean inferMultiWordRules = Integer.parseInt(properties.getProperty("inferMultiWordRules", "0")) != 0;

		if (trainFile == null){
			logger.err("Error: No training file provided in properties or via command line.");
			return;
		}

		Map<String, String[]> multiWordRules = new HashMap<String, String[]>();
		// Read or generate multi-word rules
		if (multiWordRulesFile != null){
			if (inferMultiWordRules){
				logger.warn("Conflicting properties. Multi-word rules file will be considered.");
			}
			logger.info("Reading Multi-Word rules file ... ");
			multiWordRules = trainTokenizer.readMultiWordRules(multiWordRulesFile);
		} else {
			if (inferMultiWordRules){
				logger.info("Inferring Multi-Word rules from training set ... ");
				multiWordRules = trainTokenizer.inferMultiWordRules(trainFile);
			}
			else{
				logger.warn("No multi-word rules provided. No inferMultiWordRules flag validated. Not inferring rules from training.");
			}
		}

		// Generate training file from ConLL-U
		logger.info("Creating training set from "+trainFile);
		ArrayList<Pair<String, String>> classCharText = trainTokenizer.fileToTrainSet(trainFile,multiWordRules);
		logger.info("Adding Features");
		List<String> trainingInput = trainTokenizer.addFeatures(classCharText, windowSize);
		logger.info("Training is ready.");
		
		// Write temporary training data on file
		String trainFileIOB = trainFile+".IOB.features.tmp";
		FileWriter fileWriter = new FileWriter(trainFileIOB);
		for (String line : trainingInput){
			fileWriter.write(line+System.lineSeparator());
		}
		fileWriter.close();

		properties.setProperty("trainFile", trainFileIOB);

		logger.info("Creating classifier...");

		// Build the ColumnDataClassifier and train it on the temporary training file (or test it).
		ColumnDataClassifier cdc = new ColumnDataClassifier(properties);

		String testFile 				= properties.getProperty("testFile", null);
		String serializeTo 				= properties.getProperty("serializeTo", null);
		String crossValidationFoldsStr 	= properties.getProperty("crossValidationFolds", null);
		int crossValidationFolds 		= 0;
		if (crossValidationFoldsStr != null){
			crossValidationFolds = Integer.parseInt(crossValidationFoldsStr);
		}
		String loadClassifier			= properties.getProperty("loadClassifier", null); 

		if ((testFile == null && serializeTo == null && crossValidationFolds < 2) ||
            (trainFileIOB == null && loadClassifier == null)) { 

			logger.err("Not enough information provided via command line properties or properties file.");
			return;
		}

		if (loadClassifier == null) {
	    	if ( ! cdc.trainClassifier(trainFileIOB)) {
	        	return;
	      	}
	    }

	    if (testFile != null) {
	      cdc.testClassifier(testFile);
	    }

	    // Delete the temporary training file.
	    File delTrainFileIOB = new File(trainFileIOB); 
	    if (delTrainFileIOB.delete()){
	    	logger.info("IOB training file deleted.");
	    }
	}

}
