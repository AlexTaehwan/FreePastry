
package rice.p2p.scribe.testing;
import java.io.IOException;
import java.util.*;

import rice.environment.Environment;
import rice.p2p.commonapi.*;
import rice.p2p.commonapi.testing.CommonAPITest;
import rice.p2p.scribe.*;
import rice.p2p.scribe.messaging.SubscribeMessage;

/**
 * @(#) DistScribeRegrTest.java Provides regression testing for the Scribe service using distributed
 * nodes.
 *
 * @version $Id$
 * @author Alan Mislove
 */

public class ScribeRegrTest extends CommonAPITest {

  // the instance name to use
  /**
   * DESCRIBE THE FIELD
   */
  public static String INSTANCE = "ScribeRegrTest";

  // the scribe impls in the ring
  /**
   * DESCRIBE THE FIELD
   */
  protected ScribeImpl[] scribes;

  /**
   * The scribe policies
   */
  protected TestScribePolicy policies[];

  // a random number generator
  /**
   * DESCRIBE THE FIELD
   */
  protected Random rng;

    protected Random generateIdRng;

  /**
   * Constructor which sets up all local variables
   */
  public ScribeRegrTest(Environment env) throws IOException {
    super(env);
    scribes = new ScribeImpl[NUM_NODES];
    policies = new TestScribePolicy[NUM_NODES];
    rng = new Random();
    generateIdRng = new Random();
  }


  /**
   * Usage: DistScribeRegrTest [-port p] [-bootstrap host[:port]] [-nodes n] [-protocol (rmi|wire)]
   * [-help]
   *
   * @param args DESCRIBE THE PARAMETER
   */
  public static void main(String args[]) throws IOException {
    parseArgs(args);
    ScribeRegrTest scribeTest = new ScribeRegrTest(new Environment());
    scribeTest.start();
  }

  /**
   * Method which should process the given newly-created node
   *
   * @param node The newly created node
   * @param num The number of this node
   */
  protected void processNode(int num, Node node) {
    scribes[num] = new ScribeImpl(node, INSTANCE);
    policies[num] = new TestScribePolicy(scribes[num]);
    scribes[num].setPolicy(policies[num]);
  }

  /**
   * Method which should run the test - this is called once all of the nodes have been created and
   * are ready.
   */
  protected void runTest() {
    if (NUM_NODES < 2) {
      System.out.println("The DistScribeRegrTest must be run with at least 2 nodes for proper testing.  Use the '-nodes n' to specify the number of nodes.");
      return;
    }

    // Run each test
    testBasic(1, "Basic");
    testBasic(2, "Partial (1)");
    testBasic(4, "Partial (2)");
    testSingleRoot("Single rooted Trees");
    testAPI();
    testFailureNotification();
    testMaintenance();

  }

  /*
   *  ---------- Test methods and classes ----------
   */
  
