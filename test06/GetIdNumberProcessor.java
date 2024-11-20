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
import java.util.concurrent.TimeUnit;

@Component("GetIdNumberProcessor")
public class GetIdNumberProcessor implements Processor {

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
        if (body == null || body.isEmpty()) {
            throw new IllegalArgumentException("Request body is empty.");
        }

        try {
            // Parse the JSON String to extract the 'idNumber'
            String idNumber = extractIdNumberFromJson(body);
            // Parse the JSON String to extract the 'nation'
            String nation = extractNationFromJson(body);

            if (idNumber == null || idNumber.isEmpty()) {
                throw new IllegalArgumentException("idNumber not found in the input JSON.");
            }

            String identifier = determineUrl(nation, idNumber);
    
            // Construct the API URL dynamically
            String url = "https://veinscdr.mhnexus.com/baseR4/Patient?identifier=" + identifier + "|"
                    + idNumber + "&_source=http://provider.hie.moh.gov.my";

            System.out.println("Constructed URL: " + url);
    
            // Call the method to send the GET request and retrieve device details
            String response = getIdNumberDetails(url);

            // Log the response for debugging
            System.out.println("Response from API: " + response);

            // Unmarshal the JSON response and set it back into the exchange
            JsonNode responseJson = objectMapper.readTree(response);
            exchange.getMessage().setBody(responseJson);

            // Access 'total' field from the response
            if (responseJson.has("total")) {
                int total = responseJson.path("total").asInt();
                System.out.println("Total: " + total);
                exchange.getMessage().setHeader("total", total); // Store the total in the header if needed
            } else {
                System.out.println("Total field not found.");
            }
        } catch (Exception e) {
            System.err.println("Error processing request: " + e.getMessage());
            throw e; // Rethrow or handle as needed
        }
    }

    // Method to extract 'idNumber' from JSON string
    private String extractIdNumberFromJson(String json) throws IOException {
        JsonNode rootNode = objectMapper.readTree(json);
        JsonNode sfzNode = rootNode.path("sfz");
        JsonNode idNumberNode = sfzNode.path("idnumber");
        String idNumber = idNumberNode.isMissingNode() ? null : idNumberNode.asText();
        if (idNumber == null) {
            System.err.println("idNumber not found in JSON.");
        }
        return idNumber;
    }

    // Method to extract 'nation' from JSON string
    private String extractNationFromJson(String json) throws IOException {
        JsonNode rootNode = objectMapper.readTree(json);
        JsonNode sfzNode = rootNode.path("sfz");
        JsonNode nationNode = sfzNode.path("nation");
        String nation = nationNode.isMissingNode() ? null : nationNode.asText();
        if (nation == null) {
            System.err.println("Nation not found in JSON.");
        }
        return nation;
    }

    // Method to send a GET request to the API and retrieve device details with retry logic
    public String getIdNumberDetails(String url) throws IOException {
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
            } catch (IOException e) {
                attempt++;
                if (attempt >= MAX_RETRIES) {
                    throw new IOException("Failed to retrieve device details after " + MAX_RETRIES + " attempts", e);
                }
                System.err.println("Request failed. Retrying in " + (RETRY_DELAY / 1000) + " seconds...");
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

    public boolean isMalaysian(String nation) {
        if (nation == null) {
            return false;
        }
        String lowerCaseNation = nation.toLowerCase();
        return lowerCaseNation.equals("malaysia") || lowerCaseNation.equals("malaysian");
    }

    public boolean isValidMyKad(String myKad) {
        if (myKad == null) {
            return false;
        }
        // Remove any non-digit characters (spaces, hyphens, etc.)
        String cleanedMyKad = myKad.replaceAll("\\D", "");
        // Check if the cleaned myKad number is exactly 12 digits and contains only numbers
        return cleanedMyKad.length() == 12 && cleanedMyKad.matches("\\d+");
    }

    public String determineUrl(String nation, String idNumber) {
        String url = "";

        boolean isStringNum = isValidMyKad(idNumber);

        if (isStringNum && (isMalaysian(nation) || !isMalaysian(nation))) {
            url = "http://fhir.hie.moh.gov.my/sid/my-kad-no";
        } else if (!isStringNum && (isMalaysian(nation) || !isMalaysian(nation))) {
            url = "http://fhir.hie.moh.gov.my/sid/passport-no";
        }

        System.out.println("Determined URL: " + url);
        return url;
    }
}
