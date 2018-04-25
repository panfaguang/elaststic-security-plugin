package com.pan.elasticsearch.es.security.plugin.search.listener;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.common.util.concurrent.ThreadContext;
import org.elasticsearch.index.shard.SearchOperationListener;
import org.elasticsearch.search.internal.SearchContext;

import com.pan.elasticsearch.es.security.plugin.action.OplateActionFilter;
import com.pan.elasticsearch.es.security.plugin.metric.MetricUtil;

public class OplateSearchListener implements SearchOperationListener {
    protected static final Logger log = LogManager.getLogger(OplateActionFilter.class);
    private final ThreadContext threadContext;

    public OplateSearchListener(ThreadContext threadContext) {
        super();
        this.threadContext = threadContext;
    }

    @Override
    public void onQueryPhase(SearchContext searchContext, long tookInNanos) {
        if (threadContext.getHeaders().get("token") != null) {
            MetricUtil.count(threadContext.getHeaders().get("token"));
        }
    }
}
