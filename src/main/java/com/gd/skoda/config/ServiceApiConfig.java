package com.gd.skoda.config;

import com.gd.skoda.api.ServiceApiMapper;
import com.gd.skoda.api.VehicleApiFacade;
import com.gd.skoda.error.SkodaApiProblems;
import com.gd.skoda.vehicle.VehicleService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The operations of {@code skoda-service-api.yaml} as plain beans, in every profile: the REST
 * controllers ({@code api}) and the MCP tools ({@code mcp}) both call {@link VehicleApiFacade}.
 */
@Configuration(proxyBeanMethods = false)
public class ServiceApiConfig {

    @Bean
    ServiceApiMapper serviceApiMapper() {
        return new ServiceApiMapper();
    }

    @Bean
    VehicleApiFacade vehicleApiFacade(VehicleService vehicleService, ServiceApiMapper serviceApiMapper) {
        return new VehicleApiFacade(vehicleService, serviceApiMapper);
    }

    @Bean
    SkodaApiProblems skodaApiProblems(SkodaProperties properties) {
        return new SkodaApiProblems(properties.publicApi().key());
    }
}
