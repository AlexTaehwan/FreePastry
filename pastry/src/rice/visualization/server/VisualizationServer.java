package rice.visualization.server;

import java.io.*;
import java.net.*;

import rice.visualization.*;
import rice.visualization.data.*;
import rice.visualization.client.*;
import rice.pastry.*;
import rice.pastry.dist.*;
import rice.pastry.leafset.*;
import rice.pastry.routing.*;

import java.awt.*;
import java.util.*;
import javax.swing.*;

public class VisualizationServer implements Runnable {
  
  protected InetSocketAddress address;
  
  protected Object[] objects;
  
  protected Vector panelCreators;
  
  protected ServerSocket server;
  
  protected PastryNode node;
  
  protected boolean willAcceptNewJars = true;
  
  protected boolean willAcceptNewRestartCommandLine = true;
  
  private String restartCommand = null;
    
  public VisualizationServer(InetSocketAddress address, PastryNode node, Object[] objects) {
    this.address = address;
    this.objects = objects;
    this.node = node;
    this.panelCreators = new Vector();
  }
  
  public void addPanelCreator(PanelCreator creator) {
    panelCreators.addElement(creator);
  }
  
  public void run() {
    try {    
      server = new ServerSocket();
      server.bind(address);

      while (true) {
        final Socket socket = server.accept();
        
        Thread t = new Thread() {
          public void run() {
            handleConnection(socket);
          }
        };
        
        t.start();
      }
    } catch (IOException e) {
      System.out.println("Server: Exception " + e + " thrown.");
    }
  }
  
  public InetSocketAddress getAddress() {
    return address;
  }
  
  protected void handleConnection(Socket socket) {
    try {
      while (true) {
        ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
        Object object = ois.readObject();
        System.out.println("Got "+object);
        
        if (object instanceof DataRequest) {
          ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
          oos.writeObject(getData());          
        } else if (object instanceof NodeHandlesRequest) {
          Hashtable handles = new Hashtable();
          
          addLeafSet(handles);
          addRoutingTable(handles);
          
          Collection collection = handles.values();
          ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
          oos.writeObject(collection.toArray(new DistNodeHandle[0]));       
        } else if (object instanceof UpdateJarRequest) {
          System.out.println("Got UpdateJarRequest");
          ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
          handleUpdateJarRequest((UpdateJarRequest)object,oos);
        }
      }
    } catch (IOException e) {
      System.out.println("Server: Exception " + e + " thrown.");
    } catch (ClassNotFoundException e) {
      System.out.println("Server: Exception " + e + " thrown.");
    }
  }

  protected void handleUpdateJarRequest(
       UpdateJarRequest req, 
       ObjectOutputStream oos)
        throws IOException {
    if (!willAcceptNewJars) {
      oos.writeObject(new UpdateJarResponse(UpdateJarResponse.FILE_COPY_NOT_ALLOWED));      
      return;
    }

    // this will most likely be an IOException
    Exception e = null;

    // try to write the files
    try {
      req.writeFiles();
    } catch (Exception e1) {
      e = e1;
      e.printStackTrace(); 
    }          
    
    UpdateJarResponse ujr;

    if (req.executeCommand != null) {   
      if (willAcceptNewRestartCommandLine) {      
        restartCommand = req.executeCommand;
        ujr = new UpdateJarResponse(e);
      } else {
        ujr = new UpdateJarResponse(e,UpdateJarResponse.NEW_EXEC_NOT_ALLOWED);        
      }
    } else {
      ujr = new UpdateJarResponse(e);      
    }
    // respond with the exception if there was one
    oos.writeObject(ujr);

    // kill the node
    ((DistPastryNode)node).kill();
  
    // sleep for a while
    try {
      Thread.sleep(req.getWaitTime());
    } catch (InterruptedException ie) {
      ie.printStackTrace();
    }    
    
    System.out.println("restarting with command:\""+restartCommand+"\"");
          
    Process p = Runtime.getRuntime().exec(restartCommand);
//    System.out.println("Process:"+p);
    System.exit(0);
    
    
  }
  
  protected void addLeafSet(Hashtable handles) {
    LeafSet leafset = node.getLeafSet();
    
    for (int i=-leafset.ccwSize(); i<=leafset.cwSize(); i++) 
      handles.put(leafset.get(i).getId(), (DistNodeHandle) leafset.get(i)); 
  }
  
  protected void addRoutingTable(Hashtable handles) {
    RoutingTable routingTable = node.getRoutingTable();
    
    for (int i=0; i>=routingTable.numRows(); i++) {
      RouteSet[] row = routingTable.getRow(i);
      
      for (int j=0; j<row.length; j++) 
        if ((row[j] != null) && (row[j].closestNode() != null))
          handles.put(row[j].closestNode().getNodeId(),  (DistNodeHandle) row[j].closestNode());
    }
  }
  
  protected Data getData() {
    Data data = new Data();
    
    for (int i=0; i<panelCreators.size(); i++) {
      PanelCreator creator = (PanelCreator) panelCreators.elementAt(i);
      DataPanel panel = creator.createPanel(objects);
      
      if (panel != null) {
        data.addDataPanel(panel);
        
        for (int j=0; j<panel.getDataViewCount(); j++) {
          panel.getDataView(j).setData(data);
        }
      }
    }
    
    return data;
  }

  /**
   * @param string
   * @param args
   */
  public void setRestartCommand(String string, String[] args) {
    restartCommand = string;
    if (args != null) {
      for (int i = 0; i < args.length; i++) {
        restartCommand = restartCommand.concat(" "+args[i]);    
      }
    }
  }

}