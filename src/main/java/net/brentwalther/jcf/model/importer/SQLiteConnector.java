package net.brentwalther.jcf.model.importer;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;
import net.brentwalther.jcf.model.Account;
import net.brentwalther.jcf.model.Model;
import net.brentwalther.jcf.model.Split;
import net.brentwalther.jcf.model.Transaction;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

public class SQLiteConnector {

  private final File sqliteDatabase;

  public SQLiteConnector(File sqliteDatabase) {
    this.sqliteDatabase = sqliteDatabase;
  }

  public Model extract() {
    Connection connection = null;
    try {
      // create a database connection
      connection =
          DriverManager.getConnection("jdbc:sqlite:" + this.sqliteDatabase.getAbsolutePath());
      Statement statement = connection.createStatement();
      statement.setQueryTimeout(30); // set timeout account 30 sec.

      ResultSet accountResults = statement.executeQuery("select * from accounts");
      SQLiteResultSetMatcher accountMatcher =
          new SQLiteResultSetMatcher(
              ImmutableList.of(
                  Field.GUID, Field.NAME, Field.TYPE, Field.PARENT, Field.DESCRIPTION));
      Map<String, Account> accountsById = new HashMap<>();
      if (accountMatcher.matches(accountResults.getMetaData())) {
        for (ImmutableMap<Field, String> result : accountMatcher.getResults(accountResults)) {
          Account account =
              new Account(
                  result.get(Field.GUID),
                  result.get(Field.NAME),
                  toType(result.get(Field.TYPE)),
                  result.get(Field.PARENT));
          accountsById.put(result.get(Field.GUID), account);
        }
      } else {
        System.err.println("Could not initialize accounts. Matcher did not match.");
      }

      ResultSet splitsResults = statement.executeQuery("select * from splits");
      SQLiteResultSetMatcher splitsMatcher =
          new SQLiteResultSetMatcher(
              ImmutableList.of(
                  Field.TRANSACTION_GUID,
                  Field.ACCOUNT_GUID,
                  Field.VALUE_NUMERATOR,
                  Field.VALUE_DENOMINATOR));
      Multimap<String, Split> splitsByTransactionId = ArrayListMultimap.create();
      if (splitsMatcher.matches(splitsResults.getMetaData())) {
        for (ImmutableMap<Field, String> result : splitsMatcher.getResults(splitsResults)) {
          splitsByTransactionId.put(
              result.get(Field.TRANSACTION_GUID),
              new Split(
                  accountsById.get(result.get(Field.ACCOUNT_GUID)).id,
                  result.get(Field.TRANSACTION_GUID),
                  Integer.parseInt(result.get(Field.VALUE_NUMERATOR)),
                  Integer.parseInt(result.get(Field.VALUE_DENOMINATOR))));
        }
      } else {
        System.err.println("Could not initialize splits. Matcher did not match.");
      }

      ResultSet transactionResults = statement.executeQuery("select * from transactions");
      SQLiteResultSetMatcher transactionMatcher =
          new SQLiteResultSetMatcher(
              ImmutableList.of(
                  Field.GUID, Field.CURRENCY_GUID, Field.POST_DATE, Field.DESCRIPTION));
      Map<String, Transaction> transactionsById = new HashMap<>();
      if (transactionMatcher.matches(transactionResults.getMetaData())) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        for (ImmutableMap<Field, String> result :
            transactionMatcher.getResults(transactionResults)) {
          Instant postDate =
              Instant.from(
                  ZonedDateTime.of(
                      LocalDateTime.parse(result.get(Field.POST_DATE), formatter),
                      ZoneId.systemDefault()));
          String transactionGuid = result.get(Field.GUID);
          transactionsById.put(
              transactionGuid,
              new Transaction(
                  Transaction.DataSource.GNUCASH_DB,
                  transactionGuid,
                  postDate,
                  result.get(Field.DESCRIPTION)));
        }
      } else {
        System.err.println("Could not initialize transactions. Matcher did not match.");
      }

      return new Model(accountsById, transactionsById, splitsByTransactionId);

    } catch (SQLException e) {
      // if the error message is "out of memory",
      // it probably means no database file is found
      System.err.println(e.getMessage());
      return Model.empty();
    } finally {
      try {
        if (connection != null) {
          connection.close();
        }
      } catch (SQLException e) {
        // connection close failed.
        System.err.println(e.getMessage());
        return Model.empty();
      }
    }
  }

  private Account.Type toType(String dbType) {
    if (dbType.equals("INCOME")) {
      return Account.Type.INCOME;
    } else if (dbType.equals("EXPENSE")) {
      return Account.Type.EXPENSE;
    } else if (dbType.equals("ASSET") || dbType.equals("BANK") || dbType.equals("CASH")) {
      return Account.Type.ASSET;
    } else if (dbType.equals("LIABILITY") || dbType.equals("CREDIT")) {
      return Account.Type.LIABILITY;
    } else if (dbType.equals("EQUITY")) {
      return Account.Type.EQUITY;
    } else if (dbType.equals("ROOT")) {
      return Account.Type.ROOT;
    } else {
      throw new RuntimeException("Unknown account type: " + dbType);
    }
  }

  public enum Field {
    GUID("guid"),
    NAME("name"),
    TYPE("account_type"),
    PARENT("parent_guid"),
    DESCRIPTION("description"),
    MNEMONIC("mnemonic"),
    FULL_NAME("fullname"),
    TRANSACTION_GUID("tx_guid"),
    ACCOUNT_GUID("account_guid"),
    VALUE_NUMERATOR("value_num"),
    VALUE_DENOMINATOR("value_denom"),
    POST_DATE("post_date"),
    CURRENCY_GUID("currency_guid");

    private final String columnName;

    Field(String columnName) {
      this.columnName = columnName;
    }

    public String getColumnName() {
      return columnName;
    }
  }
}
