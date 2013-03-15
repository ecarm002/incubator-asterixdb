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
package edu.uci.ics.asterix.installer.events;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import edu.uci.ics.asterix.event.driver.EventDriver;
import edu.uci.ics.asterix.event.schema.cluster.Cluster;
import edu.uci.ics.asterix.event.schema.cluster.Node;
import edu.uci.ics.asterix.event.schema.pattern.Delay;
import edu.uci.ics.asterix.event.schema.pattern.Event;
import edu.uci.ics.asterix.event.schema.pattern.Nodeid;
import edu.uci.ics.asterix.event.schema.pattern.Pattern;
import edu.uci.ics.asterix.event.schema.pattern.Patterns;
import edu.uci.ics.asterix.event.schema.pattern.Value;
import edu.uci.ics.asterix.installer.command.StopCommand;
import edu.uci.ics.asterix.installer.driver.InstallerDriver;
import edu.uci.ics.asterix.installer.error.VerificationUtil;
import edu.uci.ics.asterix.installer.model.AsterixInstance;
import edu.uci.ics.asterix.installer.model.BackupInfo;
import edu.uci.ics.asterix.installer.model.BackupInfo.BackupType;
import edu.uci.ics.asterix.installer.schema.conf.Backup;
import edu.uci.ics.asterix.installer.service.ILookupService;
import edu.uci.ics.asterix.installer.service.ServiceProvider;

public class PatternCreator {

    private ILookupService lookupService = ServiceProvider.INSTANCE.getLookupService();

    private void addInitialDelay(Pattern p, int delay, String unit) {
        Delay d = new Delay(new Value(null, "" + delay), unit);
        p.setDelay(d);
    }

    public Patterns getStartAsterixPattern(String asterixInstanceName, Cluster cluster) throws Exception {
        String ccLocationId = cluster.getMasterNode().getId();
        String ccLocationIp = cluster.getMasterNode().getIp();

        String destDir = cluster.getWorkingDir().getDir() + File.separator + "asterix";
        List<Pattern> ps = new ArrayList<Pattern>();

        Pattern copyHyracks = createCopyHyracksPattern(asterixInstanceName, cluster, ccLocationIp, destDir);
        ps.add(copyHyracks);

        Pattern createCC = createCCStartPattern(ccLocationId);
        addInitialDelay(createCC, 2, "sec");
        ps.add(createCC);

        boolean copyHyracksToNC = !cluster.getWorkingDir().isNFS();
        for (Node node : cluster.getNode()) {
            if (copyHyracksToNC) {
                Pattern copyHyracksForNC = createCopyHyracksPattern(asterixInstanceName, cluster, node.getIp(), destDir);
                ps.add(copyHyracksForNC);
            }
            Pattern createNC = createNCStartPattern(cluster.getMasterNode().getIp(), node.getId(), asterixInstanceName
                    + "_" + node.getId());
            addInitialDelay(createNC, 4, "sec");
            ps.add(createNC);
        }

        Patterns patterns = new Patterns(ps);
        patterns.getPattern().addAll(createHadoopLibraryTransferPattern(cluster).getPattern());
        return patterns;
    }

    public Patterns getClusterInfoPattern(Cluster cluster) throws Exception {
        List<Pattern> ps = new ArrayList<Pattern>();
        String pargs = "";
        List<Node> nodeList = cluster.getNode();
        nodeList.add(new Node(cluster.getMasterNode().getId(), cluster.getMasterNode().getIp(), null, null, null, null,
                null));
        for (Node node : nodeList) {
            Nodeid nodeid = new Nodeid(new Value(null, node.getId()));
            Event event = new Event("node_info", nodeid, pargs);
            ps.add(new Pattern(null, 1, null, event));
        }
        return new Patterns(ps);
    }

    public Patterns getStopCommandPattern(StopCommand stopCommand) throws Exception {
        List<Pattern> ps = new ArrayList<Pattern>();
        AsterixInstance asterixInstance = lookupService.getAsterixInstance(stopCommand.getAsterixInstanceName());
        Cluster cluster = asterixInstance.getCluster();

        String ccLocation = cluster.getMasterNode().getId();
        Pattern createCC = createCCStopPattern(ccLocation);
        addInitialDelay(createCC, 5, "sec");
        ps.add(createCC);

        String asterixInstanceName = stopCommand.getAsterixInstanceName();
        int nodeControllerIndex = 1;
        for (Node node : cluster.getNode()) {
            Pattern createNC = createNCStopPattern(node.getId(), asterixInstanceName + "_" + nodeControllerIndex);
            ps.add(createNC);
            nodeControllerIndex++;
        }

        Patterns patterns = new Patterns(ps);
        return patterns;
    }

