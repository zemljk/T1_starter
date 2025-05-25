package ru.t1.mystarterlogs.component;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "http.logging")
public record HttpLoggingProperties
        (boolean enabled,
         String level)
{

}
