package edu.stanford.nlp.ie;

import edu.stanford.nlp.ie.machinereading.structure.Span;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.simple.Sentence;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.ConfusionMatrix;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.StringUtils;

import java.io.*;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static edu.stanford.nlp.ie.KBPRelationExtractor.NERTag.*;
import static edu.stanford.nlp.util.logging.Redwood.Util.endTrack;
import static edu.stanford.nlp.util.logging.Redwood.Util.forceTrack;
import static edu.stanford.nlp.util.logging.Redwood.log;

/**
 * An interface for a KBP-style relation extractor
 *
 * @author Gabor Angeli
 */
public interface KBPRelationExtractor {

  /**
   * Classify the given sentence into the relation it expresses, with the associated
   * confidence.
   */
  Pair<String,Double> classify(KBPInput input);


  /**
   * The special tag for no relation.
   */
  String NO_RELATION = "no_relation";

  /**
   * A list of valid KBP NER tags.
   */
  enum NERTag {
    // ENUM_NAME        NAME           SHORT_NAME  IS_REGEXNER_TYPE
    CAUSE_OF_DEATH("CAUSE_OF_DEATH", "COD", true), // note: these names must be upper case
    CITY("CITY", "CIT", true), //       furthermore, DO NOT change the short names, or else serialization may break
    COUNTRY("COUNTRY", "CRY", true),
    CRIMINAL_CHARGE("CRIMINAL_CHARGE", "CC", true),
    DATE("DATE", "DT", false),
    IDEOLOGY("IDEOLOGY", "IDY", true),
    LOCATION("LOCATION", "LOC", false),
    MISC("MISC", "MSC", false),
    MODIFIER("MODIFIER", "MOD", false),
    NATIONALITY("NATIONALITY", "NAT", true),
    NUMBER("NUMBER", "NUM", false),
    ORGANIZATION("ORGANIZATION", "ORG", false),
    PERSON("PERSON", "PER", false),
    RELIGION("RELIGION", "REL", true),
    STATE_OR_PROVINCE("STATE_OR_PROVINCE", "ST", true),
    TITLE("TITLE", "TIT", true),
    URL("URL", "URL", true),
    DURATION("DURATION", "DUR", false),
    GPE("GPE", "GPE", false), // note(chaganty): This NER tag is solely used in the cold-start system for entities.
//  SCHOOL            ("SCHOOL",            "SCH", true),
    ;

    /**
     * The full name of this NER tag, as would come out of our NER or RegexNER system
     */
    public final String name;
    /**
     * A short name for this NER tag, intended for compact serialization
     */
    public final String shortName;
    /**
     * If true, this NER tag is not in the standard NER set, but is annotated via RegexNER
     */
    public final boolean isRegexNERType;

    NERTag(String name, String shortName, boolean isRegexNERType) {
      this.name = name;
      this.shortName = shortName;
      this.isRegexNERType = isRegexNERType;
    }

    /** Find the slot for a given name */
    public static Optional<NERTag> fromString(String name) {
      // Early termination
      if (StringUtils.isNullOrEmpty(name)) { return Optional.empty(); }
      // Cycle known NER tags
      name = name.toUpperCase();
      for (NERTag slot : NERTag.values()) {
        if (slot.name.equals(name)) return Optional.of(slot);
      }
      for (NERTag slot : NERTag.values()) {
        if (slot.shortName.equals(name)) return Optional.of(slot);
      }
      // Some quick fixes
      return Optional.empty();
    }
  }

