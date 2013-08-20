package edu.stanford.nlp.international.arabic;

import java.util.HashMap;
import java.util.StringTokenizer;
import java.util.regex.Pattern;
import java.io.*;

import edu.stanford.nlp.io.EncodingPrintWriter;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.trees.international.arabic.ATBTreeUtils;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.process.SerializableFunction;

/** This class can convert between Unicode and Buckwalter encodings of
 *  Arabic.
 * <p>
 * Sources
 * <p>
 * "MORPHOLOGICAL ANALYSIS & POS ANNOTATION," v3.8. LDC. 08 June 2009.
 *
 * http://www.ldc.upenn.edu/myl/morph/buckwalter.html
 * http://www.qamus.org/transliteration.htm (Tim Buckwalter's site)
 * http://www.livingflowers.com/Arabic_transliteration (many but hard to use)
 * http://www.cis.upenn.edu/~cis639/arabic/info/romanization.html
 * http://www.nongnu.org/aramorph/english/index.html (Java AraMorph)
 * BBN's MBuckWalter2Unicode.tab
 * see also my GALE-NOTES.txt file for other mappings ROSETTA people do.
 * Normalization of decomposed characters to composed:
 * ARABIC LETTER ALEF (\u0627), ARABIC MADDAH ABOVE (\u0653) ->
 *   ARABIC LETTER ALEF WITH MADDA ABOVE
 * ARABIC LETTER ALEF (\u0627), ARABIC HAMZA ABOVE (\u0654) ->
 *   ARABIC LETTER ALEF WITH HAMZA ABOVE (\u0623)
 * ARABIC LETTER WAW, ARABIC HAMZA ABOVE ->
 *    ARABIC LETTER WAW WITH HAMZA ABOVE
 * ARABIC LETTER ALEF, ARABIC HAMZA BELOW (\u0655) ->
 *    ARABIC LETTER ALEF WITH HAMZA BELOW
 * ARABIC LETTER YEH, ARABIC HAMZA ABOVE ->
 *    ARABIC LETTER YEH WITH HAMZA ABOVE
 *  
 *  @author Christopher Manning
 *  @author Spence Green
 */
public class Buckwalter implements SerializableFunction<String,String> {

	private static final long serialVersionUID = 4351710914246859336L;

	/**
	 * If true (include flag "-o"), outputs space separated
	 * unicode values (e.g., "\u0621" rather than the character version of those values.
	 * Only applicable for Buckwalter to Arabic conversion.
	 */
	boolean outputUnicodeValues = false;

	private final char[] arabicChars = {
			'\u0621', '\u0622', '\u0623', '\u0624', '\u0625', '\u0626', '\u0627',
			'\u0628', '\u0629', '\u062A', '\u062B',
			'\u062C', '\u062D', '\u062E', '\u062F',
			'\u0630', '\u0631', '\u0632', '\u0633',
			'\u0634', '\u0635', '\u0636', '\u0637', '\u0638', '\u0639', '\u063A',
			'\u0640', '\u0641', '\u0642', '\u0643',
			'\u0644', '\u0645', '\u0646', '\u0647',
			'\u0648', '\u0649', '\u064A', '\u064B',
			'\u064C', '\u064D', '\u064E', '\u064F',
			'\u0650', '\u0651', '\u0652',
			'\u0670', '\u0671',
			'\u067E', '\u0686', '\u0698', '\u06A4', '\u06AF',
			'\u0625', '\u0623', '\u0624',    // add Tim's "XML-friendly" just in case
			'\u060C', '\u061B', '\u061F', // from BBN script; status unknown
			'\u066A', '\u066B', // from IBM script
			'\u06F0','\u06F1','\u06F2','\u06F3','\u06F4', //Farsi/Urdu cardinals
			'\u06F5','\u06F6','\u06F7','\u06F8','\u06F9',
			'\u0660', '\u0661', '\u0662', '\u0663', '\u0664',
			'\u0665', '\u0666', '\u0667', '\u0668', '\u0669',
			'\u00AB', '\u00BB' // French quotes used in e.g. Gulf newswire
	};

