package edu.stanford.nlp.time;

import java.util.List;

import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.ErasureUtils;

/**
 * Set of common annotations for {@link edu.stanford.nlp.util.CoreMap}s 
 * that require classes from the time package.  See 
 * {@link CoreAnnotations} for more information.  This class exists 
 * so that {@link CoreAnnotations} need not depend on timex classes,
 * which in particular pull in the xom.jar package.
 *
 * @author John Bauer
 *
 */

public class TimeAnnotations {
  /**
   * The CoreMap key for storing a Timex annotation
   */
  public static class TimexAnnotation implements CoreAnnotation<Timex> {
    public Class<Timex> getType() {
      return Timex.class;
    }
  }


  /**
   * The CoreMap key for storing all Timex annotations in a document.
   */
  public static class TimexAnnotations implements CoreAnnotation<List<CoreMap>> {
    public Class<List<CoreMap>> getType() {
      return ErasureUtils.uncheckedCast(List.class);
    }
  }  
}

