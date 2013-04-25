/*
 * Copyright 2009-2012 by The Regents of the University of California
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * you may obtain a copy of the License from
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.uci.ics.asterix.aql.translator;

import java.io.File;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import edu.uci.ics.asterix.api.common.APIFramework;
import edu.uci.ics.asterix.api.common.APIFramework.DisplayFormat;
import edu.uci.ics.asterix.api.common.Job;
import edu.uci.ics.asterix.api.common.SessionConfig;
import edu.uci.ics.asterix.aql.base.Statement;
import edu.uci.ics.asterix.aql.expression.BeginFeedStatement;
import edu.uci.ics.asterix.aql.expression.ControlFeedStatement;
import edu.uci.ics.asterix.aql.expression.CreateDataverseStatement;
import edu.uci.ics.asterix.aql.expression.CreateFunctionStatement;
import edu.uci.ics.asterix.aql.expression.CreateIndexStatement;
import edu.uci.ics.asterix.aql.expression.DatasetDecl;
import edu.uci.ics.asterix.aql.expression.DataverseDecl;
import edu.uci.ics.asterix.aql.expression.DataverseDropStatement;
import edu.uci.ics.asterix.aql.expression.DeleteStatement;
import edu.uci.ics.asterix.aql.expression.DropStatement;
import edu.uci.ics.asterix.aql.expression.ExternalDetailsDecl;
import edu.uci.ics.asterix.aql.expression.FeedDetailsDecl;
import edu.uci.ics.asterix.aql.expression.FunctionDecl;
import edu.uci.ics.asterix.aql.expression.FunctionDropStatement;
import edu.uci.ics.asterix.aql.expression.Identifier;
import edu.uci.ics.asterix.aql.expression.IndexDropStatement;
import edu.uci.ics.asterix.aql.expression.InsertStatement;
import edu.uci.ics.asterix.aql.expression.InternalDetailsDecl;
import edu.uci.ics.asterix.aql.expression.LoadFromFileStatement;
import edu.uci.ics.asterix.aql.expression.NodeGroupDropStatement;
import edu.uci.ics.asterix.aql.expression.NodegroupDecl;
import edu.uci.ics.asterix.aql.expression.Query;
import edu.uci.ics.asterix.aql.expression.SetStatement;
import edu.uci.ics.asterix.aql.expression.TypeDecl;
import edu.uci.ics.asterix.aql.expression.TypeDropStatement;
import edu.uci.ics.asterix.aql.expression.WriteFromQueryResultStatement;
import edu.uci.ics.asterix.aql.expression.WriteStatement;
import edu.uci.ics.asterix.common.config.DatasetConfig.DatasetType;
import edu.uci.ics.asterix.common.config.GlobalConfig;
import edu.uci.ics.asterix.common.exceptions.AsterixException;
import edu.uci.ics.asterix.common.functions.FunctionSignature;
import edu.uci.ics.asterix.file.DatasetOperations;
import edu.uci.ics.asterix.file.FeedOperations;
import edu.uci.ics.asterix.file.IndexOperations;
import edu.uci.ics.asterix.formats.base.IDataFormat;
import edu.uci.ics.asterix.metadata.IDatasetDetails;
import edu.uci.ics.asterix.metadata.MetadataException;
import edu.uci.ics.asterix.metadata.MetadataManager;
import edu.uci.ics.asterix.metadata.MetadataTransactionContext;
import edu.uci.ics.asterix.metadata.api.IMetadataEntity;
import edu.uci.ics.asterix.metadata.declared.AqlMetadataProvider;
import edu.uci.ics.asterix.metadata.entities.Dataset;
import edu.uci.ics.asterix.metadata.entities.Datatype;
import edu.uci.ics.asterix.metadata.entities.Dataverse;
import edu.uci.ics.asterix.metadata.entities.ExternalDatasetDetails;
import edu.uci.ics.asterix.metadata.entities.FeedDatasetDetails;
import edu.uci.ics.asterix.metadata.entities.Function;
import edu.uci.ics.asterix.metadata.entities.Index;
import edu.uci.ics.asterix.metadata.entities.InternalDatasetDetails;
import edu.uci.ics.asterix.metadata.entities.NodeGroup;
import edu.uci.ics.asterix.om.types.ARecordType;
import edu.uci.ics.asterix.om.types.ATypeTag;
import edu.uci.ics.asterix.om.types.IAType;
import edu.uci.ics.asterix.om.types.TypeSignature;
import edu.uci.ics.asterix.result.ResultReader;
import edu.uci.ics.asterix.result.ResultUtils;
import edu.uci.ics.asterix.transaction.management.exception.ACIDException;
import edu.uci.ics.asterix.transaction.management.service.transaction.DatasetIdFactory;
import edu.uci.ics.asterix.translator.AbstractAqlTranslator;
import edu.uci.ics.asterix.translator.CompiledStatements.CompiledBeginFeedStatement;
import edu.uci.ics.asterix.translator.CompiledStatements.CompiledControlFeedStatement;
import edu.uci.ics.asterix.translator.CompiledStatements.CompiledCreateIndexStatement;
import edu.uci.ics.asterix.translator.CompiledStatements.CompiledDatasetDropStatement;
import edu.uci.ics.asterix.translator.CompiledStatements.CompiledDeleteStatement;
import edu.uci.ics.asterix.translator.CompiledStatements.CompiledIndexDropStatement;
import edu.uci.ics.asterix.translator.CompiledStatements.CompiledInsertStatement;
import edu.uci.ics.asterix.translator.CompiledStatements.CompiledLoadFromFileStatement;
import edu.uci.ics.asterix.translator.CompiledStatements.CompiledWriteFromQueryResultStatement;
import edu.uci.ics.asterix.translator.CompiledStatements.ICompiledDmlStatement;
import edu.uci.ics.asterix.translator.TypeTranslator;
import edu.uci.ics.hyracks.algebricks.common.exceptions.AlgebricksException;
import edu.uci.ics.hyracks.algebricks.common.utils.Pair;
import edu.uci.ics.hyracks.algebricks.core.algebra.expressions.AbstractFunctionCallExpression.FunctionKind;
import edu.uci.ics.hyracks.algebricks.data.IAWriterFactory;
import edu.uci.ics.hyracks.algebricks.data.IResultSerializerFactoryProvider;
import edu.uci.ics.hyracks.algebricks.runtime.serializer.ResultSerializerFactoryProvider;
import edu.uci.ics.hyracks.algebricks.runtime.writers.PrinterBasedWriterFactory;
import edu.uci.ics.hyracks.api.client.IHyracksClientConnection;
import edu.uci.ics.hyracks.api.dataset.IHyracksDataset;
import edu.uci.ics.hyracks.api.dataset.ResultSetId;
import edu.uci.ics.hyracks.api.io.FileReference;
import edu.uci.ics.hyracks.api.job.JobId;
import edu.uci.ics.hyracks.api.job.JobSpecification;
import edu.uci.ics.hyracks.dataflow.std.file.FileSplit;

/*
 * Provides functionality for executing a batch of AQL statements (queries included)
 * sequentially.
 */
public class AqlTranslator extends AbstractAqlTranslator {

    private final List<Statement> aqlStatements;
    private final PrintWriter out;
    private final SessionConfig sessionConfig;
    private final DisplayFormat pdf;
    private Dataverse activeDefaultDataverse;
    private List<FunctionDecl> declaredFunctions;

    public AqlTranslator(List<Statement> aqlStatements, PrintWriter out, SessionConfig pc, DisplayFormat pdf)
            throws MetadataException, AsterixException {
        this.aqlStatements = aqlStatements;
        this.out = out;
        this.sessionConfig = pc;
        this.pdf = pdf;
        declaredFunctions = getDeclaredFunctions(aqlStatements);
    }

    private List<FunctionDecl> getDeclaredFunctions(List<Statement> statements) {
        List<FunctionDecl> functionDecls = new ArrayList<FunctionDecl>();
        for (Statement st : statements) {
            if (st.getKind().equals(Statement.Kind.FUNCTION_DECL)) {
                functionDecls.add((FunctionDecl) st);
            }
        }
        return functionDecls;
    }

    private enum LatchType {
        READ,
        WRITE
    };

