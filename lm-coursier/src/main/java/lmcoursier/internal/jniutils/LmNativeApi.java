// Copied from windows-jni-utils-lmcoursier in https://github.com/coursier/jni-utils at tag v0.5.0.
// See jniUtilsVersion in project/Dependencies.scala.
package lmcoursier.internal.jniutils;

import coursier.jniutils.LoadWindowsLibrary;
import coursier.jniutils.NativeApi;

public final class LmNativeApi extends NativeApi {

  static native String terminalSizeNative();

  static native String enableAnsiOutputNative();

  static native String terminalSizeForStdHandleNative(int stdHandle);

  static native String enableAnsiOutputForStdHandleNative(int stdHandle);

  static native String GetUserEnvironmentVariableNative(String key);

  static native String SetUserEnvironmentVariableNative(String key, String value);

  static native String DeleteUserEnvironmentVariableNative(String key);

  static native String GetKnownFolderPathNative(String rfid);

  static native String GetModuleFileNameNative();

  static native int GetOEMCodePageNative();

  static native int GetACPNative();

  static native int GetConsoleOutputCPNative();

  @Override
  public String terminalSize() {
    return terminalSizeNative();
  }

  @Override
  public String enableAnsiOutput() {
    return enableAnsiOutputNative();
  }

  @Override
  public String terminalSizeForStdHandle(int stdHandle) {
    return terminalSizeForStdHandleNative(stdHandle);
  }

  @Override
  public String enableAnsiOutputForStdHandle(int stdHandle) {
    return enableAnsiOutputForStdHandleNative(stdHandle);
  }

  @Override
  public String GetUserEnvironmentVariable(String key) {
    return GetUserEnvironmentVariableNative(key);
  }

  @Override
  public String SetUserEnvironmentVariable(String key, String value) {
    return SetUserEnvironmentVariableNative(key, value);
  }

  @Override
  public String DeleteUserEnvironmentVariable(String key) {
    return DeleteUserEnvironmentVariableNative(key);
  }

  @Override
  public String GetKnownFolderPath(String rfid) {
    return GetKnownFolderPathNative(rfid);
  }

  @Override
  public String GetModuleFileName() {
    return GetModuleFileNameNative();
  }

  @Override
  public int GetOEMCodePage() {
    return GetOEMCodePageNative();
  }

  @Override
  public int GetACP() {
    return GetACPNative();
  }

  @Override
  public int GetConsoleOutputCP() {
    return GetConsoleOutputCPNative();
  }

  static {
    LoadWindowsLibrary.ensureInitialized("lmcoursier");
  }

  public static void setup() {
    NativeApi.set(new LmNativeApi());
  }
}
