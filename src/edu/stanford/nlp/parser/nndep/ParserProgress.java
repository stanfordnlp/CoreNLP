package edu.stanford.nlp.parser.nndep;

import edu.stanford.nlp.util.concurrent.AtomicDouble;

public class ParserProgress {

	private double cost;
	private double percentCorrect;
	private double timeSpent;
	private double bestUAS;


	public ParserProgress(double cost, double percentCorrect, double timeSpent, double bestUAS) {
		this.cost = cost;
		this.percentCorrect = percentCorrect;
		this.timeSpent = timeSpent;
		this.bestUAS = bestUAS;
	}
}
