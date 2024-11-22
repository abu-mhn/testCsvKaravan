package com.example.authorization;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

@Component("AuthorizationProcessor") // Marks this class as a Spring component to be managed by Spring
public class AuthorizationProcessor implements Processor {

    // Constants for header keys and default timezone
    private static final String HEADER_CLIENT_ID = "X-Kiosk-Client-Id";
    private static final String HEADER_CLIENT_API_KEY = "X-Kiosk-Client-API-Key";
    private static final String DEFAULT_TIMEZONE = "Asia/Singapore";

    // ObjectMapper instance for JSON serialization
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public void process(Exchange exchange) {
        // Retrieve valid client ID and API key from properties set in Camel context
        String validClientId = exchange.getProperty("validClientId", String.class);
        String validClientApiKey = exchange.getProperty("validClientApiKey", String.class);

        // Retrieve the client ID and API key from the request headers
        String clientId = exchange.getIn().getHeader(HEADER_CLIENT_ID, String.class);
        String clientApiKey = exchange.getIn().getHeader(HEADER_CLIENT_API_KEY, String.class);

        // Check if the client is authorized
        if (isAuthorized(clientId, clientApiKey, validClientId, validClientApiKey)) {
            sendSuccessResponse(exchange); // If authorized, send a success response
        } else {
            sendErrorResponse(exchange); // If not authorized, send an error response
        }
    }

    // Helper method to check if the client ID and API key are valid
    private boolean isAuthorized(String clientId, String clientApiKey, String validClientId, String validClientApiKey) {
        return validClientId != null && validClientApiKey != null &&
               validClientId.equals(clientId) && validClientApiKey.equals(clientApiKey);
    }

    // Sends an error response if authorization fails
    private void sendErrorResponse(Exchange exchange) {
        // Create a map for the error response using LinkedHashMap to preserve order
        Map<String, Object> errorResponse = new LinkedHashMap<>();
        errorResponse.put("details", ""); // Add more details if needed
        errorResponse.put("errorMessage", "Header Value Missing or Invalid");
        errorResponse.put("statuscode", "BadRequest");
        errorResponse.put("timeStamp", getCurrentTimestamp(DEFAULT_TIMEZONE));

        // Convert the map to a JSON string
        String jsonResponse;
        try {
            jsonResponse = OBJECT_MAPPER.writeValueAsString(errorResponse); // Serialize map to JSON
        } catch (Exception e) {
            throw new RuntimeException("Error converting response to JSON", e);
        }

        // Set the response headers and body
        exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 400); // Set HTTP status code
        exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, "application/json"); // Set content type
        exchange.getMessage().setBody(jsonResponse); // Set the JSON response body
    }

    // Sends a success response if authorization succeeds
    private void sendSuccessResponse(Exchange exchange) {
        exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 200); // Set HTTP status code to 200
        exchange.getMessage().setBody(null); // Optional: Set the response body to null
    }

    // Helper method to get the current timestamp in a specific timezone
    private String getCurrentTimestamp(String timezone) {
        ZoneId zoneId = ZoneId.of(timezone); // Get the ZoneId for the specified timezone
        ZonedDateTime currentTime = ZonedDateTime.now(zoneId); // Get the current time in the specified timezone
        return currentTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME); // Format the timestamp in ISO format
    }
}
