package edu.stanford.nlp.dcoref;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.pipeline.DefaultPaths;

public class Dictionaries {
  public enum MentionType { PRONOMINAL, NOMINAL, PROPER }

  public enum Gender { MALE, FEMALE, NEUTRAL, UNKNOWN }

  public enum Number { SINGULAR, PLURAL, UNKNOWN }
  public enum Animacy { ANIMATE, INANIMATE, UNKNOWN }
  public enum Person { I, YOU, HE, SHE, WE, THEY, IT, UNKNOWN}

  public final Set<String> reportVerb = new HashSet<String>(Arrays.asList(
      "accuse", "acknowledge", "add", "admit", "advise", "agree", "alert",
      "allege", "announce", "answer", "apologize", "argue",
      "ask", "assert", "assure", "beg", "blame", "boast",
      "caution", "charge", "cite", "claim", "clarify", "command", "comment",
      "compare", "complain", "concede", "conclude", "confirm", "confront", "congratulate",
      "contend", "contradict", "convey", "counter", "criticize",
      "debate", "decide", "declare", "defend", "demand", "demonstrate", "deny",
      "describe", "determine", "disagree", "disclose", "discount", "discover", "discuss",
      "dismiss", "dispute", "disregard", "doubt", "emphasize", "encourage", "endorse",
      "equate", "estimate", "expect", "explain", "express", "extoll", "fear", "feel",
      "find", "forbid", "forecast", "foretell", "forget", "gather", "guarantee", "guess",
      "hear", "hint", "hope", "illustrate", "imagine", "imply", "indicate", "inform",
      "insert", "insist", "instruct", "interpret", "interview", "invite", "issue",
      "justify", "learn", "maintain", "mean", "mention", "negotiate", "note",
      "observe", "offer", "oppose", "order", "persuade", "pledge", "point", "point out",
      "praise", "pray", "predict", "prefer", "present", "promise", "prompt", "propose",
      "protest", "prove", "provoke", "question", "quote", "raise", "rally", "read",
      "reaffirm", "realise", "realize", "rebut", "recall", "reckon", "recommend", "refer",
      "reflect", "refuse", "refute", "reiterate", "reject", "relate", "remark",
      "remember", "remind", "repeat", "reply", "report", "request", "respond",
      "restate", "reveal", "rule", "say", "see", "show", "signal", "sing",
      "slam", "speculate", "spoke", "spread", "state", "stipulate", "stress",
      "suggest", "support", "suppose", "surmise", "suspect", "swear", "teach",
      "tell", "testify", "think", "threaten", "told", "uncover", "underline",
      "underscore", "urge", "voice", "vow", "warn", "welcome",
      "wish", "wonder", "worry", "write"));

  public final Set<String> nonWords = new HashSet<String>(Arrays.asList("mm", "hmm", "ahem", "um"));
  public final Set<String> copulas = new HashSet<String>(Arrays.asList("is","are","were", "was","be", "been","become","became","becomes","seem","seemed","seems","remain","remains","remained"));
  public final Set<String> quantifiers = new HashSet<String>(Arrays.asList("not","every","any","none","everything","anything","nothing","all","enough"));
  public final Set<String> parts = new HashSet<String>(Arrays.asList("half","one","two","three","four","five","six","seven","eight","nine","ten","hundred","thousand","million","billion","tens","dozens","hundreds","thousands","millions","billions","group","groups","bunch","number","numbers","pinch","amount","amount","total","all","mile","miles","pounds"));
  public final Set<String> temporals = new HashSet<String>(Arrays.asList(
      "second", "minute", "hour", "day", "week", "month", "year", "decade", "century", "millennium",
      "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday", "now",
      "yesterday", "tomorrow", "age", "time", "era", "epoch", "morning", "evening", "day", "night", "noon", "afternoon",
      "semester", "trimester", "quarter", "term", "winter", "spring", "summer", "fall", "autumn", "season",
      "january", "february", "march", "april", "may", "june", "july", "august", "september", "october", "november", "december"));


