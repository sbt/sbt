/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt;

import sbt.testing.*;

/**
 * Adapts the old {@link org.scalatools.testing.Framework} interface into the new
 * {@link sbt.testing.Framework}
 */
final class FrameworkWrapper implements Framework {

	private final org.scalatools.testing.Framework oldFramework;

	FrameworkWrapper(final org.scalatools.testing.Framework oldFramework) {
		this.oldFramework = oldFramework;
	}

	public String name() {
		return oldFramework.name();
	}

	public Fingerprint[] fingerprints() {
		final org.scalatools.testing.Fingerprint[] oldFingerprints = oldFramework.tests();
		final int length = oldFingerprints.length;
		final Fingerprint[] fingerprints = new Fingerprint[length];
		for (int i=0; i < length; i++) {
			final org.scalatools.testing.Fingerprint oldFingerprint = oldFingerprints[i];
			if (oldFingerprint instanceof org.scalatools.testing.TestFingerprint)
				fingerprints[i] = new SubclassFingerprintWrapper((org.scalatools.testing.TestFingerprint) oldFingerprint);
			else if (oldFingerprint instanceof org.scalatools.testing.SubclassFingerprint)
				fingerprints[i] = new SubclassFingerprintWrapper((org.scalatools.testing.SubclassFingerprint) oldFingerprint);
			else
				fingerprints[i] = new AnnotatedFingerprintWrapper((org.scalatools.testing.AnnotatedFingerprint) oldFingerprint);
		}
		return fingerprints;
	}

	public Runner runner(final String[] args, final String[] remoteArgs, final ClassLoader testClassLoader) {
		return new RunnerWrapper(oldFramework, testClassLoader, args);
	}
}

final class SubclassFingerprintWrapper implements SubclassFingerprint {
	private final String superclassName;
	private final boolean isModule;
	private final boolean requireNoArgConstructor;

