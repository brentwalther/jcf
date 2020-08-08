#!/bin/sh

csv_file="$1.csv"
ledger_file="$1.ledger"
account_name=$2
payee_transaction_mapping_file=$3

bazel run //:csv_matcher -- --tsv_desc_account_mapping $payee_transaction_mapping_file --transaction_csv $csv_file --output $ledger_file --account_name "$account_name"

