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

@Component("AccessToken")
public class AccessToken implements Processor {

    private static final MediaType JSON = MediaType.parse("application/json");

    // Configuring OkHttpClient with extended timeout settings
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS) // Connection timeout
            .readTimeout(60, TimeUnit.SECONDS)    // Read timeout
            .writeTimeout(60, TimeUnit.SECONDS)   // Write timeout
            .build();

    @Override
    public void process(Exchange exchange) throws Exception {
        // Retrieve the access token URL from the Exchange properties
        String accessTokenUrl = exchange.getProperty("urlAccessToken", String.class);

        // Now you can use it in the URL
        String url = "https://" + accessTokenUrl;

        // Extract the JSON payload from the Camel Exchange
        String jsonPayload = exchange.getIn().getBody(String.class);

        if (jsonPayload == null || jsonPayload.isEmpty()) {
            String errorMessage = "{\n  \"error\": \"Empty or null JSON payload received\"\n}";
            exchange.getIn().setBody(errorMessage);
            exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 400); // Bad Request
            return;
        }

        RequestBody body = RequestBody.create(jsonPayload, JSON);
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String responseBody = response.body() != null ? response.body().string() : "No response body";
                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode responseJson = objectMapper.readTree(responseBody);

                exchange.getIn().setBody(responseBody);
                exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, response.code());
            } else {
                String responseBody = response.body() != null ? response.body().string() : "No response body";
                exchange.getIn().setBody(responseBody);
                exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, response.code());
            }
        } catch (IOException e) {
            String errorMessage = "{\n  \"error\": \"" + e.getMessage().replace("\"", "\\\"") + "\"\n}";
            exchange.getIn().setBody(errorMessage);
            exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 500);
            exchange.setProperty(Exchange.EXCEPTION_CAUGHT, e);
        }
    }
}
