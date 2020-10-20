package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.util.logging.Redwood;

import java.util.*;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.util.ArraySet;
import edu.stanford.nlp.util.CoreMap;

/**
 * This class adds gender information (MALE / FEMALE) to entity mentions as GenderAnnotations.
 * The default is to use name lists from our KBP system.
 *
 * @author jtibs
 */

public class GenderAnnotator implements Annotator {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(GenderAnnotator.class);

  /** paths to lists of male and female first names **/
  public static String MALE_FIRST_NAMES_PATH = "edu/stanford/nlp/models/gender/male_first_names.txt";
  public static String FEMALE_FIRST_NAMES_PATH = "edu/stanford/nlp/models/gender/female_first_names.txt";

  /** HashSets mapping names to potential genders **/
  public HashSet<String> maleNames = new HashSet<String>();
  public HashSet<String> femaleNames = new HashSet<String>();

  public void loadGenderNames(HashSet<String> genderSet, String filePath) {
    List<String> nameFileEntries = IOUtils.linesFromFile(filePath);
    for (String nameCSV : nameFileEntries) {
      String[] namesForThisLine = nameCSV.split(",");
      for (String name : namesForThisLine) {
        genderSet.add(name.toLowerCase());
      }
    }
  }

  public void annotateEntityMention(CoreMap entityMention, String gender) {
    // annotate the entity mention
    entityMention.set(CoreAnnotations.GenderAnnotation.class, gender);
    // annotate each token of the entity mention
    for (CoreLabel token : entityMention.get(CoreAnnotations.TokensAnnotation.class)) {
      token.set(CoreAnnotations.GenderAnnotation.class, gender);
    }
  }

  public GenderAnnotator(String annotatorName, Properties props) {
    // load the male and female names
    MALE_FIRST_NAMES_PATH = props.getProperty("gender.maleNamesFile", MALE_FIRST_NAMES_PATH);
    FEMALE_FIRST_NAMES_PATH = props.getProperty("gender.femaleNamesFile", FEMALE_FIRST_NAMES_PATH);
    loadGenderNames(maleNames, MALE_FIRST_NAMES_PATH);
    loadGenderNames(femaleNames, FEMALE_FIRST_NAMES_PATH);
  }

  public void annotate(Annotation annotation) {
    // iterate through each sentence, iterate through each entity mention in the sentence
    for (CoreMap sentence : annotation.get(CoreAnnotations.SentencesAnnotation.class)) {
      for (CoreMap entityMention : sentence.get(CoreAnnotations.MentionsAnnotation.class)) {
        // if the entityMention is of type PERSON, see if name is in one of the lists for male and female names
        // annotate the entity mention's CoreMap
        if (entityMention.get(CoreAnnotations.EntityTypeAnnotation.class).equals("PERSON")) {
          CoreLabel firstName = entityMention.get(CoreAnnotations.TokensAnnotation.class).get(0);
          if (maleNames.contains(firstName.word().toLowerCase()))
            annotateEntityMention(entityMention, "MALE");
          else if (femaleNames.contains(firstName.word().toLowerCase()))
            annotateEntityMention(entityMention, "FEMALE");
        }
      }
    }
  }

  @Override
  public Set<Class<? extends CoreAnnotation>> requires() {
    return Collections.unmodifiableSet(new ArraySet<>(Arrays.asList(
        CoreAnnotations.TextAnnotation.class,
        CoreAnnotations.TokensAnnotation.class,
        CoreAnnotations.SentencesAnnotation.class,
        CoreAnnotations.MentionsAnnotation.class,
        CoreAnnotations.EntityTypeAnnotation.class
    )));
  }

  @Override
  public Set<Class<? extends CoreAnnotation>> requirementsSatisfied() {
    return Collections.singleton(CoreAnnotations.GenderAnnotation.class);
  }

}
