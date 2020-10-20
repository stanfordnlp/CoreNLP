package edu.stanford.nlp.tagger.io;

import java.util.Iterator;
import java.util.List;
import edu.stanford.nlp.ling.TaggedWord;

public interface TaggedFileReader extends Iterable<List<TaggedWord>>, Iterator<List<TaggedWord>> {

  String filename();

}
