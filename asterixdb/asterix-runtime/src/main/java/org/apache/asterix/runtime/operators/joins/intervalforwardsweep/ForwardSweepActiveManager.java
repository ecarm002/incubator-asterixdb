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

package org.apache.asterix.runtime.operators.joins.intervalforwardsweep;

import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.hyracks.api.exceptions.HyracksDataException;
import org.apache.hyracks.dataflow.std.buffermanager.IPartitionedDeletableTupleBufferManager;
import org.apache.hyracks.dataflow.std.buffermanager.ITupleAccessor;
import org.apache.hyracks.dataflow.std.structures.TuplePointer;

public class ForwardSweepActiveManager {
    private static final Logger LOGGER = Logger.getLogger(ForwardSweepActiveManager.class.getName());

    private final int partition;

    private final IPartitionedDeletableTupleBufferManager bufferManager;
    private final LinkedList<TuplePointer> active = new LinkedList<>();

    public ForwardSweepActiveManager(IPartitionedDeletableTupleBufferManager bufferManager, int joinBranch) {
        this.bufferManager = bufferManager;
        this.partition = joinBranch;
    }

    public boolean addTuple(ITupleAccessor accessor, TuplePointer tp) throws HyracksDataException {
        if (bufferManager.insertTuple(partition, accessor, accessor.getTupleId(), tp)) {
            active.add(tp);
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("Add to memory (partition: " + partition + ").");
            }
            return true;
        }
        return false;
    }

    public List<TuplePointer> getActiveList() {
        return active;
    }

    public TuplePointer getFirst() {
        if (isEmpty()) {
            return active.get(0);
        }
        return null;
    }

    public void remove(int index) throws HyracksDataException {
        if (active.size() < index) {
            return;
        }
        TuplePointer tp = active.get(index);
        bufferManager.deleteTuple(partition, tp);
        active.remove(index);
    }

    public void remove(TuplePointer tp) throws HyracksDataException {
        bufferManager.deleteTuple(partition, tp);
        active.remove(tp);
    }

    public boolean isEmpty() {
        return active.isEmpty();
    }

    public boolean hasRecords() {
        return !active.isEmpty();
    }

    public void clear() throws HyracksDataException {
        for (TuplePointer tp : active) {
            bufferManager.deleteTuple(partition, tp);
        }
        active.clear();
        bufferManager.clearPartition(partition);
    }
}