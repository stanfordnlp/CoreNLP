// BuildLexicalizedParserITest
// Copyright (c) 2002-2010 Leland Stanford Junior University

//This program is free software; you can redistribute it and/or
//modify it under the terms of the GNU General Public License
//as published by the Free Software Foundation; either version 2
//of the License, or (at your option) any later version.

//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General Public License for more details.

//You should have received a copy of the GNU General Public License
//along with this program; if not, write to the Free Software
//Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.

//For more information, bug reports, fixes, contact:
//Christopher Manning
//Dept of Computer Science, Gates 1A
//Stanford CA 94305-9010
//USA
//Support/Questions: java-nlp-user@lists.stanford.edu
//Licensing: java-nlp-support@lists.stanford.edu
//http://www-nlp.stanford.edu/software/tagger.shtml


package edu.stanford.nlp.parser.lexparser;

import junit.framework.TestCase;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Formatter;

import edu.stanford.nlp.io.TeeStream;
import edu.stanford.nlp.util.StringUtils;

/**
 * This builds and tests several English parsers on one or a few input
 * trees each.  The goal is to make sure there are no crashes when
 * executing the normally used training paths.
 *
 * @author John Bauer
 */
public class BuildLexicalizedParserITest extends TestCase {


  // This is the example command line run by the
  // makeSerializedParser.csh script:
  //
  //String commandLine = "-evals \"factDA,tsv\" -goodPCFG -saveToSerializedFile wsjPCFG.ser.gz -saveToTextFile wsjPCFG.txt -maxLength 40 -train /afs/ir/data/linguistic-data/Treebank/3/parsed/mrg/wsj 200-2199 -testTreebank /afs/ir/data/linguistic-data/Treebank/3/parsed/mrg/wsj 2200-2219";

  // We build a similar command line using temporary files for the
  // parser and the output files
  public static final String[] englishCommandLines = {"-evals factDA,tsv -goodPCFG -saveToSerializedFile %s -saveToTextFile %s -maxLength 40 -train %s -testTreebank %s",
                                                      "-evals factDA,tsv -goodPCFG -noTagSplit -saveToSerializedFile %s -saveToTextFile %s -compactGrammar 0 -maxLength 40 -train %s -testTreebank %s",
                                                      "-evals factDA,tsv -ijcai03 -v -printStates -compactGrammar 0 -correctTags -saveToSerializedFile %s -saveToTextFile %s -maxLength 40 -train %s -testTreebank %s",
                                                      "-evals factDA,tsv -ijcai03 -v -printStates -compactGrammar 0 -saveToSerializedFile %s -saveToTextFile %s -maxLength 40 -train %s -testTreebank %s"};

  public static final String[] englishTwoTreebanks = {"-evals factDA,tsv -ijcai03 -saveToSerializedFile %s -saveToTextFile %s -maxLength 40 -train %s -train2 %s 0-9 0.5 -testTreebank %s",
                                                      "-evals factDA,tsv -goodPCFG -saveToSerializedFile %s -saveToTextFile %s -maxLength 40 -train %s -train2 %s 0-9 0.5 -testTreebank %s"};

  public static final String[] chineseCommandLines = {"-evals factDA,tsv -tLPP edu.stanford.nlp.parser.lexparser.ChineseTreebankParserParams -chinesePCFG -encoding utf-8 -saveToSerializedFile %s -saveToTextFile %s -maxLength 40 -train %s -test %s",
                                                      "-evals factDA,tsv -tLPP edu.stanford.nlp.parser.lexparser.ChineseTreebankParserParams -acl03chinese -encoding utf-8 -scTags -saveToSerializedFile %s -saveToTextFile %s -maxLength 40 -train %s -test %s",
                                                      "-evals factDA,tsv -tLPP edu.stanford.nlp.parser.lexparser.ChineseTreebankParserParams -encoding utf-8 -chineseFactored -saveToSerializedFile %s -saveToTextFile %s -maxLength 40 -train %s -test %s",
                                                      "-evals factDA,tsv -tLPP edu.stanford.nlp.parser.lexparser.ChineseTreebankParserParams -chinesePCFG -encoding utf-8 -saveToSerializedFile %s -saveToTextFile %s -maxLength 40 -train %s -test %s",
                                                      "-evals factDA,tsv -tLPP edu.stanford.nlp.parser.lexparser.ChineseTreebankParserParams -encoding utf-8 -segmentMarkov -saveToSerializedFile %s -saveToTextFile %s -train %s -test %s -sctags -acl03chinese"};

