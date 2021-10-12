package edu.stanford.nlp.ie.machinereading.common;


import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import edu.stanford.nlp.util.XMLUtils;

/**
 * Generic DOM reader for an XML file
 */
public class DomReader {

	/**
	 * Searches (recursively) for the first child that has the given name
	 */
	protected static Node getChildByName(Node node, String name) {
		NodeList children = node.getChildNodes();

		// this node matches
		if (node.getNodeName().equals(name))
			return node;

		// search children
		for (int i = 0; i < children.getLength(); i++) {
			Node found = getChildByName(children.item(i), name);
			if (found != null)
				return found;
		}

		// failed
		return null;
	}

	/**
	 * Searches for all immediate children with the given name
	 */
	protected static List<Node> getChildrenByName(Node node, String name) {
		List<Node> matches = new ArrayList<>();
		NodeList children = node.getChildNodes();

		// search children
		for (int i = 0; i < children.getLength(); i++) {
			Node child = children.item(i);
			if (child.getNodeName().equals(name)) {
				matches.add(child);
			}
		}

		return matches;
	}

	/**
	 * Searches for children that have the given attribute
	 */
	protected static Node getChildByAttribute(Node node, String attributeName, String attributeValue) {
		NodeList children = node.getChildNodes();
		NamedNodeMap attribs = node.getAttributes();
		Node attribute = null;

		// this node matches
		if (attribs != null && (attribute = attribs.getNamedItem(attributeName)) != null
				&& attribute.getNodeValue().equals(attributeValue))
			return node;

		// search children
		for (int i = 0; i < children.getLength(); i++) {
			Node found = getChildByAttribute(children.item(i), attributeName, attributeValue);
			if (found != null)
				return found;
		}

		// failed
		return null;
	}

	/**
	 * Searches for children that have the given name and attribute
	 */
	protected static Node getChildByNameAndAttribute(Node node, String name, String attributeName, String attributeValue) {
		NodeList children = node.getChildNodes();
		NamedNodeMap attribs = node.getAttributes();
		Node attribute = null;

		// this node matches
		if (node.getNodeName().equals(name) && attribs != null
				&& (attribute = attribs.getNamedItem(attributeName)) != null
				&& attribute.getNodeValue().equals(attributeValue))
			return node;

		// search children
		for (int i = 0; i < children.getLength(); i++) {
			Node found = getChildByAttribute(children.item(i), attributeName, attributeValue);
			if (found != null)
				return found;
		}

		// failed
		return null;
	}

	/**
	 * Fetches the value of a given attribute
	 */
	public static String getAttributeValue(Node node, String attributeName) {
		try {
			return node.getAttributes().getNamedItem(attributeName).getNodeValue();
		} catch (Exception e) {
		}

		return null;
	}

	/**
	 * Constructs one Document from an XML file
	 */
	public static Document readDocument(File f) throws IOException, SAXException, ParserConfigurationException {
		Document document = null;

		DocumentBuilderFactory factory = XMLUtils.safeDocumentBuilderFactory();
		// factory.setValidating(true);
		// factory.setNamespaceAware(true);

		try {
			DocumentBuilder builder = factory.newDocumentBuilder();
			document = builder.parse(f);

			// displayDocument(document);

		} catch (SAXException sxe) {
			// Error generated during parsing)
			Exception x = sxe;
			if (sxe.getException() != null)
				x = sxe.getException();
			x.printStackTrace();
			throw sxe;
		} catch (ParserConfigurationException pce) {
			// Parser with specified options can't be built
			pce.printStackTrace();
			throw pce;
		} catch (IOException ioe) {
			// I/O error
			ioe.printStackTrace();
			throw ioe;
		}

		return document;
	} // readDocument
}
