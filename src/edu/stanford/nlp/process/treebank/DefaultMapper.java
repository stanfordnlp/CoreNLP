package edu.stanford.nlp.process.treebank;

import java.io.File;

/**
 * 
 * @author Spence Green
 *
 */
public class DefaultMapper implements Mapper {

	public boolean canChangeEncoding(String parent, String child) {
		return true;
	}

	public String map(String parent, String element) {
		return element;
	}

	public void setup(File path, String... options) {}
}
