package edu.stanford.nlp.international.arabic;

import java.io.Serializable;
import java.util.*;
import edu.stanford.nlp.util.Generics;

/**
 * Manually-generated (unvocalized) word lists for different Arabic word categories.
 * 
 * @author Spence Green
 *
 */
public final class ArabicWordLists implements Serializable {

  private static final long serialVersionUID = 2752179429568209320L;

  private ArabicWordLists() {}
  
  public static Set<String> getTemporalNouns() { return Collections.unmodifiableSet(tmpNouns); }
  public static Set<String> getInnaSisters() { return Collections.unmodifiableSet(innaSisters); }
  public static Set<String> getKanSisters() { return Collections.unmodifiableSet(kanSisters); }
  public static Set<String> getDimirMunfasala() { return Collections.unmodifiableSet(dimirMunfasala); }
  public static Set<String> getDimirMutasala() { return Collections.unmodifiableSet(dimirMutasala); }

  private static final Set<String> dimirMunfasala = Generics.newHashSet();
  static {
    dimirMunfasala.add("انا");
    dimirMunfasala.add("هو");
    dimirMunfasala.add("هي");
    dimirMunfasala.add("انت"); //Unvocalized
    dimirMunfasala.add("نحن");
    dimirMunfasala.add("انتم");
    dimirMunfasala.add("انتن");
    dimirMunfasala.add("هما");
    dimirMunfasala.add("هم");
    dimirMunfasala.add("هن");
  }

  private static final Set<String> dimirMutasala = Generics.newHashSet();
  static {
    dimirMutasala.add("ي");
    dimirMutasala.add("ه");
    dimirMutasala.add("ها");
    dimirMutasala.add("ك");
    dimirMutasala.add("كن");
    dimirMutasala.add("كم");
    dimirMutasala.add("نا");
    dimirMutasala.add("هم");
    dimirMutasala.add("هن");
    dimirMutasala.add("هما");
  }
  
  private static final Set<String> innaSisters = Generics.newHashSet();
  static {
    innaSisters.add("ان");
    innaSisters.add("لكن");
    innaSisters.add("لعل");
    innaSisters.add("لان");
  }

  private static final Set<String> kanSisters = Generics.newHashSet();
  static {
    kanSisters.add("كان");
    kanSisters.add("كانت");
    kanSisters.add("كنت");
    kanSisters.add("كانوا");
    kanSisters.add("كن");
  }

  private static final Set<String> tmpNouns = Generics.newHashSet();
  static {
    tmpNouns.add("الان");
    tmpNouns.add("يوم");
    tmpNouns.add("اليوم");
    tmpNouns.add("امس");
    tmpNouns.add("ايام");
    tmpNouns.add("مساء");
    tmpNouns.add("صباحا");
    tmpNouns.add("الصباح");
    tmpNouns.add("الاثنين");
    tmpNouns.add("الأثنين");
    tmpNouns.add("الاحد");
    tmpNouns.add("الأحد");
    tmpNouns.add("الثلاثاء");
    tmpNouns.add("الارباء");
    tmpNouns.add("الخميس");
    tmpNouns.add("الجمعة");
    tmpNouns.add("السبت");
    tmpNouns.add("عام");
    tmpNouns.add("عاما");
    tmpNouns.add("سنة");
    tmpNouns.add("سنوات");
    tmpNouns.add("شهر");
    tmpNouns.add("شهور");
    tmpNouns.add("يناير");
    tmpNouns.add("كانون"); //Only one part of Dec/Jan
    tmpNouns.add("فبراير");
    tmpNouns.add("شباط");
    tmpNouns.add("مارس");
    tmpNouns.add("اذار");
    tmpNouns.add("ابريل");
    tmpNouns.add("نيسان");
    tmpNouns.add("مايو");
    tmpNouns.add("ايار");
    tmpNouns.add("يونيو");
    tmpNouns.add("حزيران");
    tmpNouns.add("يوليو");
    tmpNouns.add("تموز");
    tmpNouns.add("اغسطس");
    tmpNouns.add("اب");
    tmpNouns.add("سبتمبر");
    tmpNouns.add("ايلول");
    tmpNouns.add("اكتوبر");
    tmpNouns.add("تشرين");//Only one part of Oct/Nov
    tmpNouns.add("نوفمبر");
    tmpNouns.add("ديسمبر");
  }
}
