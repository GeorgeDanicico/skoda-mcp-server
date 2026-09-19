package com.gd.api.location.controller;

import com.gd.api.location.resource.out.GpsCoordinates;
import com.gd.api.location.resource.out.ParkResponseResource;
import com.gd.api.location.resource.out.ParkingPosition;
import com.gd.api.location.web.in.LocationWebController;
import com.gd.api.location.web.out.LocationClient;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.json.JsonCompareMode.STRICT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

@WebMvcTest(LocationWebController.class)
@ComponentScan(basePackages = {"com.gd.api.location"})
public class LocationWebControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LocationClient client;

    @Test
    public void getLocation() throws Exception {
        ParkResponseResource parkResponseResource = getParkResponseResource();
        Mockito.when(client.findPositionByVin("QMGAG8BEQSY003476")).thenReturn(parkResponseResource);

        String response = """
                {"latitude":12.3,"longitude":4.56,"address":"Rue Red 16, 1000 Brussels, Belgium"}""";

        mockMvc.perform(get("/location/QMGAG8BEQSY003476")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(content().json(response, STRICT));
    }

    private static ParkResponseResource getParkResponseResource() {
        GpsCoordinates gpsCoordinates = new GpsCoordinates();
        gpsCoordinates.setLatitude(12.3);
        gpsCoordinates.setLongitude(4.56);

        ParkingPosition parkingPosition = new ParkingPosition();
        parkingPosition.setGpsCoordinates(gpsCoordinates);
        parkingPosition.setFormattedAddress("Rue Red 16, 1000 Brussels, Belgium");

        ParkResponseResource parkResponseResource = new ParkResponseResource();
        parkResponseResource.setParkingPosition(parkingPosition);

        return parkResponseResource;
    }
}
