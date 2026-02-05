//package page;
//
//import com.microsoft.playwright.*;
//import com.microsoft.playwright.options.WaitUntilState;
//
//import java.nio.file.Files;
//import java.nio.file.Paths;
//import java.time.Duration;
//
//public class LoginPage {
//    private final String AUTH_STATE = "state.json";
//    private final String PHONE = "0786862261";
//    private final String PIN = "12345";
//    private final String BASE_URL = "https://www.iwacusoft.com/ubumenyibwanjye/services";
//
//    public Page getAuthenticatedPage(Browser browser, Browser.NewContextOptions options) {
//        // Load saved session if exists
//        if (Files.exists(Paths.get(AUTH_STATE))) {
//            options.setStorageStatePath(Paths.get(AUTH_STATE));
//        }
//
//        BrowserContext context = browser.newContext(options);
//        context.addInitScript("Object.defineProperty(navigator, 'webdriver', {get: () -> undefined})");
//
//        Page page = context.newPage();
//        page.setDefaultTimeout(30000); // default Playwright timeout
//
//        try {
//            System.out.println("🌐 Opening URL...");
//            page.navigate(BASE_URL, new Page.NavigateOptions().setWaitUntil(WaitUntilState.COMMIT));
//
//            // Retry loop for login inputs
//            boolean loginSuccess = false;
//            int attempts = 0;
//            while (!loginSuccess && attempts < 2) {
//                attempts++;
//                try {
//                    Locator phoneInput = page.locator("input[placeholder*='Phone']");
//                    phoneInput.waitFor(new Locator.WaitForOptions().setTimeout(15000));
//
//                    if (phoneInput.isVisible()) {
//                        phoneInput.fill(PHONE);
//                        page.locator("input[placeholder*='PIN']").fill(PIN);
//                        page.click("//button[contains(., 'Log in')]");
//
//                        // Wait for dashboard URL or small delay as fallback
//                        page.waitForURL("**/index", new Page.WaitForURLOptions().setTimeout(15000));
//                        context.storageState(new BrowserContext.StorageStateOptions().setPath(Paths.get(AUTH_STATE)));
//                        System.out.println("✅ Login successful.");
//                        loginSuccess = true;
//                    }
//                } catch (Exception e) {
//                    System.out.println("⚠️ Login attempt " + attempts + " failed. Retrying...");
//                    page.reload(new Page.ReloadOptions().setWaitUntil(WaitUntilState.COMMIT));
//                }
//            }
//
//            if (!loginSuccess) {
//                System.out.println("❌ Login failed after retries. Returning page anyway.");
//            }
//
//        } catch (Exception e) {
//            System.err.println("❌ Warning: Page load issues, continuing...");
//        }
//
//        return page;
//    }
//}

package page;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitUntilState;

import java.nio.file.Files;
import java.nio.file.Paths;

public class LoginPage {

    private final String AUTH_STATE = "state.json";
    private final String PHONE = System.getenv("LOGIN_PHONE");
    private final String PIN = System.getenv("LOGIN_PIN");
    private final String BASE_URL = System.getenv("LOGIN_LINK");

    public Page getAuthenticatedPage(Browser browser, Browser.NewContextOptions options) {

        // Load saved session if exists
        if (Files.exists(Paths.get(AUTH_STATE))) {
            options.setStorageStatePath(Paths.get(AUTH_STATE));
        }

        BrowserContext context = browser.newContext(options);
        context.addInitScript("Object.defineProperty(navigator, 'webdriver', {get: () -> undefined})");

        Page page = context.newPage();
        page.setDefaultTimeout(30000);

        try {
            System.out.println("🌐 Opening URL...");
            page.navigate(BASE_URL, new Page.NavigateOptions().setWaitUntil(WaitUntilState.COMMIT));

            // ===== LOGIN RETRY LOOP =====
            boolean loginSuccess = false;
            int attempts = 0;

            while (!loginSuccess && attempts < 3) { // >>>>>>>>>>>>>>>>> Added extra retry attempt
                attempts++;
                try {
                    Locator phoneInput = page.locator("input[placeholder*='Phone']");
                    phoneInput.waitFor(new Locator.WaitForOptions().setTimeout(15000));

                    if (phoneInput.isVisible()) {
                        phoneInput.fill(PHONE);
                        page.locator("input[placeholder*='PIN']").fill(PIN);
                        page.click("//button[contains(., 'Log in')]");

                        // Wait for dashboard URL or small delay as fallback
                        page.waitForURL("**/index", new Page.WaitForURLOptions().setTimeout(15000));

                        // Save session after login
                        context.storageState(new BrowserContext.StorageStateOptions().setPath(Paths.get(AUTH_STATE)));
                        System.out.println("✅ Login successful.");
                        loginSuccess = true;
                    }

                    // >>>>>>>>>>>>>>>>> WATCHDOG: detect if page freezes during login inputs

                    long startTime = System.currentTimeMillis();
                    while (!loginSuccess && System.currentTimeMillis() - startTime < 10000) {
                        // wait 10 seconds max for page to respond
                        page.waitForTimeout(500);
                    }


                } catch (Exception e) {
                    System.out.println("⚠️ Login attempt " + attempts + " failed. Retrying...");
                    // >>>>>>>>>>>>>>>>> Self-healing: refresh page if login fails
                    page.reload(new Page.ReloadOptions().setWaitUntil(WaitUntilState.COMMIT));
                }
            }

            if (!loginSuccess) {
                System.out.println("❌ Login failed after retries. Returning page anyway.");
            }

            // >>>>>>>>>>>>>>>>> GLOBAL SAFETY MEASURE: detect if page is frozen

            page.onDialog(dialog -> {
                System.out.println("⚠️ Dialog detected: " + dialog.message());
                dialog.dismiss(); // dismiss any unexpected pop-ups
            });


        } catch (Exception e) {
            System.err.println("❌ Warning: Page load issues, continuing...");
        }

        return page;
    }
}
