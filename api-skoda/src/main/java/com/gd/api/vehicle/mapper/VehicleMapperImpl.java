package com.gd.api.vehicle.mapper;

import com.gd.api.resource.VehicleWebResponseResource;
import com.gd.api.vehicle.domain.Vehicle;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class VehicleMapperImpl implements VehicleMapper {
    @Override
    public VehicleWebResponseResource toWebResource(final Vehicle vehicle) {
        return VehicleWebResponseResource.builder()
                .vin(vehicle.vin())
                .name(vehicle.name())
                .licensePlate(vehicle.licensePlate())
                .state(vehicle.state())
                .devicePlatform(vehicle.devicePlatform())
                .systemModelId(vehicle.systemModelId())
                .title(vehicle.title())
                .build();
    }

    @Override
    public List<VehicleWebResponseResource> toWebResource(final List<Vehicle> vehicles) {
        return vehicles.stream().map(this::toWebResource).collect(Collectors.toList());
    }
}
