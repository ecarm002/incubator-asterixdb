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
package org.apache.asterix.runtime.operators.joins.disjointintervalpartition;

import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.asterix.runtime.operators.joins.IIntervalMergeJoinChecker;
import org.apache.asterix.runtime.operators.joins.intervalindex.TuplePrinterUtil;
import org.apache.hyracks.api.comm.IFrame;
import org.apache.hyracks.api.comm.IFrameTupleAccessor;
import org.apache.hyracks.api.comm.IFrameWriter;
import org.apache.hyracks.api.comm.VSizeFrame;
import org.apache.hyracks.api.context.IHyracksTaskContext;
import org.apache.hyracks.api.dataflow.value.RecordDescriptor;
import org.apache.hyracks.api.exceptions.HyracksDataException;
import org.apache.hyracks.api.io.FileReference;
import org.apache.hyracks.dataflow.common.comm.util.FrameUtils;
import org.apache.hyracks.dataflow.std.buffermanager.ITupleAccessor;
import org.apache.hyracks.dataflow.std.buffermanager.TupleAccessor;
import org.apache.hyracks.dataflow.std.join.AbstractMergeJoiner;
import org.apache.hyracks.dataflow.std.join.MergeJoinLocks;
import org.apache.hyracks.dataflow.std.join.MergeStatus;
//import org.apache.hyracks.dataflow.common.io.RunFileReader;
//import org.apache.hyracks.dataflow.common.io.RunFileWriter;
import org.apache.hyracks.dataflow.std.join.RunFileReaderDir;
import org.apache.hyracks.dataflow.std.join.RunFileWriterDir;

public class DisjointIntervalPartitionJoiner extends AbstractMergeJoiner {

    private static final String BUILD_RUN_FILES_PREFIX = "disjointIntervalPartitionBuild";
    private static final String PROBE_RUN_FILES_PREFIX = "disjointIntervalPartitionProbe";
    private static final String SPILL_RUN_FILES_PREFIX = "disjointIntervalPartitionSpill";

    private static final Logger LOGGER = Logger.getLogger(DisjointIntervalPartitionJoiner.class.getName());

    private final DisjointIntervalPartitionAndSpill partitionAndSpill;
    private final int numberOfPartitions;
    private final LinkedList<RunFileReaderDir> leftRunFileReaders = new LinkedList<>();
    private final LinkedList<Long> leftPartitionCounts = new LinkedList<>();
    private final LinkedList<RunFileReaderDir> rightRunFileReaders = new LinkedList<>();
    private final LinkedList<Long> rightPartitionCounts = new LinkedList<>();

    private VSizeFrame[] runReaderFrames;
    private ITupleAccessor[] tupleAccessors;

    private final VSizeFrame tmpFrame;
    private final ITupleAccessor joinTupleAccessor;

    private final IHyracksTaskContext ctx;

    private final RecordDescriptor leftRd;
    private final RecordDescriptor rightRd;

    private final IIntervalMergeJoinChecker imjc;

    private long joinComparisonCount = 0;
    private long joinResultCount = 0;
    private long spillPartitionWriteCount = 0;
    private long spillPartitionReadCount = 0;
    private long spillJoinReadCount = 0;

    private final int partition;
    private final int memorySize;
    private DisjointIntervalPartitionComputer rightDipc;

    private PartitionMinItemComparator rightComparator;
    private PartitionMinItemComparator leftComparator;

    public DisjointIntervalPartitionJoiner(IHyracksTaskContext ctx, int memorySize, int partition, MergeStatus status,
            MergeJoinLocks locks, IIntervalMergeJoinChecker imjc, int leftKey, int rightKey, RecordDescriptor leftRd,
            RecordDescriptor rightRd, DisjointIntervalPartitionComputer leftDipc,
            DisjointIntervalPartitionComputer rightDipc, PartitionMinItemComparator rightComparator,
            PartitionMinItemComparator leftComparator) throws HyracksDataException {
        super(ctx, partition, status, locks, leftRd, rightRd);
        this.ctx = ctx;
        this.partition = partition;
        this.rightDipc = rightDipc;
        this.memorySize = memorySize;

        numberOfPartitions = memorySize - 1;

        partitionAndSpill = new DisjointIntervalPartitionAndSpill(ctx, memorySize, numberOfPartitions);
        partitionAndSpill.init();
        partitionAndSpill.resetForNewDataset(leftRd, leftDipc, BUILD_RUN_FILES_PREFIX, getNewSpillWriter());

        this.rightRd = rightRd;
        this.leftRd = leftRd;
        this.imjc = imjc;

        this.rightComparator = rightComparator;
        this.leftComparator = leftComparator;

        tmpFrame = new VSizeFrame(ctx);
        joinTupleAccessor = new TupleAccessor(leftRd);
    }

