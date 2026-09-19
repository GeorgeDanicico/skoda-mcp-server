package com.gd.api;

import com.gd.api.airconditioning.web.out.AirConditioningClient;
import com.gd.api.charging.web.out.ChargingClient;
import com.gd.api.location.web.out.LocationClient;
import com.gd.api.range.web.out.RangeClient;
import com.gd.api.status.web.out.StatusClient;
import com.gd.api.vehicle.web.out.VehicleClient;
import com.gd.api.vehicleaccess.web.out.VehicleAccessClient;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

public class ApiRuntimeHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        hints.proxies().registerJdkProxy(AirConditioningClient.class);
        hints.proxies().registerJdkProxy(ChargingClient.class);
        hints.proxies().registerJdkProxy(LocationClient.class);
        hints.proxies().registerJdkProxy(RangeClient.class);
        hints.proxies().registerJdkProxy(StatusClient.class);
        hints.proxies().registerJdkProxy(VehicleClient.class);
        hints.proxies().registerJdkProxy(VehicleAccessClient.class);
    }
}
