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

package rice.pastry.routing;

import rice.pastry.*;
import rice.pastry.NodeSet;

import java.util.*;
import java.io.*;

/**
 * A set of nodes typically stored in the routing table.  The
 * set contains a bounded number of the closest
 * node handles. Since proximity value can change unpredictably, we don't
 * keep the set in sorted order.
 *
 * @version $Id$
 *
 * @author Andrew Ladd
 * @author Peter Druschel
 */

public class RouteSet extends Observable implements NodeSet, Serializable
{
    private NodeHandle[] nodes;
    private int theSize;
    
    /**
     * Constructor.
     *
     * @param maxSize the maximum number of nodes that fit in this set.
     */

    public RouteSet(int maxSize) {
	nodes = new NodeHandle[maxSize];
	theSize = 0;	
    }

    /**
     * Puts a node into the set. The insertion succeeds either if the set is
     * below is maximal size or if the handle is closer than the most distant member in the set.
     *
     * @param handle the handle to put.
     *
     * @return true if the put succeeded, false otherwise.
     */
    
    public boolean put(NodeHandle handle) {
	int worstIndex = -1;
	int worstProximity = Integer.MIN_VALUE;

	// scan entries
	for (int i=0; i<theSize; i++) {

	    // if handle is already in the set, abort
	    if (nodes[i].getNodeId().equals(handle.getNodeId())) return false;

	    // find entry with worst proximity
	    int p = nodes[i].proximity();
	    if (p >= worstProximity) {
		worstProximity = p;
		worstIndex = i;
	    }
	}
	
	if (theSize < nodes.length) {
	    nodes[theSize++] = handle;

	    setChanged();
	    notifyObservers(new NodeSetUpdate(handle, true));
	    
	    return true; 
	}
	else {
	    if (handle.proximity() < worstProximity) {
		// remove handle with worst proximity
		setChanged();
		notifyObservers(new NodeSetUpdate(nodes[worstIndex], false));
				
		// insert new handle
		nodes[worstIndex] = handle; 

		setChanged();
		notifyObservers(new NodeSetUpdate(handle, true));

		return true;
	    }
	    else return false;	    
	}	
    }

    /**
     * Removes a node from a set.
     *
     * @param nid the node id to remove.
     *
     * @return the removed handle or null.
     */

    public NodeHandle remove(NodeId nid) {
	for (int i=0; i<theSize; i++) {
	    if (nodes[i].getNodeId().equals(nid)) {
		NodeHandle handle = nodes[i];
		
		nodes[i] = nodes[--theSize];

		setChanged();
		notifyObservers(new NodeSetUpdate(handle, false));

		return handle;
	    }
	}

	return null;
    }

    /**
     * Membership test.
     *
     * @param nid the node id to membership of.
     *
     * @return true if it is a member, false otherwise.
     */

    public boolean member(NodeId nid) {
	for (int i=0; i<theSize; i++) 
	    if (nodes[i].getNodeId().equals(nid)) return true;

	return false;
    }

    /**
     * Return the current size of the set.
     *
     * @return the size.
     */

    public int size() { return theSize; }
    
    /**
     * Pings all new nodes in the RouteSet. Called from RouteMaintenance.
     */

    public void pingAllNew() { 
	for (int i=0; i<theSize; i++) {
	    if (nodes[i].proximity() == Integer.MAX_VALUE)
		nodes[i].ping();
	}
    }

    /**
     * Return the closest live node in the set.
     *
     * @return the closest node, or null if no live node exists in the set.
     */

    public NodeHandle closestNode() { 
	int bestProximity = Integer.MAX_VALUE;
	NodeHandle bestNode = null;

	for (int i=0; i<theSize; i++) {
	    if (!nodes[i].isAlive()) continue;

	    int p = nodes[i].proximity();
	    if (p <= bestProximity) {
		bestProximity = p;
		bestNode = nodes[i];
	    }
	}

	//System.out.println(bestProximity);
	//System.out.println(nodes.length);
	
	return bestNode;
    }
    
    /**
     * Returns the node in the ith position in the set.
     *
     * @return the ith node.
     */

    public NodeHandle get(int i) { 
	if (i < 0 || i >= theSize) throw new NoSuchElementException();
	
	return nodes[i]; 
    }

    /**
     * Returns the node handle with the matching node id or null if none exists.
     *
     * @param nid the node id.
     * 
     * @return the node handle.
     */

    public NodeHandle get(NodeId nid) {
     	for (int i=0; i<theSize; i++) 
	    if (nodes[i].getNodeId().equals(nid)) return nodes[i];

	return null;
    }

    /**
     * Get the index of the node id.
     *
     * @return the node.
     */

    public int getIndex(NodeId nid) { 
	for (int i=0; i<theSize; i++)
	    if (nodes[i].getNodeId().equals(nid)) return i;
	
	return -1;
    }
}
