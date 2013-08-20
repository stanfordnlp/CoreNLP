package edu.stanford.nlp.ie.pascal;

import edu.stanford.nlp.util.StringUtils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

/**
 * Given a PERSON NER different from a pronoun, represents the person according to
 * its salutation, first name and last name.
 *
 * @author Marie-Catherine de Marneffe
 */
public class NormalizedName {

  private static final String[] salutationWords = new String[]{"mister", "mr.", "mr", "miss", "mrs.", "mrs", "ms.", "ms", "doctor", "dr.", "dr", "professor", "prof", "prof.", "governor", "gov", "gov.", "senator", "sen.", "rep.", "captain", "capt.", "general", "gen.", "maj.", "pvt.", "adm.", "colonel", "col.", "col", "lieutenant", "lt.", "sergent", "sgt.", "prince", "princess", "king", "queen", "judge", "reverend", "rev."};
  private List<String> salutations = new ArrayList<String>();

  private static HashSet<String> girlNames;
  private static HashSet<String> boyNames;

  private Map<String, String> map = new HashMap<String, String>();

  private String salutation = "";
  private String firstName = "";
  private String lastName = "";
  

  /**
   * Reads the list of boy/girl names
   * This method must be public because it can be called from Mention.initialize()
   * It's Ok to have this as static data, because it's only read-only
   */
  public static synchronized void initNameLists(String nameDir) {
      if (girlNames == null)
        girlNames = readNames(nameDir+"/girl");
      if (boyNames == null)
        boyNames = readNames(nameDir+"/boy");      
  }
  
  public static HashSet<String> getBoyNames() { return boyNames; }
  public static HashSet<String> getGirlNames() { return girlNames; }
  
  /**
   * SP august 2008
   * Add the possibility to specify the location of the name files
   * @param input name string to be normalised
   * @param nameDir location of name files (boy, girl)
   */
  public NormalizedName(String input, String nameDir) {
    
    initNameLists(nameDir);
    
    Collections.addAll(salutations, salutationWords);
    fillMap();
    extract(input);
  }

  private void extract(String input) {
    List<String> name = extractNamePart(input);
    if (!name.isEmpty()) {
      extractFields(name);
    }
  }

  // extract the name from the input string
  // since the string can be something like "William Gates, the president of ..."
  private List<String> extractNamePart(String input) {
    List<String> name = new ArrayList<String>();
    String[] words = input.split("[\\s+]");

    boolean gap = false;
    boolean prep = false;
    for (int i = 0; i < words.length; i++) {// extract the relevant part
      if (words[i].equals("of")) {
        prep = true;
      }
      if (salutations.contains(words[i].toLowerCase())) {// find a salutation, remove what's before
        name = new ArrayList<String>();
        name.add(words[i]);
        continue;
      }
      if (words[i].equals(",") && !name.isEmpty()) {
        i = words.length;
        continue;
      }
      if (words[i].equals(",") && name.isEmpty()) {
        gap = false;
        prep = false;
        continue;
      }
      if (StringUtils.isCapitalized(words[i]) && !words[i].equals("The") && !words[i].equals("This") && !gap && !prep) {
        name.add(words[i]);
      }
      if (!StringUtils.isCapitalized(words[i]) && !name.isEmpty()) {
        gap = true;
      }
    }
    //System.err.println(name);
    return name;
  }


  private void extractFields(List<String> input) {
    if (input.size() == 1) {
      String word = input.get(0);
      if (isSalutation(word)) {
        salutation = word;
      } else if (girlNames.contains(word.toLowerCase()) || boyNames.contains(word.toLowerCase())) {
        firstName = word;
      } else {
        lastName = word;
      }
    }

    if (input.size() == 2) {
      String word1 = input.get(0);
      String word2 = input.get(1);

      if (isSalutation(word1) && !isSalutation(word2)) {
        salutation = word1;
        lastName = word2;
      } else if (isSalutation(word1) && isSalutation(word2)) {
        salutation = word1 + " " + word2;
      } else {
        firstName = word1;
        lastName = word2;
      }
    }

    if (input.size() > 2) {
      int afterSal = 0;
      for (int i = 0; i < input.size(); i++) {
        String word = input.get(i);
        if (isSalutation(word)) {
          afterSal++;
          if (!salutation.equals("")) {
            salutation = salutation + " " + word;
          } else {
            salutation = word;
          }
        } else if (i == input.size() - 1) {//if the last word
          lastName = word;
        } else if (i == afterSal) {//right after the salutation
          firstName = word;
        }
      }
    }

  }

