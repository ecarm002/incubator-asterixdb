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

import org.apache.asterix.om.pointables.nonvisitor.AIntervalPointable;
import org.apache.asterix.runtime.evaluators.comparisons.ComparisonHelper;
import org.apache.asterix.runtime.evaluators.functions.temporal.IntervalLogicWithPointables;
import org.apache.hyracks.api.comm.IFrameTupleAccessor;
import org.apache.hyracks.api.exceptions.HyracksDataException;
import org.apache.hyracks.data.std.api.IPointable;
import org.apache.hyracks.data.std.primitive.TaggedValuePointable;
import org.apache.hyracks.data.std.primitive.VoidPointable;
import org.apache.hyracks.dataflow.std.buffermanager.ITupleAccessor;

public abstract class AbstractIntervalMergeJoinChecker implements IIntervalMergeJoinChecker {

    private static final long serialVersionUID = 1L;

    protected final int idLeft;
    protected final int idRight;

    protected final IntervalLogicWithPointables il = new IntervalLogicWithPointables();

    protected final TaggedValuePointable tvp = (TaggedValuePointable) TaggedValuePointable.FACTORY.createPointable();
    protected final AIntervalPointable ipLeft = (AIntervalPointable) AIntervalPointable.FACTORY.createPointable();
    protected final AIntervalPointable ipRight = (AIntervalPointable) AIntervalPointable.FACTORY.createPointable();

    protected final ComparisonHelper ch = new ComparisonHelper();
    protected final IPointable startLeft = VoidPointable.FACTORY.createPointable();
    protected final IPointable endLeft = VoidPointable.FACTORY.createPointable();
    protected final IPointable startRight = VoidPointable.FACTORY.createPointable();
    protected final IPointable endRight = VoidPointable.FACTORY.createPointable();

    public AbstractIntervalMergeJoinChecker(int idLeft, int idRight) {
        this.idLeft = idLeft;
        this.idRight = idRight;
    }

    @Override
    public boolean checkToRemoveLeftActive() {
        return true;
    }

    @Override
    public boolean checkToRemoveRightActive() {
        return true;
    }

    @Override
    public boolean checkToSaveInMemory(ITupleAccessor accessorLeft, ITupleAccessor accessorRight)
            throws HyracksDataException {
        return checkToSaveInMemory(accessorLeft, accessorLeft.getTupleId(), accessorRight, accessorRight.getTupleId());
    }

    @Override
    public boolean checkToRemoveInMemory(ITupleAccessor accessorLeft, ITupleAccessor accessorRight)
            throws HyracksDataException {
        return checkToRemoveInMemory(accessorLeft, accessorLeft.getTupleId(), accessorRight,
                accessorRight.getTupleId());
    }

    @Override
    public boolean checkToLoadNextRightTuple(ITupleAccessor accessorLeft, ITupleAccessor accessorRight)
            throws HyracksDataException {
        return checkToLoadNextRightTuple(accessorLeft, accessorLeft.getTupleId(), accessorRight,
                accessorRight.getTupleId());
    }

    @Override
    public boolean checkToLoadNextRightTuple(IFrameTupleAccessor accessorLeft, int leftTupleIndex,
            IFrameTupleAccessor accessorRight, int rightTupleIndex) throws HyracksDataException {
        long start1 = IntervalJoinUtil.getIntervalStart(accessorRight, rightTupleIndex, idRight);
        long end0 = IntervalJoinUtil.getIntervalEnd(accessorLeft, leftTupleIndex, idLeft);
        return end0 > start1;
    }

    @Override
    public boolean checkIfMoreMatches(ITupleAccessor accessorLeft, ITupleAccessor accessorRight)
            throws HyracksDataException {
        return checkIfMoreMatches(accessorLeft, accessorLeft.getTupleId(), accessorRight, accessorRight.getTupleId());
    }

    @Override
    public boolean checkToSaveInResult(ITupleAccessor accessorLeft, ITupleAccessor accessorRight)
            throws HyracksDataException {
        return checkToSaveInResult(accessorLeft, accessorLeft.getTupleId(), accessorRight, accessorRight.getTupleId(),
                false);
    }

    /**
     * Right (second argument) interval starts before left (first argument) interval ends.
     */
    @Override
    public boolean checkToSaveInMemory(IFrameTupleAccessor accessorLeft, int leftTupleIndex,
            IFrameTupleAccessor accessorRight, int rightTupleIndex) throws HyracksDataException {
        long start1 = IntervalJoinUtil.getIntervalStart(accessorRight, rightTupleIndex, idRight);
        long end0 = IntervalJoinUtil.getIntervalEnd(accessorLeft, leftTupleIndex, idLeft);
        long start0 = IntervalJoinUtil.getIntervalStart(accessorLeft, leftTupleIndex, idLeft);
        long end1 = IntervalJoinUtil.getIntervalEnd(accessorRight, rightTupleIndex, idRight);
        return start0 <= start1;
    }

