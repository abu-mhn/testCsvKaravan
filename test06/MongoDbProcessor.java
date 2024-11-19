import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.bson.Document;
import java.util.ArrayList;  // Add this import

import org.springframework.stereotype.Component;

@Component("MongoDbProcessor")
public class MongoDbProcessor implements Processor {

    @Override
    public void process(Exchange exchange) throws Exception {
        // Connect to MongoDB (adjust host and port as needed)
        try (var mongoClient = MongoClients.create("mongodb://admin:7~)8sPjC.VA3@5.181.132.121:27017")) {
            // Database and collection names
            String dbName = "healthKiosk"; // Replace with your desired database name
            String collectionName = "logHealthKiosk";

            // Access the database; MongoDB will create it if it doesn't exist
            MongoDatabase database = mongoClient.getDatabase(dbName);
            System.out.println("Connected to database: " + dbName);

            // Check if collection exists; create it if not
            if (!database.listCollectionNames().into(new ArrayList<>()).contains(collectionName)) {
                database.createCollection(collectionName);
                System.out.println("Collection created: " + collectionName);
            } else {
                System.out.println("Collection already exists: " + collectionName);
            }

            // Access the collection
            MongoCollection<Document> collection = database.getCollection(collectionName);

            // Extract the message body from the Camel Exchange
            String jsonData = exchange.getIn().getBody(String.class);

            // Insert the JSON data into the MongoDB collection
            Document document = Document.parse(jsonData);
            collection.insertOne(document);
            // System.out.println("Data inserted: " + document.toJson());

            // Set the result back in the exchange to return the inserted data
            exchange.getIn().setBody("Data inserted successfully: " + document.toJson());
        } catch (Exception e) {
            e.printStackTrace();
            exchange.getIn().setBody("Error inserting data into MongoDB: " + e.getMessage());
        }
    }
}
