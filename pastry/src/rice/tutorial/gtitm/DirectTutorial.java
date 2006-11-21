package rice.tutorial.gtitm;

import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Iterator;
import java.util.Vector;

import rice.environment.Environment;
import rice.p2p.commonapi.Id;
import rice.pastry.NodeHandle;
import rice.pastry.NodeIdFactory;
import rice.pastry.PastryNode;
import rice.pastry.PastryNodeFactory;
import rice.pastry.direct.*;
import rice.pastry.leafset.LeafSet;
import rice.pastry.standard.RandomNodeIdFactory;

/**
 * This tutorial shows how to setup a FreePastry node using the Socket Protocol.
 * 
 * @author Jeff Hoye
 */
public class DirectTutorial {

  // this will keep track of our applications
  Vector apps = new Vector();
  
  /**
   * This constructor launches numNodes PastryNodes.  They will bootstrap 
   * to an existing ring if one exists at the specified location, otherwise
   * it will start a new ring.
   * 
   * @param bindport the local port to bind to 
   * @param bootaddress the IP:port of the node to boot from
   * @param numNodes the number of nodes to create in this JVM
   * @param env the environment for these nodes
   */
  public DirectTutorial(int numNodes, Environment env, String fileName) throws Exception {
    
    // Generate the NodeIds Randomly
    NodeIdFactory nidFactory = new RandomNodeIdFactory(env);
    
    // construct the PastryNodeFactory, this is how we use rice.pastry.direct, from a file
    PastryNodeFactory factory = new DirectPastryNodeFactory(nidFactory, new GenericNetwork(env, new File(fileName)), env);

    // create the handle to boot off of
    NodeHandle bootHandle = null;
    
    // loop to construct the nodes/apps
    for (int curNode = 0; curNode < numNodes; curNode++) {
  
      // construct a node, passing the null boothandle on the first loop will cause the node to start its own ring
      PastryNode node = factory.newNode(bootHandle);
      
      // this way we can boot off the previous node
      bootHandle = node.getLocalHandle();
        
      // the node may require sending several messages to fully boot into the ring
      synchronized(node) {
        while(!node.isReady() && !node.joinFailed()) {
          // delay so we don't busy-wait
          node.wait(500);
          
          // abort if can't join
          if (node.joinFailed()) {
            throw new IOException("Could not join the FreePastry ring.  Reason:"+node.joinFailedReason()); 
          }
        }       
      }
      
      System.out.println("Finished creating new node "+node);
      
      // construct a new MyApp
      MyApp app = new MyApp(node);
      
      apps.add(app);
    }
      
    // wait 10 seconds
    env.getTimeSource().sleep(10000);

      
    // route 10 messages
    for (int i = 0; i < 10; i++) {
        
      // for each app
      Iterator appIterator = apps.iterator();
      while(appIterator.hasNext()) {
        MyApp app = (MyApp)appIterator.next();
        
        // pick a key at random
        Id randId = nidFactory.generateNodeId();
        
        // send to that key
        app.routeMyMsg(randId);
        
        // wait a bit
        env.getTimeSource().sleep(100);
      }
    }
    // wait 1 second
    env.getTimeSource().sleep(1000);
      
    // for each app
    Iterator appIterator = apps.iterator();
    while(appIterator.hasNext()) {
      MyApp app = (MyApp)appIterator.next();
      PastryNode node = (PastryNode)app.getNode();
      
      // send directly to my leafset
      LeafSet leafSet = node.getLeafSet();
      
      // this is a typical loop to cover your leafset.  Note that if the leafset
      // overlaps, then duplicate nodes will be sent to twice
      for (int i=-leafSet.ccwSize(); i<=leafSet.cwSize(); i++) {
        if (i != 0) { // don't send to self
          // select the item
          NodeHandle nh = leafSet.get(i);
          
          // send the message directly to the node
          app.routeMyMsgDirect(nh);   
          
          // wait a bit
          env.getTimeSource().sleep(100);
        }
      }
    }
  }

  /**
   * Usage: 
   * java [-cp FreePastry-<version>.jar] rice.tutorial.gtitm.DirectTutorial numNodes <filename>
   * example java rice.tutorial.gtitm.DirectTutorial 100 GNPINPUT
   */
  public static void main(String[] args) throws Exception {
    // Loads pastry settings, and sets up the Environment for simulation
    Environment env = Environment.directEnvironment();
    
    try {
      // the number of nodes to use
      int numNodes = Integer.parseInt(args[0]);    

      String fileName = "GNPINPUT";
      if (args.length > 1)
        fileName = args[1];
      
      // launch our node!
      DirectTutorial dt = new DirectTutorial(numNodes, env, fileName);
    } catch (Exception e) {
      // remind user how to use
      System.out.println("Usage:"); 
      System.out.println("java [-cp FreePastry-<version>.jar] rice.tutorial.gtitm.DirectTutorial numNodes <filename>");
      System.out.println("example java rice.tutorial.gtitm.DirectTutorial 100 GNPINPUT");
      throw e; 
    }
  }
}