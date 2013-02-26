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
package edu.uci.ics.asterix.installer.driver;

import java.io.File;
import java.io.FileFilter;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;

import edu.uci.ics.asterix.event.schema.event.Events;
import edu.uci.ics.asterix.installer.command.CommandHandler;
import edu.uci.ics.asterix.installer.schema.conf.Configuration;
import edu.uci.ics.asterix.installer.service.ILookupService;
import edu.uci.ics.asterix.installer.service.ServiceProvider;

@SuppressWarnings("restriction")
public class ManagixDriver {

    public static final String MANAGIX_INTERNAL_DIR = ".managix";
    public static final String MANAGIX_EVENT_DIR = MANAGIX_INTERNAL_DIR + File.separator + "eventrix";
    public static final String MANAGIX_EVENT_SCRIPTS_DIR = MANAGIX_INTERNAL_DIR + File.separator + "eventrix"
            + File.separator + "scripts";

    public static final String ASTERIX_DIR = "asterix";
    public static final String EVENTS_DIR = "events";

    private static final Logger LOGGER = Logger.getLogger(ManagixDriver.class.getName());
    private static final String ENV_MANAGIX_HOME = "MANAGIX_HOME";
    private static final String MANAGIX_CONF_XML = "conf" + File.separator + "managix-conf.xml";

    private static Configuration conf;
    private static String managixHome;
    private static String hyracksServerZip;
    private static String hyracksClientZip;
    private static String asterixZip;
    private static Events events;

    public static String getHyrackServerZip() {
        return hyracksServerZip;
    }

    public static String getHyracksClientZip() {
        return hyracksClientZip;
    }

    public static String getAsterixZip() {
        return asterixZip;
    }

    public static String getHyracksClientHome() {
        return ASTERIX_DIR + File.separator + "hyracks-cli";
    }

    public static Configuration getConfiguration() {
        return conf;
    }

    private static void initConfig() throws Exception {
        managixHome = System.getenv(ENV_MANAGIX_HOME);
        File configFile = new File(managixHome + File.separator + MANAGIX_CONF_XML);
        JAXBContext configCtx = JAXBContext.newInstance(Configuration.class);
        Unmarshaller unmarshaller = configCtx.createUnmarshaller();
        conf = (Configuration) unmarshaller.unmarshal(configFile);

        hyracksServerZip = initBinary("hyracks-server");
        hyracksClientZip = initBinary("hyracks-cli");
        ManagixUtil.unzip(hyracksClientZip, getHyracksClientHome());
        asterixZip = initBinary("asterix-app");

        ILookupService lookupService = ServiceProvider.INSTANCE.getLookupService();
        if (!lookupService.isRunning(conf)) {
            lookupService.startService(conf);
        }
    }

    private static String initBinary(final String fileNamePattern) {
        String asterixDir = ManagixDriver.getAsterixDir();
        File file = new File(asterixDir);
        File[] zipFiles = file.listFiles(new FileFilter() {
            public boolean accept(File arg0) {
                return arg0.getAbsolutePath().contains(fileNamePattern) && arg0.isFile();
            }
        });
        if (zipFiles.length == 0) {
            String msg = " Binary not found at " + asterixDir;
            LOGGER.log(Level.SEVERE, msg);
            throw new IllegalStateException(msg);
        }
        if (zipFiles.length > 1) {
            String msg = " Multiple binaries found at " + asterixDir;
            LOGGER.log(Level.SEVERE, msg);
            throw new IllegalStateException(msg);
        }

        return zipFiles[0].getAbsolutePath();
    }

    public static String getManagixHome() {
        return managixHome;
    }

    public static String getAsterixDir() {
        return managixHome + File.separator + ASTERIX_DIR;
    }

    public static Events getEvents() {
        return events;
    }

    public static void main(String args[]) {
        try {
            if (args.length != 0) {
                initConfig();
                CommandHandler cmdHandler = new CommandHandler();
                cmdHandler.processCommand(args);
            } else {
                printUsage();
            }
        } catch (IllegalArgumentException iae) {
            LOGGER.log(Level.SEVERE, "Unknown command");
            printUsage();
        } catch (Exception e) {
            e.printStackTrace();
            LOGGER.log(Level.SEVERE, e.getMessage());
        }
    }

    private static void printUsage() {
        StringBuffer buffer = new StringBuffer("managix <command> <args>" + "\n");
        buffer.append("Commands" + "\n");
        buffer.append("create   " + ":" + " Creates a new asterix instance" + "\n");
        buffer.append("delete   " + ":" + " Deletes an asterix instance" + "\n");
        buffer.append("start    " + ":" + " Starts an  asterix instance" + "\n");
        buffer.append("stop     " + ":" + " Stops an asterix instance that is in ACTIVE state" + "\n");
        buffer.append("backup   " + ":" + " Creates a back up for an existing asterix instance" + "\n");
        buffer.append("restore  " + ":" + " Restores an asterix instance" + "\n");
        buffer.append("alter    " + ":" + " Alters the configuration for an existing asterix instance" + "\n");
        buffer.append("describe " + ":" + " Describes an existing asterix instance" + "\n");
        buffer.append("install " + ":" + " Installs a library to existing asterix instance" + "\n");
        LOGGER.log(Level.INFO, buffer.toString());
    }
}
