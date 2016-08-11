package com.google.copybara.testing;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.copybara.config.skylark.CannotResolveLabel;
import com.google.copybara.config.skylark.ConfigFile;
import com.google.copybara.config.skylark.LabelsAwareModule;
import com.google.copybara.util.console.testing.TestingConsole;
import com.google.devtools.build.lib.skylarkinterface.Param;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModuleCategory;
import com.google.devtools.build.lib.skylarkinterface.SkylarkSignature;
import com.google.devtools.build.lib.syntax.BuiltinFunction;
import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class SkylarkTestExecutorTest {

  private SkylarkTestExecutor skylark;

  @Before
  public void setup() {
    TestingConsole console = new TestingConsole();
    OptionsBuilder options = new OptionsBuilder();
    options.setConsole(console);
    skylark = new SkylarkTestExecutor(options, DummyModule.class);
  }

  @Test
  public void addExtraConfigFile() throws Exception {
    skylark.addExtraConfigFile("foo_extra", "foo content 42");
    String content = skylark.eval("c", "c = dummy.read_foo_extra()");
    assertThat(content).isEqualTo("foo content 42");
  }

  @SkylarkModule(
      name = "dummy",
      doc = "For testing.",
      category = SkylarkModuleCategory.BUILTIN,
      documented = false)
  public static class DummyModule implements LabelsAwareModule {
    private ConfigFile configFile;

    @Override
    public void setConfigFile(ConfigFile configFile) {
      this.configFile = configFile;
    }

    @SkylarkSignature(name = "read_foo_extra", returnType = String.class,
        doc = "Read foo_extra",
        objectType = DummyModule.class,
        parameters = {
            @Param(name = "self", type = DummyModule.class, doc = "self"),
        },
        documented = false)
    public static final BuiltinFunction READ_FOO_EXTRA = new BuiltinFunction("read_foo_extra") {
      public String invoke(DummyModule self) {
        try {
          return new String(self.configFile.resolve("foo_extra").content(), UTF_8);
        } catch (CannotResolveLabel|IOException inconceivable) {
          throw new AssertionError(inconceivable);
        }
      }
    };
  }
}
