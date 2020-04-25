package qa;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.util.Properties;

/**
 * Written By Pranshu Jain
 */
public class SshTunnel {
    private static final String SSH_CONFIG_KEY = "StrictHostKeyChecking";
    private static final String SSH_CONFIG_VALUE = "no";
    private static final String SSH_DEFAULT_RSA_LOCATION = "/home/" + System.getProperty("user.name") + "/.ssh/id_rsa";
    private static final String TUNNEL_CONNECT_SUCCESS_MESSAGE = "Tunnel is Connected";
    private static final String TUNNEL_CONNECT_FAILURE_MESSAGE = "Tunnel Connection Failed";
    private static final String PROPERTIES_USER_KEY = "user";
    private static final String PROPERTIES_IP_KEY = "host";
    private static final String PROPERTIES_PORT_KEY = "port";
    private static final String PROPERTIES_RSA_LOCATION_KEY = "rsaKeyLocation";
    private static final String DEFAULT_PROPERTIES_FILE_NAME = "config.properties";

    private Logger log = LoggerFactory.getLogger(this.getClass());

    private String bastionUsername;
    private String bastionPublicIP;
    private int bastionSshPort;
    private String rsaKeyLocation;
    private int localPort;
    private int remotePort;
    private String remoteHost;

    /**
     * This constructor will read properties from properties file and prepare variables which can be used while tunnel is being created.
     * Properties file should have below parameters :
     * ----------------------------
     * user=unix-abc
     * host=abc.us3.doit.in
     * port=22000
     * rsaKeyLocation=
     * ----------------------------
     * <p>
     * rsaKeyLocation should be kept empty unless your RSA key location is not as below :
     * /home/<userName>/.ssh/id_rsa
     * <p>
     * User needs to provide login details to bastion which they can find under .ssh/config
     *
     * @param propertiesFileName <config.properties>
     * @throws Exception
     */
    public SshTunnel(String propertiesFileName) throws Exception {
        if (propertiesFileName == null) {
            propertiesFileName = DEFAULT_PROPERTIES_FILE_NAME;
        }
        InputStream input = SshTunnel.class.getClassLoader().getResourceAsStream(propertiesFileName);
        Properties properties = new Properties();
        properties.load(input);

        this.bastionUsername = properties.getProperty(PROPERTIES_USER_KEY);
        this.bastionPublicIP = properties.getProperty(PROPERTIES_IP_KEY);
        this.bastionSshPort = Integer.parseInt(properties.getProperty(PROPERTIES_PORT_KEY));
        this.rsaKeyLocation = properties.getProperty(PROPERTIES_RSA_LOCATION_KEY);

        log.info(SSH_DEFAULT_RSA_LOCATION);
    }

    public SshTunnel() throws Exception {
        this(null);
    }

    /**
     * This method will create a tunnel with parameters provided by user in the config.properties file.
     * If port is not available to bind it will throw "Unable to bind port" expection which means you have to you a
     * different local port.
     *
     * @param localPort
     * @param remotePort
     * @param remoteHost
     * @return Jsch Session Object
     */
    public Session createTunnel(int localPort, int remotePort, String remoteHost) throws Exception {
        this.localPort = localPort;
        this.remoteHost = remoteHost;
        this.remotePort = remotePort;
        JSch jSch = new JSch();
        if (rsaKeyLocation == null) {
            rsaKeyLocation = SSH_DEFAULT_RSA_LOCATION;
        } else if (rsaKeyLocation.isEmpty()) {
            rsaKeyLocation = SSH_DEFAULT_RSA_LOCATION;
        }
        Session session = jSch.getSession(bastionUsername, bastionPublicIP, bastionSshPort);
        jSch.addIdentity(rsaKeyLocation);
        java.util.Properties config = new java.util.Properties();
        config.put(SSH_CONFIG_KEY, SSH_CONFIG_VALUE);
        session.setConfig(config);
        session.connect();
        if (session.isConnected()) {
            session.setPortForwardingL(localPort, remoteHost, remotePort);
            session.sendKeepAliveMsg();
            log.info(TUNNEL_CONNECT_SUCCESS_MESSAGE);
        } else {
            log.error(TUNNEL_CONNECT_FAILURE_MESSAGE);
        }
        return session;
    }

    /**
     * This method will check if that tunnel is still active or not and return true/false accordingly.
     * @return true/false
     */
    public boolean isTunnelConnected() {
        Socket socket;
        try {
            socket = new Socket("127.0.0.1", this.localPort);
            System.out.println(socket.isClosed());
            if (socket.isConnected() == true) {
                socket.close();
                return true;
            }
        } catch (IOException e) {
            return false;
        }
        return false;
    }

    /**
     * This method will check if existing tunnel is connected or not, if not connected it will try to connect again.
     * if tunnel is already connected this method will return true.
     * if tunnel is not connected it will try to connect and if successful it will return true else false.
     * @return true/false
     */
    public boolean autoTunnelReconnect() {
        if (isTunnelConnected() != true) {
            try {
                this.createTunnel(this.localPort, this.remotePort, this.remoteHost);
                return true;
            } catch (Exception e) {
                return false;
            }
        } else {
            return true;
        }
    }
}
