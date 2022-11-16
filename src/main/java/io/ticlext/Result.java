package io.ticlext;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public class Result implements Serializable {
    public String name, address, loc;
    public List<String> phones, emails, whatsapps;
    
    public static String[] csvHeader() {
        return new String[]{"name", "address", "loc", "phones", "emails", "whatsapps"};
    }
    
    public static List<Result> parse(JsonElement result) {
        JsonArray array = result.isJsonArray() ? result.getAsJsonArray() : result.getAsJsonObject()
                .get("list")
                .getAsJsonObject()
                .get("out")
                .getAsJsonObject()
                .get("base")
                .getAsJsonObject()
                .get("results")
                .getAsJsonArray();
        HashSet<Result> results = new HashSet<>();
        List<String> empty = new ArrayList<>();
        for (JsonElement e : array) {
            try {
                JsonObject obj = e.getAsJsonObject();
                Result r = new Result();
                r.name = obj.get("ds_ragsoc").getAsString();
                r.address = obj.get("addr").getAsString() + " " + obj.get("ds_cap").getAsString() + " " + obj.get("loc")
                        .getAsString();
                r.loc = obj.get("loc").getAsString();
                r.phones = empty;
                r.emails = empty;
                r.whatsapps = empty;
                if (obj.get("ds_ls_telefoni") != null){
                    JsonArray phones = obj.get("ds_ls_telefoni").getAsJsonArray();
                    r.phones = new ArrayList<>();
                    for (JsonElement e2 : phones) {
                        r.phones.add(e2.getAsString());
                    }
                }
                if (obj.get("ds_ls_email") != null){
                    JsonArray emails = obj.get("ds_ls_email").getAsJsonArray();
                    r.emails = new ArrayList<>();
                    for (JsonElement e2 : emails) {
                        r.emails.add(e2.getAsString());
                    }
                }
                if (obj.get("ds_ls_telefoni_whatsapp") != null){
                    JsonArray whatsapp = obj.get("ds_ls_telefoni_whatsapp").getAsJsonArray();
                    r.whatsapps = new ArrayList<>();
                    for (JsonElement e2 : whatsapp) {
                        r.whatsapps.add(e2.getAsString());
                    }
                }
                results.add(r);
            }catch(NullPointerException shhhhhh){
            
            }catch(Exception err){
                System.out.println("Error parsing result: " + err);
                err.printStackTrace();
            }
        }
        return new ArrayList<>(results);
    }
    
    public String[] csvData() {
        String phone = phones.isEmpty() ? "" : phones.get(phones.size() - 1);
        String email = emails.isEmpty() ? "" : emails.get(0);
        String whatsapp = whatsapps.isEmpty() ? "" : whatsapps.get(0);
        return new String[]{name, address, loc, phone, email, whatsapp};
        
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Result result = (Result) o;
        return Objects.equals(name, result.name);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}
