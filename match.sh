#!/bin/sh

csv_file="$3.csv"
ledger_file="$3.ledger"
payee_transaction_mapping_file=$1
ledger_account_listing=$2

bazel run //:csv_matcher -- --tsv_desc_account_mapping $payee_transaction_mapping_file --ledger_account_listing $ledger_account_listing --transaction_csv $csv_file --output $ledger_file

