package org.camel.karavan.demo.t24092024_3;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.bson.Document;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import org.springframework.stereotype.Component;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoClient;
import org.json.JSONObject;
import org.json.JSONArray;
import java.util.logging.Level;
import java.util.logging.Logger;

@Component("TotalProcessor")
public class TotalProcessor implements Processor {

    private final MongoDatabase database;
    private final MongoClient mongoClient;

    public TotalProcessor() {
        // Connect to MongoDB
        Logger.getLogger("org.mongodb.driver").setLevel(Level.WARNING);
        mongoClient = MongoClients.create("mongodb://admin:7~)8sPjC.VA3@5.181.132.121:27017/");
        database = mongoClient.getDatabase("veins");
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        // Get endpoint from the Camel route parameters
        String endpoint = exchange.getIn().getHeader("endpoint", String.class);

        // Check if the collection exists in the database
        if (!collectionExists(endpoint)) {
            exchange.getMessage().setBody(new JSONObject().put("Message", "Endpoint not found").toString());
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 404);
            return;
        }

        // Retrieve the MongoDB collection based on the endpoint
        MongoCollection<Document> collection = database.getCollection(endpoint);

        try {
            // Retrieve query parameters from the request
            String totalParam = exchange.getIn().getHeader("_total", String.class);
            int limit = totalParam != null ? Integer.parseInt(totalParam) : 0;

            // Query database and sort by 'created_date'
            MongoCursor<Document> cursor;
            if (limit > 0) {
                cursor = collection.find().sort(new Document("created_date", -1)).limit(limit).iterator();
            } else {
                cursor = collection.find().sort(new Document("created_date", -1)).iterator();
            }

            // Create a JSON array to store results
            JSONArray resultsArray = new JSONArray();
            while (cursor.hasNext()) {
                Document doc = cursor.next(); // Get the next Document
                resultsArray.put(new JSONObject(doc.toJson())); // Convert and add to JSONArray
            }

            // Construct a response JSON object
            JSONObject responseData = new JSONObject();
            responseData.put("data", resultsArray);

            // Set the response body and status code
            exchange.getMessage().setBody(responseData.toString());
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 200);

        } catch (Exception e) {
            e.printStackTrace();
            JSONObject errorResponse = new JSONObject();
            errorResponse.put("error", "An error occurred");
            errorResponse.put("details",  e.getMessage());
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
