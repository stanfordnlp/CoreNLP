package edu.stanford.nlp.ie.qe;

import edu.stanford.nlp.ling.tokensregex.Env;
import edu.stanford.nlp.util.ErasureUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * List of units
 *
 * @author Angel Chang
 */
public class Units {
  public static void generateUnitMappings(Env env) {
    // TODO: Read table of mappings to units
    // TODO: Register mappings with tokensregex
  }

  public static void registerUnits(Env env) {
    registerUnit(env, Currencies.class);
    registerUnit(env, Lengths.class);
    registerUnit(env, Areas.class);
    registerDerivedUnit(env, Lengths.class, Areas.TYPE, "SQ", "2");
    registerUnit(env, Volumes.class);
    registerDerivedUnit(env, Lengths.class, Volumes.TYPE, "CB", "3");
    registerUnit(env, Weights.class);
    registerUnit(env, Temperatures.class);
  }

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
    env.bind(unit.getType() + "_" + unit.getName().toUpperCase(), unit);
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
    public static Unit DOLLAR = new MoneyUnit("dollar", "$");
    public static Unit CENT = new MoneyUnit("cent", "¢", DOLLAR, 0.01);
    public static Unit POUND = new MoneyUnit("pound", "\u00A3");
    public static Unit PENNY = new MoneyUnit("penny", "¢", DOLLAR, 0.01);
    public static Unit EURO = new MoneyUnit("euro", "\u00AC");
    public static Unit YEN = new MoneyUnit("yen", "\u00A5");
    public static Unit YUAN = new MoneyUnit("yuan", "\u5143");
    public static Unit WON = new MoneyUnit("won", "\u20A9");
  }

  public static class Areas {
    public static final String TYPE = "AREA";
    public static Unit ACRE = new Unit("acre", "acre", TYPE);
  }

  public static class Volumes {
    public static final String TYPE = "VOLUME";
    public static Unit LITRE = new Unit("litre", "l", TYPE);
    public static Unit TEASPOON = new Unit("teaspoon", "tsp", TYPE);
    public static Unit TABLESPOON = new Unit("tablespoon", "Tbsp", TYPE);
  }

  public static class Lengths {
    public static final String TYPE = "LENGTH";
    public static Unit METER = new Unit("meter", "m", TYPE);
    public static Unit MILE = new Unit("mile", "mi", TYPE);
    public static Unit FOOT = new Unit("foot", "'", TYPE);
    public static Unit INCH = new Unit("inch", "''", TYPE);
    public static Unit YARD = new Unit("yard", "y", TYPE);
  }

  public static class Weights {
    public static final String TYPE = "WEIGHT";
    public static Unit POUND = new Unit("pound", "lb", TYPE);
    public static Unit OUNCE = new Unit("ounce", "oz", TYPE);
    public static Unit GRAM = new Unit("gram", "g", TYPE);
  }

  public static class Temperatures {
    public static final String TYPE = "TEMPERATURE";
    public static Unit CELSIUS = new Unit("celsius", "°C", TYPE);
    public static Unit FAHRENHEIT = new Unit("fahrenheit", "°F", TYPE);
    public static Unit KELVIN = new Unit("kelvin", "K", TYPE);
  }

}
