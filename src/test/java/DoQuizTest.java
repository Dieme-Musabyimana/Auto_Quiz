// import com.microsoft.playwright.*;
// import com.microsoft.playwright.options.*;
// import page.GroqService;
// import com.google.gson.Gson;
// import com.google.gson.reflect.TypeToken;

// import java.io.*;
// import java.nio.file.*;
// import java.util.*;

// public class DoQuizTest {

//     private static String lastProcessedQuestion = "";
//     private static Map<String, String> masterDatabase = new HashMap<>();
//     private static final String DATA_FILE = "statistics.json";
//     private static int totalMarksGained = 0;
//     private static final Random random = new Random();
//     private static int roundsCompleted = 0; // 🔄 Tracks round number in console

//     public static void main(String[] args) throws Exception {

//         loadData();
//         int totalQuestions = 90;

//         // 🔹 ACCOUNT NAME
//         String account = args.length > 0 ? args[0].toUpperCase() : "ACC1";
//         System.out.println("👤 Running account: " + account);

//         // 🔹 PROFILE & STATE
//         Path profileDir = Paths.get("profiles", account);
//         Files.createDirectories(profileDir);
//         Path statePath = Paths.get("state_" + account + ".json");

//         try (Playwright playwright = Playwright.create()) {

//             BrowserType.LaunchPersistentContextOptions options =
//                     new BrowserType.LaunchPersistentContextOptions()
//                             .setHeadless(true)
//                             .setIgnoreDefaultArgs(List.of("--enable-automation"))
//                             .setArgs(List.of(
//                                     "--disable-blink-features=AutomationControlled",
//                                     "--no-sandbox",
//                                     "--disable-dev-shm-usage"
//                             ))
//                             .setUserAgent(
//                                     "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
//                                             "AppleWebKit/537.36 (KHTML, like Gecko) " +
//                                             "Chrome/122.0.0.0 Safari/537.36"
//                             )
//                             .setViewportSize(1920, 800);

//             BrowserContext context =
//                     playwright.chromium().launchPersistentContext(profileDir, options);

//             // 🧠 Anti-bot script
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

//             // ================= MAIN INFINITE ROUND LOOP =================
//             while (true) {
//                 try {
//                     roundsCompleted++;
//                     System.out.println("\n***********************************************");
//                     System.out.println("🚀 STARTING ROUND #" + roundsCompleted + " FOR: " + account);
//                     System.out.println("***********************************************\n");

//                     page.navigate(
//                             "https://www.iwacusoft.com/ubumenyibwanjye/index",
//                             new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
//                     );

//                     loginIfNeeded(page, context, account, statePath);

//                     // --- NAVIGATION & SELECTION ---
//                     Locator startBtn = page.locator("button:has-text('START EARN')");
//                     startBtn.waitFor();
//                     startBtn.click();

//                     page.locator("#subcategory-3").waitFor();
//                     page.selectOption("#subcategory-3", new SelectOption().setIndex(2));
//                     page.selectOption("#mySelect",
//                             new SelectOption().setValue(String.valueOf(totalQuestions)));
//                     page.click("//a[contains(@onclick,\"selectLevel('advanced')\")]");
//                     page.click("//button[contains(text(),'START')]");

//                     FrameLocator quizFrame = page.frameLocator("#iframeId");
                    
//                     // --- INTERNAL QUESTION LOOP ---
//                     boolean isRoundRunning = true;
//                     int qInThisRound = 0;

//                     while (isRoundRunning) {
//                         try {
//                             // processQuestion returns false if "Quiz Finished" is detected
//                             isRoundRunning = processQuestion(quizFrame, page, ai);
//                             if (isRoundRunning) qInThisRound++;
//                         } catch (Exception e) {
//                             System.err.println("⚠️ UI Glitch in Round " + roundsCompleted + ". Attempting recovery...");
//                             page.mouse().wheel(0, 200); 
//                             page.waitForTimeout(2000);
                            
//                             // Check if the quiz ended during the glitch
//                             if (quizFrame.locator("text=Quiz Finished").isVisible() || 
//                                 quizFrame.locator("button:has-text('Try Again')").isVisible()) {
//                                 isRoundRunning = false;
//                             }
//                         }
//                     }

//                     System.out.println("\n✅ FINISHED ROUND #" + roundsCompleted);
//                     System.out.println("📊 Questions Answered: " + qInThisRound);
                    
//                     saveData();
//                     humanWait(page, 3000, 6000); // Breathe before the next round selection

//                 } catch (Exception e) {
//                     System.err.println("🔁 Round " + roundsCompleted + " crashed. Restarting session...");
//                     page.waitForTimeout(5000);
//                 }
//             }
//         }
//     }

//     // ================= QUESTION LOGIC =================
//     private static boolean processQuestion(FrameLocator quizFrame, Page page, GroqService ai) throws Exception {
        
//         // 1. END DETECTION: Check if the round is finished
//         if (quizFrame.locator("text=Quiz Finished").isVisible() || 
//             quizFrame.locator("text=Result").isVisible() ||
//             quizFrame.locator("button:has-text('Try Again')").isVisible()) {
//             return false; 
//         }

//         // 2. WAIT for Question Title (5s timeout)
//         Locator title = quizFrame.locator("#qTitle");
//         try {
//             title.waitFor(new Locator.WaitForOptions().setTimeout(5000));
//         } catch (Exception e) {
//             return false; // If title doesn't appear, round likely ended
//         }