  public static final String[] germanCommandLines = {"-evals factDA,tsv -tLPP edu.stanford.nlp.parser.lexparser.NegraPennTreebankParserParams -encoding UTF-8 -hMarkov 1 -vMarkov 2 -vSelSplitCutOff 300 -uwm 1 -unknownSuffixSize 2 -maxLength 40 -nodeCleanup 2 -saveToSerializedFile %s -saveToTextFile %s -train %s -test %s",
                                                     "-evals factDA,tsv -tLPP edu.stanford.nlp.parser.lexparser.NegraPennTreebankParserParams -encoding UTF-8 -PCFG -hMarkov 1 -vMarkov 2 -vSelSplitCutOff 300 -uwm 1 -unknownSuffixSize 1 -maxLength 40 -nodeCleanup 2 -saveToSerializedFile %s -saveToTextFile %s -train %s -test %s"};

  public static final String[] frenchCommandLines = {"-evals factDA,tsv -maxLength 40 -tLPP edu.stanford.nlp.parser.lexparser.FrenchTreebankParserParams -encoding UTF-8 -frenchFactored -saveToSerializedFile %s -saveToTextFile %s -train %s -test %s"};

  public static final String[] arabicCommandLines = {"-evals factDA,tsv -maxLength 40 -tLPP edu.stanford.nlp.parser.lexparser.ArabicTreebankParserParams -encoding UTF-8 -arabicFactored -saveToSerializedFile %s -saveToTextFile %s -train %s -test %s"};

  // TODO: weird: this parser is not saved anywhere
  //LexicalizedParser invoked with arguments: -evals factDA,tsv -acl03pcfg -maxLength 40 -train /afs/ir/data/linguistic-data/Treebank/3/parsed/mrg/wsj 200-2199 -testTreebank /afs/ir/data/linguistic-data/Treebank/3/parsed/mrg/wsj 2200-2219

  public static final String baseTestSerCommandLine = "-encoding utf-8 -loadFromSerializedFile %s -testTreebank %s 0-1";
  public static final String baseTestTextCommandLine = "-encoding utf-8 -loadFromTextFile %s -testTreebank %s 0-1";
  // public static final String PERF_EVAL = "factor LP/LR summary evalb: LP: ";


  public static final String englishOneTree = "data/edu/stanford/nlp/parser/trees/en-onetree.txt";
  public static final String englishSecondTree = "data/edu/stanford/nlp/parser/trees/en-secondtree.txt";
  public static final String englishThreeTrees = "data/edu/stanford/nlp/parser/trees/en-threetrees.txt";

  public static final String chineseOneTree = "data/edu/stanford/nlp/parser/trees/zh-onetree.txt";
  public static final String chineseThreeTrees = "data/edu/stanford/nlp/parser/trees/zh-threetrees.txt";

  public static final String germanOneTree = "data/edu/stanford/nlp/parser/trees/de-onesent.txt";
  public static final String germanThreeTrees = "data/edu/stanford/nlp/parser/trees/de-threesents.txt";

  public static final String frenchOneTree = "data/edu/stanford/nlp/parser/trees/fr-onetree.txt";
  public static final String frenchThreeTrees = "data/edu/stanford/nlp/parser/trees/fr-threetrees.txt";

  public static final String arabicOneTree = "data/edu/stanford/nlp/parser/trees/ar-onetree.txt";
  public static final String arabicThreeTrees = "data/edu/stanford/nlp/parser/trees/ar-threetrees.txt";

  public static class ParserTestCase {
    public final String[] trainCommandLine;
    public final String testPath;
    public final File parserFile;
    public final File textFile;

    ParserTestCase(String[] trainCommandLine, String testPath,
                   File parserFile, File textFile) {
      this.trainCommandLine = trainCommandLine;
      this.testPath = testPath;
      this.parserFile = parserFile;
      this.textFile = textFile;
    }

    public static ParserTestCase
      buildOneTreebankTestCase(String baseCommandLine,
                               String trainPath,
                               String testPath)
      throws IOException
    {
      File parserFile = File.createTempFile("parser", ".ser.gz");
      File textFile = File.createTempFile("parser", ".txt");

      Formatter commandLineFormatter = new Formatter();
      // Note that we test on the train path.  The goal is we should
      // get 100% accuracy if everything worked.  We will test on
      // unknown trees in the next step.
      commandLineFormatter.format(baseCommandLine, parserFile.getPath(),
                                  textFile.getPath(), trainPath, trainPath);
      String[] trainCommandLine =
        commandLineFormatter.toString().split("\\s+");

      ParserTestCase test = new ParserTestCase(trainCommandLine, testPath,
                                               parserFile, textFile);
      return test;
    }

