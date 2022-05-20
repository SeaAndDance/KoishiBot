package io.github.nickid2018.koishibot.wiki;

import java.io.File;
import java.io.InputStream;

public class PageInfo {

    public WikiInfo info;

    public String prefix;

    public String title;
    public String url;

    public boolean redirected;
    public String titlePast;

    public String shortDescription;
    public InputStream imageStream;
    public File audioFile;

    public boolean isSearched;
    public boolean isRandom;
}
