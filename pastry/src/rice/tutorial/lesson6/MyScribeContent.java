/*************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate 

Copyright 2002, Rice University. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither  the name  of Rice  University (RICE) nor  the names  of its
contributors may be  used to endorse or promote  products derived from
this software without specific prior written permission.

This software is provided by RICE and the contributors on an "as is"
basis, without any representations or warranties of any kind, express
or implied including, but not limited to, representations or
warranties of non-infringement, merchantability or fitness for a
particular purpose. In no event shall RICE or contributors be liable
for any direct, indirect, incidental, special, exemplary, or
consequential damages (including, but not limited to, procurement of
substitute goods or services; loss of use, data, or profits; or
business interruption) however caused and on any theory of liability,
whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even
if advised of the possibility of such damage.

********************************************************************************/
/*
 * Created on May 4, 2005
 */
package rice.tutorial.lesson6;

import rice.p2p.commonapi.NodeHandle;
import rice.p2p.scribe.ScribeContent;

/**
 * @author Jeff Hoye
 */
public class MyScribeContent implements ScribeContent {
  /**
   * The source of this content.
   */
  NodeHandle from;
  
  /**
   * The sequence number of the content.
   */
  int seq;
  
  /**
   * Simple constructor.  Typically, you would also like some
   * interesting payload for your application.
   * 
   * @param from Who sent the message.
   * @param seq the sequence number of this content.
   */
  public MyScribeContent(NodeHandle from, int seq) {
    this.from = from;
    this.seq = seq;
  }

  /**
   * Ye ol' toString() 
   */
  public String toString() {
    return "MyScribeContent #"+seq+" from "+from;
  }  
}
