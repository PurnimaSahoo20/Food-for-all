package com.pns.oauthserver.model.dto;

public record Food(
        String id,
        String Contact_Number,
        String Email,
        String Expiry_time,
        String Food_ItemName,
        Integer Food_Quantity,
        String Pickup_Date,
        String Pickup_time,
        String Restaurant_address,
        String Restaurant_name,
        String Restaurant_ownerName,
        String status
) {

}
