package org.mpisws.p2p.transport.commonapi;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.mpisws.p2p.transport.ErrorHandler;
import org.mpisws.p2p.transport.MessageCallback;
import org.mpisws.p2p.transport.MessageRequestHandle;
import org.mpisws.p2p.transport.P2PSocket;
import org.mpisws.p2p.transport.P2PSocketReceiver;
import org.mpisws.p2p.transport.SocketCallback;
import org.mpisws.p2p.transport.SocketRequestHandle;
import org.mpisws.p2p.transport.TransportLayer;
import org.mpisws.p2p.transport.TransportLayerCallback;
import org.mpisws.p2p.transport.liveness.LivenessListener;
import org.mpisws.p2p.transport.liveness.LivenessProvider;
import org.mpisws.p2p.transport.liveness.PingListener;
import org.mpisws.p2p.transport.proximity.ProximityListener;
import org.mpisws.p2p.transport.proximity.ProximityProvider;
import org.mpisws.p2p.transport.util.DefaultCallback;
import org.mpisws.p2p.transport.util.DefaultErrorHandler;
import org.mpisws.p2p.transport.util.InsufficientBytesException;
import org.mpisws.p2p.transport.util.MessageRequestHandleImpl;
import org.mpisws.p2p.transport.util.SocketInputBuffer;
import org.mpisws.p2p.transport.util.SocketRequestHandleImpl;
import org.mpisws.p2p.transport.util.SocketWrapperSocket;

import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.p2p.commonapi.Id;
import rice.p2p.commonapi.rawserialization.RawMessage;
import rice.p2p.util.rawserialization.SimpleInputBuffer;
import rice.p2p.util.rawserialization.SimpleOutputBuffer;

