/**
 * Copyright 2012-2013 Florian Schmaus
 *
 * All rights reserved. Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jivesoftware.smack.ping;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.jivesoftware.smack.Connection;
import org.jivesoftware.smack.ConnectionCreationListener;
import org.jivesoftware.smack.ConnectionListener;
import org.jivesoftware.smack.PacketCollector;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.SmackConfiguration;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.filter.AndFilter;
import org.jivesoftware.smack.filter.IQTypeFilter;
import org.jivesoftware.smack.filter.PacketFilter;
import org.jivesoftware.smack.filter.PacketIDFilter;
import org.jivesoftware.smack.filter.PacketTypeFilter;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.IQ.Type;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.ping.packet.Ping;
import org.jivesoftware.smackx.ServiceDiscoveryManager;

/**
 * Using an implementation of <a href="http://www.xmpp.org/extensions/xep-0199.html">XMPP Ping (XEP-0199)</a>. This
 * class provides keepalive functionality with the server that will periodically "ping" the server to maintain and/or
 * verify that the connection still exists.
 * <p>
 * The ping is done at the application level and is therefore protocol agnostic. It will thus work for both standard TCP
 * connections as well as BOSH or any other transport protocol. It will also work regardless of whether the server
 * supports the Ping extension, since an error response to the ping serves the same purpose as a pong.
 * 
 * @author Florian Schmaus
 */
public class ServerPingManager {
    private static Map<Connection, ServerPingManager> instances = Collections
            .synchronizedMap(new WeakHashMap<Connection, ServerPingManager>());
    private static long defaultPingInterval = SmackConfiguration.getKeepAliveInterval(); 
    
