package xsbti;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** A global in-memory storage to pass information. */
public class Attic {
  private static List<String> _items = new ArrayList<String>();

  /**
   * This is used to collect scalacOptions of metabuild. This works around the fact that
   * CompilationInfo.XmacroSettings is experimental.
   */
  public static void appendItems(Collection<String> values) {
    _items.addAll(values);
  }

  /**
   * This is used to return scalacOptions of metabuild. This works around the fact that
   * CompilationInfo.XmacroSettings is experimental.
   */
  public static Collection<String> getItems() {
    return _items;
  }
}
