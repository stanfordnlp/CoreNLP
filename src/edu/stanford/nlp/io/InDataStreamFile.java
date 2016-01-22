/**
 * Title:        StanfordMaxEnt<p>
 * Description:  A Maximum Entropy Toolkit<p>
 * Copyright:    Copyright (c) Bruce Eckel<p>
 * Company:      Stanford University<p>
 * @author Bruce Eckel
 * @version 1.0
 */


package edu.stanford.nlp.io;

import java.io.*;
import java.net.URL;

public class InDataStreamFile extends DataInputStream {

  public InDataStreamFile(String filename) throws FileNotFoundException {
    this(new File(filename));
  }

  public InDataStreamFile(File file) throws FileNotFoundException {
    super(new BufferedInputStream(new FileInputStream(file)));
  }

  public InDataStreamFile(URL url) throws IOException {
    super(new BufferedInputStream(url.openStream()));
  }
  
}
