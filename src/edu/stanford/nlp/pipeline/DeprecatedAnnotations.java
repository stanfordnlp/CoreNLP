package edu.stanford.nlp.pipeline;

import java.util.List;

import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.util.Pair;


public class DeprecatedAnnotations {
  @Deprecated
  public static class ParseKBestPLAnnotation implements CoreAnnotation<ClassicCounter<List<Tree>>> {
    @SuppressWarnings({"unchecked"})
    public Class<ClassicCounter<List<Tree>>> getType() {  return (Class) ClassicCounter.class; } }


  @Deprecated
  public static class CorefPLAnnotation implements CoreAnnotation<String> {
    public Class<String> getType() {  return String.class; } }


  @Deprecated
  public static class DependencyGraphPLAnnotation implements CoreAnnotation<List<SemanticGraph>> {
    @SuppressWarnings({"unchecked"})
    public Class<List<SemanticGraph>> getType() {  return (Class) List.class; } }


  @Deprecated
  public static class UncollapsedDependencyGraphPLAnnotation implements CoreAnnotation<List<SemanticGraph>> {
    @SuppressWarnings({"unchecked"})
    public Class<List<SemanticGraph>> getType() {  return (Class) List.class; } }


  @Deprecated
  public static class SRLPLAnnotation implements CoreAnnotation<List<List<Pair<String, Pair<Integer, Integer>>>>> {
    @SuppressWarnings({"unchecked"})
    public Class<List<List<Pair<String, Pair<Integer, Integer>>>>> getType() {  return (Class) List.class; } }


  @Deprecated
  public static class KBestNERsPLAnnotation implements CoreAnnotation<ClassicCounter<List<List<? extends CoreLabel>>>> {
    @SuppressWarnings({"unchecked"})
    public Class<ClassicCounter<List<List<? extends CoreLabel>>>> getType() {  return (Class) ClassicCounter.class; } }

}