  public final Set<String> femalePronouns = new HashSet<String>(Arrays.asList(new String[]{ "her", "hers", "herself", "she" }));
  public final Set<String> malePronouns = new HashSet<String>(Arrays.asList(new String[]{ "he", "him", "himself", "his" }));
  public final Set<String> neutralPronouns = new HashSet<String>(Arrays.asList(new String[]{ "it", "its", "itself", "where", "here", "there", "which" }));
  public final Set<String> possessivePronouns = new HashSet<String>(Arrays.asList(new String[]{ "my", "your", "his", "her", "its","our","their","whose" }));
  public final Set<String> otherPronouns = new HashSet<String>(Arrays.asList(new String[]{ "who", "whom", "whose", "where", "when","which" }));
  public final Set<String> thirdPersonPronouns = new HashSet<String>(Arrays.asList(new String[]{ "he", "him", "himself", "his", "she", "her", "herself", "hers", "her", "it", "itself", "its", "one", "oneself", "one's", "they", "them", "themself", "themselves", "theirs", "their", "they", "them", "'em", "themselves" }));
  public final Set<String> secondPersonPronouns = new HashSet<String>(Arrays.asList(new String[]{ "you", "yourself", "yours", "your", "yourselves" }));
  public final Set<String> firstPersonPronouns = new HashSet<String>(Arrays.asList(new String[]{ "i", "me", "myself", "mine", "my", "we", "us", "ourself", "ourselves", "ours", "our" }));
  public final Set<String> moneyPercentNumberPronouns = new HashSet<String>(Arrays.asList(new String[]{ "it", "its" }));
  public final Set<String> dateTimePronouns = new HashSet<String>(Arrays.asList(new String[]{ "when" }));
  public final Set<String> organizationPronouns = new HashSet<String>(Arrays.asList(new String[]{ "it", "its", "they", "their", "them", "which"}));
  public final Set<String> locationPronouns = new HashSet<String>(Arrays.asList(new String[]{ "it", "its", "where", "here", "there" }));
  public final Set<String> inanimatePronouns = new HashSet<String>(Arrays.asList(new String[]{ "it", "itself", "its", "where", "when" }));
  public final Set<String> animatePronouns = new HashSet<String>(Arrays.asList(new String[]{ "i", "me", "myself", "mine", "my", "we", "us", "ourself", "ourselves", "ours", "our", "you", "yourself", "yours", "your", "yourselves", "he", "him", "himself", "his", "she", "her", "herself", "hers", "her", "one", "oneself", "one's", "they", "them", "themself", "themselves", "theirs", "their", "they", "them", "'em", "themselves", "who", "whom", "whose" }));
  public final Set<String> indefinitePronouns = new HashSet<String>(Arrays.asList(new String[]{"another", "anybody", "anyone", "anything", "each", "either", "enough", "everybody", "everyone", "everything", "less", "little", "much", "neither", "no one", "nobody", "nothing", "one", "other", "plenty", "somebody", "someone", "something", "both", "few", "fewer", "many", "others", "several", "all", "any", "more", "most", "none", "some", "such"}));
  public final Set<String> relativePronouns = new HashSet<String>(Arrays.asList(new String[]{"that","who","which","whom","where","whose"}));
  public final Set<String> GPEPronouns = new HashSet<String>(Arrays.asList(new String[]{ "it", "itself", "its", "they","where" }));
  public final Set<String> pluralPronouns = new HashSet<String>(Arrays.asList(new String[]{ "we", "us", "ourself", "ourselves", "ours", "our", "yourself", "yourselves", "they", "them", "themself", "themselves", "theirs", "their" }));
  public final Set<String> singularPronouns = new HashSet<String>(Arrays.asList(new String[]{ "i", "me", "myself", "mine", "my", "yourself", "he", "him", "himself", "his", "she", "her", "herself", "hers", "her", "it", "itself", "its", "one", "oneself", "one's" }));
  public final Set<String> facilityVehicleWeaponPronouns = new HashSet<String>(Arrays.asList(new String[]{ "it", "itself", "its", "they", "where" }));
  public final Set<String> miscPronouns = new HashSet<String>(Arrays.asList(new String[]{"it", "itself", "its", "they", "where" }));
  public final Set<String> reflexivePronouns = new HashSet<String>(Arrays.asList(new String[]{"myself", "yourself", "yourselves", "himself", "herself", "itself", "ourselves", "themselves", "oneself"}));
  public final Set<String> transparentNouns = new HashSet<String>(Arrays.asList(new String[]{"bunch", "group",
      "breed", "class", "ilk", "kind", "half", "segment", "top", "bottom", "glass", "bottle",
      "box", "cup", "gem", "idiot", "unit", "part", "stage", "name", "division", "label", "group", "figure",
      "series", "member", "members", "first", "version", "site", "side", "role", "largest", "title", "fourth",
      "third", "second", "number", "place", "trio", "two", "one", "longest", "highest", "shortest",
      "head", "resident", "collection", "result", "last"
  }));
  public final Set<String> stopWords = new HashSet<String>(Arrays.asList(new String[]{"a", "an", "the", "of", "at",
      "on", "upon", "in", "to", "from", "out", "as", "so", "such", "or", "and", "those", "this", "these", "that",
      "for", ",", "is", "was", "am", "are", "'s", "been", "were"}));

