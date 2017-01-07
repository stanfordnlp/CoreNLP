package hr.fer.zemris.takelab.uima.annotator.hunpos;

import hr.fer.zemris.takelab.splitter.TokenSplitter;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.FSIndex;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.impl.RootUimaContext_impl;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ConfigurationManager;
import org.apache.uima.resource.impl.ConfigurationManager_impl;
import org.apache.uima.resource.impl.ResourceManager_impl;

import de.unihd.dbs.uima.annotator.heideltime.resources.Language;
import de.unihd.dbs.uima.annotator.heideltime.utilities.Logger;
import de.unihd.dbs.uima.types.heideltime.Sentence;
import de.unihd.dbs.uima.types.heideltime.Token;

/**
 * A wrapper around the CSTLemma lemmatiser and the HunPos POS tagger, to be used as a pre-processing engine in the UIMA pipeline.
 * Currently only offers support for the Croatian language.
 * @version 0.9
 * @author Luka Skukan
 *
 */
public class HunPosTaggerWrapper extends JCasAnnotator_ImplBase{
	
	public static final String PARAM_LANGUAGE = "language";
	public static final String PARAM_PATH = "hunpos_path";
	public static final String PARAM_MODEL_PATH = "model_path";
	public static final String PARAM_ANNOTATE_TOKENS = "annotate_tokens";
	public static final String PARAM_ANNOTATE_SENTENCES = "annotate_sentences";
	public static final String PARAM_ANNOTATE_POS = "annotate_pos";
	
	/**
	 * The language used by the instance of the wrapper
	 */
	private Language language;
	
	/**
	 * Indicates whether token annotation occurs
	 */
	private boolean annotate_tokens;
	
	/**
	 * Indicates whether sentence annotation occurs, with POS annotation being a prerequisite for it
	 */
	private boolean annotate_sentences;
	
	/**
	 * Indicates whether Part-Of-Speech annotation occurs
	 */
	private boolean annotate_pos;
	
	/**
	 * Initializes the wrapper with the given language and settings what to annotate. Sentences will not be annotated, even if set to True, unless POS annotation occurs.
	 * @param language Language used by the wrapper, determines which rule files are read
	 * @param annotateTokens Are tokens to be annotated?
	 * @param annotateSentences Are sentences to be annotated?
	 * @param annotatePOS Is POS to be annotated?
	 */
	public void initialize(Language language, String hunpos_path, String hunpos_model_path, Boolean annotateTokens, Boolean annotateSentences, Boolean annotatePOS) {
		this.initialize(new HunPosTaggerContext(language, hunpos_path, hunpos_model_path, annotateTokens, annotateSentences, annotatePOS));
	}
	
	/**
	 * Initializes the wrapper from UIMA context. See other initialize method for parameters required within context.
	 */
	public void initialize(UimaContext aContext) {
		annotate_tokens = (Boolean) aContext.getConfigParameterValue(PARAM_ANNOTATE_TOKENS);
		annotate_sentences = (Boolean) aContext.getConfigParameterValue(PARAM_ANNOTATE_SENTENCES);
		annotate_pos = (Boolean) aContext.getConfigParameterValue(PARAM_ANNOTATE_POS);
		this.language = Language.getLanguageFromString((String) aContext.getConfigParameterValue(PARAM_LANGUAGE));
		String hunposPath = (String) aContext.getConfigParameterValue(PARAM_PATH);
		String modelPath = (String) aContext.getConfigParameterValue(PARAM_MODEL_PATH);

		HunPosWrapper.initialize(modelPath, hunposPath);
	}

	@Override
	public void process(JCas aJCas) throws AnalysisEngineProcessException {
		if(annotate_tokens) {
			TokenSplitterWrapper.splitTokens(aJCas);
		}
		
		if(annotate_pos) {
			HunPosWrapper.tagPOS(aJCas, annotate_sentences);
		}
		
		if(language == Language.CROATIAN) {
			fixCroatianSentences(aJCas);
		}		
	}
	
	/**
	 * A simple wrapper over the {@link TokenSplitter} class which invokes it over a {@link JCas} object and produces word tokens
	 * out of the text in the JCas covered document.
	 * @author Luka Skukan
	 *
	 */
	private static class TokenSplitterWrapper {
		
