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

    // Constants for header keys, valid client ID and API key, and default timezone
    private static final String HEADER_CLIENT_ID = "X-Kiosk-Client-Id";
    private static final String HEADER_CLIENT_API_KEY = "X-Kiosk-Client-API-Key";
    private static final String VALID_CLIENT_ID = "t6BhCaEN9Zqz7+KWz/Ql1xCmtiMLhgMc";
    private static final String VALID_CLIENT_API_KEY = "cqLWIaeWr6e2YDf83fzFLGVXBV/FZ13H6AjFydtRhEQ=";
    private static final String DEFAULT_TIMEZONE = "Asia/Singapore";

    // ObjectMapper instance for JSON serialization
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public void process(Exchange exchange) {
        // Retrieve the client ID and API key from the request headers
        String clientId = exchange.getIn().getHeader(HEADER_CLIENT_ID, String.class);
        String clientApiKey = exchange.getIn().getHeader(HEADER_CLIENT_API_KEY, String.class);

        // Check if the client is authorized based on the provided ID and API key
        if (isAuthorized(clientId, clientApiKey)) {
            sendSuccessResponse(exchange);  // If authorized, send a success response
        } else {
            sendErrorResponse(exchange);  // If not authorized, send an error response
        }
    }

    // Helper method to check if the client ID and API key are valid
    private boolean isAuthorized(String clientId, String clientApiKey) {
        return VALID_CLIENT_ID.equals(clientId) && VALID_CLIENT_API_KEY.equals(clientApiKey);
    }

    // Sends an error response if authorization fails
    private void sendErrorResponse(Exchange exchange) {
        // Create a map for the error response using LinkedHashMap to preserve order
        Map<String, Object> errorResponse = new LinkedHashMap<>();
        errorResponse.put("details", "");  // You can add more details about the error if needed
        errorResponse.put("errorMessage", "Header Value Missing");  // Message indicating a missing or incorrect header
        errorResponse.put("statuscode", "BadRequest");  // HTTP status for bad request (400)
        errorResponse.put("timeStamp", getCurrentTimestamp(DEFAULT_TIMEZONE));  // Timestamp for when the error occurred

        // Convert the map to a JSON string
        String jsonResponse;
        try {
            jsonResponse = OBJECT_MAPPER.writeValueAsString(errorResponse);  // Serialize map to JSON
        } catch (Exception e) {
            throw new RuntimeException("Error converting response to JSON", e);
        }

        // Set the response headers and body
        exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 400);  // Set the HTTP status code to 400
        exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, "application/json");  // Set the content type to JSON
        exchange.getMessage().setBody(jsonResponse);  // Set the response body with the JSON error message
    }

    // Sends a success response if authorization succeeds
    private void sendSuccessResponse(Exchange exchange) {
        exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 200);  // Set the HTTP status code to 200 (OK)
        exchange.getMessage().setBody(null);  // Set the body of the response with the original request body
    }

    // Helper method to get the current timestamp in a specific timezone
    private String getCurrentTimestamp(String timezone) {
        ZoneId zoneId = ZoneId.of(timezone);  // Get the ZoneId for the provided timezone
        ZonedDateTime currentTime = ZonedDateTime.now(zoneId);  // Get the current time in the specified timezone
        return currentTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);  // Format the timestamp in ISO format with offset
    }
}
