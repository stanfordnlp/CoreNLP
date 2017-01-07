/*
 * JCasFactory.java
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

import org.apache.uima.cas.CASException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

/**
 * Factory for JCas objects to be used by HeidelTime
 * 
 * @author Andreas Fay, University of Heidelberg
 * @version 1.0
 */
public interface JCasFactory {

	/**
	 * Creates new JCas object based on the type system description
	 * 
	 * @return
	 * @throws CASException
	 * @throws ResourceInitializationException
	 */
	public JCas createJCas() throws CASException,
			ResourceInitializationException;
}