    public Patterns getBackUpAsterixPattern(AsterixInstance instance, Backup backupConf) throws Exception {
        BackupType backupType = BackupInfo.getBackupType(backupConf);
        Patterns patterns = null;
        switch (backupType) {
            case HDFS:
                patterns = getHDFSBackUpAsterixPattern(instance, backupConf);
                break;
            case LOCAL:
                patterns = getLocalBackUpAsterixPattern(instance, backupConf);
                break;
        }
        return patterns;
    }

    public Patterns getRestoreAsterixPattern(AsterixInstance instance, BackupInfo backupInfo) throws Exception {
        BackupType backupType = backupInfo.getBackupType();
        Patterns patterns = null;
        switch (backupType) {
            case HDFS:
                patterns = getHDFSRestoreAsterixPattern(instance, backupInfo);
                break;
            case LOCAL:
                patterns = getLocalRestoreAsterixPattern(instance, backupInfo);
                break;
        }
        return patterns;
    }

    private Patterns getHDFSBackUpAsterixPattern(AsterixInstance instance, Backup backupConf) throws Exception {
        Cluster cluster = instance.getCluster();
        String clusterStore = instance.getCluster().getStore();
        String hdfsUrl = backupConf.getHdfs().getUrl();
        String hadoopVersion = backupConf.getHdfs().getVersion();
        String hdfsBackupDir = backupConf.getBackupDir();
        VerificationUtil.verifyBackupRestoreConfiguration(hdfsUrl, hadoopVersion, hdfsBackupDir);
        String workingDir = cluster.getWorkingDir().getDir();
        String backupId = "" + instance.getBackupInfo().size();
        String nodeStore;
        String pargs;
        List<Pattern> patternList = new ArrayList<Pattern>();
        for (Node node : cluster.getNode()) {
            Nodeid nodeid = new Nodeid(new Value(null, node.getId()));
            nodeStore = node.getStore() == null ? clusterStore : node.getStore();
            pargs = workingDir + " " + instance.getName() + " " + nodeStore + " " + backupId + " " + hdfsBackupDir
                    + " " + "hdfs" + " " + node.getId() + " " + hdfsUrl + " " + hadoopVersion;
            Event event = new Event("backup", nodeid, pargs);
            patternList.add(new Pattern(null, 1, null, event));
        }
        return new Patterns(patternList);
    }

    private Patterns getLocalBackUpAsterixPattern(AsterixInstance instance, Backup backupConf) throws Exception {
        Cluster cluster = instance.getCluster();
        String clusterStore = instance.getCluster().getStore();
        String backupDir = backupConf.getBackupDir();
        String workingDir = cluster.getWorkingDir().getDir();
        String backupId = "" + instance.getBackupInfo().size();
        String nodeStore;
        String pargs;
        List<Pattern> patternList = new ArrayList<Pattern>();
        for (Node node : cluster.getNode()) {
            Nodeid nodeid = new Nodeid(new Value(null, node.getId()));
            nodeStore = node.getStore() == null ? clusterStore : node.getStore();
            pargs = workingDir + " " + instance.getName() + " " + nodeStore + " " + backupId + " " + backupDir + " "
                    + "local" + " " + node.getId();
            Event event = new Event("backup", nodeid, pargs);
            patternList.add(new Pattern(null, 1, null, event));
        }
        return new Patterns(patternList);
    }

    public Patterns getHDFSRestoreAsterixPattern(AsterixInstance instance, BackupInfo backupInfo) throws Exception {
        Cluster cluster = instance.getCluster();
        String clusterStore = instance.getCluster().getStore();
        String hdfsUrl = backupInfo.getBackupConf().getHdfs().getUrl();
        String hadoopVersion = backupInfo.getBackupConf().getHdfs().getVersion();
        String hdfsBackupDir = backupInfo.getBackupConf().getBackupDir();
        VerificationUtil.verifyBackupRestoreConfiguration(hdfsUrl, hadoopVersion, hdfsBackupDir);
        String workingDir = cluster.getWorkingDir().getDir();
        int backupId = backupInfo.getId();
        String nodeStore;
        String pargs;
        List<Pattern> patternList = new ArrayList<Pattern>();
        for (Node node : cluster.getNode()) {
            Nodeid nodeid = new Nodeid(new Value(null, node.getId()));
            nodeStore = node.getStore() == null ? clusterStore : node.getStore();
            pargs = workingDir + " " + instance.getName() + " " + nodeStore + " " + backupId + " " + hdfsBackupDir
                    + " " + "hdfs" + " " + node.getId() + " " + hdfsUrl + " " + hadoopVersion;
            Event event = new Event("restore", nodeid, pargs);
            patternList.add(new Pattern(null, 1, null, event));
        }
        return new Patterns(patternList);
    }

