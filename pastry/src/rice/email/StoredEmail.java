package rice.email;

import java.io.*;
import java.util.*;

import rice.*;
import rice.email.log.*;
import rice.email.messaging.*;
import rice.post.*;
import rice.post.log.*;
import rice.post.messaging.*;
import rice.post.storage.*;

/**
 * Represents the notion of a stored email: it contains the metadata(int UID),
 * the email and the Flags.
 *
 * @version   $Id$
 * @author    amislove
 */
public class StoredEmail implements Serializable, Comparable {

  Email _email;
  int _uid;
  Flags _flags;

  /**
   * Constructs a stored email
   *
   * @param email  The email we are dealing with.
   * @param uid    The unique UID for the email.
   * @param flags  The flags on the email.
   */
  public StoredEmail(Email email, int uid, Flags flags) {
    _uid = uid;
    _email = email;
    _flags = flags;
  }

  /**
   * Return the UID for the current email
   *
   * @return   The UID for the email
   */
  public int getUID() {
    return _uid;
  }

  /**
   * Return the flags for the email
   *
   * @return   The Flags for the email. //--Or do we want a string
   *      representation instead of the class?
   */
  public Flags getFlags() {
    return _flags;
  }

  /**
   * Return the email
   *
   * @return   The Email.
   */
  public Email getEmail() {
    return _email;
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @param o  DESCRIBE THE PARAMETER
   * @return   DESCRIBE THE RETURN VALUE
   */
  public boolean equals(Object o) {
    if (!(o instanceof StoredEmail)) {
      return false;
    } else {
      return ((StoredEmail) o).getEmail().equals(_email);
    }
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @param o  DESCRIBE THE PARAMETER
   * @return   DESCRIBE THE RETURN VALUE
   */
  public int compareTo(Object o) {
    StoredEmail email = (StoredEmail) o;
    return new Integer(getUID()).compareTo(new Integer(email.getUID()));
  }
}

