package org.camel.karavan.demo.t09092024.p3;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

@Component("GetDeviceProcessor")
public class GetDeviceProcessor implements Processor {

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY = 5000; // 5 seconds

    @Override
    public void process(Exchange exchange) throws Exception {
        String body = exchange.getIn().getBody(String.class);

        try {
            String deviceID = extractDeviceIDFromJson(body);
            if (deviceID == null || deviceID.isEmpty()) {
                sendErrorResponse(exchange, "deviceID is missing or empty", "BadRequest", 400);
                return;
            }

            String url = "https://veinscdr.mhnexus.com/baseR4/Device?type=kiosk&udi-di=" + deviceID;
            System.out.println("Sending request to URL: " + url);

            String response = getDeviceDetails(url);

            JsonNode responseNode = objectMapper.readTree(response);
            JsonNode totalNode = responseNode.path("total");

            if (totalNode.isMissingNode()) {
                sendErrorResponse(exchange, "'total' field missing in the response", "BadRequest", 400);
                return;
            }

            int total = totalNode.asInt();
            if (total == 0) {
                sendErrorResponse(exchange, "No devices found", "BadRequest", 400);
            } else {
                exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 200);
                exchange.getMessage().setBody(
                    new ByteArrayInputStream(response.getBytes(StandardCharsets.UTF_8)) // Ensure InputStream
                );
            }

        } catch (Exception e) {
            System.err.println("Error processing request: " + e.getMessage());
            sendErrorResponse(exchange, e.getMessage(), "InternalServerError", 500);
            throw e;
        }
    }

    private String extractDeviceIDFromJson(String json) throws IOException {
        JsonNode rootNode = objectMapper.readTree(json);
        JsonNode deviceIDNode = rootNode.path("deviceID");
        return deviceIDNode.isMissingNode() ? null : deviceIDNode.asText();
    }

    public String getDeviceDetails(String url) throws IOException {
        int attempt = 0;
        while (attempt < MAX_RETRIES) {
            try {
                Request request = new Request.Builder()
                        .url(url)
                        .get()
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        throw new IOException("Unexpected response code: " + response.code());
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

    private void sendErrorResponse(Exchange exchange, String errorMessage, String statusCode, int httpStatusCode) {
        try {
            JsonNode errorResponse = objectMapper.createObjectNode()
                    .put("details", "")
                    .put("errorMessage", errorMessage)
                    .put("statuscode", statusCode)
                    .put("timeStamp", generateTimestamp());

            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, httpStatusCode);
            exchange.getMessage().setBody(
                new ByteArrayInputStream(errorResponse.toString().getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception e) {
            System.err.println("Error creating error response: " + e.getMessage());
        }
    }

    private String generateTimestamp() {
        ZonedDateTime utcNow = ZonedDateTime.now(ZoneId.of("UTC"));
        ZonedDateTime singaporeTime = utcNow.withZoneSameInstant(ZoneId.of("Asia/Singapore"));
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
        return singaporeTime.format(formatter);
    }
}
