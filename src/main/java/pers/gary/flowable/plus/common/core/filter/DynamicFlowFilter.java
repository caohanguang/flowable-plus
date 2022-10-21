package pers.gary.flowable.plus.common.core.filter;

import com.alibaba.fastjson.JSON;
import pers.gary.flowable.plus.common.util.PlusUtil;
import pers.gary.flowable.plus.common.entity.FlowInfo;
import pers.gary.flowable.plus.config.FlowEnum;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.*;
import java.util.stream.Stream;

@Slf4j
public class DynamicFlowFilter implements Filter {

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        Map<String,String> originHeaderMap = new HashMap<>();
        HttpServletRequest request = (HttpServletRequest)servletRequest;
        Enumeration<String> headerNames = request.getHeaderNames();
        while(headerNames.hasMoreElements()){
            String headerName = headerNames.nextElement();
            String headerValue;
            if(StringUtils.isNotBlank(headerValue = request.getHeader(headerName))){
                originHeaderMap.put(headerName.toLowerCase(),headerValue);
            }
        }
        log.info("flow filter headers =============================> {}",originHeaderMap);
        Map<String,String> flowInfoMap = new HashMap<>();
        Stream.of(FlowEnum.values()).forEach(c->{
            if(originHeaderMap.containsKey(c.getName().toLowerCase())){
                log.info("flow filter FlowEnum =============================>" +
                        " {} : {}",c.getName(),c.getName().toLowerCase());
                flowInfoMap.put(c.getName(),originHeaderMap.get(c.getName().toLowerCase()));
            }
        });

        if(!flowInfoMap.isEmpty()){
            String json = JSON.toJSONString(flowInfoMap);
            log.info("init flow by http header {}",json);
            FlowInfo flowInfo = JSON.parseObject(json,FlowInfo.class);
            PlusUtil.initFLowInfo(flowInfo);
        }

        filterChain.doFilter(servletRequest,servletResponse);
    }
}
