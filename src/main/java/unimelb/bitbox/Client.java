package unimelb.bitbox;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Properties;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import unimelb.bitbox.util.HostPort;
import unimelb.bitbox.util.Protocol;

/**
 * @author: Xueying Wang
 */
public class Client {
    BufferedReader in;
    BufferedWriter out;
    protected Socket socket;
    private static final String RSA_FILE = "bitboxclient_rsa";
//    static {
//        try (InputStream inputStream = new FileInputStream(RSA_FLIE)) {
//            inputStream.read
//        } catch (IOException e) {
//            log.warning("Could not read file " + rsaFile);
//        }
//    }

    public static void main(String[] args) {
        Client client = new Client();
        Options options = new Options();
        options.addOption("c", true, "command");
        options.addOption("s", true, "the server's address and port number");
        options.addOption("i", true, "identity of the client");
        CommandLineParser parser = new DefaultParser();
        try {
            CommandLine cmd = parser.parse(options, args);
            String server = cmd.getOptionValue('s');
            String command = cmd.getOptionValue('c');
            String identity = cmd.getOptionValue('i');
            HostPort hostport = new HostPort(server);
            client.initConnection(hostport, identity);
            if (cmd.hasOption('p')) {
            }
        } catch (ParseException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private void initConnection(HostPort hostport, String identity) {
        try {
            socket = new Socket(hostport.host, hostport.port);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
            out.write(Protocol.CreateAuthRequest(identity));
            out.flush();
            String secretKey=in.readLine();
            System.out.println(secretKey);
        } catch (UnknownHostException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
}
