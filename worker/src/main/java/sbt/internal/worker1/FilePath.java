package sbt.internal.worker1;

import java.net.URI;

public class FilePath {
  public URI path;
  public String digest;

  public FilePath(URI path, String digest) {
    this.path = path;
    this.digest = digest;
  }
}