    public Patterns getLibraryInstallPattern(AsterixInstance instance, String dataverse, String libraryName,
            String libraryPath) throws Exception {
        List<Pattern> patternList = new ArrayList<Pattern>();
        Cluster cluster = instance.getCluster();
        Nodeid nodeid = new Nodeid(new Value(null, EventDriver.CLIENT_NODE.getId()));
        String username = cluster.getUsername() != null ? cluster.getUsername() : System.getProperty("user.name");
        String workingDir = cluster.getWorkingDir().getDir();
        String destDir = workingDir + File.separator + "library" + File.separator + dataverse + File.separator
                + libraryName;
        String fileToTransfer = new File(libraryPath).getAbsolutePath();

        Iterator<Node> installTargets = cluster.getNode().iterator();
        Node installNode = installTargets.next();
        String destinationIp = installNode.getIp();
        String pargs = username + " " + fileToTransfer + " " + destinationIp + " " + destDir + " " + "unpack";
        Event event = new Event("file_transfer", nodeid, pargs);
        Pattern p = new Pattern(null, 1, null, event);
        patternList.add(p);

        if (!cluster.getWorkingDir().isNFS()) {
            while (installTargets.hasNext()) {
                Node node = installTargets.next();
                pargs = username + " " + fileToTransfer + " " + node.getIp() + " " + destDir + " " + "unpack";
                event = new Event("file_transfer", nodeid, pargs);
                p = new Pattern(null, 1, null, event);
                patternList.add(p);
            }

            pargs = username + " " + fileToTransfer + " " + cluster.getMasterNode().getIp() + " " + destDir + " "
                    + "unpack";
            event = new Event("file_transfer", nodeid, pargs);
            p = new Pattern(null, 1, null, event);
            patternList.add(p);
        }
        return new Patterns(patternList);
    }

    public Patterns getLibraryUninstallPattern(AsterixInstance instance, String dataverse, String libraryName)
            throws Exception {
        List<Pattern> patternList = new ArrayList<Pattern>();
        Cluster cluster = instance.getCluster();
        String workingDir = cluster.getWorkingDir().getDir();
        String destFile = dataverse + "." + libraryName;
        String pargs = workingDir + File.separator + "uninstall" + " " + destFile;

        String metadataNodeId = instance.getMetadataNodeId();
        Nodeid nodeid = new Nodeid(new Value(null, metadataNodeId));
        Event event = new Event("file_create", nodeid, pargs);
        Pattern p = new Pattern(null, 1, null, event);
        patternList.add(p);

        Iterator<Node> uninstallTargets = cluster.getNode().iterator();
        String libDir = workingDir + File.separator + "library" + File.separator + dataverse + File.separator
                + libraryName;
        Node uninstallNode = uninstallTargets.next();
        nodeid = new Nodeid(new Value(null, uninstallNode.getId()));
        event = new Event("file_delete", nodeid, libDir);
        p = new Pattern(null, 1, null, event);
        patternList.add(p);
        pargs = libDir;

        if (!cluster.getWorkingDir().isNFS()) {
            while (uninstallTargets.hasNext()) {
                uninstallNode = uninstallTargets.next();
                nodeid = new Nodeid(new Value(null, uninstallNode.getId()));
                event = new Event("file_delete", nodeid, pargs);
                p = new Pattern(null, 1, null, event);
                patternList.add(p);
            }

            nodeid = new Nodeid(new Value(null, cluster.getMasterNode().getId()));
            event = new Event("file_delete", nodeid, pargs);
            p = new Pattern(null, 1, null, event);
            patternList.add(p);

        }
        return new Patterns(patternList);
    }

