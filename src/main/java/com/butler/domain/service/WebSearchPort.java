package com.butler.domain.service;

import java.util.List;

/** 联网搜索端口：返回结构化网页结果。换搜索引擎只换实现。 */
public interface WebSearchPort {

    List<WebResult> search(String query, int count);

    record WebResult(String title, String url, String snippet, String summary,
                     String siteName, String publishTime, int authInfoLevel) {}
}
