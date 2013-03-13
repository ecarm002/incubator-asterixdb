package edu.uci.ics.asterix.hyracks.bootstrap;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.uci.ics.asterix.common.context.AsterixAppRuntimeContext;
import edu.uci.ics.asterix.metadata.MetadataManager;
import edu.uci.ics.asterix.metadata.MetadataNode;
import edu.uci.ics.asterix.metadata.api.IAsterixStateProxy;
import edu.uci.ics.asterix.metadata.api.IMetadataNode;
import edu.uci.ics.asterix.metadata.bootstrap.MetadataBootstrap;
import edu.uci.ics.hyracks.api.application.INCApplicationContext;
import edu.uci.ics.hyracks.api.application.INCApplicationEntryPoint;

public class NCApplicationEntryPoint implements INCApplicationEntryPoint {
    private static final Logger LOGGER = Logger.getLogger(NCApplicationEntryPoint.class.getName());

    private INCApplicationContext ncApplicationContext = null;
    private AsterixAppRuntimeContext runtimeContext;
    private String nodeId;
    private boolean isMetadataNode = false;
    private boolean stopInitiated = false;

    @Override
    public void start(INCApplicationContext ncAppCtx, String[] args) throws Exception {
        ncApplicationContext = ncAppCtx;
        nodeId = ncApplicationContext.getNodeId();
        if (LOGGER.isLoggable(Level.INFO)) {
            LOGGER.info("Starting Asterix node controller: " + nodeId);
        }

        runtimeContext = new AsterixAppRuntimeContext(ncApplicationContext);
        runtimeContext.initialize();
        ncApplicationContext.setApplicationObject(runtimeContext);
        JVMShutdownHook sHook = new JVMShutdownHook(this);
        Runtime.getRuntime().addShutdownHook(sHook);

    }

    @Override
    public void stop() throws Exception {
        if (!stopInitiated) {
            stopInitiated = true;
            if (LOGGER.isLoggable(Level.INFO)) {
                LOGGER.info("Stopping Asterix node controller: " + nodeId);
            }

            if (isMetadataNode) {
                MetadataBootstrap.stopUniverse();
            }
            runtimeContext.deinitialize();
        } else {
            if (LOGGER.isLoggable(Level.INFO)) {
                LOGGER.info("Duplicate attempt to stop ignored: " + nodeId);
            }
        }
    }

    @Override
    public void notifyStartupComplete() throws Exception {
        IAsterixStateProxy proxy = (IAsterixStateProxy) ncApplicationContext.getDistributedState();
        isMetadataNode = nodeId.equals(proxy.getAsterixProperties().getMetadataNodeName());
        if (isMetadataNode) {
            registerRemoteMetadataNode(proxy);

            if (LOGGER.isLoggable(Level.INFO)) {
                LOGGER.info("Bootstrapping metadata");
            }
            MetadataManager.INSTANCE = new MetadataManager(proxy);
            MetadataManager.INSTANCE.init();
            MetadataBootstrap.startUniverse(proxy.getAsterixProperties(), ncApplicationContext);
        }
        ExternalLibraryBootstrap.setUpExternaLibraries(isMetadataNode);
    }

    public void registerRemoteMetadataNode(IAsterixStateProxy proxy) throws RemoteException {
        IMetadataNode stub = null;
        MetadataNode.INSTANCE.initialize(runtimeContext);
        stub = (IMetadataNode) UnicastRemoteObject.exportObject(MetadataNode.INSTANCE, 0);
        proxy.setMetadataNode(stub);

        if (LOGGER.isLoggable(Level.INFO)) {
            LOGGER.info("Metadata node bound");
        }
    }

    /**
     * Shutdown hook that invokes {@link NCApplicationEntryPoint#stop() stop} method.
     */
    private static class JVMShutdownHook extends Thread {

        private final NCApplicationEntryPoint ncAppEntryPoint;

        public JVMShutdownHook(NCApplicationEntryPoint ncAppEntryPoint) {
            this.ncAppEntryPoint = ncAppEntryPoint;
        }

        public void run() {
            if (LOGGER.isLoggable(Level.INFO)) {
                LOGGER.info("Shutdown hook in progress");
            }
            try {
                ncAppEntryPoint.stop();
            } catch (Exception e) {
                if (LOGGER.isLoggable(Level.WARNING)) {
                    LOGGER.warning("Exception in executing shutdown hook" + e);
                }
            }
        }
    }

}