  /**
   * Known relation types (last updated for the 2013 shared task).
   *
   * Note that changing the constants here can have far-reaching consequences in loading serialized
   * models, and various bits of code that have been hard-coded to these relation types (e.g., the various
   * consistency filters).
   *
   * <p>
   * <i>Note:</i> Neither per:spouse, org:founded_by, or X:organizations_founded are SINGLE relations
   *       in the spec - these are made single here because our system otherwise over-predicts them.
   * </p>
   *
   * @author Gabor Angeli
   */
  enum RelationType {
    PER_ALTERNATE_NAMES("per:alternate_names", true, 10, PERSON, Cardinality.LIST, new NERTag[]{PERSON, MISC}, new String[]{"NNP"}, 0.0353027270308107100),
    PER_CHILDREN("per:children", true, 5, PERSON, Cardinality.LIST, new NERTag[]{PERSON}, new String[]{"NNP"}, 0.0058428110284504410),
    PER_CITIES_OF_RESIDENCE("per:cities_of_residence", true, 5, PERSON, Cardinality.LIST, new NERTag[]{CITY,}, new String[]{"NNP"}, 0.0136105679675116560),
    PER_CITY_OF_BIRTH("per:city_of_birth", true, 3, PERSON, Cardinality.SINGLE, new NERTag[]{CITY,}, new String[]{"NNP"}, 0.0358146961159769100),
    PER_CITY_OF_DEATH("per:city_of_death", true, 3, PERSON, Cardinality.SINGLE, new NERTag[]{CITY,}, new String[]{"NNP"}, 0.0102003332137774650),
    PER_COUNTRIES_OF_RESIDENCE("per:countries_of_residence", true, 5, PERSON, Cardinality.LIST, new NERTag[]{COUNTRY,}, new String[]{"NNP"}, 0.0107788293552082020),
    PER_COUNTRY_OF_BIRTH("per:country_of_birth", true, 3, PERSON, Cardinality.SINGLE, new NERTag[]{COUNTRY,}, new String[]{"NNP"}, 0.0223444134627622040),
    PER_COUNTRY_OF_DEATH("per:country_of_death", true, 3, PERSON, Cardinality.SINGLE, new NERTag[]{COUNTRY,}, new String[]{"NNP"}, 0.0060626395621941200),
    PER_EMPLOYEE_OF("per:employee_of", true, 10, PERSON, Cardinality.LIST, new NERTag[]{ORGANIZATION, COUNTRY, STATE_OR_PROVINCE, CITY}, new String[]{"NNP"}, 2.0335281901169719200),
    PER_LOC_OF_BIRTH("per:LOCATION_of_birth", true, 3, PERSON, Cardinality.LIST, new NERTag[]{CITY, STATE_OR_PROVINCE, COUNTRY}, new String[]{"NNP"}, 0.0165825918941120660),
    PER_LOC_OF_DEATH("per:LOCATION_of_death", true, 3, PERSON, Cardinality.LIST, new NERTag[]{CITY, STATE_OR_PROVINCE, COUNTRY}, new String[]{"NNP"}, 0.0165825918941120660),
    PER_LOC_OF_RESIDENCE("per:LOCATION_of_residence", true, 3, PERSON, Cardinality.LIST, new NERTag[]{STATE_OR_PROVINCE,}, new String[]{"NNP"}, 0.0165825918941120660),
    PER_MEMBER_OF("per:member_of", true, 10, PERSON, Cardinality.LIST, new NERTag[]{ORGANIZATION}, new String[]{"NNP"}, 0.0521716745149309900),
    PER_ORIGIN("per:origin", true, 10, PERSON, Cardinality.LIST, new NERTag[]{NATIONALITY, COUNTRY}, new String[]{"NNP"}, 0.0069795559463618380),
    PER_OTHER_FAMILY("per:other_family", true, 5, PERSON, Cardinality.LIST, new NERTag[]{PERSON}, new String[]{"NNP"}, 2.7478566717959990E-5),
    PER_PARENTS("per:parents", true, 5, PERSON, Cardinality.LIST, new NERTag[]{PERSON}, new String[]{"NNP"}, 0.0032222235077692030),
    PER_SCHOOLS_ATTENDED("per:schools_attended", true, 5, PERSON, Cardinality.LIST, new NERTag[]{ORGANIZATION}, new String[]{"NNP"}, 0.0054696810172276150),
    PER_SIBLINGS("per:siblings", true, 5, PERSON, Cardinality.LIST, new NERTag[]{PERSON}, new String[]{"NNP"}, 1.000000000000000e-99),
    PER_SPOUSE("per:spouse", true, 3, PERSON, Cardinality.LIST, new NERTag[]{PERSON}, new String[]{"NNP"}, 0.0164075968113292680),
    PER_STATE_OR_PROVINCES_OF_BIRTH("per:stateorprovince_of_birth", true, 3, PERSON, Cardinality.SINGLE, new NERTag[]{STATE_OR_PROVINCE,}, new String[]{"NNP"}, 0.0165825918941120660),
    PER_STATE_OR_PROVINCES_OF_DEATH("per:stateorprovince_of_death", true, 3, PERSON, Cardinality.SINGLE, new NERTag[]{STATE_OR_PROVINCE,}, new String[]{"NNP"}, 0.0050083303444366030),
    PER_STATE_OR_PROVINCES_OF_RESIDENCE("per:stateorprovinces_of_residence", true, 5, PERSON, Cardinality.LIST, new NERTag[]{STATE_OR_PROVINCE,}, new String[]{"NNP"}, 0.0066787379528178550),
    PER_AGE("per:age", true, 3, PERSON, Cardinality.SINGLE, new NERTag[]{NUMBER, DURATION}, new String[]{"CD", "NN"}, 0.0483159977322951300),
    PER_DATE_OF_BIRTH("per:date_of_birth", true, 3, PERSON, Cardinality.SINGLE, new NERTag[]{DATE}, new String[]{"CD", "NN"}, 0.0743584477791533200),
    PER_DATE_OF_DEATH("per:date_of_death", true, 3, PERSON, Cardinality.SINGLE, new NERTag[]{DATE}, new String[]{"CD", "NN"}, 0.0189819046406960460),
    PER_CAUSE_OF_DEATH("per:cause_of_death", true, 3, PERSON, Cardinality.SINGLE, new NERTag[]{CAUSE_OF_DEATH}, new String[]{"NN"}, 1.0123682475037891E-5),
    PER_CHARGES("per:charges", true, 5, PERSON, Cardinality.LIST, new NERTag[]{CRIMINAL_CHARGE}, new String[]{"NN"}, 3.8614617440501670E-4),
    PER_RELIGION("per:religion", true, 3, PERSON, Cardinality.SINGLE, new NERTag[]{RELIGION}, new String[]{"NN"}, 7.6650738739572610E-4),
    PER_TITLE("per:title", true, 15, PERSON, Cardinality.LIST, new NERTag[]{TITLE, MODIFIER}, new String[]{"NN"}, 0.0334283995325751200),
    ORG_ALTERNATE_NAMES("org:alternate_names", true, 10, ORGANIZATION, Cardinality.LIST, new NERTag[]{ORGANIZATION, MISC}, new String[]{"NNP"}, 0.0552058867767352000),
    ORG_CITY_OF_HEADQUARTERS("org:city_of_headquarters", true, 3, ORGANIZATION, Cardinality.SINGLE, new NERTag[]{CITY, LOCATION}, new String[]{"NNP"}, 0.0555949254318473740),
    ORG_COUNTRY_OF_HEADQUARTERS("org:country_of_headquarters", true, 3, ORGANIZATION, Cardinality.SINGLE, new NERTag[]{COUNTRY, NATIONALITY}, new String[]{"NNP"}, 0.0580217167451493100),
    ORG_FOUNDED_BY("org:founded_by", true, 3, ORGANIZATION, Cardinality.LIST, new NERTag[]{PERSON, ORGANIZATION}, new String[]{"NNP"}, 0.0050806423621154450),
    ORG_LOC_OF_HEADQUARTERS("org:LOCATION_of_headquarters", true, 10, ORGANIZATION, Cardinality.LIST, new NERTag[]{CITY, STATE_OR_PROVINCE, COUNTRY,}, new String[]{"NNP"}, 0.0555949254318473740),
    ORG_MEMBER_OF("org:member_of", true, 20, ORGANIZATION, Cardinality.LIST, new NERTag[]{ORGANIZATION, STATE_OR_PROVINCE, COUNTRY,}, new String[]{"NNP"}, 0.0396298781687126140),
    ORG_MEMBERS("org:members", true, 20, ORGANIZATION, Cardinality.LIST, new NERTag[]{ORGANIZATION, COUNTRY}, new String[]{"NNP"}, 0.0012220730987724312),
    ORG_PARENTS("org:parents", true, 10, ORGANIZATION, Cardinality.LIST, new NERTag[]{ORGANIZATION,}, new String[]{"NNP"}, 0.0550048593675880200),
    ORG_POLITICAL_RELIGIOUS_AFFILIATION("org:political/religious_affiliation", true, 5, ORGANIZATION, Cardinality.LIST, new NERTag[]{IDEOLOGY, RELIGION}, new String[]{"NN", "JJ"}, 0.0059266929689578970),
    ORG_SHAREHOLDERS("org:shareholders", true, 10, ORGANIZATION, Cardinality.LIST, new NERTag[]{PERSON, ORGANIZATION}, new String[]{"NNP"}, 1.1569922828614734E-5),
    ORG_STATE_OR_PROVINCES_OF_HEADQUARTERS("org:stateorprovince_of_headquarters", true, 3, ORGANIZATION, Cardinality.SINGLE, new NERTag[]{STATE_OR_PROVINCE}, new String[]{"NNP"}, 0.0312619314829170100),
    ORG_SUBSIDIARIES("org:subsidiaries", true, 20, ORGANIZATION, Cardinality.LIST, new NERTag[]{ORGANIZATION}, new String[]{"NNP"}, 0.0162412791706679320),
    ORG_TOP_MEMBERS_SLASH_EMPLOYEES("org:top_members/employees", true, 10, ORGANIZATION, Cardinality.LIST, new NERTag[]{PERSON}, new String[]{"NNP"}, 0.0907168724184609800),
    ORG_DISSOLVED("org:dissolved", true, 3, ORGANIZATION, Cardinality.SINGLE, new NERTag[]{DATE}, new String[]{"CD", "NN"}, 0.0023877428237553656),
    ORG_FOUNDED("org:founded", true, 3, ORGANIZATION, Cardinality.SINGLE, new NERTag[]{DATE}, new String[]{"CD", "NN"}, 0.0796314401082944800),
    ORG_NUMBER_OF_EMPLOYEES_SLASH_MEMBERS("org:number_of_employees/members", true, 3, ORGANIZATION, Cardinality.SINGLE, new NERTag[]{NUMBER}, new String[]{"CD", "NN"}, 0.0366274831946870950),
    ORG_WEBSITE("org:website", true, 3, ORGANIZATION, Cardinality.SINGLE, new NERTag[]{URL}, new String[]{"NNP", "NN"}, 0.0051544006201478640),
    // Inverse types
    ORG_EMPLOYEES("org:employees_or_members", false, 68, ORGANIZATION, Cardinality.LIST, new NERTag[]{PERSON}, new String[]{"NNP", "NN"}, 0.0051544006201478640),
    GPE_EMPLOYEES("gpe:employees_or_members", false, 10, GPE, Cardinality.LIST, new NERTag[]{PERSON}, new String[]{"NNP", "NN"}, 0.0051544006201478640),
    ORG_STUDENTS("org:students", false, 50, ORGANIZATION, Cardinality.LIST, new NERTag[]{PERSON}, new String[]{"NNP", "NN"}, 0.0051544006201478640),
    GPE_BIRTHS_IN_CITY("gpe:births_in_city", false, 50, GPE, Cardinality.LIST, new NERTag[]{PERSON}, new String[]{"NNP", "NN"}, 0.0051544006201478640),
    GPE_BIRTHS_IN_STATE_OR_PROVINCE("gpe:births_in_stateorprovince", false, 50, GPE, Cardinality.LIST, new NERTag[]{PERSON}, new String[]{"NNP", "NN"}, 0.0051544006201478640),
    GPE_BIRTHS_IN_COUNTRY("gpe:births_in_country", false, 50, GPE, Cardinality.LIST, new NERTag[]{PERSON}, new String[]{"NNP", "NN"}, 0.0051544006201478640),
    GPE_RESIDENTS_IN_CITY("gpe:residents_of_city", false, 50, GPE, Cardinality.LIST, new NERTag[]{PERSON}, new String[]{"NNP", "NN"}, 0.0051544006201478640),
    GPE_RESIDENTS_IN_STATE_OR_PROVINCE("gpe:residents_of_stateorprovince", false, 50, GPE, Cardinality.LIST, new NERTag[]{PERSON}, new String[]{"NNP", "NN"}, 0.0051544006201478640),
    GPE_RESIDENTS_IN_COUNTRY("gpe:residents_of_country", false, 50, GPE, Cardinality.LIST, new NERTag[]{PERSON}, new String[]{"NNP", "NN"}, 0.0051544006201478640),
    GPE_DEATHS_IN_CITY("gpe:deaths_in_city", false, 50, GPE, Cardinality.LIST, new NERTag[]{PERSON}, new String[]{"NNP", "NN"}, 0.0051544006201478640),
    GPE_DEATHS_IN_STATE_OR_PROVINCE("gpe:deaths_in_stateorprovince", false, 50, GPE, Cardinality.LIST, new NERTag[]{PERSON}, new String[]{"NNP", "NN"}, 0.0051544006201478640),
    GPE_DEATHS_IN_COUNTRY("gpe:deaths_in_country", false, 50, GPE, Cardinality.LIST, new NERTag[]{PERSON}, new String[]{"NNP", "NN"}, 0.0051544006201478640),
    PER_HOLDS_SHARES_IN("per:holds_shares_in", false, 10, PERSON, Cardinality.LIST, new NERTag[]{ORGANIZATION}, new String[]{"NNP", "NN"}, 0.0051544006201478640),
    GPE_HOLDS_SHARES_IN("gpe:holds_shares_in", false, 10, GPE, Cardinality.LIST, new NERTag[]{ORGANIZATION}, new String[]{"NNP", "NN"}, 0.0051544006201478640),
    ORG_HOLDS_SHARES_IN("org:holds_shares_in", false, 10, ORGANIZATION, Cardinality.LIST, new NERTag[]{ORGANIZATION}, new String[]{"NNP", "NN"}, 0.0051544006201478640),
    PER_ORGANIZATIONS_FOUNDED("per:organizations_founded", false, 3, PERSON, Cardinality.LIST, new NERTag[]{ORGANIZATION}, new String[]{"NNP", "NN"}, 0.0051544006201478640),
    GPE_ORGANIZATIONS_FOUNDED("gpe:organizations_founded", false, 3, GPE, Cardinality.LIST, new NERTag[]{ORGANIZATION}, new String[]{"NNP", "NN"}, 0.0051544006201478640),
    ORG_ORGANIZATIONS_FOUNDED("org:organizations_founded", false, 3, ORGANIZATION, Cardinality.LIST, new NERTag[]{ORGANIZATION}, new String[]{"NNP", "NN"}, 0.0051544006201478640),
    PER_TOP_EMPLOYEE_OF("per:top_member_employee_of", false, 5, PERSON, Cardinality.LIST, new NERTag[]{ORGANIZATION}, new String[]{"NNP", "NN"}, 0.0051544006201478640),
    GPE_MEMBER_OF("gpe:member_of", false, 10, GPE, Cardinality.LIST, new NERTag[]{ORGANIZATION}, new String[]{"NNP"}, 0.0396298781687126140),
    GPE_SUBSIDIARIES("gpe:subsidiaries", false, 10, GPE, Cardinality.LIST, new NERTag[]{ORGANIZATION}, new String[]{"NNP"}, 0.0396298781687126140),
    GPE_HEADQUARTERS_IN_CITY("gpe:headquarters_in_city", false, 50, GPE, Cardinality.LIST, new NERTag[]{ORGANIZATION}, new String[]{"NNP", "NN"}, 0.0051544006201478640),
    GPE_HEADQUARTERS_IN_STATE_OR_PROVINCE("gpe:headquarters_in_stateorprovince", false, 50, GPE, Cardinality.LIST, new NERTag[]{ORGANIZATION}, new String[]{"NNP", "NN"}, 0.0051544006201478640),
    GPE_HEADQUARTERS_IN_COUNTRY("gpe:headquarters_in_country", false, 50, GPE, Cardinality.LIST, new NERTag[]{ORGANIZATION}, new String[]{"NNP", "NN"}, 0.0051544006201478640),
    ;

