//TaggerFeature -- StanfordMaxEnt, A Maximum Entropy Toolkit
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
import edu.stanford.nlp.maxent.Feature;


/**
 * Holds a Tagger Feature for the loglinear model.
 * Tagger Features are binary valued, and indexed in a particular way.
 *
 * @author Kristina Toutanova
 * @version 1.0
 */
public class TaggerFeature extends Feature {

  private int start;
  private int end;
  private FeatureKey key;
  private int yTag;
  private final TTags ttags;
  private final TaggerExperiments domain;

  protected TaggerFeature(int start, int end, FeatureKey key, 
                          TTags ttags, TaggerExperiments domain) {
    this.start = start;
    this.end = end;
    this.key = key;
    this.ttags = ttags;
    this.domain = domain;
    yTag = ttags.getIndex(key.tag);
  }

  public TaggerFeature(TTags ttags, TaggerExperiments domain) {
    this.ttags = ttags;
    this.domain = domain;
  }


  @Override
  public double getVal(int index) {
    return 1.0;
  }


  @Override
  public int getY(int index) {
    return yTag;
  }


  @Override
  public int len() {
    return end - start + 1;
  }

  @Override
  public int getX(int index) {
    return domain.getTaggerFeatures().xIndexed[start + index];
  }

  public int getYTag() {
    return yTag;
  }


  @Override
  public double getVal(int x, int y) {
    int num = x * domain.ySize + y;
    if (!(getYTag() == y)) {
      return 0;
    }
    for (int i = 0; i < len(); i++) {
      if (getX(i) == num) {
        return 1;
      }
    }
    return 0;
  }


  @Override
  public void save(OutDataStreamFile rF) {
    try {
      rF.writeInt(start);
      rF.writeInt(end);
      key.save(rF);
      rF.writeInt(yTag);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Override
  public void read(InDataStreamFile rF) {
    try {
      start = rF.readInt();
      end = rF.readInt();
      key = new FeatureKey();
      key.read(rF);
      yTag = rF.readInt();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }


  @Override
  public double ftilde() {
    double s = 0.0;
    int y = getYTag();
    for (int example = start; example < end + 1; example++) {
      int x = domain.getTaggerFeatures().xIndexed[example];
      s = s + domain.ptildeXY(x, y);
    }
    return s;
  }

}
