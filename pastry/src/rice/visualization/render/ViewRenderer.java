package rice.visualization.render;

import java.awt.*;
import java.awt.image.*;
import java.util.*;
import javax.swing.*;

import rice.visualization.*;
import rice.visualization.data.*;

public abstract class ViewRenderer {
  
  protected Visualization visualization;
  
  public ViewRenderer (Visualization visualization) {
    this.visualization = visualization;
  }
  
  public abstract boolean canRender(DataView view);
  
  public abstract JPanel render(DataView view);
  
  protected Font getDefaultFont() {
    return new Font("Courier", Font.PLAIN, 10);
  }

  protected Font getDefaultSmallFont() {
    return new Font("Courier", Font.PLAIN, 9);
  }
  
  protected Color getDefaultFontColor() {
    return Color.black;
  }
  
  protected Color getDefaultBackgroundColor() {
    return Color.gray;
  }
  
  protected int getDefaultBorder() {
    return 15;
  }
}