/*
 * DocumentCreationTimeMissingException.java
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

package de.unihd.dbs.heideltime.standalone.exceptions;

import de.unihd.dbs.heideltime.standalone.DocumentType;

/**
 * Exception thrown if document creation time is missing while processing a document of type {@link DocumentType#NEWS}
 * 
 * @author Andreas Fay, University of Heidelberg
 * @version 1.0
 */
public class DocumentCreationTimeMissingException extends Exception {

	/**
	 * 
	 */
	private static final long serialVersionUID = -157033697488394828L;

}
