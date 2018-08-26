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

import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.asterix.runtime.operators.joins.IIntervalMergeJoinChecker;
import org.apache.asterix.runtime.operators.joins.IIntervalMergeJoinCheckerFactory;
import org.apache.asterix.runtime.operators.joins.IntervalJoinUtil;
import org.apache.hyracks.api.comm.IFrameTupleAccessor;
import org.apache.hyracks.api.comm.IFrameWriter;
import org.apache.hyracks.api.context.IHyracksTaskContext;
import org.apache.hyracks.api.dataflow.value.RecordDescriptor;
import org.apache.hyracks.api.exceptions.HyracksDataException;
import org.apache.hyracks.dataflow.common.comm.util.FrameUtils;
import org.apache.hyracks.dataflow.std.buffermanager.IPartitionedDeletableTupleBufferManager;
import org.apache.hyracks.dataflow.std.buffermanager.ITupleAccessor;
import org.apache.hyracks.dataflow.std.buffermanager.ITuplePointerAccessor;
import org.apache.hyracks.dataflow.std.buffermanager.TupleAccessor;
import org.apache.hyracks.dataflow.std.buffermanager.VPartitionDeletableTupleBufferManager;
import org.apache.hyracks.dataflow.std.join.AbstractMergeJoiner;
import org.apache.hyracks.dataflow.std.join.MergeJoinLocks;
import org.apache.hyracks.dataflow.std.join.MergeStatus;
import org.apache.hyracks.dataflow.std.join.RunFileStream;
import org.apache.hyracks.dataflow.std.structures.RunFilePointer;
import org.apache.hyracks.dataflow.std.structures.TuplePointer;

/**
 * Interval Index Merge Joiner takes two sorted streams of input and joins.
 * The two sorted streams must be in a logical order and the comparator must
 * support keeping that order so the join will work.
 * The left stream will spill to disk when memory is full.
 * The both right and left use memory to maintain active intervals for the join.
 */
public class IntervalIndexJoiner extends AbstractMergeJoiner {

    private static final Logger LOGGER = Logger.getLogger(IntervalIndexJoiner.class.getName());

    private final IPartitionedDeletableTupleBufferManager bufferManager;

    private final ActiveSweepManager[] activeManager;
    private final ITupleAccessor[] memoryAccessor;
    private final int[] streamIndex;
    private final RunFileStream[] runFileStream;
    private final RunFilePointer[] runFilePointer;

    private final IIntervalMergeJoinChecker imjc;

    private final byte point;

    private final int leftKey;
    private final int rightKey;

    private long joinComparisonCount = 0;
    private long joinResultCount = 0;
    private long leftSpillCount = 0;
    private long rightSpillCount = 0;
    private long[] spillFileCount = { 0, 0 };
    private long[] spillReadCount = { 0, 0 };
    private long[] spillWriteCount = { 0, 0 };

    private final int partition;
    private final int memorySize;

