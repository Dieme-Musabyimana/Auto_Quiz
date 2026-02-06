import com.microsoft.playwright.*;
import com.microsoft.playwright.options.*;
import page.GroqService;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import com.microsoft.playwright.options.WaitForSelectorState;

public class DoQuizTest {
    private static Map<String, String> masterDatabase = new HashMap<>();
    private static final String DATA_FILE = "statistics.json";
    private static int totalMarksGained = 0;
    private static final Random random = new Random();

    // Helper method for resilient clicking/interaction
    private void smartInteract(Locator locator, String description) {
        try {
            System.out.println("🔍 Finding: " + description);
            locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(15000));
            locator.evaluate("el => el.scrollIntoView()");
            locator.click();
        } catch (Exception e) {
            System.err.println("❌ Failed to interact with: " + description);
            throw e;
        }
    }

    private static void hardRestart(Page page) {
        System.out.println("🔁 Restarting quiz and navigating to index...");
        page.navigate("https://www.iwacusoft.com/ubumenyibwanjye/index", 
            new Page.NavigateOptions().setWaitUntil(WaitUntilState.NETWORKIDLE));
    }

    @Test
    public void startBot() {
        loadData();
        int totalQuestions = 97;

        try (Playwright playwright = Playwright.create()) {
            BrowserType.LaunchPersistentContextOptions options = new BrowserType.LaunchPersistentContextOptions()
                .setHeadless(true)
                .setArgs(Arrays.asList("--no-sandbox", "--disable-dev-shm-usage"))
                .setViewportSize(1920, 1080);

            BrowserContext context = playwright.chromium().launchPersistentContext(Paths.get("bot_profile"), options);
            Page page = context.pages().get(0);
            GroqService ai = new GroqService();

            while (true) {
                try {
                    System.out.println("\n📊 STATS | MARKS: " + totalMarksGained + " | MEMORY: " + masterDatabase.size());
                    
                    page.navigate("https://www.iwacusoft.com/ubumenyibwanjye/index");
                    loginIfNeeded(page);

                    // --- resilient START EARN button ---
                    Locator startBtn = page.locator("button:has-text('START EARN'), a:has-text('START EARN'), .btn-primary").first();
                    smartInteract(startBtn, "START EARN Button");

                    // --- resilient DROPDOWNS ---
                    Locator subCategory = page.locator("#subcategory-3, select[name*='category']").first();
                    subCategory.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(15000));
                    subCategory.selectOption(new SelectOption().setIndex(2));
                    System.out.println("✅ Selected Subcategory");

                    Locator numSelect = page.locator("#mySelect, select[name*='questions']").first();
                    numSelect.selectOption(new SelectOption().setValue(String.valueOf(totalQuestions)));
                    System.out.println("✅ Selected Question Count");

                    // --- resilient LEVEL & START ---
                    Locator advancedBtn = page.locator("//a[contains(@onclick,\"selectLevel('advanced')\")]").first();
                    smartInteract(advancedBtn, "Advanced Level Button");

                    Locator finalStartBtn = page.locator("//button[contains(text(),'START')]").first();
                    smartInteract(finalStartBtn, "Final START Button");

                    // --- 3. QUESTION LOOP ---
                    int currentQuestion = 1;
                    while (currentQuestion <= totalQuestions) {
                        try {
                            FrameLocator quizFrame = page.frameLocator("#iframeId");
                            // Wait for frame content
                            quizFrame.locator("#qTitle").waitFor(new Locator.WaitForOptions().setTimeout(15000));
                            
                            processQuestion(quizFrame, page, ai, currentQuestion);
                            currentQuestion++;
                        } catch (Exception e) {
                            System.err.println("⚠️ Q" + currentQuestion + " Error: " + e.getMessage());
                            page.reload(); 
                            break; 
                        }
                    }
                    saveData();
                } catch (Exception e) {
                    System.err.println("🔄 Loop Error: " + e.getMessage());
                    page.screenshot(new Page.ScreenshotOptions().setPath(Paths.get("error_view.png")));
                    hardRestart(page);
                }
            }
        }
    }

    private static void loginIfNeeded(Page page) {
        try {
            Locator phoneInput = page.locator("input[placeholder*='Phone']").first();
            if (phoneInput.isVisible(new Locator.IsVisibleOptions().setTimeout(3000))) {
                phoneInput.fill(System.getenv("LOGIN_PHONE"));
                page.locator("input[placeholder*='PIN']").fill(System.getenv("LOGIN_PIN"));
                page.click("//button[contains(., 'Log in')]");
                page.waitForLoadState(LoadState.NETWORKIDLE);
                System.out.println("✅ Auto-login successful");
            }
        } catch (Exception ignored) {}
    }

    private static void processQuestion(FrameLocator quizFrame, Page page, GroqService ai, int i) throws Exception {
        Locator qTitle = quizFrame.locator("#qTitle");
        String qText = qTitle.innerText().trim();
        
        List<String> options = quizFrame.locator(".opt .txt").allInnerTexts();
        options.removeIf(String::isEmpty);

        String finalChoice;
        if (masterDatabase.containsKey(qText)) {
            finalChoice = masterDatabase.get(qText);
            System.out.println("📝 Q" + i + " [Memory] " + finalChoice);
        } else {
            // AI PROMPT
            StringBuilder prompt = new StringBuilder("Question: " + qText + "\nOptions:\n");
            for (int idx = 0; idx < options.size(); idx++) prompt.append(idx + 1).append(") ").append(options.get(idx)).append("\n");
            prompt.append("Respond with ONLY the NUMBER of the correct option.");

            String aiResponse = ai.askAI(prompt.toString()).replaceAll("[^0-9]", "").trim();
            int choiceIndex = 0;
            try {
                int num = Integer.parseInt(aiResponse);
                choiceIndex = (num >= 1 && num <= options.size()) ? num - 1 : random.nextInt(options.size());
            } catch (Exception e) { choiceIndex = random.nextInt(options.size()); }
            
            finalChoice = options.get(choiceIndex);
            System.out.println("📝 Q" + i + " [AI] " + finalChoice);
        }

        // Resilient Click on Option
        Locator choiceLoc = quizFrame.locator(".opt").filter(new Locator.FilterOptions().setHasText(finalChoice)).first();
        choiceLoc.click();
        
        page.waitForTimeout(500);
        quizFrame.locator("button:has-text('Submit'), #submitBtn").first().click();
        
        // LEARNING
        try {
            page.waitForTimeout(2000); 
            String resultText = quizFrame.locator("#lastBody").innerText();
            if (resultText.contains("Correct:")) {
                String letter = resultText.split("Correct:")[1].trim().substring(0, 1);
                int correctIdx = letter.toUpperCase().charAt(0) - 'A';
                if (correctIdx >= 0 && correctIdx < options.size()) {
                    masterDatabase.put(qText, options.get(correctIdx));
                    if (finalChoice.equalsIgnoreCase(options.get(correctIdx))) totalMarksGained++;
                    saveData();
                }
            }
        } catch (Exception ignored) {}
    }

    private static void saveData() {
        try (Writer writer = new FileWriter(DATA_FILE)) {
            Map<String, Object> data = new HashMap<>();
            data.put("database", masterDatabase);
            data.put("totalMarks", totalMarksGained);
            new Gson().toJson(data, writer);
        } catch (Exception ignored) {}
    }

    private static void loadData() {
        try {
            File file = new File(DATA_FILE);
            if (file.exists()) {
                Reader reader = new FileReader(file);
                Map<String, Object> data = new Gson().fromJson(reader, new TypeToken<Map<String, Object>>() {}.getType());
                if (data != null) {
                    if (data.get("database") != null) masterDatabase = (Map<String, String>) data.get("database");
                    if (data.get("totalMarks") != null) totalMarksGained = ((Double) data.get("totalMarks")).intValue();
                }
            }
        } catch (Exception ignored) {}
    }
}