    public enum Cardinality {
      SINGLE,
      LIST
    }

    /**
     * A canonical name for this relation type. This is the official 2010 relation name,
     * that has since changed.
     */
    public final String canonicalName;
    /**
     * If true, realtation was one of the original (non-inverse) KBP relation.
     */
    public final boolean isOriginalRelation;
    /**
     * A guess of the maximum number of results to query for this relation.
     * Only really relevant for cold start.
     */
    public final int queryLimit;
    /**
     * The entity type (left arg type) associated with this relation. That is, either a PERSON or an ORGANIZATION "slot".
     */
    public final NERTag entityType;
    /**
     * The cardinality of this entity. That is, can multiple right arguments participate in this relation (born_in vs. lived_in)
     */
    public final Cardinality cardinality;
    /**
     * Valid named entity labels for the right argument to this relation
     */
    public final Set<NERTag> validNamedEntityLabels;
    /**
     * Valid POS [prefixes] for the right argument to this relation (e.g., can only take nouns, or can only take numbers, etc.)
     */
    public final Set<String> validPOSPrefixes;
    /**
     * The prior for how often this relation occurs in the training data.
     * Note that this prior is not necessarily accurate for the test data.
     */
    public final double priorProbability;


    RelationType(String canonicalName, boolean isOriginalRelation, int queryLimit, NERTag type, Cardinality cardinality, NERTag[] validNamedEntityLabels, String[] validPOSPrefixes,
                 double priorProbability) {
      this.canonicalName          = canonicalName;
      this.isOriginalRelation     = isOriginalRelation;
      this.queryLimit             = queryLimit;
      this.entityType             = type;
      this.cardinality            = cardinality;
      this.validNamedEntityLabels = new HashSet<>(Arrays.asList(validNamedEntityLabels));
      this.validPOSPrefixes       = new HashSet<>(Arrays.asList(validPOSPrefixes));
      this.priorProbability       = priorProbability;
    }

