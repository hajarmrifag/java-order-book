package dev.hajar.orderbook;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DemoIntegrationTest {
    @Test void replayProducesDeterministicHumanReadableTrades() {
        var original=System.out;
        var bytes=new ByteArrayOutputStream();
        try(var output=new PrintStream(bytes,true,StandardCharsets.UTF_8)) {
            System.setOut(output);
            Demo.main(new String[0]);
        } finally { System.setOut(original); }
        assertEquals("TRADE maker=1 taker=3 price=100 quantity=5\n"
                +"TRADE maker=2 taker=3 price=100 quantity=2\nCANCEL 2 true\nACTIVE 0\n",
                bytes.toString(StandardCharsets.UTF_8).replace("\r\n","\n"));
    }
}
