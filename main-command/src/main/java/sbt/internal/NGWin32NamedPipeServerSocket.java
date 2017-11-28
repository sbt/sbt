// Copied from https://github.com/facebook/nailgun/blob/af623fddedfdca010df46302a0711ce0e2cc1ba6/nailgun-server/src/main/java/com/martiansoftware/nailgun/NGWin32NamedPipeServerSocket.java

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

import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinError;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.ptr.IntByReference;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

public class NGWin32NamedPipeServerSocket extends ServerSocket {
    private static final NGWin32NamedPipeLibrary API = NGWin32NamedPipeLibrary.INSTANCE;
    private static final String WIN32_PIPE_PREFIX = "\\\\.\\pipe\\";
    private static final int BUFFER_SIZE = 65535;
    private final LinkedBlockingQueue<HANDLE> openHandles;
    private final LinkedBlockingQueue<HANDLE> connectedHandles;
    private final NGWin32NamedPipeSocket.CloseCallback closeCallback;
    private final String path;
    private final int maxInstances;
    private final HANDLE lockHandle;

    public NGWin32NamedPipeServerSocket(String path) throws IOException {
        this(NGWin32NamedPipeLibrary.PIPE_UNLIMITED_INSTANCES, path);
    }

    public NGWin32NamedPipeServerSocket(int maxInstances, String path) throws IOException {
        this.openHandles = new LinkedBlockingQueue<>();
        this.connectedHandles = new LinkedBlockingQueue<>();
        this.closeCallback = handle -> {
            if (connectedHandles.remove(handle)) {
                closeConnectedPipe(handle, false);
            }
            if (openHandles.remove(handle)) {
                closeOpenPipe(handle);
            }
        };
        this.maxInstances = maxInstances;
        if (!path.startsWith(WIN32_PIPE_PREFIX)) {
            this.path = WIN32_PIPE_PREFIX + path;
        } else {
            this.path = path;
        }
        String lockPath = this.path + "_lock";
        lockHandle = API.CreateNamedPipe(
                lockPath,
                NGWin32NamedPipeLibrary.FILE_FLAG_FIRST_PIPE_INSTANCE | NGWin32NamedPipeLibrary.PIPE_ACCESS_DUPLEX,
                0,
                1,
                BUFFER_SIZE,
                BUFFER_SIZE,
                0,
                null);
        if (lockHandle == NGWin32NamedPipeLibrary.INVALID_HANDLE_VALUE) {
            throw new IOException(String.format("Could not create lock for %s, error %d", lockPath, API.GetLastError()));
        } else {
            if (!API.DisconnectNamedPipe(lockHandle)) {
                throw new IOException(String.format("Could not disconnect lock %d", API.GetLastError()));
            }
        }

    }

    public void bind(SocketAddress endpoint) throws IOException {
        throw new IOException("Win32 named pipes do not support bind(), pass path to constructor");
    }

    public Socket accept() throws IOException {
        HANDLE handle = API.CreateNamedPipe(
                path,
                NGWin32NamedPipeLibrary.PIPE_ACCESS_DUPLEX | WinNT.FILE_FLAG_OVERLAPPED,
                0,
                maxInstances,
                BUFFER_SIZE,
                BUFFER_SIZE,
                0,
                null);
        if (handle == NGWin32NamedPipeLibrary.INVALID_HANDLE_VALUE) {
            throw new IOException(String.format("Could not create named pipe, error %d", API.GetLastError()));
        }
        openHandles.add(handle);

        HANDLE connWaitable = API.CreateEvent(null, true, false, null);
        WinBase.OVERLAPPED olap = new WinBase.OVERLAPPED();
        olap.hEvent = connWaitable;
        olap.write();

        boolean immediate = API.ConnectNamedPipe(handle, olap.getPointer());
        if (immediate) {
            openHandles.remove(handle);
            connectedHandles.add(handle);
            return new NGWin32NamedPipeSocket(handle, closeCallback);
        }

        int connectError = API.GetLastError();
        if (connectError == WinError.ERROR_PIPE_CONNECTED) {
            openHandles.remove(handle);
            connectedHandles.add(handle);
            return new NGWin32NamedPipeSocket(handle, closeCallback);
        } else if (connectError == WinError.ERROR_NO_DATA) {
            // Client has connected and disconnected between CreateNamedPipe() and ConnectNamedPipe()
            // connection is broken, but it is returned it avoid loop here.
            // Actual error will happen for NGSession when it will try to read/write from/to pipe
            return new NGWin32NamedPipeSocket(handle, closeCallback);
        } else if (connectError == WinError.ERROR_IO_PENDING) {
            if (!API.GetOverlappedResult(handle, olap.getPointer(), new IntByReference(), true)) {
                openHandles.remove(handle);
                closeOpenPipe(handle);
                throw new IOException("GetOverlappedResult() failed for connect operation: " + API.GetLastError());
            }
            openHandles.remove(handle);
            connectedHandles.add(handle);
            return new NGWin32NamedPipeSocket(handle, closeCallback);
        } else {
            throw new IOException("ConnectNamedPipe() failed with: " + connectError);
        }
    }

    public void close() throws IOException {
        try {
            List<HANDLE> handlesToClose = new ArrayList<>();
            openHandles.drainTo(handlesToClose);
            for (HANDLE handle : handlesToClose) {
                closeOpenPipe(handle);
            }

            List<HANDLE> handlesToDisconnect = new ArrayList<>();
            connectedHandles.drainTo(handlesToDisconnect);
            for (HANDLE handle : handlesToDisconnect) {
                closeConnectedPipe(handle, true);
            }
        } finally {
            API.CloseHandle(lockHandle);
        }
    }

    private void closeOpenPipe(HANDLE handle) throws IOException {
        API.CancelIoEx(handle, null);
        API.CloseHandle(handle);
    }

    private void closeConnectedPipe(HANDLE handle, boolean shutdown) throws IOException {
        if (!shutdown) {
            API.WaitForSingleObject(handle, 10000);
        }
        API.DisconnectNamedPipe(handle);
        API.CloseHandle(handle);
    }
}
