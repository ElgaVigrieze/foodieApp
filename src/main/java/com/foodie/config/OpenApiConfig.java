package com.foodie.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI foodieOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("FoodieApp API")
                        .description("""
                                REST API for FoodieApp - a meal planning and nutrition tracking application 
                                with AI-powered features for effortless food logging.
                                
                                ## Key Features
                                - **Photo Nutrition Analysis** - Upload food photos for instant nutrition estimates
                                - **Smart Recipe Import** - Extract recipes from URLs using AI
                                - **Voice Food Logging** - Natural language parsing for meal logging
                                - **Barcode Scanning** - Lookup products via Open Food Facts
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Elga Vigrieze")
                                .url("https://github.com/ElgaVigrieze"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server().url("/").description("Current server")
                ));
    }
}
