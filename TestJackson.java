import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
public class TestJackson {
    public static void main(String[] args) throws Exception {
        String json = "{\"a\":1}{\"b\":2}";
        System.out.println(new ObjectMapper().readTree(json).toString());
    }
}
