package net.brentwalther.jcf.util;

import net.brentwalther.jcf.model.JcfModel.Split;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class ModelUtil {
  public static BigDecimal toBigDecimal(Split split) {
    return new BigDecimal(split.getValueNumerator())
        .setScale(2)
        .divide(new BigDecimal(split.getValueDenominator()), RoundingMode.UNNECESSARY);
  }
}
