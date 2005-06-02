
package rice.pastry.dist;

import java.net.InetSocketAddress;
import java.util.*;

import rice.*;
import rice.environment.Environment;
import rice.pastry.ExponentialBackoffScheduledMessage;
import rice.pastry.NetworkListener;
import rice.pastry.NodeHandle;
import rice.pastry.NodeId;
import rice.pastry.PastryNode;
import rice.pastry.ScheduledMessage;
import rice.pastry.join.InitiateJoin;
import rice.pastry.leafset.InitiateLeafSetMaintenance;
import rice.pastry.messaging.Message;
import rice.pastry.routing.InitiateRouteSetMaintenance;
import rice.persistence.PersistentStorage;
import rice.selector.SelectorManager;
import rice.selector.Timer;
import sun.misc.SignalHandler;

/**
 * Class which represents the abstraction of a "real" pastry node. Designed to
 * be extended by the protocol implementation (i.e. RMI or Socket) desired.
 *
 * @version $Id$
 * @author Alan Mislove
 */
public abstract class DistPastryNode extends PastryNode {
  public static final boolean verbose = false;
  
  // the queue used for processing requests
  public static ProcessingQueue QUEUE = new ProcessingQueue();
  public static ProcessingThread THREAD = new ProcessingThread(QUEUE);
  
  static {
    THREAD.start();
    THREAD.setPriority(Thread.MIN_PRIORITY);
  }
  
  // Period (in seconds) at which the leafset and routeset maintenance tasks, respectively, are invoked.
  // 0 means never.
  protected int leafSetMaintFreq, routeSetMaintFreq;

  // timer that supports scheduled messages
  protected Timer timer;// = SelectorManager.getSelectorManager().getTimer();//new Timer(true);
  
  // the list of network listeners
  private Vector listeners;

  // the list of errors
  private static Vector errors = new Vector();

  // join retransmission stuff
  private ScheduledMessage joinEvent;

  /**
   * Constructor, with NodeId. Need to set the node's ID before this node is
   * inserted as localHandle.localNode.
   *
   * @param id DESCRIBE THE PARAMETER
   */
  protected DistPastryNode(NodeId id, Environment e) {    
    super(id, e);
    SignalHandler s;
    timer = e.getSelectorManager().getTimer();
//    timer = new Timer(true);
    // uses deamon thread, so it terminates once other threads have terminated
    
    this.listeners = new Vector();
  }
  
  public Timer getTimer() {
    return timer;
  }
  
  public void addNetworkListener(NetworkListener listener) {
    listeners.add(listener);
  }
  
  protected NetworkListener[] getNetworkListeners() {
    return (NetworkListener[]) listeners.toArray(new NetworkListener[0]);
  }
  
  public void broadcastSentListeners(Object message, InetSocketAddress[] path, int size) {
    NetworkListener[] listeners = getNetworkListeners();
    
    for (int i=0; i<listeners.length; i++)
      listeners[i].dataSent(message, path[path.length-1], size);
  }
  
  public void broadcastReceivedListeners(Object message, InetSocketAddress[] path, int size) {
    NetworkListener[] listeners = getNetworkListeners();
    
    for (int i=0; i<listeners.length; i++)
      listeners[i].dataReceived(message, path[path.length-1], size);
  }

  public static String[] getErrors() {
    String[] result = (String[]) errors.toArray(new String[0]);
    errors.clear();
    
    return result;
  }
  
  public static void addError(String error) {
    if (errors.size() > 20)
      errors.removeElementAt(0);
    
    errors.add(error);
  }

  /**
   * Method which returns the Dist for this Pastry node.
   *
   * @return The node handle pool for this pastry node.
   */
  public abstract DistNodeHandlePool getNodeHandlePool();


  /**
   * Sends an InitiateJoin message to itself.
   *
   * @param bootstrap Node handle to bootstrap with.
   */
  public final void initiateJoin(NodeHandle bootstrap) {
    //System.out.println("DistPN.initiateJoin()");
    if (bootstrap != null) {
      //this.receiveMessage(new InitiateJoin(bootstrap));

      // schedule (re-)transmission of the join message, every 5s
      //joinEvent = scheduleMsg(new InitiateJoin(bootstrap), 0, 5000);

      // schedule (re-)transmission of the join message at an exponential backoff
      joinEvent = scheduleMsgExpBackoff(new InitiateJoin(bootstrap), 0, 15000, 2);

    } else {
      setReady();
    }
    // no bootstrap node, so ready immediately
  }


  /**
   * Called from PastryNode when the join succeeds.
   */
  public void nodeIsReady() {
    if (joinEvent != null) {
      joinEvent.cancel();
    }
    // cancel join retransmissions
  }

	protected ScheduledMessage leafSetRoutineMaintenance = null;
	protected ScheduledMessage routeSetRoutineMaintenance = null;

  /**
   * Called after the node is initialized.
   *
   * @param bootstrap DESCRIBE THE PARAMETER
   */
  public void doneNode(NodeHandle bootstrap) {

    if (routeSetMaintFreq > 0) {
      // schedule the routeset maintenance event
      routeSetRoutineMaintenance = scheduleMsgAtFixedRate(new InitiateRouteSetMaintenance(),
        routeSetMaintFreq * 1000, routeSetMaintFreq * 1000);
//        System.out.println("Scheduling routeSetMaint for "+routeSetMaintFreq * 1000+","+routeSetMaintFreq * 1000);
    }
    if (leafSetMaintFreq > 0) {
      // schedule the leafset maintenance event
      leafSetRoutineMaintenance = scheduleMsgAtFixedRate(new InitiateLeafSetMaintenance(),
        leafSetMaintFreq * 1000, leafSetMaintFreq * 1000);
//        System.out.println("Scheduling leafSetMaint for "+leafSetMaintFreq * 1000+","+leafSetMaintFreq * 1000);
    }
  }


