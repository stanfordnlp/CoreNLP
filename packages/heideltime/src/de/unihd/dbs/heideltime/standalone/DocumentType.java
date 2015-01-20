/*
 * DocumentType.java
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

/**
 * Type of document to be processed by HeidelTime
 * 
 * @author Andreas Fay, University of Heidelberg
 * @version 1.0
 */
public enum DocumentType {
	NARRATIVES {
		public String toString() {
			return "narratives";
		}
	},
	NEWS {
		public String toString() {
			return "news";
		}
	},
	COLLOQUIAL {
		public String toString() {
			return "colloquial";
		}
	},
	SCIENTIFIC {
		public String toString() {
			return "scientific";
		}
	}
}
