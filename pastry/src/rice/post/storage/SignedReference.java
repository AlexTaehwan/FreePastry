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
package rice.post.storage;

import java.security.*;
import java.io.*;

import rice.p2p.commonapi.*;
import rice.p2p.past.*;

/**
* This class serves as a reference to a PostObject
 * stored in the Post system.  This class knows both the
 * location in the network and the encryption key of the
 * corresponding PostData object.
 * 
 * @version $Id$
 */
public class SignedReference implements Serializable {
  public static final short TYPE = 7;

  // serialver for backwards compatibility
  private static final long serialVersionUID = -6793773589264272354L;
  
  /**
   * Location where this data is stored in PAST.
   */
  private Id location;

  /**
   * Contructs a PostDataReference object given
   * the address of the object.
   *
   * @param location The location in PAST of the PostData object
   */
  public SignedReference(Id location) {
    this.location = location;
  }

  /**
   * @return The location of the data referenced by this object
   */
  public Id getLocation() {
    return location;
  }
  
  public int hashCode() {
    return location.hashCode();
  }

  public boolean equals(Object o) {
    if (! (o instanceof SignedReference))
      return false;

    SignedReference ref = (SignedReference) o;

    return location.equals(ref.getLocation());
  }

  public String toString() {
    return "[SignedRef " + location + "]";
  }
}
