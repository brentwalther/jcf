package net.brentwalther.jcf.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import net.brentwalther.jcf.model.JcfModel.Split;

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
