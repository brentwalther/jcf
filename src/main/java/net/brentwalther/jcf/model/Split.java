package net.brentwalther.jcf.model;

import com.google.common.base.Objects;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class Split {
  public final String accountId;
  public final String transactionId;
  public final int valueNumerator;
  public final int valueDenominator;

  public Split(String accountId, String transactionId, int valueNumerator, int valueDenominator) {
    this.accountId = accountId;
    this.transactionId = transactionId;
    this.valueNumerator = valueNumerator;
    this.valueDenominator = valueDenominator;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(accountId, transactionId, valueNumerator, valueDenominator);
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof Split)) {
      return false;
    }
    Split that = (Split) o;
    return Objects.equal(accountId, that.accountId)
        && Objects.equal(transactionId, that.transactionId)
        && Objects.equal(valueNumerator, that.valueNumerator)
        && Objects.equal(valueDenominator, that.valueDenominator);
  }

  @Override
  public String toString() {
    return "" + valueNumerator + " / " + valueDenominator;
  }

  public BigDecimal amount() {
    return new BigDecimal(valueNumerator)
        .setScale(2)
        .divide(new BigDecimal(valueDenominator), RoundingMode.UNNECESSARY);
  }
}