//         String qText = title.innerText().trim();
        
//         // Anti-double-process check
//         if (qText.isEmpty() || qText.equals(lastProcessedQuestion)) {
//             page.waitForTimeout(1000);
//             return true; 
//         }
//         lastProcessedQuestion = qText;

//         // 3. GET OPTIONS
//         List<String> options = quizFrame.locator(".opt .txt").allInnerTexts();
//         options.removeIf(String::isEmpty);

//         if (options.isEmpty()) return true;

//         // 4. CHOOSE ANSWER (Memory first, then Random/AI)
//         String choice = masterDatabase.getOrDefault(qText, 
//                         options.get(random.nextInt(options.size())));
        
//         System.out.println("📝 [R" + roundsCompleted + "] Q: " + 
//                            (qText.length() > 30 ? qText.substring(0, 30) + "..." : qText) + 
//                            " | Ans: " + choice);

//         // 5. CLICK & SUBMIT
//         try {
//             quizFrame.locator(".opt")
//                     .filter(new Locator.FilterOptions().setHasText(choice))
//                     .first()
//                     .click(new Locator.ClickOptions().setForce(true).setTimeout(3000));
            
//             page.waitForTimeout(500);
            
//             quizFrame.locator("button:has-text('Submit'), #submitBtn")
//                     .first()
//                     .click(new Locator.ClickOptions().setTimeout(3000));
//         } catch (Exception e) {
//             throw new Exception("Click Failed");
//         }

//         return true; 
//     }

//     // ================= LOGIN LOGIC =================
//     private static void loginIfNeeded(Page page, BrowserContext context, String account, Path statePath) {
//         try {
//             page.waitForTimeout(2000);
//             if (page.locator("button:has-text('START EARN')").isVisible()) {
//                 System.out.println("✅ Already logged in: " + account);
//                 return;
//             }

//             Locator phoneInput = page.locator("input[placeholder*='Phone']");
//             if (!phoneInput.isVisible()) return;

//             String phone = System.getenv("LOGIN_PHONE");
//             String pin   = System.getenv("LOGIN_PIN");

//             if (phone == null || pin == null) {
//                 throw new RuntimeException("Missing secrets for " + account);
//             }

//             phoneInput.fill(phone);
//             page.locator("input[placeholder*='PIN']").fill(pin);
//             page.click("//button[contains(., 'Log in')]");

//             page.waitForURL("**/index", new Page.WaitForURLOptions().setTimeout(15000));
//             context.storageState(new BrowserContext.StorageStateOptions().setPath(statePath));
//             System.out.println("💾 Login saved for " + account);

//         } catch (Exception e) {
//             System.err.println("❌ Login failed for " + account + ": " + e.getMessage());
//         }
//     }

//     // ================= DATA PERSISTENCE =================
//     private static void saveData() {
//         try (Writer writer = new FileWriter(DATA_FILE)) {
//             Map<String, Object> data = new HashMap<>();
//             data.put("database", masterDatabase);
//             data.put("totalMarks", totalMarksGained);
//             new Gson().toJson(data, writer);
//             System.out.println("💾 Progress saved to disk.");
//         } catch (IOException e) {
//             System.err.println("❌ Failed to save data: " + e.getMessage());
//         }
//     }

//     private static void loadData() {
//         try {
//             File file = new File(DATA_FILE);
//             if (file.exists()) {
//                 Reader reader = new FileReader(file);
//                 Map<String, Object> data = new Gson().fromJson(reader, new TypeToken<Map<String, Object>>() {}.getType());
//                 if (data != null) {
//                     if (data.get("database") != null) masterDatabase = (Map<String, String>) data.get("database");
//                     if (data.get("totalMarks") != null) {
//                         totalMarksGained = ((Double) data.get("totalMarks")).intValue();
//                     }
//                 }
//                 System.out.println("📂 Memory Loaded. Database size: " + masterDatabase.size());
//             }
//         } catch (Exception e) {
//             System.err.println("⚠️ Could not load database. Starting fresh.");
//         }
//     }

//     private static void humanWait(Page page, int min, int max) {
//         page.waitForTimeout(random.nextInt(max - min + 1) + min);
//     }
// }

// import com.microsoft.playwright.*;
// import com.microsoft.playwright.options.*;
// import page.GroqService;
// import com.google.gson.Gson;
// import com.google.gson.reflect.TypeToken;

// import java.io.*;
// import java.nio.file.Files;
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

//     public static void main(String[] args) throws IOException {

//         loadData();
//         int totalQuestions = 90;

//         // ------------------ MULTI-ACCOUNT SUPPORT ------------------
//         String profileName = args.length > 0 ? args[0] : "acc_default";
//         Path userDataDir = Paths.get("profiles", profileName);

//         if (!Files.exists(userDataDir)) Files.createDirectories(userDataDir);
//         System.out.println("👤 Using profile: " + profileName + " → folder: " + userDataDir.toAbsolutePath());
//         // ------------------------------------------------------------

//         try (Playwright playwright = Playwright.create()) {

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
//                             .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
//                             .setViewportSize(1920, 800)
//                             .setSlowMo(0);

//             BrowserContext context = playwright.chromium().launchPersistentContext(userDataDir, options);

//             // +++ SESSION LOADING LOGIC +++
//             Path statePath = Paths.get("state_" + profileName + ".json");
//             if (Files.exists(statePath)) {
//                 System.out.println("📂 Loading session state: " + statePath);
//                 context.storageState(new BrowserContext.StorageStateOptions().setPath(statePath));
//             }

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

