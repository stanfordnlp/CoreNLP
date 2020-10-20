package edu.stanford.nlp.ie;

import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.pipeline.*;
import edu.stanford.nlp.util.*;

import java.util.*;
import java.util.stream.*;

/**
 * Perform basic coreference for Spanish
 */

public class KBPBasicSpanishCorefSystem {

  public static final String NER_PERSON = "PERSON";
  public static final String NER_ORGANIZATION = "ORGANIZATION";

  public static final Set<String> CORPORATE_SUFFIXES = Collections.unmodifiableSet(new HashSet<String>() {{
    // From http://en.wikipedia.org/wiki/Types_of_companies#United_Kingdom
    add("cic"); add("cio"); add("general partnership"); add("llp"); add("llp."); add("limited liability partnership");
    add("lp"); add("lp."); add("limited partnership"); add("ltd"); add("ltd."); add("plc"); add("plc.");
    add("private company limited by guarantee"); add("unlimited company"); add("sole proprietorship");
    add("sole trader");
    // From http://en.wikipedia.org/wiki/Types_of_companies#United_States
    add("na"); add("nt&sa"); add("federal credit union"); add("federal savings bank"); add("lllp"); add("lllp.");
    add("llc"); add("llc."); add("lc"); add("lc."); add("ltd"); add("ltd."); add("co"); add("co.");
    add("pllc"); add("pllc."); add("corp"); add("corp."); add("inc"); add("inc.");
    add("pc"); add("p.c."); add("dba");
    // From state requirements section
    add("corporation"); add("incorporated"); add("limited"); add("association"); add("company"); add("clib");
    add("syndicate"); add("institute"); add("fund"); add("foundation"); add("club"); add("partners");
  }});

  public List<CoreEntityMention> wrapEntityMentions(List<CoreMap> entityMentions) {
    return entityMentions.stream().map(em -> new CoreEntityMention(null, em)).collect(Collectors.toList());
  }

  /**
   * A utility to strip out corporate titles (e.g., "corp.", "incorporated", etc.)
   * @param input The string to strip titles from
   * @return A string without these titles, or the input string if not such titles exist.
   */
  protected String stripCorporateTitles(String input) {
    for (String suffix : CORPORATE_SUFFIXES) {
      if (input.toLowerCase().endsWith(suffix)) {
        return input.substring(0, input.length() - suffix.length()).trim();
      }
    }
    return input;
  }

  public String noSpecialChars(String original) {
    char[] chars = original.toCharArray();
    // Compute the size of the output
    int size = 0;
    boolean isAllLowerCase = true;
    for (char aChar : chars) {
      if (aChar != '\\' && aChar != '"' && aChar != '-') {
        if (isAllLowerCase && !Character.isLowerCase(aChar)) { isAllLowerCase = false; }
        size += 1;
      }
    }
    if (size == chars.length && isAllLowerCase) { return original; }
    // Copy to a new String
    char[] out = new char[size];
    int i = 0;
    for (char aChar : chars) {
      if (aChar != '\\' && aChar != '"' && aChar != '-') {
        out[i] = Character.toLowerCase(aChar);
        i += 1;
      }
    }
    // Return
    return new String(out);
  }