		/**
		 * Takes a document wrapped in the {@link JCas} object and records all tokens within the covered text as
		 * {@link Token} objects which are added to the JCas indexes.
		 * @param jcas
		 */
		public static void splitTokens(JCas jcas) {
			List<String> tokens = TokenSplitter.getTokens(jcas.getDocumentText());
			int tokenOffset = 0;
			
			for(String token : tokens) {
				if (jcas.getDocumentText().indexOf(token, tokenOffset) < 0)
					throw new RuntimeException("Opps! Could not find token "+ token +
							" in JCas after tokenizing with token splitter for Croatian." +
							" Hmm, there may exist a charset missmatch!" +
							" Default encoding is " + Charset.defaultCharset().name() + 
							" and should always be UTF-8.");

				// create tokens and add them to the jcas's indexes.
				Token newToken = new Token(jcas);
				newToken.setBegin(jcas.getDocumentText().indexOf(token, tokenOffset));
				newToken.setEnd(newToken.getBegin() + token.length());
				newToken.addToIndexes();
				tokenOffset = newToken.getEnd();
			}
			
		}
		
	}
	

	private static class HunPosWrapper {
		private static List<String> command;
		
		public static final String HUNPOS_HOME = "HUNPOS_HOME";
		
		@SuppressWarnings("unused")
		public static void initialize(String modelPath) {
			initialize(modelPath, null);
		}
		
		public static void initialize(String modelPath, String hunposPath) {
			String hunposRoot = hunposPath;
			if(hunposRoot == null) {
				hunposRoot = System.getenv(HUNPOS_HOME);
			}
			
			if(hunposRoot == null || !new File(hunposRoot).exists()) {
				Logger.printError(HunPosWrapper.class, "The environment variable HUNPOS_HOME was not set, or set to \"" + hunposRoot + "\", which does not exist.");
				System.exit(-1);
			}
			File hunPosRootFile = new File(hunposRoot);
			
			command = new ArrayList<String>();
			command.add(hunposRoot + "/hunpos-tag"); //Constructing a tagger call
			
			File modelFile = new File(hunPosRootFile, modelPath);
			if(modelFile.exists()) {
				command.add(modelFile.getAbsolutePath());
			} else {
				Logger.printError(HunPosWrapper.class, "The supplied model path " + modelPath + " does not exist.");
				System.exit(-1);
			}
		}
		
