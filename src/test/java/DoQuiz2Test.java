package test;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.*;
import page.LoginPage;
import java.nio.file.Paths;
import java.util.*;

public class DoQuiz2Test {
    private static final String MEMORY_FILE = "memory.json";

    public static void main(String[] args) {
        String account = args[0]; // comes from YAML matrix.account
        Playwright playwright = Playwright.create();
        Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));

        // ===== LOGIN =====
        Browser.NewContextOptions options = new Browser.NewContextOptions();
        Page page = new LoginPage().getAuthenticatedPage(browser, options);

        // ===== ACCOUNT → SUBCATEGORY MAP =====
        Map<String, String> accountToSubcategory = new HashMap<>();
        accountToSubcategory.put("SAM", "#subcategory-1");
        accountToSubcategory.put("NA", "#subcategory-2");
        accountToSubcategory.put("DA", "#subcategory-2");
        accountToSubcategory.put("MU", "#subcategory-2");
        accountToSubcategory.put("JON", "#subcategory-2");
        accountToSubcategory.put("PIE", "#subcategory-2");
        // default if account not mapped
        String subcategorySelector = accountToSubcategory.getOrDefault(account, "#subcategory-1");

        // ===== SELECT SUBCATEGORY =====
        Locator subcategoryButton = page.locator(subcategorySelector).first();
        subcategoryButton.click();

        // ===== NAVIGATE TO QUIZ =====
        page.navigate(System.getenv("QUIZ_URL"), new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));

        // ===== LOAD MEMORY =====
        Map<String, String> memory = MemoryUtils.loadMemory(MEMORY_FILE);

        // ===== QUIZ LOOP =====
        while (true) {
            try {
                Locator questionTitle = page.locator("#qTitle").first();
                String question = questionTitle.innerText().trim();

                // check if quiz finished
                if (question.isEmpty()) break;

                Locator optionsList = page.locator("#options .opt");
                String answerText;

                if (memory.containsKey(question)) {
                    // pick answer from memory
                    answerText = memory.get(question);
                    for (Locator opt : optionsList.all()) {
                        if (opt.innerText().trim().equals(answerText)) {
                            opt.click();
                            break;
                        }
                    }
                } else {
                    // pick random option first
                    Locator firstOpt = optionsList.first();
                    firstOpt.click();

                    // submit
                    page.locator("#submit").click();

                    // get correct answer text after submit
                    Locator correctLetter = page.locator("xpath=//span[contains(., 'Correct:')]/following-sibling::b").first();
                    String letter = correctLetter.innerText().trim();

                    // map letter to actual option text
                    String correctText = "";
                    List<Locator> allOpts = optionsList.all();
                    for (int i = 0; i < allOpts.size(); i++) {
                        String optLetter = Character.toString((char)('A' + i));
                        if (optLetter.equals(letter)) {
                            correctText = allOpts.get(i).innerText().trim();
                            break;
                        }
                    }
                    answerText = correctText;

                    // save to memory
                    memory.put(question, answerText);
                    MemoryUtils.saveMemory(MEMORY_FILE, memory);
                }

                // submit answer if not already
                page.locator("#submit").click();

                // short delay between questions
                page.waitForTimeout(500);

            } catch (PlaywrightException e) {
                System.out.println("⚠️ Error processing question, moving on: " + e.getMessage());
                break;
            }
        }

        System.out.println("✅ Quiz completed for account: " + account);
        browser.close();
        playwright.close();
    }
}



// package test;

// import com.microsoft.playwright.*;
// import com.microsoft.playwright.options.*;
// import page.LoginPage;
// import com.google.gson.Gson;
// import com.google.gson.reflect.TypeToken;

// import java.io.*;
// import java.nio.file.*;
// import java.util.*;

// public class DoQuiz2Test {

//     private static final String MEMORY_FILE = "memory.json";
//     private static Map<String, String> memory = new HashMap<>();

//     static {
//         // Load memory.json if exists
//         try {
//             if (Files.exists(Paths.get(MEMORY_FILE))) {
//                 memory = new Gson().fromJson(
//                         new String(Files.readAllBytes(Paths.get(MEMORY_FILE))),
//                         new TypeToken<Map<String, String>>() {}.getType()
//                 );
//             }
//         } catch (Exception e) {
//             System.out.println("⚠️ Failed to load memory.json: " + e.getMessage());
//         }
//     }

//     public static void main(String[] args) {
//         String account = args.length > 0 ? args[0] : "default";

//         try (Playwright playwright = Playwright.create()) {
//             Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
//                     .setHeadless(false));

//             LoginPage loginPage = new LoginPage();
//             Page page = loginPage.getAuthenticatedPage(browser, new Browser.NewContextOptions());

//             // Wait for quiz page to load
//             page.waitForSelector("#qTitle");

//             boolean hasNext = true;
//             while (hasNext) {
//                 String question = page.locator("#qTitle").innerText().trim();

//                 // Check memory
//                 if (memory.containsKey(question)) {
//                     String answerText = memory.get(question);
//                     Locator options = page.locator("#options .opt");
//                     int count = options.count();
//                     for (int i = 0; i < count; i++) {
//                         if (options.nth(i).innerText().trim().equals(answerText)) {
//                             options.nth(i).click();
//                             break;
//                         }
//                     }
//                 } else {
//                     // New question: pick random option
//                     Locator options = page.locator("#options .opt");
//                     int count = options.count();
//                     int choiceIndex = new Random().nextInt(count);
//                     options.nth(choiceIndex).click();

//                     page.locator("button[type='submit']").click();

//                     // Wait for correct answer to appear
//                     page.waitForSelector("xpath=//span[contains(., 'Correct:')]/following-sibling::b");

//                     String correctLetter = page.locator(
//                             "xpath=//span[contains(., 'Correct:')]/following-sibling::b"
//                     ).innerText().trim();

//                     // Map letter to option text
//                     Map<String, String> letterToText = new HashMap<>();
//                     for (int i = 0; i < count; i++) {
//                         String text = options.nth(i).innerText().trim();
//                         String letter = String.valueOf((char) ('A' + i));
//                         letterToText.put(letter, text);
//                     }

//                     String correctText = letterToText.getOrDefault(correctLetter, options.nth(0).innerText().trim());
//                     memory.put(question, correctText);

//                     // Save memory immediately
//                     try (Writer writer = new FileWriter(MEMORY_FILE)) {
//                         new Gson().toJson(memory, writer);
//                     } catch (Exception e) {
//                         System.out.println("⚠️ Failed to save memory.json: " + e.getMessage());
//                     }
//                 }

//                 // Click next
//                 Locator nextButton = page.locator("//button[contains(., 'Next')]");
//                 if (nextButton.isVisible()) {
//                     nextButton.click();
//                     page.waitForTimeout(1000); // wait a bit
//                 } else {
//                     hasNext = false;
//                 }
//             }

//             System.out.println("✅ Quiz finished for account: " + account);
//         } catch (Exception e) {
//             System.err.println("❌ Error during quiz: " + e.getMessage());
//         }
//     }
// }
