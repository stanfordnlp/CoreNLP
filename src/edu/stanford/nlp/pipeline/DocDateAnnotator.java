package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.io.*;
import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.util.*;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.*;


public class DocDateAnnotator implements Annotator {

  // property names
  public static String DOC_DATE_FIXED_PROPERTY = "useFixedDate";
  public static String DOC_DATE_MAPPING_FILE_PROPERTY = "useMappingFile";
  public static String DOC_DATE_PRESENT_PROPERTY = "usePresent";
  public static String DOC_DATE_REGEX_PROPERTY = "useRegex";

  // helpful regexes
  public static Pattern DATE_PROPER_FORMAT = Pattern.compile("[0-9]{4}\\-[0-9]{2}\\-[0-9]{2}");
  public static Pattern DATE_NO_HYPHENS_PATTERN = Pattern.compile("[0-9]{8}");

  // settings
  public boolean useFixedDate = false;
  public boolean useMappingFile = false;
  public boolean usePresentDate = false;
  public boolean useRegex = false;

  // fixed date user could provide
  public String fixedDate;

  // mapping from doc id to doc date
  public HashMap<String, String> docIDToDocDate;

  // regex
  public Pattern fileDocDatePattern;

  public DocDateAnnotator(String name, Properties props) throws IOException {
    // if a mapping file is specified, build the hash map
    useFixedDate = props.containsKey(name+"."+DOC_DATE_FIXED_PROPERTY);
    useMappingFile = props.containsKey(name+"."+DOC_DATE_MAPPING_FILE_PROPERTY);
    usePresentDate = PropertiesUtils.getBool(props, name+"."+DOC_DATE_PRESENT_PROPERTY, false);
    useRegex = props.containsKey(name+"."+DOC_DATE_REGEX_PROPERTY);
    if (useMappingFile) {
      // if using mapping file, load the doc dates
      String mappingFilePath = props.getProperty(name + "." + DOC_DATE_MAPPING_FILE_PROPERTY);
      docIDToDocDate = new HashMap<String,String>();
      List<String> mappingEntries =
          IOUtils.linesFromFile(mappingFilePath);
      for (String fileNameAndDocDate : mappingEntries) {
        String[] keyAndValue = fileNameAndDocDate.split("\t");
        docIDToDocDate.put(keyAndValue[0], keyAndValue[1]);
      }
    } else if (useFixedDate) {
      // if using fixed date, set that date
      fixedDate = props.getProperty(name+"."+DOC_DATE_FIXED_PROPERTY);
    } else if (useRegex) {
      fileDocDatePattern = Pattern.compile(props.getProperty(name+"."+DOC_DATE_REGEX_PROPERTY));
    }
  }

  @Override
  public void annotate(Annotation annotation) {
    String docID = annotation.get(CoreAnnotations.DocIDAnnotation.class);
    if (docID == null)
      docID = "";
    String foundDocDate;
    if (useMappingFile) {
      foundDocDate = docIDToDocDate.get(docID);
      if (foundDocDate == null)
        foundDocDate = "";
    } else if (useFixedDate) {
      foundDocDate = fixedDate;
    } else if (usePresentDate) {
      foundDocDate = currentDate();
    } else if (useRegex) {
      Matcher m = fileDocDatePattern.matcher(docID);
      m.matches();
      foundDocDate = m.group(1);
      if (DATE_NO_HYPHENS_PATTERN.matcher(foundDocDate).find() && foundDocDate.length() == 8)
        foundDocDate = addHyphensToDate(foundDocDate);
    } else {
      foundDocDate = "";
    }
    // check date has proper format
    Matcher properDateFormat = DATE_PROPER_FORMAT.matcher(foundDocDate);
    if (properDateFormat.matches())
      annotation.set(CoreAnnotations.DocDateAnnotation.class, foundDocDate);
  }

  /** helper for return current date **/
  public String currentDate() {
    return new SimpleDateFormat("yyyy-MM-dd").format(Calendar.getInstance().getTime());
  }

  /** helper to add hyphens **/
  private static String addHyphensToDate(String compactDateString) {
    String yyyy = compactDateString.substring(0,4);
    String mm = compactDateString.substring(4,6);
    String dd = compactDateString.substring(6,8);
    return yyyy + '-' + mm + '-' + dd;
  }

  @Override
  public Set<Class<? extends CoreAnnotation>> requires() {
    return Collections.emptySet();
  }

  @Override
  public Set<Class<? extends CoreAnnotation>> requirementsSatisfied() {
    return Collections.emptySet();
  }

}
