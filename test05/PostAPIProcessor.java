import org.apache.camel.Exchange;
import org.apache.camel.Processor;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import org.springframework.stereotype.Component;

@Component("PostAPIProcessor")
public class PostAPIProcessor implements Processor {

    @Override
    public void process(Exchange exchange) throws Exception {
        String urlString = "http://103.187.26.142:4513"; // The API endpoint
        String jsonInputString = "{ \"key1\": \"value1\", \"key2\": \"value2\" }"; // Replace with your JSON payload

        // Retrieve input from exchange body if required
        // jsonInputString = exchange.getIn().getBody(String.class);

        try {
            // Create a URL object
            URL url = new URL(urlString);

            // Open a connection to the URL
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            // Configure the connection for a POST request
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; utf-8");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true); // Allows sending data in the request body

            // Write the JSON input string to the request body
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonInputString.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            // Read the response
            int responseCode = conn.getResponseCode();
            System.out.println("Response Code: " + responseCode);

            if (responseCode == HttpURLConnection.HTTP_OK) {
                System.out.println("Request was successful.");
                exchange.getMessage().setBody("Request successful, Response Code: " + responseCode);
            } else {
                System.out.println("Request failed. Response Code: " + responseCode);
                exchange.getMessage().setBody("Request failed, Response Code: " + responseCode);
            }

            // Disconnect the connection
            conn.disconnect();

        } catch (Exception e) {
            e.printStackTrace();
            // Set exception details to the exchange for downstream handling
            exchange.getMessage().setBody("Request failed: " + e.getMessage());
        }
    }
}
