package rice.tutorial.max_min;

import java.io.IOException;
import java.net.*;
import java.util.*;

import rice.environment.Environment;
import rice.p2p.commonapi.NodeHandle;
import rice.pastry.*;
import rice.pastry.socket.SocketPastryNodeFactory;
import rice.pastry.standard.RandomNodeIdFactory;
import rice.pastry.transport.TransportPastryNodeFactory;

public class ScribeMinMax {
	Vector<MyScribeClient> apps = new Vector<MyScribeClient>();

	public ScribeMinMax(int bindport, InetSocketAddress bootaddress,
		int numNodes, Environment env) throws Exception {

		// generate random node
		NodeIdFactory nidFactory = new RandomNodeIdFactory(env);

		// construct the PastryNodeFactory; how we use socket
		PastryNodeFactory factory = new SocketPastryNodeFactory(nidFactory, bindport, env);

		// loop to construct # nodes
		for (int curNode = 0; curNode < numNodes; curNode++){
			//construct a new node
			PastryNode node = factory.newNode();

			//construct a new scribe application
			MyScribeClient app = new MyScribeClient(node);
			apps.add(app);

			node.boot(bootaddress);

			synchronized(node){
				while(!node.isReady() && !node.joinFailed()){
					node.wait(500);
					if (node.joinFailed()){
						throw new IOException("Could not join the FreePastry ring. Reason:"+node.joinFailedReason());
					}
				}
			}
			System.out.println("Finished creating new node: "+node);
		}

		Iterator<MyScribeClient> i = apps.iterator();
		MyScribeClient app = (MyScribeClient) i.next();
		app.subscribe();
		app.startPublishTask();
		while (i.hasNext()){
			app = (MyScribeClient) i.next();
			app.subscribe();
		}

		env.getTimeSource().sleep(5000);
		sendNumber(apps);
		env.getTimeSource().sleep(5000);
		printMaxNumber(apps);

	}

	public static void sendNumber(Vector<MyScribeClient> apps){
		Iterator<MyScribeClient> i = apps.iterator();
		while (i.hasNext()){
			MyScribeClient app = (MyScribeClient) i.next();
			// if the app is not root
			if (!app.isRoot()){
				NodeHandle parent = app.getParent();
				app.routeMyMsg(parent);
			}
		}
	}

	public static void printMaxNumber(Vector<MyScribeClient> apps){
		Iterator<MyScribeClient> i = apps.iterator();
		while (i.hasNext()){
			MyScribeClient app = (MyScribeClient) i.next();
			if (app.isRoot()){
				int max_num = Collections.max(app.list,null);
				System.out.println("Max val from head node:"+max_num);
			}
		}
	}


	public static void main(String[] args) throws Exception {
    // Loads pastry configurations
    Environment env = new Environment();

    // disable the UPnP setting (in case you are testing this on a NATted LAN)
    env.getParameters().setString("nat_search_policy","never");
    
    try {
      // the port to use locally
      int bindport = Integer.parseInt(args[0]);

      // build the bootaddress from the command line args
      InetAddress bootaddr = InetAddress.getByName(args[1]);
      int bootport = Integer.parseInt(args[2]);
      InetSocketAddress bootaddress = new InetSocketAddress(bootaddr, bootport);

      // the port to use locally
      int numNodes = Integer.parseInt(args[3]);

      // launch our node!
      ScribeMinMax dt = new ScribeMinMax(bindport, bootaddress, numNodes,
          env);
    } catch (Exception e) {
      // remind user how to use
      System.out.println("Usage:");
      System.out
          .println("java [-cp FreePastry-<version>.jar] rice.tutorial.scribe.ScribeTutorial localbindport bootIP bootPort numNodes");
      System.out
          .println("example java rice.tutorial.scribe.ScribeTutorial 9001 pokey.cs.almamater.edu 9001 10");
      throw e;
    }
  }
}