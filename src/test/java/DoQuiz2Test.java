import com.microsoft.playwright.*;
import com.microsoft.playwright.options.*;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class DoQuiz2Test {

    private static String lastProcessedQuestion = "";
    private static Map<String, String> masterDatabase = new HashMap<>();
    private static final String DATA_FILE = "statistics.json";
    private static int totalMarksGained = 0;
    private static final Random random = new Random();
    private static final int QUESTION_TIMEOUT_MS = 10000;

    public static void main(String[] args) throws IOException {

        loadData();
        int totalQuestions = random.nextInt(100 - 85 + 1) + 85;

        // 🔐 KEEP PERSISTENT LOGIN
        String profileName = args.length > 0 ? args[0] : "acc_default";
        Path userDataDir = Paths.get("profiles", profileName);

        if (!Files.exists(userDataDir))
            Files.createDirectories(userDataDir);

        System.out.println("👤 Profile: " + profileName);

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
                            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/122.0.0.0 Safari/537.36")
                            .setViewportSize(1920, 1080);

            BrowserContext context =
                    playwright.chromium().launchPersistentContext(userDataDir, options);

            Page page = context.pages().get(0);

            while (true) {
                try {

                    System.out.println("\n📊 MEMORY: " + masterDatabase.size()
                            + " | MARKS: " + totalMarksGained);

                    page = loginIfNeeded(page, context, profileName);

                    page.navigate(
                            "https://www.iwacusoft.com/ubumenyibwanjye/index",
                            new Page.NavigateOptions()
                                    .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                    );

                    // ---------------- START BUTTON ----------------
                    Locator startBtn = page.locator("button:has-text('START EARN')");
                    startBtn.waitFor();
                    startBtn.click();

                    page.waitForTimeout(1000);

                    // ---------------- PREVENT AUTO-RESUME ----------------
                    Locator resumeBtn = page.locator("button:has-text('Resume')");
                    if (resumeBtn.isVisible()) {
                        resumeBtn.click();
                        page.waitForTimeout(1000);
                    }

                    // ---------------- FORCE CORRECT SUBCATEGORY ----------------
                    Locator dropdown = page.locator("#subcategory-2");
                    dropdown.waitFor();

                    // Reset dropdown first (important!)
                    try {
                        dropdown.selectOption(new SelectOption().setValue(""));
                        page.waitForTimeout(500);
                    } catch (Exception ignored) {}

                    // Now select correct subject (index 3)
                    dropdown.selectOption(new SelectOption().setIndex(3));

                    // Force change event
                    page.evalOnSelector("#subcategory-2",
                            "el => el.dispatchEvent(new Event('change', { bubbles: true }))");

                    // Wait for server response
                    page.waitForLoadState(LoadState.NETWORKIDLE);

                    // Debug confirmation
                    String selectedText = dropdown.evaluate(
                            "el => el.options[el.selectedIndex].text"
                    ).toString();

                    System.out.println("🎯 Targeting Subject: " + selectedText);

                    page.waitForTimeout(1000);

                    // ---------------- QUIZ SETTINGS ----------------
                    page.selectOption("#mySelect",
                            new SelectOption().setValue(String.valueOf(totalQuestions)));

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
                    page.reload();
                }
            }
        }
    }

    // ---------------- PROCESS QUESTION ----------------
    private static void processQuestion(FrameLocator quizFrame, Page page, int i) throws Exception {

        int cycles = 0;
        String qText = "";

        while (cycles < QUESTION_TIMEOUT_MS / 200) {
            try {
                qText = quizFrame.locator("#qTitle").innerText().trim();
                if (!qText.isEmpty()
                        && !qText.contains("Loading")
                        && !qText.equals(lastProcessedQuestion)) break;
            } catch (Exception ignored) {}

            page.waitForTimeout(200);
            cycles++;
        }

        if (qText.isEmpty() || qText.equals(lastProcessedQuestion))
            throw new Exception("Question timeout");

        lastProcessedQuestion = qText;

        List<String> options = quizFrame.locator(".opt .txt").allInnerTexts();
        options.removeIf(String::isEmpty);

        if (options.isEmpty())
            throw new Exception("No options found");

        String choice;

        if (masterDatabase.containsKey(qText)) {
            choice = masterDatabase.get(qText);
            System.out.println("📝 Q" + i + " [Memory]: " + choice);
        } else {
            choice = options.get(random.nextInt(options.size()));
            System.out.println("📝 Q" + i + " [New]");
        }

        quizFrame.locator(".opt")
                .filter(new Locator.FilterOptions().setHasText(choice))
                .first()
                .click();

        quizFrame.locator("button:has-text('Submit'), #submitBtn")
                .first()
                .click();

        page.waitForTimeout(600);

        try {
            String resultText = quizFrame.locator("#lastBody").innerText();

            if (resultText.contains("Correct:")) {
                String correctLetter =
                        resultText.split("Correct:")[1].trim().substring(0, 1);

                int correctIndex = correctLetter.charAt(0) - 'A';

                if (correctIndex >= 0 && correctIndex < options.size()) {
                    String correctAnswer = options.get(correctIndex);

                    masterDatabase.put(qText, correctAnswer);

                    if (choice.equalsIgnoreCase(correctAnswer))
                        totalMarksGained++;

                    saveData();
                }
            }
        } catch (Exception ignored) {}
    }

    // ---------------- LOGIN ----------------
    private static Page loginIfNeeded(Page page,
                                      BrowserContext context,
                                      String profileName) {
        try {
            Locator phoneInput = page.locator("input[placeholder*='Phone']");
            if (phoneInput.isVisible()) {

                String phoneEnv = System.getenv("LOGIN_PHONE");
                String pinEnv = System.getenv("LOGIN_PIN");

                if (phoneEnv == null || pinEnv == null ||
                        phoneEnv.isEmpty() || pinEnv.isEmpty()) {
                    System.out.println("⚠️ No LOGIN_PHONE / LOGIN_PIN provided.");
                    return page;
                }

                phoneInput.fill(phoneEnv);
                page.locator("input[placeholder*='PIN']").fill(pinEnv);
                page.click("//button[contains(., 'Log in')]");
                page.waitForURL("**/index");

                context.storageState(
                        new BrowserContext.StorageStateOptions()
                                .setPath(Paths.get("state_" + profileName + ".json"))
                );

                System.out.println("✅ Login successful.");
            }
        } catch (Exception e) {
            System.err.println("❌ Login failed: " + e.getMessage());
        }
        return page;
    }

    // ---------------- WAIT ----------------
    private static void humanWait(Page page, int min, int max) {
        int delay = random.nextInt(max - min + 1) + min;
        page.waitForTimeout(delay);
    }

    // ---------------- SAVE / LOAD ----------------
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
                Map<String, Object> data =
                        new Gson().fromJson(
                                reader,
                                new TypeToken<Map<String, Object>>() {}.getType()
                        );
                if (data != null) {
                    if (data.get("database") != null)
                        masterDatabase = (Map<String, String>) data.get("database");
                    if (data.get("totalMarks") != null)
                        totalMarksGained =
                                ((Double) data.get("totalMarks")).intValue();
                }
            }
        } catch (Exception ignored) {}
    
