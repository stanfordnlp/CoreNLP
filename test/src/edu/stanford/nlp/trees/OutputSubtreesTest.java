package edu.stanford.nlp.trees;

import static org.junit.Assert.assertArrayEquals;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import edu.stanford.nlp.io.IOUtils;

public class OutputSubtreesTest {
  public Path tmpDir;

  @Before
  public void makeTmpDir() throws IOException {
    tmpDir = Files.createTempDirectory("output");
    tmpDir.toFile().deleteOnExit();
  }

  String[] EXPECTED = {"1   In this case zero .",
                       "2   In this",
                       "2   In",
                       "2   this",
                       "1   case zero .",
                       "2   case",
                       "1   zero .",
                       "1   zero",
                       "2   ."};


  @Test
  public void testSingleTree() throws IOException {
    String inputFilename = tmpDir + File.separator + "input.txt";
    IOUtils.writeStringToFile("(1 (2 (2 In) (2 this)) (1 (2 case) (1 (1 zero) (2 .))))", inputFilename, "utf-8");

    String outputFilename = tmpDir + File.separator + "output.txt";    
    String[] args = {"-input", inputFilename, "-output", outputFilename};
    OutputSubtrees.main(args);

    String[] results = IOUtils.slurpFile(outputFilename).trim().split("\n");
    assertArrayEquals(EXPECTED, results);
  }
}