	SubclassFingerprintWrapper(final org.scalatools.testing.SubclassFingerprint oldFingerprint) {
		superclassName = oldFingerprint.superClassName();
		isModule = oldFingerprint.isModule();
		requireNoArgConstructor = false;  // Old framework SubclassFingerprint does not require no arg constructor
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

final class AnnotatedFingerprintWrapper implements AnnotatedFingerprint {
	private final String annotationName;
	private final boolean isModule;

	AnnotatedFingerprintWrapper(final org.scalatools.testing.AnnotatedFingerprint oldFingerprint) {
		annotationName = oldFingerprint.annotationName();
		isModule = oldFingerprint.isModule();
	}

	public boolean isModule() {
		return isModule;
	}

	public String annotationName() {
		return annotationName;
	}
}

final class EventHandlerWrapper implements org.scalatools.testing.EventHandler {

	private final EventHandler newEventHandler;
	private final String fullyQualifiedName;
	private final Fingerprint fingerprint;

	EventHandlerWrapper(final EventHandler newEventHandler, final String fullyQualifiedName, final Fingerprint fingerprint) {
		this.newEventHandler = newEventHandler;
		this.fullyQualifiedName = fullyQualifiedName;
		this.fingerprint = fingerprint;
	}

	public void handle(final org.scalatools.testing.Event oldEvent) {
		newEventHandler.handle(new EventWrapper(oldEvent, fullyQualifiedName, fingerprint));
	}
}

final class EventWrapper implements Event {

	private final org.scalatools.testing.Event oldEvent;
	private final String className;
	private final Fingerprint fingerprint;
	private final OptionalThrowable throwable;

	EventWrapper(final org.scalatools.testing.Event oldEvent, final String className, final Fingerprint fingerprint) {
		this.oldEvent = oldEvent;
		this.className = className;
		this.fingerprint = fingerprint;
		final Throwable oldThrowable = oldEvent.error();
		if (oldThrowable == null)
			throwable = new OptionalThrowable();
		else
			throwable = new OptionalThrowable(oldThrowable);
	}

	public String fullyQualifiedName() {
		return className;
	}

	public Fingerprint fingerprint() {
		return fingerprint;
	}

	public Selector selector() {
		return new TestSelector(oldEvent.testName());
	}

	public Status status() {
		switch (oldEvent.result()) {
			case Success:
				return Status.Success;
			case Error:
				return Status.Error;
			case Failure:
				return Status.Failure;
			case Skipped:
				return Status.Skipped;
			default:
				throw new IllegalStateException("Invalid status.");
		}
	}

	public OptionalThrowable throwable() {
		return throwable;
	}

	public long duration() {
		return -1;  // Just return -1 as old event does not have duration.
	}
}

final class RunnerWrapper implements Runner {

	private final org.scalatools.testing.Framework oldFramework;
	private final ClassLoader testClassLoader;
	private final String[] args;

	RunnerWrapper(final org.scalatools.testing.Framework oldFramework, final ClassLoader testClassLoader, final String[] args) {
		this.oldFramework = oldFramework;
		this.testClassLoader = testClassLoader;
		this.args = args;
	}

	public Task[] tasks(final TaskDef[] taskDefs) {
		final int length = taskDefs.length;
		final Task[] tasks = new Task[length];
		for (int i = 0; i < length; i++) {
			final TaskDef taskDef = taskDefs[i];
			tasks[i] = createTask(taskDef);
		}
		return tasks;
	}

	private Task createTask(final TaskDef taskDef) {
		return new Task() {
			public String[] tags() {
				return new String[0];  // Old framework does not support tags
			}

			private org.scalatools.testing.Logger createOldLogger(final Logger logger) {
				return new org.scalatools.testing.Logger() {
					public boolean ansiCodesSupported() { return logger.ansiCodesSupported(); }
					public void error(final String msg) { logger.error(msg); }
					public void warn(final String msg) { logger.warn(msg); }
					public void info(final String msg) { logger.info(msg); }
					public void debug(final String msg) { logger.debug(msg); }
					public void trace(final Throwable t) { logger.trace(t); }
				};
			}

			private void runRunner(final org.scalatools.testing.Runner runner, final Fingerprint fingerprint, final EventHandler eventHandler) {
				// Old runner only support subclass fingerprint.
				final SubclassFingerprint subclassFingerprint = (SubclassFingerprint) fingerprint;
				final org.scalatools.testing.TestFingerprint oldFingerprint =
					new org.scalatools.testing.TestFingerprint() {
						public boolean isModule() { return subclassFingerprint.isModule(); }
						public String superClassName() { return subclassFingerprint.superclassName(); }
					};
				final String name = taskDef.fullyQualifiedName();
				runner.run(name, oldFingerprint, new EventHandlerWrapper(eventHandler, name, subclassFingerprint), args);
			}

			private void runRunner2(final org.scalatools.testing.Runner2 runner, final Fingerprint fingerprint, final EventHandler eventHandler) {
				final org.scalatools.testing.Fingerprint oldFingerprint;
				if (fingerprint instanceof SubclassFingerprint) {
					final SubclassFingerprint subclassFingerprint = (SubclassFingerprint) fingerprint;
					oldFingerprint = new org.scalatools.testing.SubclassFingerprint() {
						public boolean isModule() { return subclassFingerprint.isModule(); }
						public String superClassName() { return subclassFingerprint.superclassName(); }
					};
				}
				else {
					final AnnotatedFingerprint annotatedFingerprint = (AnnotatedFingerprint) fingerprint;
					oldFingerprint = new org.scalatools.testing.AnnotatedFingerprint() {
						public boolean isModule() { return annotatedFingerprint.isModule(); }
						public String annotationName() { return annotatedFingerprint.annotationName(); }
					};
				}
				final String name = taskDef.fullyQualifiedName();
				runner.run(name, oldFingerprint, new EventHandlerWrapper(eventHandler, name, fingerprint), args);
			}

			public Task[] execute(final EventHandler eventHandler, final Logger[] loggers) {
				final int length = loggers.length;
				final org.scalatools.testing.Logger[] oldLoggers = new org.scalatools.testing.Logger[length];
				for (int i=0; i<length; i++) {
					oldLoggers[i] = createOldLogger(loggers[i]);
				}

				final org.scalatools.testing.Runner runner = oldFramework.testRunner(testClassLoader, oldLoggers);
				final Fingerprint fingerprint = taskDef.fingerprint();
				if (runner instanceof org.scalatools.testing.Runner2) {
					runRunner2((org.scalatools.testing.Runner2) runner, fingerprint, eventHandler);
				}
				else {
					runRunner(runner, fingerprint, eventHandler);
				}
				return new Task[0];
			}

			public TaskDef taskDef() {
				return taskDef;
			}
		};
	}

	public String done() {
		return "";
	}

	public String[] args() {
		return args;
	}

	public String[] remoteArgs() {
		return new String[0];  // Old framework does not support remoteArgs
	}
}
