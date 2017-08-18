/**
 * (C) LE HONG Phuong, phuonglh@gmail.com
 */
package vn.hus.nlp.tokenizer.tools;

import java.util.ArrayList;
import java.util.List;

import vn.hus.nlp.fsm.builder.FSMBuilder;
import vn.hus.nlp.fsm.builder.MinimalFSMBuilder;
import vn.hus.nlp.fsm.util.FSMUtilities;
import vn.hus.nlp.lexicon.LexiconUnmarshaller;
import vn.hus.nlp.lexicon.jaxb.Corpus;
import vn.hus.nlp.lexicon.jaxb.W;
import vn.hus.nlp.tokenizer.IConstants;

/**
 * @author LE HONG Phuong, phuonglh@gmail.com
 *         <p>
 *         Jun 12, 2008, 10:53:50 AM
 *         <p>
 *         This utility is used to rebuild the minimal DFA that encodes the
 *         Vietnamese dictionary. User need to run this tool after they make
 *         updates on the lexicon (for example add or remove words). This assures
 *         that the minimal DFA encoding the lexicon is updated with changes.
 *         The construction of the minimal DFA may take some time, so it is recommended
 *         that this utility is not called frequently. They are often used when 
 *         user made a remarkable changes to the Vietnamese lexicon.  
 */
public class DFALexiconBuilder {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// load the lexicon 
		LexiconUnmarshaller  lexiconUnmarshaller = new LexiconUnmarshaller();
		Corpus lexicon = lexiconUnmarshaller.unmarshal(IConstants.LEXICON);
		List<W> ws = lexicon.getBody().getW();
		List<String> words = new ArrayList<String>();
		for (W w : ws) {
			words.add(w.getContent());
		}
		// create an FSM builder of type DFA.
		//
		FSMBuilder  builder = new MinimalFSMBuilder(vn.hus.nlp.fsm.IConstants.FSM_DFA);
		System.out.println("Updating the lexicon automaton...");
		long startTime = System.currentTimeMillis();
		builder.create(words);
		long endTime = System.currentTimeMillis();
		System.err.println("Duration = " + (endTime - startTime) + " (ms)");
		// encode the result 
		builder.encode(IConstants.LEXICON_DFA);
		// print some statistic of the DFA:
		FSMUtilities.statistic(builder.getMachine());
		// dispose the builder to save memory
		builder.dispose();
		System.out.println("Lexicon automaton updated.");
	}

}
