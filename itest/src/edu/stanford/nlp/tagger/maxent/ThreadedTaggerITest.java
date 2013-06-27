// ThreadedTaggerITest -- StanfordMaxEnt, A Maximum Entropy Toolkit
// Copyright (c) 2002-2011 Leland Stanford Junior University
//
//
// This program is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License
// as published by the Free Software Foundation; either version 2
// of the License, or (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
//
// For more information, bug reports, fixes, contact:
//    Christopher Manning
//    Dept of Computer Science, Gates 1A
//    Stanford CA 94305-9010
//    USA
//    Support/Questions: java-nlp-user@lists.stanford.edu
//    Licensing: java-nlp-support@lists.stanford.edu
//    http://www-nlp.stanford.edu/software/tagger.shtml
package edu.stanford.nlp.tagger.maxent;

import junit.framework.TestCase;

import java.util.Properties;

import edu.stanford.nlp.util.DataFilePaths;

public class ThreadedTaggerITest extends TestCase {
  public static final String tagger1 = DataFilePaths.convert(
    "$NLP_DATA_HOME/data/pos-tagger/distrib/" + 
    "english-left3words-distsim.tagger");

  public static final String tagger2 = DataFilePaths.convert(
    "$NLP_DATA_HOME/data/pos-tagger/distrib/" +
    "wsj-0-18-left3words-distsim.tagger");

  public static final String testFile = 
    DataFilePaths.convert("$NLP_DATA_HOME/data/pos-tagger/english/test-wsj-19-21");

  public void testOneTagger() 
    throws Exception
  {
    Properties props = new Properties();
    props.setProperty("model", tagger1);
    props.setProperty("verboseResults", "false");
    props.setProperty("testFile", testFile);
    TestThreadedTagger.runThreadedTest(props);
  }

  public void testTwoTaggers() 
    throws Exception
  {
    Properties props = new Properties();
    props.setProperty("model1", tagger1);
    props.setProperty("model2", tagger2);
    props.setProperty("verboseResults", "false");
    props.setProperty("testFile", testFile);
    TestThreadedTagger.runThreadedTest(props);
  }
}
