package org.camel.karavan.demo.t24092024_3;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.bson.Document;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoClient;
import com.mongodb.client.model.Filters;
import org.springframework.stereotype.Component;
import org.json.JSONArray; // Importing JSONArray
import org.json.JSONObject;
import java.util.logging.Level;
import java.util.logging.Logger;

@Component("IdFilterProcessor")
public class IdFilterProcessor implements Processor {

    private final MongoDatabase database;
    private final MongoClient mongoClient;

    public IdFilterProcessor() {
        // Connect to MongoDB
        Logger.getLogger("org.mongodb.driver").setLevel(Level.WARNING);
        mongoClient = MongoClients.create("mongodb://admin:7~)8sPjC.VA3@5.181.132.121:27017/");
        database = mongoClient.getDatabase("veins");
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        // Get endpoint and id from the Camel route parameters
        String endpoint = exchange.getIn().getHeader("endpoint", String.class);
        String id = exchange.getIn().getHeader("id", String.class);

        // Check if the collection exists in the database
        if (!collectionExists(endpoint)) {
            exchange.getMessage().setBody(new JSONObject().put("Message", "Endpoint not found").toString());
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 404);
            return;
        }

        // Retrieve the MongoDB collection based on the endpoint
        MongoCollection<Document> collection = database.getCollection(endpoint);

        try {
            if (id != null && !id.isEmpty()) {
                // Create a JSON array to store results
                JSONArray resultsArray = new JSONArray();

                // Fetch the specific document by ID
                Document result = collection.find(Filters.eq("_id", id)).first();

                // Convert and add to JSONArray
                if (result != null) {
                    resultsArray.put(result); 
                    // Create a JSON object to store the result
                    JSONObject responseData = new JSONObject();
                    responseData.put("data", resultsArray);

                    exchange.getMessage().setBody(responseData.toString());
                    exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 200);
                } else {
                    exchange.getMessage().setBody(new JSONObject().put("Message", "Document not found").toString());
                    exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 404);
                }
            } else {
                exchange.getMessage().setBody(new JSONObject().put("Message", "ID is required").toString());
                exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 400);
            }
        } catch (Exception e) {
            e.printStackTrace();
            JSONObject errorResponse = new JSONObject();
            errorResponse.put("error", "An error occurred");
            errorResponse.put("details", e.getMessage());
            exchange.getMessage().setBody(errorResponse.toString());
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 500);
        }
    }

    /**
     * Check if a collection exists in the database
     *
     * @param collectionName the name of the collection to check
     * @return true if the collection exists, false otherwise
     */
    private boolean collectionExists(String collectionName) {
        for (String name : database.listCollectionNames()) {
            if (name.equalsIgnoreCase(collectionName)) {
                return true;
            }
        }
        return false;
    }
}
