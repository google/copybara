// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.testing;

import com.google.copybara.Destination;
import com.google.copybara.Options;
import com.google.copybara.Origin.Reference;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

/**
 * A destination for testing which doesn't write the workdir anywhere and simply records when
 * {@link Destination#process(Path, Reference, long, String)} is called and with what arguments.
 */
public class RecordsProcessCallDestination implements Destination, Destination.Yaml {

  public List<Long> processTimestamps = new ArrayList<>();
  public int calls = 0;
  private String originRef;

  @Override
  public void process(Path workdir, Reference<?> originRef, long timestamp, String changesSummary) {
    this.originRef = originRef.asString();
    processTimestamps.add(timestamp);
    calls++;
  }

  @Nullable
  @Override
  public String getPreviousRef(String labelName) {
    return originRef;
  }

  @Override
  public Destination withOptions(Options options, String configName) {
    return this;
  }
}
