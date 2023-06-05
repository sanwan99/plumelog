package com.nglog.http.openfeign;


import com.nglog.core.TraceId;
import feign.RequestInterceptor;
import feign.RequestTemplate;

import static com.nglog.core.constant.LogMessageConstant.TRACE_ID;


/**
 * @author YIJIUE
 */
public class PlumelogFeignInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate requestTemplate) {
        // if it is a timed task, traceId may not exist, so it needs to be generated again
        String traceId = TraceId.getTraceId(TraceId.logTraceID.get());
        requestTemplate.header(TRACE_ID, traceId);
    }
}
