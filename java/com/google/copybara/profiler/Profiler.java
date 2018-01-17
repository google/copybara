/*
 * Copyright (C) 2016 Google Inc.
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

package com.google.copybara.profiler;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Ticker;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import javax.annotation.Nullable;

/**
 * A profiler that allows to record hierarchical time statistics of the different
 * Copybara components.
 */
public final class Profiler {

  @VisibleForTesting
  public static final String ROOT_NAME = "//copybara";
  public static final String TYPE = "type";

  private final ProfilerTask nullProfilerTask;

  /**
   * A stack of tasks to be finished. When a new thread is spawned we create a copy
   * of the stack with only the top-level element so that we can use it as a parent.
   */
  @VisibleForTesting
  final InheritableThreadLocal<Deque<Task>> taskQueue =
      new InheritableThreadLocal<Deque<Task>>() {

    /**
     * Make a copy of the parent thread stack but only keep the top level one, since a child
     * shouldn't be able to finish a parent job. We only keep the latest one in order to be
     * able to construct child elements.
     */
    @Override
    protected Deque<Task> childValue(Deque<Task> parentValue) {
      // Parent can be empty if the listener creates a thread when it receives the finish
      // event for the root task ("//copybara").
      if (stopped || parentValue.isEmpty()) {
        return null;
      }
      return createQueue(parentValue.element());
    }

    /**
     * Shouldn't be called regularly. But we might have network processes that starts
     * their own thread pool outside of Copybara.
     *
     */
    @Override
    protected Deque<Task> initialValue() {
      if (stopped) {
        return null;
      }
      return createQueue(
          new Task("//detached_thread", ticker.read()));
    }
  };
  private boolean stopped;

  private Deque<Task> createQueue(Task element) {
    ArrayDeque<Task> tasks = new ArrayDeque<>(2);
    tasks.add(element);
    return tasks;
  }

  private final Ticker ticker;
  private List<Listener> listeners = ImmutableList.of();
  private ProfilerTask rootProfilerTask;

  public Profiler(Ticker ticker) {
    this.ticker = ticker;
    this.nullProfilerTask = new ProfilerTask(/* expectedTask= */ null);
  }

  /**
   * Call this method once at the beginning of Copybara binary run.
   * @param listeners the listeners to be notified o the task events
   */
  public void init(List<Listener> listeners){
    this.listeners = listeners;
    if (listeners.isEmpty()){
      return;
    }
    Task task = new Task(ROOT_NAME, ticker.read());
    taskQueue.set(createQueue(task));
    for (Listener listener : listeners) {
      listener.taskStarted(task);
    }
    rootProfilerTask = new ProfilerTask(task);
  }

  /**
   * Call this method once at the end of the Copybara binary run.
   */
  public void stop(){
    if (listeners.isEmpty()) {
      return;
    }
    Preconditions.checkState(taskQueue.get().element().getDescription().equals(ROOT_NAME));
    rootProfilerTask.close();
    Preconditions.checkState(taskQueue.get().isEmpty());
    stopped = true;
  }

  public ImmutableMap<String, String> taskType(String type) {
    return ImmutableMap.of(TYPE, type);
  }

  /**
   * Create a new profiler task that can be closed using try-with-resources.
   *
   *  <p>The profiler tasks are reentrant. So you can stack them in multiple
   *  nested try-with-resources.
   *
   * <p>Example usage:
   *  <pre>{@code
   *     try (ProfilerTask p = profiler.start("migration")) {
   *       try (ProfilerTask p2 = profiler.start("subtack")) {
   *         // Do job
   *       }
   *     }
   * }</pre>
   *
   * @param description description of the task
   * @return a {@link AutoCloseable} task than can be closed manually or with
   * try-with-resources pattern
   */
  public ProfilerTask start(String description) {
    return start(description, ImmutableMap.of());
  }

  /**
   * Overloaded method for {@link #start(String)}, that allows adding {@code fields} to the context
   * of this task.
   */
  public ProfilerTask start(String description, ImmutableMap<String, String> fields) {
    if (stopped || listeners.isEmpty()) {
      return nullProfilerTask;
    }
    Deque<Task> tasks = taskQueue.get();
    Preconditions.checkState(!tasks.isEmpty());
    Task parent = tasks.element();
    Task child = new Task(parent.getDescription() + "/" + description, fields, ticker.read());
    tasks.push(child);
    for (Listener listener : listeners) {
      listener.taskStarted(child);
    }
    return new ProfilerTask(child);
  }

  /**
   * Record a simple task metric. The user is in charge of providing its own time.
   */
  public void simpleTask(String description, long startNanos, long endNanos) {
    if (stopped || listeners.isEmpty()) {
      return;
    }
    Deque<Task> tasks = taskQueue.get();
    Preconditions.checkState(!tasks.isEmpty());
    Task parent = tasks.element();
    Task child  = new Task(parent.getDescription() + "/" + description, startNanos);
    Task finishedChild = child.finish(endNanos);
    for (Listener listener : listeners) {
      listener.taskStarted(child);
      listener.taskFinished(finishedChild);
    }
  }

  /**
   * A profiler task that can be closed to send the finish metric.
   */
  public class ProfilerTask implements AutoCloseable {
    @Nullable
    private final Task expectedTask;

    private ProfilerTask(@Nullable Task expectedTask) {
      this.expectedTask = expectedTask;
    }

    /**
     * Close the {@code task} if its not null.
     */
    @Override
    public void close() {
      if (expectedTask != null && !stopped) {
        Task task = taskQueue.get().pop();
        if (task != this.expectedTask) {
          throw new IllegalStateException("Trying to finish a task that is different"
              + " from the registered one: " + task.getDescription() + ". Expecting: "
              + this.expectedTask.getDescription());
        }
        task = task.finish(ticker.read());
        for (Listener listener : listeners) {
          listener.taskFinished(task);
        }
      }
    }
  }
}
