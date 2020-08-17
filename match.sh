#!/bin/sh

csv_file="$1.csv"
ledger_file="$1.ledger"
payee_transaction_mapping_file=$2
ledger_account_listing=$3

bazel run //:csv_matcher -- --tsv_desc_account_mapping $payee_transaction_mapping_file --ledger_account_listing $ledger_account_listing --transaction_csv $csv_file --output $ledger_file

