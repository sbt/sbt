/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.worker1;

import org.scalasbt.shadedgson.com.google.gson.Gson;

import sbt.testing.*;

import java.io.IOException;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.concurrent.*;

public class ForkTestMain {

  // serializables
  // -----------------------------------------------------------------------------

  public static final class SubclassFingerscan implements SubclassFingerprint, Serializable {
    private final boolean isModule;
    private final String superclassName;
    private final boolean requireNoArgConstructor;

    public SubclassFingerscan(final SubclassFingerprint print) {
      isModule = print.isModule();
      superclassName = print.superclassName();
      requireNoArgConstructor = print.requireNoArgConstructor();
    }

    public boolean isModule() {
      return isModule;
    }

    public String superclassName() {
      return superclassName;
    }

    public boolean requireNoArgConstructor() {
      return requireNoArgConstructor;
    }
  }

  public static final class AnnotatedFingerscan implements AnnotatedFingerprint, Serializable {
    private final boolean isModule;
    private final String annotationName;

    public AnnotatedFingerscan(final AnnotatedFingerprint print) {
      isModule = print.isModule();
      annotationName = print.annotationName();
    }

    public boolean isModule() {
      return isModule;
    }

    public String annotationName() {
      return annotationName;
    }
  }

  public static final class ForkEvent implements Event, Serializable {
    private final String fullyQualifiedName;
    private final Fingerprint fingerprint;
    private final Selector selector;
    private final Status status;
    private final OptionalThrowable throwable;
    private final long duration;

    ForkEvent(final Event e) {
      this.fullyQualifiedName = e.fullyQualifiedName();
      final Fingerprint rawFingerprint = e.fingerprint();

      if (rawFingerprint instanceof SubclassFingerprint)
        this.fingerprint = new SubclassFingerscan((SubclassFingerprint) rawFingerprint);
      else this.fingerprint = new AnnotatedFingerscan((AnnotatedFingerprint) rawFingerprint);

      this.selector = e.selector();
      checkSerializableSelector(selector);
      this.status = e.status();
      final OptionalThrowable originalThrowable = e.throwable();

      if (originalThrowable.isDefined())
        this.throwable = new OptionalThrowable(new ForkError(originalThrowable.get()));
      else this.throwable = originalThrowable;

      this.duration = e.duration();
    }

    public String fullyQualifiedName() {
      return fullyQualifiedName;
    }

    public Fingerprint fingerprint() {
      return fingerprint;
    }

    public Selector selector() {
      return selector;
    }

    public Status status() {
      return status;
    }

    public OptionalThrowable throwable() {
      return throwable;
    }

    public long duration() {
      return duration;
    }

    private static void checkSerializableSelector(final Selector selector) {
      if (!(selector instanceof Serializable)) {
        throw new UnsupportedOperationException(
            "Selector implementation must be Serializable, but "
                + selector.getClass().getName()
                + " is not.");
      }
    }
  }

  public static class ForkEventsInfo implements Serializable {
    public long id;
    public String group;
    public ArrayList<ForkEvent> events;

    public ForkEventsInfo(long id, String group, ArrayList<ForkEvent> events) {
      this.id = id;
      this.group = group;
      this.events = events;
    }
  }

  // -----------------------------------------------------------------------------

  public static final class ForkError extends Exception {
    private final String originalMessage;
    private final String originalName;
    private ForkError cause1;

    ForkError(final Throwable t) {
      originalMessage = t.getMessage();
      originalName = t.getClass().getName();
      setStackTrace(t.getStackTrace());
      if (t.getCause() != null) cause1 = new ForkError(t.getCause());
    }

    public String getMessage() {
      return originalName + ": " + originalMessage;
    }

    public Exception getCause() {
      return cause1;
    }
  }

  public static class ForkErrorInfo implements Serializable {
    public long id;
    public ForkError error;

    public ForkErrorInfo(long id, ForkError error) {
      this.id = id;
      this.error = error;
    }
  }

  // main
  // ----------------------------------------------------------------------------------------------------------------

  public static void main(long id, TestInfo info, PrintStream originalOut, ClassLoader classLoader)
      throws Exception {
    new Run(originalOut, id).run(info, classLoader);
  }

  // ----------------------------------------------------------------------------------------------------------------

  public static final class Run {
    final PrintStream originalOut;
    final long id;
    final Gson gson;

    Run(PrintStream originalOut, long id) {
      this.originalOut = originalOut;
      this.id = id;
      this.gson = WorkerMain.mkGson();
    }

    private void run(TestInfo info, ClassLoader classLoader) {
      try {
        runTests(info, classLoader);
      } catch (final RunAborted e) {
        internalError(e);
      } catch (final Throwable t) {
        try {
          logError("Uncaught exception when running tests: " + t.toString());
          writeError(new ForkError(t));
        } catch (final Throwable t2) {
          internalError(t2);
        }
      }
    }

