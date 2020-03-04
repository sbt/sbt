/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.scriptedtest;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import xsbti.AppConfiguration;
import xsbti.AppMain;
import xsbti.AppProvider;
import xsbti.ApplicationID;
import xsbti.ComponentProvider;
import xsbti.CrossValue;
import xsbti.GlobalLock;
import xsbti.Launcher;
import xsbti.MavenRepository;
import xsbti.Predefined;
import xsbti.PredefinedRepository;
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
      String[] arguments)
      throws InvocationTargetException, ClassNotFoundException, NoSuchMethodException,
          IllegalAccessException, IOException {
    String[] args = arguments;
    Object appID = null;
    if (System.getProperty("sbt.launch.jar") == null) {
      final ClassLoader previous = Thread.currentThread().getContextClassLoader();
      final URL configURL = URLForClass(xsbti.AppConfiguration.class);
      final URL mainURL = URLForClass(sbt.xMain.class);
      final URL scriptedURL = URLForClass(ScriptedLauncher.class);
      final ClassLoader topLoader = new URLClassLoader(new URL[] {configURL}, top());
      final URLClassLoader loader = new URLClassLoader(new URL[] {mainURL, scriptedURL}, topLoader);
      try {
        while (true) {
          final Class<?> clazz = loader.loadClass("sbt.internal.scriptedtest.ScriptedLauncher");
          final Class<?> reboot = loader.loadClass("xsbti.Reboot");
          final Class<?> exit = loader.loadClass("xsbti.Exit");

          Method method =
              clazz.getDeclaredMethod(
                  "getConf",
                  ClassLoader.class,
                  File.class,
                  String.class,
                  String.class,
                  File.class,
                  File.class,
                  File[].class,
                  String[].class,
                  loader.loadClass("xsbti.ApplicationID"));
          Thread.currentThread().setContextClassLoader(loader);
          try {
            final Object conf =
                method.invoke(
                    null,
                    topLoader,
                    scalaHome,
                    sbtVersion,
                    scalaVersion,
                    bootDirectory,
                    baseDir,
                    classpath,
                    args,
                    appID);
            final Object launchResult =
                clazz
                    .getDeclaredMethod(
                        "launchImpl", ClassLoader.class, ClassLoader.class, Object.class)
                    .invoke(null, topLoader, loader, conf);
            if (reboot.isAssignableFrom(launchResult.getClass())) {
              final Object a = reboot.getDeclaredMethod("arguments").invoke(launchResult);
              final int length = Array.getLength(a);
              args = new String[length];
              for (int j = 0; j < length; ++j) {
                args[j] = (String) Array.get(a, j);
              }
              appID = reboot.getDeclaredMethod("app").invoke(launchResult);
            } else if (exit.isAssignableFrom(launchResult.getClass())) {
              return Optional.of((Integer) exit.getDeclaredMethod("code").invoke(launchResult));
            }
          } catch (final InvocationTargetException e) {
            Throwable t = e.getCause();
            while (t != null && !t.getClass().getCanonicalName().equals("xsbti.FullReload"))
              t = t.getCause();
            final RuntimeException reload = t == null ? null : (RuntimeException) t;
            if (reload != null) {
              final boolean clean =
                  (boolean) reload.getClass().getDeclaredMethod("clean").invoke(reload);
              if (clean) deleteRecursive(bootDirectory);
              final Object reloadArgs =
                  reload.getClass().getDeclaredMethod("arguments").invoke(reload);
              throw new xsbti.FullReload((String[]) reloadArgs, true);
            }
            if (e.getCause() instanceof RuntimeException) throw (RuntimeException) e.getCause();
            throw new RuntimeException(e.getCause());
          }
        }
      } finally {
        swap(loader, previous);
      }
    } else {
      final URL url = new URL("file:" + System.getProperty("sbt.launch.jar"));
      final URLClassLoader loader = new URLClassLoader(new URL[] {url}, top());
      final Class<?> boot = loader.loadClass("xsbt.boot.Boot");
      // If we don't initialize the arguments this way, then the call to invoke on
      // xsbt.boot.Boot.main fails with an IllegalArgumentException
      final Object newArgs = Array.newInstance(loader.loadClass("java.lang.String"), args.length);
      for (int i = 0; i < args.length; ++i) ((String[]) newArgs)[i] = args[i];
      final ClassLoader previous = Thread.currentThread().getContextClassLoader();

      try {
        Thread.currentThread().setContextClassLoader(loader);
        boot.getDeclaredMethod("main", newArgs.getClass()).invoke(null, newArgs);
        return Optional.empty();
      } finally {
        swap(loader, previous);
      }
    }
  }

  private static ClassLoader top() {
    ClassLoader result = ClassLoader.getSystemClassLoader();
    while (result.getParent() != null) result = result.getParent();
    return result;
  }

  private static void swap(final URLClassLoader old, final ClassLoader stashed) {
    try {
      old.close();
    } catch (final Exception e) {
    }
    Thread.currentThread().setContextClassLoader(stashed);
  }

  private static boolean copy(final File[] files, final File toDirectory) throws IOException {
    boolean result = true;
    for (final File file : files) {
      try {
        Files.createDirectories(toDirectory.toPath());
        Files.copy(file.toPath(), toDirectory.toPath().resolve(file.getName()));
      } catch (final FileAlreadyExistsException e) {
        result = false;
      }
    }
    return result;
  }

  @SuppressWarnings("unused")
  public static Object launchImpl(
      final ClassLoader topLoader, final ClassLoader loader, final Object conf)
      throws ClassNotFoundException, InvocationTargetException, IllegalAccessException,
          NoSuchMethodException, InstantiationException {
    final Class<?> clazz = loader.loadClass("sbt.xMain");
    final Object instance = clazz.getConstructor().newInstance();
    final Method run = clazz.getDeclaredMethod("run", loader.loadClass("xsbti.AppConfiguration"));
    return run.invoke(instance, conf);
  }

  @SuppressWarnings("unused")
  public static AppConfiguration getConf(
      final ClassLoader topLoader,
      final File scalaHome,
      final String sbtVersion,
      final String scalaVersion,
      final File bootDirectory,
      final File baseDir,
      final File[] classpath,
      String[] args,
      final ApplicationID appID) {

    final File libDir = new File(scalaHome, "lib");
    final AtomicReference<File[]> classpathExtra = new AtomicReference<>(new File[0]);
    final ApplicationID id =
        appID != null
            ? appID
            : new ApplicationID() {
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
                return classpathExtra.get();
              }
            };
    final File appHome =
        scalaHome.toPath().resolve(id.groupID()).resolve(id.name()).resolve(id.version()).toFile();
    final ComponentProvider provider =
        new ComponentProvider() {
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
              try {
                copy(components, dir);
              } catch (final IOException e) {
                e.printStackTrace(System.err);
              }
            }
          }

          @Override
          public boolean addToComponent(String componentID, File[] components) {
            try {
              boolean result = copy(components, componentLocation(componentID));
              final File[] extra = componentLocation(componentID).listFiles();
              classpathExtra.set(extra == null ? new File[0] : extra);
              return result;
            } catch (final IOException e) {
              return true;
            }
          }

          @Override
          public File lockFile() {
            return new File(appHome, "sbt.components.lock");
          }
        };
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
                    new Repository[] {
                      (PredefinedRepository) () -> Predefined.Local,
                      (PredefinedRepository) () -> Predefined.MavenCentral,
                      newMavenRepo(
                          "scala-ea",
                          "https://scala-ci.typesafe.com/artifactory/scala-integration/"),
                      newMavenRepo(
                          "scala-pr",
                          "https://scala-ci.typesafe.com/artifactory/scala-pr-validation-snapshots/")
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
              return (AppMain) loader().loadClass("sbt.xMain").getConstructor().newInstance();
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
            return provider;
          }
        };
      }
    };
  }

  private static void deleteRecursive(final File directory) {
    try {
      Files.walkFileTree(
          directory.toPath(),
          new FileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
              return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                throws IOException {
              if (attrs.isRegularFile()) Files.deleteIfExists(file);
              return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
              return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc)
                throws IOException {
              Files.deleteIfExists(dir);
              return FileVisitResult.CONTINUE;
            }
          });
    } catch (final IOException e) {
    }
  }

  private static MavenRepository newMavenRepo(final String id, final String url) {
    return new MavenRepository() {
      @Override
      public String id() {
        return id;
      }

      @Override
      public URL url() {
        try {
          return new URL(url);
        } catch (MalformedURLException e) {
          throw new RuntimeException(e);
        }
      }
    };
  }
}