//             System.out.println("✅ Initialization done for account: " + profileName);

//             // ==================== SAFETY LOOP START ====================
//             while (true) {
//                 try {
//                     System.out.println("\n📊 [" + new Date() + "] STATS | MARKS: " + totalMarksGained + " | MEMORY: " + masterDatabase.size());

//                     // Navigate first to check login status
//                     page.navigate("https://www.iwacusoft.com/ubumenyibwanjye/index",
//                             new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));

//                     // Unified Login Logic
//                     page = loginIfNeeded(page, context, profileName);

//                     Locator startBtn;
//                     try {
//                         startBtn = page.locator("button:has-text('START EARN')");
//                         startBtn.waitFor(new Locator.WaitForOptions().setTimeout(10000));
//                         startBtn.click();
//                     } catch (Exception e) {
//                         System.err.println("❌ Failed to click START EARN: " + e.getMessage());
//                         hardRestart(page);
//                         continue;
//                     }

//                     // Quiz Setup
//                     page.locator("#subcategory-3").waitFor();
//                     page.selectOption("#subcategory-3", new SelectOption().setIndex(2));
//                     page.selectOption("#mySelect", new SelectOption().setValue(String.valueOf(totalQuestions)));
//                     page.click("//a[contains(@onclick,\"selectLevel('advanced')\")]");
//                     page.click("//button[contains(text(),'START')]");

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
//                                         quizFrameRetry.locator("#qTitle")
//                                                 .waitFor(new Locator.WaitForOptions().setTimeout(5000));
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
//                     continue;
//                 }
//                 humanWait(page, 2000, 3500);
//             }
//         } catch (Exception e) {
//             System.err.println("❌ Playwright initialization failed: " + e.getMessage());
//         }
//     }

//     private static Page loginIfNeeded(Page page, BrowserContext context, String profileName) {
//         try {
//             // Check if START EARN is already visible (means we are logged in)
//             if (page.locator("button:has-text('START EARN')").isVisible()) {
//                 System.out.println("✅ Session already active for " + profileName + ". Bypassing login.");
//                 return page;
//             }

//             Locator phoneInput = page.locator("input[placeholder*='Phone']");
//             if (phoneInput.isVisible()) {
//                 String accountKey = profileName.toUpperCase();
//                 String phoneEnv = System.getenv("LOGIN_PHONE_" + accountKey);
//                 if (phoneEnv == null) phoneEnv = System.getenv("LOGIN_PHONE");
                
//                 String pinEnv = System.getenv("LOGIN_PIN_" + accountKey);
//                 if (pinEnv == null) pinEnv = System.getenv("LOGIN_PIN");

//                 if (phoneEnv == null || pinEnv == null || phoneEnv.isEmpty() || pinEnv.isEmpty()) {
//                     System.out.println("⚠️ No credentials provided for " + profileName);
//                     return page;
//                 }

//                 phoneInput.fill(phoneEnv);
//                 page.locator("input[placeholder*='PIN']").fill(pinEnv);
//                 page.click("//button[contains(., 'Log in')]");
//                 page.waitForURL("**/index", new Page.WaitForURLOptions().setTimeout(15000));

//                 // Save session for next time
//                 context.storageState(new BrowserContext.StorageStateOptions()
//                         .setPath(Paths.get("state_" + profileName + ".json")));
//                 System.out.println("💾 State saved for account: " + profileName);
//             }
//         } catch (Exception e) {
//             System.err.println("❌ Login failed for account: " + profileName + " | " + e.getMessage());
//         }
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
//             StringBuilder sb = new StringBuilder("Question: ").append(qText).append("\nOptions:\n");
//             for (int idx = 0; idx < options.size(); idx++) sb.append(idx + 1).append(") ").append(options.get(idx)).append("\n");
//             sb.append("Respond with the exact NUMBER of the correct option only.");

//             String aiResponse = ai.askAI(sb.toString()).replaceAll("[^0-9]", "").trim();
//             int choiceIndex;
//             try {
//                 int number = Integer.parseInt(aiResponse);
//                 choiceIndex = (number >= 1 && number <= options.size()) ? number - 1 : random.nextInt(options.size());
//             } catch (Exception e) { choiceIndex = random.nextInt(options.size()); }
//             finalChoice = options.get(choiceIndex);
//             System.out.println("📝 Q" + i + " [AI] Chose: " + finalChoice);
//         }

//         quizFrame.locator(".opt").filter(new Locator.FilterOptions().setHasText(finalChoice)).first().click();
//         humanWait(page, 500, 1000);
//         quizFrame.locator("button:has-text('Submit'), #submitBtn").first().click();
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

//     private static void hardRestart(Page page) {
//         lastProcessedQuestion = "";
//         System.out.println("🔁 Restarting quiz...");
//         try {
//             page.navigate("https://www.iwacusoft.com/ubumenyibwanjye/index");
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


