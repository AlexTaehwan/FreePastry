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

package rice.pastry;

import rice.pastry.messaging.*;
import rice.pastry.security.*;
import rice.pastry.client.*;
import rice.pastry.leafset.*;
import rice.pastry.routing.*;

import java.util.*;

/**
 * A Pastry node is single entity in the pastry network.
 *
 * @version $Id$
 *
 * @author Andrew Ladd
 */

public class PastryNode implements NodeHandle 
{
    private NodeId myNodeId;
    private PastrySecurityManager mySecurityManager;
    private MessageDispatch myMessageDispatch;
    private LeafSet leafSet;
    private RoutingTable routeSet;
    private NodeHandle localhandle;
    public boolean syncinit;
  
    /**
     * Constructor.  Creates a new Pastry network.
     *
     * @param pFactory a Pastry node factory.
     */

    public PastryNode(PastryNodeFactory pFactory) 
    {
	pFactory.constructNode();
	
	myNodeId = pFactory.getNodeId();
	mySecurityManager = pFactory.getSecurityManager();
	myMessageDispatch = pFactory.getMessageDispatch();
	
	leafSet = pFactory.getLeafSet();
	routeSet = pFactory.getRouteSet();
	syncinit = false;

	pFactory.doneWithNode(this);
    }

    public PastryNode(PastryNodeFactory pFactory, boolean si) 
    {
	pFactory.constructNode();
	
	myNodeId = pFactory.getNodeId();
	mySecurityManager = pFactory.getSecurityManager();
	myMessageDispatch = pFactory.getMessageDispatch();
	
	leafSet = pFactory.getLeafSet();
	routeSet = pFactory.getRouteSet();
	syncinit = si;

	pFactory.doneWithNode(this);
    }

    public NodeHandle getLocalHandle() { return localhandle; }
    public void setLocalHandle(NodeHandle nh) { localhandle = nh; }

    // node handle interface

    public final NodeId getNodeId() { return myNodeId; }
    
    public final boolean isAlive() { return true; }

    public final boolean ping() { return true; }

    public final int proximity() { return 0; }

    public final LeafSet getLeafSet() { return leafSet; }

    public final RoutingTable getRoutingTable() { return routeSet; }

    /**
     * Add a leaf set observer to the Pastry node.
     *
     * @param o the observer.
     */
    
    public final void addLeafSetObserver(Observer o) { leafSet.addObserver(o); }
    
    /**
     * Add a route set observer to the Pastry node.
     *     
     * @param o the observer.
     */
    
    public final void addRouteSetObserver(Observer o) { routeSet.addObserver(o); }

    // message receiver interface

    public final synchronized void receiveMessage(Message msg) 
    {
	if (mySecurityManager.verifyMessage(msg) == true)
	    myMessageDispatch.dispatchMessage(msg); 
    }

    /** 
     * Registers a message receiver with this Pastry node.
     *
     * @param cred the credentials.
     * @param address the address that the receiver will be at.
     * @param receiver the message receiver.
     */

    public final void registerReceiver(Credentials cred, Address address, MessageReceiver receiver) 
    {
	if (mySecurityManager.verifyAddressBinding(cred, address) == true)
	    myMessageDispatch.registerReceiver(address, receiver);	
	else throw new Error("security failure");
    }    

    /**
     * Registers a client with the Pastry network.
     *
     * @param client the client to register.
     */

    public final void registerClient(PastryClient client) {
	if (client.getAddress() == null) throw new NullPointerException();
	
	registerReceiver(client.getCredentials(), client.getAddress(), client);
    }

    public String toString() {
	return "Pastry node " + myNodeId.toString();
    }
}
