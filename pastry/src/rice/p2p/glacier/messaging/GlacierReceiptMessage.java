package rice.p2p.glacier.messaging;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.glacier.*;

/**
 * DESCRIBE THE CLASS
 *
 * @version $Id$
 * @author ahae
 */
public class GlacierReceiptMessage extends GlacierMessage {
  /**
   * DESCRIBE THE FIELD
   */
  protected FragmentKey key;

  /**
   * Constructor for GlacierReceiptMessage.
   *
   * @param uid DESCRIBE THE PARAMETER
   * @param key DESCRIBE THE PARAMETER
   * @param source DESCRIBE THE PARAMETER
   * @param dest DESCRIBE THE PARAMETER
   */
  public GlacierReceiptMessage(int uid, FragmentKey key, NodeHandle source, Id dest) {
    super(uid, source, dest);

    this.key = key;
  }

  /**
   * Gets the Key attribute of the GlacierReceiptMessage object
   *
   * @return The Key value
   */
  public FragmentKey getKey() {
    return key;
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @return DESCRIBE THE RETURN VALUE
   */
  public String toString() {
    return "[GlacierReceipt for " + key + "]";
  }
}

