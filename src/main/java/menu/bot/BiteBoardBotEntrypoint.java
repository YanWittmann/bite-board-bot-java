package menu.bot;

import lombok.extern.log4j.Log4j2;
import menu.providers.implementations.HochschuleMannheimTagessichtMenuProvider;
import menu.service.ApplicationStateLogger;

import java.util.Arrays;

@Log4j2
public class BiteBoardBotEntrypoint {
    public static void main(String[] args) throws InterruptedException {
        ApplicationStateLogger.logApplicationSplashScreen();

        new BiteBoardBot(Arrays.asList(
                new HochschuleMannheimTagessichtMenuProvider()
        ));
    }
}
