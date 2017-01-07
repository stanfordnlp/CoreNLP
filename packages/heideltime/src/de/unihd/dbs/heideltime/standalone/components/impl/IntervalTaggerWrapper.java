package de.unihd.dbs.heideltime.standalone.components.impl;

import java.util.Properties;

import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

import de.unihd.dbs.heideltime.standalone.components.UIMAAnnotator;
import de.unihd.dbs.uima.annotator.intervaltagger.IntervalTagger;

public class IntervalTaggerWrapper implements UIMAAnnotator {
	// uima wrapper instance
	private IntervalTagger tagger = new IntervalTagger();

	public void initialize(Properties settings) {
		StandaloneConfigContext aContext = new StandaloneConfigContext();
		
		// construct a context for the uima engine 
		aContext.setConfigParameterValue(IntervalTagger.PARAM_LANGUAGE, (String) settings.get(IntervalTagger.PARAM_LANGUAGE));
		aContext.setConfigParameterValue(IntervalTagger.PARAM_INTERVALS, (Boolean) settings.get(IntervalTagger.PARAM_INTERVALS));
		aContext.setConfigParameterValue(IntervalTagger.PARAM_INTERVAL_CANDIDATES, (Boolean) settings.get(IntervalTagger.PARAM_INTERVAL_CANDIDATES));
		
		try {
			tagger.initialize(aContext);
		} catch (ResourceInitializationException e) {
			e.printStackTrace();
		}
	}

	/**
	 * invokes the IntervalTagger's process method.
	 */
	public void process(JCas jcas) {
		try {
			tagger.process(jcas);
		} catch(AnalysisEngineProcessException e) {
			e.printStackTrace();
		}
	}
}
