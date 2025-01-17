package rice.pastry.secureconnection.test;

import java.net.InetAddress;
import rice.environment.Environment;
import rice.pastry.NodeIdFactory;

/**
 *
 * @author Luboš Mátl
 */
public class CollisionSecureIdConnector extends SecureIdConnector {

    @Override
    protected NodeIdFactory createNodeIdFactory(InetAddress localIP, int port, Environment env) {
        return new ColisionNodeIdFactory(env);
    }
    
    
}
