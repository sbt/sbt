/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.scriptedtest;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import xsbti.AppConfiguration;
import xsbti.AppMain;
import xsbti.AppProvider;
import xsbti.ApplicationID;
import xsbti.ComponentProvider;
import xsbti.CrossValue;
import xsbti.Exit;
import xsbti.GlobalLock;
import xsbti.Launcher;
import xsbti.MainResult;
import xsbti.Predefined;
import xsbti.PredefinedRepository;
import xsbti.Reboot;
import xsbti.Repository;
import xsbti.ScalaProvider;

public class ScriptedLauncher {
  private static URL URLForClass(final Class<?> clazz)
      throws MalformedURLException, ClassNotFoundException {
    final String path = clazz.getCanonicalName().replace('.', '/') + ".class";
    final URL url = clazz.getClassLoader().getResource(path);
    if (url == null) throw new ClassNotFoundException(clazz.getCanonicalName());
    return new URL(url.toString().replaceAll(path + "$", ""));
  }

  public static Optional<Integer> launch(
      final File scalaHome,
      final String sbtVersion,
      final String scalaVersion,
      final File bootDirectory,
      final File baseDir,
      final File[] classpath,
      String[] args)
      throws MalformedURLException, InvocationTargetException, ClassNotFoundException,
          IllegalAccessException {
    while (true) {
      final URL configURL = URLForClass(xsbti.AppConfiguration.class);
      final URL mainURL = URLForClass(sbt.xMain.class);
      final URL scriptedURL = URLForClass(ScriptedLauncher.class);
      final ClassLoader topLoader =
          new URLClassLoader(new URL[] {configURL}, ClassLoader.getSystemClassLoader().getParent());
      final URLClassLoader loader = new URLClassLoader(new URL[] {mainURL, scriptedURL}, topLoader);
      final ClassLoader previous = Thread.currentThread().getContextClassLoader();
      try {
        Thread.currentThread().setContextClassLoader(loader);
        final AtomicInteger result = new AtomicInteger(-1);
        final AtomicReference<String[]> newArguments = new AtomicReference<>();
        final Class<?> clazz = loader.loadClass("sbt.internal.scriptedtest.ScriptedLauncher");
        Method method = null;
        for (final Method m : clazz.getDeclaredMethods()) {
          if (m.getName().equals("launchImpl")) method = m;
        }
        method.invoke(
            null,
            topLoader,
            loader,
            scalaHome,
            sbtVersion,
            scalaVersion,
            bootDirectory,
            baseDir,
            classpath,
            args,
            result,
            newArguments);
        final int res = result.get();
        if (res >= 0) return res == Integer.MAX_VALUE ? Optional.empty() : Optional.of(res);
        else args = newArguments.get();
      } finally {
        try {
          loader.close();
        } catch (final Exception e) {
        }
        Thread.currentThread().setContextClassLoader(previous);
      }
    }
  }

  private static void copy(final File[] files, final File toDirectory) {
    for (final File file : files) {
      try {
        Files.createDirectories(toDirectory.toPath());
        Files.copy(file.toPath(), toDirectory.toPath().resolve(file.getName()));
      } catch (final IOException e) {
        e.printStackTrace(System.err);
      }
    }
  }

  @SuppressWarnings("unused")
  public static void launchImpl(
      final ClassLoader topLoader,
      final ClassLoader loader,
      final File scalaHome,
      final String sbtVersion,
      final String scalaVersion,
      final File bootDirectory,
      final File baseDir,
      final File[] classpath,
      final String[] args,
      final AtomicInteger result,
      final AtomicReference<String[]> newArguments)
      throws ClassNotFoundException, InvocationTargetException, IllegalAccessException,
          InstantiationException {
    final AppConfiguration conf =
        getConf(
            topLoader,
            scalaHome,
            sbtVersion,
            scalaVersion,
            bootDirectory,
            baseDir,
            classpath,
            args);
    final Class<?> clazz = loader.loadClass("sbt.xMain");
    final Object instance = clazz.newInstance();
    Method run = null;
    for (final Method m : clazz.getDeclaredMethods()) {
      if (m.getName().equals("run")) run = m;
    }
    final Object runResult = run.invoke(instance, conf);
    if (runResult instanceof xsbti.Reboot) newArguments.set(((Reboot) runResult).arguments());
    else {
      if (runResult instanceof xsbti.Exit) {
        result.set(((Exit) runResult).code());
      } else if (runResult instanceof xsbti.Continue) {
        result.set(Integer.MAX_VALUE);
      } else {
        handleUnknownMainResult((MainResult) runResult);
      }
    }
  }

  private static void handleUnknownMainResult(MainResult x) {
    final String clazz = x == null ? "" : " (class: " + x.getClass() + ")";
    System.err.println("Invalid main result: " + x + clazz);
    System.exit(1);
  }