    public static ParserTestCase
      buildTwoTreebankTestCase(String baseCommandLine,
                               String trainPath,
                               String secondaryPath,
                               String testPath)
      throws IOException
    {
      File parserFile = File.createTempFile("parser", ".ser.gz");
      File textFile = File.createTempFile("parser", ".txt");

      Formatter commandLineFormatter = new Formatter();
      // Note that we test on the train path.  The goal is we should
      // get 100% accuracy if everything worked.  We will test on
      // unknown trees in the next step.
      commandLineFormatter.format(baseCommandLine, parserFile.getPath(),
                                  textFile.getPath(), trainPath,
                                  secondaryPath, trainPath);
      String[] trainCommandLine =
        commandLineFormatter.toString().split("\\s+");

      ParserTestCase test = new ParserTestCase(trainCommandLine, testPath,
                                               parserFile, textFile);
      return test;
    }
  }

  static public void buildAndTest(ParserTestCase test)
    throws IOException
  {
    PrintStream originalOut = System.out;
    PrintStream originalErr = System.err;

    System.out.println("Training:");
    System.out.println(StringUtils.join(test.trainCommandLine));

    ByteArrayOutputStream savedOutput = new ByteArrayOutputStream();
    TeeStream teeOut = new TeeStream(savedOutput, System.out);
    PrintStream teeOutPS = new PrintStream(teeOut);
    TeeStream teeErr = new TeeStream(savedOutput, System.err);
    PrintStream teeErrPS = new PrintStream(teeErr);
    System.setOut(teeOutPS);
    System.setErr(teeErrPS);

    LexicalizedParser.main(test.trainCommandLine);

    teeOutPS.flush();
    teeErrPS.flush();
    teeOut.flush();
    teeErr.flush();

    String[] outputLines =
      savedOutput.toString().split("(?:\\n|\\r)+");
    String perfLine = outputLines[outputLines.length - 5];
    System.out.println(perfLine);
    assertEquals("factor LP/LR summary evalb: LP: 100.0 LR: 100.0 F1: 100.0 Exact: 100.0 N: 1", perfLine.trim());

    Formatter commandLineFormatter = new Formatter();
    commandLineFormatter.format(baseTestSerCommandLine,
                                test.parserFile.getPath(), test.testPath);
    String[] testCommandLine =
      commandLineFormatter.toString().split("\\s");

    System.out.println("Testing:");
    System.out.println(StringUtils.join(testCommandLine));

    LexicalizedParser.main(testCommandLine);

    commandLineFormatter = new Formatter();
    commandLineFormatter.format(baseTestTextCommandLine,
                                test.textFile.getPath(), test.testPath);
    testCommandLine = commandLineFormatter.toString().split("\\s");

    System.out.println("Testing:");
    System.out.println(StringUtils.join(testCommandLine));

    LexicalizedParser.main(testCommandLine);

    teeOutPS.flush();
    teeErrPS.flush();
    teeOut.flush();
    teeErr.flush();

    System.setOut(originalOut);
    System.setErr(originalErr);
  }

  /**
   * This tests that building and running a simple English parser
   * model works correctly.
   */
  public void testBuildEnglishParser()
    throws IOException
  {
    for (String englishCommandLine : englishCommandLines) {
      ParserTestCase test = ParserTestCase.buildOneTreebankTestCase(englishCommandLine, englishOneTree, englishThreeTrees);
      buildAndTest(test);
    }

    for (String englishCommandLine : englishTwoTreebanks) {
      ParserTestCase test = ParserTestCase.buildTwoTreebankTestCase(englishCommandLine, englishOneTree, englishSecondTree, englishThreeTrees);
      buildAndTest(test);
    }
  }

  public void testBuildChineseParser()
    throws IOException
  {
    for (String chineseCommandLine : chineseCommandLines) {
      ParserTestCase test = ParserTestCase.buildOneTreebankTestCase(chineseCommandLine, chineseOneTree, chineseThreeTrees);
      buildAndTest(test);
    }
  }

  public void testBuildGermanParser()
    throws IOException
  {
    for (String germanCommandLine : germanCommandLines) {
      ParserTestCase test = ParserTestCase.buildOneTreebankTestCase(germanCommandLine, germanOneTree, germanThreeTrees);
      buildAndTest(test);
    }
  }

  public void testBuildFrenchParser()
    throws IOException
  {
    for (String frenchCommandLine : frenchCommandLines) {
      ParserTestCase test = ParserTestCase.buildOneTreebankTestCase(frenchCommandLine, frenchOneTree, frenchThreeTrees);
      buildAndTest(test);
    }
  }

  public void testBuildArabicParser()
    throws IOException
  {
    for (String arabicCommandLine : arabicCommandLines) {
      ParserTestCase test = ParserTestCase.buildOneTreebankTestCase(arabicCommandLine, arabicOneTree, arabicThreeTrees);
      buildAndTest(test);
    }
  }



}
