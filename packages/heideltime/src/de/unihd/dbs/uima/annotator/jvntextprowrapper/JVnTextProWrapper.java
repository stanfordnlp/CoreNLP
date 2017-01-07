/**
 * This is a preprocessing engine for use in a UIMA pipeline. It will invoke
 * the JVnTextPro api that is supposed to be available in the classpath.
 */
package de.unihd.dbs.uima.annotator.jvntextprowrapper;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

import jmaxent.Classification;
import jvnpostag.POSContextGenerator;
import jvnpostag.POSDataReader;
import jvnsegmenter.CRFSegmenter;
import jvnsensegmenter.JVnSenSegmenter;
import jvntextpro.JVnTextPro;
import jvntextpro.conversion.CompositeUnicode2Unicode;
import jvntextpro.data.DataReader;
import jvntextpro.data.TWord;
import jvntextpro.data.TaggingData;
import jvntextpro.util.StringUtils;
import jvntokenizer.PennTokenizer;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;

import de.unihd.dbs.uima.annotator.heideltime.utilities.Logger;
import de.unihd.dbs.uima.types.heideltime.Sentence;
import de.unihd.dbs.uima.types.heideltime.Token;

/**
 * @author Julian Zell
 *
 */
public class JVnTextProWrapper extends JCasAnnotator_ImplBase {
	private Class<?> component = this.getClass();

	// definitions of what names these parameters have in the wrapper's descriptor file
	public static final String PARAM_SENTSEGMODEL_PATH = "sent_model_path";
	public static final String PARAM_WORDSEGMODEL_PATH = "word_model_path";
	public static final String PARAM_POSMODEL_PATH = "pos_model_path";
	public static final String PARAM_ANNOTATE_TOKENS = "annotate_tokens";
	public static final String PARAM_ANNOTATE_SENTENCES = "annotate_sentences";
	public static final String PARAM_ANNOTATE_PARTOFSPEECH = "annotate_partofspeech";
	
	// switches for annotation parameters
	private Boolean annotate_tokens = false;
	private Boolean annotate_sentences = false;
	private Boolean annotate_partofspeech = false;
	private String sentModelPath = null;
	private String wordModelPath = null;
	private String posModelPath = null;
	
	// private jvntextpro objects
	JVnSenSegmenter vnSenSegmenter = new JVnSenSegmenter();
	CRFSegmenter vnSegmenter = new CRFSegmenter();
	DataReader reader = new POSDataReader();
	TaggingData dataTagger = new TaggingData();
	Classification classifier = null;
	
	/**
	 * initialization method where we fill configuration values and check some prerequisites
	 */
	public void initialize(UimaContext aContext) {
		// get configuration from the descriptor
		annotate_tokens = (Boolean) aContext.getConfigParameterValue(PARAM_ANNOTATE_TOKENS);
		annotate_sentences = (Boolean) aContext.getConfigParameterValue(PARAM_ANNOTATE_SENTENCES);
		annotate_partofspeech = (Boolean) aContext.getConfigParameterValue(PARAM_ANNOTATE_PARTOFSPEECH);
		sentModelPath = (String) aContext.getConfigParameterValue(PARAM_SENTSEGMODEL_PATH);
		wordModelPath = (String) aContext.getConfigParameterValue(PARAM_WORDSEGMODEL_PATH);
		posModelPath = (String) aContext.getConfigParameterValue(PARAM_POSMODEL_PATH);
		
		if(sentModelPath != null)
			if(!vnSenSegmenter.init(sentModelPath)) {
				Logger.printError(component, "Error initializing the sentence segmenter model: " + sentModelPath);
				System.exit(-1);
			}
		
		if(wordModelPath != null) 
			try {
				vnSegmenter.init(wordModelPath);
			} catch(Exception e) {
				Logger.printError(component, "Error initializing the word segmenter model: " + wordModelPath);
				System.exit(-1);
			}
		
		if(posModelPath != null) 
			try {
				dataTagger.addContextGenerator(new POSContextGenerator(posModelPath + File.separator + "featuretemplate.xml"));
				classifier = new Classification(posModelPath);	
			} catch(Exception e) {
				Logger.printError(component, "Error initializing the POS tagging model: " + posModelPath);
				System.exit(-1);
			}
	}
	