  private void fillMap() {
    map.put("mister", "mr");
    map.put("miss", "ms");
    map.put("doctor", "dr");
    map.put("lieutenant", "lt");
  }

  private boolean isSalutation(String word) {
    if (salutations.contains(word.toLowerCase())) {
      return true;
    }

    return false;
  }

  @Override
  public String toString() {
    return ("salutation: " + salutation + " firstName: " + firstName + " lastName: " + lastName);
  }


  public boolean isCompatible(NormalizedName nn) {
    if (this.lastName.equals(nn.lastName)) {// same last names
      if (compatibleFirstName(this.firstName, nn.firstName)) {// ok for first name
        if (compatibleSalutation(this.salutation, nn.salutation) || this.salutation.equals("") || nn.salutation.equals("")) {
          return true;
        } else {
          return false;
        }
      } else {
        return false;
      }
    } else {
      if ((this.lastName.equals("") || nn.lastName.equals("")) && this.firstName.equals(nn.firstName)) {
        return true;
      }
    }
    return false;
  }

  // 2 first names are compatbile if the same, one is empty or
  // one starts with the same letter as the other
  private boolean compatibleFirstName(String in1, String in2) {
    if (in1.equals(in2) || in1.equals("") || in2.equals("")) {
      return true;
    }
    if (in1.endsWith(".") || in2.endsWith(".")) {//initials
      return in2.startsWith(in1.substring(0, in1.length() - 1)) || in1.startsWith(in2.substring(0, in2.length() - 1));
    }
    if (in1.length() == 1 || in2.length() == 1) {
      return in2.startsWith(in1) || in1.startsWith(in2);
    }
    return false;
  }

  private boolean compatibleSalutation(String in1, String in2) {
    String[] string1 = in1.split("[\\s]");
    String[] string2 = in2.split("[\\s]");

    if (string1.length != string2.length) {
      return false;
    }


    for (int i = 0; i < string1.length; i++) {
      String key = string1[i].toLowerCase();
      if (key.endsWith(".")) {
        key = key.substring(0, key.length() - 1);
      }
      String value = string2[i].toLowerCase();
      if (value.endsWith(".")) {
        value = value.substring(0, value.length() - 1);
      }
      if (key.equals(value) || (key.startsWith(value) && value.length() > 2) || (value.startsWith(key) && key.length() > 2) || (map.get(key) != null && map.get(key).equals(value)) || (map.get(value) != null && map.get(value).equals(key))) {
      } else {
        return false;
      }
    }

    return true;
  }

  private static HashSet<String> readNames(String file) {
    HashSet<String> names = new HashSet<String>();
    try {
      BufferedReader reader = new BufferedReader(new FileReader(file));
      for (String line; (line = reader.readLine()) != null;) {
        line = line.replaceAll("\\s+$", "").toLowerCase();
        names.add(line);
        //System.err.println(line);
      }
      reader.close();
    } catch (IOException e) {
      System.err.println("Error IOException with processing " + file);
      throw new RuntimeException(e.getMessage());
    }
    return names;
  }


  /**
   * For testing only
   *
   */
  public static void main(String[] args) {
    String data1 = args[0];
    NormalizedName nn1 = new NormalizedName(data1,"/u/nlp/data/coref");
    System.err.println("first name: " + nn1);

    if (args.length > 1) {
      String data2 = args[1];
      NormalizedName nn2 = new NormalizedName(data2,"/u/nlp/data/coref");
      System.err.println("second name: " + nn2);
      System.err.println("match: " + nn1.isCompatible(nn2));
    }
  }

}
