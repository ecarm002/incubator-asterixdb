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
package org.apache.asterix.runtime.operators.joins;

import org.apache.asterix.dataflow.data.nontagged.serde.AIntervalSerializerDeserializer;
import org.apache.hyracks.api.comm.IFrameTupleAccessor;
import org.apache.hyracks.api.dataflow.value.ITuplePartitionComputer;
import org.apache.hyracks.api.dataflow.value.ITuplePartitionComputerFactory;
import org.apache.hyracks.api.exceptions.HyracksDataException;

public class IntervalPartitionComputerFactory implements ITuplePartitionComputerFactory {
    private static final long serialVersionUID = 1L;
    private final int intervalFieldId;
    private final int k;
    private final long partitionStart;
    private final long partitionDuration;

    public IntervalPartitionComputerFactory(int intervalFieldId, int k, long partitionStart, long partitionDuration) {
        this.intervalFieldId = intervalFieldId;
        this.k = k;
        this.partitionStart = partitionStart;
        this.partitionDuration = partitionDuration;
    }

    @Override
    public ITuplePartitionComputer createPartitioner() {
        return new ITuplePartitionComputer() {
            @Override
            public int partition(IFrameTupleAccessor accessor, int tIndex, int nParts) throws HyracksDataException {
                long partitionI = getIntervalPartitionI(accessor, tIndex, intervalFieldId, partitionStart,
                        partitionDuration);
                long partitionJ = getIntervalPartitionJ(accessor, tIndex, intervalFieldId, partitionStart,
                        partitionDuration);
                return intervalPartitionMap(partitionJ, partitionI, k);
            }
        };
    }

    public static int intervalPartitionMap(long i, long j, int k) {
        long duration = j - i;
        int p;
        for (p = 0; p < duration - 1; ++p) {
            p += k - duration + 1;
        }
        p += i;
        return p;
    }

    private long getIntervalPartitionI(IFrameTupleAccessor accessor, int tIndex, int fieldId, long partitionStart,
            long partitionDuration) throws HyracksDataException {
        return Math.floorDiv((getIntervalStart(accessor, tIndex, fieldId) - partitionStart), partitionDuration);
    }

    private long getIntervalPartitionJ(IFrameTupleAccessor accessor, int tIndex, int fieldId, long partitionStart,
            long partitionDuration) throws HyracksDataException {
        return Math.floorDiv((getIntervalEnd(accessor, tIndex, fieldId) - partitionStart), partitionDuration);
    }

    private long getIntervalStart(IFrameTupleAccessor accessor, int tupleId, int fieldId) throws HyracksDataException {
        int start = accessor.getTupleStartOffset(tupleId) + accessor.getFieldSlotsLength()
                + accessor.getFieldStartOffset(tupleId, fieldId) + 1;
        return AIntervalSerializerDeserializer.getIntervalStart(accessor.getBuffer().array(), start);
    }

    private long getIntervalEnd(IFrameTupleAccessor accessor, int tupleId, int fieldId) throws HyracksDataException {
        int start = accessor.getTupleStartOffset(tupleId) + accessor.getFieldSlotsLength()
                + accessor.getFieldStartOffset(tupleId, fieldId) + 1;
        return AIntervalSerializerDeserializer.getIntervalEnd(accessor.getBuffer().array(), start);
    }

}