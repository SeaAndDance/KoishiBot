package io.github.nickid2018.koishibot.wiki;

import com.google.gson.*;
import io.github.nickid2018.koishibot.KoishiBotMain;
import io.github.nickid2018.koishibot.util.ImageRenderer;
import io.github.nickid2018.koishibot.util.MutableBoolean;
import io.github.nickid2018.koishibot.util.RegexUtil;
import io.github.nickid2018.koishibot.util.WebUtil;
import org.apache.http.client.methods.HttpGet;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.regex.Pattern;

public class WikiInfo {

    public static final String WIKI_META = "action=query&format=json&meta=siteinfo&siprop=extensions%7Cgeneral%7Cinterwikimap";
    public static final String QUERY_PAGE = "action=query&format=json&prop=info%7Cimageinfo%7Cextracts%7Cpageprops&inprop=url&iiprop=url&" +
                                            "ppprop=description%7Cdisplaytitle%7Cdisambiguation%7Cinfoboxes&explaintext&" +
                                            "exsectionformat=plain&exchars=200&redirects";
    public static final String QUERY_PAGE_NOE = "action=query&format=json&inprop=url&iiprop=url&prop=info%7Cimageinfo&redirects";
    public static final String QUERY_PAGE_TEXT = "action=parse&format=json&prop=text";
    public static final String WIKI_SEARCH = "action=query&format=json&list=search&srwhat=text&srlimit=1&srenablerewrite";
    public static final String WIKI_RANDOM = "action=query&format=json&list=random";

    public static final String EDIT_URI_STR = "<link rel=\"EditURI\" type=\"application/rsd+xml\" href=\"";

    public static final Pattern USER_ANONYMOUS = Pattern.compile("User:\\d{1,3}(\\.\\d{1,3}){3}");

    public static final Set<String> SUPPORTED_IMAGE = WebUtil.SUPPORTED_IMAGE;
    public static final Set<String> NEED_TRANSFORM_IMAGE = new HashSet<>(
            Arrays.asList("webp", "ico")
    );
    public static final Set<String> NEED_TRANSFORM_AUDIO = new HashSet<>(
            Arrays.asList("oga", "ogg", "flac", "mp3", "wav")
    );

    private static final Map<String, WikiInfo> STORED_WIKI_INFO = new HashMap<>();
    private static final Map<WikiInfo, String> STORED_INTERWIKI_SOURCE_URL = new HashMap<>();

    private final Map<String, String> additionalHeaders;
    private String url;

    private boolean available;
    private boolean useTextExtracts;
    private String baseURI;
    private String articleURL;
    private String script;
    private final Map<String, String> interWikiMap = new HashMap<>();

    public WikiInfo(String url) {
        this.url = url;
        additionalHeaders = new HashMap<>();
        STORED_WIKI_INFO.put(url, this);
    }

    public WikiInfo(String url, Map<String, String> additionalHeaders) {
        this.url = url;
        this.additionalHeaders = additionalHeaders;
        STORED_WIKI_INFO.put(url, this);
    }

    public void checkAvailable() throws IOException {
        if (!available) {
            JsonObject object;
            String data = checkAndGet(url + WIKI_META);
            try {
                object = JsonParser.parseString(data).getAsJsonObject();
            } catch (JsonSyntaxException e) {
                // Not a valid API entrance, try to get the api.php
                int index = data.indexOf(EDIT_URI_STR);
                if (index < 0)
                    throw new IOException("无法获取信息，可能网站不是一个MediaWiki或被验证码阻止");
                String sub = data.substring(index + EDIT_URI_STR.length());
                url= sub.substring(0, sub.indexOf("?") + 1);
                STORED_WIKI_INFO.put(url, this);
                data = checkAndGet(url + WIKI_META);
                try {
                    object = JsonParser.parseString(data).getAsJsonObject();
                } catch (JsonSyntaxException ex) {
                    throw new IOException("无法获取信息，可能网站不是一个MediaWiki或被验证码阻止");
                }
            }

            JsonArray extensions = object.getAsJsonObject("query").getAsJsonArray("extensions");
            for (JsonElement element : extensions) {
                String name = element.getAsJsonObject().get("name").getAsString();
                if (name.equals("TextExtracts")) {
                    useTextExtracts = true;
                    break;
                }
            }
            String server = WebUtil.getDataInPathOrNull(object, "query.general.server");
            String realURL;
            if (server != null && server.startsWith("/"))
                realURL = url.split("/")[0] + server;
            else
                realURL = server;
            articleURL = realURL + WebUtil.getDataInPathOrNull(object, "query.general.articlepath");
            script = realURL + WebUtil.getDataInPathOrNull(object, "query.general.script");
            baseURI = "https://" + new URL(articleURL).getHost();

            if (!getInterWikiDataFromPage()) {
                JsonArray interwikiMap = object.getAsJsonObject("query").getAsJsonArray("interwikimap");
                for (JsonElement element : interwikiMap) {
                    JsonObject obj = element.getAsJsonObject();
                    String url = obj.get("url").getAsString();
                    String prefix = obj.get("prefix").getAsString();
                    interWikiMap.put(prefix, url);
                    WikiInfo info = new WikiInfo(url.contains("?") ?
                            url.substring(0, url.lastIndexOf('?') + 1) : url + "?");
                    STORED_WIKI_INFO.put(url, info);
                    STORED_INTERWIKI_SOURCE_URL.put(info, url);
                }
            }

            available = true;
        }
    }

