package org.camel.karavan.demo.t09092024.p3;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Component("PostBillingDetail")
public class PostBillingDetail implements Processor {

    private static final MediaType JSON = MediaType.parse("application/json");

    // Configuring OkHttpClient with extended timeout settings
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS) // Connection timeout
            .readTimeout(60, TimeUnit.SECONDS)    // Read timeout
            .writeTimeout(60, TimeUnit.SECONDS)   // Write timeout
            .build();

    @Override
    public void process(Exchange exchange) throws Exception {
        // Define the target URL
        String url = "http://medilinkuat.medibridgeasia.tech/EccsProviderApi/api/clinic/PostBillingDetail";

        // Extract the JSON payload from the Camel Exchange
        String jsonPayload = exchange.getIn().getBody(String.class);

        // Log the payload for debugging
        System.out.println("Received JSON Payload: " + jsonPayload);

        if (jsonPayload == null || jsonPayload.isEmpty()) {
            String errorMessage = "{\n  \"error\": \"Empty or null JSON payload received\"\n}";
            exchange.getIn().setBody(errorMessage);
            exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 400); // Bad Request
            return;
        }

        // Build the HTTP request
        RequestBody body = RequestBody.create(jsonPayload, JSON);
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();

        // Execute the HTTP request and handle the response
        try (Response response = client.newCall(request).execute()) {
            // Log response status and body for debugging
            String responseBody = response.body() != null ? response.body().string() : "No response body";
            System.out.println("Response Code: " + response.code());
            System.out.println("Response Body: " + responseBody);

            // Handle unsuccessful responses
            if (!response.isSuccessful()) {
                handleUnsuccessfulResponse(response, responseBody, exchange);
            } else {
                // Process successful response
                exchange.getIn().setBody(responseBody);
                exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, response.code());
            }
        } catch (IOException e) {
            // Log and handle exceptions
            e.printStackTrace();
            String errorMessage = "{\n  \"error\": \"" + e.getMessage().replace("\"", "\\\"") + "\"\n}";
            exchange.getIn().setBody(errorMessage);
            exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 500); // Internal Server Error
            exchange.setProperty(Exchange.EXCEPTION_CAUGHT, e);
        }
    }

    /**
     * Handles unsuccessful responses by logging details and setting the Exchange body and headers.
     *
     * @param response      The HTTP response object
     * @param responseBody  The response body as a string
     * @param exchange      The Camel Exchange object
     */
    private void handleUnsuccessfulResponse(Response response, String responseBody, Exchange exchange) throws IOException {
        // Log error response details
        System.err.println("Unsuccessful Response Code: " + response.code());
        System.err.println("Unsuccessful Response Body: " + responseBody);

        // Parse the response body if JSON
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            JsonNode responseJson = objectMapper.readTree(responseBody);
            exchange.getIn().setBody(responseJson.toPrettyString());
        } catch (Exception e) {
            exchange.getIn().setBody(responseBody); // Set raw response if parsing fails
        }

        // Set HTTP response code in the Exchange
        exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, response.code());
    }
}
