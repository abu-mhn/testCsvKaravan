import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

@Component("DateTime")
public class DateTime implements Processor {
    @Override
    public void process(Exchange exchange) throws Exception {
        // Get the current date and time in UTC
        ZonedDateTime utcNow = ZonedDateTime.now(ZoneId.of("UTC"));

        // Adjust the timestamp to Singapore timezone (+08:00)
        ZonedDateTime singaporeTime = utcNow.withZoneSameInstant(ZoneId.of("Asia/Singapore"));

        // Format the timestamp as per the required pattern
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
        String formattedDateTime = singaporeTime.format(formatter);

        // Set the formatted timestamp in the exchange's body
        exchange.getIn().setBody(formattedDateTime);  // This will return the formatted string
    }
}
