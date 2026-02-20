package test;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitUntilState;
import page.LoginPage;

import java.util.List;
import java.util.Map;

public class DoQuiz2Test {

    private static final String MEMORY_FILE = "memory.json";

    public static void main(String[] args) {
        String accountName = args.length > 0 ? args[0] : "NA";
        Playwright playwright = Playwright.create();
        Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));

        Map<String, String> memory = MemoryUtils.loadMemory(MEMORY_FILE);

        Browser.NewContextOptions contextOptions = new Browser.NewContextOptions();
        LoginPage loginPage = new LoginPage();
        Page page = loginPage.getAuthenticatedPage(browser, contextOptions);

        try {
            page.navigate("https://www.iwacusoft.com/ubumenyibwanjye/index", new Page.NavigateOptions()
                    .setWaitUntil(WaitUntilState.DOMCONTENTLOADED));

            // ===== Choose subcategory 2 (matches YAML assignment for DoQuiz2Test) =====
            page.locator("//div[contains(@class,'subcat')][2]").click();

            page.locator("//button[contains(.,'Start')]").click();

            boolean hasMoreQuestions = true;

            while (hasMoreQuestions) {
                Locator questionLocator = page.locator("#qTitle");
                String questionText = questionLocator.innerText().trim();

                List<Locator> options = page.locator("#options .opt").all();

                String answerText;

                if (memory.containsKey(questionText)) {
                    answerText = memory.get(questionText);
                    for (Locator option : options) {
                        if (option.innerText().trim().equals(answerText)) {
                            option.click();
                            break;
                        }
                    }
                } else {
                    // Pick first option for new question
                    options.get(0).click();
                }

                page.locator("//button[contains(.,'Submit')]").click();

                // After submit, capture correct text answer
                try {
                    String correctLetter = page.locator("xpath=//span[contains(., 'Correct:')]/following-sibling::b").innerText().trim();

                    for (Locator option : options) {
                        String optText = option.innerText().trim();
                        if (optText.startsWith(correctLetter) || optText.equals(correctLetter)) {
                            answerText = optText;
                            break;
                        }
                    }

                    memory.put(questionText, answerText);
                    MemoryUtils.saveMemory(MEMORY_FILE, memory);

                } catch (Exception e) {
                    if (!memory.containsKey(questionText)) {
                        memory.put(questionText, options.get(0).innerText().trim());
                        MemoryUtils.saveMemory(MEMORY_FILE, memory);
                    }
                }

                hasMoreQuestions = page.locator("#qTitle").isVisible();
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            browser.close();
            playwright.close();
        }

        System.out.println("✅ Quiz completed for account: " + accountName);
    }
}