	private final char[] buckChars = {
			'\'', '|', '>', '&', '<', '}', 'A',
			'b', 'p', 't', 'v',
			'j', 'H', 'x', 'd', // end 062x
			'*', 'r', 'z', 's',
			'$', 'S', 'D', 'T', 'Z', 'E', 'g', // end 063x
			'_', 'f', 'q', 'k',
			'l', 'm', 'n', 'h',
			'w', 'Y', 'y', 'F',
			'N', 'K', 'a', 'u', // end 0064x
			'i', '~', 'o',
			'`', '{',
			'P', 'J', 'R', 'V', 'G', // U+0698 is Farsi Jeh: R to ATB POS guidelines
			'I', 'O', 'W',   // add Tim's "XML-friendly" versions just in case
			',', ';', '?', // from BBN script; status unknown
			'%', '.', // from IBM script
			'0', '1', '2', '3', '4',
			'5', '6', '7', '8', '9',
			'0', '1', '2', '3', '4',
			'5', '6', '7', '8', '9',
			'"', '"' // French quotes used in e.g. Gulf newswire
	};

	/* BBN also maps to @: 0x007B 0x066C 0x066D 0x0660 0x0661 0x0662 0x0663
                         0x0664 0x0665 0x0666 0x0667 0x0668 0x0669 0x066A
                         0x0686 0x06AF 0x066D 0x06AF 0x06AA 0x06AB 0x06B1
                         0x06F0 0x06EC 0x06DF 0x06DF 0x06F4 0x002A 0x274A
                         0x00E9 0x00C9 0x00AB 0x00BB 0x00A0 0x00A4
	 */
	/* BBNWalter dispreferring punct chars:
     '\u0624', '\u0625', '\u0626',  -> 'L', 'M', 'Q',
     '\u0630', -> 'C', '\u0640', -> '@', '\u0651', -> 'B',
	 */
	/* IBM also deletes: 654 655 670 */

	private boolean unicode2Buckwalter = false;
	private final HashMap<Character,Character> u2bMap;
	private final HashMap<Character,Character> b2uMap;
	private ClassicCounter<String> unmappable;

	private static boolean DEBUG = false;
	private static final boolean PASS_ASCII_IN_UNICODE = true;
	private static boolean SUPPRESS_DIGIT_MAPPING_IN_B2A = true;
	private static boolean SUPPRESS_PUNC_MAPPING_IN_B2A = true;

	//wsg: I have included _ in this list, which actually maps to tatweel.
	//In practice we strip tatweel as part of orthographic normalization,
	//so any instances of _ in the Buckwalter should actually be treated as
	//punctuation.
	private static final Pattern latinPunc = Pattern.compile("[\"\\?%,-;\\._]+");

	public Buckwalter() {
		if (arabicChars.length != buckChars.length)
			throw new RuntimeException(this.getClass().getName() + ": Inconsistent u2b/b2u arrays.");

		u2bMap = new HashMap<Character,Character>(arabicChars.length);
		b2uMap = new HashMap<Character,Character>(buckChars.length);
		for (int i = 0; i < arabicChars.length; i++) {
			Character charU = Character.valueOf(arabicChars[i]);
			Character charB = Character.valueOf(buckChars[i]);
			u2bMap.put(charU, charB);
			b2uMap.put(charB, charU);
		}

		if (DEBUG) unmappable = new ClassicCounter<String>();
	}

	public Buckwalter(boolean unicodeToBuckwalter) {
		this();
		unicode2Buckwalter = unicodeToBuckwalter;
	}

	public void suppressBuckDigitConversion(boolean b) { SUPPRESS_DIGIT_MAPPING_IN_B2A = b; }

	public void suppressBuckPunctConversion(boolean b) { SUPPRESS_PUNC_MAPPING_IN_B2A = b; }

	public String apply(String in) { return convert(in, unicode2Buckwalter); }

	public String buckwalterToUnicode(String in) { return convert(in, false);	}

	public String unicodeToBuckwalter(String in) { return convert(in, true); }

