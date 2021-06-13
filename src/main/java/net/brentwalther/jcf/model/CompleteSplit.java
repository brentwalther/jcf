package net.brentwalther.jcf.model;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.auto.value.AutoValue;
import net.brentwalther.jcf.model.JcfModel.Account;
import net.brentwalther.jcf.model.JcfModel.Split;
import net.brentwalther.jcf.model.JcfModel.Transaction;

@AutoValue
public abstract class CompleteSplit {
  public static CompleteSplit create(Split split, Account account, Transaction transaction) {
    checkNotNull(split);
    checkNotNull(account);
    checkNotNull(transaction);
    checkState(
        !split.getAccountId().isEmpty()
            && !split.getTransactionId().isEmpty()
            && split.getAccountId().equals(account.getId())
            && split.getTransactionId().equals(transaction.getId()));
    return new AutoValue_CompleteSplit(split, account, transaction);
  }

  public abstract Split split();

  public abstract Account account();

  public abstract Transaction transaction();
}
