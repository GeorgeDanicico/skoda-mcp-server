package com.gd.api.status.resource.out;

public class StatusResponseResource {
    private StatusOverallResponseResource overall;
    private StatusDetailResponseResource detail;
    private String carCapturedTimestamp;

    public StatusOverallResponseResource getOverall() {
        return overall;
    }

    public void setOverall(StatusOverallResponseResource overall) {
        this.overall = overall;
    }

    public StatusDetailResponseResource getDetail() {
        return detail;
    }

    public void setDetail(StatusDetailResponseResource detail) {
        this.detail = detail;
    }

    public String getCarCapturedTimestamp() {
        return carCapturedTimestamp;
    }

    public void setCarCapturedTimestamp(String carCapturedTimestamp) {
        this.carCapturedTimestamp = carCapturedTimestamp;
    }
}
