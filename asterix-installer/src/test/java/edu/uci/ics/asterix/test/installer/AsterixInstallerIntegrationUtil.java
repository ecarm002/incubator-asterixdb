package edu.uci.ics.asterix.test.installer;

import edu.uci.ics.asterix.hyracks.bootstrap.CCApplicationEntryPoint;
import edu.uci.ics.asterix.hyracks.bootstrap.NCApplicationEntryPoint;
import edu.uci.ics.hyracks.api.client.HyracksConnection;
import edu.uci.ics.hyracks.api.client.IHyracksClientConnection;
import edu.uci.ics.hyracks.control.cc.ClusterControllerService;
import edu.uci.ics.hyracks.control.common.controllers.CCConfig;
import edu.uci.ics.hyracks.control.common.controllers.NCConfig;
import edu.uci.ics.hyracks.control.nc.NodeControllerService;

public class AsterixInstallerIntegrationUtil {
	public static final String ASTERIX_INSTANCE_NAME  = "asterix";
    public static final String NC2_ID = "nc2";
    public static final String[] ASTERIX_DATA_DIRS = new String[] { "nc1data", "nc2data" };

    public static final int DEFAULT_HYRACKS_CC_CLIENT_PORT = 1098;

    public static final int DEFAULT_HYRACKS_CC_CLUSTER_PORT = 1099;

    private static ClusterControllerService cc;
    private static NodeControllerService nc1;
    private static NodeControllerService nc2;
    private static IHyracksClientConnection hcc;

    public static void init() throws Exception {
        CCConfig ccConfig = new CCConfig();
        ccConfig.clusterNetIpAddress = "127.0.0.1";
        ccConfig.clientNetIpAddress = "127.0.0.1";
        ccConfig.clientNetPort = DEFAULT_HYRACKS_CC_CLIENT_PORT;
        ccConfig.clusterNetPort = DEFAULT_HYRACKS_CC_CLUSTER_PORT;
        ccConfig.defaultMaxJobAttempts = 0;
        ccConfig.appCCMainClass = CCApplicationEntryPoint.class.getName();
        // ccConfig.useJOL = true;
        cc = new ClusterControllerService(ccConfig);
        cc.start();

        NCConfig ncConfig1 = new NCConfig();
        ncConfig1.ccHost = "localhost";
        ncConfig1.ccPort = DEFAULT_HYRACKS_CC_CLUSTER_PORT;
        ncConfig1.clusterNetIPAddress = "127.0.0.1";
        ncConfig1.dataIPAddress = "127.0.0.1";
        ncConfig1.datasetIPAddress = "127.0.0.1";
        ncConfig1.nodeId = NC1_ID;
        ncConfig1.appNCMainClass = NCApplicationEntryPoint.class.getName();
        nc1 = new NodeControllerService(ncConfig1);
        nc1.start();

        NCConfig ncConfig2 = new NCConfig();
        ncConfig2.ccHost = "localhost";
        ncConfig2.ccPort = DEFAULT_HYRACKS_CC_CLUSTER_PORT;
        ncConfig2.clusterNetIPAddress = "127.0.0.1";
        ncConfig2.dataIPAddress = "127.0.0.1";
        ncConfig2.datasetIPAddress = "127.0.0.1";
        ncConfig2.nodeId = NC2_ID;
        ncConfig2.appNCMainClass = NCApplicationEntryPoint.class.getName();
        nc2 = new NodeControllerService(ncConfig2);
        nc2.start();

        hcc = new HyracksConnection(cc.getConfig().clientNetIpAddress, cc.getConfig().clientNetPort);
    }
}
