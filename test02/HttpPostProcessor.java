import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.main.Main;
import org.springframework.stereotype.Component;

@Component("HttpPostProcessor")
public class HttpPostProcessor implements Processor {

    @Override
    public void process(Exchange exchange) throws Exception {
        // Retrieve the existing body from the exchange payload
        String requestBody = exchange.getIn().getBody(String.class);
        
        // You can modify the requestBody if needed
        // For example, adding headers or appending data to the body content
        exchange.getIn().setBody(requestBody);
    }

    public static void main(String[] args) throws Exception {
        Main main = new Main();
        main.configure().addRoutesBuilder(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // Define the route to post to the specified endpoint
                from("direct:start")
                    .process(new HttpPostProcessor())
                    .to("http://localhost:8082/testA");
            }
        });
        main.run(args);
    }
}
