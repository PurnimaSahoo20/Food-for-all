package com.pns.oauthserver.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pns.oauthserver.model.dto.Food;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.util.*;
@Service
public class OrderManageService {
    String sampleData = """
              [{
                "id": "9ba45448-a1bb-4b51-add2-df7638f87621",
                "Contact_Number": "647-336-5543",
                "Email": "lrowena0@apple.com",
                "Expiry_time": "4:34 AM",
                "Food_ItemName": "Lamb - Shanks",
                "Food_Quantity": 20,
                "Pickup_Date": "4/14/2025",
                "Pickup_time": "6:09 PM",
                "Restaurant_address": "11th Floor",
                "Restaurant_name": "Bednar-Lynch",
                "Restaurant_ownerName": "Linet Rowena",
                "status": "Pending"
              }, {
                "id": "ed448c2d-2336-42ba-bbe9-523de7fe165d",
                "Contact_Number": "102-868-1284",
                "Email": "bmilbourn1@com.com",
                "Expiry_time": "5:07 PM",
                "Food_ItemName": "Tomatillo",
                "Food_Quantity": 51,
                "Pickup_Date": "4/7/2025",
                "Pickup_time": "7:51 PM",
                "Restaurant_address": "Suite 13",
                "Restaurant_name": "Funk-Little",
                "Restaurant_ownerName": "Brittan Milbourn",
                "status": "Delivered"
              }, {
                "id": "6112155d-9d3b-44d6-a848-5244a2ff58a4",
                "Contact_Number": "821-470-1324",
                "Email": "cmacgown2@walmart.com",
                "Expiry_time": "10:58 AM",
                "Food_ItemName": "Beans - Butter Lrg Lima",
                "Food_Quantity": 52,
                "Pickup_Date": "6/21/2025",
                "Pickup_time": "9:10 PM",
                "Restaurant_address": "12th Floor",
                "Restaurant_name": "Bartell and Sons",
                "Restaurant_ownerName": "Cosmo MacGown",
                "status": "Delivered"
              }, {
                "id": "5c8aa02b-9cc5-4cee-8526-b543872b85e5",
                "Contact_Number": "987-823-5293",
                "Email": "apaulino3@yahoo.com",
                "Expiry_time": "12:18 AM",
                "Food_ItemName": "Soup - Tomato Mush. Florentine",
                "Food_Quantity": 66,
                "Pickup_Date": "6/3/2025",
                "Pickup_time": "5:14 AM",
                "Restaurant_address": "Room 1661",
                "Restaurant_name": "Schneider, Muller and Bartoletti",
                "Restaurant_ownerName": "Ammamaria Paulino",
                "status": "Delivered"
              }, {
                "id": "6481b1c3-e083-407c-a60b-32e359acce56",
                "Contact_Number": "857-322-3786",
                "Email": "hspedding4@cyberchimps.com",
                "Expiry_time": "12:41 AM",
                "Food_ItemName": "Ecolab - Hobart Upr Prewash Arm",
                "Food_Quantity": 27,
                "Pickup_Date": "7/15/2025",
                "Pickup_time": "4:00 AM",
                "Restaurant_address": "PO Box 9078",
                "Restaurant_name": "Braun-Gutkowski",
                "Restaurant_ownerName": "Helli Spedding",
                "status": "Delivered"
              }, {
                "id": "19f4cdb3-7453-40a5-8dca-84147ff93d06",
                "Contact_Number": "444-572-0289",
                "Email": "kdyzart5@reuters.com",
                "Expiry_time": "4:27 AM",
                "Food_ItemName": "Cheese - Cambozola",
                "Food_Quantity": 22,
                "Pickup_Date": "1/1/2025",
                "Pickup_time": "12:13 PM",
                "Restaurant_address": "PO Box 28096",
                "Restaurant_name": "Kilback-Mueller",
                "Restaurant_ownerName": "Karla Dyzart",
                "status": "Pending"
              }, {
                "id": "3349c3dd-d933-422e-8bb3-926711b831ed",
                "Contact_Number": "414-887-6731",
                "Email": "dmaclaughlin6@netscape.com",
                "Expiry_time": "4:53 AM",
                "Food_ItemName": "Bar - Sweet And Salty Chocolate",
                "Food_Quantity": 6,
                "Pickup_Date": "5/6/2025",
                "Pickup_time": "4:52 PM",
                "Restaurant_address": "Apt 1954",
                "Restaurant_name": "O'Conner and Sons",
                "Restaurant_ownerName": "Denys MacLaughlin",
                "status": "Pending"
              }, {
                "id": "47fb7db1-c164-45c3-8f62-bd28e3c47b53",
                "Contact_Number": "429-554-8892",
                "Email": "vyesinin7@clickbank.net",
                "Expiry_time": "11:55 PM",
                "Food_ItemName": "Cheese - Bocconcini",
                "Food_Quantity": 40,
                "Pickup_Date": "12/3/2024",
                "Pickup_time": "4:51 PM",
                "Restaurant_address": "PO Box 59498",
                "Restaurant_name": "Turner, O'Keefe and Hackett",
                "Restaurant_ownerName": "Verina Yesinin",
                "status": "Delivered"
              }, {
                "id": "6d62d699-511f-4191-8f12-c9ac3f309687",
                "Contact_Number": "747-875-2262",
                "Email": "hsolomon8@wsj.com",
                "Expiry_time": "10:14 PM",
                "Food_ItemName": "Pepper - Red Chili",
                "Food_Quantity": 66,
                "Pickup_Date": "6/9/2025",
                "Pickup_time": "11:42 PM",
                "Restaurant_address": "Suite 88",
                "Restaurant_name": "Metz and Sons",
                "Restaurant_ownerName": "Helli Solomon",
                "status": "Delivered"
              }, {
                "id": "4f1c5ae2-a27e-4da7-adfb-aac79d4b60a5",
                "Contact_Number": "595-995-8720",
                "Email": "gpiddlehinton9@indiegogo.com",
                "Expiry_time": "11:46 AM",
                "Food_ItemName": "Pasta - Fett Alfredo, Single Serve",
                "Food_Quantity": 9,
                "Pickup_Date": "5/9/2025",
                "Pickup_time": "3:38 AM",
                "Restaurant_address": "Room 773",
                "Restaurant_name": "Oberbrunner, Kuvalis and Cassin",
                "Restaurant_ownerName": "Ginger Piddlehinton",
                "status": "Delivered"
              }, {
                "id": "33c3a7c5-c4c6-4070-a547-add4d7a8eea6",
                "Contact_Number": "296-731-7036",
                "Email": "hweedona@dyndns.org",
                "Expiry_time": "3:59 PM",
                "Food_ItemName": "Pork - Smoked Back Bacon",
                "Food_Quantity": 30,
                "Pickup_Date": "9/7/2025",
                "Pickup_time": "2:13 AM",
                "Restaurant_address": "Apt 1355",
                "Restaurant_name": "Welch-Lesch",
                "Restaurant_ownerName": "Halli Weedon",
                "status": "Pending"
              }, {
                "id": "acab6fd3-2154-491e-b9b3-24f277725622",
                "Contact_Number": "714-401-5900",
                "Email": "lfokerb@histats.com",
                "Expiry_time": "11:48 PM",
                "Food_ItemName": "Chips - Doritos",
                "Food_Quantity": 2,
                "Pickup_Date": "6/25/2025",
                "Pickup_time": "4:46 PM",
                "Restaurant_address": "PO Box 27201",
                "Restaurant_name": "Rempel, Hagenes and Heidenreich",
                "Restaurant_ownerName": "Lora Foker",
                "status": "Accepted"
              }, {
                "id": "0d7aabbb-dd44-4663-b098-96a176a7c371",
                "Contact_Number": "351-602-7450",
                "Email": "brannc@pinterest.com",
                "Expiry_time": "1:24 PM",
                "Food_ItemName": "Halibut - Steaks",
                "Food_Quantity": 62,
                "Pickup_Date": "8/15/2025",
                "Pickup_time": "3:47 PM",
                "Restaurant_address": "Room 341",
                "Restaurant_name": "Durgan-Block",
                "Restaurant_ownerName": "Buffy Rann",
                "status": "Delivered"
              }, {
                "id": "1ced248f-d9fb-4440-ba78-9c43d291fbc6",
                "Contact_Number": "571-511-6313",
                "Email": "dlongstaffed@purevolume.com",
                "Expiry_time": "8:28 AM",
                "Food_ItemName": "Pail - 15l White, With Handle",
                "Food_Quantity": 15,
                "Pickup_Date": "6/12/2025",
                "Pickup_time": "12:54 PM",
                "Restaurant_address": "PO Box 75412",
                "Restaurant_name": "Feeney-Hegmann",
                "Restaurant_ownerName": "Daphna Longstaffe",
                "status": "Pending"
              }, {
                "id": "f4f29e5c-a684-42d0-bd6e-4c6a60553f95",
                "Contact_Number": "321-171-3926",
                "Email": "eleveragee@earthlink.net",
                "Expiry_time": "3:45 AM",
                "Food_ItemName": "Bols Melon Liqueur",
                "Food_Quantity": 14,
                "Pickup_Date": "5/23/2025",
                "Pickup_time": "10:58 AM",
                "Restaurant_address": "7th Floor",
                "Restaurant_name": "Paucek, Bernhard and Kuhlman",
                "Restaurant_ownerName": "Edeline Leverage",
                "status": "Accepted"
              }]
            
            """;

