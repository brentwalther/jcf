package net.brentwalther.jcf.util;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;

public class Formatter {
  public static String currency(BigDecimal n) {
    return NumberFormat.getCurrencyInstance().format(n);
  }

  public static String dateTime(Instant instant) {
    return instant
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM));
  }

  public static String date(Instant instant) {
    return instant
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM));
  }

  public static String ledgerDate(Instant instant) {
    return instant.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_LOCAL_DATE);
  }

  public static String ledgerCurrency(BigDecimal amount) {
    boolean isNegative = amount.compareTo(BigDecimal.ZERO) < 0;
    StringBuilder formatted = new StringBuilder("$");
    if (amount.compareTo(BigDecimal.ZERO) < 0) {
      amount = amount.negate();
      formatted.append("-");
    }
    String numCents = amount.movePointRight(2).toBigInteger().toString();
    switch (numCents.length()) {
      case 1:
        formatted.append("0.0").append(numCents);
        break;
      case 2:
        formatted.append("0.").append(numCents);
        break;
      default:
        formatted
            .append(numCents.substring(0, numCents.length() - 2))
            .append(".")
            .append(numCents.substring(numCents.length() - 2));
        break;
    }
    return formatted.toString();
  }

  public static Instant parseDateFrom(String str, DateTimeFormatter dateTimeFormatter) {
    return LocalDate.from(dateTimeFormatter.parse(str))
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant();
  }
}
