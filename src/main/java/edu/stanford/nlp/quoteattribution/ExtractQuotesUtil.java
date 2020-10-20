package edu.stanford.nlp.quoteattribution;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.ProtobufAnnotationSerializer;
import edu.stanford.nlp.util.Pair;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

/**
 * Created by michaelf on 5/30/16.
 */
public class ExtractQuotesUtil {




  //return true if one is contained in the other.
  public static boolean rangeContains(Pair<Integer, Integer> r1, Pair<Integer, Integer> r2) {
    return ((r1.first <= r2.first && r1.second >= r2.first) || (r1.first <= r2.second && r1.second >= r2.second));
  }
  public static Annotation readSerializedProtobufFile(File fileIn)
  {
    Annotation annotation;
    try {
      ProtobufAnnotationSerializer pas = new ProtobufAnnotationSerializer();
      InputStream is = new BufferedInputStream(new FileInputStream(fileIn));
      Pair<Annotation, InputStream> pair = pas.read(is);
      pair.second.close();
      annotation = pair.first;
      IOUtils.closeIgnoringExceptions(is);
      return annotation;
    }
    catch(Exception e)
    {
      throw new RuntimeException(e);
    }
  }

}