    /** A small cache of names to relation types; we call fromString() a lot in the code, usually expecting it to be very fast */
    private static final Map<String, RelationType> cachedFromString = new HashMap<>();

    /** Find the slot for a given name */
    public static Optional<RelationType> fromString(String name) {
      if (name == null) { return Optional.empty(); }
      String originalName = name;
      if (cachedFromString.get(name) != null) { return Optional.of(cachedFromString.get(name)); }
      if (cachedFromString.containsKey(name)) { return Optional.empty(); }
      // Try naive
      for (RelationType slot : RelationType.values()) {
        if (slot.canonicalName.equals(name) || slot.name().equals(name)) {
          cachedFromString.put(originalName, slot);
          return Optional.of(slot);
        }
      }
      // Replace slashes
      name = name.toLowerCase().replaceAll("[Ss][Ll][Aa][Ss][Hh]", "/");
      for (RelationType slot : RelationType.values()) {
        if (slot.canonicalName.equalsIgnoreCase(name)) {
          cachedFromString.put(originalName, slot);
          return Optional.of(slot);
        }
      }
      cachedFromString.put(originalName, null);
      return Optional.empty();
    }


    /**
     * Returns whether two entity types could plausibly have a relation hold between them.
     * That is, is there a known relation type that would hold between these two entity types.
     * @param entityType The NER tag of the entity.
     * @param slotValueType The NER tag of the slot value.
     * @return True if there is a plausible relation which could occur between these two types.
     */
    public static boolean plausiblyHasRelation(NERTag entityType, NERTag slotValueType) {
      for (RelationType rel : RelationType.values()) {
        if (rel.entityType == entityType && rel.validNamedEntityLabels.contains(slotValueType)) {
          return true;
        }
      }
      return false;
    }
  }

