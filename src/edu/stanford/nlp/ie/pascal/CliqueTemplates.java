package edu.stanford.nlp.ie.pascal;

import edu.stanford.nlp.stats.ClassicCounter;

import java.net.URL;
import java.util.HashMap;
import java.util.ArrayList;

/**
 * Template information and counters corresponding to sampling on one document.
 *
 * As an alternative to reading a document labelling into a full {@link PascalTemplate}
 * we can read it into partial templates which contain only strictly related information,
 * (See {@link DateTemplate} and {@link InfoTemplate}).
 *
 * @author Chris Cox
 */

public class CliqueTemplates {

  public HashMap stemmedAcronymIndex = new HashMap();
  public HashMap inverseAcronymMap = new HashMap();

  public ArrayList<String> urls = null;

  public ClassicCounter dateCliqueCounter = new ClassicCounter();
  public ClassicCounter locationCliqueCounter = new ClassicCounter();
  public ClassicCounter workshopInfoCliqueCounter = new ClassicCounter();


}