    public PageInfo parsePageInfo(String title, int pageID, String prefix) throws Exception {
        if (title != null && title.isEmpty())
            throw new IOException("无效的wiki查询");

        try {
            checkAvailable();
        } catch (IOException e) {
            if (STORED_INTERWIKI_SOURCE_URL.containsKey(this)) {
                PageInfo pageInfo = new PageInfo();
                pageInfo.info = this;
                pageInfo.prefix = prefix;
                pageInfo.title = title;
                pageInfo.url = STORED_INTERWIKI_SOURCE_URL.get(this)
                        .replace("$1", title);
                pageInfo.shortDescription = "目标可能不是一个MediaWiki，已自动转换为网址链接";
                return pageInfo;
            } else
                throw e;
        }

        if (prefix != null && prefix.split(":").length > 5)
            throw new IOException("请求跳转wiki次数过多");

        if (title != null && title.contains(":")){
            String namespace = title.split(":")[0];
            if (interWikiMap.containsKey(namespace)) {
                WikiInfo skip = STORED_WIKI_INFO.get(interWikiMap.get(namespace));
                return skip.parsePageInfo(title.split(":", 2)[1], 0,
                        (prefix == null ? "" : prefix + ":") + namespace);
            }
        }

        if (title != null && title.equalsIgnoreCase("~rd"))
            return random(prefix);
        if (title != null && title.equalsIgnoreCase("~iw"))
            return interwikiList();

        String section = null;
        if (title != null && title.contains("#")) {
            String[] titleSplit = title.split("#", 2);
            title = titleSplit[0];
            section = titleSplit[1];
        }

        JsonObject query;
        String queryFormat = useTextExtracts ? QUERY_PAGE : QUERY_PAGE_NOE;
        try {
            if (title == null)
                query = JsonParser.parseString(checkAndGet(url + queryFormat + "&pageids=" + pageID))
                        .getAsJsonObject().getAsJsonObject("query");
            else
                query = JsonParser.parseString(checkAndGet(url + queryFormat + "&titles=" + WebUtil.encode(title)))
                        .getAsJsonObject().getAsJsonObject("query");
        } catch (JsonSyntaxException e) {
            throw new IOException("返回了错误的数据，可能机器人被验证码阻止");
        }

        if (query == null)
            throw new IOException("无法获取数据");

        PageInfo pageInfo = new PageInfo();
        pageInfo.info = this;
        pageInfo.prefix = prefix;
        pageInfo.title = title;
        tryRedirect(query.get("redirects"), pageInfo);
        tryRedirect(query.get("normalized"), pageInfo);

        JsonObject pages = query.getAsJsonObject("pages");
        if (pages == null)
            throw new IOException("未查询到任何页面");

        for (Map.Entry<String, JsonElement> entry : pages.entrySet()) {
            String id = entry.getKey();
            JsonObject object = entry.getValue().getAsJsonObject();
            if (object.has("special")) {
                pageInfo.url = articleURL.replace("$1", WebUtil.encode(title));
                pageInfo.shortDescription = "特殊页面";
                return pageInfo;
            }
            pageInfo.url = script + "?curid=" + id;
            if (object.has("missing")) {
                if (!object.has("known")) {
                    if (title != null)
                        return search(title, prefix);
                    throw new IOException("无法找到页面");
                } else {
                    pageInfo.url = articleURL.replace("$1", WebUtil.encode(title));
                    pageInfo.shortDescription = "<页面无内容>";
                    // Special:MyPage -> [MessageContext:IP]
                    if (RegexUtil.match(USER_ANONYMOUS, pageInfo.title))
                        pageInfo.title = "匿名用户页";
                }
            } else {
                pageInfo.title = title = object.get("title").getAsString();
                if (object.has("pageprops") && object.getAsJsonObject("pageprops").has("disambiguation"))
                    pageInfo.shortDescription = getDisambiguationText(title);
                else {
                    if (useTextExtracts && object.has("extract") && section == null)
                        pageInfo.shortDescription = resolveText(object.get("extract").getAsString().trim());
                    else
                        pageInfo.shortDescription = resolveText(getMarkdown(title, section, pageInfo));
                    pageInfo.infobox = InfoBoxShooter.getInfoBoxShot(pageInfo.url, baseURI);
                }
            }
            if (object.has("imageinfo")) {
                JsonArray array = object.getAsJsonArray("imageinfo");
                if (array.size() > 0) {
                    String type = array.get(0).getAsJsonObject().get("descriptionurl").getAsString();
                    String suffix = type.substring(Math.min(type.length() - 1, type.lastIndexOf('.') + 1))
                            .toLowerCase(Locale.ROOT);
                    if (SUPPORTED_IMAGE.contains(suffix) || NEED_TRANSFORM_IMAGE.contains(suffix))
                        pageInfo.imageStream = new URL(array.get(0).getAsJsonObject().get("url").getAsString()).openStream();
                    else if (NEED_TRANSFORM_AUDIO.contains(suffix)) {
                        pageInfo.shortDescription = "音频信息，将分割后发送";
                        pageInfo.audioFiles = KoishiBotMain.INSTANCE.executor.submit(() -> AudioTransform.transform(
                                suffix, new URL(array.get(0).getAsJsonObject().get("url").getAsString())));
                    }
                }
            }
        }

        if (pageInfo.url.startsWith("//"))
            pageInfo.url = "https:" + pageInfo.url;

        return pageInfo;
    }

