package net.brentwalther.jcf.model.importer;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.brentwalther.jcf.model.importer.SQLiteConnector.Field;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

class SQLiteResultSetMatcher {
  private final ImmutableList<Field> requiredFields;

  SQLiteResultSetMatcher(ImmutableList<Field> requiredFields) {
    this.requiredFields = requiredFields;
  }

  boolean matches(ResultSetMetaData metaData) {
    Map<String, Integer> columnNames;
    try {
      columnNames = getColumnIndicesByName(metaData);
    } catch (SQLException e) {
      return false;
    }
    return requiredFields.stream()
        .map(SQLiteConnector.Field::getColumnName)
        .allMatch(columnNames::containsKey);
  }

  private ImmutableMap<String, Integer> getColumnIndicesByName(ResultSetMetaData metaData)
      throws SQLException {
    ImmutableMap.Builder<String, Integer> columnNamesSetBuilder = ImmutableMap.builder();
    int columnCount = metaData.getColumnCount();
    for (int i = 1; i <= columnCount; i++) {
      columnNamesSetBuilder.put(metaData.getColumnName(i), i);
    }

    return columnNamesSetBuilder.build();
  }

  Iterable<ImmutableMap<Field, String>> getResults(ResultSet resultSet) throws SQLException {
    ResultSetMetaData metaData = resultSet.getMetaData();
    if (!matches(metaData)) {
      throw new IllegalArgumentException("ResultSet does not match matcher.");
    }

    Map<String, Integer> columnIndicesByName = getColumnIndicesByName(metaData);

    EnumMap<Field, Integer> columnIndexMap = new EnumMap<>(Field.class);
    for (Field requiredField : requiredFields) {
      columnIndexMap.put(requiredField, columnIndicesByName.get(requiredField.getColumnName()));
    }

    final ImmutableMap<Field, Integer> fieldNameToColumnIndexMapping =
        ImmutableMap.copyOf(columnIndexMap);
    return () ->
        new Iterator<ImmutableMap<Field, String>>() {
          @Override
          public boolean hasNext() {
            try {
              return resultSet.next();
            } catch (SQLException e) {
              return false;
            }
          }

          @Override
          public ImmutableMap<Field, String> next() {
            ImmutableMap.Builder<Field, String> nextResultBuilder = ImmutableMap.builder();
            try {
              for (Map.Entry<Field, Integer> fieldAndIndex :
                  fieldNameToColumnIndexMapping.entrySet()) {
                nextResultBuilder.put(
                    fieldAndIndex.getKey(),
                    Strings.nullToEmpty(resultSet.getString(fieldAndIndex.getValue())));
              }
            } catch (SQLException e) {
              throw new NoSuchElementException();
            }
            return nextResultBuilder.build();
          }
        };
  }
}
