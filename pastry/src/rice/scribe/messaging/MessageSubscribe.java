/*************************************************************************

"Free Pastry" Peer-to-Peer Application Development Substrate 

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


package rice.scribe.messaging;

import rice.pastry.*;
import rice.pastry.security.*;
import rice.pastry.routing.*;
import rice.pastry.messaging.*;
import rice.scribe.*;
import rice.scribe.security.*;
import rice.scribe.maintenance.*;

import java.io.*;

/**
 *
 * MessageSubscribe is used whenever a Scribe node wants to subscribe itself 
 * to a topic.
 * 
 * @version $Id$ 
 * 
 * @author Romer Gil 
 * @author Eric Engineer
 */


public class MessageSubscribe extends ScribeMessage implements Serializable
{
    /**
     * Constructor
     *
     * @param addr the address of the scribe receiver.
     * @param source the node generating the message.
     * @param tid the topic to which this message refers to.
     * @param c the credentials associated with the mesasge.
     */
    public 
	MessageSubscribe( Address addr, NodeHandle source, 
			  NodeId tid, Credentials c) {
	super( addr, source, tid, c );
    }
    
    /**
     * This method is called whenever the scribe node receives a message for 
     * itself and wants to process it. The processing is delegated by scribe 
     * to the message.
     * 
     * @param scribe the scribe application.
     * @param topic the topic within the scribe application.
     */
    public void 
	handleDeliverMessage( Scribe scribe, Topic topic ) {

	// We need not do anything here, because handleForwardMessage()
	// is always executed first, even on the destination 
	// node (the node closest to topicId).

    }
    
    /**
     * This method is called whenever the scribe node forwards a message in 
     * the scribe network. The processing is delegated by scribe to the 
     * message.
     * 
     * @param scribe the scribe application.
     * @param topic the topic within the scribe application.
     *
     * @return true if the message should be routed further, false otherwise.
     */
    public boolean 
	handleForwardMessage(Scribe scribe, Topic topic ) {
	NodeId topicId = m_topicId;
	NodeHandle nhandle = m_source;
	Credentials cred = scribe.getCredentials();
	SendOptions opt = scribe.getSendOptions();
	
	if( !scribe.getSecurityManager().
	    verifyCanSubscribe( m_source, m_topicId ) ) {

	    //bad permissions from source node
	    return false;
	}

	if( m_source.getNodeId().equals( scribe.getNodeId() ) ) {
	    return true;
	}
	else {
	}
	
	if ( topic == null ) {
	    topic = new Topic( topicId, scribe );
	    
	    // add topic to known topics
	    topic.addToScribe();

	    ScribeMessage msg = scribe.makeSubscribeMessage( m_topicId, cred);
	    
	    topic.postponeParentHandler();

	    // pass the application specific data along the path
	    // toward the root.
	    msg.setData((Serializable)this.getData());

	    // join multicast tree by routing subscribe message thru pastry
	    scribe.routeMsg( m_topicId, msg, cred, opt );

	    // notify scribeObservers of this event, that a topic
	    // was created.
	    scribe.notifyScribeObservers(m_topicId);
	}
	
	// make the source a child for this topic
	topic.addChild( nhandle, this );

	// Inform all interested applications
	/*
	IScribeApp[] apps = topic.getApps();
	for ( int i=0; i<apps.length; i++ ) {
	    apps[i].subscribeHandler( this, m_topicId, nhandle, true );
	}
	*/
	// stop routing the original message
	return false;
    }

    public String toString() {
	return new String( "SUBSCRIBE MSG:" + m_source);
    }
}



