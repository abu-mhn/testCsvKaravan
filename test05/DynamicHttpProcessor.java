import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component("DynamicHttpProcessor")
public class DynamicHttpProcessor implements Processor {

    private static final Logger LOGGER = LoggerFactory.getLogger(DynamicHttpProcessor.class);

    @Override
    public void process(Exchange exchange) throws Exception {
        // Get the body and event coding from the exchange properties
        String bodyRequest = exchange.getProperty("bodyRequest", String.class);
        String eventCoding = exchange.getProperty("eventCoding", String.class);
        if (bodyRequest == null || eventCoding == null) {
            LOGGER.error("Required properties 'bodyRequest' or 'eventCoding' are missing.");
            return;
        }

        // Set up HTTP POST request
        String url = "http://103.187.26.142:4513";
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(url);
            httpPost.setHeader("Accept", "application/json");
            httpPost.setHeader("Content-Type", "application/json");

            // Set the body of the request
            httpPost.setEntity(new StringEntity(bodyRequest));

            // Execute the request
            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                if (response.getEntity() != null) {
                    String responseBody = EntityUtils.toString(response.getEntity());
                    exchange.getIn().setBody(responseBody);
                } else {
                    LOGGER.warn("Response entity is null for URL: {}", url);
                    exchange.getIn().setBody(null);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error occurred while sending HTTP POST request to URL: {}", url, e);
            throw e;
        }
        
        // Clean up headers and set Kafka key if present
        exchange.getIn().getHeaders().clear();
        String kafkaKey = exchange.getProperty("global.kafka-key", String.class);
        if (kafkaKey != null) {
            exchange.getIn().setHeader("kafka.KEY", kafkaKey);
        }
    }
}
