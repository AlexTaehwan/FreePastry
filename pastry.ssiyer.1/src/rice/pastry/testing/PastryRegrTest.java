/*************************************************************************

"Free Pastry" Peer-to-Peer Application Development Substrate 

Copyright 2002, Rice University. All rights reserved.  Developed by
Andrew Ladd, Peter Druschel.

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

package rice.pastry.testing;

import rice.pastry.*;
import rice.pastry.direct.*;
import rice.pastry.standard.*;
import rice.pastry.join.*;
import rice.pastry.client.*;
import rice.pastry.messaging.*;
import rice.pastry.security.*;
import rice.pastry.routing.*;
import rice.pastry.leafset.*;

import java.util.*;

/**
 * PastryRegrTest
 *
 * a regression test suite for pastry.
 *
 * @author andrew ladd
 * @author peter druschel
 */

public class PastryRegrTest {
    private DirectPastryNodeFactory factory;
    private NetworkSimulator simulator;

    public Vector pastryNodes;
    public TreeMap pastryNodesSorted;
    public Vector pastryNodesLastAdded;
    public boolean inConcJoin;
    private Vector rtApps;

    private Random rng;

    public Message lastMsg;
    public NodeId.Distance lastDist;
    public NodeId lastNode;

    public int msgCount;

    // constructor

    public PastryRegrTest() {
	factory = new DirectPastryNodeFactory();
	simulator = factory.getNetworkSimulator();

	pastryNodes = new Vector();
	pastryNodesSorted = new TreeMap();
	pastryNodesLastAdded = new Vector();
	inConcJoin = false;
	rtApps = new Vector();
	rng = new Random();
    }

    /**
     * make a new pastry node
     */

    public void makePastryNode() {
	PastryNode pn = new PastryNode(factory);
	pastryNodes.addElement(pn);
	pastryNodesSorted.put(pn.getNodeId(),pn);
	pastryNodesLastAdded.clear();
	pastryNodesLastAdded.addElement(pn.getNodeId());

	RegrTestApp rta = new RegrTestApp(pn,this);
	rtApps.addElement(rta);

	int n = pastryNodes.size();
	int msgCount = 0;

	if (n > 1) {
	    PastryNode other = (PastryNode) pastryNodes.get(n - 2);
	    
	    pn.receiveMessage(new InitiateJoin(other));
	    while(simulate()) msgCount++;
	}

	//System.out.println("created " + pn + " messages: " + msgCount);

	checkLeafSet(rta);
	checkRoutingTable(rta);
	//System.out.println("");
    }


    /**
     * make a set of num new pastry nodes, concurrently
     * this tests concurrent node joins
     *
     * @param num the number of nodes in a set
     */

    public void makePastryNode(int num) {
	RegrTestApp rta[] = new RegrTestApp[num];
	pastryNodesLastAdded.clear();

	inConcJoin = true;

	for (int i=0; i<num; i++) {
	    PastryNode pn = new PastryNode(factory);
	    pastryNodes.addElement(pn);
	    pastryNodesSorted.put(pn.getNodeId(),pn);
	    pastryNodesLastAdded.addElement(pn.getNodeId());

	    rta[i] = new RegrTestApp(pn,this);
	    rtApps.addElement(rta[i]);

	    int n = pastryNodes.size();

	    if (n > num) {
		PastryNode other = (PastryNode) pastryNodes.get(n - num - 1);
		pn.receiveMessage(new InitiateJoin(other));
	    }
	    else if (n > 1) {
		// we have to join the first batch of nodes sequentially, else we created multiple rings

		PastryNode other = (PastryNode) pastryNodes.get(n - 2);
		pn.receiveMessage(new InitiateJoin(other));
		while(simulate()) msgCount++;
	    }
	}
	
	int msgCount = 0;

	// now simulate concurrent joins
	while(simulate()) msgCount++;

	inConcJoin = false;

	for (int i=0; i<num; i++) {
	    System.out.println("created " + rta[i].getNodeId());

	    checkLeafSet(rta[i]);
	    checkRoutingTable(rta[i]);
	}

	System.out.println("messages: " + msgCount);

	/*
	for (int i=0; i<rtApps.size(); i++) {
	    checkLeafSet((RegrTestApp)rtApps.get(i));
	    checkRoutingTable((RegrTestApp)rtApps.get(i));
	}	
	*/

    }