// import com.microsoft.playwright.*;
// import com.microsoft.playwright.options.*;
// import page.GroqService;
// import com.google.gson.Gson;
// import com.google.gson.reflect.TypeToken;
// import org.junit.jupiter.api.Test;
// import java.io.*;
// import java.nio.file.Path;
// import java.nio.file.Paths;
// import java.util.*;
// import com.microsoft.playwright.options.WaitForSelectorState;

// public class DoQuizTest {
//     private static String lastProcessedQuestion = "";
//     private static Map<String, String> masterDatabase = new HashMap<>();
//     private static final String DATA_FILE = "statistics.json";
//     private static int totalMarksGained = 0;
//     private static final Random random = new Random();
//     private static final int MAX_RETRIES = 2;
//     private static final int QUESTION_TIMEOUT_MS = 15000; // Increased for GitHub lag

//   private static void hardRestart(Page page) {
//     lastProcessedQuestion = "";
//     System.out.println("🔁 Restarting quiz and navigating to index...");
//     page.navigate("https://www.iwacusoft.com/ubumenyibwanjye/index", 
//         new Page.NavigateOptions().setWaitUntil(WaitUntilState.NETWORKIDLE));
// }

//     @Test
//     public void startBot() {
//         loadData();
//         int totalQuestions = 97;

//         try (Playwright playwright = Playwright.create()) {
//             BrowserType.LaunchPersistentContextOptions options = new BrowserType.LaunchPersistentContextOptions()
//                 .setHeadless(true)
//                 .setArgs(Arrays.asList("--no-sandbox", "--disable-dev-shm-usage"))
//                 .setViewportSize(1920, 1080);

//             BrowserContext context = playwright.chromium().launchPersistentContext(Paths.get("bot_profile"), options);
//             Page page = context.pages().get(0);
//             GroqService ai = new GroqService();

//             while (true) {
//                 try {
//                     System.out.println("\n📊 STATS | MARKS: " + totalMarksGained + " | MEMORY: " + masterDatabase.size());
                    
//                     // 1. Login Logic
//                     page.navigate("https://www.iwacusoft.com/ubumenyibwanjye/index");
//                     loginIfNeeded(page);

//                     // 2. Start Quiz Flow
//                     // Use a broader selector for the Start Earn button
//                     // 2. Start Quiz Flow
// System.out.println("🔍 Searching for Start button...");

// // Broaden the search: look for the button by text OR by its specific link/onclick action
// Locator startBtn = page.locator("button:has-text('START EARN'), a:has-text('START EARN'), .btn-primary, button:visible").first();

// try {
//     // Give it 15 seconds and force it to wait until visible
//     startBtn.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(15000));
    
//     // Scroll it into view just in case GitHub's window is too small
//     startBtn.evaluate("element => element.scrollIntoView()");
    
//     startBtn.click();
//     System.out.println("🖱️ Clicked Start button!");
// } catch (Exception e) {
//     System.out.println("❌ Could not find 'START EARN'. Printing page URL: " + page.url());
//     // If it fails, take a screenshot so we can see what the bot sees (Crucial for GitHub)
//     page.screenshot(new Page.ScreenshotOptions().setPath(Paths.get("error_view.png")));
//     throw new Exception("Start button not found on " + page.url());
// }
//                     // startBtn.waitFor(new Locator.WaitForOptions().setTimeout(10000));
//                     // startBtn.click();

//                     page.locator("#subcategory-3").waitFor();
//                     page.selectOption("#subcategory-3", new SelectOption().setIndex(2));
//                     page.selectOption("#mySelect", new SelectOption().setValue(String.valueOf(totalQuestions)));

//                     page.click("//a[contains(@onclick,\"selectLevel('advanced')\")]");
//                     page.click("//button[contains(text(),'START')]");

//                     // 3. Question Loop
//                     int currentQuestion = 1;
//                     while (currentQuestion <= totalQuestions) {
//                         try {
//                             FrameLocator quizFrame = page.frameLocator("#iframeId");
//                             quizFrame.locator("#qTitle").waitFor(new Locator.WaitForOptions().setTimeout(10000));
                            
//                             processQuestion(quizFrame, page, ai, currentQuestion);
//                             currentQuestion++;
//                         } catch (Exception e) {
//                             System.err.println("⚠️ Q" + currentQuestion + " Error: " + e.getMessage());
//                             page.reload(); // Try reloading the page if a question fails
//                             break; // Break to the main loop to restart properly
//                         }
//                     }
//                     saveData();
//                 } catch (Exception e) {
//                     System.err.println("🔄 Loop Error: " + e.getMessage());
//                     hardRestart(page);
//                 }
//             }
//         }
//     }

