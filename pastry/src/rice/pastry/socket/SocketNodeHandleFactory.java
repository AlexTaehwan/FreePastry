/*******************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate

Copyright 2002-2007, Rice University. Copyright 2006-2007, Max Planck Institute 
for Software Systems.  All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither the name of Rice  University (RICE), Max Planck Institute for Software 
Systems (MPI-SWS) nor the names of its contributors may be used to endorse or 
promote products derived from this software without specific prior written 
permission.

This software is provided by RICE, MPI-SWS and the contributors on an "as is" 
basis, without any representations or warranties of any kind, express or implied 
including, but not limited to, representations or warranties of 
non-infringement, merchantability or fitness for a particular purpose. In no 
event shall RICE, MPI-SWS or contributors be liable for any direct, indirect, 
incidental, special, exemplary, or consequential damages (including, but not 
limited to, procurement of substitute goods or services; loss of use, data, or 
profits; or business interruption) however caused and on any theory of 
liability, whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even if 
advised of the possibility of such damage.

*******************************************************************************/ 
package rice.pastry.socket;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.mpisws.p2p.transport.commonapi.TransportLayerNodeHandle;
import org.mpisws.p2p.transport.multiaddress.MultiInetSocketAddress;

import rice.environment.logging.Logger;
import rice.p2p.commonapi.rawserialization.InputBuffer;
import rice.pastry.Id;
import rice.pastry.NodeHandle;
import rice.pastry.NodeHandleFactory;
import rice.pastry.transport.TLPastryNode;

public class SocketNodeHandleFactory implements NodeHandleFactory {
  TLPastryNode pn;
  Map<MultiInetSocketAddress, SocketNodeHandle> handles;
  Map<SocketNodeHandle, SocketNodeHandle> handleSet;
  
  Logger logger;
  
  public SocketNodeHandleFactory(TLPastryNode pn) {
    this.pn = pn;
    this.logger = pn.getEnvironment().getLogManager().getLogger(SocketNodeHandleFactory.class, null);
    
    handles = new HashMap<MultiInetSocketAddress, SocketNodeHandle>();
    handleSet = new HashMap<SocketNodeHandle, SocketNodeHandle>();
  }
  
  
  /**
   * This is kind of weird, may need to rethink this.
   * 
   * @param i
   * @param id
   * @return
   */
  public SocketNodeHandle getNodeHandle(MultiInetSocketAddress i, long epoch, Id id) {
    if (handles.containsKey(i)) {
      SocketNodeHandle ret = handles.get(i);
      if (ret.getEpoch() == epoch && ret.getId().equals(id)) {
        return ret;        
      } else {
        // this is kind of dangerous because this dictionary is necessay for the identity layer, and could be 
        // poisoned with this method 
        
        if (logger.level <= Logger.WARNING) logger.log("getNodeHandle("+i+","+epoch+","+id+") replacing "+ret);
      }      
    }
    SocketNodeHandle handle = new SocketNodeHandle(i, epoch, id, pn);
    handleSet.put(handle, handle);
    handles.put(i, handle);
    return handle;
  }

  public NodeHandle readNodeHandle(InputBuffer buf) throws IOException {
//    TLNodeHandle handle = TLNodeHandle.build(buf, pn);
//    TLNodeHandle old = handles.get(handle.eaddress);
//    
//    if (handle.equals(old)) {
//      return old; 
//    }
//        
//    if (old != null) {
//      if (logger.level <= Logger.INFO) logger.log("readNodeHandle(): nodeHandle changed old:"+old+" new:"+handle);
//    }
//    handleSet.put(handle, handle);
//    handles.put(handle.eaddress, handle);
//    
//    return handle;
    return coalesce(SocketNodeHandle.build(buf, pn));
  }
  
//  public org.mpisws.p2p.transport.commonapi.NodeHandleFactory<MultiInetSocketAddress> getTLInterface() {
//    return tlInterface;
//  }

  public NodeHandle coalesce(NodeHandle h) {
    SocketNodeHandle handle = (SocketNodeHandle)h;
    if (handleSet.containsKey(handle)) {
      return handleSet.get(handle);
    }
    
    handle.setLocalNode(pn);
    
    handles.put(handle.eaddress, handle);
    handleSet.put(handle, handle);
    return handle;
  }


  public TransportLayerNodeHandle<MultiInetSocketAddress> lookupNodeHandle(MultiInetSocketAddress i) {
    return handles.get(i);
  }

}
