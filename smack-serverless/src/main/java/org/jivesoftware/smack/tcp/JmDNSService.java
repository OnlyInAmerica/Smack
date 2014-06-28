/**
 *
 * Copyright 2009 Jonas Ådahl.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package org.jivesoftware.smack.tcp;


import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.XMPPError;
import org.jivesoftware.smack.util.Tuple;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;
import javax.jmdns.impl.JmDNSImpl;
import javax.jmdns.impl.DNSCache;

import java.util.Iterator;
import java.net.InetAddress;
import java.io.IOException;
import java.util.Hashtable;

/**
 * Implements a LLService using JmDNS.
 *
 * @author Jonas Ådahl
 */
public class JmDNSService extends LLService implements ServiceListener {
    static JmDNS jmdns = null;
    private ServiceInfo serviceInfo;
    static final String SERVICE_TYPE = "_presence._tcp.local.";

    private JmDNSService(LLPresence presence, LLPresenceDiscoverer presenceDiscoverer) {
        super(presence, presenceDiscoverer);
    }

    /**
     * Instantiate a new JmDNSService and start to listen for connections.
     *
     * @param presence the mDNS presence information that should be used.
     */
    public static LLService create(LLPresence presence) throws XMPPException {
        return create(presence, null);
    }

    /**
     * Instantiate a new JmDNSService and start to listen for connections.
     *
     * @param presence the mDNS presence information that should be used.
     * @param addr the INET Address to use.
     */
    public static LLService create(LLPresence presence, InetAddress addr) throws XMPPException {
        // Start the JmDNS daemon.
        initJmDNS(addr);

        // Start the presence discoverer
        JmDNSPresenceDiscoverer presenceDiscoverer = new JmDNSPresenceDiscoverer();

        // Start the presence service
        JmDNSService service = new JmDNSService(presence, presenceDiscoverer);

        return service;
    }

    @Override
    public void close() throws IOException {
        super.close();
        jmdns.close();
    }

    /**
     * Start the JmDNS daemon.
     */
    private static void initJmDNS(InetAddress addr) throws XMPPException {
        try {
            if (jmdns == null) {
                if (addr == null) {
                    jmdns = JmDNS.create();
                }
                else {
                    jmdns = JmDNS.create(addr);
                }
            }
        }
        catch (IOException ioe) {
            throw new XMPPException.XMPPErrorException("Failed to create a JmDNS instance", new XMPPError(XMPPError.Condition.undefined_condition), ioe);
        }
    }

    protected void updateText() {
        Hashtable<String,String> ht = new Hashtable<String,String>();
        
        for (Tuple<String,String> t : presence.toList()) {
            if (t.a != null && t.b != null) {
                ht.put(t.a, t.b);
            }
        }

        serviceInfo.setText(ht);
    }

    /**
     * Register the DNS-SD service with the daemon.
     */
    protected void registerService() throws XMPPException {
        Hashtable<String,String> ht = new Hashtable<String,String>();
        
        for (Tuple<String,String> t : presence.toList()) {
            if (t.a != null && t.b != null)
                ht.put(t.a, t.b);
        }
        serviceInfo = ServiceInfo.create(SERVICE_TYPE,
                presence.getServiceName(), presence.getPort(), 0, 0, ht);
//        serviceInfo.addServiceNameListener(this);
        jmdns.addServiceListener(SERVICE_TYPE, this);
        try {
            String originalName = serviceInfo.getQualifiedName();
            jmdns.registerService(serviceInfo);
            presence.setServiceName(serviceInfo.getName());

            if (!originalName.equals(serviceInfo.getQualifiedName())) {
                // Update presence service name
                // Name collision occured, lets remove confusing elements
                // from cache in case something goes wrong
                JmDNSImpl jmdnsimpl = (JmDNSImpl) jmdns;
//                DNSCache.CacheNode n = jmdnsimpl.getCache().find(originalName);
//                LinkedList<DNSEntry> toRemove = new LinkedList<DNSEntry>();
//                while (n != null) {
//                    DNSEntry e = n.getValue();
//                    if (e != null)
//                        toRemove.add(e);
//
//                    n = n.next();
//                }
//
//                // Remove the DNSEntry's one by one
//                for (DNSEntry e : toRemove) {
//                    jmdnsimpl.getCache().remove(e);
//                }

                DNSCache cache = jmdnsimpl.getCache();
                Iterator i = cache.getDNSEntryList(originalName).iterator();
                while (i.hasNext()) {
                    i.next();
                    i.remove();
                }

            }
        }
        catch (IOException ioe) {
            throw new XMPPException.XMPPErrorException("Failed to register DNS-SD Service", new XMPPError(XMPPError.Condition.undefined_condition), ioe);
        }
    }

    /**
     * Reregister the DNS-SD service with the daemon.
     */
    protected void reannounceService() throws XMPPException {
        try {
            jmdns.registerService(serviceInfo);
            // TODO: Ensure registerService is an acceptable replacement
            // for original statement below:
            //jmdns.reannounceService(serviceInfo);
        }
        catch (IOException ioe) {
            throw new XMPPException.XMPPErrorException("Exception occured when reannouncing mDNS presence.", new XMPPError(XMPPError.Condition.undefined_condition), ioe);
        }
    }

//    public void serviceNameChanged(String newName, String oldName) {
//        try {
//            super.serviceNameChanged(newName, oldName);
//        }
//        catch (Throwable t) {
//            // ignore
//        }
//    }

    /**
     * Unregister the DNS-SD service, making the client unavailable.
     */
    public void makeUnavailable() {
        jmdns.unregisterService(serviceInfo);
        serviceInfo = null;
    }


    @Override
    public void spam() {
        super.spam();
        System.out.println("Service name: " + serviceInfo.getName());
    }

    /** vv {@link javax.jmdns.ServiceListener} vv **/

    @Override
    public void serviceAdded(ServiceEvent event) {
        // Calling super.serviceNameChanged changes
        // the current local presence to that of the
        // newly added Service.
        // How can we assume that a new Service added
        // corresponds to the local service name changing?
        // This logic is currently executed when a new client joins
        // changing the local presence and confusing our chat logic
        // We could perhaps consider services added at the same host
        // address to be name changes... But that also opens the
        // door to undesired behavior when DHCP leases expire
        // and local addresses are recycled

        // What's wrong with treating new services as new services?
        // From my reading of XEP-0174, I don't see any reason why
        // a client should change their service name.

//        System.out.println("Service added " + event.getName());
//        if (!presence.getServiceName().equals(event.getName())) {
//            super.serviceNameChanged(event.getName(), presence.getServiceName());
//        }
    }

    @Override
    public void serviceRemoved(ServiceEvent event) {

    }

    @Override
    public void serviceResolved(ServiceEvent event) {

    }

    /** ^^ {@link javax.jmdns.ServiceListener} ^^ **/
}
