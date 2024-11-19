import org.apache.camel.Exchange;
import org.apache.camel.Processor;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import org.springframework.stereotype.Component;

@Component("ConvoyPost")
public class ConvoyPost implements Processor {

    @Override
    public void process(Exchange exchange) throws Exception {
        // Retrieve the payload from the incoming message
        String jsonPayload = exchange.getIn().getBody(String.class);

        // Define the API URL
        String apiUrl = "http://5.181.132.121:5005/ingest/MNKbFrVkti99jCia";

        try {
            // Create a URL object
            URL url = new URL(apiUrl);

            // Open a connection
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            // Set the request method to POST
            connection.setRequestMethod("POST");

            // Set headers
            connection.setRequestProperty("Content-Type", "application/json; utf-8");
            connection.setRequestProperty("Accept", "application/json");

            // Enable writing to the connection output stream
            connection.setDoOutput(true);

            // Write the JSON payload to the output stream
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonPayload.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            // Get the response code
            int responseCode = connection.getResponseCode();

            // Read the response body
            StringBuilder response = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(
                            connection.getInputStream(), "utf-8"))) {
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
            }

            // Log and set the response body in the exchange
            String responseMessage = response.toString();
            System.out.println(responseMessage);
            exchange.getMessage().setBody(responseMessage);

            // Close the connection
            connection.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
            exchange.getMessage().setBody("Error: " + e.getMessage());
        }
    }
}
