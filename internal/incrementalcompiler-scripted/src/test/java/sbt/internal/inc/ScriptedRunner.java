package sbt.internal.inc;

import java.io.File;

public class ScriptedRunner {
  // This is called by project/Scripted.scala
  public void run(File resourceBaseDirectory, boolean bufferLog, String[] tests) {
    ScriptedRunnerImpl.run(resourceBaseDirectory, bufferLog, tests);
  }
}
