package net.brentwalther.jcf.model.importer;

import com.google.common.base.Splitter;
import net.brentwalther.jcf.model.JcfModel.Account;
import net.brentwalther.jcf.model.Model;
import net.brentwalther.jcf.model.Split;
import net.brentwalther.jcf.model.Transaction;

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
      String transactionId = String.valueOf(id++);

      // TODO: Make ledger dump the account type to let us fill it in here.
      accountsById.putIfAbsent(
          account, Account.newBuilder().setId(account).setName(account).build());
      transactions.add(
          new Transaction(
              Transaction.DataSource.TRANSACTION_DESC_ACCOUNT_NAME_MAPPING_FILE,
              transactionId,
              Instant.now(),
              payee));
      splits.add(new Split(account, transactionId, 0, 1));
    }
    return new Model(accountsById.values(), transactions, splits);
  }
}
