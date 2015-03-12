package edu.stanford.nlp.ie.qe;

/**
 * Quantifiable entity
 *
 * @author Angel Chang
 */
public class SimpleQuantifiableEntity {
  private double amount;
  private Unit unit;

  public SimpleQuantifiableEntity(double amount, Unit unit) {
    this.unit = unit;
    this.amount = amount;
  }

  public SimpleQuantifiableEntity(Number amount, Unit unit) {
    this.unit = unit;
    this.amount = amount.doubleValue();
  }

  public double getAmount() {
    return amount;
  }

  public void setAmount(double amount) {
    this.amount = amount;
  }

  public Unit getUnit() {
    return unit;
  }

  public void setUnit(Unit unit) {
    this.unit = unit;
  }

  @Override
  public String toString() {
    return unit.formatInDefaultUnit(amount);
  }
}