	private String convert(String in, boolean unicodeToBuckwalter) {
		final StringTokenizer st = new StringTokenizer(in);
		final StringBuilder result = new StringBuilder(in.length());

		while(st.hasMoreTokens()) {
			final String token = st.nextToken();
			for (int i = 0; i < token.length(); i++) {
				if(ATBTreeUtils.reservedWords.contains(token)) {
					result.append(token);
					break;
				}

				final Character inCh = Character.valueOf(token.charAt(i));
				Character outCh = null;

				if (unicodeToBuckwalter) {
					outCh = (PASS_ASCII_IN_UNICODE && inCh.charValue() < 127) ? inCh : u2bMap.get(inCh);

				} else if((SUPPRESS_DIGIT_MAPPING_IN_B2A && Character.isDigit(inCh)) ||
						(SUPPRESS_PUNC_MAPPING_IN_B2A && latinPunc.matcher(inCh.toString()).matches())) {
					outCh = inCh;

				} else {
					outCh = b2uMap.get(inCh);
				}

				if (outCh == null) {
					if (DEBUG) {
						String key = inCh + "[U+" +
						StringUtils.padLeft(Integer.toString(inCh, 16).toUpperCase(), 4, '0') + ']';
						unmappable.incrementCount(key);
					}
					result.append(inCh);  // pass through char

				} else if(outputUnicodeValues) {
					result.append("\\u").append(StringUtils.padLeft(Integer.toString(inCh, 16).toUpperCase(), 4, '0'));

				} else {
					result.append(outCh);
				}
			}
			result.append(" ");
		}

		return result.toString().trim();
	}


	private static final StringBuilder usage = new StringBuilder();
	static {
		usage.append("Usage: java Buckwalter [OPTS] file   (or < file)\n");
		usage.append("Options:\n");
		usage.append("          -u2b : Unicode -> Buckwalter (default is Buckwalter -> Unicode).\n");
		usage.append("          -d   : Debug mode.\n");
		usage.append("          -o   : Output unicode values.\n");	
	}

	/**
	 * 
	 * @param args
	 */
	public static void main(String[] args) {

		boolean unicodeToBuck = false;
		boolean outputUnicodeValues = false;
		File inputFile = null;
		for(int i = 0; i < args.length; i++) {
			if(args[i].startsWith("-")) {
				if(args[i].equals("-u2b"))
					unicodeToBuck = true;
				else if(args[i].equals("-o"))
					outputUnicodeValues = false;
				else if(args[i].equals("-d"))
					DEBUG = true;
				else {
					System.out.println(usage.toString());
					return;					
				}

			} else if(i != args.length) {
				inputFile = new File(args[i]);
				break;
			}
		}

		final Buckwalter b = new Buckwalter(unicodeToBuck);
		b.outputUnicodeValues = outputUnicodeValues;

		int j = (b.outputUnicodeValues ? 2 : Integer.MAX_VALUE);
		if (j < args.length) {
			for (; j < args.length; j++)
				EncodingPrintWriter.out.println(args[j] + " -> " + b.apply(args[j]), "utf-8");

		} else {
			int numLines = 0;			
			try {
				final BufferedReader br = (inputFile == null) ? new BufferedReader(new InputStreamReader(System.in, "utf-8")) :
					new BufferedReader(new InputStreamReader(new FileInputStream(inputFile), "utf-8"));

				System.err.printf("Reading input...");
				String line;
				while ((line = br.readLine()) != null) {
					EncodingPrintWriter.out.println(b.apply(line), "utf-8");
					numLines++;
				}
				br.close();

				System.err.printf("done.\nConverted %d lines from %s.\n",numLines, 
						(unicodeToBuck ? "UTF-8 to Buckwalter" : "Buckwalter to UTF-8"));

			} catch (UnsupportedEncodingException e) {
				System.err.println("ERROR: File system does not support UTF-8 encoding.");

			} catch (FileNotFoundException e) {
				System.err.println("ERROR: File does not exist: " + inputFile.getPath());

			} catch (IOException e) {
				System.err.printf("ERROR: IO exception while reading file (line %d).\n",numLines);
			}
		}

		if (DEBUG) {
			if ( ! b.unmappable.keySet().isEmpty()) {
				EncodingPrintWriter.err.println("Characters that could not be converted [passed through!]:", "utf-8");
				EncodingPrintWriter.err.println(b.unmappable.toString(), "utf-8");
			} else {
				EncodingPrintWriter.err.println("All characters successfully converted!", "utf-8");
			}
		}
	}

}