//   private static void loginIfNeeded(Page page) {
//     try {
//         Locator phoneInput = page.locator("input[placeholder*='Phone']");
//         if (phoneInput.isVisible(new Locator.IsVisibleOptions().setTimeout(3000))) {
//             phoneInput.fill(System.getenv("LOGIN_PHONE"));
//             page.locator("input[placeholder*='PIN']").fill(System.getenv("LOGIN_PIN"));
//             page.click("//button[contains(., 'Log in')]");
            
//             // Fixed the underscore here
//             page.waitForLoadState(LoadState.NETWORKIDLE);
//             System.out.println("✅ Auto-login successful");
//         }
//     } catch (Exception ignored) {}
// }

//     private static void processQuestion(FrameLocator quizFrame, Page page, GroqService ai, int i) throws Exception {
//         // Wait for question text
//         String qText = quizFrame.locator("#qTitle").innerText().trim();
//         if (qText.isEmpty() || qText.contains("Loading")) {
//             page.waitForTimeout(2000);
//             qText = quizFrame.locator("#qTitle").innerText().trim();
//         }

//         List<String> options = quizFrame.locator(".opt .txt").allInnerTexts();
//         options.removeIf(String::isEmpty);

//         String finalChoice;
//         if (masterDatabase.containsKey(qText)) {
//             finalChoice = masterDatabase.get(qText);
//         } else {
//             // AI Logic (Keep your existing Groq logic here)
//             finalChoice = options.get(random.nextInt(options.size())); // Placeholder
//         }

//         // Click the answer - Simplified for GitHub (No mouse movement needed)
//         quizFrame.locator(".opt").filter(new Locator.FilterOptions().setHasText(finalChoice)).first().click();
        
//         page.waitForTimeout(1000);
//         quizFrame.locator("button:has-text('Submit'), #submitBtn").first().click();
        
//         // Save Correct Answer Logic
//         try {
//             page.waitForTimeout(1000);
//             String resultText = quizFrame.locator("#lastBody").innerText();
//             if (resultText.contains("Correct:")) {
//                 // ... same as your existing logic to parse correct answer ...
//                 saveData();
//             }
//         } catch (Exception ignored) {}
//     }

//     private static void loadData() { /* Existing loadData code */ }
//     private static void saveData() { /* Existing saveData code */ }
// }

// import com.microsoft.playwright.*;
// import com.microsoft.playwright.options.*;
// import page.GroqService;
// import com.google.gson.Gson;
// import com.google.gson.reflect.TypeToken;

// // Added for Maven/JUnit execution
// import org.junit.jupiter.api.Test;

// import java.io.*;
// import java.nio.file.Path;
// import java.nio.file.Paths;
// import java.util.*;
// import java.util.regex.Pattern;

// public class DoQuizTest {

//     private static String lastProcessedQuestion = "";
//     private static Map<String, String> masterDatabase = new HashMap<>();
//     private static final String DATA_FILE = "statistics.json";
//     private static int totalMarksGained = 0;
//     private static final Random random = new Random();
//     private static final int MAX_RETRIES = 2;
//     private static final int QUESTION_TIMEOUT_MS = 10000;

//     // ==================== HARD RESTART FUNCTION ====================
//     private static void hardRestart(Page page) {
//         lastProcessedQuestion = "";
//         System.out.println("🔁 Restarting quiz from beginning...");
//         page.navigate(System.getenv("QUIZ_LINK"));
//         // Wait until we are back on the index page (URL containing "/index")
//         page.waitForURL(
//             Pattern.compile(".*/index.*"),
//             new Page.WaitForURLOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
//         );
//     }

//     @Test
//     public void startBot() {
//         loadData();
//         int totalQuestions = 97;

//         try (Playwright playwright = Playwright.create()) {
//             Path userDataDir = Paths.get("bot_profile");

//             BrowserType.LaunchPersistentContextOptions options =
//                 new BrowserType.LaunchPersistentContextOptions()
//                     .setHeadless(true)
//                     .setIgnoreDefaultArgs(Arrays.asList("--enable-automation"))
//                     .setArgs(Arrays.asList(
//                         "--disable-blink-features=AutomationControlled",
//                         "--no-sandbox",
//                         "--disable-dev-shm-usage",
//                         "--start-maximized"
//                     ))
//                     .setUserAgent(
//                         "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
//                         "AppleWebKit/537.36 (KHTML, like Gecko) " +
//                         "Chrome/122.0.0.0 Safari/537.36"
//                     )
//                     .setViewportSize(1920, 800)
//                     .setSlowMo(0);

//             BrowserContext context = playwright.chromium().launchPersistentContext(userDataDir, options);

//             context.addInitScript(
//                 "() => {" +
//                     "Object.defineProperty(navigator,'webdriver',{get:()=>undefined});" +
//                     "Object.defineProperty(navigator,'languages',{get:()=>['en-US','en']});" +
//                     "Object.defineProperty(navigator,'plugins',{get:()=>[1,2,3,4,5]});" +
//                     "window.chrome={runtime:{}};" +
//                 "}"
//             );

//             Page page = context.pages().get(0);
//             GroqService ai = new GroqService();

//             while (true) {
//                 try {
//                     System.out.println("\n📊 [" + new Date() + "] STATS | MARKS: " + totalMarksGained + " | MEMORY: " + masterDatabase.size());

//                     page = loginIfNeeded(page, context);

//                     page.navigate(System.getenv("QUIZ_LINK"), new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));

//                     Locator startBtn = page.locator("button:has-text('START EARN')");
//                     startBtn.waitFor();
//                     startBtn.click();

//                     page.locator("#subcategory-3").waitFor();
//                     page.selectOption("#subcategory-3", new SelectOption().setIndex(2));
//                     page.selectOption("#mySelect", new SelectOption().setValue(String.valueOf(totalQuestions)));

//                     page.click("//a[contains(@onclick,\"selectLevel('advanced')\")] ");
//                     page.click("//button[contains(text(),'START')]");

//                     FrameLocator quizFrame = page.frameLocator("#iframeId");
//                     int currentQuestion = 1;

//                     while (currentQuestion <= totalQuestions) {
//                         boolean success = false;
//                         int attempt = 0;
//                         long questionStartTime = System.currentTimeMillis();

