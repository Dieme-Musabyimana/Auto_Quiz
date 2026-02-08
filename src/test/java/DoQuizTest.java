import com.microsoft.playwright.*;
import com.microsoft.playwright.options.*;
import page.GroqService;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class DoQuizTest {

    private static String lastProcessedQuestion = "";
    private static Map<String, String> masterDatabase = new HashMap<>();
    private static final String DATA_FILE = "statistics.json";
    private static int totalMarksGained = 0;
    private static final Random random = new Random();
    private static final int MAX_RETRIES = 2;
    private static final int QUESTION_TIMEOUT_MS = 10000;

    public static void main(String[] args) throws Exception {

        loadData();
        int totalQuestions = 90;

        // 🔹 ACCOUNT NAME
        String account = args.length > 0 ? args[0].toUpperCase() : "ACC1";
        System.out.println("👤 Running account: " + account);

        // 🔹 PROFILE & STATE
        Path profileDir = Paths.get("profiles", account);
        Files.createDirectories(profileDir);
        Path statePath = Paths.get("state_" + account + ".json");

        try (Playwright playwright = Playwright.create()) {

            BrowserType.LaunchPersistentContextOptions options =
                    new BrowserType.LaunchPersistentContextOptions()
                            .setHeadless(true)
                            .setIgnoreDefaultArgs(List.of("--enable-automation"))
                            .setArgs(List.of(
                                    "--disable-blink-features=AutomationControlled",
                                    "--no-sandbox",
                                    "--disable-dev-shm-usage"
                            ))
                            .setUserAgent(
                                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                                            "AppleWebKit/537.36 (KHTML, like Gecko) " +
                                            "Chrome/122.0.0.0 Safari/537.36"
                            )
                            .setViewportSize(1920, 800);

            BrowserContext context =
                    playwright.chromium().launchPersistentContext(profileDir, options);

            // 🧠 Anti-bot
            context.addInitScript(
                    "() => {" +
                            "Object.defineProperty(navigator,'webdriver',{get:()=>undefined});" +
                            "Object.defineProperty(navigator,'languages',{get:()=>['en-US','en']});" +
                            "Object.defineProperty(navigator,'plugins',{get:()=>[1,2,3,4,5]});" +
                            "window.chrome={runtime:{}};" +
                            "}"
            );

            Page page = context.pages().get(0);
            GroqService ai = new GroqService();

            while (true) {
                try {

                    page.navigate(
                            "https://www.iwacusoft.com/ubumenyibwanjye/index",
                            new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                    );

                    loginIfNeeded(page, context, account, statePath);

                    Locator startBtn = page.locator("button:has-text('START EARN')");
                    startBtn.waitFor();
                    startBtn.click();

                    page.locator("#subcategory-3").waitFor();
                    page.selectOption("#subcategory-3", new SelectOption().setIndex(2));
                    page.selectOption("#mySelect",
                            new SelectOption().setValue(String.valueOf(totalQuestions)));
                    page.click("//a[contains(@onclick,\"selectLevel('advanced')\")]");
                    page.click("//button[contains(text(),'START')]");

                    FrameLocator quizFrame = page.frameLocator("#iframeId");
                    int q = 1;

                    while (q <= totalQuestions) {
                        try {
                            processQuestion(quizFrame, page, ai, q, System.currentTimeMillis());
                            q++;
                        } catch (Exception e) {
                            page.waitForTimeout(2000);
                        }
                    }

                    saveData();
                    humanWait(page, 1500, 3000);

                } catch (Exception e) {
                    System.err.println("🔁 Restarting for " + account);
                }
            }
        }
    }

    // ================= LOGIN PER ACCOUNT =================
    private static void loginIfNeeded(Page page,
                                     BrowserContext context,
                                     String account,
                                     Path statePath) {

        try {
            page.waitForTimeout(2000);

            if (page.locator("button:has-text('START EARN')").isVisible()) {
                System.out.println("✅ Already logged in: " + account);
                return;
            }

            Locator phoneInput = page.locator("input[placeholder*='Phone']");
            if (!phoneInput.isVisible()) return;

            String phone = System.getenv("LOGIN_PHONE_" + account);
            String pin   = System.getenv("LOGIN_PIN_" + account);

            if (phone == null || pin == null) {
                throw new RuntimeException("Missing secrets for " + account);
            }

            phoneInput.fill(phone);
            page.locator("input[placeholder*='PIN']").fill(pin);
            page.click("//button[contains(., 'Log in')]");

            page.waitForURL("**/index",
                    new Page.WaitForURLOptions().setTimeout(15000));

            context.storageState(
                    new BrowserContext.StorageStateOptions().setPath(statePath)
            );

            System.out.println("💾 Login saved for " + account);

        } catch (Exception e) {
            System.err.println("❌ Login failed for " + account + ": " + e.getMessage());
        }
    }

    // ================= QUESTION LOGIC (UNCHANGED) =================
    private static void processQuestion(FrameLocator quizFrame,
                                        Page page,
                                        GroqService ai,
                                        int i,
                                        long start) throws Exception {

        quizFrame.locator("#qTitle")
                .waitFor(new Locator.WaitForOptions().setTimeout(5000));

        String qText = quizFrame.locator("#qTitle").innerText().trim();
        if (qText.equals(lastProcessedQuestion)) throw new Exception();

        lastProcessedQuestion = qText;

        List<String> options = quizFrame.locator(".opt .txt").allInnerTexts();
        options.removeIf(String::isEmpty);

        String choice = masterDatabase.getOrDefault(qText,
                options.get(random.nextInt(options.size())));

        quizFrame.locator(".opt")
                .filter(new Locator.FilterOptions().setHasText(choice))
                .first().click();

        quizFrame.locator("button:has-text('Submit')").click();
    }

    private static void humanWait(Page page, int min, int max) {
        page.waitForTimeout(random.nextInt(max - min + 1) + min);
    }

    private static void saveData() {}
    private static void loadData() {}
}


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
