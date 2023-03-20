package io.github.nickid2018.koishibot.message.qq;

import io.github.nickid2018.koishibot.message.api.UnsupportedMessage;
import io.github.nickid2018.koishibot.message.api.*;
import io.github.nickid2018.koishibot.network.Connection;
import net.mamoe.mirai.Bot;
import net.mamoe.mirai.contact.Group;
import net.mamoe.mirai.contact.User;
import net.mamoe.mirai.message.data.*;

public class QQEnvironment extends Environment {

    private final Bot bot;
    private final QQMessagePublisher publisher;
    private final boolean nudgeEnabled;

    public QQEnvironment(Bot bot, boolean nudgeEnabled, Connection connection) {
        super(connection);
        this.bot = bot;
        this.nudgeEnabled = nudgeEnabled;

        publisher = new QQMessagePublisher(this);

        botID = "qq.user" + bot.getId();
        environmentName = "QQ";
        environmentUserPrefix = "qq.user";
        forwardMessageSupported = true;
        audioSupported = true;
        audioToFriendSupported = false;
        quoteSupported = true;
        needAntiFilter = true;
        audioSilk = true;
    }

    public User getQQUser(String id, boolean isStranger) {
        return id.startsWith("qq.user") ? (isStranger ?
                bot.getStranger(Long.parseLong(id.substring(7))) :
                bot.getFriend(Long.parseLong(id.substring(7)))
        ) : null;
    }

    public Group getQQGroup(String id) {
        return id.startsWith("qq.group") ? bot.getGroup(Long.parseLong(id.substring(8))) : null;
    }

    @Override
    public UserInfo getUser(String id, boolean isStranger) {
        return id.startsWith("qq.user") ?
                new QQUser(this, getQQUser(id, isStranger), isStranger) :
                null;
    }

    @Override
    public GroupInfo getGroup(String id) {
        return id.startsWith("qq.group") ? new QQGroup(this, getQQGroup(id)) : null;
    }

    public void close() {
        bot.close();
    }

    public boolean isNudgeEnabled() {
        return nudgeEnabled;
    }

    public Bot getBot() {
        return bot;
    }

    public AbstractMessage cast(Message message) {
        if (message instanceof MessageChain chain)
            return new QQChain(this, chain);
        else if (message instanceof At at)
            return new QQAt(this, at);
        else if (message instanceof PlainText text)
            return new QQText(this, text);
        else if (message instanceof Audio audio)
            return new QQAudio(this, audio);
        else if (message instanceof Image image)
            return new QQImage(this, image);
        else if (message instanceof QuoteReply quoteReply)
            return new QQQuote(this, quoteReply);
        else if (message instanceof net.mamoe.mirai.message.data.ForwardMessage forwardMessage)
            return new QQForward(this, forwardMessage);
        else if (message instanceof RichMessage rich)
            return new QQService(this, rich);
        else
            return new UnsupportedMessage(this);
    }

    public static void send(UserInfo user, AbstractMessage message) {
        User qq = ((QQUser) user).getUser();
        qq.sendMessage(((QQMessage) message).getMessage());
    }

    public static void send(GroupInfo group, AbstractMessage message) {
        Group qq = ((QQGroup) group).getGroup();
        qq.sendMessage(((QQMessage) message).getMessage());
    }
}