//                         while (!success && attempt < MAX_RETRIES) {
//                             attempt++;
//                             try {
//                                 FrameLocator quizFrameRetry;
//                                 while (true) {
//                                     try {
//                                         quizFrameRetry = page.frameLocator("#iframeId");
//                                         quizFrameRetry.locator("#qTitle").waitFor(new Locator.WaitForOptions().setTimeout(5000));
//                                         break;
//                                     } catch (PlaywrightException e) {
//                                         System.out.println("⚠️ Waiting for quiz frame... retrying in 5s");
//                                         page.waitForTimeout(5000);
//                                     }
//                                 }

//                                 processQuestion(quizFrameRetry, page, ai, currentQuestion, questionStartTime);
//                                 success = true;
//                                 currentQuestion++;
//                             } catch (Exception e) {
//                                 System.err.println("⚠️ Retry Q" + currentQuestion + " attempt " + attempt + " | " + e.getMessage());
//                                 page.waitForTimeout(3000);
//                             }
//                         }

//                         if (!success) {
//                             System.err.println("❌ Q" + currentQuestion + " skipped after retries.");
//                             currentQuestion++;
//                         }
//                     }

//                     saveData();
//                     System.out.println("🏁 Batch finished. Sleeping briefly...");
//                     humanWait(page, 1500, 3000);

//                 } catch (Exception e) {
//                     System.err.println("🔄 Main loop error: " + e.getMessage());
//                     hardRestart(page);
//                 }
//                 humanWait(page, 2000, 3500);
//             }
//         }
//     }

//     private static Page loginIfNeeded(Page page, BrowserContext context) {
//         try {
//             Locator phoneInput = page.locator("input[placeholder*='Phone']");
//             if (phoneInput.isVisible()) {
//                 phoneInput.fill(System.getenv("LOGIN_PHONE"));
//                 page.locator("input[placeholder*='PIN']").fill(System.getenv("LOGIN_PIN"));
//                 page.click("//button[contains(., 'Log in')]");

//                 // Wait until we reach a URL containing "/index" after login
//                 page.waitForURL(
//                     Pattern.compile(".*/index.*"),
//                     new Page.WaitForURLOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
//                 );

//                 context.storageState(new BrowserContext.StorageStateOptions().setPath(Paths.get("state.json")));
//                 System.out.println("✅ Auto-login successful");
//             }
//         } catch (Exception ignored) {}
//         return page;
//     }

//     private static void processQuestion(FrameLocator quizFrame, Page page, GroqService ai, int i, long questionStartTime) throws Exception {
//         int cycles = 0;
//         String qText = "";
//         while (cycles < QUESTION_TIMEOUT_MS / 200) {
//             try {
//                 qText = quizFrame.locator("#qTitle").innerText().trim();
//                 if (!qText.isEmpty() && !qText.contains("Loading") && !qText.equals(lastProcessedQuestion)) break;
//             } catch (Exception ignored) {}
//             page.waitForTimeout(200);
//             cycles++;
//             if (System.currentTimeMillis() - questionStartTime > 10_000) {
//                 Locator retryBtn = quizFrame.locator("#retryBtn");
//                 if (retryBtn.isVisible()) retryBtn.click();
//                 throw new Exception("Submit exceeded 10 seconds");
//             }
//         }

//         if (qText.isEmpty() || qText.equals(lastProcessedQuestion)) throw new Exception("Question not loaded properly");
//         lastProcessedQuestion = qText;

//         List<String> options = quizFrame.locator(".opt .txt").allInnerTexts();
//         options.removeIf(String::isEmpty);
//         if (options.isEmpty()) throw new Exception("No answer options loaded");

//         String finalChoice;
//         if (masterDatabase.containsKey(qText)) {
//             finalChoice = masterDatabase.get(qText);
//             System.out.println("📝 Q" + i + " [Memory] " + finalChoice);
//         } else {
//             StringBuilder promptBuilder = new StringBuilder();
//             promptBuilder.append("Question: ").append(qText).append("\nOptions:\n");
//             for (int idx = 0; idx < options.size(); idx++) {
//                 promptBuilder.append(idx + 1).append(") ").append(options.get(idx)).append("\n");
//             }
//             promptBuilder.append("Respond with the exact NUMBER of the correct option only.");

//             String aiResponse = ai.askAI(promptBuilder.toString()).replaceAll("[^0-9]", "").trim();
//             int choiceIndex;
//             try {
//                 int number = Integer.parseInt(aiResponse);
//                 choiceIndex = (number >= 1 && number <= options.size()) ? number - 1 : random.nextInt(options.size());
//             } catch (Exception e) {
//                 choiceIndex = random.nextInt(options.size());
//             }
//             finalChoice = options.get(choiceIndex);
//             System.out.println("📝 Q" + i + " [AI] Chose option " + (choiceIndex + 1) + ": " + finalChoice);
//         }

//         Locator answerLocator = quizFrame.locator(".opt").filter(new Locator.FilterOptions().setHasText(finalChoice)).first();
//         mouseMoveHumanLike(page, answerLocator);
//         answerLocator.waitFor(new Locator.WaitForOptions().setTimeout(5000));
//         answerLocator.click();

//         page.waitForTimeout(qText.length() * 30 + random.nextInt(500));
//         Locator submitBtn = quizFrame.locator("button:has-text('Submit'), #submitBtn").first();
//         submitBtn.waitFor(new Locator.WaitForOptions().setTimeout(5000));
//         submitBtn.click();
//         page.waitForTimeout(500);

//         try {
//             String resultText = quizFrame.locator("#lastBody").innerText();
//             if (resultText.contains("Correct:")) {
//                 String correctLetter = resultText.split("Correct:")[1].trim().substring(0, 1);
//                 int correctIndex = correctLetter.charAt(0) - 'A';
//                 if (correctIndex >= 0 && correctIndex < options.size()) {
//                     String actualAns = options.get(correctIndex);
//                     masterDatabase.put(qText, actualAns);
//                     if (finalChoice.equalsIgnoreCase(actualAns)) totalMarksGained++;
//                     saveData();
//                 }
//             }
//         } catch (Exception ignored) {}
//     }

//     private static void mouseMoveHumanLike(Page page, Locator locator) {
//         try {
//             com.microsoft.playwright.options.BoundingBox box = locator.boundingBox();
//             if (box == null) return;
//             double startX = box.x + box.width / 2;
//             double startY = box.y + box.height / 2;
//             for (int i = 1; i <= 5; i++) {
//                 double x = startX + (Math.random() - 0.5) * 10;
//                 double y = startY + (Math.random() - 0.5) * 10;
//                 page.mouse().move(x, y);
//                 Thread.sleep(20 + (int) (Math.random() * 30));
//             }
//         } catch (Exception ignored) {}
//     }