    /**
     * Send messages among random message pairs. In each round, one
     * message is sent from a random source node to a random
     * destination; then, a second message is sent from a random
     * source node with a random key (key is not necessaily the nodeId
     * of an existing node)
     * 
     * @param k the number of rounds */

    public void sendPings(int k) {
	int n = rtApps.size();
		
	for (int i=0; i<k; i++) {
	    int from = rng.nextInt(n);
	    int to = rng.nextInt(n);
	    byte[] keyBytes = new byte[16];
	    rng.nextBytes(keyBytes);
	    NodeId key = new NodeId(keyBytes);

	    RegrTestApp rta = (RegrTestApp) rtApps.get(from);
	    PastryNode pn = (PastryNode) pastryNodes.get(to);

	    // send to a  random node
	    rta.sendTrace(pn.getNodeId());
	    while(simulate());

	    // send to a random key
	    rta.sendTrace(key);
	    while(simulate());
	    
	    //System.out.println("-------------------");
	}
    }

    /**
     * send one simulated message
     */

    public boolean simulate() { 
	boolean res = simulator.simulate(); 
	if (res) msgCount++;
	return res;
    }


    /**
     * verify the correctness of the leaf set
     */

    public void checkLeafSet(RegrTestApp rta) {
	LeafSet ls = rta.getLeafSet();
	NodeId localId = rta.getNodeId();

	// check size
	if (ls.size() < ls.maxSize() && pastryNodesSorted.size() > ls.size() + 1)
	    System.out.println("checkLeafSet: too small at" + rta.getNodeId() +
			       "ls.size()=" + ls.size() + " total nodes=" + pastryNodesSorted.size() + "\n" + ls);

	// check for correct leafset range
	// ccw half
	for (int i=-ls.ccwSize(); i<0; i++) {
	    NodeId nid = ls.get(i).getNodeId();
	    int inBetween;

	    if (localId.compareTo(nid) > 0) // local > nid ?
		inBetween = pastryNodesSorted.subMap(nid, localId).size();
	    else
		inBetween = pastryNodesSorted.tailMap(nid).size() + 
		    pastryNodesSorted.headMap(localId).size();	    

	    if (inBetween != -i)
		System.out.println("checkLeafSet: failure at" + rta.getNodeId() +
				   "i=" + i + " inBetween=" + inBetween + "\n" + ls);
	}
	
	// cw half
	for (int i=1; i<=ls.cwSize(); i++) {
	    NodeId nid = ls.get(i).getNodeId();
	    int inBetween;

	    if (localId.compareTo(nid) < 0)   // localId < nid?
		inBetween = pastryNodesSorted.subMap(localId, nid).size();
	    else 
		inBetween = pastryNodesSorted.tailMap(localId).size() + 
		    pastryNodesSorted.headMap(nid).size();

	    if (inBetween != i)
		System.out.println("checkLeafSet: failure at" + rta.getNodeId() +
				       "i=" + i + " inBetween=" + inBetween + "\n" + ls);
	}

    }


    /**
     * verify the correctness of the routing table
     */

    public void checkRoutingTable(RegrTestApp rta) {
	RoutingTable rt = rta.getRoutingTable();

	// check routing table

	for (int i=rt.numRows()-1; i>=0; i--) {
	  // next row 
	    for (int j=0; j<rt.numColumns(); j++) {
		// next column

		// skip if local nodeId digit
		// if (j == rta.getNodeId().getDigit(i,4)) continue;

		RouteSet rs = rt.getRouteSet(i,j);

		NodeId domainFirst = rta.getNodeId().getDomainPrefix(i,j,0);
		NodeId domainLast = rta.getNodeId().getDomainPrefix(i,j,0xf);
		//System.out.println("prefixes " + rta.getNodeId() + domainFirst + domainLast);

		if (rs.size() == 0) {
		    // no entry
		    
		    // check if no nodes with appropriate prefix exist
		    int inBetween = pastryNodesSorted.subMap(domainFirst,domainLast).size() +
			(pastryNodesSorted.containsKey(domainLast) ? 1 : 0);

		    if (inBetween > 0) {

			System.out.println("checkRoutingTable: missing RT entry at" + rta.getNodeId() +
					   "row=" + i + " column=" + j + " inBetween=" + inBetween);
			//System.out.println("prefixes " + rta.getNodeId() + domainFirst + domainLast);
		    }
		}
		else {
		    // check entries
		    int lastProximity = 0;
		    for (int k=0; k<rs.size(); k++) {

			// check for correct proximity ordering
			if (rs.get(k).proximity() < lastProximity) {
			    System.out.println("checkRoutingTable failure 1, row=" + i + " column=" + j +
					       " rank=" + k);

			}
			//lastProximity = rs.get(k).proximity();

			NodeId id = rs.get(k).getNodeId();

			// check if node exists
			if (!pastryNodesSorted.containsKey(id)) {
			    if (simulator.isAlive(id))
				System.out.println("checkRoutingTable failure 2, row=" + i + " column=" + j +
						   " rank=" + k);
			}
			else   // check if node has correct prefix
			    if ( !pastryNodesSorted.subMap(domainFirst,domainLast).containsKey(id) &&
				 !domainLast.equals(id) )
				System.out.println("checkRoutingTable failure 3, row=" + i + " column=" + j +
						   " rank=" + k);
		    }
		}
	    }		
	}

	//System.out.println(rt);

    }

