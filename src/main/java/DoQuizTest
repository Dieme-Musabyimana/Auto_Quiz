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

    // ==================== HARD RESTART FUNCTION ====================
    private static void hardRestart(Page page) {
        lastProcessedQuestion = "";
        System.out.println("🔁 Restarting quiz from beginning...");
        try {
            page.navigate(
                    "https://www.iwacusoft.com/ubumenyibwanjye/index",
                    new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
            );
        } catch (Exception e) {
            System.err.println("❌ Failed to navigate during hard restart: " + e.getMessage());
        }
    }

    public static void main(String[] args) {

        loadData();
        int totalQuestions = 90;

        try (Playwright playwright = Playwright.create()) {

            Path userDataDir = Paths.get("bot_profile");
            try {
                if (!Files.exists(userDataDir)) Files.createDirectories(userDataDir);
            } catch (IOException e) {
                System.err.println("❌ Could not create bot_profile directory: " + e.getMessage());
            }

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
                            .setUserAgent(
                                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                                            "AppleWebKit/537.36 (KHTML, like Gecko) " +
                                            "Chrome/122.0.0.0 Safari/537.36"
                            )
                            .setViewportSize(1920, 800)
                            .setSlowMo(0);

            BrowserContext context =
                    playwright.chromium().launchPersistentContext(userDataDir, options);

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

            // ==================== SAFETY MEASURE START ====================
            while (true) {
                try {

                    System.out.println(
                            "\n📊 [" + new Date() + "] STATS | MARKS: " +
                                    totalMarksGained + " | MEMORY: " + masterDatabase.size()
                    );

                    page = loginIfNeeded(page, context);

                    try {
                        page.navigate(
                                "https://www.iwacusoft.com/ubumenyibwanjye/index",
                                new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                        );
                    } catch (Exception e) {
                        System.err.println("❌ Navigation failed: " + e.getMessage());
                        hardRestart(page);
                        continue;
                    }

                    Locator startBtn;
                    try {
                        startBtn = page.locator("button:has-text('START EARN')");
                        startBtn.waitFor();
                        startBtn.click();
                    } catch (Exception e) {
                        System.err.println("❌ Failed to click START EARN: " + e.getMessage());
                        hardRestart(page);
                        continue;
                    }

                    // ==================== Keep your logic intact ====================
                    page.locator("#subcategory-3").waitFor();
                    page.selectOption("#subcategory-3", new SelectOption().setIndex(2));
                    page.selectOption("#mySelect", new SelectOption().setValue(String.valueOf(totalQuestions)));
                    page.click("//a[contains(@onclick,\"selectLevel('advanced')\")]");
                    page.click("//button[contains(text(),'START')]");

                    FrameLocator quizFrame = page.frameLocator("#iframeId");
                    int currentQuestion = 1;

                    while (currentQuestion <= totalQuestions) {

                        boolean success = false;
                        int attempt = 0;
                        long questionStartTime = System.currentTimeMillis();

                        while (!success && attempt < MAX_RETRIES) {
                            attempt++;
                            try {
                                FrameLocator quizFrameRetry;
                                while (true) {
                                    try {
                                        quizFrameRetry = page.frameLocator("#iframeId");
                                        quizFrameRetry.locator("#qTitle")
                                                .waitFor(new Locator.WaitForOptions().setTimeout(5000));
                                        break;
                                    } catch (PlaywrightException e) {
                                        System.out.println("⚠️ Waiting for quiz frame... retrying in 5s");
                                        page.waitForTimeout(5000);
                                    }
                                }
                                processQuestion(quizFrameRetry, page, ai, currentQuestion, questionStartTime);
                                success = true;
                                currentQuestion++;
                            } catch (Exception e) {
                                System.err.println(
                                        "⚠️ Retry Q" + currentQuestion +
                                                " attempt " + attempt + " | " + e.getMessage()
                                );
                                page.waitForTimeout(3000);
                            }
                        }

                        if (!success) {
                            System.err.println("❌ Q" + currentQuestion + " skipped after retries.");
                            currentQuestion++;
                        }
                    }

                    saveData();
                    System.out.println("🏁 Batch finished. Sleeping briefly...");
                    humanWait(page, 1500, 3000);

                } catch (Exception e) {
                    System.err.println("🔄 Main loop error: " + e.getMessage());
                    hardRestart(page);
                    continue;
                }

                humanWait(page, 2000, 3500);
            }
            // ==================== SAFETY MEASURE END ====================
        } catch (Exception e) {
            System.err.println("❌ Playwright initialization failed: " + e.getMessage());
        }
    }

    // ==================== Login, processQuestion, humanWait, saveData, loadData remain unchanged ====================
    private static Page loginIfNeeded(Page page, BrowserContext context) {
        try {
            Locator phoneInput = page.locator("input[placeholder*='Phone']");
            if (phoneInput.isVisible()) {
                phoneInput.fill("0786862261");
                page.locator("input[placeholder*='PIN']").fill("12345");
                page.click("//button[contains(., 'Log in')]");
                page.waitForURL("**/index",
                        new Page.WaitForURLOptions().setTimeout(10000));
                context.storageState(
                        new BrowserContext.StorageStateOptions()
                                .setPath(Paths.get("state.json"))
                );
                System.out.println("✅ Auto-login successful");
            }
        } catch (Exception ignored) {}
        return page;
    }

    private static void humanWait(Page page, int min, int max) {
        int delay = random.nextInt(max - min + 1) + min;
        page.waitForTimeout(delay);
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
                System.out.println("📂 Memory Loaded: " + masterDatabase.size());
            }
        } catch (Exception ignored) {}
    }
}
