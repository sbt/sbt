/* sbt -- Simple Build Tool
 * Copyright 2012 Eugene Vigdorchik
 */
package sbt;

import org.scalatools.testing.*;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.Socket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

public class ForkMain {
  public static enum Tags {
			Error, Warn, Info, Debug, Done;
	}

  static class SubclassFingerscan implements TestFingerprint, Serializable {
    private boolean isModule;
    private String superClassName;
    SubclassFingerscan(SubclassFingerprint print) {
      isModule = print.isModule();
      superClassName = print.superClassName();
    }
    public boolean isModule() { return isModule; }
    public String superClassName() { return superClassName; }
  }
  static class AnnotatedFingerscan implements AnnotatedFingerprint, Serializable {
    private boolean isModule;
    private String annotationName;
    AnnotatedFingerscan(AnnotatedFingerprint print) {
      isModule = print.isModule();
      annotationName = print.annotationName();
    }
    public boolean isModule() { return isModule; }
    public String annotationName() { return annotationName; }
  }
  public static class ForkTestDefinition implements Serializable {
    public String name;
    public Fingerprint fingerprint;

    public ForkTestDefinition(String name, Fingerprint fingerprint) {
      this.name = name;
      if (fingerprint instanceof SubclassFingerprint) {
        this.fingerprint = new SubclassFingerscan((SubclassFingerprint) fingerprint);
      } else {
        this.fingerprint = new AnnotatedFingerscan((AnnotatedFingerprint) fingerprint);
      }
    }
  }
  static class ForkEvent implements Event, Serializable {
    private String testName;
    private String description;
    private Result result;
    ForkEvent(Event e) {
      testName = e.testName();
      description = e.description();
      result = e.result();
    }
    public String testName() { return testName; }
    public String description() { return description; }
    public Result result() { return result;}
    public Throwable error() { return null; }
  }
  public static void main(String[] args) throws Exception {
    Socket socket = new Socket(InetAddress.getByName(null), Integer.valueOf(args[0]));
		final ObjectInputStream is = new ObjectInputStream(socket.getInputStream());
		final ObjectOutputStream os = new ObjectOutputStream(socket.getOutputStream());
		try {
			new Run().run(is, os);
		} finally {
			is.close();
			os.close();	
		}
  }
  private static class Run {
    boolean matches(Fingerprint f1, Fingerprint f2) {
      if (f1 instanceof SubclassFingerprint && f2 instanceof SubclassFingerprint) {
        final SubclassFingerprint sf1 = (SubclassFingerprint) f1;
        final SubclassFingerprint sf2 = (SubclassFingerprint) f2;
        return sf1.isModule() == sf2.isModule() && sf1.superClassName().equals(sf2.superClassName());
      } else if (f1 instanceof AnnotatedFingerprint && f2 instanceof AnnotatedFingerprint) {
        AnnotatedFingerprint af1 = (AnnotatedFingerprint) f1;
        AnnotatedFingerprint af2 = (AnnotatedFingerprint) f2;
        return af1.isModule() == af2.isModule() && af1.annotationName().equals(af2.annotationName());
      }
      return false;
    }
    void run(ObjectInputStream is, final ObjectOutputStream os) throws Exception {
			final boolean ansiCodesSupported = is.readBoolean();
      Logger[] loggers = {
          new Logger() {
            public boolean ansiCodesSupported() { return ansiCodesSupported; }
            void write(Object obj) {
              try {
                os.writeObject(obj);
              } catch (IOException e) {
                System.err.println("Cannot write to socket");
              }
            }
						public void error(String s) { write(new Object[]{Tags.Error, s});  }
            public void warn(String s) { write(new Object[]{Tags.Warn, s}); }
            public void info(String s) { write(new Object[]{Tags.Info, s}); }
            public void debug(String s) { write(new Object[]{Tags.Debug, s}); }
            public void trace(Throwable t) { write(t); }
          }
      };

      final ForkTestDefinition[] tests = (ForkTestDefinition[]) is.readObject();
      int nFrameworks = is.readInt();
      for (int i = 0; i < nFrameworks; i++) {
        final Framework framework;
        final String implClassName = (String) is.readObject();
        try {
          framework = (Framework) Class.forName(implClassName).newInstance();
        } catch (ClassNotFoundException e) {
          System.err.println("Framework implementation '" + implClassName + "' not present.");
          continue;
        }

        final String[] frameworkArgs = (String[]) is.readObject();

        ArrayList<ForkTestDefinition> filteredTests = new ArrayList<ForkTestDefinition>();
        for (Fingerprint testFingerprint : framework.tests()) {
          for (ForkTestDefinition test : tests) {
            if (matches(testFingerprint, test.fingerprint)) filteredTests.add(test);
          }
        }
        final org.scalatools.testing.Runner runner = framework.testRunner(getClass().getClassLoader(), loggers);
        for (ForkTestDefinition test : filteredTests) {
          final List<ForkEvent> events = new ArrayList<ForkEvent>();
          EventHandler handler = new EventHandler() { public void handle(Event e){ events.add(new ForkEvent(e)); } };
          if (runner instanceof Runner2) {
            ((Runner2) runner).run(test.name, test.fingerprint, handler, frameworkArgs);
          } else if (test.fingerprint instanceof TestFingerprint) {
            runner.run(test.name, (TestFingerprint) test.fingerprint, handler, frameworkArgs);
          } else {
            System.err.println("Framework '" + framework + "' does not support test '" + test.name + "'");
          }
          os.writeObject(events.toArray(new ForkEvent[events.size()]));
        }
      }
      os.writeObject(Tags.Done);
			is.readObject();
    }
  }
}
