package edu.stanford.nlp.tagger.io;

import junit.framework.TestCase;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import edu.stanford.nlp.ling.TaggedWord;

public class TSVTaggedFileReaderTest extends TestCase {
  static final String TEST_FILE = 
    "A\t1\nB\t2\nC\t3\n\nD\t4\nE\t5\n\n\n\nF\t6\n\n\n";

  File createFile(String data) 
    throws IOException
  {
    File file = File.createTempFile("TSVTaggedFileReaderTest", "txt");
    FileWriter fout = new FileWriter(file);
    fout.write(data);
    fout.close();
    return file;
  }

  File createTestFile() 
    throws IOException
  {
    return createFile(TEST_FILE);
  }

  File createBrokenFile()
    throws IOException
  {
    // no tags
    return createFile("A\nB\n\n");
  }

  TaggedFileRecord createRecord(File file, String extraArgs) {
    String description = extraArgs + "format=TSV," + file;
    Properties props = new Properties();
    return TaggedFileRecord.createRecord(props, description);
  }
  
  public void testReadNormal()
    throws IOException
  {
    File file = createTestFile();
    TaggedFileRecord record = createRecord(file, ""); 
    List<List<TaggedWord>> sentences = new ArrayList<List<TaggedWord>>();
    
    for (List<TaggedWord> sentence : record.reader()) {
      sentences.add(sentence);
    }
    assertEquals(3, sentences.size());
    assertEquals(3, sentences.get(0).size());

    assertEquals("A", sentences.get(0).get(0).word());
    assertEquals("B", sentences.get(0).get(1).word());
    assertEquals("C", sentences.get(0).get(2).word());
    assertEquals("D", sentences.get(1).get(0).word());
    assertEquals("E", sentences.get(1).get(1).word());
    assertEquals("F", sentences.get(2).get(0).word());

    assertEquals("1", sentences.get(0).get(0).tag());
    assertEquals("2", sentences.get(0).get(1).tag());
    assertEquals("3", sentences.get(0).get(2).tag());
    assertEquals("4", sentences.get(1).get(0).tag());
    assertEquals("5", sentences.get(1).get(1).tag());
    assertEquals("6", sentences.get(2).get(0).tag());
  }


  public void testReadBackwards()
    throws IOException
  {
    File file = createTestFile();
    TaggedFileRecord record = createRecord(file, "tagColumn=0,wordColumn=1,"); 
    List<List<TaggedWord>> sentences = new ArrayList<List<TaggedWord>>();
    
    for (List<TaggedWord> sentence : record.reader()) {
      sentences.add(sentence);
    }
    assertEquals(3, sentences.size());
    assertEquals(3, sentences.get(0).size());

    assertEquals("A", sentences.get(0).get(0).tag());
    assertEquals("B", sentences.get(0).get(1).tag());
    assertEquals("C", sentences.get(0).get(2).tag());
    assertEquals("D", sentences.get(1).get(0).tag());
    assertEquals("E", sentences.get(1).get(1).tag());
    assertEquals("F", sentences.get(2).get(0).tag());

    assertEquals("1", sentences.get(0).get(0).word());
    assertEquals("2", sentences.get(0).get(1).word());
    assertEquals("3", sentences.get(0).get(2).word());
    assertEquals("4", sentences.get(1).get(0).word());
    assertEquals("5", sentences.get(1).get(1).word());
    assertEquals("6", sentences.get(2).get(0).word());
  }

  public void testError()
    throws IOException
  {
    File file = createBrokenFile();
    TaggedFileRecord record = createRecord(file, "tagColumn=0,wordColumn=1,");
    try {
      for (List<TaggedWord> sentence : record.reader()) {
        throw new AssertionError("Should have thrown an error " +
                                 " reading a file with no tags");
      }
    } catch (IllegalArgumentException e) {
      // yay
    }
  }
}