//     private static void humanWait(Page page, int min, int max) {
//         page.waitForTimeout(random.nextInt(max - min + 1) + min);
//     }

//     private static void saveData() {
//         try (Writer writer = new FileWriter(DATA_FILE)) {
//             Map<String, Object> data = new HashMap<>();
//             data.put("database", masterDatabase);
//             data.put("totalMarks", totalMarksGained);
//             new Gson().toJson(data, writer);
//         } catch (IOException ignored) {}
//     }

//     private static void loadData() {
//         try {
//             File file = new File(DATA_FILE);
//             if (file.exists()) {
//                 Reader reader = new FileReader(file);
//                 Map<String, Object> data = new Gson().fromJson(reader, new TypeToken<Map<String, Object>>() {}.getType());
//                 if (data != null) {
//                     if (data.get("database") != null) masterDatabase = (Map<String, String>) data.get("database");
//                     if (data.get("totalMarks") != null) totalMarksGained = ((Double) data.get("totalMarks")).intValue();
//                 }
//                 System.out.println("📂 Memory Loaded: " + masterDatabase.size());
//             }
//         } catch (Exception ignored) {}
//     }
// }




// // TEST RUN - VERSION 3
// import com.microsoft.playwright.*;
// import com.microsoft.playwright.options.*;
// import page.GroqService;
// import com.google.gson.Gson;
// import com.google.gson.reflect.TypeToken;

// import org.junit.jupiter.api.Test;

// import java.io.*;
// import java.nio.file.Path;
// import java.nio.file.Paths;
// import java.util.*;

// public class DoQuizTest {

//     private static String lastProcessedQuestion = "";
//     private static Map<String, String> masterDatabase = new HashMap<>();
//     private static final String DATA_FILE = "statistics.json";
//     private static int totalMarksGained = 0;
//     private static final Random random = new Random();
//     private static final int MAX_RETRIES = 2;
//     private static final int QUESTION_TIMEOUT_MS = 10000;

//     // ==================== HARD RESTART FUNCTION ====================
//     private static void hardRestart(Page page) {
//         lastProcessedQuestion = "";
//         System.out.println("🔁 Restarting quiz from beginning...");

//         // Go back to the quiz URL
//         page.navigate(System.getenv("QUIZ_LINK"));

//         // Wait until the URL matches the quiz link again
//         page.waitForURL(System.getenv("QUIZ_LINK"));
//     }

//     @Test
//     public void startBot() {
//         loadData();
//         int totalQuestions = 97;

//         try (Playwright playwright = Playwright.create()) {
//             Path userDataDir = Paths.get("bot_profile");

//             BrowserType.LaunchPersistentContextOptions options =
//                 new BrowserType.LaunchPersistentContextOptions()
//                     .setHeadless(true)
//                     .setIgnoreDefaultArgs(Arrays.asList("--enable-automation"))
//                     .setArgs(Arrays.asList(
//                         "--disable-blink-features=AutomationControlled",
//                         "--no-sandbox",
//                         "--disable-dev-shm-usage",
//                         "--start-maximized"
//                     ))
//                     .setUserAgent(
//                         "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
//                         "AppleWebKit/537.36 (KHTML, like Gecko) " +
//                         "Chrome/122.0.0.0 Safari/537.36"
//                     )
//                     .setViewportSize(1920, 800)
//                     .setSlowMo(0);

//             BrowserContext context = playwright.chromium().launchPersistentContext(userDataDir, options);

//             context.addInitScript(
//                 "() => {" +
//                     "Object.defineProperty(navigator,'webdriver',{get:()=>undefined});" +
//                     "Object.defineProperty(navigator,'languages',{get:()=>['en-US','en']});" +
//                     "Object.defineProperty(navigator,'plugins',{get:()=>[1,2,3,4,5]});" +
//                     "window.chrome={runtime:{}};" +
//                 "}"
//             );

//             Page page = context.pages().get(0);
//             GroqService ai = new GroqService();

//             while (true) {
//                 try {
//                     System.out.println("\n📊 [" + new Date() + "] STATS | MARKS: " + totalMarksGained + " | MEMORY: " + masterDatabase.size());

//                     page = loginIfNeeded(page, context);

//                     page.navigate(System.getenv("QUIZ_LINK"),
//                         new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));

//                     Locator startBtn = page.locator("button:has-text('START EARN')");
//                     startBtn.waitFor();
//                     startBtn.click();

//                     page.locator("#subcategory-3").waitFor();
//                     page.selectOption("#subcategory-3", new SelectOption().setIndex(2));
//                     page.selectOption("#mySelect", new SelectOption().setValue(String.valueOf(totalQuestions)));

//                     page.click("//a[contains(@onclick,\"selectLevel('advanced')\")]" );
//                     page.click("//button[contains(text(),'START')]");

//                     FrameLocator quizFrame = page.frameLocator("#iframeId");
//                     int currentQuestion = 1;

//                     while (currentQuestion <= totalQuestions) {
//                         boolean success = false;
//                         int attempt = 0;
//                         long questionStartTime = System.currentTimeMillis();

//                         while (!success && attempt < MAX_RETRIES) {
//                             attempt++;
//                             try {
//                                 FrameLocator quizFrameRetry;
//                                 while (true) {
//                                     try {
//                                         quizFrameRetry = page.frameLocator("#iframeId");
//                                         quizFrameRetry.locator("#qTitle").waitFor(
//                                             new Locator.WaitForOptions().setTimeout(5000));
//                                         break;
//                                     } catch (PlaywrightException e) {
//                                         System.out.println("⚠️ Waiting for quiz frame... retrying in 5s");
//                                         page.waitForTimeout(5000);
//                                     }
//                                 }

//                                 processQuestion(quizFrameRetry, page, ai, currentQuestion, questionStartTime);
//                                 success = true;
//                                 currentQuestion++;
//                             } catch (Exception e) {
//                                 System.err.println("⚠️ Retry Q" + currentQuestion + " attempt " + attempt + " | " + e.getMessage());
//                                 page.waitForTimeout(3000);
//                             }
//                         }

//                         if (!success) {
//                             System.err.println("❌ Q" + currentQuestion + " skipped after retries.");
//                             currentQuestion++;
//                         }
//                     }

