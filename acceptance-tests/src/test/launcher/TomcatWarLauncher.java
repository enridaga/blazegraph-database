import java.io.File;

import org.apache.catalina.startup.Tomcat;

/**
 * Deploys a WAR in an embedded Apache Tomcat 9 (javax.servlet) and blocks.
 * <p>
 * Run in Java source-file mode, in its own JVM, with only Tomcat on the class
 * path, so that the container is fully independent of the WAR under test:
 *
 * <pre>
 * java -cp 'tomcat/*' TomcatWarLauncher.java WAR PORT CONTEXT_PATH BASE_DIR
 * </pre>
 */
public class TomcatWarLauncher {

    public static void main(String[] args) throws Exception {
        if (args.length != 4) {
            System.err.println("usage: TomcatWarLauncher WAR PORT CONTEXT_PATH BASE_DIR");
            System.exit(2);
        }
        final File war = new File(args[0]).getAbsoluteFile();
        final int port = Integer.parseInt(args[1]);
        final String contextPath = args[2];
        final File baseDir = new File(args[3]).getAbsoluteFile();

        final Tomcat tomcat = new Tomcat();
        tomcat.setBaseDir(baseDir.getPath());
        tomcat.setPort(port);
        tomcat.getConnector(); // creates the default HTTP connector
        tomcat.getHost().setAppBase(baseDir.getPath());
        tomcat.addWebapp(contextPath, war.getPath());
        tomcat.start();
        System.out.println("Tomcat started on port " + port + ", context " + contextPath);
        tomcat.getServer().await();
    }
}
