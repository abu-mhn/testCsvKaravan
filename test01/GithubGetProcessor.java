import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import org.springframework.stereotype.Component;

@Component("GithubGetProcessor")
public class GithubGetProcessor implements Processor {

    // URL to the raw CSV file on GitHub
    private static final String FILE_URL = "https://raw.githubusercontent.com/abu-mhn/testCsvKaravan/main/test01/Spotify%20Most%20Streamed%20Songs.csv";

    @Override
    public void process() {
        try {
            URL url = new URL(FILE_URL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        Processor processor = new ProcessorImpl();
        processor.process();
    }
}
