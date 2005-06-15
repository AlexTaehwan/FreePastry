package rice.pastry.socket;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.*;
import java.util.zip.*;

import rice.environment.logging.Logger;
import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.pastry.socket.*;
import rice.pastry.socket.SocketCollectionManager.*;

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
public class SocketChannelRepeater {
  
  // the default size of the transfer array
  protected int REPEATER_BUFFER_SIZE;
  // 4 for the ip, 4 for the port (int), 8 for the epoch (long)
  protected static int HEADER_BUFFER_SIZE = 16;
  
  // whether or not this repeater has been connected to the other side
  private boolean connected;
  
  // the local node
  private PastryNode spn;
  
  // the original socket channel
  private SocketChannel original;
  
  // the source route manager
  private SourceRouteManager manager;
    
  // for first buffer for socket 1 -> 2
  private ByteBuffer buffer1;
  
  // for first buffer for socket 2 -> 1
  private ByteBuffer buffer2;
  
  // for reading from the header information socket
  private ByteBuffer headerBuffer;

  /**
   * Constructor which creates this SocketChannelReader and the WirePastryNode.
   * Once the reader has completely read a message, it deserializes the message
   * and hands it off to the pastry node.
   *
   * @param spn The PastryNode the SocketChannelReader serves.
   */
  public SocketChannelRepeater(PastryNode spn, SourceRouteManager manager) {
    this.spn = spn;
    this.manager = manager;
    REPEATER_BUFFER_SIZE = spn.getEnvironment().getParameters().getInt("pastry_socket_repeater_buffer_size");
    this.headerBuffer = ByteBuffer.allocateDirect(HEADER_BUFFER_SIZE);
    this.buffer1 = ByteBuffer.allocateDirect(REPEATER_BUFFER_SIZE);
    this.buffer2 = ByteBuffer.allocateDirect(REPEATER_BUFFER_SIZE);
  }
  
  /**
   * Method which can be used to constuct the necessary header for the 
   * intermediate hop
   *
   * @param address The final address of the source route
   * @return The entire header
   */
  public static byte[] encodeHeader(EpochInetSocketAddress address) {
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      DataOutputStream dos = new DataOutputStream(baos);
      dos.write(address.getAddress().getAddress().getAddress());
      dos.writeInt(address.getAddress().getPort());
      dos.writeLong(address.getEpoch());
      dos.flush();
      
      return baos.toByteArray();
    } catch (IOException canthappen) {
      throw new RuntimeException("PANIC: SHOULDN'T HAPPEN " + canthappen, canthappen);
    }
  }
  
  /**
   * Method which can be used to decode the necessary header for the 
   * intermediate hop
   *
   * @param array The encoded header
   * @return The address
   */
  public static EpochInetSocketAddress decodeHeader(byte[] array) throws IOException {
    return decodeHeader(array, 0);
  }
  
  /**
   * Method which can be used to decode the necessary header for the 
   * intermediate hop
   *
   * @param array The encoded header
   * @return The address
   */
  public static EpochInetSocketAddress decodeHeader(byte[] array, int offset) throws IOException {
    byte[] ip = new byte[4];
    byte[] skip = new byte[HEADER_BUFFER_SIZE];
    
    // read the object size
    DataInputStream dis = new DataInputStream(new ByteArrayInputStream(array));
    
    // skip the stuff
    for (int i=0; i<offset; i++)
      dis.readFully(skip);
    
    // now read our stuff
    dis.readFully(ip);
    int port = dis.readInt();
    long epoch = dis.readLong();
    
    if ((port <= 0) || (port >= 65536)) 
      throw new IOException("Found inet address with improper port - " + port);
    
    return new EpochInetSocketAddress(new InetSocketAddress(InetAddress.getByAddress(ip), port), epoch);
  }
  
  /**
   * Internal method which determines which maps socket channels to buffers
   * - the original socket channel gets buffer 1, and the second channel
   * gets buffer 2.
   *
   * @param channel The channel
   * @param reading Whether or not the channel is for reading
   * @return The buffer for that channel
   */
  private ByteBuffer getBuffer(SocketChannel sc, boolean reading) {
    if (reading == (sc == original))
      return buffer1;
    else
      return buffer2;
  }

  /**
   * Method which is to be called when there is data available on the specified
   * SocketChannel. The data is read in, and is put into the output buffer.
   *
   * @param sc The channel to read from.
   * @return whether or not we the reading key should be turned off an the
   *     writing key on
   * @exception IOException DESCRIBE THE EXCEPTION
   */
  public boolean read(SocketChannel sc) throws IOException {
    if (original == null) original = sc;

    if (! connected) {
      int read = sc.read(headerBuffer);

      // implies that the channel is closed
      if (read == -1) 
        throw new IOException("Error on read - the channel has been closed.");

      if (headerBuffer.remaining() == 0) 
        processHeaderBuffer();
      else 
        return false;
    }
    
    ByteBuffer buffer = getBuffer(sc, true);

    int read = sc.read(buffer);
    
    debug("Read " + read + " bytes of data..." + buffer.remaining());

    // implies that the channel is closed
    if (read == -1) 
      throw new ClosedChannelException();

    // return true if we've read anything (and can therefore write something)
    if (read > 0) {
      buffer.flip();
      return true;
    } else {
      return false;
    }
  }
  
  /**
   * Method which is designed to be called when this repeater should write 
   * something out.
   *
   * @param sc The SocketChannel to write to
   * @return true if this output stream is done, false otherwise
   * @exception IOException DESCRIBE THE EXCEPTION
   */
  public boolean write(SocketChannel sc) throws IOException {
    ByteBuffer buffer = getBuffer(sc, false);
        
    int j = buffer.limit();
    int i = sc.write(buffer);
    
    debug("Wrote " + i + " of " + j + " bytes to " + sc.socket().getRemoteSocketAddress());
    
    // if we've written everything in the buffer, clear it, and return true
    if (buffer.remaining() == 0) {
      buffer.flip();
      buffer.clear();
      
      return true;
    } else {
      return false;
    }
  }  

  /**
   * Private method which is designed to read the header of the incoming
   * message, and determine which foriegn address to connect to
   *
   * @exception IOException DESCRIBE THE EXCEPTION
   */
  private void processHeaderBuffer() throws IOException {
    // flip the buffer
    headerBuffer.flip();
    
    // allocate space for the header
    byte[] headerArray = new byte[HEADER_BUFFER_SIZE];
    headerBuffer.get(headerArray);

    EpochInetSocketAddress address = decodeHeader(headerArray);
    manager.createConnection(address);
    
    debug("Read address " + address);    
    
    this.connected = true;
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @param s DESCRIBE THE PARAMETER
   */
  private void debug(String s) {
    spn.getEnvironment().getLogManager().getLogger(SocketChannelRepeater.class, null).log(Logger.FINER,
        "(SCR): " + s);
  }
}
