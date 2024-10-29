package org.camel.karavan.demo.test01;

import java.io.BufferedReader;
import java.io.IOException;  // Import IOException
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

@Component("GithubGetProcessor")
public class GithubGetProcessor implements Processor {

    // URL to the raw CSV file on GitHub
    private static final String FILE_URL = "https://raw.githubusercontent.com/abu-mhn/testCsvKaravan/main/test01/Spotify%20Most%20Streamed%20Songs.csv";

    // Mapping of old column names to new column names
    private static final Map<String, String> COLUMN_RENAMES = new HashMap<>() {{
        put("track_name", "trackName");
        put("artist(s)_name", "artistName");
        put("artist_count", "artistCount");
        put("released_year", "releasedYear");
        put("released_month", "releasedMonth");
        put("released_day", "releasedDay");
        put("in_spotify_playlists", "inSpotifyPlaylists");
        put("in_spotify_charts", "inSpotifyCharts");
        put("streams", "streams");
        put("in_apple_playlists", "inApplePlaylists");
        put("in_apple_charts", "inAppleCharts");
        put("in_deezer_playlists", "inDeezerPlaylists");
        put("in_deezer_charts", "inDeezerCharts");
        put("in_shazam_charts", "inShazamCharts");
        put("bpm", "bpm");
        put("key", "key");
        put("mode", "mode");
        put("danceability_%", "danceability");
        put("valence_%", "valence");
        put("energy_%", "energy");
        put("acousticness_%", "acousticness");
        put("instrumentalness_%", "instrumentalness");
        put("liveness_%", "liveness");
        put("speechiness_%", "speechiness");
        put("cover_url", "coverUrl");
    }};

    private final ObjectMapper objectMapper;

    // Constructor to initialize ObjectMapper with pretty-printing enabled
    public GithubGetProcessor() {
        objectMapper = new ObjectMapper();
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT); // Enable pretty-printing
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        // Get the HTTP URL connection to the CSV file
        URL url = new URL(FILE_URL);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");

        // Check HTTP response code
        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new IOException("HTTP error code: " + responseCode);
        }

        // Read the CSV content
        List<Map<String, Object>> jsonResponse = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            String headerLine = reader.readLine(); // Read the header line
            if (headerLine != null) {
                // Rename columns as needed
                String modifiedHeaderLine = renameColumns(headerLine);
                String[] headers = modifiedHeaderLine.split(",");

                String line;
                while ((line = reader.readLine()) != null) {
                    // System.out.println("Raw line: " + line); // Log raw line for debugging
                    String[] values = line.split(","); // Adjust delimiter if needed
                    Map<String, Object> row = new HashMap<>();
                    for (int i = 0; i < headers.length; i++) {
                        // Ensure values do not go out of bounds
                        if (i < values.length) {
                            // Clean the value by trimming whitespace and removing backslashes and quotes
                            String cleanedValue = values[i].trim().replace("\\", "")
                                                            .replace("\"", ""); // Remove backslashes and quotes
                            row.put(headers[i].trim(), cleanedValue);
                        }
                    }
                    jsonResponse.add(row); // Add the row to the response list
                }
            }
        }

        // Wrap the list of maps in a new map with the key "data"
        Map<String, Object> wrappedResponse = new HashMap<>();
        wrappedResponse.put("data", jsonResponse);

        // Convert the wrapped map to pretty-printed JSON and set it to the message body
        String jsonOutput = objectMapper.writeValueAsString(wrappedResponse);
        // System.out.println(jsonOutput); // Log the JSON output for debugging
        exchange.getIn().setBody(jsonOutput);
        exchange.getIn().setHeader(Exchange.CONTENT_TYPE, "application/json"); // Set content type to JSON
    }

    // Method to rename columns in the CSV header
    private String renameColumns(String headerLine) {
        String[] columns = headerLine.split(","); // Adjust the delimiter if needed
        for (int i = 0; i < columns.length; i++) {
            // Check if the column needs to be renamed
            if (COLUMN_RENAMES.containsKey(columns[i].trim())) {
                columns[i] = COLUMN_RENAMES.get(columns[i].trim()); // Rename the column
            }
        }
        return String.join(",", columns); // Join the columns back to a single line
    }
}
