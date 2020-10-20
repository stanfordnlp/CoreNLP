package edu.stanford.nlp.ie.machinereading; 
import edu.stanford.nlp.util.logging.Redwood;

import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.stanford.nlp.ie.machinereading.structure.EntityMention;
import edu.stanford.nlp.ie.machinereading.structure.MachineReadingAnnotations;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.StringUtils;

public class EntityExtractorResultsPrinter extends ResultsPrinter  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(EntityExtractorResultsPrinter.class);

	/** Contains a set of labels that should be excluded from scoring */
	private Set<String> excludedClasses;

	/** Use subtypes for scoring or just types? */
	private boolean useSubTypes;

	private boolean verbose;
	
	private boolean verboseInstances;

	private static final DecimalFormat FORMATTER = new DecimalFormat();
	static {
		FORMATTER.setMaximumFractionDigits(1);
		FORMATTER.setMinimumFractionDigits(1);
	}

	public EntityExtractorResultsPrinter() {
	  this(null, false);
	}

	protected EntityExtractorResultsPrinter(Set<String> excludedClasses, boolean useSubTypes) {
		this.excludedClasses = excludedClasses;
		this.useSubTypes = useSubTypes;
		this.verbose = true;
		this.verboseInstances = true;
	}

	@Override
	public void printResults(PrintWriter pw, List<CoreMap> goldStandard,
	    List<CoreMap> extractorOutput) {
		ResultsPrinter.align(goldStandard, extractorOutput);

		Counter<String> correct = new ClassicCounter<>();
		Counter<String> predicted = new ClassicCounter<>();
		Counter<String> gold = new ClassicCounter<>();

		for (int i = 0; i < goldStandard.size(); i++) {
			CoreMap goldSent = goldStandard.get(i);
			CoreMap sysSent = extractorOutput.get(i);
			String sysText = sysSent.get(TextAnnotation.class);
			String goldText = goldSent.get(TextAnnotation.class);

			if (verbose) {
			  log.info("SCORING THE FOLLOWING SENTENCE:");
				log.info(sysSent.get(CoreAnnotations.TokensAnnotation.class));
			}

			HashSet<String> matchedGolds = new HashSet<>();
			List<EntityMention> goldEntities = goldSent
			    .get(MachineReadingAnnotations.EntityMentionsAnnotation.class);
			if (goldEntities == null) {
				goldEntities = new ArrayList<>();
			}

			for (EntityMention m : goldEntities) {
				String label = makeLabel(m);
				if (excludedClasses != null && excludedClasses.contains(label))
					continue;
				gold.incrementCount(label);
			}

			List<EntityMention> sysEntities = sysSent
			    .get(MachineReadingAnnotations.EntityMentionsAnnotation.class);
			if (sysEntities == null) {
				sysEntities = new ArrayList<>();
			}
			for (EntityMention m : sysEntities) {
				String label = makeLabel(m);
				if (excludedClasses != null && excludedClasses.contains(label))
					continue;
				predicted.incrementCount(label);
				if (verbose)
					log.info("COMPARING PREDICTED MENTION: " + m);

				boolean found = false;
				for (EntityMention gm : goldEntities) {
					if (matchedGolds.contains(gm.getObjectId()))
						continue;
					if (verbose)
						log.info("\tagainst: " + gm);
					if(gm.equals(m, useSubTypes)){
						if (verbose) log.info("\t\t\tMATCH!");
						found = true;
						matchedGolds.add(gm.getObjectId());
						if(verboseInstances){
						  log.info("TRUE POSITIVE: " + m + " matched " + gm);
						  log.info("In sentence: " + sysText);
						}
						break;
					}
				}

				if (found) {
					correct.incrementCount(label);
				} else if(verboseInstances){
				  log.info("FALSE POSITIVE: " + m.toString());
				  log.info("In sentence: " + sysText);
				}
			}
			
			if (verboseInstances) {
				for (EntityMention m : goldEntities) {
					String label = makeLabel(m);
					if (!matchedGolds.contains(m.getObjectId())
					    && (excludedClasses == null || !excludedClasses.contains(label))) {
					  log.info("FALSE NEGATIVE: " + m.toString());
	          log.info("In sentence: " + goldText);
					}
				}
			}
		}

		double totalCount = 0;
		double totalCorrect = 0;
		double totalPredicted = 0;
		pw.println("Label\tCorrect\tPredict\tActual\tPrecn\tRecall\tF");
		List<String> labels = new ArrayList<>(gold.keySet());
		Collections.sort(labels);
		for (String label : labels) {
			if (excludedClasses != null && excludedClasses.contains(label))
				continue;
			double numCorrect = correct.getCount(label);
			double numPredicted = predicted.getCount(label);
			double trueCount = gold.getCount(label);
			double precision = (numPredicted > 0) ? (numCorrect / numPredicted) : 0;
			double recall = numCorrect / trueCount;
			double f = (precision + recall > 0) ? 2 * precision * recall
			    / (precision + recall) : 0.0;
			pw.println(StringUtils.padOrTrim(label, 21) + "\t" + numCorrect + "\t"
			    + numPredicted + "\t" + trueCount + "\t"
			    + FORMATTER.format(precision * 100) + "\t"
			    + FORMATTER.format(100 * recall) + "\t" + FORMATTER.format(100 * f));
			totalCount += trueCount;
			totalCorrect += numCorrect;
			totalPredicted += numPredicted;
		}
		double precision = (totalPredicted > 0) ? (totalCorrect / totalPredicted)
		    : 0;
		double recall = totalCorrect / totalCount;
		double f = (totalPredicted > 0 && totalCorrect > 0) ? 2 * precision
		    * recall / (precision + recall) : 0.0;
		pw.println("Total\t" + totalCorrect + "\t" + totalPredicted + "\t"
		    + totalCount + "\t" + FORMATTER.format(100 * precision) + "\t"
		    + FORMATTER.format(100 * recall) + "\t" + FORMATTER.format(100 * f));		
	}

	private String makeLabel(EntityMention m) {
		String label = m.getType();
		if (useSubTypes && m.getSubType() != null)
			label += "-" + m.getSubType();
		return label;
	}
	
	public void printResultsUsingLabels(PrintWriter pw, 
      List<String> goldStandard,
      List<String> extractorOutput) {}
}
