package rice.email;

import java.util.*;

/**
 * Represents a notion of a folder in the email service.
 */
public class Folder {
  // name of the folder
  private String _name;
  // the emails stored in the folder
  private Vector _contents;
    
  /**
   * Constructs an Folder.
   *
   * @param name The name of the folder.
   */
  public Folder(String name) {
    _name = name;
    _contents = new Vector();
  }
    
  /**
   * Returns the name of this folder
   * 
   * @return The name of the folder
   */
  public String getName() {
    return _name;
  }
        
  /**
   * Returns the Emails contained in this Folder.
   *
   * @return the stored Emails
   */
  public Email[] getContents() {
    return (Email[])_contents.toArray();
  }
}
