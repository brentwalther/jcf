package net.brentwalther.jcf.model.importer;

import com.google.common.base.Splitter;
import net.brentwalther.jcf.model.JcfModel.Account;
import net.brentwalther.jcf.model.JcfModel.Split;
import net.brentwalther.jcf.model.JcfModel.Transaction;
import net.brentwalther.jcf.model.Model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TsvTransactionDescAccountMappingImporter {

  private static final Splitter TSV_SPLITTER = Splitter.on('\t');

  public Model importFrom(Iterable<String> lineIterator) {
    Map<String, Account> accountsById = new HashMap<>();
    List<Transaction> transactions = new ArrayList<>();
    List<Split> splits = new ArrayList<>();
    int id = 0;
    for (String line : lineIterator) {
      List<String> pieces = TSV_SPLITTER.splitToList(line);
      if (pieces.size() != 2) {
        System.err.println(
            "Mappings file has a bad line. Skipping it. Expected a 2-value TSV line but saw: "
                + line);
        continue;
      }
      String payee = pieces.get(0);
      String account = pieces.get(1);

      // TODO: Make ledger dump the account type to let us fill it in here.
      accountsById.putIfAbsent(
          account, Account.newBuilder().setId(account).setName(account).build());
      Transaction transaction =
          Transaction.newBuilder()
              .setId(String.valueOf(id++))
              .setPostDateEpochSecond(Instant.now().getEpochSecond())
              .setDescription(payee)
              .build();
      transactions.add(transaction);
      splits.add(
          Split.newBuilder()
              .setAccountId(account)
              .setTransactionId(transaction.getId())
              .setValueNumerator(0)
              .setValueDenominator(1)
              .build());
    }
    return new Model(accountsById.values(), transactions, splits);
  }
}
