package com.gd.api.charging.resource.out;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ChargingSettingsResponseResource {
    private int targetStateOfChargeInPercent;
    private int batteryCareModeTargetValueInPercent;
    private String preferredChargeMode;
    private List<String> availableChargeModes;
    private String chargingCareMode;
    private String autoUnlockPlugWhenCharged;
    private String maxChargeCurrentAc;
}
