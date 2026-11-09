package com.stanley.ledger.api;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Serves the console index page at /console/ (Spring serves static folders only by file name). */
@Configuration
public class ConsoleWebConfig implements WebMvcConfigurer {

  @Override
  public void addViewControllers(ViewControllerRegistry registry) {
    registry.addRedirectViewController("/console", "/console/");
    registry.addViewController("/console/").setViewName("forward:/console/index.html");
  }
}
