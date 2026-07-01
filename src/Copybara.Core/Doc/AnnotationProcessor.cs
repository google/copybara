/*
 * Copyright (C) 2021 Google Inc.
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

namespace Copybara.Doc;

// Port note: com.google.copybara.doc.AnnotationProcessor is a build-time Java annotation processor
// (extends com.google.auto.common.BasicAnnotationProcessor). At compile time it scanned each jar for
// @StarlarkBuiltin/@Library types and emitted a "starlark_class_list.txt" resource inside the jar,
// which the Generator/ModuleLoader later read back to know which classes to document.
//
// The .NET port has no equivalent build-time processor and no proto/class-list pipeline. Its role is
// fully subsumed by runtime reflection in ModuleLoader (see the "Port note" there), which discovers
// [StarlarkBuiltin]/[Library]-annotated types directly from the loaded assemblies. There is
// therefore no type to port here.
//
// TODO(port): if a design ever needs the precomputed class list (e.g. for AOT/trimming scenarios
// where reflection over all assemblies is undesirable), reintroduce a source generator that emits an
// embedded resource analogous to starlark_class_list.txt.
