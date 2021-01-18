package net.brentwalther.jcf.export;

import com.google.common.flogger.FluentLogger;
import com.google.protobuf.CodedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import net.brentwalther.jcf.model.JcfModel.Model;

/** A screen to export a model to the JCF proto format. */
public class JcfModelExporter {
  private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();

  public static void start(Model model, File file) {
    try {
      model.writeTo(CodedOutputStream.newInstance(new FileOutputStream(file)));
    } catch (IOException e) {
      LOGGER.atSevere().withCause(e).log(
          "Failed to export JCF model proto to file %s", file.getAbsolutePath());
    }
  }
}
