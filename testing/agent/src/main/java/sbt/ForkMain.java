/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt;

import sbt.testing.*;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.Socket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;

final public class ForkMain {

	// serializables
	// -----------------------------------------------------------------------------

	final static class SubclassFingerscan implements SubclassFingerprint, Serializable {
		private final boolean isModule;
		private final String superclassName;
		private final boolean requireNoArgConstructor;
		SubclassFingerscan(final SubclassFingerprint print) {
			isModule = print.isModule();
			superclassName = print.superclassName();
			requireNoArgConstructor = print.requireNoArgConstructor();
		}
		public boolean isModule() { return isModule; }
		public String superclassName() { return superclassName; }
		public boolean requireNoArgConstructor() { return requireNoArgConstructor; }
	}

	final static class AnnotatedFingerscan implements AnnotatedFingerprint, Serializable {
		private final boolean isModule;
		private final String annotationName;
		AnnotatedFingerscan(final AnnotatedFingerprint print) {
			isModule = print.isModule();
			annotationName = print.annotationName();
		}
		public boolean isModule() { return isModule; }
		public String annotationName() { return annotationName; }
	}

	final static class ForkEvent implements Event, Serializable {
		private final String fullyQualifiedName;
		private final Fingerprint fingerprint;
		private final Selector selector;
		private final Status status;
		private final OptionalThrowable throwable;
		private final long duration;

		ForkEvent(final Event e) {
			fullyQualifiedName = e.fullyQualifiedName();
			final Fingerprint rawFingerprint = e.fingerprint();

			if (rawFingerprint instanceof SubclassFingerprint)
				fingerprint = new SubclassFingerscan((SubclassFingerprint) rawFingerprint);
			else
				fingerprint = new AnnotatedFingerscan((AnnotatedFingerprint) rawFingerprint);

			selector = e.selector();
			checkSerializableSelector(selector);
			status = e.status();
			final OptionalThrowable originalThrowable = e.throwable();

			if (originalThrowable.isDefined())
				throwable = new OptionalThrowable(new ForkError(originalThrowable.get()));
			else
				throwable = originalThrowable;

			duration = e.duration();
		}

		public String fullyQualifiedName() { return fullyQualifiedName; }
		public Fingerprint fingerprint() { return fingerprint; }
		public Selector selector() { return selector; }
		public Status status() { return status; }
		public OptionalThrowable throwable() { return throwable; }
		public long duration() { return duration; }

		private static void checkSerializableSelector(final Selector selector) {
			if (! (selector instanceof Serializable)) {
				throw new UnsupportedOperationException("Selector implementation must be Serializable, but " + selector.getClass().getName() + " is not.");
			}
		}
	}

	// -----------------------------------------------------------------------------


	final static class ForkError extends Exception {
		private final String originalMessage;
		private final String originalName;
		private ForkError cause;
		ForkError(final Throwable t) {
			originalMessage = t.getMessage();
			originalName = t.getClass().getName();
			setStackTrace(t.getStackTrace());
			if (t.getCause() != null) cause = new ForkError(t.getCause());
		}
		public String getMessage() { return originalName + ": " + originalMessage; }
		public Exception getCause() { return cause; }
	}


	// main
	// ----------------------------------------------------------------------------------------------------------------

	public static void main(final String[] args) throws Exception {
		final Socket socket = new Socket(InetAddress.getByName(null), Integer.valueOf(args[0]));
		final ObjectInputStream is = new ObjectInputStream(socket.getInputStream());
		final ObjectOutputStream os = new ObjectOutputStream(socket.getOutputStream());
		// Must flush the header that the constructor writes, otherwise the ObjectInputStream on the other end may block indefinitely
		os.flush();
		try {
			new Run().run(is, os);
		} finally {
			try {
				is.close();
				os.close();
			} finally {
				System.exit(0);
			}
		}
	}

	// ----------------------------------------------------------------------------------------------------------------


	final private static class Run {

		private void run(final ObjectInputStream is, final ObjectOutputStream os) {
			try {
				runTests(is, os);
			} catch (final RunAborted e) {
				internalError(e);
			} catch (final Throwable t) {
				try {
					logError(os, "Uncaught exception when running tests: " + t.toString());
					write(os, new ForkError(t));
				} catch (final Throwable t2) {
					internalError(t2);
				}
			}
		}