    public IntervalIndexJoiner(IHyracksTaskContext ctx, int memorySize, int partition, MergeStatus status,
            MergeJoinLocks locks, Comparator<EndPointIndexItem> endPointComparator,
            IIntervalMergeJoinCheckerFactory imjcf, int[] leftKeys, int[] rightKeys, RecordDescriptor leftRd,
            RecordDescriptor rightRd) throws HyracksDataException {
        super(ctx, partition, status, locks, leftRd, rightRd);
        this.partition = partition;
        this.memorySize = memorySize;

        this.point = imjcf.isOrderAsc() ? EndPointIndexItem.START_POINT : EndPointIndexItem.END_POINT;

        this.imjc = imjcf.createMergeJoinChecker(leftKeys, rightKeys, ctx);

        this.leftKey = leftKeys[0];
        this.rightKey = rightKeys[0];

        RecordDescriptor[] recordDescriptors = new RecordDescriptor[JOIN_PARTITIONS];
        recordDescriptors[LEFT_PARTITION] = leftRd;
        recordDescriptors[RIGHT_PARTITION] = rightRd;

        streamIndex = new int[JOIN_PARTITIONS];
        streamIndex[LEFT_PARTITION] = TupleAccessor.UNSET;
        streamIndex[RIGHT_PARTITION] = TupleAccessor.UNSET;

        if (memorySize < 5) {
            throw new HyracksDataException(
                    "IntervalIndexJoiner does not have enough memory (needs > 4, got " + memorySize + ").");
        }
        //        bufferManager = new VPartitionDeletableTupleBufferManager(ctx,
        //                VPartitionDeletableTupleBufferManager.NO_CONSTRAIN, JOIN_PARTITIONS,
        //                (memorySize - 4) * ctx.getInitialFrameSize(), recordDescriptors);
        bufferManager = new VPartitionDeletableTupleBufferManager(ctx,
                VPartitionDeletableTupleBufferManager.NO_CONSTRAIN, JOIN_PARTITIONS,
                memorySize * ctx.getInitialFrameSize(), recordDescriptors);
        memoryAccessor = new ITupleAccessor[JOIN_PARTITIONS];
        memoryAccessor[LEFT_PARTITION] = bufferManager.getTupleAccessor(leftRd);
        memoryAccessor[RIGHT_PARTITION] = bufferManager.getTupleAccessor(rightRd);

        activeManager = new ActiveSweepManager[JOIN_PARTITIONS];
        activeManager[LEFT_PARTITION] = new ActiveSweepManager(bufferManager, leftKey, LEFT_PARTITION,
                endPointComparator);
        activeManager[RIGHT_PARTITION] = new ActiveSweepManager(bufferManager, rightKey, RIGHT_PARTITION,
                endPointComparator);

        // Run files for both branches
        runFileStream = new RunFileStream[JOIN_PARTITIONS];
        runFileStream[LEFT_PARTITION] = new RunFileStream(ctx, "left", status.branch[LEFT_PARTITION]);
        runFileStream[RIGHT_PARTITION] = new RunFileStream(ctx, "right", status.branch[RIGHT_PARTITION]);
        runFilePointer = new RunFilePointer[JOIN_PARTITIONS];
        runFilePointer[LEFT_PARTITION] = new RunFilePointer();
        runFilePointer[RIGHT_PARTITION] = new RunFilePointer();

        LOGGER.setLevel(Level.FINE);
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("IntervalIndexJoiner has started partition " + partition + " with " + memorySize
                    + " frames of memory.");
        }
    }

    private void addToResult(IFrameTupleAccessor accessor1, int index1, IFrameTupleAccessor accessor2, int index2,
            boolean reversed, IFrameWriter writer) throws HyracksDataException {
        if (reversed) {
            FrameUtils.appendConcatToWriter(writer, resultAppender, accessor2, index2, accessor1, index1);
        } else {
            FrameUtils.appendConcatToWriter(writer, resultAppender, accessor1, index1, accessor2, index2);
        }
        joinResultCount++;
    }

    private void flushMemory(int partition) throws HyracksDataException {
        activeManager[partition].clear();
    }

    private TupleStatus loadSpilledTuple(int partition) throws HyracksDataException {
        if (!inputAccessor[partition].exists()) {
            // if statement must be separate.
            if (!runFileStream[partition].loadNextBuffer(inputAccessor[partition])) {
                return TupleStatus.EMPTY;
            }
        }
        return TupleStatus.LOADED;
    }

    private TupleStatus loadTuple(int partition) throws HyracksDataException {
        TupleStatus loaded;
        if (status.branch[partition].isRunFileReading()) {
            loaded = loadSpilledTuple(partition);
            if (loaded.isEmpty()) {
                continueStream(partition, inputAccessor[partition]);
                loaded = loadTuple(partition);
            }
        } else {
            loaded = loadMemoryTuple(partition);
        }
        return loaded;
    }

    /**
     * Ensures a frame exists for the right branch, either from memory or the run file.
     *
     * @throws HyracksDataException
     */
    private TupleStatus loadRightTuple() throws HyracksDataException {
        TupleStatus loaded = loadTuple(RIGHT_PARTITION);
        if (loaded == TupleStatus.UNKNOWN) {
            loaded = pauseAndLoadRightTuple();
        }
        return loaded;
    }

    /**
     * Ensures a frame exists for the left branch, either from memory or the run file.
     *
     * @throws HyracksDataException
     */
    private TupleStatus loadLeftTuple() throws HyracksDataException {
        return loadTuple(LEFT_PARTITION);
    }

    @Override
    public void processLeftFrame(IFrameWriter writer) throws HyracksDataException {
        TupleStatus leftTs = loadLeftTuple();
        TupleStatus rightTs = loadRightTuple();
        while (leftTs.isKnown() && checkHasMoreProcessing(leftTs, LEFT_PARTITION, RIGHT_PARTITION)
                && checkHasMoreProcessing(rightTs, RIGHT_PARTITION, LEFT_PARTITION)) {
            if (runFileStream[RIGHT_PARTITION].isWriting()) {
                // Right side from disk
                rightTs = processRightTupleSpill(writer);
            } else if (runFileStream[LEFT_PARTITION].isWriting()) {
                // Left side from disk
                leftTs = processLeftTupleSpill(writer);
            } else {
                if (leftTs.isEmpty() || (rightTs.isLoaded() && checkToProcessRightTuple())) {
                    // Right side from stream
                    processRightTuple(writer);
                    rightTs = loadRightTuple();
                } else {
                    // Left side from stream
                    processLeftTuple(writer);
                    leftTs = loadLeftTuple();
                }
            }
        }
    }

    @Override
    public void processLeftClose(IFrameWriter writer) throws HyracksDataException {
        processLeftFrame(writer);
        resultAppender.write(writer, true);

        activeManager[LEFT_PARTITION].clear();
        activeManager[RIGHT_PARTITION].clear();
        runFileStream[LEFT_PARTITION].close();
        runFileStream[RIGHT_PARTITION].close();

        long ioCost = runFileStream[LEFT_PARTITION].getWriteCount() + runFileStream[LEFT_PARTITION].getReadCount()
                + runFileStream[RIGHT_PARTITION].getWriteCount() + runFileStream[RIGHT_PARTITION].getReadCount();
        if (LOGGER.isLoggable(Level.WARNING)) {
            LOGGER.warning(",IntervalIndexJoiner Statistics Log,"+ partition + ",partition," + memorySize
                    + ",memory,"  + joinResultCount + ",results," + joinComparisonCount
                    + ",CPU," + ioCost + ",IO," + leftSpillCount + ",left spills,"
                    + runFileStream[LEFT_PARTITION].getWriteCount() + ",left frames_written,"
                    + runFileStream[LEFT_PARTITION].getReadCount() + ",left frames_read," + rightSpillCount + ",right spills,"
                    + runFileStream[RIGHT_PARTITION].getWriteCount() + ",right frames_written,"
                    + runFileStream[RIGHT_PARTITION].getReadCount() + ",right frames_read");
        }
        System.out.println(",IntervalIndexJoiner Statistics Log,"+ partition + ",partition," + memorySize
                + ",memory,"  + joinResultCount + ",results," + joinComparisonCount
                + ",CPU," + ioCost + ",IO," + leftSpillCount + ",left spills,"
                + runFileStream[LEFT_PARTITION].getWriteCount() + ",left frames_written,"
                + runFileStream[LEFT_PARTITION].getReadCount() + ",left frames_read," + rightSpillCount + ",right spills,"
                + runFileStream[RIGHT_PARTITION].getWriteCount() + ",right frames_written,"
                + runFileStream[RIGHT_PARTITION].getReadCount() + ",right frames_read");
    }

    private boolean checkHasMoreProcessing(TupleStatus ts, int partition, int joinPartition) {
        return ts.isLoaded() || status.branch[partition].isRunFileWriting()
                || (checkHasMoreTuples(joinPartition) && activeManager[partition].hasRecords());
    }

    private boolean checkHasMoreTuples(int partition) {
        return status.branch[partition].hasMore() || status.branch[partition].isRunFileReading();
    }

    private boolean checkToProcessRightTuple() {
        long leftStart = IntervalJoinUtil.getIntervalStart(inputAccessor[LEFT_PARTITION], leftKey);
        long rightStart = IntervalJoinUtil.getIntervalStart(inputAccessor[RIGHT_PARTITION], rightKey);
        if (leftStart < rightStart) {
            // Left stream has next tuple, check if right active must be updated first.
            return activeManager[RIGHT_PARTITION].hasRecords()
                    && activeManager[RIGHT_PARTITION].getTopPoint() < leftStart;
        } else {
            // Right stream has next tuple, check if left active must be update first.
            return !(activeManager[LEFT_PARTITION].hasRecords()
                    && activeManager[LEFT_PARTITION].getTopPoint() < rightStart);
        }
    }

    private boolean checkToProcessAdd(long startMemory, long endMemory) {
        return startMemory < endMemory;
    }

    private TupleStatus processLeftTupleSpill(IFrameWriter writer) throws HyracksDataException {
        // Process left tuples one by one, check them with active memory from the right branch.
        int count = 0;
        TupleStatus ts = loadLeftTuple();
        while (ts.isLoaded() && activeManager[RIGHT_PARTITION].hasRecords()) {
            long sweep = activeManager[RIGHT_PARTITION].getTopPoint();
            if (checkToProcessAdd(IntervalJoinUtil.getIntervalStart(inputAccessor[LEFT_PARTITION], leftKey), sweep)
                    || !imjc.checkToRemoveRightActive()) {
                // Add individual tuples.
                processTupleJoin(activeManager[RIGHT_PARTITION].getActiveList(), memoryAccessor[RIGHT_PARTITION],
                        inputAccessor[LEFT_PARTITION], true, writer);
                if (!runFileStream[LEFT_PARTITION].isReading()) {
                    runFileStream[LEFT_PARTITION].addToRunFile(inputAccessor[LEFT_PARTITION]);
                }
                inputAccessor[LEFT_PARTITION].next();
                ts = loadLeftTuple();
                ++count;
            } else {
                // Remove from active.
                activeManager[RIGHT_PARTITION].removeTop();
            }
        }

        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("Spill for " + count + " left tuples");
        }

        // Memory is empty and we can start processing the run file.
        if (activeManager[RIGHT_PARTITION].isEmpty() || ts.isEmpty()) {
            unfreezeAndContinue(LEFT_PARTITION, inputAccessor[LEFT_PARTITION]);
            ts = loadLeftTuple();
        }
        return ts;
    }

    private TupleStatus processRightTupleSpill(IFrameWriter writer) throws HyracksDataException {
        // Process left tuples one by one, check them with active memory from the right branch.
        int count = 0;
        TupleStatus ts = loadRightTuple();
        while (ts.isLoaded() && activeManager[LEFT_PARTITION].hasRecords() && inputAccessor[RIGHT_PARTITION].exists()) {
            long sweep = activeManager[LEFT_PARTITION].getTopPoint();
            if (checkToProcessAdd(IntervalJoinUtil.getIntervalStart(inputAccessor[RIGHT_PARTITION], rightKey), sweep)
                    || !imjc.checkToRemoveLeftActive()) {
                // Add individual tuples.
                processTupleJoin(activeManager[LEFT_PARTITION].getActiveList(), memoryAccessor[LEFT_PARTITION],
                        inputAccessor[RIGHT_PARTITION], false, writer);
                if (!runFileStream[RIGHT_PARTITION].isReading()) {
                    runFileStream[RIGHT_PARTITION].addToRunFile(inputAccessor[RIGHT_PARTITION]);
                }
                inputAccessor[RIGHT_PARTITION].next();
                ts = loadRightTuple();
                ++count;
            } else {
                // Remove from active.
                activeManager[LEFT_PARTITION].removeTop();
            }
        }

        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("Spill for " + count + " right tuples");
        }

        // Memory is empty and we can start processing the run file.
        if (!activeManager[LEFT_PARTITION].hasRecords() || ts.isEmpty()) {
            unfreezeAndContinue(RIGHT_PARTITION, inputAccessor[RIGHT_PARTITION]);
            ts = loadRightTuple();
        }
        return ts;
    }

    private void processLeftTuple(IFrameWriter writer) throws HyracksDataException {
        // Process endpoints
        do {
            if ((!activeManager[LEFT_PARTITION].hasRecords()
                    || checkToProcessAdd(IntervalJoinUtil.getIntervalStart(inputAccessor[LEFT_PARTITION], leftKey),
                            activeManager[LEFT_PARTITION].getTopPoint()))
                    || !imjc.checkToRemoveLeftActive()) {
                // Add to active, end point index and buffer.
                TuplePointer tp = new TuplePointer();
                if (activeManager[LEFT_PARTITION].addTuple(inputAccessor[LEFT_PARTITION], tp)) {
                    processTupleJoin(activeManager[RIGHT_PARTITION].getActiveList(), memoryAccessor[RIGHT_PARTITION],
                            inputAccessor[LEFT_PARTITION], true, writer);
                    //                    buffer.add(tp);
                } else {
                    // Spill case
                    freezeAndSpill();
                    break;
                }
                inputAccessor[LEFT_PARTITION].next();
            } else {
                // Remove from active.
                activeManager[LEFT_PARTITION].removeTop();
            }
        } while (loadLeftTuple().isLoaded() && loadRightTuple().isLoaded() && !checkToProcessRightTuple());

        // Add Results
        //        if (!buffer.isEmpty()) {
        //            processActiveJoin(activeManager[RIGHT_PARTITION].getActiveList(), memoryAccessor[RIGHT_PARTITION], buffer,
        //                    memoryAccessor[LEFT_PARTITION], true, writer);
        //        }
    }

    private void processRightTuple(IFrameWriter writer) throws HyracksDataException {
        // Process endpoints
        do {
            if ((!activeManager[RIGHT_PARTITION].hasRecords()
                    || checkToProcessAdd(IntervalJoinUtil.getIntervalStart(inputAccessor[RIGHT_PARTITION], rightKey),
                            activeManager[RIGHT_PARTITION].getTopPoint()))
                    || !imjc.checkToRemoveRightActive()) {
                // Add to active, end point index and buffer.
                TuplePointer tp = new TuplePointer();
                if (activeManager[RIGHT_PARTITION].addTuple(inputAccessor[RIGHT_PARTITION], tp)) {
                    processTupleJoin(activeManager[LEFT_PARTITION].getActiveList(), memoryAccessor[LEFT_PARTITION],
                            inputAccessor[RIGHT_PARTITION], false, writer);
                    //                    buffer.add(tp);
                } else {
                    // Spill case
                    freezeAndSpill();
                    break;
                }
                inputAccessor[RIGHT_PARTITION].next();
            } else {
                // Remove from active.
                activeManager[RIGHT_PARTITION].removeTop();
            }
        } while (loadRightTuple().isLoaded() && checkToProcessRightTuple());

        // Add Results
        //        if (!buffer.isEmpty()) {
        //            processActiveJoin(activeManager[LEFT_PARTITION].getActiveList(), memoryAccessor[LEFT_PARTITION], buffer,
        //                    memoryAccessor[RIGHT_PARTITION], false, writer);
        //        }
    }

    //    private void processActiveJoin(List<TuplePointer> outer, ITuplePointerAccessor outerAccessor,
    //            List<TuplePointer> inner, ITuplePointerAccessor innerAccessor, boolean reversed, IFrameWriter writer)
    //            throws HyracksDataException {
    //        for (TuplePointer outerTp : outer) {
    //            outerAccessor.reset(outerTp);
    //            for (TuplePointer innerTp : inner) {
    //                innerAccessor.reset(innerTp);
    //                if (imjc.checkToSaveInResult(outerAccessor, outerTp.getTupleIndex(), innerAccessor,
    //                        innerTp.getTupleIndex(), reversed)) {
    //                    addToResult(outerAccessor, outerTp.getTupleIndex(), innerAccessor, innerTp.getTupleIndex(),
    //                            reversed, writer);
    //                }
    //                joinComparisonCount++;
    //            }
    //        }
    //        if (LOGGER.isLoggable(Level.FINE)) {
    //            LOGGER.fine("Sweep for " + buffer.size() + " tuples");
    //        }
    //        buffer.clear();
    //    }

    private void processTupleJoin(List<TuplePointer> outer, ITupleAccessor outerAccessor,
            ITupleAccessor tupleAccessor, boolean reversed, IFrameWriter writer) throws HyracksDataException {
        for (TuplePointer outerTp : outer) {
            outerAccessor.reset(outerTp);
            if (imjc.checkToSaveInResult(outerAccessor, outerTp.getTupleIndex(), tupleAccessor,
                    tupleAccessor.getTupleId(), reversed)) {
                addToResult(outerAccessor, outerTp.getTupleIndex(), tupleAccessor, tupleAccessor.getTupleId(), reversed,
                        writer);
            }
            joinComparisonCount++;
        }
    }

    private void freezeAndSpill() throws HyracksDataException {
//        if (LOGGER.isLoggable(Level.FINEST)) {
//            LOGGER.finest("freeze snapshot: " + frameCounts[RIGHT_PARTITION] + " right, " + frameCounts[LEFT_PARTITION]
//                    + " left, left[" + bufferManager.getNumTuples(LEFT_PARTITION) + " memory]. right["
//                    + bufferManager.getNumTuples(RIGHT_PARTITION) + " memory].");
//        }
        //        LOGGER.warning("disk IO: right, " + runFileStream[RIGHT_PARTITION].getReadCount() + " left, "
        //                + runFileStream[LEFT_PARTITION].getReadCount());
        int freezePartition;
        if (bufferManager.getNumTuples(LEFT_PARTITION) > bufferManager.getNumTuples(RIGHT_PARTITION)) {
            freezePartition = RIGHT_PARTITION;
            rightSpillCount++;
        } else {
            freezePartition = LEFT_PARTITION;
            leftSpillCount++;
        }

//        LOGGER.warning("freeze snapshot(" + freezePartition + "): " + frameCounts[RIGHT_PARTITION] + " right, "
//                + frameCounts[LEFT_PARTITION] + " left, left[" + bufferManager.getNumTuples(LEFT_PARTITION)
//                + " memory, " + leftSpillCount + " spills, "
//                + (runFileStream[LEFT_PARTITION].getFileCount() - spillFileCount[LEFT_PARTITION]) + " files, "
//                + (runFileStream[LEFT_PARTITION].getWriteCount() - spillWriteCount[LEFT_PARTITION]) + " written, "
//                + (runFileStream[LEFT_PARTITION].getReadCount() - spillReadCount[LEFT_PARTITION]) + " read]. right["
//                + bufferManager.getNumTuples(RIGHT_PARTITION) + " memory, " + +rightSpillCount + " spills, "
//                + (runFileStream[RIGHT_PARTITION].getFileCount() - spillFileCount[RIGHT_PARTITION]) + " files, "
//                + (runFileStream[RIGHT_PARTITION].getWriteCount() - spillWriteCount[RIGHT_PARTITION]) + " written, "
//                + (runFileStream[RIGHT_PARTITION].getReadCount() - spillReadCount[RIGHT_PARTITION]) + " read].");
        spillFileCount[LEFT_PARTITION] = runFileStream[LEFT_PARTITION].getFileCount();
        spillReadCount[LEFT_PARTITION] = runFileStream[LEFT_PARTITION].getReadCount();
        spillWriteCount[LEFT_PARTITION] = runFileStream[LEFT_PARTITION].getWriteCount();
        spillFileCount[RIGHT_PARTITION] = runFileStream[RIGHT_PARTITION].getFileCount();
        spillReadCount[RIGHT_PARTITION] = runFileStream[RIGHT_PARTITION].getReadCount();
        spillWriteCount[RIGHT_PARTITION] = runFileStream[RIGHT_PARTITION].getWriteCount();

        // Mark where to start reading
        if (runFileStream[freezePartition].isReading()) {
            runFilePointer[freezePartition].reset(runFileStream[freezePartition].getReadPointer(),
                    inputAccessor[freezePartition].getTupleId());
        } else {
            runFilePointer[freezePartition].reset(0, 0);
            runFileStream[freezePartition].createRunFileWriting();
        }
        // Start writing
        runFileStream[freezePartition].startRunFileWriting();

        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.finest("Memory is full. Freezing the " + freezePartition + " branch. (Left memory tuples: "
                    + bufferManager.getNumTuples(LEFT_PARTITION) + ", Right memory tuples: "
                    + bufferManager.getNumTuples(RIGHT_PARTITION) + ")");
            bufferManager.printStats("memory details");
        }
    }

    private void continueStream(int diskPartition, ITupleAccessor accessor) throws HyracksDataException {
        // Stop reading.
        runFileStream[diskPartition].closeRunFileReading();
        if (runFilePointer[diskPartition].getFileOffset() < 0) {
            // Remove file if not needed.
            runFileStream[diskPartition].close();
            runFileStream[diskPartition].removeRunFile();
        }

        // Continue on stream
        accessor.reset(inputBuffer[diskPartition].getBuffer());
        accessor.setTupleId(streamIndex[diskPartition]);
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("Continue with stream (" + diskPartition + ").");
        }
    }

    private void unfreezeAndContinue(int frozenPartition, ITupleAccessor accessor) throws HyracksDataException {
        int flushPartition = frozenPartition == LEFT_PARTITION ? RIGHT_PARTITION : LEFT_PARTITION;
        //                if (LOGGER.isLoggable(Level.FINEST)) {
//        LOGGER.warning("unfreeze snapshot(" + frozenPartition + "): " + frameCounts[RIGHT_PARTITION] + " right, "
//                + frameCounts[LEFT_PARTITION] + " left, left[" + bufferManager.getNumTuples(LEFT_PARTITION)
//                + " memory, " + leftSpillCount + " spills, "
//                + (runFileStream[LEFT_PARTITION].getFileCount() - spillFileCount[LEFT_PARTITION]) + " files, "
//                + (runFileStream[LEFT_PARTITION].getWriteCount() - spillWriteCount[LEFT_PARTITION]) + " written, "
//                + (runFileStream[LEFT_PARTITION].getReadCount() - spillReadCount[LEFT_PARTITION]) + " read]. right["
//                + bufferManager.getNumTuples(RIGHT_PARTITION) + " memory, " + rightSpillCount + " spills, "
//                + (runFileStream[RIGHT_PARTITION].getFileCount() - spillFileCount[RIGHT_PARTITION]) + " files, "
//                + (runFileStream[RIGHT_PARTITION].getWriteCount() - spillWriteCount[RIGHT_PARTITION]) + " written, "
//                + (runFileStream[RIGHT_PARTITION].getReadCount() - spillReadCount[RIGHT_PARTITION]) + " read].");
//        spillFileCount[LEFT_PARTITION] = runFileStream[LEFT_PARTITION].getFileCount();
//        spillReadCount[LEFT_PARTITION] = runFileStream[LEFT_PARTITION].getReadCount();
//        spillWriteCount[LEFT_PARTITION] = runFileStream[LEFT_PARTITION].getWriteCount();
//        spillFileCount[RIGHT_PARTITION] = runFileStream[RIGHT_PARTITION].getFileCount();
//        spillReadCount[RIGHT_PARTITION] = runFileStream[RIGHT_PARTITION].getReadCount();
//        spillWriteCount[RIGHT_PARTITION] = runFileStream[RIGHT_PARTITION].getWriteCount();
        //                }

        // Finish writing
        runFileStream[frozenPartition].flushRunFile();

        // Clear memory
        flushMemory(flushPartition);
        if ((LEFT_PARTITION == frozenPartition && !runFileStream[LEFT_PARTITION].isReading())
                || (RIGHT_PARTITION == frozenPartition && !runFileStream[RIGHT_PARTITION].isReading())) {
            streamIndex[frozenPartition] = accessor.getTupleId();
        }

        // Start reading
        runFileStream[frozenPartition].startReadingRunFile(accessor, runFilePointer[frozenPartition].getFileOffset());
        accessor.setTupleId(runFilePointer[frozenPartition].getTupleIndex());
        runFilePointer[frozenPartition].reset(-1, -1);

        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("Unfreezing (" + frozenPartition + ").");
        }
    }

}
