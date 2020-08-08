package net.brentwalther.jcf.model;

import com.google.common.base.Objects;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class Transaction {

  public final DataSource dataSource;
  public final String id;
  public final Instant postDate;
  public final String description;

  public Transaction(DataSource dataSource, String id, Instant postDate, String description) {
    this.dataSource = dataSource;
    this.id = id;
    this.postDate = postDate;
    this.description = description;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id, postDate, description);
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof Transaction)) {
      return false;
    }
    Transaction that = (Transaction) o;
    return Objects.equal(id, that.id)
        && Objects.equal(postDate, that.postDate)
        && Objects.equal(description, that.description);
  }

  @Override
  public String toString() {
    return postDate.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_DATE)
        + " "
        + description
        + " (id: "
        + id
        + ")";
  }

  /** The data source of this transaction. */
  public enum DataSource {
    GNUCASH_DB,
    OFX,
    TRANSACTION_DESC_ACCOUNT_NAME_MAPPING_FILE,
    CSV,
    JCF
  }
}