    private boolean matches(final Fingerprint f1, final Fingerprint f2) {
      if (f1 instanceof SubclassFingerprint && f2 instanceof SubclassFingerprint) {
        final SubclassFingerprint sf1 = (SubclassFingerprint) f1;
        final SubclassFingerprint sf2 = (SubclassFingerprint) f2;
        return sf1.isModule() == sf2.isModule()
            && sf1.superclassName().equals(sf2.superclassName());
      } else if (f1 instanceof AnnotatedFingerprint && f2 instanceof AnnotatedFingerprint) {
        final AnnotatedFingerprint af1 = (AnnotatedFingerprint) f1;
        final AnnotatedFingerprint af2 = (AnnotatedFingerprint) f2;
        return af1.isModule() == af2.isModule()
            && af1.annotationName().equals(af2.annotationName());
      }
      return false;
    }

    class RunAborted extends RuntimeException {
      RunAborted(final Exception e) {
        super(e);
      }
    }

    private void writeError(ForkError error) {
      ForkErrorInfo info = new ForkErrorInfo(this.id, error);
      String params = this.gson.toJson(info, ForkErrorInfo.class);
      String notification =
          String.format(
              "{ \"jsonrpc\": \"2.0\", \"method\": \"forkError\", \"params\": %s }", params);
      this.originalOut.println(notification);
      this.originalOut.flush();
    }

    private void log(final String message, final ForkTags level) {
      TestLogInfo info = new TestLogInfo(this.id, level, message);
      String params = this.gson.toJson(info, TestLogInfo.class);
      String notification =
          String.format(
              "{ \"jsonrpc\": \"2.0\", \"method\": \"testLog\", \"params\": %s }", params);
      this.originalOut.println(notification);
      this.originalOut.flush();
    }

    private void logDebug(final String message) {
      log(message, ForkTags.Debug);
    }

    private void logInfo(final String message) {
      log(message, ForkTags.Info);
    }

    private void logWarn(final String message) {
      log(message, ForkTags.Warn);
    }

    private void logError(final String message) {
      log(message, ForkTags.Error);
    }

    private Logger remoteLogger(final boolean ansiCodesSupported) {
      return new Logger() {
        public boolean ansiCodesSupported() {
          return ansiCodesSupported;
        }

        public void error(final String s) {
          logError(s);
        }

        public void warn(final String s) {
          logWarn(s);
        }

        public void info(final String s) {
          logInfo(s);
        }

        public void debug(final String s) {
          logDebug(s);
        }

        public void trace(final Throwable t) {
          writeError(new ForkError(t));
        }
      };
    }

    private void writeEvents(final TaskDef taskDef, final ForkEvent[] events) {
      ForkEventsInfo info =
          new ForkEventsInfo(
              this.id,
              taskDef.fullyQualifiedName(),
              new ArrayList<ForkEvent>(Arrays.asList(events)));
      String params = this.gson.toJson(info, ForkEventsInfo.class);
      String notification =
          String.format(
              "{ \"jsonrpc\": \"2.0\", \"method\": \"testEvents\", \"params\": %s }", params);
      this.originalOut.println(notification);
      this.originalOut.flush();
    }

    private ExecutorService executorService(final boolean parallel, final Integer parallelism) {
      if (parallel) {
        final int nbThreads =
            (parallelism != null && parallelism > 0)
                ? parallelism
                : Runtime.getRuntime().availableProcessors();
        logDebug("Create a test executor with a thread pool of " + nbThreads + " threads.");
        return Executors.newFixedThreadPool(nbThreads);
      } else {
        logDebug("Create a single-thread test executor");
        return Executors.newSingleThreadExecutor();
      }
    }

