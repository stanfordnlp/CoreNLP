/*
 * PartOfSpeechTagger.java
 *
 * Copyright (c) 2011, Database Research Group, Institute of Computer Science, University of Heidelberg.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU General Public License.
 *
 * authors: Andreas Fay, Jannik Strötgen
 * email:  fay@stud.uni-heidelberg.de, stroetgen@uni-hd.de
 *
 * HeidelTime is a multilingual, cross-domain temporal tagger.
 * For details, see http://dbs.ifi.uni-heidelberg.de/heideltime
 */ 

package de.unihd.dbs.heideltime.standalone.components;

/**
 * Part of speech tagger
 * 
 * @author Andreas Fay, University of Heidelberg
 * @version 1.0
 */
public interface PartOfSpeechTagger extends UIMAAnnotator {
	public static final String TREETAGGER_ANNOTATE_TOKENS = "annotateTokens";
	public static final String TREETAGGER_ANNOTATE_SENTENCES = "annotateSentences";
	public static final String TREETAGGER_ANNOTATE_POS = "annotatePartOfSpeech";
	public static final String TREETAGGER_IMPROVE_GERMAN_SENTENCES = "improveGermanSentences";
	public static final String TREETAGGER_LANGUAGE = "language";
	public static final String TREETAGGER_CHINESE_TOKENIZER_PATH = "ChineseTokenizerPath";
	
	public static final String JVNTEXTPRO_WORD_MODEL_PATH = "word_model_path";
	public static final String JVNTEXTPRO_SENT_MODEL_PATH = "sent_model_path";
	public static final String JVNTEXTPRO_POS_MODEL_PATH = "pos_model_path";
	public static final String JVNTEXTPRO_ANNOTATE_TOKENS = "annotate_tokens";
	public static final String JVNTEXTPRO_ANNOTATE_SENTENCES = "annotate_sentences";
	public static final String JVNTEXTPRO_ANNOTATE_POS = "annotate_partofspeech";
	
	public static final String STANFORDPOSTAGGER_ANNOTATE_TOKENS = "annotate_tokens";
	public static final String STANFORDPOSTAGGER_ANNOTATE_SENTENCES = "annotate_sentences"; 
	public static final String STANFORDPOSTAGGER_ANNOTATE_POS = "annotate_partofspeech";
	public static final String STANFORDPOSTAGGER_MODEL_PATH = "model_path";
	public static final String STANFORDPOSTAGGER_CONFIG_PATH = "config_path";

	public static final String HUNPOS_LANGUAGE = "language";
	public static final String HUNPOS_MODEL_PATH = "model_path";
	public static final String HUNPOS_ANNOTATE_TOKENS = "annotate_tokens";
	public static final String HUNPOS_ANNOTATE_SENTENCES = "annotate_sentences";
	public static final String HUNPOS_ANNOTATE_POS = "annotate_pos";
}
