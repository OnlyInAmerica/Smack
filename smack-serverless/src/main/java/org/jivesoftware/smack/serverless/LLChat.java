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

package org.jivesoftware.smack.serverless;


import org.jivesoftware.smack.Chat;
import org.jivesoftware.smack.ChatManager;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPException;

import java.io.IOException;
import java.util.UUID;

/**
 * Keeps track of a chat session between two link-local clients.
 */
public class LLChat extends Chat {

    LLChat(LLService service, LLPresence presence) throws XMPPException.XMPPErrorException, IOException, SmackException {
        // In local link we have one chat per connection, threadIDs aren't meaningful
        super(ChatManager.getInstanceFor(service.getConnection(presence.getServiceName())),
                presence.getServiceName(),
                UUID.randomUUID().toString());
    }

    /**
     * Get the service name of the remote client of this chat session.
     * 
     * @return the service name of the remote client of this chat session
     */
    public String getServiceName() {
        return participant;
    }

}
