import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
  public static void main(String[] args) throws Exception {
    final Arg arg = new Arg();
    final Path dir = Paths.get(args[0]);
    Files.createDirectories(dir);
    Thread t = new Thread(() -> {
      try {
        Thread.sleep(1000);
        Late.use(arg);
        Files.writeString(dir.resolve("result-ok"), "ok");
      } catch (Throwable e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        try { Files.writeString(dir.resolve("result-error"), sw.toString()); } catch (Exception ignored) {}
      }
    });
    t.setDaemon(true);
    t.start();
  }
}
