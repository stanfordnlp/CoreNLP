package edu.stanford.nlp.ie.machinereading.domains.nfl;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.stanford.nlp.ie.machinereading.structure.EntityMentionFactory;
import edu.stanford.nlp.ie.machinereading.structure.RelationMentionFactory;
import edu.stanford.nlp.ie.machinereading.structure.AnnotationUtils;
import edu.stanford.nlp.ie.machinereading.structure.EntityMention;
import edu.stanford.nlp.ie.machinereading.structure.ExtractionObject;
import edu.stanford.nlp.ie.machinereading.structure.RelationMention;
import edu.stanford.nlp.ie.machinereading.structure.Span;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;

/**
 * 
 * @author Andrey Gusev
 * 
 */
public class AMTResultNFLReader extends NFLReader {

	/**
	 * 
	 * @param args
	 *          - file to write sentences to and optional override for default
	 *          location of gazetteer
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {

		AMTResultNFLReader reader = new AMTResultNFLReader();
		reader.read("C:/dev/javanlp-data/AMT relations/results/");
	}

	private Map<String, Integer> nameToColumn;
	
	private EntityMentionFactory entityMentionFactory;
	private RelationMentionFactory relationMentionFactory;

	public AMTResultNFLReader() {
		super();
		logger.setLevel(Level.INFO);
		entityMentionFactory = new NFLEntityMentionFactory();
		relationMentionFactory = new NFLRelationMentionFactory();
	}

	@Override
	protected String getFileAcceptExtension() {
		return ".csv";
	}

	@Override
	protected void read(File input, Annotation dataset) throws Exception {

		List<String> inputLines = readLines(input);

		// first line contains the column names in csv format
		initializeNameToColumnMapping(inputLines.get(0));

		// remove headings
		inputLines.remove(0);
		// sentence to hit result mapping
		Map<String, List<HITResult>> sentenceToHitResults = getHitResults(inputLines);

		int docId = 0;
		int numRelations = 0;
		for (Map.Entry<String, List<HITResult>> hitResults : sentenceToHitResults
		    .entrySet()) {

			String textContent = hitResults.getKey();

			NFLTokenizer tokenizer = new NFLTokenizer(textContent);
			List<CoreLabel> tokens = tokenizer.tokenize();
			Annotation relSent = new Annotation(textContent);
			List<CoreLabel> labels = tokens; // AnnotationUtils.wordsToCoreLabels(tokens);
			relSent.set(CoreAnnotations.DocIDAnnotation.class, String
			    .valueOf(docId++));
			relSent.set(CoreAnnotations.TokensAnnotation.class, labels);
			AnnotationUtils.addSentence(dataset, relSent);

			// caches previously seen entity mentions; indexed by extent span
			HashMap<Integer, EntityMention> seenMentions = new HashMap<Integer, EntityMention>();
			for (HITResult result : hitResults.getValue()) {

				Counter<Integer> responses = result.getResponses();

				double positiveResponse = 0;
				double negativeResponses = 0;

				for (int response : responses.keySet()) {
					double count = responses.getCount(response);
					if (response == 1) {
						positiveResponse += count;
					} else {
						negativeResponses += count;
					}

				}

				if (positiveResponse > negativeResponses) {

					logOrPrint("\n\n++++Adding relation and entities");

					String parsableRelation = result.parsableRelation;

					List<EntityMention> argCandidates = getEntityMentions(
					    parsableRelation, relSent);

					List<EntityMention> actualArgs = new ArrayList<EntityMention>();
					for (EntityMention arg : argCandidates) {
						int seenKey = (10000000 * arg.getExtentTokenStart() + arg
						    .getExtentTokenEnd());
						EntityMention mention = seenMentions.get(seenKey);
						if (mention == null) {
							seenMentions.put(seenKey, arg);
							mention = arg;
						}
						actualArgs.add(mention);
					}

					AnnotationUtils.addEntityMentions(relSent, actualArgs);

					RelationMention relationMention = getRelationMention(
					    parsableRelation, actualArgs, relSent);

					AnnotationUtils.addRelationMention(relSent, relationMention);
					numRelations++;
					logOrPrint(relationMention.toString());

					logOrPrint(result.sentence);
					logOrPrint(result.humanReadableRelation);
					logOrPrint(responses.toString());
				}

			}
		}

		logOrPrint("Added " + numRelations + " relations");
	}

	private final static boolean PRINT = true;

	private void logOrPrint(String msg) {
		if (PRINT) {
			System.out.println(msg);
		} else {
			logger.info(msg);
		}
	}

	private RelationMention getRelationMention(String parsableRelation,
	    List<EntityMention> args, Annotation sentence) {
		String[] parts = parsableRelation.split("_");
		String type = parts[0];

		
    // sort by argument type alphabetically
    Collections.sort(args, new Comparator<EntityMention>() {

      public int compare(EntityMention one, EntityMention two) {
        return one.getType().compareTo(two.getType());
      }
    });
		
		int start = Integer.MAX_VALUE;
		int end = Integer.MIN_VALUE;
		List<ExtractionObject> argsForRel = new ArrayList<ExtractionObject>();
		for (ExtractionObject arg : args) {
			if (arg.getExtentTokenStart() < start) {
				start = arg.getExtentTokenStart();
			}

			if (arg.getExtentTokenEnd() > end) {
				end = arg.getExtentTokenEnd();
			}
			argsForRel.add(arg);
		}
		Span span = new Span(start, end);

    // fix typo in input files
    if ("gameLooser".equals(type)) {
      type = "gameLoser";
    }
		
		RelationMention rel = relationMentionFactory.constructRelationMention(
		    RelationMention.makeUniqueId(),
		    sentence, span, type, null, argsForRel, null);

		return rel;
	}

	private static final Pattern ENTITY_PATTERN = Pattern.compile("\\[.*?\\]");

	private List<EntityMention> getEntityMentions(String parsableRelation,
	    Annotation sentence) {

		Matcher m = ENTITY_PATTERN.matcher(parsableRelation);

		List<EntityMention> retVal = new ArrayList<EntityMention>();
		while (m.find()) {
			String entityToParse = m.group();
			entityToParse = entityToParse.substring(1, entityToParse.length() - 1);
			String[] parts = entityToParse.split("_");
			String label = parts[1];
			int start = Integer.valueOf(parts[2]);
			int end = Integer.valueOf(parts[3]);

			EntityMention entityMention = entityMentionFactory.constructEntityMention(
			    EntityMention.makeUniqueId(), sentence, new Span(start, end), new Span(start, end), label, null, null); 
			retVal.add(entityMention);

			logOrPrint(label + ":" + entityMention.getValue());

		}
		return retVal;

	}

	private Map<String, List<HITResult>> getHitResults(List<String> inputLines) {

		Map<String, List<HITResult>> sentenceToHitsMap = new HashMap<String, List<HITResult>>();

		// assumes that all same hits done by different people are returned together
		// - this is how AMT results are returned
		String prevHitId = null;
		HITResult hit = null;
		for (String line : inputLines) {

			// remove leading and trailing quotes
			line = line.substring(1, line.length() - 1);
			String[] values = line.split("\",\"");

			String hitId = getColumnValue(COLUMN_HIT_ID, values);
			String sentence = getColumnValue(COLUMN_SENTENCE, values);
			String humanReadableRelation = getColumnValue(COLUMN_RELATION, values);
			String responseAndRelaiton = getColumnValue(COLUMN_RESPONSE, values);

			if (!hitId.equals(prevHitId) && hit != null) {
				List<HITResult> sentenceRelations = sentenceToHitsMap.get(hit.sentence);
				if (sentenceRelations == null) {
					sentenceRelations = new ArrayList<HITResult>();
					sentenceToHitsMap.put(hit.sentence, sentenceRelations);
				}
				sentenceRelations.add(hit);
				// create new hit
				hit = new HITResult(sentence, humanReadableRelation,
				    responseAndRelaiton);
			} else if (hit == null) {
				// should only occur on the first line
				hit = new HITResult(sentence, humanReadableRelation,
				    responseAndRelaiton);
			}

			hit.addResponse(responseAndRelaiton);
			prevHitId = hitId;

		}

		return sentenceToHitsMap;

	}

	private List<String> readLines(File intput) throws IOException {
		List<String> lines = new ArrayList<String>();

		BufferedReader in = new BufferedReader(new FileReader(intput));

		String line = null;
		while ((line = in.readLine()) != null) {
			line = line.trim();
			if (line.length() > 0) {
				lines.add(line);
			}
		}

		return lines;
	}

	private void initializeNameToColumnMapping(String line) {
		// remove leading and trailing quotes
		line = line.substring(1, line.length() - 1);
		String columns[] = line.split("\",\"");
		nameToColumn = new HashMap<String, Integer>(columns.length * 2);
		for (int ind = 0; ind < columns.length; ind++) {
			nameToColumn.put(columns[ind], ind);
		}
	}

	private String getColumnValue(String column, String[] columns) {
		return columns[nameToColumn.get(column)];
	}

	private static final String COLUMN_SENTENCE = "Input.sentence";
	private static final String COLUMN_RESPONSE = "Answer.Q1";
	private static final String COLUMN_RELATION = "Input.relation";
	private static final String COLUMN_HIT_ID = "HITId";

	static class HITResult {

		private final String sentence;
		private Counter<Integer> responses;

		private final String humanReadableRelation;
		private final String parsableRelation;

		HITResult(String sentence, String humanReadableRelation, String response) {
			this.sentence = sentence;
			this.humanReadableRelation = humanReadableRelation;
			this.parsableRelation = response.substring(2);
			this.responses = new ClassicCounter<Integer>();
		}

		void addResponse(String response) {
			int value = Integer.valueOf(response.substring(0, 1));
			this.responses.incrementCount(value);
		}

		Counter<Integer> getResponses() {
			return this.responses;
		}
	}
}
