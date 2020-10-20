package edu.stanford.nlp.international.arabic.pipeline; 
import edu.stanford.nlp.util.logging.Redwood;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.stanford.nlp.trees.treebank.Mapper;
import edu.stanford.nlp.util.Generics;

/**
 * Maps pre-terminal ATB morphological analyses to the shortened Bies tag set.
 *
 * @author Spence Green
 *
 */
public class LDCPosMapper implements Mapper  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(LDCPosMapper.class);

	protected Pattern startOfTagMap = Pattern.compile("\\(tag-map");
	protected Pattern endOfTagMap = Pattern.compile("^\\s*\\)\\s*$");
	protected Pattern mapping = Pattern.compile("\\((\\S+)\\s+(\\S+)\\)\\s*$");
	protected int numExpectedTokens = 2;

	private boolean addDT = false;
	private final Pattern determiner = Pattern.compile("DET");
	private final Pattern nounBaseTag = Pattern.compile("NN");
	private final Pattern adjBaseTag = Pattern.compile("JJ");
	private final Pattern LDCdeterminer = Pattern.compile("DT\\+");

	protected final Map<String,String> tagMap;
	protected final Set<String> tagsToEscape;

	public LDCPosMapper() {
		this(false);
	}

	public LDCPosMapper(boolean addDeterminer) {
		addDT = addDeterminer;
		tagMap = Generics.newHashMap();

		//Pre-terminal tags that do not appear in LDC tag maps
		tagsToEscape = Generics.newHashSet();
		tagsToEscape.add("-NONE-");             //Traces
		tagsToEscape.add("PUNC");               //Punctuation
	}

	/**
	 *
	 * @param posTag The preterminal tag
	 * @param terminal The optional terminal, which may be used for context
	 */
	public String map(String posTag, String terminal) {
		String rawTag = posTag.trim();

		if(tagMap.containsKey(rawTag))
			return tagMap.get(rawTag);
		else if(tagsToEscape.contains(rawTag))
			return rawTag;

		System.err.printf("%s: No mapping for %s%n", this.getClass().getName(),rawTag);

		return rawTag;
	}

	//Modifies the shortened tag based on information contained in the longer tag
	private String processShortTag(String longTag, String shortTag) {
		if(shortTag == null) return null;

		//Hacks to make p5+ mappings compatible with p1-3
		if(shortTag.startsWith("DT+"))
		  shortTag = LDCdeterminer.matcher(shortTag).replaceAll("");
		if(longTag.equals("NUMERIC_COMMA"))
		  shortTag = "PUNC";

		//As recommended by (Kulick et al., 2006)
		if(addDT && (longTag != null)) {
			Matcher detInLongTag = determiner.matcher(longTag);
			Matcher someKindOfNoun = nounBaseTag.matcher(shortTag);
			Matcher someKindOfAdj = adjBaseTag.matcher(shortTag);

			if(detInLongTag.find() && (someKindOfNoun.find() || someKindOfAdj.find()))
				shortTag = "DT" + shortTag.trim();
		}

		if(tagMap.containsKey(longTag)) {
			String existingShortTag = tagMap.get(longTag);
			if(!existingShortTag.equals(shortTag))
				System.err.printf("%s: Union of mapping files will cause overlap for %s (current: %s new: %s)%n", this.getClass().getName(),longTag,existingShortTag,shortTag);
			return existingShortTag;
		}

		return shortTag;
	}

	public void setup(File path, String... options) {
		if(path == null || !path.exists()) return;

		LineNumberReader reader = null;
		try {
			reader = new LineNumberReader(new FileReader(path));
			boolean insideTagMap = false;
			for(String line; (line = reader.readLine()) != null; ) {
				line = line.trim();

				Matcher isStartSymbol = startOfTagMap.matcher(line);
				insideTagMap = (isStartSymbol.matches() || insideTagMap);

				if(insideTagMap) {
					//Comment line
					if(line.startsWith(";")) continue;

					Matcher mappingLine = mapping.matcher(line);
					if(mappingLine.find()) {
						if(mappingLine.groupCount() == numExpectedTokens) {
							String finalShortTag = processShortTag(mappingLine.group(1),mappingLine.group(2));
							tagMap.put(mappingLine.group(1), finalShortTag);
						}
						else
							System.err.printf("%s: Skipping bad mapping in %s (line %d)%n",this.getClass().getName(),path.getPath(),reader.getLineNumber());
					}

					Matcher isEndSymbol = endOfTagMap.matcher(line);
					if(isEndSymbol.matches()) break;
				}
			}

			reader.close();

		} catch (FileNotFoundException e) {
			System.err.printf("%s: Could not open mapping file %s%n", this.getClass().getName(),path.getPath());
		} catch (IOException e) {
		  int lineNum = (reader == null) ? -1 : reader.getLineNumber();
			System.err.printf("%s: Error reading %s (line %d)%n",this.getClass().getName(),path.getPath(),lineNum);
		}
	}

	public boolean canChangeEncoding(String parent, String element) {
		//POS tags aren't encoded, so no need to check
		return true;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		for (String longTag : tagMap.keySet()) {
		  sb.append(longTag).append('\t').append(tagMap.get(longTag)).append('\n');
		}
		return sb.toString();
	}

	public static void main(String[] args) {
		Mapper mapper = new LDCPosMapper(true);
		File mapFile = new File("/u/nlp/data/Arabic/ldc/atb-latest/p1/docs/atb1-v4.0-taglist-conversion-to-PennPOS-forrelease.lisp");
		mapper.setup(mapFile);

		String test1 = "DET+NOUN+NSUFF_FEM_SG+CASE_DEF_ACC";
		String test2 = "ADJXXXXX";
		String test3 = "REL_ADV";
		String test4 = "NUMERIC_COMMA";

		System.out.printf("%s --> %s\n",test1,mapper.map(test1, null));
		System.out.printf("%s --> %s\n",test2,mapper.map(test2, null));
		System.out.printf("%s --> %s\n",test3,mapper.map(test3, null));
		System.out.printf("%s --> %s\n",test4,mapper.map(test4, null));
	}

}
