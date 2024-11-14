import com.mongodb.client.*;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.bson.Document;  // Add this import

import javax.annotation.PreDestroy;

@Component("MongoDbGet")
public class MongoDbGet implements Processor {

    private MongoClient mongoClient;

    // Constructor to initialize MongoClient only once
    public MongoDbGet() {
        // MongoDB connection URI (do not hardcode sensitive credentials in real applications)
        String uri = "mongodb://admin:7~)8sPjC.VA3@5.181.132.121:27017"; 
        mongoClient = MongoClients.create(uri);  // Initialize MongoClient for reuse
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        try {
            // Use the same MongoClient instance for each request
            MongoDatabase database = mongoClient.getDatabase("TestProdDb");
            MongoCollection<Document> collection = database.getCollection("Encounter");

            // Find all documents in the 'Encounter' collection
            FindIterable<Document> appointments = collection.find();

            // Create an ObjectMapper to convert documents to JSON
            ObjectMapper objectMapper = new ObjectMapper();
            ArrayNode jsonArray = objectMapper.createArrayNode();

            // Iterate over the documents and convert them to JSON
            for (Document doc : appointments) {
                jsonArray.add(objectMapper.readTree(doc.toJson()));
            }

            // Set the JSON array as the body in the exchange
            exchange.getIn().setBody(jsonArray.toString());

            // Set the content type to application/json
            exchange.getIn().setHeader(Exchange.CONTENT_TYPE, "application/json");

        } catch (Exception e) {
            // In case of an error, set an error message in JSON format
            ObjectMapper objectMapper = new ObjectMapper();
            Document errorDoc = new Document("error", "Error retrieving data from MongoDB: " + e.getMessage());
            exchange.getIn().setBody(objectMapper.writeValueAsString(errorDoc));
            exchange.getIn().setHeader(Exchange.CONTENT_TYPE, "application/json");
        }
    }

    // Cleanup: close the MongoClient when the application stops
    @PreDestroy
    public void shutdown() {
        if (mongoClient != null) {
            mongoClient.close();  // Close the MongoClient when the application shuts down
        }
    }
}
