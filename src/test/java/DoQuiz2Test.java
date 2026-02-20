package test;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.*;
import page.LoginPage;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class DoQuiz2Test {

    private static final String MEMORY_FILE = "memory.json";
    private static Map<String, String> memory = new HashMap<>();

    static {
        // Load memory.json if exists
        try {
            if (Files.exists(Paths.get(MEMORY_FILE))) {
                memory = new Gson().fromJson(
                        new String(Files.readAllBytes(Paths.get(MEMORY_FILE))),
                        new TypeToken<Map<String, String>>() {}.getType()
                );
            }
        } catch (Exception e) {
            System.out.println("⚠️ Failed to load memory.json: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        String account = args.length > 0 ? args[0] : "default";

        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                    .setHeadless(false));

            LoginPage loginPage = new LoginPage();
            Page page = loginPage.getAuthenticatedPage(browser, new Browser.NewContextOptions());

            // Wait for quiz page to load
            page.waitForSelector("#qTitle");

            boolean hasNext = true;
            while (hasNext) {
                String question = page.locator("#qTitle").innerText().trim();

                // Check memory
                if (memory.containsKey(question)) {
                    String answerText = memory.get(question);
                    Locator options = page.locator("#options .opt");
                    int count = options.count();
                    for (int i = 0; i < count; i++) {
                        if (options.nth(i).innerText().trim().equals(answerText)) {
                            options.nth(i).click();
                            break;
                        }
                    }
                } else {
                    // New question: pick random option
                    Locator options = page.locator("#options .opt");
                    int count = options.count();
                    int choiceIndex = new Random().nextInt(count);
                    options.nth(choiceIndex).click();

                    page.locator("button[type='submit']").click();

                    // Wait for correct answer to appear
                    page.waitForSelector("xpath=//span[contains(., 'Correct:')]/following-sibling::b");

                    String correctLetter = page.locator(
                            "xpath=//span[contains(., 'Correct:')]/following-sibling::b"
                    ).innerText().trim();

                    // Map letter to option text
                    Map<String, String> letterToText = new HashMap<>();
                    for (int i = 0; i < count; i++) {
                        String text = options.nth(i).innerText().trim();
                        String letter = String.valueOf((char) ('A' + i));
                        letterToText.put(letter, text);
                    }

                    String correctText = letterToText.getOrDefault(correctLetter, options.nth(0).innerText().trim());
                    memory.put(question, correctText);

                    // Save memory immediately
                    try (Writer writer = new FileWriter(MEMORY_FILE)) {
                        new Gson().toJson(memory, writer);
                    } catch (Exception e) {
                        System.out.println("⚠️ Failed to save memory.json: " + e.getMessage());
                    }
                }

                // Click next
                Locator nextButton = page.locator("//button[contains(., 'Next')]");
                if (nextButton.isVisible()) {
                    nextButton.click();
                    page.waitForTimeout(1000); // wait a bit
                } else {
                    hasNext = false;
                }
            }

            System.out.println("✅ Quiz finished for account: " + account);
        } catch (Exception e) {
            System.err.println("❌ Error during quiz: " + e.getMessage());
        }
    }
}
