package com.sky.utils;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.sky.properties.ShopProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class BaiduMapUtil {
    @Autowired
    private ShopProperties shopProperties;

    public String getCoordinate(String address){

        Map<String, String> params = new HashMap<>();
        params.put("address", address);
        params.put("output", "json");
        params.put("ak",shopProperties.getAk());

        //拿到百度回的话
        String result = HttpClientUtil.doGet("https://api.map.baidu.com/geocoding/v3/",params);

        JSONObject jsonObject = JSONObject.parseObject(result);

        //判断百度是不是回应成功了
        if(jsonObject.getIntValue("status")!=0){
            throw new RuntimeException("地理编码失败");
        }

        JSONObject location = jsonObject.getJSONObject("result").getJSONObject("location");
        double lng = location.getDouble("lng");
        double lat = location.getDouble("lat");

        //返回经度纬度

        return lat + "," + lng;
    }

    public long getDistance(String origin, String destination){
        Map<String,String> params = new HashMap();
        params.put("origin", origin);
        params.put("destination", destination);
        params.put("ak", shopProperties.getAk());

        //拿到百度返回的话
        String result = HttpClientUtil.doGet("https://api.map.baidu.com/directionlite/v1/driving",params);

        JSONObject jsonObject = JSONObject.parseObject(result);

        //判断百度是否回应成功了
        if(jsonObject.getIntValue("status")!=0){
            throw new RuntimeException("距离计算失败");
        }

        JSONArray routes = jsonObject.getJSONObject("result").getJSONArray("routes");
        long distance = routes.getJSONObject(0).getLong("distance");

        return distance;
    }
}
