
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
//         // int totalQuestions = 90;
//         int totalQuestions = random.nextInt(100 - 85 + 1) + 85;


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
//                   // <<<<<<<<<<<<<<<<<<<<SELECT RANDON QUESTIONS<<<<<<<<<<<<<<<<<
//                     System.out.println("🎯 New batch size: " + totalQuestions);
//                   // <<<<<<<<<<<<<<<<<<<END OF RANDOM QUESTIONS<<<<<<<<<<<<<<<<<<

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
    private static final int QUESTION_TIMEOUT_MS = 15000;

    public static void main(String[] args) throws IOException {

        loadData();

        // ------------------ MULTI-ACCOUNT SUPPORT ------------------
        String profileName = args.length > 0 ? args[0] : "acc_default";
        Path userDataDir = Paths.get("profiles", profileName);

        if (!Files.exists(userDataDir)) Files.createDirectories(userDataDir);
        System.out.println("👤 Using profile: " + profileName);
        // ------------------------------------------------------------

        try (Playwright playwright = Playwright.create()) {

            BrowserType.LaunchPersistentContextOptions options =
                    new BrowserType.LaunchPersistentContextOptions()
                            .setHeadless(true)
                            .setIgnoreDefaultArgs(Arrays.asList("--enable-automation"))
                            .setArgs(Arrays.asList(
                                    "--disable-blink-features=AutomationControlled",
                                    "--no-sandbox",
                                    "--disable-dev-shm-usage"
                            ))
                            .setViewportSize(1920, 800)
                            .setSlowMo(0);

            BrowserContext context = playwright.chromium().launchPersistentContext(userDataDir, options);
            Page page = context.pages().get(0);
            GroqService ai = new GroqService();

            while (true) {
                try {
                    int totalQuestions = random.nextInt(100 - 85 + 1) + 85;
                    System.out.println("\n📊 [" + new Date() + "] STATS | MARKS: " + totalMarksGained + " | MEMORY: " + masterDatabase.size());
                    
                    page = loginIfNeeded(page, context, profileName);                  

                    page.navigate("https://www.iwacusoft.com/ubumenyibwanjye/index", 
                        new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));

                    // 1. Click Start Earn
                    page.locator("button:has-text('START EARN')").waitFor();
                    page.click("button:has-text('START EARN')");

                    // 2. Random Subject Selection + Sub-Subject Selection (Index 2)
                    String[][] subjectData = {
                        {"#subcategory-1", "Technology And It"},
                        {"#subcategory-3", "Language"},
                        {"#subcategory-7", "Education"},
                        {"#subcategory-14", "General Studies"},
                        {"#subcategory-2", "Business And Management"},
                        {"#subcategory-4", "Health And Nutrition"},
                        {"#subcategory-10", "Architecture And Earth Science"},
                        {"#subcategory-75", "Ikinyarwanda"}
                    };

                    int randomIndex = random.nextInt(subjectData.length);
                    String subId = subjectData[randomIndex][0];
                    System.out.println("🎯 Selected Subject: " + subjectData[randomIndex][1]);

                    page.locator(subId).waitFor();
                    page.click(subId);
                    
                    // CRITICAL: Select index 2 to reveal the #mySelect dropdown
                    page.selectOption(subId, new SelectOption().setIndex(2));

                    // 3. Select Batch Size and Level
                    page.selectOption("#mySelect", new SelectOption().setValue(String.valueOf(totalQuestions)));
                    page.click("//a[contains(@onclick,\"selectLevel('advanced')\")]");
                    page.click("//button[contains(text(),'START')]");

                    FrameLocator quizFrame = page.frameLocator("#iframeId");
                    int currentQuestion = 1;

                    while (currentQuestion <= totalQuestions) {
                        try {
                            processQuestion(quizFrame, page, ai, currentQuestion);
                            currentQuestion++;
                        } catch (Exception e) {
                            System.err.println("⚠️ Q" + currentQuestion + " Error: " + e.getMessage());
                            page.waitForTimeout(3000); // Wait on error
                        }
                    }

                    saveData();
                    System.out.println("🏁 Batch finished. Cooling down...");
                    humanWait(page, 5000, 10000);

                } catch (Exception e) {
                    System.err.println("🔄 Main loop error: " + e.getMessage());
                    hardRestart(page);
                }
            }
        }
    }

    private static void processQuestion(FrameLocator quizFrame, Page page, GroqService ai, int i) throws Exception {
        // Wait for question to load
        quizFrame.locator("#qTitle").waitFor(new Locator.WaitForOptions().setTimeout(10000));
        String qText = quizFrame.locator("#qTitle").innerText().trim();
        
        if (qText.isEmpty() || qText.equals(lastProcessedQuestion)) {
            throw new Exception("Question text empty or not refreshed");
        }
        lastProcessedQuestion = qText;

        List<String> options = quizFrame.locator(".opt .txt").allInnerTexts();
        options.removeIf(String::isEmpty);

        String finalChoice;

        if (masterDatabase.containsKey(qText)) {
            finalChoice = masterDatabase.get(qText);
            System.out.println("📝 Q" + i + " [Memory] " + finalChoice);
        } else {
            // STAGGER: Wait to avoid Rate Limits (18 bots sharing keys)
            page.waitForTimeout(1500 + random.nextInt(2000));

            StringBuilder sb = new StringBuilder("Question: " + qText + "\nOptions:\n");
            for (int idx = 0; idx < options.size(); idx++) {
                sb.append(idx + 1).append(") ").append(options.get(idx)).append("\n");
            }
            sb.append("Respond with the exact NUMBER only.");

            String raw = ai.askAI(sb.toString());

            // 🛡️ NO-GUESS SAFETY: Throw exception if API fails
            if (raw == null || raw.trim().isEmpty()) {
                throw new Exception("AI returned null - possible Rate Limit");
            }

            int choiceIdx = Integer.parseInt(raw.replaceAll("[^0-9]", "")) - 1;
            if (choiceIdx < 0 || choiceIdx >= options.size()) {
                throw new Exception("AI Index out of range: " + choiceIdx);
            }

            finalChoice = options.get(choiceIdx);
            System.out.println("📝 Q" + i + " [AI] Chose: " + finalChoice);
        }

        // Click answer
        quizFrame.locator(".opt").filter(new Locator.FilterOptions().setHasText(finalChoice)).first().click();
        
        // Submit
        quizFrame.locator("button:has-text('Submit'), #submitBtn").first().click();

        // Learning Logic
        try {
            page.waitForTimeout(1000);
            String resultText = quizFrame.locator("#lastBody").innerText();
            if (resultText.contains("Correct:")) {
                int correctIndex = resultText.split("Correct:")[1].trim().charAt(0) - 'A';
                String actualAns = options.get(correctIndex);
                masterDatabase.put(qText, actualAns);
                if (finalChoice.equals(actualAns)) totalMarksGained++;
                saveData();
            }
        } catch (Exception ignored) {}
    }

    private static Page loginIfNeeded(Page page, BrowserContext context, String profileName) {
        try {
            if (page.locator("input[placeholder*='Phone']").isVisible()) {
                String phone = System.getenv("LOGIN_PHONE");
                String pin = System.getenv("LOGIN_PIN");
                if (phone == null || pin == null) return page;

                page.locator("input[placeholder*='Phone']").fill(phone);
                page.locator("input[placeholder*='PIN']").fill(pin);
                page.click("//button[contains(., 'Log in')]");
                page.waitForURL("**/index");
                context.storageState(new BrowserContext.StorageStateOptions().setPath(Paths.get("state_" + profileName + ".json")));
            }
        } catch (Exception ignored) {}
        return page;
    }

    private static void hardRestart(Page page) {
        lastProcessedQuestion = "";
        try { page.navigate("https://www.iwacusoft.com/ubumenyibwanjye/index"); } catch (Exception ignored) {}
    }

    private static void humanWait(Page page, int min, int max) {
        page.waitForTimeout(random.nextInt(max - min + 1) + min);
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
            File f = new File(DATA_FILE);
            if (f.exists()) {
                Reader r = new FileReader(f);
                Map<String, Object> data = new Gson().fromJson(r, new TypeToken<Map<String, Object>>(){}.getType());
                if (data != null) {
                    masterDatabase = (Map<String, String>) data.get("database");
                    totalMarksGained = ((Double) data.get("totalMarks")).intValue();
                    System.out.println("📂 Loaded Memory: " + masterDatabase.size());
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
