import service.Server;
import org.apache.log4j.BasicConfigurator;

public class Main {

    public static void main(String[] args) {
        // I add it this line to make the web socket run without getting err
        BasicConfigurator.configure();
        // Clean up in case of external shut down
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                System.out.println("Shutdown hook running");
                Server.getInstance().close();
            }
        }));

        Server.getInstance().launch();
    }
}