  public final Set<String> notOrganizationPRP = new HashSet<String>(Arrays.asList(new String[]{"i", "me", "myself",
      "mine", "my", "yourself", "he", "him", "himself", "his", "she", "her", "herself", "hers", "here"}));

  public final Set<String> personPronouns = new HashSet<String>();
  public final Set<String> allPronouns = new HashSet<String>();

  public final Map<String, String> statesAbbreviation = new HashMap<String, String>();
  public final Map<String, Set<String>> demonyms = new HashMap<String, Set<String>>();
  public final Set<String> demonymSet = new HashSet<String>();
  public final Set<String> adjectiveNation = new HashSet<String>();

  public final Set<String> countries = new HashSet<String>();
  public final Set<String> statesAndProvinces = new HashSet<String>();

  public final Set<String> neutralWords = new HashSet<String>();
  public final Set<String> femaleWords = new HashSet<String>();
  public final Set<String> maleWords = new HashSet<String>();

  public final Set<String> pluralWords = new HashSet<String>();
  public final Set<String> singularWords = new HashSet<String>();

  public final Set<String> inanimateWords = new HashSet<String>();
  public final Set<String> animateWords = new HashSet<String>();

  public final Map<List<String>, int[]> genderNumber = new HashMap<List<String>, int[]>();

  private void setPronouns() {
    for(String s: animatePronouns){
      personPronouns.add(s);
    }

    allPronouns.addAll(firstPersonPronouns);
    allPronouns.addAll(secondPersonPronouns);
    allPronouns.addAll(thirdPersonPronouns);
    allPronouns.addAll(otherPronouns);

    stopWords.addAll(allPronouns);
  }

  public void loadStateAbbreviation(String statesFile) {
    BufferedReader reader = null;
    try {
      reader = new BufferedReader(new InputStreamReader(IOUtils.getInputStreamFromURLOrClasspathOrFileSystem(statesFile)));
      while(reader.ready()){
        String[] tokens = reader.readLine().split("\t");
        statesAbbreviation.put(tokens[1], tokens[0]);
        statesAbbreviation.put(tokens[2], tokens[0]);
      }
    } catch (IOException e){
      throw new RuntimeIOException(e);
    } finally {
      IOUtils.closeIgnoringExceptions(reader);
    }
  }

  private void loadDemonymLists(String demonymFile) {
    BufferedReader reader = null;
    try {
      reader = new BufferedReader(new InputStreamReader(IOUtils.getInputStreamFromURLOrClasspathOrFileSystem(demonymFile)));
      while(reader.ready()){
        String[] line = reader.readLine().split("\t");
        if(line[0].startsWith("#")) continue;
        Set<String> set = new HashSet<String>();
        for(String s : line){
          set.add(s.toLowerCase());
          demonymSet.add(s.toLowerCase());
        }
        demonyms.put(line[0].toLowerCase(), set);
      }
      adjectiveNation.addAll(demonymSet);
      adjectiveNation.removeAll(demonyms.keySet());
    } catch (IOException e){
      throw new RuntimeIOException(e);
    } finally {
      IOUtils.closeIgnoringExceptions(reader);
    }
  }

  private static void getWordsFromFile(String filename, Set<String> resultSet, boolean lowercase) throws IOException {
    BufferedReader reader = new BufferedReader(new InputStreamReader(IOUtils.getInputStreamFromURLOrClasspathOrFileSystem(filename)));
    while(reader.ready()) {
      if(lowercase) resultSet.add(reader.readLine().toLowerCase());
      else resultSet.add(reader.readLine());
    }
    IOUtils.closeIgnoringExceptions(reader);
  }

