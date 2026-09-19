package com.gd.skoda.mcp.gateway;

import com.gd.skoda.mcp.SkodaVehicleFacade;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Blocks gateway readiness until the expensive MySkoda login has completed once. */
@Profile("gateway")
@Component
public class GatewayWarmup implements ApplicationRunner {
    private final SkodaVehicleFacade facade;

    public GatewayWarmup(SkodaVehicleFacade facade) {
        this.facade = facade;
    }

    @Override
    public void run(ApplicationArguments args) {
        facade.warmUp();
    }
}
