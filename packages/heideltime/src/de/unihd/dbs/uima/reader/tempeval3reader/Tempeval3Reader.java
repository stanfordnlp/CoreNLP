/**
 * 
 */
package de.unihd.dbs.uima.reader.tempeval3reader;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.collection.CollectionException;
import org.apache.uima.collection.CollectionReader_ImplBase;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Progress;
import org.apache.uima.util.ProgressImpl;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import de.unihd.dbs.uima.annotator.heideltime.utilities.Logger;
import de.unihd.dbs.uima.types.heideltime.Dct;

/**
 * @author Julian Zell
 *
 */
public class Tempeval3Reader extends CollectionReader_ImplBase {
	private Class<?> component = this.getClass();
	
	// uima descriptor parameter name
	private String PARAM_INPUTDIR = "InputDirectory";
	
	private Integer numberOfDocuments = 0;
	
	private Queue<File> files = new LinkedList<File>();
	
	public void initialize() throws ResourceInitializationException {
		String dirPath = (String) getConfigParameterValue(PARAM_INPUTDIR);
		dirPath = dirPath.trim();
		
		populateFileList(dirPath);
	}

	public void getNext(CAS aCAS) throws IOException, CollectionException {
		JCas jcas;
		
		try {
			jcas = aCAS.getJCas();
		} catch (CASException e) {
			throw new CollectionException(e);
		}

		fillJCas(jcas);
		
		// give an indicator that a file has been processed
		System.err.print(".");
	}

	private void fillJCas(JCas jcas) {
		// grab a file to process
		File f = files.poll();
		try {
			// create xml parsing facilities
			DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
			// parse input xml file
			Document doc = db.parse(f);
			
			doc.getDocumentElement().normalize();
			
			// get the <text> tag's content to set the document text
			NodeList nList = doc.getElementsByTagName("TEXT");
			Node textNode = nList.item(0);
			String text = textNode.getTextContent();
			
			jcas.setDocumentText(text);
			
			// get the <dct> timex tag's value attribute for the dct
			Boolean gotDCT = false;
			String dctText = null;
			try {
				nList = doc.getDocumentElement().getElementsByTagName("DCT");
				nList = ((Element) nList.item(0)).getElementsByTagName("TIMEX3"); // timex3 tag
				Node dctTimex = nList.item(0);
				NamedNodeMap dctTimexAttr = dctTimex.getAttributes();
				Node dctValue = dctTimexAttr.getNamedItem("value");
				dctText = dctValue.getTextContent();
				gotDCT = true;
			} catch(Exception e) {
				gotDCT = false;
			}
			
			if(!gotDCT) 
				try { // try a different location for the DCT timex element
					nList = doc.getDocumentElement().getElementsByTagName("TEXT");
					nList = ((Element) nList.item(0)).getElementsByTagName("TIMEX3"); // timex3 tag
					Node dctTimex = nList.item(0);
					NamedNodeMap dctTimexAttr = dctTimex.getAttributes();
					if(dctTimexAttr.getNamedItem("functionInDocument") != null && dctTimexAttr.getNamedItem("functionInDocument").getTextContent().equals("CREATION_TIME")) {
						Node dctValue = dctTimexAttr.getNamedItem("value");
						dctText = dctValue.getTextContent();
					}
					gotDCT = true;
				} catch(Exception e) {
					gotDCT = false;
				}
			
			// get the document id
			nList = doc.getElementsByTagName("DOCID");
			String filename = null;
			if(nList != null && nList.getLength() > 0)
				filename = nList.item(0).getTextContent();
			else
				filename = f.getName().replaceAll("\\.[^\\.]+$", "");

			Dct dct = new Dct(jcas);
			dct.setBegin(0);
			dct.setEnd(text.length());
			dct.setFilename(filename);
			dct.setValue(dctText);
			dct.setTimexId("t0");
			dct.addToIndexes();
		} catch(Exception e) {
			  e.printStackTrace();
			  Logger.printError(component, "File "+f.getAbsolutePath()+" could not be properly parsed.");
		}
	}

	public boolean hasNext() throws IOException, CollectionException {
	    return files.size() > 0;
	}
	
	public Progress[] getProgress() {
		return new Progress[] { new ProgressImpl(numberOfDocuments-files.size(), numberOfDocuments , Progress.ENTITIES) };
	}
	
	public void close() throws IOException {
		files.clear();
	}

	private void populateFileList(String dirPath) throws ResourceInitializationException {
		ArrayList<File> myFiles = new ArrayList<File>();
		File dir = new File(dirPath);
		
		// check if the given directory path is valid
		if(!dir.exists() || !dir.isDirectory())
			throw new ResourceInitializationException();
		else
			myFiles.addAll(Arrays.asList(dir.listFiles()));
		
		// check for existence and readability; add handle to the list
		for(File f : myFiles) {
			if(!f.exists() || !f.isFile() || !f.canRead()) {
				Logger.printDetail(component, "File \""+f.getAbsolutePath()+"\" was ignored because it either didn't exist, wasn't a file or wasn't readable.");
			} else {
				files.add(f);
			}
		}
		
		numberOfDocuments = files.size();
	}
}