  private void loadAnimacyLists(String animateWordsFile, String inanimateWordsFile) {
    try {
      getWordsFromFile(animateWordsFile, animateWords, false);
      getWordsFromFile(inanimateWordsFile, inanimateWords, false);
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
  }

  private void loadGenderLists(String maleWordsFile, String neutralWordsFile, String femaleWordsFile) {
    try {
      getWordsFromFile(maleWordsFile, maleWords, false);
      getWordsFromFile(neutralWordsFile, neutralWords, false);
      getWordsFromFile(femaleWordsFile, femaleWords, false);
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
  }

  private void loadNumberLists(String pluralWordsFile, String singularWordsFile) {
    try {
      getWordsFromFile(pluralWordsFile, pluralWords, false);
      getWordsFromFile(singularWordsFile, singularWords, false);
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
  }
  private void loadStatesLists(String file) {
    try {
      getWordsFromFile(file, statesAndProvinces, true);
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
  }

  private void loadCountriesLists(String file) {
    try{
      BufferedReader reader = new BufferedReader(new InputStreamReader(IOUtils.getInputStreamFromURLOrClasspathOrFileSystem(file)));
      while(reader.ready()) {
        String line = reader.readLine();
        countries.add(line.split("\t")[1].toLowerCase());
      }
      reader.close();
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
  }

  private void loadGenderNumber(String file){
    try {
      BufferedReader reader = new BufferedReader(new InputStreamReader(IOUtils.getInputStreamFromURLOrClasspathOrFileSystem(file)));
      String line;
      while ((line = reader.readLine())!=null){
        String[] split = line.split("\t");
        List<String> tokens = new ArrayList<String>(Arrays.asList(split[0].split(" ")));
        String[] countStr = split[1].split(" ");
        int[] counts = new int[4];
        counts[0] = Integer.parseInt(countStr[0]);
        counts[1] = Integer.parseInt(countStr[1]);
        counts[2] = Integer.parseInt(countStr[2]);
        counts[3] = Integer.parseInt(countStr[3]);

        genderNumber.put(tokens, counts);
      }
      reader.close();
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
  }
  private void loadExtraGender(String file){
    BufferedReader reader = null;
    try {
      reader = new BufferedReader(new InputStreamReader(IOUtils.getInputStreamFromURLOrClasspathOrFileSystem(file)));
      while(reader.ready()) {
        String[] split = reader.readLine().split("\t");
        if(split[1].equals("MALE")) maleWords.add(split[0]);
        else if(split[1].equals("FEMALE")) femaleWords.add(split[0]);
      }
    } catch (IOException e){
      throw new RuntimeIOException(e);
    } finally {
      IOUtils.closeIgnoringExceptions(reader);
    }
  }

  public Dictionaries(Properties props) {
    this(props.getProperty(Constants.DEMONYM_PROP, DefaultPaths.DEFAULT_DCOREF_DEMONYM),
        props.getProperty(Constants.ANIMATE_PROP, DefaultPaths.DEFAULT_DCOREF_ANIMATE),
        props.getProperty(Constants.INANIMATE_PROP, DefaultPaths.DEFAULT_DCOREF_INANIMATE),
        props.getProperty(Constants.MALE_PROP, DefaultPaths.DEFAULT_DCOREF_MALE),
        props.getProperty(Constants.NEUTRAL_PROP, DefaultPaths.DEFAULT_DCOREF_NEUTRAL),
        props.getProperty(Constants.FEMALE_PROP, DefaultPaths.DEFAULT_DCOREF_FEMALE),
        props.getProperty(Constants.PLURAL_PROP, DefaultPaths.DEFAULT_DCOREF_PLURAL),
        props.getProperty(Constants.SINGULAR_PROP, DefaultPaths.DEFAULT_DCOREF_SINGULAR),
        props.getProperty(Constants.STATES_PROP, DefaultPaths.DEFAULT_DCOREF_STATES),
        props.getProperty(Constants.GENDER_NUMBER_PROP, DefaultPaths.DEFAULT_DCOREF_GENDER_NUMBER),
        props.getProperty(Constants.COUNTRIES_PROP, DefaultPaths.DEFAULT_DCOREF_COUNTRIES),
        props.getProperty(Constants.STATES_PROVINCES_PROP, DefaultPaths.DEFAULT_DCOREF_STATES_AND_PROVINCES),
        props.getProperty(Constants.EXTRA_GENDER_PROP, DefaultPaths.DEFAULT_DCOREF_EXTRA_GENDER),
        Boolean.parseBoolean(props.getProperty(Constants.BIG_GENDER_NUMBER_PROP, "false")) ||
        Boolean.parseBoolean(props.getProperty(Constants.REPLICATECONLL_PROP, "false")));
  }

  public static String signature(Properties props) {
    StringBuilder os = new StringBuilder();
    os.append(Constants.DEMONYM_PROP + ":" +
            props.getProperty(Constants.DEMONYM_PROP,
                    DefaultPaths.DEFAULT_DCOREF_DEMONYM));
    os.append(Constants.ANIMATE_PROP + ":" +
            props.getProperty(Constants.ANIMATE_PROP,
                    DefaultPaths.DEFAULT_DCOREF_ANIMATE));
    os.append(Constants.INANIMATE_PROP + ":" +
            props.getProperty(Constants.INANIMATE_PROP,
                    DefaultPaths.DEFAULT_DCOREF_INANIMATE));
    os.append(Constants.MALE_PROP + ":" +
            props.getProperty(Constants.MALE_PROP,
                    DefaultPaths.DEFAULT_DCOREF_MALE));
    os.append(Constants.NEUTRAL_PROP + ":" +
            props.getProperty(Constants.NEUTRAL_PROP,
                    DefaultPaths.DEFAULT_DCOREF_NEUTRAL));
    os.append(Constants.FEMALE_PROP + ":" +
            props.getProperty(Constants.FEMALE_PROP,
                    DefaultPaths.DEFAULT_DCOREF_FEMALE));
    os.append(Constants.PLURAL_PROP + ":" +
            props.getProperty(Constants.PLURAL_PROP,
                    DefaultPaths.DEFAULT_DCOREF_PLURAL));
    os.append(Constants.SINGULAR_PROP + ":" +
            props.getProperty(Constants.SINGULAR_PROP,
                    DefaultPaths.DEFAULT_DCOREF_SINGULAR));
    os.append(Constants.STATES_PROP + ":" +
            props.getProperty(Constants.STATES_PROP,
                    DefaultPaths.DEFAULT_DCOREF_STATES));
    os.append(Constants.GENDER_NUMBER_PROP + ":" +
            props.getProperty(Constants.GENDER_NUMBER_PROP,
                    DefaultPaths.DEFAULT_DCOREF_GENDER_NUMBER));
    os.append(Constants.COUNTRIES_PROP + ":" +
            props.getProperty(Constants.COUNTRIES_PROP,
                    DefaultPaths.DEFAULT_DCOREF_COUNTRIES));
    os.append(Constants.STATES_PROVINCES_PROP + ":" +
            props.getProperty(Constants.STATES_PROVINCES_PROP,
                    DefaultPaths.DEFAULT_DCOREF_STATES_AND_PROVINCES));
    os.append(Constants.EXTRA_GENDER_PROP + ":" +
            props.getProperty(Constants.EXTRA_GENDER_PROP,
                    DefaultPaths.DEFAULT_DCOREF_EXTRA_GENDER));
    os.append(Constants.BIG_GENDER_NUMBER_PROP + ":" +
            props.getProperty(Constants.BIG_GENDER_NUMBER_PROP,
                    "false"));
    os.append(Constants.REPLICATECONLL_PROP + ":" +
            props.getProperty(Constants.REPLICATECONLL_PROP,
                    "false"));
    return os.toString();
  }

  public Dictionaries(
      String demonymWords,
      String animateWords,
      String inanimateWords,
      String maleWords,
      String neutralWords,
      String femaleWords,
      String pluralWords,
      String singularWords,
      String statesWords,
      String genderNumber,
      String countries,
      String states,
      String extraGender,
      boolean loadBigGenderNumber) {
    loadDemonymLists(demonymWords);
    loadStateAbbreviation(statesWords);
    if(Constants.USE_ANIMACY_LIST) loadAnimacyLists(animateWords, inanimateWords);
    if(Constants.USE_GENDER_LIST) loadGenderLists(maleWords, neutralWords, femaleWords);
    if(Constants.USE_NUMBER_LIST) loadNumberLists(pluralWords, singularWords);
    if(loadBigGenderNumber) loadGenderNumber(genderNumber);
    loadCountriesLists(countries);
    loadStatesLists(states);
    loadExtraGender(extraGender);
    setPronouns();
  }

  public Dictionaries() {
    this(new Properties());
  }
}
