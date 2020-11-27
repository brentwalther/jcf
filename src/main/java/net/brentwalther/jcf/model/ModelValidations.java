package net.brentwalther.jcf.model;

import com.google.common.collect.FluentIterable;
import net.brentwalther.jcf.model.JcfModel.Split;

import java.math.BigDecimal;

public class ModelValidations {
  public static boolean areSplitsBalanced(Iterable<Split> splits) {
    return FluentIterable.from(splits).transform(ModelTransforms::bigDecimalAmountForSplit).toList()
            .stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .compareTo(BigDecimal.ZERO)
        == 0;
  }
}
