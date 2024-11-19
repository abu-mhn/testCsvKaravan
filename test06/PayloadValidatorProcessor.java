import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component("PayloadValidatorProcessor")
public class PayloadValidatorProcessor implements Processor {

    // ObjectMapper instance to parse and generate JSON
    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void process(Exchange exchange) throws Exception {
        // Extract the request body (JSON) from the exchange
        String body = exchange.getIn().getBody(String.class);
        
        // Validate the JSON payload
        String validationResponse = validatePayload(body);

        // If validation fails, set HTTP response to 400 and return the error message
        if (validationResponse != null) {
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 400);
            exchange.getMessage().setBody(validationResponse);
        } else {
            // If valid, set HTTP response to 200 and no need to set the body
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 200);
        }
    }

    // Method to validate the JSON payload
    private String validatePayload(String jsonPayload) {
        try {
            JsonNode rootNode = mapper.readTree(jsonPayload);  // Parse JSON string into JsonNode
            List<String> errorMessages = new ArrayList<>(); // Collect validation errors

            // Validate 'sfz' object and required fields
            if (rootNode.has("sfz")) {
                JsonNode sfzNode = rootNode.get("sfz");
                String[] requiredFields = {"idnumber", "name"};

                // Check if required fields are present and not empty
                for (String field : requiredFields) {
                    if (!sfzNode.has(field) || sfzNode.get(field).isNull() || sfzNode.get(field).asText().trim().isEmpty()) {
                        errorMessages.add("Missing or empty required field '" + field + "' in 'sfz'");
                    }
                }
            }

            // If any validation errors, return a formatted error response
            if (!errorMessages.isEmpty()) {
                return createErrorResponse(errorMessages);
            }
            return null; // No errors, payload is valid
        } catch (IOException e) {
            // If the JSON is malformed, return an error response
            return createErrorResponse(List.of("Invalid JSON format"));
        }
    }

    // Method to create a structured error response in JSON format
    private String createErrorResponse(List<String> errorMessages) {
        OffsetDateTime currentDateTime = OffsetDateTime.now(ZoneOffset.ofHours(8));  // Get current date and time in UTC+8
        String formattedTime = currentDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")); // Format date/time

        // Create the error response JSON structure
        ObjectNode response = mapper.createObjectNode();
        ArrayNode detailsArray = response.putArray("details");  // Array for error details
        for (String errorMessage : errorMessages) {
            detailsArray.add(errorMessage);  // Add each error message
        }

        // Populate other response fields
        response.put("errorMessage", "Validation failed");
        response.put("statuscode", "BadRequest");
        response.put("timeStamp", formattedTime);
        
        // Return the error response as a string
        return response.toString();
    }
}
