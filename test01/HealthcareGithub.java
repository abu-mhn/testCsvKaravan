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

@Component("HealthcareGithub")
public class HealthcareGithub implements Processor {
    // URL to the raw CSV file on GitHub
    private static final String FILE_URL = "https://raw.githubusercontent.com/abu-mhn/testCsvKaravan/refs/heads/main/test01/healthcare_dataset.csv";

    // Database connection parameters
    private static final String DB_URL = "jdbc:postgresql://103.91.65.22:5093/staging";
    private static final String DB_USER = "mhnusr";
    private static final String DB_PASSWORD = "mHnY0uS3r456&890";

    // Mapping of old column names to new column names
    private static final Map<String, String> COLUMN_RENAMES = new HashMap<>() {
        {
    put("Name",
            "name");
    put("Age",
            "age");
    put("Gender",
            "gender");
    put("Blood Type",
            "bloodType");
    put("Medical Condition",
            "medicalCondition");
    put("Date of Admission",
            "dateOfAdmission");
    put("Doctor",
            "doctor");
    put("Hospital",
            "hospital");
    put("Insurance Provider",
            "insuranceProvider");
    put("Billing Amount",
            "billingAmount");
    put("Room Number",
            "roomNumber");
    put("Admission Type",
            "admissionType");
    put("Discharge Date",
            "dischargeDate");
    put("Medication",
            "medication");
    put("Test Results",
            "testResults");
        }
    };


    private final ObjectMapper objectMapper;

    public HealthcareGithub() {
        objectMapper = new ObjectMapper();
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        // Create the database table
        createHealthcareDataTable();

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
                String insertSQL = "INSERT INTO golf.healthcare_data (" +
                    "name, age, gender, blood_type, medical_condition, date_of_admission, doctor, " +
                    "hospital, insurance_provider, billing_amount, room_number, admission_type, " +
                    "discharge_date, medication, test_results) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

                try (PreparedStatement preparedStatement = dbConnection.prepareStatement(insertSQL)) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String[] values = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");

                        Map<String, Object> rowData = new HashMap<>(); // To hold the current row's data

                        for (int i = 0; i < headers.length; i++) {
    if (i < values.length) {
        String value = values[i
                                ].replaceAll("^\"|\"$",
                                "").trim();

        // Handle type conversion based on the column name
        switch (headers[i
                                ]) {
            case "age":
                                    // Age should be numeric
                if (value.isEmpty()) {
                    preparedStatement.setObject(i + 1, null);
                    rowData.put(headers[i
                                        ], null); // Store null in the row data
                                    } else {
                    try {
                        int ageValue = Integer.parseInt(value); // Assuming age is an integer
                        preparedStatement.setObject(i + 1, ageValue);
                        rowData.put(headers[i
                                            ], ageValue); // Store the value in the row data
                                        } catch (NumberFormatException e) {
                        preparedStatement.setObject(i + 1, null); // Handle or log invalid values as needed
                        rowData.put(headers[i
                                            ], null); // Store null in the row data
                                        }
                                    }
                break;
            case "bloodType":
            case "medicalCondition":
            case "doctor":
            case "hospital":
            case "insuranceProvider":
            case "roomNumber":
            case "admissionType":
            case "medication":
            case "testResults":
                                    // These fields are strings
                preparedStatement.setObject(i + 1, value.isEmpty() ? null : value);
                rowData.put(headers[i
                                    ], value.isEmpty() ? null : value); // Store the value in the row data
                break;
            case "dateOfAdmission":
            case "dischargeDate":
                                    // Dates should be parsed from String to Date
                if (value.isEmpty()) {
                    preparedStatement.setObject(i + 1, null);
                    rowData.put(headers[i
                                        ], null); // Store null in the row data
                                    } else {
                    try {
                        preparedStatement.setDate(i + 1, java.sql.Date.valueOf(value)); // Adjust this based on your date format
                        rowData.put(headers[i
                                            ], value); // Store the raw date string or parsed date as needed
                                        } catch (IllegalArgumentException e) {
                        preparedStatement.setObject(i + 1, null); // Handle or log invalid date formats
                        rowData.put(headers[i
                                            ], null); // Store null in the row data
                                        }
                                    }
                break;
            default:
                preparedStatement.setObject(i + 1, value.isEmpty() ? null : value);
                rowData.put(headers[i
                                    ], value.isEmpty() ? null : value); // Store the value in the row data
                                }
                            } else {
        preparedStatement.setObject(i + 1, null); // Handle missing values
        rowData.put(headers[i
                                ], null); // Store null in the row data
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
        exchange.getIn().setHeader(Exchange.CONTENT_TYPE,
        "application/json");
    }
    // Method to create the Spotify data table in the database
    private void createHealthcareDataTable() {
    String createTableSQL = "CREATE SCHEMA IF NOT EXISTS golf; " +
            "CREATE TABLE IF NOT EXISTS golf.healthcare_data (" +
            "name VARCHAR(255), " +
            "age INTEGER, " +
            "gender VARCHAR(10), " +
            "blood_type VARCHAR(5), " +
            "medical_condition VARCHAR(255), " +
            "date_of_admission DATE, " +
            "doctor VARCHAR(255), " +
            "hospital VARCHAR(255), " +
            "insurance_provider VARCHAR(255), " +
            "billing_amount NUMERIC, " +
            "room_number VARCHAR(20), " +
            "admission_type VARCHAR(50), " +
            "discharge_date DATE, " +
            "medication TEXT, " +
            "test_results TEXT" +
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