package edu.stanford.nlp.ie.qe;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.tokensregex.Env;
import edu.stanford.nlp.util.ErasureUtils;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.logging.Redwood;

import java.io.BufferedReader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * List of units
 *
 * @author Angel Chang
 */
public class Units {
  public static void registerDerivedUnit(Env env, Class clazz, String derivedType, String suffix, String symbolSuffix) {
    Field[] fields = clazz.getDeclaredFields();
    for (Field field:fields) {
      boolean isStatic = Modifier.isStatic(field.getModifiers());
      boolean isUnit = Unit.class.isAssignableFrom(field.getType());
      if (isStatic && isUnit) {
        try {
          Unit unit = ErasureUtils.uncheckedCast(field.get(null));
          registerDerivedUnit(env, unit, derivedType, suffix, symbolSuffix);
        } catch (IllegalAccessException ex) {
        }
      }
    }
  }

  public static void registerDerivedUnit(Env env, Unit unit, String derivedType, String suffix, String symbolSuffix) {
    Unit derivedUnit = new Unit(unit.getName() + " " + suffix, unit.getSymbol() + symbolSuffix, derivedType);
    env.bind(derivedType + "_" + unit.getName().toUpperCase() + "_" + suffix.toUpperCase(), derivedUnit);
  }

  public static void registerUnit(Env env, Class clazz) {
    Field[] fields = clazz.getDeclaredFields();
    for (Field field:fields) {
      boolean isStatic = Modifier.isStatic(field.getModifiers());
      boolean isUnit = Unit.class.isAssignableFrom(field.getType());
      if (isStatic && isUnit) {
        try {
          Unit unit = ErasureUtils.uncheckedCast(field.get(null));
          registerUnit(env, unit);
        } catch (IllegalAccessException ex) {
        }
      }
    }
  }

  public static void registerUnit(Env env, Unit unit) {
    env.bind((unit.getType() + "_" + unit.getName()).toUpperCase(), unit);
  }

  public static class MoneyUnit extends Unit {
    public static final String TYPE = "MONEY";

    public MoneyUnit(String name, String symbol) {
      super(name, symbol, TYPE);
    }

    public MoneyUnit(String name, String symbol, Unit defaultUnit, double defaultUnitScale) {
      super(name, symbol, TYPE, defaultUnit, defaultUnitScale);
    }

    public String format(double amount) {
      // Format to 2 decimal places
      return symbol + String.format("%.2f", amount);
    }
  }

  public static class Currencies {
    public static final Unit DOLLAR = new MoneyUnit("dollar", "$");
    public static final Unit CENT = new MoneyUnit("cent", "¢", DOLLAR, 0.01);
    public static final Unit POUND = new MoneyUnit("pound", "\u00A3");
    public static final Unit PENNY = new MoneyUnit("penny", "¢", DOLLAR, 0.01);
    public static final Unit EURO = new MoneyUnit("euro", "\u00AC");
    public static final Unit YEN = new MoneyUnit("yen", "\u00A5");
    public static final Unit YUAN = new MoneyUnit("yuan", "\u5143");
    public static final Unit WON = new MoneyUnit("won", "\u20A9");

    private Currencies() {} // constant holder class
  }

  public static void registerUnits(Env env, String filename) throws IOException {
    List<Unit> units = loadUnits(filename);
    registerUnits(env, units);
    registerUnit(env, Currencies.class);
  }

  public static void registerUnits(Env env, List<Unit> units) {
    for (Unit unit: units) {
      registerUnit(env, unit);
      if ("LENGTH".equals(unit.getType())) {
        registerDerivedUnit(env, unit, "AREA", "2", "2");
        registerDerivedUnit(env, unit, "VOLUME", "3", "3");
      }
    }
  }

  public static List<Unit> loadUnits(String filename) throws IOException {
    Pattern commaPattern = Pattern.compile("\\s*,\\s*");
    BufferedReader br = IOUtils.readerFromString(filename);
    String headerString = br.readLine();
    String[] header = commaPattern.split(headerString);
    Map<String,Integer> headerIndex = new HashMap<>();
    for (int i = 0; i < header.length; i++) {
      headerIndex.put(header[i], i);
    }
    int iName = headerIndex.get("unit");
    int iPrefix = headerIndex.get("prefix");
    int iSymbol = headerIndex.get("symbol");
    int iType = headerIndex.get("type");
    int iSystem = headerIndex.get("system");
    int iDefaultUnit = headerIndex.get("defaultUnit");
    int iDefaultUnitScale = headerIndex.get("defaultUnitScale");
    String line;
    List<Unit> list = new ArrayList<>();
    Map<String,Unit> unitsByName = new HashMap<>();
    Map<String,Pair<String,Double>> unitToDefaultUnits = new HashMap<>();
    while ((line = br.readLine()) != null) {
      String[] fields = commaPattern.split(line);
      Unit unit = new Unit(fields[iName], fields[iSymbol], fields[iType].toUpperCase());
      unit.system = fields[iSystem];
      if (fields.length > iPrefix) {
        unit.prefixSystem = fields[iPrefix];
      }
      if (fields.length > iDefaultUnit) {
        double scale = 1.0;
        if (fields.length > iDefaultUnitScale) {
          scale = Double.parseDouble(fields[iDefaultUnitScale]);
        }
        unitToDefaultUnits.put(unit.getName(), Pair.makePair(fields[iDefaultUnit], scale));
      }
      unitsByName.put(unit.getName(), unit);
      list.add(unit);
    }
    for (Map.Entry<String, Pair<String,Double>> entry: unitToDefaultUnits.entrySet()) {
      Unit unit = unitsByName.get(entry.getKey());
      Unit defaultUnit = unitsByName.get(entry.getValue().first);
      if (defaultUnit != null) {
        unit.defaultUnit = defaultUnit;
        unit.defaultUnitScale = entry.getValue().second;
      } else {
        Redwood.Util.warn("Unknown default unit " + entry.getValue().first + " for " + entry.getKey());
      }
    }
    br.close();
    return list;
  }

}
