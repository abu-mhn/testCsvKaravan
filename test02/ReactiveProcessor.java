import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

@Component("ReactiveProcessor")
public class ReactiveProcessor implements Processor {

    // Create a sink for emitting events
    private final Sinks.Many<String> eventSink = Sinks.many().replay().all();

    // WebClient instance for making HTTP requests
    private final WebClient webClient;

    // Constructor to inject WebClient
    public ReactiveProcessor(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.baseUrl("http://localhost:8082").build();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        // Set the HTTP response headers for a successful SSE response
        exchange.getIn().setHeader("CamelHttpResponseCode", 200);
        exchange.getIn().setHeader("Content-Type", "text/event-stream");

        // Call the external POST API and get the response as Flux<String>
        Flux<String> eventStream = callExternalApi();

        // Convert Flux<String> to a byte array stream
        StringBuilder sb = new StringBuilder();
        eventStream.toStream().forEach(sb::append); // Collect events in a StringBuilder
        ByteArrayInputStream inputStream = new ByteArrayInputStream(sb.toString().getBytes(StandardCharsets.UTF_8));

        // Set the InputStream as the body of the exchange
        exchange.getIn().setBody(inputStream);
    }

    // Method to call the external POST API and retrieve the response as a Flux<String>
    private Flux<String> callExternalApi() {
        return webClient.post()
                .uri("/testB")
                .retrieve()
                .bodyToMono(String.class)
                .flatMapMany(response -> {
                    emitEvent(response); // Emit the response as an event
                    return getEventStream();
                });
    }

    // Method to generate a Flux stream for SSE from the sink
    public Flux<String> getEventStream() {
        return eventSink.asFlux()
                .doOnNext(item -> System.out.println("Sending event: " + item))
                .delayElements(java.time.Duration.ofSeconds(1))
                .map(item -> "data: " + item + "\n\n");
    }

    // Method to simulate event publishing
    public void emitEvent(String event) {
        eventSink.tryEmitNext(event);  // Emit a new event to the sink
    }
}
