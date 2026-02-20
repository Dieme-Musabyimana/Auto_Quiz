import com.microsoft.playwright.*;
import com.microsoft.playwright.options.*;
import java.nio.file.Paths;
import java.util.*;

public class DoQuiz2Test {

    private static final String LOGIN_URL = "https://yourquizsite.com/login";
    private static final String COOKIE_PATH = "cookies_doquiz2.json";
    private static Map<String, String> memoryDatabase = new HashMap<>();

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java DoQuiz2Test <account> [loginOnly|freshQuiz]");
            return;
        }

        String account = args[0];
        String mode = args.length > 1 ? args[1] : "freshQuiz";

        String phone = System.getenv("LOGIN_PHONE");
        String pin = System.getenv("LOGIN_PIN");

        try (Playwright playwright = Playwright.create()) {
            BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
                    .setHeadless(true);

            Browser browser;

            if (mode.equals("loginOnly")) {
                browser = playwright.chromium().launch(launchOptions);
                BrowserContext context = browser.newContext(
                        new Browser.NewContextOptions()
                                .setStorageStatePath(Paths.get(COOKIE_PATH))
                );
                login(context, phone, pin);
                context.storageState(new BrowserContext.StorageStateOptions().setPath(Paths.get(COOKIE_PATH)));
                context.close();
                browser.close();
                System.out.println("Login completed and saved for account: " + account);

            } else if (mode.equals("freshQuiz")) {
                browser = playwright.chromium().launch(launchOptions);
                BrowserContext context = browser.newContext();
                try {
                    context.addCookies(BrowserContext.Cookies.fromFile(Paths.get(COOKIE_PATH)));
                } catch (Exception ignored) {}
                Page page = context.newPage();
                login(page, phone, pin);
                startQuiz(page);
                context.storageState(new BrowserContext.StorageStateOptions().setPath(Paths.get(COOKIE_PATH)));
                context.close();
                browser.close();
            } else {
                System.out.println("Unknown mode: " + mode);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void login(BrowserContext context, String phone, String pin) {
        Page page = context.newPage();
        login(page, phone, pin);
        page.close();
    }

    private static void login(Page page, String phone, String pin) {
        page.navigate(LOGIN_URL);
        page.locator("#phone").fill(phone);
        page.locator("#pin").fill(pin);
        page.locator("#loginButton").click();
        page.waitForLoadState();
        System.out.println("Logged in successfully");
    }

    private static void startQuiz(Page page) {
        page.navigate("https://yourquizsite.com/quiz");
        page.locator("#subcategorySelect").click();
        page.locator(".subcategoryOption").first().click();

        for (int i = 1; i <= 50; i++) {
            try {
                Locator option = page.locator(".opt").first();
                String answer = memoryDatabase.getOrDefault("Q" + i, "skip");
                if (!answer.equals("skip")) {
                    option.filter(new Locator.FilterOptions().setHasText(answer)).click();
                }
                page.locator("#submitButton").click();
                memoryDatabase.put("Q" + i, option.textContent());
            } catch (Exception e) {
                System.out.println("Q" + i + " skipped or error occurred");
            }
        }
    }
}
