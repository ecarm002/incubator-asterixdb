/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.hyracks.dataflow.std.join;

import org.apache.commons.io.FileUtils;
import org.apache.hyracks.api.comm.FrameHelper;
import org.apache.hyracks.api.comm.IFrame;
import org.apache.hyracks.api.comm.IFrameReader;
import org.apache.hyracks.api.exceptions.HyracksDataException;
import org.apache.hyracks.api.io.FileReference;
import org.apache.hyracks.api.io.IFileHandle;
import org.apache.hyracks.api.io.IIOManager;

public class RunFileReaderDir implements IFrameReader {
    private final FileReference file;
    private IFileHandle handle;
    private final IIOManager ioManager;
    private final long size;
    private long readPreviousPtr;
    private long readPtr;
    private boolean deleteAfterClose;

    public RunFileReaderDir(FileReference file, IIOManager ioManager, long size, boolean deleteAfterRead) {
        this.file = file;
        this.ioManager = ioManager;
        this.size = size;
        this.deleteAfterClose = deleteAfterRead;
    }

    @Override
    public void open() throws HyracksDataException {
        handle = ioManager.openDir(file, IIOManager.FileReadWriteMode.READ_ONLY, null);
        readPtr = 0;
        readPreviousPtr = 0;
    }

    @Override
    public boolean nextFrame(IFrame frame) throws HyracksDataException {
        if (readPtr >= size) {
            return false;
        }
        readPreviousPtr = readPtr;
        frame.reset();

        int readLength = ioManager.syncDirRead(handle, readPtr, frame.getBuffer());
        if (readLength <= 0) {
            throw new HyracksDataException("Premature end of file");
        }
        frame.ensureFrameSize(frame.getMinSize() * FrameHelper.deserializeNumOfMinFrame(frame.getBuffer()));
        if (FrameHelper.deserializeNumOfMinFrame(frame.getBuffer()) > 1) {
            readLength = ioManager.syncDirRead(handle, readPtr, frame.getBuffer());
            if (readLength < 0) {
                throw new HyracksDataException("Premature end of file");
            }
            if (frame.getBuffer().hasRemaining()) { // file is vanished.
                FrameHelper.clearRemainingFrame(frame.getBuffer(), frame.getBuffer().position());
            }
        }
        readPtr += readLength;
        //frame.getBuffer().flip();
        return true;
    }

    public void reset() throws HyracksDataException {
        readPtr = 0;
        readPreviousPtr = readPtr;
    }

    public void reset(long pointer) throws HyracksDataException {
        readPtr = pointer;
        readPreviousPtr = readPtr;
    }

    @Override
    public void close() throws HyracksDataException {
        ioManager.closeDir(handle);
        if (deleteAfterClose) {
            FileUtils.deleteQuietly(file.getFile());
        }
    }

    public void delete() {
        FileUtils.deleteQuietly(file.getFile());
    }

    public long getFileSize() {
        return size;
    }

    public long getReadPointer() {
        return readPreviousPtr;
    }

    public long getReadPointerCurrent() {
        return readPtr;
    }
}
