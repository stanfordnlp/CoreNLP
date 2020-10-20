package edu.stanford.nlp.graph;

public class DirectedEdge<V, E> {
  E data;
  V head;
  V tail;

  public DirectedEdge(E data, V head, V tail) {
    this.data = data;
    this.head = head;
    this.tail = tail;
  }

  E getData() { return data; }
  V getHead() { return head; }
  V getTail() { return tail; }
}
