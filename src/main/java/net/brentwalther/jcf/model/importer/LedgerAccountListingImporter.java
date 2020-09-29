package net.brentwalther.jcf.model.importer;

import com.google.common.collect.ImmutableList;
import net.brentwalther.jcf.model.JcfModel;
import net.brentwalther.jcf.model.JcfModel.Account;
import net.brentwalther.jcf.model.ModelGenerator;

public class LedgerAccountListingImporter implements JcfModelImporter {

  private static final String ACCOUNT_NAME_PREFIX = "account ";

  private final ImmutableList<String> ledgerAccountListings;

  private LedgerAccountListingImporter(ImmutableList<String> ledgerAccountListings) {
    this.ledgerAccountListings = ledgerAccountListings;
  }

  public static LedgerAccountListingImporter create(ImmutableList<String> ledgerAccountListings) {
    return new LedgerAccountListingImporter(ledgerAccountListings);
  }

  public JcfModel.Model get() {
    ImmutableList.Builder<Account> accountListBuilder =
        ImmutableList.builderWithExpectedSize(ledgerAccountListings.size());
    for (String ledgerAccountListing : ledgerAccountListings) {
      if (!ledgerAccountListing.startsWith(ACCOUNT_NAME_PREFIX)) {
        System.err.println("Skipping improperly formatted line: " + ledgerAccountListing);
        continue;
      }
      String accountName = ledgerAccountListing.substring(ACCOUNT_NAME_PREFIX.length()).trim();
      // TODO: Output the account type in ledger to be able to import it here.
      accountListBuilder.add(
          Account.newBuilder()
              .setId(accountName)
              .setName(accountName)
              .setType(Account.Type.UNKNOWN_TYPE)
              .build());
    }
    return ModelGenerator.createForAccounts(accountListBuilder.build());
  }
}
