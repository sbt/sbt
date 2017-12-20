// Copied from https://github.com/facebook/nailgun/blob/af623fddedfdca010df46302a0711ce0e2cc1ba6/nailgun-server/src/main/java/com/martiansoftware/nailgun/NGWin32NamedPipeLibrary.java

/*

 Copyright 2004-2017, Martian Software, Inc.

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.

 */
package sbt.internal;

import java.nio.ByteBuffer;

import com.sun.jna.*;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.WinNT.*;
import com.sun.jna.platform.win32.WinBase.*;
import com.sun.jna.ptr.IntByReference;

import com.sun.jna.win32.W32APIOptions;

public interface NGWin32NamedPipeLibrary extends WinNT {
    int PIPE_ACCESS_DUPLEX = 3;
    int PIPE_UNLIMITED_INSTANCES = 255;
    int FILE_FLAG_FIRST_PIPE_INSTANCE = 524288;

    NGWin32NamedPipeLibrary INSTANCE =
            (NGWin32NamedPipeLibrary) Native.loadLibrary(
                    "kernel32",
                    NGWin32NamedPipeLibrary.class,
                    W32APIOptions.UNICODE_OPTIONS);

    HANDLE CreateNamedPipe(
            String lpName,
            int dwOpenMode,
            int dwPipeMode,
            int nMaxInstances,
            int nOutBufferSize,
            int nInBufferSize,
            int nDefaultTimeOut,
            SECURITY_ATTRIBUTES lpSecurityAttributes);
    boolean ConnectNamedPipe(
            HANDLE hNamedPipe,
            Pointer lpOverlapped);
    boolean DisconnectNamedPipe(
            HANDLE hObject);
    boolean ReadFile(
            HANDLE hFile,
            Memory lpBuffer,
            int nNumberOfBytesToRead,
            IntByReference lpNumberOfBytesRead,
            Pointer lpOverlapped);
    boolean WriteFile(
            HANDLE hFile,
            ByteBuffer lpBuffer,
            int nNumberOfBytesToWrite,
            IntByReference lpNumberOfBytesWritten,
            Pointer lpOverlapped);
    boolean CloseHandle(
            HANDLE hObject);
    boolean GetOverlappedResult(
            HANDLE hFile,
            Pointer lpOverlapped,
            IntByReference lpNumberOfBytesTransferred,
            boolean wait);
    boolean CancelIoEx(
            HANDLE hObject,
            Pointer lpOverlapped);
    HANDLE CreateEvent(
            SECURITY_ATTRIBUTES lpEventAttributes,
            boolean bManualReset,
            boolean bInitialState,
            String lpName);
    int WaitForSingleObject(
            HANDLE hHandle,
            int dwMilliseconds
    );

    int GetLastError();
}