    private RunFileWriterDir getNewSpillWriter() throws HyracksDataException {
        FileReference file = ctx.getJobletContext().createManagedWorkspaceFile(SPILL_RUN_FILES_PREFIX);
        RunFileWriterDir writer = new RunFileWriterDir(file, ctx.getIOManager());
        writer.open();
        return writer;
    }

    @Override
    public void processLeftFrame(IFrameWriter writer) throws HyracksDataException {
        partitionAndSpill.processFrame(inputBuffer[LEFT_PARTITION].getBuffer());
    }

    private void printPartitionCounts(String key, LinkedList<Long> counts) {
        System.err.println(key + " Partitions{");
        for (int i = 0; i < counts.size(); ++i) {
            System.err.println("  " + i + ": " + counts.get(i) + ",");
        }
        System.err.println("}");
    }

    @Override
    public void processLeftClose(IFrameWriter writer) throws HyracksDataException {
        getRunFileReaders(partitionAndSpill, leftRunFileReaders, leftPartitionCounts);
        // Handle spill file.
        processSpill(partitionAndSpill, leftRunFileReaders, leftPartitionCounts);

        printPartitionCounts("Left", leftPartitionCounts);

        // Probe side
        partitionAndSpill.resetForNewDataset(rightRd, rightDipc, PROBE_RUN_FILES_PREFIX, getNewSpillWriter());
        while (loadRightTuple() == TupleStatus.LOADED) {
            partitionAndSpill.processTupleAccessor(inputAccessor[RIGHT_PARTITION]);
            inputAccessor[RIGHT_PARTITION].next();
        }
        if (partitionAndSpill.hasSpillPartitions()) {
            // Prepare spilled partitions for join
            getRunFileReaders(partitionAndSpill, rightRunFileReaders, rightPartitionCounts);
            processSpill(partitionAndSpill, rightRunFileReaders, rightPartitionCounts);
            processSpilledJoin(writer);
        } else {
            // Perform an in-memory join with LEFT spill files.
            getInMemoryTupleAccessors(partitionAndSpill, rightPartitionCounts);
            processInMemoryJoin(writer);
        }
        printPartitionCounts("Right", rightPartitionCounts);

        partitionAndSpill.close();
        resultAppender.write(writer, true);

        cleanupPartitions(leftRunFileReaders);
        cleanupPartitions(rightRunFileReaders);
        long cpu = joinComparisonCount;
        if (LOGGER.isLoggable(Level.WARNING)) {
            LOGGER.warning(",DisjointIntervalPartitionJoiner Statistics Log," + partition + ",partition," + memorySize
                    + ",memory," + joinResultCount + ",results," + cpu + ",CPU," + spillJoinReadCount + ",IO,"
                    + spillPartitionWriteCount + ",partition_frames_written," + spillPartitionReadCount
                    + ",partition_frames_read," + spillJoinReadCount + ",partition_frames_read,"
                    + leftComparator.getTotalCalled() + ",partition_comparison_left," + rightComparator.getTotalCalled()
                    + ",partition_comparison_right," + joinComparisonCount + ",join_comparison");
        }
        System.out.println(",DisjointIntervalPartitionJoiner Statistics Log," + partition + ",partition," + memorySize
                + ",memory," + joinResultCount + ",results," + cpu + ",CPU," + spillJoinReadCount + ",IO,"
                + spillPartitionWriteCount + ",partition_frames_written," + spillPartitionReadCount
                + ",partition_frames_read," + spillJoinReadCount + ",partition_frames_read,"
                + leftComparator.getTotalCalled() + ",partition_comparison_left," + rightComparator.getTotalCalled()
                + ",partition_comparison_right," + joinComparisonCount + ",join_comparison");
    }

    private void processInMemoryJoin(IFrameWriter writer) throws HyracksDataException {
        //        int i = 0;
        for (int l = 0; l < leftRunFileReaders.size(); l++) {
            //            printRunFileTuples("spilled " + i++ + " on " + partition, leftRunFileReaders.get(l));
            resetInMemoryPartitions();
            leftRunFileReaders.get(l).reset();
            joinInMemoryPartitions(leftRunFileReaders.get(l), l, writer);
        }
    }

    private void joinInMemoryPartitions(RunFileReaderDir runFileReader, int leftPid, IFrameWriter writer)
            throws HyracksDataException {
        // Prepare frame.
        runFileReader.open();
        if (runFileReader.nextFrame(tmpFrame)) {
            joinTupleAccessor.reset(tmpFrame.getBuffer());
            joinTupleAccessor.next();
            spillJoinReadCount++;
        }
        while (joinTupleAccessor.exists()) {
            joinInMemoryPartitionTuple(leftPid, writer);
            loadNextTuple(joinTupleAccessor, runFileReader, tmpFrame);
        }
        runFileReader.close();
    }

