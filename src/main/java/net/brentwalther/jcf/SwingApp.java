package net.brentwalther.jcf;

import com.alee.laf.WebLookAndFeel;
import com.alee.managers.settings.UISettingsManager;
import com.alee.managers.style.StyleManager;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import java.util.function.Function;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import net.brentwalther.jcf.environment.JcfEnvironment;
import net.brentwalther.jcf.model.JcfModel.Model;
import net.brentwalther.jcf.report.TsvExpensesByMonthReport;
import net.brentwalther.jcf.report.TsvNetworthReport;
import net.brentwalther.jcf.ui.swing.SwingUi;
import net.brentwalther.jcf.ui.swing.impl.SwingUiImpl;

public class SwingApp extends JFrame {

  private static final ImmutableMap<String, Function<Model, String>> REPORT_GENERATORS_BY_NAME_MAP =
      ImmutableMap.of(
          "networth", TsvNetworthReport::generateFrom,
          "expense", TsvExpensesByMonthReport::generateFrom);
  private final SwingUi rootUi;

  public SwingApp(SwingUi rootUi) {
    super("JCF Swing UI");
    this.rootUi = rootUi;

    setSize(1024, 768);
    setContentPane(rootUi.getRootContainer());
  }

  public static void main(String[] args) {
    // You should always work with UI inside Event Dispatch Thread (EDT)
    // That includes installing L&F, creating any Swing components etc.
    SwingUtilities.invokeLater(
        () -> {
          WebLookAndFeel.install();
          StyleManager.initialize();
          UISettingsManager.initialize();

          // Initialize your application once you're done setting everything up
          // JFrame frame = ...
          JFrame frame = SwingApp.create(args);
          frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
          frame.setVisible(true);
        });
  }

  private static SwingApp create(String[] args) {
    SwingUi ui = SwingUiImpl.create();
    JcfEnvironment environment = JcfEnvironmentImpl.createFromArgsForEnv(args, ui);
    if (environment.needsHelp()) {
      StringBuilder builder = new StringBuilder();
      environment.printHelpTextTo(builder);
      return new SwingApp(SwingUiImpl.dummyAppFromHelpText(builder.toString()));
    }
    Function<Model, String> reportGenerator =
        REPORT_GENERATORS_BY_NAME_MAP.get(environment.getReportType());
    if (reportGenerator == null) {
      ui.getPrinter()
          .println(
              "Unknown report type "
                  + environment.getReportType()
                  + ". Recognized report types are: "
                  + Joiner.on(", ").join(REPORT_GENERATORS_BY_NAME_MAP.keySet()));
    } else {
      ui.getPrinter().println(reportGenerator.apply(environment.getInitialModel()));
    }
    return new SwingApp(ui);
  }
}
