package org.camel.karavan.demo.t09092024.p3;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

@Component("GetDeviceProcessor")
public class GetDeviceProcessor implements Processor {

    // Configure OkHttpClient with increased timeout settings
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS) // Increase connection timeout
            .readTimeout(60, TimeUnit.SECONDS)    // Increase read timeout
            .build();
    
    private final ObjectMapper objectMapper = new ObjectMapper(); // Jackson ObjectMapper for JSON processing

    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY = 5000; // 5 seconds

    @Override
    public void process(Exchange exchange) throws Exception {
        // Retrieve the body as a String
        String body = exchange.getIn().getBody(String.class);
        
        try {
            // Parse the JSON String to extract the 'deviceID'
            String deviceID = extractDeviceIDFromJson(body);

            if (deviceID == null || deviceID.isEmpty()) {
                sendErrorResponse(exchange, "deviceID do not exist", "BadRequest", 400);
                return;
            }

            // Construct the API URL dynamically
            String url = "https://veinscdr.mhnexus.com/baseR4/Device?type=kiosk&udi-di=" + deviceID;
            System.out.println("Sending request to URL: " + url);

            // Call the method to send the GET request and retrieve device details
            String response = getDeviceDetails(url);

            // Check for "total" in the response
            JsonNode responseNode = objectMapper.readTree(response);
            JsonNode totalNode = responseNode.path("total");
            
            if (totalNode.isMissingNode()) {
                sendErrorResponse(exchange, "'total' field missing in the response", "BadRequest", 400);
                return;
            }
            
            int total = totalNode.asInt();

            // Set the appropriate status code based on the 'total' value
            if (total == 0) {
                sendErrorResponse(exchange, "No devices found", "BadRequest", 400);
            } else {
                exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 200);
                exchange.getMessage().setBody(response);  // Set the actual response body
            }
            
        } catch (Exception e) {
            System.err.println("Error processing request: " + e.getMessage());
            sendErrorResponse(exchange, e.getMessage(), "InternalServerError", 500);
            throw e; // Rethrow or handle as needed
        }
    }

    // Method to extract 'deviceID' from JSON string
    private String extractDeviceIDFromJson(String json) throws IOException {
        JsonNode rootNode = objectMapper.readTree(json);
        JsonNode deviceIDNode = rootNode.path("deviceID");
        return deviceIDNode.isMissingNode() ? null : deviceIDNode.asText();
    }

    // Method to send a GET request to the API and retrieve device details with retry logic
    public String getDeviceDetails(String url) throws IOException {
        int attempt = 0;
        while (attempt < MAX_RETRIES) {
            try {
                // Create the request
                Request request = new Request.Builder()
                        .url(url)
                        .get()
                        .build();

                // Execute the request and return the response body as a string
                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        throw new IOException("Unexpected response code: " + response.code() + " - " + response.message());
                    }
                    if (response.body() != null) {
                        return response.body().string();
                    } else {
                        throw new IOException("Empty response body");
                    }
                }
            } catch (SocketTimeoutException e) {
                attempt++;
                if (attempt >= MAX_RETRIES) {
                    throw new IOException("Request timed out after " + MAX_RETRIES + " attempts", e);
                }
                System.err.println("Request timed out. Retrying in " + (RETRY_DELAY / 1000) + " seconds...");
                try {
                    Thread.sleep(RETRY_DELAY);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Retry interrupted", ie);
                }
            }
        }
        throw new IOException("Failed to retrieve device details after " + MAX_RETRIES + " attempts");
    }

    // Method to send structured error response in the desired format
    private void sendErrorResponse(Exchange exchange, String errorMessage, String statusCode, int httpStatusCode) {
        try {
            // Create a structured error message
            JsonNode errorResponse = objectMapper.createObjectNode()
                    .put("details", "")
                    .put("errorMessage", errorMessage)
                    .put("statuscode", statusCode)
                    .put("timeStamp", generateTimestamp());

            // Set HTTP status code
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, httpStatusCode);
            exchange.getMessage().setBody(errorResponse.toString());
        } catch (Exception e) {
            System.err.println("Error creating error response: " + e.getMessage());
        }
    }

    // Method to generate the timestamp (from the TimeStamp class)
    private String generateTimestamp() {
        // Get the current date and time in UTC
        ZonedDateTime utcNow = ZonedDateTime.now(ZoneId.of("UTC"));

        // Adjust the timestamp to Singapore timezone (+08:00)
        ZonedDateTime singaporeTime = utcNow.withZoneSameInstant(ZoneId.of("Asia/Singapore"));

        // Format the timestamp as a custom string with milliseconds
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
        return singaporeTime.format(formatter);
    }
}
