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

package rice;

/**
 * Asynchronously receives the result to a given method call, using
 * the command pattern.
 * 
 * Implementations of this class contain the remainder of a computation
 * which included an asynchronous method call.  When the result to the
 * call becomes available, the receiveResult method on this command
 * is called.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 * @author Andreas Haeberlen
 */
public interface Continuation {

  /**
   * Called when a previously requested result is now availble.
   *
   * @param result The result of the command.
   */
  public void receiveResult(Object result);

  /**
   * Called when an execption occured as a result of the
   * previous command.
   *
   * @param result The exception which was caused.
   */
  public void receiveException(Exception result);

  /**
   * This class is a Continuation provided for simplicity which
   * passes any errors up to the parent Continuation which it
   * is constructed with.  Subclasses should implement the
   * receiveResult() method with the appropriate behavior.
   */
  public static abstract class StandardContinuation implements Continuation {

    /**
     * The parent continuation
     */
    protected Continuation parent;

    /**
     * Constructor which takes in the parent continuation
     * for this continuation.
     *
     * @param continuation The parent of this continuation
     */
    public StandardContinuation(Continuation continuation) {
      parent = continuation;
    }

    /**
     * Called when an execption occured as a result of the
     * previous command.  Simply calls the parent continuation's
     * receiveResult() method.
     *
     * @param result The exception which was caused.
     */
    public void receiveException(Exception result) {
      parent.receiveException(result);
    }
  }
  
  /**
   * This class is a Continuation provided for simplicity which
   * passes any results up to the parent Continuation which it
   * is constructed with.  Subclasses should implement the
   * receiveException() method with the appropriate behavior.
   */
  public static abstract class ErrorContinuation implements Continuation {
    
    /**
    * The parent continuation
     */
    protected Continuation parent;
    
    /**
     * Constructor which takes in the parent continuation
     * for this continuation.
     *
     * @param continuation The parent of this continuation
     */
    public ErrorContinuation(Continuation continuation) {
      parent = continuation;
    }
    
    /**
     * Called when an the result is availble.  Simply passes the result
     * to the parent;
     *
     * @param result The result
     */
    public void receiveResult(Object result) {
      parent.receiveResult(result);
    }
  }

  /**
   * This class is a Continuation provided for simplicity which
   * listens for any errors and ignores any success values.  This
   * Continuation is provided for testing convience only and should *NOT* be
   * used in production environment.
   */
  public static class ListenerContinuation implements Continuation {

    /**
     * The name of this continuation
     */
    protected String name;
    
    /**
     * Constructor which takes in a name
     *
     * @param name A name which uniquely identifies this contiuation for
     *   debugging purposes
     */
    public ListenerContinuation(String name) {
      this.name = name;
    }
    
    /**
     * Called when a previously requested result is now availble. Does
     * absolutely nothing.
     *
     * @param result The result
     */
    public void receiveResult(Object result) {
    }

    /**
     * Called when an execption occured as a result of the
     * previous command.  Simply prints an error message to the screen.
     *
     * @param result The exception which was caused.
     */
    public void receiveException(Exception result) {
      System.out.println("ERROR - Received exception " + result + " during task " + name);
      result.printStackTrace();
    }
  }
  
  /**
   * This class is a Continuation provided for simplicity which
   * passes both results and exceptions to the receiveResult() method.
   */
  public abstract static class SimpleContinuation implements Continuation {
    
    /**
     * Called when an execption occured as a result of the
     * previous command.  Simply prints an error message to the screen.
     *
     * @param result The exception which was caused.
     */
    public void receiveException(Exception result) {
      receiveResult(result);
    }
  }

  /**
   * This class provides a continuation which is designed to be used from
   * an external thread.  Applications should construct this continuation pass it
   * in to the appropriate method, and then call sleep().  Once the thread is woken
   * up, the user should check exceptionThrown() to determine if an error was
   * caused, and then call getException() or getResult() as apprpritate.
   */
  public static class ExternalContinuation implements Continuation {

    protected Exception exception;
    protected Object result;
    protected boolean done = false;

    public synchronized void receiveResult(Object o) {
      result = o;
      done = true;
      notify();
    }

    public synchronized void receiveException(Exception e) {
      exception = e;
      done = true;
      notify();
    }