import com.microsoft.playwright.*;
import com.microsoft.playwright.options.*;
import page.GroqService;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class DoQuizTest {

    private static String lastProcessedQuestion = "";
    private static Map<String, String> masterDatabase = new HashMap<>();
    private static final String DATA_FILE = "statistics.json";
    private static int totalMarksGained = 0;
    private static final Random random = new Random();
    private static final int MAX_RETRIES = 2;
    private static final int QUESTION_TIMEOUT_MS = 10000;

    public static void main(String[] args) throws IOException {

        loadData();

        // Support for 13 accounts via command line arguments
        String profileName = args.length > 0 ? args[0] : "acc_default";
        Path userDataDir = Paths.get("profiles", profileName);

        if (!Files.exists(userDataDir)) Files.createDirectories(userDataDir);
        System.out.println("👤 ACCOUNT: " + profileName + " | Profile Path: " + userDataDir.toAbsolutePath());

        try (Playwright playwright = Playwright.create()) {

            BrowserType.LaunchPersistentContextOptions options =
                    new BrowserType.LaunchPersistentContextOptions()
                            .setHeadless(true)
                            .setIgnoreDefaultArgs(Arrays.asList("--enable-automation"))
                            .setArgs(Arrays.asList(
                                    "--disable-blink-features=AutomationControlled",
                                    "--no-sandbox",
                                    "--disable-dev-shm-usage",
                                    "--start-maximized"
                            ))
                            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                            .setViewportSize(1920, 800)
                            .setSlowMo(0);

            BrowserContext context = playwright.chromium().launchPersistentContext(userDataDir, options);
            
            // Anti-detection script
            context.addInitScript(
                    "() => {" +
                            "Object.defineProperty(navigator,'webdriver',{get:()=>undefined});" +
                            "Object.defineProperty(navigator,'languages',{get:()=>['en-US','en']});" +
                            "window.chrome={runtime:{}};" +
                            "}"
            );

            Page page = context.pages().get(0);
            GroqService ai = new GroqService();

            // Safety delay: staggered start so 13 accounts don't hit the server at the exact same millisecond
            page.waitForTimeout(random.nextInt(5000));

            // Set a session limit (e.g., 3 rounds) so the bot doesn't run forever and waste GitHub minutes
            int roundsCompleted = 0;
            while (roundsCompleted < 3) { 
                try {
                    // 🎲 STEP 1: Randomize question count (85 to 100)
                    int totalQuestions = 85 + random.nextInt(16);

                    System.out.println("\n📊 [" + new Date() + "] SESSION: " + (roundsCompleted + 1) + 
                                       " | TARGET: " + totalQuestions + " Qs | MARKS: " + totalMarksGained);

                    page = loginIfNeeded(page, context, profileName);

                    page.navigate("https://www.iwacusoft.com/ubumenyibwanjye/index",
                            new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));

                    // Click START EARN with a small human delay
                    page.waitForTimeout(1000 + random.nextInt(1000));
                    page.locator("button:has-text('START EARN')").click();

                    // Quiz Setup
                    page.locator("#subcategory-3").waitFor();
                    page.selectOption("#subcategory-3", new SelectOption().setIndex(2));
                    
                    // Apply the randomized total
                    page.selectOption("#mySelect", new SelectOption().setValue(String.valueOf(totalQuestions)));
                    
                    page.click("//a[contains(@onclick,\"selectLevel('advanced')\")]");
                    page.click("//button[contains(text(),'START')]");

                    int currentQuestion = 1;
                    while (currentQuestion <= totalQuestions) {
                        FrameLocator quizFrame = page.frameLocator("#iframeId");
                        
                        // 🛡️ ANTI-STUCK: Attempt to find the quiz frame 5 times
                        boolean frameFound = false;
                        for (int f = 0; f < 5; f++) {
                            try {
                                quizFrame.locator("#qTitle").waitFor(new Locator.WaitForOptions().setTimeout(5000));
                                frameFound = true;
                                break;
                            } catch (Exception e) {
                                System.out.println("⚠️ Frame missing (Attempt " + (f+1) + "/5). Retrying...");
                                page.waitForTimeout(2000);
                            }
                        }

                        if (!frameFound) {
                            System.err.println("❌ Quiz stuck. Triggering page refresh.");
                            break; // Breaks to hardRestart
                        }

                        processQuestion(quizFrame, page, ai, currentQuestion, System.currentTimeMillis());
                        currentQuestion++;
                        
                        // 🐢 HUMAN SPEED: Wait 1-2 seconds between questions
                        page.waitForTimeout(1000 + random.nextInt(1500));
                    }

                    roundsCompleted++;
                    saveData();
                    System.out.println("🏁 Round " + roundsCompleted + " finished.");
                    page.waitForTimeout(5000 + random.nextInt(5000)); // Rest between rounds

                } catch (Exception e) {
                    System.err.println("🔄 Main Loop Recovery: " + e.getMessage());
                    hardRestart(page);
                }
            }
            System.out.println("🛑 Daily quota finished for " + profileName + ". Closing browser.");
        } catch (Exception e) {
            System.err.println("❌ Critical Failure: " + e.getMessage());
        }
    }

    private static Page loginIfNeeded(Page page, BrowserContext context, String profileName) {
        try {
            Locator phoneInput = page.locator("input[placeholder*='Phone']");
            if (phoneInput.isVisible()) {
                String phoneEnv = System.getenv("LOGIN_PHONE");
                String pinEnv = System.getenv("LOGIN_PIN");

                if (phoneEnv == null || pinEnv == null) return page;

                // Human-like typing
                phoneInput.type(phoneEnv, new Locator.TypeOptions().setDelay(100));
                page.locator("input[placeholder*='PIN']").type(pinEnv, new Locator.TypeOptions().setDelay(100));
                
                page.click("//button[contains(., 'Log in')]");
                page.waitForURL("**/index", new Page.WaitForURLOptions().setTimeout(10000));

                context.storageState(new BrowserContext.StorageStateOptions().setPath(Paths.get("state_" + profileName + ".json")));
                System.out.println("✅ Login success for: " + profileName);
            }
        } catch (Exception e) {
            System.err.println("❌ Login Error: " + e.getMessage());
        }
        return page;
    }

    private static void hardRestart(Page page) {
        lastProcessedQuestion = "";
        System.out.println("🔁 Refreshing page...");
        try {
            page.navigate("https://www.iwacusoft.com/ubumenyibwanjye/index");
        } catch (Exception ignored) {}
    }

    private static void saveData() {
        try (Writer writer = new FileWriter(DATA_FILE)) {
            Map<String, Object> data = new HashMap<>();
            data.put("database", masterDatabase);
            data.put("totalMarks", totalMarksGained);
            new Gson().toJson(data, writer);
        } catch (IOException ignored) {}
    }

    private static void loadData() {
        try {
            File file = new File(DATA_FILE);
            if (file.exists()) {
                Reader reader = new FileReader(file);
                Map<String, Object> data = new Gson().fromJson(reader, new TypeToken<Map<String, Object>>() {}.getType());
                if (data != null) {
                    masterDatabase = (Map<String, String>) data.getOrDefault("database", new HashMap<>());
                    totalMarksGained = ((Double) data.getOrDefault("totalMarks", 0.0)).intValue();
                }
            }
        } catch (Exception ignored) {}
    }

    private static void processQuestion(FrameLocator quizFrame, Page page, GroqService ai, int i, long startTime) throws Exception {
        int cycles = 0;
        String qText = "";

        while (cycles < 50) { // 10 second wait
            try {
                qText = quizFrame.locator("#qTitle").innerText().trim();
                if (!qText.isEmpty() && !qText.contains("Loading") && !qText.equals(lastProcessedQuestion)) break;
            } catch (Exception ignored) {}
            page.waitForTimeout(200);
            cycles++;
        }

        if (qText.isEmpty() || qText.equals(lastProcessedQuestion)) throw new Exception("Q failed to load");
        lastProcessedQuestion = qText;

        List<String> options = quizFrame.locator(".opt .txt").allInnerTexts();
        options.removeIf(String::isEmpty);

        String finalChoice;
        if (masterDatabase.containsKey(qText)) {
            finalChoice = masterDatabase.get(qText);
            System.out.println("📝 Q" + i + " [Memory] " + finalChoice);
        } else {
            StringBuilder prompt = new StringBuilder("Question: ").append(qText).append("\nOptions:\n");
            for (int idx = 0; idx < options.size(); idx++) {
                prompt.append(idx + 1).append(") ").append(options.get(idx)).append("\n");
            }
            prompt.append("Respond with the NUMBER only.");

            String res = ai.askAI(prompt.toString()).replaceAll("[^0-9]", "").trim();
            int choiceIdx = (res.isEmpty()) ? 0 : Math.min(Integer.parseInt(res) - 1, options.size() - 1);
            finalChoice = options.get(Math.max(0, choiceIdx));
            System.out.println("📝 Q" + i + " [AI] " + finalChoice);
        }

        quizFrame.locator(".opt").filter(new Locator.FilterOptions().setHasText(finalChoice)).first().click();
        page.waitForTimeout(500 + random.nextInt(500));
        quizFrame.locator("button:has-text('Submit'), #submitBtn").first().click();

        // Learning logic
        try {
            page.waitForTimeout(500);
            String resultText = quizFrame.locator("#lastBody").innerText();
            if (resultText.contains("Correct:")) {
                String letter = resultText.split("Correct:")[1].trim().substring(0, 1);
                int correctIdx = letter.charAt(0) - 'A';
                if (correctIdx >= 0 && correctIdx < options.size()) {
                    String actualAns = options.get(correctIdx);
                    masterDatabase.put(qText, actualAns);
                    if (finalChoice.equalsIgnoreCase(actualAns)) totalMarksGained++;
                }
            }
        } catch (Exception ignored) {}
    }
}



