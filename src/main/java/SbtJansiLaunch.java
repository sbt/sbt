class SbtJansiLaunch {
  public static void main(String[] args) {
    org.fusesource.jansi.AnsiConsole.systemInstall();
    xsbt.boot.Boot.main(args);
  }
}
