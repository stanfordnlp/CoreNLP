package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.io.*;
import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.util.*;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;


public class DocDateAnnotator implements Annotator {

  // property names
  public static String DOC_DATE_FIXED_PROPERTY = "useFixedDate";
  public static String DOC_DATE_MAPPING_FILE_PROPERTY = "mappingFile";
  public static String DOC_DATE_PRESENT_PROPERTY = "usePresent";

  // settings
  public boolean useFixedDate = false;
  public boolean useMappingFile = false;
  public boolean usePresentDate = false;

  // fixed date user could provide
  public String fixedDate;

  // mapping from doc id to doc date
  public HashMap<String, String> docIDToDocDate;

  public DocDateAnnotator(String name, Properties props) throws IOException {
    // if a mapping file is specified, build the hash map
    useFixedDate = props.containsKey(name+"."+DOC_DATE_FIXED_PROPERTY);
    useMappingFile = props.containsKey(name+"."+DOC_DATE_MAPPING_FILE_PROPERTY);
    usePresentDate = PropertiesUtils.getBool(props, name+"."+DOC_DATE_PRESENT_PROPERTY, false);
    if (useMappingFile) {
      String mappingFilePath = props.getProperty(name + "." + DOC_DATE_MAPPING_FILE_PROPERTY);
      docIDToDocDate = new HashMap<String,String>();
      List<String> mappingEntries =
          IOUtils.linesFromFile(mappingFilePath);
      for (String fileNameAndDocDate : mappingEntries) {
        String[] keyAndValue = fileNameAndDocDate.split("\t");
        docIDToDocDate.put(keyAndValue[0], keyAndValue[1]);
      }
    } else if (useFixedDate) {
      fixedDate = props.getProperty(name+"."+DOC_DATE_FIXED_PROPERTY);
    }
  }

  @Override
  public void annotate(Annotation annotation) {
    String docID = annotation.get(CoreAnnotations.DocIDAnnotation.class);
    if (docID == null)
      docID = "";
    if (useMappingFile) {
      String docDate = docIDToDocDate.get(docID);
      annotation.set(CoreAnnotations.DocDateAnnotation.class, docDate);
    } else if (useFixedDate) {
      annotation.set(CoreAnnotations.DocDateAnnotation.class, fixedDate);
    } else if (usePresentDate) {
      annotation.set(CoreAnnotations.DocDateAnnotation.class, currentDate());
    }
  }

  /** helper for return current date **/
  public String currentDate() {
    return new SimpleDateFormat("yyyy-MM-dd").format(Calendar.getInstance().getTime());
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
