package net.brentwalther.jcf.model;

public enum FileType {
  /**
   * A TSV (tab separated value) file with lines of format:
   *
   * <p>"Transaction Description\tAccount Name\n".
   */
  TSV_TRANSACTION_DESCRIPTION_TO_ACCOUNT_NAME_MAPPING,
  /**
   * A ledger-compatible account listing file of format:
   *
   * <p>"account [account_name]\n"
   */
  LEDGER_ACCOUNT_LISTING,
  LEDGER_CLI,
}
