package edu.stanford.nlp.ie.machinereading.domains.ace.reader;

import java.io.BufferedReader;
import java.io.FileReader;

/**
 * The true caser does not print white spaces at the end of lines.
 * We restore these, because they are needed for the ACE character offsets.
 */
public class RealignTrueCasedFile {
	
	private static final String CHANGE_EOS = " ";
	
	public static void main(String[] args) throws Exception {
		// original file (EOS aligned)
	  BufferedReader is = new BufferedReader(new FileReader(args[0]));
	  // true cased file 
	  BufferedReader ist = new BufferedReader(new FileReader(args[1]));
	  String line, linet;
	  
	  while((line = is.readLine()) != null){
	  	linet = ist.readLine();
	  	assert(linet != null);
	  	
	  	System.out.print(linet);
	  	if(line.endsWith(CHANGE_EOS)){
	  		System.out.print(".");
	  	}
	  	System.out.println();
	  }
  }
}
