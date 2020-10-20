package edu.stanford.nlp.coref.data;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import edu.stanford.nlp.coref.hybrid.HybridCorefProperties;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.neural.VectorMap;
import edu.stanford.nlp.pipeline.DefaultPaths;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.logging.Redwood;


/**
 * Stores various data used for coreference.
 * TODO: get rid of dependence on HybridCorefProperties
 *
 * @author Heeyoung Lee
 */
public class Dictionaries  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(Dictionaries.class);

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

  public Set<String> reportVerb;
  public Set<String> reportNoun;
  public Set<String> nonWords;
  public Set<String> copulas;
  public Set<String> quantifiers;
  public Set<String> parts;
  public Set<String> temporals;

  public Set<String> femalePronouns;
  public Set<String> malePronouns;
  public Set<String> neutralPronouns;
  public Set<String> possessivePronouns;
  public Set<String> otherPronouns;
  public Set<String> thirdPersonPronouns;
  public Set<String> secondPersonPronouns;
  public Set<String> firstPersonPronouns;
  public Set<String> moneyPercentNumberPronouns;
  public Set<String> dateTimePronouns;
  public Set<String> organizationPronouns;
  public Set<String> locationPronouns;
  public Set<String> inanimatePronouns;
  public Set<String> animatePronouns;
  public Set<String> indefinitePronouns;
  public Set<String> relativePronouns;
  public Set<String> interrogativePronouns;
  public Set<String> GPEPronouns;
  public Set<String> pluralPronouns;
  public Set<String> singularPronouns;
  public Set<String> facilityVehicleWeaponPronouns;
  public Set<String> miscPronouns;
  public Set<String> reflexivePronouns;
  public Set<String> transparentNouns;
  public Set<String> stopWords;
  public Set<String> notOrganizationPRP;
  public Set<String> quantifiers2;
  public Set<String> determiners;
  public Set<String> negations;
  public Set<String> neg_relations;
  public Set<String> modals;
  public Set<String> titleWords;
  public Set<String> removeWords;
  public Set<String> removeChars;

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

  private void readWordLists(Locale lang) {
    switch (lang.getLanguage()) {
      default: case "en":
      reportVerb = WordLists.reportVerbEn;
      reportNoun = WordLists.reportNounEn;
      nonWords = WordLists.nonWordsEn;
      copulas = WordLists.copulasEn;
      quantifiers = WordLists.quantifiersEn;
      parts = WordLists.partsEn;
      temporals = WordLists.temporalsEn;
      femalePronouns = WordLists.femalePronounsEn;
      malePronouns = WordLists.malePronounsEn;
      neutralPronouns = WordLists.neutralPronounsEn;
      possessivePronouns = WordLists.possessivePronounsEn;
      otherPronouns = WordLists.otherPronounsEn;
      thirdPersonPronouns = WordLists.thirdPersonPronounsEn;
      secondPersonPronouns = WordLists.secondPersonPronounsEn;
      firstPersonPronouns = WordLists.firstPersonPronounsEn;
      moneyPercentNumberPronouns = WordLists.moneyPercentNumberPronounsEn;
      dateTimePronouns = WordLists.dateTimePronounsEn;
      organizationPronouns = WordLists.organizationPronounsEn;
      locationPronouns = WordLists.locationPronounsEn;
      inanimatePronouns = WordLists.inanimatePronounsEn;
      animatePronouns = WordLists.animatePronounsEn;
      indefinitePronouns = WordLists.indefinitePronounsEn;
      relativePronouns = WordLists.relativePronounsEn;
      GPEPronouns = WordLists.GPEPronounsEn;
      pluralPronouns = WordLists.pluralPronounsEn;
      singularPronouns = WordLists.singularPronounsEn;
      facilityVehicleWeaponPronouns = WordLists.facilityVehicleWeaponPronounsEn;
      miscPronouns = WordLists.miscPronounsEn;
      reflexivePronouns = WordLists.reflexivePronounsEn;
      transparentNouns = WordLists.transparentNounsEn;
      stopWords = WordLists.stopWordsEn;
      notOrganizationPRP = WordLists.notOrganizationPRPEn;
      quantifiers2 = WordLists.quantifiers2En;
      determiners = WordLists.determinersEn;
      negations = WordLists.negationsEn;
      neg_relations = WordLists.neg_relationsEn;
      modals = WordLists.modalsEn;
      break;

      case "zh":
      reportVerb = WordLists.reportVerbZh;
      reportNoun = WordLists.reportNounZh;
      nonWords = WordLists.nonWordsZh;
      copulas = WordLists.copulasZh;
      quantifiers = WordLists.quantifiersZh;
      parts = WordLists.partsZh;
      temporals = WordLists.temporalsZh;
      femalePronouns = WordLists.femalePronounsZh;
      malePronouns = WordLists.malePronounsZh;
      neutralPronouns = WordLists.neutralPronounsZh;
      possessivePronouns = WordLists.possessivePronounsZh;
      otherPronouns = WordLists.otherPronounsZh;
      thirdPersonPronouns = WordLists.thirdPersonPronounsZh;
      secondPersonPronouns = WordLists.secondPersonPronounsZh;
      firstPersonPronouns = WordLists.firstPersonPronounsZh;
      moneyPercentNumberPronouns = WordLists.moneyPercentNumberPronounsZh;
      dateTimePronouns = WordLists.dateTimePronounsZh;
      organizationPronouns = WordLists.organizationPronounsZh;
      locationPronouns = WordLists.locationPronounsZh;
      inanimatePronouns = WordLists.inanimatePronounsZh;
      animatePronouns = WordLists.animatePronounsZh;
      indefinitePronouns = WordLists.indefinitePronounsZh;
      relativePronouns = WordLists.relativePronounsZh;
      interrogativePronouns = WordLists.interrogativePronounsZh;
      GPEPronouns = WordLists.GPEPronounsZh;
      pluralPronouns = WordLists.pluralPronounsZh;
      singularPronouns = WordLists.singularPronounsZh;
      facilityVehicleWeaponPronouns = WordLists.facilityVehicleWeaponPronounsZh;
      miscPronouns = WordLists.miscPronounsZh;
      reflexivePronouns = WordLists.reflexivePronounsZh;
      transparentNouns = WordLists.transparentNounsZh;
      stopWords = WordLists.stopWordsZh;
      notOrganizationPRP = WordLists.notOrganizationPRPZh;
      quantifiers2 = WordLists.quantifiers2Zh;
      determiners = WordLists.determinersZh;
      negations = WordLists.negationsZh;
      neg_relations = WordLists.neg_relationsZh;
      modals = WordLists.modalsZh;
      titleWords = WordLists.titleWordsZh;
      removeWords = WordLists.removeWordsZh;
      removeChars = WordLists.removeCharsZh;
      break;
    }
  }

  public int dimVector;

  public VectorMap vectors = new VectorMap();

  public Map<String, String> strToEntity = Generics.newHashMap();
  public Counter<String> dictScore = new ClassicCounter<>();

  private void setPronouns() {
    personPronouns.addAll(animatePronouns);

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
  private void loadStateAbbreviation(String statesFile) {
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
   *  demonyms is a has from each country (etc.) to a set of demonymic Strings;
   *  adjectiveNation is a set of demonymic Strings;
   *  demonymSet has all country (etc.) names and all demonymic Strings.
   */
  private void loadDemonymLists(String demonymFile) {
    try (BufferedReader reader = IOUtils.readerFromString(demonymFile)) {
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
      while(reader.ready()) {
        if(lowercase) resultSet.add(reader.readLine().toLowerCase());
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
/*
  private void loadGenderNumber(String file, String neutralWordsFile) {
    try {
      getWordsFromFile(neutralWordsFile, neutralWords, false);
      Map<List<String>, Gender> temp = IOUtils.readObjectFromURLOrClasspathOrFileSystem(file);
      genderNumber.putAll(temp);
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    } catch (ClassNotFoundException e) {
      throw new RuntimeIOException(e);
    }
  }
*/

  /**
   * Load Bergsma and Lin (2006) gender and number list.
   *
   */
  private void loadGenderNumber(String file, String neutralWordsFile) {
    try (BufferedReader reader = IOUtils.readerFromString(file)) {
      getWordsFromFile(neutralWordsFile, neutralWords, false);
      String[] split = new String[2];
      String[] countStr = new String[3];
      for (String line; (line = reader.readLine()) != null; ) {
        StringUtils.splitOnChar(split, line, '\t');
        StringUtils.splitOnChar(countStr, split[1], ' ');

        int male = Integer.parseInt(countStr[0]);
        int female = Integer.parseInt(countStr[1]);
        int neutral = Integer.parseInt(countStr[2]);

        Gender gender = Gender.UNKNOWN;
        if (male * 0.5 > female + neutral && male > 2) {
          gender = Gender.MALE;
        } else if (female * 0.5 > male + neutral && female > 2) {
          gender = Gender.FEMALE;
        } else if (neutral * 0.5 > male + female && neutral > 2) {
          gender = Gender.NEUTRAL;
        }

        if (gender == Gender.UNKNOWN) {
          continue;
        }

        String[] words = split[0].split(" ");
        List<String> tokens = Arrays.asList(words);

        genderNumber.put(tokens, gender);
      }
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
  }

  private void loadChineseGenderNumberAnimacy(String file) {
    String[] split = new String[8];
    for (String line : IOUtils.readLines(file)) {
      if (line.startsWith("#WORD")) continue;    // ignore first row
      StringUtils.splitOnChar(split, line, '\t');

      String word = split[0];
      int animate = Integer.parseInt(split[1]);
      int inanimate = Integer.parseInt(split[2]);
      int male = Integer.parseInt(split[3]);
      int female = Integer.parseInt(split[4]);
      int neutral = Integer.parseInt(split[5]);
      int singular = Integer.parseInt(split[6]);
      int plural = Integer.parseInt(split[7]);

      if (male * 0.5 > female + neutral && male > 2) {
        maleWords.add(word);
      } else if (female * 0.5 > male + neutral && female > 2) {
        femaleWords.add(word);
      } else if (neutral * 0.5 > male + female && neutral > 2) {
        neutralWords.add(word);
      }

      if (animate * 0.5 > inanimate && animate > 2) {
        animateWords.add(word);
      } else if (inanimate * 0.5 > animate && inanimate > 2) {
        inanimateWords.add(word);
      }

      if (singular * 0.5 > plural && singular >2) {
        singularWords.add(word);
      } else if (plural * 0.5 > singular && plural > 2) {
        pluralWords.add(word);
      }
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

  public void loadSemantics(Properties props) throws ClassNotFoundException, IOException {
    log.info("LOADING SEMANTICS");

//    wordnet = new WordNet();

    // load word vector
    if(HybridCorefProperties.loadWordEmbedding(props)) {
      log.info("LOAD: WordVectors");
      String wordvectorFile = HybridCorefProperties.getPathSerializedWordVectors(props);
      String word2vecFile = HybridCorefProperties.getPathWord2Vec(props);
      try {
        // Try to read the serialized vectors
        vectors = VectorMap.deserialize(wordvectorFile);
      } catch (IOException e) {
        // If that fails, try to read the vectors from the word2vec file
        if(new File(word2vecFile).exists()) {
          vectors = VectorMap.readWord2Vec(word2vecFile);
          if (wordvectorFile != null && !wordvectorFile.startsWith("edu")) {
            vectors.serialize(wordvectorFile);
          }
        } else {
          // If that fails, give up and crash
          throw new RuntimeIOException(e);
        }
      }
      dimVector = vectors.entrySet().iterator().next().getValue().length;

//    if(Boolean.parseBoolean(props.getProperty("useValDictionary"))) {
//      log.info("LOAD: ValDictionary");
//      for(String line : IOUtils.readLines(valDict)) {
//        String[] split = line.toLowerCase().split("\t");
//        strToEntity.put(split[0], split[2]);
//        dictScore.setCount(split[0], Double.parseDouble(split[1]));
//      }
//    }
    }
  }

  public Dictionaries(Properties props) throws ClassNotFoundException, IOException {
    this(props.getProperty(HybridCorefProperties.LANG_PROP, HybridCorefProperties.LANGUAGE_DEFAULT.toLanguageTag()),
        props.getProperty(HybridCorefProperties.DEMONYM_PROP, DefaultPaths.DEFAULT_DCOREF_DEMONYM),
        props.getProperty(HybridCorefProperties.ANIMATE_PROP, DefaultPaths.DEFAULT_DCOREF_ANIMATE),
        props.getProperty(HybridCorefProperties.INANIMATE_PROP, DefaultPaths.DEFAULT_DCOREF_INANIMATE),
        props.getProperty(HybridCorefProperties.MALE_PROP),
        props.getProperty(HybridCorefProperties.NEUTRAL_PROP),
        props.getProperty(HybridCorefProperties.FEMALE_PROP),
        props.getProperty(HybridCorefProperties.PLURAL_PROP),
        props.getProperty(HybridCorefProperties.SINGULAR_PROP),
        props.getProperty(HybridCorefProperties.STATES_PROP, DefaultPaths.DEFAULT_DCOREF_STATES),
        props.getProperty(HybridCorefProperties.GENDER_NUMBER_PROP, HybridCorefProperties.getGenderNumber(props)),
        props.getProperty(HybridCorefProperties.COUNTRIES_PROP, DefaultPaths.DEFAULT_DCOREF_COUNTRIES),
        props.getProperty(HybridCorefProperties.STATES_PROVINCES_PROP, DefaultPaths.DEFAULT_DCOREF_STATES_AND_PROVINCES),
        HybridCorefProperties.getSieves(props).contains("CorefDictionaryMatch"),
        PropertiesUtils.getStringArray(props, HybridCorefProperties.DICT_LIST_PROP,
            new String[]{DefaultPaths.DEFAULT_DCOREF_DICT1, DefaultPaths.DEFAULT_DCOREF_DICT2,
                DefaultPaths.DEFAULT_DCOREF_DICT3, DefaultPaths.DEFAULT_DCOREF_DICT4}),
        props.getProperty(HybridCorefProperties.DICT_PMI_PROP, DefaultPaths.DEFAULT_DCOREF_DICT1),
        props.getProperty(HybridCorefProperties.SIGNATURES_PROP, DefaultPaths.DEFAULT_DCOREF_NE_SIGNATURES));
    /*if(CorefProperties.useSemantics(props)) {
      loadSemantics(props);
    } else {
      log.info("SEMANTICS NOT LOADED");
    }*/
    if(props.containsKey("coref.zh.dict")) {
      loadChineseGenderNumberAnimacy(props.getProperty("coref.zh.dict"));
    }
  }

  public static String signature(Properties props) {
    StringBuilder os = new StringBuilder();
    os.append(HybridCorefProperties.DEMONYM_PROP + ":" +
            props.getProperty(HybridCorefProperties.DEMONYM_PROP,
                    DefaultPaths.DEFAULT_DCOREF_DEMONYM));
    os.append(HybridCorefProperties.ANIMATE_PROP + ":" +
            props.getProperty(HybridCorefProperties.ANIMATE_PROP,
                    DefaultPaths.DEFAULT_DCOREF_ANIMATE));
    os.append(HybridCorefProperties.INANIMATE_PROP + ":" +
            props.getProperty(HybridCorefProperties.INANIMATE_PROP,
                    DefaultPaths.DEFAULT_DCOREF_INANIMATE));
    if(props.containsKey(HybridCorefProperties.MALE_PROP)) {
      os.append(HybridCorefProperties.MALE_PROP + ":" +
            props.getProperty(HybridCorefProperties.MALE_PROP));
    }
    if(props.containsKey(HybridCorefProperties.NEUTRAL_PROP)) {
      os.append(HybridCorefProperties.NEUTRAL_PROP + ":" +
            props.getProperty(HybridCorefProperties.NEUTRAL_PROP));
    }
    if(props.containsKey(HybridCorefProperties.FEMALE_PROP)) {
      os.append(HybridCorefProperties.FEMALE_PROP + ":" +
            props.getProperty(HybridCorefProperties.FEMALE_PROP));
    }
    if(props.containsKey(HybridCorefProperties.PLURAL_PROP)) {
      os.append(HybridCorefProperties.PLURAL_PROP + ":" +
            props.getProperty(HybridCorefProperties.PLURAL_PROP));
    }
    if(props.containsKey(HybridCorefProperties.SINGULAR_PROP)) {
      os.append(HybridCorefProperties.SINGULAR_PROP + ":" +
            props.getProperty(HybridCorefProperties.SINGULAR_PROP));
    }
    os.append(HybridCorefProperties.STATES_PROP + ":" +
            props.getProperty(HybridCorefProperties.STATES_PROP,
                    DefaultPaths.DEFAULT_DCOREF_STATES));
    os.append(HybridCorefProperties.GENDER_NUMBER_PROP + ":" +
            props.getProperty(HybridCorefProperties.GENDER_NUMBER_PROP,
                    DefaultPaths.DEFAULT_DCOREF_GENDER_NUMBER));
    os.append(HybridCorefProperties.COUNTRIES_PROP + ":" +
            props.getProperty(HybridCorefProperties.COUNTRIES_PROP,
                    DefaultPaths.DEFAULT_DCOREF_COUNTRIES));
    os.append(HybridCorefProperties.STATES_PROVINCES_PROP + ":" +
            props.getProperty(HybridCorefProperties.STATES_PROVINCES_PROP,
                    DefaultPaths.DEFAULT_DCOREF_STATES_AND_PROVINCES));
    return os.toString();
  }

  public Dictionaries(
      String language,
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
    Locale lang = Locale.forLanguageTag(language);
    readWordLists(lang);
    loadDemonymLists(demonymWords);
    loadStateAbbreviation(statesWords);
    loadAnimacyLists(animateWords, inanimateWords);
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

  public Dictionaries() throws ClassNotFoundException, IOException {
    this(new Properties());
  }

}
