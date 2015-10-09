package xsbti.compile;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import xsbti.Maybe;
import xsbti.Reporter;

/**
 * Helper object for xsbti.compile.IncOptions
 */
public class IncOptionsUtil {
  /**
   * 1. recompile changed sources
   * 2. recompile direct dependencies and transitive public inheritance dependencies of sources with API changes in 1.
   * 3. further changes invalidate all dependencies transitively to avoid too many steps.
   */
  public static int defaultTransitiveStep() {
    return 3;
  }

  public static double defaultRecompileAllFraction() {
    return 0.5;
  }

  public static boolean defaultRelationsDebug() {
    return false;
  }

  public static boolean defaultApiDebug() {
    return false;
  }

  public static int defaultApiDiffContextSize() {
    return 5;
  }

  public static Maybe<File> defaultApiDumpDirectory() {
    return Maybe.<File>nothing();
  }

  public static Maybe<String> defaultClassfileManagerType() {
    return Maybe.<String>nothing();
  }

  public static Maybe<Boolean> defaultRecompileOnMacroDef() {
    return Maybe.<Boolean>nothing();
  }

  public static boolean defaultRecompileOnMacroDefImpl() {
    return true;
  }

  public static boolean getRecompileOnMacroDef(IncOptions options) {
    if (options.recompileOnMacroDef().isDefined()) {
      return options.recompileOnMacroDef().get();
    } else {
      return defaultRecompileOnMacroDefImpl();
    }
  }

  public static boolean defaultNameHashing() {
    return true;
  }

  public static boolean defaultAntStyle() {
    return false;
  }

  public static Map<String, String> defaultExtra() {
    return new HashMap<String, String>();
  }

  public static IncOptions defaultIncOptions() {
    IncOptions retval = new IncOptions(
      defaultTransitiveStep(), defaultRecompileAllFraction(),
      defaultRelationsDebug(), defaultApiDebug(),
      defaultApiDiffContextSize(), defaultApiDumpDirectory(),
      defaultClassfileManagerType(), defaultRecompileOnMacroDef(),
      defaultNameHashing(), defaultAntStyle(),
      defaultExtra());
    return retval;
  }
}
