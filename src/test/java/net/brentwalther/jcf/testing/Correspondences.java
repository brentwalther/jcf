package net.brentwalther.jcf.testing;

import com.google.common.truth.Correspondence;
import net.brentwalther.jcf.model.JcfModel.Account;
import net.brentwalther.jcf.model.JcfModel.Transaction;

import java.math.BigDecimal;

/** Contains various correspondences to use with Google Truth assertThat() assertions. */
public class Correspondences {

  public static final Correspondence<Account, String> ACCOUNT_NAME_CORRESPONDENCE =
      Correspondence.transforming(Account::getName, "is an account with name equal to");

  public static final Correspondence<Transaction, String> TRANSACTION_DESCRIPTION_CORRESPONDENCE =
      Correspondence.transforming(
          Transaction::getDescription, "is a transaction with description equal to");

  public static final Correspondence<BigDecimal, BigDecimal> BIGDECIMAL_COMPARETO_CORRESPONDENCE =
      Correspondence.from(
          (first, second) -> first != null && second != null && first.compareTo(second) == 0,
          "is a big decimal amount that compares equally");
}
