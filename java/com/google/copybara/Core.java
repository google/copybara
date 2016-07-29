package com.google.copybara;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.copybara.Authoring.AuthoringMappingMode;
import com.google.copybara.Origin.Reference;
import com.google.copybara.config.skylark.OptionsAwareModule;
import com.google.copybara.transform.Sequence;
import com.google.copybara.transform.Transformation;
import com.google.copybara.util.PathMatcherBuilder;
import com.google.devtools.build.lib.events.Location;
import com.google.devtools.build.lib.skylarkinterface.Param;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModuleCategory;
import com.google.devtools.build.lib.skylarkinterface.SkylarkSignature;
import com.google.devtools.build.lib.syntax.BuiltinFunction;
import com.google.devtools.build.lib.syntax.EvalException;
import com.google.devtools.build.lib.syntax.Runtime;
import com.google.devtools.build.lib.syntax.Runtime.NoneType;
import com.google.devtools.build.lib.syntax.SkylarkList;
import java.util.HashMap;
import java.util.Map;

/**
 * Main configuration class for creating workflows.
 *
 * <p>This class is exposed in Skylark configuration as an instance variable
 * called "core". So users can use it as:
 * <pre>
 * core.workspace(
 *   name = "foo",
 *   ...
 * )
 * </pre>
 */
@SkylarkModule(
    name = Core.CORE_VAR,
    doc = "Core functionality for creating workflows, and basic transformations.",
    category = SkylarkModuleCategory.BUILTIN)
public class Core implements OptionsAwareModule {

  public static final String CORE_VAR = "core";
  public static final String PROJECT_FUNC = "project";
  private static final String WORKFLOW_FUNC = "workflow";

  private final Map<String, Workflow<?>> workflows = new HashMap<>();
  private GeneralOptions generalOptions;
  private WorkflowOptions workflowOptions;
  private String projectName;

  @Override
  public void setOptions(Options options) {
    generalOptions = options.get(GeneralOptions.class);
    workflowOptions = options.get(WorkflowOptions.class);
  }

  public String getProjectName() {
    return projectName;
  }

  public Map<String, Workflow<?>> getWorkflows() {
    return workflows;
  }

  @SkylarkSignature(name = PROJECT_FUNC, returnType = NoneType.class,
      doc = "General configuration of the project. Like the name.",
      parameters = {
          @Param(name = "self", type = Core.class, doc = "this object"),
          @Param(name = "name", type = String.class, doc = "The name of the configuration."),
      },
      objectType = Core.class, useLocation = true)
  public static final BuiltinFunction project = new BuiltinFunction(PROJECT_FUNC) {
    public NoneType invoke(Core self, String name, Location location) throws EvalException {
      if (Strings.isNullOrEmpty(name) || name.trim().equals("")) {
        throw new EvalException(location, "Empty name for the project is not allowed");
      }
      self.projectName = name;
      return Runtime.NONE;
    }
  };

  @SkylarkSignature(name = WORKFLOW_FUNC, returnType = NoneType.class,
      doc = "Defines a migration pipeline which can be invoked via the Copybara command.",
      parameters = {
          @Param(name = "self", type = Core.class, doc = "this object"),
          @Param(name = "name", type = String.class,
              doc = "The name of the workflow."),
          @Param(name = "origin", type = Origin.class,
              doc = "Where to read the migration code from."),
          @Param(name = "destination", type = Destination.class,
              doc = "Where to read the migration code from."),
          @Param(name = "transformations", type = SkylarkList.class,
              generic1 = Transformation.class,
              doc = "Where to read the migration code from."),
      },
      objectType = Core.class, useLocation = true)
  public static final BuiltinFunction workflow = new BuiltinFunction(WORKFLOW_FUNC) {
    public NoneType invoke(Core self, String workflowName, Origin<Reference> origin,
        Destination destination, SkylarkList<Transformation> transformations, Location location)
        throws EvalException {

      if (self.projectName == null) {
        throw new EvalException(location, "Project name not defined. Use project() first.");
      }

      // TODO(malcon): map the rest of Workflow parameters
      self.workflows.put(workflowName, new AutoValue_Workflow<>(self.projectName, workflowName,
          origin,
          destination,
          new Authoring(new Author("foo", "bar"), AuthoringMappingMode.PASS_THRU,
              ImmutableSet.<String>of()),
          Sequence.createSequence(ImmutableList.copyOf(transformations)),
          self.workflowOptions.getLastRevision(),
          self.generalOptions.console(),
          PathMatcherBuilder.EMPTY, PathMatcherBuilder.EMPTY,
          WorkflowMode.SQUASH, /*includeChangelistNotes=*/true, self.workflowOptions,
          /*reversibleCheck=*/ false, self.generalOptions.isVerbose(), /*askForConfirmation=*/
          false));
      return Runtime.NONE;
    }
  };

}