  /**
    * Tests basic functionality
   */
  protected void testBasic(int skip, String name) {
    sectionStart(name + " Scribe Networks");
    int NUM_MESSAGES = 5;
    int SKIP = skip;
    Topic topic = new Topic(generateId());
    TestScribeClient[] clients = new TestScribeClient[NUM_NODES/SKIP];

    stepStart(name + " Tree Construction");
    for (int i = 0; i < NUM_NODES/SKIP; i++) {
      clients[i] = new TestScribeClient(scribes[i], topic, i);
      scribes[i].subscribe(topic, clients[i]);
      simulate();
    }

    int numWithParent = 0;
    for (int i=0; i < NUM_NODES/SKIP; i++) {
      if (scribes[i].getParent(topic) != null)
        numWithParent++;
    }

    if (numWithParent < (NUM_NODES/SKIP) - 1)
      stepDone(FAILURE, "Expected at least " + (NUM_NODES/SKIP - 1) + " nodes with parents, found " + numWithParent);
    else
      stepDone(SUCCESS);

    stepStart(name + " Publish");
    ScribeImpl local = scribes[rng.nextInt(NUM_NODES/SKIP)];

    for (int i = 0; i < NUM_MESSAGES; i++) {
      local.publish(topic, new TestScribeContent(topic, i));
      simulate();
    }

    boolean failed = false;
    for (int i=0; i < NUM_NODES/SKIP; i++) {
      if (clients[i].getPublishMessages().length != NUM_MESSAGES) {
        stepDone(FAILURE, "Expected client " + clients[i] + " to receive all messages, received " + clients[i].getPublishMessages().length);
        failed = true;
      }
    }

    if (! failed)
      stepDone(SUCCESS);

    stepStart(name + " Anycast - No Accept");
    local = scribes[rng.nextInt(NUM_NODES)];

    local.anycast(topic, new TestScribeContent(topic, 59));
    simulate();

    failed = false;
    for (int i=0; i < NUM_NODES/SKIP; i++) {
      if (clients[i].getAnycastMessages().length != 0) {
        stepDone(FAILURE, "Expected no accepters for anycast, found one at " + scribes[i]);
        failed = true;
      }
    }

    if (! failed)
      stepDone(SUCCESS);

    stepStart(name + " Anycast - 1 Accept");
    TestScribeClient client = clients[rng.nextInt(NUM_NODES/SKIP)];
    client.acceptAnycast(true);
    local = scribes[rng.nextInt(NUM_NODES)];

    local.anycast(topic, new TestScribeContent(topic, 59));
    simulate();

    failed = false;
    for (int i=0; i < NUM_NODES/SKIP; i++) {
      if (clients[i].equals(client)) {
        if (clients[i].getAnycastMessages().length != 1) {
          stepDone(FAILURE, "Expected node to accept anycast at " + client+" accepted "+clients[i].getAnycastMessages().length);
          failed = true;
        }
      } else {
        if (clients[i].getAnycastMessages().length != 0) {
          stepDone(FAILURE, "Expected no accepters for anycast, found one at " + scribes[i]);
          failed = true;
        }
      }
    }

    if (! failed)
      stepDone(SUCCESS);

    stepStart(name + " Anycast - All Accept");
    for (int i=0; i < NUM_NODES/SKIP; i++) {
      clients[i].acceptAnycast(true);
    }

    local = scribes[rng.nextInt(NUM_NODES/SKIP)];

    local.anycast(topic, new TestScribeContent(topic, 59));
    simulate();

    int total = 0;
    for (int i=0; i < NUM_NODES/SKIP; i++) {
      total += clients[i].getAnycastMessages().length;
    }

    if (total != 2)
      stepDone(FAILURE, "Expected 2 anycast messages to be found, found " + total);
    else
      stepDone(SUCCESS);

    stepStart(name + " Unsubscribe");
    for (int i=0; i < NUM_NODES/SKIP; i++) {
      scribes[i].unsubscribe(topic, clients[i]);
      simulate();
    }

    local = scribes[rng.nextInt(NUM_NODES)];
    local.publish(topic, new TestScribeContent(topic, 100));
    simulate();

    failed = false;
    for (int i=0; i < NUM_NODES/SKIP; i++) {
      if (clients[i].getPublishMessages().length != NUM_MESSAGES) {
        stepDone(FAILURE, "Expected client " + clients[i] + " to receive no additional messages, received " + clients[i].getPublishMessages().length);
        failed = true;
      }
    }

    if (! failed)
      stepDone(SUCCESS);

    stepStart(name + " Tree Completely Demolished");
    failed = false;
    for (int i=0; i < NUM_NODES; i++) {
      if (scribes[i].getClients(topic).length > 0) {
        stepDone(FAILURE, "Expected scribe " + scribes[i] + " to have no clients, had " + scribes[i].getClients(topic).length);
        failed = true;
      }

      if (scribes[i].getChildren(topic).length > 0) {
        stepDone(FAILURE, "Expected scribe " + scribes[i] + " to have no children, had " + scribes[i].getChildren(topic).length);
        failed = true;
      }

      if (scribes[i].getParent(topic) != null) {
        stepDone(FAILURE, "Expected scribe " + scribes[i] + " to have no parent, had " + scribes[i].getParent(topic));
        failed = true;
      }
    }

    if (! failed)
      stepDone(SUCCESS);    

    sectionDone();
  }