  public static AppConfiguration getConf(
      final ClassLoader topLoader,
      final File scalaHome,
      final String sbtVersion,
      final String scalaVersion,
      final File bootDirectory,
      final File baseDir,
      final File[] classpath,
      String[] args) {

    final File libDir = new File(scalaHome, "lib");
    final ApplicationID id =
        new ApplicationID() {
          @Override
          public String groupID() {
            return "org.scala-sbt";
          }

          @Override
          public String name() {
            return "sbt";
          }

          @Override
          public String version() {
            return sbtVersion;
          }

          @Override
          public String mainClass() {
            return "sbt.xMain";
          }

          @Override
          public String[] mainComponents() {
            return new String[] {"xsbti", "extra"};
          }

          @Deprecated
          @Override
          public boolean crossVersioned() {
            return false;
          }

          @Override
          public CrossValue crossVersionedValue() {
            return CrossValue.Disabled;
          }

          @Override
          public File[] classpathExtra() {
            return new File[0];
          }
        };
    final File appHome =
        scalaHome.toPath().resolve(id.groupID()).resolve(id.name()).resolve(id.version()).toFile();
    assert (libDir.exists());
    final File[] jars = libDir.listFiles(f -> f.isFile() && f.getName().endsWith(".jar"));
    final URL[] urls = new URL[jars.length];
    for (int i = 0; i < jars.length; ++i) {
      try {
        urls[i] = jars[i].toURI().toURL();
      } catch (final IOException e) {
        throw new RuntimeException(e);
      }
    }
    return new AppConfiguration() {
      @Override
      public String[] arguments() {
        return args;
      }

      @Override
      public File baseDirectory() {
        return baseDir;
      }

      @Override
      public AppProvider provider() {
        return new AppProvider() {
          final AppProvider self = this;
          final ScalaProvider scalaProvider =
              new ScalaProvider() {
                private final ScalaProvider sp = this;
                private final String scalaOrg = "org.scala-lang";
                private final Repository[] repos =
                    new PredefinedRepository[] {
                      () -> Predefined.Local, () -> Predefined.MavenCentral
                    };
                private final Launcher launcher =
                    new Launcher() {
                      @Override
                      public ScalaProvider getScala(String version) {
                        return getScala(version, "");
                      }

                      @Override
                      public ScalaProvider getScala(String version, String reason) {
                        return getScala(version, reason, scalaOrg);
                      }

                      @Override
                      public ScalaProvider getScala(
                          String version, String reason, String scalaOrg) {
                        return sp;
                      }

                      @Override
                      public AppProvider app(ApplicationID id, String version) {
                        return self;
                      }

                      @Override
                      public ClassLoader topLoader() {
                        return topLoader;
                      }

                      class foo extends Throwable {
                        foo(final Exception e) {
                          super(e.getMessage(), null, true, false);
                        }
                      }

                      @Override
                      public GlobalLock globalLock() {
                        return new GlobalLock() {
                          @Override
                          public <T> T apply(File lockFile, Callable<T> run) {
                            try {
                              return run.call();
                            } catch (final Exception e) {
                              throw new RuntimeException(new foo(e)) {
                                @Override
                                public StackTraceElement[] getStackTrace() {
                                  return new StackTraceElement[0];
                                }
                              };
                            }
                          }
                        };
                      }

                      @Override
                      public File bootDirectory() {
                        return bootDirectory;
                      }

                      @Override
                      public Repository[] ivyRepositories() {
                        return repos;
                      }

                      @Override
                      public Repository[] appRepositories() {
                        return repos;
                      }

                      @Override
                      public boolean isOverrideRepositories() {
                        return false;
                      }

                      @Override
                      public File ivyHome() {
                        final String home = System.getProperty("sbt.ivy.home");
                        return home == null
                            ? new File(System.getProperty("user.home"), ".ivy2")
                            : new File(home);
                      }

                      @Override
                      public String[] checksums() {
                        return new String[] {"sha1", "md5"};
                      }
                    };

                @Override
                public Launcher launcher() {
                  return launcher;
                }

                @Override
                public String version() {
                  return scalaVersion;
                }

                @Override
                public ClassLoader loader() {
                  return new URLClassLoader(urls, topLoader);
                }

                @Override
                public File[] jars() {
                  return jars;
                }

                @Deprecated
                @Override
                public File libraryJar() {
                  return new File(libDir, "scala-library.jar");
                }

                @Deprecated
                @Override
                public File compilerJar() {
                  return new File(libDir, "scala-compiler.jar");
                }

                @Override
                public AppProvider app(ApplicationID id) {
                  return self;
                }
              };

          @Override
          public ScalaProvider scalaProvider() {
            return scalaProvider;
          }

          @Override
          public ApplicationID id() {
            return id;
          }

          @Override
          public ClassLoader loader() {
            return new URLClassLoader(urls, topLoader);
          }

          @Deprecated
          @Override
          public Class<? extends AppMain> mainClass() {
            return AppMain.class;
          }

          @Override
          public Class<?> entryPoint() {
            return AppMain.class;
          }

          @Override
          public AppMain newMain() {
            try {
              return (AppMain) loader().loadClass("sbt.xMain").newInstance();
            } catch (final Exception e) {
              throw new RuntimeException(e);
            }
          }

          @Override
          public File[] mainClasspath() {
            return classpath;
          }

          @Override
          public ComponentProvider components() {
            return new ComponentProvider() {
              @Override
              public File componentLocation(String id) {
                return new File(appHome, id);
              }

              @Override
              public File[] component(String componentID) {
                final File dir = componentLocation(componentID);
                final File[] files = dir.listFiles(File::isFile);
                return files == null ? new File[0] : files;
              }

              @Override
              public void defineComponent(String componentID, File[] components) {
                final File dir = componentLocation(componentID);
                if (dir.exists()) {
                  final StringBuilder files = new StringBuilder();
                  for (final File file : components) {
                    if (files.length() > 0) {
                      files.append(',');
                    }
                    files.append(file.toString());
                  }
                  throw new RuntimeException(
                      "Cannot redefine component. ID: " + id + ", files: " + files);
                } else {
                  copy(components, dir);
                }
              }

              @Override
              public boolean addToComponent(String componentID, File[] components) {
                copy(components, componentLocation(componentID));
                return false;
              }

              @Override
              public File lockFile() {
                return new File(appHome, "sbt.components.lock");
              }
            };
          }
        };
      }
    };
  }
}
