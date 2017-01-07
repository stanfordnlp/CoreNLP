/*
 * JCasFactoryImpl.java
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

package de.unihd.dbs.heideltime.standalone.components.impl;

import org.apache.uima.cas.CASException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.CasManager;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceManager;
import org.apache.uima.resource.impl.ResourceManager_impl;
import org.apache.uima.resource.metadata.ProcessingResourceMetaData;
import org.apache.uima.resource.metadata.TypeSystemDescription;
import org.apache.uima.resource.metadata.impl.ProcessingResourceMetaData_impl;
import org.apache.uima.util.CasCreationUtils;

import de.unihd.dbs.heideltime.standalone.components.JCasFactory;

/**
 * @see JCasFactory
 */
public class JCasFactoryImpl implements JCasFactory {
	
	/**
	 * Cas Manager
	 */
	private CasManager casManager;

	/**
	 * Constructor
	 * 
	 * @param typeSystemDescriptions
	 */
	public JCasFactoryImpl(TypeSystemDescription[] typeSystemDescriptions) {
		// Initialize cas manager
		ResourceManager resManager = new ResourceManager_impl();
		casManager = resManager.getCasManager();
		
		for (TypeSystemDescription desc : typeSystemDescriptions) {
			ProcessingResourceMetaData metaData = new ProcessingResourceMetaData_impl();
			metaData.setTypeSystem(desc);
			
			casManager.addMetaData(metaData);
		}
	}

	@Override
	public JCas createJCas() throws CASException,
			ResourceInitializationException {
		return CasCreationUtils.createCas(casManager.getCasDefinition(), null).getJCas();
	}
}
