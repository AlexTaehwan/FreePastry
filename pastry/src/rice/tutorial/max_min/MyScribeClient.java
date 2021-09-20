package rice.tutorial.max_min;

import rice.p2p.commonapi.*;
import rice.p2p.commonapi.Application;
import rice.p2p.commonapi.CancellableTask;
import rice.p2p.commonapi.Endpoint;
import rice.p2p.commonapi.Id;
import rice.p2p.commonapi.Message;
import rice.p2p.commonapi.NodeHandle;
import rice.p2p.commonapi.RouteMessage;
import rice.p2p.scribe.Scribe;
import rice.p2p.scribe.ScribeClient;
import rice.p2p.scribe.ScribeContent;
import rice.p2p.scribe.ScribeImpl;
import rice.p2p.scribe.Topic;
import rice.pastry.commonapi.PastryIdFactory;
import java.util.Random;
import java.util.*;

public class MyScribeClient implements ScribeClient, Application {
	int seqNum = 0;
	int int_random;
	CancellableTask publishTask;
	Scribe myScribe;
	Topic myTopic;
	List<Integer> list;
	protected Endpoint endpoint;

	public MyScribeClient(Node node){
		// make a list to save the random numbers (for the head node)
		this.list = new ArrayList<Integer>();

		//random number generator (for each node)
		Random rand = new Random();
		int upperbound = 25;
		this.int_random = rand.nextInt(upperbound);
		System.out.println("Random number="+int_random);

		this.endpoint = node.buildEndpoint(this, "myinstance");
		//construct Scribe
		myScribe = new ScribeImpl(node, "myScribeInstance");

		//construct the topic
		myTopic = new Topic(new PastryIdFactory(node.getEnvironment()), "example topic");
		System.out.println("myTopic = "+myTopic);

		endpoint.register();
	}

	public void subscribe(){
		myScribe.subscribe(myTopic, this);
	}

	public void startPublishTask(){
		// publishTask = endpoint.scheduleMessage(new PublishContent(), 5000, 5000);
	}

	public void deliver(Id id, Message message){
		System.out.println(this+" received "+message);
		// if (message instanceof PublishContent){
		// 	sendMulticast();
		// }
		if (message instanceof MyMsg){
			int rand_int = ((MyMsg)message).rand_int;
			list.add(rand_int);
		}
	}

	public void deliver(Topic topic, ScribeContent content){
		System.out.println("MyScribeClient.deliver("+topic+","+content+")");
		if (((MyScribeContent)content).from == null) {
			new Exception("Stack Trace").printStackTrace();
		}
	}

	public void sendMulticast(){
		System.out.println("Node "+endpoint.getLocalNodeHandle()+" broadcasting "+seqNum);
		MyScribeContent myMessage = new MyScribeContent(endpoint.getLocalNodeHandle(), seqNum);
		myScribe.publish(myTopic, myMessage);
		seqNum++;
	}

	public void childAdded(Topic topic, NodeHandle child) {

	}

	public void subscribeFailed(Topic topic) {
//    System.out.println("MyScribeClient.childFailed("+topic+")");
  	}
  	public void childRemoved(Topic topic, NodeHandle child) {
//    System.out.println("MyScribeClient.childRemoved("+topic+","+child+")");
  	}

	public boolean forward(RouteMessage message) {
		return true;
	}

	public void update(NodeHandle handle, boolean joined) {

	}
	public boolean anycast(Topic topic, ScribeContent content) {
    boolean returnValue = myScribe.getEnvironment().getRandomSource().nextInt(3) == 0;
    System.out.println("MyScribeClient.anycast("+topic+","+content+"):"+returnValue);
    return returnValue;
  	}

	class PublishContent implements Message {
		public int getPriority(){
			return MAX_PRIORITY;
		}
	}

	public boolean isRoot() {
		return myScribe.isRoot(myTopic);
	}
	public NodeHandle getParent(){
		return ((ScribeImpl)myScribe).getParent(myTopic);
	}

	public NodeHandle[] getChildren(){
		return myScribe.getChildren(myTopic);
	}

	// route message
	public void routeMyMsg(NodeHandle nh){
		System.out.println(this+" sending to parent node"+nh+"Random int: "+int_random);
		Message msg = new MyMsg(endpoint.getId(), nh.getId(), int_random);
		endpoint.route(null, msg, nh);
	}

}