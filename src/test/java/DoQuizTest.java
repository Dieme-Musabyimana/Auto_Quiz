
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
@@ -53,78 +32,76 @@ public void startBot() {
            while (true) {
                try {
                    System.out.println("\n📊 STATS | MARKS: " + totalMarksGained + " | MEMORY: " + masterDatabase.size());
                    page.navigate("https://www.iwacusoft.com/ubumenyibwanjye/index");




                    loginIfNeeded(page);

                    // --- STEP 1: RESILIENT START EARN CLICK ---
                    Locator startBtn = page.locator("button:has-text('START EARN'), a:has-text('START EARN'), .btn-primary").first();
                    startBtn.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(15000));

                    // Click until the URL changes or the dropdown appears (Max 3 tries)
                    for (int i = 0; i < 3; i++) {
                        startBtn.click(new Locator.ClickOptions().setForce(true));
                        page.waitForTimeout(3000); // Wait for page reaction
                        if (page.locator("#subcategory-3").isVisible()) break;
                        System.out.println("⚠️ Click didn't trigger dropdown, retrying...");
                    }

                    // --- STEP 2: SELECT DROPDOWNS ---
                    System.out.println("✅ Menu loaded. Selecting options...");
                    Locator subCategory = page.locator("#subcategory-3");
                    subCategory.selectOption(new SelectOption().setIndex(2));
                    
                    page.locator("#mySelect").selectOption(new SelectOption().setValue(String.valueOf(totalQuestions)));

                    // --- STEP 3: SELECT LEVEL & LAUNCH ---
                    // Using direct JavaScript click because buttons in modals are often 'unclickable' by standard Playwright on Linux
                    page.evaluate("() => { " +
                        "document.querySelector(\"a[onclick*='selectLevel']\").click(); " +
                        "setTimeout(() => { document.querySelector(\"button:has-text('START'), #startBtn\").click(); }, 1000);" +
                        "}");

                    System.out.println("🚀 Quiz Launched via JS execution.");

                    // --- STEP 4: QUESTION LOOP ---
                    int currentQuestion = 1;
                    while (currentQuestion <= totalQuestions) {
                        try {
                            FrameLocator quizFrame = page.frameLocator("#iframeId");
                            quizFrame.locator("#qTitle").waitFor(new Locator.WaitForOptions().setTimeout(20000));


                            processQuestion(quizFrame, page, ai, currentQuestion);
                            currentQuestion++;
                        } catch (Exception e) {
                            System.err.println("⚠️ Q" + currentQuestion + " Error. Reloading...");
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

@@ -133,46 +110,41 @@ private static void processQuestion(FrameLocator quizFrame, Page page, GroqServi
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
@@ -181,7 +153,7 @@ private static void saveData() {
        } catch (Exception ignored) {}
    }

    private static void loadData() {
        try {
            File file = new File(DATA_FILE);
            if (file.exists()) {
