/*
 * Copyright (C) 2018 Google Inc.
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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.graph.AbstractGraph;
import com.google.common.graph.ElementOrder;
import java.util.LinkedHashMap;
import java.util.Set;

/**
 * A {@link com.google.common.graph.Graph} implementation for representing a graph of changes.
 *
 * <p>The main difference with standard {@code Graph}s is that the parents (ancestors) are
 * guaranteed to be returned in the order of insertion. This is important because Copybara gives
 * special meaning to first-parent (It is a linear history migrator).
 */
public final class ChangeGraph<C> extends AbstractGraph<C> {

  private final ImmutableSet<C> nodes;
  private final ImmutableSetMultimap<C, C> parents;
  private final ImmutableSetMultimap<C, C> children;

  private ChangeGraph(ImmutableSet<C> nodes,
      ImmutableSetMultimap<C, C> parents, ImmutableSetMultimap<C, C> children) {
    this.nodes = Preconditions.checkNotNull(nodes);
    this.parents = Preconditions.checkNotNull(parents);
    this.children = Preconditions.checkNotNull(children);
  }

  /**
   * Create a {@link ChangeGraph} builder
   */
  public static <C> Builder<C> builder() {
    return new Builder<>();
  }

  @Override
  public Set<C> nodes() {
    return ImmutableSet.copyOf(nodes);
  }

  @Override
  public boolean isDirected() {
    return true;
  }

  @Override
  public boolean allowsSelfLoops() {
    return false;
  }

  @Override
  public ElementOrder<C> nodeOrder() {
    return ElementOrder.insertion();
  }

  @Override
  public Set<C> adjacentNodes(C node) {
    throw new UnsupportedOperationException("Not implemented. Not needed for now");
  }

  /**
   * Returns the parents for {@code node} in the original order they were inserted.
   */
  @Override
  public Set<C> predecessors(C node) {
    checkArgument(nodes.contains(node));
    return parents.get(node);
  }

  @Override
  public Set<C> successors(C node) {
    checkArgument(nodes.contains(node));
    return children.get(node);
  }

  /** A builder class for {@code ChangeGraph} */
  public static class Builder<C> {

    // We use a map to avoid having duplicates that represent the same object in the edges
    private final LinkedHashMap<C, C> changes = new LinkedHashMap<>();
    private final ImmutableSetMultimap.Builder<C, C> parents = ImmutableSetMultimap.builder();
    private final ImmutableSetMultimap.Builder<C, C> children = ImmutableSetMultimap.builder();

    private Builder() {
    }

    /**
     * Add all nodes and edges from {@code graph}, preserving the order
     */
    public Builder<C> addAll(ChangeGraph<C> graph) {
      graph.nodes.forEach(this::addChange);
      graph.parents.forEach(this::addParent);
      return this;
    }

    /**
     * Add a {@code parent} for {@code change}. If two parents are inserted for the same change, the
     * insertion order is preserved. In other words:
     * {@code graph.predecessors(change).iterator().next()} is always the first inserted parent.
     */
    public Builder<C> addParent(C change, C parent) {
      C internalChange = changes.get(change);
      C internalParent = changes.get(parent);
      checkNotNull(internalChange, "%s not present in graph", change);
      checkNotNull(internalParent, "parent %s not present in graph", parent);

      // Use the changes instance instead of the one provided to avoid duplicates on copy
      parents.put(internalChange, internalParent);
      children.put(internalParent, internalChange);
      return this;
    }

    /**
     * Add a {@code change}. Insertion order is preserved
     */
    public Builder<C> addChange(C change) {
      checkArgument(!changes.containsKey(change), "%s already present in graph", change);
      changes.put(change, change);
      return this;
    }

    /**
     * Construct an immutable Graph that preserves the order of node insertions and edge order
     */
    public ChangeGraph<C> build() {
      return new ChangeGraph<>(ImmutableSet.copyOf(changes.keySet()),
          parents.build(),
          children.build());
    }
  }
}
