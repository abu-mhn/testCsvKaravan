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
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Component("FhirPostJsonProcessor")
public class FhirPostJsonProcessor implements Processor {

    private static final String URL = "https://veinscdr.mhnexus.com/baseR4/";
    private static final MediaType JSON = MediaType.parse("application/fhir+json");

    // Configuring OkHttpClient with extended timeout settings
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS) // Connection timeout
            .readTimeout(60, TimeUnit.SECONDS)    // Read timeout
            .writeTimeout(60, TimeUnit.SECONDS)   // Write timeout
            .build();

    @Override
    public void process(Exchange exchange) throws Exception {
        // Extract the JSON payload from the Camel Exchange
        String jsonPayload = exchange.getIn().getBody(String.class);

        if (jsonPayload == null || jsonPayload.isEmpty()) {
            String errorMessage = buildErrorResponse("Empty or null JSON payload received", "BadRequest");
            exchange.getIn().setBody(errorMessage);
            exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 400); // Bad Request
            return;
        }

        RequestBody body = RequestBody.create(jsonPayload, JSON);
        Request request = new Request.Builder()
                .url(URL)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                // Handle error response
                String responseBody = response.body() != null ? response.body().string() : "No response body";

                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode responseJson = objectMapper.readTree(responseBody);
                JsonNode diagnosticsNode = responseJson.path("issue").get(0).path("diagnostics");

                String diagnosticMessage = diagnosticsNode.isMissingNode() ? "No diagnostics found" : diagnosticsNode.asText();
                String errorMessage = buildErrorResponse(diagnosticMessage, String.valueOf(response.code()));
                exchange.getIn().setBody(errorMessage);
                exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, response.code());
            } else {
                // Simply return the full FHIR response body for a successful response
                String responseBody = response.body() != null ? response.body().string() : "{}";
                exchange.getIn().setBody(responseBody);
                exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 200); // OK
            }
        } catch (IOException e) {
            // Handle exception and set response
            String errorMessage = buildErrorResponse(e.getMessage(), "InternalServerError");
            exchange.getIn().setBody(errorMessage);
            exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 500);
            exchange.setProperty(Exchange.EXCEPTION_CAUGHT, e);
        }
    }

    private String buildErrorResponse(String errorMessage, String statusCode) {
        // Generate timestamp
        String timestamp = generateTimestamp();

        // Build the error response in the desired format
        return String.format(
            "{\n" +
            "  \"details\": \"\",\n" +
            "  \"errorMessage\": \"%s\",\n" +
            "  \"statuscode\": \"%s\",\n" +
            "  \"timeStamp\": \"%s\"\n" +
            "}",
            errorMessage, statusCode, timestamp
        );
    }

    private String generateTimestamp() {
        // Get the current date and time in UTC
        ZonedDateTime utcNow = ZonedDateTime.now(ZoneId.of("UTC"));

        // Adjust the timestamp to Singapore timezone (+08:00)
        ZonedDateTime singaporeTime = utcNow.withZoneSameInstant(ZoneId.of("Asia/Singapore"));

        // Format the timestamp as a custom string with milliseconds
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
        return singaporeTime.format(formatter);  // Return the formatted timestamp string
    }
}
