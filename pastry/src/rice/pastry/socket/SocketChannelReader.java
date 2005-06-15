
package rice.pastry.socket;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.*;
import java.util.zip.*;

import rice.*;
import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.pastry.*;
import rice.pastry.messaging.*;

/**
 * Class which serves as an "reader" for messages sent across the wire via the
 * Pastry socket protocol. This class builds up an object as it is being sent
 * across the wire, and when it has recieved all of an object, it informs the
 * WirePastryNode by using the recieveMessage(msg) method. The
 * SocketChannelReader is designed to be reused, to read objects continiously
 * off of one stream.
 *
 * @version $Id: SocketChannelReader.java,v 1.5 2004/03/08 19:53:57 amislove Exp
 *      $
 * @author Alan Mislove
 */
public class SocketChannelReader {
  
  // the maximal message size to be deserialized on the selector thread
  public int SELECTOR_DESERIALIZATION_MAX_SIZE;

  // the pastry node
  private PastryNode spn;

  // the cached size of the message
  private int objectSize = -1;

  // for reading from the socket
  private ByteBuffer buffer;

  // for reading the size of the object (header)
  private ByteBuffer sizeBuffer;
  
  // the address this reader is reading from
  protected SourceRoute path;
  
  // the environment to use
  protected Environment environment;
  
  /**
   * Constructor which creates this SocketChannelReader and the WirePastryNode.
   * Once the reader has completely read a message, it deserializes the message
   * and hands it off to the pastry node.
   *
   * @param spn The PastryNode the SocketChannelReader serves.
   */
  public SocketChannelReader(PastryNode spn, SourceRoute path) {
    this(spn.getEnvironment(), path);
    this.spn = spn;
  }
  
  public SocketChannelReader(Environment env, SourceRoute path) {
    this.environment = env;
    this.path = path;
    sizeBuffer = ByteBuffer.allocateDirect(4);
    SELECTOR_DESERIALIZATION_MAX_SIZE = environment.getParameters().getInt(
        "pastry_socket_reader_selector_deserialization_max_size");
  }
  
  /**
   * Sets this reader's path
   *
   * @param path The path this reader is using
   */
  protected void setPath(SourceRoute path) {
    this.path = path;
  }
  
  private void log(int level, String s) {
    environment.getLogManager().getLogger(SocketChannelReader.class, null).log(level,s);
  }

  private void logException(int level, Exception e) {
    environment.getLogManager().getLogger(SocketChannelReader.class, null).logException(level, e);
  }


  /**
   * Method which is to be called when there is data available on the specified
   * SocketChannel. The data is read in, and if the object is done being read,
   * it is parsed.
   *
   * @param sc The channel to read from.
   * @return The object read off the stream, or null if no object has been
   *      completely read yet
   * @exception IOException DESCRIBE THE EXCEPTION
   */
  public Object read(final SocketChannel sc) throws IOException {
    if (objectSize == -1) {
      int read = sc.read(sizeBuffer);

      // implies that the channel is closed
      if (read == -1) 
        throw new IOException("Error on read - the channel has been closed.");

      if (sizeBuffer.remaining() == 0)
        initializeObjectBuffer();
      else
        return null;
    }

    if (objectSize != -1) {
      int read = sc.read(buffer);

      log(Logger.FINEST, "(R) Read " + read + " bytes of object..." + buffer.remaining());

      // implies that the channel is closed
      if (read == -1) 
        throw new ClosedChannelException();

      if (buffer.remaining() == 0) {
        buffer.flip();

        final byte[] objectArray = new byte[objectSize];
        buffer.get(objectArray);
        final int size = objectSize + 4;
        reset();

        if (size < SELECTOR_DESERIALIZATION_MAX_SIZE) {
          Object obj = deserialize(objectArray);
          
          if (obj != null) {
            log(Logger.FINER, "(R) Deserialized bytes into object " + obj);
            
            if ((spn != null) && (spn instanceof SocketPastryNode))
              ((SocketPastryNode) spn).broadcastReceivedListeners(obj, (path == null ? new InetSocketAddress[] {(InetSocketAddress) sc.socket().getRemoteSocketAddress()} : path.toArray()), size);

            record(obj, size, path);
            
            return obj;
          }
        } else {
          log(Logger.INFO, "COUNT: Read message, but too big to deserialize on Selector thread");
          ((SocketPastryNode) spn).process(new Executable() {
            public String toString() { return "Deserialization of message of size " + size + " from " + path; }
            public Object execute() {
              log(Logger.INFO, "COUNT: Starting deserialization on message on processing thread");
              try {
                return deserialize(objectArray);
              } catch (Exception e) {
                return e;
              }
            }
          }, new Continuation() {
            public void receiveResult(Object o) {
              if ((spn != null) && (spn instanceof SocketPastryNode))
                ((SocketPastryNode) spn).broadcastReceivedListeners(o, (path == null ? new InetSocketAddress[] {(InetSocketAddress) sc.socket().getRemoteSocketAddress()} : path.toArray()), size);
              
              record(o, size, path);
              
              if (o instanceof Message) 
                spn.receiveMessage((Message) o);
              else
                receiveException((Exception) o);
            }
            
            public void receiveException(Exception e) {
              log(Logger.WARNING, "Processing deserialization of message caused exception " + e);
              logException(Logger.WARNING, e);
            }
          });
        }
      }
    }

    return null;
  }
  
