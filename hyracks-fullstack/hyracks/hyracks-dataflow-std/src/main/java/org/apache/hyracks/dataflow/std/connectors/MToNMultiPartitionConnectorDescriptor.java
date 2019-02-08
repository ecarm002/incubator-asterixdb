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
package org.apache.hyracks.dataflow.std.connectors;

import java.util.BitSet;

import org.apache.hyracks.api.comm.IFrameReader;
import org.apache.hyracks.api.comm.IFrameWriter;
import org.apache.hyracks.api.comm.IPartitionCollector;
import org.apache.hyracks.api.comm.IPartitionWriterFactory;
import org.apache.hyracks.api.context.IHyracksTaskContext;
import org.apache.hyracks.api.dataflow.value.IBinaryComparator;
import org.apache.hyracks.api.dataflow.value.IBinaryComparatorFactory;
import org.apache.hyracks.api.dataflow.value.INormalizedKeyComputer;
import org.apache.hyracks.api.dataflow.value.INormalizedKeyComputerFactory;
import org.apache.hyracks.api.dataflow.value.ITupleMultiPartitionComputerFactory;
import org.apache.hyracks.api.dataflow.value.RecordDescriptor;
import org.apache.hyracks.api.exceptions.HyracksDataException;
import org.apache.hyracks.api.job.IConnectorDescriptorRegistry;
import org.apache.hyracks.dataflow.std.base.AbstractMToNConnectorDescriptor;
import org.apache.hyracks.dataflow.std.base.RangeId;
import org.apache.hyracks.dataflow.std.collectors.IPartitionBatchManager;
import org.apache.hyracks.dataflow.std.collectors.NonDeterministicChannelReader;
import org.apache.hyracks.dataflow.std.collectors.NonDeterministicFrameReader;
import org.apache.hyracks.dataflow.std.collectors.NonDeterministicPartitionBatchManager;
import org.apache.hyracks.dataflow.std.collectors.PartitionCollector;
import org.apache.hyracks.dataflow.std.collectors.SortMergeFrameReader;

public class MToNMultiPartitioningConnectorDescriptor extends AbstractMToNConnectorDescriptor {
    private static final long serialVersionUID = 1L;

    private final ITupleMultiPartitionComputerFactory trpcf;
    private final RangeId rangeId;

    public MToNMultiPartitioningConnectorDescriptor(IConnectorDescriptorRegistry spec,
                                                    ITupleMultiPartitionComputerFactory trpcf, RangeId rangeId, int[] sortFields,
                                                    IBinaryComparatorFactory[] comparatorFactories, INormalizedKeyComputerFactory nkcFactory) {
        super(spec);
        this.trpcf = trpcf;
        this.rangeId = rangeId;
    }

    @Override
    public IFrameWriter createPartitioner(IHyracksTaskContext ctx, RecordDescriptor recordDesc,
                                          IPartitionWriterFactory edwFactory, int index, int nProducerPartitions, int nConsumerPartitions)
            throws HyracksDataException {
        return new MultiPartitionDataWriter(ctx, nConsumerPartitions, edwFactory, recordDesc, trpcf, rangeId);
    }

    @Override
    public IPartitionCollector createPartitionCollector(IHyracksTaskContext ctx, RecordDescriptor recordDesc, int index,
                                                        int nProducerPartitions, int nConsumerPartitions) throws HyracksDataException {
        BitSet expectedPartitions = new BitSet(nProducerPartitions);
        expectedPartitions.set(0, nProducerPartitions);
        NonDeterministicChannelReader channelReader = new NonDeterministicChannelReader(nProducerPartitions,
                expectedPartitions);
        NonDeterministicFrameReader frameReader = new NonDeterministicFrameReader(channelReader);
        return new PartitionCollector(ctx, getConnectorId(), index, expectedPartitions, frameReader, channelReader);
    }
}