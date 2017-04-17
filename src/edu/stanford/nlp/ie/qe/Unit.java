package edu.stanford.nlp.ie.qe;

/**
 * Quantifiable entity unit.
 *
 * @author Angel Chang
 */
public class Unit {

  protected String name;
  protected String symbol;
  protected String type;
  protected String system;
  protected String prefixSystem;

  // What unit should be used to express this unit
  protected Unit defaultUnit;
  protected double defaultUnitScale = 1.0;

  public Unit(String name, String symbol, String type) {
    this.name = name;
    this.symbol = symbol;
    this.type = type;
  }

  public Unit(String name, String symbol, String type, Unit defaultUnit, double defaultUnitScale) {
    this.name = name;
    this.symbol = symbol;
    this.type = type;
    this.defaultUnit = defaultUnit;
    this.defaultUnitScale = defaultUnitScale;
  }

  // TODO: unit specific formatting
  public String format(double amount) {
    return String.valueOf(amount) + symbol;
  }

  public String formatInDefaultUnit(double amount) {
    if (defaultUnit != null && defaultUnit != this) {
      return defaultUnit.formatInDefaultUnit(amount*defaultUnitScale);
    } else {
      return format(amount);
    }
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getSymbol() {
    return symbol;
  }

  public void setSymbol(String symbol) {
    this.symbol = symbol;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public Unit getDefaultUnit() {
    return defaultUnit;
  }

  public void setDefaultUnit(Unit defaultUnit) {
    this.defaultUnit = defaultUnit;
  }

  public double getDefaultUnitScale() {
    return defaultUnitScale;
  }

  public void setDefaultUnitScale(double defaultUnitScale) {
    this.defaultUnitScale = defaultUnitScale;
  }
}