  /** see if a potential mention is longer or same length and appears earlier **/
  public boolean moreCanonicalMention(CoreMap entityMention, CoreMap potentialCanonicalMention) {
    // text of the mentions
    String entityMentionText = entityMention.get(CoreAnnotations.TextAnnotation.class);
    String potentialCanonicalMentionText = potentialCanonicalMention.get(CoreAnnotations.TextAnnotation.class);
    // start positions of mentions
    int entityMentionStart = entityMention.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class);
    int potentialCanonicalMentionStart =
        potentialCanonicalMention.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class);
    if (potentialCanonicalMentionText.length() > entityMentionText.length())
      return true;
    else if (potentialCanonicalMentionText.length() == entityMentionText.length() &&
        potentialCanonicalMentionStart < entityMentionStart)
      return true;
    else
      return false;
  }

  public boolean firstNameMatch(String firstNameOne, String firstNameTwo) {
    return Math.min(firstNameOne.length(), firstNameTwo.length()) >= 5 &&
        StringUtils.levenshteinDistance(firstNameOne, firstNameTwo) < 3;
  }

  protected boolean sameEntityWithoutLinking(CoreEntityMention emOne, CoreEntityMention emTwo) {
    String type = emOne.entityType();
    if (type.equals(NER_PERSON) && emOne.tokens().size() >= 2 && emTwo.tokens().size() >= 2 &&
        emOne.tokens().get(emOne.tokens().size() - 1).word().toLowerCase().equals(
            emTwo.tokens().get(emTwo.tokens().size() - 1).word().toLowerCase())) {
      String firstNameOne = emOne.tokens().get(0).word().toLowerCase();
      String firstNameTwo = emTwo.tokens().get(0).word().toLowerCase();
      if (firstNameMatch(firstNameOne, firstNameTwo)) {
        return true;
      } else if (emOne.tokens().size() == 2 && emTwo.tokens().size() == 2){
        return false;
      }
    }

    // Proper match score
    double matchScore = Math.max(
        approximateEntityMatchScore(emOne.text(), emTwo.text()),
        approximateEntityMatchScore(emTwo.text(), emOne.text()));

    // Some simple cases
    if( matchScore == 1.0 ) {
      return true;
    }
    if( matchScore < 0.34 ) {
      return false;
    }
    if (type.equals(NER_PERSON) && matchScore > 0.49) {
      // Both entities are more than one character
      if (Math.min(emOne.text().length(), emTwo.text().length()) > 1) {
        // Last names match
        if ( (emOne.tokens().size() == 1 && emTwo.tokens().size() > 1 && emTwo.tokens().get(emTwo.tokens().size() - 1).word().equalsIgnoreCase(emOne.tokens().get(0).word())) ||
            (emTwo.tokens().size() == 1 && emOne.tokens().size() > 1 && emOne.tokens().get(emOne.tokens().size() - 1).word().equalsIgnoreCase(emTwo.tokens().get(0).word())) ) {
          return true;
        }
        // First names match
        if ((emOne.tokens().size() == 1 && emTwo.tokens().size() > 1 && emTwo.tokens().get(0).word().equalsIgnoreCase(emOne.tokens().get(0).word())) ||
            (emTwo.tokens().size() == 1 && emOne.tokens().size() > 1 && emOne.tokens().get(0).word().equalsIgnoreCase(emTwo.tokens().get(0).word()))
            ) {
          return true;
        }
      }
      if (matchScore > 0.65) {
        return true;
      }
    }

    if (type == NER_ORGANIZATION && matchScore > 0.79) {
      return true;
    }

    return false;

  }

  private boolean nearExactEntityMatch( String higherGloss, String lowerGloss ) {
    // case: slots have same relation, and that relation isn't an alternate name
    // Filter case sensitive match
    if (higherGloss.equalsIgnoreCase(lowerGloss)) { return true; }
    // Ignore certain characters
    else if (noSpecialChars(higherGloss).equalsIgnoreCase(noSpecialChars(lowerGloss))) { return true; }
    return false;
  }

  /**
   * Approximately check if two entities are equivalent.
   * Taken largely from
   * edu.stanford.nlp.kbp.slotfilling.evaluate,HeuristicSlotfillPostProcessors.NoDuplicatesApproximate;
   */
  public double approximateEntityMatchScore(String higherGloss, String lowerGloss) {
    if( nearExactEntityMatch(higherGloss, lowerGloss) ) return 1.0;

    String[] higherToks = stripCorporateTitles(higherGloss).split("\\s+");
    String[] lowerToks = stripCorporateTitles(lowerGloss).split("\\s+");
    // Case: acronyms of each other
    if (AcronymMatcher.isAcronym(higherToks, lowerToks)) { return 1.0; }

    int match = 0;
    // Get number of matching tokens between the two slot fills
    boolean[] matchedHigherToks = new boolean[higherToks.length];
    boolean[] matchedLowerToks = new boolean[lowerToks.length];
    for (int h = 0; h < higherToks.length; ++h) {
      if (matchedHigherToks[h]) { continue; }
      String higherTok = higherToks[h];
      String higherTokNoSpecialChars = noSpecialChars(higherTok);
      boolean doesMatch = false;
      for (int l = 0; l < lowerToks.length; ++l) {
        if (matchedLowerToks[l]) { continue; }
        String lowerTok = lowerToks[l];
        String lowerTokNoSpecialCars = noSpecialChars(lowerTok);
        int minLength = Math.min(lowerTokNoSpecialCars.length(), higherTokNoSpecialChars.length());
        if (higherTokNoSpecialChars.equalsIgnoreCase(lowerTokNoSpecialCars) ||  // equal
            (minLength > 5 && (higherTokNoSpecialChars.endsWith(lowerTokNoSpecialCars) || higherTokNoSpecialChars.startsWith(lowerTokNoSpecialCars))) ||  // substring
            (minLength > 5 && (lowerTokNoSpecialCars.endsWith(higherTokNoSpecialChars) || lowerTokNoSpecialCars.startsWith(higherTokNoSpecialChars))) ||  // substring the other way
            (minLength > 5 && StringUtils.levenshteinDistance(lowerTokNoSpecialCars, higherTokNoSpecialChars) <= 1)  // edit distance <= 1
            ) {
          doesMatch = true;  // a loose metric of "same token"
          matchedHigherToks[h] = true;
          matchedLowerToks[l] = true;
        }
      }
      if (doesMatch) { match += 1; }
    }

    return (double) match / ((double) Math.max(higherToks.length, lowerToks.length));
  }

  public List<List<CoreMap>> clusterEntityMentions(List<CoreMap> entityMentions) {
    List<CoreEntityMention> wrappedEntityMentions = wrapEntityMentions(entityMentions);
    ArrayList<ArrayList<CoreEntityMention>> entityMentionClusters = new ArrayList<ArrayList<CoreEntityMention>>();
    for (CoreEntityMention newEM : wrappedEntityMentions) {
      boolean clusterMatch = false;
      for (ArrayList<CoreEntityMention> emCluster : entityMentionClusters) {
        for (CoreEntityMention clusterEM : emCluster) {
          if (sameEntityWithoutLinking(newEM, clusterEM)) {
            emCluster.add(newEM);
            clusterMatch = true;
            break;
          }
        }
        if (clusterMatch)
            break;
      }
      if (!clusterMatch) {
        ArrayList<CoreEntityMention> newCluster = new ArrayList<>();
        newCluster.add(newEM);
        entityMentionClusters.add(newCluster);
      }
    }
    List<List<CoreMap>> coreMapEntityMentionClusters = new ArrayList<List<CoreMap>>();
    for (ArrayList<CoreEntityMention> emCluster : entityMentionClusters) {
      List<CoreMap> coreMapCluster =
          emCluster.stream().map(coreEM -> coreEM.coreMap()).collect(Collectors.toList());
      coreMapEntityMentionClusters.add(coreMapCluster);
    }
    return coreMapEntityMentionClusters;
  }

  public CoreMap bestEntityMention(List<CoreMap> entityMentionCluster) {
    CoreMap bestEntityMention = null;
    for (CoreMap candidateEntityMention : entityMentionCluster) {
      if (bestEntityMention == null) {
        bestEntityMention = candidateEntityMention;
        continue;
      } else if (candidateEntityMention.get(CoreAnnotations.TextAnnotation.class).length() >
          bestEntityMention.get(CoreAnnotations.TextAnnotation.class).length()) {
        bestEntityMention = candidateEntityMention;
        continue;
      } else if (candidateEntityMention.get(CoreAnnotations.TextAnnotation.class).length() ==
          bestEntityMention.get(CoreAnnotations.TextAnnotation.class).length() &&
          candidateEntityMention.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class) <
          bestEntityMention.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class)) {
        bestEntityMention = candidateEntityMention;
        continue;
      }
    }
    return bestEntityMention;
  }

  public Map<CoreMap,CoreMap> createCanonicalMentionMap(List<List<CoreMap>> entityMentionClusters) {
    Map<CoreMap,CoreMap> canonicalMentionMap = new HashMap<CoreMap,CoreMap>();
    for (List<CoreMap> entityMentionCluster : entityMentionClusters) {
      CoreMap bestEntityMention = bestEntityMention(entityMentionCluster);
      for (CoreMap clusterEntityMention : entityMentionCluster) {
        canonicalMentionMap.put(clusterEntityMention, bestEntityMention);
      }
    }
    return canonicalMentionMap;
  }

  public Map<CoreMap,CoreMap> canonicalMentionMapFromEntityMentions(List<CoreMap> entityMentions) {
    List<List<CoreMap>> entityMentionClusters = clusterEntityMentions(entityMentions);
    return createCanonicalMentionMap(entityMentionClusters);
  }

}
