package edu.stanford.nlp.dcoref;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.pipeline.DefaultPaths;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.PropertiesUtils;

/** Provides accessors for various grammatical, semantic, and world knowledge
 *  lexicons and word lists primarily used by the Sieve coreference system,
 *  but sometimes also drawn on from other code.
 *
 *  The source of the dictionaries on Stanford NLP machines is
 *  /u/nlp/data/coref/gazetteers/dcoref/ . In models jars, they live in
 *  edu/stanford/nlp/models/dcoref .
 */
public class Dictionaries {

  public enum MentionType {
    PRONOMINAL(1), NOMINAL(3), PROPER(4), LIST(2);

    /**
     * A higher representativeness means that this type of mention is more preferred for choosing
     * the representative mention. See {@link Mention#moreRepresentativeThan(Mention)}.
     */
    public final int representativeness;
    MentionType(int representativeness) { this.representativeness = representativeness; }
  }

  public enum Gender { MALE, FEMALE, NEUTRAL, UNKNOWN }

  public enum Number { SINGULAR, PLURAL, UNKNOWN }
  public enum Animacy { ANIMATE, INANIMATE, UNKNOWN }
  public enum Person { I, YOU, HE, SHE, WE, THEY, IT, UNKNOWN}

  public final Set<String> reportVerb = Generics.newHashSet(Arrays.asList(
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

  public final Set<String> reportNoun = Generics.newHashSet(Arrays.asList(
      "acclamation", "account", "accusation", "acknowledgment", "address", "addressing",
      "admission", "advertisement", "advice", "advisory", "affidavit", "affirmation", "alert",
      "allegation", "analysis", "anecdote", "annotation", "announcement", "answer", "antiphon",
      "apology", "applause", "appreciation", "argument", "arraignment", "article", "articulation",
      "aside", "assertion", "asseveration", "assurance", "attestation", "attitude",
      "averment", "avouchment", "avowal", "axiom", "backcap", "band-aid", "basic", "belief", "bestowal",
      "bill", "blame", "blow-by-blow", "bomb", "book", "bow", "break", "breakdown", "brief", "briefing",
      "broadcast", "broadcasting", "bulletin", "buzz", "cable", "calendar", "call", "canard", "canon",
      "card", "cause", "censure", "certification", "characterization", "charge", "chat", "chatter",
      "chitchat", "chronicle", "chronology", "citation", "claim", "clarification", "close", "cognizance",
      "comeback", "comment", "commentary", "communication", "communique", "composition", "concept",
      "concession", "conference", "confession", "confirmation", "conjecture", "connotation", "construal",
      "construction", "consultation", "contention", "contract", "convention", "conversation", "converse",
      "conviction", "counterclaim", "credenda", "creed", "critique",
      "cry", "declaration", "defense", "definition", "delineation", "delivery", "demonstration",
      "denial", "denotation", "depiction", "deposition", "description", "detail", "details", "detention",
      "dialogue", "diction", "dictum", "digest", "directive", "disclosure", "discourse", "discovery",
      "discussion", "dispatch", "display", "disquisition", "dissemination", "dissertation", "divulgence",
      "dogma", "editorial", "ejaculation", "emphasis", "enlightenment",
      "enunciation", "essay", "evidence", "examination", "example", "excerpt", "exclamation",
      "excuse", "execution", "exegesis", "explanation", "explication", "exposing", "exposition", "expounding",
      "expression", "eye-opener", "feedback", "fiction", "findings", "fingerprint", "flash", "formulation",
      "fundamental", "gift", "gloss", "goods", "gospel", "gossip", "gratitude", "greeting",
      "guarantee", "hail", "hailing", "handout", "hash", "headlines", "hearing", "hearsay",
      "ideas", "idiom", "illustration", "impeachment", "implantation", "implication", "imputation",
      "incrimination", "indication", "indoctrination", "inference", "info", "information",
      "innuendo", "insinuation", "insistence", "instruction", "intelligence", "interpretation", "interview",
      "intimation", "intonation", "issue", "item", "itemization", "justification", "key", "knowledge",
      "leak", "letter", "locution", "manifesto",
      "meaning", "meeting", "mention", "message", "missive", "mitigation", "monograph", "motive", "murmur",
      "narration", "narrative", "news", "nod", "note", "notice", "notification", "oath", "observation",
      "okay", "opinion", "oral", "outline", "paper", "parley", "particularization", "phrase", "phraseology",
      "phrasing", "picture", "piece", "pipeline", "pitch", "plea", "plot", "portraiture", "portrayal",
      "position", "potboiler", "prating", "precept", "prediction", "presentation", "presentment", "principle",
      "proclamation", "profession", "program", "promulgation", "pronouncement", "pronunciation", "propaganda",
      "prophecy", "proposal", "proposition", "prosecution", "protestation", "publication", "publicity",
      "publishing", "quotation", "ratification", "reaction", "reason", "rebuttal", "receipt", "recital",
      "recitation", "recognition", "record", "recount", "recountal", "refutation", "regulation", "rehearsal",
      "rejoinder", "relation", "release", "remark", "rendition", "repartee", "reply", "report", "reporting",
      "representation", "resolution", "response", "result", "retort", "return", "revelation", "review",
      "rule", "rumble", "rumor", "rundown", "saying", "scandal", "scoop",
      "scuttlebutt", "sense", "showing", "sign", "signature", "significance", "sketch", "skinny", "solution",
      "speaking", "specification", "speech", "statement", "story", "study", "style", "suggestion",
      "summarization", "summary", "summons", "tale", "talk", "talking", "tattle", "telecast",
      "telegram", "telling", "tenet", "term", "testimonial", "testimony", "text", "theme", "thesis",
      "tract", "tractate", "tradition", "translation", "treatise", "utterance", "vent", "ventilation",
      "verbalization", "version", "vignette", "vindication", "warning",
      "warrant", "whispering", "wire", "word", "work", "writ", "write-up", "writeup", "writing",
      "acceptance", "complaint", "concern", "disappointment", "disclose", "estimate", "laugh", "pleasure", "regret",
      "resentment", "view"));

  public final Set<String> nonWords = Generics.newHashSet(Arrays.asList("mm", "hmm", "ahem", "um"));
  public final Set<String> copulas = Generics.newHashSet(Arrays.asList("is","are","were", "was","be", "been","become","became","becomes","seem","seemed","seems","remain","remains","remained"));
  public final Set<String> quantifiers = Generics.newHashSet(Arrays.asList("not","every","any","none","everything","anything","nothing","all","enough"));
  public final Set<String> parts = Generics.newHashSet(Arrays.asList("half","one","two","three","four","five","six","seven","eight","nine","ten","hundred","thousand","million","billion","tens","dozens","hundreds","thousands","millions","billions","group","groups","bunch","number","numbers","pinch","amount","amount","total","all","mile","miles","pounds"));
  public final Set<String> temporals = Generics.newHashSet(Arrays.asList(
      "second", "minute", "hour", "day", "week", "month", "year", "decade", "century", "millennium",
      "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday", "now",
      "yesterday", "tomorrow", "age", "time", "era", "epoch", "morning", "evening", "day", "night", "noon", "afternoon",
      "semester", "trimester", "quarter", "term", "winter", "spring", "summer", "fall", "autumn", "season",
      "january", "february", "march", "april", "may", "june", "july", "august", "september", "october", "november", "december"));


  public final Set<String> femalePronouns = Generics.newHashSet(Arrays.asList(new String[]{ "her", "hers", "herself", "she" }));
  public final Set<String> malePronouns = Generics.newHashSet(Arrays.asList(new String[]{ "he", "him", "himself", "his" }));
  public final Set<String> neutralPronouns = Generics.newHashSet(Arrays.asList(new String[]{ "it", "its", "itself", "where", "here", "there", "which" }));
  public final Set<String> possessivePronouns = Generics.newHashSet(Arrays.asList(new String[]{ "my", "your", "his", "her", "its","our","their","whose" }));
  public final Set<String> otherPronouns = Generics.newHashSet(Arrays.asList(new String[]{ "who", "whom", "whose", "where", "when","which" }));
  public final Set<String> thirdPersonPronouns = Generics.newHashSet(Arrays.asList(new String[]{ "he", "him", "himself", "his", "she", "her", "herself", "hers", "her", "it", "itself", "its", "one", "oneself", "one's", "they", "them", "themself", "themselves", "theirs", "their", "they", "them", "'em", "themselves" }));
  public final Set<String> secondPersonPronouns = Generics.newHashSet(Arrays.asList(new String[]{ "you", "yourself", "yours", "your", "yourselves" }));
  public final Set<String> firstPersonPronouns = Generics.newHashSet(Arrays.asList(new String[]{ "i", "me", "myself", "mine", "my", "we", "us", "ourself", "ourselves", "ours", "our" }));
  public final Set<String> moneyPercentNumberPronouns = Generics.newHashSet(Arrays.asList(new String[]{ "it", "its" }));
  public final Set<String> dateTimePronouns = Generics.newHashSet(Arrays.asList(new String[]{ "when" }));
  public final Set<String> organizationPronouns = Generics.newHashSet(Arrays.asList(new String[]{ "it", "its", "they", "their", "them", "which"}));
  public final Set<String> locationPronouns = Generics.newHashSet(Arrays.asList(new String[]{ "it", "its", "where", "here", "there" }));
  public final Set<String> inanimatePronouns = Generics.newHashSet(Arrays.asList(new String[]{ "it", "itself", "its", "where", "when" }));
  public final Set<String> animatePronouns = Generics.newHashSet(Arrays.asList(new String[]{ "i", "me", "myself", "mine", "my", "we", "us", "ourself", "ourselves", "ours", "our", "you", "yourself", "yours", "your", "yourselves", "he", "him", "himself", "his", "she", "her", "herself", "hers", "her", "one", "oneself", "one's", "they", "them", "themself", "themselves", "theirs", "their", "they", "them", "'em", "themselves", "who", "whom", "whose" }));
  public final Set<String> indefinitePronouns = Generics.newHashSet(Arrays.asList(new String[]{"another", "anybody", "anyone", "anything", "each", "either", "enough", "everybody", "everyone", "everything", "less", "little", "much", "neither", "no one", "nobody", "nothing", "one", "other", "plenty", "somebody", "someone", "something", "both", "few", "fewer", "many", "others", "several", "all", "any", "more", "most", "none", "some", "such"}));
  public final Set<String> relativePronouns = Generics.newHashSet(Arrays.asList(new String[]{"that","who","which","whom","where","whose"}));
  public final Set<String> GPEPronouns = Generics.newHashSet(Arrays.asList(new String[]{ "it", "itself", "its", "they","where" }));
  public final Set<String> pluralPronouns = Generics.newHashSet(Arrays.asList(new String[]{ "we", "us", "ourself", "ourselves", "ours", "our", "yourself", "yourselves", "they", "them", "themself", "themselves", "theirs", "their" }));
  public final Set<String> singularPronouns = Generics.newHashSet(Arrays.asList(new String[]{ "i", "me", "myself", "mine", "my", "yourself", "he", "him", "himself", "his", "she", "her", "herself", "hers", "her", "it", "itself", "its", "one", "oneself", "one's" }));
  public final Set<String> facilityVehicleWeaponPronouns = Generics.newHashSet(Arrays.asList(new String[]{ "it", "itself", "its", "they", "where" }));
  public final Set<String> miscPronouns = Generics.newHashSet(Arrays.asList(new String[]{"it", "itself", "its", "they", "where" }));
  public final Set<String> reflexivePronouns = Generics.newHashSet(Arrays.asList(new String[]{"myself", "yourself", "yourselves", "himself", "herself", "itself", "ourselves", "themselves", "oneself"}));
  public final Set<String> transparentNouns = Generics.newHashSet(Arrays.asList(new String[]{"bunch", "group",
      "breed", "class", "ilk", "kind", "half", "segment", "top", "bottom", "glass", "bottle",
      "box", "cup", "gem", "idiot", "unit", "part", "stage", "name", "division", "label", "group", "figure",
      "series", "member", "members", "first", "version", "site", "side", "role", "largest", "title", "fourth",
      "third", "second", "number", "place", "trio", "two", "one", "longest", "highest", "shortest",
      "head", "resident", "collection", "result", "last"
  }));
  public final Set<String> stopWords = Generics.newHashSet(Arrays.asList(new String[]{"a", "an", "the", "of", "at",
      "on", "upon", "in", "to", "from", "out", "as", "so", "such", "or", "and", "those", "this", "these", "that",
      "for", ",", "is", "was", "am", "are", "'s", "been", "were"}));

  public final Set<String> notOrganizationPRP = Generics.newHashSet(Arrays.asList(new String[]{"i", "me", "myself",
      "mine", "my", "yourself", "he", "him", "himself", "his", "she", "her", "herself", "hers", "here"}));

  public final Set<String> quantifiers2 = Generics.newHashSet(Arrays.asList("all", "both", "neither", "either"));
  public final Set<String> determiners = Generics.newHashSet(Arrays.asList("the", "this", "that", "these", "those", "his", "her", "my", "your", "their", "our"));
  public final Set<String> negations = Generics.newHashSet(Arrays.asList("n't","not", "nor", "neither", "never", "no", "non", "any", "none", "nobody", "nothing", "nowhere", "nearly","almost",
      "if", "false", "fallacy", "unsuccessfully", "unlikely", "impossible", "improbable", "uncertain", "unsure", "impossibility", "improbability", "cancellation", "breakup", "lack",
      "long-stalled", "end", "rejection", "failure", "avoid", "bar", "block", "break", "cancel", "cease", "cut", "decline", "deny", "deprive", "destroy", "excuse",
      "fail", "forbid", "forestall", "forget", "halt", "lose", "nullify", "prevent", "refrain", "reject", "rebut", "remain", "refuse", "stop", "suspend", "ward"));
  public final Set<String> neg_relations = Generics.newHashSet(Arrays.asList("nmod:without", "acl:without", "advcl:without",
      "nmod:except", "acl:except", "advcl:except", "nmod:excluding", "acl:excluding", "advcl:excluding", "nmod:if", "acl:if",
      "advcl:if", "nmod:whether", "acl:whether", "advcl:whether",  "nmod:away_from", "acl:away_from", "advcl:away_fom",
      "nmod:instead_of", "acl:instead_of", "advcl:instead_of"));
  public final Set<String> modals = Generics.newHashSet(Arrays.asList("can", "could", "may", "might", "must", "should", "would", "seem",
      "able", "apparently", "necessarily", "presumably", "probably", "possibly", "reportedly", "supposedly",
      "inconceivable", "chance", "impossibility", "improbability", "encouragement", "improbable", "impossible",
      "likely", "necessary", "probable", "possible", "uncertain", "unlikely", "unsure", "likelihood", "probability",
      "possibility", "eventual", "hypothetical" , "presumed", "supposed", "reported", "apparent"));

  public final Set<String> personPronouns = Generics.newHashSet();
  public final Set<String> allPronouns = Generics.newHashSet();

  public final Map<String, String> statesAbbreviation = Generics.newHashMap();
  private final Map<String, Set<String>> demonyms = Generics.newHashMap();
  public final Set<String> demonymSet = Generics.newHashSet();
  private final Set<String> adjectiveNation = Generics.newHashSet();

  public final Set<String> countries = Generics.newHashSet();
  public final Set<String> statesAndProvinces = Generics.newHashSet();

  public final Set<String> neutralWords = Generics.newHashSet();
  public final Set<String> femaleWords = Generics.newHashSet();
  public final Set<String> maleWords = Generics.newHashSet();

  public final Set<String> pluralWords = Generics.newHashSet();
  public final Set<String> singularWords = Generics.newHashSet();

  public final Set<String> inanimateWords = Generics.newHashSet();
  public final Set<String> animateWords = Generics.newHashSet();

  public final Map<List<String>, Gender> genderNumber = Generics.newHashMap();

  public final ArrayList<Counter<Pair<String, String>>> corefDict = new ArrayList<>(4);
  public final Counter<Pair<String, String>> corefDictPMI = new ClassicCounter<>();
  public final Map<String,Counter<String>> NE_signatures = Generics.newHashMap();

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

  /** The format of each line of this file is
   *     fullStateName ( TAB  abbrev )*
   *  The file is cased and checked cased.
   *  The result is: statesAbbreviation is a hash from each abbrev to the fullStateName.
   */
  public void loadStateAbbreviation(String statesFile) {
    BufferedReader reader = null;
    try {
      reader = IOUtils.readerFromString(statesFile);
      for (String line; (line = reader.readLine()) != null; ) {
        String[] tokens = line.split("\t");
        for (String token : tokens) {
          statesAbbreviation.put(token, tokens[0]);
        }
      }
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    } finally {
      IOUtils.closeIgnoringExceptions(reader);
    }
  }

  /** If the input string is an abbreviation of a U.S. state name
   *  or the canonical name, the canonical name is returned.
   *  Otherwise, null is returned.
   *
   *  @param name Is treated as a cased string. ME != me
   */
  public String lookupCanonicalAmericanStateName(String name) {
    return statesAbbreviation.get(name);
  }

  /** The format of the demonyms file is
   *     countryCityOrState ( TAB demonym )*
   *  Lines starting with # are ignored
   *  The file is cased but stored in in-memory data structures uncased.
   *  The results are:
   *  demonyms is a hash from each country (etc.) to a set of demonymic Strings;
   *  adjectiveNation is a set of demonymic Strings;
   *  demonymSet has all country (etc.) names and all demonymic Strings.
   */
  private void loadDemonymLists(String demonymFile) {
    BufferedReader reader = null;
    try {
      reader = IOUtils.readerFromString(demonymFile);
      for (String line; (line = reader.readLine()) != null; ) {
        line = line.toLowerCase(Locale.ENGLISH);
        String[] tokens = line.split("\t");
        if (tokens[0].startsWith("#")) continue;
        Set<String> set = Generics.newHashSet();
        for (String s : tokens) {
          set.add(s);
          demonymSet.add(s);
        }
        demonyms.put(tokens[0], set);
      }
      adjectiveNation.addAll(demonymSet);
      adjectiveNation.removeAll(demonyms.keySet());
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    } finally {
      IOUtils.closeIgnoringExceptions(reader);
    }
  }

  /** Returns a set of demonyms for a country (or city or region).
   *  @param name Some string perhaps a country name like "Australia"
   *  @return A Set of demonym Strings, perhaps { "Australian", "Aussie", "Aussies" }.
   *     If none are known (including if the argument isn't a country/region name,
   *     then the empty set will be returned.
   */
  public Set<String> getDemonyms(String name) {
    Set<String> result = demonyms.get(name);
    if (result == null) {
      result = Collections.emptySet();
    }
    return result;
  }

  /** Returns whether this mention (possibly multi-word) is the
   *  adjectival form of a demonym, like "African" or "Iraqi".
   *  True if it is an adjectival form, even if also a name for a
   *  person of that country (such as "Iraqi").
   */
  public boolean isAdjectivalDemonym(String token) {
    return adjectiveNation.contains(token.toLowerCase(Locale.ENGLISH));
  }

  private static void getWordsFromFile(String filename, Set<String> resultSet, boolean lowercase) throws IOException {
    if(filename==null) {
      return ;
    }
    try (BufferedReader reader = IOUtils.readerFromString(filename)) {
      while (reader.ready()) {
        if (lowercase) resultSet.add(reader.readLine().toLowerCase());
        else resultSet.add(reader.readLine());
      }
    }
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
    try (BufferedReader reader = IOUtils.readerFromString(file)) {
      for (String line; (line = reader.readLine()) != null; ) {
        countries.add(line.split("\t")[1].toLowerCase());
      }
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
  }

  /**
   * Load Bergsma and Lin (2006) gender and number list.
   * <br>
   * The list is converted from raw text and numbers to a serialized
   * map, which saves quite a bit of time loading.
   * See edu.stanford.nlp.dcoref.util.ConvertGenderFile
   */
  private void loadGenderNumber(String file, String neutralWordsFile) {
    try {
      getWordsFromFile(neutralWordsFile, neutralWords, false);
    } catch (IOException e) {
      throw new RuntimeIOException("Couldn't load " + neutralWordsFile);
    }
    try {
      Map<List<String>, Gender> temp = IOUtils.readObjectFromURLOrClasspathOrFileSystem(file);
      genderNumber.putAll(temp);
    } catch (IOException | ClassNotFoundException e) {
      throw new RuntimeIOException("Couldn't load " + file);
    }
  }

  private static void loadCorefDict(String[] file,
      ArrayList<Counter<Pair<String, String>>> dict) {

    for(int i = 0; i < 4; i++){
      dict.add(new ClassicCounter<>());

      BufferedReader reader = null;
      try {
        reader = IOUtils.readerFromString(file[i]);
        // Skip the first line (header)
        reader.readLine();

        while(reader.ready()) {
          String[] split = reader.readLine().split("\t");
          dict.get(i).setCount(new Pair<>(split[0], split[1]), Double.parseDouble(split[2]));
        }

      } catch (IOException e) {
        throw new RuntimeException(e);
      } finally {
        IOUtils.closeIgnoringExceptions(reader);
      }
    }
  }

  private static void loadCorefDictPMI(String file, Counter<Pair<String, String>> dict) {

      BufferedReader reader = null;
      try {
        reader = IOUtils.readerFromString(file);
        // Skip the first line (header)
        reader.readLine();

        while(reader.ready()) {
          String[] split = reader.readLine().split("\t");
          dict.setCount(new Pair<>(split[0], split[1]), Double.parseDouble(split[3]));
        }

      } catch (IOException e) {
        throw new RuntimeException(e);
      } finally {
        IOUtils.closeIgnoringExceptions(reader);
      }
  }

  private static void loadSignatures(String file, Map<String,Counter<String>> sigs) {
    BufferedReader reader = null;
    try {
      reader = IOUtils.readerFromString(file);

      while(reader.ready()) {
        String[] split = reader.readLine().split("\t");
        Counter<String> cntr = new ClassicCounter<>();
        sigs.put(split[0], cntr);
        for (int i = 1; i < split.length; i=i+2) {
          cntr.setCount(split[i], Double.parseDouble(split[i+1]));
        }
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    } finally {
      IOUtils.closeIgnoringExceptions(reader);
    }
  }

  public Dictionaries(Properties props) {
    this(props.getProperty(Constants.DEMONYM_PROP, DefaultPaths.DEFAULT_DCOREF_DEMONYM),
        props.getProperty(Constants.ANIMATE_PROP, DefaultPaths.DEFAULT_DCOREF_ANIMATE),
        props.getProperty(Constants.INANIMATE_PROP, DefaultPaths.DEFAULT_DCOREF_INANIMATE),
        props.getProperty(Constants.MALE_PROP),
        props.getProperty(Constants.NEUTRAL_PROP),
        props.getProperty(Constants.FEMALE_PROP),
        props.getProperty(Constants.PLURAL_PROP),
        props.getProperty(Constants.SINGULAR_PROP),
        props.getProperty(Constants.STATES_PROP, DefaultPaths.DEFAULT_DCOREF_STATES),
        props.getProperty(Constants.GENDER_NUMBER_PROP, DefaultPaths.DEFAULT_DCOREF_GENDER_NUMBER),
        props.getProperty(Constants.COUNTRIES_PROP, DefaultPaths.DEFAULT_DCOREF_COUNTRIES),
        props.getProperty(Constants.STATES_PROVINCES_PROP, DefaultPaths.DEFAULT_DCOREF_STATES_AND_PROVINCES),
        props.getProperty(Constants.SIEVES_PROP, Constants.SIEVEPASSES).contains("CorefDictionaryMatch"),
        PropertiesUtils.getStringArray(props, Constants.DICT_LIST_PROP,
                                       new String[]{DefaultPaths.DEFAULT_DCOREF_DICT1, DefaultPaths.DEFAULT_DCOREF_DICT2,
                                                    DefaultPaths.DEFAULT_DCOREF_DICT3, DefaultPaths.DEFAULT_DCOREF_DICT4}),
        props.getProperty(Constants.DICT_PMI_PROP, DefaultPaths.DEFAULT_DCOREF_DICT1),
        props.getProperty(Constants.SIGNATURES_PROP, DefaultPaths.DEFAULT_DCOREF_NE_SIGNATURES));
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
    if(props.containsKey(Constants.MALE_PROP)) {
      os.append(Constants.MALE_PROP + ":" +
            props.getProperty(Constants.MALE_PROP));
    }
    if(props.containsKey(Constants.NEUTRAL_PROP)) {
      os.append(Constants.NEUTRAL_PROP + ":" +
            props.getProperty(Constants.NEUTRAL_PROP));
    }
    if(props.containsKey(Constants.FEMALE_PROP)) {
      os.append(Constants.FEMALE_PROP + ":" +
            props.getProperty(Constants.FEMALE_PROP));
    }
    if(props.containsKey(Constants.PLURAL_PROP)) {
      os.append(Constants.PLURAL_PROP + ":" +
            props.getProperty(Constants.PLURAL_PROP));
    }
    if(props.containsKey(Constants.SINGULAR_PROP)) {
      os.append(Constants.SINGULAR_PROP + ":" +
            props.getProperty(Constants.SINGULAR_PROP));
    }
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
      boolean loadCorefDict,
      String[] corefDictFiles,
      String corefDictPMIFile,
      String signaturesFile) {
    loadDemonymLists(demonymWords);
    loadStateAbbreviation(statesWords);
    if(Constants.USE_ANIMACY_LIST) loadAnimacyLists(animateWords, inanimateWords);
    loadGenderLists(maleWords, neutralWords, femaleWords);
    loadNumberLists(pluralWords, singularWords);
    loadGenderNumber(genderNumber, neutralWords);
    loadCountriesLists(countries);
    loadStatesLists(states);
    setPronouns();
    if(loadCorefDict){
      loadCorefDict(corefDictFiles, corefDict);
      loadCorefDictPMI(corefDictPMIFile, corefDictPMI);
      loadSignatures(signaturesFile, NE_signatures);
    }
  }

  public Dictionaries() {
    this(new Properties());
  }

}
