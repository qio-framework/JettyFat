/* Mortbay */
package fat;

import org.apache.tomcat.InstanceManager;
import org.apache.tomcat.SimpleInstanceManager;
import org.apache.tomcat.util.scan.StandardJarScanFilter;
import org.apache.tomcat.util.scan.StandardJarScanner;
import org.eclipse.jetty.apache.jsp.JettyJasperInitializer;
import org.eclipse.jetty.jsp.JettyJspServlet;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.component.AbstractLifeCycle;
import qio.HttpMediator;
import qio.Qio;
import qio.support.EventsListener;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Enumeration;
import java.util.jar.JarEntry;


public class JettyServer {

    private static final String WEBROOT = "/src/main/webapp/";

    private Server server;

    public static void main(String[] args) throws Exception {
        JettyServer jettyServer = new JettyServer();
        jettyServer.start();
        jettyServer.standby();
    }

    public void start() throws Exception {

        server = new Server();

        org.eclipse.jetty.server.ServerConnector connector = new ServerConnector(server, -1, -1);
        connector.setPort(3001);
        server.addConnector(connector);

        URI baseUri = getWebRootResourceUri();

        ServletContextHandler servletContextHandler = new ServletContextHandler(ServletContextHandler.SESSIONS);
        servletContextHandler.addEventListener(new EventsListener());
        servletContextHandler.setContextPath("/");
        servletContextHandler.setResourceBase(baseUri.toASCIIString());

        enableEmbeddedJspSupport(servletContextHandler);

        addWebappJsps(servletContextHandler);

        ServletHolder mediatorServlet = new ServletHolder("httpMediator", HttpMediator.class);
        servletContextHandler.addServlet(mediatorServlet, "/*");

        ServletHolder holderDefault = new ServletHolder("default", DefaultServlet.class);
        holderDefault.setInitParameter("resourceBase", baseUri.toASCIIString());
        holderDefault.setInitParameter("dirAllowed", "true");
        servletContextHandler.addServlet(holderDefault, "/");

        server.setHandler(servletContextHandler);

        server.start();
    }

    protected void addWebappJsps(ServletContextHandler servletContextHandler){
        Enumeration<JarEntry> entries = Qio.getEntries();
        do {

            JarEntry jarEntry = entries.nextElement();
            if(jarEntry.toString().contains("src/main/webapp/") &&
                    jarEntry.toString().endsWith(".jsp")){

                String[] bits = jarEntry.toString().split("src/main/webapp");
                String path = bits[1];

                ServletHolder holderAltMapping = new ServletHolder();
                holderAltMapping.setName(path);
                holderAltMapping.setForcedPath(path);

                servletContextHandler.addServlet(holderAltMapping, path);

            }

        }while(entries.hasMoreElements());

    }

    private void enableEmbeddedJspSupport(ServletContextHandler servletContextHandler) throws IOException {

        File tempDir = new File(System.getProperty("java.io.tmpdir"));
        File scratchDir = new File(tempDir.toString(), "embedded-jetty-jsp");

        if (!scratchDir.exists()) {
            if (!scratchDir.mkdirs()) {
                throw new IOException("Unable to create scratch directory: " + scratchDir);
            }
        }

        servletContextHandler.setAttribute("javax.servlet.context.tempdir", scratchDir);

        ClassLoader jspClassLoader = new URLClassLoader(new URL[0], this.getClass().getClassLoader());
        servletContextHandler.setClassLoader(jspClassLoader);

        servletContextHandler.addBean(new JettyStarter(servletContextHandler));

        ServletHolder holderJsp = new ServletHolder("jsp", JettyJspServlet.class);
        holderJsp.setInitOrder(0);
        holderJsp.setInitParameter("scratchdir", scratchDir.toString());
        holderJsp.setInitParameter("logVerbosityLevel", "DEBUG");
        holderJsp.setInitParameter("fork", "false");
        holderJsp.setInitParameter("xpoweredBy", "false");
        holderJsp.setInitParameter("compilerTargetVM", "1.8");
        holderJsp.setInitParameter("compilerSourceVM", "1.8");
        holderJsp.setInitParameter("keepgenerated", "true");
        servletContextHandler.addServlet(holderJsp, "*.jsp");

        servletContextHandler.setAttribute(InstanceManager.class.getName(), new SimpleInstanceManager());
    }

    private URI getWebRootResourceUri() throws FileNotFoundException, URISyntaxException {
        URL uri = this.getClass().getResource(WEBROOT);
        if (uri == null) throw new FileNotFoundException("Unable to find resource " + WEBROOT);

        return uri.toURI();
    }

    public void stop() throws Exception {
        server.stop();
    }

    public void standby() throws InterruptedException {
        server.join();
    }






    public class JettyStarter extends AbstractLifeCycle {

        JettyJasperInitializer sci;
        ServletContextHandler context;

        public JettyStarter(ServletContextHandler context) {
            this.sci = new JettyJasperInitializer();
            this.context = context;

            StandardJarScanner jarScanner = new StandardJarScanner();
            StandardJarScanFilter jarScanFilter = new StandardJarScanFilter();

            jarScanFilter.setTldScan("taglibs-standard-impl-*");
            jarScanFilter.setTldSkip("apache-*,ecj-*,jetty-*,asm-*,javax.servlet-*,javax.annotation-*,taglibs-standard-spec-*");
            jarScanner.setJarScanFilter(jarScanFilter);

            this.context.setAttribute("org.apache.tomcat.JarScanner", jarScanner);
        }

        @Override
        protected void doStart() throws Exception {
            ClassLoader old = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(context.getClassLoader());
            try {
                sci.onStartup(null, context.getServletContext());
                super.doStart();
            }finally {
                Thread.currentThread().setContextClassLoader(old);
            }
        }
    }

}
