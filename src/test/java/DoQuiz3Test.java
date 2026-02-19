import com.microsoft.playwright.*;
import com.microsoft.playwright.options.*;
import page.GroqService; // You can remove this import if you want, as it's no longer used
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

// ⚠️ CHANGE THIS NAME for each file: DoQuizTest, DoQuiz2Test, or DoQuiz3Test
public class DoQuiz3Test { 

    private static String lastProcessedQuestion = "";
    private static Map<String, String> masterDatabase = new HashMap<>();
    private static final String DATA_FILE = "statistics.json";
    private static int totalMarksGained = 0;
    private static final Random random = new Random();
    private static final int QUESTION_TIMEOUT_MS = 10000;

    public static void main(String[] args) throws IOException {
        loadData();
        int totalQuestions = random.nextInt(100 - 85 + 1) + 85;

        String profileName = args.length > 0 ? args[0] : "acc_default";
        Path userDataDir = Paths.get("profiles", profileName);

        if (!Files.exists(userDataDir)) Files.createDirectories(userDataDir);
        System.out.println("👤 Profile: " + profileName);

        try (Playwright playwright = Playwright.create()) {
            BrowserType.LaunchPersistentContextOptions options = new BrowserType.LaunchPersistentContextOptions()
                .setHeadless(true)
                .setIgnoreDefaultArgs(Arrays.asList("--enable-automation"))
                .setArgs(Arrays.asList("--disable-blink-features=AutomationControlled", "--no-sandbox", "--disable-dev-shm-usage"))
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/122.0.0.0 Safari/537.36")
                .setViewportSize(1920, 1080);

            BrowserContext context = playwright.chromium().launchPersistentContext(userDataDir, options);
            Page page = context.pages().get(0);

            while (true) {
                try {
                    System.out.println("\n📊 Database Size: " + masterDatabase.size() + " | Session Marks: " + totalMarksGained);
                    
                    page = loginIfNeeded(page, context, profileName);
                    page.navigate("https://www.iwacusoft.com/ubumenyibwanjye/index", 
                        new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));

                    page.locator("button:has-text('START EARN')").click();
                    page.locator("#subcategory-1").waitFor();
                    page.selectOption("#subcategory-1", new SelectOption().setIndex(2));
                    page.selectOption("#mySelect", new SelectOption().setValue(String.valueOf(totalQuestions)));
                    page.click("//a[contains(@onclick,\"selectLevel('advanced')\")]");
                    page.click("//button[contains(text(),'START')]");

                    FrameLocator quizFrame = page.frameLocator("#iframeId");
                    int currentQuestion = 1;

                    while (currentQuestion <= totalQuestions) {
                        processQuestion(quizFrame, page, currentQuestion);
                        currentQuestion++;
                    }

                    saveData();
                    System.out.println("🏁 Batch Completed.");
                    humanWait(page, 2000, 5000);

                } catch (Exception e) {
                    System.err.println("🔄 Restarting: " + e.getMessage());
                    page.navigate("https://www.iwacusoft.com/ubumenyibwanjye/index");
                }
            }
        }
    }

    private static void processQuestion(FrameLocator quizFrame, Page page, int i) throws Exception {
        int cycles = 0;
        String qText = "";

        // Wait for question to load
        while (cycles < 50) {
            try {
                qText = quizFrame.locator("#qTitle").innerText().trim();
                if (!qText.isEmpty() && !qText.contains("Loading") && !qText.equals(lastProcessedQuestion)) break;
            } catch (Exception ignored) {}
            page.waitForTimeout(200);
            cycles++;
        }

        if (qText.isEmpty() || qText.equals(lastProcessedQuestion)) throw new Exception("Question Timeout");
        lastProcessedQuestion = qText;

        List<String> options = quizFrame.locator(".opt .txt").allInnerTexts();
        options.removeIf(String::isEmpty);

        String choiceToClick;
        boolean isNewQuestion = false;

        // CHECK MEMORY
        if (masterDatabase.containsKey(qText)) {
            choiceToClick = masterDatabase.get(qText);
            System.out.println("📝 Q" + i + " [Found]: " + choiceToClick);
        } else {
            // NEW QUESTION: PICK RANDOM
            choiceToClick = options.get(random.nextInt(options.size()));
            isNewQuestion = true;
            System.out.println("📝 Q" + i + " [New]: Picking random to learn...");
        }

        // Click and Submit
        quizFrame.locator(".opt").filter(new Locator.FilterOptions().setHasText(choiceToClick)).first().click();
        quizFrame.locator("button:has-text('Submit'), #submitBtn").first().click();

        // LEARN THE CORRECT ANSWER
        try {
            page.waitForTimeout(600); // Wait for result display
            String resultText = quizFrame.locator("#lastBody").innerText();
            
            if (resultText.contains("Correct:")) {
                String correctLetter = resultText.split("Correct:")[1].trim().substring(0, 1);
                int correctIndex = correctLetter.charAt(0) - 'A';
                
                if (correctIndex >= 0 && correctIndex < options.size()) {
                    String actualAns = options.get(correctIndex);
                    
                    // Always update database to ensure it's correct
                    if (!masterDatabase.containsKey(qText)) {
                        masterDatabase.put(qText, actualAns);
                        saveData(); // Save immediately for new discoveries
                        System.out.println("💡 Learned: " + actualAns);
                    }

                    if (choiceToClick.equalsIgnoreCase(actualAns)) totalMarksGained++;
                }
            }
        } catch (Exception ignored) {}
    }

    // --- SHARED DATA LOGIC ---
    private static synchronized void saveData() {
        Map<String, String> diskDb = new HashMap<>();
        int diskMarks = 0;
        try {
            File file = new File(DATA_FILE);
            if (file.exists()) {
                Reader r = new FileReader(file);
                Map<String, Object> data = new Gson().fromJson(r, new TypeToken<Map<String, Object>>(){}.getType());
                if (data != null) {
                    if (data.get("database") != null) diskDb = (Map<String, String>) data.get("database");
                    if (data.get("totalMarks") != null) diskMarks = ((Double) data.get("totalMarks")).intValue();
                }
            }
            diskDb.putAll(masterDatabase);
            try (Writer w = new FileWriter(DATA_FILE)) {
                Map<String, Object> out = new HashMap<>();
                out.put("database", diskDb);
                out.put("totalMarks", Math.max(diskMarks, totalMarksGained));
                new Gson().toJson(out, w);
                masterDatabase = diskDb;
            }
        } catch (Exception e) { System.err.println("Save Error: " + e.getMessage()); }
    }

    private static void loadData() {
        try {
            File file = new File(DATA_FILE);
            if (file.exists()) {
                Reader r = new FileReader(file);
                Map<String, Object> data = new Gson().fromJson(r, new TypeToken<Map<String, Object>>(){}.getType());
                if (data != null) {
                    if (data.get("database") != null) masterDatabase = (Map<String, String>) data.get("database");
                    if (data.get("totalMarks") != null) totalMarksGained = ((Double) data.get("totalMarks")).intValue();
                }
            }
        } catch (Exception ignored) {}
    }

    private static Page loginIfNeeded(Page page, BrowserContext context, String profileName) {
        try {
            if (page.locator("input[placeholder*='Phone']").isVisible()) {
                page.locator("input[placeholder*='Phone']").fill(System.getenv("LOGIN_PHONE"));
                page.locator("input[placeholder*='PIN']").fill(System.getenv("LOGIN_PIN"));
                page.click("//button[contains(., 'Log in')]");
                page.waitForURL("**/index");
            }
        } catch (Exception ignored) {}
        return page;
    }

    private static void humanWait(Page page, int min, int max) {
        page.waitForTimeout(random.nextInt(max - min + 1) + min);
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

// // ⚠️ CHANGE THIS NAME for each file: DoQuizTest, DoQuiz2Test, or DoQuiz3Test
// public class DoQuiz3Test { 

//     private static String lastProcessedQuestion = "";
//     private static Map<String, String> masterDatabase = new HashMap<>();
//     private static final String DATA_FILE = "statistics.json";
//     private static int totalMarksGained = 0;
//     private static final Random random = new Random();
//     private static final int MAX_RETRIES = 2;
//     private static final int QUESTION_TIMEOUT_MS = 10000;

//     public static void main(String[] args) throws IOException {
//         loadData();
//         int totalQuestions = random.nextInt(100 - 85 + 1) + 85;

//         String profileName = args.length > 0 ? args[0] : "acc_default";
//         Path userDataDir = Paths.get("profiles", profileName);

//         if (!Files.exists(userDataDir)) Files.createDirectories(userDataDir);
//         System.out.println("👤 Profile: " + profileName);

//         try (Playwright playwright = Playwright.create()) {
//             BrowserType.LaunchPersistentContextOptions options = new BrowserType.LaunchPersistentContextOptions()
//                 .setHeadless(true)
//                 .setIgnoreDefaultArgs(Arrays.asList("--enable-automation"))
//                 .setArgs(Arrays.asList("--disable-blink-features=AutomationControlled", "--no-sandbox", "--disable-dev-shm-usage"))
//                 .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/122.0.0.0 Safari/537.36")
//                 .setViewportSize(1920, 1080);

//             BrowserContext context = playwright.chromium().launchPersistentContext(userDataDir, options);
//             Page page = context.pages().get(0);
//             GroqService ai = new GroqService();

//             while (true) {
//                 try {
//                     System.out.println("\n📊 Memory: " + masterDatabase.size() + " | Marks: " + totalMarksGained);
                    
//                     page = loginIfNeeded(page, context, profileName);
//                     page.navigate("https://www.iwacusoft.com/ubumenyibwanjye/index", 
//                         new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));

//                     // Navigation
//                     page.locator("button:has-text('START EARN')").click();
//                     page.locator("#subcategory-3").waitFor();
//                     page.selectOption("#subcategory-3", new SelectOption().setIndex(2));
//                     page.selectOption("#mySelect", new SelectOption().setValue(String.valueOf(totalQuestions)));
//                     page.click("//a[contains(@onclick,\"selectLevel('advanced')\")]");
//                     page.click("//button[contains(text(),'START')]");

//                     FrameLocator quizFrame = page.frameLocator("#iframeId");
//                     int currentQuestion = 1;

//                     while (currentQuestion <= totalQuestions) {
//                         long questionStartTime = System.currentTimeMillis();
//                         processQuestion(quizFrame, page, ai, currentQuestion, questionStartTime);
//                         currentQuestion++;
//                     }

//                     saveData(); // Merge findings to shared memory
//                     System.out.println("🏁 Batch Done.");
//                     humanWait(page, 2000, 5000);

//                 } catch (Exception e) {
//                     System.err.println("🔄 Restarting: " + e.getMessage());
//                     page.navigate("https://www.iwacusoft.com/ubumenyibwanjye/index");
//                 }
//             }
//         }
//     }

//     // ==================== DATA SAVING (SHARED MEMORY LOGIC) ====================
//     private static synchronized void saveData() {
//         Map<String, String> diskDatabase = new HashMap<>();
//         int diskMarks = 0;

//         // Load latest from disk to prevent overwriting other accounts
//         try {
//             File file = new File(DATA_FILE);
//             if (file.exists()) {
//                 Reader reader = new FileReader(file);
//                 Map<String, Object> data = new Gson().fromJson(reader, new TypeToken<Map<String, Object>>() {}.getType());
//                 if (data != null) {
//                     if (data.get("database") != null) diskDatabase = (Map<String, String>) data.get("database");
//                     if (data.get("totalMarks") != null) diskMarks = ((Double) data.get("totalMarks")).intValue();
//                 }
//             }
//         } catch (Exception e) { System.err.println("Read Error: " + e.getMessage()); }

//         // Merge local data into disk data
//         diskDatabase.putAll(masterDatabase);
//         int finalMarks = Math.max(diskMarks, totalMarksGained);

//         try (Writer writer = new FileWriter(DATA_FILE)) {
//             Map<String, Object> data = new HashMap<>();
//             data.put("database", diskDatabase);
//             data.put("totalMarks", finalMarks);
//             new Gson().toJson(data, writer);
//             masterDatabase = diskDatabase; // Update local brain
//         } catch (IOException e) { System.err.println("Write Error: " + e.getMessage()); }
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
//             }
//         } catch (Exception ignored) {}
//     }

//     // ==================== PROCESS QUESTION (REMAINS UNTOUCHED) ====================
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

//         if (qText.isEmpty() || qText.equals(lastProcessedQuestion)) throw new Exception("Question Error");
//         lastProcessedQuestion = qText;

//         List<String> options = quizFrame.locator(".opt .txt").allInnerTexts();
//         options.removeIf(String::isEmpty);

//         String finalChoice;
//         if (masterDatabase.containsKey(qText)) {
//             finalChoice = masterDatabase.get(qText);
//         } else {
//             // AI Logic
//             StringBuilder pb = new StringBuilder();
//             pb.append("Question: ").append(qText).append("\nOptions:\n");
//             for (int idx = 0; idx < options.size(); idx++) pb.append(idx + 1).append(") ").append(options.get(idx)).append("\n");
//             pb.append("Respond with the exact NUMBER only.");
//             String res = ai.askAI(pb.toString()).replaceAll("[^0-9]", "").trim();
//             int choiceIndex;
//             try {
//                 int n = Integer.parseInt(res);
//                 choiceIndex = (n >= 1 && n <= options.size()) ? n - 1 : random.nextInt(options.size());
//             } catch (Exception e) { choiceIndex = random.nextInt(options.size()); }
//             finalChoice = options.get(choiceIndex);
//         }

//         quizFrame.locator(".opt").filter(new Locator.FilterOptions().setHasText(finalChoice)).first().click();
//         quizFrame.locator("button:has-text('Submit'), #submitBtn").first().click();

//         // Learning Truth
//         try {
//             page.waitForTimeout(500);
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

//     private static Page loginIfNeeded(Page page, BrowserContext context, String profileName) {
//         try {
//             if (page.locator("input[placeholder*='Phone']").isVisible()) {
//                 page.locator("input[placeholder*='Phone']").fill(System.getenv("LOGIN_PHONE"));
//                 page.locator("input[placeholder*='PIN']").fill(System.getenv("LOGIN_PIN"));
//                 page.click("//button[contains(., 'Log in')]");
//                 page.waitForURL("**/index");
//             }
//         } catch (Exception ignored) {}
//         return page;
//     }

//     private static void humanWait(Page page, int min, int max) {
//         page.waitForTimeout(random.nextInt(max - min + 1) + min);
//     }
// }
