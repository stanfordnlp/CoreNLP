/**
 *  @author LE Hong Phuong
 *  <p>
 *	20 mars 07
 */
package vn.hus.nlp.lang.model.bigram;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import vn.hus.nlp.lexicon.LexiconUnmarshaller;
import vn.hus.nlp.lexicon.jaxb.Corpus;
import vn.hus.nlp.lexicon.jaxb.W;

/**
 * @author LE Hong Phuong
 * <p>
 * 20 mars 07
 * <p>
 * vn.hus.tokenizer
 * <p>
 * The ambiguity resolver for the lexical tokenizer. The resolver uses
 * of a bigram model.
 */
public class Resolver {
	/**
	 * Conditional probabilities P(w_i | w_{i-1}) = P(s | f).
	 * We use a map instead of a list to hold these probabilities to accelerate
	 * search time. The value set of this map is not used. This is really a trick.
	 * <p>
	 * This map is loaded from an external data file which is outputed by 
	 * <code>Estimator</code>.
	 */
	private Map<Couple, Integer> probabilities;
	/**
	 * The unigram probabilities.
	 */
	private Map<String, Double> unigram;
	/**
	 * Lambda 1 
	 */
	private static final double LAMBDA1 = 0.996;
	/**
	 * Lambda 2
	 */
	private static final double LAMBDA2 = 0.004;
	
	/**
	 * A set of ambiguities that have been resolved by the resolver.
	 * We maintain this set in order not to re-resolve an ambiguity
	 * if it has been done.
	 */
	private Set<Ambiguity> ambiguities;
	
	private LexiconUnmarshaller unmarshaller;
	

	/**
	 * 
	 * @param probFilename a conditional probability filename
	 * @param unigramFilename unigram filename
	 */
	public Resolver(String probFilename, String unigramFilename) {
		init();
		// load conditional probabitilies
		loadProbabilities(probFilename);
		// load unigram probabilities
		loadUnigram(unigramFilename);
	}
	
	/**
	 * Load unigram model and calculate probabilities.
	 * @param unigramFilename
	 */
	private void loadUnigram(String unigramFilename) {
		System.out.println("Loading unigram model...");
		// load unigram model
		Corpus unigramCorpus =  unmarshaller.unmarshal(unigramFilename);
		List<W> ws = unigramCorpus.getBody().getW();
		for (Iterator<W> iterator = ws.iterator(); iterator.hasNext();) {
			W w = iterator.next();
			String freq = w.getMsd();
			String word = w.getContent();
			unigram.put(word, Double.parseDouble(freq));
		}
	}

	private void init() {
		probabilities = new HashMap<Couple, Integer>();
		unigram = new HashMap<String, Double>();
		ambiguities = new HashSet<Ambiguity>();
		
		// create the unmarshaller
		unmarshaller = new LexiconUnmarshaller();
		
	}
	/**
	 * Load a probability file and initialize the <code>probabilities</code> map. 
	 * @param probFilename
	 */
	private void loadProbabilities(String probFilename) {
		System.out.println("Load conditional probabilities model...");
		// load conditional prob model
		Corpus probCorpus =  unmarshaller.unmarshal(probFilename);
		List<W> ws = probCorpus.getBody().getW();
		for (Iterator<W> iterator = ws.iterator(); iterator.hasNext();) {
			W w = iterator.next();
			String prob = w.getMsd();
			String words = w.getContent();
			// split the word using |. 
			// In general, there are only 2 words, but if a word itself is a
			// comma, we simply do not consider this case :-)
			String[] two = words.split("|");
			
			if (two.length == 2) {
				// update the prob model
				String first = two[0];
				String second = two[1];
				// create a couple
				Couple couple = new Couple(first, second);
				couple.setProb(Double.parseDouble(prob));
				// add a couple to the map with a "fake" integer value 0
				probabilities.put(couple, new Integer(0));
			}
		}
	}
	
	/**
	 * Resolve an ambiguity group of three tokens.
	 * This updates the selection of an ambiguity
	 * by calling the method <code>setIsFirstGroup</code> on the ambiguity. 
	 * @param ambiguity
	 */
	public void resolve(Ambiguity ambiguity) {
		// check to see if the ambiguity has been resolved or not yet
		// if it has been already in the set, we simply return the correspondent.
		if (ambiguities.contains(ambiguity)) {
			for (Iterator<Ambiguity> it = ambiguities.iterator(); it.hasNext();) {
				Ambiguity amb = it.next();
				if (amb.equals(ambiguity)) {
					ambiguity.setIsFirstGroup(amb.getIsFirstGroup());
					return;
				}
			}
		}
		// resolve the ambiguity
		
		Couple firstCouple = new Couple(ambiguity.second, ambiguity.first);
		Couple secondCouple = new Couple(ambiguity.third, ambiguity.second);
		// calculate probs of the first couple and the second couple
		double firstCoupleProb = 0; // P(s | f)
		double secondCoupleProb = 0; // P(t | s)
		if (probabilities.containsKey(firstCouple)) {
			firstCoupleProb = LAMBDA1 * firstCouple.getProb();
			if (unigram.containsKey(firstCouple.getFirst())) {
				firstCoupleProb += (LAMBDA2 * unigram.get(firstCouple.getFirst()));
			}
		}
		if (probabilities.containsKey(secondCouple)) {
			secondCoupleProb = LAMBDA1 * secondCouple.getProb();
			if (unigram.containsKey(secondCouple.getFirst())) {
				secondCoupleProb += (LAMBDA2 * unigram.get(secondCouple.getFirst()));
			}
		}
		// compare the two probabilities and make decision
		if (firstCoupleProb < secondCoupleProb) {
			// select the second couple instead of the first (the default) couple
			ambiguity.setIsFirstGroup(false);
		}
		
		// after resolving, add the ambiguity to the set of ambiguites
		ambiguities.add(ambiguity);
		
	}
	
	/**
	 * Resolve an ambiguity that composes of three tokens.
	 * @param first
	 * @param second
	 * @param third
	 * @see #resolve(Ambiguity)
	 */
	public void resolve(String first, String second, String third) {
		resolve(new Ambiguity(first, second, third));
	}
	
	/**
	 * Resolve a list of ambiguities.
	 * @param ambiguities a list of ambiguities.
	 * @see #resolve(Ambiguity)
	 */
	public void resolve(List<Ambiguity> ambiguities) {
		for (Iterator<Ambiguity> it = ambiguities.iterator(); it.hasNext(); ) {
			Ambiguity a = it.next();
			resolve(a);
		}
	}
	/**
	 * Show the conditional probabilities set.
	 *
	 */
	public void showProbabilities() {
		Iterator<Couple> couples = probabilities.keySet().iterator();
		while (couples.hasNext()) {
			Couple c = couples.next();
			System.out.println(c);
		}
	}
	/**
	 * Show the unigram probabilities.
	 *
	 */
	public void showUnigram() {
		Iterator<String> tokens = unigram.keySet().iterator();
		while (tokens.hasNext()) {
			String token = tokens.next();
			System.out.println(token + "\t" + unigram.get(token));
		}
	}
	
	/**
	 * Get the set of ambiguities that have been resolved by the resolver.
	 * @return
	 */
	public Set<Ambiguity> getAmbiguities() {
		return ambiguities;
	}
}