    private PageInfo random(String prefix) throws Exception {
        JsonObject data = WebUtil.fetchDataInJson(getWithHeader(url + WIKI_RANDOM)).getAsJsonObject();
        PageInfo info =  parsePageInfo(Objects.requireNonNull(
                WebUtil.getDataInPathOrNull(data, "query.random.0.title")), 0, prefix);
        info.isRandom = true;
        return info;
    }

    private PageInfo search(String key, String prefix) throws IOException {
        JsonObject data = WebUtil.fetchDataInJson(getWithHeader(
                url + WIKI_SEARCH + "&srsearch=" + WebUtil.encode(key))).getAsJsonObject();
        JsonArray search = data.getAsJsonObject("query").getAsJsonArray("search");
        PageInfo info = new PageInfo();
        info.prefix = prefix;
        info.info = this;
        info.isSearched = true;
        if (search.size() != 0)
            info.title = search.get(0).getAsJsonObject().get("title").getAsString();
        return info;
    }

    private PageInfo interwikiList() throws IOException {
        PageInfo info = new PageInfo();
        BufferedImage image = ImageRenderer.renderMap(interWikiMap, "跨wiki前缀", "目标",
                ImageRenderer.Alignment.RIGHT, ImageRenderer.Alignment.LEFT);
        ByteArrayOutputStream boas = new ByteArrayOutputStream();
        ImageIO.write(image, "png", boas);
        info.imageStream = new ByteArrayInputStream(boas.toByteArray());
        return info;
    }

    private void tryRedirect(JsonElement entry, PageInfo info) {
        if (entry instanceof JsonArray && info.title != null) {
            for (JsonElement element : (JsonArray) entry) {
                JsonObject object = element.getAsJsonObject();
                String from = object.get("from").getAsString();
                if (from.equalsIgnoreCase(info.title)) {
                    String to = element.getAsJsonObject().get("to").getAsString();
                    if (info.titlePast == null)
                        info.titlePast = info.title;
                    info.title = to;
                    info.redirected = true;
                    break;
                }
            }
        }
    }

    private HttpGet getWithHeader(String url) {
        HttpGet get = new HttpGet(WebUtil.mirror(url));
        get.setHeader("Accept-Language", "zh-CN,zh;q=0.8,zh-TW;q=0.7,zh-HK;q=0.5,en-US;q=0.3,en;q=0.2");
        for (Map.Entry<String, String> entry : additionalHeaders.entrySet())
            get.setHeader(entry.getKey(), entry.getValue());
        return get;
    }

