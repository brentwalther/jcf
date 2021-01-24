package net.brentwalther.jcf;

import com.alee.laf.WebLookAndFeel;
import com.alee.managers.settings.UISettingsManager;
import com.alee.managers.style.StyleManager;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import net.brentwalther.jcf.JcfEnvironmentImpl.EnvironmentType;
import net.brentwalther.jcf.environment.JcfEnvironment;
import net.brentwalther.jcf.ui.swing.SwingUi;
import net.brentwalther.jcf.ui.swing.impl.SwingUiImpl;

public class SwingApp extends JFrame {
  private final SwingUi rootUi;

  public SwingApp(SwingUi rootUi) {
    super("JCF Swing UI");
    this.rootUi = rootUi;

    setSize(1024, 768);
    setContentPane(rootUi.getRootContainer());
  }

  public static void main(String[] args) {
    JcfEnvironment jcfEnvironment =
        JcfEnvironmentImpl.createFromArgsForEnv(args, EnvironmentType.SWING_UI);
    startApp(jcfEnvironment);
  }

  private static void startApp(JcfEnvironment jcfEnvironment) {
    // You should always work with UI inside Event Dispatch Thread (EDT)
    // That includes installing L&F, creating any Swing components etc.
    SwingUtilities.invokeLater(
        () -> {
          WebLookAndFeel.install();
          StyleManager.initialize();
          UISettingsManager.initialize();

          // Initialize your application once you're done setting everything up
          // JFrame frame = ...
          JFrame frame = SwingApp.createFromInitialEnvironment(jcfEnvironment);
          frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
          frame.setVisible(true);
        });
  }

  private static SwingApp createFromInitialEnvironment(JcfEnvironment jcfEnvironment) {
    if (jcfEnvironment.needsHelp()) {
      StringBuilder builder = new StringBuilder();
      jcfEnvironment.printHelpTextTo(builder);
      return new SwingApp(SwingUiImpl.dummyAppFromHelpText(builder.toString()));
    }
    return new SwingApp(SwingUiImpl.create(jcfEnvironment));
  }
}
