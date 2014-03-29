/**
 * $RCSfile$
 * $Revision: 11824 $
 * $Date: 2010-08-15 08:13:05 -0700 (Sun, 15 Aug 2010) $
 *
 * Copyright 2003-2007 Jive Software.
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

package org.jivesoftware.smack.packet;

/**
 * A generic Packet of unknown type
 */
public abstract class UnknownPacket extends Packet implements PacketExtension {
    public String toXML() {
        StringBuilder buf = new StringBuilder();
        buf.append("<").append(getElementName());
        if (getXmlns() != null) {
            buf.append(" xmlns=\"").append(getNamespace()).append("\"");
        }
        buf.append("/>");
        return buf.toString();
    }
}