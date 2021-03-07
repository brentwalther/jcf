package net.brentwalther.jcf.flogger;

import com.google.common.flogger.backend.LoggerBackend;
import com.google.common.flogger.backend.system.BackendFactory;

public class JcfBackendFactory extends BackendFactory {
  @Override
  public LoggerBackend create(String s) {
    return new JcfLoggerBackend();
  }
}
