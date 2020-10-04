package net.brentwalther.jcf.model;

import net.brentwalther.jcf.model.JcfModel.Split;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class ModelTransforms {
  public static BigDecimal bigDecimalAmountForSplit(Split split) {
    BigDecimal denominator = new BigDecimal(split.getValueDenominator());
    BigDecimal numerator = new BigDecimal(split.getValueNumerator());
    return numerator
        .setScale(50, RoundingMode.UNNECESSARY)
        .divide(denominator, RoundingMode.HALF_UP)
        .stripTrailingZeros();
  }
}