		private boolean matches(final Fingerprint f1, final Fingerprint f2) {
			if (f1 instanceof SubclassFingerprint && f2 instanceof SubclassFingerprint) {
				final SubclassFingerprint sf1 = (SubclassFingerprint) f1;
				final SubclassFingerprint sf2 = (SubclassFingerprint) f2;
				return sf1.isModule() == sf2.isModule() && sf1.superclassName().equals(sf2.superclassName());
			} else if (f1 instanceof AnnotatedFingerprint && f2 instanceof AnnotatedFingerprint) {
				final AnnotatedFingerprint af1 = (AnnotatedFingerprint) f1;
				final AnnotatedFingerprint af2 = (AnnotatedFingerprint) f2;
				return af1.isModule() == af2.isModule() && af1.annotationName().equals(af2.annotationName());
			}
			return false;
		}

		class RunAborted extends RuntimeException {
			RunAborted(final Exception e) { super(e); }
		}

		private synchronized void write(final ObjectOutputStream os, final Object obj) {
			try {
				os.writeObject(obj);
				os.flush();
			} catch (final IOException e) {
				throw new RunAborted(e);
			}
		}

		private void log(final ObjectOutputStream os, final String message, final ForkTags level) {
			write(os, new Object[]{level, message});
		}

		private void logDebug(final ObjectOutputStream os, final String message) { log(os, message, ForkTags.Debug); }
		private void logInfo(final ObjectOutputStream os, final String message) { log(os, message, ForkTags.Info); }
		private void logWarn(final ObjectOutputStream os, final String message) { log(os, message, ForkTags.Warn); }
		private void logError(final ObjectOutputStream os, final String message) { log(os, message, ForkTags.Error); }

		private Logger remoteLogger(final boolean ansiCodesSupported, final ObjectOutputStream os) {
			return new Logger() {
				public boolean ansiCodesSupported() { return ansiCodesSupported; }
				public void error(final String s) { logError(os, s); }
				public void warn(final String s) { logWarn(os, s); }
				public void info(final String s) { logInfo(os, s); }
				public void debug(final String s) { logDebug(os, s); }
				public void trace(final Throwable t) { write(os, new ForkError(t)); }
			};
		}

		private void writeEvents(final ObjectOutputStream os, final TaskDef taskDef, final ForkEvent[] events) {
			write(os, new Object[]{taskDef.fullyQualifiedName(), events});
		}

		private ExecutorService executorService(final ForkConfiguration config, final ObjectOutputStream os) {
			if(config.isParallel()) {
				final int nbThreads = Runtime.getRuntime().availableProcessors();
				logDebug(os, "Create a test executor with a thread pool of " + nbThreads + " threads.");
				// more options later...
				// TODO we might want to configure the blocking queue with size #proc
				return Executors.newFixedThreadPool(nbThreads);
			} else {
				logDebug(os, "Create a single-thread test executor");
				return Executors.newSingleThreadExecutor();
			}
		}

		private void runTests(final ObjectInputStream is, final ObjectOutputStream os) throws Exception {
			final ForkConfiguration config = (ForkConfiguration) is.readObject();
			final ExecutorService executor = executorService(config, os);
			final TaskDef[] tests = (TaskDef[]) is.readObject();
			final int nFrameworks = is.readInt();
			final Logger[] loggers = { remoteLogger(config.isAnsiCodesSupported(), os) };

			for (int i = 0; i < nFrameworks; i++) {
				final String[] implClassNames = (String[]) is.readObject();
				final String[] frameworkArgs = (String[]) is.readObject();
				final String[] remoteFrameworkArgs = (String[]) is.readObject();

				Framework framework = null;
				for (final String implClassName : implClassNames) {
					try {
						final Object rawFramework = Class.forName(implClassName).getDeclaredConstructor().newInstance();
						if (rawFramework instanceof Framework)
							framework = (Framework) rawFramework;
						else
							framework = new FrameworkWrapper((org.scalatools.testing.Framework) rawFramework);
						break;
					} catch (final ClassNotFoundException e) {
						logDebug(os, "Framework implementation '" + implClassName + "' not present.");
					}
				}

				if (framework == null)
					continue;

				final ArrayList<TaskDef> filteredTests = new ArrayList<TaskDef>();
				for (final Fingerprint testFingerprint : framework.fingerprints()) {
					for (final TaskDef test : tests) {
						// TODO: To pass in correct explicitlySpecified and selectors
						if (matches(testFingerprint, test.fingerprint()))
							filteredTests.add(new TaskDef(test.fullyQualifiedName(), test.fingerprint(), test.explicitlySpecified(), test.selectors()));
					}
				}
				final Runner runner = framework.runner(frameworkArgs, remoteFrameworkArgs, getClass().getClassLoader());
				final Task[] tasks = runner.tasks(filteredTests.toArray(new TaskDef[filteredTests.size()]));
				logDebug(os, "Runner for " + framework.getClass().getName() + " produced " + tasks.length + " initial tasks for " + filteredTests.size() + " tests.");

				runTestTasks(executor, tasks, loggers, os);

				runner.done();
			}
			write(os, ForkTags.Done);
			is.readObject();
		}

