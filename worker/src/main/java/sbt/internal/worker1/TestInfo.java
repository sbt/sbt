/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.worker1;

import java.io.Serializable;
import java.util.ArrayList;
import sbt.testing.TaskDef;

public class TestInfo implements Serializable {
  public static class TestRunner implements Serializable {
    public final ArrayList<String> implClassNames;
    public final ArrayList<String> mainRunnerArgs;
    public final ArrayList<String> mainRunnerRemoteArgs;

    public TestRunner(
        ArrayList<String> implClassNames,
        ArrayList<String> mainRunnerArgs,
        ArrayList<String> mainRunnerRemoteArgs) {
      this.implClassNames = implClassNames;
      this.mainRunnerArgs = mainRunnerArgs;
      this.mainRunnerRemoteArgs = mainRunnerRemoteArgs;
    }
  }

  public final boolean jvm;
  public final RunInfo.JvmRunInfo jvmRunInfo;
  public final RunInfo.NativeRunInfo nativeRunInfo;
  public final boolean ansiCodesSupported;
  public final boolean parallel;
  public final Integer parallelism;
  public final ArrayList<TaskDef> taskDefs;
  public final ArrayList<TestRunner> testRunners;

  public TestInfo(
      boolean jvm,
      RunInfo.JvmRunInfo jvmRunInfo,
      RunInfo.NativeRunInfo nativeRunInfo,
      boolean ansiCodesSupported,
      boolean parallel,
      Integer parallelism,
      ArrayList<TaskDef> taskDefs,
      ArrayList<TestRunner> testRunners) {
    this.jvm = jvm;
    this.jvmRunInfo = jvmRunInfo;
    this.nativeRunInfo = nativeRunInfo;
    this.ansiCodesSupported = ansiCodesSupported;
    this.parallel = parallel;
    this.parallelism = parallelism;
    this.taskDefs = taskDefs;
    this.testRunners = testRunners;
  }
}