    public Patterns getLocalRestoreAsterixPattern(AsterixInstance instance, BackupInfo backupInfo) throws Exception {
        Cluster cluster = instance.getCluster();
        String clusterStore = instance.getCluster().getStore();
        String backupDir = backupInfo.getBackupConf().getBackupDir();
        String workingDir = cluster.getWorkingDir().getDir();
        int backupId = backupInfo.getId();
        String nodeStore;
        String pargs;
        List<Pattern> patternList = new ArrayList<Pattern>();
        for (Node node : cluster.getNode()) {
            Nodeid nodeid = new Nodeid(new Value(null, node.getId()));
            nodeStore = node.getStore() == null ? clusterStore : node.getStore();
            pargs = workingDir + " " + instance.getName() + " " + nodeStore + " " + backupId + " " + backupDir + " "
                    + "local" + " " + node.getId();
            Event event = new Event("restore", nodeid, pargs);
            patternList.add(new Pattern(null, 1, null, event));
        }
        return new Patterns(patternList);
    }

    public Patterns createHadoopLibraryTransferPattern(Cluster cluster) throws Exception {
        List<Pattern> patternList = new ArrayList<Pattern>();
        String workingDir = cluster.getWorkingDir().getDir();
        String hadoopVersion = InstallerDriver.getConfiguration().getBackup().getHdfs().getVersion();
        File hadoopDir = new File(InstallerDriver.getManagixHome() + File.separator
                + InstallerDriver.MANAGIX_INTERNAL_DIR + File.separator + "hadoop-" + hadoopVersion);
        if (!hadoopDir.exists()) {
            throw new IllegalStateException("Hadoop version :" + hadoopVersion + " not supported");
        }

        Nodeid nodeid = new Nodeid(new Value(null, EventDriver.CLIENT_NODE.getId()));
        String username = cluster.getUsername() != null ? cluster.getUsername() : System.getProperty("user.name");
        String pargs = username + " " + hadoopDir.getAbsolutePath() + " " + cluster.getMasterNode().getIp() + " "
                + workingDir;
        Event event = new Event("directory_transfer", nodeid, pargs);
        Pattern p = new Pattern(null, 1, null, event);
        addInitialDelay(p, 2, "sec");
        patternList.add(p);

        boolean copyToNC = !cluster.getWorkingDir().isNFS();
        if (copyToNC) {
            for (Node node : cluster.getNode()) {
                nodeid = new Nodeid(new Value(null, node.getId()));
                pargs = cluster.getUsername() + " " + hadoopDir.getAbsolutePath() + " " + node.getIp() + " "
                        + workingDir;
                event = new Event("directory_transfer", nodeid, pargs);
                p = new Pattern(null, 1, null, event);
                addInitialDelay(p, 2, "sec");
                patternList.add(p);
            }
        }
        Patterns patterns = new Patterns(patternList);
        return patterns;
    }

    public Patterns createDeleteInstancePattern(AsterixInstance instance) throws Exception {
        List<Pattern> patternList = new ArrayList<Pattern>();
        patternList.addAll(createRemoveAsterixStoragePattern(instance).getPattern());
        if (instance.getBackupInfo() != null && instance.getBackupInfo().size() > 0) {
            patternList.addAll(createRemoveHDFSBackupPattern(instance).getPattern());
        }
        patternList.addAll(createRemoveAsterixWorkingDirPattern(instance).getPattern());
        patternList.addAll(createRemoveAsterixLogDirPattern(instance).getPattern());
        Patterns patterns = new Patterns(patternList);
        return patterns;
    }

    private Patterns createRemoveHDFSBackupPattern(AsterixInstance instance) throws Exception {
        List<Pattern> patternList = new ArrayList<Pattern>();
        Cluster cluster = instance.getCluster();
        String hdfsUrl = InstallerDriver.getConfiguration().getBackup().getHdfs().getUrl();
        String hadoopVersion = InstallerDriver.getConfiguration().getBackup().getHdfs().getVersion();
        String hdfsBackupDir = InstallerDriver.getConfiguration().getBackup().getBackupDir();
        String workingDir = cluster.getWorkingDir().getDir();
        Node launchingNode = cluster.getNode().get(0);
        Nodeid nodeid = new Nodeid(new Value(null, launchingNode.getId()));
        String pathToDelete = hdfsBackupDir + File.separator + instance.getName();
        String pargs = workingDir + " " + hadoopVersion + " " + hdfsUrl + " " + pathToDelete;
        Event event = new Event("hdfs_delete", nodeid, pargs);
        patternList.add(new Pattern(null, 1, null, event));
        Patterns patterns = new Patterns(patternList);
        return patterns;
    }