    private void runTests(TestInfo info, ClassLoader classLoader) throws Exception {
      Thread.currentThread().setContextClassLoader(classLoader);
      final ExecutorService executor = executorService(info.parallel, info.parallelism);
      final TaskDef[] tests = info.taskDefs.toArray(new TaskDef[] {});
      final int nFrameworks = info.testRunners.size();
      final Logger[] loggers = {remoteLogger(info.ansiCodesSupported)};

      for (TestInfo.TestRunner testRunner : info.testRunners) {
        final String[] frameworkArgs = testRunner.mainRunnerArgs.toArray(new String[] {});
        final String[] remoteFrameworkArgs =
            testRunner.mainRunnerRemoteArgs.toArray(new String[] {});

        Framework framework = null;
        for (final String implClassName : testRunner.implClassNames) {
          try {
            final Object rawFramework =
                classLoader.loadClass(implClassName).getDeclaredConstructor().newInstance();
            if (rawFramework instanceof Framework) framework = (Framework) rawFramework;
            else framework = new FrameworkWrapper((org.scalatools.testing.Framework) rawFramework);
            break;
          } catch (final ClassNotFoundException e) {
            logError("Framework implementation '" + implClassName + "' not present.");
          }
        }

        if (framework == null) continue;

        final LinkedHashSet<TaskDef> filteredTests = new LinkedHashSet<>();
        for (final Fingerprint testFingerprint : framework.fingerprints()) {
          for (final TaskDef test : tests) {
            // TODO: To pass in correct explicitlySpecified and selectors
            if (matches(testFingerprint, test.fingerprint()))
              filteredTests.add(
                  new TaskDef(
                      test.fullyQualifiedName(),
                      test.fingerprint(),
                      test.explicitlySpecified(),
                      test.selectors()));
          }
        }
        final Runner runner = framework.runner(frameworkArgs, remoteFrameworkArgs, classLoader);
        final Task[] tasks = runner.tasks(filteredTests.toArray(new TaskDef[filteredTests.size()]));
        logDebug(
            "Runner for "
                + framework.getClass().getName()
                + " produced "
                + tasks.length
                + " initial tasks for "
                + filteredTests.size()
                + " tests.");

        Thread callDoneOnShutdown = new Thread(() -> runner.done());
        Runtime.getRuntime().addShutdownHook(callDoneOnShutdown);

        runTestTasks(executor, tasks, loggers);

        runner.done();

        Runtime.getRuntime().removeShutdownHook(callDoneOnShutdown);
      }
    }

    private void runTestTasks(
        final ExecutorService executor, final Task[] tasks, final Logger[] loggers) {
      if (tasks.length > 0) {
        final List<Future<Task[]>> futureNestedTasks = new ArrayList<>();
        for (final Task task : tasks) {
          futureNestedTasks.add(runTest(executor, task, loggers));
        }

        // Note: this could be optimized further, we could have a callback once a test finishes that
        // executes immediately the nested tasks
        //       At the moment, I'm especially interested in JUnit, which doesn't have nested tasks.
        final List<Task> nestedTasks = new ArrayList<>();
        for (final Future<Task[]> futureNestedTask : futureNestedTasks) {
          try {
            nestedTasks.addAll(Arrays.asList(futureNestedTask.get()));
          } catch (final Exception e) {
            logError("Failed to execute task " + futureNestedTask);
          }
        }
        runTestTasks(executor, nestedTasks.toArray(new Task[nestedTasks.size()]), loggers);
      }
    }

    private Future<Task[]> runTest(
        final ExecutorService executor, final Task task, final Logger[] loggers) {
      return executor.submit(
          () -> {
            ForkEvent[] events;
            Task[] nestedTasks;
            final TaskDef taskDef = task.taskDef();
            try {
              final Collection<ForkEvent> eventList = new ConcurrentLinkedDeque<>();
              final EventHandler handler =
                  new EventHandler() {
                    public void handle(final Event e) {
                      eventList.add(new ForkEvent(e));
                    }
                  };
              logDebug("  Running " + taskDef);
              nestedTasks = task.execute(handler, loggers);
              if (nestedTasks.length > 0 || eventList.size() > 0)
                logDebug(
                    "    Produced "
                        + nestedTasks.length
                        + " nested tasks and "
                        + eventList.size()
                        + " events.");
              events = eventList.toArray(new ForkEvent[eventList.size()]);
            } catch (final Throwable t) {
              nestedTasks = new Task[0];
              events =
                  new ForkEvent[] {
                    testError(
                        taskDef,
                        "Uncaught exception when running "
                            + taskDef.fullyQualifiedName()
                            + ": "
                            + t.toString(),
                        t)
                  };
            }
            writeEvents(taskDef, events);
            return nestedTasks;
          });
    }

    private void internalError(final Throwable t) {
      System.err.println("Internal error when running tests: " + t.toString());
    }

    private ForkEvent testEvent(
        final String fullyQualifiedName,
        final Fingerprint fingerprint,
        final Selector selector,
        final Status r,
        final ForkError err,
        final long duration) {
      final OptionalThrowable throwable;
      if (err == null) throwable = new OptionalThrowable();
      else throwable = new OptionalThrowable(err);
      return new ForkEvent(
          new Event() {
            public String fullyQualifiedName() {
              return fullyQualifiedName;
            }

            public Fingerprint fingerprint() {
              return fingerprint;
            }

            public Selector selector() {
              return selector;
            }

            public Status status() {
              return r;
            }

            public OptionalThrowable throwable() {
              return throwable;
            }

            public long duration() {
              return duration;
            }
          });
    }

    private ForkEvent testError(final TaskDef taskDef, final String message, final Throwable t) {
      logError(message);
      final ForkError fe = new ForkError(t);
      writeError(fe);
      return testEvent(
          taskDef.fullyQualifiedName(),
          taskDef.fingerprint(),
          new SuiteSelector(),
          Status.Error,
          fe,
          0);
    }
  }
}
