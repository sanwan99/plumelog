package com.plumelog.trace.aspect;


import com.nglog.core.LogMessageThreadLocal;
import com.nglog.core.TraceId;
import com.nglog.core.TraceMessage;
import com.nglog.core.constant.LogMessageConstant;
import com.nglog.core.util.GfJsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;

/**
 * className：AbstractAspect
 * description： TODO
 * time：2020-05-26.11:17
 *
 * @author Tank
 * @version 1.0.0
 */
@Slf4j
public abstract class AbstractAspect {

    public Object aroundExecute(JoinPoint joinPoint) throws Throwable {
        TraceMessage traceMessage = LogMessageThreadLocal.logMessageThreadLocal.get();
        String traceId = TraceId.logTraceID.get();
        if (traceMessage == null || traceId == null) {
            traceMessage = new TraceMessage();
            traceMessage.getPositionNum().set(0);
        }
        traceMessage.setTraceId(traceId);
        traceMessage.setMessageType(joinPoint.getSignature().toString());
        traceMessage.setPosition(LogMessageConstant.TRACE_START);
        traceMessage.getPositionNum().incrementAndGet();
        LogMessageThreadLocal.logMessageThreadLocal.set(traceMessage);
        log.info(LogMessageConstant.TRACE_PRE + GfJsonUtil.toJSONString(traceMessage));
        Object proceed = ((ProceedingJoinPoint) joinPoint).proceed();
        traceMessage.setMessageType(joinPoint.getSignature().toString());
        traceMessage.setPosition(LogMessageConstant.TRACE_END);
        traceMessage.getPositionNum().incrementAndGet();
        log.info(LogMessageConstant.TRACE_PRE + GfJsonUtil.toJSONString(traceMessage));
        return proceed;
    }
}
