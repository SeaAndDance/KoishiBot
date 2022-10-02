package io.github.nickid2018.koishibot.message.kook;

import io.github.kookybot.JavaBaseClass;
import io.github.kookybot.client.Client;
import io.github.kookybot.contract.Self;
import io.github.kookybot.contract.TextChannel;
import io.github.nickid2018.koishibot.core.BotLoginData;
import io.github.nickid2018.koishibot.message.MessageManager;
import io.github.nickid2018.koishibot.message.MessageSender;
import io.github.nickid2018.koishibot.message.api.*;
import kotlin.Unit;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.concurrent.Future;

public class KOOKEnvironment implements Environment {

    private final Client kookClient;
    private final Self self;
    private final MessageSender sender;
    private final MessageManager manager;
    private final KOOKMessagePublisher publisher;

    public KOOKEnvironment(BotLoginData data) {
        kookClient = new Client(data.token(), configureScope -> Unit.INSTANCE);
        self = JavaBaseClass.utils.connectWebsocket(kookClient);

        publisher = new KOOKMessagePublisher(this);
        sender = new MessageSender(this, false);
        manager = new MessageManager(this);
    }

    @Override
    public AtMessage at() {
        return new KOOKAt(this);
    }

    @Override
    public ChainMessage chain() {
        return new KOOKChain(this);
    }

    @Override
    public TextMessage text() {
        return new KOOKText(this);
    }

    @Override
    public ImageMessage image() {
        return new KOOKImage(this);
    }

    @Override
    public AudioMessage audio() {
        // Unsupported
        return null;
    }

    @Override
    public ForwardMessage forwards() {
        // Unsupported
        return null;
    }

    @Override
    public MessageEntry messageEntry() {
        // Unsupported
        return null;
    }

    @Override
    public QuoteMessage quote() {
        return new KOOKQuote(this);
    }

    @Override
    public ServiceMessage service() {
        // Unsupported
        return null;
    }

    @Override
    public UserInfo getUser(String id, boolean isStranger) {
        if (!id.startsWith("kook.user"))
            return null;
        String user = id.substring(9);
        return self.getGuilds().values().stream()
                .map(guild -> guild.getGuildUser(user))
                .map(guildUser -> new KOOKUser(this, guildUser))
                .findFirst()
                .orElse(null);
    }

    @Override
    public GroupInfo getGroup(String id) {
        return id.startsWith("kook.group") ? new KOOKTextChannel(this,
                (TextChannel) self.getChannel(id.substring(10))) : null;
    }

    @Override
    public String getBotId() {
        return "kook.user" + self.getId();
    }

    @Override
    public MessageEventPublisher getEvents() {
        return publisher;
    }

    @Override
    public MessageSender getMessageSender() {
        return sender;
    }

    @Override
    public MessageManager getManager() {
        return manager;
    }

    @Override
    public boolean forwardMessageSupported() {
        return false;
    }

    @Override
    public boolean audioSupported() {
        return false;
    }

    @Override
    public boolean quoteSupported() {
        return true;
    }

    @Override
    public String getEnvironmentName() {
        return "开黑啦";
    }

    @Override
    public String getEnvironmentUserPrefix() {
        return "kook.user";
    }

    @Override
    public Future<File[]> parseAudioFile(String suffix, URL url) throws IOException {
        return null;
    }

    @Override
    public void close() {
        kookClient.close();
    }

    public Client getKookClient() {
        return kookClient;
    }

    public Self getSelf() {
        return self;
    }
}