    /**
     * Left (first argument) interval ends before the Right (second argument) interval ends.
     */
    @Override
    public boolean checkToIncrementMerge(ITupleAccessor accessorLeft, ITupleAccessor accessorRight)
            throws HyracksDataException {
        IntervalJoinUtil.getIntervalPointable(accessorLeft, idLeft, tvp, ipLeft);
        IntervalJoinUtil.getIntervalPointable(accessorRight, idRight, tvp, ipRight);
        ipLeft.getEnd(endLeft);
        ipRight.getEnd(endRight);
        return ch.compare(ipLeft.getTypeTag(), ipRight.getTypeTag(), endLeft, endRight) < 0;
    }

    /**
     * Left (first argument) interval starts after the Right (second argument) interval ends.
     */
    @Override
    public boolean checkToRemoveInMemory(IFrameTupleAccessor accessorLeft, int leftTupleIndex,
            IFrameTupleAccessor accessorRight, int rightTupleIndex) throws HyracksDataException {
        long start0 = IntervalJoinUtil.getIntervalStart(accessorLeft, leftTupleIndex, idLeft);
        long end0 = IntervalJoinUtil.getIntervalEnd(accessorLeft, leftTupleIndex, idLeft);
        long start1 = IntervalJoinUtil.getIntervalStart(accessorRight, rightTupleIndex, idRight);
        long end1 = IntervalJoinUtil.getIntervalEnd(accessorRight, rightTupleIndex, idRight);
        return start0 > start1;
    }

    /**
     * Left (first argument) interval starts after the Right (second argument) interval ends.
     */
    @Override
    public boolean checkIfMoreMatches(IFrameTupleAccessor accessorLeft, int leftTupleIndex,
            IFrameTupleAccessor accessorRight, int rightTupleIndex) throws HyracksDataException {
        long start1 = IntervalJoinUtil.getIntervalStart(accessorRight, rightTupleIndex, idRight);
        long end0 = IntervalJoinUtil.getIntervalEnd(accessorLeft, leftTupleIndex, idLeft);
        return end0 > start1;
    }

    @Override
    public boolean checkToSaveInResult(IFrameTupleAccessor accessorLeft, int leftTupleIndex,
            IFrameTupleAccessor accessorRight, int rightTupleIndex, boolean reversed) throws HyracksDataException {
        if (reversed) {
            IntervalJoinUtil.getIntervalPointable(accessorLeft, leftTupleIndex, idLeft, tvp, ipRight);
            IntervalJoinUtil.getIntervalPointable(accessorRight, rightTupleIndex, idRight, tvp, ipLeft);
        } else {
            IntervalJoinUtil.getIntervalPointable(accessorLeft, leftTupleIndex, idLeft, tvp, ipLeft);
            IntervalJoinUtil.getIntervalPointable(accessorRight, rightTupleIndex, idRight, tvp, ipRight);
        }
        return compareInterval(ipLeft, ipRight);
    }

    @Override
    public boolean checkToSaveInResult(long start0, long end0, long start1, long end1, boolean reversed) {
        if (reversed) {
            return compareInterval(start1, end1, start0, end0);
        } else {
            return compareInterval(start0, end0, start1, end1);
        }
    }
    
    /**
     * Right (second argument) interval starts before left (first argument) interval ends.
     */
    @Override
    public boolean checkForEarlyExit(IFrameTupleAccessor accessorLeft, int leftTupleIndex,
            IFrameTupleAccessor accessorRight, int rightTupleIndex) throws HyracksDataException {
        long start1 = IntervalJoinUtil.getIntervalStart(accessorRight, rightTupleIndex, idRight);
        long end0 = IntervalJoinUtil.getIntervalEnd(accessorLeft, leftTupleIndex, idLeft);
        long start0 = IntervalJoinUtil.getIntervalStart(accessorLeft, leftTupleIndex, idLeft);
        long end1 = IntervalJoinUtil.getIntervalEnd(accessorRight, rightTupleIndex, idRight);
        return end0 < start1;
    }

    @Override
    public abstract boolean compareInterval(AIntervalPointable ipLeft, AIntervalPointable ipRight)
            throws HyracksDataException;

    @Override
    public abstract boolean compareInterval(long start0, long end0, long start1, long end1);

    @Override
    public abstract boolean compareIntervalPartition(int s1, int e1, int s2, int e2);

}