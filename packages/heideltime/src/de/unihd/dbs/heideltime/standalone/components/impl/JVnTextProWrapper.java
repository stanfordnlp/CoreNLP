package de.unihd.dbs.heideltime.standalone.components.impl;

import java.util.Properties;

import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;

import de.unihd.dbs.heideltime.standalone.components.PartOfSpeechTagger;

public class JVnTextProWrapper implements PartOfSpeechTagger {
	// uima wrapper instance
	private de.unihd.dbs.uima.annotator.jvntextprowrapper.JVnTextProWrapper jvntextpro = 
			new de.unihd.dbs.uima.annotator.jvntextprowrapper.JVnTextProWrapper();

	@Override
	public void process(JCas jcas) {
		try {
			jvntextpro.process(jcas);
		} catch(AnalysisEngineProcessException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void initialize(Properties settings) {
		StandaloneConfigContext aContext = new StandaloneConfigContext();
		
		// construct a context for the uima engine 
		aContext.setConfigParameterValue(de.unihd.dbs.uima.annotator.jvntextprowrapper.JVnTextProWrapper.PARAM_ANNOTATE_TOKENS, 
				(Boolean) settings.get(JVNTEXTPRO_ANNOTATE_TOKENS));
		aContext.setConfigParameterValue(de.unihd.dbs.uima.annotator.jvntextprowrapper.JVnTextProWrapper.PARAM_ANNOTATE_SENTENCES, 
				(Boolean) settings.get(JVNTEXTPRO_ANNOTATE_SENTENCES));
		aContext.setConfigParameterValue(de.unihd.dbs.uima.annotator.jvntextprowrapper.JVnTextProWrapper.PARAM_ANNOTATE_PARTOFSPEECH, 
				(Boolean) settings.get(JVNTEXTPRO_ANNOTATE_POS));
		aContext.setConfigParameterValue(de.unihd.dbs.uima.annotator.jvntextprowrapper.JVnTextProWrapper.PARAM_WORDSEGMODEL_PATH, 
				(String) settings.get(JVNTEXTPRO_WORD_MODEL_PATH));
		aContext.setConfigParameterValue(de.unihd.dbs.uima.annotator.jvntextprowrapper.JVnTextProWrapper.PARAM_SENTSEGMODEL_PATH, 
				(String) settings.get(JVNTEXTPRO_SENT_MODEL_PATH));
		aContext.setConfigParameterValue(de.unihd.dbs.uima.annotator.jvntextprowrapper.JVnTextProWrapper.PARAM_POSMODEL_PATH, 
				(String) settings.get(JVNTEXTPRO_POS_MODEL_PATH));
		
		jvntextpro.initialize(aContext);
	}

}