    private void joinInMemoryPartitionTuple(int leftPid, IFrameWriter writer) throws HyracksDataException {
        for (int i = 0; i < tupleAccessors.length; i++) {
            while (null != tupleAccessors[i] && tupleAccessors[i].exists()) {
                // Join comparison
                //                printJoinDetails(leftPid, i, i);
                if (imjc.checkToSaveInResult(joinTupleAccessor, tupleAccessors[i])) {
                    addToResult(joinTupleAccessor, joinTupleAccessor.getTupleId(), tupleAccessors[i],
                            tupleAccessors[i].getTupleId(), false, writer);
                }
                joinComparisonCount++;

                // Load next item.
                if (imjc.checkToIncrementMerge(joinTupleAccessor, tupleAccessors[i])) {
                    // Still can compare this tuple. Do not advance partition.
                    break;
                } else {
                    tupleAccessors[i].next();
                }
            }
        }
    }

    private void resetInMemoryPartitions() {
        for (int i = 0; i < tupleAccessors.length; i++) {
            if (null != tupleAccessors[i]) {
                tupleAccessors[i].reset();
                tupleAccessors[i].next();
            }
        }
    }

    private void getInMemoryTupleAccessors(DisjointIntervalPartitionAndSpill dipas, LinkedList<Long> rpc) {
        tupleAccessors = new ITupleAccessor[numberOfPartitions];
        for (int i = 0; i < numberOfPartitions; i++) {
            if (dipas.getPartitionSizeInTup(i) > 0) {
                rpc.add(dipas.getPartitionSizeInTup(i));
                tupleAccessors[i] = dipas.getPartitionTupleAccessor(i);
                tupleAccessors[i].reset();
                tupleAccessors[i].next();
            } else {
                tupleAccessors[i] = null;
            }
        }
    }

    private TupleStatus loadRightTuple() throws HyracksDataException {
        TupleStatus loaded = loadMemoryTuple(RIGHT_PARTITION);
        if (loaded == TupleStatus.UNKNOWN) {
            loaded = pauseAndLoadRightTuple();
        }
        return loaded;
    }

    private void processSpill(DisjointIntervalPartitionAndSpill dipas, LinkedList<RunFileReaderDir> rfrs,
            LinkedList<Long> rpc) throws HyracksDataException {
        while (dipas.getSpillSizeInTup() > 0) {
            RunFileReaderDir rfr = dipas.getSpillRFReader();
            dipas.reset(getNewSpillWriter());
            rfr.open();
            while (rfr.nextFrame(tmpFrame)) {
                dipas.processFrame(tmpFrame.getBuffer());
                spillPartitionReadCount++;
            }
            rfr.close();
            getRunFileReaders(dipas, rfrs, rpc);
        }
    }

    private void getRunFileReaders(DisjointIntervalPartitionAndSpill dipas, LinkedList<RunFileReaderDir> rfrs,
            LinkedList<Long> rpc) throws HyracksDataException {
        //        int offset = rfrs.size();
        dipas.spillAllPartitions();
        spillPartitionWriteCount += dipas.getSpillWriteCount();
        for (int i = 0; i < numberOfPartitions; i++) {
            if (dipas.getPartitionSizeInTup(i) > 0) {
                rfrs.add(dipas.getRFReader(i));
                rpc.add(dipas.getPartitionSizeInTup(i));
                //                printRunFileTuples("spilled " + (i + offset) + " on " + partition, dipas.getRFReader(i));
            } else {
                break;
            }
        }
    }

    private void printRunFileTuples(String message, RunFileReaderDir rfReader) throws HyracksDataException {
        rfReader.open();
        rfReader.reset();
        if (rfReader.nextFrame(tmpFrame)) {
            joinTupleAccessor.reset(tmpFrame.getBuffer());
            joinTupleAccessor.next();
        }
        while (joinTupleAccessor.exists()) {
            TuplePrinterUtil.printTuple("RunFile: " + message, joinTupleAccessor);
            loadNextTuple(joinTupleAccessor, rfReader, tmpFrame);
        }
        rfReader.close();
    }

    private void processSpilledJoin(IFrameWriter writer) throws HyracksDataException {
        prepareFrames(numberOfPartitions, rightRunFileReaders);

        int count = 0;
        for (int offset = 0; offset < rightRunFileReaders.size(); offset += numberOfPartitions) {
            openPartitions(rightRunFileReaders, offset);
            for (int l = 0; l < leftRunFileReaders.size(); l++) {
                resetPartitions(rightRunFileReaders, offset);
                leftRunFileReaders.get(l).reset();
                joinPartition(leftRunFileReaders.get(l), l, rightRunFileReaders, offset, writer);
            }
            closePartitions(rightRunFileReaders, offset);
            count++;
        }
        System.err.println("Disjoint Interval Partition Join in " + count + " passes over spilled data.");
    }