  /**
    * Tests basic publish functionality
   */
    protected void testAPI() {
	sectionStart("Scribe API Functionality");
    int NUM_MESSAGES = 5;
    Topic topic = new Topic(generateId());
    TestScribeClient[] clients = new TestScribeClient[NUM_NODES];

    stepStart("Tree Construction");
    for(int i = 0; i < NUM_NODES; i++)
	policies[i].allowSubscribe(false);

    for (int i = 0; i < NUM_NODES/2; i++) {
      clients[i] = new TestScribeClient(scribes[i], topic, i);
      scribes[i].subscribe(topic, clients[i]);
      simulate();
    }

    int numWithParent = 0;
    for (int i=0; i < NUM_NODES; i++) {
      if (scribes[i].getParent(topic) != null)
        numWithParent++;
    }

    if (numWithParent < (NUM_NODES/2) - 1)
      stepDone(FAILURE, "Expected at least " + (NUM_NODES/2 - 1) + " nodes with parents, found " + numWithParent);
    else
      if (numWithParent > (NUM_NODES/2))
        stepDone(FAILURE, "Expected no more than " + (NUM_NODES/2) + " nodes with parents, due to policy, found " + numWithParent);
      else 
        stepDone(SUCCESS);

    stepStart("Drop Child");
    // now, find a scribe with a child
    ScribeImpl scribe = null;
    TestScribeClient client = null;
    TestScribePolicy policy = null;

    for (int i=0; (i<NUM_NODES) && (scribe == null); i++) {
      if (scribes[i].getChildren(topic).length > 0) {
        scribe = scribes[i];
        client = clients[i];
        policy = policies[i];
      }
    }

    if (scribe == null) {
      stepDone(FAILURE, "Could not find any scribes with children");
    } else {
      NodeHandle child = scribe.getChildren(topic)[0];

      // set this client to never allow subscribes
      policy.neverAllowSubscribe(true);
      
      // drop the handle now
      scribe.removeChild(topic, child);
      simulate();

      ScribeImpl local = scribes[rng.nextInt(NUM_NODES)];

      for (int i = 0; i < NUM_MESSAGES; i++) {
        local.publish(topic, new TestScribeContent(topic, i));
        simulate();
      }

      boolean failed = false;
      for (int i=0; i < NUM_NODES/2; i++) {
        if (clients[i].getPublishMessages().length != NUM_MESSAGES) {
          stepDone(FAILURE, "Expected client " + clients[i] + " to receive all messages, received " + clients[i].getPublishMessages().length);
          failed = true;
        }
      }

      NodeHandle[] children = scribe.getChildren(topic);

      if (Arrays.asList(children).contains(child)) {
        stepDone(FAILURE, "Child resubscribed to previous node, policy should prevent this.");
        failed = true;
      }

      if (! failed)
        stepDone(SUCCESS);
    }

    stepStart("Reset Policies");
    for (int i=0; i<NUM_NODES; i++) {
      policies[i].allowSubscribe(true);
      policies[i].neverAllowSubscribe(false);
    }
    stepDone(SUCCESS);
      
    sectionDone();
  }

  /**
    * Tests failure notification
   */
  protected void testFailureNotification() {
    sectionStart("Subscribe Failure Notification");
    Topic topic = new Topic(generateId());
    TestScribeClient client;

    stepStart("Policy Change");
    for (int i=0; i < NUM_NODES; i++) {
      policies[i].neverAllowSubscribe(true);
    }

    stepDone(SUCCESS);
    
    stepStart("Subscribe Attempt");
    int i = rng.nextInt(NUM_NODES);

    while (scribes[i].isRoot(topic))
      i = rng.nextInt(NUM_NODES);
    
    client = new TestScribeClient(scribes[i], topic, i);
    scribes[i].subscribe(topic, client);
    simulate();

    stepDone(SUCCESS);

    stepStart("Failure Notification Delivered");
    if (! client.getSubscribeFailed())
      stepDone(FAILURE, "Expected subscribe to fail, but did not.");
    else
      stepDone(SUCCESS);

    stepStart("Policy Reset");
    for (int j=0; j < NUM_NODES; j++) {
      policies[j].neverAllowSubscribe(false);
    }

    stepDone(SUCCESS);

    sectionDone();
  }    


    protected void testSingleRoot(String name) {
	sectionStart(name + "");
	int numTrees = 10;
	boolean failed = false;

	for(int num=0; num<numTrees; num ++) {
	    Topic topic = new Topic(generateId());
	    TestScribeClient[] clients = new TestScribeClient[NUM_NODES];
	    
	    stepStart(name + " TopicId=" + topic.getId());
	    for (int i = 0; i < NUM_NODES; i++) {
	    clients[i] = new TestScribeClient(scribes[i], topic, i);
	    scribes[i].subscribe(topic, clients[i]);
	    simulate();
	    }
	    
	    int numRoot = 0;
	    for (int i=0; i < NUM_NODES; i++) {
		if (scribes[i].isRoot(topic)) {
		    numRoot++;
		    //System.out.println("myId= " + scribes[i].getId());
		}
	    }
	    
	    if (numRoot != 1) {
		stepDone(FAILURE, "Number of roots= " + numRoot);
		failed = true;
	    }
	    else
		stepDone(SUCCESS);
	    
	}
	sectionDone();
	
    }
    


