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

public class RunInfo implements Serializable {
  public static class JvmRunInfo implements Serializable {
    public ArrayList<String> args;
    public ArrayList<FilePath> classpath;
    public String mainClass;
    public boolean connectInput;

    public JvmRunInfo(
        ArrayList<String> args,
        ArrayList<FilePath> classpath,
        String mainClass,
        boolean connectInput) {
      this.args = args;
      this.classpath = classpath;
      this.mainClass = mainClass;
      this.connectInput = connectInput;
    }
  }

  public static class NativeRunInfo implements Serializable {}

  public boolean jvm;
  public JvmRunInfo jvmRunInfo;
  public NativeRunInfo nativeRunInfo;

  public RunInfo(boolean jvm, JvmRunInfo jvmRunInfo, NativeRunInfo nativeRunInfo) {
    this.jvm = jvm;
    this.jvmRunInfo = jvmRunInfo;
    this.nativeRunInfo = nativeRunInfo;
  }
}