//                     saveData();
//                     System.out.println("🏁 Batch finished. Sleeping briefly...");
//                     humanWait(page, 1500, 3000);

//                 } catch (Exception e) {
//                     System.err.println("🔄 Main loop error: " + e.getMessage());
//                     hardRestart(page);
//                 }
//                 humanWait(page, 2000, 3500);
//             }
//         }
//     }

//     private static Page loginIfNeeded(Page page, BrowserContext context) {
//         try {
//             Locator phoneInput = page.locator("input[placeholder*='Phone']");
//             if (phoneInput.isVisible()) {
//                 phoneInput.fill(System.getenv("LOGIN_PHONE"));
//                 page.locator("input[placeholder*='PIN']").fill(System.getenv("LOGIN_PIN"));
//                 page.click("//button[contains(., 'Log in')]");

//                 // After login, wait until we are back on the main quiz URL
//                 page.waitForURL(System.getenv("QUIZ_LINK"));

//                 context.storageState(
//                     new BrowserContext.StorageStateOptions().setPath(Paths.get("state.json"))
//                 );
//                 System.out.println("✅ Auto-login successful");
//             }
//         } catch (Exception ignored) {
//         }
//         return page;
//     }

//     private static void processQuestion(
//         FrameLocator quizFrame,
//         Page page,
//         GroqService ai,
//         int i,
//         long questionStartTime
//     ) throws Exception {
//         int cycles = 0;
//         String qText = "";
//         while (cycles < QUESTION_TIMEOUT_MS / 200) {
//             try {
//                 qText = quizFrame.locator("#qTitle").innerText().trim();
//                 if (!qText.isEmpty() && !qText.contains("Loading") && !qText.equals(lastProcessedQuestion)) {
//                     break;
//                 }
//             } catch (Exception ignored) {
//             }
//             page.waitForTimeout(200);
//             cycles++;
//             if (System.currentTimeMillis() - questionStartTime > 10_000) {
//                 Locator retryBtn = quizFrame.locator("#retryBtn");
//                 if (retryBtn.isVisible()) retryBtn.click();
//                 throw new Exception("Submit exceeded 10 seconds");
//             }
//         }

//         if (qText.isEmpty() || qText.equals(lastProcessedQuestion)) {
//             throw new Exception("Question not loaded properly");
//         }
//         lastProcessedQuestion = qText;

//         List<String> options = quizFrame.locator(".opt .txt").allInnerTexts();
//         options.removeIf(String::isEmpty);
//         if (options.isEmpty()) {
//             throw new Exception("No answer options loaded");
//         }

//         String finalChoice;
//         if (masterDatabase.containsKey(qText)) {
//             finalChoice = masterDatabase.get(qText);
//             System.out.println("📝 Q" + i + " [Memory] " + finalChoice);
//         } else {
//             StringBuilder promptBuilder = new StringBuilder();
//             promptBuilder.append("Question: ").append(qText).append("\nOptions:\n");
//             for (int idx = 0; idx < options.size(); idx++) {
//                 promptBuilder.append(idx + 1).append(") ").append(options.get(idx)).append("\n");
//             }
//             promptBuilder.append("Respond with the exact NUMBER of the correct option only.");

//             String aiResponse = ai.askAI(promptBuilder.toString())
//                 .replaceAll("[^0-9]", "")
//                 .trim();

//             int choiceIndex;
//             try {
//                 int number = Integer.parseInt(aiResponse);
//                 if (number >= 1 && number <= options.size()) {
//                     choiceIndex = number - 1;
//                 } else {
//                     choiceIndex = random.nextInt(options.size());
//                 }
//             } catch (Exception e) {
//                 choiceIndex = random.nextInt(options.size());
//             }

//             finalChoice = options.get(choiceIndex);
//             System.out.println("📝 Q" + i + " [AI] Chose option " + (choiceIndex + 1) + ": " + finalChoice);
//         }

//         Locator answerLocator = quizFrame.locator(".opt")
//             .filter(new Locator.FilterOptions().setHasText(finalChoice))
//             .first();
//         mouseMoveHumanLike(page, answerLocator);
//         answerLocator.waitFor(new Locator.WaitForOptions().setTimeout(5000));
//         answerLocator.click();

//         page.waitForTimeout(qText.length() * 30 + random.nextInt(500));
//         Locator submitBtn = quizFrame.locator("button:has-text('Submit'), #submitBtn").first();
//         submitBtn.waitFor(new Locator.WaitForOptions().setTimeout(5000));
//         submitBtn.click();
//         page.waitForTimeout(500);

//         try {
//             String resultText = quizFrame.locator("#lastBody").innerText();
//             if (resultText.contains("Correct:")) {
//                 String correctLetter = resultText.split("Correct:")[1].trim().substring(0, 1);
//                 int correctIndex = correctLetter.charAt(0) - 'A';
//                 if (correctIndex >= 0 && correctIndex < options.size()) {
//                     String actualAns = options.get(correctIndex);
//                     masterDatabase.put(qText, actualAns);
//                     if (finalChoice.equalsIgnoreCase(actualAns)) {
//                         totalMarksGained++;
//                     }
//                     saveData();
//                 }
//             }
//         } catch (Exception ignored) {
//         }
//     }

//     private static void mouseMoveHumanLike(Page page, Locator locator) {
//         try {
//             com.microsoft.playwright.options.BoundingBox box = locator.boundingBox();
//             if (box == null) return;

//             double startX = box.x + box.width / 2;
//             double startY = box.y + box.height / 2;
//             for (int i = 1; i <= 5; i++) {
//                 double x = startX + (Math.random() - 0.5) * 10;
//                 double y = startY + (Math.random() - 0.5) * 10;
//                 page.mouse().move(x, y);
//                 Thread.sleep(20 + (int) (Math.random() * 30));
//             }
//         } catch (Exception ignored) {
//         }
//     }

//     private static void humanWait(Page page, int min, int max) {
//         page.waitForTimeout(random.nextInt(max - min + 1) + min);
//     }

//     private static void saveData() {
//         try (Writer writer = new FileWriter(DATA_FILE)) {
//             Map<String, Object> data = new HashMap<>();
//             data.put("database", masterDatabase);
//             data.put("totalMarks", totalMarksGained);
//             new Gson().toJson(data, writer);
//         } catch (IOException ignored) {
//         }
//     }

