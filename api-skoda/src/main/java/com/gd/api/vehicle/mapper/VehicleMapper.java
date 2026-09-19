package com.gd.api.vehicle.mapper;

import com.gd.api.resource.VehicleWebResponseResource;
import com.gd.api.vehicle.domain.Vehicle;

import java.util.List;

public interface VehicleMapper {
    VehicleWebResponseResource toWebResource(final Vehicle vehicle);

    List<VehicleWebResponseResource> toWebResource(final List<Vehicle> vehicles);
}