    Map<String,Food> store = new HashMap<>();
    ObjectMapper mapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        try {
            List<Map<String, Object>> list = mapper.readValue(
                    sampleData,
                    new TypeReference<List<Map<String, Object>>>() {}
            );

            for (Map<String, Object> item : list) {
                String uuid = UUID.randomUUID().toString();

                Food food = new Food(
                        uuid,
                        (String) item.get("Contact_Number"),
                        (String) item.get("Email"),
                        (String) item.get("Expiry_time"),
                        (String) item.get("Food_ItemName"),
                        (Integer) item.get("Food_Quantity"),
                        (String) item.get("Pickup_Date"),
                        (String) item.get("Pickup_time"),
                        (String) item.get("Restaurant_address"),
                        (String) item.get("Restaurant_name"),
                        (String) item.get("Restaurant_ownerName"),
                        (String) item.get("status")
                );

                store.put(uuid, food);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // -------- CRUD OPERATIONS --------

    public List<Food> getAll() {
        return new ArrayList<>(store.values());
    }

    public Food get(String id) {
        return store.get(id);
    }

    public Food create(Food request) {
        String newId = UUID.randomUUID().toString();

        Food food = new Food(
                newId,
                request.Contact_Number(),
                request.Email(),
                request.Expiry_time(),
                request.Food_ItemName(),
                request.Food_Quantity(),
                request.Pickup_Date(),
                request.Pickup_time(),
                request.Restaurant_address(),
                request.Restaurant_name(),
                request.Restaurant_ownerName(),
                request.status()
        );

        store.put(newId, food);

        return food;
    }

    public Food update(String id, Food request) {
        if (!store.containsKey(id)) return null;

        Food updated = new Food(
                id,
                request.Contact_Number(),
                request.Email(),
                request.Expiry_time(),
                request.Food_ItemName(),
                request.Food_Quantity(),
                request.Pickup_Date(),
                request.Pickup_time(),
                request.Restaurant_address(),
                request.Restaurant_name(),
                request.Restaurant_ownerName(),
                request.status()
        );

        store.put(id, updated);

        return updated;
    }

    public boolean delete(String id) {
        return store.remove(id) != null;
    }
}






