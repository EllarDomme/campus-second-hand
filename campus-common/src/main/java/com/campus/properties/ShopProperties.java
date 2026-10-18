package com.campus.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "campus.shop")
@Data
public class ShopProperties {
    private String address;
    private String ak;
}
