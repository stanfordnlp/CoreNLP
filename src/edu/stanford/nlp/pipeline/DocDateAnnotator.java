package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.io.*;
import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.util.logging.Redwood;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.*;


/** Find and record a document (authoring) date for a document.
 *  This document date can be used, for instance, in resolving time expressions like "next Thursday".
 *  The document date can be today, passed in via a property, given by a filename to date mapping file,
 *   or it may be extracted from a filename by regex.
 *   The document date is stored as a YYYY-MM-DD String under the
 *   CoreAnnotations.DocDateAnnotation key.
 *
 *  @author Sebastian Schuster
 */
public class DocDateAnnotator implements Annotator {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(DocDateAnnotator.class);

  // property names
  public static final String DOC_DATE_FIXED_PROPERTY = "useFixedDate";
  public static final String DOC_DATE_MAPPING_FILE_PROPERTY = "useMappingFile";
  public static final String DOC_DATE_PRESENT_PROPERTY = "usePresent";
  public static final String DOC_DATE_REGEX_PROPERTY = "useRegex";

  // helpful regexes
  public static final Pattern DATE_PROPER_FORMAT = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}");
  public static final Pattern DATE_NO_HYPHENS_PATTERN = Pattern.compile("[0-9]{8}");

  // settings
  private final boolean useFixedDate; // = false;
  private final boolean useMappingFile; // = false;
  private final boolean usePresentDate; // = false;
  private final boolean useRegex; // = false;

  // fixed date user could provide
  private String fixedDate;

  // mapping from doc id to doc date
  private HashMap<String, String> docIDToDocDate;

  // regex
  private Pattern fileDocDatePattern;

  private final String description;

  public DocDateAnnotator(String name, Properties props) {
    StringBuilder sb = new StringBuilder("DocDateAnnotator[");
    // if a mapping file is specified, build the hash map
    useFixedDate = props.containsKey(name+"."+DOC_DATE_FIXED_PROPERTY);
    useMappingFile = props.containsKey(name+"."+DOC_DATE_MAPPING_FILE_PROPERTY);
    usePresentDate = PropertiesUtils.getBool(props, name+"."+DOC_DATE_PRESENT_PROPERTY, false);
    useRegex = props.containsKey(name+"."+DOC_DATE_REGEX_PROPERTY);
    if (useMappingFile) {
      // if using mapping file, load the doc dates
      String mappingFilePath = props.getProperty(name + "." + DOC_DATE_MAPPING_FILE_PROPERTY);
      sb.append("mappingFile=");
      sb.append(mappingFilePath);
      docIDToDocDate = new HashMap<>();
      List<String> mappingEntries =
          IOUtils.linesFromFile(mappingFilePath);
      for (String fileNameAndDocDate : mappingEntries) {
        String[] keyAndValue = fileNameAndDocDate.split("\t");
        docIDToDocDate.put(keyAndValue[0], keyAndValue[1]);
      }
    } else if (useFixedDate) {
      // if using fixed date, set that date
      fixedDate = props.getProperty(name + "." + DOC_DATE_FIXED_PROPERTY);
      sb.append("fixedDate=");
      sb.append(fixedDate);
    } else if (usePresentDate) {
      fixedDate = currentDate();
      sb.append("presentDate=");
      sb.append(fixedDate);
    } else if (useRegex) {
      String regex = props.getProperty(name+"."+DOC_DATE_REGEX_PROPERTY);
      fileDocDatePattern = Pattern.compile(regex);
      sb.append("regex=");
      sb.append(regex);
    } else {
      sb.append("no docDate finder");
    }
    sb.append(']');
    description = sb.toString();
  }

  @Override
  public void annotate(Annotation annotation) {
    String docID = annotation.get(CoreAnnotations.DocIDAnnotation.class);
    if (docID == null)
      docID = "";
    String foundDocDate;
    if (useMappingFile) {
      foundDocDate = docIDToDocDate.get(docID);
      if (foundDocDate == null) {
        log.warn("DocDate mapping file failed to match against " + docID);
        foundDocDate = "";
      }
    } else if (useFixedDate || usePresentDate) {
      foundDocDate = fixedDate;
    } else if (useRegex) {
      Matcher m = fileDocDatePattern.matcher(docID);
      if (m.matches()) {
        foundDocDate = m.group(1);
        if (foundDocDate.length() == 8 && DATE_NO_HYPHENS_PATTERN.matcher(foundDocDate).matches()) {
          foundDocDate = addHyphensToDate(foundDocDate);
        }
      } else {
        log.warn("DocDate regex failed to match against " + docID);
        foundDocDate = "";
      }
    } else {
      foundDocDate = "";
    }
    // check date has proper format
    Matcher properDateFormat = DATE_PROPER_FORMAT.matcher(foundDocDate);
    if (properDateFormat.matches()) {
      annotation.set(CoreAnnotations.DocDateAnnotation.class, foundDocDate);
    }
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
  public String toString() {
    return description;
  }

  @SuppressWarnings("RawUseOfParameterized")
  @Override
  public Set<Class<? extends CoreAnnotation>> requires() {
    return Collections.emptySet();
  }

  @SuppressWarnings("RawUseOfParameterized")
  @Override
  public Set<Class<? extends CoreAnnotation>> requirementsSatisfied() {
    return Collections.emptySet();
  }

}
