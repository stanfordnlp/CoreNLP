package de.unihd.dbs.uima.consumer.tempeval3writer;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.collection.CasConsumer_ImplBase;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceProcessException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import de.unihd.dbs.uima.annotator.heideltime.utilities.Logger;
import de.unihd.dbs.uima.types.heideltime.Dct;
import de.unihd.dbs.uima.types.heideltime.Timex3;

public class TempEval3Writer extends CasConsumer_ImplBase {
	private Class<?> component = this.getClass();

	private static final String PARAM_OUTPUTDIR = "OutputDir";
	
	// counter for outputting documents. gets increased in case there is no DCT/filename info 
	private static volatile Integer outCount = 0;

	private File mOutputDir;

	public void initialize() throws ResourceInitializationException {
		mOutputDir = new File((String) getConfigParameterValue(PARAM_OUTPUTDIR));
		
		if (!mOutputDir.exists()) {
			if(!mOutputDir.mkdirs()) {
				Logger.printError(component, "Couldn't create non-existant folder "+mOutputDir.getAbsolutePath());
				throw new ResourceInitializationException();
			}
		}
		
		if(!mOutputDir.canWrite()) {
			Logger.printError(component, "Folder "+mOutputDir.getAbsolutePath()+" is not writable.");
			throw new ResourceInitializationException();
		}
	}
	
	public void processCas(CAS aCAS) throws ResourceProcessException {
		JCas jcas;
		try {
			jcas = aCAS.getJCas();
		} catch (CASException e) {
			throw new ResourceProcessException(e);
		}
		
		// get the DCT
		Dct dct = null;
		String filename = null;
		try {
			dct = (Dct) jcas.getAnnotationIndex(Dct.type).iterator().next();
			filename = new File(dct.getFilename()).getName();
		} catch(Exception e) {
			filename = "doc_" + TempEval3Writer.getOutCount() + ".tml";
		}

		// assemble an XML document
		Document xmlDoc = buildTimeMLDocument(jcas, dct, filename);
		
		// write the document to file
		writeTimeMLDocument(xmlDoc, filename);
	}

