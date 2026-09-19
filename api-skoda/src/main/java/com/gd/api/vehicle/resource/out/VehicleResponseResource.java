package com.gd.api.vehicle.resource.out;

public class VehicleResponseResource {

    private String vin;
    private String name;
    private String licensePlate;
    private String state;
    private String devicePlatform;
    private String systemModelId;
    private String title;

    public String getVin() {
        return vin;
    }

    public void setVin(String vin) {
        this.vin = vin;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLicensePlate() {
        return licensePlate;
    }

    public void setLicensePlate(String licensePlate) {
        this.licensePlate = licensePlate;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getDevicePlatform() {
        return devicePlatform;
    }

    public void setDevicePlatform(String devicePlatform) {
        this.devicePlatform = devicePlatform;
    }

    public String getSystemModelId() {
        return systemModelId;
    }

    public void setSystemModelId(String systemModelId) {
        this.systemModelId = systemModelId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
