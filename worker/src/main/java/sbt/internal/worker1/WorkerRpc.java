/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.worker1;

import java.io.PrintStream;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.scalasbt.shadedgson.com.google.gson.JsonElement;
import org.scalasbt.shadedgson.com.google.gson.JsonObject;
import org.scalasbt.shadedgson.com.google.gson.JsonParser;

/**
 * Correlates JSON-RPC requests the worker sends to sbt with their replies. A single reader thread
 * owns the socket: replies complete the matching future, every other line goes to the handler
 * supplied to {@link #start}. Every failure resolves to a {@code null} reply rather than throwing,
 * so a null means "no more work" or "channel failed" — {@link #isBroken()} tells them apart.
 */
public final class WorkerRpc {
  private final PrintStream out;
  private final Scanner in;
  private final long timeoutMillis;
  private final AtomicLong nextId = new AtomicLong(1000000L);
  private final ConcurrentHashMap<Long, CompletableFuture<JsonElement>> pending =
      new ConcurrentHashMap<>();
  private volatile boolean closed = false;
  private volatile boolean broken = false;

  public WorkerRpc(final PrintStream out, final Scanner in, final long timeoutMillis) {
    this.out = out;
    this.in = in;
    this.timeoutMillis = timeoutMillis;
  }

  /**
   * Starts the reader thread. Non-reply lines go to onRequest; onEnd runs once when the reader
   * stops for any reason.
   */
  public void start(final Consumer<String> onRequest, final Runnable onEnd) {
    final Thread reader =
        new Thread(
            () -> {
              try {
                while (!closed && in.hasNextLine()) {
                  final String line = in.nextLine();
                  if (!completeIfReply(line)) onRequest.accept(line);
                }
                // Running out of lines before close() means sbt went away.
                if (!closed) broken = true;
              } catch (final Throwable t) {
                broken = true;
              } finally {
                failAllPending();
                onEnd.run();
              }
            });
    reader.setName("sbt-worker-rpc-reader");
    reader.setDaemon(true);
    reader.start();
  }

  private boolean completeIfReply(final String line) {
    final JsonObject o;
    try {
      o = JsonParser.parseString(line).getAsJsonObject();
    } catch (final Throwable t) {
      return false;
    }
    if (o.has("method") || !o.has("id")) return false;
    if (!o.has("result") && !o.has("error")) return false;
    final long id;
    try {
      id = o.getAsJsonPrimitive("id").getAsLong();
    } catch (final Throwable t) {
      return false;
    }
    final CompletableFuture<JsonElement> f = pending.remove(id);
    if (f == null) return false;
    if (o.has("error")) {
      broken = true;
      f.complete(null);
    } else {
      f.complete(o.get("result"));
    }
    return true;
  }

  private void failAllPending() {
    for (final Long key : pending.keySet()) {
      final CompletableFuture<JsonElement> f = pending.remove(key);
      if (f != null) f.complete(null);
    }
  }

  /** Whether the channel failed, so a null reply must not be read as "no more work". */
  public boolean isBroken() {
    return broken;
  }

  /**
   * Sends a request and blocks for its reply, returning the {@code result} as an Integer, or null
   * on a JSON null, EOF, timeout, or error reply — check {@link #isBroken()} to tell "no more work"
   * from a channel failure.
   */
  public Integer requestIndex(final String method, final String params) {
    // `broken` too: a stopped reader has drained `pending`, so a future registered later is never
    // completed and the caller would wait out the ten-minute reply timeout.
    if (closed || broken) return null;
    final long id = nextId.getAndIncrement();
    final CompletableFuture<JsonElement> f = new CompletableFuture<>();
    pending.put(id, f);
    // Again after registering: the reader sets the flag before draining `pending`, so whichever way
    // the two interleave, one of these checks sees it. The check above alone cannot.
    if (closed || broken) {
      pending.remove(id);
      return null;
    }
    synchronized (out) {
      out.println(
          String.format(
              "{ \"jsonrpc\": \"2.0\", \"method\": \"%s\", \"params\": %s, \"id\": %d }",
              method, params, id));
      out.flush();
    }
    try {
      final JsonElement result = f.get(timeoutMillis, TimeUnit.MILLISECONDS);
      if (result == null || result.isJsonNull()) return null;
      return Integer.valueOf(result.getAsInt());
    } catch (final Throwable t) {
      // Timed out, interrupted, or the reply was not an int; not a clean drain.
      broken = true;
      pending.remove(id);
      return null;
    }
  }

  public void close() {
    closed = true;
    failAllPending();
  }
}