    public Object getResult() {
      if (exception != null) {
        throw new IllegalArgumentException("Exception was thrown in ExternalContinuation, but getResult() called!");
      }
        
      return result;
    }

    public Exception getException() {
      return exception;
    }

    public synchronized void sleep() {
      try {
        if (! done) {
          wait();
        }
      } catch (InterruptedException e) {
        exception = e;
      }
    }
    
    public boolean exceptionThrown() {
      return (exception != null);
    }
  }
  
  /**
   * This class represents a Continuation which is used when multiple 
   * results are expected, which can come back at different times.  The
   * prototypical example of its use is in an application like Past, where
   * Insert messages are sent to a number of replicas and the responses
   * come back at different times.  
   *
   * Optionally, the creator can override the isDone() method, which 
   * is called each time an intermediate result comes in.  This allows
   * applications like Past to declare an insert successful after a 
   * certain number of results have come back successful.
   */
  public static class MultiContinuation {
  
    protected Object result[];
    protected boolean haveResult[];
    protected Continuation parent;
    protected boolean done;
    
    /**
     * Constructor which takes a parent continuation as well
     * as the number of results which to expect.  
     *
     * @param parent The parent continuation
     * @param num The number of results expected to come in
     */
    public MultiContinuation(Continuation parent, int num) {
      this.parent = parent;
      this.result = new Object[num];
      this.haveResult = new boolean[num];
      this.done = false;
    }
    
    /**
     * Returns the continuation which should be used as the
     * result continuation for the index-th result.  This should
     * be called exactly once for each int between 0 and num.
     *
     * @param The index of this continuation
     */
    public Continuation getSubContinuation(final int index) {
      return new Continuation() {
        public void receiveResult(Object o) { receive(index, o); }
        public void receiveException(Exception e) { receive(index, e); }
      };
    }
    
    /**
     * Internal method which receives the results and determines
     * if we are done with this task.  This method ignores multiple
     * calls by the same client continuation.
     *
     * @param index The index the result is for
     * @param o The result for that continuation
     */
    protected void receive(int index, Object o) {
      if ((! done) && (! haveResult[index])) {
        haveResult[index] = true;
        result[index] = o;

        try {
          if (isDone()) {
            done = true;
            parent.receiveResult(getResult());
          }
        } catch (Exception e) {
          done = true;
          parent.receiveException(e);
        }
      }
    }
    
    /**
     * Method which returns whether or not we are done.  This is designed
     * to be overridden by subclasses in order to allow for more advanced
     * behavior.  
     *
     * If we are done and the subclass wishes to return an exception to the
     * calling application, it may throw an Exception, which will be caught
     * and returned to the parent via the receiveException() method.  This
     * will cause this continaution to be permanently marked as done.
     */
    public boolean isDone() throws Exception {
      for (int i=0; i<haveResult.length; i++) 
        if (! haveResult[i]) 
          return false;
      
      return true;
    }
    
    /**
     * Method which can also be overriden to change what result should be 
     * returned to the parent continuation.  This defaults to the Object[]
     * containing results or exceptions.
     *
     * @return The result which should be returned to the application
     */
    public Object getResult() {
      return result; 
    }
  }
  
  /**
   * Continuation class which takes a provided string as it's name, and
   * returns that String when toString() is called.
   */
  public static class NamedContinuation implements Continuation {
    
    // the internal continuation
    protected Continuation parent;
    
    // the name of this continuation
    protected String name;
    
    /**
     * Builds a new NamedContinuation given the name and the wrapped
     * continuation
     *
     * @param name The name
     * @param command The parent continuation
     */
    public NamedContinuation(String name, Continuation command) {
      this.name = name;
      this.parent = command;
    }
    
    /**
     * Called when an the result is availble.  Simply passes the result
     * to the parent;
     *
     * @param result The result
     */
    public void receiveResult(Object result) {
      parent.receiveResult(result);
    }
    
    /**
     * Called when an execption occured as a result of the
     * previous command.  Simply calls the parent continuation's
     * receiveException() method.
     *
     * @param result The exception which was caused.
     */
    public void receiveException(Exception result) {
      parent.receiveException(result);
    }
    
    /**
     * Returns the name of this continuation
     *
     * @return The name
     */
    public String toString() {
      return name;
    }
  }
}