  protected void record(Object obj, int size, SourceRoute path) {
		boolean recorded = false;
		try {
			if (obj instanceof rice.pastry.routing.RouteMessage) {
				record(((rice.pastry.routing.RouteMessage) obj).unwrap(), size, path);
				recorded = true;
			} else if (obj instanceof rice.pastry.commonapi.PastryEndpointMessage) {
				record(((rice.pastry.commonapi.PastryEndpointMessage) obj).getMessage(), size, path);
				recorded = true;
//			} else if (obj instanceof rice.post.messaging.PostPastryMessage) {
//				record(((rice.post.messaging.PostPastryMessage) obj).getMessage().getMessage(), size, path);
//				recorded = true;
			}
		} catch (java.lang.NoClassDefFoundError exc) { }

    if (!recorded) {
      log(Logger.FINER, "COUNT: Read message " + obj.getClass() + " of size " + size + " from " + path);
    }
  }

  /**
   * Resets this input stream so that it is ready to read another object off of
   * the queue.
   */
  public void reset() {
    objectSize = -1;

    buffer = null;
    sizeBuffer.clear();
  }

  /**
   * Private method which is designed to read the header of the incoming
   * message, and prepare the buffer for the object appropriately.
   *
   * @exception IOException DESCRIBE THE EXCEPTION
   */
  private void initializeObjectBuffer() throws IOException {
    // flip the buffer
    sizeBuffer.flip();

    // allocate space for the header
    byte[] sizeArray = new byte[4];
    sizeBuffer.get(sizeArray, 0, 4);
    
    // read the object size
    DataInputStream dis = new DataInputStream(new ByteArrayInputStream(sizeArray));
    objectSize = dis.readInt();
    
    if (objectSize <= 0) 
      throw new IOException("Found message of improper number of bytes - " + objectSize + " bytes");
    
    log(Logger.FINER, "(R) Found object of " + objectSize + " bytes");
    
    // allocate the appropriate space
    buffer = ByteBuffer.allocateDirect(objectSize);
  }

  /**
   * Method which parses an object once it is ready, and notifies the pastry
   * node of the message.
   *
   * @param array DESCRIBE THE PARAMETER
   * @return the deserialized object
   * @exception IOException DESCRIBE THE EXCEPTION
   */
  private Object deserialize(byte[] array) throws IOException {
    ObjectInputStream ois = new PastryObjectInputStream(new ByteArrayInputStream(array), spn);
    Object o = null;

    try {
      return ois.readObject();
    } catch (ClassCastException e) {
      log(Logger.SEVERE, "PANIC: Serialized message was not a pastry message!");
      throw new IOException("Message recieved " + o + " was not a pastry message - closing channel.");
    } catch (ClassNotFoundException e) {
      log(Logger.SEVERE, "PANIC: Unknown class type in serialized message!");
      throw new IOException("Unknown class type in message - closing channel.");
    } catch (InvalidClassException e) {
      log(Logger.SEVERE, "PANIC: Serialized message was an invalid class! " + e.getMessage());
      throw new IOException("Invalid class in message - closing channel.");
    } catch (IllegalStateException e) {
      log(Logger.SEVERE, "PANIC: Serialized message caused an illegal state exception! " + e.getMessage());
      throw new IOException("Illegal state from deserializing message - closing channel.");
    } catch (NullPointerException e) {
      log(Logger.SEVERE, "PANIC: Serialized message caused a null pointer exception! " + e.getMessage());
      return null;
    } catch (Exception e) {
      log(Logger.SEVERE, "PANIC: Serialized message caused exception! " + e.getMessage());
      throw new IOException("Exception from deserializing message - closing channel.");
    }
  }


}
