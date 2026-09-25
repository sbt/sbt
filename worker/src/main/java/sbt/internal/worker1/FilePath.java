package sbt.internal.worker1;

import java.net.URI;
import java.util.Objects;

public class FilePath {
  public URI path;
  public String digest;

  public FilePath(URI path, String digest) {
    this.path = path;
    this.digest = digest;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof FilePath)) return false;
    FilePath other = (FilePath) o;
    return Objects.equals(path, other.path) && Objects.equals(digest, other.digest);
  }

  @Override
  public int hashCode() {
    return Objects.hash(path, digest);
  }
}
