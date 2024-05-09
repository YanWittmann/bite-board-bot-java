package menu.bot;

import lombok.extern.log4j.Log4j2;
import menu.providers.implementations.HochschuleMannheimTagessichtMenuProvider;

import java.util.Arrays;

@Log4j2
public class BiteBoardBotEntrypoint {
    public static void main(String[] args) throws InterruptedException {
        log.info("Starting BiteBoardBot");

        final BiteBoardBot bot = new BiteBoardBot(Arrays.asList(
                new HochschuleMannheimTagessichtMenuProvider()
        ));
    }
}