//     @SuppressWarnings("unchecked")
//     private static void loadData() {
//         try {
//             File file = new File(DATA_FILE);
//             if (file.exists()) {
//                 try (Reader reader = new FileReader(file)) {
//                     Map<String, Object> data = new Gson().fromJson(
//                         reader,
//                         new TypeToken<Map<String, Object>>() {}.getType()
//                     );
//                     if (data != null) {
//                         Object db = data.get("database");
//                         if (db instanceof Map) {
//                             masterDatabase = (Map<String, String>) db;
//                         }
//                         Object total = data.get("totalMarks");
//                         if (total instanceof Number) {
//                             totalMarksGained = ((Number) total).intValue();
//                         }
//                     }
//                 }
//                 System.out.println("📂 Memory Loaded: " + masterDatabase.size());
//             }
//         } catch (Exception ignored) {
//         }
//     }
// }




// import com.microsoft.playwright.*;
// import com.microsoft.playwright.options.*;
// import page.GroqService;
// import com.google.gson.Gson;
// import com.google.gson.reflect.TypeToken;

// // Added for Maven/JUnit execution
// import org.junit.jupiter.api.Test;

// import java.io.*;
// import java.nio.file.Path;
// import java.nio.file.Paths;
// import java.util.*;

// public class DoQuizTest {

//     private static String lastProcessedQuestion = "";
//     private static Map<String, String> masterDatabase = new HashMap<>();
//     private static final String DATA_FILE = "statistics.json";
//     private static int totalMarksGained = 0;
//     private static final Random random = new Random();
//     private static final int MAX_RETRIES = 2;
//     private static final int QUESTION_TIMEOUT_MS = 10000;

//     // ==================== HARD RESTART FUNCTION ====================
//     private static void hardRestart(Page page) {
//         lastProcessedQuestion = "";
//         System.out.println("🔁 Restarting quiz from beginning...");
//         page.navigate(System.getenv("QUIZ_LINK"));
//         // FIXED: Using WaitForURLOptions (and no 'new' keyword before page.waitForURL)
//         page.waitForURL("**/index", new Page.WaitForURLOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
//     }

//     @Test
//     public void startBot() {
//         loadData();
//         int totalQuestions = 97;

//         try (Playwright playwright = Playwright.create()) {
//             Path userDataDir = Paths.get("bot_profile");

//             BrowserType.LaunchPersistentContextOptions options =
//                     new BrowserType.LaunchPersistentContextOptions()
//                             .setHeadless(true) 
//                             .setIgnoreDefaultArgs(Arrays.asList("--enable-automation"))
//                             .setArgs(Arrays.asList(
//                                     "--disable-blink-features=AutomationControlled",
//                                     "--no-sandbox",
//                                     "--disable-dev-shm-usage",
//                                     "--start-maximized"
//                             ))
//                             .setUserAgent(
//                                     "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
//                                             "AppleWebKit/537.36 (KHTML, like Gecko) " +
//                                             "Chrome/122.0.0.0 Safari/537.36"
//                             )
//                             .setViewportSize(1920, 800)
//                             .setSlowMo(0);

//             BrowserContext context = playwright.chromium().launchPersistentContext(userDataDir, options);

//             context.addInitScript(
//                     "() => {" +
//                             "Object.defineProperty(navigator,'webdriver',{get:()=>undefined});" +
//                             "Object.defineProperty(navigator,'languages',{get:()=>['en-US','en']});" +
//                             "Object.defineProperty(navigator,'plugins',{get:()=>[1,2,3,4,5]});" +
//                             "window.chrome={runtime:{}};" +
//                             "}"
//             );

//             Page page = context.pages().get(0);
//             GroqService ai = new GroqService();

//             while (true) {
//                 try {
//                     System.out.println("\n📊 [" + new Date() + "] STATS | MARKS: " + totalMarksGained + " | MEMORY: " + masterDatabase.size());

//                     page = loginIfNeeded(page, context);

//                     page.navigate(System.getenv("QUIZ_LINK"), new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));

//                     Locator startBtn = page.locator("button:has-text('START EARN')");
//                     startBtn.waitFor();
//                     startBtn.click();

//                     page.locator("#subcategory-3").waitFor();
//                     page.selectOption("#subcategory-3", new SelectOption().setIndex(2));
//                     page.selectOption("#mySelect", new SelectOption().setValue(String.valueOf(totalQuestions)));

//                     page.click("//a[contains(@onclick,\"selectLevel('advanced')\")]");
//                     page.click("//button[contains(text(),'START')]");

//                     FrameLocator quizFrame = page.frameLocator("#iframeId");
//                     int currentQuestion = 1;

//                     while (currentQuestion <= totalQuestions) {
//                         boolean success = false;
//                         int attempt = 0;
//                         long questionStartTime = System.currentTimeMillis();

//                         while (!success && attempt < MAX_RETRIES) {
//                             attempt++;
//                             try {
//                                 FrameLocator quizFrameRetry;
//                                 while (true) {
//                                     try {
//                                         quizFrameRetry = page.frameLocator("#iframeId");
//                                         quizFrameRetry.locator("#qTitle").waitFor(new Locator.WaitForOptions().setTimeout(5000));
//                                         break;
//                                     } catch (PlaywrightException e) {
//                                         System.out.println("⚠️ Waiting for quiz frame... retrying in 5s");
//                                         page.waitForTimeout(5000);
//                                     }
//                                 }

//                                 processQuestion(quizFrameRetry, page, ai, currentQuestion, questionStartTime);
//                                 success = true;
//                                 currentQuestion++;
//                             } catch (Exception e) {
//                                 System.err.println("⚠️ Retry Q" + currentQuestion + " attempt " + attempt + " | " + e.getMessage());
//                                 page.waitForTimeout(3000);
//                             }
//                         }

//                         if (!success) {
//                             System.err.println("❌ Q" + currentQuestion + " skipped after retries.");
//                             currentQuestion++;
//                         }
//                     }

//                     saveData();
//                     System.out.println("🏁 Batch finished. Sleeping briefly...");
//                     humanWait(page, 1500, 3000);

//                 } catch (Exception e) {
//                     System.err.println("🔄 Main loop error: " + e.getMessage());
//                     hardRestart(page);
//                 }
//                 humanWait(page, 2000, 3500);
//             }
//         }
//     }