    private static ScheduledExecutorService periodicPingExecutorService = new ScheduledThreadPoolExecutor(1, new ThreadFactory() {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread pingThread = new Thread(runnable, "Smack Server Ping");
            pingThread.setDaemon(true);
            return pingThread;
        }
    });

    static {
        if (defaultPingInterval > 0) {
            Connection.addConnectionCreationListener(new ConnectionCreationListener() {
                public void connectionCreated(Connection connection) {
                    new ServerPingManager(connection);
                }
            });
        }
    }

    private Connection connection;
    private long pingInterval = SmackConfiguration.getKeepAliveInterval();
    private Set<PingFailedListener> pingFailedListeners = Collections.synchronizedSet(new HashSet<PingFailedListener>());
    private volatile ScheduledFuture<?> periodicPingTask;
    private volatile long lastSuccessfulContact = -1;

    /**
     * Retrieves a {@link ServerPingManager} for the specified {@link Connection}, creating one if it doesn't already
     * exist.
     * 
     * @param connection
     * The connection the manager is attached to.
     * @return The new or existing manager.
     */
    public synchronized static ServerPingManager getInstanceFor(Connection connection) {
        ServerPingManager pingManager = instances.get(connection);

        if (pingManager == null) {
            pingManager = new ServerPingManager(connection);
        }
        return pingManager;
    }

    private ServerPingManager(Connection connection) {
        ServiceDiscoveryManager sdm = ServiceDiscoveryManager.getInstanceFor(connection);
        sdm.addFeature(Ping.NAMESPACE);
        this.connection = connection;
        init();
    }

    private void init() {
        PacketFilter pingPacketFilter = new AndFilter(new PacketTypeFilter(Ping.class), new IQTypeFilter(Type.GET));
        
        connection.addPacketListener(new PacketListener() {
            /**
             * Sends a Pong for every Ping
             */
            public void processPacket(Packet packet) {
                IQ pong = IQ.createResultIQ((Ping) packet);
                connection.sendPacket(pong);
            }
        }, pingPacketFilter);

        connection.addConnectionListener(new ConnectionListener() {

            @Override
            public void connectionClosed() {
                stopPingServerTask();
            }

            @Override
            public void connectionClosedOnError(Exception arg0) {
                stopPingServerTask();
            }

            @Override
            public void reconnectionSuccessful() {
                schedulePingServerTask();
            }

            @Override
            public void reconnectingIn(int seconds) {
            }

            @Override
            public void reconnectionFailed(Exception e) {
            }
        });

        // Listen for all incoming packets and reset the scheduled ping whenever
        // one arrives.
        connection.addPacketListener(new PacketListener() {

            @Override
            public void processPacket(Packet packet) {
                // reschedule the ping based on this last server contact
                lastSuccessfulContact = System.currentTimeMillis();
                schedulePingServerTask();
            }
        }, null);
        instances.put(connection, this);
        schedulePingServerTask();
    }

    /**
     * Sets the ping interval.
     * 
     * @param pingInterval
     * The new ping time interval in milliseconds.
     */
    public void setPingInterval(long newPingInterval) {
        if (pingInterval != newPingInterval) {
            pingInterval = newPingInterval;
            
            if (pingInterval < 0) {
                stopPinging();
            }
            else {
                schedulePingServerTask();
            }
        }
    }

    /**
     * Stops pinging the server.  This cannot stop a ping that has already started, but will prevent another from being triggered.
     * <p>
     * To restart, call {@link #setPingInterval(long)}.
     */
    public void stopPinging() {
        pingInterval = -1;
        stopPingServerTask();
    }
    
    /**
     * Gets the ping interval.
     * 
     * @return The ping interval in milliseconds.
     */
    public long getPingInterval() {
        return pingInterval;
    }

    /**
     * Add listener for notification when a server ping fails.
     * 
     * <p>
     * Please note that this doesn't necessarily mean that the connection is lost, a slow to respond server could also
     * cause a failure due to taking too long to respond and thus causing a reply timeout.
     * 
     * @param listener
     * The listener to be called
     */
    public void addPingFailedListener(PingFailedListener listener) {
        pingFailedListeners.add(listener);
    }

    /**
     * Remove the listener.
     * 
     * @param listener
     * The listener to be removed.
     */
    public void removePingFailedListener(PingFailedListener listener) {
        pingFailedListeners.remove(listener);
    }

    /**
     * Returns the time of the last successful contact with the server. (i.e. the last time any message was received).
     * 
     * @return Time of last message or -1 if none has been received since manager was created.
     */
    public long getLastSuccessfulContact() {
        return lastSuccessfulContact;
    }

    /**
     * Cancels any existing periodic ping task if there is one and schedules a new ping task if pingInterval is greater
     * then zero.
     * 
     * This is designed so only one executor is used for scheduling all pings on all connections.  This results in only 1 thread used for pinging.
     */
    private synchronized void schedulePingServerTask() {
        stopPingServerTask();
        
        if (pingInterval > 0) {
            periodicPingTask = periodicPingExecutorService.schedule(new Runnable() {
                @Override
                public void run() {
                    Ping ping = new Ping();
                    PacketFilter responseFilter = new PacketIDFilter(ping.getPacketID());
                    final PacketCollector response = connection.createPacketCollector(responseFilter);
                    connection.sendPacket(ping);
        
                    if (!pingFailedListeners.isEmpty()) {
                        // Schedule a collector for the ping reply, notify listeners if none is received.
                        periodicPingExecutorService.schedule(new Runnable() {
                            @Override
                            public void run() {
                                Packet result = response.nextResult(1);
                
                                // Stop queuing results
                                response.cancel();
                
                                // The actual result of the reply can be ignored since we only care if we actually got one.
                                if (result == null) {
                                    for (PingFailedListener listener : pingFailedListeners) {
                                        listener.pingFailed();
                                    }
                                }
                            }
                        }, SmackConfiguration.getPacketReplyTimeout(), TimeUnit.MILLISECONDS);
                    }
                }
            }, getPingInterval(), TimeUnit.MILLISECONDS);
        }
    }

    private void stopPingServerTask() {
        if (periodicPingTask != null) {
            periodicPingTask.cancel(true);
            periodicPingTask = null;
        }
    }
}