//
// StanfordCoreNLP -- a suite of NLP tools.
// Copyright (c) 2009-2011 The Board of Trustees of
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
// For more information, bug reports, fixes, contact:
//    Christopher Manning
//    Dept of Computer Science, Gates 1A
//    Stanford CA 94305-9010
//    USA
//

package edu.stanford.nlp.pipeline;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import edu.stanford.nlp.util.StringUtils;

/**
 * Stores and describes a set of requirements for the typical use of
 * the pipeline.  For example, the POS tagger requires that tokenizer
 * and ssplit have run, etc.  Implements a conjunction of disjunctions.
 *
 * @author John Bauer
 */
public class Requirement {

  private final List<List<String>> requirements = new ArrayList<List<String>>();

  public Requirement(String ... disjunctions) {
      for (String disjunction : disjunctions) {
        List<String> requirement = Arrays.asList(disjunction.split("[ |]+"));
        requirements.add(requirement);
      }
    }

  public String getMissingRequirement(Set<String> alreadyAdded) {
    for (List<String> requirement : requirements) {
      boolean found = false;
      for (String annotator : requirement) {
        if (alreadyAdded.contains(annotator)) {
          found = true;
          break;
        }
      }
      if (!found) {
        return StringUtils.join(requirement, "|");
      }
    }
    return null;
  }

}