# jcf - Java Cash Flow

A CLI program for matching and converting bank transaction CSVs to `ledger-cli` compatible files.

This is part of an overall workflow for maintaining a personal finance ledger using `git` and this tool, as described in the walkthrough here: https://brentwalther.net/personal-finance-automation-with-ledger

You need `bazel` installed to build and run it. To see an invocation example, check `match.sh`. It can also run as an application (TUI) but some features are unimplemented there; see `BUILD` file. Not tested on Windows or Mac.