	/**
	 * Method that gets called to process the documents' cas objects
	 */
	public void process(JCas jcas) throws AnalysisEngineProcessException {
		CompositeUnicode2Unicode convertor = new CompositeUnicode2Unicode();
		String origText = jcas.getDocumentText();

		final String convertedText = convertor.convert(origText);
		final String senSegmentedText = vnSenSegmenter.senSegment(convertedText).trim();
		
		final String tokenizedText = PennTokenizer.tokenize(senSegmentedText).trim();
		final String segmentedText = vnSegmenter.segmenting(tokenizedText);
		final String postProcessedString = (new JVnTextPro()).postProcessing(segmentedText).trim();
		
		List<jvntextpro.data.Sentence> posSentences = jvnTagging(postProcessedString);
		LinkedList<TWord> posWords = new LinkedList<TWord>();
		for(jvntextpro.data.Sentence sent : posSentences)
			for(Integer i = 0; i < sent.size(); ++i)
				posWords.add(sent.getTWordAt(i));
		
		/*
		 * annotate sentences
		 */
		if(annotate_sentences) {
			Integer offset = 0;
			String[] sentences = senSegmentedText.split("\n");
			for(String sentence : sentences) {
				Sentence s = new Sentence(jcas);
				sentence = sentence.trim();
				Integer sentOffset = origText.indexOf(sentence, offset);
				
				if(sentOffset >= 0) {
					s.setBegin(sentOffset);
					offset = sentOffset + sentence.length();
					s.setEnd(offset);
					s.addToIndexes();
				} else {
					sentence = sentence.substring(0, sentence.length() - 1).trim();
					sentOffset = origText.indexOf(sentence, offset);
					if(sentOffset >= 0) {
						s.setBegin(sentOffset);
						offset = sentOffset + sentence.length();
						s.setEnd(offset);
						s.addToIndexes();
					} else {
						System.err.println("Sentence \"" + sentence + "\" was not found in the original text.");
					}
				}
			}
		}
		
		/*
		 * annotate tokens
		 */
		if(annotate_tokens) {
			Integer offset = 0;
			String[] tokens = postProcessedString.split("\\s+");
			for(Integer i = 0; i < tokens.length; ++i) {
				final String token = tokens[i].trim();
				String thisPosTag = null;
				if(posWords.size() >= i + 1) {
					if(!token.equals(posWords.get(i).getWord())) {
						System.err.println("Couldn't match token: " + token 
								+ " to expected word/tag combination " + posWords.get(i).getWord());
					} else {
						thisPosTag = posWords.get(i).getTag();
					}
				}
				Integer tokenOffset = origText.indexOf(token, offset);
				
				Token t = new Token(jcas);
				
				if(tokenOffset >= 0 ) {
					/*
					 * first, try to find the string in the form the tokenizer returned it
					 */
					t.setBegin(tokenOffset);
					offset = tokenOffset + token.length();
					t.setEnd(offset);
					
					sanitizeToken(t, jcas);
					
					if(annotate_tokens) t.setPos(thisPosTag);
					t.addToIndexes();
				} else {
					/*
					 * straight up token not found.
					 * assume that it is a compound word (e.g. some_thing)
					 * and try to find it in the original text again; first using
					 * a "_" -> " " replacement, then try just removing the underscore.
					 */
					String underscoreToSpaceToken = token.replaceAll("_", " ");
					Integer spaceOffset = origText.indexOf(underscoreToSpaceToken, offset);
					String underscoreRemovedToken = token.replaceAll("_", "");
					Integer removedOffset = origText.indexOf(underscoreRemovedToken, offset);
					
					/*
					 * offsets are the same. can't think of a good example where this could 
					 * possibly happen, but maybe there is one.
					 */
					if(removedOffset >= 0 && spaceOffset >= 0) {
						if(removedOffset >= spaceOffset) {
							t.setBegin(spaceOffset);
							offset = spaceOffset + underscoreToSpaceToken.length();
							t.setEnd(offset);
							
							sanitizeToken(t, jcas);
							
							if(annotate_tokens) t.setPos(thisPosTag);
							t.addToIndexes();
						} else {
							t.setBegin(removedOffset);
							offset = removedOffset + underscoreRemovedToken.length();
							t.setEnd(offset);
							
							sanitizeToken(t, jcas);
							
							t.addToIndexes();
						}
					}
					/*
					 * underscore removed was found, underscore replaced to space was not
					 */
					else if(removedOffset >= 0 && spaceOffset == -1) {
						t.setBegin(removedOffset);
						offset = removedOffset + underscoreRemovedToken.length();
						t.setEnd(offset);
						
						sanitizeToken(t, jcas);

						if(annotate_tokens) t.setPos(thisPosTag);
						t.addToIndexes();
					} 
					/*
					 * underscore removed was not found, underscore replaced was found
					 */
					else if(removedOffset == -1 && spaceOffset >= 0) {
						t.setBegin(spaceOffset);
						offset = spaceOffset + underscoreToSpaceToken.length();
						t.setEnd(offset);
						
						sanitizeToken(t, jcas);

						if(annotate_tokens) t.setPos(thisPosTag);
						t.addToIndexes();
					}
					/*
					 * there is no hope of finding this token
					 */
					else {
						System.err.println("Token \"" + token + "\" was not found in the original text.");
					}
				}
			}
		}
	}
	
