package io.github.nickid2018.koishibot.resolver;

import com.google.zxing.*;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import io.github.nickid2018.koishibot.core.TempFileSystem;
import io.github.nickid2018.koishibot.message.DelegateEnvironment;
import io.github.nickid2018.koishibot.message.MessageResolver;
import io.github.nickid2018.koishibot.message.ResolverName;
import io.github.nickid2018.koishibot.message.Syntax;
import io.github.nickid2018.koishibot.message.api.AbstractMessage;
import io.github.nickid2018.koishibot.message.api.ImageMessage;
import io.github.nickid2018.koishibot.message.api.MessageContext;
import io.github.nickid2018.koishibot.util.AsyncUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@ResolverName("qr-code")
@Syntax(syntax = "~qrcode encode [文本]", help = "将文本转变成字符串")
@Syntax(syntax = "~qrcode decode", help = "将图片内的二维码解析成字符串", rem = "需要信息中含至少一张图片")
public class QRCodeResolver extends MessageResolver {

    private static final Map<EncodeHintType, Object> HINTS = new HashMap<>();
    private static final MultiFormatWriter WRITER = new MultiFormatWriter();
    private static final MultiFormatReader READER = new MultiFormatReader();

    static {
        HINTS.put(EncodeHintType.CHARACTER_SET, "utf-8");
        HINTS.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
        HINTS.put(EncodeHintType.MARGIN, 2);
    }

    public QRCodeResolver() {
        super("~qrcode");
    }

    @Override
    public boolean resolveInternal(String key, MessageContext context, Object resolvedArguments, DelegateEnvironment environment) {
        key = key.trim();
        String[] data = key.split(" ", 2);
        switch (data[0].toLowerCase(Locale.ROOT)) {
            case "encode":
                if (data.length != 2)
                    return false;
                encode(data[1], context, environment);
                return true;
            case "decode":
                return decode(context, environment);
        }
        return false;
    }

    private void encode(String message, MessageContext context, DelegateEnvironment environment) {
        AsyncUtil.execute(() -> {
            try {
                BitMatrix matrix = WRITER.encode(message, BarcodeFormat.QR_CODE, 200, 200, HINTS);
                BufferedImage image = new BufferedImage(matrix.getWidth(), matrix.getHeight(), BufferedImage.TYPE_INT_BGR);
                for (int x = 0; x < matrix.getWidth(); x++)
                    for (int y = 0; y < matrix.getHeight(); y++)
                        if (matrix.get(x, y))
                            image.setRGB(x, y, 0);
                        else
                            image.setRGB(x, y, 0xFFFFFFFF);
                File file = TempFileSystem.createTmpFile("qrcode", ".png");
                try (FileOutputStream os = new FileOutputStream(file)) {
                    ImageIO.write(image, "png", os);
                }
                environment.getMessageSender().sendMessageRecallable(context, environment.newImage(file.toURI().toURL()));
            } catch (Exception e) {
                environment.getMessageSender().onError(e, "qrcode.encode", context, true);
            }
        });
    }

    private boolean decode(MessageContext context, DelegateEnvironment environment) {
        ImageMessage image = null;
        for (AbstractMessage message : context.message().getMessages()) {
            if (message instanceof ImageMessage) {
                image = (ImageMessage) message;
                break;
            }
        }
        if (image == null)
            return false;
        ImageMessage finalImage = image;
        AsyncUtil.execute(() -> {
            try {
                BufferedImage qrcode = ImageIO.read(finalImage.getImage());
                RGBLuminanceSource source = new RGBLuminanceSource(qrcode.getWidth(), qrcode.getHeight(),
                        qrcode.getRGB(0, 0, qrcode.getWidth(), qrcode.getHeight(),
                                null, 0, qrcode.getWidth()));
                Result result = READER.decode(new BinaryBitmap(new HybridBinarizer(source)));
                environment.getMessageSender().sendMessageRecallable(context, environment.newChain(
                        environment.newQuote(context.message()),
                        environment.newText(result.getText())
                ));
            } catch (NotFoundException e) {
                environment.getMessageSender().sendMessageRecallable(context, environment.newChain(
                        environment.newQuote(context.message()),
                        environment.newText("图片内找不到有效的二维码")
                ));
            } catch (Exception e) {
                environment.getMessageSender().onError(e, "qrcode.decode", context, true);
            }
        });
        return true;
    }
}
