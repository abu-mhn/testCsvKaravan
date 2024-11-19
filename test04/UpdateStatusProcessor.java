package org.camel.karavan.demo.t24092024_3;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.json.JSONObject;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.stereotype.Component;

@Component("UpdateStatusProcessor")
public class UpdateStatusProcessor implements Processor {

    private final MongoDatabase database;
    private final MongoClient mongoClient;

    public UpdateStatusProcessor() {
        // Connect to MongoDB
        Logger.getLogger("org.mongodb.driver").setLevel(Level.WARNING);
        mongoClient = MongoClients.create("mongodb://admin:7~)8sPjC.VA3@5.181.132.121:27017/");
        database = mongoClient.getDatabase("veins");
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        // Accessing the MongoDB collection
        MongoCollection<Document> manifestCollection = database.getCollection("ips_manifest");

        // Get consentId from query parameters
        String consentId = exchange.getIn().getHeader("consentId", String.class);

        if (consentId == null || consentId.isEmpty()) {
            exchange.getIn().setBody("{\"error\": \"consentId query parameter is missing\"}");
            exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 400);
            return;
        }

        // Get new status from request JSON body
        String requestBody = exchange.getIn().getBody(String.class);
        JSONObject jsonObject = new JSONObject(requestBody);

        if (!jsonObject.has("status")) {
            exchange.getIn().setBody("{\"error\": \"New status is missing from the request body\"}");
            exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 400);
            return;
        }

        String newStatus = jsonObject.getString("status");

        // Update the document in MongoDB
        Document filter = new Document("consentId", consentId);
        Document update = new Document("$set", new Document("status", newStatus));

        UpdateResult result = manifestCollection.updateOne(filter, update);

        if (result.getMatchedCount() > 0) {
            exchange.getIn().setBody("{\"message\": \"Status updated successfully\"}");
            exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 200);
        } else {
            exchange.getIn().setBody("{\"error\": \"Consent not found\"}");
            exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 404);
        }
    }
}
