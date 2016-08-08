package com.google.copybara.testing;

import com.google.copybara.Option;
import com.google.copybara.Options;
import com.google.copybara.config.skylark.OptionsAwareModule;
import com.google.devtools.build.lib.skylarkinterface.Param;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModuleCategory;
import com.google.devtools.build.lib.skylarkinterface.SkylarkSignature;
import com.google.devtools.build.lib.syntax.BuiltinFunction;
import com.google.devtools.build.lib.syntax.EvalException;

/**
 * A Skylark module used by tests
 */
@SkylarkModule(
    name = "testing",
    doc = "",
    category = SkylarkModuleCategory.BUILTIN)
public class TestingModule implements OptionsAwareModule {

  private TestingOptions testingOptions;

  @Override
  public void setOptions(Options options) {
    testingOptions = options.get(TestingOptions.class);
  }

  @SkylarkSignature(name = "origin", returnType = DummyOrigin.class,
      doc = "A dummy origin",
      parameters = {
          @Param(name = "self", type = TestingModule.class, doc = "this object"),
      },
      objectType = TestingModule.class)
  public static final BuiltinFunction ORIGIN = new BuiltinFunction("origin") {
    public DummyOrigin invoke(TestingModule self) throws EvalException {
      return self.testingOptions.origin;
    }
  };

  @SkylarkSignature(name = "destination",
      returnType = RecordsProcessCallDestination.class,
      doc = "A dummy destination",
      parameters = {
          @Param(name = "self", type = TestingModule.class, doc = "this object"),
      },
      objectType = TestingModule.class)
  public static final BuiltinFunction DESTINATION = new BuiltinFunction("destination") {
    public RecordsProcessCallDestination invoke(TestingModule self) throws EvalException {
      return self.testingOptions.destination;
    }
  };

  public final static class TestingOptions implements Option {

    public DummyOrigin origin;
    public RecordsProcessCallDestination destination;
  }
}
