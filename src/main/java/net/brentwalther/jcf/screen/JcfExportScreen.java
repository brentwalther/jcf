package net.brentwalther.jcf.screen;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.CodedOutputStream;
import net.brentwalther.jcf.TerminalProvider;
import net.brentwalther.jcf.model.JcfModel.Model;
import net.brentwalther.jcf.prompt.NoticePrompt;
import net.brentwalther.jcf.prompt.PromptEvaluator;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/** A screen to export a model to the JCF proto format. */
public class JcfExportScreen {
  public static void start(Model model, File file) {
    try {
      model.writeTo(CodedOutputStream.newInstance(new FileOutputStream(file)));
    } catch (IOException e) {
      PromptEvaluator.showAndGetResult(
          TerminalProvider.get(),
          NoticePrompt.withMessages(
              ImmutableList.of(
                  "Failed to export model " + model,
                  "  to file: " + file.getAbsolutePath(),
                  "  due to exception: " + e)));
    }
  }
}
