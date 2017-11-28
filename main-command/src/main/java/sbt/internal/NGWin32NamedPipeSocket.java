// Copied from https://github.com/facebook/nailgun/blob/af623fddedfdca010df46302a0711ce0e2cc1ba6/nailgun-server/src/main/java/com/martiansoftware/nailgun/NGWin32NamedPipeSocket.java
// Made change in `read` to read just the amount of bytes available.

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

import com.sun.jna.Memory;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinError;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.ptr.IntByReference;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;

public class NGWin32NamedPipeSocket extends Socket {
    private static final NGWin32NamedPipeLibrary API = NGWin32NamedPipeLibrary.INSTANCE;
    private final HANDLE handle;
    private final CloseCallback closeCallback;
    private final InputStream is;
    private final OutputStream os;
    private final HANDLE readerWaitable;
    private final HANDLE writerWaitable;

    interface CloseCallback {
        void onNamedPipeSocketClose(HANDLE handle) throws IOException;
    }

    public NGWin32NamedPipeSocket(
            HANDLE handle,
            NGWin32NamedPipeSocket.CloseCallback closeCallback) throws IOException {
        this.handle = handle;
        this.closeCallback = closeCallback;
        this.readerWaitable = API.CreateEvent(null, true, false, null);
        if (readerWaitable == null) {
            throw new IOException("CreateEvent() failed ");
        }
        writerWaitable = API.CreateEvent(null, true, false, null);
        if (writerWaitable == null) {
            throw new IOException("CreateEvent() failed ");
        }
        this.is = new NGWin32NamedPipeSocketInputStream(handle);
        this.os = new NGWin32NamedPipeSocketOutputStream(handle);
    }

    @Override
    public InputStream getInputStream() {
        return is;
    }

    @Override
    public OutputStream getOutputStream() {
        return os;
    }

    @Override
    public void close() throws IOException {
        closeCallback.onNamedPipeSocketClose(handle);
    }

    @Override
    public void shutdownInput() throws IOException {
    }

    @Override
    public void shutdownOutput() throws IOException {
    }

    private class NGWin32NamedPipeSocketInputStream extends InputStream {
        private final HANDLE handle;

        NGWin32NamedPipeSocketInputStream(HANDLE handle) {
            this.handle = handle;
        }

        @Override
        public int read() throws IOException {
            int result;
            byte[] b = new byte[1];
            if (read(b) == 0) {
                result = -1;
            } else {
                result = 0xFF & b[0];
            }
            return result;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            Memory readBuffer = new Memory(len);

            WinBase.OVERLAPPED olap = new WinBase.OVERLAPPED();
            olap.hEvent = readerWaitable;
            olap.write();

            boolean immediate = API.ReadFile(handle, readBuffer, len, null, olap.getPointer());
            if (!immediate) {
                int lastError = API.GetLastError();
                if (lastError != WinError.ERROR_IO_PENDING) {
                    throw new IOException("ReadFile() failed: " + lastError);
                }
            }

            IntByReference read = new IntByReference();
            if (!API.GetOverlappedResult(handle, olap.getPointer(), read, true)) {
                int lastError = API.GetLastError();
                throw new IOException("GetOverlappedResult() failed for read operation: " + lastError);
            }
            int actualLen = read.getValue();
            byte[] byteArray = readBuffer.getByteArray(0, actualLen);
            System.arraycopy(byteArray, 0, b, off, actualLen);
            return actualLen;
        }
    }

    private class NGWin32NamedPipeSocketOutputStream extends OutputStream {
        private final HANDLE handle;

        NGWin32NamedPipeSocketOutputStream(HANDLE handle) {
            this.handle = handle;
        }

        @Override
        public void write(int b) throws IOException {
            write(new byte[]{(byte) (0xFF & b)});
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            ByteBuffer data = ByteBuffer.wrap(b, off, len);

            WinBase.OVERLAPPED olap = new WinBase.OVERLAPPED();
            olap.hEvent = writerWaitable;
            olap.write();

            boolean immediate = API.WriteFile(handle, data, len, null, olap.getPointer());
            if (!immediate) {
                int lastError = API.GetLastError();
                if (lastError != WinError.ERROR_IO_PENDING) {
                    throw new IOException("WriteFile() failed: " + lastError);
                }
            }
            IntByReference written = new IntByReference();
            if (!API.GetOverlappedResult(handle, olap.getPointer(), written, true)) {
                int lastError = API.GetLastError();
                throw new IOException("GetOverlappedResult() failed for write operation: " + lastError);
            }
            if (written.getValue() != len) {
                throw new IOException("WriteFile() wrote less bytes than requested");
            }
        }
    }
}
