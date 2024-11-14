import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.json.JSONObject;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component("PostgresPost")
public class PostgresPost implements Processor {

    @Override
    public void process(Exchange exchange) throws Exception {
        // Extract the payload (assuming it's a List of Maps containing the encounter data)
        Object payload = exchange.getIn().getBody();

        // Log the payload for debugging purposes
        if (payload == null || !(payload instanceof List) || ((List<?>) payload).isEmpty()) {
            System.err.println("Payload is null or empty: " + exchange.getIn().getBody());
            JSONObject responseJson = new JSONObject();
            responseJson.put("status", "error");
            responseJson.put("message", "Payload is empty or null, cannot insert into database.");
            exchange.getIn().setBody(responseJson.toString());
            exchange.getIn().setHeader(Exchange.CONTENT_TYPE, "application/json");
            return;
        }

        // Database connection details
        String url = "jdbc:postgresql://103.91.65.22:5093/staging";
        String username = "mhnusr";
        String password = "mHnY0uS3r456&890";

        // SQL query to create the table if it doesn't exist
        String createTableSql = "CREATE TABLE IF NOT EXISTS golf.testTable (" +
                                "id VARCHAR(255), " +
                                "class_code VARCHAR(255), " +
                                "class_display VARCHAR(255), " +
                                "identifier_value VARCHAR(255), " +
                                "status VARCHAR(255), " +
                                "period_start TIMESTAMP, " +
                                "service_provider_ref VARCHAR(255), " +
                                "resource_type VARCHAR(255)" +
                                ")";

        // SQL query to insert data in batch
        String sql = "INSERT INTO golf.testTable (id, class_code, class_display, identifier_value, status, period_start, service_provider_ref, resource_type) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        JSONObject responseJson = new JSONObject();
        int totalInserted = 0;

        if (payload instanceof List<?>) {
            List<Map<String, Object>> encounters = (List<Map<String, Object>>) payload;

            // Establish the connection and set schema
            try (Connection connection = DriverManager.getConnection(url, username, password);
                 PreparedStatement setSchemaStatement = connection.prepareStatement("SET search_path TO golf");
                 PreparedStatement createTableStatement = connection.prepareStatement(createTableSql);
                 PreparedStatement preparedStatement = connection.prepareStatement(sql)) {

                // Set the schema and create the table if it doesn't exist
                setSchemaStatement.executeUpdate();
                createTableStatement.executeUpdate();

                // Process each encounter record in the payload and add to batch
                for (Map<String, Object> encounter : encounters) {
                    try {
                        String id = (String) encounter.get("id");
                        Map<String, String> classInfo = (Map<String, String>) encounter.get("class");
                        String classCode = classInfo != null ? classInfo.get("code") : null;
                        String classDisplay = classInfo != null ? classInfo.get("display") : null;
                        List<Map<String, String>> identifiers = (List<Map<String, String>>) encounter.get("identifier");
                        String identifierValue = (identifiers != null && !identifiers.isEmpty()) ? identifiers.get(0).get("value") : null;
                        String status = (String) encounter.get("status");
                        Map<String, String> period = (Map<String, String>) encounter.get("period");
                        String periodStartStr = period != null ? period.get("start") : null;
                        Map<String, String> serviceProvider = (Map<String, String>) encounter.get("serviceProvider");
                        String serviceProviderRef = serviceProvider != null ? serviceProvider.get("reference") : null;
                        String resourceType = (String) encounter.get("resourceType");

                        // Convert periodStartStr to Timestamp
                        Timestamp periodStart = null;
                        if (periodStartStr != null) {
                            try {
                                DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME;
                                LocalDateTime dateTime = LocalDateTime.parse(periodStartStr, formatter);
                                periodStart = Timestamp.valueOf(dateTime);
                            } catch (Exception e) {
                                System.err.println("Error parsing periodStart: " + e.getMessage());
                            }
                        }

                        // Set values and add to batch
                        preparedStatement.setString(1, id);
                        preparedStatement.setString(2, classCode);
                        preparedStatement.setString(3, classDisplay);
                        preparedStatement.setString(4, identifierValue);
                        preparedStatement.setString(5, status);
                        preparedStatement.setTimestamp(6, periodStart);
                        preparedStatement.setString(7, serviceProviderRef);
                        preparedStatement.setString(8, resourceType);
                        preparedStatement.addBatch();

                    } catch (Exception e) {
                        System.err.println("Error processing encounter data: " + e.getMessage());
                    }
                }

                // Execute batch update and count inserted rows
                int[] batchResult = preparedStatement.executeBatch();
                totalInserted = batchResult.length;

                responseJson.put("status", "success");
                responseJson.put("message", "Data inserted successfully.");
                responseJson.put("totalInserted", totalInserted);
            } catch (SQLException e) {
                System.err.println("SQL Error during table creation or schema setting: " + e.getMessage());
                responseJson.put("status", "error");
                responseJson.put("message", "Failed to create table or set schema: " + e.getMessage());
                exchange.getIn().setBody(responseJson.toString());
                exchange.getIn().setHeader(Exchange.CONTENT_TYPE, "application/json");
                return;
            }
        } else {
            responseJson.put("status", "error");
            responseJson.put("message", "Payload is not a valid list of encounter data.");
        }

        exchange.getIn().setBody(responseJson.toString());
        exchange.getIn().setHeader(Exchange.CONTENT_TYPE, "application/json");
    }
}
