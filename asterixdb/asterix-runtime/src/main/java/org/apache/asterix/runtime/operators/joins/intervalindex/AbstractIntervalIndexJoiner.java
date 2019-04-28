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
package org.apache.asterix.runtime.operators.joins.intervalindex;

import java.nio.ByteBuffer;

import org.apache.asterix.runtime.operators.joins.intervalmergejoin.IntervalMergeBranchStatus;
import org.apache.hyracks.api.comm.IFrame;
import org.apache.hyracks.api.comm.VSizeFrame;
import org.apache.hyracks.api.context.IHyracksTaskContext;
import org.apache.hyracks.api.exceptions.HyracksDataException;
import org.apache.hyracks.dataflow.common.comm.io.FrameTupleAppender;
import org.apache.hyracks.dataflow.std.buffermanager.ITupleAccessor;
import org.apache.hyracks.dataflow.std.buffermanager.TupleAccessor;

public abstract class AbstractIntervalIndexJoiner implements IIndexJoiner {

    public enum TupleStatus {
        UNKNOWN,
        LOADED,
        EMPTY;

        public boolean isLoaded() {
            return this.equals(LOADED);
        }

        public boolean isEmpty() {
            return this.equals(EMPTY);
        }

        public boolean isKnown() {
            return !this.equals(UNKNOWN);
        }
    }
    protected final IntervalMergeBranchStatus[] branchStatus;

    protected static final int JOIN_PARTITIONS = 2;
    protected static final int LEFT_PARTITION = 0;
    protected static final int RIGHT_PARTITION = 1;

    protected final IFrame[] inputBuffer;
    protected final FrameTupleAppender resultAppender;
    protected final ITupleAccessor[] inputAccessor;
    protected final IConsumerFrame[] consumerFrames;

    private final int partition;
    protected long[] frameCounts = { 0, 0 };
    protected long[] tupleCounts = { 0, 0 };

    public AbstractIntervalIndexJoiner(IHyracksTaskContext ctx, int partition, IConsumerFrame leftCF,
            IConsumerFrame rightCF) throws HyracksDataException {
        this.partition = partition;

        inputAccessor = new TupleAccessor[JOIN_PARTITIONS];
        inputAccessor[LEFT_PARTITION] = new TupleAccessor(leftCF.getRecordDescriptor());
        inputAccessor[RIGHT_PARTITION] = new TupleAccessor(rightCF.getRecordDescriptor());

        inputBuffer = new IFrame[JOIN_PARTITIONS];
        inputBuffer[LEFT_PARTITION] = new VSizeFrame(ctx);
        inputBuffer[RIGHT_PARTITION] = new VSizeFrame(ctx);

        branchStatus = new IntervalMergeBranchStatus[JOIN_PARTITIONS];
        branchStatus[LEFT_PARTITION] = new IntervalMergeBranchStatus();
        branchStatus[RIGHT_PARTITION] = new IntervalMergeBranchStatus();

        consumerFrames = new IConsumerFrame[JOIN_PARTITIONS];
        consumerFrames[LEFT_PARTITION] = leftCF;
        consumerFrames[RIGHT_PARTITION] = rightCF;

        // Result
        resultAppender = new FrameTupleAppender(new VSizeFrame(ctx));
    }

    protected TupleStatus loadMemoryTuple(int branch) throws HyracksDataException {
        TupleStatus loaded;
        if (inputAccessor[branch] != null && inputAccessor[branch].exists()) {
            // Still processing frame.
            loaded = TupleStatus.LOADED;
        } else {
            if (consumerFrames[branch].hasMoreFrames()) {
                setFrame(branch, consumerFrames[branch].getFrame());
                loaded = TupleStatus.LOADED;
            } else {
                // No more frames or tuples to process.
                loaded = TupleStatus.EMPTY;
                branchStatus[branch].noMore();
            }
        }
        return loaded;
    }

    private void setFrame(int branch, ByteBuffer buffer) throws HyracksDataException {
        inputBuffer[branch].getBuffer().clear();
        if (inputBuffer[branch].getFrameSize() < buffer.capacity()) {
            inputBuffer[branch].resize(buffer.capacity());
        }
        inputBuffer[branch].getBuffer().put(buffer.array(), 0, buffer.capacity());
//        inputBuffer[branch].getBuffer().put(buffer);
        inputAccessor[branch].reset(inputBuffer[branch].getBuffer());
        inputAccessor[branch].next();
        frameCounts[branch]++;
        tupleCounts[branch] += inputAccessor[branch].getTupleCount();
    }
}