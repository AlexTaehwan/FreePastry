package rice.visualization.server;

import rice.visualization.data.*;
import rice.pastry.*;
import rice.p2p.past.*;
import rice.p2p.past.gc.*;
import rice.persistence.*;
import rice.Continuation.*;
import rice.selector.*;

import java.awt.*;
import java.util.*;

public class MultiPersistencePanelCreator implements PanelCreator {
  
  PersistencePanelCreator[] creators;
  
  public MultiPersistencePanelCreator(rice.selector.Timer timer, String[] names, StorageManagerImpl[] storages) {
    creators = new PersistencePanelCreator[names.length];
    
    for (int i=0; i<names.length; i++)
      creators[i] = new PersistencePanelCreator(timer, names[i], storages[i]);
  }
  
  public DataPanel createPanel(Object[] objects) {
    MultiDataPanel panel = new MultiDataPanel("Persistence");
    
    for (int i=0; i<creators.length; i++)
      panel.addDataPanel(creators[i].createPanel(objects));
    
    return panel;
  }
}