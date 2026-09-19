package com.gd.api.vehicle.controller;

import com.gd.api.vehicle.resource.out.VehicleGarageResponseResource;
import com.gd.api.vehicle.resource.out.VehicleResponseResource;
import com.gd.api.vehicle.web.in.VehicleWebController;
import com.gd.api.vehicle.web.out.VehicleClient;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;

import static org.springframework.test.json.JsonCompareMode.STRICT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

@WebMvcTest(VehicleWebController.class)
@ComponentScan(basePackages = {"com.gd.api.vehicle"})
public class VehicleWebControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VehicleClient client;

    @Test
    public void findAllVehicles() throws Exception {
        List<VehicleResponseResource> vehicles = new ArrayList<>();
        vehicles.add(getVehicleResponseResource());

        VehicleGarageResponseResource vehicleGarageResponseResource = new VehicleGarageResponseResource();
        vehicleGarageResponseResource.setVehicles(vehicles);

        Mockito.when(client.getVehicles()).thenReturn(vehicleGarageResponseResource);


        String response = """
                [
                    {
                        "vin": "QMGAG8BEQSY003476",
                        "name": "Enyaq",
                        "licensePlate": "ABC123",
                        "state": "ACTIVATED",
                        "devicePlatform": "WCAR",
                        "systemModelId": "5AZFF2",
                        "title": "Škoda Enyaq"
                    }
                ]
                """;

        this.mockMvc.perform(get("/vehicle")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(content().json(response, STRICT));
    }

    private static VehicleResponseResource getVehicleResponseResource() {
        VehicleResponseResource vehicleResponseResource = new VehicleResponseResource();
        vehicleResponseResource.setVin("QMGAG8BEQSY003476");
        vehicleResponseResource.setName("Enyaq");
        vehicleResponseResource.setLicensePlate("ABC123");
        vehicleResponseResource.setState("ACTIVATED");
        vehicleResponseResource.setDevicePlatform("WCAR");
        vehicleResponseResource.setSystemModelId("5AZFF2");
        vehicleResponseResource.setTitle("Škoda Enyaq");
        return vehicleResponseResource;
    }
}
