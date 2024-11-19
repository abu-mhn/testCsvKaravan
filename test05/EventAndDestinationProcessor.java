package com.example.camel.processors;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.http.HttpMethods;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component("EventAndDestinationProcessor") // Ensure the bean name matches your route reference
public class EventAndDestinationProcessor implements Processor {

    @Autowired
    private ProducerTemplate producerTemplate;

    @Override
    public void process(Exchange exchange) throws Exception {
        Object body = exchange.getIn().getBody();
        if (body instanceof Map) {
            Map<?, ?> bodyMap = (Map<?, ?>) body;
            extractEventCodingCode(bodyMap, exchange);
            extractDestinationCode(bodyMap, exchange);
        }

        String destination = exchange.getProperty("destination", String.class);
        if (destination != null) {
            String url = "https://veinscdr.mhnexus.com/baseR4/Endpoint?name=veinsBridge&organization=" + destination;
            Exchange responseExchange = producerTemplate.send("direct:dynamicHttpCall", e -> {
                e.getIn().setHeader(Exchange.HTTP_METHOD, HttpMethods.POST); // Changed to POST
                e.getIn().setHeader(Exchange.CONTENT_TYPE, "application/json");
                e.getIn().setBody(exchange.getIn().getBody()); // Ensure the body is suitable for POST
                e.getIn().setHeader(Exchange.HTTP_URI, url);
            });
            exchange.getIn().setBody(responseExchange.getMessage().getBody());
        }
    }

    private void extractEventCodingCode(Map<?, ?> bodyMap, Exchange exchange) {
        List<?> entries = (List<?>) bodyMap.get("entry");
        if (entries != null && !entries.isEmpty()) {
            Map<?, ?> firstEntry = (Map<?, ?>) entries.get(0);
            Map<?, ?> resource = (Map<?, ?>) firstEntry.get("resource");
            if (resource != null) {
                Map<?, ?> eventCoding = (Map<?, ?>) resource.get("eventCoding");
                if (eventCoding != null) {
                    String eventCode = (String) eventCoding.get("code");
                    if (eventCode != null) {
                        exchange.setProperty("eventCoding", eventCode);
                    }
                }
            }
        }
    }

    private void extractDestinationCode(Map<?, ?> bodyMap, Exchange exchange) {
        List<?> entries = (List<?>) bodyMap.get("entry");
        if (entries != null && !entries.isEmpty()) {
            Map<?, ?> firstEntry = (Map<?, ?>) entries.get(0);
            Map<?, ?> resource = (Map<?, ?>) firstEntry.get("resource");
            if (resource != null) {
                List<?> destinations = (List<?>) resource.get("destination");
                if (destinations != null && !destinations.isEmpty()) {
                    Map<?, ?> firstDestination = (Map<?, ?>) destinations.get(0);
                    Map<?, ?> receiver = (Map<?, ?>) firstDestination.get("receiver");
                    if (receiver != null) {
                        Map<?, ?> identifier = (Map<?, ?>) receiver.get("identifier");
                        if (identifier != null) {
                            Map<?, ?> type = (Map<?, ?>) identifier.get("type");
                            if (type != null) {
                                List<?> codings = (List<?>) type.get("coding");
                                if (codings != null && !codings.isEmpty()) {
                                    Map<?, ?> firstCoding = (Map<?, ?>) codings.get(0);
                                    String destinationCode = (String) firstCoding.get("code");
                                    if (destinationCode != null) {
                                        exchange.setProperty("destination", destinationCode);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