  /**
   * Tests basic publish functionality
   */
    protected void testMaintenance() {
	sectionStart("Tree Maintenance Under Node Death");
	int NUM_MESSAGES = 5;
	Topic topic = new Topic(generateId());
	TestScribeClient[] clients = new TestScribeClient[NUM_NODES];

    stepStart("Tree Construction");
    for (int i = 0; i < NUM_NODES; i++) {
      clients[i] = new TestScribeClient(scribes[i], topic, i);
      scribes[i].subscribe(topic, clients[i]);
      simulate();
    }

    int numWithParent = 0;
    for (int i=0; i < NUM_NODES; i++) {
      if (scribes[i].getParent(topic) != null)
        numWithParent++;
    }

    if (numWithParent < NUM_NODES-1)
      stepDone(FAILURE, "Expected at least " + (NUM_NODES - 1) + " nodes with parents, found " + numWithParent);
    else
      stepDone(SUCCESS);

    stepStart("Killing Nodes");
    for (int i=0; i<NUM_NODES/2; i++) {
      kill(i);
      simulate();
    }

    stepDone(SUCCESS);

    stepStart("Tree Recovery");
    ScribeImpl local = scribes[rng.nextInt(NUM_NODES/2) + NUM_NODES/2];

    for (int i = 0; i < NUM_MESSAGES; i++) {
      local.publish(topic, new TestScribeContent(topic, i));
      simulate();
    }

    boolean failed = false;
    for (int i=NUM_NODES/2; i < NUM_NODES; i++) {
      if (clients[i].getPublishMessages().length != NUM_MESSAGES) {
        stepDone(FAILURE, "Expected client " + clients[i] + " to receive all messages, received " + clients[i].getPublishMessages().length);
        failed = true;
      }
    }

    if (! failed)
      stepDone(SUCCESS);

    sectionDone();
  }
  

  /**
   * Private method which generates a random Id
   *
   * @return A new random Id
   */
  private Id generateId() {
    byte[] data = new byte[20];
    generateIdRng.nextBytes(data);
    return FACTORY.buildId(data);
  }

  /**
   * Utility class for past content objects
   *
   * @version $Id$
   * @author amislove
   */
  protected static class TestScribeContent implements ScribeContent {

    /**
     * DESCRIBE THE FIELD
     */
    protected Topic topic;

    /**
     * DESCRIBE THE FIELD
     */
    protected int num;

    /**
     * Constructor for TestScribeContent.
     *
     * @param topic DESCRIBE THE PARAMETER
     * @param num DESCRIBE THE PARAMETER
     */
    public TestScribeContent(Topic topic, int num) {
      this.topic = topic;
      this.num = num;
    }

    /**
     * DESCRIBE THE METHOD
     *
     * @param o DESCRIBE THE PARAMETER
     * @return DESCRIBE THE RETURN VALUE
     */
    public boolean equals(Object o) {
      if (!(o instanceof TestScribeContent)) {
        return false;
      }

      return (((TestScribeContent) o).topic.equals(topic) &&
        ((TestScribeContent) o).num == num);
    }

    /**
     * DESCRIBE THE METHOD
     *
     * @return DESCRIBE THE RETURN VALUE
     */
    public String toString() {
      return "TestScribeContent(" + topic + ", " + num + ")";
    }
  }

  /**
   * Utility class which simulates a route message
   *
   * @version $Id$
   * @author amislove
   */
  protected static class TestRouteMessage implements RouteMessage {

    private Id id;

    private NodeHandle nextHop;

    private Message message;

    /**
     * Constructor for TestRouteMessage.
     *
     * @param id DESCRIBE THE PARAMETER
     * @param nextHop DESCRIBE THE PARAMETER
     * @param message DESCRIBE THE PARAMETER
     */
    public TestRouteMessage(Id id, NodeHandle nextHop, Message message) {
      this.id = id;
      this.nextHop = nextHop;
      this.message = message;
    }

    /**
     * Gets the DestinationId attribute of the TestRouteMessage object
     *
     * @return The DestinationId value
     */
    public Id getDestinationId() {
      return id;
    }

    /**
     * Gets the NextHopHandle attribute of the TestRouteMessage object
     *
     * @return The NextHopHandle value
     */
    public NodeHandle getNextHopHandle() {
      return nextHop;
    }

    /**
     * Gets the Message attribute of the TestRouteMessage object
     *
     * @return The Message value
     */
    public Message getMessage() {
      return message;
    }

    /**
     * Sets the DestinationId attribute of the TestRouteMessage object
     *
     * @param id The new DestinationId value
     */
    public void setDestinationId(Id id) {
      this.id = id;
    }