	/**
	 * Creates a DOM Document filled with all of the timex3s that are in the jcas.
	 * @param jcas
	 * @param dct the document's DCT
	 * @return
	 */
	@SuppressWarnings("rawtypes")
	private Document buildTimeMLDocument(JCas jcas, Dct dct, String filename) {
		DocumentBuilderFactory dbf = null;
		DocumentBuilder db = null;
		Document doc = null;
		
		try {
			dbf = DocumentBuilderFactory.newInstance();
			dbf.setNamespaceAware(true);
			db = dbf.newDocumentBuilder();
			doc = db.newDocument();
		} catch (ParserConfigurationException e) {
			e.printStackTrace();
			Logger.printError(component, "XML Builder could not be instantiated");
		}
		
		// create the TimeML root element
		Element rootEl = doc.createElement("TimeML");
		rootEl.setAttributeNS("xmlns", "xsi", "http://www.w3.org/2001/XMLSchema-instance");
		rootEl.setAttributeNS("xsi", "noNamespaceSchemaLocation", "http://timeml.org/timeMLdocs/TimeML_1.2.1.xsd");
		doc.appendChild(rootEl);
		
		// create DOCID tag
		Element docidEl = doc.createElement("DOCID");
		docidEl.appendChild(doc.createTextNode(filename));
		rootEl.appendChild(docidEl);
		
		// create DCT tag
		if(dct != null) {
			Element dctEl = doc.createElement("DCT");
	
			Element timexForDCT = doc.createElement("TIMEX3");
			timexForDCT.setAttribute("tid", "t0");
			timexForDCT.setAttribute("type", "DATE");
			timexForDCT.setAttribute("value", dct.getValue());
			timexForDCT.setAttribute("temporalFunction", "false");
			timexForDCT.setAttribute("functionInDocument", "CREATION_TIME");
			timexForDCT.appendChild(doc.createTextNode(dct.getValue()));
			
			dctEl.appendChild(timexForDCT); // NEW		
			rootEl.appendChild(dctEl);
		}
		
		// create and fill the TEXT tag
		Integer offset = 0;
		Element textEl = doc.createElement("TEXT");
		rootEl.appendChild(textEl);
		
		FSIterator it = jcas.getAnnotationIndex(Timex3.type).iterator();
		// if there are no timexes, just add one text node as a child. otherwise, iterate through timexes
		String docText = jcas.getDocumentText();
		if(!it.hasNext()) {
			textEl.appendChild(doc.createTextNode(docText));
		} else {
			HashSet<Timex3> timexesToSkip = new HashSet<Timex3>();
			Timex3 prevT = null;
			Timex3 thisT = null;
			// iterate over timexes to find overlaps
			while(it.hasNext()) {
				thisT = (Timex3) it.next();
				
				if((Class) thisT.getClass() != (Class) Timex3.class) // disregard types that inherit from Timex3
					continue;
				
				// check for whether this and the previous timex overlap. ex: [early (friday] morning)
				if(prevT != null && prevT.getEnd() > thisT.getBegin()) {
					
					Timex3 removedT = null; // only for debug message
					// assuming longer value string means better granularity
					if(prevT.getTimexValue().length() > thisT.getTimexValue().length()) {
						timexesToSkip.add(thisT);
						removedT = thisT;
						/* prevT stays the same. */
					} else {
						timexesToSkip.add(prevT);
						removedT = prevT;
						prevT = thisT; // this iteration's prevT was removed; setting for new iteration 
					}
					
					Logger.printError(component, "Two overlapping Timexes have been discovered:" + System.getProperty("line.separator")
							+ "Timex A: " + prevT.getCoveredText() + " [\"" + prevT.getTimexValue() + "\" / " + prevT.getBegin() + ":" + prevT.getEnd() + "]" 
							+ System.getProperty("line.separator")
							+ "Timex B: " + removedT.getCoveredText() + " [\"" + removedT.getTimexValue() + "\" / " + removedT.getBegin() + ":" + removedT.getEnd() + "]" 
							+ " [removed]" + System.getProperty("line.separator")
							+ "The writer chose, for granularity: " + prevT.getCoveredText() + System.getProperty("line.separator")
							+ "This usually happens with an incomplete ruleset. Please consider adding "
							+ "a new rule that covers the entire expression.");
				} else { // no overlap found? set current timex as next iteration's previous timex
					prevT = thisT;
				}
			}
			
			it.moveToFirst(); // reset iterator for another loop
			// iterate over timexes to write the DOM tree.
			while(it.hasNext()) {
				Timex3 t = (Timex3) it.next();
				if((Class) t.getClass() != (Class) Timex3.class) // disregard types that inherit from Timex3
					continue;
				
				// if this timex was marked to be removed, skip it entirely and go to the next one.
				if(timexesToSkip.contains(t))
					continue;
				
				if(t.getBegin() > offset) { 
					// add a textnode that contains the text before the timex
					textEl.appendChild(doc.createTextNode(docText.substring(offset, t.getBegin())));
				}
				
				// create the TIMEX3 element
				Element newTimex = doc.createElement("TIMEX3");
				
				// set its required attributes
				newTimex.setAttribute("tid", t.getTimexId());
				newTimex.setAttribute("type", t.getTimexType());
				newTimex.setAttribute("value", t.getTimexValue());
				
				// set its optional attributes
				if(!t.getTimexMod().equals(""))
					newTimex.setAttribute("mod", t.getTimexMod());
				if(!t.getTimexQuant().equals(""))
					newTimex.setAttribute("quant", t.getTimexQuant());
				if(!t.getTimexFreq().equals(""))
					newTimex.setAttribute("freq", t.getTimexFreq());
				
				// set text
				newTimex.appendChild(doc.createTextNode(t.getCoveredText()));
				
				// add the timex tag to the text tag
				textEl.appendChild(newTimex);
				
				// set cursor to the end of the timex
				offset = t.getEnd();
			}
			
			// append the rest of the document text
			if(offset < docText.length())
				textEl.appendChild(doc.createTextNode(docText.substring(offset)));
		}
		
		return doc;
	}

	/**
	 * writes a populated DOM xml(timeml) document to a given directory/file 
	 * @param xmlDoc xml dom object
	 * @param filename name of the file that gets appended to the set output path
	 */
	private void writeTimeMLDocument(Document xmlDoc, String filename) {
		// create output file handle
		File outFile = new File(mOutputDir, filename+".tml"); 
		
		BufferedWriter bw = null;
		try {
			// create a buffered writer for the output file
			bw = new BufferedWriter(new FileWriter(outFile));
			
			// prepare the transformer to convert from the xml doc to output text
			Transformer transformer = TransformerFactory.newInstance().newTransformer();
			// some pretty printing
			transformer.setOutputProperty(OutputKeys.INDENT, "no");
			DOMSource source = new DOMSource(xmlDoc);
			StreamResult result = new StreamResult(bw);
			
			// transform
			transformer.transform(source, result);
		} catch (IOException e) { // something went wrong with the bufferedwriter
			e.printStackTrace();
			Logger.printError(component, "File "+outFile.getAbsolutePath()+" could not be written.");
		} catch (TransformerException e) { // the transformer malfunctioned (call optimus prime)
			e.printStackTrace();
			Logger.printError(component, "XML transformer could not be properly initialized.");
		} finally { // clean up for the bufferedwriter
			try {
				bw.close();
			} catch(IOException e) {
				e.printStackTrace();
				Logger.printError(component, "File "+outFile.getAbsolutePath()+" could not be closed.");
			}
		}
	}

	public static synchronized Integer getOutCount() {
		return outCount++;
	}
}