    /**
     * initiate leafset maintenance
     *
     * @param pn the pastry node
     */
    public void initiateLeafSetMaintenance() {

	for (int i=0; i<pastryNodes.size(); i++) {
	    PastryNode pn = (PastryNode)pastryNodes.get(i);
	    pn.receiveMessage(new InitiateLeafSetMaintenance());
	    while(simulate());
	}

    }

    /**
     * kill a random number of nodes
     *
     * @param num the number of nodes to kill
     */

    public void killNodes(int num) {
	EuclideanNetwork enet = (EuclideanNetwork)simulator;

	for (int i=0; i<num; i++) {
	    int n = rng.nextInt(pastryNodes.size());

	    PastryNode pn = (PastryNode)pastryNodes.get(n);
	    pastryNodes.remove(n);
	    rtApps.remove(n);
	    pastryNodesSorted.remove(pn.getNodeId());
	    enet.setAlive(pn.getNodeId(), false);
	    System.out.println("Killed " + pn.getNodeId());
	}
    }


    /**
     * main
     */

    public static void main(String args[]) {
	PastryRegrTest pt = new PastryRegrTest();
	
	int n = 4000;
	int d = 1000;
	int k = 100;
	int numConcJoins = 1;
	int m = 100;

	Date old = new Date();

	for (int i=0; i<n; i += numConcJoins) {
	    pt.makePastryNode(numConcJoins);

	    if ((i + numConcJoins) % m == 0) {
		Date now = new Date();
		System.out.println((i + numConcJoins) + " " + (now.getTime() - old.getTime()) + 
				   " " + pt.msgCount);
		pt.msgCount = 0;
		old = now;
	    }

	    pt.sendPings(k);
	}
	
	System.out.println(n + " nodes constructed");

	// kill some nodes
	pt.killNodes(d);

	System.out.println(d + " nodes killed");

	// send messages
	pt.sendPings((n-d)*k);
	System.out.println((n-d)*k + " messages sent");

	System.out.println("starting leafset maintenance");

	// initiate leafset maint.
	pt.initiateLeafSetMaintenance();

	System.out.println("finished leafset maintenance");

	// send messages
	pt.sendPings((n-d)*k);
	System.out.println((n-d)*k + " messages sent");

	// print all nodeIds, sorted
	Iterator it = pt.pastryNodesSorted.keySet().iterator();
	while (it.hasNext())
	    System.out.println(it.next());

	System.out.println("starting RT and leafset check");

	// check all routing tables, leaf sets
	for (int i=0; i<pt.rtApps.size(); i++) {
	    pt.checkLeafSet((RegrTestApp)pt.rtApps.get(i));
	    pt.checkRoutingTable((RegrTestApp)pt.rtApps.get(i));
	}
    
	//pt.sendPings(k);

	System.out.println("Starting second leafset maintenance");

	// initiate 2nd leafset maint.
	pt.initiateLeafSetMaintenance();

	System.out.println("finished second leafset maintenance");

	// send messages
	pt.sendPings((n-d)*k);
	System.out.println((n-d)*k + " messages sent");

	System.out.println("starting RT and leafset check");

	// check all routing tables, leaf sets
	for (int i=0; i<pt.rtApps.size(); i++) {
	    pt.checkLeafSet((RegrTestApp)pt.rtApps.get(i));
	    pt.checkRoutingTable((RegrTestApp)pt.rtApps.get(i));
	}

    }
}





