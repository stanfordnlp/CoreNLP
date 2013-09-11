package edu.stanford.nlp.ie.ner;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;

public abstract class OverlapResolver {

  public OverlapResolver(String filename) {

    resolveSentences(readAnswers(filename));

  }

  public void resolveSentences(LinkedList<Sentence<Answer>> l) {
    while ( ! l.isEmpty()) {
      Sentence<Answer> s = resolveSentence(l.removeFirst());
      System.out.println(s);
    }
    System.err.println(numContains + " containments found.");
  }

  public abstract List<Answer> resolveContains(Answer a, Answer b);

  public abstract List<Answer> resolveOverlaps(Answer a, Answer b);

  int numContains = 0;

  public Sentence<Answer> resolveSentence(Sentence<Answer> s) {

    if (s.size() < 2) {
      return s;
    }

    Sentence<Answer> newSentence = new Sentence<Answer>();
    boolean found = false;
    while ( ! s.isEmpty()) {
      Answer a = s.removeFirst();
      found = false;
      for (int i = 0; i < s.size(); i++) {
        Answer b = s.get(i);
        if (contains(a, b)) {
          numContains++;
          System.err.println(a + "\n" + b + "\n");
          List<Answer> c = resolveContains(a, b);
          s.remove(i--);
          for (int j = 0; j < c.size(); j++) {
            newSentence.add(c.get(j));
          }
          found = true;
          break;
        } /* else if (overlap(a,b)) {
        List c = resolveOverlaps(a,b);
        s.remove(i--);
        for (int j=0; j<c.size(); j++)
      newSentence.add(c.get(j));
        found = true;
        break;
        } */
      }
      if (!found) {
        newSentence.add(a);
      }
    }
    return newSentence;
  }

  public static boolean contains(Answer a, Answer b) {
    if (a.start <= b.start && a.end >= b.end) {
      return true;
    }
    if (a.start >= b.start && a.end <= b.end) {
      return true;
    }
    return false;
  }

  public static boolean overlap(Answer a, Answer b) {
    return ((a.start <= b.start && a.end >= b.start && a.end <= b.end) || (b.start <= a.start && b.end >= a.start && b.end <= a.start));
  }

  public LinkedList<Sentence<Answer>> readAnswers(String filename) {

    try {

      BufferedReader in = new BufferedReader(new FileReader(filename));
      String line;
      LinkedList<Sentence<Answer>> sentences = new LinkedList<Sentence<Answer>>();
      Sentence<Answer> sent = null;
      String prevID = "";
      Answer a;
      while ((line = in.readLine()) != null) {
        a = readAnswer(line);
        if (!a.id.equals(prevID)) {
          if (sent != null) {
            sentences.add(sent);
          }
          sent = new Sentence<Answer>();
          prevID = a.id;
        }
        sent.add(a);
      }
      return sentences;

    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }


  private static class Sentence<T> extends LinkedList<T> {
    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < size(); i++) {
        sb.append(get(i));
        if (i != size() - 1) {
          sb.append("\n");
        }
      }
      return sb.toString();
    }

    private static final long serialVersionUID = 6660186431624905803L;
  }


  public static Answer readAnswer(String line) {
    Answer a = new Answer();
    StringTokenizer st = new StringTokenizer(line, "|");
    a.id = st.nextToken();
    String[] s = st.nextToken().split(" ");
    a.start = Integer.parseInt(s[0]);
    a.end = Integer.parseInt(s[1]);
    a.ans = st.nextToken().split(" ");
    return a;
  }


  static class Answer {

    int start;
    int end;
    String id;
    String[] ans;

    public String phrase() {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < ans.length; i++) {
        sb.append(ans[i]);
        sb.append(" ");
      }
      return sb.toString().trim();
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder(id + "|" + start + " " + end + "|");
      for (int i = 0; i < ans.length; i++) {
        sb.append(ans[i]);
        sb.append(" ");
      }
      return sb.toString().trim();
    }

  }

}