// ..........................3live one..........................................

// import com.microsoft.playwright.*;
// import com.microsoft.playwright.options.*;
// import page.GroqService;
// import com.google.gson.Gson;
// import com.google.gson.reflect.TypeToken;

// import java.io.*;
// import java.nio.file.Files;
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

//     public static void main(String[] args) throws IOException {

//         loadData();
//         int totalQuestions = 90;

//         // ------------------ MULTI-ACCOUNT SUPPORT ------------------
//         // Get profile name from args; default to "acc_default"
//         String profileName = args.length > 0 ? args[0] : "acc_default";
//         Path userDataDir = Paths.get("profiles", profileName);

//         // Create unique folder per account
//         if (!Files.exists(userDataDir)) Files.createDirectories(userDataDir);
//         System.out.println("👤 Using profile: " + profileName + " → folder: " + userDataDir.toAbsolutePath());
//         // ------------------------------------------------------------

//         try (Playwright playwright = Playwright.create()) {

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

//             BrowserContext context =
//                     playwright.chromium().launchPersistentContext(userDataDir, options);

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

//             System.out.println("✅ Initialization done for account: " + profileName);

//             // ==================== SAFETY LOOP START ====================
//             while (true) {
//                 try {
//                     System.out.println(
//                             "\n📊 [" + new Date() + "] STATS | MARKS: " +
//                                     totalMarksGained + " | MEMORY: " + masterDatabase.size()
//                     );

