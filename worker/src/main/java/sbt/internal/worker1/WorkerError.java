package sbt.internal.worker1;

import java.io.Serializable;

public final class WorkerError implements Serializable {
  public final int code;
  public final String message;

  public WorkerError(int code, String message) {
    this.code = code;
    this.message = message;
  }
}
