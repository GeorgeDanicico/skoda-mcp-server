package com.gd.api.status.domain;

public record Status(String doorsLocked, String locked, String doors, String windows, String lights,
                     String reliableLockStatus, String sunroof, String trunk, String bonnet, String carCapturedTimestamp) {
}
