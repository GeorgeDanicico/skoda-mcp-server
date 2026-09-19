package com.gd.api.vehicleaccess.controller;

import com.gd.api.location.domain.Location;
import com.gd.api.location.service.LocationClientService;
import com.gd.api.vehicleaccess.resource.out.HonkAndFlashRequestResource;
import com.gd.api.vehicleaccess.resource.out.VehicleAccessSecurityRequestResource;
import com.gd.api.vehicleaccess.resource.out.VehiclePositionRequestResource;
import com.gd.api.vehicleaccess.web.in.VehicleAccessWebController;
import com.gd.api.vehicleaccess.web.out.VehicleAccessClient;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VehicleAccessWebController.class)
@ComponentScan(basePackages = {"com.gd.api.vehicleaccess"})
class VehicleAccessWebControllerTest {

    private static final String VIN = "QMGAG8BEQSY003476";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VehicleAccessClient client;

    @MockitoBean
    private LocationClientService locationClientService;

    @Test
    void flash() throws Exception {
        Mockito.when(locationClientService.findLocationByVin(VIN))
                .thenReturn(new Location(17.292954, 2.87205, "Brussels"));

        mockMvc.perform(post("/vehicle-access/{vin}/flash", VIN))
                .andExpect(status().isAccepted());

        verify(client).honkAndFlash(VIN, new HonkAndFlashRequestResource(
                "FLASH", new VehiclePositionRequestResource(17.292954, 2.87205)));
    }

    @Test
    void honkAndFlash() throws Exception {
        Mockito.when(locationClientService.findLocationByVin(VIN))
                .thenReturn(new Location(17.292954, 2.87205, "Brussels"));

        mockMvc.perform(post("/vehicle-access/{vin}/honk-and-flash", VIN))
                .andExpect(status().isAccepted());

        verify(client).honkAndFlash(VIN, new HonkAndFlashRequestResource(
                "HONK_AND_FLASH", new VehiclePositionRequestResource(17.292954, 2.87205)));
    }

    @Test
    void lock() throws Exception {
        mockMvc.perform(post("/vehicle-access/{vin}/lock", VIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"spin":"1234"}
                                """))
                .andExpect(status().isAccepted());

        verify(client).lock(VIN, new VehicleAccessSecurityRequestResource("1234"));
    }

    @Test
    void unlock() throws Exception {
        mockMvc.perform(post("/vehicle-access/{vin}/unlock", VIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"spin":"4321"}
                                """))
                .andExpect(status().isAccepted());

        verify(client).unlock(VIN, new VehicleAccessSecurityRequestResource("4321"));
    }

    @Test
    void rejectInvalidSpin() throws Exception {
        mockMvc.perform(post("/vehicle-access/{vin}/unlock", VIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"spin":"12ab"}
                                """))
                .andExpect(status().isBadRequest());
    }
}
