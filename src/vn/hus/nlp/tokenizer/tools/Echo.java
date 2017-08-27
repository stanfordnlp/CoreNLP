package vn.hus.nlp.tokenizer.tools;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * 
 * @author LE Hong Phuong
 * This class implements a simple SAX parser to echo the content of an XML file.
 * This utility can be used to convert Vietnamese entities in an XML data file
 * to UTF-8 equivalent characters. 
 * 
 */
public class Echo extends DefaultHandler {

	StringBuffer textBuffer;

	static private Writer out;

	static final String FILENAME_INP = "samples/htb0.xml";
	static final String FILENAME_OUT = "samples/htb0.txt";

	private void emit(String s) throws SAXException {
		try {
			out.write(s);
			out.flush();
		} catch (IOException e) {
			// TODO: handle exception
			throw new SAXException("I/O error.", e);
		}
	}

	private void nl() throws SAXException {
		try {
			String lineEnd = System.getProperty("line.separator");
			out.write(lineEnd);
		} catch (IOException e) {
			// TODO: handle exception
			throw new SAXException("I/O error.", e);
		}
	}

	public void startDocument() throws SAXException {
		emit("<?xml version='1.0' encoding='UTF-8'?>");
		nl();
	}

	public void endDocument() throws SAXException {
		try {
			nl();
			out.flush();
		} catch (IOException e) {
			throw new SAXException("I/O error", e);
		}
	}

	public void startElement(String namespaceURI, String sName, // simple name
			String qName, // qualified name
			Attributes attrs) throws SAXException {
		echoText();
		String eName = sName; // element name
		if ("".equals(eName))
			eName = qName; // not namespace-aware
		emit("<" + eName);
		if (attrs != null) {
			for (int i = 0; i < attrs.getLength(); i++) {
				String aName = attrs.getLocalName(i); // Attr name
				if ("".equals(aName))
					aName = attrs.getQName(i);
				emit(" ");
				emit(aName + "=\"" + attrs.getValue(i) + "\"");
			}
		}
		emit(">");
	}

	public void endElement(String namespaceURI, String sName, // simple name
			String qName // qualified name
	) throws SAXException {
		echoText();
		String eName = sName; // element name
		if ("".equals(eName))
			eName = qName; // not namespace-aware
		emit("</" + eName + ">");
		
	}

	public void characters(char buf[], int offset, int len) throws SAXException {
		String s = new String(buf, offset, len);
		if (textBuffer == null) {
			textBuffer = new StringBuffer(s);
		} else {
			textBuffer.append(s);
		}
	}

	private void echoText() throws SAXException {
		if (textBuffer == null)
			return;
		String s = "" + textBuffer;
		emit(s);
		textBuffer = null;
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		try {
			// create a default handler
			DefaultHandler handler = new Echo();
			// create a SAX parser
			SAXParserFactory factory = SAXParserFactory.newInstance();
			SAXParser parser = factory.newSAXParser();
			// setup the output stream using UTF-8 encoding
			FileOutputStream fout = new FileOutputStream(Echo.FILENAME_OUT);
			out = new OutputStreamWriter(fout, "UTF-8");
			// parser the sample file
			parser.parse(new File(Echo.FILENAME_INP), handler);
			out.close();
		} catch (Throwable e) {
			// TODO: handle exception
			e.printStackTrace();
		}
		System.exit(0);
	}

}
