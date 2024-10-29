package org.camel.karavan.demo.test01;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

// Response class to structure the JSON response
class Response {
    private String message;
    private int recordsInserted;
    private List<Map<String, Object>> data; // New field to hold the inserted data

    public Response(String message, int recordsInserted, List<Map<String, Object>> data) {
        this.message = message;
        this.recordsInserted = recordsInserted;
        this.data = data;
    }

    // Getters
    public String getMessage() {
        return message;
    }

    public int getRecordsInserted() {
        return recordsInserted;
    }

    public List<Map<String, Object>> getData() {
        return data; // Getter for the new field
    }
}

@Component("GithubGetProcessor")
public class GithubGetProcessor implements Processor {

    // URL to the raw CSV file on GitHub
    private static final String FILE_URL = "https://raw.githubusercontent.com/abu-mhn/testCsvKaravan/main/Spotify%20Most%20Streamed%20Songs.csv";

    // Database connection parameters
    private static final String DB_URL = "jdbc:postgresql://103.91.65.22:5093/staging";
    private static final String DB_USER = "mhnusr";
    private static final String DB_PASSWORD = "mHnY0uS3r456&890";

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

    public GithubGetProcessor() {
        objectMapper = new ObjectMapper();
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        // Create the database table
        createSpotifyDataTable();

        // Get the HTTP URL connection to the CSV file
        URL url = new URL(FILE_URL);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");

        // Check HTTP response code
        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new IOException("HTTP error code: " + responseCode);
        }

        int totalRecordsInserted = 0; // To count the number of records inserted
        List<Map<String, Object>> insertedData = new ArrayList<>(); // To hold the inserted data

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
             Connection dbConnection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {

            String headerLine = reader.readLine(); // Read the header line
            if (headerLine != null) {
                // Rename columns as needed
                String modifiedHeaderLine = renameColumns(headerLine);
                String[] headers = modifiedHeaderLine.split(",");

                // Prepare SQL INSERT statement
                String insertSQL = "INSERT INTO golf.spotify_data (" +
                        "track_name, artist_name, artist_count, released_year, released_month, released_day, " +
                        "in_spotify_playlists, in_spotify_charts, streams, in_apple_playlists, in_apple_charts, " +
                        "in_deezer_playlists, in_deezer_charts, in_shazam_charts, bpm, key, mode, danceability, " +
                        "valence, energy, acousticness, instrumentalness, liveness, speechiness, cover_url) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

                try (PreparedStatement preparedStatement = dbConnection.prepareStatement(insertSQL)) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String[] values = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");

                        Map<String, Object> rowData = new HashMap<>(); // To hold the current row's data

                        for (int i = 0; i < headers.length; i++) {
                            if (i < values.length) {
                                String value = values[i].replaceAll("^\"|\"$", "").trim();

                                // Handle type conversion based on the column name
                                switch (headers[i]) {
                                    case "artistCount":
                                    case "releasedYear":
                                    case "releasedMonth":
                                    case "releasedDay":
                                    case "inSpotifyPlaylists":
                                    case "inSpotifyCharts":
                                    case "streams":
                                    case "inApplePlaylists":
                                    case "inAppleCharts":
                                    case "inDeezerPlaylists":
                                    case "inDeezerCharts":
                                    case "inShazamCharts":
                                    case "mode":
                                    case "bpm":
                                    case "danceability":
                                    case "valence":
                                    case "energy":
                                    case "acousticness":
                                    case "instrumentalness":
                                    case "liveness":
                                    case "speechiness":
                                        // Check if the value is numeric before parsing
                                        if (value.isEmpty()) {
                                            preparedStatement.setObject(i + 1, null);
                                            rowData.put(headers[i], null); // Store null in the row data
                                        } else {
                                            try {
                                                double numericValue = Double.parseDouble(value);
                                                preparedStatement.setObject(i + 1, numericValue);
                                                rowData.put(headers[i], numericValue); // Store the value in the row data
                                            } catch (NumberFormatException e) {
                                                preparedStatement.setObject(i + 1, null); // Handle or log invalid values as needed
                                                rowData.put(headers[i], null); // Store null in the row data
                                            }
                                        }
                                        break;
                                    case "key":
                                    case "trackName":
                                    case "artistName":
                                    case "coverUrl":
                                        preparedStatement.setObject(i + 1, value.isEmpty() ? null : value);
                                        rowData.put(headers[i], value.isEmpty() ? null : value); // Store the value in the row data
                                        break;
                                    default:
                                        preparedStatement.setObject(i + 1, value.isEmpty() ? null : value);
                                        rowData.put(headers[i], value.isEmpty() ? null : value); // Store the value in the row data
                                }
                            } else {
                                preparedStatement.setObject(i + 1, null); // Handle missing values
                                rowData.put(headers[i], null); // Store null in the row data
                            }
                        }
                        preparedStatement.addBatch(); // Add to batch for efficient insertion
                        insertedData.add(rowData); // Add the row data to the list
                    }
                    totalRecordsInserted = preparedStatement.executeBatch().length; // Execute batch and get records count
                }
            }
        }

        // Create the response object
        Response response = new Response("Data inserted into the database successfully.", totalRecordsInserted, insertedData);
        
        // Convert response to JSON
        String jsonResponse = objectMapper.writeValueAsString(response);

        // Set JSON response
        exchange.getIn().setBody(jsonResponse);
        exchange.getIn().setHeader(Exchange.CONTENT_TYPE, "application/json");
    }

    // Method to create the Spotify data table in the database
    private void createSpotifyDataTable() {
        String createTableSQL = "CREATE SCHEMA IF NOT EXISTS golf; " +
                "CREATE TABLE IF NOT EXISTS golf.spotify_data (" +
                "track_name VARCHAR(255), " +
                "artist_name VARCHAR(255), " +
                "artist_count INTEGER, " +
                "released_year INTEGER, " +
                "released_month INTEGER, " +
                "released_day INTEGER, " +
                "in_spotify_playlists INTEGER, " +
                "in_spotify_charts INTEGER, " +
                "streams BIGINT, " +
                "in_apple_playlists INTEGER, " +
                "in_apple_charts INTEGER, " +
                "in_deezer_playlists INTEGER, " +
                "in_deezer_charts INTEGER, " +
                "in_shazam_charts INTEGER, " +
                "bpm NUMERIC, " +
                "key VARCHAR(255), " +
                "mode VARCHAR(255), " +
                "danceability NUMERIC, " +
                "valence NUMERIC, " +
                "energy NUMERIC, " +
                "acousticness NUMERIC, " +
                "instrumentalness NUMERIC, " +
                "liveness NUMERIC, " +
                "speechiness NUMERIC, " +
                "cover_url TEXT" +
                ");";

        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             Statement statement = connection.createStatement()) {
            statement.execute(createTableSQL);
        } catch (SQLException e) {
            e.printStackTrace(); // Handle exceptions properly in production code
        }
    }

    // Method to rename columns based on mapping
    private String renameColumns(String headerLine) {
        String[] headers = headerLine.split(",");
        StringBuilder modifiedHeaders = new StringBuilder();

        for (String header : headers) {
            String trimmedHeader = header.trim();
            String newHeader = COLUMN_RENAMES.getOrDefault(trimmedHeader, trimmedHeader);
            modifiedHeaders.append(newHeader).append(",");
        }

        // Remove trailing comma
        if (modifiedHeaders.length() > 0) {
            modifiedHeaders.setLength(modifiedHeaders.length() - 1);
        }

        return modifiedHeaders.toString();
    }
}