    /**
     * Compiles and submits for execution a list of AQL statements.
     * 
     * @param hcc
     *            A Hyracks client connection that is used to submit a jobspec to Hyracks.
     * @param hdc
     *            A Hyracks dataset client object that is used to read the results.
     * @param asyncResults
     *            True if the results should be read asynchronously or false if we should wait for results to be read.
     * @return A List<QueryResult> containing a QueryResult instance corresponding to each submitted query.
     * @throws Exception
     */
    public List<QueryResult> compileAndExecute(IHyracksClientConnection hcc, IHyracksDataset hdc, boolean asyncResults)
            throws Exception {
        int resultSetIdCounter = 0;
        List<QueryResult> executionResult = new ArrayList<QueryResult>();
        FileSplit outputFile = null;
        IAWriterFactory writerFactory = PrinterBasedWriterFactory.INSTANCE;
        IResultSerializerFactoryProvider resultSerializerFactoryProvider = ResultSerializerFactoryProvider.INSTANCE;
        Map<String, String> config = new HashMap<String, String>();
        List<JobSpecification> jobsToExecute = new ArrayList<JobSpecification>();
        LatchType latchType = LatchType.READ;

        for (Statement stmt : aqlStatements) {
            validateOperation(activeDefaultDataverse, stmt);
            AqlMetadataProvider metadataProvider = new AqlMetadataProvider(activeDefaultDataverse);
            metadataProvider.setWriterFactory(writerFactory);
            metadataProvider.setResultSerializerFactoryProvider(resultSerializerFactoryProvider);
            metadataProvider.setOutputFile(outputFile);
            metadataProvider.setConfig(config);
            jobsToExecute.clear();
            MetadataTransactionContext mdTxnCtx;
            StatementExecutionContext stmtExecCtx = null;
            try {
                mdTxnCtx = MetadataManager.INSTANCE.beginTransaction();
                metadataProvider.setMetadataTxnContext(mdTxnCtx);
                switch (stmt.getKind()) {
                    case SET: {
                        handleSetStatement(metadataProvider, stmt, config);
                        break;
                    }
                    case DATAVERSE_DECL: {
                        latchType = LatchType.READ;
                        acquireLatch(latchType);
                        stmtExecCtx = handleUseDataverseStatement(metadataProvider, stmt);
                        break;
                    }
                    case CREATE_DATAVERSE: {
                        latchType = LatchType.WRITE;
                        acquireLatch(latchType);
                        stmtExecCtx = handleCreateDataverseStatement(metadataProvider, stmt);
                        break;
                    }
                    case DATASET_DECL: {
                        latchType = LatchType.WRITE;
                        acquireLatch(latchType);
                        stmtExecCtx = handleCreateDatasetStatement(metadataProvider, stmt, hcc);
                        break;
                    }
                    case CREATE_INDEX: {
                        latchType = LatchType.WRITE;
                        acquireLatch(latchType);
                        stmtExecCtx = handleCreateIndexStatement(metadataProvider, stmt, hcc);
                        break;
                    }
                    case TYPE_DECL: {
                        latchType = LatchType.WRITE;
                        acquireLatch(latchType);
                        stmtExecCtx = handleCreateTypeStatement(metadataProvider, stmt);
                        break;
                    }
                    case NODEGROUP_DECL: {
                        latchType = LatchType.WRITE;
                        acquireLatch(latchType);
                        stmtExecCtx = handleCreateNodeGroupStatement(metadataProvider, stmt);
                        break;
                    }
                    case DATAVERSE_DROP: {
                        latchType = LatchType.WRITE;
                        acquireLatch(latchType);
                        stmtExecCtx = handleDataverseDropStatement(metadataProvider, stmt, hcc);
                        break;
                    }
                    case DATASET_DROP: {
                        latchType = LatchType.WRITE;
                        acquireLatch(latchType);
                        stmtExecCtx = handleDatasetDropStatement(metadataProvider, stmt, hcc);
                        break;
                    }
                    case INDEX_DROP: {
                        latchType = LatchType.WRITE;
                        acquireLatch(latchType);
                        stmtExecCtx = handleIndexDropStatement(metadataProvider, stmt, hcc);
                        break;
                    }
                    case TYPE_DROP: {
                        latchType = LatchType.WRITE;
                        acquireLatch(latchType);
                        stmtExecCtx = handleTypeDropStatement(metadataProvider, stmt);
                        break;
                    }
                    case NODEGROUP_DROP: {
                        latchType = LatchType.WRITE;
                        acquireLatch(latchType);
                        stmtExecCtx = handleNodegroupDropStatement(metadataProvider, stmt);
                        break;
                    }

                    case CREATE_FUNCTION: {
                        latchType = LatchType.WRITE;
                        acquireLatch(latchType);
                        stmtExecCtx = handleCreateFunctionStatement(metadataProvider, stmt);
                        break;
                    }

                    case FUNCTION_DROP: {
                        latchType = LatchType.WRITE;
                        acquireLatch(latchType);
                        stmtExecCtx = handleFunctionDropStatement(metadataProvider, stmt);
                        break;
                    }

                    case LOAD_FROM_FILE: {
                        latchType = LatchType.READ;
                        acquireLatch(latchType);
                        stmtExecCtx = handleLoadFromFileStatement(metadataProvider, stmt, hcc);
                        break;
                    }
                    case WRITE_FROM_QUERY_RESULT: {
                        stmtExecCtx = handleWriteFromQueryResultStatement(metadataProvider, stmt, hcc);
                        break;
                    }
                    case INSERT: {
                        latchType = LatchType.READ;
                        acquireLatch(latchType);
                        stmtExecCtx = handleInsertStatement(metadataProvider, stmt, hcc);
                        break;
                    }
                    case DELETE: {
                        latchType = LatchType.READ;
                        acquireLatch(latchType);
                        stmtExecCtx = handleDeleteStatement(metadataProvider, stmt, hcc);
                        break;
                    }

                    case BEGIN_FEED: {
                        latchType = LatchType.READ;
                        acquireLatch(latchType);
                        stmtExecCtx = handleBeginFeedStatement(metadataProvider, stmt, hcc);
                        break;
                    }

                    case CONTROL_FEED: {
                        latchType = LatchType.READ;
                        acquireLatch(latchType);
                        stmtExecCtx = handleControlFeedStatement(metadataProvider, stmt, hcc);
                        break;
                    }

                    case QUERY: {
                        metadataProvider.setResultSetId(new ResultSetId(resultSetIdCounter++));
                        executionResult.add(handleQuery(metadataProvider, (Query) stmt, hcc, hdc, asyncResults));
                        break;
                    }

                    case WRITE: {
                        Pair<IAWriterFactory, FileSplit> result = handleWriteStatement(metadataProvider, stmt);
                        if (result.first != null) {
                            writerFactory = result.first;
                        }
                        outputFile = result.second;
                        break;
                    }

                }
                MetadataManager.INSTANCE.commitTransaction(mdTxnCtx);

                MetadataTransactionContext postSuccessTxn = null;
                MetadataTransactionContext postFailureTxn = null;

                if (stmtExecCtx != null) {
                    try {
                        JobSpecification[] jobs = stmtExecCtx.getJobSpec();
                        try {
                            for (JobSpecification jobSpec : jobs) {
                                runJob(hcc, jobSpec, true);
                            }
                            IPostStatementSuccess postSuccess = stmtExecCtx.getPostSuccess();
                            if (postSuccess != null) {
                                postSuccessTxn = MetadataManager.INSTANCE.beginTransaction();
                                postSuccess.doPostSuccess(postSuccessTxn);
                            }
                        } catch (Exception e) {
                            if (postSuccessTxn == null) {
                                IPostStatementFailure postFailure = stmtExecCtx.getPostFailure();
                                if (postFailure != null) {
                                    postFailureTxn = MetadataManager.INSTANCE.beginTransaction();
                                    postFailure.doPostFailure(postFailureTxn);
                                }
                            } else {
                                MetadataManager.INSTANCE.abortTransaction(postSuccessTxn);
                            }
                        }
                    } catch (Exception e) {
                        MetadataManager.INSTANCE.abortTransaction(postFailureTxn);
                    }
                }
            } catch (Exception e) {
                abortInCatchBlock(e, e, mdTxnCtx);
                throw new AlgebricksException(new MetadataException(e));
            } finally {
                releaseLatch(latchType);
            }
        }
        return executionResult;
    }

