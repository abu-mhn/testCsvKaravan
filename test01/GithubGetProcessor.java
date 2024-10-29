package org.camel.karavan.demo.test01;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.springframework.stereotype.Component;

@Component("GithubGetProcessor")
public class GithubGetProcessor implements Processor {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void process(Exchange exchange) throws Exception {
        // Get the URL from the exchange body, expecting it to be a JSON object
        String body = exchange.getIn().getBody(String.class);
        JsonNode jsonNode = objectMapper.readTree(body);
        String FILE_URL = jsonNode.get("url").asText().trim(); // Extracting the URL

        try {
            // Create a connection to the URL
            HttpURLConnection connection = (HttpURLConnection) new URL(FILE_URL).openConnection();
            connection.setRequestMethod("GET");
            connection.connect();

            // Check for successful response code
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                // Read the first line of the CSV content
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    String firstLine = reader.readLine(); // Read only the first line
                    if (firstLine != null) {
                        // Split the first line by comma and store it in an array
                        String[] lineArray = firstLine.split(",");

                        // Set the array to the message body
                        exchange.getIn().setBody(lineArray);
                    }
                }
            } else {
                throw new RuntimeException("Failed to connect: " + responseCode);
            }
        } catch (MalformedURLException e) {
            throw new RuntimeException("Malformed URL: " + FILE_URL, e);
        } catch (Exception e) {
            throw new RuntimeException("Error during HTTP connection: " + e.getMessage(), e);
        }
    }
}
