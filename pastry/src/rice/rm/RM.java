/*************************************************************************

"Free Pastry" Peer-to-Peer Application Development Substrate 

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


package rice.rm;

import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.pastry.security.*;

/**
 * @(#) RM.java
 *
 * This interface is exported by Replica Manager for any applications which need to 
 * replicate objects across k closest nodes in the NodeId space.
 *
 * @version $Id$
 * @author Atul Singh
 * @author Animesh Nandi
 */
public interface RM {


     /**
     * Registers the application to the RM.
     * @param appAddress the application's address
     * @param app the application, which is an instance of ReplicaClient
     */
    public boolean register(Address appAddress, RMClient app);


    /**
     * Called by the application when it needs to replicate an object into k nodes
     * closest to the object key.
     *
     * @param appAddress applications address which calls this method
     * @param objectKey  the pastry key for the object
     * @param object the object
     * @param replicaFactor the number of nodes k into which the object is replicated
     * @return true if operation successful else false
     */
    public boolean replicate(Address appAddress, NodeId objectKey, Object object, int replicaFactor);

    /**
     * Called by the application when it needs to refresh an object into k nodes
     * closest to the object key. This mechanism is used to ensure that stsle objects
     * do not persist by any chance and that replicas of an object are maintained
     * under all circumstances.
     *
     * @param appAddress applications address which calls this method
     * @param objectKey  the pastry key for the object
     * @param replicaFactor the number of nodes k into which the object is replicated
     * @return true if operation successful else false
     */
    public boolean heartbeat(Address appAddress, NodeId objectKey, int replicaFactor);



    /**
     * Called by applications when it needs to remove this object from k nodes 
     * closest to the objectKey. 
     *
     * @param appAddress applications address
     * @param objectKey  the pastry key for the object
     * @param replicaFactor the replication factor of the object
     * @return true if operation successful
     */
    public boolean remove(Address appAddress, NodeId objectKey, int replicaFactor);


}