    private void handleSetStatement(AqlMetadataProvider metadataProvider, Statement stmt, Map<String, String> config)
            throws RemoteException, ACIDException {
        SetStatement ss = (SetStatement) stmt;
        String pname = ss.getPropName();
        String pvalue = ss.getPropValue();
        config.put(pname, pvalue);
    }

    private Pair<IAWriterFactory, FileSplit> handleWriteStatement(AqlMetadataProvider metadataProvider, Statement stmt)
            throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        WriteStatement ws = (WriteStatement) stmt;
        File f = new File(ws.getFileName());
        FileSplit outputFile = new FileSplit(ws.getNcName().getValue(), new FileReference(f));
        IAWriterFactory writerFactory = null;
        if (ws.getWriterClassName() != null) {
            writerFactory = (IAWriterFactory) Class.forName(ws.getWriterClassName()).newInstance();
        }
        return new Pair<IAWriterFactory, FileSplit>(writerFactory, outputFile);
    }

    private StatementExecutionContext handleUseDataverseStatement(AqlMetadataProvider metadataProvider, Statement stmt)
            throws Exception {
        DataverseDecl dvd = (DataverseDecl) stmt;
        String dvName = dvd.getDataverseName().getValue();
        Dataverse dv = MetadataManager.INSTANCE.getDataverse(metadataProvider.getMetadataTxnContext(), dvName);
        if (dv == null) {
            throw new MetadataException("Unknown dataverse " + dvName);
        }
        activeDefaultDataverse = dv;
        return null;
    }

    private StatementExecutionContext handleCreateDataverseStatement(AqlMetadataProvider metadataProvider,
            Statement stmt) throws Exception {
        CreateDataverseStatement stmtCreateDataverse = (CreateDataverseStatement) stmt;
        String dvName = stmtCreateDataverse.getDataverseName().getValue();
        Dataverse dv = MetadataManager.INSTANCE.getDataverse(metadataProvider.getMetadataTxnContext(), dvName);
        if (dv != null) {
            if (stmtCreateDataverse.getIfNotExists()) {
            } else {
                throw new AlgebricksException("A dataverse with this name " + dvName + " already exists.");
            }
        }
        MetadataManager.INSTANCE.addDataverse(metadataProvider.getMetadataTxnContext(), new Dataverse(dvName,
                stmtCreateDataverse.getFormat(), IMetadataEntity.PENDING_NO_OP));
        return null;
    }

    private StatementExecutionContext handleCreateDatasetStatement(AqlMetadataProvider metadataProvider,
            Statement stmt, IHyracksClientConnection hcc) throws AsterixException, Exception {

        String dataverseName = null;
        String datasetName = null;
        Dataset dataset = null;
        DatasetDecl dd = (DatasetDecl) stmt;
        dataverseName = dd.getDataverse() != null ? dd.getDataverse().getValue()
                : activeDefaultDataverse != null ? activeDefaultDataverse.getDataverseName() : null;
        if (dataverseName == null) {
            throw new AlgebricksException(" dataverse not specified ");
        }
        datasetName = dd.getName().getValue();

        DatasetType dsType = dd.getDatasetType();
        String itemTypeName = dd.getItemTypeName().getValue();

        IDatasetDetails datasetDetails = null;
        Dataset ds = MetadataManager.INSTANCE.getDataset(metadataProvider.getMetadataTxnContext(), dataverseName,
                datasetName);
        if (ds != null) {
            if (dd.getIfNotExists()) {
                return null;
            } else {
                throw new AlgebricksException("A dataset with this name " + datasetName + " already exists.");
            }
        }
        Datatype dt = MetadataManager.INSTANCE.getDatatype(metadataProvider.getMetadataTxnContext(), dataverseName,
                itemTypeName);
        if (dt == null) {
            throw new AlgebricksException(": type " + itemTypeName + " could not be found.");
        }
        switch (dd.getDatasetType()) {
            case INTERNAL: {
                IAType itemType = dt.getDatatype();
                if (itemType.getTypeTag() != ATypeTag.RECORD) {
                    throw new AlgebricksException("Can only partition ARecord's.");
                }
                List<String> partitioningExprs = ((InternalDetailsDecl) dd.getDatasetDetailsDecl())
                        .getPartitioningExprs();
                ARecordType aRecordType = (ARecordType) itemType;
                aRecordType.validatePartitioningExpressions(partitioningExprs);
                String ngName = ((InternalDetailsDecl) dd.getDatasetDetailsDecl()).getNodegroupName().getValue();
                datasetDetails = new InternalDatasetDetails(InternalDatasetDetails.FileStructure.BTREE,
                        InternalDatasetDetails.PartitioningStrategy.HASH, partitioningExprs, partitioningExprs, ngName);
                break;
            }
            case EXTERNAL: {
                String adapter = ((ExternalDetailsDecl) dd.getDatasetDetailsDecl()).getAdapter();
                Map<String, String> properties = ((ExternalDetailsDecl) dd.getDatasetDetailsDecl()).getProperties();
                datasetDetails = new ExternalDatasetDetails(adapter, properties);
                break;
            }
            case FEED: {
                IAType itemType = dt.getDatatype();
                if (itemType.getTypeTag() != ATypeTag.RECORD) {
                    throw new AlgebricksException("Can only partition ARecord's.");
                }
                List<String> partitioningExprs = ((FeedDetailsDecl) dd.getDatasetDetailsDecl()).getPartitioningExprs();
                ARecordType aRecordType = (ARecordType) itemType;
                aRecordType.validatePartitioningExpressions(partitioningExprs);
                String ngName = ((FeedDetailsDecl) dd.getDatasetDetailsDecl()).getNodegroupName().getValue();
                String adapter = ((FeedDetailsDecl) dd.getDatasetDetailsDecl()).getAdapterFactoryClassname();
                Map<String, String> configuration = ((FeedDetailsDecl) dd.getDatasetDetailsDecl()).getConfiguration();
                FunctionSignature signature = ((FeedDetailsDecl) dd.getDatasetDetailsDecl()).getFunctionSignature();
                datasetDetails = new FeedDatasetDetails(InternalDatasetDetails.FileStructure.BTREE,
                        InternalDatasetDetails.PartitioningStrategy.HASH, partitioningExprs, partitioningExprs, ngName,
                        adapter, configuration, signature, FeedDatasetDetails.FeedState.INACTIVE.toString());
                break;
            }
        }

        //#. add a new dataset with PendingAddOp
        dataset = new Dataset(dataverseName, datasetName, itemTypeName, datasetDetails, dd.getHints(), dsType,
                DatasetIdFactory.generateDatasetId(), IMetadataEntity.PENDING_ADD_OP);
        MetadataManager.INSTANCE.addDataset(metadataProvider.getMetadataTxnContext(), dataset);

        StatementExecutionContext stmtExecCtx = null;
        if (dd.getDatasetType() == DatasetType.INTERNAL || dd.getDatasetType() == DatasetType.FEED) {
            Dataverse dataverse = MetadataManager.INSTANCE.getDataverse(metadataProvider.getMetadataTxnContext(),
                    dataverseName);
            JobSpecification jobSpec = DatasetOperations.createDatasetJobSpec(dataverse, datasetName, metadataProvider);

            IPostStatementSuccess postSuccess = new AbstractPostStatementSuccess(new Object[] { dataset }) {
                @Override
                public void doPostSuccess(MetadataTransactionContext mdTxnCtx) throws Exception {
                    Dataset ds = (Dataset) getObject(0);
                    MetadataManager.INSTANCE.dropDataset(mdTxnCtx, ds.getDataverseName(), ds.getDatasetName());
                    ds.setPendingOp(IMetadataEntity.PENDING_NO_OP);
                    MetadataManager.INSTANCE.addDataset(mdTxnCtx, ds);
                }
            };

            IPostStatementFailure postFailure = new AbstractPostStatementFailure(new Object[] { dataset,
                    metadataProvider, hcc }) {
                @Override
                public void doPostFailure(MetadataTransactionContext mdTxnCtx) throws Exception {
                    Dataset ds = (Dataset) getObject(0);
                    AqlMetadataProvider metadataProvider = (AqlMetadataProvider) getObject(1);
                    IHyracksClientConnection hcc = (IHyracksClientConnection) getObject(2);

                    mdTxnCtx = MetadataManager.INSTANCE.beginTransaction();
                    CompiledDatasetDropStatement cds = new CompiledDatasetDropStatement(ds.getDataverseName(),
                            ds.getDatasetName());
                    JobSpecification jobSpec = DatasetOperations.createDropDatasetJobSpec(cds, metadataProvider);
                    MetadataManager.INSTANCE.commitTransaction(mdTxnCtx);
                    runJob(hcc, jobSpec, true);

                    //   remove the record from the metadata.
                    mdTxnCtx = MetadataManager.INSTANCE.beginTransaction();
                    metadataProvider.setMetadataTxnContext(mdTxnCtx);
                    MetadataManager.INSTANCE.dropDataset(metadataProvider.getMetadataTxnContext(),
                            ds.getDataverseName(), ds.getDatasetName());
                    MetadataManager.INSTANCE.commitTransaction(mdTxnCtx);

                }
            };

            stmtExecCtx = new StatementExecutionContext(new JobSpecification[] { jobSpec }, postSuccess, postFailure);
        }
        return stmtExecCtx;
    }

    private StatementExecutionContext handleCreateIndexStatement(AqlMetadataProvider metadataProvider, Statement stmt,
            IHyracksClientConnection hcc) throws Exception {

        String dataverseName = null;
        String datasetName = null;
        String indexName = null;
        JobSpecification spec = null;
        CreateIndexStatement stmtCreateIndex = (CreateIndexStatement) stmt;
        dataverseName = stmtCreateIndex.getDataverseName() == null ? activeDefaultDataverse == null ? null
                : activeDefaultDataverse.getDataverseName() : stmtCreateIndex.getDataverseName().getValue();
        if (dataverseName == null) {
            throw new AlgebricksException(" dataverse not specified ");
        }
        datasetName = stmtCreateIndex.getDatasetName().getValue();

        Dataset ds = MetadataManager.INSTANCE.getDataset(metadataProvider.getMetadataTxnContext(), dataverseName,
                datasetName);
        if (ds == null) {
            throw new AlgebricksException("There is no dataset with this name " + datasetName + " in dataverse "
                    + dataverseName);
        }

        indexName = stmtCreateIndex.getIndexName().getValue();
        Index idx = MetadataManager.INSTANCE.getIndex(metadataProvider.getMetadataTxnContext(), dataverseName,
                datasetName, indexName);

        if (idx != null) {
            if (stmtCreateIndex.getIfNotExists()) {
                return null;
            } else {
                throw new AlgebricksException("An index with this name " + indexName + " already exists.");
            }
        }

        //#. add a new index with PendingAddOp
        Index index = new Index(dataverseName, datasetName, indexName, stmtCreateIndex.getIndexType(),
                stmtCreateIndex.getFieldExprs(), stmtCreateIndex.getGramLength(), false, IMetadataEntity.PENDING_ADD_OP);
        MetadataManager.INSTANCE.addIndex(metadataProvider.getMetadataTxnContext(), index);

        //#. prepare to create the index artifact in NC.
        CompiledCreateIndexStatement cis = new CompiledCreateIndexStatement(index.getIndexName(), dataverseName,
                index.getDatasetName(), index.getKeyFieldNames(), index.getGramLength(), index.getIndexType());
        spec = IndexOperations.buildSecondaryIndexCreationJobSpec(cis, metadataProvider);
        if (spec == null) {
            throw new AsterixException("Failed to create job spec for creating index '"
                    + stmtCreateIndex.getDatasetName() + "." + stmtCreateIndex.getIndexName() + "'");
        }

        StatementExecutionContext stmtExecCtx = null;

        IPostStatementSuccess postSuccess = new AbstractPostStatementSuccess(new Object[] { index, metadataProvider,
                hcc }) {
            @Override
            public void doPostSuccess(MetadataTransactionContext mdTxnCtx) throws Exception {
                Index index = (Index) getObject(0);
                AqlMetadataProvider metadataProvider = (AqlMetadataProvider) getObject(1);
                IHyracksClientConnection hcc = (IHyracksClientConnection) getObject(2);

                //#. load data into the index in NC.
                CompiledCreateIndexStatement cis = new CompiledCreateIndexStatement(index.getIndexName(),
                        index.getDataverseName(), index.getDatasetName(), index.getKeyFieldNames(),
                        index.getGramLength(), index.getIndexType());
                JobSpecification indexLoadingSpec = IndexOperations.buildSecondaryIndexLoadingJobSpec(cis,
                        metadataProvider);
                runJob(hcc, indexLoadingSpec, true);
                metadataProvider.setMetadataTxnContext(mdTxnCtx);

                //#. add another new index with PendingNoOp after deleting the index with PendingAddOp
                MetadataManager.INSTANCE.dropIndex(metadataProvider.getMetadataTxnContext(), index.getDataverseName(),
                        index.getDatasetName(), index.getIndexName());
                index.setPendingOp(IMetadataEntity.PENDING_NO_OP);
                MetadataManager.INSTANCE.addIndex(metadataProvider.getMetadataTxnContext(), index);
                MetadataManager.INSTANCE.commitTransaction(mdTxnCtx);
            }
        };

        IPostStatementFailure postFailure = new AbstractPostStatementFailure(new Object[] { index, metadataProvider,
                hcc }) {
            @Override
            public void doPostFailure(MetadataTransactionContext mdTxnCtx) throws Exception {
                Index index = (Index) getObject(0);
                AqlMetadataProvider metadataProvider = (AqlMetadataProvider) getObject(1);
                IHyracksClientConnection hcc = (IHyracksClientConnection) getObject(2);

                mdTxnCtx = MetadataManager.INSTANCE.beginTransaction();
                metadataProvider.setMetadataTxnContext(mdTxnCtx);
                CompiledIndexDropStatement cds = new CompiledIndexDropStatement(index.getDataverseName(),
                        index.getDatasetName(), index.getIndexName());
                JobSpecification jobSpec = IndexOperations.buildDropSecondaryIndexJobSpec(cds, metadataProvider);
                MetadataManager.INSTANCE.commitTransaction(mdTxnCtx);
                runJob(hcc, jobSpec, true);

                //   remove the record from the metadata.
                mdTxnCtx = MetadataManager.INSTANCE.beginTransaction();
                metadataProvider.setMetadataTxnContext(mdTxnCtx);
                MetadataManager.INSTANCE.dropIndex(metadataProvider.getMetadataTxnContext(), index.getDataverseName(),
                        index.getDatasetName(), index.getIndexName());
                MetadataManager.INSTANCE.commitTransaction(mdTxnCtx);

            }
        };

        stmtExecCtx = new StatementExecutionContext(new JobSpecification[] { spec }, postSuccess, postFailure);
        return stmtExecCtx;
    }

    private StatementExecutionContext handleCreateTypeStatement(AqlMetadataProvider metadataProvider, Statement stmt)
            throws Exception {

        TypeDecl stmtCreateType = (TypeDecl) stmt;
        String dataverseName = stmtCreateType.getDataverseName() == null ? activeDefaultDataverse == null ? null
                : activeDefaultDataverse.getDataverseName() : stmtCreateType.getDataverseName().getValue();
        if (dataverseName == null) {
            throw new AlgebricksException(" dataverse not specified ");
        }
        String typeName = stmtCreateType.getIdent().getValue();
        Dataverse dv = MetadataManager.INSTANCE.getDataverse(metadataProvider.getMetadataTxnContext(), dataverseName);
        if (dv == null) {
            throw new AlgebricksException("Unknonw dataverse " + dataverseName);
        }
        Datatype dt = MetadataManager.INSTANCE.getDatatype(metadataProvider.getMetadataTxnContext(), dataverseName,
                typeName);
        if (dt != null) {
            if (!stmtCreateType.getIfNotExists()) {
                throw new AlgebricksException("A datatype with this name " + typeName + " already exists.");
            }
        } else {
            if (builtinTypeMap.get(typeName) != null) {
                throw new AlgebricksException("Cannot redefine builtin type " + typeName + ".");
            } else {
                Map<TypeSignature, IAType> typeMap = TypeTranslator.computeTypes(
                        metadataProvider.getMetadataTxnContext(), (TypeDecl) stmt, dataverseName);
                TypeSignature typeSignature = new TypeSignature(dataverseName, typeName);
                IAType type = typeMap.get(typeSignature);
                MetadataManager.INSTANCE.addDatatype(metadataProvider.getMetadataTxnContext(), new Datatype(
                        dataverseName, typeName, type, false));
            }
        }
        return null;
    }

    private StatementExecutionContext handleDataverseDropStatement(AqlMetadataProvider metadataProvider,
            Statement stmt, IHyracksClientConnection hcc) throws Exception {

        MetadataTransactionContext mdTxnCtx = metadataProvider.getMetadataTxnContext();
        String dataverseName = null;
        List<JobSpecification> jobsToExecute = new ArrayList<JobSpecification>();

        DataverseDropStatement stmtDelete = (DataverseDropStatement) stmt;
        dataverseName = stmtDelete.getDataverseName().getValue();

        Dataverse dv = MetadataManager.INSTANCE.getDataverse(mdTxnCtx, dataverseName);
        if (dv == null) {
            if (stmtDelete.getIfExists()) {
                MetadataManager.INSTANCE.commitTransaction(mdTxnCtx);
                return null;
            } else {
                throw new AlgebricksException("There is no dataverse with this name " + dataverseName + ".");
            }
        }

        //#. prepare jobs which will drop corresponding datasets with indexes. 
        List<Dataset> datasets = MetadataManager.INSTANCE.getDataverseDatasets(mdTxnCtx, dataverseName);
        for (int j = 0; j < datasets.size(); j++) {
            String datasetName = datasets.get(j).getDatasetName();
            DatasetType dsType = datasets.get(j).getDatasetType();
            if (dsType == DatasetType.INTERNAL || dsType == DatasetType.FEED) {

                List<Index> indexes = MetadataManager.INSTANCE.getDatasetIndexes(mdTxnCtx, dataverseName, datasetName);
                for (int k = 0; k < indexes.size(); k++) {
                    if (indexes.get(k).isSecondaryIndex()) {
                        CompiledIndexDropStatement cds = new CompiledIndexDropStatement(dataverseName, datasetName,
                                indexes.get(k).getIndexName());
                        jobsToExecute.add(IndexOperations.buildDropSecondaryIndexJobSpec(cds, metadataProvider));
                    }
                }

                CompiledDatasetDropStatement cds = new CompiledDatasetDropStatement(dataverseName, datasetName);
                jobsToExecute.add(DatasetOperations.createDropDatasetJobSpec(cds, metadataProvider));
            }
        }

        //#. mark PendingDropOp on the dataverse record by 
        //   first, deleting the dataverse record from the DATAVERSE_DATASET
        //   second, inserting the dataverse record with the PendingDropOp value into the DATAVERSE_DATASET
        MetadataManager.INSTANCE.dropDataverse(mdTxnCtx, dataverseName);
        MetadataManager.INSTANCE.addDataverse(mdTxnCtx, new Dataverse(dataverseName, dv.getDataFormat(),
                IMetadataEntity.PENDING_DROP_OP));

        MetadataManager.INSTANCE.commitTransaction(mdTxnCtx);

        StatementExecutionContext stmtExecCtx = null;
        JobSpecification[] jobSpecs = jobsToExecute.toArray(new JobSpecification[] {});

        IPostStatementSuccess postSuccess = new AbstractPostStatementSuccess(new Object[] { dataverseName }) {
            @Override
            public void doPostSuccess(MetadataTransactionContext mdTxnCtx) throws Exception {
                String dataverseName = (String) getObject(0);
                //#. finally, delete the dataverse.
                MetadataManager.INSTANCE.dropDataverse(mdTxnCtx, dataverseName);
                if (activeDefaultDataverse != null && activeDefaultDataverse.getDataverseName() == dataverseName) {
                    activeDefaultDataverse = null;
                }
            }
        };

        IPostStatementFailure postFailure = new AbstractPostStatementFailure(new Object[] { dataverseName,
                jobsToExecute, metadataProvider, hcc }) {
            @Override
            public void doPostFailure(MetadataTransactionContext mdTxnCtx) throws Exception {

                String dataverseName = (String) getObject(0);
                List<JobSpecification> jobsToExecute = (List<JobSpecification>) getObject(1);
                AqlMetadataProvider metadataProvider = (AqlMetadataProvider) getObject(2);
                IHyracksClientConnection hcc = (IHyracksClientConnection) getObject(3);

                if (activeDefaultDataverse != null && activeDefaultDataverse.getDataverseName() == dataverseName) {
                    activeDefaultDataverse = null;
                }

                //#. execute compensation operations
                //   remove the all indexes in NC
                for (JobSpecification jobSpec : jobsToExecute) {
                    runJob(hcc, jobSpec, true);
                }

                //   remove the record from the metadata.
                mdTxnCtx = MetadataManager.INSTANCE.beginTransaction();
                metadataProvider.setMetadataTxnContext(mdTxnCtx);
                MetadataManager.INSTANCE.dropDataverse(metadataProvider.getMetadataTxnContext(), dataverseName);
                MetadataManager.INSTANCE.commitTransaction(mdTxnCtx);
            }
        };

        stmtExecCtx = new StatementExecutionContext(jobSpecs, postSuccess, postFailure);
        return stmtExecCtx;
    }

    private StatementExecutionContext handleDatasetDropStatement(AqlMetadataProvider metadataProvider, Statement stmt,
            IHyracksClientConnection hcc) throws Exception {

        MetadataTransactionContext mdTxnCtx = metadataProvider.getMetadataTxnContext();
        String dataverseName = null;
        String datasetName = null;
        List<JobSpecification> jobsToExecute = new ArrayList<JobSpecification>();

        DropStatement stmtDelete = (DropStatement) stmt;
        dataverseName = stmtDelete.getDataverseName() == null ? activeDefaultDataverse == null ? null
                : activeDefaultDataverse.getDataverseName() : stmtDelete.getDataverseName().getValue();
        if (dataverseName == null) {
            throw new AlgebricksException(" dataverse not specified ");
        }
        datasetName = stmtDelete.getDatasetName().getValue();

        Dataset ds = MetadataManager.INSTANCE.getDataset(mdTxnCtx, dataverseName, datasetName);
        if (ds == null) {
            if (stmtDelete.getIfExists()) {
                MetadataManager.INSTANCE.commitTransaction(mdTxnCtx);
                return null;
            } else {
                throw new AlgebricksException("There is no dataset with this name " + datasetName + " in dataverse "
                        + dataverseName + ".");
            }
        }

        StatementExecutionContext stmtExecCtx = null;
        if (ds.getDatasetType() == DatasetType.INTERNAL || ds.getDatasetType() == DatasetType.FEED) {

            //#. prepare jobs to drop the datatset and the indexes in NC
            List<Index> indexes = MetadataManager.INSTANCE.getDatasetIndexes(mdTxnCtx, dataverseName, datasetName);
            for (int j = 0; j < indexes.size(); j++) {
                if (indexes.get(j).isSecondaryIndex()) {
                    CompiledIndexDropStatement cds = new CompiledIndexDropStatement(dataverseName, datasetName, indexes
                            .get(j).getIndexName());
                    jobsToExecute.add(IndexOperations.buildDropSecondaryIndexJobSpec(cds, metadataProvider));
                }
            }
            CompiledDatasetDropStatement cds = new CompiledDatasetDropStatement(dataverseName, datasetName);
            jobsToExecute.add(DatasetOperations.createDropDatasetJobSpec(cds, metadataProvider));

            JobSpecification[] jobSpecs = jobsToExecute.toArray(new JobSpecification[] {});

            IPostStatementSuccess postSuccess = new AbstractPostStatementSuccess(new Object[] { ds, metadataProvider,
                    hcc }) {
                @Override
                public void doPostSuccess(MetadataTransactionContext mdTxnCtx) throws Exception {
                    Dataset ds = (Dataset) getObject(0);
                    AqlMetadataProvider metadataProvider = (AqlMetadataProvider) getObject(1);
                    IHyracksClientConnection hcc = (IHyracksClientConnection) getObject(2);

                    //#. mark the existing dataset as PendingDropOp
                    MetadataManager.INSTANCE.dropDataset(mdTxnCtx, ds.getDataverseName(), ds.getDatasetName());
                    ds.setPendingOp(IMetadataEntity.PENDING_DROP_OP);
                    MetadataManager.INSTANCE.addDataset(mdTxnCtx, ds);
                }
            };

            IPostStatementFailure postFailure = new AbstractPostStatementFailure(new Object[] { ds, metadataProvider,
                    jobsToExecute, hcc }) {
                @Override
                public void doPostFailure(MetadataTransactionContext mdTxnCtx) throws Exception {
                    Dataset ds = (Dataset) getObject(0);
                    AqlMetadataProvider metadataProvider = (AqlMetadataProvider) getObject(1);
                    List<JobSpecification> jobsToExecute = (List<JobSpecification>) getObject(2);
                    IHyracksClientConnection hcc = (IHyracksClientConnection) getObject(3);

                    for (JobSpecification jobSpec : jobsToExecute) {
                        runJob(hcc, jobSpec, true);
                    }

                    //   remove the record from the metadata.
                    mdTxnCtx = MetadataManager.INSTANCE.beginTransaction();
                    metadataProvider.setMetadataTxnContext(mdTxnCtx);
                    MetadataManager.INSTANCE.dropDataset(metadataProvider.getMetadataTxnContext(),
                            ds.getDataverseName(), ds.getDatasetName());
                    MetadataManager.INSTANCE.commitTransaction(mdTxnCtx);
                }
            };

            stmtExecCtx = new StatementExecutionContext(jobSpecs, postSuccess, postFailure);

        }
        return stmtExecCtx;

    }

    private StatementExecutionContext handleIndexDropStatement(AqlMetadataProvider metadataProvider, Statement stmt,
            IHyracksClientConnection hcc) throws Exception {

        MetadataTransactionContext mdTxnCtx = metadataProvider.getMetadataTxnContext();
        String dataverseName = null;
        String datasetName = null;
        String indexName = null;
        List<JobSpecification> jobsToExecute = new ArrayList<JobSpecification>();

        IndexDropStatement stmtIndexDrop = (IndexDropStatement) stmt;
        datasetName = stmtIndexDrop.getDatasetName().getValue();
        dataverseName = stmtIndexDrop.getDataverseName() == null ? activeDefaultDataverse == null ? null
                : activeDefaultDataverse.getDataverseName() : stmtIndexDrop.getDataverseName().getValue();
        if (dataverseName == null) {
            throw new AlgebricksException(" dataverse not specified ");
        }

        Dataset ds = MetadataManager.INSTANCE.getDataset(mdTxnCtx, dataverseName, datasetName);
        if (ds == null) {
            throw new AlgebricksException("There is no dataset with this name " + datasetName + " in dataverse "
                    + dataverseName);
        }

        StatementExecutionContext stmtExecCtx = null;
        if (ds.getDatasetType() == DatasetType.INTERNAL || ds.getDatasetType() == DatasetType.FEED) {
            indexName = stmtIndexDrop.getIndexName().getValue();
            Index index = MetadataManager.INSTANCE.getIndex(mdTxnCtx, dataverseName, datasetName, indexName);
            if (index == null) {
                if (stmtIndexDrop.getIfExists()) {
                    MetadataManager.INSTANCE.commitTransaction(mdTxnCtx);
                    return null;
                } else {
                    throw new AlgebricksException("There is no index with this name " + indexName + ".");
                }
            }
            //#. prepare a job to drop the index in NC.
            CompiledIndexDropStatement cds = new CompiledIndexDropStatement(dataverseName, datasetName, indexName);
            jobsToExecute.add(IndexOperations.buildDropSecondaryIndexJobSpec(cds, metadataProvider));

            //#. mark PendingDropOp on the existing index
            MetadataManager.INSTANCE.dropIndex(mdTxnCtx, dataverseName, datasetName, indexName);
            MetadataManager.INSTANCE.addIndex(mdTxnCtx,
                    new Index(dataverseName, datasetName, indexName, index.getIndexType(), index.getKeyFieldNames(),
                            index.isPrimaryIndex(), IMetadataEntity.PENDING_DROP_OP));

            JobSpecification[] jobSpecs = jobsToExecute.toArray(new JobSpecification[] {});

            IPostStatementSuccess postSuccess = new AbstractPostStatementSuccess(new Object[] { dataverseName,
                    datasetName, indexName, metadataProvider }) {
                @Override
                public void doPostSuccess(MetadataTransactionContext mdTxnCtx) throws Exception {
                    String dataverseName = (String) getObject(0);
                    String datasetName = (String) getObject(1);
                    String indexName = (String) getObject(2);
                    AqlMetadataProvider metadataProvider = (AqlMetadataProvider) getObject(3);

                    //#. begin a new transaction
                    mdTxnCtx = MetadataManager.INSTANCE.beginTransaction();
                    metadataProvider.setMetadataTxnContext(mdTxnCtx);

                    //#. finally, delete the existing index
                    MetadataManager.INSTANCE.dropIndex(mdTxnCtx, dataverseName, datasetName, indexName);

                }
            };

            IPostStatementFailure postFailure = new AbstractPostStatementFailure(new Object[] { index, jobsToExecute,
                    metadataProvider, hcc }) {
                @Override
                public void doPostFailure(MetadataTransactionContext mdTxnCtx) throws Exception {

                    Index index = (Index) getObject(0);
                    List<JobSpecification> jobsToExecute = (List<JobSpecification>) getObject(1);
                    AqlMetadataProvider metadataProvider = (AqlMetadataProvider) getObject(2);
                    IHyracksClientConnection hcc = (IHyracksClientConnection) getObject(3);

                    //#. execute compensation operations
                    //   remove the all indexes in NC
                    for (JobSpecification jobSpec : jobsToExecute) {
                        runJob(hcc, jobSpec, true);
                    }

                    mdTxnCtx = MetadataManager.INSTANCE.beginTransaction();
                    metadataProvider.setMetadataTxnContext(mdTxnCtx);
                    MetadataManager.INSTANCE.dropIndex(metadataProvider.getMetadataTxnContext(),
                            index.getDataverseName(), index.getDatasetName(), index.getIndexName());
                    MetadataManager.INSTANCE.commitTransaction(mdTxnCtx);

                }
            };

            stmtExecCtx = new StatementExecutionContext(jobSpecs, postSuccess, postFailure);
        }

        return stmtExecCtx;
    }

    private StatementExecutionContext handleTypeDropStatement(AqlMetadataProvider metadataProvider, Statement stmt)
            throws Exception {

        MetadataTransactionContext mdTxnCtx = metadataProvider.getMetadataTxnContext();

        TypeDropStatement stmtTypeDrop = (TypeDropStatement) stmt;
        String dataverseName = stmtTypeDrop.getDataverseName() == null ? (activeDefaultDataverse == null ? null
                : activeDefaultDataverse.getDataverseName()) : stmtTypeDrop.getDataverseName().getValue();
        if (dataverseName == null) {
            throw new AlgebricksException(" dataverse not specified ");
        }
        String typeName = stmtTypeDrop.getTypeName().getValue();
        Datatype dt = MetadataManager.INSTANCE.getDatatype(mdTxnCtx, dataverseName, typeName);
        if (dt == null) {
            if (!stmtTypeDrop.getIfExists())
                throw new AlgebricksException("There is no datatype with this name " + typeName + ".");
        } else {
            MetadataManager.INSTANCE.dropDatatype(mdTxnCtx, dataverseName, typeName);
        }
        return null;
    }

    private StatementExecutionContext handleNodegroupDropStatement(AqlMetadataProvider metadataProvider, Statement stmt)
            throws Exception {
        MetadataTransactionContext mdTxnCtx = metadataProvider.getMetadataTxnContext();
        NodeGroupDropStatement stmtDelete = (NodeGroupDropStatement) stmt;
        String nodegroupName = stmtDelete.getNodeGroupName().getValue();
        NodeGroup ng = MetadataManager.INSTANCE.getNodegroup(mdTxnCtx, nodegroupName);
        if (ng == null) {
            if (!stmtDelete.getIfExists())
                throw new AlgebricksException("There is no nodegroup with this name " + nodegroupName + ".");
        } else {
            MetadataManager.INSTANCE.dropNodegroup(mdTxnCtx, nodegroupName);
        }
        return null;
    }

    private StatementExecutionContext handleCreateFunctionStatement(AqlMetadataProvider metadataProvider, Statement stmt)
            throws Exception {
        MetadataTransactionContext mdTxnCtx = metadataProvider.getMetadataTxnContext();
        CreateFunctionStatement cfs = (CreateFunctionStatement) stmt;
        String dataverse = cfs.getSignature().getNamespace() == null ? activeDefaultDataverse == null ? null
                : activeDefaultDataverse.getDataverseName() : cfs.getSignature().getNamespace();
        if (dataverse == null) {
            throw new AlgebricksException(" dataverse not specified ");
        }
        Dataverse dv = MetadataManager.INSTANCE.getDataverse(mdTxnCtx, dataverse);
        if (dv == null) {
            throw new AlgebricksException("There is no dataverse with this name " + dataverse + ".");
        }
        Function function = new Function(dataverse, cfs.getaAterixFunction().getName(), cfs.getaAterixFunction()
                .getArity(), cfs.getParamList(), Function.RETURNTYPE_VOID, cfs.getFunctionBody(),
                Function.LANGUAGE_AQL, FunctionKind.SCALAR.toString());
        MetadataManager.INSTANCE.addFunction(mdTxnCtx, function);
        return null;
    }

    private StatementExecutionContext handleFunctionDropStatement(AqlMetadataProvider metadataProvider, Statement stmt)
            throws Exception {
        MetadataTransactionContext mdTxnCtx = metadataProvider.getMetadataTxnContext();
        FunctionDropStatement stmtDropFunction = (FunctionDropStatement) stmt;
        FunctionSignature signature = stmtDropFunction.getFunctionSignature();
        Function function = MetadataManager.INSTANCE.getFunction(mdTxnCtx, signature);
        if (function == null) {
            if (!stmtDropFunction.getIfExists())
                throw new AlgebricksException("Unknonw function " + signature);
        } else {
            MetadataManager.INSTANCE.dropFunction(mdTxnCtx, signature);
        }
        return null;
    }

    private StatementExecutionContext handleLoadFromFileStatement(AqlMetadataProvider metadataProvider, Statement stmt,
            IHyracksClientConnection hcc) throws Exception {
        MetadataTransactionContext mdTxnCtx = metadataProvider.getMetadataTxnContext();
        List<JobSpecification> jobsToExecute = new ArrayList<JobSpecification>();
        LoadFromFileStatement loadStmt = (LoadFromFileStatement) stmt;
        String dataverseName = loadStmt.getDataverseName() == null ? activeDefaultDataverse == null ? null
                : activeDefaultDataverse.getDataverseName() : loadStmt.getDataverseName().getValue();
        CompiledLoadFromFileStatement cls = new CompiledLoadFromFileStatement(dataverseName, loadStmt.getDatasetName()
                .getValue(), loadStmt.getAdapter(), loadStmt.getProperties(), loadStmt.dataIsAlreadySorted());

        IDataFormat format = getDataFormat(metadataProvider.getMetadataTxnContext(), dataverseName);
        Job job = DatasetOperations.createLoadDatasetJobSpec(metadataProvider, cls, format);
        jobsToExecute.add(job.getJobSpec());
        // Also load the dataset's secondary indexes.
        List<Index> datasetIndexes = MetadataManager.INSTANCE.getDatasetIndexes(mdTxnCtx, dataverseName, loadStmt
                .getDatasetName().getValue());
        for (Index index : datasetIndexes) {
            if (!index.isSecondaryIndex()) {
                continue;
            }
            // Create CompiledCreateIndexStatement from metadata entity 'index'.
            CompiledCreateIndexStatement cis = new CompiledCreateIndexStatement(index.getIndexName(), dataverseName,
                    index.getDatasetName(), index.getKeyFieldNames(), index.getGramLength(), index.getIndexType());
            jobsToExecute.add(IndexOperations.buildSecondaryIndexLoadingJobSpec(cis, metadataProvider));
        }

        JobSpecification[] jobSpecs = jobsToExecute.toArray(new JobSpecification[] {});
        StatementExecutionContext stmtExecCtx = new StatementExecutionContext(jobSpecs, null, null);
        return stmtExecCtx;
    }

    private StatementExecutionContext handleWriteFromQueryResultStatement(AqlMetadataProvider metadataProvider,
            Statement stmt, IHyracksClientConnection hcc) throws Exception {
        metadataProvider.setWriteTransaction(true);
        WriteFromQueryResultStatement st1 = (WriteFromQueryResultStatement) stmt;
        String dataverseName = st1.getDataverseName() == null ? activeDefaultDataverse == null ? null
                : activeDefaultDataverse.getDataverseName() : st1.getDataverseName().getValue();
        CompiledWriteFromQueryResultStatement clfrqs = new CompiledWriteFromQueryResultStatement(dataverseName, st1
                .getDatasetName().getValue(), st1.getQuery(), st1.getVarCounter());

        JobSpecification compiled = rewriteCompileQuery(metadataProvider, clfrqs.getQuery(), clfrqs);
        StatementExecutionContext stmtExecCtx = null;
        if (compiled != null) {
            JobSpecification[] jobSpecs = new JobSpecification[] { compiled };
            stmtExecCtx = new StatementExecutionContext(jobSpecs, null, null);
        }
        return stmtExecCtx;
    }

    private StatementExecutionContext handleInsertStatement(AqlMetadataProvider metadataProvider, Statement stmt,
            IHyracksClientConnection hcc) throws Exception {
        metadataProvider.setWriteTransaction(true);
        InsertStatement stmtInsert = (InsertStatement) stmt;
        String dataverseName = stmtInsert.getDataverseName() == null ? activeDefaultDataverse == null ? null
                : activeDefaultDataverse.getDataverseName() : stmtInsert.getDataverseName().getValue();
        CompiledInsertStatement clfrqs = new CompiledInsertStatement(dataverseName, stmtInsert.getDatasetName()
                .getValue(), stmtInsert.getQuery(), stmtInsert.getVarCounter());
        JobSpecification compiled = rewriteCompileQuery(metadataProvider, clfrqs.getQuery(), clfrqs);

        StatementExecutionContext stmtExecCtx = null;
        if (compiled != null) {
            JobSpecification[] jobSpecs = new JobSpecification[] { compiled };
            stmtExecCtx = new StatementExecutionContext(jobSpecs, null, null);

        }
        return stmtExecCtx;
    }

    private StatementExecutionContext handleDeleteStatement(AqlMetadataProvider metadataProvider, Statement stmt,
            IHyracksClientConnection hcc) throws Exception {

        metadataProvider.setWriteTransaction(true);
        DeleteStatement stmtDelete = (DeleteStatement) stmt;
        String dataverseName = stmtDelete.getDataverseName() == null ? activeDefaultDataverse == null ? null
                : activeDefaultDataverse.getDataverseName() : stmtDelete.getDataverseName().getValue();
        CompiledDeleteStatement clfrqs = new CompiledDeleteStatement(stmtDelete.getVariableExpr(), dataverseName,
                stmtDelete.getDatasetName().getValue(), stmtDelete.getCondition(), stmtDelete.getDieClause(),
                stmtDelete.getVarCounter(), metadataProvider);
        JobSpecification compiled = rewriteCompileQuery(metadataProvider, clfrqs.getQuery(), clfrqs);
        StatementExecutionContext stmtExecCtx = null;

        if (compiled != null) {
            JobSpecification[] jobSpecs = new JobSpecification[] { compiled };
            stmtExecCtx = new StatementExecutionContext(jobSpecs, null, null);
        }
        return stmtExecCtx;
    }

    private JobSpecification rewriteCompileQuery(AqlMetadataProvider metadataProvider, Query query,
            ICompiledDmlStatement stmt) throws AsterixException, RemoteException, AlgebricksException, JSONException,
            ACIDException {

        // Query Rewriting (happens under the same ongoing metadata transaction)
        Pair<Query, Integer> reWrittenQuery = APIFramework.reWriteQuery(declaredFunctions, metadataProvider, query,
                sessionConfig, out, pdf);

        // Query Compilation (happens under the same ongoing metadata
        // transaction)
        JobSpecification spec = APIFramework.compileQuery(declaredFunctions, metadataProvider, query,
                reWrittenQuery.second, stmt == null ? null : stmt.getDatasetName(), sessionConfig, out, pdf, stmt);

        return spec;

    }

    private StatementExecutionContext handleBeginFeedStatement(AqlMetadataProvider metadataProvider, Statement stmt,
            IHyracksClientConnection hcc) throws Exception {

        MetadataTransactionContext mdTxnCtx = metadataProvider.getMetadataTxnContext();

        BeginFeedStatement bfs = (BeginFeedStatement) stmt;
        String dataverseName = bfs.getDataverseName() == null ? activeDefaultDataverse == null ? null
                : activeDefaultDataverse.getDataverseName() : bfs.getDataverseName().getValue();

        CompiledBeginFeedStatement cbfs = new CompiledBeginFeedStatement(dataverseName,
                bfs.getDatasetName().getValue(), bfs.getQuery(), bfs.getVarCounter());

        Dataset dataset;
        dataset = MetadataManager.INSTANCE.getDataset(metadataProvider.getMetadataTxnContext(), dataverseName, bfs
                .getDatasetName().getValue());
        if (dataset == null) {
            throw new AsterixException("Unknown dataset :" + bfs.getDatasetName().getValue());
        }
        IDatasetDetails datasetDetails = dataset.getDatasetDetails();
        if (datasetDetails.getDatasetType() != DatasetType.FEED) {
            throw new IllegalArgumentException("Dataset " + bfs.getDatasetName().getValue() + " is not a feed dataset");
        }
        bfs.initialize(metadataProvider.getMetadataTxnContext(), dataset);
        cbfs.setQuery(bfs.getQuery());
        JobSpecification compiled = rewriteCompileQuery(metadataProvider, bfs.getQuery(), cbfs);
        StatementExecutionContext stmtExecCtx = null;

        if (compiled != null) {
            JobSpecification[] jobSpecs = new JobSpecification[] { compiled };
            stmtExecCtx = new StatementExecutionContext(jobSpecs, null, null);
        }
        return stmtExecCtx;
    }

    private StatementExecutionContext handleControlFeedStatement(AqlMetadataProvider metadataProvider, Statement stmt,
            IHyracksClientConnection hcc) throws Exception {
        MetadataTransactionContext mdTxnCtx = metadataProvider.getMetadataTxnContext();
        metadataProvider.setMetadataTxnContext(mdTxnCtx);
        acquireReadLatch();

        ControlFeedStatement cfs = (ControlFeedStatement) stmt;
        String dataverseName = cfs.getDataverseName() == null ? activeDefaultDataverse == null ? null
                : activeDefaultDataverse.getDataverseName() : cfs.getDatasetName().getValue();
        CompiledControlFeedStatement clcfs = new CompiledControlFeedStatement(cfs.getOperationType(), dataverseName,
                cfs.getDatasetName().getValue(), cfs.getAlterAdapterConfParams());
        JobSpecification jobSpec = FeedOperations.buildControlFeedJobSpec(clcfs, metadataProvider);

        StatementExecutionContext stmtExecCtx = null;

        if (jobSpec != null) {
            JobSpecification[] jobSpecs = new JobSpecification[] { jobSpec };
            stmtExecCtx = new StatementExecutionContext(jobSpecs, null, null);
        }
        return stmtExecCtx;
    }

    private QueryResult handleQuery(AqlMetadataProvider metadataProvider, Query query, IHyracksClientConnection hcc,
            IHyracksDataset hdc, boolean asyncResults) throws Exception {

        MetadataTransactionContext mdTxnCtx = MetadataManager.INSTANCE.beginTransaction();
        boolean bActiveTxn = true;
        metadataProvider.setMetadataTxnContext(mdTxnCtx);
        acquireReadLatch();

        try {
            JobSpecification compiled = rewriteCompileQuery(metadataProvider, query, null);

            QueryResult queryResult = new QueryResult(query, metadataProvider.getResultSetId());
            MetadataManager.INSTANCE.commitTransaction(mdTxnCtx);
            bActiveTxn = false;

            if (compiled != null) {
                GlobalConfig.ASTERIX_LOGGER.info(compiled.toJSON().toString(1));
                JobId jobId = runJob(hcc, compiled, false);

                JSONObject response = new JSONObject();
                if (asyncResults) {
                    JSONArray handle = new JSONArray();
                    handle.put(jobId.getId());
                    handle.put(metadataProvider.getResultSetId().getId());
                    response.put("handle", handle);
                } else {
                    ByteBuffer buffer = ByteBuffer.allocate(ResultReader.FRAME_SIZE);
                    ResultReader resultReader = new ResultReader(hcc, hdc);
                    resultReader.open(jobId, metadataProvider.getResultSetId());
                    buffer.clear();
                    JSONArray results = new JSONArray();
                    while (resultReader.read(buffer) > 0) {
                        results.put(ResultUtils.getJSONFromBuffer(buffer, resultReader.getFrameTupleAccessor()));
                        buffer.clear();
                    }
                    response.put("results", results);
                }
                switch (pdf) {
                    case HTML:
                        out.println("<pre>");
                        ResultUtils.prettyPrintHTML(out, response);
                        out.println("</pre>");
                        break;
                    case TEXT:
                    case JSON:
                        out.print(response);
                        break;
                }
                hcc.waitForCompletion(jobId);
            }

            return queryResult;
        } catch (Exception e) {
            if (bActiveTxn) {
                abortInCatchBlock(e, e, mdTxnCtx);
            }
            throw new AlgebricksException(e);
        } finally {
            releaseReadLatch();
        }
    }

    private StatementExecutionContext handleCreateNodeGroupStatement(AqlMetadataProvider metadataProvider,
            Statement stmt) throws Exception {
        MetadataTransactionContext mdTxnCtx = metadataProvider.getMetadataTxnContext();
        NodegroupDecl stmtCreateNodegroup = (NodegroupDecl) stmt;
        String ngName = stmtCreateNodegroup.getNodegroupName().getValue();
        NodeGroup ng = MetadataManager.INSTANCE.getNodegroup(mdTxnCtx, ngName);
        if (ng != null) {
            if (!stmtCreateNodegroup.getIfNotExists())
                throw new AlgebricksException("A nodegroup with this name " + ngName + " already exists.");
        } else {
            List<Identifier> ncIdentifiers = stmtCreateNodegroup.getNodeControllerNames();
            List<String> ncNames = new ArrayList<String>(ncIdentifiers.size());
            for (Identifier id : ncIdentifiers) {
                ncNames.add(id.getValue());
            }
            MetadataManager.INSTANCE.addNodegroup(mdTxnCtx, new NodeGroup(ngName, ncNames));
        }
        return null;
    }

    private JobId runJob(IHyracksClientConnection hcc, JobSpecification spec, boolean waitForCompletion)
            throws Exception {
        JobId[] jobIds = executeJobArray(hcc, new Job[] { new Job(spec) }, out, pdf, waitForCompletion);
        return jobIds[0];
    }

    public JobId[] executeJobArray(IHyracksClientConnection hcc, Job[] jobs, PrintWriter out, DisplayFormat pdf,
            boolean waitForCompletion) throws Exception {
        JobId[] startedJobIds = new JobId[jobs.length];
        for (int i = 0; i < jobs.length; i++) {
            JobSpecification spec = jobs[i].getJobSpec();
            spec.setMaxReattempts(0);
            JobId jobId = hcc.startJob(spec);
            startedJobIds[i] = jobId;
            if (waitForCompletion) {
                hcc.waitForCompletion(jobId);
            }
        }
        return startedJobIds;
    }

    private static IDataFormat getDataFormat(MetadataTransactionContext mdTxnCtx, String dataverseName)
            throws AsterixException {
        Dataverse dataverse = MetadataManager.INSTANCE.getDataverse(mdTxnCtx, dataverseName);
        IDataFormat format;
        try {
            format = (IDataFormat) Class.forName(dataverse.getDataFormat()).newInstance();
        } catch (Exception e) {
            throw new AsterixException(e);
        }
        return format;
    }

    private void acquireWriteLatch() {
        MetadataManager.INSTANCE.acquireWriteLatch();
    }

    private void releaseWriteLatch() {
        MetadataManager.INSTANCE.releaseWriteLatch();
    }

    private void acquireReadLatch() {
        MetadataManager.INSTANCE.acquireReadLatch();
    }

    private void acquireLatch(LatchType latchType) {
        switch (latchType) {
            case READ:
                MetadataManager.INSTANCE.acquireReadLatch();
                break;
            case WRITE:
                MetadataManager.INSTANCE.acquireWriteLatch();
                break;
        }
    }

    private void releaseLatch(LatchType latchType) {
        switch (latchType) {
            case READ:
                MetadataManager.INSTANCE.releaseReadLatch();
                break;
            case WRITE:
                MetadataManager.INSTANCE.releaseWriteLatch();
                break;
        }
    }
   
}
