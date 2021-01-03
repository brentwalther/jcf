package net.brentwalther.jcf.model.importer;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import net.brentwalther.jcf.model.JcfModel;
import net.brentwalther.jcf.model.JcfModel.Account;
import net.brentwalther.jcf.model.JcfModel.Split;
import net.brentwalther.jcf.model.JcfModel.Transaction;
import net.brentwalther.jcf.model.ModelGenerators;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TsvTransactionDescAccountMappingImporter implements JcfModelImporter {

  private static final Splitter TSV_SPLITTER = Splitter.on('\t');

  private final ImmutableList<String> tsvLines;

  private TsvTransactionDescAccountMappingImporter(List<String> tsvLines) {
    this.tsvLines = ImmutableList.copyOf(tsvLines);
  }

  public static TsvTransactionDescAccountMappingImporter create(List<String> tsvLines) {
    return new TsvTransactionDescAccountMappingImporter(tsvLines);
  }

  public JcfModel.Model get() {
    Map<String, Account> accountsById = new HashMap<>();
    List<Transaction> transactions = new ArrayList<>();
    List<Split> splits = new ArrayList<>();
    int id = 0;
    for (String line : tsvLines) {
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
    return ModelGenerators.create(accountsById.values(), transactions, splits);
  }
}
