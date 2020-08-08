package net.brentwalther.jcf.model;

import com.google.common.base.Objects;
import net.brentwalther.jcf.model.JcfModel.Account.Type;

/** An account that can be assigned to transaction splits. */
public class Account {
  public final String id;
  public final String name;
  public final Type type;
  public final String parentId;

  public Account(String id, String name, Type type, String parentId) {
    this.id = id;
    this.name = name;
    this.type = type;
    this.parentId = parentId;
  }

  public String getId() {
    return id;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id, name, type, parentId);
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof Account)) {
      return false;
    }
    Account that = (Account) o;
    return Objects.equal(id, that.id)
        && Objects.equal(name, that.name)
        && Objects.equal(type, that.type)
        && Objects.equal(parentId, that.parentId);
  }

  @Override
  public String toString() {
    return name + "(t: " + type + ", id: " + id + ")";
  }
}