    private String checkAndGet(String url) throws IOException {
        String data = WebUtil.fetchDataInText(getWithHeader(url), true);
        // Blocked by CloudFlare
        if (data.contains("Attention Required! | Cloudflare"))
            throw new IOException("机器人被CloudFlare拦截");
        // Blocked by Tencent
        if (data.contains("腾讯T-Sec Web应用防火墙(WAF)"))
            throw new IOException("机器人被T-Sec Web防火墙拦截");
        return data;
    }

    private String resolveText(String source) {
        source = source.replaceAll("(\n){2,}+", "\n");
        int index = 0;
        int blankets = 0;
        boolean quote = false;
        boolean subQuote = false;
        INDEX_FIND: for (; index < source.length(); index++) {
            char now = source.charAt(index);
            switch (now) {
                case '《':
                case '「':
                case '<':
                case '[':
                case '{':
                case '(':
                case '（':
                    blankets++;
                    break;
                case '>':
                case ']':
                case '}':
                case '」':
                case '》':
                case ')':
                case '）':
                    blankets--;
                    break;
                case '"':
                    quote = !quote;
                    break;
                case '\'':
                    subQuote = !subQuote;
                    break;
                case '?':
                case '!':
                case '.':
                case '？':
                case '！':
                case '。':
                    if (blankets == 0 && !quote && !subQuote && index > 15)
                        break INDEX_FIND;
                case '\n':
                    if (index > 20)
                        break INDEX_FIND;
            }
        }
        return source.substring(0, Math.min(source.length(), index + 1));
    }

    private String getMarkdown(String page, String section, PageInfo info) throws IOException {
        JsonObject data = WebUtil.fetchDataInJson(getWithHeader(url + QUERY_PAGE_TEXT + "&page="
                        + WebUtil.encode(page)))
                .getAsJsonObject();
        String html = WebUtil.getDataInPathOrNull(data, "parse.text.*");
        if (html == null)
            throw new IOException("页面无内容");
        String markdown = WebUtil.getAsMarkdownClean(html);
        if (section != null) {
            StringBuilder builder = new StringBuilder();
            MutableBoolean bool = new MutableBoolean(false);
            new BufferedReader(new StringReader(markdown)).lines().forEach(s -> {
                if (s.startsWith("##"))
                    bool.setValue(s.startsWith("## " + section));
                else if (bool.getValue())
                    builder.append(s).append("\n");
            });
            String sectionData = builder.toString().trim();
            if (!sectionData.isEmpty()) {
                info.url += "#" + WebUtil.encode(section);
                return sectionData;
            }
        }
        return markdown;
    }

    private String getDisambiguationText(String page) throws IOException {
        JsonObject data = WebUtil.fetchDataInJson(getWithHeader(url + QUERY_PAGE_TEXT + "&page="
                        + WebUtil.encode(page)))
                .getAsJsonObject();
        String html = WebUtil.getDataInPathOrNull(data, "parse.text.*");
        if (html == null)
            throw new IOException("页面无内容");

        List<String> items = new ArrayList<>();

        Document document = Jsoup.parse(html);
        Elements elements = document.getElementsByTag("a");

        for (Element element : elements) {
            String title = element.ownText();
            if (title.isEmpty())
                title = element.attr("title");
            items.add(title);
        }

        return "消歧义页面，" + page + "可以指:\n" + String.join(", ", items);
    }

    private boolean getInterWikiDataFromPage(){
        try {
            String data = WebUtil.fetchDataInText(
                    getWithHeader(articleURL.replace("$1", "Special:Interwiki")));
            Document page = Jsoup.parse(data);
            Elements interWikiSection = page.getElementsByClass("mw-interwikitable-row");
            for (Element entry : interWikiSection) {
                Element prefixEntry = entry.getElementsByClass("mw-interwikitable-prefix").get(0);
                Element urlEntry = entry.getElementsByClass("mw-interwikitable-url").get(0);
                String prefix = prefixEntry.ownText();
                String url = urlEntry.ownText();
                interWikiMap.put(prefix, url);
                WikiInfo info = new WikiInfo(url.contains("?") ?
                        url.substring(0, url.lastIndexOf('?') + 1) : url + "?");
                STORED_WIKI_INFO.put(url, info);
                STORED_INTERWIKI_SOURCE_URL.put(info, url);
            }
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}