		public static void tagPOS(JCas jCas, boolean tagSentences) {
			Process p = null;
			
			String[] cmd = new String[command.size()];
			command.toArray(cmd);
			
			try {
				p = Runtime.getRuntime().exec(cmd);
			} catch (IOException e2) {
				Logger.printError(HunPosWrapper.class, "An error occured while trying to call HunPos at " + System.getenv(HUNPOS_HOME));
				e2.printStackTrace();
			}
			
			Writer writer = new OutputStreamWriter(p.getOutputStream());

			Logger.printDetail(HunPosWrapper.class, "Starting the POS tagging process.");
			
			final List<Token> tokens = new ArrayList<Token>();
			
			FSIterator ai = jCas.getAnnotationIndex(Token.type).iterator();
			while(ai.hasNext()) {
				Token t = (Token) ai.next();
				tokens.add(t);
			}
			
			class TaggingJob implements Runnable {
				
				private final Pattern HUNPOS_PATTERN = Pattern.compile("^(.+)\t([^\t]+)$");
				
				private JCas jCas;
				
				private List<Token> tokens;

				private boolean tagSentences;
				
				private InputStream input;
				
				private final String terminal = "Z";
				
				private HunPosAnnotionTranslator trans = new HunPosAnnotionTranslator();
				
				public TaggingJob(JCas jCas, List<Token> tokens, boolean tagSentences, InputStream input) {
					this.jCas = jCas;
					this.tokens = tokens;
					this.tagSentences = tagSentences;
					this.input = input;
				}
				
				@Override
				public void run() {
					InputStreamReader ir = new InputStreamReader(new BufferedInputStream(input), Charset.forName("UTF-8"));
					Scanner scan = new Scanner(ir);
					int i = 0;
					String s = null;
					Sentence sentence = null;
					
					try {
						while(true) {
							if(!scan.hasNextLine()) {
								break;
							}
							s = scan.nextLine().trim();
							if(s.isEmpty()) continue;
							Token token = tokens.get(i++);
							
							while (token.getCoveredText().isEmpty()){
								token.setPos("");
								token.addToIndexes();
								token = tokens.get(i++);
							}

							Matcher m = HUNPOS_PATTERN.matcher(s);
							if(m.find()) {
								s = m.group(2);
							} else {
								i--;
							}
							
							token.removeFromIndexes(); 

							token.setPos(trans.translate(s));
							token.addToIndexes();
						

							if(tagSentences) {

								if (sentence == null) {
									sentence = new Sentence(jCas);
									sentence.setBegin(token.getBegin());
								}

								
								if (terminal.equals(s) || i == tokens.size()) {
									sentence.setEnd(token.getEnd());
									sentence.addToIndexes();
								

									sentence = null;
								}
							}
						}
						scan.close();
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
				
			}
			
			Thread thr = new Thread(new TaggingJob(jCas, tokens, tagSentences, p.getInputStream()));
			thr.start();
			
			for(Token t : tokens) {
				try {
					writer.write(t.getCoveredText() + "\n");
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			
			try {
				writer.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			try {
				thr.join();
				p.waitFor();
			} catch (InterruptedException e1) {
				e1.printStackTrace();
			}
		}
	}
	
	private void fixCroatianSentences(JCas jCas) {
		final String reBeginsWithMonth =
				"^(Siječ(anj|nja)|Veljač[ae]|Ožuj(ak|ka)|Trav(anj|nja)|Svib(anj|nja)|Lip(anj|nja)|Srp(anj|nja)|Kolovoza?|Ruj(an|na)|Listopada?|Studen(i|og)|Prosin(ac|ca)).*";
		final String reBeginsWithUppercase = "^[A-ZŠĐČĆŽ].*";
		final String reEndsWithDate = "(?s).*\\d{1,4}\\.$";
		final String reFalseSentenceEnd = "(?s)^.*(\\s[A-Z]\\.|[:;,%\"\\(\\)\\-])$";
		
		FSIndex annoHeidelSentences = jCas.getAnnotationIndex(de.unihd.dbs.uima.types.heideltime.Sentence.type);
		FSIterator iterHeidelSent   = annoHeidelSentences.iterator();
		
		HashSet<Sentence> hsNewAnnotations = new HashSet<Sentence>();
		HashSet<Sentence> hsOldAnnotations = new HashSet<Sentence>();
		
		
		boolean prevIsDate = false;
		boolean prevIsFalseEnd = false;
		Sentence sOld = null;
		
		while(iterHeidelSent.hasNext()) {
			Sentence s = (Sentence) iterHeidelSent.next();
			String text = s.getCoveredText();

			//If the previous sentence ended in a day or month and the next one is not uppercase, or is an uppercase month, merge them
			if(prevIsFalseEnd || (prevIsDate && (!text.matches(reBeginsWithUppercase) || (text.matches(reBeginsWithUppercase) && text.matches(reBeginsWithMonth))))) {
				Sentence sMerged = new Sentence(jCas);
				
				sMerged.setBegin(sOld.getBegin());
				sMerged.setEnd(s.getEnd());
				
				if(hsNewAnnotations.contains(sOld)) {
					hsNewAnnotations.remove(sOld);
				}
				
				hsNewAnnotations.add(sMerged);

				prevIsDate = false;
				prevIsFalseEnd = false;
				sOld = sMerged;
				text = sOld.getCoveredText();
			} else {
				if(!hsNewAnnotations.contains(s)) {
					hsNewAnnotations.add(s);
				}
				sOld = s;
			}
			
			if(text.matches(reEndsWithDate)) {
				prevIsDate = true;
			} 
			
			if(text.matches(reFalseSentenceEnd)) {
				prevIsFalseEnd = true;
			}
		}
		
		iterHeidelSent.moveToFirst();
		while(iterHeidelSent.hasNext()) hsOldAnnotations.add((Sentence)iterHeidelSent.next());
		
		for(Sentence s : hsOldAnnotations) s.removeFromIndexes(jCas);
		for(Sentence s : hsNewAnnotations) s.addToIndexes(jCas);
	}
	

	
	private class HunPosTaggerContext extends RootUimaContext_impl {
		public HunPosTaggerContext(Language language, String hunpos_path, String hunpos_model_path, 
				Boolean annotateTokens, Boolean annotateSentences, Boolean annotatePartOfSpeech) {
			super();

			// Initialize config
			ConfigurationManager configManager = new ConfigurationManager_impl();

			// Initialize context
			this.initializeRoot(null, new ResourceManager_impl(), configManager);

			// Set session
			configManager.setSession(this.getSession());
			
			// Set necessary variables
			configManager.setConfigParameterValue(makeQualifiedName(PARAM_LANGUAGE), language.getName());
			configManager.setConfigParameterValue(makeQualifiedName(PARAM_MODEL_PATH), hunpos_model_path);
			configManager.setConfigParameterValue(makeQualifiedName(PARAM_PATH), hunpos_path);
			configManager.setConfigParameterValue(makeQualifiedName(PARAM_ANNOTATE_TOKENS), annotateTokens);
			configManager.setConfigParameterValue(makeQualifiedName(PARAM_ANNOTATE_POS), annotatePartOfSpeech);
			configManager.setConfigParameterValue(makeQualifiedName(PARAM_ANNOTATE_SENTENCES), annotateSentences);
		}
	}
}