    /**
     * Sets the NextHopHandle attribute of the TestRouteMessage object
     *
     * @param nextHop The new NextHopHandle value
     */
    public void setNextHopHandle(NodeHandle nextHop) {
      this.nextHop = nextHop;
    }

    /**
     * Sets the Message attribute of the TestRouteMessage object
     *
     * @param message The new Message value
     */
    public void setMessage(Message message) {
      this.message = message;
    }
  }

  /**
   * DESCRIBE THE CLASS
   *
   * @version $Id$
   * @author amislove
   */
  protected class TestScribeClient implements ScribeClient {

    /**
     * DESCRIBE THE FIELD
     */
    protected ScribeImpl scribe;

    /**
     * DESCRIBE THE FIELD
     */
    protected int i;

    /**
     * The publish messages received so far
     */
    protected Vector publishMessages;

    /**
      * The publish messages received so far
     */
    protected Vector anycastMessages;

    /**
     * The topic this client is listening for
     */
    protected Topic topic;

    /**
     * Whether or not this client should accept anycasts
     */
    protected boolean acceptAnycast;

    /**
     * Whether this client has had a subscribe fail
     */
    protected boolean subscribeFailed;

    /**
     * Constructor for TestScribeClient.
     *
     * @param scribe DESCRIBE THE PARAMETER
     * @param i DESCRIBE THE PARAMETER
     */
    public TestScribeClient(ScribeImpl scribe, Topic topic, int i) {
      this.scribe = scribe;
      this.i = i;
      this.topic = topic;
      this.publishMessages = new Vector();
      this.anycastMessages = new Vector();
      this.acceptAnycast = false;
      this.subscribeFailed = false;
    }

    public ScribeContent[] getPublishMessages() {
      return (ScribeContent[]) publishMessages.toArray(new ScribeContent[0]);
    }

    public ScribeContent[] getAnycastMessages() {
      return (ScribeContent[]) anycastMessages.toArray(new ScribeContent[0]);
    }

    public void acceptAnycast(boolean value) {
      this.acceptAnycast = value;
    }

    /**
     * DESCRIBE THE METHOD
     *
     * @param topic DESCRIBE THE PARAMETER
     * @param content DESCRIBE THE PARAMETER
     * @return DESCRIBE THE RETURN VALUE
     */
    public boolean anycast(Topic topic, ScribeContent content) {
      if (acceptAnycast)
        anycastMessages.add(content);

      return acceptAnycast;
    }

    /**
     * DESCRIBE THE METHOD
     *
     * @param topic DESCRIBE THE PARAMETER
     * @param content DESCRIBE THE PARAMETER
     */
    public void deliver(Topic topic, ScribeContent content) {
      publishMessages.add(content);
    }

    /**
     * DESCRIBE THE METHOD
     *
     * @param topic DESCRIBE THE PARAMETER
     * @param child DESCRIBE THE PARAMETER
     */
    public void childAdded(Topic topic, NodeHandle child) {
     // System.out.println("CHILD ADDED AT " + scribe.getId());
    }

    /**
     * DESCRIBE THE METHOD
     *
     * @param topic DESCRIBE THE PARAMETER
     * @param child DESCRIBE THE PARAMETER
     */
    public void childRemoved(Topic topic, NodeHandle child) {
     // System.out.println("CHILD REMOVED AT " + scribe.getId());
    }

    public void subscribeFailed(Topic topic) {
      subscribeFailed = true;
    }

    public boolean getSubscribeFailed() {
      return subscribeFailed;
    }
  }

  public class TestScribePolicy extends ScribePolicy.DefaultScribePolicy {

    protected Scribe scribe;

    protected boolean allowSubscribe;

    protected boolean neverAllowSubscribe;
    
    public TestScribePolicy(Scribe scribe) {
      this.scribe = scribe;
      allowSubscribe = true;
      neverAllowSubscribe = false;
    }

    public void allowSubscribe(boolean allowSubscribe) {
      this.allowSubscribe = allowSubscribe;
    }

    public void neverAllowSubscribe(boolean neverAllowSubscribe) {
      this.neverAllowSubscribe = neverAllowSubscribe;
    }

    public boolean allowSubscribe(SubscribeMessage message, ScribeClient[] clients, NodeHandle[] children) {
	//System.out.println("Allow subscribe , client.size "+clients.length+", children "+children.length+" for subscriber "+message.getSubscriber());
      return (! neverAllowSubscribe) && (allowSubscribe || (clients.length > 0) || this.scribe.isRoot(message.getTopic()));
    }
  }
}














