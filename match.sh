#!/bin/sh

csv_file="$2.csv"
ledger_file="$2.ledger"
maste_ledger_file=$1

bazel run //:csv_matcher -- --master_ledger "$master_ledger_file" --transaction_csv "$csv_file" --output "$ledger_file"

