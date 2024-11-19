package com.example.processor;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

@Component("HttpPostProcessor")
public class HttpPostProcessor implements Processor {

    @Override
    public void process(Exchange exchange) throws Exception {
        // Get the input message body from the exchange
        String jsonPayload = exchange.getIn().getBody(String.class);

        // Target URL
        String targetUrl = "http://103.187.26.142:4513";

        // Log payload and target URL
        System.out.println("Sending payload: " + jsonPayload);
        System.out.println("Target URL: " + targetUrl);

        HttpURLConnection connection = null;
        try {
            // Create URL object and open connection
            URL url = new URL(targetUrl);
            connection = (HttpURLConnection) url.openConnection();

            // Configure connection
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            connection.setDoOutput(true);

            // Write JSON payload to the output stream
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonPayload.getBytes("UTF-8");
                os.write(input, 0, input.length);
            }

            // Read the response
            int responseCode = connection.getResponseCode();
            String responseMessage = connection.getResponseMessage();
            exchange.getIn().setHeader("HttpResponseCode", responseCode);

            System.out.println("Response Code: " + responseCode);
            System.out.println("Response Message: " + responseMessage);

            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(
                            responseCode >= 200 && responseCode < 300
                                    ? connection.getInputStream()
                                    : connection.getErrorStream(),
                            "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line.trim());
                }
            }

            // Set the response body in the exchange
            exchange.getIn().setBody(response.toString());
            System.out.println("Response Body: " + response);

        } catch (Exception e) {
            // Log and rethrow the exception
            System.err.println("Error during HTTP POST request: " + e.getMessage());
            throw e;
        } finally {
            // Disconnect the connection
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
