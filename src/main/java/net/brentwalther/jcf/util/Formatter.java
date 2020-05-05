package net.brentwalther.jcf.util;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
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
}
