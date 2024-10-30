import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.springframework.stereotype.Component;

@Component("GithubGetProcessor")
public class GithubGetProcessor implements Processor {

    private static final String DB_URL = "jdbc:postgresql://103.91.65.22:5093/staging";
    private static final String DB_USER = "mhnusr";
    private static final String DB_PASSWORD = "mHnY0uS3r456&890";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void process(Exchange exchange) throws Exception {
        String body = exchange.getIn().getBody(String.class);
        JsonNode jsonNode = objectMapper.readTree(body);

        // Extract URL and table name from headers or body
        String fileUrl = jsonNode.get("url").asText().trim();
        String tableName = jsonNode.get("tableName").asText().trim();

        // Log extracted values
        System.out.println("Processing file from URL: " + fileUrl);
        System.out.println("Table name to be created: " + tableName);

        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(fileUrl).openConnection();
            connection.setRequestMethod("GET");
            connection.connect();

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    // Read the first line (column names)
                    String firstLine = reader.readLine();
                    if (firstLine != null) {
                        String[] lineArray = firstLine.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");

                        // Prepare a list to collect all data rows
                        List<JsonNode> dataRows = new ArrayList<>();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            String[] dataLineArray = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");

                            // Create table with inferred data types on the first data line
                            if (dataRows.isEmpty()) {
                                createTableIfNotExists(tableName, lineArray, dataLineArray);
                            }

                            // Log lengths for debugging
                            System.out.println("Line Array Length: " + lineArray.length);
                            System.out.println("Data Line Array Length: " + dataLineArray.length);

                            // Check for length match
                            if (dataLineArray.length != lineArray.length) {
                                System.err.println("Data length does not match columns length for table " + tableName);
                                System.err.println("Columns: " + Arrays.toString(lineArray));
                                System.err.println("Data: " + Arrays.toString(dataLineArray));
                                continue; // Skip to the next line
                            }

                            // Insert the current line data into the database
                            insertDataIntoTable(tableName, lineArray, dataLineArray);

                            // Convert data line to JSON object and add it to the list
                            ObjectNode dataRow = objectMapper.createObjectNode();
                            for (int i = 0; i < lineArray.length; i++) {
                                dataRow.put(lineArray[i].trim(), dataLineArray[i].trim());
                            }
                            dataRows.add(dataRow);
                        }

                        // Prepare JSON response
                        JsonNode responseJson = objectMapper.createObjectNode()
                                .put("status", "success")
                                .put("message", "Data processed successfully")
                                .put("tableName", tableName)
                                .put("recordCount", dataRows.size())
                                .set("data", objectMapper.valueToTree(dataRows)); // Add the structured data

                        // Store the JSON response in the exchange body
                        exchange.getIn().setBody(objectMapper.writeValueAsString(responseJson));
                    }
                }
            } else {
                throw new RuntimeException("Failed to connect: HTTP response code " + responseCode);
            }
        } catch (MalformedURLException e) {
            throw new RuntimeException("Malformed URL: " + fileUrl, e);
        } catch (Exception e) {
            throw new RuntimeException("Error during HTTP connection: " + e.getMessage(), e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void createTableIfNotExists(String tableName, String[] columns, String[] sampleData) {
        // Check if columns are provided
        if (columns.length == 0) {
            throw new RuntimeException("No columns provided for table " + tableName);
        }

        // Construct the SQL statement dynamically
        StringBuilder createTableSQL = new StringBuilder();
        createTableSQL.append(String.format("CREATE TABLE IF NOT EXISTS golf.%s (", tableName));

        for (int i = 0; i < columns.length; i++) {
            String columnName = columns[i].trim().replaceAll("[^a-zA-Z0-9_]", "_"); // Replace invalid characters
            String dataType = determineDataType(sampleData[i]);
            createTableSQL.append(String.format("%s %s, ", columnName, dataType));
            System.out.println(String.format("Inferred data type for column '%s': %s", columnName, dataType)); // Log inferred data type
        }

        // Remove the last comma and close the parentheses
        if (columns.length > 0) {
            createTableSQL.setLength(createTableSQL.length() - 2); // Remove last comma and space
        }
        createTableSQL.append(");"); // Close the statement

        System.out.println("Executing SQL: " + createTableSQL);  // Log the SQL for debugging

        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             Statement statement = connection.createStatement()) {
            statement.execute(createTableSQL.toString());
            System.out.println("Table created or verified in schema golf.");
        } catch (SQLException e) {
            e.printStackTrace(); // For additional error details
            throw new RuntimeException("Error creating table in database: " + e.getMessage(), e);
        }
    }

    private void insertDataIntoTable(String tableName, String[] columns, String[] data) {
        // Log the columns and data being inserted
        System.out.println("Inserting data into table: " + tableName);
        System.out.println("Columns: " + Arrays.toString(columns));
        System.out.println("Data: " + Arrays.toString(data));

        // Check that the number of columns matches the number of data values
        if (data.length != columns.length) {
            System.err.println("Data length does not match columns length for table " + tableName);
            System.err.println("Number of columns: " + columns.length);
            System.err.println("Number of values: " + data.length);
            System.err.println("Data to insert: " + Arrays.toString(data));
            return; // Skip insert, handle as needed
        }

        // Construct the SQL insert statement
        StringBuilder insertSQL = new StringBuilder();
        insertSQL.append(String.format("INSERT INTO golf.%s (", tableName));

        for (String column : columns) {
            String columnName = column.trim().replaceAll("[^a-zA-Z0-9_]", "_"); // Replace invalid characters
            insertSQL.append(columnName).append(", ");
        }

        // Remove the last comma and space
        if (columns.length > 0) {
            insertSQL.setLength(insertSQL.length() - 2);
        }
        insertSQL.append(") VALUES (");

        for (String value : data) {
            insertSQL.append("'").append(value.replace("'", "''")).append("', "); // Escape single quotes in data
        }

        // Remove the last comma and close the parentheses
        if (data.length > 0) {
            insertSQL.setLength(insertSQL.length() - 2);
        }
        insertSQL.append(");"); // Close the statement

        System.out.println("Executing SQL: " + insertSQL);  // Log the SQL for debugging

        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement preparedStatement = connection.prepareStatement(insertSQL.toString())) {
            preparedStatement.executeUpdate();
            System.out.println("Data inserted into table " + tableName);
        } catch (SQLException e) {
            System.err.println("SQL Error Code: " + e.getErrorCode());
            System.err.println("SQL State: " + e.getSQLState());
            e.printStackTrace(); // For additional error details
            throw new RuntimeException("Error inserting data into database: " + e.getMessage(), e);
        }
    }

    private String determineDataType(String value) {
        if (value == null || value.isEmpty()) {
            return "VARCHAR(255)"; // Default to VARCHAR for empty values
        }

        // Check if the value is an integer
        if (value.matches("-?\\d+")) {
            return "INTEGER";
        }
        // Check if the value is a double
        else if (value.matches("-?\\d+(\\.\\d+)?")) {
            return "DOUBLE PRECISION";
        }
        // Check if the value is a boolean
        else if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
            return "BOOLEAN";
        }
        // Otherwise, return VARCHAR
        return "VARCHAR(255)";
    }
}