//                     page = loginIfNeeded(page, context, profileName);

//                     page.navigate(
//                             "https://www.iwacusoft.com/ubumenyibwanjye/index",
//                             new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
//                     );

//                     Locator startBtn;
//                     try {
//                         startBtn = page.locator("button:has-text('START EARN')");
//                         startBtn.waitFor();
//                         startBtn.click();
//                     } catch (Exception e) {
//                         System.err.println("❌ Failed to click START EARN: " + e.getMessage());
//                         hardRestart(page);
//                         continue;
//                     }

//                     // ==================== Keep existing quiz logic intact ====================
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
//                                         quizFrameRetry.locator("#qTitle")
//                                                 .waitFor(new Locator.WaitForOptions().setTimeout(5000));
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
//                                 System.err.println(
//                                         "⚠️ Retry Q" + currentQuestion +
//                                                 " attempt " + attempt + " | " + e.getMessage()
//                                 );
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
//                     continue;
//                 }

//                 humanWait(page, 2000, 3500);
//             }
//             // ==================== SAFETY LOOP END ====================
//         } catch (Exception e) {
//             System.err.println("❌ Playwright initialization failed: " + e.getMessage());
//         }
//     }

//     // ==================== LOGIN METHOD ====================
//     private static Page loginIfNeeded(Page page, BrowserContext context, String profileName) {
//         try {
//             Locator phoneInput = page.locator("input[placeholder*='Phone']");
//             if (phoneInput.isVisible()) {
//                 // 🔒 Use environment variable for the correct account
//                 String phoneEnv = System.getenv("LOGIN_PHONE");
//                 String pinEnv = System.getenv("LOGIN_PIN");

//                 if (phoneEnv == null || pinEnv == null || phoneEnv.isEmpty() || pinEnv.isEmpty()) {
//                     System.out.println("⚠️ No LOGIN_PHONE / LOGIN_PIN provided for account " + profileName + ". Skipping login.");
//                     return page;
//                 }

//                 phoneInput.fill(phoneEnv);
//                 page.locator("input[placeholder*='PIN']").fill(pinEnv);
//                 page.click("//button[contains(., 'Log in')]");
//                 page.waitForURL("**/index",
//                         new Page.WaitForURLOptions().setTimeout(10000));

//                 context.storageState(
//                         new BrowserContext.StorageStateOptions()
//                                 .setPath(Paths.get("state_" + profileName + ".json"))
//                 );
//                 System.out.println("✅ Login successful for account: " + profileName);
//             }
//         } catch (Exception e) {
//             System.err.println("❌ Login failed for account: " + profileName + " | " + e.getMessage());
//         }
//         return page;
//     }

//     // ==================== HARD RESTART ====================
//     private static void hardRestart(Page page) {
//         lastProcessedQuestion = "";
//         System.out.println("🔁 Restarting quiz from beginning...");
//         try {
//             page.navigate(
//                     "https://www.iwacusoft.com/ubumenyibwanjye/index",
//                     new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
//             );
//         } catch (Exception e) {
//             System.err.println("❌ Failed to navigate during hard restart: " + e.getMessage());
//         }
//     }

//     // ==================== HUMAN WAIT ====================
//     private static void humanWait(Page page, int min, int max) {
//         int delay = random.nextInt(max - min + 1) + min;
//         page.waitForTimeout(delay);
//     }

//     // ==================== SAVE & LOAD DATA ====================
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
//                 Map<String, Object> data =
//                         new Gson().fromJson(
//                                 reader,
//                                 new TypeToken<Map<String, Object>>() {}.getType()
//                         );
//                 if (data != null) {
//                     if (data.get("database") != null)
//                         masterDatabase = (Map<String, String>) data.get("database");
//                     if (data.get("totalMarks") != null)
//                         totalMarksGained =
//                                 ((Double) data.get("totalMarks")).intValue();
//                 }
//                 System.out.println("📂 Memory Loaded: " + masterDatabase.size());
//             }
//         } catch (Exception ignored) {}
//     }

//     // ==================== PROCESS QUESTION ====================
//     private static void processQuestion(
//             FrameLocator quizFrame,
//             Page page,
//             GroqService ai,
//             int i,
//             long questionStartTime
//     ) throws Exception {

//         int cycles = 0;
//         String qText = "";

//         while (cycles < QUESTION_TIMEOUT_MS / 200) {
//             try {
//                 qText = quizFrame.locator("#qTitle").innerText().trim();
//                 if (!qText.isEmpty()
//                         && !qText.contains("Loading")
//                         && !qText.equals(lastProcessedQuestion)) break;
//             } catch (Exception ignored) {}

//             page.waitForTimeout(200);
//             cycles++;

//             if (System.currentTimeMillis() - questionStartTime > 10_000) {
//                 Locator retryBtn = quizFrame.locator("#retryBtn");
//                 if (retryBtn.isVisible()) {
//                     retryBtn.click();
//                     System.out.println("⏱️ >10s before submit → Retry clicked");
//                 }
//                 throw new Exception("Submit exceeded 10 seconds");
//             }
//         }

//         if (qText.isEmpty() || qText.equals(lastProcessedQuestion))
//             throw new Exception("Question not loaded properly");

//         lastProcessedQuestion = qText;

//         List<String> options = quizFrame.locator(".opt .txt").allInnerTexts();
//         options.removeIf(String::isEmpty);

//         if (options.isEmpty())
//             throw new Exception("No answer options loaded");

