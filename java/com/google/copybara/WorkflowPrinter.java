/*
 * Copyright (C) 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.copybara;

import com.google.common.base.Strings;
import com.google.copybara.config.Migration;
import com.google.copybara.transform.ExplicitReversal;
import com.google.copybara.transform.Sequence;
import com.google.copybara.transform.debug.TransformDebug;
import java.util.ArrayList;
import java.util.List;

/**
 * Formats a {@link Migration} or {@link Workflow} configuration into human-readable sections for
 * terminal output.
 */
public final class WorkflowPrinter {

  private WorkflowPrinter() {}

  /**
   * Returns a structured string representation of the given {@link Migration}.
   *
   * @param migration the migration configuration to format
   */
  public static String print(Migration migration) {
    if (migration instanceof Workflow<?, ?>) {
      return printWorkflow((Workflow<?, ?>) migration);
    }
    return String.format("Migration '%s' (%s):\n  %s",
        migration.getName(), migration.getModeString(), migration);
  }

  /**
   * Formats a {@link Workflow} into structured sections: Mode, Origin, Destination, Authoring, and
   * unwrapped Transformations.
   *
   * @param workflow the workflow configuration to format
   */
  public static String printWorkflow(Workflow<?, ?> workflow) {
    StringBuilder sb = new StringBuilder();
    sb.append(String.format("Workflow: %s\n", workflow.getName()));
    sb.append(String.format("Mode: %s\n", workflow.getMode()));
    if (!Strings.isNullOrEmpty(workflow.getDescription())) {
      sb.append(String.format("Description: %s\n", workflow.getDescription()));
    }

    sb.append("\nOrigin:\n");
    sb.append(String.format("  %s\n", workflow.getOrigin()));
    sb.append(String.format("  Origin files: %s\n", workflow.getOriginFiles()));

    sb.append("\nDestination:\n");
    sb.append(String.format("  %s\n", workflow.getDestination()));
    sb.append(String.format("  Destination files: %s\n", workflow.getDestinationFiles()));

    sb.append("\nAuthoring:\n");
    sb.append(String.format("  %s\n", workflow.getAuthoring()));

    sb.append("\nTransformations:\n");
    List<Transformation> transformations = unwrapTransformations(workflow.getTransformation());
    if (transformations.isEmpty()) {
      sb.append("  (none)\n");
    } else {
      for (int i = 0; i < transformations.size(); i++) {
        sb.append(String.format("  %d. %s\n", i + 1, transformations.get(i)));
      }
    }

    return sb.toString();
  }

  /**
   * Recursively unwraps {@link Sequence}, {@link ExplicitReversal}, and {@link TransformDebug}
   * transformations into a flat list of underlying transformations in execution order.
   *
   * @param transformation the transformation to unwrap
   * @return a flat list of transformations in execution order
   */
  public static List<Transformation> unwrapTransformations(Transformation transformation) {
    List<Transformation> result = new ArrayList<>();
    collectTransformations(transformation, result);
    return result;
  }

  private static void collectTransformations(
      Transformation transformation, List<Transformation> result) {
    if (transformation == null) {
      return;
    }
    if (transformation instanceof Sequence sequence) {
      for (Transformation child : sequence.getSequence()) {
        collectTransformations(child, result);
      }
    } else if (transformation instanceof ExplicitReversal explicitReversal) {
      collectTransformations(explicitReversal.getForward(), result);
    } else if (transformation instanceof TransformDebug transformDebug) {
      collectTransformations(transformDebug.getDelegate(), result);
    } else {
      result.add(transformation);
    }
  }
}
