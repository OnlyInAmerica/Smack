package org.jivesoftware.smackx;

import org.jivesoftware.smack.tcp.LLPresence;
import org.jivesoftware.smack.tcp.LLPresenceListener;

public class MDNSListener implements LLPresenceListener {

    public void presenceNew(LLPresence pr) {
        try {
            System.out.println("New presence: " + pr.getServiceName() + 
                    " (" + pr.getStatus() + "), ver=" + pr.getVer());
        } catch (Exception e) {
            System.err.println(e);
            e.printStackTrace();
        }

    }

    public void presenceRemove(LLPresence pr) {
        System.out.println("Removed presence: " + pr.getServiceName());
    }

}
