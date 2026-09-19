package com.gd.api.status.resource.out;

public class StatusOverallResponseResource {
    private String doorsLocked;
    private String locked;
    private String doors;
    private String windows;
    private String lights;
    private String reliableLockStatus;

    public String getDoorsLocked() {
        return doorsLocked;
    }

    public void setDoorsLocked(String doorsLocked) {
        this.doorsLocked = doorsLocked;
    }

    public String getLocked() {
        return locked;
    }

    public void setLocked(String locked) {
        this.locked = locked;
    }

    public String getDoors() {
        return doors;
    }

    public void setDoors(String doors) {
        this.doors = doors;
    }

    public String getWindows() {
        return windows;
    }

    public void setWindows(String windows) {
        this.windows = windows;
    }

    public String getLights() {
        return lights;
    }

    public void setLights(String lights) {
        this.lights = lights;
    }

    public String getReliableLockStatus() {
        return reliableLockStatus;
    }

    public void setReliableLockStatus(String reliableLockStatus) {
        this.reliableLockStatus = reliableLockStatus;
    }
}
