/*
 * Config.java
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

package de.unihd.dbs.heideltime.standalone;

import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Properties;

/**
 * Static class
 * 
 * @author Andreas Fay, University of Heidelberg
 * @version 1.0
 */
public abstract class Config {

	/**
	 * 
	 */
	private static Properties properties;

	/*
	 * Constants to organize consistent access to config parameters
	 */
	public static final String CONSIDER_DATE = "considerDate";
	public static final String CONSIDER_DURATION = "considerDuration";
	public static final String CONSIDER_SET = "considerSet";
	public static final String CONSIDER_TIME = "considerTime";
	public static final String TREETAGGERHOME = "treeTaggerHome";
	public static final String CHINESE_TOKENIZER_PATH = "chineseTokenizerPath";
	
	public static final String JVNTEXTPRO_WORD_MODEL_PATH = "word_model_path";
	public static final String JVNTEXTPRO_SENT_MODEL_PATH = "sent_model_path";
	public static final String JVNTEXTPRO_POS_MODEL_PATH = "pos_model_path";
	
	public static final String STANFORDPOSTAGGER_MODEL_PATH = "model_path";
	public static final String STANFORDPOSTAGGER_CONFIG_PATH = "config_path";
	
	public static final String HUNPOS_PATH = "hunpos_path";
	public static final String HUNPOS_MODEL_PATH = "hunpos_model_name";
	
	public static final String TYPESYSTEMHOME = "typeSystemHome";
	public static final String TYPESYSTEMHOME_DKPRO = "typeSystemHome_DKPro";
	
	public static final String UIMAVAR_DATE = "uimaVarDate";
	public static final String UIMAVAR_DURATION = "uimaVarDuration";
	public static final String UIMAVAR_LANGUAGE = "uimaVarLanguage";
	public static final String UIMAVAR_SET = "uimaVarSet";
	public static final String UIMAVAR_TIME = "uimaVarTime";
	public static final String UIMAVAR_TYPETOPROCESS = "uimaVarTypeToProcess";
	public static final String UIMAVAR_CONVERTDURATIONS = "ConvertDurations";

	/**
	 * 
	 */
	private Config() {
	}

	/**
	 * Gets config parameter identified by <code>key</code>
	 * 
	 * @param key
	 *            Identifier of config parameter
	 * @return Config paramter
	 */
	public static String get(String key) {
		if (properties == null) {
			return null;
		}

		return properties.getProperty(key);
	}
	
	/**
	 * Checks whether config was already initialized
	 * 
	 * @return
	 */
	public static boolean isInitialized() {
		return properties != null;
	}

	/**
	 * Sets properties once
	 * 
	 * @param prop
	 *            Properties
	 */
	@SuppressWarnings({"unchecked","rawtypes"})
	public static void setProps(Properties prop) {
		properties = prop;
		
		Iterator propIt = properties.entrySet().iterator();
		while(propIt.hasNext()) {
			Entry<String, String> entry = (Entry<String, String>) propIt.next();
			
			properties.setProperty(entry.getKey(), entry.getValue().trim());
		}
	}
}
