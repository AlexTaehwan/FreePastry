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

package rice.pastry.dist;

import rice.pastry.*;
import rice.pastry.messaging.*;

import java.io.*;

/**
 * Abstract class for handles to "real" remote nodes. This class abstracts out
 * the node handle verification which is necessary in the "real" pastry protocols,
 * since NodeHandles are sent across the wire.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */

public abstract class DistNodeHandle extends NodeHandle {

    // the nodeId of this node handle's remote node
    protected NodeId nodeId;

    // used for ensuring there is only one active node handle per remote node
    private transient boolean verified;
    private transient DistNodeHandle redirect;

    // liveness and distance functions
    protected transient boolean isInPool;
    protected transient boolean alive;
    protected transient boolean isLocal;

    // distance metric. private in order to notify observers of metric change
    private transient int distance;

    /**
     * Constructor
     *
     * @param nodeId This node handle's node Id.
     */
    public DistNodeHandle(NodeId nodeId) {
      this.nodeId = nodeId;
      verified = false;
      redirect = null;

      isInPool = false;
      alive = true;
      distance = Integer.MAX_VALUE;
      isLocal = false;
    }

    /**
     * Method which verifies this node handle by looking into the node handle
     * pool to see if there is already an entry there. If so, all method called
     * on this node handle will redirect to the previously-existing one. Otherwise,
     * this node handle in inserted into the pool, and all methods are executed on
     * this node handle.
     */
    private final void verify() {
      if (! verified) {
        if (getLocalNode() != null) {
          DistNodeHandle nh = ((DistPastryNode) getLocalNode()).getNodeHandlePool().coalesce(this);

          if (nh != this) {
            redirect = nh;
          }

          verified = true;
        }
      }
    }

    /**
     * Gets the nodeId of this Pastry node.
     *
     * @return the node id.
     */
    public final NodeId getNodeId() {
      return nodeId;
    }

    /**
     * Method called from LocalNode after localnode is set to non-null.
     * Updates the isLocal and alive variables.
     */
    public void afterSetLocalNode() {
      alive = true;

      if ((nodeId != null) && getLocalNode().getNodeId().equals(nodeId)) {
        isLocal = true;
      } else {
        isLocal = false;
      }

      verify();
    }

    /**
     * Returns the last known liveness information about the Pastry node associated with this handle.
     * Invoking this method does not cause network activity. This method is designed to be called by
     * clients using the node handle, and is provided in order to ensure that the right node handle is
     * being talked to.
     *
     * @return true if the node is alive, false otherwise.
     */
    public final boolean isAlive() {
      verify();

      if (redirect != null) {
        return redirect.isAlive();
      }

      if (isLocal && !alive)
        System.out.println("panic; local node dead");

      return alive;
    }

    /**
     * Marks this handle as alive (if dead earlier), and reset distance to infinity. If node is
     * already marked as alive, this does nothing.
     */
    public final void markAlive() {
      verify();

      if (redirect != null) {
        redirect.markAlive();
        return;
      }

      if (alive == false) {
        if (Log.ifp(5)) System.out.println(getLocalNode() + "found " + nodeId + " to be alive after all");

        alive = true;
        distance = Integer.MAX_VALUE;

        notifyObservers();
      }
    }

    /**
     * Mark this handle as dead (if alive earlier), and reset distance to infinity. If node is
     * already marked as deas, this method does nothing.
     */
    public final void markDead() {
      verify();

      if (redirect != null) {
        redirect.markDead();
        return;
      }

      if (alive == true) {
        if (Log.ifp(5)) System.out.println(getLocalNode() + "found " + nodeId + " to be dead");

        alive = false;
        distance = Integer.MAX_VALUE;

        notifyObservers();
      }
    }

    /**
     * Returns the last known proximity information about the Pastry node associated with this handle.
     * Invoking this method does not cause network activity. This method is designed to be called by
     * clients using the node handle, and is provided in order to ensure that the right node handle is
     * being talked to.
     *
     * Smaller values imply greater proximity. The exact nature and interpretation of the proximity metric
     * implementation-specific.
     *
     * @return the proximity metric value
     */
    public final int proximity() {
      verify();

      if (redirect != null) {
        return redirect.proximity();
      }

      if (isLocal)
        return 0;
      else
        return distance;
    }

    /**
     * Method which is designed to be called by subclassses whenever there is a change in the distance
     * metric.  This is done in order to abstract out the notification of the observers of a distance
     * metric change.
     *
     * @param value The new distance value
     */
    protected final void setProximity(int value) {
      distance = value;

      notifyObservers();
    }

    /**
     * Ping the node. Refreshes the cached liveness status and proximity value of the Pastry node associated
     * with this. Invoking this method causes network activity.  This method is designed to be called by
     * clients using the node handle, and is provided in order to ensure that the right node handle is
     * being talked to.
     *
     * @return true if node is currently alive.
     */
    public final boolean ping() {
      verify();

      if (redirect != null)
        return redirect.pingImpl();
      else
        return pingImpl();
    }

    /**
     * Ping the node. Refreshes the cached liveness status and proximity value of the Pastry node associated
     * with this. Invoking this method causes network activity. This method is to be run by the
     * node handle which is in the NodeHandlePool.
     *
     * @return true if node is currently alive.
     */
    protected abstract boolean pingImpl();

    /**
     * Called to send a message to the node corresponding to this handle. This method is
     * designed to be called by clients using the node handle, and is provided in order
     * to ensure that the right node handle is being talked to.
     *
     * @param pn local pastrynode
     */
    public final void receiveMessage(Message message) {
      verify();

      if (redirect != null)
        redirect.receiveMessageImpl(message);
      else
        receiveMessageImpl(message);
    }

    /**
     * Called to send a message to the node corresponding to this handle. This method
     * is to be run by the node handle which is in the NodeHandlePool.
     *
     * @param pn local pastrynode
     */
    protected abstract void receiveMessageImpl(Message message);

    /**
     * Returns a String representation of this DistNodeHandle. This method is
     * designed to be called by clients using the node handle, and is provided in order
     * to ensure that the right node handle is being talked to.
     *
     * @return A String representation of the node handle.
     */
    public final String toString() {
      verify();

      if (redirect != null)
        return redirect.toStringImpl();
      else
        return toStringImpl();
    }

    /**
     * Returns a String representation of this DistNodeHandle. This method
     * is to be run by the node handle which is in the NodeHandlePool.
     *
     * @return A String representation of the node handle.
     */
    protected abstract String toStringImpl();

    /**
     * Returns whether or not this node handle is the one in the node handle pool.
     *
     * @return Whether or not this node handle is in the pool.
     */
    public boolean getIsInPool() {
      return isInPool;
    }

    /**
     * Sets whether or not this node handle is in the node handle pool.
     *
     * @param iip Whether or not this node handle is in the node handle pool.
     */
    public void setIsInPool(boolean iip) {
      isInPool = iip;
    }

    /**
     * Overridden in order to restore default values for all of the
     * variables concerning node distance, status, etc...
     *
     * @param ois The input stream reading this object.
     */
    private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
      ois.defaultReadObject();

      verified = false;
      isInPool = false;
      alive = true;
      distance = Integer.MAX_VALUE;
      isLocal = false;
    }


    /**
     * Prints out nicely formatted debug messages.
     *
     * @param s The message to print.
     */
    protected void debug(String s) {
      if (Log.ifp(6)) {
       if (getLocalNode() != null)
         System.out.println(getLocalNode().getNodeId() + " (" + nodeId + "): " + s);
       else
         System.out.println(getLocalNode() + " (" + nodeId + "): " + s);
     }
   }
}



