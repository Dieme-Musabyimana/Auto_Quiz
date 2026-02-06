

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.microsoft.playwright.*;
import page.LoginPage;

import java.net.http.HttpResponse;
import java.util.Arrays; // Added for stealth args
import java.util.Random;

public class ShotTest {
    public static void main(String[] args) {
        try (Playwright playwright = Playwright.create()) {
            // 🛡️ ANTI-BOT LAYER 1: Launch Arguments
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                    .setHeadless(false)
                    .setArgs(Arrays.asList("--disable-blink-features=AutomationControlled"))
                    .setSlowMo(600));

            // 🛡️ ANTI-BOT LAYER 2: Define Stealth Blueprint
            Browser.NewContextOptions stealthOptions = new Browser.NewContextOptions()
                    .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36")
                    .setViewportSize(1920, 1080);

            LoginPage login = new LoginPage();

            // ✅ FIXED: Now passing both arguments to satisfy the new LoginPage method
            Page page = login.getAuthenticatedPage(browser, stealthOptions);

            page.navigate("https://www.iwacusoft.com/ubumenyibwanjye/money_arena");
            page.waitForSelector("#boardSurface");
            page.mouse().wheel(0, 700);

            Locator startBtn = page.locator("button:has-text('START ROUND'), #startBtn");
            if (startBtn.isVisible()) {
                startBtn.click();
                page.waitForTimeout(3000);
            }

            Random random = new Random();

            for (int i = 1; i <= 8; i++) {
                System.out.println("🎾 Targeting Bills & Utilities - Ball " + i + "...");

                // --- SPEED: MAXIMUM ALLOWED SLOWNESS (1200) ---
                page.locator("#speedSlider").fill("1200");

                // --- DIRECTION: ALIGNING WITH THE TOP-RIGHT POT ---
                String targetDir = String.valueOf(27 + random.nextInt(3));
                page.locator("#dirSlider").fill(targetDir);

                page.waitForTimeout(800);

                Locator shootBtn = page.locator("#shootBtn");
                if (shootBtn.isDisabled()) {
                    page.waitForTimeout(2500);
                }

                shootBtn.click(new Locator.ClickOptions().setForce(true));
                System.out.println("🚀 Shot Fired! Speed: 1200 | Direction: " + targetDir);

                page.waitForTimeout(12000);
            }

            browser.close();
        } catch (Exception e) { e.printStackTrace(); }
    }
}
//HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
//
//            if (response.statusCode() == 200) {
//JsonObject jsonResponse = JsonParser.parseString(response.body()).getAsJsonObject();
//                return jsonResponse.getAsJsonArray("choices").get(0).getAsJsonObject()
//                        .getAsJsonObject("message").get("content").getAsString();
//            }
//                    } catch (Exception e) {
//        System.err.println("⏱️ 10s Deadline reached or error. Skipping AI.");
//        }
//                return "Error";
//                }