		private void runTestTasks(final ExecutorService executor, final Task[] tasks, final Logger[] loggers, final ObjectOutputStream os) {
			if( tasks.length > 0 ) {
				final List<Future<Task[]>> futureNestedTasks = new ArrayList<Future<Task[]>>();
				for( final Task task : tasks ) {
					futureNestedTasks.add(runTest(executor, task, loggers, os));
				}

				// Note: this could be optimized further, we could have a callback once a test finishes that executes immediately the nested tasks
				//       At the moment, I'm especially interested in JUnit, which doesn't have nested tasks.
				final List<Task> nestedTasks = new ArrayList<Task>();
				for( final Future<Task[]> futureNestedTask : futureNestedTasks ) {
					try {
						nestedTasks.addAll( Arrays.asList(futureNestedTask.get()));
					} catch (final Exception e) {
						logError(os, "Failed to execute task " + futureNestedTask);
					}
				}
				runTestTasks(executor, nestedTasks.toArray(new Task[nestedTasks.size()]), loggers, os);
			}
		}

		private Future<Task[]> runTest(final ExecutorService executor, final Task task, final Logger[] loggers, final ObjectOutputStream os) {
			return executor.submit(new Callable<Task[]>() {
				@Override
				public Task[] call() {
					ForkEvent[] events;
					Task[] nestedTasks;
					final TaskDef taskDef = task.taskDef();
					try {
						final List<ForkEvent> eventList = new ArrayList<ForkEvent>();
						final EventHandler handler = new EventHandler() { public void handle(final Event e){ eventList.add(new ForkEvent(e)); } };
						logDebug(os, "  Running " + taskDef);
						nestedTasks = task.execute(handler, loggers);
						if(nestedTasks.length > 0 || eventList.size() > 0)
							logDebug(os, "    Produced " + nestedTasks.length + " nested tasks and " + eventList.size() + " events.");
						events = eventList.toArray(new ForkEvent[eventList.size()]);
					}
					catch (final Throwable t) {
						nestedTasks = new Task[0];
						events = new ForkEvent[] { testError(os, taskDef, "Uncaught exception when running " + taskDef.fullyQualifiedName() + ": " + t.toString(), t) };
					}
					writeEvents(os, taskDef, events);
					return nestedTasks;
				}
			});
		}

		private void internalError(final Throwable t) {
			System.err.println("Internal error when running tests: " + t.toString());
		}

		private ForkEvent testEvent(final String fullyQualifiedName, final Fingerprint fingerprint, final Selector selector, final Status r, final ForkError err, final long duration) {
			final OptionalThrowable throwable;
			if (err == null)
				throwable = new OptionalThrowable();
			else
				throwable = new OptionalThrowable(err);
			return new ForkEvent(new Event() {
				public String fullyQualifiedName() { return fullyQualifiedName; }
				public Fingerprint fingerprint() { return fingerprint; }
				public Selector selector() { return selector; }
				public Status status() { return r; }
				public OptionalThrowable throwable() {
					return throwable;
				}
				public long duration() {
					return duration;
				}
			});
		}

		private ForkEvent testError(final ObjectOutputStream os, final TaskDef taskDef, final String message, final Throwable t) {
			logError(os, message);
			final ForkError fe = new ForkError(t);
			write(os, fe);
			return testEvent(taskDef.fullyQualifiedName(), taskDef.fingerprint(), new SuiteSelector(), Status.Error, fe, 0);
		}
	}
}
