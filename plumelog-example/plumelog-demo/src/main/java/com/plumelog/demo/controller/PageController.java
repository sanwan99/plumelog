package com.plumelog.demo.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.slf4j.MDC;
import java.util.Random;


@Slf4j
@Controller
public class PageController {

    @RequestMapping("/")
    public String index(String data) {
        return "index";
    }

    @RequestMapping("/hello")
    public String hello(String data) {


        for (int i = 0; i <20 ; i++) {
            Integer nextInt1 = new Random().nextInt(99);
            Integer nextInt = new Random().nextInt(99);
            MDC.put("testTime", nextInt.toString());
            MDC.put("testTime1", nextInt1.toString());
            log.info("hello"+i);
        }




        return "index";
    }
}
