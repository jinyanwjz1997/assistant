package com.jinyan.assistant.util;

import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class UserToolUtil {

    @Tool("获取当前日期和具体时间")
    public String getCurrentDate(){
        return LocalDate.now().toString();
    }

    @Tool("获取上海当前温度")
    public String getShangHaiWeather() {
        return "30度";
    }

    @Tool("根据温度适配可以玩什么")
    public String getPlayGame(Integer tem) {
        if (tem > 35) {
            return "water!";
        } else if (tem > 20) {
            return "露营";
        } else {
            return "吃火锅！";
        }
    }
}