  @SuppressWarnings("unused")
  class KBPInput {

    public final Span subjectSpan;
    public final Span objectSpan;
    public final NERTag subjectType;
    public final NERTag objectType;
    public final Sentence sentence;

    public KBPInput(Span subjectSpan, Span objectSpan,
                    NERTag subjectType, NERTag objectType,
                    Sentence sentence) {
      this.subjectSpan = subjectSpan;
      this.objectSpan = objectSpan;
      this.subjectType = subjectType;
      this.objectType = objectType;
      this.sentence = sentence;
    }

    public Sentence getSentence() {
      return sentence;
    }

    public Span getSubjectSpan() {
      return subjectSpan;
    }

    public String getSubjectText() {
      return StringUtils.join(sentence.originalTexts().subList(subjectSpan.start(), subjectSpan.end()).stream(), " ");
    }

    public Span getObjectSpan() {
      return objectSpan;
    }

    public String getObjectText() {
      return StringUtils.join(sentence.originalTexts().subList(objectSpan.start(), objectSpan.end()).stream(), " ");
    }

    @Override
    public String toString() {
      return "KBPInput{" +
          ", subjectSpan=" + subjectSpan +
          ", objectSpan=" + objectSpan +
          ", sentence=" + sentence +
          '}';
    }
  }

