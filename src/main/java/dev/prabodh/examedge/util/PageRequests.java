package dev.prabodh.examedge.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

public final class PageRequests {

    public static final int DEFAULT_PAGE_SIZE = 10;

    private PageRequests() {
    }

    public static PageRequest of(int page, Sort sort) {
        return PageRequest.of(Math.max(0, page), DEFAULT_PAGE_SIZE, sort);
    }

    public static PageRequest of(int page) {
        return PageRequest.of(Math.max(0, page), DEFAULT_PAGE_SIZE);
    }

    public static String normalizeSearch(String raw) {
        return raw == null ? "" : raw.trim();
    }
}
