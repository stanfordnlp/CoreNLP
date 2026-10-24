package edu.stanford.nlp.semgraph.semgrex.ssurgeon;

import edu.stanford.nlp.international.Language;
import edu.stanford.nlp.semgraph.semgrex.SemgrexMatch;
import edu.stanford.nlp.trees.GrammaticalRelation;

/**
 * The {@code -reln} of an Ssurgeon edit, which may refer to the
 * variables of the match, such as {@code ${etype}:pass}.
 *<br>
 * A relation with no {@code ${...}} is looked up once, when the edit is
 * read.  One with them is looked up each time the edit is applied, in
 * the language the edit was read in, as {@link SsurgeonTemplate}
 * describes.
 */
final class SsurgeonRelation {
  /** The relation, or null if it depends on the match */
  private final GrammaticalRelation fixed;
  /** The relation as written, or null if it was given as a GrammaticalRelation */
  private final SsurgeonTemplate template;
  /** The language to look the relation up in */
  private final Language language;

  /**
   * A relation which does not depend on the match.
   *
   * @param relation the relation
   */
  SsurgeonRelation(GrammaticalRelation relation) {
    this.fixed = relation;
    this.template = null;
    this.language = relation.getLanguage();
  }

  private SsurgeonRelation(SsurgeonTemplate template, Language language) {
    this.template = template;
    this.language = language;
    this.fixed = template.hasVariables() ? null : GrammaticalRelation.valueOf(language, template.literal());
  }

  /**
   * Reads the {@code -reln} of an edit.
   *
   * @param text the relation as written, or null
   * @param language the language to look the relation up in
   * @return the relation, or null if {@code text} is null
   * @throws SsurgeonParseException if a {@code ${} has no closing brace, or names no variable
   */
  static SsurgeonRelation parse(String text, Language language) {
    if (text == null) {
      return null;
    }
    return new SsurgeonRelation(new SsurgeonTemplate(text), language);
  }

  /**
   * A relation which does not depend on the match, or null.
   *
   * @param relation the relation, or null
   * @return the relation, or null if {@code relation} is null
   */
  static SsurgeonRelation of(GrammaticalRelation relation) {
    return relation == null ? null : new SsurgeonRelation(relation);
  }

  /**
   * The relation in the current match.
   *
   * @param sm the match, with its variables
   * @return the relation, or null if it refers to a variable which is not set
   */
  GrammaticalRelation get(SemgrexMatch sm) {
    if (fixed != null) {
      return fixed;
    }
    String name = template.expand(sm);
    return name == null ? null : GrammaticalRelation.valueOf(language, name);
  }

  /**
   * The relation as an edit would write it, which reads back as the same relation.
   */
  @Override
  public String toString() {
    if (template != null) {
      return template.text;
    }
    // a $ followed by { or $ would otherwise be read as a variable or an escape
    return fixed.toString().replace("$", "$$");
  }
}