  /**
   * Read a dataset from a CoNLL formatted input file
   * @param conllInputFile The input file, formatted as a TSV
   * @return A list of examples.
   */
  @SuppressWarnings("StatementWithEmptyBody")
  static List<Pair<KBPInput, String>> readDataset(File conllInputFile) throws IOException {
    BufferedReader reader = IOUtils.readerFromFile(conllInputFile);
    List<Pair<KBPInput, String>> examples = new ArrayList<>();

    int i = 0;
    String relation = null;
    List<String> tokens = new ArrayList<>();
    Span subject = new Span(Integer.MAX_VALUE, Integer.MIN_VALUE);
    NERTag subjectNER = null;
    Span object = new Span(Integer.MAX_VALUE, Integer.MIN_VALUE);
    NERTag objectNER = null;

    String line = reader.readLine();
    if (!line.startsWith("#")) {
      throw new IllegalArgumentException("First line of input file should be header definition");
    }
    while ( (line = reader.readLine()) != null ) {
      String[] fields = line.split("\t");
      if (relation == null) {
        // Case: read the relation
        assert fields.length == 1;
        relation = fields[0];
      } else if (fields.length == 9) {
        // Case: read a token
        tokens.add(fields[0]);
        if ("SUBJECT".equals(fields[1])) {
          subject = new Span(Math.min(subject.start(), i), Math.max(subject.end(), i + 1));
          subjectNER = valueOf(fields[2].toUpperCase(Locale.ROOT));
        } else if ("OBJECT".equals(fields[3])) {
          object = new Span(Math.min(object.start(), i), Math.max(object.end(), i + 1));
          objectNER = valueOf(fields[4].toUpperCase(Locale.ROOT));
        } else if ("-".equals(fields[1]) && "-".equals(fields[3])) {
          // do nothing
        } else {
          throw new IllegalStateException("Could not parse CoNLL file");
        }
        i += 1;
      } else if (StringUtils.isNullOrEmpty(line.trim())) {
        // Case: commit a sentence
        examples.add(Pair.makePair(new KBPInput(
            subject,
            object,
            subjectNER,
            objectNER,
            new Sentence(tokens)
        ), relation));

        // (clear the variables)
        i = 0;
        relation = null;
        tokens = new ArrayList<>();
        subject = new Span(Integer.MAX_VALUE, Integer.MIN_VALUE);
        object = new Span(Integer.MAX_VALUE, Integer.MIN_VALUE);
      } else {
        throw new IllegalStateException("Could not parse CoNLL file");
      }
    }

    return examples;
  }

