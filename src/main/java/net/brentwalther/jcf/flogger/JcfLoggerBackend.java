package net.brentwalther.jcf.flogger;

import com.google.common.flogger.backend.LogData;
import com.google.common.flogger.backend.LoggerBackend;
import java.util.logging.Level;

public class JcfLoggerBackend extends LoggerBackend {

  private static

  private final String name;

  public JcfLoggerBackend(String name) {
    this.name = name;
  }

  public static JcfLoggerBackend create(String name) {
    return new JcfLoggerBackend(name);
  }

  @Override
  public String getLoggerName() {
    return name;
  }

  @Override
  public boolean isLoggable(Level level) {
    return false;
  }

  @Override
  public void log(LogData logData) {
    logData.getLevel();
  }

  @Override
  public void handleError(RuntimeException e, LogData logData) {}
}
