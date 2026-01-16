/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.worker1;

import org.scalasbt.shadedgson.com.google.gson.Gson;
import org.scalasbt.shadedgson.com.google.gson.TypeAdapter;
import org.scalasbt.shadedgson.com.google.gson.TypeAdapterFactory;
import org.scalasbt.shadedgson.com.google.gson.reflect.TypeToken;
import org.scalasbt.shadedgson.com.google.gson.stream.JsonReader;
import org.scalasbt.shadedgson.com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.util.ArrayList;

class ThrowableAdapterFactory implements TypeAdapterFactory {
  private ThrowableAdapterFactory() {}

  public static final ThrowableAdapterFactory INSTANCE = new ThrowableAdapterFactory();

  @Override
  public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
    // Only handles Throwable and subclasses; let other factories handle any other type
    if (!Throwable.class.isAssignableFrom(type.getRawType())) {
      return null;
    }

    @SuppressWarnings("unchecked")
    TypeAdapter<T> adapter =
        (TypeAdapter<T>)
            new TypeAdapter<Throwable>() {
              @Override
              public Throwable read(JsonReader in) throws IOException {
                String message = null;
                String type = null;
                ArrayList<StackTraceElement> stackTraces = new ArrayList<>();
                in.beginObject();
                while (in.hasNext()) {
                  String name = in.nextName();
                  if (name.equals("message")) {
                    message = in.nextString();
                  } else if (name.equals("type")) {
                    type = in.nextString();
                  } else if (name.equals("staceTrace")) {
                    in.beginArray();
                    while (in.hasNext()) {
                      StackTraceElement item = readStaceTraceElement(in);
                      stackTraces.add(item);
                    }
                    in.endArray();
                  } else {
                    in.skipValue();
                  }
                }
                in.endObject();
                Throwable ex = new PersistedException(message, null, type);
                StackTraceElement array[] = new StackTraceElement[stackTraces.size()];
                ex.setStackTrace(stackTraces.toArray(array));
                return new ForkTestMain.ForkError(ex);
              }

              @Override
              public void write(JsonWriter out, Throwable value) throws IOException {
                if (value == null) {
                  out.nullValue();
                  return;
                }

                out.beginObject();
                // Include exception type name to give more context; for example
                // NullPointerException might
                // not have a message
                out.name("type");
                out.value(value.getClass().getSimpleName());

                out.name("message");
                out.value(value.getMessage());

                Throwable cause = value.getCause();
                if (cause != null) {
                  out.name("cause");
                  write(out, cause);
                }

                Throwable[] suppressedArray = value.getSuppressed();
                if (suppressedArray.length > 0) {
                  out.name("suppressed");
                  out.beginArray();

                  for (Throwable suppressed : suppressedArray) {
                    write(out, suppressed);
                  }

                  out.endArray();
                }

                StackTraceElement[] staceTrace = value.getStackTrace();
                if (staceTrace.length > 0) {
                  out.name("staceTrace");
                  out.beginArray();
                  for (StackTraceElement item : staceTrace) {
                    writeStaceTraceElement(out, item);
                  }
                  out.endArray();
                }
                out.endObject();
              }

              public StackTraceElement readStaceTraceElement(JsonReader in) throws IOException {
                in.beginObject();
                String className = null;
                String methodName = null;
                String fileName = null;
                Integer lineNumber = null;
                while (in.hasNext()) {
                  String name = in.nextName();
                  if (name.equals("className")) {
                    className = in.nextString();
                  } else if (name.equals("methodName")) {
                    methodName = in.nextString();
                  } else if (name.equals("fileName")) {
                    fileName = in.nextString();
                  } else if (name.equals("lineNumber")) {
                    lineNumber = in.nextInt();
                  } else {
                    in.skipValue();
                  }
                }
                in.endObject();
                StackTraceElement retval =
                    new StackTraceElement(className, methodName, fileName, lineNumber);
                return retval;
              }

              public void writeStaceTraceElement(JsonWriter out, StackTraceElement value)
                  throws IOException {
                out.beginObject();
                out.name("className");
                out.value(value.getClassName());
                out.name("methodName");
                out.value(value.getMethodName());
                out.name("fileName");
                out.value(value.getFileName());
                out.name("lineNumber");
                out.value(value.getLineNumber());
                out.endObject();
              }
            };
    return adapter;
  }
}