	private Boolean sanitizeToken(Token t, JCas jcas) {
		Boolean workDone = false;
		
		// check the beginning of the token for punctuation and split off into a new token
		if(t.getCoveredText().matches("^\\p{Punct}.*") && t.getCoveredText().length() > 1) {
			Character thisChar = t.getCoveredText().charAt(0);
			t.setBegin(t.getBegin() + 1); // set corrected token boundary for the word
			Token puncToken = new Token(jcas); // create a new token for the punctuation character
			puncToken.setBegin(t.getBegin() - 1);
			puncToken.setEnd(t.getBegin());
			// check if we want to annotate pos or the token itself
			if(annotate_partofspeech)
				puncToken.setPos(""+thisChar);
			if(annotate_tokens)
				puncToken.addToIndexes();
			
			workDone = true;
		}
		
		// check the end of the token for punctuation and split off into a new token
		if(t.getCoveredText().matches(".*\\p{Punct}$") && t.getCoveredText().length() > 1) {
			Character thisChar = t.getCoveredText().charAt(t.getEnd() - t.getBegin() - 1);
			t.setEnd(t.getEnd() - 1); // set corrected token boundary for the word
			Token puncToken = new Token(jcas); // create a new token for the punctuation character
			puncToken.setBegin(t.getEnd());
			puncToken.setEnd(t.getEnd() + 1);
			// check if we want to annotate pos or the token itself
			if(annotate_partofspeech)
				puncToken.setPos(""+thisChar);
			if(annotate_tokens)
				puncToken.addToIndexes();
			
			workDone = true;
		}
		
		// get into a recursion to sanitize tokens as long as there are stray ones
		if(workDone) {
			workDone = sanitizeToken(t, jcas);
		}
		
		return workDone;
	}
	
	/**
	 * Taken from the JVnTextPro package and adapted to not output a string
	 * @param instr input string to be tagged
	 * @return tagged text
	 */
	public List<jvntextpro.data.Sentence> jvnTagging(String instr) {
		List<jvntextpro.data.Sentence> data = reader.readString(instr);
		for (int i = 0; i < data.size(); ++i) {
        	
			jvntextpro.data.Sentence sent = data.get(i);
    		for (int j = 0; j < sent.size(); ++j) {
    			String [] cps = dataTagger.getContext(sent, j);
    			String label = classifier.classify(cps);
    			
    			if (label.equalsIgnoreCase("Mrk")) {
    				if (StringUtils.isPunc(sent.getWordAt(j)))
    					label = sent.getWordAt(j);
    				else label = "X";
    			}
    			
    			sent.getTWordAt(j).setTag(label);
    		}
    	}
		
		return data;
	}
}
