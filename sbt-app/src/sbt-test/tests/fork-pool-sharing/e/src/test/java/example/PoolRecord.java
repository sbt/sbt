package example;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/** Records the window a forked JVM spent on one class, tagged with the project and the JVM's pid. */
public final class PoolRecord {
  public static void mark() throws IOException, InterruptedException {
    String dir = System.getProperty("records.dir");
    String proj = System.getProperty("proj");
    Files.createDirectories(Paths.get(dir));
    long pid = ProcessHandle.current().pid();
    long start = System.currentTimeMillis();
    Thread.sleep(1500);
    long end = System.currentTimeMillis();
    synchronized (PoolRecord.class) {
      try (FileWriter w = new FileWriter(dir + "/" + proj + "-" + pid + ".records", true)) {
        w.write(proj + " " + pid + " " + start + " " + end + "\n");
      }
    }
  }
}
