package io.github.nickid2018.koishibot.message.telegram;

import org.telegram.telegrambots.meta.api.objects.*;

import java.util.ArrayList;
import java.util.List;

public class TelegramMessageData {

    public static final List<String> TYPE_REGARD_AS_TEXTS = List.of(
            "text", "bot_command", "url", "email", "phone_number", "bold",
            "italic", "underline", "strikethrough", "text_link"
    );

    private final List<String> texts = new ArrayList<>();
    private final List<MessageEntity> mentionUsers = new ArrayList<>();

    private final List<PhotoSize> images = new ArrayList<>();

    private InputFile imageSend;
    private InputFile audioSend;

    private Audio audio;
    private int msgID;
    private int quoteID = -1;
    private TelegramMessageData quoteMsg;
    private User quoteUser;

    public static TelegramMessageData fromMessage(Message message) {
        TelegramMessageData data = new TelegramMessageData();
        data.msgID = message.getMessageId();
        List<Integer> textSplit = new ArrayList<>();
        (message.hasPhoto()? message.getCaptionEntities() : message.getEntities()).stream()
                .filter(entity -> !TYPE_REGARD_AS_TEXTS.contains(entity.getType()))
                .forEach(entity -> {
                    textSplit.add(entity.getOffset());
                    textSplit.add(entity.getOffset() + entity.getLength());
                });
        textSplit.add(0);
        textSplit.add((message.hasPhoto()? message.getCaption() : message.getText()).length());
        textSplit.sort(Integer::compareTo);
        for (int i = 0; i < textSplit.size() - 1; i += 2) {
            int start = textSplit.get(i);
            int end = textSplit.get(i + 1);
            if (start == end)
                continue;
            data.texts.add((message.hasPhoto()? message.getCaption() : message.getText()).substring(start, end));
        }
        (message.hasPhoto()? message.getCaptionEntities() : message.getEntities()).stream()
                .filter(entity -> entity.getType().equals("text_mention") || entity.getType().equals("mention"))
                .forEach(data.mentionUsers::add);
        data.images.addAll(message.getPhoto());
        data.audio = message.getAudio();
        if (message.getReplyToMessage() != null) {
            data.quoteMsg = fromMessage(message.getReplyToMessage());
            data.quoteID = data.quoteMsg.msgID;
            data.quoteUser = message.getReplyToMessage().getFrom();
        }
        return data;
    }

    public List<String> getTexts() {
        return texts;
    }

    public List<MessageEntity> getMentionUsers() {
        return mentionUsers;
    }

    public List<PhotoSize> getImages() {
        return images;
    }

    public void setImageSend(InputFile imageSend) {
        this.imageSend = imageSend;
    }

    public InputFile getImageSend() {
        return imageSend;
    }

    public int getQuoteID() {
        return quoteID;
    }

    public void setQuoteID(int quoteID) {
        this.quoteID = quoteID;
    }

    public void setAudio(Audio audio) {
        this.audio = audio;
    }

    public void setAudioSend(InputFile audioSend) {
        this.audioSend = audioSend;
    }

    public InputFile getAudioSend() {
        return audioSend;
    }
}