//         String finalChoice;

//         if (masterDatabase.containsKey(qText)) {
//             finalChoice = masterDatabase.get(qText);
//             System.out.println("📝 Q" + i + " [Memory] " + finalChoice);
//         } else {

//             StringBuilder promptBuilder = new StringBuilder();
//             promptBuilder.append("Question: ").append(qText).append("\nOptions:\n");

//             for (int idx = 0; idx < options.size(); idx++) {
//                 promptBuilder.append(idx + 1).append(") ")
//                         .append(options.get(idx)).append("\n");
//             }

//             promptBuilder.append(
//                     "Respond with the exact NUMBER of the correct option only."
//             );

//             String aiResponse = ai.askAI(promptBuilder.toString())
//                     .replaceAll("[^0-9]", "")
//                     .trim();

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

//             System.out.println(
//                     "📝 Q" + i + " [AI] Chose option " +
//                             (choiceIndex + 1) + ": " + finalChoice
//             );
//         }

//         Locator answerLocator = quizFrame.locator(".opt")
//                 .filter(new Locator.FilterOptions().setHasText(finalChoice))
//                 .first();

//         answerLocator.waitFor(new Locator.WaitForOptions().setTimeout(5000));
//         answerLocator.click();

//         humanWait(page, 500, 1000);

//         Locator submitBtn = quizFrame
//                 .locator("button:has-text('Submit'), #submitBtn")
//                 .first();

//         submitBtn.waitFor(new Locator.WaitForOptions().setTimeout(5000));
//         submitBtn.click();

//         page.waitForTimeout(500);

//         try {
//             String resultText = quizFrame.locator("#lastBody").innerText();
//             if (resultText.contains("Correct:")) {
//                 String correctLetter =
//                         resultText.split("Correct:")[1].trim().substring(0, 1);
//                 int correctIndex = correctLetter.charAt(0) - 'A';
//                 if (correctIndex >= 0 && correctIndex < options.size()) {
//                     String actualAns = options.get(correctIndex);
//                     masterDatabase.put(qText, actualAns);
//                     if (finalChoice.equalsIgnoreCase(actualAns))
//                         totalMarksGained++;
//                     saveData();
//                 }
//             }
//         } catch (Exception ignored) {}
//     }
// }












// import com.microsoft.playwright.*;
// import com.microsoft.playwright.options.*;
// import page.GroqService;
// import com.google.gson.Gson;
// import com.google.gson.reflect.TypeToken;

// import java.io.*;
// import java.nio.file.Files;
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
//         try {
//             page.navigate(
//                     "https://www.iwacusoft.com/ubumenyibwanjye/index",
//                     new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
//             );
//         } catch (Exception e) {
//             System.err.println("❌ Failed to navigate during hard restart: " + e.getMessage());
//         }
//     }

//     public static void main(String[] args) {

//         loadData();
//         int totalQuestions = 90;

//         try (Playwright playwright = Playwright.create()) {

//             Path userDataDir = Paths.get("bot_profile");
//             try {
//                 if (!Files.exists(userDataDir)) Files.createDirectories(userDataDir);
//             } catch (IOException e) {
//                 System.err.println("❌ Could not create bot_profile directory: " + e.getMessage());
//             }

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

//             BrowserContext context =
//                     playwright.chromium().launchPersistentContext(userDataDir, options);

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

//             // ==================== SAFETY MEASURE START ====================
//             while (true) {
//                 try {

//                     System.out.println(
//                             "\n📊 [" + new Date() + "] STATS | MARKS: " +
//                                     totalMarksGained + " | MEMORY: " + masterDatabase.size()
//                     );

//                     page = loginIfNeeded(page, context);

//                     try {
//                         page.navigate(
//                                 "https://www.iwacusoft.com/ubumenyibwanjye/index",
//                                 new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
//                         );
//                     } catch (Exception e) {
//                         System.err.println("❌ Navigation failed: " + e.getMessage());
//                         hardRestart(page);
//                         continue;
//                     }

//                     Locator startBtn;
//                     try {
//                         startBtn = page.locator("button:has-text('START EARN')");
//                         startBtn.waitFor();
//                         startBtn.click();
//                     } catch (Exception e) {
//                         System.err.println("❌ Failed to click START EARN: " + e.getMessage());
//                         hardRestart(page);
//                         continue;
//                     }

//                     // ==================== Keep your logic intact ====================
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
//                                         quizFrameRetry.locator("#qTitle")
//                                                 .waitFor(new Locator.WaitForOptions().setTimeout(5000));
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
//                                 System.err.println(
//                                         "⚠️ Retry Q" + currentQuestion +
//                                                 " attempt " + attempt + " | " + e.getMessage()
//                                 );
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
//                     continue;
//                 }

//                 humanWait(page, 2000, 3500);
//             }
//             // ==================== SAFETY MEASURE END ====================
//         } catch (Exception e) {
//             System.err.println("❌ Playwright initialization failed: " + e.getMessage());
//         }
//     }

//     // ==================== Login Method ====================
//     private static Page loginIfNeeded(Page page, BrowserContext context) {
//         try {
//             Locator phoneInput = page.locator("input[placeholder*='Phone']");
//             if (phoneInput.isVisible()) {
//                 phoneInput.fill("0786862261");
//                 page.locator("input[placeholder*='PIN']").fill("12345");
//                 page.click("//button[contains(., 'Log in')]");
//                 page.waitForURL("**/index",
//                         new Page.WaitForURLOptions().setTimeout(10000));
//                 context.storageState(
//                         new BrowserContext.StorageStateOptions()
//                                 .setPath(Paths.get("state.json"))
//                 );
//                 System.out.println("✅ Auto-login successful");
//             }
//         } catch (Exception ignored) {}
//         return page;
//     }

