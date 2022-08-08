package io.github.nickid2018.koishibot.module.wiki;

import io.github.nickid2018.koishibot.core.TempFileSystem;
import io.github.nickid2018.koishibot.util.web.WebPageRenderer;
import io.github.nickid2018.koishibot.util.web.WebUtil;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.WebElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.Future;

public class WikiPageShooter {

    public static final Logger WIKI_PAGE_LOGGER = LoggerFactory.getLogger("Wiki Page Shooter");

    public static final String[] SUPPORT_INFOBOX = new String[] {
            "notaninfobox", "infoboxtable", "infoboxSpecial", "infotemplatebox", "infobox2",
            "tpl-infobox", "portable-infobox", "toccolours", "infobox"
    };

    private static Document fetchWikiPage(String url) throws IOException {
        URLConnection connection = new URL(url).openConnection();
        connection.addRequestProperty("Accept-Language", "zh-CN,zh;q=0.8,zh-TW;q=0.7,zh-HK;q=0.5,en-US;q=0.3,en;q=0.2");
        connection.addRequestProperty("User-Agent", WebUtil.chooseRandomUA());
        String htmlData = IOUtils.toString(connection.getInputStream(), StandardCharsets.UTF_8);
        return Jsoup.parse(htmlData);
    }

    public static Future<File> getInfoBoxShot(String url, String baseURI) {
        if (WebPageRenderer.getExecutor() != null)
            return WebPageRenderer.getExecutor().submit(() -> getInfoBoxShotInternal(url, baseURI));
        return null;
    }

    public static Future<File> getDisAmbiguousShot(String url, String baseURI) {
        if (WebPageRenderer.getExecutor() != null)
            return WebPageRenderer.getExecutor().submit(() -> getDisAmbiguousShotInternal(url, baseURI));
        return null;
    }

    public static Future<File> getSectionShot(String url, String baseURI, String section) {
        if (WebPageRenderer.getExecutor() != null)
            return WebPageRenderer.getExecutor().submit(() -> getSectionShotInternal(url, baseURI, section));
        return null;
    }

    private static File getInfoBoxShotInternal(String url, String baseURI) throws IOException {
        File data = TempFileSystem.getTmpFileBuffered("infobox", url);
        if (data != null)
            return data;
        Document doc = fetchWikiPage(url);
        Element element = null;
        String className = null;
        for (String name : SUPPORT_INFOBOX) {
            Elements elements = doc.getElementsByClass(name);
            Optional<Element> elementFind = elements.stream().filter(elementNow -> elementNow.classNames().contains(name)).findFirst();
            if (elementFind.isPresent()) {
                element = elementFind.get();
                className = name;
                break;
            }
        }
        if (element == null) {
            WIKI_PAGE_LOGGER.info("URL {} has no infobox.", url);
            return null;
        } else
            WIKI_PAGE_LOGGER.info("URL {} has an infobox, element class is {}", url, className);
        File srcFile = cleanAndRender(baseURI, doc, element);
        File png = TempFileSystem.createTmpFileBuffered("infobox", url, "infobox", "png", false);
        return chopImage(srcFile, png, By.className(className));
    }

    private static File getDisAmbiguousShotInternal(String url, String baseURI) throws IOException {
        File data = TempFileSystem.getTmpFileBuffered("disam", url);
        if (data != null)
            return data;
        Document doc = fetchWikiPage(url);
        Elements elements = doc.getElementsByClass("mw-parser-output");
        if (elements.size() != 1)
            return null;
        Element element = elements.get(0);
        File srcFile = cleanAndRender(baseURI, doc, element);
        File png = TempFileSystem.createTmpFileBuffered(
                "disam", url, "disam", "png", false);
        return chopImage(srcFile, png, By.id("mw-content-text"));
    }

    private static File getSectionShotInternal(String url, String baseURI, String section) throws IOException {
        File data = TempFileSystem.getTmpFileBuffered("section", url + "-" + section);
        if (data != null)
            return data;
        Document doc = fetchWikiPage(url);
        Elements elements = doc.getElementsByClass("mw-headline");
        Element found = null;
        for (Element element : elements) {
            if (element.text().equalsIgnoreCase(section)) {
                found = element.parent();
                break;
            }
        }
        if (found == null)
            return null;
        if (found.nextElementSibling() == null)
            return null;
        String tagName = found.tagName();
        Elements renderElements = new Elements(found);
        Element nowElement = found;
        while (nowElement.nextElementSibling() != null && !nowElement.nextElementSibling().tagName().equals(tagName)) {
            nowElement = nowElement.nextElementSibling();
            renderElements.add(nowElement);
        }
        Element element = found.parent().clone();
        element.children().forEach(Element::remove);
        found.parent().parent().appendChild(element);
        renderElements.forEach(element::appendChild);
        File srcFile = cleanAndRender(baseURI, doc, element);
        File png = TempFileSystem.createTmpFileBuffered(
                "section", url + "-" + section, "section", "png", false);
        return chopImage(srcFile, png, By.id("mw-content-text"));
    }

    private static File cleanAndRender(String baseURI, Document doc, Element element) throws IOException {
        while (!element.equals(doc.body())) {
            Element parent = element.parent();
            for (Element child : parent.children())
                if (!child.equals(element))
                    child.remove();
            element = parent;
            if (element.tagName().equals("template")) {
                Element clone = element.child(0).clone();
                element.parent().appendChild(clone);
                element = clone;
            }
        }
        Queue<Element> bfs = new LinkedList<>();
        bfs.offer(element);
        while (!bfs.isEmpty()) {
            Element now = bfs.poll();
            now.removeClass("mw-collapsible");
            now.removeClass("mw-collapsed");
            now.removeClass("collapsible");
            now.removeClass("collapsed");
            now.children().forEach(bfs::offer);
        }
        doc.body().addClass("heimu_toggle_on");
        doc.head().prependChild(new Element("base").attr("href", baseURI));
        Element heimuToggle = new Element("style").text("""
                        body.heimu_toggle_on .heimu, body.heimu_toggle_on .heimu rt {
                          background-color: rgba(37,37,37,0.13) !important;
                        }
                        """);
        doc.head().appendChild(heimuToggle);
        File html = TempFileSystem.createTmpFileAndCreate("htm", "html");
        try (Writer writer = new FileWriter(html)) {
            IOUtils.write(doc.html(), writer);
        }
        WebPageRenderer.getDriver().manage().window().setSize(new Dimension(0, 0));
        WebPageRenderer.getDriver().get(html.getAbsolutePath());
        TempFileSystem.unlockFileAndDelete(html);
        return WebPageRenderer.getDriver().getFullPageScreenshotAs(OutputType.FILE);
    }

    @NotNull
    private static File chopImage(File srcFile, File png, By element) throws IOException {
        BufferedImage image = ImageIO.read(srcFile);
        try {
            WebElement element2 = WebPageRenderer.getDriver().findElement(element);
            BufferedImage sub = image.getSubimage(element2.getLocation().x,
                    element2.getLocation().y, element2.getSize().width, element2.getSize().height);
            ImageIO.write(sub, "png", png);
            srcFile.delete();
        } catch (Exception e) {
            throw new IOException("无法渲染信息框", e);
        }

        return png;
    }
}
