// TregexPatternCompiler
// Copyright (c) 2004-2007 The Board of Trustees of
// The Leland Stanford Junior University. All Rights Reserved.
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
//
// For more information, bug reports, fixes, contact:
//    Christopher Manning
//    Dept of Computer Science, Gates 1A
//    Stanford CA 94305-9010
//    USA
//    Support/Questions: parser-user@lists.stanford.edu
//    Licensing: parser-support@lists.stanford.edu
//    http://www-nlp.stanford.edu/software/tregex.shtml

package old.edu.stanford.nlp.trees.tregex;

import old.edu.stanford.nlp.util.Function;
import old.edu.stanford.nlp.trees.CollinsHeadFinder;
import old.edu.stanford.nlp.trees.HeadFinder;
import old.edu.stanford.nlp.trees.PennTreebankLanguagePack;

/**
 * A class for compiling TregexPatterns with specific HeadFinders and or
 * basicCategoryFunctions.
 *
 * @author Galen Andrew
 */
public class TregexPatternCompiler {

  private Function<String,String> basicCatFunction = new PennTreebankLanguagePack().getBasicCategoryFunction();
  private HeadFinder headFinder = new CollinsHeadFinder();

  public static TregexPatternCompiler defaultCompiler = new TregexPatternCompiler();

  public TregexPatternCompiler() {
  }

  /**
   * A compiler that uses this basicCatFunction and the default headfinder.
   *
   * @param basicCatFunction the function mapping Strings to Strings
   */
  public TregexPatternCompiler(Function<String,String> basicCatFunction) {
    this.basicCatFunction = basicCatFunction;
  }

  /**
   * A compiler that uses this HeadFinder and the default basicCategoryFunction
   *
   * @param headFinder the HeadFinder
   */
  public TregexPatternCompiler(HeadFinder headFinder) {
    this.headFinder = headFinder;
  }

  /**
   * A compiler that uses this HeadFinder and this basicCategoryFunction
   *
   * @param headFinder       the HeadFinder
   * @param basicCatFunction hthe function mapping Strings to Strings
   */
  public TregexPatternCompiler(HeadFinder headFinder, Function<String,String> basicCatFunction) {
    this.headFinder = headFinder;
    this.basicCatFunction = basicCatFunction;
  }

  /**
   * Create a TregexPattern from this tregex string using the headFinder and
   * basicCat function this TregexPatternCompiler was created with
   *
   * @param tregex the pattern to parse
   * @return a new TregexPattern object based on this string
   * @throws ParseException
   */
  public TregexPattern compile(String tregex) throws ParseException {
    TregexPattern.setBasicCatFunction(basicCatFunction);
    Relation.setHeadFinder(headFinder);
    TregexPattern pattern = TregexParser.parse(tregex);
    pattern.setPatternString(tregex);
    return pattern;
  }
}
