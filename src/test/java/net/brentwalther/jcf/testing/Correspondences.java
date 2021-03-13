package net.brentwalther.jcf.testing;

import com.google.common.truth.Correspondence;
import java.math.BigDecimal;
import net.brentwalther.jcf.model.JcfModel.Account;
import net.brentwalther.jcf.model.JcfModel.Split;
import net.brentwalther.jcf.model.JcfModel.Transaction;
import net.brentwalther.jcf.model.ModelTransforms;

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
          "is a BigDecimal amount that compares equally");

  public static final Correspondence<Split, String> SPLIT_WITH_ACCOUNT_ID_CORRESPONDENCE =
      Correspondence.transforming(Split::getAccountId, "is a split with account ID");

  public static final Correspondence<Split, BigDecimal>
      SPLIT_WITH_BIGDECIMAL_AMOUNT_COMPARETO_CORRESPONDENCE =
          Correspondence.from(
              (split, bigDecimal) ->
                  split != null
                      && bigDecimal != null
                      && BIGDECIMAL_COMPARETO_CORRESPONDENCE.compare(
                          ModelTransforms.bigDecimalAmountForSplit(split), bigDecimal),
              "is a split with a BigDecimal amount that compares equally to");

  private Correspondences() {
    /* do not instantiate. */
  }
}