  /**
   * Method which kills a PastryNode (used only for testing).
   */
  public void resign() {
    leafSetRoutineMaintenance.cancel();
    routeSetRoutineMaintenance.cancel();
  }


  /**
   * Schedule the specified message to be sent to the local node after a
   * specified delay. Useful to provide timeouts.
   *
   * @param msg a message that will be delivered to the local node after the
   *      specified delay
   * @param delay time in milliseconds before message is to be delivered
   * @return the scheduled event object; can be used to cancel the message
   */
  public ScheduledMessage scheduleMsg(Message msg, long delay) {
    ScheduledMessage sm = new ScheduledMessage(this, msg);
    timer.schedule(sm, delay);
    return sm;
  }


  /**
   * Schedule the specified message for repeated fixed-delay delivery to the
   * local node, beginning after the specified delay. Subsequent executions take
   * place at approximately regular intervals separated by the specified period.
   * Useful to initiate periodic tasks.
   *
   * @param msg a message that will be delivered to the local node after the
   *      specified delay
   * @param delay time in milliseconds before message is to be delivered
   * @param period time in milliseconds between successive message deliveries
   * @return the scheduled event object; can be used to cancel the message
   */
  public ScheduledMessage scheduleMsg(Message msg, long delay, long period) {
    ScheduledMessage sm = new ScheduledMessage(this, msg);
    timer.schedule(sm, delay, period);
    return sm;
  }

  public ExponentialBackoffScheduledMessage scheduleMsgExpBackoff(Message msg, long delay, long initialPeriod, double expBase) {
    ExponentialBackoffScheduledMessage sm = new ExponentialBackoffScheduledMessage(this,msg,timer,delay,initialPeriod,expBase);
    return sm;
  }

  /**
   * Schedule the specified message for repeated fixed-rate delivery to the
   * local node, beginning after the specified delay. Subsequent executions take
   * place at approximately regular intervals, separated by the specified
   * period.
   *
   * @param msg a message that will be delivered to the local node after the
   *      specified delay
   * @param delay time in milliseconds before message is to be delivered
   * @param period time in milliseconds between successive message deliveries
   * @return the scheduled event object; can be used to cancel the message
   */
  public ScheduledMessage scheduleMsgAtFixedRate(Message msg, long delay, long period) {
    ScheduledMessage sm = new ScheduledMessage(this, msg);
    timer.scheduleAtFixedRate(sm, delay, period);
    return sm;
  }
  
  /**
   * Schedules a job for processing on the dedicated processing thread.  CPU intensive jobs, such
   * as encryption, erasure encoding, or bloom filter creation should never be done in the context
   * of the underlying node's thread, and should only be done via this method.  
   *
   * @param task The task to run on the processing thread
   * @param command The command to return the result to once it's done
   */
  public void process(Executable task, Continuation command) {
    QUEUE.enqueue(new ProcessingRequest(task, command, getEnvironment().getSelectorManager()));
  }
  
  private static class ProcessingThread extends Thread {
    ProcessingQueue queue;
	   
	   public ProcessingThread(ProcessingQueue queue){
       super("Dedicated Processing Thread");
       this.queue = queue;
	   }
	   
	   public void run() {
       while (true) {
         ProcessingRequest e = queue.dequeue();
         
         if (DistPastryNode.verbose) System.out.println("COUNT: " + System.currentTimeMillis() + " Starting execution of " + e.r);
         e.run();
         if (DistPastryNode.verbose) System.out.println("COUNT: " + System.currentTimeMillis() + " Done execution of " + e.r);
       }
	   }
  }
  
  public static class ProcessingQueue {
    
    List q = new LinkedList();
	  int capacity = -1;
	  
	  public ProcessingQueue() {
	     /* do nothing */
	  }
	  
	  public ProcessingQueue(int capacity) {
	     this.capacity = capacity;
	  }
    
    public synchronized int getLength() {
      return q.size();
    }
	  
	  public synchronized void enqueue(ProcessingRequest request) {
      if (capacity < 0 || q.size() < capacity) {
			  q.add(request);
			  notifyAll();
		  } else {
			  request.returnError(new ProcessingQueueOverflowException());
      }
	  }
	  
	  public synchronized ProcessingRequest dequeue() {
      while (q.isEmpty()) {
        try {
          wait();
        } catch (InterruptedException e) {
        }
      }
      
      return (ProcessingRequest) q.remove(0);
    }
	}

  private static class ProcessingRequest {
    Continuation c;
    Executable r;
    SelectorManager sm;
    
		public ProcessingRequest(Executable r, Continuation c, SelectorManager sm){
      this.r = r;
      this.c = c;
      this.sm = sm;
		}
    
    public void returnResult(Object o) {
      c.receiveResult(o); 
    }
    
    public void returnError(Exception e) {
      c.receiveException(e); 
    }
    
    public void run() {
      try {
      //  long start = System.currentTimeMillis();
        final Object result = r.execute();
      //  System.out.println("QT: " + (System.currentTimeMillis() - start) + " " + r.toString());

        sm.invoke(new Runnable() {
          public void run() {
            returnResult(result);
          }
          public String toString(){
            return "return ProcessingRequest for " + r + " to " + c;
          }
        });
      } catch (final Exception e) {
        sm.invoke(new Runnable() {
          public void run() {
            returnError(e);
          }
          public String toString(){
            return "return ProcessingRequest for " + r + " to " + c;
          }
        });
      }
    }
	}
  
  public static class ProcessingQueueOverflowException extends Exception {
  }
}