    private Patterns createRemoveAsterixWorkingDirPattern(AsterixInstance instance) throws Exception {
        List<Pattern> patternList = new ArrayList<Pattern>();
        Cluster cluster = instance.getCluster();
        String workingDir = cluster.getWorkingDir().getDir();
        String pargs = workingDir;
        Nodeid nodeid = new Nodeid(new Value(null, cluster.getMasterNode().getId()));
        Event event = new Event("file_delete", nodeid, pargs);
        patternList.add(new Pattern(null, 1, null, event));

        if (!cluster.getWorkingDir().isNFS()) {
            for (Node node : cluster.getNode()) {
                nodeid = new Nodeid(new Value(null, node.getId()));
                event = new Event("file_delete", nodeid, pargs);
                patternList.add(new Pattern(null, 1, null, event));
            }
        }
        Patterns patterns = new Patterns(patternList);
        return patterns;
    }

    private Patterns createRemoveAsterixLogDirPattern(AsterixInstance instance) throws Exception {
        List<Pattern> patternList = new ArrayList<Pattern>();
        Cluster cluster = instance.getCluster();
        String pargs = instance.getCluster().getLogdir();
        Nodeid nodeid = new Nodeid(new Value(null, cluster.getMasterNode().getId()));
        Event event = new Event("file_delete", nodeid, pargs);
        patternList.add(new Pattern(null, 1, null, event));

        if (!cluster.getWorkingDir().isNFS()) {
            for (Node node : cluster.getNode()) {
                nodeid = new Nodeid(new Value(null, node.getId()));
                event = new Event("file_delete", nodeid, pargs);
                patternList.add(new Pattern(null, 1, null, event));
            }
        }
        Patterns patterns = new Patterns(patternList);
        return patterns;
    }

    private Patterns createRemoveAsterixStoragePattern(AsterixInstance instance) throws Exception {
        List<Pattern> patternList = new ArrayList<Pattern>();
        Cluster cluster = instance.getCluster();
        String pargs = null;

        for (Node node : cluster.getNode()) {
            Nodeid nodeid = new Nodeid(new Value(null, node.getId()));
            String[] nodeStores;
            if (node.getStore() != null) {
                nodeStores = node.getStore().trim().split(",");
                for (String ns : nodeStores) {
                    pargs = ns + File.separator + instance.getName();
                }
            } else {
                nodeStores = cluster.getStore().trim().split(",");
                for (String ns : nodeStores) {
                    pargs = ns + File.separator + node.getId() + File.separator + instance.getName();
                }
            }
            Event event = new Event("file_delete", nodeid, pargs);
            patternList.add(new Pattern(null, 1, null, event));
        }
        Patterns patterns = new Patterns(patternList);
        return patterns;
    }

    private Pattern createCopyHyracksPattern(String instanceName, Cluster cluster, String destinationIp, String destDir) {
        Nodeid nodeid = new Nodeid(new Value(null, EventDriver.CLIENT_NODE.getId()));
        String username = cluster.getUsername() != null ? cluster.getUsername() : System.getProperty("user.name");
        String asterixZipName = InstallerDriver.getAsterixZip().substring(
                InstallerDriver.getAsterixZip().lastIndexOf(File.separator) + 1);
        String fileToTransfer = new File(InstallerDriver.getAsterixDir() + File.separator + instanceName
                + File.separator + asterixZipName).getAbsolutePath();
        String pargs = username + " " + fileToTransfer + " " + destinationIp + " " + destDir + " " + "unpack";
        Event event = new Event("file_transfer", nodeid, pargs);
        return new Pattern(null, 1, null, event);
    }

    private Pattern createCCStartPattern(String hostId) {
        Nodeid nodeid = new Nodeid(new Value(null, hostId));
        Event event = new Event("cc_start", nodeid, "");
        return new Pattern(null, 1, null, event);
    }

    public Pattern createCCStopPattern(String hostId) {
        Nodeid nodeid = new Nodeid(new Value(null, hostId));
        Event event = new Event("cc_failure", nodeid, null);
        return new Pattern(null, 1, null, event);
    }

    public Pattern createNCStartPattern(String ccHost, String hostId, String nodeControllerId) {
        Nodeid nodeid = new Nodeid(new Value(null, hostId));
        Event event = new Event("node_join", nodeid, ccHost + " " + nodeControllerId);
        return new Pattern(null, 1, null, event);
    }

    public Pattern createNCStopPattern(String hostId, String nodeControllerId) {
        Nodeid nodeid = new Nodeid(new Value(null, hostId));
        Event event = new Event("node_failure", nodeid, nodeControllerId);
        return new Pattern(null, 1, null, event);
    }

}
