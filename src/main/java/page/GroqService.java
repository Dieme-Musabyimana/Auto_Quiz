package page;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class GroqService {
    private static final String API_KEY = System.getenv("API_KEY");
    private static final String URL = System.getenv("AI_SERVICE_LINK");

    public String askAI(String prompt) {
        int maxRetries = 3;
        int retryDelayMs = 5000;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                // --- GEMINI JSON STRUCTURE ---
                JsonObject textPart = new JsonObject();
                textPart.addProperty("text", prompt);

                JsonArray partsArray = new JsonArray();
                partsArray.add(textPart);

                JsonObject contentObj = new JsonObject();
                contentObj.add("parts", partsArray);

                JsonArray contentsArray = new JsonArray();
                contentsArray.add(contentObj);

                JsonObject jsonBody = new JsonObject();
                jsonBody.add("contents", contentsArray);

                String finalJson = new Gson().toJson(jsonBody);
                HttpClient client = HttpClient.newHttpClient();

                // Gemini expects the key in the URL: URL?key=API_KEY
                String fullUrl = URL + "?key=" + API_KEY;

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(fullUrl))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(finalJson))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonObject jsonResponse = new Gson().fromJson(response.body(), JsonObject.class);
                    // Gemini structure: candidates[0].content.parts[0].text
                    return jsonResponse.getAsJsonArray("candidates")
                            .get(0).getAsJsonObject()
                            .getAsJsonObject("content")
                            .getAsJsonArray("parts")
                            .get(0).getAsJsonObject()
                            .get("text").getAsString().trim();
                }

                if (response.statusCode() == 429) {
                    System.out.println("⚠️ Gemini Rate Limit (15 RPM). Waiting " + (retryDelayMs / 1000) + "s...");
                    Thread.sleep(retryDelayMs + (int)(Math.random() * 3000)); // Add randomness for 18 bots
                    continue;
                }

                System.out.println("API Error: " + response.statusCode() + " - " + response.body());
                return "Error";

            } catch (Exception e) {
                System.err.println("Failure in Service: " + e.getMessage());
            }
        }
        return "Error";
    }
}



// package page;

// import com.google.gson.Gson;
// import com.google.gson.JsonArray;
// import com.google.gson.JsonObject;
// import java.net.URI;
// import java.net.http.HttpClient;
// import java.net.http.HttpRequest;
// import java.net.http.HttpResponse;

// public class GroqService {
//     private static final String API_KEY = System.getenv("API_KEY");
//     private static final String URL = System.getenv("AI_SERVICE_LINK");

//     public String askAI(String prompt) {
//         int maxRetries = 3;
//         int retryDelayMs = 5000;

//         for (int attempt = 1; attempt <= maxRetries; attempt++) {
//             try {
//                 JsonObject jsonBody = new JsonObject();
//                 jsonBody.addProperty("model", "llama-3.3-70b-versatile");
//                 JsonArray messages = new JsonArray();
//                 JsonObject messageObj = new JsonObject();
//                 messageObj.addProperty("role", "user");
//                 messageObj.addProperty("content", prompt);
//                 messages.add(messageObj);
//                 jsonBody.add("messages", messages);
//                 jsonBody.addProperty("temperature", 0.5);

//                 String finalJson = new Gson().toJson(jsonBody);
//                 HttpClient client = HttpClient.newHttpClient();
//                 HttpRequest request = HttpRequest.newBuilder()
//                         .uri(URI.create(URL))
//                         .header("Content-Type", "application/json")
//                         .header("Authorization", "Bearer " + API_KEY)
//                         .POST(HttpRequest.BodyPublishers.ofString(finalJson))
//                         .build();

//                 HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

//                 if (response.statusCode() == 200) {
//                     JsonObject jsonResponse = new Gson().fromJson(response.body(), JsonObject.class);
//                     return jsonResponse.getAsJsonArray("choices")
//                             .get(0).getAsJsonObject()
//                             .getAsJsonObject("message")
//                             .get("content").getAsString().trim();
//                 }

//                 if (response.statusCode() == 429) {
//                     System.out.println("⚠️ Groq Rate Limit. Waiting " + (retryDelayMs/1000) + "s...");
//                     Thread.sleep(retryDelayMs);
//                     continue;
//                 }

//                 System.out.println("API Error: " + response.body());
//                 return "Error";

//             } catch (Exception e) {
//                 System.err.println("Failure in GroqService: " + e.getMessage());
//             }
//         }
//         return "Error";
//     }
// }
