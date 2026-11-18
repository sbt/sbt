/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.worker1;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.function.BiFunction;
import org.scalasbt.shadedgson.com.google.gson.Gson;
import org.scalasbt.shadedgson.com.google.gson.GsonBuilder;
import org.scalasbt.shadedgson.com.google.gson.JsonDeserializationContext;
import org.scalasbt.shadedgson.com.google.gson.JsonDeserializer;
import org.scalasbt.shadedgson.com.google.gson.JsonObject;
import org.scalasbt.shadedgson.com.google.gson.reflect.TypeToken;
import sbt.internal.worker1.ForkTestMain.AnnotatedFingerscan;
import sbt.internal.worker1.ForkTestMain.ForkEvent;
import sbt.internal.worker1.ForkTestMain.SubclassFingerscan;
import sbt.internal.worker1.TestInfo.TestRunner;
import sbt.testing.Fingerprint;
import sbt.testing.OptionalThrowable;
import sbt.testing.Selector;
import sbt.testing.Status;
import sbt.testing.TaskDef;

final class TestPayloadAdapters {
  private static final Type STRINGS = new TypeToken<ArrayList<String>>() {}.getType();
  private static final Type TASK_DEFS = new TypeToken<ArrayList<TaskDef>>() {}.getType();
  private static final Type TEST_RUNNERS = new TypeToken<ArrayList<TestRunner>>() {}.getType();

  private TestPayloadAdapters() {}

  static GsonBuilder register(GsonBuilder builder) {
    register(builder, TestInfo.class, TestPayloadAdapters::readTestInfo);
    register(builder, TestRunner.class, TestPayloadAdapters::readTestRunner);
    register(builder, TestLogInfo.class, TestPayloadAdapters::readTestLogInfo);
    register(builder, ForkEvent.class, TestPayloadAdapters::readForkEvent);
    return builder;
  }

  private static <T> void register(
      GsonBuilder builder,
      Class<T> type,
      BiFunction<JsonObject, JsonDeserializationContext, T> reader) {
    JsonDeserializer<T> adapter =
        (json, ignored, context) -> reader.apply(json.getAsJsonObject(), context);
    builder.registerTypeAdapter(type, adapter);
  }

  private static TestInfo readTestInfo(JsonObject json, JsonDeserializationContext context) {
    return new TestInfo(
        Boolean.TRUE.equals(context.deserialize(json.get("jvm"), Boolean.class)),
        context.deserialize(json.get("jvmRunInfo"), RunInfo.JvmRunInfo.class),
        context.deserialize(json.get("nativeRunInfo"), RunInfo.NativeRunInfo.class),
        Boolean.TRUE.equals(context.deserialize(json.get("ansiCodesSupported"), Boolean.class)),
        Boolean.TRUE.equals(context.deserialize(json.get("parallel"), Boolean.class)),
        context.deserialize(json.get("parallelism"), Integer.class),
        context.deserialize(json.get("taskDefs"), TASK_DEFS),
        context.deserialize(json.get("testRunners"), TEST_RUNNERS));
  }

  private static TestRunner readTestRunner(JsonObject json, JsonDeserializationContext context) {
    return new TestRunner(
        context.deserialize(json.get("implClassNames"), STRINGS),
        context.deserialize(json.get("mainRunnerArgs"), STRINGS),
        context.deserialize(json.get("mainRunnerRemoteArgs"), STRINGS));
  }

  private static TestLogInfo readTestLogInfo(JsonObject json, JsonDeserializationContext context) {
    return new TestLogInfo(
        longOrZero(context.deserialize(json.get("id"), Long.class)),
        context.deserialize(json.get("tag"), ForkTags.class),
        context.deserialize(json.get("message"), String.class));
  }

  private static ForkEvent readForkEvent(JsonObject json, JsonDeserializationContext context) {
    return new ForkEvent(
        context.deserialize(json.get("fullyQualifiedName"), String.class),
        context.deserialize(json.get("fingerprint"), Fingerprint.class),
        context.deserialize(json.get("selector"), Selector.class),
        context.deserialize(json.get("status"), Status.class),
        context.deserialize(json.get("throwable"), OptionalThrowable.class),
        longOrZero(context.deserialize(json.get("duration"), Long.class)));
  }

  static SubclassFingerscan readSubclassFingerscan(Gson gson, JsonObject json) {
    return new SubclassFingerscan(
        Boolean.TRUE.equals(gson.fromJson(json.get("isModule"), Boolean.class)),
        gson.fromJson(json.get("superclassName"), String.class),
        Boolean.TRUE.equals(gson.fromJson(json.get("requireNoArgConstructor"), Boolean.class)));
  }

  static AnnotatedFingerscan readAnnotatedFingerscan(Gson gson, JsonObject json) {
    return new AnnotatedFingerscan(
        Boolean.TRUE.equals(gson.fromJson(json.get("isModule"), Boolean.class)),
        gson.fromJson(json.get("annotationName"), String.class));
  }

  private static long longOrZero(Long value) {
    return value == null ? 0L : value;
  }
}
