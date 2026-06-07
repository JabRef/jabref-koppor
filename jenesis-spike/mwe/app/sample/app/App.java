package sample.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sample.greeter.Greeter;

public final class App {

    private static final Logger LOG = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) {
        String name = args.length > 0 ? String.join(" ", args) : "world";
        String message = new Greeter().greeting(name);
        LOG.info("{}", message);
        System.out.println(message);
    }
}
