package com.gd.api.vehicle.resource.out;

import java.util.List;

public class VehicleGarageResponseResource {
    private List<VehicleResponseResource> vehicles;

    public List<VehicleResponseResource> getVehicles() {
        return vehicles;
    }

    public void setVehicles(List<VehicleResponseResource> vehicles) {
        this.vehicles = vehicles;
    }
}
