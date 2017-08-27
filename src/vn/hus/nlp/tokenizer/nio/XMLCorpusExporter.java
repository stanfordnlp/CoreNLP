/**
 * 
 */
package vn.hus.nlp.tokenizer.nio;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import javax.xml.bind.JAXBException;

import vn.hus.nlp.corpus.CorpusMarshaller;
import vn.hus.nlp.corpus.jaxb.Body;
import vn.hus.nlp.corpus.jaxb.Corpus;
import vn.hus.nlp.corpus.jaxb.ObjectFactory;
import vn.hus.nlp.corpus.jaxb.S;
import vn.hus.nlp.corpus.jaxb.W;
import vn.hus.nlp.tokenizer.tokens.TaggedWord;

/**
 * @author LE HONG Phuong, phuonglh@gmail.com
 *         <p>
 *         Jul 13, 2009, 1:49:56 PM
 *         <p>
 *         An XML exporter for exporting tokenization results to XML format.
 * 
 */
public class XMLCorpusExporter implements IExporter {

	private CorpusMarshaller corpusMarshaller;
	
	public XMLCorpusExporter() {
		corpusMarshaller = new CorpusMarshaller();
	}
	
	/* (non-Javadoc)
	 * @see vn.hus.nlp.tokenizer.nio.IExporter#export(java.util.List)
	 */
	public String export(List<List<TaggedWord>> list) {
		ObjectFactory factory = CorpusMarshaller.getFactory();
		Corpus corpus = factory.createCorpus();
		corpus.setId(new Date().toString());
		Body body = factory.createBody();
		corpus.setBody(body);

		Iterator<List<TaggedWord>> iter = list.iterator();
		while (iter.hasNext()) {
			List<TaggedWord> list2 = iter.next();
			if (list2.size() == 1 && list2.get(0).getText().equals("\n")) {
				body.getPOrS().add(factory.createP());
			} else {
				S s = factory.createS();
				for (Iterator<TaggedWord> it = list2.iterator(); it.hasNext(); ) {
					TaggedWord tw = it.next();
					W w = factory.createW();
					w.setContent(tw.getText());
					w.setT(tw.getRule().getName());
					s.getW().add(w);
				}
				body.getPOrS().add(s);
			}
		}
		
		StringWriter writer = new StringWriter();
		
		try {
			corpusMarshaller.getMarshaller().marshal(corpus, writer);
			writer.close();
		} catch (JAXBException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return writer.toString();
	}
}