  /** A class to compute the accuracy of a relation extractor. */
  @SuppressWarnings("unused")
  class Accuracy {

    private static class PerRelationStat implements  Comparable<PerRelationStat> {
      public final String name;
      public final double precision;
      public final double recall;
      public final int predictedCount;
      public final int goldCount;
      public PerRelationStat(String name, double precision, double recall, int predictedCount, int goldCount) {
        this.name = name;
        this.precision = precision;
        this.recall = recall;
        this.predictedCount = predictedCount;
        this.goldCount = goldCount;
      }
      public double f1() {
        if (precision == 0.0 && recall == 0.0) {
          return 0.0;
        } else {
          return 2.0 * precision * recall / (precision + recall);
        }
      }
      @SuppressWarnings("NullableProblems")
      @Override
      public int compareTo(PerRelationStat o) {
        if (this.precision < o.precision) {
          return -1;
        } else if (this.precision > o.precision) {
          return 1;
        } else {
          return 0;
        }
      }
      @Override
      public String toString() {
        DecimalFormat df = new DecimalFormat("0.00%");
        return "[" + name + "]  pred/gold: " + predictedCount + "/" + goldCount + "  P: " + df.format(precision) + "  R: " + df.format(recall) + "  F1: " + df.format(f1());
      }
    }

    private Counter<String> correctCount   = new ClassicCounter<>();
    private Counter<String> predictedCount = new ClassicCounter<>();
    private Counter<String> goldCount      = new ClassicCounter<>();
    private Counter<String> totalCount     = new ClassicCounter<>();
    public final ConfusionMatrix<String> confusion = new ConfusionMatrix<>();


    public void predict(Set<String> predictedRelationsRaw, Set<String> goldRelationsRaw) {
      Set<String> predictedRelations = new HashSet<>(predictedRelationsRaw);
      predictedRelations.remove(NO_RELATION);
      Set<String> goldRelations = new HashSet<>(goldRelationsRaw);
      goldRelations.remove(NO_RELATION);
      // Register the prediction
      for (String pred : predictedRelations) {
        if (goldRelations.contains(pred)) {
          correctCount.incrementCount(pred);
        }
        predictedCount.incrementCount(pred);
      }
      goldRelations.forEach(goldCount::incrementCount);
      HashSet<String> allRelations = new HashSet<String>(){{ addAll(predictedRelations); addAll(goldRelations); }};
      allRelations.forEach(totalCount::incrementCount);

      // Register the confusion matrix
      if (predictedRelations.size() == 1 && goldRelations.size() == 1) {
        confusion.add(predictedRelations.iterator().next(), goldRelations.iterator().next());
      }
      if (predictedRelations.size() == 1 && goldRelations.isEmpty()) {
        confusion.add(predictedRelations.iterator().next(), "NR");
      }
      if (predictedRelations.isEmpty() && goldRelations.size() == 1) {
        confusion.add("NR", goldRelations.iterator().next());
      }
    }