public class CommonAPITransportLayerImpl<Identifier> implements 
    CommonAPITransportLayer<Identifier>, 
    TransportLayerCallback<Identifier, ByteBuffer>,
    LivenessListener<Identifier>,
    ProximityListener<Identifier> {
  
  TransportLayerNodeHandle<Identifier> localAddress; 
  TransportLayer<Identifier, ByteBuffer> tl;
  LivenessProvider<Identifier> livenessProvider;
  ProximityProvider<Identifier> proximityProvider;
  TransportLayerCallback<TransportLayerNodeHandle<Identifier>, RawMessage> callback;
  ErrorHandler<TransportLayerNodeHandle<Identifier>> errorHandler;
  RawMessageDeserializer deserializer;
  IdFactory idFactory;
  NodeHandleFactory<Identifier> nodeHandleFactory;
  Logger logger;

  public CommonAPITransportLayerImpl(
      TransportLayerNodeHandle localAddress, 
      TransportLayer<Identifier, ByteBuffer> tl, 
      LivenessProvider<Identifier> livenessProvider,
      ProximityProvider<Identifier> proximityProvider,
      IdFactory idFactory,
      NodeHandleFactory<Identifier> nodeHandleFactory,
      RawMessageDeserializer deserializer,
      Environment env) {
    
    this.logger = env.getLogManager().getLogger(CommonAPITransportLayerImpl.class, null);
    this.tl = tl;
    this.localAddress = localAddress;
    this.deserializer = deserializer; 
    
    if (tl == null) throw new IllegalArgumentException("tl must be non-null");
    if (localAddress == null) throw new IllegalArgumentException("localAddress must be non-null");
    if (proximityProvider == null) throw new IllegalArgumentException("proximityProvider must be non-null");
    if (livenessProvider == null) throw new IllegalArgumentException("livenessProvider must be non-null");
    if (idFactory == null) throw new IllegalArgumentException("idFactroy must be non-null");
    if (nodeHandleFactory == null) throw new IllegalArgumentException("idFactroy must be non-null");
    if (deserializer == null) throw new IllegalArgumentException("deserializer must be non-null");
    
    this.nodeHandleFactory = nodeHandleFactory;
    this.livenessProvider = livenessProvider;
    this.proximityProvider = proximityProvider;
    proximityProvider.addProximityListener(this);
    this.idFactory = idFactory;
    
    if (this.callback == null) {
      this.callback = new DefaultCallback<TransportLayerNodeHandle<Identifier>, RawMessage>(env);
    }
    if (this.errorHandler == null) {
      this.errorHandler = new DefaultErrorHandler<TransportLayerNodeHandle<Identifier>>(logger); 
    }
      
    
    tl.setCallback(this);
    livenessProvider.addLivenessListener(this);
    
    this.livenessListeners = new ArrayList<LivenessListener<TransportLayerNodeHandle<Identifier>>>();
  }
  
  public void acceptMessages(boolean b) {
    tl.acceptMessages(b);
  }

  public void acceptSockets(boolean b) {
    tl.acceptSockets(b);
  }

  public TransportLayerNodeHandle getLocalIdentifier() {
    return localAddress;
  }

  public MessageRequestHandle<TransportLayerNodeHandle<Identifier>, RawMessage> sendMessage(
      final TransportLayerNodeHandle<Identifier> i,
      RawMessage m, 
      final MessageCallback<TransportLayerNodeHandle<Identifier>, RawMessage> deliverAckToMe,
      Map<String, Integer> options) {
    // TODO Auto-generated method stub
    if (logger.level <= Logger.FINE) logger.log("sendMessage("+i+","+m+")");

    final MessageRequestHandleImpl<TransportLayerNodeHandle<Identifier>, RawMessage> handle 
      = new MessageRequestHandleImpl<TransportLayerNodeHandle<Identifier>, RawMessage>(i, m, options);
    final ByteBuffer buf;
    
    // we only serialize the Id, we assume the underlieing layer got the address of the NodeHandle correct
//    SimpleOutputBuffer sob = new SimpleOutputBuffer(4+localAddress.getId().getByteArrayLength());
    SimpleOutputBuffer sob = new SimpleOutputBuffer();
    try {
      // TODO: maybe we should write my entire address to be compatible with the lower levels, why do we need to do this at all?  
      // Is the contract that the lower level's identifier is proper?  
      // What about the fact that the priorityTL will establish end to end connectivity?  
      // Perhaps this is wasteful.
      // cant put the PriorityTL above this because this is where we serialize
      
      sob.writeLong(localAddress.getEpoch());
      localAddress.getId().serialize(sob);
      if (logger.level <= Logger.FINER) logger.log("sendMessage(): epoch:"+localAddress.getEpoch()+" id:"+localAddress.getId()+" hand:"+localAddress);
      deserializer.serialize(m, sob);
//      m.serialize(sob);
    } catch (IOException ioe) {
      if (deliverAckToMe == null) {
        errorHandler.receivedException(i, ioe);
      } else {
        deliverAckToMe.sendFailed(handle, ioe);
      }
    }
    
    buf = ByteBuffer.wrap(sob.getBytes());

    handle.setSubCancellable(tl.sendMessage(
        i.getAddress(), 
        buf, 
        new MessageCallback<Identifier, ByteBuffer>() {
    
          public void ack(MessageRequestHandle<Identifier, ByteBuffer> msg) {
            if (handle.getSubCancellable() != null && msg != handle.getSubCancellable()) throw new RuntimeException("msg != cancellable.getSubCancellable() (indicates a bug in the code) msg:"+msg+" sub:"+handle.getSubCancellable());
            if (deliverAckToMe != null) deliverAckToMe.ack(handle);
          }
        
          public void sendFailed(MessageRequestHandle<Identifier, ByteBuffer> msg, IOException ex) {
            if (handle.getSubCancellable() != null && msg != handle.getSubCancellable()) throw new RuntimeException("msg != cancellable.getSubCancellable() (indicates a bug in the code) msg:"+msg+" sub:"+handle.getSubCancellable());
            if (deliverAckToMe == null) {
              errorHandler.receivedException(i, ex);
            } else {
              deliverAckToMe.sendFailed(handle, ex);
            }
          }
        }, 
        options));
    return handle;
  }

  public void messageReceived(Identifier i, ByteBuffer m, Map<String, Integer> options) throws IOException {
    if (logger.level <= Logger.FINE) logger.log("messageReceived("+i+","+m+")");
    SimpleInputBuffer buf = new SimpleInputBuffer(m.array(), m.position());
    long epoch = buf.readLong();
    Id id = idFactory.build(buf);
    TransportLayerNodeHandle<Identifier> handle = nodeHandleFactory.getNodeHandle(i, epoch, id); 
    if (logger.level <= Logger.FINER) logger.log("messageReceived(): epoch:"+epoch+" id:"+id+" hand:"+handle);
    callback.messageReceived(handle, deserializer.deserialize(buf), options);
  }

  public void setCallback(
      TransportLayerCallback<TransportLayerNodeHandle<Identifier>, RawMessage> callback) {
    this.callback = callback;
  }

  public void setErrorHandler(ErrorHandler<TransportLayerNodeHandle<Identifier>> handler) {
    this.errorHandler = handler;
  }

  public void destroy() {
    tl.destroy();
  }

  List<LivenessListener<TransportLayerNodeHandle<Identifier>>> livenessListeners;
  public void addLivenessListener(LivenessListener<TransportLayerNodeHandle<Identifier>> name) {
    synchronized(livenessListeners) {
      livenessListeners.add(name);
    }
  }

  public boolean removeLivenessListener(LivenessListener<TransportLayerNodeHandle<Identifier>> name) {
    synchronized(livenessListeners) {
      return livenessListeners.remove(name);
    }
  }
  
  public int getLiveness(TransportLayerNodeHandle<Identifier> i, Map<String, Integer> options) {
    return livenessProvider.getLiveness(i.getAddress(), options);
  }

  public void livenessChanged(Identifier i, int val) {
    notifyLivenessListeners(nodeHandleFactory.lookupNodeHandle(i), val);    
  }
  
  private void notifyLivenessListeners(TransportLayerNodeHandle<Identifier> i, int liveness) {
    if (logger.level <= Logger.FINER) logger.log("notifyLivenessListeners("+i+","+liveness+")");
    List<LivenessListener<TransportLayerNodeHandle<Identifier>>> temp;
    synchronized(livenessListeners) {
      temp = new ArrayList<LivenessListener<TransportLayerNodeHandle<Identifier>>>(livenessListeners);
    }
    for (LivenessListener<TransportLayerNodeHandle<Identifier>> listener : temp) {
      listener.livenessChanged(i, liveness);
    }
  }
  
  Collection<ProximityListener<TransportLayerNodeHandle<Identifier>>> proxListeners = 
    new ArrayList<ProximityListener<TransportLayerNodeHandle<Identifier>>>();
  public void addProximityListener(ProximityListener<TransportLayerNodeHandle<Identifier>> name) {
    synchronized(proxListeners) {
      proxListeners.add(name);
    }
  }

  public boolean removeProximityListener(ProximityListener<TransportLayerNodeHandle<Identifier>> name) {
    synchronized(proxListeners) {
      return proxListeners.remove(name);
    }
  }
  
  public int proximity(TransportLayerNodeHandle<Identifier> i) {    
    return proximityProvider.proximity(i.getAddress());
  }

  public void proximityChanged(Identifier i, int newProx, Map<String, Integer> options) {
    notifyProximityListeners(nodeHandleFactory.lookupNodeHandle(i), newProx, options);    
  }
  
  private void notifyProximityListeners(TransportLayerNodeHandle<Identifier> i, int newProx, Map<String, Integer> options) {
    if (logger.level <= Logger.FINER) logger.log("notifyProximityListeners("+i+","+newProx+")");
    List<ProximityListener<TransportLayerNodeHandle<Identifier>>> temp;
    synchronized(proxListeners) {
      temp = new ArrayList<ProximityListener<TransportLayerNodeHandle<Identifier>>>(proxListeners);
    }
    for (ProximityListener<TransportLayerNodeHandle<Identifier>> listener : temp) {
      listener.proximityChanged(i, newProx, options);
    }
  }
  


//  List<PingListener<TransportLayerNodeHandle<Identifier>>> pingListeners;
//  public void addPingListener(PingListener<TransportLayerNodeHandle<Identifier>> name) {
//    synchronized(pingListeners) {
//      pingListeners.add(name);
//    }
//  }
//
//  public boolean removePingListener(PingListener<TransportLayerNodeHandle<Identifier>> name) {
//    synchronized(pingListeners) {
//      return pingListeners.remove(name);
//    }
//  }
//  
//  private void notifyPingListenersResponse(TransportLayerNodeHandle<Identifier> i, int rtt, Map<String, Integer> options) {
//    List<PingListener<TransportLayerNodeHandle<Identifier>>> temp;
//    synchronized(pingListeners) {
//      temp = new ArrayList<PingListener<TransportLayerNodeHandle<Identifier>>>(pingListeners);
//    }
//    for (PingListener<TransportLayerNodeHandle<Identifier>> listener : temp) {
//      listener.pingResponse(i, rtt, null);
//    }
//  }
//
//  private void notifyPingListenersReceived(TransportLayerNodeHandle<Identifier> i, Map<String, Integer> options) {
//    List<PingListener<TransportLayerNodeHandle<Identifier>>> temp;
//    synchronized(pingListeners) {
//      temp = new ArrayList<PingListener<TransportLayerNodeHandle<Identifier>>>(pingListeners);
//    }
//    for (PingListener<TransportLayerNodeHandle<Identifier>> listener : temp) {
//      listener.pingReceived(i, options);
//    }
//  }
//
//  public void pingReceived(Identifier i, Map<String, Integer> options) {
//    notifyPingListenersReceived(nodeHandleFactory.lookupNodeHandle(i), options);   
//  }
//
//  public void pingResponse(Identifier i, int rtt, Map<String, Integer> options) {
//    notifyPingListenersResponse(nodeHandleFactory.lookupNodeHandle(i), rtt, options);    
//  }

  public boolean checkLiveness(TransportLayerNodeHandle<Identifier> i, Map<String, Integer> options) {
    return livenessProvider.checkLiveness(i.getAddress(), options);
  }

//  public boolean ping(TransportLayerNodeHandle<Identifier> i, Map<String, Integer> options) {
//    return tl.ping(i.getAddress(), options);
//  }

  public SocketRequestHandle<TransportLayerNodeHandle<Identifier>> openSocket(
      final TransportLayerNodeHandle<Identifier> i,
      final SocketCallback<TransportLayerNodeHandle<Identifier>> deliverSocketToMe, 
      Map<String, Integer> options) {
    if (deliverSocketToMe == null) throw new IllegalArgumentException("deliverSocketToMe must be non-null!");

    final SocketRequestHandleImpl<TransportLayerNodeHandle<Identifier>> handle = 
      new SocketRequestHandleImpl<TransportLayerNodeHandle<Identifier>>(i, options);
    
    if (logger.level <= Logger.FINE) logger.log("openSocket("+i+")");
    
    // we only serialize the Id, we assume the underlieing layer got the address of the NodeHandle correct
    SimpleOutputBuffer sob = new SimpleOutputBuffer(8+localAddress.getId().getByteArrayLength());
    try {
      sob.writeLong(localAddress.getEpoch());
      localAddress.getId().serialize(sob);
    } catch (IOException ioe) {
      deliverSocketToMe.receiveException(handle, ioe);
      return null;
    }
    final ByteBuffer b = ByteBuffer.wrap(sob.getBytes());
    
    Identifier addr = i.getAddress();
        
    handle.setSubCancellable(tl.openSocket(addr, 
        new SocketCallback<Identifier>(){    
      
      public void receiveResult(SocketRequestHandle<Identifier> c, P2PSocket<Identifier> result) {
        if (c != handle.getSubCancellable()) throw new RuntimeException("c != cancellable.getSubCancellable() (indicates a bug in the code) c:"+c+" sub:"+handle.getSubCancellable());
        
        if (logger.level <= Logger.FINER) logger.log("openSocket("+i+"):receiveResult("+result+")");
        result.register(false, true, new P2PSocketReceiver<Identifier>() {        
          public void receiveSelectResult(P2PSocket<Identifier> socket,
              boolean canRead, boolean canWrite) throws IOException {
            if (canRead || !canWrite) throw new IOException("Expected to write! "+canRead+","+canWrite);
            
            // do the work
            socket.write(b);
            
            // keep working or pass up the new socket
            if (b.hasRemaining()) {
              socket.register(false, true, this); 
            } else {
              deliverSocketToMe.receiveResult(handle, 
                  new SocketWrapperSocket<TransportLayerNodeHandle<Identifier>, Identifier>(
                      i, socket, logger, socket.getOptions()));                 
            }
          }
        
          public void receiveException(P2PSocket<Identifier> socket,
              IOException e) {
            deliverSocketToMe.receiveException(handle, e);
          }        
        });         
      }    
      public void receiveException(SocketRequestHandle<Identifier> c, IOException exception) {
        if (c != handle.getSubCancellable()) throw new RuntimeException("c != cancellable.getSubCancellable() (indicates a bug in the code) c:"+c+" sub:"+handle.getSubCancellable());
        deliverSocketToMe.receiveException(handle, exception);
      }
    }, options));
    return handle;
  }

  public void incomingSocket(P2PSocket<Identifier> s) throws IOException {
    if (logger.level <= Logger.FINE) logger.log("incomingSocket("+s+")");

    final SocketInputBuffer sib = new SocketInputBuffer(s,1024);
    s.register(true, false, new P2PSocketReceiver<Identifier>() {
    
      public void receiveSelectResult(P2PSocket<Identifier> socket,
          boolean canRead, boolean canWrite) throws IOException {
        if (logger.level <= Logger.FINER) logger.log("incomingSocket("+socket+"):receiveSelectResult()");
        if (canWrite || !canRead) throw new IOException("Expected to read! "+canRead+","+canWrite);
        try {
          long epoch = sib.readLong();
          Id id = idFactory.build(sib);
          if (logger.level <= Logger.FINEST) logger.log("Read epoch:"+epoch+" id:"+id+" from:"+socket.getIdentifier());
          callback.incomingSocket(new SocketWrapperSocket<TransportLayerNodeHandle<Identifier>, Identifier>(
              nodeHandleFactory.getNodeHandle(socket.getIdentifier(), epoch, id), socket, logger, socket.getOptions()));
        } catch (InsufficientBytesException ibe) {
          socket.register(true, false, this); 
        } catch (IOException e) {
          errorHandler.receivedException(nodeHandleFactory.getNodeHandle(socket.getIdentifier(), 0, null), e);
        }
      }
    
      public void receiveException(P2PSocket<Identifier> socket,IOException e) {
        errorHandler.receivedException(nodeHandleFactory.getNodeHandle(socket.getIdentifier(), 0, null), e);
      }          
    });
  }


}
