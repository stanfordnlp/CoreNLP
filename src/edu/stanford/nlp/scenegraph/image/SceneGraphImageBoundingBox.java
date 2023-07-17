package edu.stanford.nlp.scenegraph.image;

public class SceneGraphImageBoundingBox {

  public int h;
  public int w;
  public int x;
  public int y;
  

  public SceneGraphImageBoundingBox(int h, int w, int x, int y) {
    this.h = h;
    this.w = w;
    this.x = x;
    this.y = y;
  }

  public boolean equals(Object other) {
    if (!(other instanceof SceneGraphImageBoundingBox)) {
      return false;
    }
    SceneGraphImageBoundingBox box = (SceneGraphImageBoundingBox) other;
    if ((this.h == box.h) && (this.w == box.w) && (this.x == box.x) && (this.y == box.y)) {
      return true;
    }
    return false;
  }
}

