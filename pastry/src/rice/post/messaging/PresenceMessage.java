/*************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate 

Copyright 2002, Rice University. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither  the name  of Rice  University (RICE) nor  the names  of its
contributors may be  used to endorse or promote  products derived from
this software without specific prior written permission.

This software is provided by RICE and the contributors on an "as is"
basis, without any representations or warranties of any kind, express
or implied including, but not limited to, representations or
warranties of non-infringement, merchantability or fitness for a
particular purpose. In no event shall RICE or contributors be liable
for any direct, indirect, incidental, special, exemplary, or
consequential damages (including, but not limited to, procurement of
substitute goods or services; loss of use, data, or profits; or
business interruption) however caused and on any theory of liability,
whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even
if advised of the possibility of such damage.

********************************************************************************/
package rice.post.messaging;

import java.io.*;
import rice.post.*;
import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.*;

/**
 * This is the message broadcast to the Scribe group of
 * the user to inform replica holders that that user is available
 * at the given nodeid.
 */
public class PresenceMessage extends PostMessage {
  public static final short TYPE = 10;

  private Id location;

  static final long serialVersionUID = -2972426454617508369L;

  private NodeHandle handle;
  
  /**
   * Constructs a PresenceMessage
   *
   * @param sender The address of the user asserted to be present.
   * @param location The user's asserted location.
   */
  public PresenceMessage(PostEntityAddress sender, NodeHandle handle) {
    super(sender);
    this.location = handle.getId();
    this.handle = handle;
  }

  /**
   * Gets the location of the user.
   *
   * @return The location in the Pastry ring of the user.
   */
  public Id getLocation() {
    return location;
  }
  
  /**
   * Gets the handle to this user.
   *
   * @return The location in the Pastry ring of the user.
   */
  public NodeHandle getHandle() {
    return handle;
  }
  
  public PresenceMessage(InputBuffer buf, Endpoint endpoint) throws IOException {
    super(buf, endpoint);
    
    location = endpoint.readId(buf, buf.readShort());
    
    handle = endpoint.readNodeHandle(buf);
  }
  
  public void serialize(OutputBuffer buf) throws IOException {
    super.serialize(buf);

    buf.writeShort(location.getType());
    location.serialize(buf);
    
    handle.serialize(buf);
  }
  
  public short getType() {
    return TYPE;
  }
}
