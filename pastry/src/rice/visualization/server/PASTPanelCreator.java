/*******************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate

Copyright 2002-2007, Rice University. Copyright 2006-2007, Max Planck Institute 
for Software Systems.  All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither the name of Rice  University (RICE), Max Planck Institute for Software 
Systems (MPI-SWS) nor the names of its contributors may be used to endorse or 
promote products derived from this software without specific prior written 
permission.

This software is provided by RICE, MPI-SWS and the contributors on an "as is" 
basis, without any representations or warranties of any kind, express or implied 
including, but not limited to, representations or warranties of 
non-infringement, merchantability or fitness for a particular purpose. In no 
event shall RICE, MPI-SWS or contributors be liable for any direct, indirect, 
incidental, special, exemplary, or consequential damages (including, but not 
limited to, procurement of substitute goods or services; loss of use, data, or 
profits; or business interruption) however caused and on any theory of 
liability, whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even if 
advised of the possibility of such damage.

*******************************************************************************/ 
package rice.visualization.server;

import rice.*;
import rice.visualization.data.*;
import rice.environment.logging.Logger;
import rice.pastry.*;
import rice.p2p.past.*;
import rice.p2p.past.gc.*;
import rice.persistence.*;
import rice.Continuation.*;
import rice.selector.*;

import java.util.*;

public class PASTPanelCreator implements PanelCreator {
  
  public static int NUM_DATA_POINTS = 600;
  public static int UPDATE_TIME = 1000;
  public static int REQUEST_UPDATE_OFFSET = 60;
  
  long count = 0;
  
  Vector times = new Vector();
  Vector times2 = new Vector();
  Vector outstanding = new Vector();
  Vector inserts = new Vector();
  Vector lookups = new Vector();
  Vector fetchHandles = new Vector();
  Vector others = new Vector();
  
  protected PastImpl past;
  protected String name;
  
  /**
   * Lazilly constructed.
   */
  protected Logger logger;  
  
  public PASTPanelCreator(rice.selector.Timer timer, String name, PastImpl past) {
    this.past = past;
    this.name = name;
    
    timer.scheduleAtFixedRate(new rice.selector.TimerTask() {
      public void run() {
        updateData();
      }
    }, 0, UPDATE_TIME);
  }
  
  public DataPanel createPanel(Object[] objects) {
    DataPanel pastPanel = new DataPanel(name + " PAST");
    
    Constraints pastCons2 = new Constraints();
    pastCons2.gridx = 0;
    pastCons2.gridy = 0;
    pastCons2.fill = Constraints.HORIZONTAL;
    
    TableView pastView2 = new TableView(name + " Outstanding Messages", 380, 200, pastCons2);
    pastView2.setSizes(new int[] {350});

    Continuation[] outstanding = past.getOutstandingMessages();
    
    for (int i=0; i<outstanding.length; i++)     
      pastView2.addRow(new String[] {outstanding[i].toString()});
       
    pastPanel.addDataView(pastView2);
    
    try {      
      Constraints dataStorageCons = new Constraints();
      dataStorageCons.gridx = 1;
      dataStorageCons.gridy = 0;
      dataStorageCons.fill = Constraints.HORIZONTAL;
      
      LineGraphView dataStorageView = new LineGraphView(name + " Outstanding", 380, 200, dataStorageCons, "Time (m)", "Outstanding", false, false);
      dataStorageView.addSeries("Insert", getTimeArray(), getArray(this.outstanding), Color.red);
      
      pastPanel.addDataView(dataStorageView);
    } catch (Exception e) {
      if (logger == null) logger = past.getEnvironment().getLogManager().getLogger(PASTPanelCreator.class, null);
      if (logger.level <= Logger.SEVERE) logger.logException(
          "",e);
    }
    
    try {      
      Constraints dataStorageCons = new Constraints();
      dataStorageCons.gridx = 2;
      dataStorageCons.gridy = 0;
      dataStorageCons.fill = Constraints.HORIZONTAL;
      
      LineGraphView dataStorageView = new LineGraphView(name + " Requests", 380, 200, dataStorageCons, "Time (m)", "Count", false, true);
      dataStorageView.addSeries("Insert", getTimeArray2(), getArray(inserts), Color.blue);
      dataStorageView.addSeries("Lookup", getTimeArray2(), getArray(lookups), Color.red);
      dataStorageView.addSeries("Fetch Handle", getTimeArray2(), getArray(fetchHandles), Color.green);
      dataStorageView.addSeries("Refresh", getTimeArray2(), getArray(others), Color.black);
      
      pastPanel.addDataView(dataStorageView);
    } catch (Exception e) {
      if (logger == null) logger = past.getEnvironment().getLogManager().getLogger(PASTPanelCreator.class, null);
      if (logger.level <= Logger.SEVERE) logger.logException(
          "",e);
    }
        
    return pastPanel;
  }
  
  protected synchronized double[] getTimeArray2() {
    if (times2.size() > 0) {
      double[] timesA = new double[times2.size()];
      long offset = ((Long) times2.elementAt(0)).longValue();
      
      for (int i=0; i<timesA.length; i++) 
        timesA[i] = (double) ((((Long) times2.elementAt(i)).longValue() - offset) / UPDATE_TIME);
      
      return timesA;
    } else {
      return new double[0];
    }
  }
  
  protected synchronized double[] getTimeArray() {
    if (times.size() > 0) {
      double[] timesA = new double[times.size()];
      long offset = ((Long) times.elementAt(0)).longValue();
      
      for (int i=0; i<timesA.length; i++) 
        timesA[i] = (double) ((((Long) times.elementAt(i)).longValue() - offset) / UPDATE_TIME);
      
      return timesA;
    } else {
      return new double[0];
    }
  }
  
  protected synchronized double[] getArray(Vector vector) {
    if (vector.size() > 0) {
      double[] dataA = new double[vector.size()];
      
      for (int i=0; i<dataA.length; i++) 
        dataA[i] = ((Double) vector.elementAt(i)).doubleValue();
      
      return dataA;
    } else {
      return new double[0];
    }
  }
  
  protected synchronized void updateData() {
    try {
      times.add(new Long(past.getEnvironment().getTimeSource().currentTimeMillis()));
      outstanding.add(new Double((double) past.getOutstandingMessages().length));

      if (count % REQUEST_UPDATE_OFFSET == 0) {
        times2.add(new Long(past.getEnvironment().getTimeSource().currentTimeMillis()));
        inserts.add(new Double((double) past.inserts));
        lookups.add(new Double((double) past.lookups));
        fetchHandles.add(new Double((double) past.fetchHandles));
        others.add(new Double((double) past.other));
      }
      
      count++;
      
      
      past.inserts = 0;
      past.lookups = 0;
      past.fetchHandles = 0;
      past.other = 0;
      
      if (outstanding.size() > NUM_DATA_POINTS) {
        outstanding.removeElementAt(0);
        times.removeElementAt(0);
      }
      
      if (inserts.size() > NUM_DATA_POINTS) {
        times2.removeElementAt(0);
        inserts.removeElementAt(0);
        lookups.removeElementAt(0);
        fetchHandles.removeElementAt(0);
        others.removeElementAt(0);
      }
    } catch (Exception e) {
      if (logger == null) logger = past.getEnvironment().getLogManager().getLogger(PASTPanelCreator.class, null);
      if (logger.level <= Logger.SEVERE) logger.logException(
          "",e);
    }
  }
}