    private void openPartitions(List<RunFileReaderDir> partitionRunsReaders, int offset) throws HyracksDataException {
        for (int i = 0; i < tupleAccessors.length && i + offset < partitionRunsReaders.size(); i++) {
            partitionRunsReaders.get(i + offset).open();
        }
    }

    private void resetPartitions(List<RunFileReaderDir> partitionRunsReaders, int offset) throws HyracksDataException {
        for (int i = 0; i < tupleAccessors.length && i + offset < partitionRunsReaders.size(); i++) {
            partitionRunsReaders.get(i + offset).reset();
            if (partitionRunsReaders.get(i + offset).nextFrame(runReaderFrames[i])) {
                tupleAccessors[i].reset(runReaderFrames[i].getBuffer());
                tupleAccessors[i].next();
                spillJoinReadCount++;
            }
        }
    }

    private void closePartitions(List<RunFileReaderDir> partitionRunsReaders, int offset) throws HyracksDataException {
        for (int i = 0; i < tupleAccessors.length && i + offset < partitionRunsReaders.size(); i++) {
            partitionRunsReaders.get(i + offset).close();
        }
    }

    private void cleanupPartitions(List<RunFileReaderDir> partitionRunsReaders) throws HyracksDataException {
        for (int i = 0; i < partitionRunsReaders.size(); i++) {
            partitionRunsReaders.get(i).close();
            partitionRunsReaders.get(i).delete();
        }
    }

    private void joinPartition(RunFileReaderDir runFileReader, int leftPid, LinkedList<RunFileReaderDir> partitionRunsReaders,
            int offset, IFrameWriter writer) throws HyracksDataException {
        // Prepare frame.
        runFileReader.open();
        if (runFileReader.nextFrame(tmpFrame)) {
            joinTupleAccessor.reset(tmpFrame.getBuffer());
            joinTupleAccessor.next();
            spillJoinReadCount++;
        }
        while (joinTupleAccessor.exists()) {
            joinPartitionTuple(leftPid, partitionRunsReaders, offset, writer);
            loadNextTuple(joinTupleAccessor, runFileReader, tmpFrame);
        }
        runFileReader.close();
    }

    private void loadNextTuple(ITupleAccessor accessor, RunFileReaderDir reader, IFrame frame)
            throws HyracksDataException {
        accessor.next();
        if (!accessor.exists()) {
            // Load next frame.
            if (reader.nextFrame(frame)) {
                accessor.reset(frame.getBuffer());
                accessor.next();
                spillJoinReadCount++;
            }
        }
    }

    private void joinPartitionTuple(int leftPid, LinkedList<RunFileReaderDir> partitionRunsReaders, int offset,
            IFrameWriter writer) throws HyracksDataException {
        for (int i = 0; i < tupleAccessors.length && i + offset < partitionRunsReaders.size(); i++) {
            while (tupleAccessors[i].exists()) {
                // Join comparison
                //                printJoinDetails(leftPid, i + offset, i);
                if (imjc.checkToSaveInResult(joinTupleAccessor, tupleAccessors[i])) {
                    addToResult(joinTupleAccessor, joinTupleAccessor.getTupleId(), tupleAccessors[i],
                            tupleAccessors[i].getTupleId(), false, writer);
                }
                joinComparisonCount++;

                // Load next item.
                if (imjc.checkToIncrementMerge(joinTupleAccessor, tupleAccessors[i])) {
                    // Still can compare this tuple. Do not advance partition.
                    break;
                } else {
                    loadNextTuple(tupleAccessors[i], partitionRunsReaders.get(i + offset), runReaderFrames[i]);
                }
            }
        }
    }

    private void printJoinDetails(int leftPid, int rightPid, int i) throws HyracksDataException {
        System.err.println("joining : " + leftPid + " looping on partition: " + rightPid);
        TuplePrinterUtil.printTuple("Left:", joinTupleAccessor);
        //        System.err.println("Left Interval: " + ipLeft.toString());
        TuplePrinterUtil.printTuple("  Right", tupleAccessors[i]);
        //        System.err.println("  Right Interval: " + ipRight.toString());
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

    private void prepareFrames(int partitionMemory, List<RunFileReaderDir> partitionRunsReaders)
            throws HyracksDataException {
        int size = Math.min(partitionMemory, partitionRunsReaders.size());
        runReaderFrames = new VSizeFrame[size];
        tupleAccessors = new ITupleAccessor[size];
        for (int i = 0; i < size; i++) {
            runReaderFrames[i] = new VSizeFrame(ctx);
            tupleAccessors[i] = new TupleAccessor(rightRd);
        }
    }

    public void failureCleanUp() throws HyracksDataException {
        cleanupPartitions(leftRunFileReaders);
        cleanupPartitions(rightRunFileReaders);
    }

}
