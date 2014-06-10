//TaggerFeatures -- StanfordMaxEnt, A Maximum Entropy Toolkit
//Copyright (c) 2002-2008 Leland Stanford Junior University


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
//    Support/Questions: java-nlp-user@lists.stanford.edu
//    Licensing: java-nlp-support@lists.stanford.edu
//http://www-nlp.stanford.edu/software/tagger.shtml
package edu.stanford.nlp.tagger.maxent;

import edu.stanford.nlp.io.InDataStreamFile;
import edu.stanford.nlp.io.OutDataStreamFile;
import edu.stanford.nlp.maxent.Features;

/**
 * This class contains POS tagger specific features.
 *
 * @author Kristina Toutanova
 * @version 1.0
 */
public class TaggerFeatures extends Features {

  int[] xIndexed;

  final TTags ttags;
  final TaggerExperiments domain;

  TaggerFeatures(TTags ttags, TaggerExperiments domain) {
    super();
    this.ttags = ttags;
    this.domain = domain;
  }

  @Override
  public void save(String filename) {
    try {
      OutDataStreamFile rF = new OutDataStreamFile(filename);
      rF.writeInt(xIndexed.length);
      for (int aXIndexed : xIndexed) {
        rF.writeInt(aXIndexed);
      }
      rF.writeInt(size());
      for (int i = 0; i < size(); i++) {
        get(i).save(rF);
      }
      rF.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Override
  public void read(String filename) {
    try {
      InDataStreamFile rF = new InDataStreamFile(filename);
      int len = rF.readInt();
      xIndexed = new int[len];
      for (int i = 0; i < xIndexed.length; i++) {
        xIndexed[i] = rF.readInt();
      }
      int numFeats = rF.readInt();
      for (int i = 0; i < numFeats; i++) {
        TaggerFeature tF = new TaggerFeature(ttags, domain);
        tF.read(rF);
        this.add(tF);
      }
      rF.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

}