    public double precision(String relation) {
      if (predictedCount.getCount(relation) == 0) {
        return 1.0;
      }
      return correctCount.getCount(relation) / predictedCount.getCount(relation);
    }

    public double precisionMicro() {
      if (predictedCount.totalCount() == 0) {
        return 1.0;
      }
      return correctCount.totalCount() / predictedCount.totalCount();
    }

    public double precisionMacro() {
      double sumPrecision = 0.0;
      for (String rel : totalCount.keySet()) {
        sumPrecision += precision(rel);
      }
      return sumPrecision / ((double) totalCount.size());
    }


    public double recall(String relation) {
      if (goldCount.getCount(relation) == 0) {
        return 0.0;
      }
      return correctCount.getCount(relation) / goldCount.getCount(relation);
    }

    public double recallMicro() {
      if (goldCount.totalCount() == 0) {
        return 0.0;
      }
      return correctCount.totalCount() / goldCount.totalCount();
    }

    public double recallMacro() {
      double sumRecall = 0.0;
      for (String rel : totalCount.keySet()) {
        sumRecall += recall(rel);
      }
      return sumRecall / ((double) totalCount.size());
    }

    public double f1(String relation) {
      return 2.0 * precision(relation) * recall(relation) / (precision(relation) + recall(relation));
    }

    public double f1Micro() {
      return 2.0 * precisionMicro() * recallMicro() / (precisionMicro() + recallMicro());
    }

    public double f1Macro() {
      return 2.0 * precisionMacro() * recallMacro() / (precisionMacro() + recallMacro());
    }

    public void dumpPerRelationStats(PrintStream out) {
      List<PerRelationStat> stats = goldCount.keySet().stream().map(relation -> new PerRelationStat(relation, precision(relation), recall(relation), (int) predictedCount.getCount(relation), (int) goldCount.getCount(relation))).collect(Collectors.toList());
      Collections.sort(stats);
      out.println("Per-relation Accuracy");
      for (PerRelationStat stat : stats) {
        out.println(stat);
      }
    }

    public void dumpPerRelationStats() {
      dumpPerRelationStats(System.out);

    }

    public void toString(PrintStream out) {
      out.println();
      out.println("PRECISION (micro average): " + new DecimalFormat("0.000%").format(precisionMicro()));
      out.println("RECALL    (micro average): " + new DecimalFormat("0.000%").format(recallMicro()));
      out.println("F1        (micro average): " + new DecimalFormat("0.000%").format(f1Micro()));
      out.println();
      out.println("PRECISION (macro average): " + new DecimalFormat("0.000%").format(precisionMacro()));
      out.println("RECALL    (macro average): " + new DecimalFormat("0.000%").format(recallMacro()));
      out.println("F1        (macro average): " + new DecimalFormat("0.000%").format(f1Macro()));
      out.println();
    }

    public String toString() {
      ByteArrayOutputStream bs = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(bs);
      toString(out);
      return bs.toString();
    }

    /**
     * A short, single line summary of the micro-precision/recall/f1.
     */
    public String toOneLineString() {
      return
          "P: " + new DecimalFormat("0.000%").format(precisionMicro()) + "  " +
          "R: " + new DecimalFormat("0.000%").format(recallMicro()) + "  " +
          "F1: " + new DecimalFormat("0.000%").format(f1Micro());
    }
  }

  default Accuracy computeAccuracy(Stream<Pair<KBPInput, String>> examples,
                                   Optional<PrintStream> predictOut) {
    forceTrack("Accuracy");
    Accuracy accuracy = new Accuracy();
    AtomicInteger testI = new AtomicInteger(0);
    DecimalFormat confidenceFormat = new DecimalFormat("0.0000");
    forceTrack("Featurizing");
    examples.parallel().map(example -> {
      Pair<String, Double> predicted = this.classify(example.first);
      synchronized (accuracy) {
        accuracy.predict(Collections.singleton(predicted.first), Collections.singleton(example.second));
      }
      if (testI.incrementAndGet() % 1000 == 0) {
        log(KBPRelationExtractor.class, "[" + testI.get() + "]  " + accuracy.toOneLineString());
      }
      return predicted.first + "\t" + confidenceFormat.format(predicted.second);
    })
      .forEachOrdered(line -> {
        if (predictOut.isPresent()) {
          predictOut.get().println(line);
        }
      });
    endTrack("Featurizing");
    log(accuracy.toString());
    endTrack("Accuracy");
    return accuracy;
  }

}