//     private static Page loginIfNeeded(Page page, BrowserContext context) {
//         try {
//             Locator phoneInput = page.locator("input[placeholder*='Phone']");
//             if (phoneInput.isVisible()) {
//                 phoneInput.fill(System.getenv("LOGIN_PHONE"));
//                 page.locator("input[placeholder*='PIN']").fill(System.getenv("LOGIN_PIN"));
//                 page.click("//button[contains(., 'Log in')]");
                
//                 // FIXED: Use WaitForURLOptions instead of NavigateOptions
//                 page.waitForURL("**/index", new Page.WaitForURLOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
                
//                 context.storageState(new BrowserContext.StorageStateOptions().setPath(Paths.get("state.json")));
//                 System.out.println("✅ Auto-login successful");
//             }
//         } catch (Exception ignored) {}
//         return page;
//     }

//     private static void processQuestion(FrameLocator quizFrame, Page page, GroqService ai, int i, long questionStartTime) throws Exception {
//         int cycles = 0;
//         String qText = "";
//         while (cycles < QUESTION_TIMEOUT_MS / 200) {
//             try {
//                 qText = quizFrame.locator("#qTitle").innerText().trim();
//                 if (!qText.isEmpty() && !qText.contains("Loading") && !qText.equals(lastProcessedQuestion)) break;
//             } catch (Exception ignored) {}
//             page.waitForTimeout(200);
//             cycles++;
//             if (System.currentTimeMillis() - questionStartTime > 10_000) {
//                 Locator retryBtn = quizFrame.locator("#retryBtn");
//                 if (retryBtn.isVisible()) retryBtn.click();
//                 throw new Exception("Submit exceeded 10 seconds");
//             }
//         }

//         if (qText.isEmpty() || qText.equals(lastProcessedQuestion)) throw new Exception("Question not loaded properly");
//         lastProcessedQuestion = qText;

//         List<String> options = quizFrame.locator(".opt .txt").allInnerTexts();
//         options.removeIf(String::isEmpty);
//         if (options.isEmpty()) throw new Exception("No answer options loaded");

//         String finalChoice;
//         if (masterDatabase.containsKey(qText)) {
//             finalChoice = masterDatabase.get(qText);
//             System.out.println("📝 Q" + i + " [Memory] " + finalChoice);
//         } else {
//             StringBuilder promptBuilder = new StringBuilder();
//             promptBuilder.append("Question: ").append(qText).append("\nOptions:\n");
//             for (int idx = 0; idx < options.size(); idx++) {
//                 promptBuilder.append(idx + 1).append(") ").append(options.get(idx)).append("\n");
//             }
//             promptBuilder.append("Respond with the exact NUMBER of the correct option only.");

//             String aiResponse = ai.askAI(promptBuilder.toString()).replaceAll("[^0-9]", "").trim();
//             int choiceIndex;
//             try {
//                 int number = Integer.parseInt(aiResponse);
//                 choiceIndex = (number >= 1 && number <= options.size()) ? number - 1 : random.nextInt(options.size());
//             } catch (Exception e) {
//                 choiceIndex = random.nextInt(options.size());
//             }
//             finalChoice = options.get(choiceIndex);
//             System.out.println("📝 Q" + i + " [AI] Chose option " + (choiceIndex + 1) + ": " + finalChoice);
//         }

//         Locator answerLocator = quizFrame.locator(".opt").filter(new Locator.FilterOptions().setHasText(finalChoice)).first();
//         mouseMoveHumanLike(page, answerLocator);
//         answerLocator.waitFor(new Locator.WaitForOptions().setTimeout(5000));
//         answerLocator.click();

//         page.waitForTimeout(qText.length() * 30 + random.nextInt(500));
//         Locator submitBtn = quizFrame.locator("button:has-text('Submit'), #submitBtn").first();
//         submitBtn.waitFor(new Locator.WaitForOptions().setTimeout(5000));
//         submitBtn.click();
//         page.waitForTimeout(500);

//         try {
//             String resultText = quizFrame.locator("#lastBody").innerText();
//             if (resultText.contains("Correct:")) {
//                 String correctLetter = resultText.split("Correct:")[1].trim().substring(0, 1);
//                 int correctIndex = correctLetter.charAt(0) - 'A';
//                 if (correctIndex >= 0 && correctIndex < options.size()) {
//                     String actualAns = options.get(correctIndex);
//                     masterDatabase.put(qText, actualAns);
//                     if (finalChoice.equalsIgnoreCase(actualAns)) totalMarksGained++;
//                     saveData();
//                 }
//             }
//         } catch (Exception ignored) {}
//     }

//     private static void mouseMoveHumanLike(Page page, Locator locator) {
//         try {
//             com.microsoft.playwright.options.BoundingBox box = locator.boundingBox();
//             if (box == null) return;
//             double startX = box.x + box.width / 2;
//             double startY = box.y + box.height / 2;
//             for (int i = 1; i <= 5; i++) {
//                 double x = startX + (Math.random() - 0.5) * 10;
//                 double y = startY + (Math.random() - 0.5) * 10;
//                 page.mouse().move(x, y);
//                 Thread.sleep(20 + (int)(Math.random() * 30));
//             }
//         } catch (Exception ignored) {}
//     }

//     private static void humanWait(Page page, int min, int max) {
//         page.waitForTimeout(random.nextInt(max - min + 1) + min);
//     }

//     private static void saveData() {
//         try (Writer writer = new FileWriter(DATA_FILE)) {
//             Map<String, Object> data = new HashMap<>();
//             data.put("database", masterDatabase);
//             data.put("totalMarks", totalMarksGained);
//             new Gson().toJson(data, writer);
//         } catch (IOException ignored) {}
//     }

//     private static void loadData() {
//         try {
//             File file = new File(DATA_FILE);
//             if (file.exists()) {
//                 Reader reader = new FileReader(file);
//                 Map<String, Object> data = new Gson().fromJson(reader, new TypeToken<Map<String, Object>>() {}.getType());
//                 if (data != null) {
//                     if (data.get("database") != null) masterDatabase = (Map<String, String>) data.get("database");
//                     if (data.get("totalMarks") != null) totalMarksGained = ((Double) data.get("totalMarks")).intValue();
//                 }
//                 System.out.println("📂 Memory Loaded: " + masterDatabase.size());
//             }
//         } catch (Exception ignored) {}
//     }
// }
