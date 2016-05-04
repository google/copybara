// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.testing;

import com.google.copybara.Destination;
import com.google.copybara.Options;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

/**
 * A destination for testing which doesn't write the workdir anywhere and simply records when
 * {@link #process(Path,String,long,String)} is called and with what arguments.
 */
public class RecordsProcessCallDestination implements Destination, Destination.Yaml {

  public List<Long> processTimestamps = new ArrayList<>();

  @Override
  public void process(Path workdir, String originRef, long timestamp, String changesSummary) {
    processTimestamps.add(timestamp);
  }

  @Nullable
  @Override
  public String getPreviousRef() {
    return null;
  }

  @Override
  public Destination withOptions(Options options) {
    return this;
  }
}
