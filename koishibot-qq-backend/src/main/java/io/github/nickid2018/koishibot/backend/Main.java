package io.github.nickid2018.koishibot.backend;

import io.github.nickid2018.koishibot.message.qq.QQEnvironment;
import io.github.nickid2018.koishibot.network.Connection;
import net.mamoe.mirai.Bot;
import net.mamoe.mirai.BotFactory;
import net.mamoe.mirai.utils.BotConfiguration;
import net.mamoe.mirai.utils.LoggerAdapters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.InetAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public class Main {

    public static final Logger LOGGER = LoggerFactory.getLogger("KoishiBot QQ Backend");

    private static QQEnvironment environment;

    public static void main(String[] args) {
        try {
            Settings.loadSettings();
        } catch (Exception e) {
            LOGGER.error("Failed to load settings.", e);
            return;
        }

        AtomicBoolean nudgeEnabled = new AtomicBoolean();
        Bot bot = BotFactory.INSTANCE.newBot(Settings.id, Settings.password, new BotConfiguration() {{
            setHeartbeatStrategy(HeartbeatStrategy.STAT_HB);
            setWorkingDir(new File("qq"));
            fileBasedDeviceInfo();
            setBotLoggerSupplier(bot -> LoggerAdapters.asMiraiLogger(LoggerFactory.getLogger("Mirai Bot")));
            setNetworkLoggerSupplier(bot -> LoggerAdapters.asMiraiLogger(LoggerFactory.getLogger("Mirai Net")));
            if (Settings.protocol != null) {
                MiraiProtocol miraiProtocol = MiraiProtocol.valueOf(Settings.protocol.toUpperCase());
                setProtocol(miraiProtocol);
                nudgeEnabled.set(miraiProtocol == MiraiProtocol.ANDROID_PHONE || miraiProtocol == MiraiProtocol.IPAD);
            } else
                nudgeEnabled.set(true);
        }});
        bot.login();

        int retry = 0;
        while (bot.isOnline() && retry < 20) {
            CompletableFuture<Void> disconnected = new CompletableFuture<>();
            BackendDataListener listener = new BackendDataListener(() -> environment, disconnected);
            try {
                Connection connection = Connection.connectToTcpServer(
                        listener.registry, listener, InetAddress.getLocalHost(), Settings.delegatePort);
                environment = new QQEnvironment(bot, nudgeEnabled.get(), connection);
                retry = 0;
                disconnected.get();
            } catch (Exception e) {
                LOGGER.error("Failed to link.", e);
            }
            LOGGER.info("Disconnected. Waiting 1min to reconnect.");
            retry++;
            try {
                Thread.sleep(60000);
            } catch (InterruptedException ignored) {
            }
        }

        LOGGER.error(retry == 20 ? "Retries > 20, can't link to delegate. Shutting down." : "Bot offline. Shutting down.");
        bot.close();
    }
}