//     // ==================== Human Wait ====================
//     private static void humanWait(Page page, int min, int max) {
//         int delay = random.nextInt(max - min + 1) + min;
//         page.waitForTimeout(delay);
//     }

//     // ==================== Save Data ====================
//     private static void saveData() {
//         try (Writer writer = new FileWriter(DATA_FILE)) {
//             Map<String, Object> data = new HashMap<>();
//             data.put("database", masterDatabase);
//             data.put("totalMarks", totalMarksGained);
//             new Gson().toJson(data, writer);
//         } catch (IOException ignored) {}
//     }

//     // ==================== Load Data ====================
//     private static void loadData() {
//         try {
//             File file = new File(DATA_FILE);
//             if (file.exists()) {
//                 Reader reader = new FileReader(file);
//                 Map<String, Object> data =
//                         new Gson().fromJson(
//                                 reader,
//                                 new TypeToken<Map<String, Object>>() {}.getType()
//                         );
//                 if (data != null) {
//                     if (data.get("database") != null)
//                         masterDatabase = (Map<String, String>) data.get("database");
//                     if (data.get("totalMarks") != null)
//                         totalMarksGained =
//                                 ((Double) data.get("totalMarks")).intValue();
//                 }
//                 System.out.println("📂 Memory Loaded: " + masterDatabase.size());
//             }
//         } catch (Exception ignored) {}
//     }

//     // ==================== processQuestion METHOD (MISSING) ====================
//     private static void processQuestion(
//             FrameLocator quizFrame,
//             Page page,
//             GroqService ai,
//             int i,
//             long questionStartTime
//     ) throws Exception {

//         int cycles = 0;
//         String qText = "";

//         while (cycles < QUESTION_TIMEOUT_MS / 200) {
//             try {
//                 qText = quizFrame.locator("#qTitle").innerText().trim();
//                 if (!qText.isEmpty()
//                         && !qText.contains("Loading")
//                         && !qText.equals(lastProcessedQuestion)) break;
//             } catch (Exception ignored) {}

//             page.waitForTimeout(200);
//             cycles++;

//             if (System.currentTimeMillis() - questionStartTime > 10_000) {
//                 Locator retryBtn = quizFrame.locator("#retryBtn");
//                 if (retryBtn.isVisible()) {
//                     retryBtn.click();
//                     System.out.println("⏱️ >10s before submit → Retry clicked");
//                 }
//                 throw new Exception("Submit exceeded 10 seconds");
//             }
//         }

//         if (qText.isEmpty() || qText.equals(lastProcessedQuestion))
//             throw new Exception("Question not loaded properly");

//         lastProcessedQuestion = qText;

//         List<String> options = quizFrame.locator(".opt .txt").allInnerTexts();
//         options.removeIf(String::isEmpty);

//         if (options.isEmpty())
//             throw new Exception("No answer options loaded");

//         String finalChoice;

//         if (masterDatabase.containsKey(qText)) {
//             finalChoice = masterDatabase.get(qText);
//             System.out.println("📝 Q" + i + " [Memory] " + finalChoice);
//         } else {

//             StringBuilder promptBuilder = new StringBuilder();
//             promptBuilder.append("Question: ").append(qText).append("\nOptions:\n");

//             for (int idx = 0; idx < options.size(); idx++) {
//                 promptBuilder.append(idx + 1).append(") ")
//                         .append(options.get(idx)).append("\n");
//             }

//             promptBuilder.append(
//                     "Respond with the exact NUMBER of the correct option only."
//             );

//             String aiResponse = ai.askAI(promptBuilder.toString())
//                     .replaceAll("[^0-9]", "")
//                     .trim();

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

//             System.out.println(
//                     "📝 Q" + i + " [AI] Chose option " +
//                             (choiceIndex + 1) + ": " + finalChoice
//             );
//         }

//         Locator answerLocator = quizFrame.locator(".opt")
//                 .filter(new Locator.FilterOptions().setHasText(finalChoice))
//                 .first();

//         answerLocator.waitFor(new Locator.WaitForOptions().setTimeout(5000));
//         answerLocator.click();

//         humanWait(page, 500, 1000);

//         Locator submitBtn = quizFrame
//                 .locator("button:has-text('Submit'), #submitBtn")
//                 .first();

//         submitBtn.waitFor(new Locator.WaitForOptions().setTimeout(5000));
//         submitBtn.click();

//         page.waitForTimeout(500);

//         try {
//             String resultText = quizFrame.locator("#lastBody").innerText();
//             if (resultText.contains("Correct:")) {
//                 String correctLetter =
//                         resultText.split("Correct:")[1].trim().substring(0, 1);
//                 int correctIndex = correctLetter.charAt(0) - 'A';
//                 if (correctIndex >= 0 && correctIndex < options.size()) {
//                     String actualAns = options.get(correctIndex);
//                     masterDatabase.put(qText, actualAns);
//                     if (finalChoice.equalsIgnoreCase(actualAns))
//                         totalMarksGained++;
//                     saveData();
//                 }
//             }
//         } catch (Exception ignored) {}
//     }

// }
