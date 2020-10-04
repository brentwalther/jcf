package net.brentwalther.jcf.model;

import net.brentwalther.jcf.model.JcfModel.Split;

import java.math.BigDecimal;
import java.util.stream.Stream;

public class ModelValidations {
  public static boolean areSplitsBalanced(Stream<Split> splitStream) {
    return splitStream
            .map(ModelTransforms::bigDecimalAmountForSplit)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .compareTo(BigDecimal.ZERO)
        == 0;
  }
}
